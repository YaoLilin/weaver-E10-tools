package com.weaver.seconddev.hnweaver.common.formassist.controller;

import javax.validation.Valid;
import javax.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import com.weaver.seconddev.hnweaver.common.formassist.domain.dto.FormEditRequest;
import com.weaver.seconddev.hnweaver.common.formassist.service.FormEditService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.weaver.common.authority.annotation.WeaPermission;
import com.weaver.common.base.entity.result.WeaResult;
import com.weaver.seconddev.hnweaver.common.formassist.domain.dto.FormFieldDataDTO;
import com.weaver.seconddev.hnweaver.common.formassist.service.FormFieldValueService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 流程表单助手接口：查询字段，并保存管理员确认的字段与明细变更。
 *
 * <p>接口位于 /api/secondev 前缀下，需登录后访问；业务上再由服务层校验只有系统管理员可调用。
 *
 * @author 姚礼林
 */
@RestController
@RequestMapping("/api/secondev/hnweaver/form-assist")
@WeaPermission(publicPermission = true)
@RequiredArgsConstructor
@Slf4j
public class FormAssistController {

    private final FormFieldValueService formFieldValueService;
    private final FormEditService formEditService;

    /**
     * 查询表单全部字段值，含未铺入表单布局的字段。
     *
     * @param formId 表单 ID
     * @param dataId 表单数据 ID
     * @param module 表单类型，省略时为 workflow
     * @return 主表字段与明细表分组
     */
    @GetMapping("/field-value")
    public WeaResult<FormFieldDataDTO> getFieldValue(@RequestParam Long formId, @RequestParam Long dataId,
            @RequestParam(defaultValue = "workflow") String module) {
        try {
            return WeaResult.success(formFieldValueService.getFormFieldData(formId, dataId, module));
        } catch (Throwable throwable) {
            log.error("查询表单字段失败，module={}，formId={}，dataId={}", module, formId, dataId, throwable);
            return WeaResult.fail(throwable.getMessage(),throwable);
        }
    }
    /**
     * 保存管理员在表单助手内确认的字段或明细行变更。
     * @param response HTTP 响应，附带编辑实现版本便于确认热部署生效
     * @param request 本次增量变更
     * @return 平台是否确认保存成功
     */
    @PostMapping("/edit")
    public WeaResult<Boolean> edit(@Valid @RequestBody FormEditRequest request, HttpServletResponse response) {
        response.setHeader("X-Form-Assist-Edit-Revision", "delete-selected-rows-v3");
        try {
            boolean saved = formEditService.save(request);
            String message = request.getOperation() == FormEditRequest.Operation.DELETE_ROWS
                    ? "明细删除成功（按选中行增量）" : "修改成功";
            return WeaResult.success(saved, message);
        } catch (Exception exception) {
            log.error("表单助手保存失败，formId={}，dataId={}", request.getFormId(), request.getDataId(), exception);
            return WeaResult.fail(exception.getMessage(), exception);
        }
    }

}
