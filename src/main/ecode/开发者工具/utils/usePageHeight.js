import {useLayoutEffect, useState} from 'react';

/**
 * 根据页面顶部位置计算可用高度，并在窗口变化时重新测量。
 * @param {Object} ref 页面根元素引用。
 * @returns {number} 页面可用高度。
 */
export function usePageHeight(ref) {
    const [height, setHeight] = useState(0);
    useLayoutEffect(() => {
        const measure = () => {
            const top = ref.current.getBoundingClientRect().top;
            const bottomGap = parseFloat(getComputedStyle(document.body).marginBottom) || 0;
            setHeight(Math.max(0, document.documentElement.clientHeight - top - bottomGap));
        };
        measure();
        window.addEventListener('resize', measure);
        return () => window.removeEventListener('resize', measure);
    }, [ref]);
    return height;
}
