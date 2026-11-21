package com.weaver.seconddev.hnweaver.common.formassist.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.weaver.common.distribution.genid.IdGenerator;
import com.weaver.common.distribution.lock.DistributionLockInterface;
import com.weaver.common.form.data.FormData;
import com.weaver.common.form.data.detail.FormDataDetail;
import com.weaver.common.form.data.log.FormDataLog;
import com.weaver.common.form.data.log.FormDataLogService;
import com.weaver.common.form.data.log.OperationType;
import com.weaver.common.form.data.log.SimpleFormDataLog;
import com.weaver.common.form.dto.data.FormDataDelDto;
import com.weaver.common.form.dto.data.FormDataDto;
import com.weaver.common.form.metadata.ModuleSource;
import com.weaver.common.form.metadata.field.FormField;
import com.weaver.seconddev.hnweaver.common.exception.FormAssistException;
import com.weaver.seconddev.hnweaver.common.formassist.domain.FormDeletionProgressException;
import com.weaver.seconddev.hnweaver.common.formassist.domain.dto.FormEditRequest;
import com.weaver.teams.domain.user.SimpleEmployee;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * 在删除前保存租户隔离的日志补偿快照，按记录串行处理删除与重试。
 * 使用固定的平台日志记录 ID 回读写入结果，避免在重新点击删除时再次保存表单。
 */
@Slf4j
@Service
public class FormDeletionAudit {
    private static final long RETENTION_HOURS = 24L;
    private static final long RESEND_DELAY_MILLIS = TimeUnit.SECONDS.toMillis(15);
    private static final long LOCK_LEASE_SECONDS = 120L;
    private static final String KEY_PREFIX = "secondev:tools:form-deletion:";

    private final ObjectProvider<RedisTemplate<String, String>> redis;
    private final ObjectProvider<FormDataLogService> logs;
    private final ObjectProvider<DistributionLockInterface> locks;
    private final ObjectMapper json = new ObjectMapper();

    /**
     * 复用 E10 提供的共享缓存、分布式锁和原生表单日志服务。
     * @param redis 平台 Redis 模板
     * @param logs 平台表单日志服务提供器
     * @param locks 平台分布式锁提供器
     */
    public FormDeletionAudit(
            @Qualifier("redisTemplate") ObjectProvider<RedisTemplate<String, String>> redis,
            ObjectProvider<FormDataLogService> logs,
            ObjectProvider<DistributionLockInterface> locks) {
        this.redis = redis;
        this.logs = logs;
        this.locks = locks;
    }

    /**
     * 同一租户、模块及记录只允许一个助手删除操作持有锁。
     */
    <T> T withRecordLock(FormEditRequest request, SimpleEmployee operator, Supplier<T> operation) {
        String key = recordKey(request, operator) + ":lock";
        DistributionLockInterface lock = locks.getIfAvailable();
        if (lock == null) {
            throw new FormAssistException("当前环境未启用 E10 分布式锁，无法安全删除明细");
        }
        if (!lock.isSuccessTryLock(key, 0L, LOCK_LEASE_SECONDS, TimeUnit.SECONDS)) {
            throw new FormAssistException("当前表单有明细删除正在处理，请稍后重试");
        }
        try {
            return operation.get();
        } finally {
            try {
                lock.unLock(key);
            } catch (Exception exception) {
                log.warn("释放表单删除锁失败，dataId={}", request.getDataId(), exception);
            }
        }
    }

    /**
     * 按当前操作人和完全相同的选中行集合查找补偿记录。
     */
    FormDeletionSnapshot find(FormEditRequest request, SimpleEmployee operator) {
        String value = requireRedis().opsForValue().get(retryKey(request, operator));
        if (value == null) {
            return null;
        }
        try {
            return json.readValue(value, FormDeletionSnapshot.class);
        } catch (JsonProcessingException exception) {
            log.error("无法读取待补记的表单删除日志，dataId={}", request.getDataId(), exception);
            throw new FormAssistException("无法读取明细删除补偿记录，请联系管理员");
        }
    }

    /**
     * 删除前记录选中行的旧值，确保快照保存成功后才允许调用删除接口。
     */
    FormDeletionSnapshot prepare(
            FormEditRequest request,
            FormData existing,
            FormDataDto change,
            List<FormDataDetail> details,
            Map<Long, FormField> metadata,
            SimpleEmployee operator) {
        requireLogService();
        Set<Long> selected = new HashSet<>(request.getRowIds());
        FormDeletionSnapshot pending = new FormDeletionSnapshot();
        pending.setRecordId(IdGenerator.generate());
        pending.setObjectId(change.getObjectId());
        pending.setRows(change.getDelRowIds());
        SimpleFormDataLog source = existing.getSimpleFormDataLog();
        if (source != null) {
            pending.setSources(source.getSources());
            pending.setSourceId(source.getSourceId());
        }
        pending.setValues(details.stream()
                .filter(detail -> selected.contains(detail.getRowId())
                        && FormEditChanges.resolveSubForm(detail, metadata).filter(request.getSubFormId()::equals)
                                .isPresent())
                .map(detail -> FormDeletionSnapshot.captureValue(detail, metadata, request.getSubFormId()))
                .collect(Collectors.toList()));
        write(request, operator, pending);
        return pending;
    }

