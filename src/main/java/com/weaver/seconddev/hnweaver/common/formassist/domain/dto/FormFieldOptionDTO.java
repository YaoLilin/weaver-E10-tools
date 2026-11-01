package com.weaver.seconddev.hnweaver.common.formassist.domain.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

/**
 * 展示流程表单字段的选项值，包括附件组件所需的文件标识和安全属性。
 *
 * <p>仅包含从平台选项对象白名单提取的属性，不直接序列化平台对象。
 */
@Data
public class FormFieldOptionDTO {

    /** 选项 ID；以字符串返回，避免前端大整数精度丢失。 */
    private String optionId;

    /** 选项显示内容。 */
    private String content;

    /** 选项类型，例如 file。 */
    private String type;

    /** 附件组件使用的文件属性白名单。 */
    private JsonNode optionObj;
}
