package com.github.caiheyu.keybook.feature.transfer

import kotlinx.serialization.Serializable

@Serializable
enum class BackupScope { WORKSPACES, COMPANIES, APPS }

@Serializable
enum class BackupProtection { NONE, AES_256_GCM }

@Serializable
data class BackupPayload(
    val schemaVersion: Int,
    val exportId: String,
    val createdAt: String,
    val scope: BackupScope,
    val protection: BackupProtection,
    val workspaces: List<BackupWorkspace>,
)

@Serializable
data class BackupWorkspace(
    val id: String,
    val name: String,
    val data: BackupWorkspaceData,
)

@Serializable
data class BackupWorkspaceData(
    val companies: List<BackupCompany>,
    val apps: List<BackupApp>,
    val accounts: List<BackupAccount>,
    val secretItems: List<BackupSecretItem>,
    val generatorPresets: List<BackupGeneratorPreset>,
    val defaultGenerator: BackupGeneratorSelection?,
    val catalogPresets: List<BackupCatalogPreset>,
    val builtinCatalogOverrides: List<BackupBuiltinCatalogOverride>,
    val iconAssets: List<BackupIconAsset>,
)

@Serializable
data class BackupCompany(
    val id: String,
    val name: String,
    val note: String,
    val order: Int,
    val websiteUrl: String,
    val iconAssetId: String?,
)

@Serializable
data class BackupApp(
    val id: String,
    val companyId: String,
    val name: String,
    val note: String,
    val order: Int,
    val websiteUrl: String,
    val iconAssetId: String?,
)

@Serializable
data class BackupAccount(
    val id: String,
    val appId: String,
    val name: String,
    val note: String,
    val order: Int,
    val login: String,
    val password: String,
)

@Serializable
data class BackupSecretItem(
    val id: String,
    val accountId: String,
    val name: String,
    val note: String,
    val order: Int,
    val password: String,
)

@Serializable
data class BackupGeneratorPreset(
    val id: String,
    val name: String,
    val length: Int,
    val lower: Boolean,
    val upper: Boolean,
    val digits: Boolean,
    val symbols: Boolean,
    val exclude: Boolean,
    val guarantee: Boolean,
    val chars: String,
)

@Serializable
data class BackupGeneratorSelection(
    val kind: String,
    val id: String,
)

@Serializable
data class BackupCatalogPreset(
    val id: String,
    val type: String,
    val name: String,
    val note: String,
    val websiteUrl: String,
    val iconAssetId: String?,
)

@Serializable
data class BackupBuiltinCatalogOverride(
    val builtinId: String,
    val type: String,
    val name: String,
    val note: String,
    val websiteUrl: String,
    val iconAssetId: String?,
)

@Serializable
data class BackupIconAsset(
    val id: String,
    val mime: String,
    val width: Int,
    val height: Int,
    val byteLength: Int,
    val sha256: String,
    val data: String,
)

sealed interface BackupImportPlan

data object CopyWorkspaceImport : BackupImportPlan

data class ReplaceWorkspaceImport(
    val targetsBySourceWorkspaceId: Map<String, String>,
) : BackupImportPlan

data class AppendCompaniesImport(
    val targetWorkspaceId: String,
) : BackupImportPlan

data class AppendAppsImport(
    val targetWorkspaceId: String,
    val companyTargets: Map<String, AppCompanyTarget>,
) : BackupImportPlan

data class AppCompanyTarget(
    val existingCompanyId: String? = null,
    val newCompanyName: String? = null,
)

data class BackupImportResult(
    val affectedWorkspaceIds: List<String>,
)
