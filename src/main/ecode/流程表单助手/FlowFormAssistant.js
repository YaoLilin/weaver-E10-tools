import React, {useEffect, useRef, useState} from 'react';
import {Dialog, SlideTabs} from '@weapp/ui';
import AssistantTrigger from './components/AssistantTrigger';
import AssistantDialogLayer from './components/AssistantDialogLayer';
import EditConfirmationDialog from './components/EditConfirmationDialog';
import FormInfoTab from './components/FormInfoTab';
import NodeListTab from './components/NodeListTab';
import FormDataTab from './components/FormDataTab';
import config from './config/config';
import defaultConfig from './config/config_default';

const DRAWER_TAB_DATA = [
    { id: 'formFields', content: '表单字段' },
    { id: 'workflow', content: '流程信息' },
    { id: 'nodes', content: '节点列表' },
];
const EB_DRAWER_TAB_DATA = [
    { id: 'formFields', content: '表单字段' },
    { id: 'workflow', content: '表单信息' },
];

/**
 * 将 EB 卡片的 SDK 模块名转换为助手使用的 EB 表单模块名。
 * @param {string} module 表单 SDK 返回的模块名
 * @returns {string} 表单助手接口与标签页使用的模块名
 */
function normalizeFormModule(module) {
    return module === 'ebuildercard' ? 'ebuilderform' : module;
}

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
 * 流程表单助手悬浮入口与侧边抽屉。
 *
 * @param {{initialFormSdk?: object, dialogZIndex?: number}} props 侧栏打开时明确绑定的表单实例
 * @returns {React.ReactElement} 流程表单助手组件
 */
const FlowFormAssistant = ({initialFormSdk, dialogZIndex = 900}) => {
    const [drawerVisible, setDrawerVisible] = useState(false);
    const [activeTab, setActiveTab] = useState('formFields');
    const [formInfo, setFormInfo] = useState({});
    const [pendingChange, setPendingChange] = useState(null);
    const [formModule, setFormModule] = useState(
        initialFormSdk ? normalizeFormModule(initialFormSdk.getBaseInfo().module)
            : (window.location.pathname.startsWith('/sp/ebdfpage/card/') ? 'ebuilderform' : 'workflow'),
    );
    const formSdkRef = useRef(null);
    const isEbuilderForm = formModule === 'ebuilderform';
    const assistantTitle = isEbuilderForm ? 'EB 表单助手' : '流程表单助手';
    const configuredDrawerWidth = config && config.drawerWidth;
    const drawerWidth = isValidDrawerWidth(configuredDrawerWidth)
        ? configuredDrawerWidth
        : defaultConfig.drawerWidth;

    /**
     * 读取当前流程表单信息。
     *
     * @returns {object} 当前流程表单信息
     */
    const getFormInfo = () => {
        try {
            formSdkRef.current = initialFormSdk || window.WeFormSDK.getWeFormInstance();
            const baseInfo = formSdkRef.current.getBaseInfo();
            return baseInfo ? {...baseInfo, module: normalizeFormModule(baseInfo.module)} : {};
        } catch (error) {
            console.error('[ecode:${appId}-流程表单助手]:流程表单助手获取表单信息失败', error);
            return {};
        }
    };

    /**
     * 打开流程表单助手抽屉。
     */
    const openDrawer = () => {
        setDrawerVisible(true);
        const currentFormInfo = getFormInfo();
        setFormInfo(currentFormInfo);
        setFormModule(currentFormInfo.module);
        setActiveTab('formFields');
        console.log('[ecode:${appId}-流程表单助手]:流程表单助手抽屉已展开');
    };

    /**
     * 关闭流程表单助手抽屉。
     */
    const closeDrawer = () => {
        cancelChange();
        setDrawerVisible(false);
        console.log('[ecode:${appId}-流程表单助手]:流程表单助手抽屉已关闭');
    };

    /**
     * 打开确认框，保留用户正在编辑的字段、行草稿及勾选状态。
     * @param {string} title 当前操作的标题
     * @param {Function} applyChange 用户确认后执行的操作
     * @param {string} okText 确认按钮文字
     */
    const confirmChange = (title, applyChange, okText = '确定') => {
        setPendingChange(current => current || {title, applyChange, okText});
    };

    /** 关闭确认框，保留助手中的编辑状态。 */
    const cancelChange = () => setPendingChange(null);

    /** 确认后关闭提示并执行待提交的操作。 */
    const applyConfirmedChange = () => {
        cancelChange();
        return pendingChange.applyChange();
    };

    /**
     * Esc 优先关闭待确认的操作，再关闭助手抽屉。
     */
    useEffect(() => {
        if (!drawerVisible) {
            return undefined;
        }

        const closeOnEscape = event => {
            if (event.key === 'Escape') {
                if (pendingChange) {
                    cancelChange();
                } else {
                    closeDrawer();
                }
            }
        };
        window.addEventListener('keydown', closeOnEscape);
        return () => window.removeEventListener('keydown', closeOnEscape);
    }, [drawerVisible, pendingChange]);

    return (
        <div className={'app-1305269432046043137 appid-${appId} seconddev-ecode'}>
            <AssistantTrigger title={assistantTitle} onOpen={openDrawer} />
            <Dialog
                wrapClassName="app-1305269432046043137 seconddev-ecode"
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
                    {drawerVisible && (
                        <AssistantDialogLayer.Provider value={dialogZIndex}>
                            <FormDataTab
                                active={activeTab === 'formFields'}
                                confirmChange={confirmChange}
                                formInfo={formInfo}
                                formSdk={formSdkRef.current}
                            />
                            {!isEbuilderForm && (
                                <NodeListTab active={activeTab === 'nodes'} formInfo={formInfo} />
                            )}
                            {activeTab === 'workflow' && <FormInfoTab formInfo={formInfo} />}
                        </AssistantDialogLayer.Provider>
                    )}
                </div>
            </Dialog>
            {pendingChange && (
                <EditConfirmationDialog
                    change={pendingChange}
                    onCancel={cancelChange}
                    onConfirm={applyConfirmedChange}
                    zIndex={dialogZIndex + 1}
                />
            )}
        </div>
    );
};

export default FlowFormAssistant;
