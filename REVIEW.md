# 代码审查报告

## 🔴 需修复 (会导致功能异常)

### 1. UpdateChecker 版本比较逻辑反了
- **问题**: `build-28` (CI 包号) vs `versionCode=3` (App版本)，每次都会提示更新
- **原因**: CI 每次构建都生成 `build-{N}` tag，N 永远大于 App 的 versionCode
- **修复**: 版本格式优先级改为 `v{x}.{y}.{z}` > `build-{N}`，或只比较同格式

### 2. LoginScreen: isLoading 状态捕获问题
- **问题**: `WebView.factory` 只执行一次，闭包捕获的 `isLoading` 是初始值 `false`
- **影响**: `onPageStarted` 中 `isLoading = true` 不会触发 Compose 重组
- **修复**: 使用 `rememberUpdatedState` 或将状态提升到 WebView 外部

### 3. SettingsScreen: 缺少 collectAsState
- **问题**: `updateState` 直接读取 StateFlow 对象，未收集为 Compose State
- **影响**: 设置页的"检查更新"副标题不会实时更新
- **修复**: 添加 `.collectAsState()`

## 🟡 建议优化

### 4. UpdateViewModel: 下载轮询无超时
- **问题**: `pollDownloadProgress` 是无限循环，仅在下载完成/失败时退出
- **风险**: 如果 DownloadManager 状态异常，协程永远挂起
- **修复**: 添加最大等待时间 (如 10 分钟)

### 5. UpdateViewModel: installApk 逻辑冗余
- **问题**: 先用 `getUriForDownloadedFile` 拿 URI，又检查文件是否存在再换 FileProvider
- **简化**: 直接用 FileProvider 构造 URI 即可

### 6. LoginScreen: 多余的 @OptIn 注解
- **问题**: `@OptIn(ExperimentalMaterial3Api::class)` 但未使用实验性 API
- **修复**: 移除

### 7. SettingsScreen: 多余的 import
- **问题**: `import androidx.compose.runtime.collectAsState` 可能未被识别
- **原因**: 缺少实际调用
