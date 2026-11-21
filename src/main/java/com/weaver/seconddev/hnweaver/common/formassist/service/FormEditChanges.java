package com.weaver.seconddev.hnweaver.common.formassist.service;

import com.weaver.common.form.data.detail.FormDataDetail;
import com.weaver.common.form.dto.data.FormDataDelDto;
import com.weaver.common.form.dto.data.FormDataDetailDto;
import com.weaver.common.form.dto.data.FormDataOptionDto;
import com.weaver.common.form.metadata.field.FormField;
import com.weaver.seconddev.hnweaver.common.exception.FormAssistException;
import com.weaver.seconddev.hnweaver.common.formassist.domain.dto.FormEditRequest;

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
 * 校验表单字段和明细行归属，并构造平台增量写入参数。
 */
final class FormEditChanges {
    private FormEditChanges() {
    }

    /**
     * 校验字段和行的归属，只构造本次需要保存的字段。
     */
    static List<FormDataDetailDto> buildFields(
            FormEditRequest request, Map<Long, FormField> metadata, List<FormDataDetail> details) {
        if (request.getFields().isEmpty() || !request.getRowIds().isEmpty()) {
            throw new FormAssistException("字段保存请求必须包含字段且不能混入删除行");
        }
        Set<String> unique = new HashSet<>();
        Set<Long> existingRows = rowsOf(request.getSubFormId(), details, metadata);
        Map<Long, Long> newRowIndexes = assignNewRowIndexes(request, details, existingRows);
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
            changes.add(toDetail(item, subFormId, newRowIndexes.get(item.getRowId())));
        }
        return changes;
    }

    /**
     * 为本次新增的不同明细行分配递增索引；既有行按行 ID 修改。
     */
    private static Map<Long, Long> assignNewRowIndexes(
            FormEditRequest request, List<FormDataDetail> details, Set<Long> existingRows) {
        if (request.getOperation() != FormEditRequest.Operation.ADD_ROWS) {
            return Collections.emptyMap();
        }
        long nextIndex = details.stream()
                .filter(item -> existingRows.contains(item.getRowId()))
                .map(FormDataDetail::getDataIndex)
                .filter(Objects::nonNull)
                .max(Long::compareTo)
                .orElse(0L);
        // 键为本次新增行 ID，值为服务端分配的行索引。
        Map<Long, Long> indexes = new HashMap<>(16);
        for (FormEditRequest.FieldChange item : request.getFields()) {
            if (!indexes.containsKey(item.getRowId())) {
                indexes.put(item.getRowId(), ++nextIndex);
            }
        }
        return indexes;
    }

    /**
     * 将已经校验归属的字段转换为平台增量保存对象。
     */
    private static FormDataDetailDto toDetail(FormEditRequest.FieldChange item, Long subFormId, Long dataIndex) {
        FormDataDetailDto value = new FormDataDetailDto();
        value.setFieldId(item.getFieldId());
        value.setSubFormId(subFormId);
        value.setRowId(item.getRowId());
        if (dataIndex != null) {
            value.setDataIndex(dataIndex);
        }
        value.setContent(item.getContent());
        value.setDataOptions(item.getDataOptions().stream().map(FormEditChanges::toOption).collect(Collectors.toList()));
        return value;
    }

    /**
     * 校验明细行存在性，防止使用其他表单或明细表的行 ID。
     */
    private static void validateRow(
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
     * 在读取补偿记录之前校验删除请求只包含选中行。
     */
    static void validateDeletionRequest(FormEditRequest request) {
        if (request.getSubFormId() == null || request.getRowIds().isEmpty() || !request.getFields().isEmpty()) {
            throw new FormAssistException("删除请求必须提供当前明细表和待删除行，不能混入字段修改");
        }
    }

    /**
     * 校验选中行均属于当前明细表，并生成平台删除标识。
     */
    static List<FormDataDelDto> buildDeletions(
            FormEditRequest request, Map<Long, FormField> metadata, List<FormDataDetail> details) {
        boolean hasSubForm =
                request.getSubFormId() != null
                        && metadata.values().stream()
                        .anyMatch(field ->
                                field.getSubForm() != null
                                        && request.getSubFormId().equals(field.getSubForm().getId()));
        if (!hasSubForm) {
            throw new FormAssistException("待删除行不存在或不属于当前明细表");
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
    static Set<Long> rowsOf(Long subFormId, List<FormDataDetail> details, Map<Long, FormField> metadata) {
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
    static Optional<Long> resolveSubForm(FormDataDetail detail, Map<Long, FormField> metadata) {
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
    static FormField resolveField(FormDataDetail detail, Map<Long, FormField> metadata) {
        Long fieldId = detail.getFormField() == null ? null : detail.getFormField().getId();
        if (fieldId == null && detail.getFieldIds() != null && !detail.getFieldIds().isEmpty()) {
            fieldId = detail.getFieldIds().get(0);
        }
        return metadata.get(fieldId);
    }

    /**
     * 只映射选项标识、显示内容和类型，不接受任意文件对象。
     */
    private static FormDataOptionDto toOption(FormEditRequest.OptionValue option) {
        FormDataOptionDto mapped = new FormDataOptionDto();
        mapped.setOptionId(option.getOptionId());
        mapped.setContent(option.getContent());
        mapped.setType(option.getType());
        return mapped;
    }
}
