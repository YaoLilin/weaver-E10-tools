import React, {useEffect, useState} from 'react';
import CopyValue from './CopyValue';

/**
 * 查询并展示当前流程的节点名称及 ID。
 * @param {{formInfo: object, active: boolean}} props 当前表单基础信息和标签页是否选中
 * @returns {React.ReactElement|null} 节点列表标签页
 */
const NodeListTab = ({formInfo, active}) => {
    const [nodeError, setNodeError] = useState('');
    const [nodeLoading, setNodeLoading] = useState(false);
    const [nodes, setNodes] = useState([]);
    useEffect(() => {
        let cancelled = false;
        /** 查询当前流程的节点列表，并在标签页卸载后忽略返回结果。 */
        const loadNodes = async formInfo => {
            const workflowId = formInfo && formInfo.customParam && formInfo.customParam.workflowId;
            if (!workflowId) {
                setNodes([]);
                setNodeError('未获取到当前流程 ID');
                console.error('[ecode:${appId}-流程表单助手]:流程表单助手未获取到 workflowId');
                return;
            }

            setNodeLoading(true);
            setNodeError('');
            try {
                console.debug('[ecode:${appId}-流程表单助手]:流程表单助手请求节点列表', { workflowId });
                const { data: responseData } = await axios.get(
                    `/api/bs/workflow/pathdef/nodeBase/getNodeList?workflowId=${encodeURIComponent(workflowId)}`,
                );
                if (responseData.code !== 200) {
                    throw new Error(`节点列表请求失败：${responseData.msg}`);
                }
                if (cancelled) {
                    return;
                }
                const nodeList = responseData && responseData.data && Array.isArray(responseData.data.data)
                    ? responseData.data.data
                    : [];
                setNodes(nodeList);
                console.debug('[ecode:${appId}-流程表单助手]:流程表单助手节点列表请求成功', {
                    workflowId,
                    nodeCount: nodeList.length,
                });
            } catch (error) {
                if (cancelled) {
                    return;
                }
                setNodes([]);
                setNodeError('节点列表加载失败');
                console.error('[ecode:${appId}-流程表单助手]:流程表单助手请求节点列表失败', error);
            } finally {
                if (!cancelled) {
                    setNodeLoading(false);
                }
            }
        };

        loadNodes(formInfo);
        return () => { cancelled = true; };
    }, [formInfo]);
    if (!active) {
        return null;
    }
    if (nodeLoading) {
        return <div className="ffa-message">节点列表加载中...</div>;
    }
    if (nodeError) {
        return <div className="ffa-error-message">{nodeError}</div>;
    }
    if (!nodes.length) {
        return <div className="ffa-message">暂无节点信息</div>;
    }

    return (
        <div className="ffa-info-content">
            {nodes.map(node => (
                <div
                    key={node.id}
                    className="ffa-info-row"
                >
                    <div className="ffa-node-title">
                        <CopyValue label="节点名称" value={node.nodeName} />
                    </div>
                    <div className="ffa-node-id">
                        ID：<CopyValue label="节点 ID" value={node.id} />
                    </div>
                </div>
            ))}
        </div>
    );
};

export default NodeListTab;
