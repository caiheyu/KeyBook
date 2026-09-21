package com.github.caiheyu.keybook.feature.transfer

import android.graphics.BitmapFactory
import java.security.MessageDigest
import java.time.Instant
import java.util.Base64
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import com.github.caiheyu.keybook.core.validation.InputValidation
import com.github.caiheyu.keybook.data.catalog.BuiltinCatalogRepository
import com.github.caiheyu.keybook.data.catalog.CatalogType
import com.github.caiheyu.keybook.feature.generator.BuiltinGeneratorPresets
import com.github.caiheyu.keybook.feature.generator.PasswordGenerator
import com.github.caiheyu.keybook.feature.generator.PasswordRules

@Singleton
class BackupValidator @Inject constructor(
    private val catalogRepository: BuiltinCatalogRepository,
) {
    fun validate(payload: BackupPayload): BackupPayload {
        require(payload.schemaVersion == 2) { "仅支持 v2 备份" }
        requireUuid(payload.exportId)
        require(payload.createdAt.codePointCount(0, payload.createdAt.length) in 1..40) { "导出时间无效" }
        runCatching { Instant.parse(payload.createdAt) }.getOrElse { throw IllegalArgumentException("导出时间无效") }
        require(payload.workspaces.size in 1..1000) { "备份空间数量无效" }
        require(payload.scope == BackupScope.WORKSPACES || payload.workspaces.size == 1) {
            "部分备份只能包含一个空间分组"
        }
        val workspaceIds = mutableSetOf<String>()
        var imageBytes = 0L
        payload.workspaces.forEach { workspace ->
            requireUuid(workspace.id)
            require(workspaceIds.add(workspace.id)) { "空间分组 ID 重复" }
            requireNormalizedName(workspace.name, 40)
            imageBytes += validateWorkspace(payload.scope, workspace.data)
            require(imageBytes <= MAX_IMAGE_BYTES) { "备份图标总量超过 16 MiB" }
        }
        return payload
    }

    private fun validateWorkspace(scope: BackupScope, data: BackupWorkspaceData): Long {
        require(data.companies.size + data.apps.size + data.accounts.size + data.secretItems.size <= MAX_RECORDS) {
            "记录数量超过 10000 条"
        }
        require(data.generatorPresets.size <= 100) { "自定义生成器预设超过 100 个" }
        require(data.catalogPresets.size <= 1000) { "自定义预定义项超过 1000 个" }
        require(data.builtinCatalogOverrides.size <= 40) { "内置覆盖数量无效" }

        val allIds = mutableSetOf<String>()
        fun takeId(id: String) {
            requireUuid(id)
            require(allIds.add(id)) { "备份包含重复 ID" }
        }
        val assets = mutableMapOf<String, ByteArray>()
        var imageBytes = 0L
        data.iconAssets.forEach { asset ->
            takeId(asset.id)
            require(asset.mime == "image/png") { "备份图标 MIME 无效" }
            require(asset.width in 1..256 && asset.height in 1..256) { "备份图标尺寸无效" }
            require(asset.byteLength in 1..256 * 1024) { "备份图标大小无效" }
            require(SHA256.matches(asset.sha256) && asset.sha256 == asset.sha256.lowercase(Locale.ROOT)) {
                "备份图标摘要无效"
            }
            require(BASE64_PATTERN.matches(asset.data)) { "备份图标 Base64 无效" }
            val bytes = runCatching { Base64.getDecoder().decode(asset.data) }
                .getOrElse { throw IllegalArgumentException("备份图标 Base64 无效") }
            imageBytes += bytes.size
            require(bytes.size == asset.byteLength) { "备份图标长度不一致" }
            require(bytes.hasPngSignature()) { "备份图标不是 PNG" }
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            require(options.outMimeType == "image/png" && options.outWidth == asset.width && options.outHeight == asset.height) {
                "备份图标无法解码或尺寸不一致"
            }
            // Bounds decoding only parses the image header. Decode the pixels as well so
            // truncated/corrupt PNGs cannot enter the encrypted asset store.
            val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            require(decoded != null && decoded.width == asset.width && decoded.height == asset.height) {
                "备份图标无法解码或尺寸不一致"
            }
            decoded.recycle()
            require(bytes.sha256Hex() == asset.sha256) { "备份图标摘要不一致" }
            assets[asset.id] = bytes
        }

        val referencedAssets = mutableSetOf<String>()
        fun validateIcon(id: String?) {
            require(id == null || assets.containsKey(id)) { "图标引用无效" }
            if (id != null) referencedAssets += id
        }
        val companies = mutableSetOf<String>()
        val companyOrders = mutableSetOf<Int>()
        data.companies.forEach { company ->
            takeId(company.id)
            require(company.order >= 0 && companyOrders.add(company.order)) { "企业排序无效或重复" }
            validateCommonFields(company.name, company.note, company.websiteUrl)
            validateIcon(company.iconAssetId)
            companies += company.id
        }
        val apps = mutableSetOf<String>()
        val appOrders = mutableSetOf<Pair<String, Int>>()
        data.apps.forEach { app ->
            takeId(app.id)
            require(app.companyId in companies) { "应用父企业引用无效" }
            require(app.order >= 0 && appOrders.add(app.companyId to app.order)) { "应用排序无效或重复" }
            validateCommonFields(app.name, app.note, app.websiteUrl)
            validateIcon(app.iconAssetId)
            apps += app.id
        }
        val accounts = mutableSetOf<String>()
        val accountOrders = mutableSetOf<Pair<String, Int>>()
        data.accounts.forEach { account ->
            takeId(account.id)
            require(account.appId in apps) { "账号父应用引用无效" }
            require(account.order >= 0 && accountOrders.add(account.appId to account.order)) { "账号排序无效或重复" }
            requireNormalizedName(account.name, 100)
            InputValidation.normalizeNote(account.note)
            InputValidation.validateRaw(account.login, 1, 320, "登录账号")
            InputValidation.validateRaw(account.password, 1, 1024, "登录密码")
            accounts += account.id
        }
        val secretOrders = mutableSetOf<Pair<String, Int>>()
        data.secretItems.forEach { item ->
            takeId(item.id)
            require(item.accountId in accounts) { "附加项父账号引用无效" }
            require(item.order >= 0 && secretOrders.add(item.accountId to item.order)) { "附加项排序无效或重复" }
            requireNormalizedName(item.name, 100)
            InputValidation.normalizeNote(item.note)
            InputValidation.validateRaw(item.password, 1, 1024, "附加密码")
        }

        val customPresetIds = mutableSetOf<String>()
        val generatorNames = BuiltinGeneratorPresets.all.mapTo(mutableSetOf()) { it.name.lowercase(Locale.ROOT) }
        data.generatorPresets.forEach { preset ->
            takeId(preset.id)
            requireNormalizedName(preset.name, 40)
            require(generatorNames.add(preset.name.lowercase(Locale.ROOT))) { "生成器预设名称重复" }
            PasswordGenerator().validate(preset.toRules())
            customPresetIds += preset.id
        }
        data.catalogPresets.forEach { preset ->
            takeId(preset.id)
            validateCatalog(preset.type, preset.name, preset.note, preset.websiteUrl, preset.iconAssetId)
            validateIcon(preset.iconAssetId)
        }
        val builtins = catalogRepository.entries().associateBy { it.id }
        val overrideIds = mutableSetOf<String>()
        data.builtinCatalogOverrides.forEach { override ->
            val builtin = builtins[override.builtinId] ?: throw IllegalArgumentException("内置覆盖 ID 无效")
            require(overrideIds.add(override.builtinId)) { "内置覆盖 ID 重复" }
            require(override.type == builtin.type.fileValue) { "内置覆盖类型不匹配" }
            validateCatalog(override.type, override.name, override.note, override.websiteUrl, override.iconAssetId)
            validateIcon(override.iconAssetId)
        }

        if (scope == BackupScope.WORKSPACES) {
            val selection = requireNotNull(data.defaultGenerator) { "空间备份缺少默认生成器" }
            require(
                selection.kind == "BUILTIN" && BuiltinGeneratorPresets.all.any { it.id == selection.id } ||
                    selection.kind == "CUSTOM" && selection.id in customPresetIds,
            ) { "默认生成器引用无效" }
        } else {
            require(data.generatorPresets.isEmpty() && data.catalogPresets.isEmpty() &&
                data.builtinCatalogOverrides.isEmpty() && data.defaultGenerator == null
            ) { "部分备份不能包含预设配置" }
        }
        when (scope) {
            BackupScope.WORKSPACES -> Unit
            BackupScope.COMPANIES -> require(data.companies.isNotEmpty()) { "企业备份范围为空" }
            BackupScope.APPS -> {
                require(data.apps.isNotEmpty()) { "应用备份范围为空" }
                require(data.companies.all { company -> data.apps.any { it.companyId == company.id } }) {
                    "应用备份包含无关企业"
                }
            }
        }
        require(referencedAssets == assets.keys) { "备份包含未引用图标" }
        return imageBytes
    }

    private fun validateCommonFields(name: String, note: String, website: String) {
        requireNormalizedName(name, 100)
        InputValidation.normalizeNote(note)
        InputValidation.validateWebsite(website)
    }

    private fun validateCatalog(
        type: String,
        name: String,
        note: String,
        website: String,
        iconId: String?,
    ) {
        require(type == "company" || type == "app") { "预定义项类型无效" }
        validateCommonFields(name, note, website)
        require(iconId == null || UUID_V4.matches(iconId)) { "预定义项图标引用无效" }
    }

    private fun requireNormalizedName(value: String, max: Int) {
        require(InputValidation.normalizeName(value, max) == value) { "名称包含未裁剪空白" }
    }

    private fun requireUuid(value: String) {
        require(UUID_V4.matches(value)) { "备份 ID 不是 UUID v4" }
    }

    private fun BackupGeneratorPreset.toRules() = PasswordRules(
        length, lower, upper, digits, symbols, chars, exclude, guarantee,
    )

    private val CatalogType.fileValue: String
        get() = if (this == CatalogType.COMPANY) "company" else "app"

    private fun ByteArray.hasPngSignature(): Boolean = size >= PNG_SIGNATURE.size &&
        copyOfRange(0, PNG_SIGNATURE.size).contentEquals(PNG_SIGNATURE)

    private fun ByteArray.sha256Hex(): String = MessageDigest.getInstance("SHA-256")
        .digest(this).joinToString("") { "%02x".format(it) }

    companion object {
        private const val MAX_RECORDS = 10_000
        private const val MAX_IMAGE_BYTES = 16L * 1024 * 1024
        private val UUID_V4 = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-4[0-9a-fA-F]{3}-[89aAbB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$")
        private val SHA256 = Regex("^[0-9a-f]{64}$")
        private val BASE64_PATTERN = Regex("^(?:[A-Za-z0-9+/]{4})*(?:[A-Za-z0-9+/]{2}==|[A-Za-z0-9+/]{3}=)?$")
        private val PNG_SIGNATURE = byteArrayOf(
            0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a,
        )
    }
}
