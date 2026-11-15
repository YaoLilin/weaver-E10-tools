import React, {useState} from 'react';
import {Pagination, Table} from '@weapp/ui';
import FieldInfoButton from './FieldInfoButton';
import FormFieldValue from './FormFieldValue';

const PAGE_SIZE = 50;

/**
 * 使用公共 Table 展示明细字段，支持字段信息、模式切换及分页。
 *
 * @param {{formSdk: object, table: object}} props 组件参数
 * @returns {React.ReactElement} 明细表组件
 */
const DetailTable = ({ formSdk, table }) => {
    const [fieldModes, setFieldModes] = useState({});
    const [page, setPage] = useState(1);
    const showPagination = table.rows.length > PAGE_SIZE;
    const pageCount = Math.max(1, Math.ceil(table.rows.length / PAGE_SIZE));
    const currentPage = Math.min(page, pageCount);
    const rowOffset = (currentPage - 1) * PAGE_SIZE;
    const visibleRows = table.rows.slice(rowOffset, rowOffset + PAGE_SIZE);

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

    const rows = visibleRows.map((row, index) => ({
        ...row,
        assistantRowKey: `${table.id}-${row.index}`,
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
                    showLabel={false}
                    showModeButton={false}
                />
            ),
        })),
    ];

    return (
        <section className="appid-${appId} seconddev-ecode flow-form-assistant-detail-table ffa-detail-section">
            <h3 className="ffa-detail-title">{table.title}</h3>
            <Table
                bordered
                className="ffa-detail-table"
                columns={columns}
                data={rows}
                emptyLabel="暂无数据"
                needScroll
                rowKey="assistantRowKey"
            />
            {showPagination && (
                <Pagination
                    className="appid-${appId} seconddev-ecode flow-form-assistant-detail-pagination ffa-detail-pagination"
                    onChange={setPage}
                    pageSize={PAGE_SIZE}
                    showJumper
                    showSizeChanger={false}
                    simple={false}
                    total={table.rows.length}
                    value={currentPage}
                />
            )}
        </section>
    );
};

export default DetailTable;
