# UpdateLib
[![](https://jitpack.io/v/miaoqidong/UpdateLib.svg)](https://jitpack.io/#miaoqidong/UpdateLib)

基于 GitHub Releases 的 Android 应用自更新库，提供检查更新、下载 APK、安装三大核心功能。

分为四个模块：

- **update-lib** — 核心库，Kotlin 编写，提供检查更新、下载管理、安装及传统 AlertDialog UI
- **update-java** — 纯 Java 版，零外部依赖，极轻量（< 55KB），适合老项目或对 APK 体积敏感的项目
- **update-simple** — 极简 Java 版，单文件零资源零外部依赖（< 5KB），仅检查自定义 JSON + 弹窗 + 跳转网站
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

**纯 Java 零依赖（推荐老项目 / 体积敏感）：**

```kotlin
dependencies {
    implementation("com.github.miaoqidong.UpdateLib:update-java:Tag")
}
```

**极简 Java 版（单文件 < 5KB，仅检查 JSON + 弹窗 + 跳转网站）：**

```kotlin
dependencies {
    implementation("com.github.miaoqidong.UpdateLib:update-simple:Tag")
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

### 快速接入（推荐）

调用 `checkAndShowUpdateDialog` 一行代码即可完成「检查 → 弹窗 → 下载 → 安装」的全部流程。内部自动处理所有 `CheckResult` 分支和安装权限检查，APK 已下载时自动显示安装按钮。

**Kotlin：**

```kotlin
class MyActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 进入页面自动检查更新
        UpdateDialogHelper.checkAndShowUpdateDialog(this)

        // 按钮手动触发
        findViewById<Button>(R.id.btnCheckUpdate).setOnClickListener {
            UpdateDialogHelper.checkAndShowUpdateDialog(this)
        }
    }
}
```

**Java：**

```java
public class MyActivity extends ComponentActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 进入页面自动检查更新
        UpdateDialogHelper.checkAndShowUpdateDialog(this, null);

        // 按钮手动触发
        findViewById(R.id.btnCheckUpdate).setOnClickListener(v ->
            UpdateDialogHelper.checkAndShowUpdateDialog(this, null)
        );
    }
}
```

### 统一更新弹窗（手动控制）

如果需要更细粒度的控制，可使用 `showUpdateDialog`，它将版本信息展示和内联下载进度条整合在同一个弹窗中：

```kotlin
val state = ... // 从 checkForUpdate 获得

val apkFile = ApkInstaller.apkFile(this, state.latestVersion)
val alreadyDownloaded = ApkInstaller.isDownloaded(apkFile, state.apkSize)

