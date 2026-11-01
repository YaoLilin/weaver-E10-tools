package com.weaver.seconddev.hnweaver.common.exception;

/**
 * @author 姚礼林
 * @desc 表单助手接口业务异常，使用此异常时前端可直接展示 message
 * @date 2026/9/18
 **/
public class FormAssistException extends RuntimeException implements ApiExceptionMsgInterface {

    /**
     * 创建表单助手业务异常。
     * @param message 前端可展示的业务错误信息
     */
    public FormAssistException(String message) {
        super(message);
    }
}
