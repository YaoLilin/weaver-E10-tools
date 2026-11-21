package com.weaver.seconddev.hnweaver.common.formassist.domain.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 验证明细行 ID 的 JSON 契约不依赖环境全局 Long 序列化配置。 */
class FormDetailRowDTOTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("超过前端安全整数范围的明细行 ID 必须完整返回字符串")
    void serializesLargeRowIdAsText() throws Exception {
        FormDetailRowDTO row = new FormDetailRowDTO();
        row.setRowId(479144359725827125L);
        row.setDataIndex(1L);
        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(row));
        assertTrue(json.get("rowId").isTextual());
        assertEquals("479144359725827125", json.get("rowId").asText());
        assertTrue(json.get("dataIndex").isNumber());
    }

    @Test
    @DisplayName("空明细行 ID 保持空值且不转换为 null 文本")
    void preservesNullRowId() throws Exception {
        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(new FormDetailRowDTO()));
        assertTrue(json.get("rowId").isNull());
    }
}
