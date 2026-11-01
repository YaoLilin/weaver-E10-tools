package com.weaver.seconddev.hnweaver.common.formassist.service;

import com.weaver.seconddev.hnweaver.common.formassist.domain.dto.FormFieldDataDTO;

/**
 * @author 姚礼林
 * @desc 表单全部字段值查询服务
 * @date 2026/9/18
 **/
public interface FormFieldValueService {

    /**
     * 查询表单全部字段与字段值，含未铺入表单布局的字段。
     *
     * <p>仅系统管理员可调用。
     *
     * @param formId 表单 ID
     * @param dataId 表单数据 ID
     * @return 主表字段与明细表分组
     */
    FormFieldDataDTO getFormFieldData(long formId, long dataId);

    /**
     * 查询指定类型表单的全部字段及字段值，包含布局外字段。
     * @param formId 表单 ID
     * @param dataId 数据 ID
     * @param module 表单类型，仅支持 workflow 和 ebuilderform
     * @return 主表字段与明细表分组
     */
    FormFieldDataDTO getFormFieldData(long formId, long dataId, String module);
}
