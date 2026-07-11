# 代码审查与修复报告

更新时间：2026-07-11

## 已修复

1. 登录不再在原生界面收集密码，也不再通过 JavaScript 向 WebView 注入凭据。
2. SSO 顶层导航使用 HTTPS 与完整主机名白名单，禁止混合内容、文件访问和内容访问。
3. 删除 ADB Cookie 注入广播及 Cookie、Token、聊天正文等敏感日志。
4. 登录验证失败不再仅凭 Cookie 名称放行；退出登录会先断开 Gateway 并清空待处理 RPC。
5. Gateway 使用连接代次隔离旧回调，并在 JSON-RPC 握手完成后才进入已连接状态。
6. 新对话使用独立 Dashboard session；流式事件按 sessionKey 和 messageId 路由。
7. 修复发送失败、停止生成及切换会话时流式状态无法复位或回复串线的问题。
8. Release 不再使用 debug keystore；生产签名改由 `MIMO_RELEASE_*` 环境变量注入。
9. 更新版本同时支持语义版本和 CI build 号，网络/解析错误不再伪装成“已是最新版本”。
10. APK 下载至应用私有目录，安装前校验 GitHub 资产 SHA-256、包名和当前应用签名。
11. 关闭明文流量与系统备份，FileProvider 仅暴露更新目录。
12. 任务、浏览、设置页面已接入导航；未实现的设置项不再表现为可点击入口。
13. 补齐 Windows `gradlew.bat`，Release 开启 R8 和资源压缩。

## 自动验证

- `testDebugUnitTest`：5 tests，0 failures。
- `lintDebug`：No issues found。
- `assembleDebug`：成功。
- `assembleRelease`：成功；未配置生产密钥时输出 unsigned APK。
- `apksigner verify app-debug.apk`：通过 APK Signature Scheme v2 验证。

## 发布签名环境变量

- `MIMO_RELEASE_STORE_FILE`
- `MIMO_RELEASE_STORE_PASSWORD`
- `MIMO_RELEASE_KEY_ALIAS`
- `MIMO_RELEASE_KEY_PASSWORD`

正式发布必须使用稳定、受保护的生产密钥，并让 GitHub Release APK 资产包含 `sha256:` digest。
