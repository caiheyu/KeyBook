package com.github.caiheyu.keybook.ui

import android.content.Intent
import android.net.Uri
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.ListAlt
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Apartment
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Velocity
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.github.caiheyu.keybook.BuildConfig
import com.github.caiheyu.keybook.core.clipboard.SensitiveClipboard
import com.github.caiheyu.keybook.core.security.RootDetector
import com.github.caiheyu.keybook.core.validation.InputValidation
import com.github.caiheyu.keybook.data.model.AccountDraft
import com.github.caiheyu.keybook.data.model.AccountDeletionCounts
import com.github.caiheyu.keybook.data.catalog.CatalogType
import com.github.caiheyu.keybook.data.model.AppDraft
import com.github.caiheyu.keybook.data.model.AppDeletionCounts
import com.github.caiheyu.keybook.data.model.CatalogPresetDraft
import com.github.caiheyu.keybook.data.model.CompanyDraft
import com.github.caiheyu.keybook.data.model.IconDraft
import com.github.caiheyu.keybook.data.model.GeneratorPresetDraft
import com.github.caiheyu.keybook.data.model.GeneratorSelection
import com.github.caiheyu.keybook.data.model.GeneratorSelectionKind
import com.github.caiheyu.keybook.data.model.SearchResultType
import com.github.caiheyu.keybook.data.model.SecretItemDraft
import com.github.caiheyu.keybook.data.settings.ThemeMode
import com.github.caiheyu.keybook.feature.generator.BuiltinGeneratorPresets
import com.github.caiheyu.keybook.feature.generator.PasswordGenerator
import com.github.caiheyu.keybook.feature.generator.PasswordRules
import com.github.caiheyu.keybook.feature.icon.WebsiteIconCandidate
import com.github.caiheyu.keybook.ui.navigation.AboutRoute
import com.github.caiheyu.keybook.ui.navigation.AccountEditRoute
import com.github.caiheyu.keybook.ui.navigation.AccountRoute
import com.github.caiheyu.keybook.ui.navigation.AppEditRoute
import com.github.caiheyu.keybook.ui.navigation.AppRoute
import com.github.caiheyu.keybook.ui.navigation.CompanyEditRoute
import com.github.caiheyu.keybook.ui.navigation.CompanyRoute
import com.github.caiheyu.keybook.ui.navigation.CatalogRoute
import com.github.caiheyu.keybook.ui.navigation.CatalogEditRoute
import com.github.caiheyu.keybook.ui.navigation.GeneratorRoute
import com.github.caiheyu.keybook.ui.navigation.ExportRoute
import com.github.caiheyu.keybook.ui.navigation.ImportRoute
import com.github.caiheyu.keybook.ui.navigation.SecretEditRoute
import com.github.caiheyu.keybook.ui.navigation.SecurityRoute
import com.github.caiheyu.keybook.ui.navigation.SettingsRoute
import com.github.caiheyu.keybook.ui.navigation.VaultRoute
import kotlin.math.roundToInt

@Composable
fun KeyBookApp(
    viewModel: KeyBookViewModel = hiltViewModel(),
    clipboard: SensitiveClipboard = hiltViewModel<ClipboardViewModel>().clipboard,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val route = backStack?.destination?.route.orEmpty()
    val snackbar = remember { SnackbarHostState() }
    val showBottomBar = !route.contains("EditRoute") && !route.contains("ExportRoute") &&
        !route.contains("ImportRoute")

    LaunchedEffect(route) {
        if (!routeKeepsSensitiveDetails(route)) viewModel.releaseSensitiveDetails()
    }

    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        clipboard.onBackground()
        viewModel.releaseSensitiveDetails()
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { clipboard.onForeground() }

    LaunchedEffect(state.error) {
        state.error?.let {
            snackbar.showSnackbar(it)
            viewModel.dismissError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (showBottomBar) {
                BottomNavigation(
                    route = route,
                    onVault = { navController.navigateRoot(VaultRoute) },
                    onGenerator = { navController.navigateRoot(GeneratorRoute()) },
                    onSettings = { navController.navigateRoot(SettingsRoute) },
                )
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            val fatalError = state.fatalError
            if (fatalError != null) {
                FatalStorageScreen(fatalError)
            } else if (!state.initialized) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("正在初始化加密存储…")
                }
            } else {
                NavHost(navController, startDestination = VaultRoute) {
                    composable<VaultRoute> {
                        VaultScreen(state, viewModel, navController)
                    }
                    composable<CompanyRoute> { entry ->
                        CompanyScreen(entry.toRoute(), state, viewModel, navController)
                    }
                    composable<AppRoute> { entry ->
                        AppScreen(entry.toRoute(), state, viewModel, navController)
                    }
                    composable<AccountRoute> { entry ->
                        AccountScreen(entry.toRoute(), state, viewModel, navController)
                    }
                    composable<CompanyEditRoute> { entry ->
                        CompanyForm(entry.toRoute(), state, viewModel, navController)
                    }
                    composable<AppEditRoute> { entry ->
                        AppForm(entry.toRoute(), state, viewModel, navController)
                    }
                    composable<AccountEditRoute> { entry ->
                        AccountForm(entry.toRoute(), state, viewModel, navController)
                    }
                    composable<SecretEditRoute> { entry ->
                        SecretForm(entry.toRoute(), state, viewModel, navController)
                    }
                    composable<GeneratorRoute> { entry ->
                        GeneratorScreen(entry.toRoute<GeneratorRoute>().openPresetManager, state, viewModel)
                    }
                    composable<SettingsRoute> { SettingsScreen(state, viewModel, navController) }
                    composable<CatalogRoute> { CatalogScreen(state, viewModel, navController) }
                    composable<CatalogEditRoute> { entry ->
                        CatalogForm(entry.toRoute(), state, viewModel, navController)
                    }
                    composable<AboutRoute> { AboutScreen(state, viewModel, navController) }
                    composable<SecurityRoute> { SecurityScreen(navController) }
                    composable<ExportRoute> { entry ->
                        ExportScreen(entry.toRoute(), state, viewModel, navController)
                    }
                    composable<ImportRoute> {
                        ImportScreen(state, viewModel, navController)
                    }
                }
            }
            if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        }
    }
}

internal fun routeKeepsSensitiveDetails(route: String): Boolean =
    route.contains("AccountRoute") || route.contains("AccountEditRoute") ||
        route.contains("SecretEditRoute")

