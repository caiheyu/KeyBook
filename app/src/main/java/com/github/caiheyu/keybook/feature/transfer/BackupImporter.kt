package com.github.caiheyu.keybook.feature.transfer

import androidx.room.withTransaction
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.security.MessageDigest
import java.util.Base64
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import com.github.caiheyu.keybook.core.crypto.CryptoBox
import com.github.caiheyu.keybook.core.crypto.RecordAad
import com.github.caiheyu.keybook.core.crypto.VaultCrypto
import com.github.caiheyu.keybook.core.validation.InputValidation
import com.github.caiheyu.keybook.data.db.AccountEntity
import com.github.caiheyu.keybook.data.db.AppEntity
import com.github.caiheyu.keybook.data.db.BuiltinCatalogOverrideEntity
import com.github.caiheyu.keybook.data.db.CatalogPresetEntity
import com.github.caiheyu.keybook.data.db.CompanyEntity
import com.github.caiheyu.keybook.data.db.GeneratorPresetEntity
import com.github.caiheyu.keybook.data.db.IconAssetEntity
import com.github.caiheyu.keybook.data.db.KeyBookDatabase
import com.github.caiheyu.keybook.data.db.SecretItemEntity
import com.github.caiheyu.keybook.data.db.VaultDao
import com.github.caiheyu.keybook.data.db.WorkspaceConfigEntity
import com.github.caiheyu.keybook.data.db.WorkspaceEntity
import com.github.caiheyu.keybook.data.model.AccountPayload
import com.github.caiheyu.keybook.data.model.AppPayload
import com.github.caiheyu.keybook.data.model.BuiltinCatalogOverridePayload
import com.github.caiheyu.keybook.data.model.CatalogPresetPayload
import com.github.caiheyu.keybook.data.model.CompanyPayload
import com.github.caiheyu.keybook.data.model.GeneratorPresetPayload
import com.github.caiheyu.keybook.data.model.IconDraft
import com.github.caiheyu.keybook.data.model.SecretItemPayload
import com.github.caiheyu.keybook.data.model.WorkspaceConfigPayload
import com.github.caiheyu.keybook.data.model.WorkspacePayload
import com.github.caiheyu.keybook.feature.generator.BuiltinGeneratorPresets

