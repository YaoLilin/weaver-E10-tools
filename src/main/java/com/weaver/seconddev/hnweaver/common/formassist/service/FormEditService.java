package com.weaver.seconddev.hnweaver.common.formassist.service;

import com.weaver.common.form.api.rest.FormRestFactory;
import com.weaver.common.form.api.rest.data.FormDataRest;
import com.weaver.common.form.data.FormData;
import com.weaver.common.form.data.detail.FormDataDetail;
import com.weaver.common.form.dto.data.FormDataDelDto;
import com.weaver.common.form.dto.data.FormDataDto;
import com.weaver.common.form.metadata.field.FormField;
import com.weaver.framework.rpc.annotation.RpcReference;
import com.weaver.seconddev.hnweaver.common.exception.FormAssistException;
import com.weaver.seconddev.hnweaver.common.formassist.domain.FormAssistModule;
import com.weaver.seconddev.hnweaver.common.formassist.domain.FormDeletionProgressException;
import com.weaver.seconddev.hnweaver.common.formassist.domain.dto.FormEditRequest;
import com.weaver.seconddev.hnweaver.common.util.FieldRpcUtil;
import com.weaver.teams.domain.user.SimpleEmployee;
import com.weaver.teams.security.context.UserContext;
import com.weaver.workflow.core.api.rest.publicapi.WfcFormDataRest;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
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
    private final FormDeletionAudit deletionAudit;

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
        if (request.getOperation() == FormEditRequest.Operation.DELETE_ROWS) {
            FormEditChanges.validateDeletionRequest(request);
            return deletionAudit.withRecordLock(request, operator, () -> deleteRows(request, module, rest, operator));
        }
        FormData existing = loadOwnedData(request, module, rest, operator);
        Map<Long, FormField> metadata = requireFieldMetadata(request, module, operator);
        FormDataDto change = createChange(request, existing, rest.getObjectIdByFormData(request.getDataId(), operator));
        change.setDataDetails(FormEditChanges.buildFields(request, metadata, detailsOf(existing)));
        persistChange(request, rest, change, operator);
        return true;
    }

    /**
     * 保留删除前快照，确认所选行已删除后写入日志；同请求可补记已落库的删除。
     */
    private boolean deleteRows(
            FormEditRequest request, FormAssistModule module, FormDataRest rest, SimpleEmployee operator) {
        FormData existing = loadOwnedData(request, module, rest, operator);
        if (existing.getDataDetails() == null) {
            throw new FormAssistException("平台未返回明细行数据，请刷新原表单后重试");
        }
        Map<Long, FormField> metadata = requireFieldMetadata(request, module, operator);
        List<FormDataDetail> details = existing.getDataDetails();
        FormDeletionSnapshot pending = deletionAudit.find(request, operator);
        if (pending != null && !removedRows(pending, existing).isEmpty()) {
            return finishDeletion(request, pending, existing, metadata, operator);
        }

        FormDataDto change = createChange(request, existing, rest.getObjectIdByFormData(request.getDataId(), operator));
        change.setDelRowIds(FormEditChanges.buildDeletions(request, metadata, details));
        // 键为当前明细表 ID，false 表示按行增量保存。
        change.setCoverMap(Collections.singletonMap(request.getSubFormId(), false));
        pending = deletionAudit.prepare(request, existing, change, details, metadata, operator);
        persistChange(request, rest, change, operator);
        FormData verified = readBackDeletion(request, module, rest, operator);
        return finishDeletion(request, pending, verified, metadata, operator);
    }

    /**
     * 补记已确认从表单移除的选中行；部分成功时明确提示剩余行需要重新选择。
     */
    private boolean finishDeletion(
            FormEditRequest request, FormDeletionSnapshot pending, FormData verified,
            Map<Long, FormField> metadata, SimpleEmployee operator) {
        Set<Long> removed = removedRows(pending, verified);
        if (removed.isEmpty()) {
            throw new FormAssistException("删除明细失败：平台保存后仍存在待删除行");
        }
        if (removed.size() != pending.getRows().size()) {
            deletionAudit.retainRemovedRows(request, operator, pending, removed);
        }
        deletionAudit.complete(request, pending, verified, metadata, operator);
        if (pending.isPartial()) {
            throw new FormDeletionProgressException("仅部分选中明细已删除并记录日志，请刷新表单后重新选择剩余行");
        }
        return true;
    }

    private Set<Long> removedRows(FormDeletionSnapshot pending, FormData data) {
        Set<Long> present = data.getDataDetails().stream().map(FormDataDetail::getRowId).collect(Collectors.toSet());
        return pending.getRows().stream().map(FormDataDelDto::getRowId)
                .filter(rowId -> !present.contains(rowId)).collect(Collectors.toSet());
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
     * 按模块读取记录并确认它属于请求指定的表单。
     */
    private FormData loadOwnedData(
            FormEditRequest request, FormAssistModule module, FormDataRest rest, SimpleEmployee operator) {
        Long dataId = request.getDataId();
        FormData existing = module == FormAssistModule.WORKFLOW
                ? wfcFormDataRest.getFullFormData(dataId, operator) : rest.getFullFormData(dataId, operator);
        if (existing == null || existing.getForm() == null
                || !request.getFormId().equals(existing.getForm().getId())) {
            throw new FormAssistException("表单记录不存在或不属于指定表单");
        }
        return existing;
    }

    private List<FormDataDetail> detailsOf(FormData existing) {
        return Optional.ofNullable(existing.getDataDetails()).orElse(Collections.emptyList());
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
     * 只校验本次选中行的回读结果；其他操作可以同时改变未选中行。
     */
    private FormData readBackDeletion(
            FormEditRequest request, FormAssistModule module, FormDataRest rest, SimpleEmployee operator) {
        FormData verified;
        try {
            verified = loadOwnedData(request, module, rest, operator);
        } catch (Exception exception) {
            log.warn("明细删除后的回读失败，dataId={}", request.getDataId(), exception);
            throw new FormDeletionProgressException("平台已返回保存结果，但删除回读失败，请重试相同的删除操作");
        }
        if (verified.getDataDetails() == null) {
            throw new FormDeletionProgressException("平台未返回明细行数据，请重试相同的删除操作核对结果");
        }
        return verified;
    }

    /**
     * 创建限定当前记录的增量保存对象。
     */
    private FormDataDto createChange(FormEditRequest request, FormData existing, Long objectId) {
        FormDataDto change = new FormDataDto();
        change.setFormId(request.getFormId());
        change.setDataId(request.getDataId());
        change.setModule(request.getModule());
        change.setNewLayout(existing.isNewLayout());
        change.setLayoutMultiId(existing.getMultipleLayoutId());
        change.setDataDetails(Collections.emptyList());
        change.setObjectId(objectId);
        return change;
    }
}
