package com.weaver.seconddev.hnweaver.common.formassist.domain.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

/**
 * @author 姚礼林
 * @desc 表单字段信息与字段值，主表字段与明细列使用同一结构
 * @date 2026/9/18
 **/
@Data
public class FormFieldDTO {

    /**
     * 字段 ID，字符串形式返回，避免前端大数精度丢失
     */
    private String fieldId;

    /**
     * 字段 dataKey，未铺入表单布局的字段靠它辨识
     */
    private String dataKey;

    /**
     * 字段数据库列名，用于在表单助手中按数据库字段名搜索
     */
    private String columnName;

    /**
     * 字段标题
     */
    private String title;

    /**
     * 控件类型，如 Text、NumberComponent、ImageComponent
     */
    private String componentKey;

    /**
     * 平台字段元数据中的组件配置；字段未配置或配置无效时为 null
     */
    private JsonNode componentConfig;

    /**
     * 平台字段类型，如 Text、Number、Image
     */
    private String type;

    /**
     * 所属明细表 ID，主表字段为 null
     */
    private String subFormId;

    /**
     * 显示顺序
     */
    private Double showOrder;

    /**
     * 是否必填
     */
    private Boolean required;

    /**
     * 是否取到值
     */
    private Boolean hasValue;

    /**
     * 字段值，无值时为 null
     */
    private FormFieldValueDTO value;
}
