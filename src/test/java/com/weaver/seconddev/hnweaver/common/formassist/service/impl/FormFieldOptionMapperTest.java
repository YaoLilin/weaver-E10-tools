package com.weaver.seconddev.hnweaver.common.formassist.service.impl;

import com.weaver.common.form.data.option.FormDataOption;
import com.weaver.common.form.metadata.ModuleSource;
import com.weaver.eteams.file.client.file.FileObj;
import com.weaver.eteams.file.client.constant.FileOption;
import com.weaver.teams.domain.user.SimpleEmployee;
import com.weaver.workflow.common.cfg.org.service.UserService;
import com.weaver.seconddev.hnweaver.common.formassist.domain.dto.FormFieldOptionDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * 验证流程字段选项和附件文件属性的安全投影。
 */
@DisplayName("流程字段选项投影测试")
class FormFieldOptionMapperTest {

    private final UserService userService = mock(UserService.class);
    private final FormFieldOptionMapper mapper = new FormFieldOptionMapper(userService);

    @Test
    @DisplayName("FileObj保留时间与预览下载操作，移除编辑动作和内部扩展信息")
    void shouldProjectOnlyAllowedFileProperties() {
        FormDataOption option = new FormDataOption();
        option.setOptionId("1310177016467636226");
        option.setContent("jointFile (2).docx");
        option.setType(ModuleSource.file);
        FileObj source = new FileObj();
        source.setId(1310177016467636226L);
        source.setFileid(1310177016467636226L);
        source.setName("jointFile (2).docx");
        source.setSize(18149);
        source.setUploadTime(new Date(1790412647000L));
        source.setOptions(Arrays.asList(action("preview", true), action("download", false), action("delete", false)));
        source.setLoadlink("/papi/file/remotedownload/example");
        source.setTenantKey("test-tenant");
        option.setOptionObj(source);

        FormFieldOptionDTO result = mapper.mapOptions(Collections.singletonList(option), "field-id").get(0);

        assertEquals("1310177016467636226", result.getOptionId());
        assertEquals("file", result.getType());
        assertEquals("1310177016467636226", result.getOptionObj().get("id").asText());
        assertEquals("jointFile (2).docx", result.getOptionObj().get("name").asText());
        assertEquals(18149, result.getOptionObj().get("size").asInt());
        assertEquals(1790412647000L, result.getOptionObj().get("uploadTime").asLong());
        assertEquals(2, result.getOptionObj().get("options").size());
        assertEquals("preview", result.getOptionObj().get("options").get(0).get("id").asText());
        assertEquals("download", result.getOptionObj().get("options").get(1).get("id").asText());
        assertFalse(result.getOptionObj().get("options").get(1).get("moreType").asBoolean());
        assertNull(result.getOptionObj().get("options").get(0).get("url"));
        assertNull(result.getOptionObj().get("options").get(0).get("cusMap"));
        assertEquals("/papi/file/remotedownload/example", result.getOptionObj().get("loadlink").asText());
        assertNull(result.getOptionObj().get("tenantKey"));
    }

    @Test
    @DisplayName("上传人姓名缺失时通过同租户上传员工补充，不使用账号名")
    void shouldResolveMissingUploadUserName() {
        FileObj file = new FileObj();
        file.setUploadEmployeeId(981391815338188804L);
        file.setTenantKey("test-tenant");
        file.setUploadName("account-name");
        SimpleEmployee employee = mock(SimpleEmployee.class);
        when(employee.getTenantKey()).thenReturn("test-tenant");
        when(employee.getName()).thenReturn("上传员工");
        when(userService.getSimpleEmployeeById(file.getUploadEmployeeId())).thenReturn(employee);

        FormFieldOptionDTO result = projectFile(file);

        assertEquals("上传员工", result.getOptionObj().get("uploadUserName").asText());
        assertEquals("981391815338188804", result.getOptionObj().get("uploadEmployeeId").asText());
    }

