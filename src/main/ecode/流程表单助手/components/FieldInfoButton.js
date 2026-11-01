import React, {useState} from 'react';
import {Dialog, Icon} from '@weapp/ui';

/**
 * 点击字段信息图标，查看数据库字段名和字段 ID。
 * @param {{field: object}} props 主表字段或明细列的字段定义
 * @returns {React.ReactElement} 信息按钮和只读对话框
 */
const FieldInfoButton = ({field}) => {
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
                <Icon name="Icon-tips-o" style={{width: 16, height: 16}} />
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
            >
                <dl style={{padding: '16px 24px', overflowWrap: 'anywhere'}}>
                    <dt>数据库字段名</dt>
                    <dd style={valueStyle}>{field.columnName || '未提供'}</dd>
                    <dt>字段 ID</dt>
                    <dd style={valueStyle}>{field.id}</dd>
                </dl>
            </Dialog>
        </React.Fragment>
    );
};

const buttonStyle = {
    background: 'transparent', border: 0, color: 'var(--primary, #5d9cec)',
    cursor: 'pointer', fontSize: '16px', padding: '0 6px', flexShrink: 0,
};
const valueStyle = {margin: '8px 0 16px', userSelect: 'text'};

export default FieldInfoButton;
