# UpdateLib
[![](https://jitpack.io/v/miaoqidong/UpdateLib.svg)](https://jitpack.io/#miaoqidong/UpdateLib)

基于 GitHub Releases 的 Android 应用自更新库，提供检查更新、下载 APK、安装三大核心功能。

分为两个模块：

- **update-lib** — 核心库，纯 View 体系，提供检查更新、下载管理、安装及传统 AlertDialog UI
- **update-compose** — Compose 扩展，依赖 update-lib，提供 Material3 风格的 Compose 弹窗 UI

---

## 集成

### 1. 添加 JitPack 仓库

在根 `settings.gradle.kts` 中添加：

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

### 2. 添加依赖

**仅使用传统 View（只需 update-lib）：**

```kotlin
dependencies {
    implementation("com.github.miaoqidong.UpdateLib:update-lib:Tag")
}
```

**使用 Compose UI（自动包含 update-lib）：**

```kotlin
dependencies {
    implementation("com.github.miaoqidong.UpdateLib:update-compose:Tag")
}
```

### 3. 最低要求

- minSdk 23（Android 6.0）
- compileSdk 36
- Java 17

---

## 初始化

在 `Application.onCreate()` 中调用 `init()`。`currentVersion` 和 `currentVersionCode` 会自动从 `PackageInfo` 读取，无需手动传入。

**使用 GitHub Releases：**

```kotlin
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        UpdateManager.init(
            context = this,
            githubOwner = "your-github-username",
            githubRepo = "your-repo-name"
        )
    }
}
```

**仅使用自定义 JSON（不用 GitHub）：**

```kotlin
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        UpdateManager.init(
            context = this,
            fallbackUrl = "https://example.com/update.json",
            fallbackOnly = true
        )
    }
}
```

---

## Compose 用法（推荐）

Compose 项目只需在页面中放一个 `UpdateHost`，它会自动处理检查更新、弹窗、下载进度、安装等全部流程。

```kotlin
class MainActivity : ComponentActivity() {

    private val updateViewModel: UpdateViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                // 放置 UpdateHost 即可，它会自动管理弹窗
                UpdateHost(vm = updateViewModel)

                // 你的页面内容
                Scaffold { padding ->
                    // ...
                }
            }
        }

        // 通知点击 / 冷启动恢复
        updateViewModel.onEntry()
    }

    override fun onResume() {
        super.onResume()
        // 回到前台时检查下载状态
        updateViewModel.onForeground()
    }
}
```

### 手动检查更新

```kotlin
// 强制检查（忽略缓存）
updateViewModel.checkManually()
```

### 检查中状态

```kotlin
val checking by updateViewModel.checkingFlow.collectAsState()
// checking == true 时显示加载指示器
```

### UpdateViewModel 主要方法

| 方法 | 说明 |
|------|------|
| `checkOnLaunch()` | 启动时自动检查（24 小时缓存间隔） |
| `checkManually()` | 强制检查更新 |
| `onEntry()` | 冷启动 / 通知点击时调用 |
| `onForeground()` | 回到前台时调用 |
| `onConfirmUpdate(context)` | 确认下载（会自动检查安装权限） |
| `onInstall(context)` | 安装已下载的 APK |
| `onMoveToBackground()` | 后台下载 |
| `dismiss()` | 关闭弹窗 |

---

## 传统 View 用法

非 Compose 项目使用 `UpdateDialogHelper` 提供的一系列 AlertDialog 方法。

### 完整流程示例

```kotlin
class MyActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<Button>(R.id.btnCheckUpdate).setOnClickListener {
            checkForUpdate()
        }
    }

    private fun checkForUpdate() {
        lifecycleScope.launch {
            val result = UpdateManager.checkForUpdate(force = true)

            when (result) {
                is UpdateRepository.CheckResult.NewVersion -> {
                    UpdateDialogHelper.showUpdateAvailableDialog(
                        context = this@MyActivity,
                        version = result.state.latestVersion,
                        releaseNotes = result.state.notes,
                        apkUrl = result.state.apkUrl,
                        apkSize = result.state.apkSize,
                        onConfirm = { startDownload(result.state) }
                    )
                }

                is UpdateRepository.CheckResult.UpToDate -> {
                    UpdateDialogHelper.showAlreadyLatestDialog(this@MyActivity)
                }

                is UpdateRepository.CheckResult.Failed -> {
                    UpdateDialogHelper.showCheckFailedDialog(
                        this@MyActivity,
                        onConfirm = { openGitHubPage() }
                    )
                }

                is UpdateRepository.CheckResult.RateLimited -> {
                    UpdateDialogHelper.showRateLimitedDialog(
                        this@MyActivity,
                        onConfirm = { openGitHubPage() }
                    )
                }

                is UpdateRepository.CheckResult.NoApk -> {
                    UpdateDialogHelper.showNoApkDialog(
                        this@MyActivity,
                        onConfirm = { openGitHubPage() }
                    )
                }

                UpdateRepository.CheckResult.Skipped -> {
                    // 缓存未过期，跳过检查
                }
            }
        }
    }

    private fun startDownload(state: UpdateState) {
        if (!UpdateManager.canInstall(this)) {
            UpdateManager.gotoUnknownSourceSetting(this)
            return
        }

        UpdateManager.downloadUpdate(this, state.latestVersion, state.apkUrl, state.apkSize)

        val (dialog, job) = UpdateDialogHelper.showDownloadProgressDialog(this)

        // 等待对话框关闭后检查 APK 并安装
        lifecycleScope.launch {
            while (dialog.isShowing) {
                delay(200)
            }
            val apkFile = ApkInstaller.apkFile(this@MyActivity, state.latestVersion)
            if (ApkInstaller.isDownloaded(apkFile, state.apkSize)) {
                UpdateManager.installUpdate(this@MyActivity, state.latestVersion)
            }
        }
    }
}

private fun openGitHubPage() {
    try {
        startActivity(Intent(Intent.ACTION_VIEW,
            android.net.Uri.parse(UpdateManager.getReleasesPageUrl())))
    } catch (_: Exception) {
    }
}
```

### UpdateDialogHelper 方法一览

| 方法 | 说明 |
|------|------|
| `showUpdateAvailableDialog(context, version, releaseNotes, apkUrl, apkSize, onConfirm, onCancel)` | 新版本可用弹窗 |
| `showDownloadProgressDialog(context, onDismiss)` | 下载进度弹窗，返回 `Pair<AlertDialog, Job>` |
| `showDownloadFailedDialog(context, onRetry)` | 下载失败弹窗 |
| `showAlreadyLatestDialog(context)` | 已是最新版本弹窗 |
| `showCheckFailedDialog(context, onConfirm)` | 检查失败弹窗 |
| `showRateLimitedDialog(context, onConfirm)` | API 限流弹窗 |
| `showNoApkDialog(context, onConfirm)` | 新版本暂无 APK 弹窗 |
| `showNotificationPermissionDialog(context, onConfirm, onCancel)` | 通知权限说明弹窗 |

所有弹窗标题栏右上角都带有详情跳转按钮，点击会打开 `getReleasesPageUrl()` 返回的链接（优先使用备用源 `desUrl`，回退到 GitHub Releases 页面）。

---

## 监听下载进度

通过 `DownloadController.flow`（`StateFlow`）实时监听下载状态：

```kotlin
lifecycleScope.launch {
    DownloadController.flow.collect { state ->
        when (state.status) {
            DownloadController.DownloadStatus.DOWNLOADING -> {
                // state.progress: 0-100
                progressBar.progress = state.progress
            }
            DownloadController.DownloadStatus.FAILED -> {
                // 下载失败
            }
            DownloadController.DownloadStatus.IDLE -> {
                // 空闲（下载完成或尚未开始）
            }
        }
    }
}
```

---

## UpdateManager API

| 方法 | 说明 |
|------|------|
| `init(context, [githubOwner], [githubRepo], ..., [fallbackUrl], [fallbackOnly])` | 初始化，仅传 context 即可（版本自动检测） |
| `checkForUpdate(force)` | 检查更新，返回 `CheckResult` |
| `checkOnLaunch()` | 启动时检查（带 24h 缓存） |
| `hasNewVersion()` | 是否有新版本 |
| `downloadUpdate(context, version, url, size)` | 开始下载 |
| `installUpdate(context, version)` | 安装已下载的 APK |
| `canInstall(context)` | 是否已有安装权限 |
| `gotoUnknownSourceSetting(context)` | 跳转系统安装未知应用设置 |
| `canNotify(context)` | 是否已有通知权限（Android 13 以下始终 true） |
| `gotoNotificationSetting(context)` | 跳转应用通知设置页面 |
| `isDownloaded(context, version, expectedSize)` | APK 是否已下载完成 |
| `resetDownloadState()` | 重置下载状态 |
| `getCurrentVersion()` | 获取当前版本号 |
| `getReleasesPageUrl()` | 获取弹窗右上角按钮跳转链接（优先备用源 desUrl，回退 GitHub） |
| `getLatestVersion()` | 获取最新版本号 |
| `getReleaseNotes()` | 获取更新说明 |
| `getApkUrl()` | 获取 APK 下载地址 |
| `getApkSize()` | 获取 APK 文件大小 |

---

## CheckResult 说明

`checkForUpdate()` 返回密封接口 `UpdateRepository.CheckResult`，包含 6 种状态：

| 状态 | 说明 |
|------|------|
| `NewVersion(state)` | 发现新版本，`state` 包含版本、说明、下载地址等 |
| `UpToDate(state)` | 已是最新版本 |
| `Failed` | 网络或解析失败 |
| `RateLimited(resetEpochSeconds)` | GitHub API 限流，`resetEpochSeconds` 为解除时间 |
| `NoApk(version)` | 有新版本但 Release 中尚未上传 APK |
| `Skipped` | 检查间隔未到，使用了缓存结果 |

---

## GitHub Release 规范

发布新版本时，在 GitHub Release 中上传 `.apk` 文件即可。库会自动从 Release 资源中查找 APK 并提取下载链接和文件大小。

---

## 备用更新源

当 GitHub 不可达时（如网络限制），库会自动回退到备用 JSON 端点获取更新信息。在 `Application` 中配置：

```kotlin
UpdateManager.init(this, fallbackUrl = "https://example.com/update.json")
```

如果你完全不使用 GitHub Releases，可以开启仅备用源模式，跳过 GitHub 直接走自定义 JSON：

```kotlin
UpdateManager.init(
    context = this,
    fallbackUrl = "https://example.com/update.json",
    fallbackOnly = true
)
```

### JSON 格式

```json
{
  "versionName": "8.0.73",
  "versionCode": 579,
  "downloadUrl": "http://xiazai.example.com/app.apk",
  "des": "https://example.com/release-notes.html",
  "desUrl": "https://example.com/release-notes.html"
}
```

| 字段 | 说明 |
|------|------|
| `versionName` | 版本名称，如 `"8.0.73"` |
| `versionCode` | 版本号（整数），用于与当前 `versionCode` 比较 |
| `downloadUrl` | APK 下载地址 |
| `des` | 更新说明内容的抓取地址，支持 HTML 或纯文本，弹窗内自动识别渲染方式 |
| `desUrl` | 升级弹窗右上角详情按钮的跳转链接 |

`des` 和 `desUrl` 可以指向同一地址，也可以分开配置。如果 `des` 为空，弹窗内不显示更新说明；如果 `desUrl` 为空，右上角按钮回退到 GitHub Releases 页面。

---

## 权限说明

库已在 Manifest 中声明以下权限，合并到你的项目中无需额外配置：

- `INTERNET` — 网络请求
- `REQUEST_INSTALL_PACKAGES` — 安装 APK
- `POST_NOTIFICATIONS` — 下载进度通知
- `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_DATA_SYNC` — 后台下载服务

---

## License

```
Copyright 2026 quzhuligpt@gmail.com

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```
