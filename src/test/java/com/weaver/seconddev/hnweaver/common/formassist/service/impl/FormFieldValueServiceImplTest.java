package com.weaver.seconddev.hnweaver.common.formassist.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.weaver.common.form.data.FormData;
import com.weaver.common.form.api.rest.FormRestFactory;
import com.weaver.common.form.api.rest.data.FormDataRest;
import com.weaver.common.form.data.detail.FileDetail;
import com.weaver.common.form.data.detail.FormDataDetail;
import com.weaver.common.form.data.option.FormDataOption;
import com.weaver.common.form.data.text.FormDataText;
import com.weaver.common.form.dto.field.simple.SimpleFormFieldDto;
import com.weaver.common.form.metadata.ModuleSource;
import com.weaver.common.form.metadata.field.FormField;
import com.weaver.eteams.file.client.file.FileObj;
import com.weaver.workflow.common.cfg.org.service.UserService;
import com.weaver.seconddev.hnweaver.common.constants.RpcGroup;
import com.weaver.seconddev.hnweaver.common.exception.FormAssistException;
import com.weaver.seconddev.hnweaver.common.formassist.domain.dto.FormDetailRowDTO;
import com.weaver.seconddev.hnweaver.common.formassist.domain.dto.FormDetailTableDTO;
import com.weaver.seconddev.hnweaver.common.formassist.domain.dto.FormFieldDTO;
import com.weaver.seconddev.hnweaver.common.formassist.domain.dto.FormFieldDataDTO;
import com.weaver.seconddev.hnweaver.common.util.FieldRpcUtil;
import com.weaver.teams.domain.user.SimpleEmployee;
import com.weaver.teams.security.context.UserContext;
import com.weaver.workflow.core.api.rest.publicapi.WfcFormDataRest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 表单全部字段值查询服务测试。
 *
 * @author 姚礼林
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("表单全部字段值查询测试")
class FormFieldValueServiceImplTest {

    private static final long FORM_ID = 1151612316977602563L;
    private static final long DATA_ID = 1316376329162014723L;
    private static final long TEXT_FIELD_ID = 1151612316977602601L;
    private static final long NUMBER_FIELD_ID = 1151612316977602602L;
    private static final long SUB_FORM_ID = 1151612316977602611L;
    private static final long COLUMN_ONE_ID = 1151612316977602612L;
    private static final long COLUMN_TWO_ID = 1151612316977602613L;
    private static final long ROW_ID = 1151612316977602700L;
    private static final long UNKNOWN_FIELD_ID = 1151612316977602999L;

    @Mock
    private FieldRpcUtil fieldRpcUtil;

    @Mock
    private WfcFormDataRest wfcFormDataRest;

    @Mock
    private FormRestFactory formRestFactory;

    @Mock
    private FormDataRest ebFormDataRest;

    @Spy
    private FormFieldComponentConfigMapper componentConfigMapper = new FormFieldComponentConfigMapper(new ObjectMapper());

    @Spy
    private FormFieldOptionMapper fieldOptionMapper = new FormFieldOptionMapper(
            Mockito.mock(UserService.class));

    @Spy
    private FormFieldValueMapper fieldValueMapper = new FormFieldValueMapper(fieldOptionMapper);

    @InjectMocks
    private FormFieldValueServiceImpl formFieldValueService;

    private MockedStatic<UserContext> userContext;

    @BeforeEach
    void setUp() throws Exception {
        // 被测类只有一个构造方法（注入 FieldRpcUtil），Mockito 走构造注入后不再做字段注入，
        // 因此 @RpcReference 标注的字段需在此显式注入
        Field rpcField = FormFieldValueServiceImpl.class.getDeclaredField("wfcFormDataRest");
        rpcField.setAccessible(true);
        rpcField.set(formFieldValueService, wfcFormDataRest);
        userContext = Mockito.mockStatic(UserContext.class);
    }

    @AfterEach
    void tearDown() {
        userContext.close();
    }


