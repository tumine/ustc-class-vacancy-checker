<div align="center">
  <img src="images/app_icon.png" width="128" alt="USTC 选课余量检测 Logo">
  <h1>USTC 选课余量检测</h1>
</div>

一款 Android 应用，用于自动检测中国科学技术大学教务系统（jw.ustc.edu.cn）中指定课程的选课余量。

## 功能特性

- **自动登录**：通过 USTC 统一身份认证（CAS）自动登录教务系统，凭证加密存储于本地
- **课程检索与余量查询**：支持按课程名称、课堂代码、教师姓名检索全校课程，实时查看已选人数与人数上限
- **后台监控与空位通知**：将关注的课程加入跟踪列表，后台自动定时轮询余量，发现空位即时发送系统通知提醒
- **智能选课**：检测到空位后自动点击选课按钮；自动识别已选课程，避免重复操作
- **应用内更新检查**：支持从 GitHub Releases 检查最新版本更新

## 系统要求

- Android 14 (API 34) 及以上

## 技术栈

| 技术 | 用途 |
|------|------|
| Kotlin | 开发语言 |
| Jetpack Compose | UI 框架 |
| Material 3 | 设计语言 |
| Hilt | 依赖注入 |
| WorkManager | 定时后台轮询任务 |
| DataStore | 跟踪课程列表的本地持久化 |
| OkHttp & Gson | 轻量级原生网络请求与 JSON 解析 |
| WebView + JavaScript 注入 | 自动化操作教务系统页面 |
| EncryptedSharedPreferences | 加密存储用户凭证 |

## 使用方法

1. 打开应用，输入 USTC 统一身份认证账号密码
2. 登录成功后进入主界面，可直接输入课堂号或通过关键字检索全校课程
3. **即时检测**：输入课堂号后点击「开始检测」，实时查看课程余量
4. **智能选课**：开启自动选课后，检测到空位将自动点击选课按钮；已选课程会自动识别并提示
5. **后台捡漏**：将课程加入跟踪列表，后台自动轮询余量，发现空位即时通知
6. **检查更新**：在设置页面点击「检查更新」获取最新版本

## 构建

```bash
# 克隆项目
git clone https://github.com/tumine/ustc-class-vacancy-checker.git
cd ustc-class-vacancy-checker

# 构建 Debug APK
./gradlew assembleDebug

# 构建 Release APK
./gradlew assembleRelease
```

## 项目结构

```
app/src/main/java/com/ustc/vacancychecker/
├── MainActivity.kt                    # 入口 Activity
├── VacancyCheckerApp.kt               # Application 类 (含 WorkManager 配置)
├── data/
│   ├── local/
│   │   ├── CredentialsManager.kt      # 加密凭证管理
│   │   └── CourseRepository.kt        # 课程追踪持久化 DataStore 
│   ├── model/
│   │   └── TrackedCourse.kt           # 课程数据类实体
│   ├── remote/
│   │   ├── CatalogScriptUtils.kt      # 课程目录检索 JS 脚本
│   │   ├── CourseCheckScriptUtils.kt  # 选课页面 JS 脚本
│   │   ├── LoginScriptUtils.kt        # 登录页面 JS 脚本
│   │   └── UpdateChecker.kt           # GitHub Releases 更新检查
│   └── worker/
│       ├── ClassVacancyWorker.kt      # 定时检测空位后台 Worker
│       └── BackgroundJwVacancyChecker.kt # 后台 WebView 检测逻辑
├── di/
│   └── AppModule.kt                   # Hilt 依赖注入模块
└── ui/
    ├── coursecheck/                   # 选课查询主页
    ├── courselookup/                  # 课程检索查询页面
    ├── login/                         # 登录界面
    ├── navigation/                    # 导航图与路由
    ├── settings/                      # 设置页面
    ├── tracker/                       # 后台跟踪与轮询列表界面
    └── theme/                         # Material 3 主题
```

## 隐私说明

- 用户的账号密码仅保存在本地设备中，使用 Android `EncryptedSharedPreferences` 加密存储
- 应用不会将凭证上传至任何第三方服务器
- 所有操作均通过 WebView 在用户设备上完成

## 许可证

本项目基于 [MIT 许可证](LICENSE) 开源。
