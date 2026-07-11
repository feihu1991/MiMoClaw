# MiMo 控制台 / MiMo Claw 手机版功能审计与接口说明

> 审计日期：2026-07-11  
> 视图：当前桌面 Chrome 已登录会话，视口覆盖为 393 × 852（手机宽度）  
> 范围：`platform.xiaomimimo.com` 的“账单及用量”控制台页、现有 Android 客户端源码，以及当前 MiMo Claw 接入层。  
> 隐私：本文不记录账号、Cookie、Token、手机号、消费金额或用量数值。

## 1. 结论与产品边界

目前 Android 项目是一个 **MiMo Claw 对话客户端**，而不是完整的 MiMo 开放平台控制台。它已具备：小米账号网页登录、用户信息校验、WebSocket 会话列表、流式聊天、历史会话、会话模型切换、浏览器页和应用更新。

控制台的“了解更多”菜单中存在 **MiMo Claw** 入口，目标为 `https://aistudio.xiaomimimo.com/#/?forcePage=claw`。该入口打开的是网页端 MiMo Claw 工作区/对话框，而非一个独立的账单页。因此手机端应把“Claw 工作区”视为控制台生态中的工作区入口：它与控制台共享小米账号登录态，但职责不同。

控制台网页版已验证的“账单及用量”能力包括：累计消费、Token 历史消耗分项、音频转写时长、插件服务调用次数、搜索服务调用次数、按月筛选、按 API Key 筛选、图表/列表切换、导出、月度账单，以及语言模型/语音识别/语音合成三个用量视图。

**云文件浏览器和文件下载目前不在 Android 代码中。** 这是需要新增的控制台模块；不能把它描述成已经实现的功能，更不能猜测其私有 API。

## 2. 手机版信息架构

建议底部四个一级入口：

| 入口 | 页面职责 | 当前状态 |
| --- | --- | --- |
| Claw 工作区 | MiMo Claw 会话、流式回复、模型切换、云端工作区 | 已有对话；文件待补 |
| 控制台 | 用量概览、账单、API Key、模型、文件 | 用量入口已设计；其余待补 |
| 文件 | 云端文件列表、搜索、预览、下载 | 待实现 |
| 设置 | 账号、主题、版本、退出登录 | 已有 |

“浏览”不应再是一级产品入口；它可以作为控制台页内“打开官网”的退路，用于当前没有官方公开 App API 的页面。控制台内的“MiMo Claw”点击后应直接进入 Android 的 Claw 工作区；如该工作区尚未原生支持某项能力，再使用受控网页承接。

### 2.1 已验证的控制台侧栏菜单

| 分组 | 网页入口 | 路径 | 手机版目标 |
| --- | --- | --- | --- |
| 账号 | 个人中心 | `/console/profile` | 原生账户页 |
| 账号 | API Keys | `/console/api-keys` | 原生密钥页；未确认接口前网页承接 |
| 财务 | 账户余额 | `/console/balance` | 原生余额卡 + 网页详情 |
| 财务 | Token Plan | `/console/plan-manage` | 原生订阅页 + 网页详情 |
| 财务 | 账单明细 | `/console/usage` | 原生用量/账单页 |
| 财务 | 充值明细 | `/console/recharge` | 原生列表 + 网页详情 |
| 财务 | 发票开具 | `/console/invoice` | 网页承接；外部副作用操作 |
| 插件 | 插件管理 | `/console/plugin` | 原生插件列表；未确认接口前网页承接 |
| 了解更多 | MiMo Claw | `aistudio…/?forcePage=claw` | **进入 Claw 工作区** |
| 了解更多 | 与 MiMo 对话 | `aistudio…/?forcePage=chat` | 进入普通 MiMo 对话 |

## 3. 已验证的控制台网页能力

### 3.1 全局按钮与入口

| 页面元素 | 行为 | 手机版实现建议 |
| --- | --- | --- |
| 邀请 | 邀请成员/协作方 | 仅在确认个人账号支持后显示；否则隐藏 |
| 更多操作 | 打开附加账户操作 | 收入顶部溢出菜单 |
| 展开侧边栏 | 展开控制台导航 | 手机版改为抽屉菜单 |
| 使用详情 | 查看周期内明细 | 控制台二级 Tab |
| 月度账单 | 查看月度结算账单 | 控制台二级 Tab |
| 年 / 月 | 聚合粒度切换 | 分段控件 |
| 选择月份 | 筛选结算月份 | 月份选择器 |
| 选择 API Key | 按密钥筛选消耗 | 仅显示密钥备注和末四位 |
| 图表 / 列表 | 切换用量展示方式 | 分段控件 |
| 导出 | 导出当前筛选结果 | 使用 Android 文件创建器保存，不自动下载 |
| 语言模型 / 语音识别模型 / 语音合成模型 | 按模型类别查看用量 | 顶部横向 Tab |