    @Test
    @DisplayName("EB 表单按建模服务读取完整字段库、布局外值及明细数据")
    void shouldReadFullEbuilderForm() {
        SimpleEmployee operator = sysadminUser();
        userContext.when(UserContext::getCurrentUser).thenReturn(operator);
        when(fieldRpcUtil.listFieldLibrary(RpcGroup.EBUILDER_FORM, FORM_ID, operator)).thenReturn(Arrays.asList(
                mainField(TEXT_FIELD_ID, "outside_layout", "布局外字段"), detailTableEntry()));
        when(fieldRpcUtil.getFieldMapByForm(RpcGroup.EBUILDER_FORM, FORM_ID, operator))
                .thenReturn(Collections.emptyMap());
        when(formRestFactory.getFormDataRest("ebuilderform")).thenReturn(ebFormDataRest);
        when(ebFormDataRest.getFullFormData(DATA_ID, operator)).thenReturn(formData(Arrays.asList(
                fieldValue(TEXT_FIELD_ID, "布局外值"), rowValue(COLUMN_ONE_ID, 1L, "明细值"))));

        FormFieldDataDTO result = formFieldValueService.getFormFieldData(FORM_ID, DATA_ID, "ebuilderform");

        assertEquals("布局外值", result.getMainFields().get(0).getValue().getContent());
        assertEquals(1, result.getDetailTables().get(0).getRows().size());
        verify(fieldRpcUtil).getFieldMapByForm(RpcGroup.EBUILDER_FORM, FORM_ID, operator);
        verify(ebFormDataRest).getFullFormData(DATA_ID, operator);
        verifyNoInteractions(wfcFormDataRest);
    }

    @Test
    @DisplayName("拒绝未知表单类型，不访问任何平台数据服务")
    void shouldRejectUnknownModule() {
        userContext.when(UserContext::getCurrentUser).thenReturn(sysadminUser());
        assertThrows(FormAssistException.class,
                () -> formFieldValueService.getFormFieldData(FORM_ID, DATA_ID, "unknown"));
        verifyNoInteractions(fieldRpcUtil, formRestFactory, wfcFormDataRest);
    }

    @Test
    @DisplayName("非管理员不能读取 EB 表单数据")
    void shouldRejectEbuilderReadWithoutAdmin() {
        userContext.when(UserContext::getCurrentUser).thenReturn(new SimpleEmployee());
        assertThrows(FormAssistException.class,
                () -> formFieldValueService.getFormFieldData(FORM_ID, DATA_ID, "ebuilderform"));
        verifyNoInteractions(fieldRpcUtil, formRestFactory, ebFormDataRest);
    }

    @Test
    @DisplayName("EB 数据服务不可用时返回明确异常")
    void shouldRejectMissingEbuilderService() {
        SimpleEmployee operator = sysadminUser();
        userContext.when(UserContext::getCurrentUser).thenReturn(operator);
        when(fieldRpcUtil.listFieldLibrary(RpcGroup.EBUILDER_FORM, FORM_ID, operator))
                .thenReturn(Collections.singletonList(mainField(TEXT_FIELD_ID, "text", "文本")));
        assertThrows(FormAssistException.class,
                () -> formFieldValueService.getFormFieldData(FORM_ID, DATA_ID, "ebuilderform"));
        verifyNoInteractions(wfcFormDataRest);
    }

