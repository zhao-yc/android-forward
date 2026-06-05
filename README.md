# 安卓到 iPhone 通知转发

![Platform](https://img.shields.io/badge/platform-Android-3DDC84)
![License](https://img.shields.io/badge/license-MIT-blue)

这是一个个人侧载使用的 Android MVP，用于把安卓手机收到的系统通知、短信、来电和未接来电通过 Bark 明文推送到 iPhone。

## 功能特性

- 监听系统通知并转发应用名、标题、正文和时间。
- 接收新短信并转发发件人、正文和时间。
- 监听来电和未接来电，号码不可用时自动降级为未知号码。
- 通过 Bark 官方接口推送到 iPhone。
- Bark Key 使用 Android Keystore 加密保存。
- 失败重试队列加密暂存，状态日志只保存最近 100 条摘要。

## 使用前准备

1. 在 iPhone 上安装 Bark，并复制 Bark Key。
2. 在安卓手机安装本项目生成的 APK。
3. 打开应用，填写 Bark Key 并保存。
4. 依次授予通知访问、短信权限、电话状态权限，并把应用加入省电白名单。
5. 点击“发送测试推送”，确认 iPhone 能收到通知。

## 本地构建

本机需要 Android SDK 35、JDK 17 或 21、Gradle 8.7。当前项目也提供 Docker 构建环境：

```bash
docker build --platform linux/amd64 -f docker/Dockerfile.android -t android-forward-builder .
docker run --rm \
  -u "$(id -u):$(id -g)" \
  -e HOME=/tmp/android-forward-home \
  -e GRADLE_USER_HOME=/workspace/.gradle-docker-cache \
  -e JAVA_TOOL_OPTIONS=-Duser.home=/tmp/android-forward-home \
  -v "$PWD":/workspace \
  android-forward-builder \
  bash -lc 'mkdir -p /tmp/android-forward-home /workspace/.gradle-docker-cache && gradle assembleDebug --no-daemon'
```

APK 输出路径：

```text
app/build/outputs/apk/debug/app-debug.apk
```

## 安装更新

如果手机提示“与已安装应用签名不一致”，说明旧 APK 和新 APK 不是同一个证书签出来的。需要先卸载手机里的旧版“通知转发”，再安装新的 `app-debug.apk`。

如果希望后续本机 Debug APK 可以稳定覆盖安装，请按 [signing/README.md](signing/README.md) 创建本地固定调试签名。真实 keystore 和密码不要提交到 Git。

## 权限与限制

- 需要用户手动授予通知使用权、短信权限、电话状态权限，并把应用加入省电白名单。
- 项目按个人侧载设计，不按 Google Play 上架合规设计；Google Play 对 SMS/Call Log 权限限制很严格。
- iPhone 后台实时到达依赖 Bark/APNs 和网络状态；安卓后台存活依赖厂商系统策略。

## 隐私说明

本项目按个人自用和侧载安装设计。Bark Key 会用 Android Keystore 加密存储；转发日志只保存类型、来源、状态和错误摘要，不保存完整通知或短信正文。开启失败重试时，待重试的推送内容会加密暂存在本机，发送成功或超过重试次数后删除。
