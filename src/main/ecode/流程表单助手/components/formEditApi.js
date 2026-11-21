import {devRequest} from '@weapp/ecodesdk';

export const DELETION_PROGRESS_CODE = 409;

/**
 * 使用管理员编辑接口持久化当前确认的增量变更。
 * @param {object} formInfo 当前表单信息
 * @param {object} change 字段或明细变更
 * @returns {Promise<void>} 平台确认保存成功
 */
export async function saveFormChange(formInfo, change) {
    const result = await devRequest({
        url: '/api/secondev/hnweaver/form-assist/edit', method: 'POST',
        data: {formId: String(formInfo.formId), dataId: String(formInfo.dataId), module: formInfo.module, ...change},
    });
    if (!result || result.status !== true || result.data !== true) {
        const error = new Error((result && result.msg) || '平台未确认保存成功');
        error.code = result && result.code;
        throw error;
    }
}

/** 将完整组件值投影为增量保存参数，保留空值以支持清空字段。 */
export function toFieldChange(field, value, row) {
    const options = Array.isArray(value.dataOptions) ? value.dataOptions : [];
    return {
        fieldId: String(field.id), rowId: row ? String(row.rowId) : null,
        dataIndex: row ? row.index : null,
        content: value.content === undefined || value.content === null ? '' : String(value.content),
        dataOptions: options.map(option => ({
            optionId: String(option.optionId), content: option.content || '', type: option.type || null,
        })),
    };
}

/** 使用公开 SDK 把已保存的新值同步到表单布局中的同一字段。 */
export function syncFieldValue(formSdk, field, value, row) {
    const mark = `field${field.id}${row ? '_' + row.rowId : ''}`;
    const options = Array.isArray(value.dataOptions) ? value.dataOptions : [];
    const hasOptions = options.length > 0 || ['Select', 'SelectMultiple', 'RadioBox', 'CheckBox', 'Browser', 'File', 'Image'].includes(field.type);
    const sdkValue = hasOptions ? {specialObj: options.map(option => ({
        id: option.optionId, name: option.content, type: option.type, data: option.optionObj,
    }))} : {value: value.content === undefined || value.content === null ? '' : value.content};
    formSdk.changeFieldValue(mark, sdkValue);
}
