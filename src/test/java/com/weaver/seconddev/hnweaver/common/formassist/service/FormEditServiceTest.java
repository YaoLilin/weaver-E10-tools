package com.weaver.seconddev.hnweaver.common.formassist.service;

import com.weaver.common.distribution.genid.IdGenerator;
import com.weaver.common.distribution.lock.DistributionLockInterface;
import com.weaver.common.form.api.rest.FormRestFactory;
import com.weaver.common.form.api.rest.data.FormDataRest;
import com.weaver.common.form.data.FormData;
import com.weaver.common.form.data.log.FormDataLog;
import com.weaver.common.form.data.log.FormDataLogService;
import com.weaver.common.form.data.log.OperationType;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import com.weaver.common.form.data.detail.FormDataDetail;
import com.weaver.common.form.dto.data.FormDataDto;
import com.weaver.common.form.metadata.Form;
import com.weaver.common.form.metadata.ModuleSource;
import com.weaver.common.form.metadata.field.FormField;
import com.weaver.common.form.metadata.subform.SubForm;
import com.weaver.seconddev.hnweaver.common.constants.RpcGroup;
import com.weaver.seconddev.hnweaver.common.exception.FormAssistException;
import com.weaver.seconddev.hnweaver.common.formassist.domain.dto.FormEditRequest;
import com.weaver.seconddev.hnweaver.common.util.FieldRpcUtil;
import com.weaver.teams.domain.user.SimpleEmployee;
import com.weaver.teams.security.context.UserContext;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import com.weaver.workflow.core.api.rest.publicapi.WfcFormDataRest;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

/** 编辑接口只测试增量数据及权限边界，不调用真实 RPC 或数据库。 */
@DisplayName("表单助手编辑服务测试")
class FormEditServiceTest {
    private FormRestFactory factory;
    private FieldRpcUtil fields;
    private FormDataRest rest;
    private WfcFormDataRest workflowData;
    private FormEditService service;
    private FormDataLogService formDataLog;
    private ObjectProvider<FormDataLogService> logProvider;
    private SimpleEmployee admin;
    private FormData existing;
    private FormEditRequest request;
    private FormEditRequest.FieldChange change;
    private MockedStatic<UserContext> users;
    private MockedStatic<IdGenerator> ids;
    private DistributionLockInterface lock;
    private final Map<String, String> journal = new HashMap<>(4);

