import React, { forwardRef, useEffect, useImperativeHandle, useRef, useState } from 'react';
import ReactDOM from 'react-dom';
import { Button, Dialog,Help,Alert } from '@weapp/ui';
import {getActionParamKeys, mergeTableData, PARAM_TYPE_OPTIONS} from './actionParamData';

const { message } = Dialog;

const ACTION_PARAM_API = '/api/secondev/esb/action/params';
let localIdSequence = 0;

/** 为新增的参数行分配本页面内唯一的标识。 */
function createLocalId() {
    localIdSequence = (localIdSequence + 1) % 100000;
    return `${Date.now()}${String(localIdSequence).padStart(5, '0')}`;
}

/**
 * 动作流 Action 参数表格扩展组件, 增加获取参数按钮，可自动获取当前 Action 中的输入或输出参数，补充到 Table 内。
 *
 * @param {Object} props 组件属性。
 * @param {React.ComponentType} props.OriginCom 原始表格组件。
 * @param {String} props.groupId Action 的 Spring Bean 名称。
 * @param {Array} props.data 表格现有数据。
 * @param {React.Ref} ref 原始表格组件引用。
 */
const ActionParamTable = forwardRef((props, ref) => {
    const { OriginCom, groupId, data, onChange } = props;
    const originRef = useRef(null);
    const checkedGroupIdRef = useRef(null);
    const [tableData, setTableData] = useState(toDataArray(data));
    const [loading, setLoading] = useState(false);
    const [hasMissingRequiredParams, setHasMissingRequiredParams] = useState(false);
    const [hasMissingOptionalParams, setHasMissingOptionalParams] = useState(false);

    useEffect(() => {
        setTableData(toDataArray(data));
    }, [data]);

    useEffect(() => {
        if (!groupId || checkedGroupIdRef.current === groupId) {
            return;
        }

        const direction = getActionParamDirection(originRef.current);
        if (!direction) {
            return;
        }

        checkedGroupIdRef.current = groupId;
        checkMissingParams(direction, groupId, toDataArray(data))
            .then((missing) => {
                setHasMissingRequiredParams(missing.required);
                setHasMissingOptionalParams(missing.optional);
            })
            .catch((error) => {
                console.error('[ecode:${appId}-动作流Action参数自动获取]:检查 Action 缺失参数失败', error);
            });
    }, [data, groupId]);

    useImperativeHandle(ref, () => originRef.current);

    const handleGetParams = async () => {
        if (!groupId || loading) {
            return;
        }

        setLoading(true);
        try {
            const direction = getActionParamDirection(originRef.current);
            if (!direction) {
                throw new Error('无法根据对话框标题识别输入或输出参数');
            }
            const response = await axios.get(`${ACTION_PARAM_API}/${direction}`, { params: { groupId } });
            const result = response && response.data;
            if (!result || result.status !== true) {
                throw new Error((result && result.msg) || '获取 Action 参数失败');
            }

            const actionParams = result.data;
            if (!Array.isArray(actionParams)) {
                throw new Error('获取 Action 参数失败');
            }

            const nextData = mergeTableData(tableData, actionParams, createLocalId);
            setTableData(nextData);
            setHasMissingRequiredParams(false);
            setHasMissingOptionalParams(false);
            if (typeof onChange === 'function') {
                onChange(nextData);
            }
            message({ type: 'success', content: 'Action 参数获取成功' });
        } catch (error) {
            console.error('[ecode:${appId}-动作流Action参数自动获取]:获取 Action 参数失败', error);
            message({
                type: 'error',
                content: error && error.message ? error.message : '获取 Action 参数失败'
            });
        } finally {
            setLoading(false);
        }
    };

    const handleTableChange = (...args) => {
        if (Array.isArray(args[0])) {
            setTableData(args[0]);
        }
        if (typeof onChange === 'function') {
            onChange(...args);
        }
    };

    const showOptionalParamsHint = hasMissingOptionalParams && !hasMissingRequiredParams;

    return (
        <div>
            {
                groupId ?
                    <>
                        {hasMissingRequiredParams &&
                            <Alert
                                className={'ecode-${appId}'}
                                style={{marginBottom:8}}
                                message={'Action 参数存在必填参数缺失，请点击“获取参数”按钮进行补充'}
                                type="warning"
                            />
                        }
                        {showOptionalParamsHint &&
                            <Alert
                                className={'ecode-${appId}'}
                                style={{marginBottom:8}}
                                message="当前缺少非必需参数，可点击获取参数按钮添加参数"
                                type="info"
                            />
                        }
                        <div style={{display: 'flex', alignItems: 'center'}} className={'ecode-${appId}'}>
                            <Button disabled={loading} onClick={handleGetParams}>获取参数</Button>
                            <div style={{marginLeft:'4px'}} >
                                <Help title={'可自动获取定义的参数，Action 类必需继承 AbstractEsbAction 并且范型参数定义具体对象类型才可以获取参数'}
                                      placement="topLeft"/>
                            </div>
                            <a
                                href={`/sp/custom/1291641870251851777/index?groupId=${encodeURIComponent(groupId)}`}
                                target="_blank"
                                rel="noopener noreferrer"
                                style={{color: '#4285f4', marginLeft: '12px', textDecoration: 'none'}}
                            >
                                查看文档
                            </a>
                        </div>
                    </> :
                    <Alert
                        className={'ecode-${appId}'}
                        style={{marginBottom:8}}
                        message="自动获取参数需在保存动作流后才能使用"
                        type="info"
                    />
            }
            <OriginCom
                {...props}
                className={'ecode-${appId}'}
                ref={originRef}
                data={tableData}
                onChange={handleTableChange}
                paramTypeOptions={PARAM_TYPE_OPTIONS}
                noOverwrite={true}
            />
        </div>
    );
});

function toDataArray(data) {
    return Array.isArray(data) ? data : [];
}

/**
 * 获取当前参数类型，是输入参数还是输出参数
 */
function getActionParamDirection(component) {
    const tableDom = getComponentDom(component);
    const dialogContainer = tableDom && tableDom.closest('.ui-layout-inner-container');
    const title = dialogContainer && dialogContainer.querySelector('.weapp-esb-setting-custom-dlg-title');
    const titleText = title && title.textContent;

    if (titleText && titleText.includes('输出')) {
        return 'output';
    }
    if (titleText && titleText.includes('输入')) {
        return 'input';
    }
    return null;
}

function getComponentDom(component) {
    if (!component) {
        return null;
    }
    if (component.nodeType === 1) {
        return component;
    }
    return ReactDOM.findDOMNode(component);
}

async function checkMissingParams(direction, groupId, tableData) {
    const response = await axios.get(`${ACTION_PARAM_API}/${direction}`, { params: { groupId } });
    const result = response && response.data;
    if (!result || result.status !== true || !Array.isArray(result.data)) {
        throw new Error((result && result.msg) || '获取 Action 参数失败');
    }

    const existingParamKeys = new Set(tableData.map((item) => item && item.paramKey).filter(Boolean));
    const params = getActionParamKeys(result.data);
    return {
        required: params.some((param) => param.required && !existingParamKeys.has(param.key)),
        optional: params.some((param) => !param.required && !existingParamKeys.has(param.key))
    };
}

export default ActionParamTable;
