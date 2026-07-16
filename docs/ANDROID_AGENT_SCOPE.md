# Android Agent 能力边界

MiMoClaw 的 Android Agent 以“手机上完成安全的代码修改和 Git 协作”为目标，不尝试在本地复制完整桌面开发环境。

## 本地支持

- 克隆或导入 Git 项目到应用私有工作区
- 浏览目录、搜索文本和读取文件
- 新建、修改、移动和删除项目文件
- 生成并应用单文件或多文件 Patch
- 展示 Diff，并在写入前请求确认
- Git status、diff、branch、stage、commit、fetch、pull 和 push
- 创建远程分支及 Pull Request
- 保存会话、任务进度、检查点和项目记忆
- 对删除文件、覆盖内容、提交和推送等操作执行分级授权

## 本地不支持

- Gradle、Maven、Node、Python、Go、Rust 等完整构建环境
- APK、AAB、桌面安装包或容器镜像打包
- 应用商店上传、服务器发布和生产部署
- 任意 Bash、PowerShell 或不受限制的系统命令
- 下载后直接执行未知二进制文件
- 自动向默认分支强制推送

## Git 默认流程

1. Agent 读取项目并提出修改方案。
2. Agent 生成 Patch，界面展示逐文件 Diff。
3. 用户批准后写入本地工作区。
4. Agent 创建 `agent/*` 分支并仅暂存本轮相关文件。
5. 用户确认提交信息后创建 Commit。
6. 用户确认远程目标后 Push 分支。
7. App 创建草稿 Pull Request。

默认不直接推送 `main`，不允许静默执行 `force push`、删除远程分支或破坏本地历史的 Git 操作。

## 可选外部能力

未来如需运行测试、编译或启动语言服务器，应通过用户明确配置的电脑端 Local Runner 完成。Local Runner 是可选增强，不属于 Android 本地 Agent 的基础能力，也不影响手机端独立完成文件修改、Git 提交和创建 Pull Request。
