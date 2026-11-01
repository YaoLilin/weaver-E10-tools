import React, {useEffect, useRef, useState} from 'react';
import {Button, Layout, Select, Spin} from '@weapp/ui';
import {getLogLevel, LOG_LEVELS, parseLogLevel, saveLogLevel} from '../utils/logApi';
import {styles} from '../styles/layout';

const OPTIONS = LOG_LEVELS.map(level => ({id: level, content: level}));

/**
 * 日志配置页，自行管理查询、级别选择及提交状态。无外部参数。
 * @returns {React.ReactElement} 日志配置表单。
 */
export default function LogPage() {
    const [current, setCurrent] = useState('');
    const [level, setLevel] = useState('');
    const [busy, setBusy] = useState(true);
    const [error, setError] = useState('');
    const [success, setSuccess] = useState('');
    const mounted = useRef(false);
    const inFlight = useRef(false);

    useEffect(() => {
        mounted.current = true;
        refresh();
        return () => { mounted.current = false; };
    }, []);

    /** 查询配置并回显，失败时清除过期值，允许用户重新查询。 */
    async function loadCurrent() {
        try {
            const description = await getLogLevel();
            if (mounted.current) {
                setCurrent(description);
                setLevel(parseLogLevel(description));
            }
        } catch (requestError) {
            console.error('ecodeId:${appId}，开发者工具查询日志级别失败', requestError);
            if (mounted.current) {
                setCurrent('');
                setError(`获取当前日志级别失败：${requestError.message || '请稍后重试'}`);
            }
        }
    }

    /** 刷新当前日志级别；互斥锁防止重复请求。 */
    async function refresh() {
        if (inFlight.current) {
            return;
        }
        inFlight.current = true;
        setBusy(true);
        setError('');
        setSuccess('');
        try {
            await loadCurrent();
        } finally {
            inFlight.current = false;
            if (mounted.current) {
                setBusy(false);
            }
        }
    }

    /** 提交选择的日志级别，保存成功后重新查询后端配置。 */
    async function submit() {
        const blocked = inFlight.current || !LOG_LEVELS.includes(level);
        if (blocked) {
            return;
        }
        inFlight.current = true;
        setBusy(true);
        setError('');
        setSuccess('');
        try {
            const message = await saveLogLevel(level);
            if (mounted.current) {
                setSuccess(message);
                await loadCurrent();
            }
        } catch (requestError) {
            console.error('ecodeId:${appId}，开发者工具修改日志级别失败', requestError);
            if (mounted.current) {
                setError(`修改日志级别失败：${requestError.message || '请稍后重试'}`);
            }
        } finally {
            inFlight.current = false;
            if (mounted.current) {
                setBusy(false);
            }
        }
    }

    const submitDisabled = busy || !current || !LOG_LEVELS.includes(level);
    const hasError = Boolean(error);
    const hasSuccess = Boolean(success);
    const currentText = current || '暂时无法获取当前日志级别';

    return (
        <section className="appid-${appId} seconddev-ecode" style={styles.card} aria-busy={busy}>
            <p style={styles.description}>查看和调整二次开发服务的日志级别配置。</p>
            <Spin
                wrapperClassName="appid-${appId} seconddev-ecode"
                spinning={busy}
                text="正在处理日志配置…"
            >
                <Layout.Row className="appid-${appId} seconddev-ecode" align="middle" style={styles.formRow}>
                    <Layout.Col className="appid-${appId} seconddev-ecode" flex="156px">
                        当前日志级别配置
                    </Layout.Col>
                    <Layout.Col className="appid-${appId} seconddev-ecode" flex="auto">
                        <span role="status">{currentText}</span>
                    </Layout.Col>
                </Layout.Row>
                <Layout.Row className="appid-${appId} seconddev-ecode" align="middle" style={styles.formRow}>
                    <Layout.Col className="appid-${appId} seconddev-ecode" flex="156px">
                        <span id="dev-tools-level-label">修改日志级别</span>
                    </Layout.Col>
                    <Layout.Col className="appid-${appId} seconddev-ecode" flex="auto">
                        <Select
                            className="appid-${appId} seconddev-ecode"
                            aria-labelledby="dev-tools-level-label"
                            style={{width: 280}}
                            data={OPTIONS}
                            value={level}
                            onChange={setLevel}
                            disabled={busy}
                            allowClear={false}
                            placeholder="请选择日志级别"
                        />
                    </Layout.Col>
                </Layout.Row>
                <Layout.Row className="appid-${appId} seconddev-ecode" gutter={12} style={styles.actions}>
                    <Layout.Col className="appid-${appId} seconddev-ecode">
                        <Button className="appid-${appId} seconddev-ecode" type="primary" disabled={submitDisabled} onClick={submit}>
                            提交
                        </Button>
                    </Layout.Col>
                    <Layout.Col className="appid-${appId} seconddev-ecode">
                        <Button className="appid-${appId} seconddev-ecode" disabled={busy} onClick={refresh}>
                            刷新
                        </Button>
                    </Layout.Col>
                </Layout.Row>
            </Spin>
            {hasError && <p role="alert" style={styles.error}>{error}</p>}
            {hasSuccess && <p role="status" style={styles.success}>{success}</p>}
        </section>
    );
}
