import React, {useContext, useState} from 'react';
import {CorsComponent, Icon} from '@weapp/ui';
import FormEditContext from './FormEditContext';
import ReadonlyFieldValue, {getComponentDefinition, getComponentFieldData} from './ReadonlyFieldValue';
import {saveFormChange, syncFieldValue, toFieldChange} from './formEditApi';

/**
 * 展示可单独编辑和确认保存的主表字段或明细单元格。
 * @param {{field: object, formSdk: object, mode: string, onModeChange: Function, rowIndex: number, showLabel: boolean, showModeButton: boolean, row: object}} props 字段展示参数与明细行编辑上下文
 * @returns {React.ReactElement} 字段及编辑操作
 */
const FormFieldValue = props => {
    const {field, formSdk, row} = props;
    const context = useContext(FormEditContext);
    const [editing, setEditing] = useState(false);
    const [draft, setDraft] = useState(null);
    const [saving, setSaving] = useState(false);
    const [error, setError] = useState('');
    const isNewRow = Boolean(row && row.isNew);
    const editable = editing || isNewRow;
    const definition = editable ? getComponentDefinition(formSdk, field) : null;
    /** 读取当前布局值；明细始终使用稳定行 ID，不依赖显示序号。 */
    const readValue = componentDefinition => {
        const sourceData = componentDefinition && row && componentDefinition.source === 'sdk'
            ? {value: formSdk.weFormApi.formStore.getWidgetData(field.id, row.rowId)}
            : (componentDefinition ? getComponentFieldData(formSdk, field, props.rowIndex, componentDefinition.source) : null);
        return (sourceData && sourceData.value) || field.value || {content: '', dataOptions: []};
    };
    const value = draft || readValue(definition);

    /** 进入字段编辑状态，已有草稿时打开保存确认框。 */
    const handleEdit = () => {
        if (!editing) {
            setDraft(JSON.parse(JSON.stringify(readValue(getComponentDefinition(formSdk, field)))));
            setError('');
            setEditing(true);
            return;
        }
        if (!definition) {
            setError('保存字段失败：未取得可编辑组件配置');
            return;
        }
        context.confirmChange('保存字段确认', saveField);
    };

    /** 用户确认后，仅保存当前字段的草稿值。 */
    const saveField = async () => {
        setSaving(true);
        setError('');
        try {
            await saveFormChange(context.formInfo, {
                operation: 'UPDATE', subFormId: field.subFormId || null,
                fields: [toFieldChange(field, value, row)],
            });
            setEditing(false);
            window.WeFormSDK.showMessage('修改成功', 3, 2);
            context.reload();
            try {
                syncFieldValue(formSdk, field, value, row);
            } catch (syncError) {
                console.error('[ecode:${appId}-流程表单助手]:字段已保存但布局同步失败', syncError);
                setError('字段已保存，但同步原表单失败，请刷新表单后继续操作');
            }
        } catch (exception) {
            console.error('[ecode:${appId}-流程表单助手]:保存字段失败', {fieldId: field.id, exception});
            setError(`保存字段失败：${exception.message}`);
        } finally {
            setSaving(false);
        }
    };

    /** 丢弃助手中的编辑草稿，恢复进入编辑前的布局值，不请求后台或写入 SDK。 */
    const cancelEdit = () => {
        setDraft(null);
        setEditing(false);
        setError('');
    };

    /** 接收 FormSwitch 的新格式完整组件值，新增行交给明细表收集。 */
    const changeDraft = next => {
        setDraft(next);
        if (isNewRow) {
            row.onDraftChange(field, next);
        }
    };
    const action = (!context || context.canPersist !== false) && (
        <button
            aria-label={editing ? `保存${field.label}` : `编辑${field.label}`}
            className="ffa-edit-button"
            disabled={saving}
            onClick={handleEdit}
            title={editing ? '确定保存' : '编辑字段'}
            type="button"
        >
            <Icon name={editing ? 'Icon-correct01' : 'Icon-edit02'} />
        </button>
    );
    const cancelAction = editing ? (
        <button
            aria-label={`取消编辑${field.label}`}
            className="ffa-edit-button"
            disabled={saving}
            onClick={cancelEdit}
            title="取消编辑"
            type="button"
        >
            <Icon name="Icon-error01" />
        </button>
    ) : null;
    const content = editable && definition ? (
        <CorsComponent
            app="@weapp/formbuilder"
            compName="FormSwitch"
            config={{...definition.config, readOnly: false, disabled: false, disableEdit: false}}
            formProps={formSdk.weFormApi.formStore.formProps}
            isNewDataFormat
            onBlur={() => {}}
            onChange={changeDraft}
            rowId={row ? row.rowId : undefined}
            value={value}
            widgetEditable
            widgetReadOnly={false}
        />
    ) : null;
    return (
        <div className={row ? 'ffa-editable-cell' + (editable ? ' is-editing' : '') : 'ffa-editable-field'}>
            <ReadonlyFieldValue {...props} editor={{action, cancel: cancelAction, content}} />
            {row && !isNewRow && action && <span className="ffa-cell-edit-action">{action}{cancelAction}</span>}
            {editable && !definition && <div className="ffa-error-message">无法编辑：未取得字段组件配置</div>}
            {error && <div className="ffa-error-message" role="alert">{error}</div>}
        </div>
    );
};

export default FormFieldValue;
