import React, {useState} from 'react';
import {Pagination} from '@weapp/ui';
import FieldInfoButton from './FieldInfoButton';
import FormFieldValue from './FormFieldValue';

const PAGE_SIZE = 50;

/**
 * 以流程明细表样式展示一张明细表。
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
        console.log('ecodeId:${appId}，流程表单助手已切换明细字段显示模式', { fieldId, mode, tableId: table.id });
    };

    return (
        <section className={'appid-${appId} seconddev-ecode flow-form-assistant-detail-table'} style={sectionStyle}>
            <h3 style={titleStyle}>{table.title}</h3>
            <div style={tableWrapStyle}>
                <table style={tableStyle}>
                    <thead>
                        <tr>
                            <th style={{ ...headerCellStyle, width: '72px' }}>序号</th>
                            {table.fields.map(field => (
                                <th key={field.id} style={headerCellStyle}>
                                    <div style={columnHeaderStyle}>
                                        <span>
                                            {field.label}
                                        </span>
                                        <span style={{display: 'inline-flex', alignItems: 'center', gap: '6px', flexShrink: 0}}>
                                            <FieldInfoButton field={field} />
                                            <button
                                                aria-label={fieldModes[field.id] === 'input' ? '切换为组件模式' : '切换为文本模式'}
                                                className={'appid-${appId} seconddev-ecode flow-form-assistant-mode-button'}
                                                onClick={() => changeFieldMode(
                                                    field.id,
                                                    fieldModes[field.id] === 'input' ? 'component' : 'input',
                                                )}
                                                style={modeButtonStyle}
                                                type="button"
                                            >
                                                <span aria-hidden="true" style={switchIconStyle}>⇄</span>
                                                {fieldModes[field.id] === 'input' ? '文本模式' : '组件模式'}
                                            </button>
                                        </span>
                                    </div>
                                </th>
                            ))}
                        </tr>
                    </thead>
                    <tbody>
                        {visibleRows.map((row, rowIndex) => (
                            <tr key={`${table.id}-${row.index}`}>
                                <td style={{ ...bodyCellStyle, textAlign: 'center' }}>{rowOffset + rowIndex + 1}</td>
                                {table.fields.map(field => (
                                    <td key={field.id} style={bodyCellStyle}>
                                        <FormFieldValue
                                            field={row.values.get(field.id) || field}
                                            formSdk={formSdk}
                                            mode={fieldModes[field.id] || 'component'}
                                            onModeChange={changeFieldMode}
                                            rowIndex={rowOffset + rowIndex + 1}
                                            showLabel={false}
                                            showModeButton={false}
                                        />
                                    </td>
                                ))}
                            </tr>
                        ))}
                        {table.rows.length === 0 && (
                            <tr>
                                <td colSpan={table.fields.length + 1} style={emptyCellStyle}>暂无数据</td>
                            </tr>
                        )}
                    </tbody>
                </table>
            </div>
            {showPagination && (
                <Pagination
                    className={'appid-${appId} seconddev-ecode flow-form-assistant-detail-pagination'}
                    onChange={setPage}
                    pageSize={PAGE_SIZE}
                    showJumper
                    showSizeChanger={false}
                    simple={false}
                    style={{ marginTop: '12px' }}
                    total={table.rows.length}
                    value={currentPage}
                />
            )}
        </section>
    );
};

const sectionStyle = { marginTop: '28px' };
const titleStyle = { color: 'var(--main-fc, #111111)', fontSize: '18px', margin: '0 0 14px' };
const tableWrapStyle = { overflowX: 'auto' };
const tableStyle = { borderCollapse: 'collapse', minWidth: '100%', tableLayout: 'fixed', width: 'max-content' };
const headerCellStyle = {
    background: 'var(--bg-color, #f6f8fb)',
    border: 'var(--border-solid, 1px solid #e0e4ea)',
    color: 'var(--secondary-fc, #657386)',
    fontWeight: 500,
    minWidth: '210px',
    padding: '10px 12px',
    textAlign: 'left',
};
const bodyCellStyle = {
    background: 'var(--base-white, #ffffff)',
    border: 'var(--border-solid, 1px solid #e0e4ea)',
    minWidth: '210px',
    padding: '10px 12px',
    verticalAlign: 'top',
};
const emptyCellStyle = {
    background: 'var(--base-white, #ffffff)',
    border: 'var(--border-solid, 1px solid #e0e4ea)',
    color: 'var(--secondary-fc, #657386)',
    padding: '10px 12px',
    textAlign: 'center',
};
const columnHeaderStyle = { alignItems: 'center', display: 'flex', justifyContent: 'space-between' };
const modeButtonStyle = {
    background: 'transparent',
    border: '1px solid var(--primary, #5d9cec)',
    borderRadius: '3px',
    color: 'var(--primary, #5d9cec)',
    cursor: 'pointer',
    fontSize: '12px',
    lineHeight: '20px',
    padding: '0 6px',
};
const switchIconStyle = { fontSize: '14px', marginRight: '4px' };

export default DetailTable;
