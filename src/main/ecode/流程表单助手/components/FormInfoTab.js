import React from 'react';
import CopyValue from './CopyValue';

/**
 * 展示流程信息或 EB 表单信息，并提供各项信息复制。
 * @param {{formInfo: object}} props 当前表单 SDK 基础信息
 * @returns {React.ReactElement} 流程或表单信息标签页
 */
const FormInfoTab = ({formInfo}) => {
    const workflowInfo = {
        ...formInfo,
        workflowId: formInfo.customParam && formInfo.customParam.workflowId,
        nodeId: formInfo.customParam && formInfo.customParam.nodeId,
    };
    const isEbuilderForm = formInfo.module === 'ebuilderform';
    const workflowItems = [
        { label: '流程 ID', value: workflowInfo.workflowId },
        { label: '表单 ID', value: workflowInfo.formId },
        { label: '数据 ID', value: workflowInfo.dataId },
        { label: '当前节点 ID', value: workflowInfo.nodeId },
    ];
    const ebItems = [
        { label: '表单名称', value: workflowInfo.formName },
        { label: '应用 ID', value: workflowInfo.appId },
        { label: '表单 ID', value: workflowInfo.formId },
        { label: '数据 ID', value: workflowInfo.dataId },
    ];
    const infoItems = isEbuilderForm ? ebItems : workflowItems;

    return (
        <div className="ffa-info-content">
            {infoItems.map(item => (
                <div
                    key={item.label}
                    className="ffa-info-row"
                >
                    <div className="ffa-info-label">
                        {item.label}
                    </div>
                    <div className="ffa-info-value">
                        <CopyValue label={item.label} value={item.value} />
                    </div>
                </div>
            ))}
        </div>
    );
};

export default FormInfoTab;
