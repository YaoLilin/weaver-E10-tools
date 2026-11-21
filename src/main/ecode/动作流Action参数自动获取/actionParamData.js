export const PARAM_TYPE_OPTIONS = [
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

/** 获取必填及非必填参数的完整路径，包含数组和嵌套子参数。 */
export function getActionParamKeys(actionParams, parentKey = '') {
    return actionParams.flatMap((param) => {
        const paramKey = getParamKey(param, parentKey);
        const childParamKeys = Array.isArray(param.children) ? getActionParamKeys(param.children, `${paramKey}.`) : [];

        return [
            { key: paramKey, required: param.required === true },
            ...childParamKeys
        ];
    });
}

/**
 * 根据后端参数定义生成新表格数据，标识分配由调用方提供。
 * @param {object[]} currentData 当前页面参数行
 * @param {object[]} actionParams 后端返回的参数树
 * @param {Function} createId 新建参数行时分配 ID 的函数
 * @returns {object[]} 保留既有参数关联的结果
 */
export function mergeTableData(currentData, actionParams, createId) {
    const data = currentData.slice();
    actionParams.forEach((param) => mergeActionParam(data, param, '', null, createId));
    return data;
}

/**
 * 沿接口返回的有限参数树递归，复用匹配行 ID 并为子参数保留父行关联。
 */
function mergeActionParam(data, actionParam, parentKey, parentId, createId) {
    const param = toTableParam(actionParam, parentKey);
    const hasChildren = Array.isArray(actionParam.children) && actionParam.children.length > 0;
    const index = findTableParamIndex(data, param, parentId);
    let tableParam;

    if (index === -1) {
        tableParam = createTableParam(param, data.length, hasChildren, parentId, createId());
        data.push(tableParam);
    } else {
        tableParam = {
            ...data[index],
            ...getTableParamInfo(param, hasChildren, parentId)
        };
        if (!tableParam.id) {
            tableParam.id = createId();
        }
        data[index] = tableParam;
    }

    if (hasChildren) {
        actionParam.children.forEach((child) => {
            mergeActionParam(data, child, `${param.paramKey}.`, tableParam.id, createId);
        });
    }
}

function toTableParam(param, parentKey) {
    const isArray = param.array;
    const paramType = getTableParamType(param.type, param.javaType);
    const option = PARAM_TYPE_OPTION_MAP[paramType];
    const desc = buildParamDescription(param);

    return {
        paramKey: getParamKey(param, parentKey),
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

function createTableParam(param, displayOrder, hasChildren, parentId, id) {
    return {
        id,
        paramKey: param.paramKey,
        displayOrder,
        newData: true,
        newdata: true,
        ...getTableParamInfo(param, hasChildren, parentId)
    };
}

function getParamKey(param, parentKey) {
    return `${parentKey}${param.name}${param.array ? '[]' : ''}`;
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
