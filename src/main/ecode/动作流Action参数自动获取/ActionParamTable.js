import React, { forwardRef, useEffect, useImperativeHandle, useRef, useState } from 'react';
import ReactDOM from 'react-dom';
import { Button, Dialog,Help,Alert } from '@weapp/ui';

const { message } = Dialog;

const ACTION_PARAM_API = '/api/secondev/esb/action/params';
let localIdSequence = 0;

const PARAM_TYPE_OPTIONS = [
    { id: 'json', content: 'JSON' },
    { id: 'xml', content: 'XML' },
    { id: 'xml_string', content: 'XML文本' },
    { id: 'string', content: '文本' },
    { id: 'int', content: '整数' },
    { id: 'double', content: '浮点数' },
    { id: 'datetime', content: '日期时间' },
    { id: 'date', content: '日期' },
    { id: 'time', content: '时间' },
    { id: 'boolean', content: '布尔型' },
    { id: 'binary', content: '字节流' }
];

const ACTION_TYPE_TO_PARAM_TYPE = {
    STRING: 'string',
    BOOLEAN: 'boolean',
    OBJECT: 'json'
};

const JAVA_NUMBER_TYPE_TO_PARAM_TYPE = {
    'java.lang.Integer': 'int',
    'java.lang.Long': 'int',
    'int': 'int',
    'long': 'int',
    'java.lang.Float': 'double',
    'java.lang.Double': 'double',
    'java.math.BigDecimal': 'double',
    'float': 'double',
    'double': 'double'
};

const PARAM_TYPE_OPTION_MAP = PARAM_TYPE_OPTIONS.reduce((result, item) => {
    result[item.id] = item;
    return result;
}, {});

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

            const nextData = mergeTableData(tableData, actionParams);
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

/** 获取必填及非必填参数的完整路径，包含数组和嵌套子参数。 */
function getActionParamKeys(actionParams, parentKey = '') {
    return actionParams.flatMap((param) => {
        const paramKey = `${parentKey}${param.name}${param.array ? '[]' : ''}`;
        const childParamKeys = Array.isArray(param.children) ? getActionParamKeys(param.children, `${paramKey}.`) : [];

        return [
            { key: paramKey, required: param.required === true },
            ...childParamKeys
        ];
    });
}

function mergeTableData(currentData, actionParams) {
    const data = currentData.slice();
    actionParams.forEach((param) => mergeActionParam(data, param, '', null));
    return data;
}

function mergeActionParam(data, actionParam, parentKey, parentId) {
    const param = toTableParam(actionParam, parentKey);
    const hasChildren = Array.isArray(actionParam.children) && actionParam.children.length > 0;
    const index = findTableParamIndex(data, param, parentId);
    let tableParam;

    if (index === -1) {
        tableParam = createTableParam(param, data.length, hasChildren, parentId);
        data.push(tableParam);
    } else {
        tableParam = {
            ...data[index],
            ...getTableParamInfo(param, hasChildren, parentId)
        };
        if (!tableParam.id) {
            tableParam.id = createLocalId();
        }
        data[index] = tableParam;
    }

    if (hasChildren) {
        actionParam.children.forEach((child) => {
            mergeActionParam(data, child, `${param.paramKey}.`, tableParam.id);
        });
    }
}

function toTableParam(param, parentKey) {
    const isArray = param.array;
    const paramType = getTableParamType(param.type, param.javaType);
    const option = PARAM_TYPE_OPTION_MAP[paramType];
    const desc = buildParamDescription(param);

    return {
        paramKey: `${parentKey}${param.name}${isArray ? '[]' : ''}`,
        paramName: param.name,
        showName: param.showName || param.name,
        desc,
        description: desc,
        paramType,
        paramTypeString: option.content,
        arrs: isArray === true,
        required: param.required === true
    };
}

function buildParamDescription(param) {
    const desc = param.desc || '';
    if (param.defaultValue === undefined || param.defaultValue === null || param.defaultValue === '') {
        return desc;
    }

    const defaultValueDesc = `默认值：${param.defaultValue}`;
    return desc ? `${desc} ${defaultValueDesc}` : defaultValueDesc;
}

function getTableParamType(actionType, javaType) {
    if (actionType === 'NUMBER') {
        return JAVA_NUMBER_TYPE_TO_PARAM_TYPE[javaType];
    }
    return ACTION_TYPE_TO_PARAM_TYPE[actionType];
}

function findTableParamIndex(data, param, parentId) {
    const index = data.findIndex((item) => item && (item.paramKey === param.paramKey
        || (item.paramName === param.paramName && (item.parentId || null) === parentId)));
    if (index !== -1) {
        return index;
    }
    return parentId ? -1 : data.findIndex((item) => item && !item.paramName);
}

function createTableParam(param, displayOrder, hasChildren, parentId) {
    return {
        id: createLocalId(),
        paramKey: param.paramKey,
        displayOrder,
        newData: true,
        newdata: true,
        ...getTableParamInfo(param, hasChildren, parentId)
    };
}

function createLocalId() {
    localIdSequence = (localIdSequence + 1) % 100000;
    return `${Date.now()}${String(localIdSequence).padStart(5, '0')}`;
}

function getTableParamInfo(param, hasChildren, parentId) {
    const dataType = hasChildren || parentId ? 'node' : 'attribute';
    return {
        paramKey: param.paramKey,
        paramName: param.paramName,
        showName: param.showName,
        desc: param.desc,
        description: param.description,
        paramType: param.paramType,
        paramTypeString: param.paramTypeString,
        arrs: param.arrs,
        required: param.required,
        dataType,
        dataTypeDisabledValue: dataType,
        canClick: hasChildren,
        dataTypeDisabled: !hasChildren,
        arrsDisabledValue: param.arrs,
        requiredDisabledValue: true,
        paramNameDisabledValue: param.paramName,
        showNameDisabledValue: param.showName,
        paramTypeDisabledValue: param.paramType,
        isParent: hasChildren,
        ...(parentId ? { parentId } : {})
    };
}

export default ActionParamTable;
