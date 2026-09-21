package com.github.caiheyu.keybook.feature.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.time.Instant
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import com.github.caiheyu.keybook.BuildConfig
import com.github.caiheyu.keybook.core.network.PublicOnlyDns
import com.github.caiheyu.keybook.core.validation.InputValidation
import com.github.caiheyu.keybook.feature.transfer.StrictJson
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody

@Serializable
data class UpdateManifest(
    val schemaVersion: Int,
    val versionName: String,
    val versionCode: Int,
    val minSdk: Int,
    val apkSha256: String,
    val signerCertificateSha256: String,
    val publishedAt: String,
    val releaseNotes: String,
)

sealed interface UpdateCheckResult {
    data object UpToDate : UpdateCheckResult
    data class Available(val manifest: UpdateManifest) : UpdateCheckResult
    data class Incompatible(val manifest: UpdateManifest) : UpdateCheckResult
}

@Singleton
class UpdateRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val json: Json,
) {
    private val manifestClient = client(MANIFEST_TIMEOUT_MILLIS)
    private val downloadClient = client(DOWNLOAD_TIMEOUT_MILLIS)
    private val cleanupHandler = Handler(Looper.getMainLooper())

    init {
        updateDirectory().listFiles()?.filter(File::isFile)?.forEach(File::delete)
    }

    suspend fun checkForUpdate(): UpdateCheckResult = withTimeout(MANIFEST_TIMEOUT_MILLIS) {
        withContext(Dispatchers.IO) {
            val repo = parseRepositoryUrl()
            val url = "https://github.com/${repo.owner}/${repo.name}/releases/latest/download/update.json"
                .toHttpUrlOrNull() ?: error("更新地址无效")
            val bytes = requestBytes(manifestClient, url, MAX_MANIFEST_BYTES)
            val raw = bytes.toString(Charsets.UTF_8)
            val manifest = decodeAndValidateUpdateManifest(raw, json)
            when {
                manifest.versionCode <= BuildConfig.VERSION_CODE -> UpdateCheckResult.UpToDate
                manifest.minSdk > Build.VERSION.SDK_INT -> UpdateCheckResult.Incompatible(manifest)
                else -> UpdateCheckResult.Available(manifest)
            }
        }
    }

    suspend fun downloadAndCreateInstallIntent(manifest: UpdateManifest): Intent =
        withTimeout(DOWNLOAD_TIMEOUT_MILLIS) {
            withContext(Dispatchers.IO) {
                validateUpdateManifest(manifest)
                require(manifest.versionCode > BuildConfig.VERSION_CODE) { "禁止下载当前版本或降级版本" }
                require(manifest.minSdk <= Build.VERSION.SDK_INT) { "此版本不支持当前 Android 系统" }
                val repo = parseRepositoryUrl()
                val url = "https://github.com/${repo.owner}/${repo.name}/releases/latest/download/keybook.apk"
                    .toHttpUrlOrNull() ?: error("APK 下载地址无效")
                val file = File(updateDirectory(), "${UUID.randomUUID()}.apk")
                try {
                    requestFile(downloadClient, url, file, MAX_APK_BYTES)
                    require(file.sha256Hex() == manifest.apkSha256.lowercase(Locale.ROOT)) { "APK SHA-256 校验失败" }
                    validateArchive(file, manifest)
                    val uri = FileProvider.getUriForFile(
                        context,
                        "${BuildConfig.APPLICATION_ID}.files",
                        file,
                    )
                    Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, APK_MIME)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                    }.also {
                        cleanupHandler.postDelayed({ file.delete() }, INSTALLER_HANDOFF_TTL_MILLIS)
                    }
                } catch (error: Exception) {
                    file.delete()
                    throw error
                }
            }
        }

    private fun validateArchive(file: File, manifest: UpdateManifest) {
        val packageInfo = archivePackageInfo(file)
            ?: throw IllegalArgumentException("无法读取 APK 包信息")
        require(packageInfo.packageName == BuildConfig.APPLICATION_ID) { "APK 包名不匹配" }
        val archiveVersionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode.toLong()
        }
        require(archiveVersionCode == manifest.versionCode.toLong()) { "APK 版本号与清单不一致" }
        val minSdk = packageInfo.applicationInfo?.minSdkVersion
            ?: throw IllegalArgumentException("无法读取 APK 最低系统版本")
        require(minSdk == manifest.minSdk) { "APK 最低系统版本与清单不一致" }
        val archiveSigners = signerDigests(packageInfo)
        val currentSigners = signerDigests(currentPackageInfo())
        require(archiveSigners.size == 1) { "APK 必须只有一个当前签名者" }
        require(archiveSigners.single() == manifest.signerCertificateSha256.lowercase(Locale.ROOT)) {
            "APK 签名与清单不一致"
        }
        require(archiveSigners == currentSigners) { "APK 签名与当前安装版本不一致" }
    }

    @Suppress("DEPRECATION")
    private fun archivePackageInfo(file: File): PackageInfo? = if (Build.VERSION.SDK_INT >= 33) {
        context.packageManager.getPackageArchiveInfo(
            file.absolutePath,
            PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong()),
        )
    } else {
        context.packageManager.getPackageArchiveInfo(
            file.absolutePath,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                PackageManager.GET_SIGNING_CERTIFICATES
            } else PackageManager.GET_SIGNATURES,
        )
    }

    @Suppress("DEPRECATION")
    private fun currentPackageInfo(): PackageInfo = if (Build.VERSION.SDK_INT >= 33) {
        context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong()),
        )
    } else {
        context.packageManager.getPackageInfo(
            context.packageName,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                PackageManager.GET_SIGNING_CERTIFICATES
            } else PackageManager.GET_SIGNATURES,
        )
    }

    @Suppress("DEPRECATION")
    private fun signerDigests(info: PackageInfo): Set<String> {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signing = info.signingInfo ?: return emptySet()
            if (signing.hasMultipleSigners()) signing.apkContentsSigners else signing.signingCertificateHistory
        } else info.signatures
        return signatures.orEmpty().mapTo(mutableSetOf()) { signature ->
            MessageDigest.getInstance("SHA-256").digest(signature.toByteArray())
                .joinToString("") { "%02x".format(it) }
        }
    }

    private suspend fun requestBytes(client: OkHttpClient, initial: HttpUrl, limit: Long): ByteArray {
        var url = initial
        repeat(MAX_REDIRECTS + 1) { index ->
            validateUpdateUrl(url)
            val result = execute(client, url) {
                if (it.code in REDIRECT_CODES) {
                    require(index < MAX_REDIRECTS) { "更新请求重定向次数过多" }
                    HttpResult.Redirect(
                        url.resolve(it.header("Location").orEmpty())
                            ?: throw IllegalArgumentException("更新请求重定向地址无效"),
                    )
                } else {
                    require(it.isSuccessful) { "更新请求失败（HTTP ${it.code}）" }
                    HttpResult.Success(
                        it.body?.readBounded(limit) ?: throw IllegalArgumentException("更新响应为空"),
                    )
                }
            }
            when (result) {
                is HttpResult.Redirect -> url = result.url
                is HttpResult.Success -> return result.value
            }
        }
        throw IllegalArgumentException("更新请求重定向次数过多")
    }

    private suspend fun requestFile(client: OkHttpClient, initial: HttpUrl, target: File, limit: Long) {
        var url = initial
        repeat(MAX_REDIRECTS + 1) { index ->
            validateUpdateUrl(url)
            val result = execute(client, url) {
                if (it.code in REDIRECT_CODES) {
                    require(index < MAX_REDIRECTS) { "APK 下载重定向次数过多" }
                    HttpResult.Redirect(
                        url.resolve(it.header("Location").orEmpty())
                            ?: throw IllegalArgumentException("APK 下载重定向地址无效"),
                    )
                } else {
                    require(it.isSuccessful) { "APK 下载失败（HTTP ${it.code}）" }
                    val body = it.body ?: throw IllegalArgumentException("APK 下载响应为空")
                    require(body.contentLength() < 0 || body.contentLength() <= limit) { "APK 超过 256 MiB" }
                    body.byteStream().use { input ->
                        FileOutputStream(target).use { output ->
                            val buffer = ByteArray(32 * 1024)
                            var total = 0L
                            while (true) {
                                val read = input.read(buffer)
                                if (read < 0) break
                                total += read
                                require(total <= limit) { "APK 超过 256 MiB" }
                                output.write(buffer, 0, read)
                            }
                            output.fd.sync()
                        }
                    }
                    HttpResult.Success(Unit)
                }
            }
            when (result) {
                is HttpResult.Redirect -> url = result.url
                is HttpResult.Success -> return
            }
        }
        throw IllegalArgumentException("APK 下载重定向次数过多")
    }

    private suspend fun <T> execute(
        client: OkHttpClient,
        url: HttpUrl,
        consume: (Response) -> HttpResult<T>,
    ): HttpResult<T> = suspendCancellableCoroutine { continuation ->
        val call = client.newCall(
            Request.Builder()
                .url(url)
                .header("Accept", "application/json,application/vnd.android.package-archive;q=0.9,*/*;q=0.1")
                .header("Cache-Control", "no-store")
                .build(),
        )
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWith(Result.failure(e))
            }

            override fun onResponse(call: Call, response: Response) {
                val result = runCatching { response.use(consume) }
                result.fold(
                    onSuccess = { value ->
                        if (continuation.isActive) continuation.resumeWith(Result.success(value))
                    },
                    onFailure = { error ->
                        if (continuation.isActive) continuation.resumeWith(Result.failure(error))
                    },
                )
            }
        })
    }

    private fun validateUpdateUrl(url: HttpUrl) {
        require(url.scheme == "https" && url.port == 443 && url.host.lowercase() in ALLOWED_HOSTS) {
            "更新地址不在允许的 GitHub 域名内"
        }
        require(url.username.isEmpty() && url.password.isEmpty()) { "更新地址不能包含认证信息" }
    }

    private fun parseRepositoryUrl(): GithubRepository {
        val url = BuildConfig.GITHUB_REPOSITORY_URL.toHttpUrlOrNull()
            ?: throw IllegalArgumentException("GitHub 仓库地址尚未配置")
        require(url.scheme == "https" && url.port == 443 && url.host == "github.com" &&
            url.username.isEmpty() && url.password.isEmpty() && url.query == null && url.fragment == null
        ) { "GitHub 仓库地址无效" }
        val segments = url.pathSegments.filter(String::isNotBlank)
        require(segments.size == 2) { "GitHub 仓库地址无效" }
        return GithubRepository(segments[0], segments[1])
    }

    private fun client(timeoutMillis: Long) = OkHttpClient.Builder()
        .dns(PublicOnlyDns())
        .followRedirects(false)
        .followSslRedirects(false)
        .cookieJar(okhttp3.CookieJar.NO_COOKIES)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(timeoutMillis, TimeUnit.MILLISECONDS)
        .callTimeout(timeoutMillis, TimeUnit.MILLISECONDS)
        .build()

    private fun ResponseBody.readBounded(limit: Long): ByteArray {
        require(contentLength() < 0 || contentLength() <= limit) { "更新响应超过大小限制" }
        val output = ByteArrayOutputStream()
        byteStream().use { input ->
            val buffer = ByteArray(16 * 1024)
            var total = 0L
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                require(total <= limit) { "更新响应超过大小限制" }
                output.write(buffer, 0, read)
            }
        }
        return output.toByteArray()
    }

    private fun File.sha256Hex(): String = inputStream().use { input ->
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(32 * 1024)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun updateDirectory(): File = File(context.cacheDir, "updates").also { directory ->
        require(directory.exists() || directory.mkdirs()) { "无法创建更新临时目录" }
    }

    private data class GithubRepository(val owner: String, val name: String)

    private sealed interface HttpResult<out T> {
        data class Redirect(val url: HttpUrl) : HttpResult<Nothing>
        data class Success<T>(val value: T) : HttpResult<T>
    }

    companion object {
        private const val MAX_REDIRECTS = 3
        private const val MAX_MANIFEST_BYTES = 256L * 1024
        private const val MAX_APK_BYTES = 256L * 1024 * 1024
        private const val MANIFEST_TIMEOUT_MILLIS = 20_000L
        private const val DOWNLOAD_TIMEOUT_MILLIS = 10L * 60 * 1000
        private const val INSTALLER_HANDOFF_TTL_MILLIS = 60L * 60 * 1000
        private const val APK_MIME = "application/vnd.android.package-archive"
        private val REDIRECT_CODES = setOf(301, 302, 303, 307, 308)
        private val ALLOWED_HOSTS = setOf(
            "github.com", "objects.githubusercontent.com", "release-assets.githubusercontent.com",
        )
    }
}

