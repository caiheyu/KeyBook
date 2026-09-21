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
versionCode = 2       // 必须高于上一个 Release
versionName = "1.0.1"
```

先提交并推送包含版本号的代码。不要提交 keystore、密码、备份文件、APK 或其他临时产物。

```powershell
git status
git add <已确认的发布文件>
git commit -m "Release 1.0.1"
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

### 3. 生成签名 APK

使用 Android Studio：

1. `Build` → `Generate Signed App Bundle / APK`
2. 选择 `APK` 和模块 `app`
3. 选择长期 Release keystore、alias 和密码
4. Build variant 选择 `release`
5. 完成构建后，将生成的签名 APK 复制到仓库根目录并改名为 `keybook.apk`

```powershell
Copy-Item "<签名 APK 路径>" ".\keybook.apk"
```

不要上传 `app-release-unsigned.apk`，也不要在计算摘要后修改或重新压缩 `keybook.apk`。

### 4. 校验签名并计算摘要

使用 Android SDK 的 `apksigner`（替换为本机 SDK 路径）：

```powershell
& "<Android SDK>\build-tools\<版本>\apksigner.bat" `
  verify --verbose --print-certs ".\keybook.apk"
```

确认输出包含 `Verifies`，使用的是长期 Release 证书而不是 debug 证书，并记录证书 SHA-256：去掉冒号后转为小写。

计算最终 APK 的 SHA-256：

```powershell
(Get-FileHash ".\keybook.apk" -Algorithm SHA256).Hash.ToLowerInvariant()
```

同时确认 APK 的以下值与本次版本一致：

```text
applicationId = com.github.caiheyu.keybook
versionCode   = app/build.gradle.kts 中的值
versionName   = app/build.gradle.kts 中的值
minSdk        = 26
```

### 5. 创建 update.json

在 `keybook.apk` 同目录创建 UTF-8 文件。顶层只能有以下 8 个字段，数字字段必须是数字而不是字符串：

```json
{
  "schemaVersion": 1,
  "versionName": "1.0.1",
  "versionCode": 2,
  "minSdk": 26,
  "apkSha256": "最终 keybook.apk 的 64 位小写 SHA-256",
  "signerCertificateSha256": "apksigner 输出的证书 SHA-256，去掉冒号并转小写",
  "publishedAt": "2026-09-21T12:00:00Z",
  "releaseNotes": "修复问题并优化使用体验"
}
```

填写时必须满足：

- `versionName`、`versionCode`、`minSdk` 与 APK 完全一致，且 `versionCode` 高于上一个 Release。
- `apkSha256` 来自最终签名 APK；`signerCertificateSha256` 来自本次签名证书。
- `publishedAt` 使用 UTC 的 ISO-8601 格式并以 `Z` 结尾；`releaseNotes` 为纯文本且不超过 4000 个 Unicode 码点。

### 6. 创建并发布 GitHub Release

1. 打开仓库的 `Releases`，点击 `Draft a new release`。
2. 创建或选择指向已推送提交的 Tag，例如 `v1.0.1`。
3. 上传且仅上传 `keybook.apk` 和 `update.json`。
4. 发布为公开的稳定 Release，不能是 Draft 或 Pre-release。

### 7. 验证公开地址

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
- 签名不匹配：确认 APK 使用的是同一份长期 Release keystore，且证书摘要填写正确。
