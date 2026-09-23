package com.github.caiheyu.keybook.ui

import android.content.ContentResolver
import android.net.Uri
import android.content.Intent
import android.provider.DocumentsContract
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import com.github.caiheyu.keybook.data.VaultRepository
import com.github.caiheyu.keybook.data.catalog.BuiltinCatalogRepository
import com.github.caiheyu.keybook.data.catalog.CatalogType
import com.github.caiheyu.keybook.data.model.Account
import com.github.caiheyu.keybook.data.model.AccountDeletionCounts
import com.github.caiheyu.keybook.data.model.AccountDraft
import com.github.caiheyu.keybook.data.model.AccountSummary
import com.github.caiheyu.keybook.data.model.AppDraft
import com.github.caiheyu.keybook.data.model.AppEntry
import com.github.caiheyu.keybook.data.model.AppDeletionCounts
import com.github.caiheyu.keybook.data.model.Company
import com.github.caiheyu.keybook.data.model.CompanyDraft
import com.github.caiheyu.keybook.data.model.CatalogPresetDraft
import com.github.caiheyu.keybook.data.model.GeneratorPresetDraft
import com.github.caiheyu.keybook.data.model.GeneratorSelection
import com.github.caiheyu.keybook.data.model.IconAsset
import com.github.caiheyu.keybook.data.model.IconDraft
import com.github.caiheyu.keybook.data.model.SearchResult
import com.github.caiheyu.keybook.data.model.SecretItem
import com.github.caiheyu.keybook.data.model.SecretItemDraft
import com.github.caiheyu.keybook.data.model.StoredGeneratorPreset
import com.github.caiheyu.keybook.data.model.Workspace
import com.github.caiheyu.keybook.data.model.WorkspaceRecordCounts
import com.github.caiheyu.keybook.data.settings.SettingsRepository
import com.github.caiheyu.keybook.data.settings.ThemeMode
import com.github.caiheyu.keybook.feature.icon.IconNormalizer
import com.github.caiheyu.keybook.feature.icon.WebsiteIconCandidate
import com.github.caiheyu.keybook.feature.icon.WebsiteIconFetcher
import com.github.caiheyu.keybook.feature.transfer.BackupImportPlan
import com.github.caiheyu.keybook.feature.transfer.BackupPayload
import com.github.caiheyu.keybook.feature.transfer.BackupScope
import com.github.caiheyu.keybook.feature.transfer.BackupTransferRepository
import com.github.caiheyu.keybook.feature.update.UpdateCheckResult
import com.github.caiheyu.keybook.feature.update.UpdateManifest
import com.github.caiheyu.keybook.feature.update.UpdateRepository

data class CompanyRow(val company: Company, val appCount: Int)
data class AppRow(val app: AppEntry, val accountCount: Int)

enum class UpdatePhase { IDLE, CHECKING, UP_TO_DATE, AVAILABLE, INCOMPATIBLE, DOWNLOADING, FAILED, HANDED_TO_INSTALLER }

data class UpdateUiState(
    val phase: UpdatePhase = UpdatePhase.IDLE,
    val manifest: UpdateManifest? = null,
    val message: String? = null,
)

data class CatalogEntryUi(
    val id: String,
    val type: CatalogType,
    val name: String,
    val websiteUrl: String,
    val note: String,
    val iconPng: ByteArray?,
    val iconWidth: Int,
    val iconHeight: Int,
    val isBuiltin: Boolean,
    val isModified: Boolean,
)

internal fun isWorkspaceResultCurrent(currentWorkspaceId: String?, resultWorkspaceId: String): Boolean =
    currentWorkspaceId == resultWorkspaceId

