/** 表单助手编辑交互回归；使用构建工具的 Babel 编译真实组件，模拟平台边界。 */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const {execFileSync} = require('node:child_process');
const globalPackages = execFileSync('npm', ['root', '-g'], {encoding: 'utf8'}).trim();
const {compileJs} = require(path.join(globalPackages, 'weaver-e10-builder/ecodecmd/src/lib/exec.js'));
const root = path.resolve(__dirname, '../../main/ecode/流程表单助手/components');

/** 隔离 React 状态和网络，只记录增量请求及 SDK 同步调用。 */
function harness() {
    let frame;
    const requests = [], syncs = [], confirmations = [], pendingChanges = [], deletions = [], messages = [];
    let fail = false;
    let saveResponse = {status: true, data: true};
    const context = {
        formInfo: {formId: '100', dataId: '200', module: 'workflow'},
        confirmChange: (title, applyChange, okText = '确定') => pendingChanges.push({title, applyChange, okText}),
        reload() {},
    };
    const React = {
        createElement: (type, props, ...children) => ({type, props: props || {}, children}),
        createContext: () => ({}), useContext: () => context,
        useState(initial) {
            const index = frame.cursor++;
            if (!(index in frame.state)) { frame.state[index] = typeof initial === 'function' ? initial() : initial; }
            const owner = frame;
            return [owner.state[index], value => { owner.state[index] = typeof value === 'function' ? value(owner.state[index]) : value; }];
        },
        useRef(initial) {
            const index = frame.cursor++;
            if (!(index in frame.state)) { frame.state[index] = {current: initial}; }
            return frame.state[index];
        },
        useMemo: fn => fn(), useEffect() {},
    };
    const modules = new Map();
    const ui = Object.fromEntries(['Table', 'Pagination', 'Icon', 'Input', 'CorsComponent', 'Dialog', 'Button'].map(name => [name, name]));
    function load(file) {
        file = path.resolve(file);
        if (modules.has(file)) { return modules.get(file); }
        const exports = {};
        modules.set(file, exports);
        const sandbox = {exports, console: {error() {}, log() {}, debug() {}}, window: {setTimeout, WeFormSDK: {showMessage: (...args) => messages.push(args), showConfirm: (...args) => confirmations.push(args)}}, require(name) {
            if (name === 'react') { return React; }
            if (name === '@weapp/ui') { return ui; }
            if (name === '@weapp/ecodesdk') { return {devRequest: async config => { requests.push(config); if (fail) { throw new Error('保存被拒绝'); } return saveResponse; }}; }
            return load(path.resolve(path.dirname(file), name + '.js'));
        }};
        vm.createContext(sandbox);
        vm.runInContext(compileJs(fs.readFileSync(file, 'utf8'), file), sandbox);
        return exports;
    }
    const field = {id: '300', label: '名称', type: 'Text', value: {content: '原值'}};
    let ids = ['500'];
    const sdk = {
        getFieldInfo: () => ({fieldId: '300', type: 'Text', componentKey: 'Text'}),
        weFormApi: {formStore: {formProps: {}, getWidgetData: () => ({content: '原值', dataOptions: []})}},
        changeFieldValue: (...args) => syncs.push(args),
        getDetailAllRowIndexStr: () => ids.join(','),
        addDetailRow: () => ids.push('501'), delDetailRow: (...args) => deletions.push(args),
    };
    function component(name, props) {
        const fn = load(path.join(root, name + '.js')).default;
        const owner = {state: [], cursor: 0};
        return {render() { frame = owner; owner.cursor = 0; return fn(props); }};
    }
    return {component, context, readLayoutFormData: load(path.join(root, "formDataAdapter.js")).readLayoutFormData, field, sdk, requests, syncs, confirmations, pendingChanges, deletions, messages, setFail(value) { fail = value; }, setSaveResponse(response) { saveResponse = response; }};
}

function find(node, predicate) {
    if (!node || typeof node !== 'object') { return undefined; }
    if (predicate(node)) { return node; }
    for (const child of (node.children || []).flat()) { const match = find(child, predicate); if (match) { return match; } }
}
const button = (tree, label) => find(tree, node => node.type === 'button' && node.props['aria-label'] === label);
const editor = tree => tree.children[0].props.editor;
const acceptChange = h => h.pendingChanges.shift().applyChange();

