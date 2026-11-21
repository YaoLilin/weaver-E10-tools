package com.weaver.seconddev.hnweaver.common.formassist.domain.dto;

import lombok.Data;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import java.util.List;

/**
 * @author 姚礼林
 * @desc 明细行数据
 * @date 2026/9/18
 **/
@Data
public class FormDetailRowDTO {

    /**
     * 明细行号
     */
    private Long dataIndex;

    /**
     * 明细行 ID；响应按字符串输出，避免浏览器丢失长整型精度。
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long rowId;

    /**
     * 该行的字段列表，字段定义与所属明细表的 columns 一致，附带各字段值
     */
    private List<FormFieldDTO> fields;
}