    @Test
    @DisplayName("系统管理员查询：返回字段库全部主表字段、明细列与字段值")
    void shouldReturnAllFieldsAndValuesForSysadmin() {
        SimpleEmployee sysadmin = sysadminUser();
        userContext.when(UserContext::getCurrentUser).thenReturn(sysadmin);
        when(fieldRpcUtil.listFieldLibrary(RpcGroup.WORKFLOW, FORM_ID, sysadmin)).thenReturn(Arrays.asList(
                mainField(TEXT_FIELD_ID, "txt_a", "文本A"),
                mainField(NUMBER_FIELD_ID, "sz_b", "数字B"),
                detailTableEntry()));
        FormField textMetadata = new FormField();
        textMetadata.setColumnName("TEXT_COLUMN");
        textMetadata.setOtherProperties("{\"componentKey\":\"Text\",\"name\":\"单行文本\"}");
        FormField columnMetadata = new FormField();
        columnMetadata.setColumnName("DETAIL_COLUMN");
        columnMetadata.setOtherProperties("{\"componentKey\":\"Text\",\"name\":\"单行文本\"}");
        // key 为字段 ID，value 为测试用的字段元数据。
        Map<Long, FormField> fieldMetadata = new HashMap<>(2);
        fieldMetadata.put(TEXT_FIELD_ID, textMetadata);
        fieldMetadata.put(COLUMN_ONE_ID, columnMetadata);
        when(fieldRpcUtil.getFieldMapByForm(RpcGroup.WORKFLOW, FORM_ID, sysadmin)).thenReturn(fieldMetadata);
        when(wfcFormDataRest.getFullFormData(DATA_ID, sysadmin)).thenReturn(formData(Arrays.asList(
                fieldValue(TEXT_FIELD_ID, "主表值A"),
                emptyValue(NUMBER_FIELD_ID),
                rowValue(COLUMN_ONE_ID, 1L, "明细值1"))));

        FormFieldDataDTO result = formFieldValueService.getFormFieldData(FORM_ID, DATA_ID);

        assertEquals(Long.valueOf(FORM_ID), result.getFormId());
        assertEquals(Long.valueOf(DATA_ID), result.getDataId());
        assertEquals(2, result.getMainFields().size());

        FormFieldDTO textField = result.getMainFields().get(0);
        assertEquals(String.valueOf(TEXT_FIELD_ID), textField.getFieldId());
        assertEquals("txt_a", textField.getDataKey());
        assertEquals("TEXT_COLUMN", textField.getColumnName());
        assertEquals("单行文本", textField.getComponentConfig().get("name").asText());
        assertEquals("文本A", textField.getTitle());
        assertNull(textField.getSubFormId());
        assertTrue(textField.getHasValue());
        assertNotNull(textField.getValue());
        assertEquals("主表值A", textField.getValue().getDisplayText());

        FormFieldDTO numberField = result.getMainFields().get(1);
        assertFalse(numberField.getHasValue());
        assertNotNull(numberField.getValue());
        assertEquals("", numberField.getValue().getDisplayText());

        assertEquals(1, result.getDetailTables().size());
        FormDetailTableDTO detailTable = result.getDetailTables().get(0);
        assertEquals(String.valueOf(SUB_FORM_ID), detailTable.getSubFormId());
        assertEquals("明细表1", detailTable.getTitle());
        assertEquals(2, detailTable.getColumns().size());
        assertNull(detailTable.getColumns().get(0).getValue());
        assertEquals(Integer.valueOf(1), detailTable.getTotalRows());
        assertFalse(detailTable.getTruncated());

        assertEquals(1, detailTable.getRows().size());
        FormDetailRowDTO row = detailTable.getRows().get(0);
        assertEquals(Long.valueOf(1L), row.getDataIndex());
        assertEquals(Long.valueOf(ROW_ID), row.getRowId());
        assertEquals(2, row.getFields().size());
        assertEquals(String.valueOf(COLUMN_ONE_ID), row.getFields().get(0).getFieldId());
        assertEquals("DETAIL_COLUMN", row.getFields().get(0).getColumnName());
        assertEquals("Text", row.getFields().get(0).getComponentConfig().get("componentKey").asText());
        assertTrue(row.getFields().get(0).getHasValue());
        assertEquals("明细值1", row.getFields().get(0).getValue().getDisplayText());
        assertNull(row.getFields().get(1).getValue());
        assertFalse(row.getFields().get(1).getHasValue());

        verify(fieldRpcUtil).listFieldLibrary(RpcGroup.WORKFLOW, FORM_ID, sysadmin);
        verify(fieldRpcUtil).getFieldMapByForm(RpcGroup.WORKFLOW, FORM_ID, sysadmin);
        verify(wfcFormDataRest).getFullFormData(DATA_ID, sysadmin);
    }

