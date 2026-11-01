# 动作流 Action 参数自动获取开发说明

## 作者信息
作者：姚礼林  
日期：2026-07-13

## 功能说明
在动作流设计器的 Serverless/Action 组件参数配置对话框中，增加“获取参数”按钮。按钮会根据当前对话框标题自动识别输入参数或输出参数，调用后端接口获取 Action 类定义的参数，并补充或更新参数表格。

支持普通参数、嵌套对象和 List 参数。嵌套对象及 List 子属性会按树形结构写入表格；Long、Integer 映射为整数，Float、Double、BigDecimal 映射为浮点数。

组件加载后会自动获取 Action 参数定义，检查表格是否缺少必填参数。发现缺失时，在表格上方显示 warning 提示：`Action 参数存在必填参数缺失，请点击“自动获取参数”按钮进行补充。`

参数获取接口：

- 输入参数：`GET /api/secondev/esb/action/params/input?groupId={groupId}`
- 输出参数：`GET /api/secondev/esb/action/params/output?groupId={groupId}`

`groupId` 为 Action 类 `@Service` 注解中的名称。Action 类需要继承 `AbstractEsbAction`，并为泛型参数指定具体对象类型。

![](${appMdRes}/1783929250-获取参数2.gif)

## 必需使用二开公共类库
后端必需部署二开公共类库才能使用此开发功能，二开公共类库获取地址：https://gitee.com/yaolilin/weaver-e10-second-dev-common

## 二开说明
接口拦截：

- 拦截 `/api/bs/esb/setting/flow/design/getDesignBySceneId/{sceneId}`，读取动作流设计数据中的 `actions`，用于根据当前组件 `wId` 获取对应 Action 的 `groupId`。

组件参数重写：

- 重写 `Dialog` 组件参数，在打开 Serverless/Action 组件配置对话框时读取当前 `wId`。

组件替换：

- 替换 `EditableTable` 组件。仅在动作流设计器中、当前组件为 Serverless/Action 参数表格时，使用 `ActionParamTable` 包装原表格；其他场景继续使用原组件。
