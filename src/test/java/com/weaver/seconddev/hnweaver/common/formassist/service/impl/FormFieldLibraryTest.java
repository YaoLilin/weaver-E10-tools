package com.weaver.seconddev.hnweaver.common.formassist.service.impl;

import com.weaver.common.form.dto.field.simple.SimpleFormFieldDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** 验证字段库模型的主表与明细分组、无效条目处理。 */
class FormFieldLibraryTest {
    @Test
    @DisplayName("主表与明细列分别分组并建立字段索引")
    void shouldIndexMainAndDetailFields() {
        FormFieldLibrary library = new FormFieldLibrary();
        SimpleFormFieldDto main = new SimpleFormFieldDto();
        main.setId(1L);
        main.setTitle("主表字段");
        SimpleFormFieldDto column = new SimpleFormFieldDto();
        column.setId(2L);
        library.addMainField(main);
        library.addColumn(10L, "明细表", column);
        assertEquals(1, library.mainFields.size());
        assertEquals(2, library.fieldMap.size());
        assertEquals("10", library.fieldMap.get("2").getSubFormId());
        assertEquals("明细表", library.toDetailTables().get(0).getTitle());
        assertTrue(library.toDetailTables().get(0).getRows().isEmpty());
    }

    @Test
    @DisplayName("字段缺少标识或明细表标识时忽略，不创建无效分组")
    void shouldIgnoreInvalidFields() {
        FormFieldLibrary library = new FormFieldLibrary();
        SimpleFormFieldDto field = new SimpleFormFieldDto();
        library.addMainField(field);
        library.addColumn(10L, "明细表", field);
        field.setId(1L);
        library.addColumn(null, "明细表", field);
        assertTrue(library.mainFields.isEmpty());
        assertTrue(library.fieldMap.isEmpty());
        assertTrue(library.toDetailTables().isEmpty());
    }
}