    @Test
    @DisplayName("字段库扁平返回明细列时：按 subFormId 归入明细表，表名取 subFormTitle")
    void shouldGroupFlatDetailColumnsBySubFormId() {
        SimpleEmployee sysadmin = sysadminUser();
        userContext.when(UserContext::getCurrentUser).thenReturn(sysadmin);
        SimpleFormFieldDto column = mainField(COLUMN_ONE_ID, "mxb1_1", "明细列1");
        column.setSubFormId(SUB_FORM_ID);
        column.setSubFormTitle("明细表1");
        when(fieldRpcUtil.listFieldLibrary(RpcGroup.WORKFLOW, FORM_ID, sysadmin)).thenReturn(Arrays.asList(
                mainField(TEXT_FIELD_ID, "txt_a", "文本A"),
                column));
        when(wfcFormDataRest.getFullFormData(DATA_ID, sysadmin)).thenReturn(formData(Arrays.asList(
                rowValue(COLUMN_ONE_ID, 2L, "明细值1"))));

        FormFieldDataDTO result = formFieldValueService.getFormFieldData(FORM_ID, DATA_ID);

        assertEquals(1, result.getMainFields().size());
        assertEquals(1, result.getDetailTables().size());
        FormDetailTableDTO detailTable = result.getDetailTables().get(0);
        assertEquals(String.valueOf(SUB_FORM_ID), detailTable.getSubFormId());
        assertEquals("明细表1", detailTable.getTitle());
        assertEquals(1, detailTable.getColumns().size());
        assertEquals(1, detailTable.getRows().size());
        assertEquals(Long.valueOf(2L), detailTable.getRows().get(0).getDataIndex());
    }

    @Test
    @DisplayName("多选字段：展示文本取选项内容，并用逗号连接")
    void shouldUseOptionContentAsDisplayText() {
        SimpleEmployee sysadmin = sysadminUser();
        userContext.when(UserContext::getCurrentUser).thenReturn(sysadmin);
        when(fieldRpcUtil.listFieldLibrary(RpcGroup.WORKFLOW, FORM_ID, sysadmin))
                .thenReturn(Collections.singletonList(mainField(TEXT_FIELD_ID, "txt_a", "文本A")));
        FormDataDetail dataDetail = fieldValue(TEXT_FIELD_ID, "1,2");
        dataDetail.setOptionId("1,2");
        dataDetail.setDataOptions(Arrays.asList(option("1", "选项A"), option("2", "选项B")));
        when(wfcFormDataRest.getFullFormData(DATA_ID, sysadmin))
                .thenReturn(formData(Collections.singletonList(dataDetail)));

        FormFieldDataDTO result = formFieldValueService.getFormFieldData(FORM_ID, DATA_ID);

        FormFieldDTO field = result.getMainFields().get(0);
        assertTrue(field.getHasValue());
        assertEquals("选项A，选项B", field.getValue().getDisplayText());
        assertEquals("1,2", field.getValue().getOptionId());
    }