    @BeforeEach
    @SuppressWarnings("unchecked")
    void prepare() throws ReflectiveOperationException {
        factory = mock(FormRestFactory.class);
        fields = mock(FieldRpcUtil.class);
        rest = mock(FormDataRest.class);
        formDataLog = mock(FormDataLogService.class);
        logProvider = mock(ObjectProvider.class);
        when(logProvider.getIfAvailable()).thenReturn(formDataLog);
        RedisTemplate<String, String> redis = mock(RedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.get(anyString())).thenAnswer(call -> journal.get(call.getArgument(0)));
        doAnswer(call -> {
            journal.put(call.getArgument(0), call.getArgument(1));
            return null;
        }).when(values).set(anyString(), anyString(), anyLong(), eq(TimeUnit.HOURS));
        lock = mock(DistributionLockInterface.class);
        when(lock.isSuccessTryLock(anyString(), anyLong(), anyLong(), eq(TimeUnit.SECONDS))).thenReturn(true);
        ObjectProvider<DistributionLockInterface> lockProvider = mock(ObjectProvider.class);
        when(lockProvider.getIfAvailable()).thenReturn(lock);
        ObjectProvider<RedisTemplate<String, String>> redisProvider = mock(ObjectProvider.class);
        when(redisProvider.getIfAvailable()).thenReturn(redis);
        service = new FormEditService(factory, fields, new FormDeletionAudit(redisProvider, logProvider, lockProvider));
        ids = mockStatic(IdGenerator.class);
        ids.when(IdGenerator::generate).thenReturn(900L);
        AtomicReference<List<FormDataLog>> writtenLogs = new AtomicReference<>(Collections.emptyList());
        when(formDataLog.listAllFormDataLogByRecordIds(eq(Collections.singletonList(900L)), anyList()))
                .thenAnswer(call -> writtenLogs.get());
        doAnswer(call -> {
            Map<Long, List<FormDataDetail>> oldValues = call.getArgument(2);
            List<FormDataLog> savedLogs = new ArrayList<>();
            for (FormDataDetail detail : oldValues.get(200L)) {
                FormDataLog entry = new FormDataLog();
                entry.setFormField(detail.getFormField());
                entry.setSubForm(detail.getSubForm());
                entry.setDataIndex(detail.getDataIndex());
                savedLogs.add(entry);
            }
            writtenLogs.set(savedLogs);
            return null;
        }).when(formDataLog).sendFormDataLogMessage(eq(OperationType.SAVE), anyList(), anyMap(),
                nullable(String.class), nullable(Long.class), any(SimpleEmployee.class));
        workflowData = mock(WfcFormDataRest.class);
        java.lang.reflect.Field workflowField = FormEditService.class.getDeclaredField("wfcFormDataRest");
        workflowField.setAccessible(true);
        workflowField.set(service, workflowData);
        admin = new SimpleEmployee();
        admin.setAdmin(true);
        admin.setId(42L);
        admin.setTenantKey("tenant-a");
        users = mockStatic(UserContext.class);
        users.when(UserContext::getCurrentUser).thenReturn(admin);
        when(factory.getFormDataRest("workflow")).thenReturn(rest);
        existing = new FormData();
        existing.setForm(new Form(100L));
        existing.setDataDetails(Collections.emptyList());
        when(workflowData.getFullFormData(200L, admin)).thenReturn(existing);
        FormField field = new FormField();
        field.setId(300L);
        field.setFormId(100L);
        when(fields.getFieldMapByForm(RpcGroup.WORKFLOW, 100L, admin)).thenReturn(Collections.singletonMap(300L, field));
        FormDataDto saved = new FormDataDto();
        saved.setDataId(200L);
        when(rest.saveFormDataByRowIds(any(FormDataDto.class), eq(admin))).thenReturn(saved);
        request = new FormEditRequest();
        request.setFormId(100L);
        request.setDataId(200L);
        request.setModule("workflow");
        request.setOperation(FormEditRequest.Operation.UPDATE);
        change = new FormEditRequest.FieldChange();
        change.setFieldId(300L);
        change.setContent("新值");
        request.setFields(Collections.singletonList(change));
    }

    @AfterEach
    void cleanup() {
        users.close();
        ids.close();
    }

    @Test @DisplayName("主表字段只提交指定字段且支持清空值")
    void savesOneField() {
        change.setContent("");
        assertTrue(service.save(request));
        FormDataDto dto = savedRequest();
        assertEquals(1, dto.getDataDetails().size());
        assertEquals("", dto.getDataDetails().get(0).getContent());
        assertEquals(200L, dto.getDataId());
    }

    @Test @DisplayName("保存沿用原记录布局版本而非 DTO 默认的新布局路由")
    void preservesLayoutRouting() {
        existing.setNewLayout(false);
        existing.setMultipleLayoutId(600L);
        assertTrue(service.save(request));
        FormDataDto dto = savedRequest();
        assertFalse(dto.isNewLayout());
        assertEquals(600L, dto.getLayoutMultiId());
    }

    @Test @DisplayName("非管理员不能调用保存服务")
    void rejectsNonAdmin() {
        admin.setAdmin(false);
        assertThrows(FormAssistException.class, () -> service.save(request));
        verifyNoInteractions(rest);
    }

    @Test @DisplayName("请求记录不属于表单时拒绝保存")
    void rejectsWrongForm() {
        existing.setForm(new Form(999L));
        assertThrows(FormAssistException.class, () -> service.save(request));
        verify(rest, never()).saveFormDataByRowIds(any(), eq(admin));
    }

    @Test @DisplayName("未知字段不能注入保存请求")
    void rejectsUnknownField() {
        change.setFieldId(999L);
        assertThrows(FormAssistException.class, () -> service.save(request));
        verify(rest, never()).saveFormDataByRowIds(any(), eq(admin));
    }

