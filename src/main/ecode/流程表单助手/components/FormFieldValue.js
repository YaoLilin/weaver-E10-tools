import React, {useMemo} from 'react';
import {CorsComponent, Input} from '@weapp/ui';
import FieldInfoButton from './FieldInfoButton';
import {getFieldText} from './formDataAdapter';

/**
 * 流程字段的只读展示组件。
 *
 * @param {{field: object, formSdk: object, mode: string, onModeChange: Function, rowIndex: number, showLabel: boolean, showModeButton: boolean}} props 组件参数
 * @returns {React.ReactElement} 字段展示组件
 */
const FormFieldValue = ({
    field,
    formSdk,
    mode,
    onModeChange,
    rowIndex,
    showLabel = true,
    showModeButton = true,
}) => {
    const isComponentMode = mode === 'component';
    const componentDefinition = useMemo(
        () => (isComponentMode ? getComponentDefinition(formSdk, field) : null),
        [field, formSdk, isComponentMode],
    );
    const componentData = useMemo(
        () => (componentDefinition ? getComponentFieldData(formSdk, field, rowIndex, componentDefinition.source) : null),
        [componentDefinition, field, formSdk, rowIndex],
    );
    const componentValue = componentData && componentData.value;
    const hasComponentValue = componentValue !== null && componentValue !== undefined;
    const shouldRenderComponent = isComponentMode && Boolean(componentDefinition) && hasComponentValue;

    return (
        <div className={'appid-${appId} seconddev-ecode flow-form-assistant-field-value'}>
            {(showLabel || showModeButton) && (
                <div className="ffa-field-header">
                    {showLabel && (
                        <span className="ffa-field-label">
                            {field.label}
                        </span>
                    )}
                    {showModeButton && (
                        <span className="ffa-field-actions">
                            <FieldInfoButton field={field} />
                            <button
                                aria-label={isComponentMode ? '切换为文本模式' : '切换为组件模式'}
                                className="appid-${appId} seconddev-ecode ffa-mode-button"
                                onClick={() => onModeChange(field.id, isComponentMode ? 'input' : 'component')}
                                type="button"
                            >
                                <span aria-hidden="true" className="ffa-switch-icon">⇄</span>
                                {isComponentMode ? '组件模式' : '文本模式'}
                            </button>
                        </span>
                    )}
                </div>
            )}
            {shouldRenderComponent ? (
                <CorsComponent
                    app="@weapp/formbuilder"
                    compName="FormSwitch"
                    config={componentDefinition.config}
                    formProps={formSdk.weFormApi.formStore.formProps}
                    isNewDataFormat
                    onBlur={() => {}}
                    onChange={() => {}}
                    rowId={componentData.rowId}
                    value={componentValue}
                />
            ) : (
                <Input inputReadOnly readOnly value={getRawFieldText(formSdk, field, rowIndex)} />
            )}
        </div>
    );
};

/**
 * 为流程字段取得只读组件配置；SDK 无配置时使用后端返回的完整字段配置。
 *
 * @param {object} formSdk 当前流程表单 SDK
 * @param {object} field 后端返回的字段展示模型
 * @returns {{config: object, source: string}|null} 组件配置及来源
 */
function getComponentDefinition(formSdk, field) {
    try {
        const fieldConfig = formSdk.getFieldInfo(field.id);
        if (fieldConfig) {
            return { config: { ...fieldConfig, readOnly: true, disabled: true }, source: 'sdk' };
        }
    } catch (error) {
        console.error('[ecode:${appId}-流程表单助手]:流程表单助手读取字段组件配置失败', { error, fieldId: field.id });
    }
    if (!field.componentKey || !field.componentConfig) {
        return null;
    }
    console.log('[ecode:${appId}-流程表单助手]:流程表单助手使用后端字段组件配置', { fieldId: field.id });
    return {
        config: {
            ...field.componentConfig,
            fieldId: field.id,
            componentKey: field.componentKey,
            readOnly: true,
            disabled: true,
            display: true,
            show: true,
        },
        source: 'backend',
    };
}

/**
 * 文本模式优先展示后端数据库值；后端未回填附件 ID 时读取表单 SDK 原始值。
 *
 * @param {object} formSdk 当前流程表单 SDK
 * @param {object} field 字段展示模型
 * @param {number} rowIndex 明细行号；主表不传
 * @returns {string} 字段原始值文本
 */
function getRawFieldText(formSdk, field, rowIndex) {
    const backendValue = getFieldText(field.value);
    if (backendValue) {
        return backendValue;
    }
    try {
        const rowId = rowIndex === undefined
            ? null
            : formSdk.getDetailRowIdByIndex(field.subFormId, rowIndex);
        if (rowIndex !== undefined && !rowId) {
            return '';
        }
        const fieldMark = rowId === null ? field.id : `${field.id}_${rowId}`;
        const sdkValue = formSdk.getFieldValue(fieldMark);
        return sdkValue === undefined || sdkValue === null ? '' : String(sdkValue);
    } catch (error) {
        console.error('[ecode:${appId}-流程表单助手]:流程表单助手读取字段原始值失败', { error, fieldId: field.id, rowIndex });
        return '';
    }
}

/**
 * 为流程字段取得组件值；布局外字段使用后端接口返回的值。
 *
 * @param {object} formSdk 当前流程表单 SDK
 * @param {object} field 字段展示模型
 * @param {number} rowIndex 明细行号；主表不传
 * @param {string} source 组件配置来源
 * @returns {{rowId: string|undefined, value: object}|null} 组件上下文与字段值
 */
function getComponentFieldData(formSdk, field, rowIndex, source) {
    if (source === 'backend') {
        return getBackendComponentFieldData(field);
    }
    try {
        const rowId = rowIndex === undefined
            ? undefined
            : formSdk.getDetailRowIdByIndex(field.subFormId, rowIndex);
        if (rowIndex !== undefined && !rowId) {
            return null;
        }
        return {
            rowId,
            value: formSdk.weFormApi.formStore.getWidgetData(field.id, rowId),
        };
    } catch (error) {
        console.error('[ecode:${appId}-流程表单助手]:流程表单助手读取字段组件值失败', { error, fieldId: field.id, rowIndex });
        return null;
    }
}

/**
 * 将后端字段值转换为只读字段组件可使用的数据对象。
 *
 * @param {object} field 后端返回的字段展示模型
 * @returns {{rowId: undefined, value: object}} 组件上下文与字段值
 */
function getBackendComponentFieldData(field) {
    const backendValue = field.value || {};
    const content = getFieldText(backendValue);
    const hasBackendOptions = Array.isArray(backendValue.dataOptions);
    const hasOption = backendValue.optionId !== null
        && backendValue.optionId !== undefined
        && backendValue.optionId !== '';
    const fallbackOptions = hasOption
        ? [{ optionId: backendValue.optionId, content: backendValue.displayText || content }]
        : [];
    const dataOptions = hasBackendOptions ? backendValue.dataOptions : fallbackOptions;
    return {
        rowId: undefined,
        value: { ...backendValue, content, dataOptions },
    };
}

export default FormFieldValue;