internal fun decodeAndValidateUpdateManifest(raw: String, json: Json): UpdateManifest {
    StrictJson.rejectDuplicateKeysAndInvalidSyntax(raw)
    val root = runCatching { json.parseToJsonElement(raw) }.getOrNull() as? JsonObject
        ?: throw IllegalArgumentException("更新清单格式无效")
    require(root.keys == UPDATE_MANIFEST_FIELDS) { "更新清单字段不完整或包含未知字段" }
    UPDATE_MANIFEST_INTEGER_FIELDS.forEach { field ->
        val value = root[field] as? JsonPrimitive
        require(value != null && !value.isString && JSON_INTEGER.matches(value.content)) {
            "更新清单字段类型无效：$field"
        }
    }
    UPDATE_MANIFEST_STRING_FIELDS.forEach { field ->
        val value = root[field] as? JsonPrimitive
        require(value != null && value.isString) { "更新清单字段类型无效：$field" }
    }
    val manifest = runCatching { json.decodeFromString<UpdateManifest>(raw) }
        .getOrElse { throw IllegalArgumentException("更新清单格式无效", it) }
    validateUpdateManifest(manifest)
    return manifest
}

internal fun validateUpdateManifest(manifest: UpdateManifest) {
    require(manifest.schemaVersion == 1) { "不支持的更新清单版本" }
    InputValidation.validateRaw(manifest.versionName, 1, 40, "版本名称")
    require(manifest.versionCode in 1..Int.MAX_VALUE) { "版本号无效" }
    require(manifest.minSdk in 26..100) { "最低 Android 版本无效" }
    require(SHA256.matches(manifest.apkSha256)) { "APK 摘要格式无效" }
    require(SHA256.matches(manifest.signerCertificateSha256)) { "签名证书摘要格式无效" }
    require(manifest.publishedAt.endsWith("Z")) { "发布时间必须使用 UTC Z 格式" }
    runCatching { Instant.parse(manifest.publishedAt) }
        .getOrElse { throw IllegalArgumentException("发布时间格式无效") }
    InputValidation.validateRaw(manifest.releaseNotes, 0, 4000, "更新说明")
}

private val SHA256 = Regex("^[0-9a-fA-F]{64}$")
private val JSON_INTEGER = Regex("-?(?:0|[1-9]\\d*)")
private val UPDATE_MANIFEST_INTEGER_FIELDS = setOf("schemaVersion", "versionCode", "minSdk")
private val UPDATE_MANIFEST_STRING_FIELDS = setOf(
    "versionName", "apkSha256", "signerCertificateSha256", "publishedAt", "releaseNotes",
)
private val UPDATE_MANIFEST_FIELDS = UPDATE_MANIFEST_INTEGER_FIELDS + UPDATE_MANIFEST_STRING_FIELDS
