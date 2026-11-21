import React from 'react';
import {Button, Dialog} from '@weapp/ui';

/**
 * 写入表单数据前提示用户保存布局中尚未提交的修改。
 * @param {{change: object, onCancel: Function, onConfirm: Function, zIndex: number}} props 待确认操作及弹层回调
 * @returns {React.ReactElement} 保存或删除确认框
 */
const EditConfirmationDialog = ({change, onCancel, onConfirm, zIndex}) => (
    <Dialog
        wrapClassName="app-1305269432046043137 seconddev-ecode"
        closable
        destroyOnClose
        mask
        onClose={onCancel}
        title={change.title}
        visible
        width={520}
        zIndex={zIndex}
        footer={[
            <Button key="cancel" onClick={onCancel}>取消</Button>,
            <Button key="confirm" onClick={onConfirm} type="primary">{change.okText}</Button>,
        ]}
    >
        <p className="ffa-edit-confirmation-warning">
            请确认当前表单无修改内容，修改后，本次您在表单布局中修改的内容将会无效，如有修改内容请先保存表单，再使用表单助手进行修改。
        </p>
    </Dialog>
);

export default EditConfirmationDialog;
