package com.github.caiheyu.keybook.data.model

import com.github.caiheyu.keybook.data.catalog.CatalogType
import com.github.caiheyu.keybook.feature.generator.PasswordRules

data class CompanyDraft(
    val name: String,
    val note: String = "",
    val websiteUrl: String = "",
    val iconAssetId: String? = null,
    val newIcon: IconDraft? = null,
    val removeIcon: Boolean = false,
)

data class AppDraft(
    val name: String,
    val note: String = "",
    val websiteUrl: String = "",
    val iconAssetId: String? = null,
    val newIcon: IconDraft? = null,
    val removeIcon: Boolean = false,
)

data class IconDraft(
    val pngBytes: ByteArray,
    val width: Int,
    val height: Int,
)

data class IconAsset(
    val id: String,
    val workspaceId: String,
    val pngBytes: ByteArray,
    val width: Int,
    val height: Int,
)

data class AccountDraft(
    val name: String,
    val login: String,
    val password: String,
    val note: String = "",
)

data class SecretItemDraft(
    val name: String,
    val password: String,
    val note: String = "",
)

data class GeneratorPresetDraft(
    val name: String,
    val rules: PasswordRules,
)

data class CatalogPresetDraft(
    val type: CatalogType,
    val name: String,
    val note: String = "",
    val websiteUrl: String = "",
    val iconAssetId: String? = null,
    val newIcon: IconDraft? = null,
    val removeIcon: Boolean = false,
)

data class AccountSummary(
    val id: String,
    val workspaceId: String,
    val appId: String,
    val name: String,
    val login: String,
    val order: Int,
)

enum class SearchResultType { COMPANY, APP, ACCOUNT, SECRET_ITEM }

data class SearchResult(
    val type: SearchResultType,
    val id: String,
    val title: String,
    val subtitle: String,
    val companyId: String? = null,
    val appId: String? = null,
    val accountId: String? = null,
)
