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
                console.debug('ecodeId:${appId}，流程表单助手业务模块异步加载成功');
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
                console.log('ecodeId:${appId}，已在流程表单页面挂载流程表单助手');
            }
        })
        .catch(error => {
            root.remove();
            console.error('ecodeId:${appId}，流程表单助手业务模块加载失败', error);
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

observeRouteChange();
if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', syncAssistant, { once: true });
} else {
    syncAssistant();
}

window.setTimeout(syncAssistant, (MODULE_LOAD_RETRY_COUNT + 1) * MODULE_LOAD_RETRY_DELAY);