@Composable
private fun FatalStorageScreen(detail: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Outlined.Shield,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.error,
        )
        Spacer(Modifier.height(16.dp))
        Text("无法打开加密存储", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text("为避免覆盖现有数据，密钥册已停止加载。请勿卸载或清除应用数据。")
        Spacer(Modifier.height(12.dp))
        Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private inline fun <reified T : Any> NavController.navigateRoot(route: T) {
    navigate(route) {
        popUpTo<VaultRoute> { inclusive = false }
        launchSingleTop = true
    }
}

@Composable
private fun BottomNavigation(
    route: String,
    onVault: () -> Unit,
    onGenerator: () -> Unit,
    onSettings: () -> Unit,
) {
    NavigationBar {
        NavigationBarItem(
            selected = route.contains("VaultRoute") || route.contains("CompanyRoute") ||
                route.contains("AppRoute") || route.contains("AccountRoute"),
            onClick = onVault,
            icon = { Icon(Icons.AutoMirrored.Outlined.ListAlt, null) },
            label = { Text("记录") },
        )
        NavigationBarItem(
            selected = route.contains("GeneratorRoute"),
            onClick = onGenerator,
            icon = { Icon(Icons.Outlined.AutoAwesome, null) },
            label = { Text("生成器") },
        )
        NavigationBarItem(
            selected = route.contains("SettingsRoute") || route.contains("AboutRoute") ||
                route.contains("SecurityRoute") || route.contains("CatalogRoute"),
            onClick = onSettings,
            icon = { Icon(Icons.Outlined.Settings, null) },
            label = { Text("设置") },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VaultScreen(
    state: KeyBookUiState,
    viewModel: KeyBookViewModel,
    navController: NavController,
) {
    val query = state.searchQuery
    val recordsListState = rememberLazyListState()
    val searchListState = rememberLazyListState()
    var workspaceMenu by remember { mutableStateOf(false) }
    var manageWorkspaces by remember { mutableStateOf(false) }
    var sorting by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("密钥册") },
            actions = {
                TextButton(onClick = { workspaceMenu = true }) {
                    Text(
                        state.currentWorkspace?.name ?: "工作空间",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Icon(Icons.Outlined.KeyboardArrowDown, null)
                }
                IconButton(
                    onClick = { sorting = !sorting },
                    enabled = query.isBlank() && state.companies.size > 1,
                ) {
                    Icon(Icons.Outlined.SwapVert, "调整企业顺序")
                }
            },
        )
        OutlinedTextField(
            value = query,
            onValueChange = viewModel::search,
            modifier = Modifier.fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .testTag("vaultSearchField"),
            placeholder = { Text("搜索企业、应用、账号或附加项") },
            leadingIcon = { Icon(Icons.Outlined.Search, null) },
            singleLine = true,
        )
        if (query.isBlank()) {
            SectionHeader("企业", "${state.companies.size} 个企业")
            LazyColumn(Modifier.weight(1f), state = recordsListState) {
                itemsIndexed(state.companies, key = { _, row -> row.company.id }) { index, row ->
                    RecordRow(
                        name = row.company.name,
                        subtitle = "${row.appCount} 个应用",
                        iconPng = row.company.iconAssetId?.let(state.icons::get)?.pngBytes,
                        onClick = { navController.navigate(CompanyRoute(row.company.id)) },
                        trailing = if (sorting) {
                            {
                                SortControls(
                                    index,
                                    state.companies.lastIndex,
                                    onMove = { offset -> viewModel.moveCompanyBy(row.company.id, offset) },
                                    onUp = { viewModel.moveCompanyBy(row.company.id, -1) },
                                    onDown = { viewModel.moveCompanyBy(row.company.id, 1) },
                                )
                            }
                        } else null,
                    )
                }
                item {
                    OutlinedButton(
                        onClick = { navController.navigate(CompanyEditRoute()) },
                        modifier = Modifier.fillMaxWidth().padding(20.dp),
                    ) {
                        Icon(Icons.Outlined.Add, null)
                        Spacer(Modifier.width(8.dp))
                        Text("新增企业")
                    }
                }
            }
        } else {
            SectionHeader("搜索结果", "${state.searchResults.size} 项")
            LazyColumn(
                Modifier.weight(1f).testTag("searchResultsList"),
                state = searchListState,
            ) {
                items(state.searchResults, key = { "${it.type}:${it.id}" }) { result ->
                    RecordRow(
                        name = result.title,
                        subtitle = result.subtitle,
                        modifier = Modifier.testTag("search-result:${result.type}:${result.id}"),
                    ) {
                        when (result.type) {
                            SearchResultType.COMPANY -> navController.navigate(CompanyRoute(requireNotNull(result.companyId)))
                            SearchResultType.APP -> navController.navigate(AppRoute(requireNotNull(result.companyId), requireNotNull(result.appId)))
                            SearchResultType.ACCOUNT, SearchResultType.SECRET_ITEM -> navController.navigate(
                                AccountRoute(
                                    requireNotNull(result.companyId),
                                    requireNotNull(result.appId),
                                    requireNotNull(result.accountId),
                                    highlightSecretId = result.id.takeIf {
                                        result.type == SearchResultType.SECRET_ITEM
                                    },
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
    if (workspaceMenu) {
        BottomSheetFrame(title = "切换工作空间", onDismiss = { workspaceMenu = false }) {
            state.workspaces.forEach { workspace ->
                ActionSheetItem(
                    icon = if (workspace.id == state.currentWorkspaceId) Icons.Outlined.Check else Icons.Outlined.Apartment,
                    label = workspace.name,
                    enabled = !state.transferLocked && !state.busy,
                    onClick = {
                        workspaceMenu = false
                        viewModel.switchWorkspace(workspace.id) {
                            navController.navigateRoot(VaultRoute)
                        }
                    },
                )
            }
            HorizontalDivider()
            ActionSheetItem(Icons.Outlined.Settings, "管理工作空间") {
                workspaceMenu = false
                manageWorkspaces = true
            }
        }
    }
    if (manageWorkspaces) {
        WorkspaceManager(state, viewModel, onDismiss = { manageWorkspaces = false })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CompanyScreen(
    route: CompanyRoute,
    state: KeyBookUiState,
    viewModel: KeyBookViewModel,
    navController: NavController,
) {
    val company = state.companies.firstOrNull { it.company.id == route.companyId }?.company
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var sorting by remember { mutableStateOf(false) }
    LaunchedEffect(route.companyId) { viewModel.loadApps(route.companyId) }
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                HierarchyTitle(
                    company?.name ?: "企业",
                    state.currentWorkspace?.name.orEmpty(),
                )
            },
            navigationIcon = { BackButton(navController) },
            actions = {
                IconButton(onClick = { sorting = !sorting }, enabled = state.apps.size > 1) {
                    Icon(Icons.Outlined.SwapVert, "调整应用顺序")
                }
                IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, "更多操作") }
            },
        )
        SectionHeader("应用", "${state.apps.size} 个应用")
        LazyColumn(Modifier.weight(1f)) {
            itemsIndexed(state.apps, key = { _, row -> row.app.id }) { index, row ->
                RecordRow(
                    name = row.app.name,
                    subtitle = "${row.accountCount} 个账号",
                    iconPng = row.app.iconAssetId?.let(state.icons::get)?.pngBytes,
                    onClick = { navController.navigate(AppRoute(route.companyId, row.app.id)) },
                    trailing = if (sorting) {
                        {
                            SortControls(
                                index,
                                state.apps.lastIndex,
                                onMove = { offset -> viewModel.moveAppBy(row.app.id, route.companyId, offset) },
                                onUp = { viewModel.moveAppBy(row.app.id, route.companyId, -1) },
                                onDown = { viewModel.moveAppBy(row.app.id, route.companyId, 1) },
                            )
                        }
                    } else null,
                )
            }
            item {
                OutlinedButton(
                    onClick = { navController.navigate(AppEditRoute(route.companyId)) },
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                ) {
                    Icon(Icons.Outlined.Add, null)
                    Spacer(Modifier.width(8.dp))
                    Text("新增应用")
                }
            }
        }
    }
    if (menu) {
        BottomSheetFrame(title = "企业操作", onDismiss = { menu = false }) {
            ActionSheetItem(Icons.Outlined.Edit, "编辑") {
                menu = false
                navController.navigate(CompanyEditRoute(route.companyId))
            }
            ActionSheetItem(
                icon = Icons.Outlined.Category,
                label = "保存为预定义项",
                enabled = company != null,
                onClick = {
                    menu = false
                    navController.navigate(
                        CatalogEditRoute(
                            type = CatalogType.COMPANY.name,
                            sourceRecordId = route.companyId,
                        ),
                    )
                },
            )
            ActionSheetItem(
                icon = Icons.Outlined.Delete,
                label = if (state.apps.isEmpty()) "删除" else "企业下还有应用，请先删除或移走应用",
                enabled = state.apps.isEmpty(),
                destructive = true,
                onClick = { menu = false; confirmDelete = true },
            )
        }
    }
    if (confirmDelete && company != null) {
        ConfirmDelete(
            title = "删除企业？",
            text = "将永久删除“${company.name}”，删除后无法恢复。",
            onDismiss = { confirmDelete = false },
            onConfirm = {
                confirmDelete = false
                viewModel.deleteCompany(route.companyId) { navController.popBackStack() }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppScreen(
    route: AppRoute,
    state: KeyBookUiState,
    viewModel: KeyBookViewModel,
    navController: NavController,
) {
    val app = state.apps.firstOrNull { it.app.id == route.appId }?.app
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<AppDeletionCounts?>(null) }
    var sorting by remember { mutableStateOf(false) }
    var moveDialog by remember { mutableStateOf(false) }
    var moveTargetId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(route.appId) {
        viewModel.loadApps(route.companyId)
        viewModel.loadAccounts(route.appId)
    }
    Column(Modifier.fillMaxSize()) {
        val companyName = state.companies.firstOrNull { it.company.id == route.companyId }
            ?.company?.name.orEmpty()
        TopAppBar(
            title = {
                HierarchyTitle(
                    app?.name ?: "应用",
                    listOf(state.currentWorkspace?.name.orEmpty(), companyName)
                        .filter(String::isNotEmpty).joinToString(" / "),
                )
            },
            navigationIcon = { BackButton(navController) },
            actions = {
                IconButton(onClick = { sorting = !sorting }, enabled = state.accounts.size > 1) {
                    Icon(Icons.Outlined.SwapVert, "调整账号顺序")
                }
                IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, "更多操作") }
            },
        )
        SectionHeader("账号", "${state.accounts.size} 个账号")
        LazyColumn(Modifier.weight(1f)) {
            itemsIndexed(state.accounts, key = { _, account -> account.id }) { index, account ->
                RecordRow(
                    name = account.name,
                    subtitle = account.login,
                    icon = Icons.Outlined.Person,
                    onClick = { navController.navigate(AccountRoute(route.companyId, route.appId, account.id)) },
                    trailing = if (sorting) {
                        {
                            SortControls(
                                index,
                                state.accounts.lastIndex,
                                onMove = { offset -> viewModel.moveAccountBy(account.id, route.appId, offset) },
                                onUp = { viewModel.moveAccountBy(account.id, route.appId, -1) },
                                onDown = { viewModel.moveAccountBy(account.id, route.appId, 1) },
                            )
                        }
                    } else null,
                )
            }
            item {
                OutlinedButton(
                    onClick = { navController.navigate(AccountEditRoute(route.appId)) },
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                ) {
                    Icon(Icons.Outlined.Add, null)
                    Spacer(Modifier.width(8.dp))
                    Text("新增账号")
                }
            }
        }
    }
    if (menu) {
        BottomSheetFrame(title = "应用操作", onDismiss = { menu = false }) {
            ActionSheetItem(Icons.Outlined.Edit, "编辑") {
                menu = false
                navController.navigate(AppEditRoute(route.companyId, route.appId))
            }
            ActionSheetItem(
                icon = Icons.AutoMirrored.Outlined.DriveFileMove,
                label = "移动到其他企业",
                enabled = state.companies.size > 1,
                onClick = { menu = false; moveDialog = true },
            )
            ActionSheetItem(
                icon = Icons.Outlined.Category,
                label = "保存为预定义项",
                enabled = app != null,
                onClick = {
                    menu = false
                    navController.navigate(
                        CatalogEditRoute(
                            type = CatalogType.APP.name,
                            sourceRecordId = route.appId,
                        ),
                    )
                },
            )
            ActionSheetItem(
                icon = Icons.Outlined.FileDownload,
                label = "导出此应用",
                enabled = app != null,
                onClick = {
                    menu = false
                    navController.navigate(ExportRoute("APPS", route.appId))
                },
            )
            ActionSheetItem(
                icon = Icons.Outlined.Delete,
                label = "删除应用及其全部内容",
                destructive = true,
                onClick = {
                    menu = false
                    viewModel.loadAppDeletionCounts(route.appId) { confirmDelete = it }
                },
            )
        }
    }
    confirmDelete?.let { counts ->
        if (app == null) return@let
        ConfirmDelete(
            "删除应用？",
            "将永久删除“${app.name}”及其 ${counts.accountCount} 个账号、" +
                "${counts.secretItemCount} 个附加密码项，删除后无法恢复。",
            onDismiss = { confirmDelete = null },
            onConfirm = {
                confirmDelete = null
                viewModel.deleteApp(route.appId, route.companyId, counts) { navController.popBackStack() }
            },
        )
    }
    if (moveDialog && app != null) {
        TargetPickerDialog(
            title = "移动应用",
            targets = state.companies
                .filter { it.company.id != route.companyId }
                .map { it.company.id to it.company.name },
            onDismiss = { moveDialog = false },
            onSelected = { target -> moveDialog = false; moveTargetId = target },
        )
    }
    moveTargetId?.let { targetId ->
        val target = state.companies.firstOrNull { it.company.id == targetId }?.company
        if (app != null && target != null) {
            ConfirmMove(
                source = state.companies.firstOrNull { it.company.id == route.companyId }?.company?.name.orEmpty(),
                item = app.name,
                target = target.name,
                onDismiss = { moveTargetId = null },
                onConfirm = {
                    moveTargetId = null
                    viewModel.moveApp(route.appId, route.companyId, targetId) { navController.popBackStack() }
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountScreen(
    route: AccountRoute,
    state: KeyBookUiState,
    viewModel: KeyBookViewModel,
    navController: NavController,
    clipboard: SensitiveClipboard = hiltViewModel<ClipboardViewModel>().clipboard,
) {
    val account = state.account?.takeIf { it.id == route.accountId }
    var passwordVisible by remember { mutableStateOf(false) }
    var visibleSecretIds by remember { mutableStateOf(emptySet<String>()) }
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<AccountDeletionCounts?>(null) }
    var secretSorting by remember { mutableStateOf(false) }
    var deleteSecretId by remember { mutableStateOf<String?>(null) }
    var moveDialog by remember { mutableStateOf(false) }
    var moveTargetId by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    LaunchedEffect(route.companyId, route.accountId) {
        viewModel.loadApps(route.companyId)
        viewModel.loadAccount(route.accountId)
    }
    LifecycleEventEffect(Lifecycle.Event.ON_START) { viewModel.loadAccount(route.accountId) }
    LaunchedEffect(route.highlightSecretId, state.secretItems) {
        val index = state.secretItems.indexOfFirst { it.id == route.highlightSecretId }
        if (index >= 0) listState.animateScrollToItem(index + 1)
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        passwordVisible = false
        visibleSecretIds = emptySet()
    }
    Column(Modifier.fillMaxSize()) {
        val companyName = state.companies.firstOrNull { it.company.id == route.companyId }
            ?.company?.name.orEmpty()
        val appName = state.apps.firstOrNull { it.app.id == route.appId }?.app?.name.orEmpty()
        TopAppBar(
            title = {
                HierarchyTitle(
                    account?.name ?: "账号详情",
                    listOf(state.currentWorkspace?.name.orEmpty(), companyName, appName)
                        .filter(String::isNotEmpty).joinToString(" / "),
                )
            },
            navigationIcon = { BackButton(navController) },
            actions = {
                IconButton(onClick = { secretSorting = !secretSorting }, enabled = state.secretItems.size > 1) {
                    Icon(Icons.Outlined.SwapVert, "调整附加密码项顺序")
                }
                IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, "更多操作") }
            },
        )
        if (account != null) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
                state = listState,
            ) {
                item {
                    Text(account.name, style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(4.dp))
                    Text("登录账号", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    SecretValueRow(account.login, visible = true, onToggle = null) {
                        clipboard.copy("登录账号", account.login, false)
                    }
                    HorizontalDivider()
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("登录密码", modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        IconButton(onClick = { navController.navigate(AccountEditRoute(route.appId, route.accountId)) }) {
                            Icon(Icons.Outlined.Edit, "编辑登录密码")
                        }
                    }
                    SecretValueRow(account.password, passwordVisible, { passwordVisible = !passwordVisible }) {
                        clipboard.copy("登录密码", account.password, true)
                    }
                    if (account.note.isNotEmpty()) {
                        HorizontalDivider()
                        Text("备注", modifier = Modifier.padding(top = 18.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(account.note, modifier = Modifier.padding(vertical = 12.dp))
                    }
                    HorizontalDivider()
                    SectionHeader("附加密码项", "${state.secretItems.size} 项", horizontalPadding = 0.dp)
                }
                itemsIndexed(state.secretItems, key = { _, item -> item.id }) { index, item ->
                    val highlighted = item.id == route.highlightSecretId
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .testTag(if (highlighted) "highlighted-secret:${item.id}" else "secret:${item.id}")
                            .background(
                                if (highlighted) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                                RoundedCornerShape(4.dp),
                            )
                            .padding(horizontal = if (highlighted) 8.dp else 0.dp, vertical = 12.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(item.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            if (secretSorting) {
                                SortControls(
                                    index,
                                    state.secretItems.lastIndex,
                                    onMove = { offset -> viewModel.moveSecretItemBy(item.id, route.accountId, offset) },
                                    onUp = { viewModel.moveSecretItemBy(item.id, route.accountId, -1) },
                                    onDown = { viewModel.moveSecretItemBy(item.id, route.accountId, 1) },
                                )
                            }
                            IconButton(onClick = { navController.navigate(SecretEditRoute(route.accountId, item.id)) }) {
                                Icon(Icons.Outlined.Edit, "编辑${item.name}")
                            }
                            IconButton(onClick = { deleteSecretId = item.id }) {
                                Icon(Icons.Outlined.Delete, "删除${item.name}")
                            }
                        }
                        SecretValueRow(
                            item.password,
                            item.id in visibleSecretIds,
                            {
                                visibleSecretIds = if (item.id in visibleSecretIds) {
                                    visibleSecretIds - item.id
                                } else {
                                    visibleSecretIds + item.id
                                }
                            },
                        ) { clipboard.copy(item.name, item.password, true) }
                        if (item.note.isNotEmpty()) {
                            Text(item.note, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    HorizontalDivider()
                }
                item {
                    OutlinedButton(
                        onClick = { navController.navigate(SecretEditRoute(route.accountId)) },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
                    ) {
                        Icon(Icons.Outlined.Add, null)
                        Spacer(Modifier.width(8.dp))
                        Text("新增附加密码项")
                    }
                }
            }
        }
    }
    if (menu) {
        BottomSheetFrame(title = "账号操作", onDismiss = { menu = false }) {
            ActionSheetItem(Icons.Outlined.Edit, "编辑账号") {
                menu = false
                navController.navigate(AccountEditRoute(route.appId, route.accountId))
            }
            ActionSheetItem(Icons.AutoMirrored.Outlined.DriveFileMove, "移动到其他应用") {
                menu = false
                viewModel.loadAllApps()
                moveDialog = true
            }
            ActionSheetItem(
                icon = Icons.Outlined.Delete,
                label = "删除账号及附加密码项",
                destructive = true,
                onClick = {
                    menu = false
                    viewModel.loadAccountDeletionCounts(route.accountId) { confirmDelete = it }
                },
            )
        }
    }
    confirmDelete?.let { counts ->
        if (account == null) return@let
        ConfirmDelete(
            "删除账号？",
            "将永久删除“${account.name}”及其 ${counts.secretItemCount} 个附加密码项，删除后无法恢复。",
            onDismiss = { confirmDelete = null },
            onConfirm = {
                confirmDelete = null
                viewModel.deleteAccount(route.accountId, route.appId, counts) { navController.popBackStack() }
            },
        )
    }
    deleteSecretId?.let { id ->
        val item = state.secretItems.firstOrNull { it.id == id }
        if (item != null) {
            ConfirmDelete(
                title = "删除附加密码项？",
                text = "将永久删除“${item.name}”，删除后无法恢复。",
                onDismiss = { deleteSecretId = null },
                onConfirm = {
                    deleteSecretId = null
                    viewModel.deleteSecretItem(id, route.accountId) {}
                },
            )
        }
    }
    if (moveDialog && account != null) {
        TargetPickerDialog(
            title = "移动账号",
            targets = state.allApps
                .filter { it.id != route.appId }
                .map { target ->
                    val company = state.companies.firstOrNull { it.company.id == target.companyId }?.company?.name
                    target.id to listOfNotNull(company, target.name).joinToString(" / ")
                },
            onDismiss = { moveDialog = false },
            onSelected = { target -> moveDialog = false; moveTargetId = target },
        )
    }
    moveTargetId?.let { targetId ->
        val target = state.allApps.firstOrNull { it.id == targetId }
        if (account != null && target != null) {
            val sourceApp = state.apps.firstOrNull { it.app.id == route.appId }?.app
                ?: state.allApps.firstOrNull { it.id == route.appId }
            val sourceCompanyId = sourceApp?.companyId
            val sourceCompany = state.companies
                .firstOrNull { it.company.id == sourceCompanyId }
                ?.company?.name
            val targetCompany = state.companies
                .firstOrNull { it.company.id == target.companyId }
                ?.company?.name
            ConfirmMove(
                source = listOfNotNull(
                    sourceCompany,
                    sourceApp?.name,
                ).joinToString(" / "),
                item = account.name,
                target = listOfNotNull(targetCompany, target.name).joinToString(" / "),
                onDismiss = { moveTargetId = null },
                onConfirm = {
                    moveTargetId = null
                    viewModel.moveAccount(route.accountId, route.appId, targetId) { navController.popBackStack() }
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CompanyForm(
    route: CompanyEditRoute,
    state: KeyBookUiState,
    viewModel: KeyBookViewModel,
    navController: NavController,
) {
    val existing = state.companies.firstOrNull { it.company.id == route.companyId }?.company
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var website by remember { mutableStateOf(existing?.websiteUrl.orEmpty()) }
    var note by remember { mutableStateOf(existing?.note.orEmpty()) }
    var templatePicker by remember { mutableStateOf(false) }
    var newIcon by remember { mutableStateOf<IconDraft?>(null) }
    var removeIcon by remember { mutableStateOf(false) }
    // The company list is loaded asynchronously. Populate an edit form when its row
    // arrives, while preserving any edits the user has already started making.
    LaunchedEffect(existing?.id) {
        if (existing != null && name.isEmpty() && website.isEmpty() && note.isEmpty() &&
            newIcon == null && !removeIcon
        ) {
            name = existing.name
            website = existing.websiteUrl
            note = existing.note
        }
    }
    val nameError = validationError { InputValidation.normalizeName(name) }
    val websiteError = validationError { InputValidation.validateWebsite(website) }
    val noteError = validationError { InputValidation.normalizeNote(note) }
    RecordFormScaffold(
        title = if (route.companyId == null) "新增企业" else "编辑企业",
        navController = navController,
        isDirty = {
            name != existing?.name.orEmpty() || website != existing?.websiteUrl.orEmpty() ||
                note != existing?.note.orEmpty() || newIcon != null || removeIcon
        },
        saveEnabled = nameError == null && websiteError == null && noteError == null && !state.busy,
        onSave = {
            viewModel.saveCompany(
                route.companyId,
                CompanyDraft(name, note, website, newIcon = newIcon, removeIcon = removeIcon),
            ) {
                navController.popBackStack()
            }
        },
    ) {
        OutlinedButton(onClick = { templatePicker = true }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Outlined.Category, null)
            Spacer(Modifier.width(8.dp))
            Text("从预定义项选择")
        }
        IconEditor(
            iconBytes = newIcon?.pngBytes ?: existing?.iconAssetId
                ?.takeUnless { removeIcon }
                ?.let(state.icons::get)?.pngBytes,
            viewModel = viewModel,
            onIcon = { newIcon = it; removeIcon = false },
            onRemove = { newIcon = null; removeIcon = true },
            websiteUrl = website,
            busy = state.busy,
        )
        CommonRecordFields(
            name,
            { name = it },
            website,
            { website = it },
            note,
            { note = it },
            nameError,
            websiteError,
            noteError,
        )
    }
    if (templatePicker) {
        CatalogTemplatePicker(
            entries = state.catalogEntries.filter { it.type == CatalogType.COMPANY },
            onDismiss = { templatePicker = false },
            onSelect = { entry ->
                name = entry.name
                website = entry.websiteUrl
                note = entry.note
                newIcon = entry.iconPng?.let { IconDraft(it, entry.iconWidth, entry.iconHeight) }
                removeIcon = entry.iconPng == null
                templatePicker = false
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppForm(
    route: AppEditRoute,
    state: KeyBookUiState,
    viewModel: KeyBookViewModel,
    navController: NavController,
) {
    val existing = state.apps.firstOrNull { it.app.id == route.appId }?.app
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var website by remember { mutableStateOf(existing?.websiteUrl.orEmpty()) }
    var note by remember { mutableStateOf(existing?.note.orEmpty()) }
    var templatePicker by remember { mutableStateOf(false) }
    var newIcon by remember { mutableStateOf<IconDraft?>(null) }
    var removeIcon by remember { mutableStateOf(false) }
    LaunchedEffect(route.companyId) { viewModel.loadApps(route.companyId) }
    LaunchedEffect(existing?.id) {
        if (existing != null && name.isEmpty() && website.isEmpty() && note.isEmpty() && newIcon == null && !removeIcon) {
            name = existing.name
            website = existing.websiteUrl
            note = existing.note
        }
    }
    val nameError = validationError { InputValidation.normalizeName(name) }
    val websiteError = validationError { InputValidation.validateWebsite(website) }
    val noteError = validationError { InputValidation.normalizeNote(note) }
    RecordFormScaffold(
        title = if (route.appId == null) "新增应用" else "编辑应用",
        navController = navController,
        isDirty = {
            name != existing?.name.orEmpty() || website != existing?.websiteUrl.orEmpty() ||
                note != existing?.note.orEmpty() || newIcon != null || removeIcon
        },
        saveEnabled = nameError == null && websiteError == null && noteError == null && !state.busy,
        onSave = {
            viewModel.saveApp(
                route.companyId,
                route.appId,
                AppDraft(name, note, website, newIcon = newIcon, removeIcon = removeIcon),
            ) {
                navController.popBackStack()
            }
        },
    ) {
        OutlinedButton(onClick = { templatePicker = true }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Outlined.Category, null)
            Spacer(Modifier.width(8.dp))
            Text("从预定义项选择")
        }
        IconEditor(
            iconBytes = newIcon?.pngBytes ?: existing?.iconAssetId
                ?.takeUnless { removeIcon }
                ?.let(state.icons::get)?.pngBytes,
            viewModel = viewModel,
            onIcon = { newIcon = it; removeIcon = false },
            onRemove = { newIcon = null; removeIcon = true },
            websiteUrl = website,
            busy = state.busy,
        )
        CommonRecordFields(
            name,
            { name = it },
            website,
            { website = it },
            note,
            { note = it },
            nameError,
            websiteError,
            noteError,
        )
    }
    if (templatePicker) {
        CatalogTemplatePicker(
            entries = state.catalogEntries.filter { it.type == CatalogType.APP },
            onDismiss = { templatePicker = false },
            onSelect = { entry ->
                name = entry.name
                website = entry.websiteUrl
                note = entry.note
                newIcon = entry.iconPng?.let { IconDraft(it, entry.iconWidth, entry.iconHeight) }
                removeIcon = entry.iconPng == null
                templatePicker = false
            },
        )
    }
}

@Composable
private fun IconEditor(
    iconBytes: ByteArray?,
    viewModel: KeyBookViewModel,
    onIcon: (IconDraft) -> Unit,
    onRemove: () -> Unit,
    websiteUrl: String,
    busy: Boolean,
) {
    val context = LocalContext.current
    var httpConfirm by remember { mutableStateOf(false) }
    var candidates by remember { mutableStateOf<List<WebsiteIconCandidate>>(emptyList()) }
    var operationJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            operationJob?.cancel()
            operationJob = viewModel.normalizeIcon(context.contentResolver, it, onIcon)
        }
    }
    DisposableEffect(Unit) {
        onDispose { operationJob?.cancel() }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        operationJob?.cancel()
        operationJob = null
        candidates = emptyList()
        httpConfirm = false
    }
    val fetch: (Boolean) -> Unit = { allowHttp ->
        operationJob?.cancel()
        operationJob = viewModel.fetchWebsiteIcons(websiteUrl, allowHttp) { candidates = it }
    }
    if (iconBytes != null) {
        Box(Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
            PngIcon(iconBytes, Modifier.size(64.dp))
        }
    }
    Row(
        Modifier.fillMaxWidth().padding(bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedButton(
            onClick = { launcher.launch(arrayOf("image/*", "application/octet-stream")) },
            modifier = Modifier.weight(1f),
            enabled = !busy,
        ) {
            Icon(Icons.Outlined.PhotoLibrary, null)
            Spacer(Modifier.width(8.dp))
            Text(if (iconBytes == null) "选择图片" else "替换图片")
        }
        if (iconBytes != null) {
            IconButton(onClick = onRemove, enabled = !busy) { Icon(Icons.Outlined.Delete, "移除图标") }
        }
    }
    OutlinedButton(
        onClick = {
            if (websiteUrl.trim().startsWith("http://", ignoreCase = true)) httpConfirm = true
            else fetch(false)
        },
        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
        enabled = !busy,
    ) {
        Icon(Icons.Outlined.Language, null)
        Spacer(Modifier.width(8.dp))
        Text("从官网获取图标")
    }
    if (httpConfirm) {
        ConfirmActionSheet(
            title = "继续使用 HTTP？",
            text = "HTTP 不会加密传输，网站地址、网页和图标可能被监视或篡改。" +
                "密钥册不会发送账号、密码或备注。是否仍要继续？",
            confirmLabel = "仍要获取",
            onDismiss = { httpConfirm = false },
            onConfirm = { httpConfirm = false; fetch(true) },
        )
    }
    if (candidates.isNotEmpty()) {
        BottomSheetFrame(
            title = "选择官网图标",
            onDismiss = { candidates = emptyList() },
            content = {
                Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                    candidates.forEach { candidate ->
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                onIcon(candidate.icon)
                                candidates = emptyList()
                            }.padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            PngIcon(candidate.icon.pngBytes, Modifier.size(48.dp))
                            Spacer(Modifier.width(12.dp))
                            Text(candidate.source, modifier = Modifier.weight(1f), maxLines = 2)
                        }
                    }
                }
            },
            actions = {
                OutlinedButton(
                    onClick = { candidates = emptyList() },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("取消") }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountForm(
    route: AccountEditRoute,
    state: KeyBookUiState,
    viewModel: KeyBookViewModel,
    navController: NavController,
) {
    val existing = state.account?.takeIf { it.id == route.accountId }
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var login by remember { mutableStateOf(existing?.login.orEmpty()) }
    var password by remember { mutableStateOf(existing?.password.orEmpty()) }
    var note by remember { mutableStateOf(existing?.note.orEmpty()) }
    LaunchedEffect(route.accountId) { route.accountId?.let(viewModel::loadAccount) }
    LifecycleEventEffect(Lifecycle.Event.ON_START) { route.accountId?.let(viewModel::loadAccount) }
    LaunchedEffect(existing?.id) {
        if (existing != null && name.isEmpty() && login.isEmpty() && password.isEmpty()) {
            name = existing.name
            login = existing.login
            password = existing.password
            note = existing.note
        }
    }
    val nameError = validationError { InputValidation.normalizeName(name) }
    val loginError = validationError { InputValidation.validateRaw(login, 1, 320, "登录账号") }
    val passwordError = validationError { InputValidation.validateRaw(password, 1, 1024, "登录密码") }
    val noteError = validationError { InputValidation.normalizeNote(note) }
    RecordFormScaffold(
        title = if (route.accountId == null) "新增账号" else "编辑账号",
        navController = navController,
        isDirty = {
            name != existing?.name.orEmpty() || login != existing?.login.orEmpty() ||
                password != existing?.password.orEmpty() || note != existing?.note.orEmpty()
        },
        saveEnabled = nameError == null && loginError == null && passwordError == null &&
            noteError == null && !state.busy,
        onSave = {
            viewModel.saveAccount(route.appId, route.accountId, AccountDraft(name, login, password, note)) {
                navController.popBackStack()
            }
        },
    ) {
        OutlinedTextField(
            name,
            { name = it },
            label = { Text("账号名称") },
            modifier = Modifier.fillMaxWidth(),
            isError = nameError != null,
            supportingText = nameError?.let { message -> { Text(message) } },
        )
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            login,
            { login = it },
            label = { Text("登录账号") },
            modifier = Modifier.fillMaxWidth(),
            isError = loginError != null,
            supportingText = loginError?.let { message -> { Text(message) } },
        )
        Spacer(Modifier.height(16.dp))
        PasswordEditor("登录密码", password, { password = it }, state, viewModel, passwordError)
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            note,
            { note = it },
            label = { Text("备注（可选）") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
            isError = noteError != null,
            supportingText = noteError?.let { message -> { Text(message) } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SecretForm(
    route: SecretEditRoute,
    state: KeyBookUiState,
    viewModel: KeyBookViewModel,
    navController: NavController,
) {
    val existing = state.secretItems.firstOrNull { it.id == route.secretId }
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var password by remember { mutableStateOf(existing?.password.orEmpty()) }
    var note by remember { mutableStateOf(existing?.note.orEmpty()) }
    LaunchedEffect(route.accountId) { viewModel.loadAccount(route.accountId) }
    LifecycleEventEffect(Lifecycle.Event.ON_START) { viewModel.loadAccount(route.accountId) }
    LaunchedEffect(existing?.id) {
        if (existing != null && name.isEmpty() && password.isEmpty()) {
            name = existing.name
            password = existing.password
            note = existing.note
        }
    }
    val nameError = validationError { InputValidation.normalizeName(name) }
    val passwordError = validationError { InputValidation.validateRaw(password, 1, 1024, "密码") }
    val noteError = validationError { InputValidation.normalizeNote(note) }
    RecordFormScaffold(
        title = if (route.secretId == null) "新增附加密码项" else "编辑附加密码项",
        navController = navController,
        isDirty = {
            name != existing?.name.orEmpty() || password != existing?.password.orEmpty() ||
                note != existing?.note.orEmpty()
        },
        saveEnabled = nameError == null && passwordError == null && noteError == null && !state.busy,
        onSave = {
            viewModel.saveSecretItem(route.accountId, route.secretId, SecretItemDraft(name, password, note)) {
                navController.popBackStack()
            }
        },
    ) {
        OutlinedTextField(
            name,
            { name = it },
            label = { Text("自定义名称") },
            modifier = Modifier.fillMaxWidth(),
            isError = nameError != null,
            supportingText = nameError?.let { message -> { Text(message) } },
        )
        Spacer(Modifier.height(16.dp))
        PasswordEditor("密码", password, { password = it }, state, viewModel, passwordError)
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            note,
            { note = it },
            label = { Text("备注（可选）") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
            isError = noteError != null,
            supportingText = noteError?.let { message -> { Text(message) } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecordFormScaffold(
    title: String,
    navController: NavController,
    isDirty: () -> Boolean,
    saveEnabled: Boolean,
    onSave: () -> Unit,
    content: @Composable () -> Unit,
) {
    var discardConfirm by remember { mutableStateOf(false) }
    val requestBack = {
        if (isDirty()) discardConfirm = true else navController.popBackStack()
        Unit
    }
    BackHandler(onBack = requestBack)
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(title) },
            navigationIcon = {
                IconButton(onClick = requestBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回")
                }
            },
            actions = { TextButton(onClick = onSave, enabled = saveEnabled) { Text("保存") } },
        )
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        ) {
            content()
            Spacer(Modifier.height(24.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = requestBack, modifier = Modifier.weight(1f)) {
                    Text("取消")
                }
                Button(onClick = onSave, enabled = saveEnabled, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Outlined.Check, null)
                    Spacer(Modifier.width(8.dp))
                    Text("保存")
                }
            }
        }
    }
    if (discardConfirm) {
        ConfirmActionSheet(
            title = "放弃修改？",
            text = "未保存的内容将丢失。",
            confirmLabel = "放弃修改",
            dismissLabel = "继续编辑",
            onDismiss = { discardConfirm = false },
            onConfirm = { discardConfirm = false; navController.popBackStack() },
        )
    }
}

@Composable
private fun CommonRecordFields(
    name: String,
    onName: (String) -> Unit,
    website: String,
    onWebsite: (String) -> Unit,
    note: String,
    onNote: (String) -> Unit,
    nameError: String? = null,
    websiteError: String? = null,
    noteError: String? = null,
) {
    OutlinedTextField(
        name,
        onName,
        label = { Text("名称") },
        modifier = Modifier.fillMaxWidth(),
        isError = nameError != null,
        supportingText = nameError?.let { message -> { Text(message) } },
    )
    Spacer(Modifier.height(16.dp))
    OutlinedTextField(
        website,
        onWebsite,
        label = { Text("官网地址（可选）") },
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        isError = websiteError != null,
        supportingText = websiteError?.let { message -> { Text(message) } },
    )
    Spacer(Modifier.height(16.dp))
    OutlinedTextField(
        note,
        onNote,
        label = { Text("备注（可选）") },
        modifier = Modifier.fillMaxWidth(),
        minLines = 3,
        isError = noteError != null,
        supportingText = noteError?.let { message -> { Text(message) } },
    )
}

private inline fun validationError(block: () -> Unit): String? = try {
    block()
    null
} catch (error: IllegalArgumentException) {
    error.message ?: "输入无效"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PasswordEditor(
    label: String,
    value: String,
    onValue: (String) -> Unit,
    state: KeyBookUiState,
    viewModel: KeyBookViewModel,
    error: String? = null,
) {
    var visible by remember { mutableStateOf(false) }
    var replaceConfirm by remember { mutableStateOf(false) }
    var generated by remember { mutableStateOf("") }
    var generatorSheet by remember { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        visible = false
        generatorSheet = false
        replaceConfirm = false
        generated = ""
    }
    val acceptGenerated: (String) -> Unit = { candidate ->
        generated = candidate
        if (value.isEmpty()) {
            onValue(candidate)
            generated = ""
        } else {
            replaceConfirm = true
        }
    }
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        isError = error != null,
        supportingText = error?.let { message -> { Text(message) } },
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            autoCorrectEnabled = false,
        ),
        trailingIcon = {
            Row {
                IconButton(onClick = { visible = !visible }) {
                    Icon(if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, if (visible) "隐藏密码" else "显示密码")
                }
                IconButton(onClick = { generatorSheet = true }) { Icon(Icons.Outlined.AutoAwesome, "生成密码") }
            }
        },
    )
    if (replaceConfirm) {
        ConfirmActionSheet(
            title = "替换当前密码？",
            text = "当前字段已有内容，确认后才会替换草稿。",
            confirmLabel = "替换",
            onDismiss = { replaceConfirm = false; generated = "" },
            onConfirm = { onValue(generated); generated = ""; replaceConfirm = false },
        )
    }
    if (generatorSheet) {
        QuickGeneratorSheet(
            state = state,
            viewModel = viewModel,
            onDismiss = { generatorSheet = false },
            onGenerated = {
                generatorSheet = false
                acceptGenerated(it)
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickGeneratorSheet(
    state: KeyBookUiState,
    viewModel: KeyBookViewModel,
    onDismiss: () -> Unit,
    onGenerated: (String) -> Unit,
) {
    val generator = remember { PasswordGenerator() }
    val defaultRules = remember(state.defaultGenerator, state.generatorPresets) {
        when (state.defaultGenerator?.kind) {
            GeneratorSelectionKind.CUSTOM -> state.generatorPresets
                .firstOrNull { it.id == state.defaultGenerator.id }?.rules
            GeneratorSelectionKind.BUILTIN -> BuiltinGeneratorPresets.all
                .firstOrNull { it.id == state.defaultGenerator.id }?.rules
            null -> null
        } ?: BuiltinGeneratorPresets.standard.rules
    }
    var rules by remember(defaultRules) { mutableStateOf(defaultRules) }
    var lengthText by remember(defaultRules) { mutableStateOf(defaultRules.length.toString()) }
    var preview by remember { mutableStateOf(runCatching { generator.generate(defaultRules) }.getOrDefault("")) }
    var error by remember { mutableStateOf<String?>(null) }
    var saveName by remember { mutableStateOf<String?>(null) }
    var manager by remember { mutableStateOf(false) }
    var editor by remember { mutableStateOf<GeneratorEditorState?>(null) }
    var deleteId by remember { mutableStateOf<String?>(null) }
    val lengthError = if (lengthText.toIntOrNull() in 4..128) {
        null
    } else {
        "密码长度须为 4–128"
    }
    val rulesError = lengthError ?: validationError { generator.validate(rules) }
    val options = remember(state.generatorPresets, state.defaultGenerator) {
        buildList {
            BuiltinGeneratorPresets.all.forEach { preset ->
                add(
                    GeneratorOption(
                        GeneratorSelection(GeneratorSelectionKind.BUILTIN, preset.id),
                        preset.name,
                        preset.rules,
                        true,
                    ),
                )
            }
            state.generatorPresets.forEach { preset ->
                add(
                    GeneratorOption(
                        GeneratorSelection(GeneratorSelectionKind.CUSTOM, preset.id),
                        preset.name,
                        preset.rules,
                        false,
                    ),
                )
            }
        }.sortedWith(compareByDescending<GeneratorOption> { it.selection == state.defaultGenerator }
            .thenByDescending { it.builtin })
    }
    fun regenerate() {
        runCatching { generator.generate(rules) }
            .onSuccess { preview = it; error = null }
            .onFailure { error = it.message }
    }
    val scrollState = rememberScrollState()
    val upwardFlingBoundary = remember {
        object : NestedScrollConnection {
            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                // The scrollable form has reached its end. Do not let its remaining upward
                // velocity settle the sheet again; downward velocity may still dismiss it.
                return if (available.y < 0f) Velocity(0f, available.y) else Velocity.Zero
            }
        }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("quickGeneratorSheet"),
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier.fillMaxWidth().heightIn(max = 680.dp)
                .nestedScroll(upwardFlingBoundary)
                .verticalScroll(scrollState, overscrollEffect = null)
                .padding(horizontal = 20.dp).padding(bottom = 32.dp),
        ) {
            Text("生成密码", style = MaterialTheme.typography.titleLarge)
            Text("选择预设", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 16.dp))
            options.forEach { option ->
                Row(
                    Modifier.fillMaxWidth().clickable {
                        runCatching { generator.generate(option.rules) }
                            .onSuccess(onGenerated)
                            .onFailure { error = it.message }
                    }.padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(option.name, modifier = Modifier.weight(1f))
                    if (option.selection == state.defaultGenerator) {
                        Icon(Icons.Outlined.Star, "默认预设", modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                    }
                    Text("${option.rules.length} 位", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null)
                }
                HorizontalDivider()
            }
            Text("本次自定义", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("密码长度", modifier = Modifier.weight(1f))
                OutlinedTextField(
                    lengthText,
                    {
                        lengthText = it
                        it.toIntOrNull()?.takeIf { value -> value in 4..128 }?.let { value ->
                            rules = rules.copy(length = value)
                        }
                    },
                    modifier = Modifier.width(88.dp),
                    isError = lengthError != null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                )
            }
            Slider(
                value = rules.length.toFloat(),
                onValueChange = {
                    val length = it.toInt()
                    rules = rules.copy(length = length)
                    lengthText = length.toString()
                },
                valueRange = 4f..128f,
                steps = 123,
            )
            RuleSwitch("小写字母 a–z", rules.lower) { rules = rules.copy(lower = it) }
            RuleSwitch("大写字母 A–Z", rules.upper) { rules = rules.copy(upper = it) }
            RuleSwitch("数字 0–9", rules.digits) { rules = rules.copy(digits = it) }
            RuleSwitch("符号", rules.symbols) { rules = rules.copy(symbols = it) }
            if (rules.symbols) {
                OutlinedTextField(
                    rules.symbolChars,
                    { rules = rules.copy(symbolChars = it) },
                    label = { Text("符号集") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            RuleSwitch("排除易混字符", rules.excludeAmbiguous) { rules = rules.copy(excludeAmbiguous = it) }
            RuleSwitch("保证每类至少一个", rules.guaranteeEachCategory) {
                rules = rules.copy(guaranteeEachCategory = it)
            }
            if (preview.isNotEmpty()) {
                Text(
                    "•".repeat(preview.length.coerceAtMost(24)),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    fontFamily = FontFamily.Monospace,
                )
            }
            (rulesError ?: error)?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = ::regenerate,
                    enabled = rulesError == null,
                    modifier = Modifier.weight(1f),
                ) { Text("重新生成") }
                Button(
                    onClick = {
                        runCatching { generator.generate(rules) }
                            .onSuccess {
                                preview = it
                                error = null
                                onGenerated(it)
                            }
                            .onFailure { error = it.message }
                    },
                    enabled = rulesError == null,
                    modifier = Modifier.weight(1f),
                ) { Text("应用") }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = { saveName = "" },
                    enabled = rulesError == null,
                    modifier = Modifier.weight(1f),
                ) { Text("另存为预设") }
                TextButton(onClick = { manager = true }, modifier = Modifier.weight(1f)) {
                    Text("管理预设")
                }
            }
        }
    }
    saveName?.let { current ->
        val generator = remember { PasswordGenerator() }
        val normalizedName = runCatching { InputValidation.normalizeName(current, 40) }.getOrNull()
        val nameError = validationError { InputValidation.normalizeName(current, 40) }
            ?: if (state.generatorPresets.any {
                    it.name.lowercase(java.util.Locale.ROOT) == normalizedName?.lowercase(java.util.Locale.ROOT)
                } || BuiltinGeneratorPresets.all.any {
                    it.name.lowercase(java.util.Locale.ROOT) == normalizedName?.lowercase(java.util.Locale.ROOT)
                }
            ) {
                "预设名称不能重复"
            } else if (state.generatorPresets.size >= 100) {
                "最多保存 100 个自定义预设"
            } else null
        val saveRulesError = validationError { generator.validate(rules) }
        BottomSheetFrame(
            title = "另存为预设",
            onDismiss = { saveName = null },
            content = {
                OutlinedTextField(
                    current,
                    { saveName = it },
                    label = { Text("预设名称") },
                    modifier = Modifier.fillMaxWidth(),
                    isError = nameError != null,
                    supportingText = nameError?.let { message -> { Text(message) } },
                )
                saveRulesError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            },
            actions = {
                OutlinedButton(onClick = { saveName = null }, modifier = Modifier.weight(1f)) {
                    Text("取消")
                }
                Button(
                    onClick = {
                        viewModel.saveGeneratorPreset(null, GeneratorPresetDraft(current, rules)) {
                            saveName = null
                        }
                    },
                    enabled = nameError == null && saveRulesError == null && !state.busy,
                    modifier = Modifier.weight(1f),
                ) { Text("保存") }
            },
        )
    }
    if (manager) {
        BottomSheetFrame(
            title = "管理生成器预设",
            onDismiss = { manager = false },
            content = {
                Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                    options.forEach { option ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(option.name)
                                Text(
                                    if (option.builtin) "内置 · ${option.rules.length} 位" else "自定义 · ${option.rules.length} 位",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(
                                onClick = { viewModel.setDefaultGenerator(option.selection) },
                                enabled = option.selection != state.defaultGenerator,
                            ) {
                                Icon(
                                    if (option.selection == state.defaultGenerator) Icons.Outlined.Star else Icons.Outlined.StarOutline,
                                    if (option.selection == state.defaultGenerator) "当前默认预设" else "设为默认预设",
                                )
                            }
                            if (option.builtin) {
                                IconButton(onClick = {
                                    manager = false
                                    editor = GeneratorEditorState(null, "", option.rules)
                                }) { Icon(Icons.Outlined.Add, "基于此新建") }
                            } else {
                                IconButton(onClick = {
                                    manager = false
                                    editor = GeneratorEditorState(option.selection.id, option.name, option.rules)
                                }) { Icon(Icons.Outlined.Edit, "编辑${option.name}") }
                                IconButton(onClick = {
                                    manager = false
                                    deleteId = option.selection.id
                                }) { Icon(Icons.Outlined.Delete, "删除${option.name}") }
                            }
                        }
                        HorizontalDivider()
                    }
                }
            },
            actions = {
                Button(onClick = { manager = false }, modifier = Modifier.fillMaxWidth()) {
                    Text("完成")
                }
            },
        )
    }
    editor?.let { editing ->
        GeneratorPresetEditorDialog(
            initial = editing,
            existingPresets = state.generatorPresets.map { it.id to it.name },
            customPresetCount = state.generatorPresets.size,
            busy = state.busy,
            onDismiss = { editor = null },
            onSave = { id, name, presetRules ->
                viewModel.saveGeneratorPreset(id, GeneratorPresetDraft(name, presetRules)) {
                    editor = null
                }
            },
        )
    }
    deleteId?.let { id ->
        val name = state.generatorPresets.firstOrNull { it.id == id }?.name.orEmpty()
        ConfirmDelete(
            "删除生成器预设？",
            "将删除“$name”，已生成或已保存的密码不会改变。",
            onDismiss = { deleteId = null },
            onConfirm = {
                deleteId = null
                viewModel.deleteGeneratorPreset(id)
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GeneratorScreen(
    openPresetManager: Boolean,
    state: KeyBookUiState,
    viewModel: KeyBookViewModel,
    clipboard: SensitiveClipboard = hiltViewModel<ClipboardViewModel>().clipboard,
) {
    val generator = remember { PasswordGenerator() }
    var rules by remember { mutableStateOf(BuiltinGeneratorPresets.standard.rules) }
    var lengthText by remember { mutableStateOf(BuiltinGeneratorPresets.standard.rules.length.toString()) }
    var result by remember { mutableStateOf(generator.generate(rules)) }
    var visible by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var presetMenu by remember { mutableStateOf(false) }
    var manager by remember(openPresetManager) { mutableStateOf(openPresetManager) }
    var editor by remember { mutableStateOf<GeneratorEditorState?>(null) }
    var deleteId by remember { mutableStateOf<String?>(null) }
    var appliedWorkspace by remember { mutableStateOf<String?>(null) }
    val options = remember(state.generatorPresets, state.defaultGenerator) {
        buildList {
            BuiltinGeneratorPresets.all.forEach { preset ->
                add(
                    GeneratorOption(
                        GeneratorSelection(GeneratorSelectionKind.BUILTIN, preset.id),
                        preset.name,
                        preset.rules,
                        true,
                    ),
                )
            }
            state.generatorPresets.forEach { preset ->
                add(
                    GeneratorOption(
                        GeneratorSelection(GeneratorSelectionKind.CUSTOM, preset.id),
                        preset.name,
                        preset.rules,
                        false,
                    ),
                )
            }
        }.sortedWith(compareByDescending<GeneratorOption> { it.selection == state.defaultGenerator }
            .thenByDescending { it.builtin })
    }
    var selected by remember { mutableStateOf<GeneratorSelection?>(null) }
    val lengthError = if (lengthText.toIntOrNull() in 4..128) {
        null
    } else {
        "密码长度须为 4–128"
    }
    val rulesError = lengthError ?: validationError { generator.validate(rules) }
    LaunchedEffect(state.currentWorkspaceId, state.defaultGenerator, options) {
        if (appliedWorkspace != state.currentWorkspaceId && state.defaultGenerator != null) {
            appliedWorkspace = state.currentWorkspaceId
            selected = state.defaultGenerator
            options.firstOrNull { it.selection == selected }?.let {
                rules = it.rules
                lengthText = it.rules.length.toString()
                result = generator.generate(it.rules)
            }
        }
    }
    fun regenerate() {
        runCatching { generator.generate(rules) }
            .onSuccess { result = it; visible = false; error = null }
            .onFailure { error = it.message }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        visible = false
        result = ""
    }
    LifecycleEventEffect(Lifecycle.Event.ON_START) {
        if (result.isEmpty()) regenerate()
    }
    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("密码生成器") })
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
            Text("生成规则", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Box(Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = { presetMenu = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        options.firstOrNull { it.selection == selected }?.name ?: "本次自定义",
                        modifier = Modifier.weight(1f),
                    )
                    Icon(Icons.Outlined.KeyboardArrowDown, null)
                }
                DropdownMenu(
                    expanded = presetMenu,
                    onDismissRequest = { presetMenu = false },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    options.forEach { option ->
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(option.name, modifier = Modifier.weight(1f))
                                    if (option.selection == state.defaultGenerator) {
                                        Icon(Icons.Outlined.Star, "默认预设", modifier = Modifier.size(18.dp))
                                    }
                                }
                            },
                            onClick = {
                                selected = option.selection
                                rules = option.rules
                                lengthText = option.rules.length.toString()
                                presetMenu = false
                                regenerate()
                            },
                        )
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = { editor = GeneratorEditorState(null, "", rules) },
                    modifier = Modifier.weight(1f),
                ) { Text("另存预设") }
                OutlinedButton(onClick = { manager = true }, modifier = Modifier.weight(1f)) {
                    Text("管理预设")
                }
            }
            Text("生成结果", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant).padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (visible) result else "•".repeat(result.length.coerceAtMost(24)),
                    modifier = Modifier.weight(1f),
                    fontFamily = FontFamily.Monospace,
                )
                IconButton(onClick = { visible = !visible }) {
                    Icon(
                        if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                        if (visible) "隐藏生成结果" else "显示生成结果",
                    )
                }
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = ::regenerate, enabled = rulesError == null, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Outlined.Refresh, null)
                    Spacer(Modifier.width(6.dp))
                    Text("重新生成")
                }
                Button(
                    onClick = { clipboard.copy("生成的密码", result, true) },
                    enabled = result.isNotEmpty() && rulesError == null,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Outlined.ContentCopy, null)
                    Spacer(Modifier.width(6.dp))
                    Text("复制密码")
                }
            }
            (rulesError ?: error)?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("密码长度", modifier = Modifier.weight(1f))
                OutlinedTextField(
                    value = lengthText,
                    onValueChange = { value ->
                        lengthText = value
                        value.toIntOrNull()?.takeIf { it in 4..128 }?.let {
                            rules = rules.copy(length = it)
                            selected = null
                        }
                    },
                    modifier = Modifier.width(88.dp),
                    isError = lengthError != null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                )
            }
            Slider(
                value = rules.length.toFloat(),
                onValueChange = {
                    val length = it.toInt()
                    rules = rules.copy(length = length)
                    lengthText = length.toString()
                    selected = null
                },
                valueRange = 4f..128f,
                steps = 123,
            )
            RuleSwitch("小写字母 a–z", rules.lower) { rules = rules.copy(lower = it); selected = null }
            RuleSwitch("大写字母 A–Z", rules.upper) { rules = rules.copy(upper = it); selected = null }
            RuleSwitch("数字 0–9", rules.digits) { rules = rules.copy(digits = it); selected = null }
            RuleSwitch("符号", rules.symbols) { rules = rules.copy(symbols = it); selected = null }
            if (rules.symbols) {
                OutlinedTextField(
                    rules.symbolChars,
                    { rules = rules.copy(symbolChars = it); selected = null },
                    label = { Text("符号集") },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                )
            }
            RuleSwitch("排除易混字符", rules.excludeAmbiguous) { rules = rules.copy(excludeAmbiguous = it); selected = null }
            RuleSwitch("保证每类至少一个", rules.guaranteeEachCategory) { rules = rules.copy(guaranteeEachCategory = it); selected = null }
        }
    }
    if (manager) {
        BottomSheetFrame(
            title = "管理生成器预设",
            onDismiss = { manager = false },
            content = {
                Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                    options.forEach { option ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(option.name)
                                Text(
                                    if (option.builtin) "内置 · ${option.rules.length} 位" else "自定义 · ${option.rules.length} 位",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(
                                onClick = { viewModel.setDefaultGenerator(option.selection) },
                                enabled = option.selection != state.defaultGenerator,
                            ) {
                                Icon(
                                    if (option.selection == state.defaultGenerator) Icons.Outlined.Star else Icons.Outlined.StarOutline,
                                    if (option.selection == state.defaultGenerator) "当前默认预设" else "设为默认预设",
                                )
                            }
                            if (option.builtin) {
                                IconButton(onClick = {
                                    manager = false
                                    editor = GeneratorEditorState(null, "", option.rules)
                                }) { Icon(Icons.Outlined.Add, "基于此新建") }
                            } else {
                                IconButton(onClick = {
                                    manager = false
                                    editor = GeneratorEditorState(option.selection.id, option.name, option.rules)
                                }) { Icon(Icons.Outlined.Edit, "编辑${option.name}") }
                                IconButton(onClick = { manager = false; deleteId = option.selection.id }) {
                                    Icon(Icons.Outlined.Delete, "删除${option.name}")
                                }
                            }
                        }
                        HorizontalDivider()
                    }
                }
            },
            actions = {
                Button(onClick = { manager = false }, modifier = Modifier.fillMaxWidth()) {
                    Text("完成")
                }
            },
        )
    }
    editor?.let { editing ->
        GeneratorPresetEditorDialog(
            initial = editing,
            existingPresets = state.generatorPresets.map { it.id to it.name },
            customPresetCount = state.generatorPresets.size,
            busy = state.busy,
            onDismiss = { editor = null },
            onSave = { id, name, presetRules ->
                viewModel.saveGeneratorPreset(id, GeneratorPresetDraft(name, presetRules)) {
                    editor = null
                }
            },
        )
    }
    deleteId?.let { id ->
        val name = state.generatorPresets.firstOrNull { it.id == id }?.name.orEmpty()
        ConfirmDelete(
            "删除生成器预设？",
            "将删除“$name”，已生成或已保存的密码不会改变。",
            onDismiss = { deleteId = null },
            onConfirm = {
                deleteId = null
                viewModel.deleteGeneratorPreset(id)
            },
        )
    }
}

private data class GeneratorOption(
    val selection: GeneratorSelection,
    val name: String,
    val rules: PasswordRules,
    val builtin: Boolean,
)

private data class GeneratorEditorState(
    val id: String?,
    val name: String,
    val rules: PasswordRules,
)

@Composable
private fun GeneratorPresetEditorDialog(
    initial: GeneratorEditorState,
    existingPresets: List<Pair<String, String>>,
    customPresetCount: Int,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSave: (String?, String, PasswordRules) -> Unit,
) {
    var name by remember(initial) { mutableStateOf(initial.name) }
    var rules by remember(initial) { mutableStateOf(initial.rules) }
    var lengthText by remember(initial) { mutableStateOf(initial.rules.length.toString()) }
    val generator = remember { PasswordGenerator() }
    val normalizedName = runCatching { InputValidation.normalizeName(name, 40) }.getOrNull()
    val nameError = validationError { InputValidation.normalizeName(name, 40) }
        ?: if (existingPresets.any { (id, existingName) ->
                id != initial.id && existingName.lowercase(java.util.Locale.ROOT) ==
                    normalizedName?.lowercase(java.util.Locale.ROOT)
            } || BuiltinGeneratorPresets.all.any {
                it.name.lowercase(java.util.Locale.ROOT) == normalizedName?.lowercase(java.util.Locale.ROOT)
            }
        ) {
            "预设名称不能重复"
        } else if (initial.id == null && customPresetCount >= 100) {
            "最多保存 100 个自定义预设"
        } else null
    val lengthError = if (lengthText.toIntOrNull() in 4..128) {
        null
    } else {
        "密码长度须为 4–128"
    }
    val rulesError = lengthError ?: validationError { generator.validate(rules) }
    BottomSheetFrame(
        title = if (initial.id == null) "新建生成器预设" else "编辑生成器预设",
        onDismiss = onDismiss,
        content = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    name,
                    { name = it },
                    label = { Text("预设名称") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = nameError != null,
                    supportingText = nameError?.let { message -> { Text(message) } },
                )
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("密码长度", modifier = Modifier.weight(1f))
                    OutlinedTextField(
                        lengthText,
                        {
                            lengthText = it
                            it.toIntOrNull()?.takeIf { value -> value in 4..128 }?.let { value ->
                                rules = rules.copy(length = value)
                            }
                        },
                        modifier = Modifier.width(88.dp),
                        isError = lengthError != null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                    )
                }
                Slider(
                    value = rules.length.toFloat(),
                    onValueChange = {
                        val length = it.toInt()
                        rules = rules.copy(length = length)
                        lengthText = length.toString()
                    },
                    valueRange = 4f..128f,
                    steps = 123,
                )
                RuleSwitch("小写字母 a–z", rules.lower) { rules = rules.copy(lower = it) }
                RuleSwitch("大写字母 A–Z", rules.upper) { rules = rules.copy(upper = it) }
                RuleSwitch("数字 0–9", rules.digits) { rules = rules.copy(digits = it) }
                RuleSwitch("符号", rules.symbols) { rules = rules.copy(symbols = it) }
                if (rules.symbols) {
                    OutlinedTextField(
                        rules.symbolChars,
                        { rules = rules.copy(symbolChars = it) },
                        label = { Text("符号集") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                RuleSwitch("排除易混字符", rules.excludeAmbiguous) { rules = rules.copy(excludeAmbiguous = it) }
                RuleSwitch("保证每类至少一个", rules.guaranteeEachCategory) {
                    rules = rules.copy(guaranteeEachCategory = it)
                }
                rulesError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
                }
            }
        },
        actions = {
            OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                Text("取消")
            }
            Button(
                onClick = { onSave(initial.id, name, rules) },
                enabled = nameError == null && rulesError == null && !busy,
                modifier = Modifier.weight(1f),
            ) { Text("保存") }
        },
    )
}

@Composable
private fun RuleSwitch(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 62.dp).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Checkbox(checked, onCheckedChange = onChecked)
    }
    HorizontalDivider()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(
    state: KeyBookUiState,
    viewModel: KeyBookViewModel,
    navController: NavController,
) {
    val theme by viewModel.themeMode.collectAsStateWithLifecycle()
    var workspaceDialog by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("设置") })
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            SectionHeader("外观", "", horizontalPadding = 0.dp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeMode.entries.forEach { mode ->
                    val label = when (mode) {
                        ThemeMode.FOLLOW_SYSTEM -> "随系统"
                        ThemeMode.LIGHT -> "浅色"
                        ThemeMode.DARK -> "深色"
                    }
                    if (theme == mode) {
                        Button(onClick = { viewModel.setTheme(mode) }, modifier = Modifier.weight(1f)) { Text(label) }
                    } else {
                        OutlinedButton(onClick = { viewModel.setTheme(mode) }, modifier = Modifier.weight(1f)) { Text(label) }
                    }
                }
            }
            SectionHeader("数据与资料", "", horizontalPadding = 0.dp)
            SettingRow(Icons.Outlined.Apartment, "工作空间", state.currentWorkspace?.name.orEmpty()) { workspaceDialog = true }
            SettingRow(Icons.Outlined.AutoAwesome, "生成器预设", "新建、编辑与设置默认规则") {
                navController.navigateRoot(GeneratorRoute(openPresetManager = true))
            }
            SettingRow(Icons.Outlined.Category, "预定义项", "企业与应用") { navController.navigate(CatalogRoute) }
            SettingRow(Icons.Outlined.FileUpload, "导入数据", "从备份文件恢复") {
                navController.navigate(ImportRoute)
            }
            SettingRow(Icons.Outlined.FileDownload, "导出数据", "按工作空间、企业或应用选择") {
                navController.navigate(ExportRoute())
            }
            SectionHeader("安全与关于", "", horizontalPadding = 0.dp)
            SettingRow(Icons.Outlined.Shield, "本地数据与安全", "应用锁由手机系统管理") {
                navController.navigate(SecurityRoute)
            }
            SettingRow(Icons.Outlined.Info, "关于 / 更新", BuildConfig.VERSION_NAME) { navController.navigate(AboutRoute) }
        }
    }
    if (workspaceDialog) WorkspaceManager(state, viewModel) { workspaceDialog = false }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CatalogScreen(
    state: KeyBookUiState,
    viewModel: KeyBookViewModel,
    navController: NavController,
) {
    var type by remember { mutableStateOf(CatalogType.COMPANY) }
    var selected by remember { mutableStateOf<CatalogEntryUi?>(null) }
    var deleteCustom by remember { mutableStateOf<CatalogEntryUi?>(null) }
    var restoreBuiltin by remember { mutableStateOf<CatalogEntryUi?>(null) }
    val entries = state.catalogEntries.filter { it.type == type }
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("预定义项") },
            navigationIcon = { BackButton(navController) },
            actions = {
                IconButton(onClick = { navController.navigate(CatalogEditRoute(type.name)) }) {
                    Icon(
                        Icons.Outlined.Add,
                        "新增${if (type == CatalogType.COMPANY) "企业" else "应用"}预定义项",
                    )
                }
            },
        )
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf(CatalogType.COMPANY to "企业", CatalogType.APP to "应用").forEach { (value, label) ->
                if (type == value) {
                    Button(onClick = { type = value }, modifier = Modifier.weight(1f)) { Text(label) }
                } else {
                    OutlinedButton(onClick = { type = value }, modifier = Modifier.weight(1f)) { Text(label) }
                }
            }
        }
        SectionHeader(
            if (type == CatalogType.COMPANY) "企业预定义项" else "应用预定义项",
            "${entries.size} 项",
        )
        LazyColumn(Modifier.weight(1f).testTag("catalogList")) {
            if (entries.isEmpty()) {
                item {
                    Text(
                        "暂无预定义项",
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(entries, key = { it.id }) { entry ->
                Row(
                    Modifier.fillMaxWidth().clickable { selected = entry }
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    NameAvatar(entry.name, null, entry.iconPng)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(entry.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            when {
                                entry.isBuiltin && entry.isModified -> "内置 · 已修改"
                                entry.isBuiltin -> "内置"
                                else -> "自定义"
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null)
                }
                HorizontalDivider(Modifier.padding(horizontal = 20.dp))
            }
        }
    }
    selected?.let { entry ->
        BottomSheetFrame(
            title = entry.name,
            onDismiss = { selected = null },
            content = {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    NameAvatar(entry.name, null, entry.iconPng)
                }
                Text(
                    if (entry.websiteUrl.isEmpty()) "未设置官网" else entry.websiteUrl,
                    modifier = Modifier.padding(top = 16.dp),
                )
                if (entry.note.isNotEmpty()) Text(entry.note, modifier = Modifier.padding(top = 12.dp))
                Text(
                    if (entry.isBuiltin) {
                        if (entry.isModified) "内置配置 · 当前空间已修改" else "内置配置"
                    } else {
                        "当前空间自定义配置"
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
                )
                if (entry.isBuiltin) {
                    OutlinedButton(
                        onClick = {
                            selected = null
                            navController.navigate(CatalogEditRoute(entry.type.name, copyFromId = entry.id))
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("基于此新建") }
                    if (entry.isModified) {
                        TextButton(
                            onClick = { selected = null; restoreBuiltin = entry },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("恢复默认") }
                    }
                } else {
                    TextButton(
                        onClick = { selected = null; deleteCustom = entry },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("删除") }
                }
            },
            actions = {
                OutlinedButton(onClick = { selected = null }, modifier = Modifier.weight(1f)) {
                    Text("关闭")
                }
                Button(
                    onClick = {
                        selected = null
                        navController.navigate(CatalogEditRoute(entry.type.name, targetId = entry.id))
                    },
                    modifier = Modifier.weight(1f),
                ) { Text("编辑配置") }
            },
        )
    }
    deleteCustom?.let { entry ->
        ConfirmDelete(
            "删除预定义项？",
            "将删除“${entry.name}”，已创建的实际记录不会改变。",
            onDismiss = { deleteCustom = null },
            onConfirm = {
                deleteCustom = null
                viewModel.deleteCatalogPreset(entry.id)
            },
        )
    }
    restoreBuiltin?.let { entry ->
        ConfirmActionSheet(
            title = "恢复内置默认值？",
            text = "将移除“${entry.name}”在当前空间的本地修改，不影响已创建的实际记录。",
            confirmLabel = "恢复默认",
            onDismiss = { restoreBuiltin = null },
            onConfirm = {
                restoreBuiltin = null
                viewModel.restoreBuiltinCatalogDefault(entry.id)
            },
        )
    }
}

private data class CatalogEditorState(
    val id: String?,
    val builtinId: String?,
    val type: CatalogType,
    val name: String,
    val websiteUrl: String,
    val note: String,
    val previewIcon: ByteArray?,
    val initialNewIcon: IconDraft?,
) {
    companion object {
        fun blank(type: CatalogType) = CatalogEditorState(null, null, type, "", "", "", null, null)

        fun edit(entry: CatalogEntryUi) = CatalogEditorState(
            id = entry.id.takeUnless { entry.isBuiltin },
            builtinId = entry.id.takeIf { entry.isBuiltin },
            type = entry.type,
            name = entry.name,
            websiteUrl = entry.websiteUrl,
            note = entry.note,
            previewIcon = entry.iconPng,
            initialNewIcon = if (entry.isBuiltin && !entry.isModified && entry.iconPng != null) {
                IconDraft(entry.iconPng, entry.iconWidth, entry.iconHeight)
            } else null,
        )

        fun copyOf(entry: CatalogEntryUi) = CatalogEditorState(
            id = null,
            builtinId = null,
            type = entry.type,
            name = entry.name,
            websiteUrl = entry.websiteUrl,
            note = entry.note,
            previewIcon = entry.iconPng,
            initialNewIcon = entry.iconPng?.let { IconDraft(it, entry.iconWidth, entry.iconHeight) },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CatalogForm(
    route: CatalogEditRoute,
    state: KeyBookUiState,
    viewModel: KeyBookViewModel,
    navController: NavController,
) {
    val type = CatalogType.entries.firstOrNull { it.name == route.type }
    LaunchedEffect(type, route.sourceRecordId) {
        if (type == CatalogType.APP && route.sourceRecordId != null) viewModel.loadAllApps()
    }
    val initial = when {
        type == null -> null
        route.targetId != null -> state.catalogEntries
            .firstOrNull { it.id == route.targetId && it.type == type }
            ?.let(CatalogEditorState::edit)
        route.copyFromId != null -> state.catalogEntries
            .firstOrNull { it.id == route.copyFromId && it.type == type }
            ?.let(CatalogEditorState::copyOf)
        route.sourceRecordId != null && type == CatalogType.COMPANY -> state.companies
            .firstOrNull { it.company.id == route.sourceRecordId }
            ?.company
            ?.let { company ->
                val asset = company.iconAssetId?.let(state.icons::get)
                CatalogEditorState(
                    id = null,
                    builtinId = null,
                    type = type,
                    name = company.name,
                    websiteUrl = company.websiteUrl,
                    note = company.note,
                    previewIcon = asset?.pngBytes,
                    initialNewIcon = asset?.let { IconDraft(it.pngBytes, it.width, it.height) },
                )
            }
        route.sourceRecordId != null && type == CatalogType.APP ->
            (state.apps.firstOrNull { it.app.id == route.sourceRecordId }?.app
                ?: state.allApps.firstOrNull { it.id == route.sourceRecordId })
                ?.let { app ->
                    val asset = app.iconAssetId?.let(state.icons::get)
                    CatalogEditorState(
                        id = null,
                        builtinId = null,
                        type = type,
                        name = app.name,
                        websiteUrl = app.websiteUrl,
                        note = app.note,
                        previewIcon = asset?.pngBytes,
                        initialNewIcon = asset?.let { IconDraft(it.pngBytes, it.width, it.height) },
                    )
                }
        else -> CatalogEditorState.blank(type)
    }
    if (initial == null) {
        Column(Modifier.fillMaxSize()) {
            TopAppBar(title = { Text("预定义项") }, navigationIcon = { BackButton(navController) })
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(if (type == null) "预定义项类型无效" else "正在读取预定义项…")
            }
        }
        return
    }
    CatalogEditorPage(initial, state, viewModel, navController)
}

@Composable
private fun CatalogEditorPage(
    initial: CatalogEditorState,
    state: KeyBookUiState,
    viewModel: KeyBookViewModel,
    navController: NavController,
) {
    var name by remember(initial) { mutableStateOf(initial.name) }
    var website by remember(initial) { mutableStateOf(initial.websiteUrl) }
    var note by remember(initial) { mutableStateOf(initial.note) }
    var newIcon by remember(initial) { mutableStateOf(initial.initialNewIcon) }
    var removeIcon by remember(initial) { mutableStateOf(false) }
    val nameError = validationError { InputValidation.normalizeName(name) }
    val websiteError = validationError { InputValidation.validateWebsite(website) }
    val noteError = validationError { InputValidation.normalizeNote(note) }
    RecordFormScaffold(
        title = when {
            initial.builtinId != null -> "编辑内置配置"
            initial.id != null -> "编辑预定义项"
            else -> "新建预定义项"
        },
        navController = navController,
        isDirty = {
            name != initial.name || website != initial.websiteUrl || note != initial.note ||
                newIcon !== initial.initialNewIcon || removeIcon
        },
        saveEnabled = nameError == null && websiteError == null && noteError == null && !state.busy,
        onSave = {
            val draft = CatalogPresetDraft(
                type = initial.type,
                name = name,
                note = note,
                websiteUrl = website,
                newIcon = newIcon,
                removeIcon = removeIcon,
            )
            if (initial.builtinId != null) {
                viewModel.saveBuiltinCatalogOverride(initial.builtinId, draft) {
                    navController.popBackStack()
                }
            } else {
                viewModel.saveCatalogPreset(initial.id, draft) {
                    navController.popBackStack()
                }
            }
        },
    ) {
        IconEditor(
            iconBytes = newIcon?.pngBytes ?: initial.previewIcon?.takeUnless { removeIcon },
            viewModel = viewModel,
            onIcon = { newIcon = it; removeIcon = false },
            onRemove = { newIcon = null; removeIcon = true },
            websiteUrl = website,
            busy = state.busy,
        )
        CommonRecordFields(
            name,
            { name = it },
            website,
            { website = it },
            note,
            { note = it },
            nameError,
            websiteError,
            noteError,
        )
    }
}

@Composable
private fun CatalogTemplatePicker(
    entries: List<CatalogEntryUi>,
    onDismiss: () -> Unit,
    onSelect: (CatalogEntryUi) -> Unit,
) {
    BottomSheetFrame(
        title = "选择预定义项",
        onDismiss = onDismiss,
        content = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                entries.forEach { entry ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onSelect(entry) }.padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        NameAvatar(entry.name, null, entry.iconPng)
                        Spacer(Modifier.width(12.dp))
                        Text(entry.name, modifier = Modifier.weight(1f))
                    }
                }
            }
        },
        actions = {
            OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("取消") }
        },
    )
}

@Composable
private fun PngIcon(bytes: ByteArray, modifier: Modifier = Modifier) {
    val bitmap = remember(bytes) {
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
    }
    if (bitmap != null) {
        Image(bitmap, null, modifier = modifier, contentScale = ContentScale.Fit)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SecurityScreen(navController: NavController) {
    val rootWarning = remember { RootDetector.isLikelyRooted() }
    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("本地数据与安全") }, navigationIcon = { BackButton(navController) })
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            if (rootWarning) {
                Text(
                    "设备安全提示",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                Text(
                    "检测到常见 Root 迹象。Root、系统被攻陷、恶意输入法或进程注入可能绕过应用隔离。" +
                        "此检测仅供提示，不会请求 Root 权限，也不会阻止继续使用。",
                    modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
                )
                HorizontalDivider()
            }

            SectionHeader("访问保护", "", horizontalPadding = 0.dp)
            Text(
                "密钥册不设置主密码、生物识别或自动锁定。启动访问由手机系统的应用锁和设备解锁负责；" +
                    "能打开应用的人可以查看所有工作空间。",
            )

            SectionHeader("本地加密", "", horizontalPadding = 0.dp)
            Text(
                "记录、图标、预定义项和生成器配置保存在应用私有目录，并按工作空间使用独立密钥加密。" +
                    "数据库仍会暴露记录数量、父子关系和密文长度。敏感窗口禁止截图、录屏和最近任务预览。",
            )

            SectionHeader("剪贴板", "", horizontalPadding = 0.dp)
            Text(
                "复制的密码会标记为敏感内容。密钥册会在 60 秒后尽力清除仍由本应用写入的同一条内容；" +
                    "系统、输入法或其他应用在此之前仍可能读取剪贴板。",
            )

            SectionHeader("备份与恢复", "", horizontalPadding = 0.dp)
            Text(
                "系统云备份和设备迁移已关闭。卸载、清除应用数据或设备密钥丢失后，" +
                    "只能通过之前主动导出的备份文件恢复。建议使用文件密码加密备份，并在设备外妥善保存。",
                modifier = Modifier.padding(bottom = 24.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AboutScreen(
    state: KeyBookUiState,
    viewModel: KeyBookViewModel,
    navController: NavController,
) {
    val context = LocalContext.current
    var updateJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    DisposableEffect(Unit) { onDispose { updateJob?.cancel() } }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        updateJob?.cancel()
        updateJob = null
    }
    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("关于 / 更新") }, navigationIcon = { BackButton(navController) })
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(52.dp).clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Outlined.Key, null, tint = MaterialTheme.colorScheme.onPrimary) }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text("密钥册", style = MaterialTheme.typography.titleLarge)
                    Text("版本 ${BuildConfig.VERSION_NAME}（${BuildConfig.VERSION_CODE}）")
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 20.dp))
            Text("项目仓库", color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(
                onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(BuildConfig.GITHUB_REPOSITORY_URL)))
                },
            ) { Text(BuildConfig.GITHUB_REPOSITORY_URL) }
            Spacer(Modifier.height(16.dp))
            val update = state.update
            Text(
                when (update.phase) {
                    UpdatePhase.IDLE -> "尚未检查更新"
                    UpdatePhase.CHECKING -> "正在检查更新…"
                    UpdatePhase.UP_TO_DATE -> "已是最新版本"
                    UpdatePhase.AVAILABLE -> "发现新版本"
                    UpdatePhase.INCOMPATIBLE -> "发现新版本，但当前系统不兼容"
                    UpdatePhase.DOWNLOADING -> "正在下载并校验 APK…"
                    UpdatePhase.FAILED -> update.message ?: "更新操作失败"
                    UpdatePhase.HANDED_TO_INSTALLER -> "已交给 Android 系统安装器"
                },
                color = if (update.phase == UpdatePhase.FAILED) {
                    MaterialTheme.colorScheme.error
                } else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            update.manifest?.let { manifest ->
                Column(Modifier.fillMaxWidth().padding(top = 16.dp)) {
                    Text("${manifest.versionName}（${manifest.versionCode}）", style = MaterialTheme.typography.titleMedium)
                    Text(manifest.publishedAt, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (manifest.releaseNotes.isNotEmpty()) {
                        Text(manifest.releaseNotes, modifier = Modifier.padding(top = 12.dp))
                    }
                }
            }
            if (update.phase == UpdatePhase.AVAILABLE ||
                update.phase == UpdatePhase.FAILED && update.manifest != null
            ) {
                Button(
                    onClick = {
                        updateJob?.cancel()
                        updateJob = viewModel.downloadUpdate { intent -> context.startActivity(intent) }
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                ) { Text(if (update.phase == UpdatePhase.FAILED) "重新下载更新" else "下载更新") }
            }
            Button(
                onClick = {
                    updateJob?.cancel()
                    updateJob = viewModel.checkForUpdate()
                },
                enabled = update.phase != UpdatePhase.CHECKING && update.phase != UpdatePhase.DOWNLOADING,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            ) {
                Text(if (update.phase == UpdatePhase.IDLE) "检查更新" else "重新检查")
            }
        }
    }
}

@Composable
private fun WorkspaceManager(
    state: KeyBookUiState,
    viewModel: KeyBookViewModel,
    onDismiss: () -> Unit,
) {
    var editingId by remember { mutableStateOf<String?>(null) }
    var newWorkspace by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var pendingDelete by remember { mutableStateOf<com.github.caiheyu.keybook.data.model.Workspace?>(null) }
    LaunchedEffect(state.workspaces.map { it.id }) {
        viewModel.refreshWorkspaceRecordCounts()
    }
    if (!newWorkspace && editingId == null && pendingDelete == null) {
        BottomSheetFrame(
            title = "工作空间",
            onDismiss = onDismiss,
            content = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                state.workspaces.forEach { workspace ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(workspace.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            val counts = state.workspaceRecordCounts[workspace.id]
                            Text(
                                buildString {
                                    if (workspace.id == state.currentWorkspaceId) append("当前 · ")
                                    if (counts == null) {
                                        append("正在统计…")
                                    } else {
                                        append("${counts.companyCount} 个企业 · ${counts.accountCount} 个账号")
                                    }
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(
                            onClick = { editingId = workspace.id; name = workspace.name },
                            enabled = !state.transferLocked && !state.busy,
                        ) {
                            Icon(Icons.Outlined.Edit, "重命名${workspace.name}")
                        }
                        IconButton(
                            onClick = { pendingDelete = workspace },
                            enabled = state.workspaces.size > 1 && !state.transferLocked && !state.busy,
                        ) { Icon(Icons.Outlined.Delete, "删除${workspace.name}") }
                    }
                }
                OutlinedButton(
                    onClick = { newWorkspace = true; name = "" },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.transferLocked && !state.busy,
                ) {
                    Icon(Icons.Outlined.Add, null)
                    Spacer(Modifier.width(8.dp))
                    Text("新建工作空间")
                }
            }
            },
            actions = {
                Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("完成") }
            },
        )
    }
    if (newWorkspace || editingId != null) {
        val normalizedName = runCatching { InputValidation.normalizeName(name, 40) }.getOrNull()
        val nameError = validationError { InputValidation.normalizeName(name, 40) }
            ?: if (state.workspaces.any { workspace ->
                    workspace.id != editingId && workspace.name == normalizedName
                }
            ) "工作空间名称不能重复" else null
        BottomSheetFrame(
            title = if (newWorkspace) "新建工作空间" else "重命名工作空间",
            onDismiss = { newWorkspace = false; editingId = null },
            content = {
                OutlinedTextField(
                    name,
                    { name = it },
                    label = { Text("工作空间名称") },
                    isError = nameError != null,
                    supportingText = nameError?.let { message -> { Text(message) } },
                )
            },
            actions = {
                OutlinedButton(
                    onClick = { newWorkspace = false; editingId = null },
                    modifier = Modifier.weight(1f),
                ) { Text("取消") }
                Button(
                    onClick = {
                        val done = { newWorkspace = false; editingId = null }
                        if (newWorkspace) viewModel.createWorkspace(name, done)
                        else viewModel.renameWorkspace(requireNotNull(editingId), name, done)
                    },
                    enabled = nameError == null && !state.transferLocked && !state.busy,
                    modifier = Modifier.weight(1f),
                ) { Text("保存") }
            },
        )
    }
    pendingDelete?.let { workspace ->
        ConfirmActionSheet(
            title = "删除工作空间？",
            text = "将永久删除“${workspace.name}”及其自定义预设、内置预设修改和生成器配置。" +
                "空间内如有企业，删除会被阻止。此操作无法恢复。",
            confirmLabel = "永久删除",
            onDismiss = { pendingDelete = null },
            onConfirm = {
                viewModel.deleteWorkspace(workspace.id) { pendingDelete = null }
            },
            confirmEnabled = !state.transferLocked && !state.busy,
        )
    }
}

@Composable
private fun SettingRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title)
            if (subtitle.isNotEmpty()) Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null)
    }
    HorizontalDivider()
}

