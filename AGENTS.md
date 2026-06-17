# Repository Guidelines

## 项目结构与模块组织

本仓库是单模块 Android 应用，根项目名为 `AndroidForward`，实际代码在 `app` 模块。Kotlin 源码位于 `app/src/main/java/com/zhaoyuchen/androidforward`，按职责分为 `forward`、`data`、`service`、`receiver`、`bluetooth` 等包；界面入口在 `MainActivity.kt`。资源位于 `app/src/main/res`，图标和图片分别放在 `drawable*`、`mipmap*`、`values`。构建相关文件包括根目录 `build.gradle.kts`、`settings.gradle.kts` 和 `app/build.gradle.kts`。签名示例在 `signing/`，真实 keystore 放在本地 `keystore/`，不要提交。

## 构建、测试与开发命令

- `gradle assembleDebug --no-daemon`：构建 Debug APK，输出到 `app/build/outputs/apk/debug/app-debug.apk`。
- `docker build --platform linux/amd64 -f docker/Dockerfile.android -t android-forward-builder .`：构建统一 Android 编译环境。
- `docker run --rm --platform linux/amd64 -u "$(id -u):$(id -g)" -e HOME=/tmp/android-forward-home -e GRADLE_USER_HOME=/workspace/.gradle-docker-cache -e JAVA_TOOL_OPTIONS=-Duser.home=/tmp/android-forward-home -v "$PWD":/workspace android-forward-builder bash -lc 'mkdir -p /tmp/android-forward-home /workspace/.gradle-docker-cache && gradle assembleDebug --no-daemon'`：在 Docker 中复现本地构建。
- `gradle testDebugUnitTest`：运行单元测试；新增测试后应在提交前执行。

## 代码风格与命名约定

使用 Kotlin、Jetpack Compose、JDK 17 目标字节码。保持 4 空格缩进，类名使用 `PascalCase`，函数和属性使用 `camelCase`，包名保持小写。关键方法和复杂逻辑添加简体中文注释。新增监听入口时，网络请求必须放到后台线程，避免阻塞广播、服务或 UI。

## 测试指南

当前仓库尚未包含测试目录。新增纯逻辑测试放在 `app/src/test`，设备或权限相关测试放在 `app/src/androidTest`。测试文件建议以被测类命名，例如 `RetryQueueTest.kt`。涉及通知、电话和蓝牙行为时，优先隔离状态存储、重试队列和转发调度逻辑。

## 提交与 Pull Request 规范

Git 历史使用简短英文祈使句，例如 `Add safe margin to launcher icon`、`Fix Bluetooth disconnect UI refresh`。提交应聚焦单一改动。PR 需说明变更目的、验证命令、权限或隐私影响；涉及 UI 或图标时附截图；关联 Issue 时在描述中链接。

## 安全与配置提示

不要提交 Bark Key、通知正文、keystore、签名密码或个人设备信息。日志只保存状态摘要，不保存完整消息内容。固定调试签名请复制 `signing/debug-signing.properties.example` 到未提交的本地配置文件。
