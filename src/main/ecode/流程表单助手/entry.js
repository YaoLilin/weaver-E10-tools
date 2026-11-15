import React from 'react';
import ReactDOM from 'react-dom';
import {asyncImport} from '@weapp/ecodesdk';

const ROOT_ID = 'seconddev-flow-form-assistant-root';
const ROUTE_CHANGE_EVENT = 'seconddev-flow-form-assistant-route-change';
const ROUTE_LISTENER_INITIALIZED_FLAG = '__seconddevFlowFormAssistantRouteListenerInitialized__';
const MODULE_LOAD_RETRY_COUNT = 10;
const MODULE_LOAD_RETRY_DELAY = 200;

/**
 * 等待应用业务模块完成登记。
 *
 * E10 前置入口可能早于应用模块包加载，需有界重试避免首次异步加载永久等待。
 *
 * @returns {Promise<{default: React.ComponentType}>} 流程表单助手模块
 */
async function loadFlowFormAssistantModule() {
    let lastError;
    for (let attempt = 0; attempt < MODULE_LOAD_RETRY_COUNT; attempt += 1) {
        try {
            const module = await Promise.race([
                asyncImport('${appId}', 'FlowFormAssistant'),
                new Promise((resolve, reject) => {
                    window.setTimeout(() => reject(new Error('等待流程表单助手模块加载超时')), MODULE_LOAD_RETRY_DELAY);
                }),
            ]);
            if (module && module.default) {
                console.debug('[ecode:${appId}-流程表单助手]:流程表单助手业务模块异步加载成功');
                return module;
            }
            lastError = new Error('流程表单助手模块未返回默认组件');
        } catch (error) {
            lastError = error;
        }
        await new Promise(resolve => window.setTimeout(resolve, MODULE_LOAD_RETRY_DELAY));
    }
    throw lastError || new Error('流程表单助手业务模块加载失败');
}

/**
 * 判断当前页面是否为 PC 流程或 EB 表单页面。
 *
 * @returns {boolean} 当前页面是否需要展示流程表单助手
 */
function isFlowFormPage() {
    const pathname = window.location.pathname;
    const isWorkflowPage = pathname.startsWith('/sp/workflow/flowpage/');
    const isEbuilderPage = pathname.startsWith('/sp/ebdfpage/card/');
    return isWorkflowPage || isEbuilderPage;
}

/**
 * 在流程表单页面挂载流程表单助手。
 */
function mountAssistant() {
    if (!document.body || !isFlowFormPage() || document.getElementById(ROOT_ID)) {
        return;
    }

    const root = document.createElement('div');
    root.id = ROOT_ID;
    document.body.appendChild(root);
    loadFlowFormAssistantModule()
        .then(module => {
            if (isFlowFormPage() && document.getElementById(ROOT_ID) === root) {
                ReactDOM.render(
                    <window.ReactRouterDOM.BrowserRouter>
                        <window.ReactRouterDOM.Route
                            path={[
                                '/sp/workflow/flowpage/:mode/:requestId',
                                '/sp/ebdfpage/card/:mode/:cardId/:dataId',
                            ]}
                            render={({ match }) => (
                                <React.Suspense fallback={null}>
                                    <module.default key={match.url} />
                                </React.Suspense>
                            )}
                        />
                    </window.ReactRouterDOM.BrowserRouter>,
                    root,
                );
                console.log('[ecode:${appId}-流程表单助手]:已在流程表单页面挂载流程表单助手');
            }
        })
        .catch(error => {
            root.remove();
            console.error('[ecode:${appId}-流程表单助手]:流程表单助手业务模块加载失败', error);
        });
}

/**
 * 在离开流程表单页面时卸载流程表单助手。
 */
function unmountAssistant() {
    const root = document.getElementById(ROOT_ID);
    if (!root || isFlowFormPage()) {
        return;
    }

    ReactDOM.unmountComponentAtNode(root);
    root.remove();
}

/**
 * 根据单页应用路由同步流程表单助手挂载状态。
 */
function syncAssistant() {
    mountAssistant();
    unmountAssistant();
}

/**
 * 监听浏览器历史记录变化，兼容单页应用 pushState 和 replaceState 路由跳转。
 */