    /**
     * 回读平台日志。尚未确认时保留快照，同一请求等待冷却后可以再投递。
     */
    void complete(
            FormEditRequest request,
            FormDeletionSnapshot pending,
            FormData existing,
            Map<Long, FormField> metadata,
            SimpleEmployee operator) {
        if (pending.isComplete()) {
            return;
        }
        try {
            FormDataLogService service = requireLogService();
            if (hasLogs(service, pending)) {
                markComplete(request, operator, pending);
                return;
            }
            long now = System.currentTimeMillis();
            if (now - pending.getLastSentAt() >= RESEND_DELAY_MILLIS) {
                List<FormDataDetail> oldValues = pending.restoreValues(metadata);
                FormData event = createEvent(request, existing, pending);
                pending.setLastSentAt(now);
                write(request, operator, pending);
                service.sendFormDataLogMessage(OperationType.SAVE, Collections.singletonList(event),
                        Collections.singletonMap(request.getDataId(), oldValues), pending.getSources(),
                        pending.getSourceId(), operator);
                if (awaitLogs(service, pending)) {
                    markComplete(request, operator, pending);
                    return;
                }
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            log.warn("等待明细日志时被中断，dataId={}，recordId={}", request.getDataId(), pending.getRecordId());
        } catch (Exception exception) {
            log.warn("明细已删除，表单日志待补记，dataId={}，recordId={}",
                    request.getDataId(), pending.getRecordId(), exception);
        }
        throw new FormDeletionProgressException("明细已删除，表单日志尚未确认。请稍后重试相同的删除操作以补记日志");
    }

    private boolean hasLogs(FormDataLogService service, FormDeletionSnapshot pending) {
        List<FormDataLog> written = service.listAllFormDataLogByRecordIds(
                Collections.singletonList(pending.getRecordId()), Collections.emptyList());
        if (written == null || written.isEmpty()) {
            return false;
        }
        // 此基线查询结果以明细表 ID、行索引和字段 ID 标识日志细项。
        Set<List<Long>> writtenCells = written.stream()
                .filter(entry -> entry.getFormField() != null && entry.getSubForm() != null)
                .map(entry -> Arrays.asList(entry.getSubForm().getId(), entry.getDataIndex(), entry.getFormField().getId()))
                .collect(Collectors.toSet());
        return pending.getValues().stream().allMatch(value ->
                writtenCells.contains(Arrays.asList(value.getSubFormId(), value.getDataIndex(), value.getFieldId())));
    }

    /**
     * 短暂等待平台异步消费者提交日志，超时后交给原请求补记。
     */
    private boolean awaitLogs(FormDataLogService service, FormDeletionSnapshot pending) throws InterruptedException {
        for (int attempt = 0; attempt < 3; attempt++) {
            if (hasLogs(service, pending)) {
                return true;
            }
            TimeUnit.MILLISECONDS.sleep(250L);
        }
        return hasLogs(service, pending);
    }

    private void markComplete(FormEditRequest request, SimpleEmployee operator, FormDeletionSnapshot pending) {
        pending.setComplete(true);
        pending.setValues(Collections.emptyList());
        write(request, operator, pending);
    }

    private FormDataLogService requireLogService() {
        FormDataLogService service = logs.getIfAvailable();
        if (service == null) {
            throw new FormAssistException("当前环境未启用 E10 表单日志服务，无法确认明细删除日志");
        }
        return service;
    }

    private FormData createEvent(FormEditRequest request, FormData existing, FormDeletionSnapshot pending) {
        FormData event = new FormData(request.getDataId());
        event.setRecordId(pending.getRecordId());
        event.setForm(existing.getForm());
        event.setFormLayout(existing.getFormLayout());
        event.setModule(existing.getModule() == null ? ModuleSource.valueOf(request.getModule()) : existing.getModule());
        event.setClient(existing.getClient());
        event.setRefId(existing.getRefId());
        event.setExternalId(existing.getExternalId());
        event.setObjectId(pending.getObjectId());
        event.setDataDetails(Collections.emptyList());
        event.setDataDelRows(pending.getRows());
        return event;
    }

    /**
     * 保存已确认删除的范围，使部分删除同样可以在重试时补记。
     */
    void retainRemovedRows(
            FormEditRequest request, SimpleEmployee operator, FormDeletionSnapshot snapshot, Set<Long> removed) {
        snapshot.retainRemovedRows(removed);
        write(request, operator, snapshot);
    }

    private void write(FormEditRequest request, SimpleEmployee operator, FormDeletionSnapshot pending) {
        try {
            requireRedis().opsForValue().set(retryKey(request, operator), json.writeValueAsString(pending),
                    RETENTION_HOURS, TimeUnit.HOURS);
        } catch (JsonProcessingException exception) {
            log.error("无法保存明细删除补偿快照，dataId={}", request.getDataId(), exception);
            throw new FormAssistException("保存明细删除补偿快照失败");
        }
    }

    private RedisTemplate<String, String> requireRedis() {
        RedisTemplate<String, String> template = redis.getIfAvailable();
        if (template == null) {
            throw new FormAssistException("当前环境未启用 E10 共享缓存，无法保留明细删除日志");
        }
        return template;
    }

    private String retryKey(FormEditRequest request, SimpleEmployee operator) {
        String rows = new TreeSet<>(request.getRowIds()).toString();
        return recordKey(request, operator) + ":" + request.getFormId() + ":" + request.getSubFormId()
                + ":" + operator.getId() + ":" + UUID.nameUUIDFromBytes(rows.getBytes(StandardCharsets.UTF_8));
    }

    private String recordKey(FormEditRequest request, SimpleEmployee operator) {
        return KEY_PREFIX + operator.getTenantKey() + ":" + request.getModule() + ":" + request.getDataId();
    }

}
