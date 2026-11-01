import { regOvProps,regOvComponent } from '@weapp/utils';
import React from 'react';
import { asyncImport } from '@weapp/ecodesdk';

const TARGET_URL_PATTERN = /\/api\/bs\/esb\/setting\/flow\/design\/getDesignBySceneId\/.+/;
let actions;
/**
 * 拦截动作流设计界面中动作流数据接口，获取到动作流数据中的 actions（组件）数据
 */
axios.interceptors.response.use((response) => {
    try {
        const requestUrl = response?.config?.url || '';

        if (!TARGET_URL_PATTERN.test(requestUrl)) {
            return response;
        }
        actions = response?.data?.data?.actions;
        console.log('[ecode:${appId}-动作流Action参数自动获取]:接口拦截：获取到动作流 actions 数据');

    } catch (error) {
        console.error('[ecode:${appId}-动作流Action参数自动获取]:接口拦截：获取动作流 actions 数据失败', error);
    }

    return response;
}, (error) => Promise.reject(error));

let wId;
/**
 * 获取动作流设计界面中的 Dialog 组件参数，获取到当前打开动作对话框的 wId（动作组件id）
 */
regOvProps('weappUi','Dialog',(props)=>{
    try{
        if(props.weId?.endsWith('_41p22o')){
            if (props.children) {
                const childrenProps = props.children.props;
                wId = childrenProps?.assignProps?.assignContext?.wId;
            }
        }
    }catch(e){
        console.error('[ecode:${appId}-动作流Action参数自动获取]:组件参数复写：获取动作流设计界面中的 Dialog 组件参数失败',e);
    }

    return props;
},1);

const ActionParamTable = React.lazy(() => asyncImport('${appId}', 'ActionParamTable'));
/**
 * 替换动作流页面中的参数对话框中的表格组件，使用自定义的表格组件，在表格上方添加参数获取按钮，自动获取 Action 参数
 */
regOvComponent('weappUi', 'EditableTable', (Com) => {
    return React.forwardRef((props, ref) => {
        try{
            // 只有 serverless/action 组件的参数对话框才替换组件
            if (props.weId?.endsWith('_emezb5_rhk27a')  && !props.noOverwrite) {
                const groupId = getActionGroupId(wId,actions);
                return (
                    <React.Suspense fallback={() => { }}>
                        <ActionParamTable className={'ecode-${appId}'} ref={ref} {...props}
                                          OriginCom={Com} groupId={groupId} type={'input'}/>
                    </React.Suspense>
                )
            }
        }catch(e){
            console.error('[ecode:${appId}-动作流Action参数自动获取]:组件替换：替换动作流页面中的参数对话框中的表格组件失败',e);
        }

        // 如果不是要替换的组件需要返回原组件
        return <Com ref={ref} {...props} />
    });
}, 1);

/**
 * 根据 wId 判断当前组件是否是 serverless/action 组件
 */
function isActionComponent(wId,actions) {
    if (!actions || !wId) {
        return false;
    }
    return actions.some(action => action.wId === wId);
}

/**
 * 获取普通或循环内 Action 的分组标识。
 * 循环子动作保存在 data.actions 中，按节点 wId 递归查找。
 * @param {string} weId 当前 Action 节点标识。
 * @param {Array} actions 当前层的动作节点。
 * @returns {string|undefined} 匹配节点的分组标识，未匹配时为空字符串。
 */
function getActionGroupId(weId,actions){
    if (!actions || !weId) {
        return '';
    }

    for (const action of actions) {
        if (action.wId === weId) {
            return action.data?.groupId;
        }
        const groupId = getActionGroupId(weId, action.data?.actions);
        if (groupId) {
            return groupId;
        }
    }
    return '';
}