@Singleton
class BackupImporter @Inject constructor(
    private val database: KeyBookDatabase,
    private val dao: VaultDao,
    private val crypto: VaultCrypto,
    private val json: Json,
) {
    suspend fun import(payload: BackupPayload, plan: BackupImportPlan): BackupImportResult =
        database.withTransaction {
            when (plan) {
                CopyWorkspaceImport -> importWorkspaceCopies(payload)
                is ReplaceWorkspaceImport -> replaceWorkspaces(payload, plan)
                is AppendCompaniesImport -> appendCompanies(payload, plan.targetWorkspaceId)
                is AppendAppsImport -> appendApps(payload, plan)
            }
        }

    private suspend fun importWorkspaceCopies(payload: BackupPayload): BackupImportResult {
        require(payload.scope == BackupScope.WORKSPACES) { "导入计划与备份范围不匹配" }
        val usedNames = dao.getWorkspaces().mapTo(mutableSetOf()) { decryptWorkspace(it).name }
        val created = mutableListOf<String>()
        payload.workspaces.forEach { source ->
            val name = uniqueImportedName(source.name, usedNames, 40)
            val (workspaceId, key) = createWorkspace(name)
            try {
                clearWorkspaceContent(workspaceId)
                importFullWorkspace(workspaceId, key, source.data)
            } finally {
                key.fill(0)
            }
            created += workspaceId
        }
        return BackupImportResult(created)
    }

    private suspend fun replaceWorkspaces(
        payload: BackupPayload,
        plan: ReplaceWorkspaceImport,
    ): BackupImportResult {
        require(payload.scope == BackupScope.WORKSPACES) { "导入计划与备份范围不匹配" }
        val sourceIds = payload.workspaces.mapTo(mutableSetOf()) { it.id }
        require(plan.targetsBySourceWorkspaceId.keys == sourceIds) { "每个源空间都必须指定替换目标" }
        val targets = plan.targetsBySourceWorkspaceId.values
        require(targets.toSet().size == targets.size) { "替换目标不能重复" }
        targets.forEach { requireNotNull(dao.getWorkspace(it)) { "替换目标空间已不存在" } }
        payload.workspaces.forEach { source ->
            val targetId = requireNotNull(plan.targetsBySourceWorkspaceId[source.id])
            val key = unwrapKey(requireNotNull(dao.getWorkspace(targetId)))
            try {
                clearWorkspaceContent(targetId)
                importFullWorkspace(targetId, key, source.data)
            } finally {
                key.fill(0)
            }
        }
        return BackupImportResult(targets.toList())
    }

    private suspend fun appendCompanies(
        payload: BackupPayload,
        workspaceId: String,
    ): BackupImportResult {
        require(payload.scope == BackupScope.COMPANIES && payload.workspaces.size == 1) {
            "导入计划与备份范围不匹配"
        }
        requireNotNull(dao.getWorkspace(workspaceId)) { "目标空间已不存在" }
        val source = payload.workspaces.single().data
        require(existingRecordCount(workspaceId) + source.recordCount <= MAX_RECORDS) { "导入后记录数量超过 10000 条" }
        val key = unwrapKey(requireNotNull(dao.getWorkspace(workspaceId)))
        try {
            val icons = IconImporter(workspaceId, key, source.iconAssets)
            val companyMap = mutableMapOf<String, String>()
            var nextCompanyOrder = nextCompanyOrder(workspaceId, key)
            source.companies.sortedBy { it.order }.forEach { company ->
                val id = newId()
                companyMap[company.id] = id
                insertCompany(
                    workspaceId, key, id, company.name, company.note, company.websiteUrl,
                    icons.map(company.iconAssetId), nextCompanyOrder++,
                )
            }
            importAppTrees(workspaceId, key, source, companyMap, icons, preserveAppOrder = true)
        } finally {
            key.fill(0)
        }
        return BackupImportResult(listOf(workspaceId))
    }

    private suspend fun appendApps(payload: BackupPayload, plan: AppendAppsImport): BackupImportResult {
        require(payload.scope == BackupScope.APPS && payload.workspaces.size == 1) {
            "导入计划与备份范围不匹配"
        }
        val workspaceId = plan.targetWorkspaceId
        val workspace = requireNotNull(dao.getWorkspace(workspaceId)) { "目标空间已不存在" }
        val source = payload.workspaces.single().data
        val sourceCompanyIds = source.companies.mapTo(mutableSetOf()) { it.id }
        require(plan.companyTargets.keys == sourceCompanyIds) { "每个源企业都必须指定导入目标" }
        val newCompanyCount = plan.companyTargets.values.count { it.existingCompanyId == null }
        val incomingCount = source.apps.size + source.accounts.size + source.secretItems.size + newCompanyCount
        require(existingRecordCount(workspaceId) + incomingCount <= MAX_RECORDS) { "导入后记录数量超过 10000 条" }
        val key = unwrapKey(workspace)
        try {
            val icons = IconImporter(workspaceId, key, source.iconAssets)
            val companyMap = mutableMapOf<String, String>()
            var nextCompanyOrder = nextCompanyOrder(workspaceId, key)
            source.companies.sortedBy { it.order }.forEach { company ->
                val target = requireNotNull(plan.companyTargets[company.id])
                require((target.existingCompanyId == null) xor (target.newCompanyName == null)) {
                    "应用导入目标无效"
                }
                if (target.existingCompanyId != null) {
                    requireNotNull(dao.getCompany(workspaceId, target.existingCompanyId)) { "目标企业已不存在" }
                    companyMap[company.id] = target.existingCompanyId
                } else {
                    val id = newId()
                    companyMap[company.id] = id
                    insertCompany(
                        workspaceId,
                        key,
                        id,
                        InputValidation.normalizeName(requireNotNull(target.newCompanyName)),
                        company.note,
                        company.websiteUrl,
                        icons.map(company.iconAssetId),
                        nextCompanyOrder++,
                    )
                }
            }
            importAppTrees(workspaceId, key, source, companyMap, icons, preserveAppOrder = false)
        } finally {
            key.fill(0)
        }
        return BackupImportResult(listOf(workspaceId))
    }

    private suspend fun importFullWorkspace(
        workspaceId: String,
        key: ByteArray,
        source: BackupWorkspaceData,
    ) {
        val icons = IconImporter(workspaceId, key, source.iconAssets)
        val companyMap = mutableMapOf<String, String>()
        source.companies.sortedBy { it.order }.forEach { company ->
            val id = newId()
            companyMap[company.id] = id
            insertCompany(
                workspaceId, key, id, company.name, company.note, company.websiteUrl,
                icons.map(company.iconAssetId), company.order,
            )
        }
        val appMap = importAppTrees(
            workspaceId, key, source, companyMap, icons, preserveAppOrder = true,
        )
        require(appMap.size == source.apps.size)

        val generatorMap = mutableMapOf<String, String>()
        val now = System.currentTimeMillis()
        source.generatorPresets.forEachIndexed { index, preset ->
            val id = newId()
            generatorMap[preset.id] = id
            val restoredAt = now + index
            val payload = GeneratorPresetPayload(
                preset.name,
                preset.length,
                preset.lower,
                preset.upper,
                preset.digits,
                preset.symbols,
                preset.chars,
                preset.exclude,
                preset.guarantee,
                restoredAt,
                restoredAt,
            )
            val box = encryptPayload(key, TYPE_GENERATOR_PRESET, workspaceId, id, null, payload)
            dao.insertGeneratorPreset(
                GeneratorPresetEntity(
                    workspaceId, id, box.nonce, box.ciphertext,
                    RecordAad.FORMAT_VERSION, RecordAad.KEY_VERSION,
                ),
            )
        }
        source.catalogPresets.forEach { preset ->
            val id = newId()
            val payload = CatalogPresetPayload(
                preset.type, preset.name, preset.note, preset.websiteUrl,
                icons.map(preset.iconAssetId), now, now,
            )
            val box = encryptPayload(key, TYPE_CATALOG_PRESET, workspaceId, id, null, payload)
            dao.insertCatalogPreset(
                CatalogPresetEntity(
                    workspaceId, id, box.nonce, box.ciphertext,
                    RecordAad.FORMAT_VERSION, RecordAad.KEY_VERSION,
                ),
            )
        }
        source.builtinCatalogOverrides.forEach { override ->
            val payload = BuiltinCatalogOverridePayload(
                override.type, override.name, override.note, override.websiteUrl,
                icons.map(override.iconAssetId), now,
            )
            val box = encryptPayload(
                key, TYPE_BUILTIN_OVERRIDE, workspaceId, override.builtinId, null, payload,
            )
            dao.upsertBuiltinCatalogOverride(
                BuiltinCatalogOverrideEntity(
                    workspaceId, override.builtinId, box.nonce, box.ciphertext,
                    RecordAad.FORMAT_VERSION, RecordAad.KEY_VERSION,
                ),
            )
        }
        val selected = requireNotNull(source.defaultGenerator)
        val selectedId = if (selected.kind == "CUSTOM") {
            requireNotNull(generatorMap[selected.id])
        } else selected.id
        upsertWorkspaceConfig(workspaceId, key, selected.kind, selectedId)
    }

    private suspend fun importAppTrees(
        workspaceId: String,
        key: ByteArray,
        source: BackupWorkspaceData,
        companyMap: Map<String, String>,
        icons: IconImporter,
        preserveAppOrder: Boolean,
    ): Map<String, String> {
        val appMap = mutableMapOf<String, String>()
        val accountMap = mutableMapOf<String, String>()
        val usedNamesByCompany = mutableMapOf<String, MutableSet<String>>()
        val nextOrders = mutableMapOf<String, Int>()
        companyMap.values.toSet().forEach { companyId ->
            val existing = dao.getApps(workspaceId, companyId).map { decryptApp(it, key) }
            usedNamesByCompany[companyId] = existing.mapTo(mutableSetOf()) { it.name }
            nextOrders[companyId] = existing.maxOfOrNull { it.order }?.plus(1) ?: 0
        }
        source.apps.sortedWith(compareBy<BackupApp> { it.companyId }.thenBy { it.order }).forEach { app ->
            val companyId = requireNotNull(companyMap[app.companyId])
            val id = newId()
            appMap[app.id] = id
            val name = if (preserveAppOrder) {
                app.name
            } else {
                uniqueImportedName(app.name, requireNotNull(usedNamesByCompany[companyId]), 100)
            }
            val order = if (preserveAppOrder) app.order else requireNotNull(nextOrders[companyId]).also {
                nextOrders[companyId] = it + 1
            }
            insertApp(
                workspaceId, key, id, companyId, name, app.note, app.websiteUrl,
                icons.map(app.iconAssetId), order,
            )
        }
        source.accounts.sortedWith(compareBy<BackupAccount> { it.appId }.thenBy { it.order }).forEach { account ->
            val appId = requireNotNull(appMap[account.appId])
            val id = newId()
            accountMap[account.id] = id
            val now = System.currentTimeMillis()
            val payload = AccountPayload(
                account.name, account.login, account.password, account.note,
                account.order, now, now,
            )
            val box = encryptPayload(key, TYPE_ACCOUNT, workspaceId, id, appId, payload)
            dao.insertAccount(
                AccountEntity(
                    workspaceId, id, appId, box.nonce, box.ciphertext,
                    RecordAad.FORMAT_VERSION, RecordAad.KEY_VERSION,
                ),
            )
        }
        source.secretItems.sortedWith(compareBy<BackupSecretItem> { it.accountId }.thenBy { it.order }).forEach { item ->
            val accountId = requireNotNull(accountMap[item.accountId])
            val id = newId()
            val now = System.currentTimeMillis()
            val payload = SecretItemPayload(item.name, item.password, item.note, item.order, now, now)
            val box = encryptPayload(key, TYPE_SECRET, workspaceId, id, accountId, payload)
            dao.insertSecretItem(
                SecretItemEntity(
                    workspaceId, id, accountId, box.nonce, box.ciphertext,
                    RecordAad.FORMAT_VERSION, RecordAad.KEY_VERSION,
                ),
            )
        }
        return appMap
    }

    private suspend fun insertCompany(
        workspaceId: String,
        key: ByteArray,
        id: String,
        name: String,
        note: String,
        website: String,
        iconId: String?,
        order: Int,
    ) {
        val now = System.currentTimeMillis()
        val payload = CompanyPayload(name, note, website, iconId, order, now, now)
        val box = encryptPayload(key, TYPE_COMPANY, workspaceId, id, null, payload)
        dao.insertCompany(
            CompanyEntity(
                workspaceId, id, box.nonce, box.ciphertext,
                RecordAad.FORMAT_VERSION, RecordAad.KEY_VERSION,
            ),
        )
    }

    private suspend fun insertApp(
        workspaceId: String,
        key: ByteArray,
        id: String,
        companyId: String,
        name: String,
        note: String,
        website: String,
        iconId: String?,
        order: Int,
    ) {
        val now = System.currentTimeMillis()
        val payload = AppPayload(name, note, website, iconId, order, now, now)
        val box = encryptPayload(key, TYPE_APP, workspaceId, id, companyId, payload)
        dao.insertApp(
            AppEntity(
                workspaceId, id, companyId, box.nonce, box.ciphertext,
                RecordAad.FORMAT_VERSION, RecordAad.KEY_VERSION,
            ),
        )
    }

    private suspend fun createWorkspace(name: String): Pair<String, ByteArray> {
        val id = newId()
        val key = crypto.createVaultKey()
        val now = System.currentTimeMillis()
        val wrapped = crypto.wrapVaultKey(id, key)
        val encrypted = encryptPayload(key, TYPE_WORKSPACE, id, id, null, WorkspacePayload(name, now))
        dao.insertWorkspace(
            WorkspaceEntity(
                id, wrapped.nonce, wrapped.ciphertext, encrypted.nonce, encrypted.ciphertext,
                RecordAad.FORMAT_VERSION, RecordAad.KEY_VERSION,
            ),
        )
        upsertWorkspaceConfig(id, key, "BUILTIN", BuiltinGeneratorPresets.standard.id)
        return id to key
    }

    private suspend fun upsertWorkspaceConfig(
        workspaceId: String,
        key: ByteArray,
        kind: String,
        id: String,
    ) {
        val payload = WorkspaceConfigPayload(kind, id)
        val box = encryptPayload(key, TYPE_WORKSPACE_CONFIG, workspaceId, workspaceId, null, payload)
        dao.upsertWorkspaceConfig(
            WorkspaceConfigEntity(
                workspaceId, box.nonce, box.ciphertext,
                RecordAad.FORMAT_VERSION, RecordAad.KEY_VERSION,
            ),
        )
    }

    private suspend fun clearWorkspaceContent(workspaceId: String) {
        dao.deleteAllApps(workspaceId)
        dao.deleteAllCompanies(workspaceId)
        dao.deleteAllGeneratorPresets(workspaceId)
        dao.deleteAllCatalogPresets(workspaceId)
        dao.deleteAllBuiltinCatalogOverrides(workspaceId)
        dao.deleteWorkspaceConfig(workspaceId)
        dao.deleteAllIconAssets(workspaceId)
    }

    private suspend fun existingRecordCount(workspaceId: String): Int =
        dao.getCompanies(workspaceId).size + dao.getAllApps(workspaceId).size +
            dao.getAllAccounts(workspaceId).size + dao.getAllSecretItems(workspaceId).size

    private suspend fun nextCompanyOrder(workspaceId: String, key: ByteArray): Int =
        dao.getCompanies(workspaceId).maxOfOrNull { decryptCompany(it, key).order }?.plus(1) ?: 0

    private fun decryptWorkspace(entity: WorkspaceEntity): WorkspacePayload {
        val key = unwrapKey(entity)
        return try {
            decryptPayload(
                key, TYPE_WORKSPACE, entity.id, entity.id, null,
                entity.payloadNonce, entity.payloadCiphertext,
            )
        } finally {
            key.fill(0)
        }
    }

    private fun decryptCompany(entity: CompanyEntity, key: ByteArray): CompanyPayload = decryptPayload(
        key, TYPE_COMPANY, entity.workspaceId, entity.id, null,
        entity.payloadNonce, entity.payloadCiphertext,
    )

    private fun decryptApp(entity: AppEntity, key: ByteArray): AppPayload = decryptPayload(
        key, TYPE_APP, entity.workspaceId, entity.id, entity.companyId,
        entity.payloadNonce, entity.payloadCiphertext,
    )

    private fun unwrapKey(entity: WorkspaceEntity): ByteArray = crypto.unwrapVaultKey(
        entity.id, CryptoBox(entity.wrappedKeyNonce, entity.wrappedKeyCiphertext),
    )

    private inline fun <reified T> encryptPayload(
        key: ByteArray,
        type: String,
        workspaceId: String,
        recordId: String,
        parentId: String?,
        payload: T,
    ): CryptoBox = crypto.encryptRecord(
        key,
        json.encodeToString(payload).toByteArray(Charsets.UTF_8),
        RecordAad.encode(type, workspaceId, recordId, parentId),
    )

    private inline fun <reified T> decryptPayload(
        key: ByteArray,
        type: String,
        workspaceId: String,
        recordId: String,
        parentId: String?,
        nonce: ByteArray,
        ciphertext: ByteArray,
    ): T {
        val plaintext = crypto.decryptRecord(
            key, CryptoBox(nonce, ciphertext), RecordAad.encode(type, workspaceId, recordId, parentId),
        )
        return try {
            json.decodeFromString(plaintext.toString(Charsets.UTF_8))
        } finally {
            plaintext.fill(0)
        }
    }

    private inner class IconImporter(
        private val workspaceId: String,
        private val key: ByteArray,
        icons: List<BackupIconAsset>,
    ) {
        private val source = icons.associateBy { it.id }
        private val mapped = mutableMapOf<String, String>()

        suspend fun map(sourceId: String?): String? {
            if (sourceId == null) return null
            mapped[sourceId]?.let { return it }
            val backup = requireNotNull(source[sourceId]) { "图标引用无效" }
            val bytes = Base64.getDecoder().decode(backup.data)
            val id = newId()
            val plaintext = encodeIcon(IconDraft(bytes, backup.width, backup.height))
            try {
                val box = crypto.encryptRecord(
                    key, plaintext, RecordAad.encode(TYPE_ICON, workspaceId, id, null),
                )
                dao.insertIconAsset(
                    IconAssetEntity(
                        workspaceId, id, box.nonce, box.ciphertext,
                        RecordAad.FORMAT_VERSION, RecordAad.KEY_VERSION,
                    ),
                )
            } finally {
                plaintext.fill(0)
            }
            mapped[sourceId] = id
            return id
        }
    }

    private fun encodeIcon(icon: IconDraft): ByteArray = ByteArrayOutputStream().also { output ->
        DataOutputStream(output).use { data ->
            data.writeInt(ICON_PAYLOAD_MAGIC)
            data.writeInt(icon.width)
            data.writeInt(icon.height)
            data.writeUTF("image/png")
            data.write(MessageDigest.getInstance("SHA-256").digest(icon.pngBytes))
            data.writeInt(icon.pngBytes.size)
            data.write(icon.pngBytes)
        }
    }.toByteArray()

    private fun uniqueImportedName(original: String, used: MutableSet<String>, maxCodePoints: Int): String {
        if (used.add(original)) return original
        var index = 1
        while (true) {
            val suffix = if (index == 1) "（导入）" else "（导入 $index）"
            val allowed = maxCodePoints - suffix.codePointCount(0, suffix.length)
            val prefixEnd = original.offsetByCodePoints(0, minOf(allowed, original.codePointCount(0, original.length)))
            val candidate = original.substring(0, prefixEnd) + suffix
            if (used.add(candidate)) return candidate
            index++
        }
    }

    private val BackupWorkspaceData.recordCount: Int
        get() = companies.size + apps.size + accounts.size + secretItems.size

    private fun newId(): String = UUID.randomUUID().toString()

    companion object {
        private const val MAX_RECORDS = 10_000
        private const val TYPE_WORKSPACE = "workspace"
        private const val TYPE_COMPANY = "company"
        private const val TYPE_APP = "app"
        private const val TYPE_ACCOUNT = "account"
        private const val TYPE_SECRET = "secret-item"
        private const val TYPE_ICON = "icon-asset"
        private const val TYPE_GENERATOR_PRESET = "generator-preset"
        private const val TYPE_CATALOG_PRESET = "catalog-preset"
        private const val TYPE_BUILTIN_OVERRIDE = "builtin-catalog-override"
        private const val TYPE_WORKSPACE_CONFIG = "workspace-config"
        private const val ICON_PAYLOAD_MAGIC = 0x4B424931
    }
}