test('确认框使用应用样式显示完整警告，并区分确定和取消', () => {
    const h = harness();
    const onCancel = () => {};
    const onConfirm = () => {};
    const dialog = h.component('EditConfirmationDialog', {
        change: {title: '删除选中的 1 行明细？', okText: '删除'}, onCancel, onConfirm, zIndex: 901,
    }).render();
    const warning = find(dialog, node => node.props.className === 'ffa-edit-confirmation-warning');
    assert.equal(warning.children[0], '请确认当前表单无修改内容，修改后，本次您在表单布局中修改的内容将会无效，如有修改内容请先保存表单，再使用表单助手进行修改。');
    assert.match(dialog.props.wrapClassName, /app-1305269432046043137/);
    assert.equal(dialog.props.zIndex, 901);
    assert.equal(dialog.props.onClose, onCancel);
    assert.equal(dialog.props.footer[0].props.onClick, onCancel);
    assert.equal(dialog.props.footer[1].props.onClick, onConfirm);
    assert.equal(dialog.props.footer[1].children[0], '删除');
});

test('主表编辑草稿在确定前不保存，确定后只保存当前字段并同步 SDK', async () => {
    const h = harness(), component = h.component('FormFieldValue', {field: h.field, formSdk: h.sdk, mode: 'component'});
    await editor(component.render()).action.props.onClick();
    editor(component.render()).content.props.onChange({content: '新值', dataOptions: []});
    assert.equal(h.requests.length, 0);
    await editor(component.render()).action.props.onClick();
    assert.equal(h.requests.length, 0);
    assert.equal(h.syncs.length, 0);
    h.pendingChanges.shift();
    assert.equal(editor(component.render()).content.props.value.content, '新值');
    editor(component.render()).action.props.onClick();
    await acceptChange(h);
    assert.equal(h.requests[0].data.operation, 'UPDATE');
    assert.equal(h.requests[0].data.fields[0].content, '新值');
    assert.equal(h.syncs[0][0], 'field300');
    assert.equal(h.messages[0][0], '修改成功');
    assert.equal(editor(component.render()).content, null);
});

test('保存失败保持字段编辑状态且不写入原表单', async () => {
    const h = harness(), component = h.component('FormFieldValue', {field: h.field, formSdk: h.sdk, mode: 'component'});
    await editor(component.render()).action.props.onClick();
    editor(component.render()).content.props.onChange({content: '', dataOptions: []});
    h.setFail(true);
    await editor(component.render()).action.props.onClick();
    await acceptChange(h);
    assert.ok(editor(component.render()).content);
    assert.equal(h.syncs.length, 0);
    assert.equal(h.requests[0].data.fields[0].content, '');
});

test('明细展示、文本回退和保存都定位同一个稳定行 ID', async () => {
    const h = harness();
    h.sdk.getDetailRowIdByIndex = () => '错误布局行';
    h.sdk.weFormApi.formStore.getWidgetData = (_, rowId) => ({content: rowId});
    h.sdk.getFieldValue = mark => mark;
    const field = {...h.field, subFormId: '400', value: {}};
    const table = {id: '400', fields: [field], rows: [{rowId: '500', index: 7, values: new Map()}]};
    const detail = h.component('DetailTable', {formSdk: h.sdk, table});
    const view = find(detail.render(), node => node.type === 'Table');
    const props = view.props.columns[1].bodyRender(view.props.data[0]).props;
    const component = h.component('FormFieldValue', props);
    const readonlyProps = component.render().children[0].props;
    const readonly = h.component('ReadonlyFieldValue', readonlyProps);
    const displayed = find(readonly.render(), node => node.type === 'CorsComponent');
    assert.equal(displayed.props.rowId, '500');
    assert.equal(displayed.props.value.content, '500');
    const text = h.component('ReadonlyFieldValue', {...readonlyProps, mode: 'input'});
    assert.equal(find(text.render(), node => node.type === 'Input').props.value, 'field300_500');
    await editor(component.render()).action.props.onClick();
    editor(component.render()).content.props.onChange({content: '明细新值', dataOptions: []});
    await editor(component.render()).action.props.onClick();
    assert.equal(h.requests.length, 0);
    await acceptChange(h);
    assert.equal(h.requests[0].data.fields[0].rowId, '500');
    assert.equal(h.syncs[0][0], 'field300_500');
});

