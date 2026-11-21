package com.weaver.seconddev.hnweaver.common.formassist.service;

import com.weaver.common.form.data.detail.FormDataDetail;
import com.weaver.common.form.dto.data.FormDataDelDto;
import com.weaver.common.form.dto.data.FormDataDetailDto;
import com.weaver.common.form.dto.data.FormDataOptionDto;
import com.weaver.common.form.metadata.field.FormField;
import com.weaver.common.form.component.base.ComponentType;
import com.weaver.seconddev.hnweaver.common.exception.FormAssistException;
import lombok.Data;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 保存待补记的明细范围、删除前字段值及原生日志记录 ID。
 */
@Data
class FormDeletionSnapshot {
    private Long recordId;
    private Long objectId;
    private String sources;
    private Long sourceId;
    private long lastSentAt;
    private boolean complete;
    private boolean partial;
    private List<FormDataDelDto> rows;
    private List<FormDataDetailDto> values;

    /**
     * 部分删除只记录已确认缺失的行，原请求再次进入时只补记这批行。
     */
    void retainRemovedRows(Set<Long> removed) {
        partial = true;
        rows = rows.stream().filter(row -> removed.contains(row.getRowId())).collect(Collectors.toList());
        values = values.stream().filter(value -> removed.contains(value.getRowId())).collect(Collectors.toList());
    }

    /**
     * 只保留审计所需字段和值，避免把关联记录或附件对象放进缓存。
     */
    static FormDataDetailDto captureValue(FormDataDetail detail, Map<Long, FormField> metadata, Long subFormId) {
        FormField field = FormEditChanges.resolveField(detail, metadata);
        if (field == null) {
            throw new FormAssistException("无法获取待删除明细的字段信息，不能生成表单日志");
        }
        FormDataDetailDto value = new FormDataDetailDto();
        value.setFieldId(field.getId());
        value.setSubFormId(subFormId);
        value.setRowId(detail.getRowId());
        value.setDataIndex(detail.getDataIndex());
        value.setContent(readContent(detail, field));
        if (detail.getDataOptions() != null) {
            value.setDataOptions(detail.getDataOptions().stream()
                    .map(option -> {
                        FormDataOptionDto snapshot = new FormDataOptionDto();
                        snapshot.setOptionId(option.getOptionId());
                        snapshot.setContent(option.getContent());
                        snapshot.setType(option.getType());
                        return snapshot;
                    })
                    .collect(Collectors.toList()));
        }
        return value;
    }

    private static String readContent(FormDataDetail detail, FormField field) {
        Object content = detail.getContentObj();
        if (ComponentType.MultiLanguage.name().equalsIgnoreCase(field.getComponentKey()) && content instanceof Map) {
            Object original = ((Map<?, ?>) content).get("content");
            return original == null ? "" : original.toString();
        }
        return detail.getContent();
    }

    /**
     * 用已校验的表单元数据重建原生日志所需的旧字段值。
     */
    List<FormDataDetail> restoreValues(Map<Long, FormField> metadata) {
        return values.stream().map(value -> {
            FormField field = metadata.get(value.getFieldId());
            if (field == null || field.getSubForm() == null || !value.getSubFormId().equals(field.getSubForm().getId())) {
                throw new FormAssistException("待补记日志的明细字段定义已变化");
            }
            FormDataDetail detail = new FormDataDetail();
            detail.setFormField(field);
            detail.setSubForm(field.getSubForm());
            detail.setRowId(value.getRowId());
            detail.setDataIndex(value.getDataIndex());
            detail.setContent(value.getContent());
            if (value.getDataOptions() != null) {
                detail.setDataOptions(value.getDataOptions().stream()
                        .map(FormDataOptionDto::toFormDataOption)
                        .collect(Collectors.toList()));
            }
            return detail;
        }).collect(Collectors.toList());
    }
}