UpdateDialogHelper.showUpdateDialog(
    context = this,
    version = state.latestVersion,
    releaseNotes = state.notes,
    apkUrl = state.apkUrl,
    apkSize = state.apkSize,
    onConfirm = {
        // 点击「立即更新」后的回调
        if (UpdateManager.canInstall(this)) {
            UpdateManager.downloadUpdate(this, state.latestVersion, state.apkUrl, state.apkSize)
        } else {
            UpdateManager.gotoUnknownSourceSetting(this)
        }
    },
    onIgnore = { /* 忽略此版本 */ },
    onDismiss = { /* 弹窗关闭 */ },
    onInstall = if (alreadyDownloaded) {
        { UpdateManager.installUpdate(this, state.latestVersion) }
    } else null
)
```

### UpdateDialogHelper 方法一览

| 方法 | 说明 |
|------|------|
| `checkAndShowUpdateDialog(activity, onDismiss)` | **推荐** 一键检查更新并展示弹窗 |
| `showUpdateDialog(context, version, releaseNotes, apkUrl, apkSize, onConfirm, onIgnore, onDismiss, onInstall)` | 统一更新弹窗（含内联进度条） |
| `showUpdateAvailableDialog(context, version, releaseNotes, apkUrl, apkSize, onConfirm, onCancel)` | 新版本可用弹窗（独立版本信息 + 独立进度框） |
| `showDownloadProgressDialog(context, onDismiss)` | 下载进度弹窗，返回 `Pair<AlertDialog, Job>` |
| `showDownloadFailedDialog(context, onRetry)` | 下载失败弹窗 |
| `showAlreadyLatestDialog(context)` | 已是最新版本弹窗 |
| `showCheckFailedDialog(context, onConfirm)` | 检查失败弹窗 |
| `showRateLimitedDialog(context, onConfirm)` | API 限流弹窗 |
| `showNoApkDialog(context, onConfirm)` | 新版本暂无 APK 弹窗 |
| `showNotificationPermissionDialog(context, onConfirm, onCancel)` | 通知权限说明弹窗 |
| `openReleasesPage(context)` | 打开 Releases 页面 |

所有弹窗标题栏右上角都带有详情跳转按钮，点击会打开 `getReleasesPageUrl()` 返回的链接（优先使用备用源 `desUrl`，回退到 GitHub Releases 页面）。

---

## update-java 用法（纯 Java · 零依赖）

`update-java` 模块专为纯 Java 老项目设计，不引入 Kotlin、协程、序列化库、DataStore 等任何额外依赖，APK 体积增量 < 50KB。

### 初始化

在 `Application.onCreate()` 中调用 `UpdateManager.init()`：

```java
public class App extends Application {
    @Override
    public void onCreate() {
        super.onCreate();

        // 使用 GitHub Releases
        UpdateManager.init(this, "owner", "repo");

        // 或仅使用备用源
        UpdateManager.init(this, "https://example.com/update.json", true);
    }
}
```

### 快速接入

一行代码完成检查 → 弹窗 → 下载 → 安装：

```java
public class MyActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 进入页面自动检查更新
        UpdateDialogHelper.checkAndShowUpdateDialog(this);

        // 按钮手动触发
        findViewById(R.id.btnCheckUpdate).setOnClickListener(v ->
            UpdateDialogHelper.checkAndShowUpdateDialog(this)
        );
    }
}
```

### API 与 update-lib 对应关系

| update-lib (Kotlin) | update-java (Java) | 说明 |
|---|---|---|
| `UpdateManager.init(...)` | `UpdateManager.init(...)` | 初始化 |
| `UpdateManager.checkForUpdate(force, callback)` | `UpdateRepository.checkAndCache(...)` | 检查更新 |
| `UpdateManager.downloadUpdate(...)` | `UpdateManager.downloadUpdate(...)` | 开始下载 |
| `UpdateManager.installUpdate(...)` | `UpdateManager.installUpdate(...)` | 安装 APK |
| `UpdateManager.canInstall(context)` | `UpdateManager.canInstall(context)` | 安装权限检查 |
| `DownloadController.flow.collect {}` | `UpdateManager.addDownloadListener(listener)` | 监听下载进度 |
| `UpdateDialogHelper.checkAndShowUpdateDialog(...)` | `UpdateDialogHelper.checkAndShowUpdateDialog(...)` | 一键检查弹窗 |
| `UpdateDialogHelper.showUpdateDialog(...)` | `UpdateDialogHelper.showUpdateDialog(...)` | 统一更新弹窗 |

### 下载进度监听

update-java 使用回调模式监听下载状态，无需协程：

```java
DownloadController.Listener listener = state -> {
    switch (state.status) {
        case DOWNLOADING:
            progressBar.setProgress(state.progress);
            break;
        case FAILED:
            // 下载失败
            break;
        case IDLE:
            // 下载完成或空闲
            break;
    }
};
UpdateManager.addDownloadListener(listener);

