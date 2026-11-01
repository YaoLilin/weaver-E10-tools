/**
 * 将后端流程表单助手接口返回的字段库与字段值转换为展示模型。
 *
 * <p>字段清单来自 {@code /api/secondev/hnweaver/form-assist/field-value}，
 * 包含未铺入表单布局的主表字段和明细列；字段值由接口按字段库结构回填。
 */

/**
 * 将后端接口数据转换为抽屉展示模型。
 *
 * @param {object} formData 后端返回的表单字段数据
 * @returns {{mainFields: object[], detailTables: object[]}} 主表字段与明细表模型
 */
export function createFormDataViewModel(formData) {
    const mainFields = Array.isArray(formData.mainFields) ? formData.mainFields : [];
    const detailTables = Array.isArray(formData.detailTables) ? formData.detailTables : [];

    return {
        mainFields: mainFields.map(createFieldViewModel),
        detailTables: detailTables.map((table, index) => createDetailTableViewModel(table, index)),
    };
}

/**
 * 转换主表字段或明细列定义。
 *
 * @param {object} formField 后端字段定义
 * @returns {object} 字段展示数据
 */
function createFieldViewModel(formField) {
    const id = String(formField.fieldId);
    const value = formField.value || { fieldId: id, content: '', displayText: '' };
    return {
        ...formField,
        id,
        columnName: formField.columnName || (formField.componentConfig && formField.componentConfig.columnName) || '',
        label: formField.title || formField.dataKey || id,
        value: { ...value, fieldId: id },
    };
}

/**
 * 转换后端明细表、明细列和行数据。
 *
 * @param {object} detailTable 后端明细表数据
 * @param {number} index 明细表在完整列表中的序号，从 0 开始
 * @returns {object} 明细表展示数据
 */
function createDetailTableViewModel(detailTable, index) {
    const fields = (Array.isArray(detailTable.columns) ? detailTable.columns : []).map(createFieldViewModel);
    const rows = (Array.isArray(detailTable.rows) ? detailTable.rows : []).map(row => {
        const rowFields = (Array.isArray(row.fields) ? row.fields : []).map(createFieldViewModel);
        return {
            index: row.dataIndex,
            rowId: row.rowId,
            values: new Map(rowFields.map(field => [field.id, field])),
        };
    });

    return {
        id: String(detailTable.subFormId),
        title: `明细${index + 1}`,
        fields,
        rows,
        totalRows: detailTable.totalRows,
        truncated: detailTable.truncated,
    };
}

/**
 * 生成只读文本框需要显示的字段数据库原始值。
 *
 * @param {object} fieldValue 后端返回的字段值
 * @returns {string} 字段展示文本
 */
export function getFieldText(fieldValue) {
    if (!fieldValue) {
        return '';
    }
    if (fieldValue.content !== undefined && fieldValue.content !== null && fieldValue.content !== '') {
        return String(fieldValue.content);
    }
    if (fieldValue.optionId !== undefined && fieldValue.optionId !== null && fieldValue.optionId !== '') {
        return String(fieldValue.optionId);
    }
    if (Array.isArray(fieldValue.dataOptions) && fieldValue.dataOptions.length > 0) {
        return fieldValue.dataOptions
            .map(option => option.optionId)
            .filter(id => id !== undefined && id !== null && id !== '')
            .join(',');
    }
    return Array.isArray(fieldValue.files)
        ? fieldValue.files.map(file => file.fileId).filter(id => id !== undefined && id !== null).join(',')
        : '';
}

/**
 * 判断字段定义或字段值是否匹配搜索词。
 *
 * @param {object} field 字段展示模型
 * @param {string} query 已标准化的搜索词
 * @returns {boolean} 是否匹配
 */
function matchesField(field, query) {
    return [
        field.title,
        field.columnName,
        field.dataKey,
        field.id,
        getFieldText(field.value),
        field.value && field.value.displayText,
    ].some(value => String(value === undefined || value === null ? '' : value).toLowerCase().includes(query));
}

/**
 * 按有值状态分组并搜索主表字段。
 *
 * @param {object[]} fields 主表字段展示模型
 * @param {string} keyword 搜索关键字
 * @returns {{valued: object[], empty: object[]}} 匹配的两组字段
 */
export function groupMainFields(fields, keyword) {
    const query = keyword.trim().toLowerCase();
    const matchedFields = query
        ? fields.filter(field => matchesField(field, query))
        : fields;

    return {
        valued: matchedFields.filter(field => field.hasValue === true),
        empty: matchedFields.filter(field => field.hasValue !== true),
    };
}

/**
 * 搜索明细字段时仅保留命中的明细列，表格行保持不变。
 *
 * @param {object[]} tables 明细表展示模型
 * @param {string} keyword 搜索关键字
 * @returns {object[]} 包含匹配列的明细表
 */
export function filterDetailTables(tables, keyword) {
    const query = keyword.trim().toLowerCase();
    if (!query) {
        return tables;
    }
    return tables.map(table => ({
        ...table,
        fields: table.fields.filter(field => matchesField(field, query)
            || table.rows.some(row => {
                const rowField = row.values.get(field.id);
                return rowField && matchesField(rowField, query);
            })),
    })).filter(table => table.fields.length > 0);
}
