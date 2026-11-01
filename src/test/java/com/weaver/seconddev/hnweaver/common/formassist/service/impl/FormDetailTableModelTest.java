package com.weaver.seconddev.hnweaver.common.formassist.service.impl;

import com.weaver.seconddev.hnweaver.common.formassist.domain.dto.FormDetailRowDTO;
import com.weaver.seconddev.hnweaver.common.formassist.domain.dto.FormFieldDTO;
import com.weaver.seconddev.hnweaver.common.formassist.domain.dto.FormFieldValueDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;

/** 验证明细模型按行聚合、排序，列定义与行值保持独立。 */
class FormDetailTableModelTest {
    @Test
    @DisplayName("明细行按索引排序，同一行多个字段合并且不污染列定义")
    void shouldGroupAndSortRowsWithoutChangingColumns() {
        FormDetailTableModel table = new FormDetailTableModel("10", "明细表");
        FormFieldDTO column = new FormFieldDTO();
        column.setFieldId("1");
        table.columns.add(column);
        FormFieldValueDTO value = new FormFieldValueDTO();
        value.setDisplayText("明细值");
        table.putValue(2L, 20L, "1", value);
        table.putValue(1L, 10L, "1", value);
        table.putValue(1L, 10L, "2", value);
        List<FormDetailRowDTO> rows = table.toRows();
        assertEquals(2, table.rowCount());
        assertFalse(table.isTruncated());
        assertEquals(Long.valueOf(1L), rows.get(0).getDataIndex());
        assertEquals(Long.valueOf(10L), rows.get(0).getRowId());
        assertTrue(rows.get(0).getFields().get(0).getHasValue());
        assertNull(column.getValue());
        assertNotSame(column, rows.get(0).getFields().get(0));
    }

    @Test
    @DisplayName("超出一千行仅截断返回列表，保留真实总行数")
    void shouldBoundReturnedRows() {
        FormDetailTableModel table = new FormDetailTableModel("10", "明细表");
        for (long index = 1001; index >= 1; index--) {
            table.putValue(index, index, "1", new FormFieldValueDTO());
        }
        assertEquals(1001, table.rowCount());
        assertTrue(table.isTruncated());
        assertEquals(1000, table.toRows().size());
        assertEquals(Long.valueOf(1L), table.toRows().get(0).getDataIndex());
    }
}