    @Test @DisplayName("修改明细字段必须匹配当前表单和行")
    void updatesDetail() {
        detailMetadata();
        request.setSubFormId(400L);
        change.setRowId(500L);
        assertTrue(service.save(request));
        assertEquals(500L, savedRequest().getDataDetails().get(0).getRowId());
    }

    @Test @DisplayName("数据值未携带明细表对象时通过字段元数据确认行归属")
    void resolvesRowThroughField() {
        detailMetadata();
        FormDataDetail row = existing.getDataDetails().get(0);
        row.setSubForm(null);
        row.setFieldIds(Collections.singletonList(300L));
        request.setSubFormId(400L);
        change.setRowId(500L);
        assertTrue(service.save(request));
        assertEquals(500L, savedRequest().getDataDetails().get(0).getRowId());
    }

    @Test @DisplayName("不能修改其他明细行")
    void rejectsUnknownRow() {
        detailMetadata();
        request.setSubFormId(400L);
        change.setRowId(999L);
        assertThrows(FormAssistException.class, () -> service.save(request));
    }

    @Test @DisplayName("新增行保留客户端SDK行ID但由服务端分配索引")
    void addsRow() {
        detailMetadata();
        request.setOperation(FormEditRequest.Operation.ADD_ROWS);
        request.setSubFormId(400L);
        change.setRowId(501L);
        change.setDataIndex(999L);
        assertTrue(service.save(request));
        assertEquals(2L, savedRequest().getDataDetails().get(0).getDataIndex());
    }

    @Test @DisplayName("重复提交已保存的新增行不能再新增")
    void rejectsDuplicateAddition() {
        detailMetadata();
        request.setOperation(FormEditRequest.Operation.ADD_ROWS);
        request.setSubFormId(400L);
        change.setRowId(500L);
        change.setDataIndex(2L);
        assertThrows(FormAssistException.class, () -> service.save(request));
    }

    @Test @DisplayName("删除只构造当前明细表的指定行且去重")
    void deletesSelectedRows() {
        detailMetadata();
        request.setOperation(FormEditRequest.Operation.DELETE_ROWS);
        request.setSubFormId(400L);
        request.setFields(Collections.emptyList());
        request.setRowIds(Arrays.asList(500L, 500L));
        FormData after = new FormData();
        after.setForm(existing.getForm());
        after.setDataDetails(Collections.emptyList());
        when(workflowData.getFullFormData(200L, admin)).thenReturn(existing, after);
        FormDataDto saved = new FormDataDto();
        saved.setDataId(200L);
        when(rest.saveFormDataByRowIds(any(FormDataDto.class), eq(admin))).thenReturn(saved);
        assertTrue(service.save(request));
        ArgumentCaptor<FormDataDto> capture = ArgumentCaptor.forClass(FormDataDto.class);
        verify(rest).saveFormDataByRowIds(capture.capture(), eq(admin));
        assertEquals(Boolean.FALSE, capture.getValue().getCoverMap().get(400L));
        verify(rest, never()).saveSubFormData(any(), anyLong(), any(SimpleEmployee.class));
        assertEquals(1, capture.getValue().getDelRowIds().size());
        assertEquals(500L, capture.getValue().getDelRowIds().get(0).getRowId());
    }

