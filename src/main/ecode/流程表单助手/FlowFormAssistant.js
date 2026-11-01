import React, {useEffect, useRef, useState} from 'react';
import {Dialog, Icon, SlideTabs} from '@weapp/ui';
import {devRequest} from '@weapp/ecodesdk';
import AssistantDialogLayer from './components/AssistantDialogLayer';
import CopyValue from './components/CopyValue';
import FormDataTab from './components/FormDataTab';
import config from './config/config';

const BUTTON_SIZE = 38;
const DEFAULT_DRAWER_WIDTH = '40%';
const POSITION_STORAGE_KEY = 'seconddev-flow-form-assistant-position';
const FORM_FIELD_VALUE_API = '/api/secondev/hnweaver/form-assist/field-value';
const DRAWER_TAB_DATA = [
    { id: 'formFields', content: '表单字段' },
    { id: 'workflow', content: '流程信息' },
    { id: 'nodes', content: '节点列表' },
];
const EB_DRAWER_TAB_DATA = [
    { id: 'formFields', content: '表单字段' },
    { id: 'workflow', content: '表单信息' },
];

const triggerStyle = {
    alignItems: 'center',
    background: 'var(--primary, #5d9cec)',
    border: 'none',
    borderRadius: '50%',
    boxShadow: 'var(--box-shadow, 0 3px 12px rgba(0, 0, 0, 0.12))',
    color: 'var(--base-white, #ffffff)',
    cursor: 'pointer',
    display: 'flex',
    height: `${BUTTON_SIZE}px`,
    justifyContent: 'center',
    padding: 0,
    position: 'fixed',
    touchAction: 'none',
    userSelect: 'none',
    width: `${BUTTON_SIZE}px`,
    zIndex: 'calc(var(--dialog-draw-zindex, 900) - 1)',
};

/**
 * 校验抽屉宽度配置。
 *
 * @param {string} width 抽屉宽度，格式为百分比或像素
 * @returns {boolean} 配置是否有效
 */
function isValidDrawerWidth(width) {
    return typeof width === 'string'
        && (/^(?:[1-9][0-9]?|100)%$/.test(width) || /^[1-9][0-9]*px$/.test(width));
}

/**
 * 限制悬浮图标位置在浏览器可视区域内。
 *
 * @param {{left: number, top: number}} position 悬浮图标位置
 * @returns {{left: number, top: number}} 可视区域内的位置
 */
function clampPosition(position) {
    const maxLeft = Math.max(0, window.innerWidth - BUTTON_SIZE);
    const maxTop = Math.max(0, window.innerHeight - BUTTON_SIZE);
    return {
        left: Math.min(Math.max(0, position.left), maxLeft),
        top: Math.min(Math.max(0, position.top), maxTop),
    };
}

/**
 * 读取悬浮图标的本地保存位置。
 *
 * @returns {{left: number, top: number}} 悬浮图标初始位置
 */
function getInitialPosition() {
    const defaultPosition = {
        left: Math.max(0, window.innerWidth - BUTTON_SIZE - 24),
        top: Math.max(0, window.innerHeight - BUTTON_SIZE - 24),
    };

    try {
        const storedPosition = JSON.parse(window.localStorage.getItem(POSITION_STORAGE_KEY));
        if (storedPosition
            && Number.isFinite(storedPosition.left)
            && Number.isFinite(storedPosition.top)) {
            return clampPosition(storedPosition);
        }
    } catch (error) {
        console.error('ecodeId:${appId}，读取流程表单助手悬浮图标位置失败', error);
    }

    return defaultPosition;
}

/**
 * 保存悬浮图标位置。
 *
 * @param {{left: number, top: number}} position 悬浮图标位置
 */
function savePosition(position) {
    try {
        window.localStorage.setItem(POSITION_STORAGE_KEY, JSON.stringify(position));
    } catch (error) {
        console.error('ecodeId:${appId}，保存流程表单助手悬浮图标位置失败', error);
    }
}

/**
 * 流程表单助手悬浮入口与侧边抽屉。
 *
 * @param {{initialFormSdk?: object, dialogZIndex?: number}} props 侧栏打开时明确绑定的表单实例
 * @returns {React.ReactElement} 流程表单助手组件
 */
