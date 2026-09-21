package com.github.caiheyu.keybook.data

import androidx.room.withTransaction
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
import com.github.caiheyu.keybook.data.db.KeyBookDatabase
import com.github.caiheyu.keybook.data.db.IconAssetEntity
import com.github.caiheyu.keybook.data.db.SecretItemEntity
import com.github.caiheyu.keybook.data.db.VaultDao
import com.github.caiheyu.keybook.data.db.WorkspaceConfigEntity
import com.github.caiheyu.keybook.data.db.WorkspaceEntity
import com.github.caiheyu.keybook.data.catalog.BuiltinCatalogRepository
import com.github.caiheyu.keybook.data.catalog.CatalogType
import com.github.caiheyu.keybook.data.model.Account
import com.github.caiheyu.keybook.data.model.AccountDeletionCounts
import com.github.caiheyu.keybook.data.model.AccountDraft
import com.github.caiheyu.keybook.data.model.AccountPayload
import com.github.caiheyu.keybook.data.model.AccountSummary
import com.github.caiheyu.keybook.data.model.AppDraft
import com.github.caiheyu.keybook.data.model.AppDeletionCounts
import com.github.caiheyu.keybook.data.model.AppEntry
import com.github.caiheyu.keybook.data.model.AppPayload
import com.github.caiheyu.keybook.data.model.BuiltinCatalogOverride
import com.github.caiheyu.keybook.data.model.BuiltinCatalogOverridePayload
import com.github.caiheyu.keybook.data.model.CatalogPreset
import com.github.caiheyu.keybook.data.model.CatalogPresetDraft
import com.github.caiheyu.keybook.data.model.CatalogPresetPayload
import com.github.caiheyu.keybook.data.model.Company
import com.github.caiheyu.keybook.data.model.CompanyDraft
import com.github.caiheyu.keybook.data.model.CompanyPayload
import com.github.caiheyu.keybook.data.model.IconAsset
import com.github.caiheyu.keybook.data.model.IconDraft
import com.github.caiheyu.keybook.data.model.GeneratorPresetDraft
import com.github.caiheyu.keybook.data.model.GeneratorPresetPayload
import com.github.caiheyu.keybook.data.model.GeneratorSelection
import com.github.caiheyu.keybook.data.model.GeneratorSelectionKind
import com.github.caiheyu.keybook.data.model.SearchResult
import com.github.caiheyu.keybook.data.model.SearchResultType
import com.github.caiheyu.keybook.data.model.SecretItem
import com.github.caiheyu.keybook.data.model.SecretItemDraft
import com.github.caiheyu.keybook.data.model.SecretItemPayload
import com.github.caiheyu.keybook.data.model.Workspace
import com.github.caiheyu.keybook.data.model.WorkspacePayload
import com.github.caiheyu.keybook.data.model.WorkspaceRecordCounts
import com.github.caiheyu.keybook.data.model.WorkspaceConfigPayload
import com.github.caiheyu.keybook.data.model.StoredGeneratorPreset
import com.github.caiheyu.keybook.feature.generator.BuiltinGeneratorPresets
import com.github.caiheyu.keybook.feature.generator.PasswordGenerator
import com.github.caiheyu.keybook.feature.generator.PasswordRules