test('新增明细先创建 SDK 草稿行，确认后才提交，空选择时删除按钮不可用', async () => {
    const h = harness();
    const componentTable = {id: '400', title: '明细1', fields: [{...h.field, subFormId: '400'}], rows: []};
    const component = h.component('DetailTable', {formSdk: h.sdk, table: componentTable});
    assert.equal(button(component.render(), '删除选中明细行').props.disabled, true);
    await button(component.render(), '添加明细行').props.onClick();
    const tree = component.render();
    assert.equal(h.requests.length, 0);
    const table = find(tree, node => node.type === 'Table');
    const row = table.props.data[0];
    assert.equal(row.rowId, '501');
    assert.equal(row.isNew, true);
    table.props.columns[1].bodyRender(row).props.row.onDraftChange(h.field, {content: '新增值', dataOptions: []});
    const reopenedTable = h.component('DetailTable', {formSdk: h.sdk, table: componentTable});
    const reopenedView = find(reopenedTable.render(), node => node.type === 'Table');
    const reopenedCell = reopenedView.props.columns[1].bodyRender(reopenedView.props.data[0]);
    const reopenedField = h.component('FormFieldValue', reopenedCell.props);
    assert.equal(editor(reopenedField.render()).content.props.value.content, '新增值');
    await button(component.render(), '保存新增明细行').props.onClick();
    assert.equal(h.requests.length, 0);
    h.pendingChanges.shift();
    assert.ok(button(component.render(), '保存新增明细行'));
    button(component.render(), '保存新增明细行').props.onClick();
    await acceptChange(h);
    assert.equal(h.requests[0].data.operation, 'ADD_ROWS');
    assert.equal(h.requests[0].data.fields[0].content, '新增值');
    assert.equal(button(component.render(), '保存新增明细行'), undefined);
});

test('删除必须先确认，取消不会调用后台或删除 SDK 行', async () => {
    const h = harness(), row = {rowId: '500', index: 1, values: new Map()};
    const component = h.component('DetailTable', {formSdk: h.sdk, table: {id: '400', title: '明细1', fields: [h.field], rows: [row]}});
    find(component.render(), node => node.type === 'Table').props.selection.onSelect(['500']);
    button(component.render(), '删除选中明细行').props.onClick();
    assert.equal(h.requests.length, 0);
    assert.equal(h.deletions.length, 0);
    assert.equal(h.pendingChanges[0].title, '删除选中的 1 行明细？');
    h.pendingChanges.shift();
    assert.equal(h.requests.length, 0);
    assert.equal(button(component.render(), '删除选中明细行').props.disabled, false);
    button(component.render(), '删除选中明细行').props.onClick();
    await acceptChange(h);
    assert.equal(h.requests[0].data.operation, 'DELETE_ROWS');
    assert.equal(h.requests[0].data.rowIds[0], '500');
    assert.equal(h.deletions[0][0], 'field400');
    assert.equal(h.deletions[0][1], '500');
});

test('明细删除保存失败时不删除布局内数据', async () => {
    const h = harness();
    const component = h.component('DetailTable', {formSdk: h.sdk, table: {id: '400', title: '明细1', fields: [h.field], rows: [{rowId: '500', index: 1, values: new Map()}]}});
    find(component.render(), node => node.type === 'Table').props.selection.onSelect(['500']);
    button(component.render(), '删除选中明细行').props.onClick();
    h.setFail(true);
    await acceptChange(h);
    assert.equal(h.deletions.length, 0);
});

test('日志尚未确认时直接显示删除进度并保留选区以便补记', async () => {
    const h = harness();
    const component = h.component('DetailTable', {formSdk: h.sdk, table: {
        id: '400', fields: [h.field], rows: [{rowId: '500', index: 1, values: new Map()}],
    }});
    find(component.render(), node => node.type === 'Table').props.selection.onSelect(['500']);
    h.setSaveResponse({status: false, code: 409, msg: '明细已删除，日志尚未确认，请重试'});
    button(component.render(), '删除选中明细行').props.onClick();
    await acceptChange(h);
    const tree = component.render();
    assert.equal(find(tree, node => node.props.role === 'alert').children[0], '明细已删除，日志尚未确认，请重试');
    assert.equal(button(tree, '删除选中明细行').props.disabled, false);
    assert.equal(h.deletions.length, 0);
});

test('后台删除成功而 SDK 同步失败时清除选择并明确提示已保存', async () => {
    const h = harness();
    h.sdk.delDetailRow = () => { throw new Error('SDK 不可用'); };
    const component = h.component('DetailTable', {formSdk: h.sdk, table: {id: '400', title: '明细1', fields: [h.field], rows: [{rowId: '500', index: 1, values: new Map()}]}});
    find(component.render(), node => node.type === 'Table').props.selection.onSelect(['500']);
    button(component.render(), '删除选中明细行').props.onClick();
    await acceptChange(h);
    assert.equal(h.requests.length, 1);
    const tree = component.render();
    assert.equal(button(tree, '删除选中明细行').props.disabled, true);
    assert.match(find(tree, node => node.props.role === 'alert').children[0], /删除已保存/);
});

