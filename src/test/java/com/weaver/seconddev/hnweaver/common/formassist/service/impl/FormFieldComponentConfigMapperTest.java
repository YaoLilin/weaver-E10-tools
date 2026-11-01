package com.weaver.seconddev.hnweaver.common.formassist.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.weaver.common.form.metadata.field.FormField;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证布局内外字段的组件配置能够安全地从平台元数据中提取。
 */
@DisplayName("表单字段组件配置提取测试")
class FormFieldComponentConfigMapperTest {

    private final FormFieldComponentConfigMapper mapper = new FormFieldComponentConfigMapper(new ObjectMapper());

    @Test
    @DisplayName("布局内字段优先读取布局明细中的组件配置")
    void shouldPreferLayoutComponentConfig() {
        FormField field = new FormField();
        field.setLayoutDetail("{\"config\":{\"componentKey\":\"Text\",\"name\":\"单行文本\"}}");
        field.setOtherProperties("{\"componentKey\":\"FileComponent\"}");

        Optional<JsonNode> config = mapper.readComponentConfig(field);

        assertTrue(config.isPresent());
        assertEquals("Text", config.get().get("componentKey").asText());
        assertEquals("单行文本", config.get().get("name").asText());
    }

    @Test
    @DisplayName("布局外字段从扩展属性读取组件配置")
    void shouldReadComponentConfigFromOtherProperties() {
        FormField field = new FormField();
        field.setOtherProperties("{\"componentKey\":\"FileComponent\",\"configType\":\"1\",\"sizeType\":\"0\"}");

        Optional<JsonNode> config = mapper.readComponentConfig(field);

        assertTrue(config.isPresent());
        assertEquals("FileComponent", config.get().get("componentKey").asText());
        assertEquals("1", config.get().get("configType").asText());
        assertEquals("0", config.get().get("sizeType").asText());
    }

    @Test
    @DisplayName("布局明细损坏时仍读取有效的扩展属性")
    void shouldFallbackToOtherPropertiesWhenLayoutIsInvalid() {
        FormField field = new FormField();
        field.setLayoutDetail("{");
        field.setOtherProperties("{\"componentKey\":\"Text\"}");

        Optional<JsonNode> config = mapper.readComponentConfig(field);

        assertTrue(config.isPresent());
        assertEquals("Text", config.get().get("componentKey").asText());
    }

    @Test
    @DisplayName("配置不是 JSON 对象或过大时拒绝返回")
    void shouldRejectInvalidOrOversizedConfig() {
        FormField field = new FormField();
        field.setOtherProperties("[]");
        assertFalse(mapper.readComponentConfig(field).isPresent());

        field.setOtherProperties(new String(new char[32769]).replace('\0', 'x'));
        assertFalse(mapper.readComponentConfig(field).isPresent());

        field.setOtherProperties("{\"componentKey\":\"Text\",\"nested\":{\"__proto__\":{}}}");
        assertFalse(mapper.readComponentConfig(field).isPresent());
    }
}
