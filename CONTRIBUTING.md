# 贡献指南

欢迎提交 Issue 和 Pull Request。这个项目目前按个人侧载场景设计，优先保证通知、电话转发的稳定性和隐私边界。

## 开发流程

1. Fork 仓库并创建功能分支。
2. 使用 Docker 构建环境验证：

```bash
docker build --platform linux/amd64 -f docker/Dockerfile.android -t android-forward-builder .
docker run --rm \
  --platform linux/amd64 \
  -u "$(id -u):$(id -g)" \
  -e HOME=/tmp/android-forward-home \
  -e GRADLE_USER_HOME=/workspace/.gradle-docker-cache \
  -e JAVA_TOOL_OPTIONS=-Duser.home=/tmp/android-forward-home \
  -v "$PWD":/workspace \
  android-forward-builder \
  bash -lc 'mkdir -p /tmp/android-forward-home /workspace/.gradle-docker-cache && gradle assembleDebug --no-daemon'
```

3. 不要提交 Bark Key、keystore、签名密码、通知正文或其它个人数据。

## 代码风格

- Kotlin 代码保持简洁，关键方法添加中文注释。
- 新增监听入口时，网络请求必须放到后台线程。
- 日志只保存状态摘要，不保存完整通知正文。