test('主表取消编辑丢弃草稿，恢复原值且不调用保存或 SDK', async () => {
    const h = harness(), original = {content: '进入编辑前', dataOptions: []};
    h.sdk.weFormApi.formStore.getWidgetData = () => original;
    const component = h.component('FormFieldValue', {field: h.field, formSdk: h.sdk, mode: 'component'});
    await editor(component.render()).action.props.onClick();
    const content = editor(component.render()).content;
    content.props.value.content = '组件内部修改';
    content.props.onChange({content: '待丢弃的草稿', dataOptions: []});
    assert.equal(original.content, '进入编辑前');
    editor(component.render()).cancel.props.onClick();
    assert.equal(editor(component.render()).content, null);
    assert.equal(editor(component.render()).cancel, null);
    assert.equal(h.requests.length, 0);
    assert.equal(h.syncs.length, 0);
    await editor(component.render()).action.props.onClick();
    assert.equal(editor(component.render()).content.props.value.content, '进入编辑前');
});

test('明细单元格保存失败后也能取消编辑并丢弃草稿', async () => {
    const h = harness();
    const component = h.component('FormFieldValue', {field: {...h.field, subFormId: '400'}, formSdk: h.sdk, mode: 'component', row: {rowId: '479144359725827125', index: 1}, showLabel: false, showModeButton: false});
    await editor(component.render()).action.props.onClick();
    editor(component.render()).content.props.onChange({content: '失败的草稿', dataOptions: []});
    h.setFail(true);
    await editor(component.render()).action.props.onClick();
    await acceptChange(h);
    assert.equal(h.requests[0].data.fields[0].rowId, '479144359725827125');
    editor(component.render()).cancel.props.onClick();
    assert.equal(editor(component.render()).content, null);
    assert.equal(find(component.render(), node => node.props.role === 'alert'), undefined);
    assert.equal(h.syncs.length, 0);
});

test('新增明细保存后回读字符串行 ID，与 SDK 草稿匹配，不追加空白行', async () => {
    const h = harness(), rowId = '479144359725827125', ids = [];
    h.sdk.getDetailAllRowIndexStr = () => ids.join(',');
    h.sdk.addDetailRow = () => ids.push(rowId);
    const table = {id: '400', title: '明细1', fields: [{...h.field, subFormId: '400'}], rows: []};
    const component = h.component('DetailTable', {formSdk: h.sdk, table});
    await button(component.render(), '添加明细行').props.onClick();
    await button(component.render(), '保存新增明细行').props.onClick();
    await acceptChange(h);
    assert.equal(h.requests[0].data.fields[0].rowId, rowId);
    table.rows = [{rowId, index: 1, values: new Map([[h.field.id, h.field]])}];
    const tree = component.render();
    assert.equal(find(tree, node => node.type === 'Table').props.data.length, 1);
    assert.equal(button(tree, '保存新增明细行'), undefined);
    assert.deepEqual(ids, [rowId]);
});

test('取消新增明细先确认，只删除草稿，不请求后台', async () => {
    const h = harness();
    const component = h.component('DetailTable', {formSdk: h.sdk, table: {id: '400', fields: [h.field], rows: []}});
    await button(component.render(), '添加明细行').props.onClick();
    button(component.render(), '取消新增明细行').props.onClick();
    assert.equal(h.deletions.length, 0);
    h.confirmations[0][2]();
    assert.ok(button(component.render(), '保存新增明细行'));
    h.confirmations[0][1]();
    assert.equal(h.deletions[0][1], '501');
    assert.equal(button(component.render(), '取消新增明细行'), undefined);
    assert.equal(h.requests.length, 0);
});

test('未保存表单读取布局内字段和明细值，隐藏持久化操作', () => {
    const h = harness();
    h.context.canPersist = false;
    h.sdk.listFieldInfo = () => [
        {fieldId: '300', type: 'Text'},
        {fieldId: '301', subFormId: '400', type: 'Text'},
        {fieldId: '', subFormId: '400', type: 'DetailTable'},
    ];
    h.sdk.getDetailAllRowIndexStr = mark => mark === 'field400' ? '479144359725827125' : '';
    h.sdk.weFormApi.formStore.getWidgetData = (id, rowId) => ({content: rowId || '主表值'});
    const data = h.readLayoutFormData(h.sdk);
    assert.equal(data.mainFields[0].value.content, '主表值');
    assert.equal(data.detailTables[0].columns.length, 1);
    assert.equal(data.detailTables[0].rows[0].fields[0].value.content, '479144359725827125');
    const field = h.component('FormFieldValue', {field: h.field, formSdk: h.sdk});
    assert.equal(editor(field.render()).action, false);
    const detail = h.component('DetailTable', {formSdk: h.sdk, table: {id: '400', fields: [h.field], rows: []}});
    assert.equal(button(detail.render(), '添加明细行'), undefined);
    assert.equal(h.requests.length, 0);
});
