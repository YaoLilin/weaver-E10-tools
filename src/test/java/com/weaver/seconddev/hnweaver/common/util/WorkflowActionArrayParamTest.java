package com.weaver.seconddev.hnweaver.common.util;

import com.weaver.seconddev.hnweaver.common.constants.ParamType;
import com.weaver.seconddev.hnweaver.common.domain.dto.ActionParamDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证自动获取 Action 参数时对 Java 数组的识别。
 */
@DisplayName("Action数组参数解析")
class WorkflowActionArrayParamTest {
    @Test
    @DisplayName("附件字符串数组应识别为文本数组")
    void shouldParseStringArray() {
        ActionParamDTO param = WorkflowActionParamParser.parseParams(AttachmentResult.class).get(0);
        assertEquals("attachList", param.getName());
        assertEquals(Boolean.TRUE, param.getArray());
        assertEquals(ParamType.STRING, param.getType());
        assertEquals(String.class.getName(), param.getJavaType());
        assertNull(param.getChildren());
    }

    @Test
    @DisplayName("基本类型数组应保留元素类型且不生成子字段")
    void shouldParsePrimitiveArrays() {
        List<ActionParamDTO> params = WorkflowActionParamParser.parseParams(PrimitiveArrays.class);
        assertEquals(ParamType.NUMBER, params.get(0).getType());
        assertEquals("int", params.get(0).getJavaType());
        assertEquals(ParamType.BOOLEAN, params.get(1).getType());
        for (ActionParamDTO param : params) {
            assertEquals(Boolean.TRUE, param.getArray());
            assertNull(param.getChildren());
        }
    }

    @Test
    @DisplayName("对象数组应解析元素字段")
    void shouldParseObjectArrayChildren() {
        ActionParamDTO param = WorkflowActionParamParser.parseParams(ObjectArrays.class).get(0);
        assertEquals(Boolean.TRUE, param.getArray());
        assertEquals(ParamType.OBJECT, param.getType());
        assertEquals("attachList", param.getChildren().get(0).getName());
        assertEquals(Boolean.TRUE, param.getChildren().get(0).getArray());
    }

    /** 附件解析结果。 */
    static class AttachmentResult {
        private String[] attachList;
    }

    /** 基本类型数组。 */
    static class PrimitiveArrays {
        private int[] counts;
        private boolean[] flags;
    }

    /** 对象数组。 */
    static class ObjectArrays {
        private AttachmentResult[] results;
    }
}