@Singleton
class VaultRepository @Inject constructor(
    private val database: KeyBookDatabase,
    private val dao: VaultDao,
    private val crypto: VaultCrypto,
    private val json: Json,
    private val catalogRepository: BuiltinCatalogRepository,
) {
    private val mutationMutex = Mutex()

    suspend fun <T> withConsistentSnapshot(block: suspend () -> T): T = mutationMutex.withLock {
        database.withTransaction { block() }
    }

    suspend fun initialize(): String = mutationMutex.withLock {
        val existing = dao.getWorkspaces()
        if (existing.isNotEmpty()) return@withLock existing.first().id
        createWorkspaceLocked("个人").id
    }

    suspend fun listWorkspaces(): List<Workspace> = dao.getWorkspaces().map(::decryptWorkspace)

    suspend fun getWorkspaceRecordCounts(workspaceId: String): WorkspaceRecordCounts =
        WorkspaceRecordCounts(
            companyCount = dao.companyCount(workspaceId),
            accountCount = dao.workspaceAccountCount(workspaceId),
        )

    suspend fun createWorkspace(name: String): Workspace = mutationMutex.withLock {
        val normalized = InputValidation.normalizeName(name, 40)
        require(listWorkspaces().none { it.name == normalized }) { "工作空间名称不能重复" }
        createWorkspaceLocked(normalized)
    }

    suspend fun renameWorkspace(workspaceId: String, name: String): Workspace = mutationMutex.withLock {
        val normalized = InputValidation.normalizeName(name, 40)
        require(listWorkspaces().none { it.id != workspaceId && it.name == normalized }) {
            "工作空间名称不能重复"
        }
        val entity = requireWorkspaceEntity(workspaceId)
        val old = decryptWorkspace(entity)
        val key = unwrapKey(entity)
        try {
            val payload = WorkspacePayload(normalized, old.createdAt)
            val box = encryptPayload(key, TYPE_WORKSPACE, workspaceId, workspaceId, null, payload)
            dao.updateWorkspace(entity.copy(payloadNonce = box.nonce, payloadCiphertext = box.ciphertext))
            Workspace(workspaceId, normalized, old.createdAt)
        } finally {
            key.fill(0)
        }
    }

    suspend fun deleteWorkspace(workspaceId: String) = mutationMutex.withLock {
        database.withTransaction {
            require(dao.workspaceCount() > 1) { "至少保留一个工作空间" }
            require(dao.companyCount(workspaceId) == 0) { "空间内还有企业，请先处理实际记录" }
            dao.deleteWorkspace(requireWorkspaceEntity(workspaceId))
        }
    }

    suspend fun listCompanies(workspaceId: String): List<Company> = withVaultKey(workspaceId) { key ->
        dao.getCompanies(workspaceId).map { decryptCompany(it, key) }.sortedBy { it.order }
    }

    suspend fun getCompany(workspaceId: String, id: String): Company? = withVaultKey(workspaceId) { key ->
        dao.getCompany(workspaceId, id)?.let { decryptCompany(it, key) }
    }

    suspend fun moveCompanyBy(workspaceId: String, id: String, offset: Int) = mutationMutex.withLock {
        require(offset != 0)
        withVaultKey(workspaceId) { key ->
            database.withTransaction {
                val ordered = dao.getCompanies(workspaceId)
                    .map { it to decryptCompany(it, key) }
                    .sortedBy { it.second.order }
                val index = ordered.indexOfFirst { it.first.id == id }
                if (index < 0) return@withTransaction
                val targetIndex = (index + offset).coerceIn(ordered.indices)
                if (targetIndex == index) return@withTransaction
                val reordered = ordered.toMutableList().apply { add(targetIndex, removeAt(index)) }
                val now = System.currentTimeMillis()
                reordered.forEachIndexed { newOrder, item ->
                    if (item.second.order != newOrder) {
                        dao.updateCompany(item.first.withPayload(key, item.second.toPayload(newOrder, now)))
                    }
                }
            }
        }
    }

    suspend fun saveCompany(workspaceId: String, id: String?, draft: CompanyDraft): Company =
        mutationMutex.withLock {
            val clean = draft.validate()
            withVaultKey(workspaceId) { key ->
                database.withTransaction {
                    val oldEntity = id?.let { dao.getCompany(workspaceId, it) }
                    require(id == null || oldEntity != null) { "企业不存在" }
                    if (oldEntity == null) requireRecordCapacity(workspaceId)
                    val old = oldEntity?.let { decryptCompany(it, key) }
                    val now = System.currentTimeMillis()
                    val recordId = id ?: UUID.randomUUID().toString()
                    val iconAssetId = resolveIcon(workspaceId, key, clean.newIcon, clean.removeIcon, clean.iconAssetId, old?.iconAssetId)
                    val payload = CompanyPayload(
                        name = clean.name,
                        note = clean.note,
                        websiteUrl = clean.websiteUrl,
                        iconAssetId = iconAssetId,
                        order = old?.order ?: nextCompanyOrder(workspaceId, key),
                        createdAt = old?.createdAt ?: now,
                        updatedAt = now,
                    )
                    val box = encryptPayload(key, TYPE_COMPANY, workspaceId, recordId, null, payload)
                    val entity = CompanyEntity.encrypted(workspaceId, recordId, box)
                    if (oldEntity == null) dao.insertCompany(entity) else dao.updateCompany(entity)
                    if (old?.iconAssetId != iconAssetId) {
                        deleteIconIfUnreferenced(workspaceId, old?.iconAssetId, key)
                    }
                    payload.toDomain(workspaceId, recordId)
                }
            }
        }

    suspend fun deleteCompany(workspaceId: String, id: String) = mutationMutex.withLock {
        withVaultKey(workspaceId) { key ->
            database.withTransaction {
                require(dao.appCount(workspaceId, id) == 0) {
                    "企业下还有应用，请先删除或移走应用"
                }
                val entity = dao.getCompany(workspaceId, id) ?: return@withTransaction
                val iconAssetId = decryptCompany(entity, key).iconAssetId
                dao.deleteCompany(entity)
                deleteIconIfUnreferenced(workspaceId, iconAssetId, key)
            }
        }
    }

    suspend fun listApps(workspaceId: String, companyId: String): List<AppEntry> =
        withVaultKey(workspaceId) { key ->
            dao.getApps(workspaceId, companyId).map { decryptApp(it, key) }.sortedBy { it.order }
        }

    suspend fun getApp(workspaceId: String, id: String): AppEntry? = withVaultKey(workspaceId) { key ->
        dao.getApp(workspaceId, id)?.let { decryptApp(it, key) }
    }

    suspend fun listAllApps(workspaceId: String): List<AppEntry> = withVaultKey(workspaceId) { key ->
        dao.getAllApps(workspaceId).map { decryptApp(it, key) }.sortedBy { it.order }
    }

    suspend fun moveAppBy(workspaceId: String, id: String, offset: Int) = mutationMutex.withLock {
        require(offset != 0)
        withVaultKey(workspaceId) { key ->
            database.withTransaction {
                val entity = dao.getApp(workspaceId, id) ?: return@withTransaction
                val ordered = dao.getApps(workspaceId, entity.companyId)
                    .map { it to decryptApp(it, key) }
                    .sortedBy { it.second.order }
                val index = ordered.indexOfFirst { it.first.id == id }
                if (index < 0) return@withTransaction
                val targetIndex = (index + offset).coerceIn(ordered.indices)
                if (targetIndex == index) return@withTransaction
                val reordered = ordered.toMutableList().apply { add(targetIndex, removeAt(index)) }
                val now = System.currentTimeMillis()
                reordered.forEachIndexed { newOrder, item ->
                    if (item.second.order != newOrder) {
                        dao.updateApp(item.first.withPayload(key, item.second.toPayload(newOrder, now)))
                    }
                }
            }
        }
    }

    suspend fun moveApp(workspaceId: String, id: String, targetCompanyId: String) = mutationMutex.withLock {
        withVaultKey(workspaceId) { key ->
            database.withTransaction {
                require(dao.getCompany(workspaceId, targetCompanyId) != null) { "目标企业不存在" }
                val entity = dao.getApp(workspaceId, id) ?: throw IllegalArgumentException("应用不存在")
                if (entity.companyId == targetCompanyId) return@withTransaction
                val payload = decryptApp(entity, key).toPayload(
                    order = nextAppOrder(workspaceId, targetCompanyId, key),
                    updatedAt = System.currentTimeMillis(),
                )
                val box = encryptPayload(key, TYPE_APP, workspaceId, id, targetCompanyId, payload)
                dao.updateApp(AppEntity.encrypted(workspaceId, id, targetCompanyId, box))
            }
        }
    }

    suspend fun saveApp(
        workspaceId: String,
        companyId: String,
        id: String?,
        draft: AppDraft,
    ): AppEntry = mutationMutex.withLock {
        val clean = draft.validate()
        withVaultKey(workspaceId) { key ->
            database.withTransaction {
                require(dao.getCompany(workspaceId, companyId) != null) { "企业不存在" }
                val oldEntity = id?.let { dao.getApp(workspaceId, it) }
                require(id == null || oldEntity != null) { "应用不存在" }
                require(oldEntity == null || oldEntity.companyId == companyId) { "应用父级不匹配" }
                if (oldEntity == null) requireRecordCapacity(workspaceId)
                val old = oldEntity?.let { decryptApp(it, key) }
                val now = System.currentTimeMillis()
                val recordId = id ?: UUID.randomUUID().toString()
                val iconAssetId = resolveIcon(workspaceId, key, clean.newIcon, clean.removeIcon, clean.iconAssetId, old?.iconAssetId)
                val payload = AppPayload(
                    name = clean.name,
                    note = clean.note,
                    websiteUrl = clean.websiteUrl,
                    iconAssetId = iconAssetId,
                    order = old?.order ?: nextAppOrder(workspaceId, companyId, key),
                    createdAt = old?.createdAt ?: now,
                    updatedAt = now,
                )
                val box = encryptPayload(key, TYPE_APP, workspaceId, recordId, companyId, payload)
                val entity = AppEntity.encrypted(workspaceId, recordId, companyId, box)
                if (oldEntity == null) dao.insertApp(entity) else dao.updateApp(entity)
                if (old?.iconAssetId != iconAssetId) {
                    deleteIconIfUnreferenced(workspaceId, old?.iconAssetId, key)
                }
                payload.toDomain(workspaceId, recordId, companyId)
            }
        }
    }

    suspend fun getIconAsset(workspaceId: String, id: String): IconAsset? = withVaultKey(workspaceId) { key ->
        val entity = dao.getIconAsset(workspaceId, id) ?: return@withVaultKey null
        val plaintext = crypto.decryptRecord(
            key,
            CryptoBox(entity.payloadNonce, entity.payloadCiphertext),
            RecordAad.encode(TYPE_ICON, workspaceId, id, null),
        )
        try {
            decodeIconAsset(workspaceId, id, plaintext)
        } finally {
            plaintext.fill(0)
        }
    }

    suspend fun getAppDeletionCounts(workspaceId: String, id: String): AppDeletionCounts =
        database.withTransaction {
            require(dao.getApp(workspaceId, id) != null) { "应用不存在" }
            val accounts = dao.getAccounts(workspaceId, id)
            AppDeletionCounts(
                accountCount = accounts.size,
                secretItemCount = accounts.sumOf { dao.secretItemCount(workspaceId, it.id) },
            )
        }

    suspend fun deleteApp(
        workspaceId: String,
        id: String,
        expectedCounts: AppDeletionCounts? = null,
    ) = mutationMutex.withLock {
        withVaultKey(workspaceId) { key ->
            database.withTransaction {
                val entity = dao.getApp(workspaceId, id) ?: return@withTransaction
                if (expectedCounts != null) {
                    val accounts = dao.getAccounts(workspaceId, id)
                    val actual = AppDeletionCounts(
                        accountCount = accounts.size,
                        secretItemCount = accounts.sumOf { dao.secretItemCount(workspaceId, it.id) },
                    )
                    require(actual == expectedCounts) { "删除范围已发生变化，请重新确认" }
                }
                val iconAssetId = decryptApp(entity, key).iconAssetId
                dao.deleteApp(entity)
                deleteIconIfUnreferenced(workspaceId, iconAssetId, key)
            }
        }
    }

    suspend fun listAccounts(workspaceId: String, appId: String): List<AccountSummary> =
        withVaultKey(workspaceId) { key ->
            dao.getAccounts(workspaceId, appId)
                .map { entity ->
                    val account = decryptAccount(entity, key)
                    AccountSummary(
                        account.id,
                        account.workspaceId,
                        account.appId,
                        account.name,
                        account.login,
                        account.order,
                    )
                }
                .sortedBy { it.order }
        }

    suspend fun getAccount(workspaceId: String, id: String): Account? = withVaultKey(workspaceId) { key ->
        dao.getAccount(workspaceId, id)?.let { decryptAccount(it, key) }
    }

    suspend fun moveAccountBy(workspaceId: String, id: String, offset: Int) = mutationMutex.withLock {
        require(offset != 0)
        withVaultKey(workspaceId) { key ->
            database.withTransaction {
                val entity = dao.getAccount(workspaceId, id) ?: return@withTransaction
                val ordered = dao.getAccounts(workspaceId, entity.appId)
                    .map { it to decryptAccount(it, key) }
                    .sortedBy { it.second.order }
                val index = ordered.indexOfFirst { it.first.id == id }
                if (index < 0) return@withTransaction
                val targetIndex = (index + offset).coerceIn(ordered.indices)
                if (targetIndex == index) return@withTransaction
                val reordered = ordered.toMutableList().apply { add(targetIndex, removeAt(index)) }
                val now = System.currentTimeMillis()
                reordered.forEachIndexed { newOrder, item ->
                    if (item.second.order != newOrder) {
                        dao.updateAccount(item.first.withPayload(key, item.second.toPayload(newOrder, now)))
                    }
                }
            }
        }
    }

    suspend fun moveAccount(workspaceId: String, id: String, targetAppId: String) = mutationMutex.withLock {
        withVaultKey(workspaceId) { key ->
            database.withTransaction {
                require(dao.getApp(workspaceId, targetAppId) != null) { "目标应用不存在" }
                val entity = dao.getAccount(workspaceId, id) ?: throw IllegalArgumentException("账号不存在")
                if (entity.appId == targetAppId) return@withTransaction
                val payload = decryptAccount(entity, key).toPayload(
                    order = nextAccountOrder(workspaceId, targetAppId, key),
                    updatedAt = System.currentTimeMillis(),
                )
                val box = encryptPayload(key, TYPE_ACCOUNT, workspaceId, id, targetAppId, payload)
                dao.updateAccount(AccountEntity.encrypted(workspaceId, id, targetAppId, box))
            }
        }
    }

    suspend fun saveAccount(
        workspaceId: String,
        appId: String,
        id: String?,
        draft: AccountDraft,
    ): Account = mutationMutex.withLock {
        val clean = draft.validate()
        withVaultKey(workspaceId) { key ->
            database.withTransaction {
                require(dao.getApp(workspaceId, appId) != null) { "应用不存在" }
                val oldEntity = id?.let { dao.getAccount(workspaceId, it) }
                require(id == null || oldEntity != null) { "账号不存在" }
                require(oldEntity == null || oldEntity.appId == appId) { "账号父级不匹配" }
                if (oldEntity == null) requireRecordCapacity(workspaceId)
                val old = oldEntity?.let { decryptAccount(it, key) }
                val now = System.currentTimeMillis()
                val recordId = id ?: UUID.randomUUID().toString()
                val payload = AccountPayload(
                    name = clean.name,
                    login = clean.login,
                    password = clean.password,
                    note = clean.note,
                    order = old?.order ?: nextAccountOrder(workspaceId, appId, key),
                    createdAt = old?.createdAt ?: now,
                    updatedAt = now,
                )
                val box = encryptPayload(key, TYPE_ACCOUNT, workspaceId, recordId, appId, payload)
                val entity = AccountEntity.encrypted(workspaceId, recordId, appId, box)
                if (oldEntity == null) dao.insertAccount(entity) else dao.updateAccount(entity)
                payload.toDomain(workspaceId, recordId, appId)
            }
        }
    }

    suspend fun getAccountDeletionCounts(workspaceId: String, id: String): AccountDeletionCounts =
        database.withTransaction {
            require(dao.getAccount(workspaceId, id) != null) { "账号不存在" }
            AccountDeletionCounts(dao.secretItemCount(workspaceId, id))
        }

    suspend fun deleteAccount(
        workspaceId: String,
        id: String,
        expectedCounts: AccountDeletionCounts? = null,
    ) = mutationMutex.withLock {
        database.withTransaction {
            val account = dao.getAccount(workspaceId, id) ?: return@withTransaction
            if (expectedCounts != null) {
                val actual = AccountDeletionCounts(dao.secretItemCount(workspaceId, id))
                require(actual == expectedCounts) { "删除范围已发生变化，请重新确认" }
            }
            dao.deleteAccount(account)
        }
    }

    suspend fun listSecretItems(workspaceId: String, accountId: String): List<SecretItem> =
        withVaultKey(workspaceId) { key ->
            dao.getSecretItems(workspaceId, accountId)
                .map { decryptSecretItem(it, key) }
                .sortedBy { it.order }
        }

    suspend fun getSecretItem(workspaceId: String, id: String): SecretItem? =
        withVaultKey(workspaceId) { key ->
            dao.getSecretItem(workspaceId, id)?.let { decryptSecretItem(it, key) }
        }

    suspend fun moveSecretItemBy(workspaceId: String, id: String, offset: Int) = mutationMutex.withLock {
        require(offset != 0)
        withVaultKey(workspaceId) { key ->
            database.withTransaction {
                val entity = dao.getSecretItem(workspaceId, id) ?: return@withTransaction
                val ordered = dao.getSecretItems(workspaceId, entity.accountId)
                    .map { it to decryptSecretItem(it, key) }
                    .sortedBy { it.second.order }
                val index = ordered.indexOfFirst { it.first.id == id }
                if (index < 0) return@withTransaction
                val targetIndex = (index + offset).coerceIn(ordered.indices)
                if (targetIndex == index) return@withTransaction
                val reordered = ordered.toMutableList().apply { add(targetIndex, removeAt(index)) }
                val now = System.currentTimeMillis()
                reordered.forEachIndexed { newOrder, item ->
                    if (item.second.order != newOrder) {
                        dao.updateSecretItem(item.first.withPayload(key, item.second.toPayload(newOrder, now)))
                    }
                }
            }
        }
    }

    suspend fun saveSecretItem(
        workspaceId: String,
        accountId: String,
        id: String?,
        draft: SecretItemDraft,
    ): SecretItem = mutationMutex.withLock {
        val clean = draft.validate()
        withVaultKey(workspaceId) { key ->
            database.withTransaction {
                require(dao.getAccount(workspaceId, accountId) != null) { "账号不存在" }
                val oldEntity = id?.let { dao.getSecretItem(workspaceId, it) }
                require(id == null || oldEntity != null) { "附加密码项不存在" }
                require(oldEntity == null || oldEntity.accountId == accountId) { "附加项父级不匹配" }
                if (oldEntity == null) requireRecordCapacity(workspaceId)
                val old = oldEntity?.let { decryptSecretItem(it, key) }
                val now = System.currentTimeMillis()
                val recordId = id ?: UUID.randomUUID().toString()
                val payload = SecretItemPayload(
                    name = clean.name,
                    password = clean.password,
                    note = clean.note,
                    order = old?.order ?: nextSecretOrder(workspaceId, accountId, key),
                    createdAt = old?.createdAt ?: now,
                    updatedAt = now,
                )
                val box = encryptPayload(key, TYPE_SECRET, workspaceId, recordId, accountId, payload)
                val entity = SecretItemEntity.encrypted(workspaceId, recordId, accountId, box)
                if (oldEntity == null) dao.insertSecretItem(entity) else dao.updateSecretItem(entity)
                payload.toDomain(workspaceId, recordId, accountId)
            }
        }
    }

    suspend fun deleteSecretItem(workspaceId: String, id: String) = mutationMutex.withLock {
        database.withTransaction {
            dao.getSecretItem(workspaceId, id)?.let { dao.deleteSecretItem(it) }
        }
    }

    suspend fun search(workspaceId: String, query: String): List<SearchResult> {
        if (query.isEmpty()) return emptyList()
        return withVaultKey(workspaceId) { key ->
            val companies = dao.getCompanies(workspaceId).associate { it.id to decryptCompany(it, key) }
            val apps = dao.getAllApps(workspaceId).associate { it.id to decryptApp(it, key) }
            val accounts = dao.getAllAccounts(workspaceId).associate { entity ->
                val account = decryptAccount(entity, key)
                account.id to AccountSearchEntry(account.id, account.appId, account.name, account.login)
            }
            val results = mutableListOf<SearchResult>()
            companies.values.filter { it.name.contains(query, ignoreCase = true) }.forEach {
                results += SearchResult(SearchResultType.COMPANY, it.id, it.name, "企业", companyId = it.id)
            }
            apps.values.filter { it.name.contains(query, ignoreCase = true) }.forEach {
                val company = companies[it.companyId] ?: return@forEach
                results += SearchResult(
                    SearchResultType.APP,
                    it.id,
                    it.name,
                    company.name,
                    companyId = company.id,
                    appId = it.id,
                )
            }
            accounts.values.filter {
                it.name.contains(query, ignoreCase = true) || it.login.contains(query, ignoreCase = true)
            }.forEach {
                val app = apps[it.appId] ?: return@forEach
                val company = companies[app.companyId] ?: return@forEach
                results += SearchResult(
                    SearchResultType.ACCOUNT,
                    it.id,
                    it.name,
                    "${company.name} / ${app.name} / ${it.login}",
                    company.id,
                    app.id,
                    it.id,
                )
            }
            dao.getAllSecretItems(workspaceId).map { decryptSecretItem(it, key) }
                .filter { it.name.contains(query, ignoreCase = true) }
                .forEach {
                    val account = accounts[it.accountId] ?: return@forEach
                    val app = apps[account.appId] ?: return@forEach
                    val company = companies[app.companyId] ?: return@forEach
                    results += SearchResult(
                        SearchResultType.SECRET_ITEM,
                        it.id,
                        it.name,
                        "${company.name} / ${app.name} / ${account.name}",
                        company.id,
                        app.id,
                        account.id,
                    )
                }
            results
        }
    }

    suspend fun listGeneratorPresets(workspaceId: String): List<StoredGeneratorPreset> =
        withVaultKey(workspaceId) { key ->
            dao.getGeneratorPresets(workspaceId)
                .map { decryptGeneratorPreset(it, key) }
                .sortedBy { it.createdAt }
        }

    suspend fun getDefaultGenerator(workspaceId: String): GeneratorSelection =
        withVaultKey(workspaceId) { key ->
            val config = dao.getWorkspaceConfig(workspaceId)
                ?: return@withVaultKey defaultGeneratorSelection()
            decryptWorkspaceConfig(config, key).toSelection()
        }

    suspend fun saveGeneratorPreset(
        workspaceId: String,
        id: String?,
        draft: GeneratorPresetDraft,
    ): StoredGeneratorPreset = mutationMutex.withLock {
        val name = InputValidation.normalizeName(draft.name, 40)
        PasswordGenerator().validate(draft.rules)
        withVaultKey(workspaceId) { key ->
            database.withTransaction {
                val existing = id?.let { dao.getGeneratorPreset(workspaceId, it) }
                require(id == null || existing != null) { "生成器预设不存在" }
                val normalizedName = name.lowercase(Locale.ROOT)
                val reservedNames = BuiltinGeneratorPresets.all.map { it.name.lowercase(Locale.ROOT) }
                require(normalizedName !in reservedNames) { "预设名称不能与内置项重复" }
                val presets = dao.getGeneratorPresets(workspaceId).map { entity ->
                    entity.id to decryptGeneratorPreset(entity, key)
                }
                require(presets.none { (otherId, preset) ->
                    otherId != id && preset.name.lowercase(Locale.ROOT) == normalizedName
                }) { "预设名称不能重复" }
                require(existing != null || presets.size < MAX_GENERATOR_PRESETS) { "最多保存 100 个自定义预设" }
                val now = System.currentTimeMillis()
                val recordId = id ?: UUID.randomUUID().toString()
                val old = existing?.let { decryptGeneratorPreset(it, key) }
                val createdAt = old?.createdAt ?: maxOf(
                    now,
                    (presets.maxOfOrNull { (_, preset) -> preset.createdAt } ?: (now - 1)) + 1,
                )
                val payload = draft.rules.toPayload(name, createdAt, maxOf(now, createdAt))
                val box = encryptPayload(key, TYPE_GENERATOR_PRESET, workspaceId, recordId, null, payload)
                val entity = GeneratorPresetEntity.encrypted(workspaceId, recordId, box)
                if (existing == null) dao.insertGeneratorPreset(entity) else dao.updateGeneratorPreset(entity)
                payload.toDomain(workspaceId, recordId)
            }
        }
    }

    suspend fun deleteGeneratorPreset(workspaceId: String, id: String) = mutationMutex.withLock {
        withVaultKey(workspaceId) { key ->
            database.withTransaction {
                val entity = dao.getGeneratorPreset(workspaceId, id) ?: return@withTransaction
                dao.deleteGeneratorPreset(entity)
                val config = dao.getWorkspaceConfig(workspaceId)?.let { decryptWorkspaceConfig(it, key) }
                if (config?.defaultGeneratorKind == GeneratorSelectionKind.CUSTOM.name &&
                    config.defaultGeneratorId == id
                ) {
                    dao.upsertWorkspaceConfig(defaultWorkspaceConfigEntity(workspaceId, key))
                }
            }
        }
    }

    suspend fun setDefaultGenerator(workspaceId: String, selection: GeneratorSelection) =
        mutationMutex.withLock {
            withVaultKey(workspaceId) { key ->
                when (selection.kind) {
                    GeneratorSelectionKind.BUILTIN -> require(
                        BuiltinGeneratorPresets.all.any { it.id == selection.id },
                    ) { "内置生成器预设不存在" }
                    GeneratorSelectionKind.CUSTOM -> require(
                        dao.getGeneratorPreset(workspaceId, selection.id) != null,
                    ) { "自定义生成器预设不存在" }
                }
                dao.upsertWorkspaceConfig(workspaceConfigEntity(workspaceId, key, selection))
            }
        }

    suspend fun listCatalogPresets(workspaceId: String): List<CatalogPreset> =
        withVaultKey(workspaceId) { key ->
            dao.getCatalogPresets(workspaceId)
                .map { decryptCatalogPreset(it, key) }
                .sortedWith(compareBy<CatalogPreset> { it.name }.thenBy { it.createdAt })
        }

    suspend fun saveCatalogPreset(
        workspaceId: String,
        id: String?,
        draft: CatalogPresetDraft,
    ): CatalogPreset = mutationMutex.withLock {
        val clean = draft.validate()
        withVaultKey(workspaceId) { key ->
            database.withTransaction {
                val oldEntity = id?.let { dao.getCatalogPreset(workspaceId, it) }
                require(id == null || oldEntity != null) { "预定义项不存在" }
                require(oldEntity != null || dao.getCatalogPresets(workspaceId).size < MAX_CATALOG_PRESETS) {
                    "最多保存 1000 个自定义预定义项"
                }
                val old = oldEntity?.let { decryptCatalogPreset(it, key) }
                require(old == null || old.type == clean.type) { "预定义项类型不能修改" }
                val now = System.currentTimeMillis()
                val recordId = id ?: UUID.randomUUID().toString()
                val iconAssetId = resolveIcon(
                    workspaceId, key, clean.newIcon, clean.removeIcon, clean.iconAssetId, old?.iconAssetId,
                )
                val payload = CatalogPresetPayload(
                    clean.type.fileValue,
                    clean.name,
                    clean.note,
                    clean.websiteUrl,
                    iconAssetId,
                    old?.createdAt ?: now,
                    now,
                )
                val box = encryptPayload(key, TYPE_CATALOG_PRESET, workspaceId, recordId, null, payload)
                val entity = CatalogPresetEntity.encrypted(workspaceId, recordId, box)
                if (oldEntity == null) dao.insertCatalogPreset(entity) else dao.updateCatalogPreset(entity)
                if (old?.iconAssetId != iconAssetId) {
                    deleteIconIfUnreferenced(workspaceId, old?.iconAssetId, key)
                }
                payload.toDomain(workspaceId, recordId)
            }
        }
    }

    suspend fun deleteCatalogPreset(workspaceId: String, id: String) = mutationMutex.withLock {
        withVaultKey(workspaceId) { key ->
            database.withTransaction {
                val entity = dao.getCatalogPreset(workspaceId, id) ?: return@withTransaction
                val iconAssetId = decryptCatalogPreset(entity, key).iconAssetId
                dao.deleteCatalogPreset(entity)
                deleteIconIfUnreferenced(workspaceId, iconAssetId, key)
            }
        }
    }

    suspend fun listBuiltinCatalogOverrides(workspaceId: String): List<BuiltinCatalogOverride> =
        withVaultKey(workspaceId) { key ->
            val availableIds = catalogRepository.entries().asSequence().map { it.id }.toSet()
            dao.getBuiltinCatalogOverrides(workspaceId)
                .filter { it.builtinId in availableIds }
                .map { decryptBuiltinOverride(it, key) }
        }

    suspend fun saveBuiltinCatalogOverride(
        workspaceId: String,
        builtinId: String,
        draft: CatalogPresetDraft,
    ): BuiltinCatalogOverride = mutationMutex.withLock {
        val clean = draft.validate()
        val builtin = catalogRepository.entries().firstOrNull { it.id == builtinId }
            ?: throw IllegalArgumentException("内置预定义项不存在")
        require(builtin.type == clean.type) { "内置预定义项类型不匹配" }
        withVaultKey(workspaceId) { key ->
            database.withTransaction {
                val oldEntity = dao.getBuiltinCatalogOverride(workspaceId, builtinId)
                val old = oldEntity?.let { decryptBuiltinOverride(it, key) }
                val iconAssetId = resolveIcon(
                    workspaceId, key, clean.newIcon, clean.removeIcon, clean.iconAssetId, old?.iconAssetId,
                )
                val payload = BuiltinCatalogOverridePayload(
                    clean.type.fileValue,
                    clean.name,
                    clean.note,
                    clean.websiteUrl,
                    iconAssetId,
                    System.currentTimeMillis(),
                )
                val box = encryptPayload(
                    key, TYPE_BUILTIN_OVERRIDE, workspaceId, builtinId, null, payload,
                )
                dao.upsertBuiltinCatalogOverride(
                    BuiltinCatalogOverrideEntity.encrypted(workspaceId, builtinId, box),
                )
                if (old?.iconAssetId != iconAssetId) {
                    deleteIconIfUnreferenced(workspaceId, old?.iconAssetId, key)
                }
                payload.toDomain(workspaceId, builtinId)
            }
        }
    }

    suspend fun restoreBuiltinCatalogDefault(workspaceId: String, builtinId: String) =
        mutationMutex.withLock {
            withVaultKey(workspaceId) { key ->
                database.withTransaction {
                    dao.getBuiltinCatalogOverride(workspaceId, builtinId)?.let { entity ->
                        val iconAssetId = decryptBuiltinOverride(entity, key).iconAssetId
                        dao.deleteBuiltinCatalogOverride(entity)
                        deleteIconIfUnreferenced(workspaceId, iconAssetId, key)
                    }
                }
            }
        }

    private suspend fun createWorkspaceLocked(name: String): Workspace {
        val now = System.currentTimeMillis()
        val id = UUID.randomUUID().toString()
        val key = crypto.createVaultKey()
        try {
            val wrapped = crypto.wrapVaultKey(id, key)
            val payload = WorkspacePayload(name, now)
            val encrypted = encryptPayload(key, TYPE_WORKSPACE, id, id, null, payload)
            database.withTransaction {
                dao.insertWorkspace(WorkspaceEntity(
                    id = id,
                    wrappedKeyNonce = wrapped.nonce,
                    wrappedKeyCiphertext = wrapped.ciphertext,
                    payloadNonce = encrypted.nonce,
                    payloadCiphertext = encrypted.ciphertext,
                    formatVersion = RecordAad.FORMAT_VERSION,
                    keyVersion = RecordAad.KEY_VERSION,
                ))
                dao.upsertWorkspaceConfig(defaultWorkspaceConfigEntity(id, key))
            }
            return Workspace(id, name, now)
        } finally {
            key.fill(0)
        }
    }

    private suspend fun <T> withVaultKey(workspaceId: String, block: suspend (ByteArray) -> T): T {
        val key = unwrapKey(requireWorkspaceEntity(workspaceId))
        return try {
            block(key)
        } finally {
            key.fill(0)
        }
    }

    private suspend fun requireWorkspaceEntity(workspaceId: String): WorkspaceEntity =
        dao.getWorkspace(workspaceId) ?: throw IllegalArgumentException("工作空间不存在")

    private fun unwrapKey(entity: WorkspaceEntity): ByteArray = crypto.unwrapVaultKey(
        entity.id,
        CryptoBox(entity.wrappedKeyNonce, entity.wrappedKeyCiphertext),
    )

    private fun decryptWorkspace(entity: WorkspaceEntity): Workspace {
        val key = unwrapKey(entity)
        return try {
            val payload: WorkspacePayload = decryptPayload(
                key,
                TYPE_WORKSPACE,
                entity.id,
                entity.id,
                null,
                entity.payloadNonce,
                entity.payloadCiphertext,
            )
            Workspace(entity.id, payload.name, payload.createdAt)
        } finally {
            key.fill(0)
        }
    }

    private fun decryptCompany(entity: CompanyEntity, key: ByteArray): Company {
        val payload: CompanyPayload = decryptPayload(
            key, TYPE_COMPANY, entity.workspaceId, entity.id, null,
            entity.payloadNonce, entity.payloadCiphertext,
        )
        return payload.toDomain(entity.workspaceId, entity.id)
    }

    private fun decryptApp(entity: AppEntity, key: ByteArray): AppEntry {
        val payload: AppPayload = decryptPayload(
            key, TYPE_APP, entity.workspaceId, entity.id, entity.companyId,
            entity.payloadNonce, entity.payloadCiphertext,
        )
        return payload.toDomain(entity.workspaceId, entity.id, entity.companyId)
    }

    private fun decryptAccount(entity: AccountEntity, key: ByteArray): Account {
        val payload: AccountPayload = decryptPayload(
            key, TYPE_ACCOUNT, entity.workspaceId, entity.id, entity.appId,
            entity.payloadNonce, entity.payloadCiphertext,
        )
        return payload.toDomain(entity.workspaceId, entity.id, entity.appId)
    }

    private fun decryptSecretItem(entity: SecretItemEntity, key: ByteArray): SecretItem {
        val payload: SecretItemPayload = decryptPayload(
            key, TYPE_SECRET, entity.workspaceId, entity.id, entity.accountId,
            entity.payloadNonce, entity.payloadCiphertext,
        )
        return payload.toDomain(entity.workspaceId, entity.id, entity.accountId)
    }

    private fun decryptGeneratorPreset(
        entity: GeneratorPresetEntity,
        key: ByteArray,
    ): StoredGeneratorPreset {
        val payload: GeneratorPresetPayload = decryptPayload(
            key, TYPE_GENERATOR_PRESET, entity.workspaceId, entity.id, null,
            entity.payloadNonce, entity.payloadCiphertext,
        )
        return payload.toDomain(entity.workspaceId, entity.id)
    }

    private fun decryptCatalogPreset(entity: CatalogPresetEntity, key: ByteArray): CatalogPreset {
        val payload: CatalogPresetPayload = decryptPayload(
            key, TYPE_CATALOG_PRESET, entity.workspaceId, entity.id, null,
            entity.payloadNonce, entity.payloadCiphertext,
        )
        return payload.toDomain(entity.workspaceId, entity.id)
    }

    private fun decryptBuiltinOverride(
        entity: BuiltinCatalogOverrideEntity,
        key: ByteArray,
    ): BuiltinCatalogOverride {
        val payload: BuiltinCatalogOverridePayload = decryptPayload(
            key, TYPE_BUILTIN_OVERRIDE, entity.workspaceId, entity.builtinId, null,
            entity.payloadNonce, entity.payloadCiphertext,
        )
        return payload.toDomain(entity.workspaceId, entity.builtinId)
    }

    private fun decryptWorkspaceConfig(
        entity: WorkspaceConfigEntity,
        key: ByteArray,
    ): WorkspaceConfigPayload = decryptPayload(
        key, TYPE_WORKSPACE_CONFIG, entity.workspaceId, entity.workspaceId, null,
        entity.payloadNonce, entity.payloadCiphertext,
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
            key,
            CryptoBox(nonce, ciphertext),
            RecordAad.encode(type, workspaceId, recordId, parentId),
        )
        return try {
            json.decodeFromString(plaintext.toString(Charsets.UTF_8))
        } finally {
            plaintext.fill(0)
        }
    }

    private suspend fun nextCompanyOrder(workspaceId: String, key: ByteArray): Int =
        dao.getCompanies(workspaceId).maxOfOrNull { decryptCompany(it, key).order }?.plus(1) ?: 0

    private suspend fun nextAppOrder(workspaceId: String, companyId: String, key: ByteArray): Int =
        dao.getApps(workspaceId, companyId).maxOfOrNull { decryptApp(it, key).order }?.plus(1) ?: 0

    private suspend fun nextAccountOrder(workspaceId: String, appId: String, key: ByteArray): Int =
        dao.getAccounts(workspaceId, appId).maxOfOrNull { decryptAccount(it, key).order }?.plus(1) ?: 0

    private suspend fun nextSecretOrder(workspaceId: String, accountId: String, key: ByteArray): Int =
        dao.getSecretItems(workspaceId, accountId).maxOfOrNull { decryptSecretItem(it, key).order }?.plus(1) ?: 0

    private suspend fun requireRecordCapacity(workspaceId: String) {
        require(dao.recordCount(workspaceId) < MAX_RECORDS) {
            "企业、应用、账号和附加项合计最多保存 10000 条"
        }
    }

    private suspend fun deleteIconIfUnreferenced(
        workspaceId: String,
        iconAssetId: String?,
        key: ByteArray,
    ) {
        if (iconAssetId == null) return
        val referenced = dao.getCompanies(workspaceId).any {
            decryptCompany(it, key).iconAssetId == iconAssetId
        } || dao.getAllApps(workspaceId).any {
            decryptApp(it, key).iconAssetId == iconAssetId
        } || dao.getCatalogPresets(workspaceId).any {
            decryptCatalogPreset(it, key).iconAssetId == iconAssetId
        } || dao.getBuiltinCatalogOverrides(workspaceId).any {
            decryptBuiltinOverride(it, key).iconAssetId == iconAssetId
        }
        if (!referenced) dao.getIconAsset(workspaceId, iconAssetId)?.let { dao.deleteIconAsset(it) }
    }

    private suspend fun resolveIcon(
        workspaceId: String,
        key: ByteArray,
        newIcon: IconDraft?,
        removeIcon: Boolean,
        requestedId: String?,
        existingId: String?,
    ): String? = when {
        removeIcon -> null
        newIcon != null -> insertIconAsset(workspaceId, key, newIcon)
        requestedId != null -> requestedId.also {
            requireNotNull(dao.getIconAsset(workspaceId, it)) { "所选图标不存在" }
        }
        else -> existingId
    }

    private suspend fun insertIconAsset(workspaceId: String, key: ByteArray, icon: IconDraft): String {
        validateIcon(icon)
        val id = UUID.randomUUID().toString()
        val plaintext = encodeIconAsset(icon)
        try {
            val box = crypto.encryptRecord(
                key,
                plaintext,
                RecordAad.encode(TYPE_ICON, workspaceId, id, null),
            )
            dao.insertIconAsset(
                IconAssetEntity(
                    workspaceId,
                    id,
                    box.nonce,
                    box.ciphertext,
                    RecordAad.FORMAT_VERSION,
                    RecordAad.KEY_VERSION,
                ),
            )
            return id
        } finally {
            plaintext.fill(0)
        }
    }

    private fun validateIcon(icon: IconDraft) {
        require(icon.pngBytes.size in 1..MAX_ICON_BYTES) { "图标文件过大" }
        require(icon.width in 1..MAX_ICON_DIMENSION && icon.height in 1..MAX_ICON_DIMENSION) { "图标尺寸无效" }
        require(icon.pngBytes.size >= PNG_SIGNATURE.size &&
            icon.pngBytes.copyOfRange(0, PNG_SIGNATURE.size).contentEquals(PNG_SIGNATURE)) {
            "图标必须是已规范化的 PNG"
        }
    }

    private fun encodeIconAsset(icon: IconDraft): ByteArray = ByteArrayOutputStream().also { output ->
        DataOutputStream(output).use { data ->
            data.writeInt(ICON_PAYLOAD_MAGIC)
            data.writeInt(icon.width)
            data.writeInt(icon.height)
            data.writeUTF(ICON_MIME)
            data.write(MessageDigest.getInstance("SHA-256").digest(icon.pngBytes))
            data.writeInt(icon.pngBytes.size)
            data.write(icon.pngBytes)
        }
    }.toByteArray()

    private fun decodeIconAsset(workspaceId: String, id: String, plaintext: ByteArray): IconAsset {
        DataInputStream(ByteArrayInputStream(plaintext)).use { data ->
            require(data.readInt() == ICON_PAYLOAD_MAGIC) { "图标数据格式无效" }
            val width = data.readInt()
            val height = data.readInt()
            require(data.readUTF() == ICON_MIME) { "图标 MIME 无效" }
            val expectedHash = ByteArray(32).also(data::readFully)
            val size = data.readInt()
            require(size in 1..MAX_ICON_BYTES && data.available() == size) { "图标数据长度无效" }
            val bytes = ByteArray(size).also(data::readFully)
            require(MessageDigest.getInstance("SHA-256").digest(bytes).contentEquals(expectedHash)) {
                "图标数据已损坏"
            }
            validateIcon(IconDraft(bytes, width, height))
            return IconAsset(id, workspaceId, bytes, width, height)
        }
    }

    private fun CompanyEntity.withPayload(key: ByteArray, payload: CompanyPayload): CompanyEntity {
        val box = encryptPayload(key, TYPE_COMPANY, workspaceId, id, null, payload)
        return copy(payloadNonce = box.nonce, payloadCiphertext = box.ciphertext)
    }

    private fun AppEntity.withPayload(key: ByteArray, payload: AppPayload): AppEntity {
        val box = encryptPayload(key, TYPE_APP, workspaceId, id, companyId, payload)
        return copy(payloadNonce = box.nonce, payloadCiphertext = box.ciphertext)
    }

    private fun AccountEntity.withPayload(key: ByteArray, payload: AccountPayload): AccountEntity {
        val box = encryptPayload(key, TYPE_ACCOUNT, workspaceId, id, appId, payload)
        return copy(payloadNonce = box.nonce, payloadCiphertext = box.ciphertext)
    }

    private fun SecretItemEntity.withPayload(key: ByteArray, payload: SecretItemPayload): SecretItemEntity {
        val box = encryptPayload(key, TYPE_SECRET, workspaceId, id, accountId, payload)
        return copy(payloadNonce = box.nonce, payloadCiphertext = box.ciphertext)
    }

    private fun workspaceConfigEntity(
        workspaceId: String,
        key: ByteArray,
        selection: GeneratorSelection,
    ): WorkspaceConfigEntity {
        val payload = WorkspaceConfigPayload(selection.kind.name, selection.id)
        val box = encryptPayload(
            key, TYPE_WORKSPACE_CONFIG, workspaceId, workspaceId, null, payload,
        )
        return WorkspaceConfigEntity(
            workspaceId, box.nonce, box.ciphertext, RecordAad.FORMAT_VERSION, RecordAad.KEY_VERSION,
        )
    }

    private fun defaultWorkspaceConfigEntity(workspaceId: String, key: ByteArray): WorkspaceConfigEntity =
        workspaceConfigEntity(workspaceId, key, defaultGeneratorSelection())

    companion object {
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
        private const val ICON_MIME = "image/png"
        private const val MAX_ICON_BYTES = 256 * 1024
        private const val MAX_ICON_DIMENSION = 256
        private const val MAX_GENERATOR_PRESETS = 100
        private const val MAX_CATALOG_PRESETS = 1000
        private const val MAX_RECORDS = 10_000
        private val PNG_SIGNATURE = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
        )
    }
}