@Composable
private fun HierarchyTitle(title: String, path: String) {
    Column {
        Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (path.isNotEmpty()) {
            Text(
                path,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String, count: String, horizontalPadding: androidx.compose.ui.unit.Dp = 20.dp) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = horizontalPadding, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        if (count.isNotEmpty()) Text(count, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun RecordRow(
    name: String,
    subtitle: String,
    icon: ImageVector? = null,
    iconPng: ByteArray? = null,
    trailing: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Row(
        modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NameAvatar(name, icon, iconPng)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (trailing == null) Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null) else trailing()
    }
    HorizontalDivider(Modifier.padding(horizontal = 20.dp))
}

@Composable
private fun SortControls(
    index: Int,
    lastIndex: Int,
    onMove: (Int) -> Unit,
    onUp: () -> Unit,
    onDown: () -> Unit,
) {
    val dragStep = with(LocalDensity.current) { 56.dp.toPx() }
    var dragDistance by remember { mutableStateOf(0f) }
    Row {
        IconButton(
            onClick = {},
            modifier = Modifier.pointerInput(index, lastIndex) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { dragDistance = 0f },
                    onDragCancel = { dragDistance = 0f },
                    onDragEnd = {
                        val offset = (dragDistance / dragStep).roundToInt()
                            .coerceIn(-index, lastIndex - index)
                        dragDistance = 0f
                        if (offset != 0) onMove(offset)
                    },
                    onDrag = { change, amount ->
                        change.consume()
                        dragDistance += amount.y
                    },
                )
            },
        ) {
            Icon(Icons.Outlined.DragHandle, "长按并拖动排序")
        }
        IconButton(onClick = onUp, enabled = index > 0) {
            Icon(Icons.Outlined.ArrowUpward, "上移")
        }
        IconButton(onClick = onDown, enabled = index < lastIndex) {
            Icon(Icons.Outlined.ArrowDownward, "下移")
        }
    }
}

@Composable
private fun TargetPickerDialog(
    title: String,
    targets: List<Pair<String, String>>,
    onDismiss: () -> Unit,
    onSelected: (String) -> Unit,
) {
    BottomSheetFrame(
        title = title,
        onDismiss = onDismiss,
        content = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                if (targets.isEmpty()) {
                    Text("暂无其他可用目标", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    targets.forEach { (id, label) ->
                        TextButton(onClick = { onSelected(id) }, modifier = Modifier.fillMaxWidth()) {
                            Text(label, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        },
        actions = {
            OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("取消") }
        },
    )
}

@Composable
private fun ConfirmMove(
    source: String,
    item: String,
    target: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    ConfirmActionSheet(
        title = "确认移动？",
        text = "$source / $item\n→ $target",
        confirmLabel = "移动",
        onDismiss = onDismiss,
        onConfirm = onConfirm,
    )
}

@Composable
private fun NameAvatar(name: String, icon: ImageVector?, iconPng: ByteArray?) {
    val colorScheme = MaterialTheme.colorScheme
    val backgrounds = listOf(
        colorScheme.primaryContainer,
        colorScheme.secondaryContainer,
        colorScheme.tertiaryContainer,
        colorScheme.surfaceVariant,
    )
    val foregrounds = listOf(
        colorScheme.onPrimaryContainer,
        colorScheme.onSecondaryContainer,
        colorScheme.onTertiaryContainer,
        colorScheme.onSurfaceVariant,
    )
    val index = placeholderColorIndex(name)
    val bitmap = remember(iconPng) {
        iconPng?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }
    }
    val decoration = if (bitmap == null) {
        Modifier.clip(RoundedCornerShape(8.dp)).background(backgrounds[index])
    } else {
        Modifier
    }
    Box(
        Modifier.size(44.dp).then(decoration),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        } else if (icon != null) {
            Icon(icon, null, tint = foregrounds[index])
        } else {
            Text(
                name.firstCodePoint(),
                style = MaterialTheme.typography.titleMedium,
                color = foregrounds[index],
            )
        }
    }
}

private fun placeholderColorIndex(name: String): Int {
    var hash = 0u
    var offset = 0
    while (offset < name.length) {
        val codePoint = name.codePointAt(offset)
        hash = hash * 31u + codePoint.toUInt()
        offset += Character.charCount(codePoint)
    }
    return (hash % 4u).toInt()
}

private fun String.firstCodePoint(): String = if (isEmpty()) "?" else {
    val end = Character.charCount(codePointAt(0))
    substring(0, end)
}

@Composable
private fun SecretValueRow(value: String, visible: Boolean, onToggle: (() -> Unit)?, onCopy: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            if (visible) value else "•".repeat(value.codePointCount(0, value.length).coerceAtMost(16)),
            modifier = Modifier.weight(1f),
            fontFamily = FontFamily.Monospace,
        )
        if (onToggle != null) {
            IconButton(onClick = onToggle) {
                Icon(if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, if (visible) "隐藏" else "显示")
            }
        }
        IconButton(onClick = onCopy) { Icon(Icons.Outlined.ContentCopy, "复制") }
    }
}

@Composable
private fun BackButton(navController: NavController) {
    IconButton(onClick = { navController.popBackStack() }) {
        Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回")
    }
}

@Composable
private fun ConfirmDelete(
    title: String,
    text: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    ConfirmActionSheet(
        title = title,
        text = text,
        confirmLabel = "永久删除",
        onDismiss = onDismiss,
        onConfirm = onConfirm,
    )
}
