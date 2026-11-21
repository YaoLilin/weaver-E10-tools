package com.weaver.seconddev.hnweaver.common.formassist.controller;

import com.weaver.common.base.entity.result.WeaResult;
import com.weaver.seconddev.hnweaver.common.exception.FormAssistException;
import com.weaver.seconddev.hnweaver.common.formassist.domain.FormDeletionProgressException;
import com.weaver.seconddev.hnweaver.common.formassist.domain.dto.FormEditRequest;
import com.weaver.seconddev.hnweaver.common.formassist.domain.dto.FormFieldDataDTO;
import com.weaver.seconddev.hnweaver.common.formassist.service.FormFieldValueService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;

import javax.validation.Validation;
import javax.validation.ValidatorFactory;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 流程表单助手接口测试。
 *
 * @author 姚礼林
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("流程表单助手接口测试")
class FormAssistControllerTest {

    private static final long FORM_ID = 1151612316977602563L;
    private static final long DATA_ID = 1316376329162014723L;

    @Mock
    private FormFieldValueService formFieldValueService;

    @Mock
    private com.weaver.seconddev.hnweaver.common.formassist.service.FormEditService formEditService;

    @InjectMocks
    private FormAssistController formAssistController;

    @Test
    @DisplayName("查询表单全部字段值：返回 WeaResult 成功结构并透传参数")
    void shouldReturnFieldValueResult() {
        FormFieldDataDTO formFieldData = new FormFieldDataDTO();
        formFieldData.setFormId(FORM_ID);
        formFieldData.setDataId(DATA_ID);
        when(formFieldValueService.getFormFieldData(FORM_ID, DATA_ID, "workflow")).thenReturn(formFieldData);

        WeaResult<FormFieldDataDTO> result = formAssistController.getFieldValue(FORM_ID, DATA_ID, "workflow");

        assertTrue(result.isStatus());
        assertFalse(result.isFail());
        assertNotNull(result.getData());
        assertEquals(Long.valueOf(FORM_ID), result.getData().getFormId());
        assertEquals(Long.valueOf(DATA_ID), result.getData().getDataId());
        verify(formFieldValueService).getFormFieldData(FORM_ID, DATA_ID, "workflow");
    }

    @Test
    @DisplayName("业务异常返回失败结构和可读信息")
    void shouldPropagateBusinessException() {
        when(formFieldValueService.getFormFieldData(FORM_ID, DATA_ID, "workflow"))
                .thenThrow(new FormAssistException("仅系统管理员可调用该接口"));

        WeaResult<FormFieldDataDTO> result = formAssistController.getFieldValue(FORM_ID, DATA_ID, "workflow");

        assertFalse(result.isStatus());
        assertTrue(result.isFail());
        assertEquals("仅系统管理员可调用该接口", result.getMsg());
    }
    @Test
    @DisplayName("EB 表单类型传递到字段查询服务")
    void shouldForwardEbuilderModule() {
        when(formFieldValueService.getFormFieldData(FORM_ID, DATA_ID, "ebuilderform"))
                .thenReturn(new FormFieldDataDTO());

        WeaResult<FormFieldDataDTO> result = formAssistController.getFieldValue(FORM_ID, DATA_ID, "ebuilderform");

        assertTrue(result.isStatus());
        verify(formFieldValueService).getFormFieldData(FORM_ID, DATA_ID, "ebuilderform");
    }

    @Test
    @DisplayName("保存接口返回平台确认结果")
    void shouldSaveChange() {
        com.weaver.seconddev.hnweaver.common.formassist.domain.dto.FormEditRequest request =
                new com.weaver.seconddev.hnweaver.common.formassist.domain.dto.FormEditRequest();
        when(formEditService.save(request)).thenReturn(true);
        assertTrue(formAssistController.edit(request, org.mockito.Mockito.mock(javax.servlet.http.HttpServletResponse.class)).getData());
    }

    @Test
    @DisplayName("保存失败返回明确错误而不冒充成功")
    void shouldReturnSaveFailure() {
        com.weaver.seconddev.hnweaver.common.formassist.domain.dto.FormEditRequest request =
                new com.weaver.seconddev.hnweaver.common.formassist.domain.dto.FormEditRequest();
        when(formEditService.save(request)).thenThrow(new FormAssistException("字段不属于当前表单"));
        WeaResult<Boolean> result = formAssistController.edit(request, org.mockito.Mockito.mock(javax.servlet.http.HttpServletResponse.class));
        assertFalse(result.isStatus());
        assertEquals("字段不属于当前表单", result.getMsg());
    }

    @Test
    @DisplayName("平台已删除但日志尚未确认时返回可继续处理的业务状态")
    void reportsDeletionProgressSeparatelyFromSaveFailure() {
        FormEditRequest request = new FormEditRequest();
        request.setOperation(FormEditRequest.Operation.DELETE_ROWS);
        when(formEditService.save(request)).thenThrow(new FormDeletionProgressException("日志尚未确认"));

        WeaResult<Boolean> result = formAssistController.edit(request, org.mockito.Mockito.mock(javax.servlet.http.HttpServletResponse.class));

        assertFalse(result.isStatus());
        assertEquals(409, result.getCode());
        assertEquals("日志尚未确认", result.getMsg());
    }

    @Test
    @DisplayName("请求绑定校验拒绝字段数组和选项数组中的空元素")
    void rejectsNullItemsBeforeCallingService() {
        FormEditRequest request = new FormEditRequest();
        request.setFormId(FORM_ID);
        request.setDataId(DATA_ID);
        request.setModule("workflow");
        request.setOperation(FormEditRequest.Operation.UPDATE);
        request.setFields(Collections.singletonList(null));
        try (ValidatorFactory factory = Validation.byDefaultProvider().configure()
                .messageInterpolator(new ParameterMessageInterpolator()).buildValidatorFactory()) {
            assertTrue(factory.getValidator().validate(request).stream()
                    .anyMatch(error -> error.getPropertyPath().toString().startsWith("fields[0]")));

            FormEditRequest.FieldChange field = new FormEditRequest.FieldChange();
            field.setFieldId(300L);
            field.setDataOptions(Collections.singletonList(null));
            request.setFields(Collections.singletonList(field));
            assertTrue(factory.getValidator().validate(request).stream()
                    .anyMatch(error -> error.getPropertyPath().toString().contains("dataOptions[0]")));
        }
    }

}