    @Test
    @DisplayName("图片、附件字段：展示文本取文件名，并返回文件名与访问地址")
    void shouldUseFileNameAsDisplayTextForFileField() {
        SimpleEmployee sysadmin = sysadminUser();
        userContext.when(UserContext::getCurrentUser).thenReturn(sysadmin);
        when(fieldRpcUtil.listFieldLibrary(RpcGroup.WORKFLOW, FORM_ID, sysadmin))
                .thenReturn(Collections.singletonList(mainField(TEXT_FIELD_ID, "tpsc_fo3a", "图片上传")));
        FormDataDetail dataDetail = fieldValue(TEXT_FIELD_ID, null);
        dataDetail.setFileDetails(Arrays.asList(file(7L, "现场照片.jpg", "/sp/file/7")));
        when(wfcFormDataRest.getFullFormData(DATA_ID, sysadmin))
                .thenReturn(formData(Collections.singletonList(dataDetail)));

        FormFieldDataDTO result = formFieldValueService.getFormFieldData(FORM_ID, DATA_ID);

        FormFieldDTO field = result.getMainFields().get(0);
        assertTrue(field.getHasValue());
        assertEquals("现场照片.jpg", field.getValue().getDisplayText());
        assertEquals(1, field.getValue().getFiles().size());
        assertEquals(Long.valueOf(7L), field.getValue().getFiles().get(0).getFileId());
        assertEquals("/sp/file/7", field.getValue().getFiles().get(0).getFileUrl());
    }

    @Test
    @DisplayName("布局外拼接文件：从原始字段选项返回附件ID与文件组件属性")
    void shouldReturnFileOptionForFieldOutsideLayout() {
        SimpleEmployee sysadmin = sysadminUser();
        userContext.when(UserContext::getCurrentUser).thenReturn(sysadmin);
        when(fieldRpcUtil.listFieldLibrary(RpcGroup.WORKFLOW, FORM_ID, sysadmin))
                .thenReturn(Collections.singletonList(mainField(TEXT_FIELD_ID, "pjwj", "拼接文件")));
        FormDataDetail dataDetail = fieldValue(TEXT_FIELD_ID, null);
        FormDataOption option = option("1310177016467636226", "jointFile (2).docx");
        option.setType(ModuleSource.file);
        FileObj fileObject = new FileObj();
        fileObject.setId(1310177016467636226L);
        fileObject.setFileid(1310177016467636226L);
        fileObject.setName("jointFile (2).docx");
        fileObject.setSize(18149);
        fileObject.setUrl("opaque-file-key");
        fileObject.setTenantKey("must-not-return");
        fileObject.setLoadlink("/papi/file/remotedownload/example");
        option.setOptionObj(fileObject);
        dataDetail.setDataOptions(Collections.singletonList(option));
        when(wfcFormDataRest.getFullFormData(DATA_ID, sysadmin))
                .thenReturn(formData(Collections.singletonList(dataDetail)));

        FormFieldDataDTO result = formFieldValueService.getFormFieldData(FORM_ID, DATA_ID);

        FormFieldDTO field = result.getMainFields().get(0);
        assertTrue(field.getHasValue());
        assertEquals("jointFile (2).docx", field.getValue().getDisplayText());
        assertTrue(field.getValue().getFiles().isEmpty());
        assertEquals("1310177016467636226", field.getValue().getDataOptions().get(0).getOptionId());
        assertEquals("file", field.getValue().getDataOptions().get(0).getType());
        assertEquals("1310177016467636226",
                field.getValue().getDataOptions().get(0).getOptionObj().get("id").asText());
        assertNull(field.getValue().getDataOptions().get(0).getOptionObj().get("tenantKey"));
        assertEquals("/papi/file/remotedownload/example",
                field.getValue().getDataOptions().get(0).getOptionObj().get("loadlink").asText());
    }

    @Test
    @DisplayName("无选项字段：展示文本取文本内容")
    void shouldUseDataTextContentAsDisplayText() {
        SimpleEmployee sysadmin = sysadminUser();
        userContext.when(UserContext::getCurrentUser).thenReturn(sysadmin);
        when(fieldRpcUtil.listFieldLibrary(RpcGroup.WORKFLOW, FORM_ID, sysadmin))
                .thenReturn(Collections.singletonList(mainField(TEXT_FIELD_ID, "txt_a", "文本A")));
        FormDataDetail dataDetail = fieldValue(TEXT_FIELD_ID, "1");
        FormDataText dataText = new FormDataText();
        dataText.setContent("文本内容");
        dataDetail.setDataText(dataText);
        when(wfcFormDataRest.getFullFormData(DATA_ID, sysadmin))
                .thenReturn(formData(Collections.singletonList(dataDetail)));

        FormFieldDataDTO result = formFieldValueService.getFormFieldData(FORM_ID, DATA_ID);

        assertEquals("文本内容", result.getMainFields().get(0).getValue().getDisplayText());
    }

