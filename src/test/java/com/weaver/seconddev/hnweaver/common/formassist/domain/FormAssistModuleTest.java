package com.weaver.seconddev.hnweaver.common.formassist.domain;

import com.weaver.seconddev.hnweaver.common.constants.RpcGroup;
import com.weaver.seconddev.hnweaver.common.exception.FormAssistException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 表单助手模块白名单测试。
 * @author 姚礼林
 */
@DisplayName("表单助手模块白名单")
class FormAssistModuleTest {

    @Test
    @DisplayName("工作流和 EB 表单使用各自的服务分组")
    void shouldResolveSupportedGroups() {
        assertEquals(RpcGroup.WORKFLOW, FormAssistModule.fromValue("workflow").getRpcGroup());
        assertEquals(RpcGroup.EBUILDER_FORM, FormAssistModule.fromValue("ebuilderform").getRpcGroup());
    }

    @Test
    @DisplayName("空值及未知类型均被拒绝")
    void shouldRejectUnsupportedModules() {
        assertThrows(FormAssistException.class, () -> FormAssistModule.fromValue(null));
        assertThrows(FormAssistException.class, () -> FormAssistModule.fromValue(""));
        assertThrows(FormAssistException.class, () -> FormAssistModule.fromValue("ebuildercard"));
    }
}