### 3.2 用量字段

| 指标 | 网页呈现 | 手机端数据格式 |
| --- | --- | --- |
| 累计消费 | 金额 | `currency` + 十进制字符串，避免 `Float` 精度问题 |
| Token 历史消耗 | 总量 | `Long` 或字符串 |
| 输入（命中缓存）Token | 分项总量 | `Long` |
| 输入（未命中缓存）Token | 分项总量 | `Long` |
| 输出 Token | 分项总量 | `Long` |
| 音频转写总时长 | 秒 | `Long`，展示时格式化为 h/m/s |
| 插件服务成功调用次数 | 次数 | `Long` |
| 搜索服务成功调用次数 | 次数 | `Long` |
| 总体消费金额 | 当前筛选下金额 | `currency` + 十进制字符串 |
| Token 总消耗 | 当前筛选下总量 | `Long` |
| 请求次数 | 当前筛选下次数 | `Long` |
| 插件调用次数 | 当前筛选下次数 | `Long` |

网页提示数据为准实时统计，存在短暂延迟，且每日会进行最终校对。手机端需要同时显示“更新时间”和“结算数据可能修订”的说明。

## 4. 当前 Android 客户端已实现的官方站点接口

所有 REST 请求基址：`https://aistudio.xiaomimimo.com/`。认证依赖官方登录页写入 App WebView Cookie；不得把 Cookie、票据或密码写入日志、数据库或导出文件。

### 4.1 登录与用户

| 方法 | 路径 | 请求 | 成功返回（已在源码建模） | 客户端用途 |
| --- | --- | --- | --- | --- |
| GET | `/open-apis/user/mi/get` | Cookie | `{ code, data: { userId, nickname, avatar, isClawDisclaimerAgreed } }` | 登录校验、用户资料 |
| GET | `/open-apis/bot/config` | Cookie | `{ code, data: { model } }` | 默认模型配置 |
| GET | `/open-apis/user/ws/ticket` | Query: `xiaomichatbot_ph`；Cookie | `{ code, data: { ticket } }` 或 `{ ticket }` | 创建 WebSocket 连接 |

`code` 的具体错误码表未在公开页面/当前代码中给出；客户端应同时处理非 2xx、`code != 0`、缺少 `data` 和空字段。

### 4.2 渠道登录

| 方法 | 路径 | 请求 | 返回 | 状态 |
| --- | --- | --- | --- | --- |
| POST | `/open-apis/user/mimo-claw/channel-login/qrcode` | Query: `channel` | `{ qrContent, qrVersion }` | 已建模，UI 尚未接入 |
| GET | `/open-apis/user/mimo-claw/channel-login/status` | Query: `channel` | `{ status, qrContent, qrVersion, message }` | 已建模，UI 尚未接入 |
| POST | `/open-apis/user/mimo-claw/channel/logout` | Query: `channel` | 空响应（HTTP 成功即成功） | 已建模，UI 尚未接入 |

`status` 已知枚举：`QR_READY`、`SUCCESS`、`EXPIRED`、`ENDED`、`NO_POD`、`UNSUPPORTED`。未知枚举必须展示原始状态并允许重试，不能当作成功。

### 4.3 MiMo Claw WebSocket / JSON-RPC

WebSocket：`wss://aistudio.xiaomimimo.com/ws/proxy?ticket={ticket}`。

统一 RPC 信封：

```json
{ "id": "UUID", "method": "<method>", "params": { } }
```

统一响应信封：

```json
{ "type": "res", "id": "UUID", "ok": true, "payload": { } }
```

失败响应：`ok: false`，错误信息可能在 `error` 或 `message`。客户端必须保留请求 ID 与超时控制。

| 方法 | 参数 | 已知成功 `payload` | 用途 |
| --- | --- | --- | --- |
| `connect` | `minProtocol`、`maxProtocol`、`client`、`caps`、`locale` | `server.connId` | 完成挑战后的握手 |
| `sessions.subscribe` | `{}` | 未依赖字段 | 订阅会话变化 |
| `sessions.list` | `includeGlobal`、`includeUnknown`、`limit` | `sessions[]`：`key`、`sessionId`、`model`、`updatedAt`，标题由客户端兼容解析 | 会话列表 |
| `chat.send` | `sessionKey`、`message`、`idempotencyKey` | 成功确认（当前不读取字段） | 发送消息 |
| `chat.abort` | `sessionKey` | 成功确认 | 中止生成 |
| `chat.history` | `sessionKey`、`limit` | `messages[]`：`role`、`content` | 历史消息 |
| `config.get` | `{}` | `baseHash`、配置文本 | 获取配置版本 |
| `config.patch` | `raw`、可选 `baseHash` | 成功确认 | 切换会话模型 |

