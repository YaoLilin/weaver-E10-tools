package com.weaver.seconddev.hnweaver.common.formassist.domain.dto;

import lombok.Data;

import java.util.List;

/**
 * @author 姚礼林
 * @desc 表单全部字段与字段值，主表字段与明细表分组返回
 * @date 2026/9/18
 **/
@Data
public class FormFieldDataDTO {

    /**
     * 表单 ID
     */
    private Long formId;

    /**
     * 表单数据 ID
     */
    private Long dataId;

    /**
     * 主表字段，含未铺入表单布局的字段
     */
    private List<FormFieldDTO> mainFields;

    /**
     * 明细表分组，含未铺入表单布局的明细列
     */
    private List<FormDetailTableDTO> detailTables;
}