private fun defaultGeneratorSelection() = GeneratorSelection(
    GeneratorSelectionKind.BUILTIN,
    BuiltinGeneratorPresets.standard.id,
)

private data class AccountSearchEntry(
    val id: String,
    val appId: String,
    val name: String,
    val login: String,
)

private fun CompanyDraft.validate() = copy(
    name = InputValidation.normalizeName(name),
    note = InputValidation.normalizeNote(note),
    websiteUrl = InputValidation.validateWebsite(websiteUrl),
)

private fun AppDraft.validate() = copy(
    name = InputValidation.normalizeName(name),
    note = InputValidation.normalizeNote(note),
    websiteUrl = InputValidation.validateWebsite(websiteUrl),
)

private fun AccountDraft.validate(): AccountDraft {
    InputValidation.validateRaw(login, 1, 320, "登录账号")
    InputValidation.validateRaw(password, 1, 1024, "登录密码")
    return copy(
        name = InputValidation.normalizeName(name),
        note = InputValidation.normalizeNote(note),
    )
}

private fun SecretItemDraft.validate(): SecretItemDraft {
    InputValidation.validateRaw(password, 1, 1024, "密码")
    return copy(
        name = InputValidation.normalizeName(name),
        note = InputValidation.normalizeNote(note),
    )
}

