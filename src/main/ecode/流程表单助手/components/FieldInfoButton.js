import React, {useContext, useState} from 'react';
import {Dialog} from '@weapp/ui';
import AssistantDialogLayer from './AssistantDialogLayer';
import CopyValue from './CopyValue';

/**
 * 点击字段信息图标，查看数据库字段名和字段 ID。
 * @param {{field: object}} props 主表字段或明细列的字段定义
 * @returns {React.ReactElement} 信息按钮和只读对话框
 */
const FieldInfoButton = ({field}) => {
    const dialogZIndex = useContext(AssistantDialogLayer);
    const [visible, setVisible] = useState(false);
    return (
        <React.Fragment>
            <button
                aria-label={`查看${field.label}的字段信息`}
                className={'appid-${appId} seconddev-ecode flow-form-assistant-field-info-button'}
                onClick={() => setVisible(true)}
                style={buttonStyle}
                title="字段信息"
                type="button"
            >
                <svg aria-hidden="true" focusable="false" viewBox="0 0 1024 1024" width="16" height="16" fill="currentColor">
                    <path d="M512 148.48c201.728 0 366.08 164.352 366.08 366.08S713.728 880.64 512 880.64 145.92 716.8 145.92 514.56 310.272 148.48 512 148.48M512 89.6c-234.496 0-424.96 190.464-424.96 424.96 0 234.496 189.952 424.96 424.96 424.96 234.496 0 424.96-190.464 424.96-424.96C936.96 280.064 746.496 89.6 512 89.6L512 89.6z" />
                    <path d="M512 762.88c-16.384 0-29.184-13.312-29.184-29.184L482.816 470.016c0-16.384 13.312-29.184 29.184-29.184s29.184 13.312 29.184 29.184l0 263.68C541.184 749.568 528.384 762.88 512 762.88z" />
                    <path d="M512 396.8c-16.384 0-29.184-13.312-29.184-29.184L482.816 308.736c0-16.384 13.312-29.184 29.184-29.184s29.184 13.312 29.184 29.184l0 58.368C541.184 383.488 528.384 396.8 512 396.8z" />
                </svg>
            </button>
            <Dialog
                className={'appid-${appId} seconddev-ecode flow-form-assistant-field-info-dialog'}
                closable
                destroyOnClose
                mask
                maskClosable
                onClose={() => setVisible(false)}
                title={`${field.label} - 字段信息`}
                visible={visible}
                width={480}
                zIndex={dialogZIndex + 1}
            >
                <div style={{padding: '8px 16px 16px', overflowWrap: 'anywhere'}}>
                    {[
                        {label: '字段名称', value: field.label},
                        {label: '数据库字段名', value: field.columnName},
                        {label: '字段 ID', value: field.id},
                    ].map(item => (
                        <div key={item.label} style={infoRowStyle}>
                            <div style={labelStyle}>{item.label}</div>
                            <div style={valueStyle}>
                                <CopyValue label={item.label} value={item.value} emptyText="未提供" />
                            </div>
                        </div>
                    ))}
                </div>
            </Dialog>
        </React.Fragment>
    );
};

const buttonStyle = {
    background: 'transparent', border: 0, color: 'var(--primary, #5d9cec)',
    cursor: 'pointer', fontSize: '16px', padding: '0 6px', flexShrink: 0,
};
const infoRowStyle = {borderBottom: 'var(--border-solid, 1px solid #e5e5e5)', padding: '12px 0'};
const labelStyle = {color: 'var(--secondary-fc, #999999)', fontSize: 'var(--font-size-sm, 12px)'};
const valueStyle = {
    color: 'var(--main-fc, #111111)', fontSize: 'var(--font-size-base, 14px)',
    marginTop: '4px', userSelect: 'text',
};

export default FieldInfoButton;
