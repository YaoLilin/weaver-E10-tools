package com.weaver.seconddev.hnweaver.common.formassist.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.weaver.common.form.metadata.field.FormField;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Iterator;
import java.util.Optional;

/**
 * 从平台表单字段元数据提取字段组件配置，供布局外字段只读展示使用。
 *
 * <p>只读取布局明细中的 {@code config} 或字段扩展属性，不向前端返回整个平台对象。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FormFieldComponentConfigMapper {

    private static final int MAX_CONFIG_LENGTH = 32768;

    private static final int MAX_CONFIG_DEPTH = 16;

    private final ObjectMapper objectMapper;

    /**
     * 读取字段组件配置，优先使用布局明细中的配置；无布局时使用字段扩展属性。
     *
     * @param field 平台字段元数据
     * @return 可用于组件展示的配置；无有效配置时为空
     */
    public Optional<JsonNode> readComponentConfig(FormField field) {
        if (field == null) {
            return Optional.empty();
        }
        Optional<JsonNode> layoutDetail = parseObject(field.getLayoutDetail(), field.getId(), "layoutDetail");
        if (layoutDetail.isPresent()) {
            JsonNode config = layoutDetail.get().get("config");
            boolean objectConfig = config != null && config.isObject();
            if (objectConfig) {
                return Optional.of(config);
            }
        }
        return parseObject(field.getOtherProperties(), field.getId(), "otherProperties");
    }

    /**
     * 解析平台字段配置 JSON，并拒绝过大的配置内容。
     *
     * @param json 配置 JSON 文本
     * @param fieldId 字段 ID
     * @param source 配置来源
     * @return JSON 对象；解析失败时为空
     */
    private Optional<JsonNode> parseObject(String json, Long fieldId, String source) {
        boolean missingJson = json == null || json.trim().isEmpty();
        if (missingJson) {
            return Optional.empty();
        }
        if (json.length() > MAX_CONFIG_LENGTH) {
            log.warn("表单字段组件配置超过大小限制，fieldId={}，source={}，length={}", fieldId, source, json.length());
            return Optional.empty();
        }
        try {
            JsonNode config = objectMapper.readTree(json);
            boolean objectConfig = config != null && config.isObject();
            if (objectConfig) {
                boolean unsafeStructure = hasUnsafeStructure(config, 0);
                if (unsafeStructure) {
                    log.warn("表单字段组件配置包含不安全的键或过深嵌套，fieldId={}，source={}", fieldId, source);
                    return Optional.empty();
                }
                return Optional.of(config);
            }
            log.warn("表单字段组件配置不是 JSON 对象，fieldId={}，source={}", fieldId, source);
        } catch (JsonProcessingException exception) {
            log.warn("表单字段组件配置 JSON 解析失败，fieldId={}，source={}", fieldId, source, exception);
        }
        return Optional.empty();
    }

    /**
     * 拒绝可能污染前端对象原型的键与过深的组件配置。
     *
     * @param node 当前 JSON 节点
     * @param depth 当前嵌套深度
     * @return 存在危险键或超过深度上限时为 true
     */
    private boolean hasUnsafeStructure(JsonNode node, int depth) {
        if (depth > MAX_CONFIG_DEPTH) {
            return true;
        }
        if (node.isArray()) {
            for (JsonNode child : node) {
                boolean unsafeElement = hasUnsafeStructure(child, depth + 1);
                if (unsafeElement) {
                    return true;
                }
            }
        }
        if (node.isObject()) {
            Iterator<String> names = node.fieldNames();
            while (names.hasNext()) {
                String name = names.next();
                boolean unsafeKey = "__proto__".equals(name) || "constructor".equals(name)
                        || "prototype".equals(name);
                boolean unsafeChild = unsafeKey || hasUnsafeStructure(node.get(name), depth + 1);
                if (unsafeChild) {
                    return true;
                }
            }
        }
        return false;
    }
}
