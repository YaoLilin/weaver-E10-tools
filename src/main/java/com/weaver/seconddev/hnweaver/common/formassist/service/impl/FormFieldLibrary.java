package com.weaver.seconddev.hnweaver.common.formassist.service.impl;

import com.weaver.common.form.data.detail.FormDataDetail;
import com.weaver.common.form.dto.field.simple.SimpleFormFieldDto;
import com.weaver.seconddev.hnweaver.common.formassist.domain.dto.FormDetailTableDTO;
import com.weaver.seconddev.hnweaver.common.formassist.domain.dto.FormFieldDTO;
import com.weaver.seconddev.hnweaver.common.formassist.domain.dto.FormFieldValueDTO;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理主表字段与明细列定义，建立字段索引并按明细表分组。
 * <p>每次查询单独创建，不在请求之间共享字段值。
 */
@Slf4j
public class FormFieldLibrary {

    final List<FormFieldDTO> mainFields = new ArrayList<>();

    /** key 为字段 ID，value 为主表字段或明细列定义。 */
    final Map<String, FormFieldDTO> fieldMap = new LinkedHashMap<>(16);

    /** key 为明细表 ID，value 为该表的列与行聚合模型。 */
    final Map<String, FormDetailTableModel> tables = new LinkedHashMap<>(16);

    /** 将有效主表字段加入清单及字段索引。 */
    public void addMainField(SimpleFormFieldDto field) {
        if (field.getId() == null) {
            log.warn("字段库中存在无字段 ID 的主表条目，已忽略，dataKey={}", field.getDataKey());
            return;
        }
        FormFieldDTO mainField = toField(field);
        mainFields.add(mainField);
        fieldMap.put(mainField.getFieldId(), mainField);
    }

    /** 将有效明细列加入所属明细表及字段索引。 */
    public void addColumn(Long subFormId, String title, SimpleFormFieldDto field) {
        if (subFormId == null) {
            log.warn("字段库中的明细列缺少明细表 ID，已忽略，dataKey={}", field.getDataKey());
            return;
        }
        if (field.getId() == null) {
            log.warn("字段库中存在无字段 ID 的明细列，已忽略，dataKey={}", field.getDataKey());
            return;
        }
        String tableId = String.valueOf(subFormId);
        FormDetailTableModel table = tables.get(tableId);
        if (table == null) {
            table = new FormDetailTableModel(tableId, title);
            tables.put(tableId, table);
        }
        FormFieldDTO column = toField(field);
        column.setSubFormId(tableId);
        table.columns.add(column);
        fieldMap.put(column.getFieldId(), column);
    }

    /** 把明细字段值交由所属明细表按行聚合。 */
    public void putRowValue(String subFormId, FormDataDetail dataDetail, String fieldId, FormFieldValueDTO value) {
        FormDetailTableModel table = tables.get(subFormId);
        if (table == null) {
            log.warn("字段值所属明细表不在字段库中，已忽略，subFormId={}，fieldId={}", subFormId, fieldId);
            return;
        }
        table.putValue(dataDetail.getDataIndex(), dataDetail.getRowId(), fieldId, value);
    }

    /** 生成各明细表的列定义、行值和截断信息。 */
    public List<FormDetailTableDTO> toDetailTables() {
        List<FormDetailTableDTO> detailTables = new ArrayList<>(tables.size());
        for (FormDetailTableModel table : tables.values()) {
            FormDetailTableDTO detailTable = new FormDetailTableDTO();
            detailTable.setSubFormId(table.subFormId);
            detailTable.setTitle(table.title);
            detailTable.setColumns(table.columns);
            detailTable.setRows(table.toRows());
            detailTable.setTotalRows(table.rowCount());
            detailTable.setTruncated(table.isTruncated());
            detailTables.add(detailTable);
        }
        return detailTables;
    }

    /**
     * 平台字段库条目转字段定义。
     *
     * @param field 字段库条目
     * @return 字段定义
     */
    private static FormFieldDTO toField(SimpleFormFieldDto field) {
        FormFieldDTO fieldDTO = new FormFieldDTO();
        fieldDTO.setFieldId(String.valueOf(field.getId()));
        fieldDTO.setDataKey(field.getDataKey());
        fieldDTO.setTitle(field.getTitle());
        fieldDTO.setComponentKey(field.getComponentKey());
        fieldDTO.setType(field.getType());
        fieldDTO.setSubFormId(field.getSubFormId() == null ? null : String.valueOf(field.getSubFormId()));
        fieldDTO.setShowOrder(field.getShowOrder());
        fieldDTO.setRequired(field.getIsRequired());
        fieldDTO.setHasValue(Boolean.FALSE);
        return fieldDTO;
    }

}
