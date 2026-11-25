# 开发与构建

## 环境

- Windows 10 或更新版本，PowerShell 5.1 或 7。
- JDK 17 或 21，设置 `JAVA_HOME`。
- Android SDK Platform 36、Build Tools 36.0.0，设置 `ANDROID_HOME`。
- Node.js 22，npm 使用 `band/package-lock.json` 安装固定依赖。
- OpenSSL，加入 PATH，或使用 Git for Windows 自带版本。

## 从全新目录构建

在项目根目录运行以下命令。

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File ./scripts/build.ps1
```

脚本先下载小米官方通信样例并核对 SDK SHA-256，然后生成开发签名，编译 Android 与 Vela 应用，运行现有单测与 Android lint。输出位于 `artifacts`。

同一个构建目录下 APK 和 RPK 使用相同包名、证书。`.local` 和 `band/sign` 在 Git 忽略范围内。后续覆盖安装应保留原签名。全新目录会产生另一套证书，安装前需考虑手机已有版本及手环配对情况。

常规 CI 每次生成临时签名，产物用于验证该次构建。对外发布版本需由维护者使用保管的发布签名构建。

## 结构

| 目录 | 内容 |
| --- | --- |
| `android/app/src/main` | 组件托管、车况解析、车控方向和确认、米家通知接入 |
| `android/app/src/test` | 协议、确认、车况、车窗和语音指令测试 |
| `band/src` | 手环主页面、状态缓存、侧滑卡片和素材 |
| `band/test` | 编译后手环协议和状态流回归 |
| `scripts` | 依赖准备、双端构建和公开源码导出 |

## 素材

公开源码中的展示素材由 `scripts/generate-assets.ps1` 生成。Windows PowerShell 使用 System.Drawing 生成 PNG，无需从官方 APK 提取图片。修改该脚本后运行以下命令。

```powershell
powershell -NoProfile -File ./scripts/generate-assets.ps1
```

自动识别银河按钮时，在运行时读取用户已安装 App 的资源，与当前卡片位图比较。适配代码中的资源名称用于定位已安装组件。

## 发布检查

运行 `scripts/export-source.ps1` 可将 Git 已登记的源码导出到新的 `.local/open-source/source` 目录。脚本检查登记文件，拒绝签名、SDK、安装包和本机缓存。导出目录适合做全新构建验证。发布时在 Releases 附 APK、RPK 和 SHA256SUMS.txt，并记录双端版本、验证设备和已知限制。
