/**
 * 复制信息值到剪贴板，兼容未启用 Clipboard API 的 HTTP 页面。
 * @param {string} text 需要复制的原始文本
 * @returns {Promise<void>} 完成复制
 */
export async function copyText(text) {
    if (window.isSecureContext && navigator.clipboard && navigator.clipboard.writeText) {
        await navigator.clipboard.writeText(text);
        return;
    }
    const activeElement = document.activeElement;
    const selection = window.getSelection();
    const ranges = selection ? Array.from({length: selection.rangeCount}, (_, index) => selection.getRangeAt(index).cloneRange()) : [];
    const input = document.createElement('textarea');
    input.value = text;
    input.setAttribute('readonly', '');
    input.style.cssText = 'position:fixed;left:-9999px;top:0;opacity:0';
    document.body.appendChild(input);
    try {
        input.select();
        if (!document.execCommand('copy')) {
            throw new Error('浏览器未允许写入剪贴板');
        }
    } finally {
        input.remove();
        if (activeElement && activeElement.focus) {
            activeElement.focus({preventScroll: true});
        }
        if (selection) {
            selection.removeAllRanges();
            ranges.forEach(range => selection.addRange(range));
        }
    }
}
