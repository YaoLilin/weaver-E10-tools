package com.weaver.seconddev.hnweaver.common.formassist.service;

import com.weaver.common.form.api.rest.FormRestFactory;
import com.weaver.common.form.api.rest.data.FormDataRest;
import com.weaver.common.form.data.FormData;
import com.weaver.common.form.data.detail.FormDataDetail;
import com.weaver.common.form.data.log.FormDataLogService;
import com.weaver.common.form.data.log.OperationType;
import com.weaver.common.form.data.log.SimpleFormDataLog;
import com.weaver.common.form.dto.data.FormDataDelDto;
import com.weaver.common.form.dto.data.FormDataDetailDto;
import com.weaver.common.form.dto.data.FormDataDto;
import com.weaver.common.form.dto.data.FormDataOptionDto;
import com.weaver.common.form.metadata.field.FormField;
import com.weaver.framework.rpc.annotation.RpcReference;
import com.weaver.seconddev.hnweaver.common.exception.FormAssistException;
import com.weaver.seconddev.hnweaver.common.formassist.domain.FormAssistModule;
import com.weaver.seconddev.hnweaver.common.formassist.domain.dto.FormEditRequest;
import com.weaver.seconddev.hnweaver.common.util.FieldRpcUtil;
import com.weaver.teams.domain.user.SimpleEmployee;
import com.weaver.teams.security.context.UserContext;
import com.weaver.workflow.core.api.rest.publicapi.WfcFormDataRest;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 校验管理员及表单归属后，通过平台 RPC 保存字段和明细变更，不提交审批。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FormEditService {
    private final FormRestFactory formRestFactory;
    private final FieldRpcUtil fieldRpcUtil;
    private final ObjectProvider<FormDataLogService> formDataLogs;

    @RpcReference
    private WfcFormDataRest wfcFormDataRest;

    /**
     * 持久化本次字段或明细行变更。
     *
     * @param request 经 Controller 校验的变更请求
     * @return 平台返回对应数据 ID 时为 true
     */
    public boolean save(FormEditRequest request) {
        SimpleEmployee operator = requireAdmin();
        FormAssistModule module = FormAssistModule.fromValue(request.getModule());
        FormDataRest rest = requireFormDataRest(module);
        FormData existing = loadData(module, rest, request.getDataId(), operator);
        validateRecordOwnership(request, existing);
        Map<Long, FormField> metadata = requireFieldMetadata(request, module, operator);
        List<FormDataDetail> details = Optional.ofNullable(existing.getDataDetails()).orElse(Collections.emptyList());

        boolean deleting = request.getOperation() == FormEditRequest.Operation.DELETE_ROWS;
        FormDataDto change = prepareChange(request, existing, metadata, details);
        change.setObjectId(rest.getObjectIdByFormData(request.getDataId(), operator));

        FormDataLogService logService = deleting ? requireFormDataLogService() : null;
        List<FormDataDetail> deletedValues =
                deleting ? snapshotDeletedValues(request, details, metadata) : Collections.emptyList();

        persistChange(request, rest, change, operator);
        if (deleting) {
            FormData refreshed = loadData(module, rest, request.getDataId(), operator);
            verifyDeletedRows(request, refreshed, details, metadata);
            writeDeletionLog(logService, existing, change, deletedValues, operator);
        }
        return true;
    }

    /**
     * 获取当前登录的系统管理员，作为平台保存和日志的操作人。
     */
    private SimpleEmployee requireAdmin() {
        SimpleEmployee operator = UserContext.getCurrentUser();
        if (operator == null || !operator.isAdmin()) {
            throw new FormAssistException("仅系统管理员可编辑表单数据");
        }
        return operator;
    }

    /**
     * 获取当前表单模块对应的平台保存接口。
     */
    private FormDataRest requireFormDataRest(FormAssistModule module) {
        FormDataRest rest = formRestFactory.getFormDataRest(module.getRpcGroup().getValue());
        if (rest == null) {
            throw new FormAssistException("未找到表单保存服务");
        }
        return rest;
    }

    /**
     * 确认请求中的数据记录属于指定表单。
     */
    private void validateRecordOwnership(FormEditRequest request, FormData existing) {
        boolean matchesForm =
                existing != null
                        && existing.getForm() != null
                        && request.getFormId().equals(existing.getForm().getId());
        if (!matchesForm) {
            throw new FormAssistException("表单记录不存在或不属于指定表单");
        }
    }

    /**
     * 读取用于校验字段及明细归属的元数据，键为字段 ID。
     */
    private Map<Long, FormField> requireFieldMetadata(
            FormEditRequest request, FormAssistModule module, SimpleEmployee operator) {
        Map<Long, FormField> metadata =
                fieldRpcUtil.getFieldMapByForm(module.getRpcGroup(), request.getFormId(), operator);
        if (metadata == null || metadata.isEmpty()) {
            throw new FormAssistException("未查询到当前表单字段");
        }
        return metadata;
    }

    /**
     * 按操作类型构造字段值或明细删除标识。
     */
    private FormDataDto prepareChange(
            FormEditRequest request, FormData existing, Map<Long, FormField> metadata, List<FormDataDetail> details) {
        FormDataDto change = createChange(request, existing);
        if (request.getOperation() == FormEditRequest.Operation.DELETE_ROWS) {
            change.setDelRowIds(buildDeletions(request, metadata, details));
            // key 为当前明细表 ID，false 表示增量处理，禁止整表覆盖。
            change.setCoverMap(Collections.singletonMap(request.getSubFormId(), false));
        } else {
            change.setDataDetails(buildFields(request, metadata, details));
        }
        return change;
    }

    /**
     * 明细删除前确认原生日志服务可用。
     */
    private FormDataLogService requireFormDataLogService() {
        FormDataLogService logService = formDataLogs.getIfAvailable();
        if (logService == null) {
            throw new FormAssistException("当前环境未启用 E10 表单日志服务，无法删除明细");
        }
        return logService;
    }

    /**
     * 调用平台按行保存接口，并确认返回数据 ID 与请求一致。
     */
    private void persistChange(
            FormEditRequest request, FormDataRest rest, FormDataDto change, SimpleEmployee operator) {
        log.info(
                "表单助手保存，operation={}，formId={}，dataId={}，operatorId={}",
                request.getOperation(),
                request.getFormId(),
                request.getDataId(),
                operator.getId());
        FormDataDto saved = rest.saveFormDataByRowIds(change, operator);
        if (saved == null || !request.getDataId().equals(saved.getDataId())) {
            throw new FormAssistException("平台未确认表单数据保存成功");
        }
    }

    /**
     * 用平台回读结果确认选中行已删除且未选中行仍存在。
     */
    private void verifyDeletedRows(
            FormEditRequest request,
            FormData refreshed,
            List<FormDataDetail> originalDetails,
            Map<Long, FormField> metadata) {
        if (refreshed == null || refreshed.getDataDetails() == null) {
            throw new FormAssistException("删除明细失败：无法确认平台保存结果");
        }
        Set<Long> remaining =
                refreshed.getDataDetails().stream()
                        .map(FormDataDetail::getRowId)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet());
        Set<Long> retained = rowsOf(request.getSubFormId(), originalDetails, metadata);
        retained.removeAll(request.getRowIds());
        if (!remaining.containsAll(retained)) {
            throw new FormAssistException("删除范围异常：未选中明细行缺失，请停止操作并联系管理员");
        }
        if (request.getRowIds().stream().anyMatch(remaining::contains)) {
            throw new FormAssistException("删除明细失败：平台保存后仍存在待删除行");
        }
    }

    /**
     * 留存选中行的原字段值，供 E10 日志比较删除前后的数据。
     */
    private List<FormDataDetail> snapshotDeletedValues(
            FormEditRequest request, List<FormDataDetail> details, Map<Long, FormField> metadata) {
        Set<Long> selected = new HashSet<>(request.getRowIds());
        return details.stream()
                .filter(detail ->
                        selected.contains(detail.getRowId())
                                && resolveSubForm(detail, metadata).filter(request.getSubFormId()::equals).isPresent())
                .map(detail -> copyLogDetail(detail, metadata))
                .collect(Collectors.toList());
    }

    /**
     * 补全平台日志序列化所需的字段和明细表元数据。
     */
    private FormDataDetail copyLogDetail(FormDataDetail detail, Map<Long, FormField> metadata) {
        FormDataDetail copy = detail.clone();
        FormField field = resolveField(detail, metadata);
        if (field == null) {
            throw new FormAssistException("无法获取待删除明细的字段信息，不能生成表单日志");
        }
        copy.setFormField(field);
        if (copy.getSubForm() == null) {
            copy.setSubForm(field.getSubForm());
        }
        return copy;
    }

    /**
     * 将已确认的删除和旧字段值交给 E10 原生表单日志服务。
     */
    private void writeDeletionLog(
            FormDataLogService logService,
            FormData existing,
            FormDataDto change,
            List<FormDataDetail> deletedValues,
            SimpleEmployee operator) {
        FormData event = new FormData(change.getDataId());
        event.setForm(existing.getForm());
        event.setFormLayout(existing.getFormLayout());
        event.setModule(existing.getModule());
        event.setClient(existing.getClient());
        event.setRefId(existing.getRefId());
        event.setExternalId(existing.getExternalId());
        event.setObjectId(change.getObjectId());
        event.setDataDetails(Collections.emptyList());
        event.setDataDelRows(change.getDelRowIds());
        SimpleFormDataLog source = existing.getSimpleFormDataLog();
        try {
            // key 为表单数据 ID，value 为本次删除行的原字段值。
            logService.sendFormDataLogMessage(
                    OperationType.SAVE,
                    Collections.singletonList(event),
                    Collections.singletonMap(change.getDataId(), deletedValues),
                    source == null ? null : source.getSources(),
                    source == null ? null : source.getSourceId(),
                    operator);
        } catch (Exception exception) {
            log.error(
                    "明细已删除，发送表单日志失败，dataId={}，subFormId={}",
                    change.getDataId(),
                    change.getDelRowIds().get(0).getSubFormId(),
                    exception);
            throw new FormAssistException("明细已删除，但表单日志发送失败，请刷新后核对");
        }
    }

    /**
     * 使用与字段查询相同的数据服务，避免删除确认和页面读取不同的数据版本。
     */
    private FormData loadData(FormAssistModule module, FormDataRest rest, Long dataId, SimpleEmployee operator) {
        return module == FormAssistModule.WORKFLOW
                ? wfcFormDataRest.getFullFormData(dataId, operator)
                : rest.getFullFormData(dataId, operator);
    }

    /**
     * 创建限定当前记录的增量保存对象。
     */
    private FormDataDto createChange(FormEditRequest request, FormData existing) {
        FormDataDto change = new FormDataDto();
        change.setFormId(request.getFormId());
        change.setDataId(request.getDataId());
        change.setModule(request.getModule());
        change.setNewLayout(existing.isNewLayout());
        change.setLayoutMultiId(existing.getMultipleLayoutId());
        change.setDataDetails(Collections.emptyList());
        return change;
    }

    /**
     * 校验字段和行的归属，只构造本次需要保存的字段。
     */
    private List<FormDataDetailDto> buildFields(
            FormEditRequest request, Map<Long, FormField> metadata, List<FormDataDetail> details) {
        if (request.getFields().isEmpty() || !request.getRowIds().isEmpty()) {
            throw new FormAssistException("字段保存请求必须包含字段且不能混入删除行");
        }
        Set<String> unique = new HashSet<>();
        // key 为新增行 ID，value 为服务端分配的明细索引，避免信任客户端行号。
        Map<Long, Long> newRowIndexes = new HashMap<>(16);
        Set<Long> existingRows = rowsOf(request.getSubFormId(), details, metadata);
        long nextIndex =
                details.stream()
                        .filter(item -> existingRows.contains(item.getRowId()))
                        .map(FormDataDetail::getDataIndex)
                        .filter(Objects::nonNull)
                        .max(Long::compareTo)
                        .orElse(0L);
        List<FormDataDetailDto> changes = new ArrayList<>(request.getFields().size());
        for (FormEditRequest.FieldChange item : request.getFields()) {
            FormField field = metadata.get(item.getFieldId());
            if (field == null || !request.getFormId().equals(field.getFormId())) {
                throw new FormAssistException("字段不属于当前表单");
            }
            Long subFormId = field.getSubForm() == null ? null : field.getSubForm().getId();
            validateRow(request, item, subFormId, existingRows);
            if (!unique.add(item.getFieldId() + ":" + item.getRowId())) {
                throw new FormAssistException("同一字段和明细行不能重复提交");
            }
            FormDataDetailDto value = new FormDataDetailDto();
            value.setFieldId(item.getFieldId());
            value.setSubFormId(subFormId);
            value.setRowId(item.getRowId());
            if (request.getOperation() == FormEditRequest.Operation.ADD_ROWS) {
                if (!newRowIndexes.containsKey(item.getRowId())) {
                    newRowIndexes.put(item.getRowId(), ++nextIndex);
                }
                value.setDataIndex(newRowIndexes.get(item.getRowId()));
            }
            value.setContent(item.getContent());
            value.setDataOptions(item.getDataOptions().stream().map(this::toOption).collect(Collectors.toList()));
            changes.add(value);
        }
        return changes;
    }

    /**
     * 校验明细行存在性，防止使用其他表单或明细表的行 ID。
     */
    private void validateRow(
            FormEditRequest request, FormEditRequest.FieldChange field, Long subFormId, Set<Long> existingRows) {
        if (subFormId == null) {
            if (field.getRowId() != null
                    || request.getSubFormId() != null
                    || request.getOperation() != FormEditRequest.Operation.UPDATE) {
                throw new FormAssistException("主表字段只能按字段修改");
            }
            return;
        }
        if (field.getRowId() == null || !subFormId.equals(request.getSubFormId())) {
            throw new FormAssistException("明细字段必须提供所属明细表及行 ID");
        }
        boolean adding = request.getOperation() == FormEditRequest.Operation.ADD_ROWS;
        if (adding == existingRows.contains(field.getRowId())) {
            throw new FormAssistException(adding ? "新增明细行 ID 已存在" : "明细行不存在或不属于当前明细表");
        }
        if (adding && field.getDataIndex() == null) {
            throw new FormAssistException("新增明细必须提供行索引");
        }
    }

    /**
     * 校验选中行均属于当前明细表，并生成平台删除标识。
     */
    private List<FormDataDelDto> buildDeletions(
            FormEditRequest request, Map<Long, FormField> metadata, List<FormDataDetail> details) {
        boolean hasSubForm =
                request.getSubFormId() != null
                        && metadata.values().stream()
                        .anyMatch(field ->
                                field.getSubForm() != null
                                        && request.getSubFormId().equals(field.getSubForm().getId()));
        if (!hasSubForm || request.getRowIds().isEmpty() || !request.getFields().isEmpty()) {
            throw new FormAssistException("删除请求必须提供当前明细表和待删除行，不能混入字段修改");
        }
        Set<Long> existingRows = rowsOf(request.getSubFormId(), details, metadata);
        List<FormDataDelDto> deletions = new ArrayList<>(request.getRowIds().size());
        for (Long rowId : new LinkedHashSet<>(request.getRowIds())) {
            if (!existingRows.contains(rowId)) {
                throw new FormAssistException("待删除行不存在或不属于当前明细表");
            }
            FormDataDelDto deletion = new FormDataDelDto();
            deletion.setDataId(request.getDataId());
            deletion.setFormId(request.getFormId());
            deletion.setSubFormId(request.getSubFormId());
            deletion.setRowId(rowId);
            deletions.add(deletion);
        }
        return deletions;
    }

    /**
     * 从当前记录中提取指定明细表的行 ID。
     */
    private Set<Long> rowsOf(Long subFormId, List<FormDataDetail> details, Map<Long, FormField> metadata) {
        if (subFormId == null) {
            return Collections.emptySet();
        }
        return details.stream()
                .filter(item ->
                        item.getRowId() != null && resolveSubForm(item, metadata).filter(subFormId::equals).isPresent())
                .map(FormDataDetail::getRowId)
                .collect(Collectors.toSet());
    }

    /**
     * 数据值未携带明细表对象时，通过字段元数据确认明细归属。
     */
    private Optional<Long> resolveSubForm(FormDataDetail detail, Map<Long, FormField> metadata) {
        if (detail.getSubForm() != null) {
            return Optional.ofNullable(detail.getSubForm().getId());
        }
        FormField field = resolveField(detail, metadata);
        return field == null || field.getSubForm() == null
                ? Optional.empty()
                : Optional.ofNullable(field.getSubForm().getId());
    }

    /**
     * 解析数据值对应的表单字段元数据。
     */
    private FormField resolveField(FormDataDetail detail, Map<Long, FormField> metadata) {
        Long fieldId = detail.getFormField() == null ? null : detail.getFormField().getId();
        if (fieldId == null && detail.getFieldIds() != null && !detail.getFieldIds().isEmpty()) {
            fieldId = detail.getFieldIds().get(0);
        }
        return metadata.get(fieldId);
    }

    /**
     * 只映射选项标识、显示内容和类型，不接受任意文件对象。
     */
    private FormDataOptionDto toOption(FormEditRequest.OptionValue option) {
        FormDataOptionDto mapped = new FormDataOptionDto();
        mapped.setOptionId(option.getOptionId());
        mapped.setContent(option.getContent());
        mapped.setType(option.getType());
        return mapped;
    }
}
