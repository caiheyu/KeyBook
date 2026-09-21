package com.github.caiheyu.keybook.feature.icon

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.URI
import java.util.Base64
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import com.github.caiheyu.keybook.core.network.PublicOnlyDns
import com.github.caiheyu.keybook.core.validation.InputValidation
import com.github.caiheyu.keybook.data.model.IconDraft
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.Jsoup

data class WebsiteIconCandidate(
    val source: String,
    val icon: IconDraft,
)

@Serializable
private data class WebManifest(val icons: List<WebManifestIcon> = emptyList())

@Serializable
private data class WebManifestIcon(val src: String = "")

@Singleton
class WebsiteIconFetcher @Inject constructor(
    private val normalizer: IconNormalizer,
) {
    private val client = OkHttpClient.Builder()
        .dns(PublicOnlyDns())
        .followRedirects(false)
        .followSslRedirects(false)
        .connectTimeout(7, TimeUnit.SECONDS)
        .readTimeout(7, TimeUnit.SECONDS)
        .callTimeout(TOTAL_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
        .cookieJar(okhttp3.CookieJar.NO_COOKIES)
        .build()

    suspend fun fetch(websiteUrl: String, allowHttp: Boolean): List<WebsiteIconCandidate> =
        withTimeout(TOTAL_TIMEOUT_MILLIS) {
            withContext(Dispatchers.IO) {
                val normalized = InputValidation.validateWebsite(websiteUrl)
                require(normalized.isNotEmpty()) { "请先填写有效的官网地址" }
                val start = normalized.toHttpUrlOrNull()
                    ?: throw IllegalArgumentException("官网地址格式无效")
                require(start.username.isEmpty() && start.password.isEmpty()) { "官网地址不能包含用户名或密码" }
                val startScheme = start.scheme.lowercase()
                require(startScheme == "https" || startScheme == "http" && allowHttp) {
                    if (startScheme == "http") "本次 HTTP 获取尚未确认" else "官网地址协议无效"
                }
                val policy = RequestPolicy(allowHttp = startScheme == "http" && allowHttp)
                val budget = FetchBudget()
                val pageResult = request(start.newBuilder().fragment(null).build(), policy, budget, MAX_DOCUMENT_BYTES)
                val direct = runCatching { normalizer.normalize(pageResult.bytes) }.getOrNull()
                if (direct != null) {
                    return@withContext listOf(WebsiteIconCandidate("官网图片", direct))
                }
                val references = WebsiteIconParsing.extractDocumentReferences(pageResult.bytes, pageResult.url)
                val sources = references.iconSources.toMutableSet()
                for (manifestUrl in references.manifestSources) {
                    if (budget.requests >= MAX_REQUESTS - 1) break
                    val resolved = manifestUrl.toHttpUrlOrNull() ?: continue
                    runCatching {
                        val response = request(resolved, policy, budget, MAX_DOCUMENT_BYTES)
                        val manifest = MANIFEST_JSON.decodeFromString<WebManifest>(response.bytes.toString(Charsets.UTF_8))
                        manifest.icons.forEach { icon ->
                            WebsiteIconParsing.resolveReference(response.url, icon.src)?.let(sources::add)
                        }
                    }
                }
                WebsiteIconParsing.resolveReference(pageResult.url, "/favicon.ico")?.let(sources::add)

                val candidates = mutableListOf<WebsiteIconCandidate>()
                for (source in sources) {
                    currentCoroutineContext().ensureActive()
                    if (candidates.size >= MAX_CANDIDATES || budget.requests >= MAX_REQUESTS) break
                    val icon = runCatching {
                        val bytes = if (source.startsWith("data:", true)) {
                            WebsiteIconParsing.decodeDataUrl(source)
                        } else {
                            val url = source.toHttpUrlOrNull() ?: return@runCatching null
                            request(url, policy, budget, IconNormalizer.MAX_INPUT_BYTES).bytes
                        }
                        normalizer.normalize(bytes)
                    }.getOrNull() ?: continue
                    candidates += WebsiteIconCandidate(source.displaySource(), icon)
                }
                require(candidates.isNotEmpty()) { "未找到可用图标" }
                WebsiteIconParsing.sortCandidates(candidates)
            }
        }

    private suspend fun request(
        initialUrl: HttpUrl,
        policy: RequestPolicy,
        budget: FetchBudget,
        responseLimit: Int,
    ): FetchedResponse {
        var url = initialUrl
        repeat(MAX_REDIRECTS + 1) { redirectIndex ->
            policy.validate(url)
            budget.beforeRequest()
            val request = Request.Builder()
                .url(url)
                .header("Accept", "text/html,application/manifest+json,image/*;q=0.9,*/*;q=0.1")
                .header("Cache-Control", "no-store")
                .build()
            val result = execute(client.newCall(request)) {
                if (it.code in REDIRECT_CODES) {
                    require(redirectIndex < MAX_REDIRECTS) { "网站重定向次数过多" }
                    val location = it.header("Location")
                        ?: throw IllegalArgumentException("网站重定向缺少目标")
                    NetworkResult.Redirect(
                        url.resolve(location) ?: throw IllegalArgumentException("网站重定向地址无效"),
                    )
                } else {
                    require(it.isSuccessful) { "网站请求失败（HTTP ${it.code}）" }
                    val body = it.body ?: throw IllegalArgumentException("网站返回空内容")
                    NetworkResult.Success(
                        FetchedResponse(url, body.readLimited(responseLimit, budget)),
                    )
                }
            }
            when (result) {
                is NetworkResult.Redirect -> url = result.url
                is NetworkResult.Success -> return result.value
            }
        }
        throw IllegalArgumentException("网站重定向次数过多")
    }

    private suspend fun <T> execute(
        call: Call,
        consume: (Response) -> NetworkResult<T>,
    ): NetworkResult<T> = suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWith(Result.failure(e))
            }

            override fun onResponse(call: Call, response: Response) {
                runCatching { response.use(consume) }.fold(
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

    private sealed interface NetworkResult<out T> {
        data class Redirect(val url: HttpUrl) : NetworkResult<Nothing>
        data class Success<T>(val value: T) : NetworkResult<T>
    }

    private fun ResponseBody.readLimited(limit: Int, budget: FetchBudget): ByteArray {
        val declared = contentLength()
        require(declared < 0 || declared <= limit) { "网站返回内容过大" }
        val output = ByteArrayOutputStream()
        byteStream().use { input ->
            val buffer = ByteArray(16 * 1024)
            var total = 0
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                require(total <= limit) { "网站返回内容过大" }
                budget.addBytes(read)
                output.write(buffer, 0, read)
            }
        }
        return output.toByteArray()
    }

    private fun String.displaySource(): String = if (startsWith("data:", true)) {
        "网页内嵌图标"
    } else {
        runCatching { URI(this).host }.getOrNull()?.let { "来自 $it" } ?: "网页图标"
    }

    private data class FetchedResponse(val url: HttpUrl, val bytes: ByteArray)

    private data class RequestPolicy(val allowHttp: Boolean) {
        fun validate(url: HttpUrl) {
            require(url.username.isEmpty() && url.password.isEmpty()) { "请求地址不能包含用户名或密码" }
            val scheme = url.scheme.lowercase()
            require(scheme == "https" || allowHttp && scheme == "http") {
                "HTTPS 官网不能降级到 HTTP"
            }
            require(url.port in 1..65535) { "请求端口无效" }
        }
    }

    private class FetchBudget {
        var requests: Int = 0
            private set
        private var bytes: Long = 0

        fun beforeRequest() {
            requests++
            require(requests <= MAX_REQUESTS) { "官网图标请求次数超过限制" }
        }

        fun addBytes(count: Int) {
            bytes += count
            require(bytes <= MAX_TOTAL_BYTES) { "官网图标响应总量超过限制" }
        }
    }

    companion object {
        private const val MAX_REQUESTS = 10
        private const val MAX_REDIRECTS = 3
        private const val MAX_CANDIDATES = 8
        private const val MAX_DOCUMENT_BYTES = 1024 * 1024
        private const val MAX_TOTAL_BYTES = 10L * 1024 * 1024
        private const val TOTAL_TIMEOUT_MILLIS = 20_000L
        private val REDIRECT_CODES = setOf(301, 302, 303, 307, 308)
        private val MANIFEST_JSON = Json { ignoreUnknownKeys = true }
    }
}

internal data class WebsiteDocumentReferences(
    val iconSources: List<String>,
    val manifestSources: List<String>,
)

internal object WebsiteIconParsing {
    fun extractDocumentReferences(bytes: ByteArray, base: HttpUrl): WebsiteDocumentReferences {
        val document = Jsoup.parse(bytes.inputStream(), null, base.toString())
        val icons = linkedSetOf<String>()
        val manifests = linkedSetOf<String>()
        document.select("link[href]").forEach { element ->
            val rel = element.attr("rel").lowercase().split(REL_WHITESPACE).filter(String::isNotEmpty)
            val resolved = resolveReference(base, element.attr("href")) ?: return@forEach
            if (rel.any { it in ICON_RELATIONS }) icons += resolved
            if ("manifest" in rel) manifests += resolved
        }
        return WebsiteDocumentReferences(icons.toList(), manifests.toList())
    }

    fun resolveReference(base: HttpUrl, reference: String): String? {
        val trimmed = reference.trim()
        if (trimmed.isEmpty()) return null
        if (trimmed.startsWith("data:", true)) return trimmed
        return base.resolve(trimmed)?.newBuilder()?.fragment(null)?.build()?.toString()
    }

    fun decodeDataUrl(value: String): ByteArray {
        require(value.length <= MAX_DATA_URL_CHARS) { "内嵌图标过大" }
        val comma = value.indexOf(',')
        require(comma > 5) { "内嵌图标格式无效" }
        val metadata = value.substring(5, comma).split(';')
        require(metadata.lastOrNull().equals("base64", true)) { "内嵌图标必须使用 Base64" }
        require(metadata.first().lowercase() in ALLOWED_DATA_MIME) { "内嵌图标 MIME 无效" }
        val encoded = value.substring(comma + 1)
        require(BASE64_PATTERN.matches(encoded)) { "内嵌图标 Base64 无效" }
        val decoded = runCatching { Base64.getDecoder().decode(encoded) }
            .getOrElse { throw IllegalArgumentException("内嵌图标 Base64 无效") }
        require(decoded.size <= MAX_DATA_BYTES) { "内嵌图标过大" }
        return decoded
    }

    fun sortCandidates(candidates: List<WebsiteIconCandidate>): List<WebsiteIconCandidate> =
        candidates.sortedWith(
            compareByDescending<WebsiteIconCandidate> { it.icon.width.toLong() * it.icon.height }
                .thenByDescending { maxOf(it.icon.width, it.icon.height) }
                .thenByDescending { minOf(it.icon.width, it.icon.height) },
        )

    private const val MAX_DATA_BYTES = 1024 * 1024
    private const val MAX_DATA_URL_CHARS = 1_500_000
    private val REL_WHITESPACE = Regex("\\s+")
    private val ICON_RELATIONS = setOf("icon", "shortcut", "apple-touch-icon", "apple-touch-icon-precomposed")
    private val BASE64_PATTERN = Regex("^(?:[A-Za-z0-9+/]{4})*(?:[A-Za-z0-9+/]{2}==|[A-Za-z0-9+/]{3}=)?$")
    private val ALLOWED_DATA_MIME = setOf(
        "image/png", "image/jpeg", "image/webp", "image/gif", "image/bmp",
        "image/x-icon", "image/vnd.microsoft.icon", "image/svg+xml",
    )
}
