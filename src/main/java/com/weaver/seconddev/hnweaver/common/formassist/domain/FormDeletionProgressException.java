package com.weaver.seconddev.hnweaver.common.formassist.domain;

import com.weaver.seconddev.hnweaver.common.exception.FormAssistException;

/**
 * 标记平台已确认保存或已删除部分明细，但回读或日志确认仍需处理。
 */
public class FormDeletionProgressException extends FormAssistException {
    public static final int RESPONSE_CODE = 409;

    /**
     * @param message 已知的删除进度及恢复方式
     */
    public FormDeletionProgressException(String message) {
        super(message);
    }
}
