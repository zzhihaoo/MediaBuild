# MediaBuild 媒体下载器

一款 Android 社交媒体内容下载工具，支持通过链接解析或屏幕悬浮窗一键下载视频和图片。

## 功能特性

### 链接解析模式
- 粘贴分享链接，自动识别平台并解析内容
- 支持从分享文案中提取链接（如"在抖音上看到这个视频 https://..."）
- 支持短链接自动展开（如 `xhslink.com`、`v.douyin.com`、`t.cn`）

### 悬浮窗模式
- 开启无障碍服务后，自动检测屏幕上的媒体内容
- 检测到可下载内容时显示悬浮窗按钮
- 支持拖拽移动悬浮窗位置

### 支持平台
| 平台 | 视频下载 | 图文下载 |
|------|---------|---------|
| 小红书 | ✅ | ✅ |
| 抖音 | ✅ | - |
| 抖音极速版 | ✅ | - |
| 微博 | ✅ | ✅ |
| 微博极速版 | ✅ | ✅ |
| TikTok | ✅ | - |

### 下载能力
- 视频：下载原始画质视频文件
- 图文：批量保存帖子中的所有图片
- 自动保存到系统相册

## 技术栈

- **语言**：Kotlin
- **UI 框架**：Jetpack Compose + Material3
- **网络请求**：OkHttp
- **HTML 解析**：Jsoup
- **图片加载**：Coil
- **JSON 解析**：Gson

## 项目结构

```
app/src/main/java/com/example/mediabuild/
├── accessibility/        # 无障碍服务
│   └── MediaAccessibilityService.kt
├── model/                # 数据模型
├── parser/               # 链接解析器
│   └── LinkParser.kt
├── service/              # 后台服务
│   ├── OverlayService.kt
│   ├── DownloadService.kt
│   └── MediaDetectService.kt
├── ui/                   # 界面
│   ├── screens/
│   │   ├── HomeScreen.kt
│   │   ├── DownloadScreen.kt
│   │   └── SettingsScreen.kt
│   └── theme/
├── util/                 # 工具类
├── MediaDownloadApp.kt   # Application
└── MainActivity.kt       # 主界面
```

## 构建说明

### 环境要求
- Android Studio (推荐最新稳定版)
- JDK 11+
- Android SDK 35

### 编译步骤

```bash
# Debug 版本
./gradlew assembleDebug

# Release 版本
./gradlew assembleRelease

# 运行单元测试
./gradlew testDebugUnitTest
```

### APK 位置
- Debug: `app/build/outputs/apk/debug/app-debug.apk`
- Release: `app/build/outputs/apk/release/app-release.apk`

## 使用方法

### 方法一：链接解析
1. 打开应用，进入首页
2. 粘贴视频或图文链接（或分享文案）
3. 点击"开始解析"
4. 预览解析结果，点击下载

### 方法二：悬浮窗
1. 进入设置页，开启无障碍服务
2. 授予悬浮窗权限
3. 打开支持的 App（小红书/抖音/微博等）
4. 浏览内容时自动检测，点击悬浮窗下载

## 权限说明

| 权限 | 用途 |
|------|------|
| 网络访问 | 解析链接、下载内容 |
| 存储/媒体 | 保存文件到相册 |
| 悬浮窗 | 显示下载按钮 |
| 无障碍服务 | 检测屏幕内容 |
| 前台服务 | 下载任务后台运行 |
| 通知 | 显示下载进度 |

## 测试

项目包含 20+ 单元测试，覆盖链接提取、平台检测、短链解析等核心逻辑：

```bash
./gradlew testDebugUnitTest
```

## 已知问题

- 抖音、微博的解析功能尚未完全调试
- 无障碍服务在部分设备上可能被系统自动关闭
- 小红书页面结构变化可能导致解析失败

## 开源协议

本项目仅供学习交流使用。
