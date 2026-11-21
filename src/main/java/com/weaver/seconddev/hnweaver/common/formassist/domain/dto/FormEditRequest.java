package com.weaver.seconddev.hnweaver.common.formassist.domain.dto;

import com.weaver.common.form.metadata.ModuleSource;
import lombok.Data;

import javax.validation.Valid;
import javax.validation.constraints.*;
import java.util.Collections;
import java.util.List;

/**
 * 表单助手单次编辑请求；只包含需要保存的字段或删除的明细行。
 */
@Data
public class FormEditRequest {
    /**
     * 保存操作类型。
     */
    public enum Operation {UPDATE, ADD_ROWS, DELETE_ROWS}

    /**
     * 当前表单 ID。
     */
    @NotNull
    @Positive
    private Long formId;
    /**
     * 当前已保存记录 ID。
     */
    @NotNull
    @Positive
    private Long dataId;
    /**
     * 表单模块，仅支持 workflow 和 ebuilderform。
     */
    @NotBlank
    private String module;
    /**
     * 本次增量操作。
     */
    @NotNull
    private Operation operation;
    /**
     * 明细操作所属表，主表编辑为空。
     */
    @Positive
    private Long subFormId;
    /**
     * 本次需要保存的字段，删除操作为空。
     */
    @NotNull
    @Size(max = 1000)
    @Valid
    private List<FieldChange> fields = Collections.emptyList();
    /**
     * 待删除的当前明细表行 ID。
     */
    @NotNull
    @Size(max = 1000)
    private List<@NotNull @Positive Long> rowIds = Collections.emptyList();

    /**
     * 一个字段的完整新值；明细字段必须携带稳定的明细行 ID。
     */
    @Data
    public static class FieldChange {
        /**
         * 字段 ID。
         */
        @NotNull
        @Positive
        private Long fieldId;
        /**
         * SDK 创建或已有的明细行 ID。
         */
        @Positive
        private Long rowId;
        /**
         * 新增行提供的行索引，最终由服务端分配。
         */
        @Positive
        private Long dataIndex;
        /**
         * 原始内容或选项显示内容。
         */
        @Size(max = 65535)
        private String content;
        /**
         * 字段的新选项列表；空集合用于清空。
         */
        @NotNull
        @Size(max = 1000)
        @Valid
        private List<OptionValue> dataOptions = Collections.emptyList();
    }

    /**
     * 选项的安全保存属性，不接收任意平台对象或文件操作配置。
     */
    @Data
    public static class OptionValue {
        /**
         * 平台选项 ID。
         */
        @NotBlank
        @Size(max = 256)
        private String optionId;
        /**
         * 原始内容或选项显示内容。
         */
        @Size(max = 2048)
        private String content;
        /**
         * 平台选项来源类型。
         */
        private ModuleSource type;
    }
}