    @Test @DisplayName("仅删除选中行且未选中行仍保留")
    void retainsUnselectedRows() {
        detailMetadata();
        FormDataDetail retained = new FormDataDetail();
        retained.setSubForm(existing.getDataDetails().get(0).getSubForm());
        retained.setRowId(501L);
        retained.setContent("必须保留");
        existing.getDataDetails().get(0).setContent("被删除的原值");
        existing.setDataDetails(Arrays.asList(existing.getDataDetails().get(0), retained));
        FormData after = new FormData();
        after.setForm(existing.getForm());
        after.setDataDetails(Collections.singletonList(retained));
        when(workflowData.getFullFormData(200L, admin)).thenReturn(existing, after);
        request.setOperation(FormEditRequest.Operation.DELETE_ROWS);
        request.setSubFormId(400L);
        request.setFields(Collections.emptyList());
        request.setRowIds(Collections.singletonList(500L));
        assertTrue(service.save(request));
        FormDataDto saved = savedRequest();
        assertEquals(500L, saved.getDelRowIds().get(0).getRowId());
        assertEquals(Boolean.FALSE, saved.getCoverMap().get(400L));
        assertTrue(saved.getDataDetails().isEmpty());
        verify(rest, never()).saveSubFormData(any(), anyLong(), any(SimpleEmployee.class));
        verify(formDataLog).sendFormDataLogMessage(eq(OperationType.SAVE),
                argThat(events -> events.size() == 1 && events.get(0).getId().equals(200L)
                        && events.get(0).getDataDelRows().get(0).getRowId().equals(500L)),
                argThat(values -> values.size() == 1 && values.get(200L).size() == 1
                        && "被删除的原值".equals(values.get(200L).get(0).getContent())),
                isNull(), isNull(), eq(admin));
    }

    @Test
    @DisplayName("EB 明细删除回读确认后按原记录发送选中行日志")
    void sendsEbuilderDeletionLogAfterReadback() {
        detailMetadata(RpcGroup.EBUILDER_FORM);
        request.setModule(RpcGroup.EBUILDER_FORM.getValue());
        request.setOperation(FormEditRequest.Operation.DELETE_ROWS);
        request.setSubFormId(400L);
        request.setFields(Collections.emptyList());
        request.setRowIds(Collections.singletonList(500L));
        existing.setModule(ModuleSource.ebuilderform);
        FormDataDetail selected = existing.getDataDetails().get(0);
        selected.setContent("删除前的 EB 字段值");
        FormDataDetail retained = selected.clone();
        retained.setRowId(501L);
        existing.setDataDetails(Arrays.asList(selected, retained));
        FormData after = new FormData();
        after.setForm(existing.getForm());
        after.setDataDetails(Collections.singletonList(retained));
        when(factory.getFormDataRest(RpcGroup.EBUILDER_FORM.getValue())).thenReturn(rest);
        when(rest.getFullFormData(200L, admin)).thenReturn(existing, after);
        when(rest.getObjectIdByFormData(200L, admin)).thenReturn(700L);

        assertTrue(service.save(request));

        InOrder order = inOrder(rest, formDataLog);
        order.verify(rest).saveFormDataByRowIds(any(FormDataDto.class), eq(admin));
        order.verify(rest).getFullFormData(200L, admin);
        order.verify(formDataLog).sendFormDataLogMessage(
                eq(OperationType.SAVE),
                argThat(events -> events.size() == 1
                        && events.get(0).getModule() == ModuleSource.ebuilderform
                        && Long.valueOf(700L).equals(events.get(0).getObjectId())
                        && events.get(0).getDataDelRows().size() == 1
                        && Long.valueOf(500L).equals(events.get(0).getDataDelRows().get(0).getRowId())),
                argThat(values -> values.size() == 1 && values.get(200L).size() == 1
                        && Long.valueOf(500L).equals(values.get(200L).get(0).getRowId())
                        && "删除前的 EB 字段值".equals(values.get(200L).get(0).getContent())),
                isNull(), isNull(), eq(admin));
    }

    @Test @DisplayName("表单日志服务不可用时拒绝删除，避免产生无日志的删除")
    void rejectsDeletionWhenAuditUnavailable() {
        detailMetadata();
        when(logProvider.getIfAvailable()).thenReturn(null);
        request.setOperation(FormEditRequest.Operation.DELETE_ROWS);
        request.setSubFormId(400L);
        request.setFields(Collections.emptyList());
        request.setRowIds(Collections.singletonList(500L));
        assertThrows(FormAssistException.class, () -> service.save(request));
        verify(rest, never()).saveFormDataByRowIds(any(), eq(admin));
    }

