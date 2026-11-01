import React, {useMemo, useState} from 'react';
import DetailTable from './DetailTable';
import FormFieldValue from './FormFieldValue';
import {createFormDataViewModel, filterDetailTables, groupMainFields} from './formDataAdapter';

/**
 * 展示流程主表及全部明细字段的标签页。
 *
 * @param {{error: string, formData: object, formSdk: object, loading: boolean}} props 组件参数
 * @returns {React.ReactElement} 表单字段标签页
 */
const FormDataTab = ({ error, formData, formSdk, loading }) => {
    const [mainFieldModes, setMainFieldModes] = useState({});
    const [searchKeyword, setSearchKeyword] = useState('');
    const [emptyFieldsExpanded, setEmptyFieldsExpanded] = useState(false);
    const viewModel = useMemo(
        () => (formData ? createFormDataViewModel(formData) : null),
        [formData],
    );
    const mainFieldGroups = useMemo(
        () => groupMainFields(viewModel ? viewModel.mainFields : [], searchKeyword),
        [viewModel, searchKeyword],
    );
    const detailTables = useMemo(
        () => filterDetailTables(viewModel ? viewModel.detailTables : [], searchKeyword),
        [viewModel, searchKeyword],
    );
    const searching = searchKeyword.trim().length > 0;
    const hasMainMatches = mainFieldGroups.valued.length > 0 || mainFieldGroups.empty.length > 0;
    const showEmptyFields = emptyFieldsExpanded || searching;

    /**
     * 更新指定主表字段的显示模式。
     *
     * @param {string} fieldId 字段 ID
     * @param {string} mode 展示模式
     */
    const changeMainFieldMode = (fieldId, mode) => {
        setMainFieldModes(currentModes => ({ ...currentModes, [fieldId]: mode }));
        console.log('ecodeId:${appId}，流程表单助手已切换主表字段显示模式', { fieldId, mode });
    };

    if (loading) {
        return <div style={messageStyle}>表单字段加载中...</div>;
    }
    if (error) {
        return <div style={{ ...messageStyle, color: 'var(--danger, #ff4d4f)' }}>{error}</div>;
    }
    if (!viewModel) {
        return <div style={messageStyle}>暂无表单字段数据</div>;
    }

    return (
        <div className={'appid-${appId} seconddev-ecode flow-form-assistant-form-data'} style={containerStyle}>
            <input
                aria-label="搜索表单字段"
                className={'appid-${appId} seconddev-ecode flow-form-assistant-field-search'}
                maxLength={200}
                onChange={event => setSearchKeyword(event.target.value)}
                placeholder="搜索主表或明细字段的名称、数据库字段名、字段 ID 或字段值"
                style={searchInputStyle}
                type="search"
                value={searchKeyword}
            />
            {(!searching || hasMainMatches) && (
                <React.Fragment>
                    <h2 style={mainTitleStyle}>主表字段</h2>
                    {(!searching || mainFieldGroups.valued.length > 0) && (
                        <section aria-label="有值字段" style={mainFieldSectionStyle}>
                            <h3 style={groupTitleStyle}>有值字段（{mainFieldGroups.valued.length}）</h3>
                            <MainFieldGrid
                                fields={mainFieldGroups.valued}
                                formSdk={formSdk}
                                modes={mainFieldModes}
                                onModeChange={changeMainFieldMode}
                            />
                        </section>
                    )}
                    {(!searching || mainFieldGroups.empty.length > 0) && (
                        <section aria-label="无值字段" style={mainFieldSectionStyle}>
                            {searching ? (
                                <h3 style={groupTitleStyle}>无值字段（{mainFieldGroups.empty.length}）</h3>
                            ) : (
                                <button
                                    aria-expanded={showEmptyFields}
                                    className={'appid-${appId} seconddev-ecode flow-form-assistant-empty-toggle'}
                                    onClick={() => setEmptyFieldsExpanded(expanded => !expanded)}
                                    style={emptyToggleStyle}
                                    type="button"
                                >
                                    {showEmptyFields ? '收起' : '展开'}无值字段（{mainFieldGroups.empty.length}）
                                </button>
                            )}
                            {showEmptyFields && (
                                <MainFieldGrid
                                    fields={mainFieldGroups.empty}
                                    formSdk={formSdk}
                                    modes={mainFieldModes}
                                    onModeChange={changeMainFieldMode}
                                />
                            )}
                        </section>
                    )}
                </React.Fragment>
            )}
            {detailTables.map(table => (
                <DetailTable formSdk={formSdk} key={table.id} table={table} />
            ))}
            {searching && !hasMainMatches && detailTables.length === 0 && (
                <div style={messageStyle}>暂无匹配字段</div>
            )}
        </div>
    );
};

/**
 * 展示一组主表字段卡片。
 *
 * @param {{fields: object[], formSdk: object, modes: object, onModeChange: Function}} props 组件参数
 * @returns {React.ReactElement} 字段网格
 */
const MainFieldGrid = ({ fields, formSdk, modes, onModeChange }) => {
    if (fields.length === 0) {
        return <div style={messageStyle}>暂无匹配字段</div>;
    }
    return (
        <div style={mainFieldGridStyle}>
            {fields.map(field => (
                <div key={field.id} style={mainFieldCardStyle}>
                    <FormFieldValue
                        field={field}
                        formSdk={formSdk}
                        mode={modes[field.id] || 'component'}
                        onModeChange={onModeChange}
                    />
                </div>
            ))}
        </div>
    );
};

const containerStyle = { padding: '16px' };
const messageStyle = { color: 'var(--secondary-fc, #999999)', padding: '16px' };
const mainTitleStyle = { color: 'var(--main-fc, #111111)', fontSize: '18px', margin: '0 0 14px' };
const searchInputStyle = {
    border: 'var(--border-solid, 1px solid #e5e5e5)',
    borderRadius: 'var(--border-radius-base, 4px)',
    color: 'var(--main-fc, #111111)',
    boxSizing: 'border-box',
    display: 'block',
    fontSize: '13px',
    marginBottom: '18px',
    padding: '7px 10px',
    width: '100%',
};
const mainFieldSectionStyle = { marginBottom: '18px' };
const groupTitleStyle = { color: 'var(--secondary-fc, #666666)', fontSize: '14px', margin: '0 0 10px' };
const emptyToggleStyle = {
    background: 'transparent',
    border: 0,
    color: 'var(--primary, #5d9cec)',
    cursor: 'pointer',
    fontSize: '14px',
    marginBottom: '10px',
    padding: 0,
};
const mainFieldGridStyle = {
    display: 'grid',
    gap: '12px',
    gridTemplateColumns: 'repeat(auto-fit, minmax(230px, 1fr))',
};
const mainFieldCardStyle = {
    background: 'var(--base-white, #ffffff)',
    border: 'var(--border-solid, 1px solid #e5e5e5)',
    borderRadius: 'var(--border-radius-base, 4px)',
    minWidth: 0,
    padding: '12px',
};

export default FormDataTab;
