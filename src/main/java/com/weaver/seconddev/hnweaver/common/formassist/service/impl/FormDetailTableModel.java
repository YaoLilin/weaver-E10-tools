package com.weaver.seconddev.hnweaver.common.formassist.service.impl;

import cn.hutool.core.text.CharSequenceUtil;
import com.weaver.seconddev.hnweaver.common.formassist.domain.dto.FormDetailRowDTO;
import com.weaver.seconddev.hnweaver.common.formassist.domain.dto.FormFieldDTO;
import com.weaver.seconddev.hnweaver.common.formassist.domain.dto.FormFieldValueDTO;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 按明细行索引聚合字段值并生成明细表响应，负责排序与返回行数上限。
 */
@Slf4j
class FormDetailTableModel {
    private static final int MAX_DETAIL_ROWS = 1000;

    final String subFormId;

    final String title;

    final List<FormFieldDTO> columns = new ArrayList<>();

    /** key 为明细行索引，value 为该行的字段值模型。 */
    private final Map<Long, RowModel> rows = new LinkedHashMap<>(16);

    FormDetailTableModel(String subFormId, String title) {
        this.subFormId = subFormId;
        this.title = title;
    }

    /** 按行索引归并字段值，保留首次取得的行 ID。 */
    void putValue(Long dataIndex, Long rowId, String fieldId, FormFieldValueDTO value) {
        RowModel row = rows.get(dataIndex);
        if (row == null) {
            row = new RowModel(dataIndex);
            rows.put(dataIndex, row);
        }
        if (row.rowId == null) {
            row.rowId = rowId;
        }
        row.values.put(fieldId, value);
    }

    /** 按行索引排序并截断返回，行字段使用列定义的副本。 */
    List<FormDetailRowDTO> toRows() {
        List<RowModel> rowModels = new ArrayList<>(rows.values());
        rowModels.sort(Comparator.comparing(RowModel::getDataIndex,
                Comparator.nullsLast(Comparator.<Long>naturalOrder())));
        if (rowModels.size() > MAX_DETAIL_ROWS) {
            log.warn("明细行数超过单表上限，已截断返回，subFormId={}，总行数={}，上限={}",
                    subFormId, rowModels.size(), MAX_DETAIL_ROWS);
            rowModels = rowModels.subList(0, MAX_DETAIL_ROWS);
        }
        List<FormDetailRowDTO> rowList = new ArrayList<>(rowModels.size());
        for (RowModel row : rowModels) {
            FormDetailRowDTO rowDTO = new FormDetailRowDTO();
            rowDTO.setDataIndex(row.dataIndex);
            rowDTO.setRowId(row.rowId);
            List<FormFieldDTO> rowFields = new ArrayList<>(columns.size());
            for (FormFieldDTO column : columns) {
                FormFieldDTO rowField = copyField(column);
                FormFieldValueDTO value = row.values.get(column.getFieldId());
                rowField.setValue(value);
                boolean hasValue = value != null && CharSequenceUtil.isNotBlank(value.getDisplayText());
                rowField.setHasValue(hasValue);
                rowFields.add(rowField);
            }
            rowDTO.setFields(rowFields);
            rowList.add(rowDTO);
        }
        return rowList;
    }

    /** 返回截断前的实际明细行数。 */
    int rowCount() {
        return rows.size();
    }

    /** 判断明细行数是否超过返回上限。 */
    boolean isTruncated() {
        return rows.size() > MAX_DETAIL_ROWS;
    }

    /**
     * 复制字段定义，用于按行生成字段（列定义不能直接携带行值）。
     *
     * @param field 字段定义
     * @return 不含值的字段副本
     */
    private static FormFieldDTO copyField(FormFieldDTO field) {
        FormFieldDTO copy = new FormFieldDTO();
        copy.setFieldId(field.getFieldId());
        copy.setDataKey(field.getDataKey());
        copy.setColumnName(field.getColumnName());
        copy.setTitle(field.getTitle());
        copy.setComponentKey(field.getComponentKey());
        copy.setComponentConfig(field.getComponentConfig());
        copy.setType(field.getType());
        copy.setSubFormId(field.getSubFormId());
        copy.setShowOrder(field.getShowOrder());
        copy.setRequired(field.getRequired());
        return copy;
    }

    /**
     * 明细行视图：一行内各字段的值。
     */
    private static class RowModel {

        private final Long dataIndex;

        private Long rowId;

        /** key 为字段 ID，value 为该行该字段的值。 */
        private final Map<String, FormFieldValueDTO> values = new LinkedHashMap<>(16);

        private RowModel(Long dataIndex) {
            this.dataIndex = dataIndex;
        }

        private Long getDataIndex() {
            return dataIndex;
        }
    }
}
