import React, {useState} from 'react';
import {Icon} from '@weapp/ui';
import {copyText} from './copyText';

/**
 * 展示信息值并提供复制按钮及结果提示。
 * @param {{label: string, value: string|number, emptyText: string}} props 信息名称、原始值及空值文案
 * @returns {React.ReactElement} 可复制的信息值
 */
const CopyValue = ({label, value, emptyText = '-'}) => {
    const [feedback, setFeedback] = useState('');
    const hasValue = value !== undefined && value !== null && String(value) !== '';

    /** 复制当前信息的原始值，并报告实际执行结果。 */
    const handleCopy = async () => {
        try {
            await copyText(String(value));
            setFeedback('已复制');
        } catch (error) {
            console.error('[ecode:${appId}-流程表单助手]:复制信息失败', {label, error});
            setFeedback(`复制${label}失败：${error.message || '请手动选择文本复制'}`);
        }
    };

    return (
        <span className={'appid-${appId} seconddev-ecode flow-form-assistant-copy-value'} style={containerStyle}>
            <span style={{overflowWrap: 'anywhere', userSelect: 'text'}}>{hasValue ? String(value) : emptyText}</span>
            {hasValue && (
                <button
                    aria-label={`复制${label}`}
                    className={'appid-${appId} seconddev-ecode flow-form-assistant-copy-button'}
                    onClick={handleCopy}
                    style={buttonStyle}
                    title={`复制${label}`}
                    type="button"
                >
                    <Icon name="Icon-copy" style={{width: 14, height: 14}} />
                </button>
            )}
            <span role="status" style={{fontSize: '12px', color: 'var(--secondary-fc, #999999)'}}>{feedback}</span>
        </span>
    );
};

const containerStyle = {display: 'inline-flex', alignItems: 'center', gap: '6px', flexWrap: 'wrap', maxWidth: '100%'};
const buttonStyle = {
    background: 'transparent', border: 0, color: 'var(--secondary-fc, #999999)',
    cursor: 'pointer', padding: '0 2px', display: 'inline-flex', alignItems: 'center', flexShrink: 0,
};

export default CopyValue;