private fun CatalogPresetDraft.validate() = copy(
    name = InputValidation.normalizeName(name),
    note = InputValidation.normalizeNote(note),
    websiteUrl = InputValidation.validateWebsite(websiteUrl),
)

private val CatalogType.fileValue: String
    get() = when (this) {
        CatalogType.COMPANY -> "company"
        CatalogType.APP -> "app"
    }

private fun String.toCatalogType(): CatalogType = when (this) {
    "company" -> CatalogType.COMPANY
    "app" -> CatalogType.APP
    else -> throw IllegalArgumentException("预定义项类型无效")
}

private fun PasswordRules.toPayload(name: String, createdAt: Long, updatedAt: Long) =
    GeneratorPresetPayload(
        name, length, lower, upper, digits, symbols, symbolChars,
        excludeAmbiguous, guaranteeEachCategory, createdAt, updatedAt,
    )

private fun GeneratorPresetPayload.toDomain(workspaceId: String, id: String) = StoredGeneratorPreset(
    id,
    workspaceId,
    name,
    PasswordRules(
        length, lower, upper, digits, symbols, symbolChars, excludeAmbiguous, guaranteeEachCategory,
    ),
    createdAt,
    updatedAt,
)

private fun CatalogPresetPayload.toDomain(workspaceId: String, id: String) = CatalogPreset(
    id, workspaceId, type.toCatalogType(), name, note, websiteUrl, iconAssetId, createdAt, updatedAt,
)