data class KeyBookUiState(
    val initialized: Boolean = false,
    val fatalError: String? = null,
    val busy: Boolean = false,
    val workspaces: List<Workspace> = emptyList(),
    val workspaceRecordCounts: Map<String, WorkspaceRecordCounts> = emptyMap(),
    val currentWorkspaceId: String? = null,
    val companies: List<CompanyRow> = emptyList(),
    val apps: List<AppRow> = emptyList(),
    val allApps: List<AppEntry> = emptyList(),
    val accounts: List<AccountSummary> = emptyList(),
    val account: Account? = null,
    val secretItems: List<SecretItem> = emptyList(),
    val searchQuery: String = "",
    val searchResults: List<SearchResult> = emptyList(),
    val catalogEntries: List<CatalogEntryUi> = emptyList(),
    val generatorPresets: List<StoredGeneratorPreset> = emptyList(),
    val defaultGenerator: GeneratorSelection? = null,
    val icons: Map<String, IconAsset> = emptyMap(),
    val error: String? = null,
    val transferLocked: Boolean = false,
    val update: UpdateUiState = UpdateUiState(),
) {
    val currentWorkspace: Workspace?
        get() = workspaces.firstOrNull { it.id == currentWorkspaceId }
}

@HiltViewModel
class KeyBookViewModel @Inject constructor(
    private val repository: VaultRepository,
    private val settingsRepository: SettingsRepository,
    private val catalogRepository: BuiltinCatalogRepository,
    private val iconNormalizer: IconNormalizer,
    private val websiteIconFetcher: WebsiteIconFetcher,
    private val backupTransferRepository: BackupTransferRepository,
    private val updateRepository: UpdateRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(KeyBookUiState())
    val state: StateFlow<KeyBookUiState> = _state
    private var searchJob: Job? = null
    private var activeOperations = 0
    private val mutationMutex = Mutex()
    private val transferCommitMutex = Mutex()

    val themeMode: StateFlow<ThemeMode> = settingsRepository.themeMode.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        ThemeMode.FOLLOW_SYSTEM,
    )

    init {
        viewModelScope.launch {
            runOperation(initial = true) {
                val initialId = repository.initialize()
                val workspaces = repository.listWorkspaces()
                val preferred = settingsRepository.currentWorkspaceId.first()
                val selected = preferred?.takeIf { id -> workspaces.any { it.id == id } } ?: initialId
                settingsRepository.setCurrentWorkspaceId(selected)
                _state.update {
                    it.copy(
                        initialized = true,
                        workspaces = workspaces,
                        currentWorkspaceId = selected,
                    )
                }
                loadWorkspaceConfiguration(selected)
                loadCompaniesInternal(selected)
            }
        }
    }

    fun dismissError() = _state.update { it.copy(error = null) }

    fun reportError(message: String) = _state.update { it.copy(error = message) }

    fun releaseSensitiveDetails() = _state.update {
        it.copy(account = null, secretItems = emptyList())
    }

    fun checkForUpdate(): Job = viewModelScope.launch {
        _state.update { it.copy(update = UpdateUiState(UpdatePhase.CHECKING)) }
        try {
            when (val result = updateRepository.checkForUpdate()) {
                UpdateCheckResult.UpToDate -> _state.update {
                    it.copy(update = UpdateUiState(UpdatePhase.UP_TO_DATE, message = "已是最新版本"))
                }
                is UpdateCheckResult.Available -> _state.update {
                    it.copy(update = UpdateUiState(UpdatePhase.AVAILABLE, result.manifest))
                }
                is UpdateCheckResult.Incompatible -> _state.update {
                    it.copy(
                        update = UpdateUiState(
                            UpdatePhase.INCOMPATIBLE,
                            result.manifest,
                            "新版要求 Android API ${result.manifest.minSdk} 或更高版本",
                        ),
                    )
                }
            }
        } catch (error: CancellationException) {
            _state.update {
                it.copy(update = UpdateUiState(UpdatePhase.FAILED, message = "更新检查已取消"))
            }
            throw error
        } catch (error: Exception) {
            _state.update {
                it.copy(update = UpdateUiState(UpdatePhase.FAILED, message = error.message ?: "检查更新失败"))
            }
        }
    }

    fun downloadUpdate(onReady: (Intent) -> Unit): Job = viewModelScope.launch {
        val manifest = _state.value.update.manifest ?: return@launch
        _state.update { it.copy(update = UpdateUiState(UpdatePhase.DOWNLOADING, manifest)) }
        try {
            val intent = updateRepository.downloadAndCreateInstallIntent(manifest)
            onReady(intent)
            _state.update {
                it.copy(
                    update = UpdateUiState(
                        UpdatePhase.HANDED_TO_INSTALLER,
                        manifest,
                        "已交给 Android 系统安装器",
                    ),
                )
            }
        } catch (error: CancellationException) {
            _state.update {
                it.copy(update = UpdateUiState(UpdatePhase.FAILED, manifest, "更新下载已取消"))
            }
            throw error
        } catch (error: Exception) {
            _state.update {
                it.copy(update = UpdateUiState(UpdatePhase.FAILED, manifest, error.message ?: "下载或校验失败"))
            }
        }
    }

    fun setTheme(mode: ThemeMode) {
        viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    }

    fun refreshCompanies() = withCurrentWorkspace { workspaceId -> loadCompaniesInternal(workspaceId) }

    fun refreshWorkspaceRecordCounts() {
        val workspaces = _state.value.workspaces
        viewModelScope.launch {
            try {
                val counts = workspaces.associate { workspace ->
                    workspace.id to repository.getWorkspaceRecordCounts(workspace.id)
                }
                if (_state.value.workspaces.map { it.id }.toSet() == counts.keys) {
                    _state.update { it.copy(workspaceRecordCounts = counts) }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _state.update { it.copy(error = error.message ?: "无法读取工作空间统计") }
            }
        }
    }

    fun loadApps(companyId: String) = withCurrentWorkspace { workspaceId -> loadAppsInternal(workspaceId, companyId) }

    fun loadAllApps() = withCurrentWorkspace { workspaceId ->
        val apps = repository.listAllApps(workspaceId)
        loadIcons(workspaceId, apps.mapNotNull { it.iconAssetId })
        if (!isCurrentWorkspace(workspaceId)) return@withCurrentWorkspace
        _state.update { it.copy(allApps = apps) }
    }

    fun loadAccounts(appId: String) = withCurrentWorkspace { workspaceId -> loadAccountsInternal(workspaceId, appId) }

    fun loadAccount(accountId: String) = withCurrentWorkspace { workspaceId ->
        val account = repository.getAccount(workspaceId, accountId)
        val secretItems = repository.listSecretItems(workspaceId, accountId)
        if (!isCurrentWorkspace(workspaceId)) return@withCurrentWorkspace
        _state.update {
            it.copy(
                account = account,
                secretItems = secretItems,
            )
        }
    }

    fun search(query: String) {
        searchJob?.cancel()
        _state.update {
            it.copy(
                searchQuery = query,
                searchResults = if (query.isBlank()) emptyList() else it.searchResults,
            )
        }
        if (query.isBlank()) return
        val workspaceId = _state.value.currentWorkspaceId ?: return
        searchJob = viewModelScope.launch {
            try {
                val results = repository.search(workspaceId, query)
                if (_state.value.currentWorkspaceId == workspaceId && _state.value.searchQuery == query) {
                    _state.update { it.copy(searchResults = results) }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (_state.value.currentWorkspaceId == workspaceId && _state.value.searchQuery == query) {
                    _state.update { it.copy(error = error.message ?: "搜索失败") }
                }
            }
        }
    }

    fun clearSearch() {
        searchJob?.cancel()
        _state.update { it.copy(searchQuery = "", searchResults = emptyList()) }
    }

    fun setTransferLocked(locked: Boolean) = _state.update { it.copy(transferLocked = locked) }

    fun normalizeIcon(contentResolver: ContentResolver, uri: Uri, onComplete: (IconDraft) -> Unit): Job =
        viewModelScope.launch {
            runOperation {
                val icon = withContext(Dispatchers.IO) {
                    iconNormalizer.fromUri(contentResolver, uri)
                }
                onComplete(icon)
            }
        }

    fun fetchWebsiteIcons(
        websiteUrl: String,
        allowHttp: Boolean,
        onComplete: (List<WebsiteIconCandidate>) -> Unit,
    ): Job {
        val workspaceId = _state.value.currentWorkspaceId
        return viewModelScope.launch {
            runOperation {
                val candidates = websiteIconFetcher.fetch(websiteUrl, allowHttp)
                if (_state.value.currentWorkspaceId == workspaceId) onComplete(candidates)
            }
        }
    }

    fun switchWorkspace(id: String, onComplete: () -> Unit = {}) {
        launchMutation {
                require(!_state.value.transferLocked) { "导入或导出进行中，暂时不能切换工作空间" }
                require(_state.value.workspaces.any { it.id == id }) { "工作空间不存在" }
                searchJob?.cancel()
                settingsRepository.setCurrentWorkspaceId(id)
                _state.update {
                    it.copy(
                        currentWorkspaceId = id,
                        apps = emptyList(),
                        allApps = emptyList(),
                        accounts = emptyList(),
                        account = null,
                        secretItems = emptyList(),
                        searchQuery = "",
                        searchResults = emptyList(),
                        icons = emptyMap(),
                    )
                }
                loadCompaniesInternal(id)
                loadWorkspaceConfiguration(id)
                onComplete()
        }
    }

    fun exportToUri(
        contentResolver: ContentResolver,
        uri: Uri,
        scope: BackupScope,
        selectedIds: Set<String>,
        password: String?,
        onComplete: () -> Unit,
    ): Job = launchTransferCommit {
            try {
                val currentWorkspaceId = _state.value.currentWorkspaceId
                val bytes = withContext(Dispatchers.IO) {
                    backupTransferRepository.export(scope, selectedIds, currentWorkspaceId, password)
                }
                try {
                    withContext(Dispatchers.IO) {
                        contentResolver.openOutputStream(uri, "w")?.use { output ->
                            output.write(bytes)
                            output.flush()
                        } ?: throw IllegalArgumentException("无法写入所选位置")
                        val verified = contentResolver.openInputStream(uri)?.use { it.readBounded(bytes.size + 1) }
                            ?: throw IllegalArgumentException("无法核验已写入文件")
                        require(verified.contentEquals(bytes)) { "写入后文件核验失败" }
                    }
                } finally {
                    bytes.fill(0)
                }
                onComplete()
            } catch (error: Exception) {
                withContext(NonCancellable + Dispatchers.IO) {
                    val deleted = runCatching {
                        DocumentsContract.deleteDocument(contentResolver, uri)
                    }.getOrDefault(false)
                    if (!deleted) runCatching { contentResolver.delete(uri, null, null) }
                }
                throw error
            }
        }

    fun readBackupFile(
        contentResolver: ContentResolver,
        uri: Uri,
        onComplete: (ByteArray, Boolean) -> Unit,
    ): Job = viewModelScope.launch {
            var delivered = false
            var bytes: ByteArray? = null
            try {
                runOperation {
                    bytes = withContext(Dispatchers.IO) {
                        contentResolver.openInputStream(uri)?.use {
                            it.readBounded(com.github.caiheyu.keybook.feature.transfer.BackupCodec.MAX_FILE_BYTES + 1)
                        } ?: throw IllegalArgumentException("无法读取所选备份文件")
                    }
                    val loaded = requireNotNull(bytes)
                    require(loaded.size <= com.github.caiheyu.keybook.feature.transfer.BackupCodec.MAX_FILE_BYTES) {
                        "文件超过 32 MiB"
                    }
                    onComplete(loaded, backupTransferRepository.isEncrypted(loaded))
                    delivered = true
                }
            } finally {
                if (!delivered) bytes?.fill(0)
            }
        }

    fun decodeBackup(
        bytes: ByteArray,
        password: String?,
        onComplete: (BackupPayload) -> Unit,
        onError: ((String) -> Unit)? = null,
    ): Job =
        viewModelScope.launch {
            runOperation(onError = onError) {
                val payload = withContext(Dispatchers.Default) {
                    backupTransferRepository.decodeAndValidate(bytes, password)
                }
                onComplete(payload)
            }
        }

    fun importBackup(payload: BackupPayload, plan: BackupImportPlan, onComplete: () -> Unit): Job =
        launchTransferCommit {
            withContext(Dispatchers.IO) { backupTransferRepository.import(payload, plan) }
            val workspaces = repository.listWorkspaces()
            val current = requireNotNull(_state.value.currentWorkspaceId)
            _state.update { it.copy(workspaces = workspaces, icons = emptyMap()) }
            loadCompaniesInternal(current)
            loadWorkspaceConfiguration(current)
            onComplete()
        }

    fun createWorkspace(name: String, onComplete: () -> Unit) {
        launchMutation {
                requireWorkspaceManagementUnlocked()
                searchJob?.cancel()
                val workspace = repository.createWorkspace(name)
                val all = repository.listWorkspaces()
                settingsRepository.setCurrentWorkspaceId(workspace.id)
                _state.update {
                    it.copy(
                        workspaces = all,
                        workspaceRecordCounts = it.workspaceRecordCounts +
                            (workspace.id to WorkspaceRecordCounts(0, 0)),
                        currentWorkspaceId = workspace.id,
                        companies = emptyList(),
                        apps = emptyList(),
                        allApps = emptyList(),
                        accounts = emptyList(),
                        account = null,
                        secretItems = emptyList(),
                        searchQuery = "",
                        searchResults = emptyList(),
                        icons = emptyMap(),
                    )
                }
                loadWorkspaceConfiguration(workspace.id)
                onComplete()
        }
    }

    fun renameWorkspace(id: String, name: String, onComplete: () -> Unit) {
        launchMutation {
                requireWorkspaceManagementUnlocked()
                repository.renameWorkspace(id, name)
                _state.update { it.copy(workspaces = repository.listWorkspaces()) }
                onComplete()
        }
    }

    fun deleteWorkspace(id: String, onComplete: () -> Unit) {
        launchMutation {
                requireWorkspaceManagementUnlocked()
                repository.deleteWorkspace(id)
                val all = repository.listWorkspaces()
                val current = _state.value.currentWorkspaceId
                val selected = if (current == id) all.first().id else requireNotNull(current)
                val switched = current == id
                if (switched) searchJob?.cancel()
                settingsRepository.setCurrentWorkspaceId(selected)
                _state.update {
                    it.copy(
                        workspaces = all,
                        workspaceRecordCounts = it.workspaceRecordCounts - id,
                        currentWorkspaceId = selected,
                        apps = if (switched) emptyList() else it.apps,
                        allApps = if (switched) emptyList() else it.allApps,
                        accounts = if (switched) emptyList() else it.accounts,
                        account = if (switched) null else it.account,
                        secretItems = if (switched) emptyList() else it.secretItems,
                        searchQuery = if (switched) "" else it.searchQuery,
                        searchResults = if (switched) emptyList() else it.searchResults,
                        icons = if (switched) emptyMap() else it.icons,
                    )
                }
                loadCompaniesInternal(selected)
                loadWorkspaceConfiguration(selected)
                onComplete()
        }
    }

    fun saveCompany(id: String?, draft: CompanyDraft, onComplete: () -> Unit) =
        mutateCurrentWorkspace { workspaceId ->
            repository.saveCompany(workspaceId, id, draft)
            loadCompaniesInternal(workspaceId)
            onComplete()
        }

    fun moveCompanyBy(id: String, offset: Int) = mutateCurrentWorkspace { workspaceId ->
        repository.moveCompanyBy(workspaceId, id, offset)
        loadCompaniesInternal(workspaceId)
    }

    fun deleteCompany(id: String, onComplete: () -> Unit) = mutateCurrentWorkspace { workspaceId ->
        repository.deleteCompany(workspaceId, id)
        loadCompaniesInternal(workspaceId)
        onComplete()
    }

    fun saveApp(companyId: String, id: String?, draft: AppDraft, onComplete: () -> Unit) =
        mutateCurrentWorkspace { workspaceId ->
            repository.saveApp(workspaceId, companyId, id, draft)
            loadCompaniesInternal(workspaceId)
            loadAppsInternal(workspaceId, companyId)
            onComplete()
        }

    fun moveAppBy(id: String, companyId: String, offset: Int) = mutateCurrentWorkspace { workspaceId ->
        repository.moveAppBy(workspaceId, id, offset)
        loadAppsInternal(workspaceId, companyId)
    }

    fun moveApp(id: String, sourceCompanyId: String, targetCompanyId: String, onComplete: () -> Unit) =
        mutateCurrentWorkspace { workspaceId ->
            repository.moveApp(workspaceId, id, targetCompanyId)
            loadCompaniesInternal(workspaceId)
            loadAppsInternal(workspaceId, sourceCompanyId)
            onComplete()
        }

    fun loadAppDeletionCounts(id: String, onComplete: (AppDeletionCounts) -> Unit) =
        withCurrentWorkspace { workspaceId -> onComplete(repository.getAppDeletionCounts(workspaceId, id)) }

    fun deleteApp(
        id: String,
        companyId: String,
        expectedCounts: AppDeletionCounts,
        onComplete: () -> Unit,
    ) =
        mutateCurrentWorkspace { workspaceId ->
            repository.deleteApp(workspaceId, id, expectedCounts)
            loadCompaniesInternal(workspaceId)
            loadAppsInternal(workspaceId, companyId)
            onComplete()
        }

    fun saveAccount(appId: String, id: String?, draft: AccountDraft, onComplete: () -> Unit) =
        mutateCurrentWorkspace { workspaceId ->
            repository.saveAccount(workspaceId, appId, id, draft)
            loadAccountsInternal(workspaceId, appId)
            onComplete()
        }

    fun moveAccountBy(id: String, appId: String, offset: Int) = mutateCurrentWorkspace { workspaceId ->
        repository.moveAccountBy(workspaceId, id, offset)
        loadAccountsInternal(workspaceId, appId)
    }

    fun moveAccount(id: String, sourceAppId: String, targetAppId: String, onComplete: () -> Unit) =
        mutateCurrentWorkspace { workspaceId ->
            repository.moveAccount(workspaceId, id, targetAppId)
            loadAccountsInternal(workspaceId, sourceAppId)
            onComplete()
        }

    fun loadAccountDeletionCounts(id: String, onComplete: (AccountDeletionCounts) -> Unit) =
        withCurrentWorkspace { workspaceId -> onComplete(repository.getAccountDeletionCounts(workspaceId, id)) }

    fun deleteAccount(
        id: String,
        appId: String,
        expectedCounts: AccountDeletionCounts,
        onComplete: () -> Unit,
    ) =
        mutateCurrentWorkspace { workspaceId ->
            repository.deleteAccount(workspaceId, id, expectedCounts)
            loadAccountsInternal(workspaceId, appId)
            onComplete()
        }

    fun saveSecretItem(accountId: String, id: String?, draft: SecretItemDraft, onComplete: () -> Unit) =
        mutateCurrentWorkspace { workspaceId ->
            repository.saveSecretItem(workspaceId, accountId, id, draft)
            _state.update { it.copy(secretItems = repository.listSecretItems(workspaceId, accountId)) }
            onComplete()
        }

    fun moveSecretItemBy(id: String, accountId: String, offset: Int) = mutateCurrentWorkspace { workspaceId ->
        repository.moveSecretItemBy(workspaceId, id, offset)
        _state.update {
            it.copy(secretItems = repository.listSecretItems(workspaceId, accountId))
        }
    }

    fun deleteSecretItem(id: String, accountId: String, onComplete: () -> Unit) =
        mutateCurrentWorkspace { workspaceId ->
            repository.deleteSecretItem(workspaceId, id)
            _state.update { it.copy(secretItems = repository.listSecretItems(workspaceId, accountId)) }
            onComplete()
        }

    fun saveGeneratorPreset(id: String?, draft: GeneratorPresetDraft, onComplete: () -> Unit = {}) =
        mutateCurrentWorkspace { workspaceId ->
            repository.saveGeneratorPreset(workspaceId, id, draft)
            loadWorkspaceConfiguration(workspaceId)
            onComplete()
        }

    fun deleteGeneratorPreset(id: String, onComplete: () -> Unit = {}) =
        mutateCurrentWorkspace { workspaceId ->
            repository.deleteGeneratorPreset(workspaceId, id)
            loadWorkspaceConfiguration(workspaceId)
            onComplete()
        }

    fun setDefaultGenerator(selection: GeneratorSelection) = mutateCurrentWorkspace { workspaceId ->
        repository.setDefaultGenerator(workspaceId, selection)
        loadWorkspaceConfiguration(workspaceId)
    }

    fun saveCatalogPreset(id: String?, draft: CatalogPresetDraft, onComplete: () -> Unit = {}) =
        mutateCurrentWorkspace { workspaceId ->
            repository.saveCatalogPreset(workspaceId, id, draft)
            loadWorkspaceConfiguration(workspaceId)
            onComplete()
        }

    fun deleteCatalogPreset(id: String, onComplete: () -> Unit = {}) =
        mutateCurrentWorkspace { workspaceId ->
            repository.deleteCatalogPreset(workspaceId, id)
            loadWorkspaceConfiguration(workspaceId)
            onComplete()
        }

    fun saveBuiltinCatalogOverride(
        builtinId: String,
        draft: CatalogPresetDraft,
        onComplete: () -> Unit = {},
    ) = mutateCurrentWorkspace { workspaceId ->
        repository.saveBuiltinCatalogOverride(workspaceId, builtinId, draft)
        loadWorkspaceConfiguration(workspaceId)
        onComplete()
    }

    fun restoreBuiltinCatalogDefault(builtinId: String, onComplete: () -> Unit = {}) =
        mutateCurrentWorkspace { workspaceId ->
            repository.restoreBuiltinCatalogDefault(workspaceId, builtinId)
            loadWorkspaceConfiguration(workspaceId)
            onComplete()
        }

    private fun withCurrentWorkspace(block: suspend (String) -> Unit) {
        viewModelScope.launch {
            runOperation {
                val workspaceId = requireNotNull(_state.value.currentWorkspaceId) { "工作空间尚未初始化" }
                block(workspaceId)
            }
        }
    }

    private fun mutateCurrentWorkspace(block: suspend (String) -> Unit) {
        launchMutation {
            val workspaceId = requireNotNull(_state.value.currentWorkspaceId) { "工作空间尚未初始化" }
            block(workspaceId)
        }
    }

    private fun launchMutation(block: suspend () -> Unit) {
        viewModelScope.launch {
            if (!mutationMutex.tryLock()) return@launch
            try {
                runOperation(block = block)
            } finally {
                mutationMutex.unlock()
            }
        }
    }

    private suspend fun loadCompaniesInternal(workspaceId: String) {
        val companies = repository.listCompanies(workspaceId)
        val rows = companies.map { company ->
            CompanyRow(company, repository.listApps(workspaceId, company.id).size)
        }
        loadIcons(workspaceId, companies.mapNotNull { it.iconAssetId })
        if (!isCurrentWorkspace(workspaceId)) return
        _state.update { it.copy(companies = rows) }
    }

    private suspend fun loadAppsInternal(workspaceId: String, companyId: String) {
        val rows = repository.listApps(workspaceId, companyId).map { app ->
            AppRow(app, repository.listAccounts(workspaceId, app.id).size)
        }
        loadIcons(workspaceId, rows.mapNotNull { it.app.iconAssetId })
        if (!isCurrentWorkspace(workspaceId)) return
        _state.update { it.copy(apps = rows) }
    }

    private suspend fun loadAccountsInternal(workspaceId: String, appId: String) {
        val accounts = repository.listAccounts(workspaceId, appId)
        if (!isCurrentWorkspace(workspaceId)) return
        _state.update { it.copy(accounts = accounts) }
    }

    private suspend fun loadWorkspaceConfiguration(workspaceId: String) {
        val generatorPresets = repository.listGeneratorPresets(workspaceId)
        val defaultGenerator = repository.getDefaultGenerator(workspaceId)
        val customCatalog = repository.listCatalogPresets(workspaceId)
        val overrides = repository.listBuiltinCatalogOverrides(workspaceId).associateBy { it.builtinId }
        loadIcons(
            workspaceId,
            customCatalog.mapNotNull { it.iconAssetId } + overrides.values.mapNotNull { it.iconAssetId },
        )
        if (!isCurrentWorkspace(workspaceId)) return
        val iconAssets = _state.value.icons
        val builtinCatalog = catalogRepository.entries()
        val builtinRows = builtinCatalog.map { builtin ->
            val override = overrides[builtin.id]
            CatalogEntryUi(
                id = builtin.id,
                type = builtin.type,
                name = override?.name ?: builtin.name,
                websiteUrl = override?.websiteUrl ?: builtin.websiteUrl,
                note = override?.note ?: builtin.note,
                iconPng = if (override == null) {
                    builtin.iconPng
                } else {
                    override.iconAssetId?.let(iconAssets::get)?.pngBytes
                },
                iconWidth = if (override == null) builtin.width else override.iconAssetId?.let(iconAssets::get)?.width ?: 0,
                iconHeight = if (override == null) builtin.height else override.iconAssetId?.let(iconAssets::get)?.height ?: 0,
                isBuiltin = true,
                isModified = override != null,
            )
        }
        val customRows = customCatalog.map { preset ->
            CatalogEntryUi(
                id = preset.id,
                type = preset.type,
                name = preset.name,
                websiteUrl = preset.websiteUrl,
                note = preset.note,
                iconPng = preset.iconAssetId?.let(iconAssets::get)?.pngBytes,
                iconWidth = preset.iconAssetId?.let(iconAssets::get)?.width ?: 0,
                iconHeight = preset.iconAssetId?.let(iconAssets::get)?.height ?: 0,
                isBuiltin = false,
                isModified = false,
            )
        }
        if (!isCurrentWorkspace(workspaceId)) return
        _state.update {
            it.copy(
                generatorPresets = generatorPresets,
                defaultGenerator = defaultGenerator,
                catalogEntries = (builtinRows + customRows).sortedWith(
                    compareBy<CatalogEntryUi> { it.type }.thenBy { it.name }.thenBy { it.id },
                ),
            )
        }
    }

    private suspend fun loadIcons(workspaceId: String, ids: List<String>) {
        val missing = ids.distinct().filterNot(_state.value.icons::containsKey)
        if (missing.isEmpty()) return
        val loaded = missing.mapNotNull { id -> repository.getIconAsset(workspaceId, id)?.let { id to it } }.toMap()
        if (!isCurrentWorkspace(workspaceId)) return
        _state.update { it.copy(icons = it.icons + loaded) }
    }

    internal fun isCurrentWorkspace(workspaceId: String): Boolean =
        isWorkspaceResultCurrent(_state.value.currentWorkspaceId, workspaceId)

    private fun requireWorkspaceManagementUnlocked() {
        require(!_state.value.transferLocked) {
            "导入或导出进行中，暂时不能管理工作空间"
        }
    }

    private fun launchTransferCommit(block: suspend () -> Unit): Job =
        viewModelScope.launch {
            if (!transferCommitMutex.tryLock()) return@launch
            try {
                runOperation(block = block)
            } finally {
                transferCommitMutex.unlock()
            }
        }

    private suspend fun runOperation(
        initial: Boolean = false,
        onError: ((String) -> Unit)? = null,
        block: suspend () -> Unit,
    ) {
        activeOperations += 1
        _state.update { it.copy(busy = true, error = null) }
        try {
            block()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            val message = error.message ?: "操作失败"
            _state.update {
                if (initial) {
                    it.copy(
                        initialized = false,
                        fatalError = message,
                        error = null,
                    )
                } else if (onError != null) {
                    it
                } else {
                    it.copy(error = message)
                }
            }
            if (!initial) onError?.invoke(message)
        } finally {
            activeOperations = (activeOperations - 1).coerceAtLeast(0)
            _state.update {
                it.copy(
                    busy = activeOperations > 0,
                )
            }
        }
    }

}

private fun java.io.InputStream.readBounded(limit: Int): ByteArray {
    val output = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(16 * 1024)
    var total = 0
    while (true) {
        val read = read(buffer)
        if (read < 0) break
        total += read
        require(total <= limit) { "文件超过允许大小" }
        output.write(buffer, 0, read)
    }
    return output.toByteArray()
}
