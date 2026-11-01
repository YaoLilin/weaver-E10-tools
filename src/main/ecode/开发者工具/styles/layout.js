/** 开发者工具布局样式，仅通过组件内联应用，使用平台公共样式变量。 */
export const styles = {
    page: {
        display: 'flex', flexDirection: 'column', overflow: 'hidden',
        background: 'var(--bg-base, #f9f9f9)', color: 'var(--main-fc, #111)',
        fontFamily: 'var(--regular-ff, sans-serif)',
    },
    header: {
        padding: '24px 32px', flexShrink: 0, background: 'var(--base-white, #fff)',
        borderBottom: '1px solid var(--border-color, #e5e5e5)',
    },
    subtitle: {margin: '8px 0 0', color: 'var(--secondary-fc, #999)'},
    body: {display: 'flex', flex: 1, minHeight: 0},
    sidebar: {
        width: 208, flexShrink: 0, paddingTop: 20, overflowY: 'auto',
        background: 'var(--base-white, #fff)', borderRight: '1px solid var(--border-color, #e5e5e5)',
    },
    menuTitle: {padding: '0 24px 16px', color: 'var(--secondary-fc, #999)', fontSize: 12},
    content: {flex: 1, minWidth: 0, padding: 28, overflow: 'auto'},
    card: {
        maxWidth: 860, boxSizing: 'border-box', padding: 32,
        background: 'var(--base-white, #fff)', border: '1px solid var(--border-color, #e5e5e5)',
        borderRadius: 'var(--border-radius-sm, 6px)',
    },
    description: {margin: '12px 0 28px', color: 'var(--regular-fc, #666)', lineHeight: 1.7},
    formRow: {marginBottom: 24, minHeight: 36},
    actions: {paddingLeft: 156},
    error: {color: 'var(--danger, #ff4d4f)', lineHeight: 1.7, margin: '20px 0 0'},
    success: {color: 'var(--success, #52c41a)', lineHeight: 1.7, margin: '20px 0 0'},
};
