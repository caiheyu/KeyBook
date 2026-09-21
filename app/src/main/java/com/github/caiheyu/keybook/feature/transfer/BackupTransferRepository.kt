package com.github.caiheyu.keybook.feature.transfer

import java.security.MessageDigest
import java.time.Instant
import java.util.Base64
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import com.github.caiheyu.keybook.data.VaultRepository
import com.github.caiheyu.keybook.data.catalog.CatalogType
import com.github.caiheyu.keybook.data.model.GeneratorSelectionKind

@Singleton
class BackupTransferRepository @Inject constructor(
    private val vault: VaultRepository,
    private val codec: BackupCodec,
    private val validator: BackupValidator,
    private val importer: BackupImporter,
) {
    suspend fun export(
        scope: BackupScope,
        selectedIds: Set<String>,
        currentWorkspaceId: String?,
        password: String?,
    ): ByteArray {
        require(selectedIds.isNotEmpty()) { "请至少选择一项" }
        val payload = vault.withConsistentSnapshot {
            val allWorkspaces = vault.listWorkspaces()
            val groups = when (scope) {
                BackupScope.WORKSPACES -> {
                    require(selectedIds.all { id -> allWorkspaces.any { it.id == id } }) { "所选工作空间已不存在" }
                    allWorkspaces.filter { it.id in selectedIds }.map { workspace ->
                        packWorkspace(workspace.id, workspace.name, scope, emptySet())
                    }
                }
                BackupScope.COMPANIES, BackupScope.APPS -> {
                    val workspaceId = requireNotNull(currentWorkspaceId) { "当前工作空间不存在" }
                    val workspace = allWorkspaces.firstOrNull { it.id == workspaceId }
                        ?: throw IllegalArgumentException("当前工作空间不存在")
                    listOf(packWorkspace(workspace.id, workspace.name, scope, selectedIds))
                }
            }
            BackupPayload(
                schemaVersion = 2,
                exportId = newId(),
                createdAt = Instant.now().toString(),
                scope = scope,
                protection = if (password == null) BackupProtection.NONE else BackupProtection.AES_256_GCM,
                workspaces = groups,
            )
        }
        validator.validate(payload)
        return codec.encode(payload, password)
    }

    fun decodeAndValidate(bytes: ByteArray, password: String?): BackupPayload =
        validator.validate(codec.decode(bytes, password))

    fun isEncrypted(bytes: ByteArray): Boolean = codec.isEncrypted(bytes)

    suspend fun import(payload: BackupPayload, plan: BackupImportPlan): BackupImportResult {
        validator.validate(payload)
        return importer.import(payload, plan)
    }

    private suspend fun packWorkspace(
        workspaceId: String,
        workspaceName: String,
        scope: BackupScope,
        selectedIds: Set<String>,
    ): BackupWorkspace {
        val allCompanies = vault.listCompanies(workspaceId)
        val allApps = allCompanies.flatMap { vault.listApps(workspaceId, it.id) }
        when (scope) {
            BackupScope.WORKSPACES -> Unit
            BackupScope.COMPANIES -> require(
                selectedIds.all { selectedId -> allCompanies.any { it.id == selectedId } },
            ) { "所选企业已不存在" }
            BackupScope.APPS -> require(
                selectedIds.all { selectedId -> allApps.any { it.id == selectedId } },
            ) { "所选应用已不存在" }
        }
        val companies = when (scope) {
            BackupScope.WORKSPACES -> allCompanies
            BackupScope.COMPANIES -> allCompanies.filter { it.id in selectedIds }
            BackupScope.APPS -> {
                val parentIds = allApps.filter { it.id in selectedIds }.mapTo(mutableSetOf()) { it.companyId }
                allCompanies.filter { it.id in parentIds }
            }
        }
        require(scope != BackupScope.COMPANIES || companies.isNotEmpty()) { "所选企业已不存在" }
        val companyIds = companies.mapTo(mutableSetOf()) { it.id }
        val apps = when (scope) {
            BackupScope.WORKSPACES, BackupScope.COMPANIES -> allApps.filter { it.companyId in companyIds }
            BackupScope.APPS -> allApps.filter { it.id in selectedIds }
        }
        require(scope != BackupScope.APPS || apps.isNotEmpty()) { "所选应用已不存在" }
        val accounts = apps.flatMap { app ->
            vault.listAccounts(workspaceId, app.id).map { summary ->
                requireNotNull(vault.getAccount(workspaceId, summary.id)) { "账号已不存在" }
            }
        }
        val secrets = accounts.flatMap { vault.listSecretItems(workspaceId, it.id) }
        val full = scope == BackupScope.WORKSPACES
        val generatorPresets = if (full) vault.listGeneratorPresets(workspaceId) else emptyList()
        val defaultGenerator = if (full) vault.getDefaultGenerator(workspaceId) else null
        val catalogPresets = if (full) vault.listCatalogPresets(workspaceId) else emptyList()
        val overrides = if (full) vault.listBuiltinCatalogOverrides(workspaceId) else emptyList()

        val companyIdsOut = companies.associate { it.id to newId() }
        val appIdsOut = apps.associate { it.id to newId() }
        val accountIdsOut = accounts.associate { it.id to newId() }
        val secretIdsOut = secrets.associate { it.id to newId() }
        val generatorIdsOut = generatorPresets.associate { it.id to newId() }
        val catalogIdsOut = catalogPresets.associate { it.id to newId() }
        val sourceIconIds = buildSet {
            companies.mapNotNullTo(this) { it.iconAssetId }
            apps.mapNotNullTo(this) { it.iconAssetId }
            catalogPresets.mapNotNullTo(this) { it.iconAssetId }
            overrides.mapNotNullTo(this) { it.iconAssetId }
        }
        val iconIdsOut = sourceIconIds.associateWith { newId() }
        val icons = sourceIconIds.map { sourceId ->
            val icon = requireNotNull(vault.getIconAsset(workspaceId, sourceId)) { "引用的图标已不存在" }
            BackupIconAsset(
                id = requireNotNull(iconIdsOut[sourceId]),
                mime = "image/png",
                width = icon.width,
                height = icon.height,
                byteLength = icon.pngBytes.size,
                sha256 = icon.pngBytes.sha256Hex(),
                data = Base64.getEncoder().encodeToString(icon.pngBytes),
            )
        }
        return BackupWorkspace(
            id = newId(),
            name = workspaceName,
            data = BackupWorkspaceData(
                companies = companies.map { company ->
                    BackupCompany(
                        requireNotNull(companyIdsOut[company.id]),
                        company.name,
                        company.note,
                        company.order,
                        company.websiteUrl,
                        company.iconAssetId?.let(iconIdsOut::get),
                    )
                },
                apps = apps.map { app ->
                    BackupApp(
                        requireNotNull(appIdsOut[app.id]),
                        requireNotNull(companyIdsOut[app.companyId]),
                        app.name,
                        app.note,
                        app.order,
                        app.websiteUrl,
                        app.iconAssetId?.let(iconIdsOut::get),
                    )
                },
                accounts = accounts.map { account ->
                    BackupAccount(
                        requireNotNull(accountIdsOut[account.id]),
                        requireNotNull(appIdsOut[account.appId]),
                        account.name,
                        account.note,
                        account.order,
                        account.login,
                        account.password,
                    )
                },
                secretItems = secrets.map { item ->
                    BackupSecretItem(
                        requireNotNull(secretIdsOut[item.id]),
                        requireNotNull(accountIdsOut[item.accountId]),
                        item.name,
                        item.note,
                        item.order,
                        item.password,
                    )
                },
                generatorPresets = generatorPresets.map { preset ->
                    BackupGeneratorPreset(
                        requireNotNull(generatorIdsOut[preset.id]),
                        preset.name,
                        preset.rules.length,
                        preset.rules.lower,
                        preset.rules.upper,
                        preset.rules.digits,
                        preset.rules.symbols,
                        preset.rules.excludeAmbiguous,
                        preset.rules.guaranteeEachCategory,
                        preset.rules.symbolChars,
                    )
                },
                defaultGenerator = defaultGenerator?.let { selection ->
                    BackupGeneratorSelection(
                        selection.kind.name,
                        if (selection.kind == GeneratorSelectionKind.CUSTOM) {
                            requireNotNull(generatorIdsOut[selection.id])
                        } else selection.id,
                    )
                },
                catalogPresets = catalogPresets.map { preset ->
                    BackupCatalogPreset(
                        requireNotNull(catalogIdsOut[preset.id]),
                        preset.type.fileValue,
                        preset.name,
                        preset.note,
                        preset.websiteUrl,
                        preset.iconAssetId?.let(iconIdsOut::get),
                    )
                },
                builtinCatalogOverrides = overrides.map { override ->
                    BackupBuiltinCatalogOverride(
                        override.builtinId,
                        override.type.fileValue,
                        override.name,
                        override.note,
                        override.websiteUrl,
                        override.iconAssetId?.let(iconIdsOut::get),
                    )
                },
                iconAssets = icons,
            ),
        )
    }

    private val CatalogType.fileValue: String
        get() = if (this == CatalogType.COMPANY) "company" else "app"

    private fun ByteArray.sha256Hex(): String = MessageDigest.getInstance("SHA-256")
        .digest(this).joinToString("") { "%02x".format(it) }

    private fun newId(): String = UUID.randomUUID().toString()
}
