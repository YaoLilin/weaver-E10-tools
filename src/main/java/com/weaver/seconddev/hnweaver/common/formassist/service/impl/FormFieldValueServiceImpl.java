package com.weaver.seconddev.hnweaver.common.formassist.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.weaver.common.form.api.rest.FormRestFactory;
import com.weaver.common.form.api.rest.data.FormDataRest;
import com.weaver.common.form.data.FormData;
import com.weaver.common.form.data.detail.FormDataDetail;
import com.weaver.common.form.dto.field.simple.SimpleFormFieldDto;
import com.weaver.common.form.metadata.field.FormField;
import com.weaver.framework.rpc.annotation.RpcReference;
import com.weaver.seconddev.hnweaver.common.constants.RpcGroup;
import com.weaver.seconddev.hnweaver.common.exception.FormAssistException;
import com.weaver.seconddev.hnweaver.common.formassist.domain.FormAssistModule;
import com.weaver.seconddev.hnweaver.common.formassist.domain.dto.FormFieldDTO;
import com.weaver.seconddev.hnweaver.common.formassist.domain.dto.FormFieldDataDTO;
import com.weaver.seconddev.hnweaver.common.formassist.domain.dto.FormFieldValueDTO;
import com.weaver.seconddev.hnweaver.common.formassist.service.FormFieldValueService;
import com.weaver.seconddev.hnweaver.common.util.FieldRpcUtil;
import com.weaver.teams.domain.user.SimpleEmployee;
import com.weaver.teams.security.context.UserContext;
import com.weaver.workflow.core.api.rest.publicapi.WfcFormDataRest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * @author 姚礼林
 * @desc 表单全部字段值查询服务实现。
 *
 * <p>字段清单来自平台字段库 {@code FormFieldRest#listAllFormFieldByFormId}，字段值来自
 * 工作流或 EB 的 {@code getFullFormData}，查询不依赖布局标识，因此布局外字段也能返回。
 * 平台对象不能直接序列化（{@code FormDataDetail#getFormData()} 与 {@code FormData#getDataDetails()}
 * 互相引用），因此统一投影为本包 DTO。
 *
 * @date 2026/9/18
 **/
@Slf4j
@Service
@RequiredArgsConstructor
public class FormFieldValueServiceImpl implements FormFieldValueService {

    private final FieldRpcUtil fieldRpcUtil;

    private final FormFieldComponentConfigMapper componentConfigMapper;

    private final FormFieldValueMapper fieldValueMapper;

    private final FormRestFactory formRestFactory;

    @RpcReference
    private WfcFormDataRest wfcFormDataRest;

    /**
     * 查询表单字段库并组装主表与明细表字段值。
     * @param formId 表单 ID
     * @param dataId 表单数据 ID
     * @return 字段定义及对应值
     * @throws FormAssistException 未登录、非系统管理员或表单数据不存在时抛出
     */
    @Override
    public FormFieldDataDTO getFormFieldData(long formId, long dataId) {
        return getFormFieldData(formId, dataId, "workflow");
    }

    /**
     * 查询工作流或 EB 表单的完整字段库和已保存字段值。
     * @param formId 表单 ID
     * @param dataId 数据 ID
     * @param module 表单类型
     * @return 主表及明细数据
     */
    @Override
    public FormFieldDataDTO getFormFieldData(long formId, long dataId, String module) {
        SimpleEmployee operator = requireSysadminOperator();
        FormAssistModule formModule = FormAssistModule.fromValue(module);
        RpcGroup rpcGroup = formModule.getRpcGroup();
        log.info("查询表单全部字段值，module={}，formId={}，dataId={}，operatorId={}",
                module, formId, dataId, operator.getId());

        List<SimpleFormFieldDto> fieldLibrary = fieldRpcUtil.listFieldLibrary(rpcGroup, formId, operator);
        if (CollUtil.isEmpty(fieldLibrary)) {
            throw new FormAssistException("未查询到表单字段，formId=" + formId);
        }

        FormData formData = loadFormData(formModule, dataId, operator)
                .orElseThrow(() -> new FormAssistException("未查询到表单数据，dataId=" + dataId));

        FormFieldLibrary library = buildFormFieldLibrary(fieldLibrary);
        log.debug("查询表单字段组件元数据，formId={}，operatorId={}", formId, operator.getId());
        // key 为字段 ID，value 为平台字段元数据。
        Map<Long, FormField> fieldMetadata = fieldRpcUtil.getFieldMapByForm(rpcGroup, formId, operator);
        fillFieldMetadata(library, fieldMetadata);
        fillFieldValues(library, formData.getDataDetails());

        FormFieldDataDTO formFieldData = buildFieldData(formId, dataId, library);
        log.info("查询表单全部字段值完成，formId={}，dataId={}，主表字段数={}，明细表数={}",
                formId, dataId, library.mainFields.size(), library.tables.size());
        return formFieldData;
    }

    /**
     * 组装主表与明细表的字段查询结果。
     * @param formId 表单 ID
     * @param dataId 数据 ID
     * @param library 字段库与已填充值
     * @return 助手字段数据
     */
    private FormFieldDataDTO buildFieldData(long formId, long dataId, FormFieldLibrary library) {
        FormFieldDataDTO result = new FormFieldDataDTO();
        result.setFormId(formId);
        result.setDataId(dataId);
        result.setMainFields(library.mainFields);
        result.setDetailTables(library.toDetailTables());
        return result;
    }

    /**
     * 按表单类型读取完整数据，不使用布局过滤接口。
     * @param module 表单类型
     * @param dataId 数据 ID
     * @param operator 当前登录用户
     * @return 平台完整表单数据，查不到时为空
     */
    private Optional<FormData> loadFormData(FormAssistModule module, long dataId, SimpleEmployee operator) {
        if (module == FormAssistModule.WORKFLOW) {
            return Optional.ofNullable(wfcFormDataRest.getFullFormData(dataId, operator));
        }
        FormDataRest formDataRest = formRestFactory.getFormDataRest(module.getRpcGroup().getValue());
        if (formDataRest == null) {
            throw new FormAssistException("未找到 EB 表单数据服务");
        }
        return Optional.ofNullable(formDataRest.getFullFormData(dataId, operator));
    }

    /**
     * 获取调用者并要求其为系统管理员。
     *
     * <p>判定使用 {@code SimpleEmployee#isAdmin()}：该实现比对权限集合中的 {@code ROLE_ADMIN}（系统管理员），
     * 也是 {@code com.weaver.teams.security.user.User} 接口声明的管理员判定方法。不要改用
     * {@code isSysAdmin()}——目标基线中该 getter 读的是 {@code isModuleAdmin} 字段，与 {@code setSysAdmin()}
     * 写入的字段不一致。
     *
     * <p>接口只对系统管理员开放，日志与后续 RPC 全部使用当前登录用户，不提供管理员兜底。
     *
     * @return 当前登录的系统管理员
     */
    private SimpleEmployee requireSysadminOperator() {
        SimpleEmployee currentUser = UserContext.getCurrentUser();
        if (currentUser == null) {
            throw new FormAssistException("接口需登录后调用");
        }
        if (!currentUser.isAdmin()) {
            log.warn("非系统管理员调用表单字段值接口，operatorId={}", currentUser.getId());
            throw new FormAssistException("仅系统管理员可调用该接口");
        }
        return currentUser;
    }

    /**
     * 解析字段库。
     *
     * <p>平台对明细列的返回形态未在文档中约定：可能由明细表条目携带 {@code subFields} 子字段，
     * 也可能每个明细列作为独立条目携带 {@code subFormId}。此处用同一个循环同时处理两种形态，
     * 目标环境实测后收敛为单一路径。
     *
     * @param fieldLibrary 平台返回的字段库
     * @return 字段库视图
     */
    private FormFieldLibrary buildFormFieldLibrary(List<SimpleFormFieldDto> fieldLibrary) {
        FormFieldLibrary library = new FormFieldLibrary();
        for (SimpleFormFieldDto field : fieldLibrary) {
            if (CollUtil.isNotEmpty(field.getSubFields())) {
                for (SimpleFormFieldDto column : field.getSubFields()) {
                    Long subFormId = column.getSubFormId() == null ? field.getSubFormId() : column.getSubFormId();
                    library.addColumn(subFormId, field.getTitle(), column);
                }
            } else if (field.getSubFormId() != null) {
                library.addColumn(field.getSubFormId(), field.getSubFormTitle(), field);
            } else {
                library.addMainField(field);
            }
        }
        return library;
    }

    /**
     * 从字段元数据补齐数据库列名与组件配置；字段库的简化 DTO 不包含这些属性。
     *
     * @param library 当前字段库视图
     * @param fieldMetadata key 为字段 ID，value 为平台字段元数据
     */
    private void fillFieldMetadata(FormFieldLibrary library, Map<Long, FormField> fieldMetadata) {
        boolean missingMetadata = fieldMetadata == null || fieldMetadata.isEmpty();
        if (missingMetadata) {
            log.warn("表单字段元数据为空，无法返回数据库列名和组件配置");
            return;
        }
        for (Map.Entry<Long, FormField> entry : fieldMetadata.entrySet()) {
            FormFieldDTO field = library.fieldMap.get(String.valueOf(entry.getKey()));
            boolean hasFieldMetadata = field != null && entry.getValue() != null;
            if (hasFieldMetadata) {
                field.setColumnName(entry.getValue().getColumnName());
                componentConfigMapper.readComponentConfig(entry.getValue()).ifPresent(field::setComponentConfig);
            }
        }
        log.debug("表单字段组件元数据查询完成，元数据字段数={}", fieldMetadata.size());
    }

    /**
     * 把字段值回填到字段库视图。
     *
     * @param library 字段库视图
     * @param dataDetails 平台返回的字段值明细
     */
    private void fillFieldValues(FormFieldLibrary library, List<FormDataDetail> dataDetails) {
        if (CollUtil.isEmpty(dataDetails)) {
            return;
        }
        for (FormDataDetail dataDetail : dataDetails) {
            String fieldId = resolveFieldId(dataDetail).orElse(null);
            FormFieldDTO field = fieldId == null ? null : library.fieldMap.get(fieldId);
            if (field == null) {
                log.warn("表单数据中存在字段库外的字段值，已忽略，fieldId={}", fieldId);
                continue;
            }
            FormFieldValueDTO value = fieldValueMapper.buildFieldValue(fieldId, dataDetail);
            if (field.getSubFormId() == null) {
                if (field.getValue() == null) {
                    field.setValue(value);
                    field.setHasValue(fieldValueMapper.isValueNotEmpty(value));
                }
                continue;
            }
            library.putRowValue(field.getSubFormId(), dataDetail, fieldId, value);
        }
    }

    /**
     * 解析字段值所属的字段 ID。
     *
     * <p>平台未在文档中约定 {@code getFullFormData} 返回值里 {@code formField} 与 {@code fieldIds}
     * 哪个被填充，此处先取 {@code formField.id}，再退回 {@code fieldIds} 首个元素；实测后收敛为单一路径。
     *
     * @param dataDetail 平台返回的字段值明细
     * @return 字段 ID，无法判定时为空
     */
    private Optional<String> resolveFieldId(FormDataDetail dataDetail) {
        FormField formField = dataDetail.getFormField();
        boolean hasFieldId = formField != null && formField.getId() != null;
        if (hasFieldId) {
            return Optional.of(String.valueOf(formField.getId()));
        }
        List<Long> fieldIds = dataDetail.getFieldIds();
        boolean hasFallbackFieldId = CollUtil.isNotEmpty(fieldIds) && fieldIds.get(0) != null;
        if (hasFallbackFieldId) {
            return Optional.of(String.valueOf(fieldIds.get(0)));
        }
        return Optional.empty();
    }

}
