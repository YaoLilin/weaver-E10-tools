# 开发者工具开发说明

## 作者信息

作者：姚礼林  
日期：2026-10-16

## 功能说明

1. 提供独立的开发者工具页面，左侧为工具菜单，右侧展示选中工具。目前包含“日志”菜单。
2. 打开日志页面后显示当前日志级别配置。
3. 可选择 DEBUG、INFO、WARN、ERROR，点击“提交”保存日志级别，保存后自动刷新当前配置。
4. 点击“刷新”可重新读取配置；查询或保存失败时，页面显示原因。处理期间禁用操作，避免重复提交。

生效范围：PC 端独立页面，需登录系统，并具备后端日志配置接口的访问权限。

![开发者工具 E10 公共组件页面效果](${appMdRes}/dev-tools-e10-components.png)

## 实现说明

### 页面与菜单

`entry.js` 通过 `regOvProps` 复写 `weappEcodesdk` 的 `Switch` 参数，使用 `regReactChildren` 注册独立页面路由。路径包含应用的 `appUUID`，并通过 `publicUrl` 适配部署路径。

页面使用 `React.lazy`、平台 `asyncImport` 和 `React.Suspense` 按需加载 `DevTools`。`DevTools.js` 管理左侧菜单选择，右侧由 `coms/LogPage.js` 展示日志配置。

界面优先使用 `@weapp/ui` 的 E10 公共组件：

| 界面内容 | E10 组件 |
| --- | --- |
| 左侧工具菜单 | `Menu` |
| 日志级别选择框 | `Select` |
| 提交和刷新按钮 | `Button` |
| 配置行与操作区布局 | `Layout.Row`、`Layout.Col` |
| 查询与提交加载提示 | `Spin` |

业务状态由页面自身管理，控件及加载提示使用平台默认视觉样式；自定义样式仅负责页面尺寸、间距和左右区域布局。页面不额外显示“开发者工具”和“日志”标题栏，级别选择框标签为“修改日志级别”。

### 日志级别查询与修改

通过 `devRequest` 调用后端 `DevToolsController` 提供的接口：

| 请求方式 | 接口路径 | 用途 |
| --- | --- | --- |
| GET | `/api/secondev/dev-tools/log-level` | 读取当前日志级别配置 |
| POST | `/api/secondev/dev-tools/log-level` | 保存选中的日志级别 |

POST 请求体示例：

```json
{
  "level": "INFO"
}
```

`utils/logApi.js` 根据 `WeaResult.status` 判断业务结果，并读取 `data` 中的配置说明或保存结果。查询返回的“当前日志级别: …”用于回显选择框；未配置或无法识别时不预选级别，由用户选择后提交。

保存成功后再次查询配置。若保存成功但刷新失败，页面分别显示保存结果和查询错误，用户可点击“刷新”重试。

### 后端依赖

目标系统需部署 `DevToolsController` 及其依赖的 `ConfigFileService`。现有后端读取和更新 `weaver-secondev-service.properties` 中的 `logging.level.com.weaver.seconddev`；该页面展示和修改的是配置文件中的值，不用于查询各服务实例当前运行时的日志状态。

## 二开说明

| 二开方式 | 作用对象 | 实现功能 |
| --- | --- | --- |
| 组件参数复写 `regOvProps` | `weappEcodesdk.Switch` | 注入开发者工具页面路由，进入指定地址时展示工具菜单和日志配置 |

页面及新增组件使用 `appid-${appId} seconddev-ecode` 样式标记。路由注册成功后输出包含应用标识的控制台日志，便于定位二开入口。

## 页面地址

在当前系统域名及部署前缀后访问：

```text
/sp/custom/801378248580521305/index
```

其中 `801378248580521305` 为应用 `appUUID`。

##
<br>
<div style="text-align:center">
  <div>
    Develop by <div style="font-size:5em;font-weight:bold">YLL</div>
  </div>
  <span>yaolilin.com</span>
</div>
