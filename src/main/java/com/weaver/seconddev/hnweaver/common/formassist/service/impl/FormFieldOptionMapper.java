package com.weaver.seconddev.hnweaver.common.formassist.service.impl;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import cn.hutool.core.util.StrUtil;
import com.weaver.common.form.data.option.FormDataOption;
import com.weaver.common.form.metadata.ModuleSource;
import com.weaver.eteams.file.client.file.FileObj;
import com.weaver.eteams.file.client.constant.FileOption;
import com.weaver.seconddev.hnweaver.common.formassist.domain.dto.FormFieldOptionDTO;
import com.weaver.teams.domain.user.SimpleEmployee;
import com.weaver.workflow.common.cfg.org.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 为流程表单助手保留字段选项值，并提取附件组件显示所需的文件属性。
 *
 * <p>平台选项对象可能反向引用表单数据，不能直接放入接口响应。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FormFieldOptionMapper {

    private static final int MAX_OPTIONS = 1000;

    private static final int MAX_PROPERTY_LENGTH = 2048;

    private static final int MAX_FILE_ACTIONS = 20;

    private final UserService userService;

    /**
     * 把平台字段选项投影为有数量上限的接口值。
     *
     * @param options 平台 RPC 返回的字段选项
     * @param fieldId 字段 ID，仅用于安全日志
     * @return 可供表单组件使用的选项值
     */
    public List<FormFieldOptionDTO> mapOptions(List<FormDataOption> options, String fieldId) {
        boolean missingOptions = options == null || options.isEmpty();
        if (missingOptions) {
            return Collections.emptyList();
        }
        int count = Math.min(options.size(), MAX_OPTIONS);
        if (options.size() > MAX_OPTIONS) {
            log.warn("表单字段选项超过返回上限，fieldId={}，total={}，limit={}", fieldId, options.size(), MAX_OPTIONS);
        }
        List<FormFieldOptionDTO> mapped = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            FormDataOption option = options.get(index);
            if (option == null) {
                continue;
            }
            FormFieldOptionDTO item = new FormFieldOptionDTO();
            item.setOptionId(option.getOptionId());
            item.setContent(option.getContent());
            ModuleSource type = option.getType();
            item.setType(type == null ? null : type.name());
            boolean fileOption = ModuleSource.file == type || ModuleSource.imageFile == type || ModuleSource.image == type;
            if (fileOption) {
                item.setOptionObj(mapFileProperties(option.getOptionObj()));
            }
            mapped.add(item);
        }
        return mapped;
    }

    /**
     * 从已核实的 FileObj 复制附件只读组件所需的元数据和查看操作。
     *
     * @param source 平台选项内的文件对象
     * @return 白名单文件属性；不可读取时为 null
     */
    private ObjectNode mapFileProperties(Object source) {
        if (!(source instanceof FileObj)) {
            if (source != null) {
                log.warn("附件选项类型不是 FileObj，type={}", source.getClass().getName());
            }
            return null;
        }
        FileObj file = (FileObj) source;
        ObjectNode projected = JsonNodeFactory.instance.objectNode();
        writeScalar(projected, "id", file.getId());
        writeScalar(projected, "refId", file.getRefId());
        writeScalar(projected, "fileid", file.getFileid());
        writeScalar(projected, "name", file.getName());
        writeScalar(projected, "type", file.getType());
        writeScalar(projected, "size", file.getSize());
        writeScalar(projected, "url", file.getUrl());
        writeScalar(projected, "encryptId", file.getEncryptId());
        writeScalar(projected, "extName", file.getExtName());
        writeScalar(projected, "doc", file.isDoc());
        writeScalar(projected, "img", file.getImg());
        writeScalar(projected, "docId", file.getDocId());
        writeScalar(projected, "fileStatus", file.getFileStatus());
        writeScalar(projected, "image", file.isImage());
        writeScalar(projected, "docType", file.getDocType());
        writeScalar(projected, "hasRef", file.isHasRef());
        writeScalar(projected, "qysFile", file.isQysFile());
        fillUploadProperties(projected, file);
        projected.set("options", mapReadOnlyActions(file));
        log.debug("附件组件属性转换完成，fileId={}，有上传时间={}，有上传人={}",
                file.getId(), projected.has("uploadTime"), projected.has("uploadUserName"));
        return projected;
    }

    /** 补充附件上传时间、上传人及平台返回的链接，不生成新的文件权限。 */
    private void fillUploadProperties(ObjectNode projected, FileObj file) {
        if (file.getUploadTime() != null) {
            projected.put("uploadTime", file.getUploadTime().getTime());
        }
        writeScalar(projected, "uploadUser", file.getUploadUser());
        writeScalar(projected, "uploadEmployeeId", file.getUploadEmployeeId());
        writeScalar(projected, "uploadName", file.getUploadName());
        resolveUploadUserName(file).ifPresent(name -> writeScalar(projected, "uploadUserName", name));
        writeScalar(projected, "loadlink", file.getLoadlink());
        writeScalar(projected, "signTip", file.getSignTip());
        writeScalar(projected, "ban", file.isBan());
        writeScalar(projected, "module", file.getModule() == null ? null : file.getModule().name());
    }

    /**
     * 获取附件上传人的显示姓名；有上传员工身份时优先查询，拒绝跨租户姓名。
     * <p>当前平台 getUploadUserName() 实际返回 uploadName，不能据此跳过员工查询。
     * @param file 平台附件对象
     * @return 上传人姓名，员工不存在或租户不匹配时为空
     */
    private Optional<String> resolveUploadUserName(FileObj file) {
        boolean missingEmployeeContext = file.getUploadEmployeeId() == null || StrUtil.isBlank(file.getTenantKey());
        if (missingEmployeeContext) {
            return Optional.ofNullable(file.getUploadUserName()).filter(StrUtil::isNotBlank);
        }
        log.debug("查询附件上传员工姓名，fileId={}，employeeId={}", file.getId(), file.getUploadEmployeeId());
        SimpleEmployee employee = userService.getSimpleEmployeeById(file.getUploadEmployeeId());
        boolean sameTenant = employee != null && Objects.equals(file.getTenantKey(), employee.getTenantKey());
        if (!sameTenant) {
            log.warn("附件上传员工不存在或租户不匹配，fileId={}，employeeId={}",
                    file.getId(), file.getUploadEmployeeId());
            return Optional.empty();
        }
        log.debug("附件上传员工查询完成，employeeId={}，有显示姓名={}",
                file.getUploadEmployeeId(), StrUtil.isNotBlank(employee.getName()));
        return Optional.ofNullable(employee.getName()).filter(StrUtil::isNotBlank);
    }

    /** 仅投影平台实际返回的预览和下载操作，移除编辑动作、URL 与扩展对象。 */
    private ArrayNode mapReadOnlyActions(FileObj file) {
        ArrayNode actions = JsonNodeFactory.instance.arrayNode();
        List<FileOption> options = file.getOptions();
        if (options == null) {
            return actions;
        }
        int count = Math.min(options.size(), MAX_FILE_ACTIONS);
        for (int index = 0; index < count; index++) {
            FileOption option = options.get(index);
            boolean viewAction = option != null
                    && ("preview".equals(option.getId()) || "download".equals(option.getId()));
            if (!viewAction) {
                continue;
            }
            ObjectNode action = actions.addObject();
            writeScalar(action, "id", option.getId());
            writeScalar(action, "icon", option.getIcon());
            writeScalar(action, "content", option.getContent());
            writeScalar(action, "moreType", option.getMoreType());
        }
        return actions;
    }

    /**
     * 把安全的标量文件属性写入接口值，拒绝嵌套对象和过长字符串。
     *
     * @param target 接口值对象
     * @param propertyName 已允许的属性名
     * @param value 平台属性值
     */
    private void writeScalar(ObjectNode target, String propertyName, Object value) {
        if (value == null) {
            return;
        }
        if (value instanceof String) {
            String text = (String) value;
            if (text.length() <= MAX_PROPERTY_LENGTH) {
                target.put(propertyName, text);
            }
            return;
        }
        if (value instanceof Boolean) {
            target.put(propertyName, (Boolean) value);
            return;
        }
        if (value instanceof Number) {
            boolean identifier = "id".equals(propertyName) || "refId".equals(propertyName)
                    || "fileid".equals(propertyName) || "docId".equals(propertyName)
                    || "uploadUser".equals(propertyName) || "uploadEmployeeId".equals(propertyName);
            boolean floatingPoint = value instanceof Float || value instanceof Double;
            if (identifier) {
                target.put(propertyName, value.toString());
            } else if (floatingPoint) {
                target.put(propertyName, ((Number) value).doubleValue());
            } else {
                target.put(propertyName, ((Number) value).longValue());
            }
        }
    }
}