    @Test
    @DisplayName("字段库外的字段值：忽略且不中断查询")
    void shouldIgnoreFieldValueOutsideFieldLibrary() {
        SimpleEmployee sysadmin = sysadminUser();
        userContext.when(UserContext::getCurrentUser).thenReturn(sysadmin);
        when(fieldRpcUtil.listFieldLibrary(RpcGroup.WORKFLOW, FORM_ID, sysadmin))
                .thenReturn(Collections.singletonList(mainField(TEXT_FIELD_ID, "txt_a", "文本A")));
        when(wfcFormDataRest.getFullFormData(DATA_ID, sysadmin)).thenReturn(formData(Arrays.asList(
                fieldValue(TEXT_FIELD_ID, "主表值A"),
                fieldValue(UNKNOWN_FIELD_ID, "已移除字段的值"))));

        FormFieldDataDTO result = formFieldValueService.getFormFieldData(FORM_ID, DATA_ID);

        assertEquals(1, result.getMainFields().size());
        assertEquals("主表值A", result.getMainFields().get(0).getValue().getDisplayText());
    }

    @Test
    @DisplayName("未登录：拒绝查询且不调用平台接口")
    void shouldRejectWhenNotLoggedIn() {
        userContext.when(UserContext::getCurrentUser).thenReturn(null);

        FormAssistException exception = assertThrows(FormAssistException.class,
                () -> formFieldValueService.getFormFieldData(FORM_ID, DATA_ID));

        assertEquals("接口需登录后调用", exception.getMessage());
        verifyNoInteractions(fieldRpcUtil, wfcFormDataRest);
    }

    @Test
    @DisplayName("非系统管理员：拒绝查询且不调用平台接口")
    void shouldRejectWhenNotSysadmin() {
        userContext.when(UserContext::getCurrentUser).thenReturn(employee(false));

        FormAssistException exception = assertThrows(FormAssistException.class,
                () -> formFieldValueService.getFormFieldData(FORM_ID, DATA_ID));

        assertEquals("仅系统管理员可调用该接口", exception.getMessage());
        verifyNoInteractions(fieldRpcUtil, wfcFormDataRest);
    }

    @Test
    @DisplayName("字段库为空：抛出业务异常")
    void shouldThrowWhenFieldLibraryIsEmpty() {
        SimpleEmployee sysadmin = sysadminUser();
        userContext.when(UserContext::getCurrentUser).thenReturn(sysadmin);
        when(fieldRpcUtil.listFieldLibrary(RpcGroup.WORKFLOW, FORM_ID, sysadmin))
                .thenReturn(Collections.emptyList());

        FormAssistException exception = assertThrows(FormAssistException.class,
                () -> formFieldValueService.getFormFieldData(FORM_ID, DATA_ID));

        assertEquals("未查询到表单字段，formId=" + FORM_ID, exception.getMessage());
        verifyNoInteractions(wfcFormDataRest);
    }

    @Test
    @DisplayName("表单数据不存在：抛出业务异常")
    void shouldThrowWhenFormDataNotFound() {
        SimpleEmployee sysadmin = sysadminUser();
        userContext.when(UserContext::getCurrentUser).thenReturn(sysadmin);
        when(fieldRpcUtil.listFieldLibrary(RpcGroup.WORKFLOW, FORM_ID, sysadmin))
                .thenReturn(Collections.singletonList(mainField(TEXT_FIELD_ID, "txt_a", "文本A")));
        when(wfcFormDataRest.getFullFormData(DATA_ID, sysadmin)).thenReturn(null);

        FormAssistException exception = assertThrows(FormAssistException.class,
                () -> formFieldValueService.getFormFieldData(FORM_ID, DATA_ID));

        assertEquals("未查询到表单数据，dataId=" + DATA_ID, exception.getMessage());
    }