    @Test @DisplayName("平台返回数据 ID 但未删除明细时不能报告成功")
    void rejectsDeletionWithoutEffect() {
        detailMetadata();
        request.setOperation(FormEditRequest.Operation.DELETE_ROWS);
        request.setSubFormId(400L);
        request.setFields(Collections.emptyList());
        request.setRowIds(Collections.singletonList(500L));
        FormDataDto saved = new FormDataDto();
        saved.setDataId(200L);
        when(rest.saveFormDataByRowIds(any(FormDataDto.class), eq(admin))).thenReturn(saved);
        assertThrows(FormAssistException.class, () -> service.save(request));
        verifyNoInteractions(formDataLog);
    }

    @Test @DisplayName("删除回读行缺少字段归属对象时仍须识别未删除行")
    void rejectsRemainingRowWithoutMetadata() {
        detailMetadata();
        request.setOperation(FormEditRequest.Operation.DELETE_ROWS);
        request.setSubFormId(400L);
        request.setFields(Collections.emptyList());
        request.setRowIds(Collections.singletonList(500L));
        FormData after = new FormData();
        after.setForm(existing.getForm());
        FormDataDetail row = new FormDataDetail();
        row.setRowId(500L);
        after.setDataDetails(Collections.singletonList(row));
        when(workflowData.getFullFormData(200L, admin)).thenReturn(existing, after);
        FormDataDto saved = new FormDataDto();
        saved.setDataId(200L);
        when(rest.saveFormDataByRowIds(any(FormDataDto.class), eq(admin))).thenReturn(saved);
        assertThrows(FormAssistException.class, () -> service.save(request));
    }

    @Test
    @DisplayName("删除回读失败后重试只补记日志并且不会再次删除")
    void retriesAuditAfterReadbackFailure() {
        detailMetadata();
        request.setOperation(FormEditRequest.Operation.DELETE_ROWS);
        request.setSubFormId(400L);
        request.setFields(Collections.emptyList());
        request.setRowIds(Collections.singletonList(500L));
        FormData after = new FormData();
        after.setForm(existing.getForm());
        after.setDataDetails(Collections.emptyList());
        when(workflowData.getFullFormData(200L, admin)).thenReturn(existing)
                .thenThrow(new IllegalStateException("回读超时")).thenReturn(after);

        assertThrows(FormAssistException.class, () -> service.save(request));
        assertFalse(journal.isEmpty());
        assertTrue(service.save(request));
        assertTrue(service.save(request));

        verify(rest, times(1)).saveFormDataByRowIds(any(), eq(admin));
        verify(formDataLog, times(1)).sendFormDataLogMessage(eq(OperationType.SAVE),
                argThat(events -> Long.valueOf(900L).equals(events.get(0).getRecordId())), anyMap(),
                isNull(), isNull(), eq(admin));
    }

    @Test
    @DisplayName("部分删除时只记录已删除的选中行，重复确认不会再次写表单")
    void auditsOnlyRemovedRowsWhenPlatformPartiallyDeletes() {
        detailMetadata();
        FormDataDetail first = existing.getDataDetails().get(0);
        FormDataDetail remaining = first.clone();
        remaining.setRowId(501L);
        remaining.setDataIndex(2L);
        existing.setDataDetails(Arrays.asList(first, remaining));
        request.setOperation(FormEditRequest.Operation.DELETE_ROWS);
        request.setSubFormId(400L);
        request.setFields(Collections.emptyList());
        request.setRowIds(Arrays.asList(500L, 501L));
        FormData after = new FormData();
        after.setForm(existing.getForm());
        after.setDataDetails(Collections.singletonList(remaining));
        when(workflowData.getFullFormData(200L, admin)).thenReturn(existing, after);

        assertTrue(assertThrows(FormAssistException.class, () -> service.save(request)).getMessage().contains("仅部分"));
        assertTrue(assertThrows(FormAssistException.class, () -> service.save(request)).getMessage().contains("仅部分"));
        verify(rest, times(1)).saveFormDataByRowIds(any(), eq(admin));
        verify(formDataLog, times(1)).sendFormDataLogMessage(eq(OperationType.SAVE),
                argThat(events -> events.get(0).getDataDelRows().size() == 1
                        && Long.valueOf(500L).equals(events.get(0).getDataDelRows().get(0).getRowId())),
                argThat(values -> values.get(200L).size() == 1), isNull(), isNull(), eq(admin));
    }