// 不再需要时移除
UpdateManager.removeDownloadListener(listener);
```

### 与 update-lib 的区别

| | update-lib | update-java |
|---|---|---|
| 语言 | Kotlin | Java |
| 外部依赖 | kotlinx-serialization, coroutines, DataStore, AndroidX Core | **无** |
| JSON 解析 | kotlinx.serialization | org.json（Android 内置） |
| 持久化 | DataStore | SharedPreferences |
| 异步 | Coroutines | Thread + Handler |
| 通知 | NotificationCompat | Notification.Builder |
| APK 体积增量 | ~200KB | < 55KB |

---

## update-simple 用法（极简 · < 7KB）

`update-simple` 是整个项目中最轻量的模块——**只有一个 Java 文件 + 一份 strings.xml，零外部依赖**。对话框风格与 update-java 一致。支持自定义 JSON 或 GitHub Releases 两种更新源，并支持 GitHub 优先 + JSON 兜底模式。弹窗后跳转网站下载。适用于只需要通知用户去网站自行下载的场景。

### 更新源（三选一）

**方式一：GitHub 优先 + JSON 兜底（推荐）**

先尝试 GitHub Releases API，失败后自动降级到自定义 JSON。需要同时提供 owner/repo 和 JSON 兜底地址。

**方式二：自定义 JSON**

自行托管一个 JSON 文件，格式如下：

```json
{"versionName":"1.2.0","desUrl":"https://example.com/download","des":"- 修复了若干问题<br>- 新增 XX 功能"}
```

| 字段 | 说明 |
|---|---|
| `versionName` | 最新版本号，如 `"1.2.0"` |
| `desUrl` | 下载页面地址，用户点击按钮后跳转 |
| `des` | 更新日志（可选），支持纯文本和 HTML，如包含 HTML 标签则自动用 `Html.fromHtml()` 渲染 |

**方式三：GitHub Releases**

传入 owner 和 repo，模块自动读取最新 Release 的 `tag_name`（版本号）和 `body`（更新日志），点击按钮跳转到 Releases 页面。GitHub 的 `body` 字段通常是 Markdown，会按 HTML 渲染。

### 初始化

在 `Application.onCreate()` 中调用：

```java
public class App extends Application {
    @Override
    public void onCreate() {
        super.onCreate();

        // 方式一：GitHub 优先 + JSON 兜底（推荐）
        UpdateHelper.init(this, "owner", "repo", "https://example.com/fallback.json");

        // 方式二：自定义 JSON
        // UpdateHelper.init(this, "https://example.com/update.json");

        // 方式三：纯 GitHub Releases
        // UpdateHelper.init(this, "owner", "repo");
    }
}
```

### 检查更新

一行代码完成「HTTP 请求 → 版本对比 → 弹窗 → 跳转」：

```java
public class MyActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 进入页面自动检查
        UpdateHelper.check(this);

        // 按钮手动触发
        findViewById(R.id.btnCheckUpdate).setOnClickListener(v ->
            UpdateHelper.check(this)
        );
    }
}
```

### 对话框样式

与 update-java 风格一致，纯代码构建（无布局 XML）：

- 标题：发现新版本
- 版本行：`v{当前} → v{最新}`（16sp 加粗）
- 更新日志区域：
  - 纯文本：14sp 可滚动 TextView（maxLines=10）
  - HTML：`Html.fromHtml()` 渲染，`LinkMovementMethod` 支持超链接点击

### API

| 方法 | 说明 |
|---|---|
| `UpdateHelper.init(context, jsonUrl)` | 初始化（自定义 JSON 源，自动读取当前版本号） |
| `UpdateHelper.init(context, owner, repo)` | 初始化（GitHub Releases 源，自动读取当前版本号） |
| `UpdateHelper.init(context, owner, repo, fallbackUrl)` | 初始化（GitHub 优先 + JSON 兜底，自动读取当前版本号） |
| `UpdateHelper.check(activity)` | 检查更新，有新版本则弹窗跳转 |

### 与 update-java 的区别

| | update-java | update-simple |
|---|---|---|
| AAR 体积 | < 51KB | **< 7KB** |
| 源文件数 | 20+ | **1** |
| 资源文件 | strings, layout, drawable, xml | strings |
| 外部依赖 | compileOnly androidx.core | **零** |
| GitHub Releases | ✓ | ✓ |
| 自定义 JSON | ✓ | ✓ |
| HTML 更新日志 | Html.fromHtml | Html.fromHtml |
| 下载/安装 | ✓ | — |
| 点击"更新" | 下载并安装 | 跳转网站 |

---

## 监听下载进度

### Kotlin（Flow）

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

### Java（回调）

使用 `observeDownloadState` 回调式 API，无需协程：

```java
Job downloadJob = UpdateManager.observeDownloadState(state -> {
    switch (state.getStatus()) {
        case DOWNLOADING:
            progressBar.setProgress(state.getProgress());
            break;
        case FAILED:
            // 下载失败
            break;
        case IDLE:
            // 空闲
            break;
    }
});

// Activity 销毁时取消观察
downloadJob.cancel();
```

---

## Java 项目接入

纯 Java 项目只需依赖 `update-lib`，使用回调式 API 即可，无需协程。

### 初始化

```java
// Application.onCreate()
UpdateManager.init(this, "your-github-owner", "your-github-repo");

// 或仅使用备用源
UpdateManager.init(this, "", "",  "", "", true, 0L,
    "https://example.com/update.json", true);
