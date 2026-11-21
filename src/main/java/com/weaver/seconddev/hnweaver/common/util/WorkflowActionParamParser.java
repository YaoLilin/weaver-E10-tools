package com.weaver.seconddev.hnweaver.common.util;

import cn.hutool.core.util.StrUtil;
import com.weaver.seconddev.hnweaver.common.AbstractEsbAction;
import com.weaver.seconddev.hnweaver.common.constants.ActionParam;
import com.weaver.seconddev.hnweaver.common.constants.ParamType;
import com.weaver.seconddev.hnweaver.common.domain.dto.ActionParamDTO;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author 姚礼林
 * @desc 动作流 Acton 参数解析工具类
 * 解析规则：
 * 1. 简单类型（String/数值/Boolean）与 List 字段默认作为参数，List 的元素对象内的属性也会被解析为 Action 参数；
 * 2. Map 字段作为对象参数解析，不解析其键和值类型；静态字段和合成字段不会被解析。
 * 3. 其它嵌套对象会解析为 Action 参数
 * @date 2026/1/15
 **/
@Slf4j
@UtilityClass
public class WorkflowActionParamParser {

    /**
     * 解析 Action 的输入参数
     *
     * @param clazz AbstractEsbAction 的子类
     * @return 参数列表
     */
    public static List<ActionParamDTO> parseInputParams(Class<? extends AbstractEsbAction<?, ?>> clazz){
        List<ActionParamDTO> result = new ArrayList<>();

        try {
            Class<?> paramType = EsbActionGenericTypeUtil.getParamClass(clazz);
            if (paramType != null) {
                result = parseParams(paramType);
            }
        } catch (Exception e) {
            log.error("解析 Action 参数失败: {}", clazz.getName(), e);
        }

        return result;
    }

    /**
     * 解析 Action 的输出参数
     *
     * @param clazz AbstractEsbAction 的子类
     * @return 参数列表
     */
    public static List<ActionParamDTO> parseOutputParams(Class<? extends AbstractEsbAction<?, ?>> clazz) {
        List<ActionParamDTO> result = new ArrayList<>();

        try {
            Class<?> resultType = EsbActionGenericTypeUtil.getResultClass(clazz);
            if (resultType != null) {
                result = parseParams(resultType);
            }
        } catch (Exception e) {
            log.error("解析 Action 输出参数失败: {}", clazz.getName(), e);
        }

        return result;
    }

    /**
     * 解析参数类型中的字段，包括父类中的字段
     *
     * @param paramType 参数类型
     * @return 参数列表
     */
    public static List<ActionParamDTO> parseParams(Class<?> paramType) {
        return parseParams(paramType, new HashSet<>());
    }

    /**
     * 沿当前字段路径追踪类型，阻止递归回到祖先，同时允许同一类型出现在不同字段分支。
     */
    private static List<ActionParamDTO> parseParams(Class<?> paramType, Set<Class<?>> path) {
        List<ActionParamDTO> result = new ArrayList<>();
        if (paramType == null || !path.add(paramType)) {
            return result;
        }
        try {
            for (Class<?> current = paramType; current != null && current != Object.class;
                    current = current.getSuperclass()) {
                for (Field field : current.getDeclaredFields()) {
                    if (!shouldSkip(field)) {
                        result.add(toParameter(field, path));
                    }
                }
            }
            return result;
        } finally {
            path.remove(paramType);
        }
    }

    /**
     * 构造单个参数的注解、类型及子字段描述。
     */
    private static ActionParamDTO toParameter(Field field, Set<Class<?>> path) {
        ActionParamDTO dto = new ActionParamDTO();
        dto.setName(field.getName());
        ActionParam annotation = field.getAnnotation(ActionParam.class);
        dto.setShowName(annotation != null && StrUtil.isNotBlank(annotation.displayName())
                ? annotation.displayName() : field.getName());
        dto.setRequired(annotation != null && annotation.required());
        if (annotation != null) {
            dto.setDefaultValue(annotation.defaultValue());
            dto.setDesc(annotation.desc());
        }

        Class<?> fieldType = field.getType();
        boolean list = List.class.isAssignableFrom(fieldType);
        Class<?> valueType = list ? getListElementType(field) : fieldType;
        if (fieldType.isArray()) {
            valueType = fieldType.getComponentType();
        }
        dto.setType(getTypeValue(valueType));
        dto.setJavaType(valueType.getName());
        dto.setArray(list || fieldType.isArray());
        if (!valueType.isPrimitive() && !isSimpleType(valueType) && !Map.class.isAssignableFrom(valueType)) {
            List<ActionParamDTO> children = parseParams(valueType, path);
            if (!children.isEmpty()) {
                dto.setChildren(children);
            }
        }
        return dto;
    }

    /**
     * 获取 List 字段的元素类型。<br>
     * <p>
     * 未声明或无法解析泛型时按 Object 处理，使前端以 JSON 数组展示。
     * </p>
     *
     * @param field List 类型字段
     * @return List 元素类型
     */
    private static Class<?> getListElementType(Field field) {
        Type genericType = field.getGenericType();
        if (genericType instanceof ParameterizedType) {
            Type[] actualTypeArguments = ((ParameterizedType) genericType).getActualTypeArguments();
            if (actualTypeArguments.length > 0 && actualTypeArguments[0] instanceof Class) {
                return (Class<?>) actualTypeArguments[0];
            }
        }
        return Object.class;
    }

    private static boolean shouldSkip(Field field) {
        int modifiers = field.getModifiers();
        // 排除静态字段和合成字段
        return Modifier.isStatic(modifiers) || field.isSynthetic();
    }

    private static boolean isSimpleType(Class<?> type) {
        return type == String.class || type == Integer.class || type == int.class ||
                type == Boolean.class || type == boolean.class ||
                type == Double.class || type == double.class ||
                type == Float.class || type == float.class ||
                type == BigDecimal.class || type == Long.class || type == long.class;
    }

    private static ParamType getTypeValue(Class<?> type) {
        if (type == Integer.class || type == int.class ||
                type == Double.class || type == double.class ||
                type == Float.class || type == float.class ||
                type == BigDecimal.class) {
            return ParamType.NUMBER;
        } else if (type == Boolean.class || type == boolean.class) {
            return ParamType.BOOLEAN;
        } else if (!isSimpleType(type)) {
            return ParamType.OBJECT;
        }
        return ParamType.STRING;
    }
}
