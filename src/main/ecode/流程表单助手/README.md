# 流程表单助手开发说明

## 作者信息

作者：姚礼林  
日期：2026-09-24

## 功能说明
表单助手支持工作流表单与 EB 表单，便于实施人员查看完整字段和已保存的字段值。
```text
⚠️ 注意：仅限管理员使用，不得开放给普通用户,请在该 ecode 应用设置生效范围，只限管理员角色
```

流程表单页面右下角提供可拖动的入口按钮。点击入口后，从右侧打开流程表单助手抽屉。

抽屉包含以下标签页：

- 表单字段：首个标签页。搜索框位于主表字段上方，同时搜索主表和明细字段的中文名称、数据库列名、字段 ID 和字段值；命中明细字段时，仅显示包含命中列的明细表，并保留表格行。主表字段分为“有值字段”和“无值字段”两组，默认只展开有值组，可手动展开无值组；搜索命中无值字段时自动显示无值组。主表采用随抽屉宽度自动扩展的网格，明细表采用与流程明细一致的表格样式。主表字段和明细列默认使用只读组件模式，切换按钮显示当前模式及切换图标；布局内字段沿用表单组件配置，布局外字段尝试使用后端字段元数据中的组件配置及值展示组件，配置或组件值不可用时回退为只读文本框。文本模式显示数据库原始值，如附件 ID，而非文件名。无数据的明细表仍展示全部列标题，表体显示“暂无数据”。
- 信息复制：字段信息对话框、流程/表单信息及节点列表中的名称、数据库字段名和 ID 提供 Icon-copy 按钮，按项复制原始值并提示结果，空值不提供复制按钮。
- 字段信息：主表字段和明细列的模式切换按钮旁提供指定 SVG 信息图标，点击打开只读对话框，显示数据库字段名和字段 ID，内容沿用流程信息标签页的分隔线、字号和颜色样式；数据库字段名优先读取接口列名，其次读取字段组件配置中的 columnName，缺失时显示“未提供”。
- 流程信息：展示当前流程的流程 ID、表单 ID、数据 ID 和当前节点 ID。
- 节点列表：展示当前流程全部节点的名称和 ID。

悬浮入口会记住用户最后一次拖动位置；点击抽屉外部遮罩、关闭按钮或按 Esc 均可关闭抽屉。

生效范围：PC 端流程表单页面 `/sp/workflow/flowpage/...`，以及 EB 表单卡片页面 `/sp/ebdfpage/card/...`。应用发布范围需同时覆盖这两类页面，并继续限制为管理员角色。

EB 列表通过侧栏或弹出窗口打开记录时，监听表单初始化事件，在当前弹层外层显示助手入口，避免弹窗内容层的 transform 导致固定定位偏移，并绑定该侧栏的表单实例；关闭侧栏后清理入口，多层侧栏仅显示最上层助手。复制图标使用灰色。

EB 页面显示“EB 表单助手”，支持相同的主表、明细、字段搜索、文本/组件切换及明细分页。EB 的“表单信息”展示表单名称、应用 ID、表单 ID、数据 ID，不展示工作流节点。字段列表包含未铺入布局的字段；取值为已保存记录的数据，尚无数据 ID 的新建表单不能查询完整字段值。

每张明细表独立分页：超过 50 行时，每页显示 50 行，可切换页码或输入页码直接跳页；50 行及以下不显示分页栏。翻页后序号连续，字段仍对应原明细行。分页范围为接口实际返回的明细数据。

关联文档使用平台文档阅读对话框打开。助手通过流程表单 Route 保留当前流程路径，字段组件复用当前表单的 `formProps`，由平台携带流程关联信息及当前文档鉴权参数；不自行拼接文档链接或保存鉴权值。


![](${appMdRes}/1787131418-image.png)  


下图为此前的表单字段页面；本次搜索位置、明细列筛选与组件显示效果需在部署生效后重新截图：  


![](${appMdRes}/flow-form-assistant-fields.png)  


「流程信息」标签页：  


![](${appMdRes}/1787131443-image.png)

## 配置说明

修改本应用的 `/config/config.js` 文件。

配置示例：

```javascript
export default {
    drawerWidth: '40%',
};
```

- `drawerWidth`：抽屉宽度，支持百分比，如 `'40%'`；也支持像素值，如 `'480px'`。

## 数据获取说明

打开助手时保存当前表单 SDK 实例，后续字段渲染使用该实例。接口参数增加 `module`，取当前 SDK 的 `getBaseInfo().module`：工作流为 `workflow`，EB 为 `ebuilderform`。后端按模块查询字段库及组件元数据，EB 字段值通过通用表单数据服务的 `getFullFormData` 取得；不依赖当前页面布局过滤字段。

流程信息从当前流程表单 SDK 的基础信息中读取：

- 流程 ID：`customParam.workflowId`
- 表单 ID：`formId`
- 数据 ID：`dataId`
- 当前节点 ID：`customParam.nodeId`

表单字段使用 `devRequest` 请求后端接口 `GET /api/secondev/hnweaver/form-assist/field-value?formId={formId}&dataId={dataId}`。该接口从表单字段库读取字段定义，并通过 `getFieldMapByFormId` 补充数据库列名及组件配置，再查询当前表单数据，包含未铺入布局的主表字段和明细列；主表字段由 `mainFields` 返回，明细表通过 `detailTables[].columns` 和 `detailTables[].rows[].fields` 返回。主表使用 `hasValue` 分组，主表及明细列搜索均匹配 `title`、`columnName`、`fieldId`、字段值与 `dataKey`，明细行中的值也参与其所属列的匹配。文本模式优先显示 `value.content`，为空时使用 `optionId`、`dataOptions[].optionId` 或附件 `fileId`；组件模式优先使用表单 SDK 的字段配置及 `formStore` 组件值，SDK 无字段配置时使用上述后端接口返回的 `componentConfig` 和字段值构造只读 `FormSwitch` 参数。布局外附件字段从后端返回的 `dataOptions` 取得文件 ID、名称及受限的文件属性；平台元数据未提供有效组件配置时回退为只读文本。布局外字段的具体显示效果需在后端部署后于目标页面验证。

接口服务端仅允许系统管理员调用。部署时还需在 E-code 前端二开准入名单中允许调用 `/api/secondev/hnweaver/form-assist/field-value`。

节点列表使用当前流程 ID 请求 `/api/bs/workflow/pathdef/nodeBase/getNodeList`，展示返回节点的 `id` 和 `nodeName`。

## 部署说明

本应用及后续改动统一执行 `./gradlew :demo:jar`，使用 `demo/build/libs/build.zip` 上传后端；demo 包会合并 common 模块实现，不单独部署 common 模块 JAR。

先部署支持 `module=ebuilderform` 的后端 JAR，再使用 E10 前端补丁工具构建本应用，将 ZIP 导入目标 E-code 应用。保持管理员角色限制，并确认应用生效范围包含流程和 EB 卡片页面。后端未更新时，不能把 EB 全字段能力作为已生效功能。

##
<br>
<div style="text-align:center">
  <div >
    Develop by <div style="font-size:5em;font-weight:bold">YLL</div>
  </div>
  <span>yaolilin.com</span>
</div>
