# APP Logo Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将启动图标替换为原创的“双手机通知转发”标志，并提供 Android 13 主题图标。

**Architecture:** 继续使用现有 Android Adaptive Icon 结构。普通启动图标使用 Image 2 生成的 PNG 彩色前景；Android 13 主题图标通过 `mipmap-anydpi-v33` 自适应图标配置引用单色前景矢量。

**Tech Stack:** PNG、Android VectorDrawable、Adaptive Icon XML、Gradle、Docker

---

### Task 1: 接入 Image 2 彩色启动图标

**Files:**
- Create: `app/src/main/res/drawable-nodpi/ic_launcher_image2.png`
- Create: `app/src/main/res/drawable/ic_launcher_image2_foreground.xml`
- Modify: `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`
- Modify: `app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml`
- Modify: `app/src/main/res/mipmap-anydpi-v33/ic_launcher.xml`
- Modify: `app/src/main/res/mipmap-anydpi-v33/ic_launcher_round.xml`
- Delete: `app/src/main/res/drawable/ic_launcher_foreground.xml`

- [x] **Step 1: 将用户选中的 Image 2 图片复制到 Android 资源目录**

Run:

```bash
cp /Users/zhaoyuchen/.codex/generated_images/019e92df-05e2-73d1-bfca-23a6473baeb4/ig_0e4af0cbf3843217016a2fb3a2bf9481919f6055ac10f8b027.png \
  app/src/main/res/drawable-nodpi/ic_launcher_image2.png
```

Expected: 项目内生成 `ic_launcher_image2.png`。

- [x] **Step 2: 创建铺满自适应图标前景的 BitmapDrawable**

`ic_launcher_image2_foreground.xml` 使用 `android:gravity="fill"` 引用 Image 2 图片。

- [x] **Step 3: 更新普通、圆形和 Android 13 彩色图标引用**

四个 Adaptive Icon 配置均将前景改为：

```xml
<foreground android:drawable="@drawable/ic_launcher_image2_foreground" />
```

- [x] **Step 4: 删除不再使用的手绘彩色前景矢量**

Run:

```bash
test ! -f app/src/main/res/drawable/ic_launcher_foreground.xml
rg 'ic_launcher_image2_foreground' app/src/main/res/mipmap-anydpi-v26 app/src/main/res/mipmap-anydpi-v33
```

Expected: 旧矢量不存在；四个 Adaptive Icon 配置均引用 Image 2 前景。

### Task 2: 增加 Android 13 主题图标

**Files:**
- Create: `app/src/main/res/drawable/ic_launcher_monochrome.xml`
- Create: `app/src/main/res/mipmap-anydpi-v33/ic_launcher.xml`
- Create: `app/src/main/res/mipmap-anydpi-v33/ic_launcher_round.xml`

- [x] **Step 1: 确认主题图标资源尚不存在**

Run:

```bash
test ! -f app/src/main/res/drawable/ic_launcher_monochrome.xml
test ! -f app/src/main/res/mipmap-anydpi-v33/ic_launcher.xml
test ! -f app/src/main/res/mipmap-anydpi-v33/ic_launcher_round.xml
```

Expected: 三条命令均成功。

- [x] **Step 2: 创建单色双手机转发矢量**

创建 `ic_launcher_monochrome.xml`，使用单一白色填充表达两部手机、通知气泡和向右箭头。

- [x] **Step 3: 创建 Android 13 普通与圆形自适应图标配置**

两个 v33 配置均引用：

```xml
<background android:drawable="@color/ic_launcher_background" />
<foreground android:drawable="@drawable/ic_launcher_image2_foreground" />
<monochrome android:drawable="@drawable/ic_launcher_monochrome" />
```

- [x] **Step 4: 检查主题图标引用**

Run:

```bash
rg '<monochrome android:drawable="@drawable/ic_launcher_monochrome"' app/src/main/res/mipmap-anydpi-v33
```

Expected: 普通与圆形自适应图标配置各命中一次。

### Task 3: 构建与视觉验证

**Files:**
- Modify: `.gitignore`
- Verify: `app/src/main/res/drawable-nodpi/ic_launcher_image2.png`
- Verify: `app/src/main/res/drawable/ic_launcher_image2_foreground.xml`
- Verify: `app/src/main/res/drawable/ic_launcher_monochrome.xml`
- Verify: `app/build/outputs/apk/debug/app-debug.apk`

- [x] **Step 1: 忽略视觉讨论临时目录，并检查 XML 与 Git 差异**

Run:

```bash
rg '^\.superpowers/$' .gitignore
git diff --check
```

Expected: `.gitignore` 命中 `.superpowers/`；`git diff --check` 无输出并成功。

- [x] **Step 2: 使用 Docker 编译 Android 资源并构建 APK**

Run:

```bash
docker run --rm --platform linux/amd64 -u 501:20 \
  -e HOME=/tmp/android-forward-home \
  -e GRADLE_USER_HOME=/workspace/.gradle-docker-cache \
  -e JAVA_TOOL_OPTIONS=-Duser.home=/tmp/android-forward-home \
  -v /Users/zhaoyuchen/Documents/ai/android_forward:/workspace \
  android-forward-builder \
  bash -lc 'mkdir -p /tmp/android-forward-home /workspace/.gradle-docker-cache && gradle assembleDebug --no-daemon'
```

Expected: 输出 `BUILD SUCCESSFUL`，并生成 `app/build/outputs/apk/debug/app-debug.apk`。

- [x] **Step 3: 检查 APK 和最终工作区**

Run:

```bash
ls -lh app/build/outputs/apk/debug/app-debug.apk
git status --short
```

Expected: APK 存在；工作区仅包含 Logo 资源、实现计划和视觉讨论临时目录。

- [x] **Step 4: 提交并推送实现**

Run:

```bash
git add .gitignore docs/superpowers/plans/2026-06-15-app-logo-redesign.md app/src/main/res
git commit -m "Redesign app launcher icon"
git push origin main
```

Expected: 提交成功并推送至 `main`。
