package com.weaver.seconddev.hnweaver.common.formassist.domain.dto;

import lombok.Data;

import java.util.List;

/**
 * @author 姚礼林
 * @desc 表单字段值，白名单投影自平台 FormDataDetail，禁止直接序列化平台对象（存在循环引用）
 * @date 2026/9/18
 **/
@Data
public class FormFieldValueDTO {

    /**
     * 字段 ID
     */
    private String fieldId;

    /**
     * 明细行号，主表字段为 null
     */
    private Long dataIndex;

    /**
     * 明细行 ID，主表字段为 null
     */
    private Long rowId;

    /**
     * 原始值
     */
    private String content;

    /**
     * 选项 ID
     */
    private String optionId;

    /**
     * 平台字段选项；附件 ID 与文件对象主要由这里返回，而非 fileDetails
     */
    private List<FormFieldOptionDTO> dataOptions;

    /**
     * 展示文本，由服务端计算：选项内容 → 文本内容 → 附件文件名 → 原始值
     */
    private String displayText;

    /**
     * 附件、图片字段的文件信息
     */
    private List<FormFileDTO> files;
}
