import React from 'react';
import {Route} from 'react-router-dom';
import {appInfo, regOvProps, regReactChildren} from '@weapp/utils';
import {asyncImport} from '@weapp/ecodesdk';

const PAGE_PATH = '/sp/custom/801378248580521305/index';
const {publicUrl} = appInfo('@weapp/ecodesdk');
const DevTools = React.lazy(() => asyncImport('${appId}', 'DevTools'));
const page = (
    <Route exact path={`${publicUrl}${PAGE_PATH}`}>
        <React.Suspense fallback={<div className="appid-${appId} seconddev-ecode">正在加载开发者工具…</div>}>
            <DevTools />
        </React.Suspense>
    </Route>
);

/**
 * 注册开发者工具页面，提供日志级别查看与修改功能。
 * <p>页面地址：/sp/custom/801378248580521305/index，使用 appUUID 标识独立页面。</p>
 * <p>通过 Switch 组件参数复写注入页面路由，左侧显示工具菜单，右侧显示日志配置页面。</p>
 * @param {Object} props 平台 Switch 组件参数。
 * @returns {Object} 已添加开发者工具路由的 Switch 组件参数。
 */
regOvProps('weappEcodesdk', 'Switch', (props) => {
    regReactChildren(props, page);
    console.info('ecodeId:${appId}，组件参数重写：已注册开发者工具页面', PAGE_PATH);
    return props;
}, 0);