private fun BuiltinCatalogOverridePayload.toDomain(workspaceId: String, builtinId: String) =
    BuiltinCatalogOverride(
        workspaceId, builtinId, type.toCatalogType(), name, note, websiteUrl, iconAssetId, updatedAt,
    )

private fun WorkspaceConfigPayload.toSelection(): GeneratorSelection {
    val kind = runCatching { GeneratorSelectionKind.valueOf(defaultGeneratorKind) }
        .getOrElse { throw IllegalArgumentException("默认生成器配置无效") }
    return GeneratorSelection(kind, defaultGeneratorId)
}

private fun CompanyPayload.toDomain(workspaceId: String, id: String) =
    Company(id, workspaceId, name, note, websiteUrl, iconAssetId, order, createdAt, updatedAt)

private fun AppPayload.toDomain(workspaceId: String, id: String, companyId: String) =
    AppEntry(id, workspaceId, companyId, name, note, websiteUrl, iconAssetId, order, createdAt, updatedAt)

private fun AccountPayload.toDomain(workspaceId: String, id: String, appId: String) =
    Account(id, workspaceId, appId, name, login, password, note, order, createdAt, updatedAt)

private fun SecretItemPayload.toDomain(workspaceId: String, id: String, accountId: String) =
    SecretItem(id, workspaceId, accountId, name, password, note, order, createdAt, updatedAt)

