import React, {useContext, useRef, useState} from 'react';
import {Icon, Pagination, Table} from '@weapp/ui';
import FormEditContext from './FormEditContext';
import {saveFormChange, syncFieldValue, toFieldChange} from './formEditApi';
import FieldInfoButton from './FieldInfoButton';
import FormFieldValue from './FormFieldValue';

const PAGE_SIZE = 50;
// key 为当前表单 SDK，value 为该 SDK 下按明细表 ID 保存的未确认行草稿。
const rowDrafts = new WeakMap();

/** 按当前表单实例保存新增行草稿，关闭助手再打开时仍能继续确认。 */
function getRowDraft(formSdk, tableId) {
    if (!rowDrafts.has(formSdk)) {
        // key 为明细表 ID，value 为未确认行和各字段完整组件值。
        rowDrafts.set(formSdk, new Map());
    }
    const tables = rowDrafts.get(formSdk);
    if (!tables.has(tableId)) {
        tables.set(tableId, {rows: [], values: new Map()});
    }
    return tables.get(tableId);
}

/**
 * 使用公共 Table 展示明细字段，支持编辑、增删、选择及分页。
 *
 * @param {{formSdk: object, table: object}} props 组件参数
 * @returns {React.ReactElement} 明细表组件
 */
const DetailTable = ({ formSdk, table }) => {
    const context = useContext(FormEditContext);
    const canPersist = (!context || context.canPersist !== false);
    const draftCache = getRowDraft(formSdk, table.id);
    const [pendingRows, setPendingRows] = useState(draftCache.rows);
    const fullFields = table.allFields || table.fields;
    const updatePendingRows = update => setPendingRows(current => {
        const next = typeof update === 'function' ? update(current) : update;
        draftCache.rows = next;
        return next;
    });
    const [selectedRows, setSelectedRows] = useState([]);
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState('');
    const pendingValues = useRef(draftCache.values);
    const persistedIds = new Set(table.rows.map(row => String(row.rowId)));
    const pendingVisibleRows = pendingRows.filter(row => !persistedIds.has(row.rowId));
    const newRows = pendingVisibleRows.filter(row => row.isNew);
    const allRows = [...table.rows, ...pendingVisibleRows];
    const [fieldModes, setFieldModes] = useState({});
    const [page, setPage] = useState(1);
    const showPagination = allRows.length > PAGE_SIZE;
    const pageCount = Math.max(1, Math.ceil(allRows.length / PAGE_SIZE));
    const currentPage = Math.min(page, pageCount);
    const rowOffset = (currentPage - 1) * PAGE_SIZE;
    const visibleRows = allRows.slice(rowOffset, rowOffset + PAGE_SIZE);

    /**
     * 更新指定明细列的显示模式。
     *
     * @param {string} fieldId 字段 ID
     * @param {string} mode 展示模式
     */
    const changeFieldMode = (fieldId, mode) => {
        setFieldModes(currentModes => ({ ...currentModes, [fieldId]: mode }));
        console.log('[ecode:${appId}-流程表单助手]:流程表单助手已切换明细字段显示模式', { fieldId, mode, tableId: table.id });
    };

    /** 添加 SDK 明细草稿行，记录稳定行 ID，不向后台保存空行。 */
    const addRow = async () => {
        setBusy(true);
        setError('');
        try {
            const mark = `field${table.id}`;
            const before = new Set(formSdk.getDetailAllRowIndexStr(mark).split(',').filter(Boolean));
            await formSdk.addDetailRow(mark);
            let ids = [];
            for (let attempt = 0; attempt < 40; attempt += 1) {
                ids = formSdk.getDetailAllRowIndexStr(mark).split(',').filter(Boolean);
                if (ids.some(id => !before.has(id))) { break; }
                await new Promise(resolve => window.setTimeout(resolve, 50));
            }
            const addedId = ids.find(id => !before.has(id));
            if (!addedId) { throw new Error('SDK 未返回新增明细行，请检查原表单'); }
            const index = ids.indexOf(addedId) + 1;
            const values = new Map(fullFields.map(field => [field.id,
                formSdk.weFormApi.formStore.getWidgetData(field.id, addedId) || {content: '', dataOptions: []},
            ]));
            pendingValues.current.set(addedId, values);
            updatePendingRows(current => [...current, {rowId: addedId, index, isNew: true, values: new Map()}]);
            setPage(Math.ceil((allRows.length + 1) / PAGE_SIZE));
        } catch (exception) {
            console.error('[ecode:${appId}-流程表单助手]:添加明细行失败', exception);
            setError(`添加明细行失败：${exception.message}`);
        } finally { setBusy(false); }
    };

    /** 一次提交全部新增草稿行，保存失败时保留输入。 */
    const saveNewRows = async () => {
        setBusy(true);
        setError('');
        try {
            const fields = newRows.flatMap(row => fullFields.map(field =>
                toFieldChange(field, pendingValues.current.get(row.rowId).get(field.id) || {content: ''}, row)));
            await saveFormChange(context.formInfo, {operation: 'ADD_ROWS', subFormId: table.id, fields});
            updatePendingRows(current => current.map(row => ({...row, isNew: false})));
            context.reload();
            try {
                newRows.forEach(row => fullFields.forEach(field =>
                    syncFieldValue(formSdk, field, pendingValues.current.get(row.rowId).get(field.id) || {content: ''}, row)));
            } catch (syncError) {
                console.error('[ecode:${appId}-流程表单助手]:明细已保存但布局同步失败', syncError);
                setError('明细已保存，但同步原表单失败，请刷新表单后继续操作');
            }
        } catch (exception) {
            console.error('[ecode:${appId}-流程表单助手]:保存新增明细失败', exception);
            setError(`保存新增明细失败：${exception.message}`);
        } finally { setBusy(false); }
    };

    /** 确认后只丢弃未入库新增行，保留原有明细。 */
    const cancelNewRows = () => {
        window.WeFormSDK.showConfirm('确认取消所有未保存的新增明细行？', () => {
            setError('');
            try {
                const ids = newRows.map(row => row.rowId);
                formSdk.delDetailRow(`field${table.id}`, ids.join(','));
                updatePendingRows(current => current.filter(row => !ids.includes(row.rowId)));
                ids.forEach(id => pendingValues.current.delete(id));
                setSelectedRows(current => current.filter(id => !ids.includes(id)));
            } catch (exception) {
                console.error('[ecode:${appId}-流程表单助手]:取消新增明细失败', exception);
                setError(`取消新增明细失败：${exception.message}`);
            }
        }, () => {}, {title: '取消新增明细', okText: '确定取消', cancelText: '继续编辑'});
    };

    /** 用户确认后删除所选明细；后台成功后通过 SDK 同步删除布局内行。 */
    const deleteSelected = () => {
        context.confirmChange(`删除选中的 ${selectedRows.length} 行明细？`, async () => {
            setBusy(true);
            setError('');
            try {
                const persisted = selectedRows.filter(id => persistedIds.has(id));
                if (persisted.length > 0) {
                    await saveFormChange(context.formInfo, {
                        operation: 'DELETE_ROWS', subFormId: table.id, rowIds: persisted,
                    });
                }
                const deletedIds = [...selectedRows];
                updatePendingRows(current => current.filter(row => !selectedRows.includes(row.rowId)));
                selectedRows.forEach(id => pendingValues.current.delete(id));
                setSelectedRows([]);
                context.reload();
                try {
                    formSdk.delDetailRow(`field${table.id}`, deletedIds.join(','));
                } catch (syncError) {
                    console.error('[ecode:${appId}-流程表单助手]:明细删除已保存但布局同步失败', syncError);
                    setError('明细删除已保存，但同步原表单失败，请刷新表单后继续操作');
                }
            } catch (exception) {
                console.error('[ecode:${appId}-流程表单助手]:删除明细行失败', exception);
                setError(`删除明细行失败：${exception.message}`);
            } finally { setBusy(false); }
        }, '删除');
    };

    const rows = visibleRows.map((row, index) => ({
        ...row,
        assistantRowKey: String(row.rowId),
        displayIndex: rowOffset + index + 1,
    }));
    const columns = [
        {
            title: '序号',
            dataIndex: 'displayIndex',
            width: 72,
            textAlign: 'center',
            bodyRender: row => row.displayIndex,
        },
        ...table.fields.map(field => ({
            title: (
                <div className="ffa-column-heading">
                    <span>
                        {field.label}
                    </span>
                    <span className="ffa-field-actions">
                        <FieldInfoButton field={field} />
                        <button
                            aria-label={fieldModes[field.id] === 'input' ? '切换为组件模式' : '切换为文本模式'}
                            className="appid-${appId} seconddev-ecode flow-form-assistant-mode-button ffa-detail-mode-button"
                            onClick={() => changeFieldMode(
                                field.id,
                                fieldModes[field.id] === 'input' ? 'component' : 'input',
                            )}
                            type="button"
                        >
                            <span aria-hidden="true" className="ffa-switch-icon">⇄</span>
                            {fieldModes[field.id] === 'input' ? '文本模式' : '组件模式'}
                        </button>
                    </span>
                </div>
            ),
            dataIndex: field.id,
            width: 260,
            className: 'ffa-detail-cell',
            disableBodyRenderSpan: true,
            bodyRender: row => (
                <FormFieldValue
                    field={row.values.get(field.id) || field}
                    formSdk={formSdk}
                    mode={fieldModes[field.id] || 'component'}
                    onModeChange={changeFieldMode}
                    rowIndex={row.displayIndex}
                    row={{...row, onDraftChange: (editedField, value) => {
                        pendingValues.current.get(row.rowId).set(editedField.id, value);
                    }}}
                    showLabel={false}
                    showModeButton={false}
                />
            ),
        })),
    ];

    return (
        <section className="appid-${appId} seconddev-ecode flow-form-assistant-detail-table ffa-detail-section">
            <div className="ffa-detail-toolbar">
                <h3 className="ffa-detail-title">{table.title}</h3>
                {canPersist && (
                    <React.Fragment>
                        <button aria-label="添加明细行" className="ffa-edit-button" disabled={busy} onClick={addRow} type="button">
                            <Icon name="Icon-add-to" />
                        </button>
                        <button aria-label="删除选中明细行" className="ffa-edit-button" disabled={busy || selectedRows.length === 0} onClick={deleteSelected} type="button">
                            <Icon name="Icon-delete" />
                        </button>
                        {newRows.length > 0 && (
                            <React.Fragment>
                                <button
                                    aria-label="保存新增明细行"
                                    className="ffa-edit-button"
                                    disabled={busy}
                                    onClick={() => context.confirmChange('保存新增明细确认', saveNewRows)}
                                    type="button"
                                >
                                    <Icon name="Icon-correct01" />
                                </button>
                                <button aria-label="取消新增明细行" className="ffa-edit-button" disabled={busy} onClick={cancelNewRows} type="button">
                                    <Icon name="Icon-error01" />
                                </button>
                            </React.Fragment>
                        )}
                    </React.Fragment>
                )}
            </div>
            {error && <div className="ffa-error-message" role="alert">{error}</div>}
            <Table
                bordered
                className="ffa-detail-table"
                columns={columns}
                data={rows}
                emptyLabel="暂无数据"
                needScroll
                rowKey="assistantRowKey"
                selection={canPersist ? {selectedRowKeys: selectedRows, onSelect: keys => setSelectedRows(keys.map(String))} : undefined}
            />
            {showPagination && (
                <Pagination
                    className="appid-${appId} seconddev-ecode flow-form-assistant-detail-pagination ffa-detail-pagination"
                    onChange={setPage}
                    pageSize={PAGE_SIZE}
                    showJumper
                    showSizeChanger={false}
                    simple={false}
                    total={allRows.length}
                    value={currentPage}
                />
            )}
        </section>
    );
};

export default DetailTable;
