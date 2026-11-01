package com.weaver.seconddev.hnweaver.common.formassist.domain.dto;

import lombok.Data;

import java.util.List;

/**
 * @author 姚礼林
 * @desc 明细表分组：明细列定义与明细行数据
 * @date 2026/9/18
 **/
@Data
public class FormDetailTableDTO {

    /**
     * 明细表 ID
     */
    private String subFormId;

    /**
     * 明细表名称
     */
    private String title;

    /**
     * 明细列定义，含未铺入表单布局的明细列
     */
    private List<FormFieldDTO> columns;

    /**
     * 明细行数据，每行字段列表与 columns 一一对应
     */
    private List<FormDetailRowDTO> rows;

    /**
     * 明细行总数，即使 rows 被截断也为真实值
     */
    private Integer totalRows;

    /**
     * rows 是否因超过单表行数上限被截断
     */
    private Boolean truncated;
}