已处理事件：

| 事件 | 读取字段 | 转换后的 App 事件 |
| --- | --- | --- |
| `connect.challenge` | 无需暴露 | 发起 `connect` |
| `agent` | `payload.data.delta` | 文本增量 |
| `chat` | `payload.deltaText` | 文本增量 |
| `session.message` | `payload.message.content[]`，块类型 `thinking` / `text` | 思考块、文本块 |
| `sessions.changed` | 当前仅记录 | 应补充会话列表刷新 |
| `session.operation`、`session.tool` | 当前仅记录 | 应映射为任务步骤/工具执行卡片 |

## 5. 云文件浏览器与下载：新增规格，不是已发现接口

网页和当前 Android 源码均未给出云文件服务的公开路径或返回结构。因此以下是**手机版需要的前端契约**，不是对小米私有 API 的断言。实现前必须在已授权浏览器会话中逐项抓取并确认实际请求与响应。

| 能力 | 建议前端抽象 | 需要的成功返回 | 关键状态 |
| --- | --- | --- | --- |
| 根目录/目录列表 | `listFiles(parentId, cursor)` | `items[]`、`nextCursor` 或 `hasMore` | loading / empty / error |
| 文件详情 | `getFile(id)` | `id`、`name`、`mimeType`、`sizeBytes`、`updatedAt`、`previewUrl?` | not found / forbidden |
| 搜索 | `searchFiles(query, cursor)` | `items[]`、分页游标 | query empty / no result |
| 下载 | `createDownload(id)` | 短时 `downloadUrl` 或下载任务 `id` | preparing / expired / failed |
| 删除 | `deleteFile(id)` | 成功状态或任务 ID | 必须二次确认 |
| 重命名 | `renameFile(id, name)` | 更新后的元数据 | 重名 / 无权限 |

### 下载安全要求

1. 只接受 HTTPS 下载地址；不在日志中记录 URL 查询参数。
2. 下载前展示文件名、类型、大小；大文件或移动网络需确认。
3. 使用 Android `DownloadManager` 或受控流式下载，写入应用专属 Downloads 目录。
4. 完成后通过 `FileProvider` 打开；不把私有文件路径暴露给其他 App。
5. 若后端提供校验和，下载完成后校验；若没有，至少校验 Content-Length 与 MIME 类型。

## 6. 手机版实现清单

### P0：先完成

- 控制台首页：用量摘要、更新时间、余额/消费、模型服务状态。
- 使用详情：月份、API Key、模型类型、图表/列表、导出。
- 文件入口：先以官网受控浏览器页承接；在未确认私有接口前不做伪原生文件列表。
- 对话中的 `session.tool` / `session.operation` 可视化为任务执行步骤。

### P1：确认接口后完成

- API Key 列表、创建、禁用、删除（密钥原文只允许展示一次）。
- 月度账单与导出。
- 原生云文件浏览、预览、下载、删除、重命名。
- 账户邀请和成员管理（仅当个人/团队权限允许时显示）。

## 7. 设计准则

- 以手机任务为中心：顶部概览、底部一级导航、筛选收进底部抽屉。
- 不展示伪造消费数字；接口未同步时显示“尚未同步”和官网入口。
- API Key 仅显示备注与末四位，绝不在列表、日志、截图或分享内容中输出完整值。
- 账单、下载、删除、密钥管理均要有空态、加载态、失败态与权限不足态。
- 网页控制台仍可作为保底入口，但原生页面必须与其筛选语义和字段口径一致。

## 8. 尚待验证的项目

1. 控制台侧栏的完整菜单及每一页的网络请求。
2. API Key、账单导出、成员邀请是否对当前账号开放，以及具体返回字段。
3. MiMo Claw 云文件浏览器的入口、文件权限模型、下载接口、预览格式和分页契约。
4. `session.operation` 与 `session.tool` 的完整 WebSocket 事件 payload。

在这些项目完成只读抓包前，手机版可以实现导航和状态界面，但不应把推测的路径或返回字段写成生产接口。