    @Test
    @DisplayName("平台已有上传人姓名时直接保留且不查询员工")
    void shouldPreserveExistingUploadUserName() {
        FileObj file = mock(FileObj.class);
        when(file.getUploadUserName()).thenReturn("原上传人");

        assertEquals("原上传人", projectFile(file).getOptionObj().get("uploadUserName").asText());
        verifyNoInteractions(userService);
    }

    @Test
    @DisplayName("员工不存在或跨租户时不返回姓名，缺少权限时不生成操作")
    void shouldNotExposeOtherTenantEmployeeOrInventActions() {
        FileObj file = new FileObj();
        file.setUploadEmployeeId(42L);
        file.setTenantKey("test-tenant");
        file.setUploadName("account-name");
        assertNull(projectFile(file).getOptionObj().get("uploadUserName"));
        SimpleEmployee employee = mock(SimpleEmployee.class);
        when(employee.getTenantKey()).thenReturn("other-tenant");
        when(employee.getName()).thenReturn("其他租户员工");
        when(userService.getSimpleEmployeeById(42L)).thenReturn(employee);

        FormFieldOptionDTO result = projectFile(file);

        assertNull(result.getOptionObj().get("uploadUserName"));
        assertNull(result.getOptionObj().get("uploadTime"));
        assertEquals(0, result.getOptionObj().get("options").size());
    }

    @Test
    @DisplayName("空附件对象不返回组件属性，禁用标记保留，过长链接丢弃")
    void shouldHandleEmptyFileAndPreserveBan() {
        assertNull(projectFile(null).getOptionObj());
        FileObj file = new FileObj();
        file.setBan(true);
        file.setLoadlink(String.join("", Collections.nCopies(2049, "a")));
        file.setOptions(Arrays.asList(null, action("rename", true)));

        FormFieldOptionDTO result = projectFile(file);

        assertTrue(result.getOptionObj().get("ban").asBoolean());
        assertNull(result.getOptionObj().get("loadlink"));
        assertEquals(0, result.getOptionObj().get("options").size());
        verifyNoInteractions(userService);
    }

    private FormFieldOptionDTO projectFile(FileObj file) {
        FormDataOption option = new FormDataOption();
        option.setType(ModuleSource.file);
        option.setOptionObj(file);
        return mapper.mapOptions(Collections.singletonList(option), "field-id").get(0);
    }

    private FileOption action(String id, boolean moreType) {
        FileOption option = new FileOption();
        option.setId(id);
        option.setIcon("test-icon");
        option.setContent(id);
        option.setMoreType(moreType);
        option.setUrl("/untrusted-action");
        option.setCusMap(Collections.singletonMap("internal", "omitted"));
        return option;
    }

    @Test
    @DisplayName("普通选项不返回平台扩展对象")
    void shouldOmitObjectForNonFileOption() {
        FormDataOption option = new FormDataOption();
        option.setOptionId("42");
        option.setContent("选项A");
        option.setType(ModuleSource.department);
        option.setOptionObj(Collections.singletonMap("tenantKey", "private-tenant"));

        FormFieldOptionDTO result = mapper.mapOptions(Collections.singletonList(option), "field-id").get(0);

        assertEquals("42", result.getOptionId());
        assertNull(result.getOptionObj());
    }

    @Test
    @DisplayName("选项超过上限时截断，空选项忽略")
    void shouldBoundOptionCountAndSkipNull() {
        List<FormDataOption> options = new ArrayList<>(1002);
        options.add(null);
        for (int index = 0; index < 1001; index++) {
            FormDataOption option = new FormDataOption();
            option.setOptionId(String.valueOf(index));
            options.add(option);
        }

        List<FormFieldOptionDTO> result = mapper.mapOptions(options, "field-id");

        assertEquals(999, result.size());
        assertFalse(result.isEmpty());
        assertEquals("998", result.get(998).getOptionId());
        assertTrue(mapper.mapOptions(null, "field-id").isEmpty());
    }
}