    @Test
    @DisplayName("日志投递失败后可确认回读并补记，保留原删除操作记录 ID")
    void retriesAuditAfterDispatchFailure() {
        detailMetadata();
        request.setOperation(FormEditRequest.Operation.DELETE_ROWS);
        request.setSubFormId(400L);
        request.setFields(Collections.emptyList());
        request.setRowIds(Collections.singletonList(500L));
        FormData after = new FormData();
        after.setForm(existing.getForm());
        after.setDataDetails(Collections.emptyList());
        when(workflowData.getFullFormData(200L, admin)).thenReturn(existing, after);
        doThrow(new IllegalStateException("队列暂不可用")).when(formDataLog).sendFormDataLogMessage(
                eq(OperationType.SAVE), anyList(), anyMap(), isNull(), isNull(), eq(admin));

        assertThrows(FormAssistException.class, () -> service.save(request));
        journal.replaceAll((key, value) -> value.replaceFirst("\"lastSentAt\":[0-9]+", "\"lastSentAt\":0"));
        FormDataLog written = new FormDataLog();
        written.setFormField(fields.getFieldMapByForm(RpcGroup.WORKFLOW, 100L, admin).get(300L));
        written.setSubForm(written.getFormField().getSubForm());
        written.setDataIndex(1L);
        doAnswer(call -> {
            when(formDataLog.listAllFormDataLogByRecordIds(eq(Collections.singletonList(900L)), anyList()))
                    .thenReturn(Collections.singletonList(written));
            return null;
        }).when(formDataLog).sendFormDataLogMessage(eq(OperationType.SAVE), anyList(), anyMap(),
                isNull(), isNull(), eq(admin));

        assertTrue(service.save(request));
        verify(rest, times(1)).saveFormDataByRowIds(any(), eq(admin));
        verify(formDataLog, times(2)).sendFormDataLogMessage(eq(OperationType.SAVE),
                argThat(events -> Long.valueOf(900L).equals(events.get(0).getRecordId())), anyMap(),
                isNull(), isNull(), eq(admin));
    }

    @Test @DisplayName("平台未确认成功时返回业务错误")
    void rejectsUnconfirmedSave() {
        when(rest.saveFormDataByRowIds(any(FormDataDto.class), eq(admin))).thenReturn(null);
        assertThrows(FormAssistException.class, () -> service.save(request));
    }

    @Test @DisplayName("平台异常向上返回而不冒充保存成功")
    void propagatesPlatformFailure() {
        when(rest.saveFormDataByRowIds(any(FormDataDto.class), eq(admin)))
                .thenThrow(new IllegalStateException("平台拒绝"));
        assertThrows(IllegalStateException.class, () -> service.save(request));
    }

    private FormDataDto savedRequest() {
        ArgumentCaptor<FormDataDto> captured = ArgumentCaptor.forClass(FormDataDto.class);
        verify(rest).saveFormDataByRowIds(captured.capture(), eq(admin));
        return captured.getValue();
    }

    private void detailMetadata() {
        detailMetadata(RpcGroup.WORKFLOW);
    }

    private void detailMetadata(RpcGroup group) {
        SubForm sub = new SubForm();
        sub.setId(400L);
        FormField field = new FormField();
        field.setId(300L);
        field.setFormId(100L);
        field.setSubForm(sub);
        when(fields.getFieldMapByForm(group, 100L, admin)).thenReturn(Collections.singletonMap(300L, field));
        FormDataDetail row = new FormDataDetail();
        row.setSubForm(sub);
        row.setRowId(500L);
        row.setFieldIds(Collections.singletonList(300L));
        row.setDataIndex(1L);
        existing.setDataDetails(Collections.singletonList(row));
    }
}
