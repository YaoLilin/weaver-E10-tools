package com.weaver.seconddev.hnweaver.common.formassist.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.text.CharSequenceUtil;
import com.weaver.common.form.data.detail.FileDetail;
import com.weaver.common.form.data.detail.FormDataDetail;
import com.weaver.common.form.data.option.FormDataOption;
import com.weaver.common.form.data.text.FormDataText;
import com.weaver.seconddev.hnweaver.common.formassist.domain.dto.FormFieldValueDTO;
import com.weaver.seconddev.hnweaver.common.formassist.domain.dto.FormFileDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 将平台字段值转换为表单助手响应，统一处理选项、文件与展示文本。
 */
@Component
@RequiredArgsConstructor
class FormFieldValueMapper {
    private static final String TEXT_SEPARATOR = "，";
    private final FormFieldOptionMapper fieldOptionMapper;

    /**
     * 构建字段值 DTO。
     *
     * @param fieldId 字段 ID
     * @param dataDetail 平台返回的字段值明细
     * @return 字段值
     */
    FormFieldValueDTO buildFieldValue(String fieldId, FormDataDetail dataDetail) {
        FormFieldValueDTO value = new FormFieldValueDTO();
        value.setFieldId(fieldId);
        value.setDataIndex(dataDetail.getDataIndex());
        value.setRowId(dataDetail.getRowId());
        value.setContent(dataDetail.getContent());
        value.setOptionId(dataDetail.getOptionId());
        value.setDataOptions(fieldOptionMapper.mapOptions(dataDetail.getDataOptions(), fieldId));
        value.setFiles(buildFiles(dataDetail.getFileDetails()));
        value.setDisplayText(buildDisplayText(dataDetail, value.getFiles()));
        return value;
    }

    /**
     * 判断字段值是否有内容。
     *
     * @param value 字段值
     * @return 展示文本非空时返回 true
     */
    boolean isValueNotEmpty(FormFieldValueDTO value) {
        return CharSequenceUtil.isNotBlank(value.getDisplayText());
    }

    /**
     * 构建附件、图片字段的文件信息，原对象含文件字节内容，必须投影。
     *
     * @param fileDetails 平台返回的文件明细
     * @return 文件信息列表
     */
    private List<FormFileDTO> buildFiles(List<FileDetail> fileDetails) {
        if (CollUtil.isEmpty(fileDetails)) {
            return Collections.emptyList();
        }
        List<FormFileDTO> files = new ArrayList<>(fileDetails.size());
        for (FileDetail fileDetail : fileDetails) {
            FormFileDTO file = new FormFileDTO();
            file.setFileId(fileDetail.getFileId());
            file.setFileName(fileDetail.getFileName());
            file.setFileUrl(fileDetail.getFileUrl());
            files.add(file);
        }
        return files;
    }

    /**
     * 计算展示文本，口径统一为：选项内容 → 文本内容 → 附件文件名 → 原始值。
     *
     * @param dataDetail 平台返回的字段值明细
     * @param files 已投影的文件信息
     * @return 展示文本，无内容时返回空串
     */
    private String buildDisplayText(FormDataDetail dataDetail, List<FormFileDTO> files) {
        String optionText = buildOptionText(dataDetail.getDataOptions());
        if (!optionText.isEmpty()) {
            return optionText;
        }
        FormDataText dataText = dataDetail.getDataText();
        boolean hasTextContent = dataText != null && CharSequenceUtil.isNotBlank(dataText.getContent());
        if (hasTextContent) {
            return dataText.getContent();
        }
        String fileText = buildFileText(files);
        if (!fileText.isEmpty()) {
            return fileText;
        }
        return dataDetail.getContent() == null ? "" : dataDetail.getContent();
    }

    /** 合并非空选项的显示内容，保留平台返回的选项顺序。 */
    private String buildOptionText(List<FormDataOption> options) {
        if (CollUtil.isEmpty(options)) {
            return "";
        }
        StringBuilder text = new StringBuilder();
        for (FormDataOption option : options) {
            boolean emptyOption = option == null || CharSequenceUtil.isBlank(option.getContent());
            if (emptyOption) {
                continue;
            }
            if (text.length() > 0) {
                text.append(TEXT_SEPARATOR);
            }
            text.append(option.getContent());
        }
        return text.toString();
    }

    /** 合并非空文件名，供无文本和选项内容的附件字段展示。 */
    private String buildFileText(List<FormFileDTO> files) {
        StringBuilder text = new StringBuilder();
        for (FormFileDTO file : files) {
            if (CharSequenceUtil.isBlank(file.getFileName())) {
                continue;
            }
            if (text.length() > 0) {
                text.append(TEXT_SEPARATOR);
            }
            text.append(file.getFileName());
        }
        return text.toString();
    }
}
