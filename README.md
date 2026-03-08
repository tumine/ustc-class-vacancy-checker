# USTC 选课余量检测

一款 Android 应用，用于自动检测中国科学技术大学教务系统（jw.ustc.edu.cn）中指定课程的选课余量。

## 功能特性

- **自动登录**：通过 USTC 统一身份认证（CAS）自动登录教务系统，凭证加密存储于本地
- **课程余量查询**：输入课堂号，自动进入选课页面查询该课程的已选人数与人数上限
- **余量提示**：检测完成后显示课程是否有空余名额
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
| WebView + JavaScript 注入 | 自动化操作教务系统页面 |
| EncryptedSharedPreferences | 加密存储用户凭证 |

## 使用方法

1. 打开应用，输入 USTC 统一身份认证账号密码
2. 登录成功后进入主界面
3. 输入需要查询的课堂号
4. 点击「开始检测」，应用将自动完成以下流程：
   - 登录教务系统
   - 进入选课页面
   - 消除选课公告弹窗（如有）
   - 搜索指定课堂号
   - 读取并显示该课程的选课余量

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
├── VacancyCheckerApp.kt               # Application 类
├── data/
│   ├── local/
│   │   └── CredentialsManager.kt      # 加密凭证管理
│   └── remote/
│       ├── CourseCheckScriptUtils.kt   # 选课页面 JS 脚本
│       └── LoginScriptUtils.kt        # 登录页面 JS 脚本
├── di/
│   └── AppModule.kt                   # Hilt 依赖注入模块
└── ui/
    ├── coursecheck/
    │   ├── CourseCheckScreen.kt        # 课堂号输入 & 结果展示
    │   ├── CourseCheckViewModel.kt     # 查询逻辑 ViewModel
    │   └── WebViewCourseCheckScreen.kt # WebView 自动化操作
    ├── login/
    │   ├── LoginScreen.kt             # 登录界面
    │   ├── LoginViewModel.kt          # 登录逻辑 ViewModel
    │   └── WebViewLoginScreen.kt      # WebView 登录操作
    ├── navigation/
    │   └── NavGraph.kt                # 导航图
    ├── settings/
    │   └── SettingsScreen.kt          # 设置页面
    └── theme/
        └── Theme.kt                   # Material 3 主题
```

## 隐私说明

- 用户的账号密码仅保存在本地设备中，使用 Android `EncryptedSharedPreferences` 加密存储
- 应用不会将凭证上传至任何第三方服务器
- 所有操作均通过 WebView 在用户设备上完成

## 许可证

本项目基于 [MIT 许可证](LICENSE) 开源。