const FlowFormAssistant = ({initialFormSdk, dialogZIndex = 900}) => {
    const [drawerVisible, setDrawerVisible] = useState(false);
    const [activeTab, setActiveTab] = useState('formFields');
    const [formData, setFormData] = useState(null);
    const [formDataError, setFormDataError] = useState('');
    const [formDataLoading, setFormDataLoading] = useState(false);
    const [nodeError, setNodeError] = useState('');
    const [nodeLoading, setNodeLoading] = useState(false);
    const [nodes, setNodes] = useState([]);
    const [position, setPosition] = useState(getInitialPosition);
    const [workflowInfo, setWorkflowInfo] = useState({});
    const [formModule, setFormModule] = useState(
        initialFormSdk ? initialFormSdk.getBaseInfo().module
            : (window.location.pathname.startsWith('/sp/ebdfpage/card/') ? 'ebuilderform' : 'workflow'),
    );
    const formSdkRef = useRef(null);
    const dragStateRef = useRef(null);
    const suppressClickRef = useRef(false);
    const isEbuilderForm = formModule === 'ebuilderform';
    const assistantTitle = isEbuilderForm ? 'EB 表单助手' : '流程表单助手';
    const drawerWidth = isValidDrawerWidth(config.drawerWidth)
        ? config.drawerWidth
        : DEFAULT_DRAWER_WIDTH;

    useEffect(() => {
        const keepPositionInViewport = () => setPosition(currentPosition => clampPosition(currentPosition));
        window.addEventListener('resize', keepPositionInViewport);
        return () => window.removeEventListener('resize', keepPositionInViewport);
    }, []);

    /**
     * 读取当前流程表单信息。
     *
     * @returns {object} 当前流程表单信息
     */
    const getFormInfo = () => {
        try {
            formSdkRef.current = initialFormSdk || window.WeFormSDK.getWeFormInstance();
            return formSdkRef.current.getBaseInfo() || {};
        } catch (error) {
            console.error('ecodeId:${appId}，流程表单助手获取表单信息失败', error);
            return {};
        }
    };

    /**
     * 获取当前流程表单 SDK。
     *
     * @returns {object|undefined} 当前流程表单 SDK
     */
    const getFormSdk = () => formSdkRef.current;

    /**
     * 更新抽屉中的流程信息。
     *
     * @returns {object} 当前流程表单信息
     */
    const refreshWorkflowInfo = () => {
        const formInfo = getFormInfo();
        const currentWorkflowInfo = {
            appId: formInfo.appId,
            dataId: formInfo.dataId,
            formId: formInfo.formId,
            formName: formInfo.formName,
            module: formInfo.module,
            nodeId: formInfo.customParam && formInfo.customParam.nodeId,
            workflowId: formInfo.customParam && formInfo.customParam.workflowId,
        };
        setWorkflowInfo(currentWorkflowInfo);
        setFormModule(formInfo.module);
        console.debug('ecodeId:${appId}，流程表单助手已读取流程信息', currentWorkflowInfo);
        return formInfo;
    };

    /**
     * 获取当前流程的节点列表。
     */
    const loadNodes = async formInfo => {
        const workflowId = formInfo && formInfo.customParam && formInfo.customParam.workflowId;
        if (!workflowId) {
            setNodes([]);
            setNodeError('未获取到当前流程 ID');
            console.error('ecodeId:${appId}，流程表单助手未获取到 workflowId');
            return;
        }

        setNodeLoading(true);
        setNodeError('');
        try {
            console.debug('ecodeId:${appId}，流程表单助手请求节点列表', { workflowId });
            const { data: responseData } = await axios.get(
                `/api/bs/workflow/pathdef/nodeBase/getNodeList?workflowId=${encodeURIComponent(workflowId)}`,
            );
            if (responseData.code !== 200) {
                throw new Error(`节点列表请求失败：${responseData.msg}`);
            }
            const nodeList = responseData && responseData.data && Array.isArray(responseData.data.data)
                ? responseData.data.data
                : [];
            setNodes(nodeList);
            console.debug('ecodeId:${appId}，流程表单助手节点列表请求成功', {
                workflowId,
                nodeCount: nodeList.length,
            });
        } catch (error) {
            setNodes([]);
            setNodeError('节点列表加载失败');
            console.error('ecodeId:${appId}，流程表单助手请求节点列表失败', error);
        } finally {
            setNodeLoading(false);
        }
    };

    /**
     * 通过后端表单助手接口获取表单字段库中的全部字段及其值。
     *
     * @param {object} formInfo 当前流程表单信息
     */
    const loadFormData = async formInfo => {
        const { dataId, formId } = formInfo;
        if (!formId || !dataId) {
            setFormData(null);
            setFormDataError('未获取到当前表单字段查询参数');
            console.error('ecodeId:${appId}，流程表单助手未获取到表单字段查询参数', {
                dataId,
                formId,
            });
            return;
        }

        setFormDataLoading(true);
        setFormDataError('');
        try {
            console.debug('ecodeId:${appId}，流程表单助手请求表单字段', { dataId, formId });
            const responseData = await devRequest({
                url: FORM_FIELD_VALUE_API,
                method: 'GET',
                params: { dataId, formId, module: formInfo.module },
            });
            if (responseData.status !== true) {
                throw new Error(responseData.msg || '表单字段请求失败');
            }
            setFormData(responseData.data);
            console.debug('ecodeId:${appId}，流程表单助手表单字段请求成功', {
                mainFieldCount: responseData.data.mainFields.length,
                detailTableCount: responseData.data.detailTables.length,
                formId,
            });
        } catch (error) {
            setFormData(null);
            setFormDataError(error.message || '表单字段加载失败');
            console.error('ecodeId:${appId}，流程表单助手请求表单字段失败', error);
        } finally {
            setFormDataLoading(false);
        }
    };

    /**
     * 打开流程表单助手抽屉。
     */
    const openDrawer = () => {
        setDrawerVisible(true);
        const formInfo = refreshWorkflowInfo();
        setActiveTab('formFields');
        if (formInfo.module === 'workflow') {
            loadNodes(formInfo);
        }
        loadFormData(formInfo);
        console.log('ecodeId:${appId}，流程表单助手抽屉已展开');
    };

    /**
     * 关闭流程表单助手抽屉。
     */
    const closeDrawer = () => {
        setDrawerVisible(false);
        setFormData(null);
        setFormDataError('');
        console.log('ecodeId:${appId}，流程表单助手抽屉已关闭');
    };

    /**
     * 抽屉打开时监听 Esc 键关闭事件。
     */
    useEffect(() => {
        if (!drawerVisible) {
            return undefined;
        }

        const closeOnEscape = event => {
            if (event.key === 'Escape') {
                closeDrawer();
            }
        };
        window.addEventListener('keydown', closeOnEscape);
        return () => window.removeEventListener('keydown', closeOnEscape);
    }, [drawerVisible]);

    /**
     * 记录开始拖动悬浮图标的位置。
     *
     * @param {React.PointerEvent<HTMLButtonElement>} event 指针事件
     */
    const startDragging = event => {
        if (event.button !== 0) {
            return;
        }

        suppressClickRef.current = false;
        dragStateRef.current = {
            offsetLeft: event.clientX - position.left,
            offsetTop: event.clientY - position.top,
            startX: event.clientX,
            startY: event.clientY,
        };
        if (event.currentTarget.setPointerCapture) {
            event.currentTarget.setPointerCapture(event.pointerId);
        }
    };

    /**
     * 更新悬浮图标拖动位置。
     *
     * @param {React.PointerEvent<HTMLButtonElement>} event 指针事件
     */
    const drag = event => {
        const dragState = dragStateRef.current;
        if (!dragState) {
            return;
        }

        if (Math.abs(event.clientX - dragState.startX) > 3 || Math.abs(event.clientY - dragState.startY) > 3) {
            suppressClickRef.current = true;
        }
        const nextPosition = clampPosition({
            left: event.clientX - dragState.offsetLeft,
            top: event.clientY - dragState.offsetTop,
        });
        dragState.position = nextPosition;
        setPosition(nextPosition);
    };

    /**
     * 结束拖动并记住悬浮图标位置。
     */
    const stopDragging = () => {
        if (!dragStateRef.current) {
            return;
        }

        const lastPosition = dragStateRef.current.position || position;
        dragStateRef.current = null;
        if (suppressClickRef.current) {
            savePosition(lastPosition);
            console.log('ecodeId:${appId}，流程表单助手悬浮图标位置已保存', lastPosition);
        }
    };

    /**
     * 处理悬浮图标点击；拖动结束后的点击不展开抽屉。
     */
    const handleTriggerClick = () => {
        if (suppressClickRef.current) {
            suppressClickRef.current = false;
            return;
        }
        openDrawer();
    };

    /**
     * 渲染节点列表标签页内容。
     *
     * @returns {React.ReactElement} 节点列表标签页
     */
    const renderNodeList = () => {
        if (nodeLoading) {
            return <div style={{ color: 'var(--secondary-fc, #999999)', padding: '16px' }}>节点列表加载中...</div>;
        }
        if (nodeError) {
            return <div style={{ color: 'var(--danger, #ff4d4f)', padding: '16px' }}>{nodeError}</div>;
        }
        if (!nodes.length) {
            return <div style={{ color: 'var(--secondary-fc, #999999)', padding: '16px' }}>暂无节点信息</div>;
        }

        return (
            <div style={{ padding: '8px 16px 16px' }}>
                {nodes.map(node => (
                    <div
                        key={node.id}
                        style={{
                            borderBottom: 'var(--border-solid, 1px solid #e5e5e5)',
                            padding: '12px 0',
                        }}
                    >
                        <div style={{ color: 'var(--main-fc, #111111)', fontSize: 'var(--font-size-base, 14px)' }}>
                            <CopyValue label="节点名称" value={node.nodeName} />
                        </div>
                        <div style={{ color: 'var(--secondary-fc, #999999)', fontSize: 'var(--font-size-sm, 12px)', marginTop: '4px' }}>
                            ID：<CopyValue label="节点 ID" value={node.id} />
                        </div>
                    </div>
                ))}
            </div>
        );
    };

    /**
     * 渲染流程信息标签页内容。
     *
     * @returns {React.ReactElement} 流程信息标签页
     */
    const renderWorkflowInfo = () => {
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
            <div style={{ padding: '8px 16px 16px' }}>
                {infoItems.map(item => (
                    <div
                        key={item.label}
                        style={{
                            borderBottom: 'var(--border-solid, 1px solid #e5e5e5)',
                            padding: '12px 0',
                        }}
                    >
                        <div style={{ color: 'var(--secondary-fc, #999999)', fontSize: 'var(--font-size-sm, 12px)' }}>
                            {item.label}
                        </div>
                        <div style={{ color: 'var(--main-fc, #111111)', fontSize: 'var(--font-size-base, 14px)', marginTop: '4px' }}>
                            <CopyValue label={item.label} value={item.value} />
                        </div>
                    </div>
                ))}
            </div>
        );
    };

    return (
        <div className={'appid-${appId} seconddev-ecode'}>
            <button
                aria-label={`打开${assistantTitle}`}
                className={'appid-${appId} seconddev-ecode flow-form-assistant-trigger'}
                onClick={handleTriggerClick}
                onPointerCancel={stopDragging}
                onPointerDown={startDragging}
                onPointerMove={drag}
                onPointerUp={stopDragging}
                style={{
                    ...triggerStyle,
                    cursor: dragStateRef.current ? 'grabbing' : 'grab',
                    left: `${position.left}px`,
                    top: `${position.top}px`,
                }}
                type="button"
            >
                <Icon name="Icon-Robot2" style={{ height: 20, width: 20 }} />
            </button>
            <Dialog
                closable
                destroyOnClose
                mask
                maskClosable
                onClose={closeDrawer}
                placement="right"
                title={assistantTitle}
                visible={drawerVisible}
                width={drawerWidth}
                zIndex={dialogZIndex}
            >
                <div className={'appid-${appId} seconddev-ecode flow-form-assistant-drawer'}>
                    <SlideTabs
                        data={isEbuilderForm ? EB_DRAWER_TAB_DATA : DRAWER_TAB_DATA}
                        onChange={setActiveTab}
                        value={activeTab}
                    />
                    {activeTab === 'formFields' && (
                        <AssistantDialogLayer.Provider value={dialogZIndex}>
                            <FormDataTab
                            error={formDataError}
                            formData={formData}
                            formSdk={getFormSdk()}
                            loading={formDataLoading}
                            />
                        </AssistantDialogLayer.Provider>
                    )}
                    {activeTab === 'workflow' && renderWorkflowInfo()}
                    {activeTab === 'nodes' && !isEbuilderForm && renderNodeList()}
                </div>
            </Dialog>
        </div>
    );
};

export default FlowFormAssistant;