private fun Company.toPayload(order: Int, updatedAt: Long) =
    CompanyPayload(name, note, websiteUrl, iconAssetId, order, createdAt, updatedAt)

private fun AppEntry.toPayload(order: Int, updatedAt: Long) =
    AppPayload(name, note, websiteUrl, iconAssetId, order, createdAt, updatedAt)

private fun Account.toPayload(order: Int, updatedAt: Long) =
    AccountPayload(name, login, password, note, order, createdAt, updatedAt)

private fun SecretItem.toPayload(order: Int, updatedAt: Long) =
    SecretItemPayload(name, password, note, order, createdAt, updatedAt)

private fun CompanyEntity.Companion.encrypted(
    workspaceId: String,
    id: String,
    box: CryptoBox,
) = CompanyEntity(
    workspaceId,
    id,
    box.nonce,
    box.ciphertext,
    RecordAad.FORMAT_VERSION,
    RecordAad.KEY_VERSION,
)

private fun AppEntity.Companion.encrypted(
    workspaceId: String,
    id: String,
    companyId: String,
    box: CryptoBox,
) = AppEntity(
    workspaceId,
    id,
    companyId,
    box.nonce,
    box.ciphertext,
    RecordAad.FORMAT_VERSION,
    RecordAad.KEY_VERSION,
)

