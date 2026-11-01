package com.weaver.seconddev.hnweaver.common.formassist.service.impl;

import com.weaver.common.form.data.detail.FormDataDetail;
import com.weaver.common.form.data.detail.FileDetail;
import com.weaver.seconddev.hnweaver.common.formassist.domain.dto.FormFieldValueDTO;
import com.weaver.workflow.common.cfg.org.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.util.Collections;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

/** 验证字段值转换保留原始值且不泄露文件字节内容。 */
class FormFieldValueMapperTest {
    private final FormFieldValueMapper mapper = new FormFieldValueMapper(
            new FormFieldOptionMapper(mock(UserService.class)));

    @Test
    @DisplayName("数字零保留为有效字段值，空内容标记为无值")
    void shouldPreserveZeroAndHandleEmptyValue() {
        FormDataDetail data = new FormDataDetail();
        data.setContent("0");
        FormFieldValueDTO value = mapper.buildFieldValue("1", data);
        assertEquals("0", value.getContent());
        assertEquals("0", value.getDisplayText());
        assertTrue(mapper.isValueNotEmpty(value));
        data.setContent(null);
        assertFalse(mapper.isValueNotEmpty(mapper.buildFieldValue("1", data)));
    }

    @Test
    @DisplayName("附件只投影文件信息，展示文本使用文件名")
    void shouldProjectFileMetadata() {
        FileDetail file = new FileDetail();
        file.setFileId(7L);
        file.setFileName("附件.txt");
        file.setFileContent(new byte[] {1, 2, 3});
        FormDataDetail data = new FormDataDetail();
        data.setFileDetails(Collections.singletonList(file));
        FormFieldValueDTO value = mapper.buildFieldValue("1", data);
        assertEquals("附件.txt", value.getDisplayText());
        assertEquals(Long.valueOf(7L), value.getFiles().get(0).getFileId());
    }
}
