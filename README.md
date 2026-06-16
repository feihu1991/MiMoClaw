# MiMo Claw - Android App

小米 MiMo Claw 的 Android 客户端，复刻自 [aistudio.xiaomimimo.com](https://aistudio.xiaomimimo.com)。

## 功能特性

- 🤖 **MiMo Claw** - AI Agent 助手，支持对话、文档生成、代码分析等
- 💬 **MiMo Chat** - 轻量聊天模式
- 📱 **Material 3 Design** - 现代化 Material You 设计语言
- 🌙 **深色模式** - 支持浅色/深色/跟随系统
- 📝 **对话管理** - 创建、查看、删除对话历史
- ⚡ **流式响应** - SSE 流式输出，实时显示 AI 回复
- 🔐 **小米账号登录** - 支持账号密码、手机号、第三方登录
- 📊 **订阅管理** - Free / Basic / Pro / Enterprise 套餐
- 🔌 **API 服务** - 开发者 API 接口文档

## 技术栈

| 类别 | 技术 |
|------|------|
| 语言 | Kotlin |
| UI 框架 | Jetpack Compose + Material 3 |
| 架构 | MVVM + Clean Architecture |
| DI | Hilt |
| 网络 | Retrofit + OkHttp |
| 本地存储 | DataStore Preferences |
| 导航 | Navigation Compose |
| 图片加载 | Coil |
| 最低 SDK | Android 8.0 (API 26) |

## 项目结构

```
app/src/main/java/com/xiaomi/mimoclaw/
├── MainActivity.kt           # 入口 Activity
├── MiMoClawApp.kt           # Application 类
├── data/
│   ├── model/Models.kt      # 数据模型
│   ├── remote/              # 网络 API
│   ├── local/               # 本地存储
│   └── repository/          # 数据仓库
├── di/
│   └── AppModule.kt         # Hilt 依赖注入
└── ui/
    ├── MainViewModel.kt     # 主 ViewModel
    ├── theme/               # 主题配置
    ├── component/           # 可复用组件
    │   ├── ChatBubble.kt    # 聊天气泡
    │   ├── MessageInput.kt  # 消息输入框
    │   ├── FeatureCard.kt   # 功能卡片
    │   └── Sidebar.kt       # 侧边栏
    ├── screen/              # 页面
    │   ├── HomeScreen.kt    # 首页
    │   ├── LoginScreen.kt   # 登录页
    │   ├── ChatScreen.kt    # 聊天页
    │   ├── SettingsScreen.kt# 设置页
    │   ├── SubscribeScreen.kt# 订阅页
    │   └── ApiServiceScreen.kt# API 服务页
    └── navigation/NavGraph.kt # 导航图
```

## 构建与运行

1. 用 Android Studio 打开 `MiMoClaw` 目录
2. 同步 Gradle 依赖
3. 连接设备或启动模拟器
4. 点击 Run 运行

```bash
# 或命令行构建
./gradlew assembleDebug
```

## TODO

- [ ] 接入小米账号 OAuth 登录
- [ ] 接入 MiMo API 实际对话
- [ ] 添加 Room 数据库持久化对话
- [ ] 支持文件/图片附件上传
- [ ] 支持语音输入
- [ ] 添加通知推送
- [ ] 优化 Markdown 渲染
- [ ] 添加 Widget 桌面小组件

## 截图

> 开发中…

## License

Private - Xiaomi MiMo Claw Android Client
