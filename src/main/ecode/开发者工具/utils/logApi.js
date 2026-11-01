import {devRequest} from '@weapp/ecodesdk';

const LOG_API = '/api/secondev/dev-tools/log-level';
export const LOG_LEVELS = ['DEBUG', 'INFO', 'WARN', 'ERROR'];

/**
 * 调用日志配置接口，统一处理 WeaResult 业务失败与异常响应。
 * @param {Object} options devRequest 请求参数。
 * @returns {Promise<string>} 接口返回的配置说明或保存结果。
 */
async function requestLogLevel(options) {
    console.debug('ecodeId:${appId}，开发者工具日志接口请求', options);
    const result = await devRequest({url: LOG_API, ...options});
    console.debug('ecodeId:${appId}，开发者工具日志接口响应', result);
    const succeeded = result && result.status === true;
    if (!succeeded) {
        throw new Error((result && result.msg) || '日志接口请求失败');
    }
    const validData = typeof result.data === 'string' && result.data.trim().length > 0;
    if (!validData) {
        throw new Error('日志接口返回的数据格式不正确');
    }
    return result.data;
}

/** @returns {Promise<string>} 当前日志级别配置说明。 */
export function getLogLevel() {
    return requestLogLevel({method: 'GET'});
}

/**
 * 保存日志级别配置。
 * @param {string} level 后端支持的日志级别。
 * @returns {Promise<string>} 保存结果。
 */
export function saveLogLevel(level) {
    if (!LOG_LEVELS.includes(level)) {
        return Promise.reject(new Error('请选择有效的日志级别'));
    }
    return requestLogLevel({method: 'POST', data: {level}});
}

/**
 * 从 Controller 返回的说明文字解析可选日志级别。
 * @param {string} description 当前配置说明。
 * @returns {string} 支持的日志级别；未配置或不支持时返回空字符串。
 */
export function parseLogLevel(description) {
    const match = description.match(/^当前日志级别:\s*(DEBUG|INFO|WARN|ERROR)\s*$/i);
    return match ? match[1].toUpperCase() : '';
}
