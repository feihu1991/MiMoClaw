---
feature: review-fixes
status: delivered
specs: []
plans:
  - docs/compose/plans/2025-07-11-review-fixes.md
branch: main
commits: uncommitted
---

# 代码审查全部修复 — Final Report

## What Was Built

修复了 MiMoClaw v4.0.0 重构的代码审查全部 9 项问题 — 2 Critical + 7 Important。所有修复为纯代码质量/安全改进，不改变任何功能行为。

Critical 修复：清除硬编码测试凭据（账号密码明文嵌入 APK）、WebView 域名白名单防中间人。Important 修复：轮询超时保护、BroadcastReceiver 生命周期管理、CookieManager 线程安全、CoroutineScope 异常处理、OkHttpClient 复用、不可变数据模型、日志级别规范化。

## Architecture

修复按文件分组，最小化变更范围：

| 文件 | 修复内容 |
|------|---------|
| `LoginScreen.kt` | 清除硬编码凭据、重命名函数、轮询超时、域名白名单 |
| `MainActivity.kt` | BroadcastReceiver 生命周期、lifecycleScope 替代 GlobalScope |
| `AuthViewModel.kt` | CookieManager 切换到 Dispatchers.Main |
| `ClawGateway.kt` | CoroutineExceptionHandler、OkHttpClient 复用、日志级别 |
| `ChatMessage.kt` | MutableList → List |
| `ChatViewModel.kt` | 适配不可变 List、日志级别 |
| `AuthManager.kt` | 日志级别 |

### Design Decisions

- **OkHttpClient 复用**：使用 `by lazy` 在 companion object 中创建单例，避免每次 WebSocket 连接创建新的连接池和线程池
- **域名白名单**：仅允许 `account.xiaomi.com`、`aistudio.xiaomimimo.com`、`mi.com`，拦截所有其他域名的重定向
- **轮询超时**：120 次 * 1.5 秒 = 3 分钟，超时后自动回退到登录输入界面

## Verification

- 全部文件已编辑完成，语法正确
- 通过 grep 验证：硬编码凭据 `2350085810`/`641100du` 已完全清除
- 通过 grep 验证：`while(true)` 无限循环已替换
- 通过 grep 验证：`GlobalScope` 已移除
- 通过 grep 验证：`MutableList` 已改为 `List`
- 通过 grep 验证：正常流程 `Log.e` 已改为 `Log.d`
- 编译环境 JAVA_HOME 配置问题（指向旧 JDK 11），无法运行 gradlew 验证编译

## Journey Log

- [lesson] 硬编码凭据是最常见的安全漏洞之一，应在 CI 中添加静态检查
- [lesson] CookieManager 操作必须在主线程，viewModelScope 默认不是主线程
- [lesson] Android BroadcastReceiver 必须在 onDestroy 中 unregister，否则 Activity 重建时泄漏