```

### 检查更新

推荐使用 `checkAndShowUpdateDialog`，一行代码搞定：

```java
// 自动检查 + 弹窗 + 下载 + 安装
UpdateDialogHelper.checkAndShowUpdateDialog(this, null);
```

如需手动控制，可调用 `checkForUpdate` 获取结果后自行处理：

```java
UpdateManager.checkForUpdate(true, result -> {
    if (result instanceof UpdateRepository.CheckResult.NewVersion) {
        UpdateState state = ((UpdateRepository.CheckResult.NewVersion) result).getState();
        // 使用统一弹窗（含内联进度条）
        UpdateDialogHelper.showUpdateDialog(this,
            state.getLatestVersion(), state.getNotes(),
            state.getApkUrl(), state.getApkSize(),
            () -> { UpdateManager.downloadUpdate(this, state.getLatestVersion(), state.getApkUrl(), state.getApkSize()); return kotlin.Unit.INSTANCE; },
            () -> kotlin.Unit.INSTANCE, // onIgnore
            () -> kotlin.Unit.INSTANCE, // onDismiss
            null // onInstall
        );
    } else if (result instanceof UpdateRepository.CheckResult.UpToDate) {
        UpdateDialogHelper.showAlreadyLatestDialog(this);
    }
    return kotlin.Unit.INSTANCE;
});
```

### 观察下载进度

```java
Job downloadJob = UpdateManager.observeDownloadState(state -> {
    if (state.getStatus() == DownloadController.DownloadStatus.DOWNLOADING) {
        progressBar.setProgress(state.getProgress());
    }
});
```

> **注意**：`CheckResult` 是 Kotlin sealed interface，Java 17+ 可用 `instanceof` 模式匹配。低版本 Java 需用 `if (result instanceof UpdateRepository.CheckResult.NewVersion)` 判断。所有 `UpdateManager` 和 `UpdateDialogHelper` 方法均有 `@JvmStatic`，Java 直接 `UpdateManager.xxx()` 调用即可。

---

## UpdateManager API

### 初始化与状态查询

| 方法 | 说明 |
|------|------|
| `init(context, [githubOwner], [githubRepo], ..., [fallbackUrl], [fallbackOnly])` | 初始化，仅传 context 即可（版本自动检测） |
| `isInitialized()` | 是否已初始化 |
| `getCurrentVersion()` | 获取当前版本号 |
| `getReleasesPageUrl()` | 获取弹窗右上角按钮跳转链接（优先备用源 desUrl，回退 GitHub） |
| `getLatestVersion()` | 获取最新版本号（suspend） |
| `getReleaseNotes()` | 获取更新说明（suspend） |
| `getApkUrl()` | 获取 APK 下载地址（suspend） |
| `getApkSize()` | 获取 APK 文件大小（suspend） |

### 检查更新

| 方法 | 说明 |
|------|------|
| `checkForUpdate(force)` | 检查更新，返回 `CheckResult`（suspend / 协程） |
| `checkForUpdate(force, callback)` | **Java 回调版**，结果通过 callback 返回 |
| `checkOnLaunch()` | 启动时检查（带 24h 缓存），返回是否执行（suspend） |
| `checkOnLaunch(callback)` | **Java 回调版** |
| `hasNewVersion()` | 是否有新版本（suspend） |

### 下载与安装

| 方法 | 说明 |
|------|------|
| `downloadUpdate(context, version, url, size)` | 开始下载 |
| `installUpdate(context, version)` | 安装已下载的 APK |
| `isDownloaded(context, version, expectedSize)` | APK 是否已下载完成 |
| `resetDownloadState()` | 重置下载状态 |

### 权限管理

| 方法 | 说明 |
|------|------|
| `canInstall(context)` | 是否已有安装权限 |
| `gotoUnknownSourceSetting(context)` | 跳转系统安装未知应用设置 |
| `canNotify(context)` | 是否已有通知权限（Android 13 以下始终 true） |
| `gotoNotificationSetting(context)` | 跳转应用通知设置页面 |

### Flow 与观察（Kotlin）

| 方法 | 说明 |
|------|------|
| `updateStateFlow()` | 更新状态 Flow（DataStore，跨进程共享） |
| `downloadStateFlow()` | 下载状态 StateFlow（内存单例，仅主进程） |

### 回调式观察（Java / Kotlin 通用）

| 方法 | 说明 |
|------|------|
| `observeDownloadState(callback)` | 持续回调下载状态，返回 `Job` 用于取消 |
| `observeUpdateState(callback)` | 持续回调更新状态，返回 `Job` 用于取消 |

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