    @Test
    @DisplayName("字段值明细为空：仍返回全部字段，值均为空")
    void shouldReturnFieldsWithoutValueWhenDataDetailsIsEmpty() {
        SimpleEmployee sysadmin = sysadminUser();
        userContext.when(UserContext::getCurrentUser).thenReturn(sysadmin);
        when(fieldRpcUtil.listFieldLibrary(RpcGroup.WORKFLOW, FORM_ID, sysadmin))
                .thenReturn(Collections.singletonList(mainField(TEXT_FIELD_ID, "txt_a", "文本A")));
        when(wfcFormDataRest.getFullFormData(DATA_ID, sysadmin)).thenReturn(formData(null));

        FormFieldDataDTO result = formFieldValueService.getFormFieldData(FORM_ID, DATA_ID);

        assertEquals(1, result.getMainFields().size());
        assertFalse(result.getMainFields().get(0).getHasValue());
        assertNull(result.getMainFields().get(0).getValue());
        assertTrue(result.getDetailTables().isEmpty());
    }

    @Test
    @DisplayName("明细行超过单表上限：截断返回且 totalRows 为真实行数")
    void shouldTruncateDetailRowsBeyondLimit() {
        SimpleEmployee sysadmin = sysadminUser();
        userContext.when(UserContext::getCurrentUser).thenReturn(sysadmin);
        SimpleFormFieldDto column = mainField(COLUMN_ONE_ID, "mxb1_1", "明细列1");
        column.setSubFormId(SUB_FORM_ID);
        column.setSubFormTitle("明细表1");
        when(fieldRpcUtil.listFieldLibrary(RpcGroup.WORKFLOW, FORM_ID, sysadmin))
                .thenReturn(Collections.singletonList(column));
        List<FormDataDetail> dataDetails = new ArrayList<>();
        for (int rowIndex = 1; rowIndex <= 1001; rowIndex++) {
            dataDetails.add(rowValue(COLUMN_ONE_ID, rowIndex, "明细值" + rowIndex));
        }
        when(wfcFormDataRest.getFullFormData(DATA_ID, sysadmin)).thenReturn(formData(dataDetails));

        FormFieldDataDTO result = formFieldValueService.getFormFieldData(FORM_ID, DATA_ID);

        FormDetailTableDTO detailTable = result.getDetailTables().get(0);
        assertEquals(Integer.valueOf(1001), detailTable.getTotalRows());
        assertTrue(detailTable.getTruncated());
        assertEquals(1000, detailTable.getRows().size());
        assertEquals(Long.valueOf(1L), detailTable.getRows().get(0).getDataIndex());
        assertEquals(Long.valueOf(1000L), detailTable.getRows().get(999).getDataIndex());
    }

    /**
     * 构建系统管理员用户。
     *
     * @return 系统管理员
     */
    private SimpleEmployee sysadminUser() {
        return employee(true);
    }

    /**
     * 构建用户。
     *
     * <p>系统管理员判定走 {@code SimpleEmployee#isAdmin()}：权限集合为空时回落到 admin 字段，
     * 因此这里用 {@code setAdmin} 构造管理员与非管理员。
     *
     * @param sysAdmin 是否系统管理员
     * @return 用户
     */
    private SimpleEmployee employee(boolean sysAdmin) {
        SimpleEmployee employee = new SimpleEmployee();
        employee.setId(10086L);
        employee.setTenantKey("test-tenant");
        employee.setAdmin(sysAdmin);
        return employee;
    }

