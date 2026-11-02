# Android 发布流程

本项目通过 GitHub Releases 提供 App 内更新。每个稳定 Release 只上传两个文件，文件名必须完全一致：

```text
keybook.apk
update.json
```

App 只会读取以下地址：

```text
https://github.com/caiheyu/KeyBook/releases/latest/download/update.json
https://github.com/caiheyu/KeyBook/releases/latest/download/keybook.apk
```

发布时必须保持包名、签名证书和版本号的连续性，否则旧版本无法覆盖安装或 App 会拒绝更新。

## 一次性准备

### 1. 创建并备份 Release keystore

在仓库外创建长期使用的 keystore，并至少保存两份备份和对应密码：

```powershell
keytool -genkeypair -v `
  -keystore "C:\Users\Lenovo\keys\keybook-release.jks" `
  -alias keybook `
  -keyalg RSA `
  -keysize 2048 `
  -validity 10000
```

不要提交、删除或更换该文件。更换签名证书后，旧版本不能直接覆盖安装。

### 2. 确认固定值

这些值应与 [app/build.gradle.kts](app/build.gradle.kts) 保持一致：

```text
applicationId: com.github.caiheyu.keybook
minSdk:        26
仓库:          https://github.com/caiheyu/KeyBook
```

## 每次发布

### 1. 修改版本并推送代码

编辑 [app/build.gradle.kts](app/build.gradle.kts)，提高 `versionCode`，并设置新的 `versionName`：

```kotlin
versionCode = 4       // 必须高于上一个 Release
versionName = "1.3"
```

先提交并推送包含版本号的代码。不要提交 keystore、密码、备份文件、APK 或其他临时产物。

```powershell
git status
git add <已确认的发布文件>
git commit -m "Release 1.3"
git push origin <当前分支>
```

### 2. 运行必要检查

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleRelease
```

有测试设备时，再运行：

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest
```

### 3. 生成并校验发布附件

使用 Android Studio：

1. `Build` → `Generate Signed App Bundle / APK`
2. 选择 `APK` 和模块 `app`
3. 选择长期 Release keystore、alias 和密码
4. Build variant 选择 `release`
5. 完成后点击通知中的 `Locate`，记下签名 APK 的实际路径。

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\prepare-release.ps1 `
  -ApkPath 'C:\path\to\app-release.apk' `
  -ReleaseNotes '修复问题并优化使用体验'
```

将 `-ApkPath` 替换成 Locate 显示的签名 APK 路径。SDK 不在默认的
`C:\develop\android\Sdk\build-tools\36.0.0` 时，可加上
`-BuildToolsDirectory 'C:\Android\Sdk\build-tools\<版本>'`。

脚本会读取 APK 的包名、版本、最低系统版本和签名，拒绝 Debug APK、未签名 APK、其他应用包名和多证书签名，并计算 APK 与证书的 SHA-256。App 安装更新时会验证 APK 签名与当前安装版本及生成的 `update.json` 一致。Release 的 `versionCode` 必须在 `app/build.gradle.kts` 中手动递增。脚本不读取 keystore 或密码，不签名、不上传文件。

生成的附件位于被 Git 忽略的构建目录：

```powershell
C:\workspace\KeyBook\app\build\outputs\github-release\keybook.apk
C:\workspace\KeyBook\app\build\outputs\github-release\update.json
```

用每次新生成的这两个附件替代手工复制 APK、计算摘要和编辑 `update.json` 的步骤。发布说明同时写入 `update.json`，方便 App 内展示。

### 4. 创建并发布 GitHub Release

1. 打开仓库的 `Releases`，点击 `Draft a new release`。
2. 创建或选择指向已推送提交的 Tag，例如 `v1.3`。
3. 上传且仅上传 `keybook.apk` 和 `update.json`。
4. 发布为公开的稳定 Release，不能是 Draft 或 Pre-release。

### 5. 验证公开地址

发布后打开以下地址，确认能获取刚发布的内容：

```text
https://github.com/caiheyu/KeyBook/releases/latest/download/update.json
https://github.com/caiheyu/KeyBook/releases/latest/download/keybook.apk
```

也可以检查清单请求是否成功：

```powershell
Invoke-WebRequest `
  "https://github.com/caiheyu/KeyBook/releases/latest/download/update.json" `
  -UseBasicParsing
```

## App 内升级验证

首次发布、签名配置变更或重要数据库变更时，必须做一次真实覆盖升级测试：

1. 先安装旧的 Release 签名版本，并准备加密备份。
2. 发布更高 `versionCode` 的新 Release。
3. 在旧版本中进入 `设置 → 关于 / 更新 → 检查更新`。
4. 下载并安装新版本，确认原有工作空间、记录和配置仍在。

不要用 Debug APK 覆盖 Release APK，也不要为了安装新版本先执行 `adb uninstall`；卸载会删除应用数据。发布前最后确认 `git status` 没有 keystore、密码、备份或临时 APK。

## 出错时先检查

- 显示“已是最新版本”：确认 Release 已公开发布且 `update.json.versionCode` 大于当前 App。
- APK 摘要不匹配：对最终的 `keybook.apk` 重新执行 `Get-FileHash`，不要复用旧摘要。
- 签名不匹配：确认 APK 使用的是同一份长期 Release keystore；`update.json` 应由脚本生成，不要手动修改证书摘要。
