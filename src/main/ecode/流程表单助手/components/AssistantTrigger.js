import React, {useEffect, useRef, useState} from 'react';
import {Icon} from '@weapp/ui';

const BUTTON_SIZE = 38;
const POSITION_STORAGE_KEY = 'seconddev-flow-form-assistant-position';

/**
 * 限制悬浮图标位置在浏览器可视区域内。
 *
 * @param {{left: number, top: number}} position 悬浮图标位置
 * @returns {{left: number, top: number}} 可视区域内的位置
 */
function clampPosition(position) {
    const maxLeft = Math.max(0, window.innerWidth - BUTTON_SIZE);
    const maxTop = Math.max(0, window.innerHeight - BUTTON_SIZE);
    return {
        left: Math.min(Math.max(0, position.left), maxLeft),
        top: Math.min(Math.max(0, position.top), maxTop),
    };
}

/**
 * 读取悬浮图标的本地保存位置。
 *
 * @returns {{left: number, top: number}} 悬浮图标初始位置
 */
function getInitialPosition() {
    const defaultPosition = {
        left: Math.max(0, window.innerWidth - BUTTON_SIZE - 24),
        top: Math.max(0, window.innerHeight - BUTTON_SIZE - 24),
    };

    try {
        const storedPosition = JSON.parse(window.localStorage.getItem(POSITION_STORAGE_KEY));
        if (storedPosition
            && Number.isFinite(storedPosition.left)
            && Number.isFinite(storedPosition.top)) {
            return clampPosition(storedPosition);
        }
    } catch (error) {
        console.error('[ecode:${appId}-流程表单助手]:读取流程表单助手悬浮图标位置失败', error);
    }

    return defaultPosition;
}

/**
 * 保存悬浮图标位置。
 *
 * @param {{left: number, top: number}} position 悬浮图标位置
 */
function savePosition(position) {
    try {
        window.localStorage.setItem(POSITION_STORAGE_KEY, JSON.stringify(position));
    } catch (error) {
        console.error('[ecode:${appId}-流程表单助手]:保存流程表单助手悬浮图标位置失败', error);
    }
}

/**
 * 展示表单助手悬浮按钮，管理拖动、位置记忆、窗口边界和拖动后防误点击。
 * @param {{title: string, onOpen: Function}} props 助手名称及正常点击时的打开回调
 * @returns {React.ReactElement} 悬浮按钮
 */
const AssistantTrigger = ({title, onOpen}) => {
    const [position, setPosition] = useState(getInitialPosition);
    const dragStateRef = useRef(null);
    const suppressClickRef = useRef(false);

    useEffect(() => {
        const keepPositionInViewport = () => setPosition(currentPosition => clampPosition(currentPosition));
        window.addEventListener('resize', keepPositionInViewport);
        return () => window.removeEventListener('resize', keepPositionInViewport);
    }, []);

    /**
     * 记录开始拖动悬浮图标的位置。
     *
     * @param {React.PointerEvent<HTMLButtonElement>} event 指针事件
     */
    const startDragging = event => {
        if (event.button !== 0) {
            return;
        }

        suppressClickRef.current = false;
        dragStateRef.current = {
            offsetLeft: event.clientX - position.left,
            offsetTop: event.clientY - position.top,
            startX: event.clientX,
            startY: event.clientY,
        };
        if (event.currentTarget.setPointerCapture) {
            event.currentTarget.setPointerCapture(event.pointerId);
        }
    };

    /**
     * 更新悬浮图标拖动位置。
     *
     * @param {React.PointerEvent<HTMLButtonElement>} event 指针事件
     */
    const drag = event => {
        const dragState = dragStateRef.current;
        if (!dragState) {
            return;
        }

        if (Math.abs(event.clientX - dragState.startX) > 3 || Math.abs(event.clientY - dragState.startY) > 3) {
            suppressClickRef.current = true;
        }
        const nextPosition = clampPosition({
            left: event.clientX - dragState.offsetLeft,
            top: event.clientY - dragState.offsetTop,
        });
        dragState.position = nextPosition;
        setPosition(nextPosition);
    };

    /**
     * 结束拖动并记住悬浮图标位置。
     */
    const stopDragging = () => {
        if (!dragStateRef.current) {
            return;
        }

        const lastPosition = dragStateRef.current.position || position;
        dragStateRef.current = null;
        if (suppressClickRef.current) {
            savePosition(lastPosition);
            console.log('[ecode:${appId}-流程表单助手]:流程表单助手悬浮图标位置已保存', lastPosition);
        }
    };

    /**
     * 处理悬浮图标点击；拖动结束后的点击不展开抽屉。
     */
    const handleTriggerClick = () => {
        if (suppressClickRef.current) {
            suppressClickRef.current = false;
            return;
        }
        onOpen();
    };

    return (
        <button
            aria-label={`打开${title}`}
            className={'appid-${appId} seconddev-ecode flow-form-assistant-trigger ffa-trigger' + (dragStateRef.current ? ' is-dragging' : '')}
            onClick={handleTriggerClick}
            onPointerCancel={stopDragging}
            onPointerDown={startDragging}
            onPointerMove={drag}
            onPointerUp={stopDragging}
            style={{'--assistant-left': `${position.left}px`, '--assistant-top': `${position.top}px`}}
            type="button"
        >
            <Icon name="Icon-Robot2" size="md" className="ffa-trigger-icon" />
        </button>
    );
};

export default AssistantTrigger;