function observeRouteChange() {
    window.addEventListener('popstate', syncAssistant);
    window.addEventListener('hashchange', syncAssistant);
    window.addEventListener(ROUTE_CHANGE_EVENT, syncAssistant);

    if (window[ROUTE_LISTENER_INITIALIZED_FLAG]) {
        return;
    }

    ['pushState', 'replaceState'].forEach(methodName => {
        const originalMethod = window.history[methodName];
        window.history[methodName] = function historyMethod(...args) {
            const result = originalMethod.apply(this, args);
            window.dispatchEvent(new Event(ROUTE_CHANGE_EVENT));
            return result;
        };
    });
    window[ROUTE_LISTENER_INITIALIZED_FLAG] = true;
}

/**
 * 在 EB 记录侧栏中挂载助手，并随侧栏关闭清理；多层侧栏只展示最上层的助手。
 * @param {Function} loadModule 加载助手业务组件的函数
 */
function observeEbuilderSidebar(loadModule) {
    const contexts = [];
    let activeContext;
    let root;
    let observer;
    let generation = 0;

    /** 移除当前助手，作废尚未完成的异步挂载。 */
    const unmount = () => {
        generation += 1;
        if (root) {
            ReactDOM.unmountComponentAtNode(root);
            root.remove();
            root = null;
        }
        activeContext = undefined;
    };

    /** 根据侧栏容器是否仍存在，恢复最上层可见表单的助手。 */
    const sync = () => {
        for (let index = contexts.length - 1; index >= 0; index -= 1) {
            if (!contexts[index].container.isConnected) {
                contexts.splice(index, 1);
            }
        }
        const context = contexts.slice().reverse().find(item => item.container.getClientRects().length > 0);
        if (context === activeContext) {
            return;
        }
        unmount();
        if (!context) {
            if (contexts.length === 0 && observer) {
                observer.disconnect();
                observer = undefined;
            }
            return;
        }
        activeContext = context;
        const version = generation;
        root = document.createElement('div');
        root.className = 'appid-${appId} seconddev-ecode flow-form-assistant-sidebar-root';
        // 弹窗内容层包含 transform，固定定位入口应挂在外层，避免坐标偏移和裁切。
        const host = context.container.closest('.ui-dialog-wrap') || context.container;
        host.appendChild(root);
        const target = root;
        loadModule().then(module => {
            if (version !== generation || !target.isConnected) {
                return;
            }
            ReactDOM.render(
                <window.ReactRouterDOM.BrowserRouter>
                    <window.ReactRouterDOM.Route path="/" render={() => (
                        <React.Suspense fallback={null}>
                            <module.default
                                initialFormSdk={context.sdk}
                                dialogZIndex={Math.max(900, Number(window.getComputedStyle(host).zIndex) + 1 || 900)}
                            />
                        </React.Suspense>
                    )} />
                </window.ReactRouterDOM.BrowserRouter>,
                target,
            );
            console.log('[ecode:${appId}-流程表单助手]:已挂载 EB 侧栏助手');
        }).catch(error => {
            console.error('[ecode:${appId}-流程表单助手]:挂载 EB 侧栏助手失败', error);
            if (version === generation) {
                // 本次表单挂载失败后停止自动重挂，避免 DOM 变化触发连续失败。
                const failedIndex = contexts.indexOf(context);
                if (failedIndex >= 0) {
                    contexts.splice(failedIndex, 1);
                }
                unmount();
            }
        });
    };

    window.addEventListener('onFormReady', event => {
        const detail = event.detail || {};
        const standalonePage = window.location.pathname.startsWith('/sp/ebdfpage/card/');
        if (detail.module !== 'ebuilderform' || standalonePage || !detail.formSdk) {
            return;
        }
        const base = detail.formSdk.getBaseInfo();
        const container = base.scrollContainer ? document.querySelector(base.scrollContainer) : null;
        if (!container) {
            return;
        }
        const previousIndex = contexts.findIndex(item => item.container === container);
        if (previousIndex >= 0) {
            contexts.splice(previousIndex, 1);
        }
        contexts.push({container, sdk: detail.formSdk});
        if (!observer) {
            observer = new MutationObserver(sync);
            observer.observe(document.body, {childList: true, subtree: true});
        }
        sync();
    });
}

observeEbuilderSidebar(loadFlowFormAssistantModule);
observeRouteChange();
if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', syncAssistant, { once: true });
} else {
    syncAssistant();
}

window.setTimeout(syncAssistant, (MODULE_LOAD_RETRY_COUNT + 1) * MODULE_LOAD_RETRY_DELAY);
