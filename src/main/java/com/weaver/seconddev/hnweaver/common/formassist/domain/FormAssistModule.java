package com.weaver.seconddev.hnweaver.common.formassist.domain;

import com.weaver.seconddev.hnweaver.common.constants.RpcGroup;
import com.weaver.seconddev.hnweaver.common.exception.FormAssistException;

/**
 * 表单助手支持的表单类型及其字段查询服务分组。
 * <p>只允许工作流和 EB 表单，禁止由请求参数指定任意 RPC 分组。
 *
 * @author 姚礼林
 */
public enum FormAssistModule {
    WORKFLOW("workflow", RpcGroup.WORKFLOW),
    EBUILDER_FORM("ebuilderform", RpcGroup.EBUILDER_FORM);

    private final String value;
    private final RpcGroup rpcGroup;

    FormAssistModule(String value, RpcGroup rpcGroup) {
        this.value = value;
        this.rpcGroup = rpcGroup;
    }

    /**
     * 解析前端表单类型。
     * @param value 表单 SDK 的 module 值
     * @return 受支持的表单类型
     */
    public static FormAssistModule fromValue(String value) {
        for (FormAssistModule module : values()) {
            if (module.value.equals(value)) {
                return module;
            }
        }
        throw new FormAssistException("不支持的表单类型：" + value);
    }

    /**
     * 获取字段查询服务分组。
     * @return 与表单类型匹配的 RPC 分组
     */
    public RpcGroup getRpcGroup() {
        return rpcGroup;
    }
}