    /**
     * 构建主表字段库条目。
     *
     * @param fieldId 字段 ID
     * @param dataKey 字段 dataKey
     * @param title 字段标题
     * @return 字段库条目
     */
    private SimpleFormFieldDto mainField(long fieldId, String dataKey, String title) {
        SimpleFormFieldDto field = new SimpleFormFieldDto();
        field.setId(fieldId);
        field.setDataKey(dataKey);
        field.setTitle(title);
        field.setComponentKey("Text");
        field.setType("Text");
        field.setIsRequired(Boolean.FALSE);
        return field;
    }

    /**
     * 构建明细表条目，明细列放在 subFields 中。
     *
     * @return 明细表条目
     */
    private SimpleFormFieldDto detailTableEntry() {
        SimpleFormFieldDto table = new SimpleFormFieldDto();
        table.setTitle("明细表1");
        table.setSubFormId(SUB_FORM_ID);
        table.setSubFields(Arrays.asList(
                detailColumn(COLUMN_ONE_ID, "mxb1_1", "明细列1"),
                detailColumn(COLUMN_TWO_ID, "mxb1_2", "明细列2")));
        return table;
    }

    /**
     * 构建明细列条目。
     *
     * @param fieldId 字段 ID
     * @param dataKey 字段 dataKey
     * @param title 字段标题
     * @return 明细列条目
     */
    private SimpleFormFieldDto detailColumn(long fieldId, String dataKey, String title) {
        SimpleFormFieldDto column = mainField(fieldId, dataKey, title);
        column.setSubFormId(SUB_FORM_ID);
        return column;
    }

    /**
     * 构建表单数据。
     *
     * @param dataDetails 字段值明细
     * @return 表单数据
     */
    private FormData formData(List<FormDataDetail> dataDetails) {
        FormData formData = new FormData();
        formData.setDataDetails(dataDetails);
        return formData;
    }

    /**
     * 构建字段值明细，字段 ID 放在 formField 中。
     *
     * @param fieldId 字段 ID
     * @param content 字段值
     * @return 字段值明细
     */
    private FormDataDetail fieldValue(long fieldId, String content) {
        FormDataDetail dataDetail = new FormDataDetail();
        FormField formField = new FormField(fieldId);
        dataDetail.setFormField(formField);
        dataDetail.setContent(content);
        return dataDetail;
    }

    /**
     * 构建无内容的字段值明细，字段 ID 放在 fieldIds 中。
     *
     * @param fieldId 字段 ID
     * @return 字段值明细
     */
    private FormDataDetail emptyValue(long fieldId) {
        FormDataDetail dataDetail = new FormDataDetail();
        dataDetail.setFieldIds(Collections.singletonList(fieldId));
        return dataDetail;
    }

    /**
     * 构建明细行字段值明细。
     *
     * @param fieldId 字段 ID
     * @param dataIndex 明细行号
     * @param content 字段值
     * @return 字段值明细
     */
    private FormDataDetail rowValue(long fieldId, long dataIndex, String content) {
        FormDataDetail dataDetail = fieldValue(fieldId, content);
        dataDetail.setDataIndex(dataIndex);
        dataDetail.setRowId(ROW_ID);
        return dataDetail;
    }

    /**
     * 构建选项。
     *
     * @param optionId 选项 ID
     * @param content 选项内容
     * @return 选项
     */
    private FormDataOption option(String optionId, String content) {
        FormDataOption option = new FormDataOption();
        option.setOptionId(optionId);
        option.setContent(content);
        return option;
    }

    /**
     * 构建附件，附带的文件字节内容不应出现在接口返回中。
     *
     * @param fileId 文件 ID
     * @param fileName 文件名
     * @param fileUrl 文件地址
     * @return 附件
     */
    private FileDetail file(long fileId, String fileName, String fileUrl) {
        FileDetail fileDetail = new FileDetail();
        fileDetail.setFileId(fileId);
        fileDetail.setFileName(fileName);
        fileDetail.setFileUrl(fileUrl);
        fileDetail.setFileContent(new byte[]{1, 2, 3});
        return fileDetail;
    }
}
