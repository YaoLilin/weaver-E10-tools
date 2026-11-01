package com.weaver.seconddev.hnweaver.common.formassist.domain.dto;

import lombok.Data;

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
     * 明细行 ID
     */
    private Long rowId;

    /**
     * 该行的字段列表，字段定义与所属明细表的 columns 一致，附带各字段值
     */
    private List<FormFieldDTO> fields;
}
