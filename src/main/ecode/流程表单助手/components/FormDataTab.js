import React, {useEffect, useMemo, useState} from 'react';
import {devRequest} from '@weapp/ecodesdk';
import DetailTable from './DetailTable';
import FormFieldValue from './FormFieldValue';
import {createFormDataViewModel, filterDetailTables, groupMainFields} from './formDataAdapter';

/**
 * 展示流程主表及全部明细字段的标签页。
 *
 * @param {{formInfo: object, formSdk: object, active: boolean}} props 当前表单基础信息、SDK 和标签页是否选中
 * @returns {React.ReactElement} 表单字段标签页
 */
const FormDataTab = ({formInfo, formSdk, active}) => {
    const [formData, setFormData] = useState(null);
    const [error, setFormDataError] = useState('');
    const [loading, setFormDataLoading] = useState(false);
    useEffect(() => {
        let cancelled = false;
        /** 查询当前表单的完整字段和值，并在标签页卸载后忽略返回结果。 */
        const loadFormData = async formInfo => {
            const { dataId, formId } = formInfo;
            if (!formId || !dataId) {
                setFormData(null);
                setFormDataError('未获取到当前表单字段查询参数');
                console.error('[ecode:${appId}-流程表单助手]:流程表单助手未获取到表单字段查询参数', {
                    dataId,
                    formId,
                });
                return;
            }

            setFormDataLoading(true);
            setFormDataError('');
            try {
                console.debug('[ecode:${appId}-流程表单助手]:流程表单助手请求表单字段', { dataId, formId });
                const responseData = await devRequest({
                    url: '/api/secondev/hnweaver/form-assist/field-value',
                    method: 'GET',
                    params: { dataId, formId, module: formInfo.module },
                });
                if (responseData.status !== true) {
                    throw new Error(responseData.msg || '表单字段请求失败');
                }
                if (cancelled) {
                    return;
                }
                setFormData(responseData.data);
                console.debug('[ecode:${appId}-流程表单助手]:流程表单助手表单字段请求成功', {
                    mainFieldCount: responseData.data.mainFields.length,
                    detailTableCount: responseData.data.detailTables.length,
                    formId,
                });
            } catch (error) {
                if (cancelled) {
                    return;
                }
                setFormData(null);
                setFormDataError(error.message || '表单字段加载失败');
                console.error('[ecode:${appId}-流程表单助手]:流程表单助手请求表单字段失败', error);
            } finally {
                if (!cancelled) {
                    setFormDataLoading(false);
                }
            }
        };

        loadFormData(formInfo);
        return () => { cancelled = true; };
    }, [formInfo]);

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
        console.log('[ecode:${appId}-流程表单助手]:流程表单助手已切换主表字段显示模式', { fieldId, mode });
    };

    if (!active) {
        return null;
    }
    if (loading) {
        return <div className="ffa-message">表单字段加载中...</div>;
    }
    if (error) {
        return <div className="ffa-error-message">{error}</div>;
    }
    if (!viewModel) {
        return <div className="ffa-message">暂无表单字段数据</div>;
    }

    return (
        <div className="appid-${appId} seconddev-ecode flow-form-assistant-form-data ffa-form-data">
            <input
                aria-label="搜索表单字段"
                className="appid-${appId} seconddev-ecode flow-form-assistant-field-search ffa-field-search"
                maxLength={200}
                onChange={event => setSearchKeyword(event.target.value)}
                placeholder="搜索主表或明细字段的名称、数据库字段名、字段 ID 或字段值"
                type="search"
                value={searchKeyword}
            />
            {(!searching || hasMainMatches) && (
                <React.Fragment>
                    <h2 className="ffa-main-title">主表字段</h2>
                    {(!searching || mainFieldGroups.valued.length > 0) && (
                        <section aria-label="有值字段" className="ffa-main-field-section">
                            <h3 className="ffa-group-title">有值字段（{mainFieldGroups.valued.length}）</h3>
                            <MainFieldGrid
                                fields={mainFieldGroups.valued}
                                formSdk={formSdk}
                                modes={mainFieldModes}
                                onModeChange={changeMainFieldMode}
                            />
                        </section>
                    )}
                    {(!searching || mainFieldGroups.empty.length > 0) && (
                        <section aria-label="无值字段" className="ffa-main-field-section">
                            {searching ? (
                                <h3 className="ffa-group-title">无值字段（{mainFieldGroups.empty.length}）</h3>
                            ) : (
                                <button
                                    aria-expanded={showEmptyFields}
                                    className="appid-${appId} seconddev-ecode flow-form-assistant-empty-toggle ffa-empty-toggle"
                                    onClick={() => setEmptyFieldsExpanded(expanded => !expanded)}
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
                <div className="ffa-message">暂无匹配字段</div>
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
        return <div className="ffa-message">暂无匹配字段</div>;
    }
    return (
        <div className="ffa-main-field-grid">
            {fields.map(field => (
                <div key={field.id} className="ffa-main-field-card">
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

export default FormDataTab;