private fun AccountEntity.Companion.encrypted(
    workspaceId: String,
    id: String,
    appId: String,
    box: CryptoBox,
) = AccountEntity(
    workspaceId,
    id,
    appId,
    box.nonce,
    box.ciphertext,
    RecordAad.FORMAT_VERSION,
    RecordAad.KEY_VERSION,
)

private fun SecretItemEntity.Companion.encrypted(
    workspaceId: String,
    id: String,
    accountId: String,
    box: CryptoBox,
) = SecretItemEntity(
    workspaceId,
    id,
    accountId,
    box.nonce,
    box.ciphertext,
    RecordAad.FORMAT_VERSION,
    RecordAad.KEY_VERSION,
)

private fun GeneratorPresetEntity.Companion.encrypted(
    workspaceId: String,
    id: String,
    box: CryptoBox,
) = GeneratorPresetEntity(
    workspaceId, id, box.nonce, box.ciphertext, RecordAad.FORMAT_VERSION, RecordAad.KEY_VERSION,
)

private fun CatalogPresetEntity.Companion.encrypted(
    workspaceId: String,
    id: String,
    box: CryptoBox,
) = CatalogPresetEntity(
    workspaceId, id, box.nonce, box.ciphertext, RecordAad.FORMAT_VERSION, RecordAad.KEY_VERSION,
)

private fun BuiltinCatalogOverrideEntity.Companion.encrypted(
    workspaceId: String,
    builtinId: String,
    box: CryptoBox,
) = BuiltinCatalogOverrideEntity(
    workspaceId, builtinId, box.nonce, box.ciphertext,
    RecordAad.FORMAT_VERSION, RecordAad.KEY_VERSION,
)
