# USTC 选课余量检测

一款 Android 应用，用于自动检测中国科学技术大学教务系统（jw.ustc.edu.cn）中指定课程的选课余量。

## 功能特性

- **自动登录**：通过 USTC 统一身份认证（CAS）自动登录教务系统，凭证加密存储于本地
- **课程多维度检索**：支持按课程名称、课堂代码、教师姓名检索全校课程
- **课程余量查询**：支持在应用内直接查看该课程的已选人数与人数上限
- **后台定时监控**：可将关注的课程加入跟踪列表，自动在后台定时（默认 15 分钟）轮询余量
- **空位优先通知**：在后台监控中一旦发现名额空余，即时发送高优先级系统通知提醒
- **选课公告处理**：自动消除进入选课页面后弹出的"选课公告"弹窗

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
2. 登录成功后进入主界面
3. 可以直接输入课堂号，或者点击 **“按关键字查找课堂号”** 进行全校课程检索
4. **即时检测**：输入课堂号后点击「开始检测」，应用将自动通过后台 WebView 消除公告弹窗并实时渲染提取最新余量
5. **后台捡漏**：在检索页面勾选心仪的课程“加入跟踪列表”。在顶部的 **“跟踪列表”** 页面内确保开关处于开启状态，即便退出应用，后台 Worker 仍会通过后台 WebView 自动登录教务系统并注入脚本定期查询选课页面余量，一旦有空位将发出系统通知进行提示

## 构建

```bash
# 克隆项目
git clone https://github.com/your-repo/ustc-class-vacancy-checker.git
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
│   │   ├── CourseCheckScriptUtils.kt  # 选课页面 JS 脚本
│   │   └── LoginScriptUtils.kt        # 登录页面 JS 脚本
│   └── worker/
│       └── ClassVacancyWorker.kt      # 定时检测空位后台 Worker
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
