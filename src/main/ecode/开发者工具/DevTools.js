import React, {useRef, useState} from 'react';
import {Menu} from '@weapp/ui';
import LogPage from './coms/LogPage';
import {usePageHeight} from './utils/usePageHeight';
import {styles} from './styles/layout';

const MENUS = [{id: 'log', content: '日志'}];

/**
 * 开发者工具独立页面，管理菜单选择与右侧页面切换。无外部参数。
 * @returns {React.ReactElement} 开发者工具页面。
 */
export default function DevTools() {
    const [menu, setMenu] = useState('log');
    const pageRef = useRef(null);
    const height = usePageHeight(pageRef);
    const showLog = menu === 'log';

    return (
        <div ref={pageRef} className="appid-${appId} seconddev-ecode" style={{...styles.page, height}}>
            <header style={styles.header}>
                <p style={styles.subtitle}>开发配置与调试工具</p>
            </header>
            <div style={styles.body}>
                <aside aria-label="工具菜单" style={styles.sidebar}>
                    <div style={styles.menuTitle}>工具菜单</div>
                    <Menu
                        className="appid-${appId} seconddev-ecode"
                        type="menu"
                        mode="inline"
                        data={MENUS}
                        value={menu}
                        onChange={setMenu}
                    />
                </aside>
                <main style={styles.content}>
                    {showLog && <LogPage />}
                </main>
            </div>
        </div>
    );
}
