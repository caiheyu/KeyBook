package com.github.caiheyu.keybook.data.catalog

import android.content.Context
import android.graphics.BitmapFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

enum class CatalogType { COMPANY, APP }

data class BuiltinCatalogEntry(
    val id: String,
    val type: CatalogType,
    val name: String,
    val websiteUrl: String,
    val note: String,
    val iconPng: ByteArray,
    val width: Int,
    val height: Int,
    val packagingReady: Boolean,
)

@Serializable
private data class CatalogFile(
    val schemaVersion: Int,
    val entries: List<CatalogJsonEntry>,
)

@Serializable
private data class CatalogJsonEntry(
    val id: String,
    val type: String,
    val name: String,
    val website: String,
    val iconPath: String,
    val iconSha256: String,
    val width: Int,
    val height: Int,
    val note: String = "",
    val packagingReady: Boolean = false,
)

@Singleton
class BuiltinCatalogRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    @Volatile
    private var cached: List<BuiltinCatalogEntry>? = null

    fun entries(): List<BuiltinCatalogEntry> = cached ?: synchronized(this) {
        cached ?: loadEntries().also { cached = it }
    }

    private fun loadEntries(): List<BuiltinCatalogEntry> {
        val raw = runCatching {
            context.assets.open(CATALOG_PATH).bufferedReader(Charsets.UTF_8).use { it.readText() }
        }.getOrElse { return emptyList() }
        val file = JSON.decodeFromString<CatalogFile>(raw)
        require(file.schemaVersion == 1) { "不支持的内置预定义项版本" }
        require(file.entries.size == EXPECTED_ENTRY_COUNT) { "内置预定义项数量不完整" }
        require(file.entries.map { it.id }.toSet().size == file.entries.size) { "内置预定义项 ID 重复" }
        return file.entries.map { entry ->
            val type = when (entry.type) {
                "company" -> CatalogType.COMPANY
                "app" -> CatalogType.APP
                else -> throw IllegalArgumentException("内置预定义项类型无效")
            }
            require(entry.id.startsWith("builtin.${entry.type}.")) { "内置预定义项 ID 无效" }
            val bytes = context.assets.open("catalog/${entry.iconPath}").use { it.readBytes() }
            require(bytes.size <= MAX_ICON_BYTES) { "内置图标过大" }
            require(bytes.sha256Hex() == entry.iconSha256.lowercase()) { "内置图标校验失败" }
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            require(options.outMimeType == "image/png") { "内置图标不是 PNG" }
            require(options.outWidth == entry.width && options.outHeight == entry.height) {
                "内置图标尺寸不一致"
            }
            require(entry.width in 1..256 && entry.height in 1..256) { "内置图标尺寸无效" }
            BuiltinCatalogEntry(
                id = entry.id,
                type = type,
                name = entry.name,
                websiteUrl = entry.website,
                note = entry.note,
                iconPng = bytes,
                width = entry.width,
                height = entry.height,
                packagingReady = entry.packagingReady,
            )
        }
    }

    private fun ByteArray.sha256Hex(): String = MessageDigest.getInstance("SHA-256")
        .digest(this)
        .joinToString("") { "%02x".format(it) }

    companion object {
        private const val CATALOG_PATH = "catalog/catalog.json"
        private const val EXPECTED_ENTRY_COUNT = 40
        private const val MAX_ICON_BYTES = 256 * 1024
        private val JSON = Json { ignoreUnknownKeys = true }
    }
}
