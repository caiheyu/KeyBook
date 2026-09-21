package com.github.caiheyu.keybook.data.model

import kotlinx.serialization.Serializable
import com.github.caiheyu.keybook.data.catalog.CatalogType
import com.github.caiheyu.keybook.feature.generator.PasswordRules

data class Workspace(
    val id: String,
    val name: String,
    val createdAt: Long,
)

data class WorkspaceRecordCounts(
    val companyCount: Int,
    val accountCount: Int,
)

data class AppDeletionCounts(
    val accountCount: Int,
    val secretItemCount: Int,
)

data class AccountDeletionCounts(
    val secretItemCount: Int,
)

data class Company(
    val id: String,
    val workspaceId: String,
    val name: String,
    val note: String,
    val websiteUrl: String,
    val iconAssetId: String?,
    val order: Int,
    val createdAt: Long,
    val updatedAt: Long,
)

data class AppEntry(
    val id: String,
    val workspaceId: String,
    val companyId: String,
    val name: String,
    val note: String,
    val websiteUrl: String,
    val iconAssetId: String?,
    val order: Int,
    val createdAt: Long,
    val updatedAt: Long,
)

data class Account(
    val id: String,
    val workspaceId: String,
    val appId: String,
    val name: String,
    val login: String,
    val password: String,
    val note: String,
    val order: Int,
    val createdAt: Long,
    val updatedAt: Long,
)

data class SecretItem(
    val id: String,
    val workspaceId: String,
    val accountId: String,
    val name: String,
    val password: String,
    val note: String,
    val order: Int,
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
internal data class WorkspacePayload(val name: String, val createdAt: Long)

@Serializable
internal data class CompanyPayload(
    val name: String,
    val note: String,
    val websiteUrl: String,
    val iconAssetId: String?,
    val order: Int,
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
internal data class AppPayload(
    val name: String,
    val note: String,
    val websiteUrl: String,
    val iconAssetId: String?,
    val order: Int,
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
internal data class AccountPayload(
    val name: String,
    val login: String,
    val password: String,
    val note: String,
    val order: Int,
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
internal data class SecretItemPayload(
    val name: String,
    val password: String,
    val note: String,
    val order: Int,
    val createdAt: Long,
    val updatedAt: Long,
)

data class StoredGeneratorPreset(
    val id: String,
    val workspaceId: String,
    val name: String,
    val rules: PasswordRules,
    val createdAt: Long,
    val updatedAt: Long,
)

enum class GeneratorSelectionKind { BUILTIN, CUSTOM }

data class GeneratorSelection(
    val kind: GeneratorSelectionKind,
    val id: String,
)

data class CatalogPreset(
    val id: String,
    val workspaceId: String,
    val type: CatalogType,
    val name: String,
    val note: String,
    val websiteUrl: String,
    val iconAssetId: String?,
    val createdAt: Long,
    val updatedAt: Long,
)

data class BuiltinCatalogOverride(
    val workspaceId: String,
    val builtinId: String,
    val type: CatalogType,
    val name: String,
    val note: String,
    val websiteUrl: String,
    val iconAssetId: String?,
    val updatedAt: Long,
)

@Serializable
internal data class GeneratorPresetPayload(
    val name: String,
    val length: Int,
    val lower: Boolean,
    val upper: Boolean,
    val digits: Boolean,
    val symbols: Boolean,
    val symbolChars: String,
    val excludeAmbiguous: Boolean,
    val guaranteeEachCategory: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
internal data class CatalogPresetPayload(
    val type: String,
    val name: String,
    val note: String,
    val websiteUrl: String,
    val iconAssetId: String?,
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
internal data class BuiltinCatalogOverridePayload(
    val type: String,
    val name: String,
    val note: String,
    val websiteUrl: String,
    val iconAssetId: String?,
    val updatedAt: Long,
)

@Serializable
internal data class WorkspaceConfigPayload(
    val defaultGeneratorKind: String,
    val defaultGeneratorId: String,
)
