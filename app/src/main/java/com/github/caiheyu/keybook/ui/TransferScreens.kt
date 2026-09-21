package com.github.caiheyu.keybook.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import java.time.Instant
import kotlinx.coroutines.Job
import com.github.caiheyu.keybook.core.validation.InputValidation
import com.github.caiheyu.keybook.feature.transfer.AppCompanyTarget
import com.github.caiheyu.keybook.feature.transfer.AppendAppsImport
import com.github.caiheyu.keybook.feature.transfer.AppendCompaniesImport
import com.github.caiheyu.keybook.feature.transfer.BackupImportPlan
import com.github.caiheyu.keybook.feature.transfer.BackupPayload
import com.github.caiheyu.keybook.feature.transfer.BackupScope
import com.github.caiheyu.keybook.feature.transfer.CopyWorkspaceImport
import com.github.caiheyu.keybook.feature.transfer.ReplaceWorkspaceImport
import com.github.caiheyu.keybook.ui.navigation.ExportRoute

private data class TransferOption(val id: String, val title: String, val subtitle: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportScreen(
    route: ExportRoute,
    state: KeyBookUiState,
    viewModel: KeyBookViewModel,
    navController: NavController,
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var scope by remember(route.initialScope) {
        mutableStateOf(runCatching { BackupScope.valueOf(route.initialScope) }.getOrDefault(BackupScope.WORKSPACES))
    }
    var selectedIds by remember(route.preselectedId, state.currentWorkspaceId) {
        mutableStateOf(
            setOfNotNull(
                route.preselectedId ?: state.currentWorkspaceId.takeIf { scope == BackupScope.WORKSPACES },
            ),
        )
    }
    var query by remember { mutableStateOf("") }
    var protectionStep by remember { mutableStateOf(false) }
    var encrypted by remember { mutableStateOf(true) }
    var password by remember { mutableStateOf("") }
    var repeatPassword by remember { mutableStateOf("") }
    var plainConfirm by remember { mutableStateOf(false) }
    var pendingPassword by remember { mutableStateOf<String?>(null) }
    var completed by remember { mutableStateOf(false) }
    var transferJob by remember { mutableStateOf<Job?>(null) }

    DisposableEffect(Unit) {
        viewModel.setTransferLocked(true)
        onDispose {
            transferJob?.cancel()
            password = ""
            repeatPassword = ""
            pendingPassword = null
            viewModel.setTransferLocked(false)
        }
    }
    LaunchedEffect(Unit) { viewModel.loadAllApps() }
    fun returnToSelection() {
        protectionStep = false
        password = ""
        repeatPassword = ""
        pendingPassword = null
        encrypted = true
    }

    fun dismissOrBack() {
        if (state.busy) return
        if (protectionStep && !completed) returnToSelection() else navController.popBackStack()
    }
    BackHandler(onBack = ::dismissOrBack)

    val options = when (scope) {
        BackupScope.WORKSPACES -> state.workspaces.map { TransferOption(it.id, it.name, "工作空间") }
        BackupScope.COMPANIES -> state.companies.map { TransferOption(it.company.id, it.company.name, "${it.appCount} 个应用") }
        BackupScope.APPS -> state.allApps.map { app ->
            val company = state.companies.firstOrNull { it.company.id == app.companyId }?.company?.name.orEmpty()
            TransferOption(app.id, app.name, company)
        }
    }
    val visible = options.filter { option ->
        query.isBlank() || option.title.contains(query, true) || option.subtitle.contains(query, true)
    }
    val createFile = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri ->
        if (uri == null) {
            pendingPassword = null
        } else {
            transferJob = viewModel.exportToUri(
                context.contentResolver,
                uri,
                scope,
                selectedIds,
                pendingPassword,
            ) {
                password = ""
                repeatPassword = ""
                pendingPassword = null
                completed = true
            }
        }
    }
    fun launchFilePicker(exportPassword: String?) {
        pendingPassword = exportPassword
        val stamp = Instant.now().toString().replace(Regex("[:.]"), "-")
        createFile.launch(
            if (exportPassword == null) {
                "keybook-$stamp-UNENCRYPTED.pmbackup-plain.json"
            } else {
                "keybook-$stamp.pmbackup"
            },
        )
    }

    ModalBottomSheet(onDismissRequest = ::dismissOrBack, sheetState = sheetState) {
        Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (completed) "导出完成" else if (protectionStep) "导出确认" else "导出数据",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = ::dismissOrBack, enabled = !state.busy) {
                Text(if (protectionStep && !completed) "上一步" else "取消")
            }
        }
        when {
            completed -> {
                Column(
                    Modifier.fillMaxSize().padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(Icons.Outlined.Check, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(12.dp))
                    Text("备份文件已写入并完成字节核验")
                    Button(onClick = { navController.popBackStack() }, modifier = Modifier.padding(top = 20.dp)) {
                        Text("完成")
                    }
                }
            }
            !protectionStep -> {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    BackupScope.entries.forEach { item ->
                        val label = when (item) {
                            BackupScope.WORKSPACES -> "工作空间"
                            BackupScope.COMPANIES -> "企业"
                            BackupScope.APPS -> "应用"
                        }
                        if (scope == item) {
                            Button(
                                onClick = {},
                                modifier = Modifier.weight(1f),
                                enabled = !state.busy,
                            ) { Text(label, maxLines = 1) }
                        } else {
                            OutlinedButton(
                                onClick = {
                                    scope = item
                                    selectedIds = emptySet()
                                    query = ""
                                },
                                modifier = Modifier.weight(1f),
                                enabled = !state.busy,
                            ) { Text(label, maxLines = 1) }
                        }
                    }
                }
                Text(
                    if (scope == BackupScope.WORKSPACES) {
                        "包含所选空间的全部记录、预定义项与生成器配置。"
                    } else {
                        "仅导出当前工作空间，不包含预定义项和生成器配置。"
                    },
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    query,
                    { query = it },
                    leadingIcon = { Icon(Icons.Outlined.Search, null) },
                    placeholder = { Text("搜索名称") },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                    singleLine = true,
                )
                val allVisibleSelected = visible.isNotEmpty() && visible.all { it.id in selectedIds }
                Row(
                    Modifier.fillMaxWidth().clickable(enabled = visible.isNotEmpty() && !state.busy) {
                        selectedIds = if (allVisibleSelected) {
                            selectedIds - visible.map { it.id }.toSet()
                        } else {
                            selectedIds + visible.map { it.id }
                        }
                    }.padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = allVisibleSelected,
                        onCheckedChange = null,
                        enabled = visible.isNotEmpty() && !state.busy,
                    )
                    Text(if (query.isBlank()) "选择全部" else "选择搜索结果")
                }
                HorizontalDivider()
                LazyColumn(Modifier.weight(1f)) {
                    items(visible, key = { it.id }) { option ->
                        Row(
                            Modifier.fillMaxWidth().clickable(enabled = !state.busy) {
                                selectedIds = if (option.id in selectedIds) selectedIds - option.id else selectedIds + option.id
                            }.padding(horizontal = 20.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(option.title)
                                Text(option.subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Checkbox(option.id in selectedIds, onCheckedChange = null, enabled = !state.busy)
                        }
                        HorizontalDivider(Modifier.padding(horizontal = 20.dp))
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("已选 ${selectedIds.size} 项", modifier = Modifier.weight(1f))
                    TextButton(onClick = { selectedIds = emptySet() }, enabled = selectedIds.isNotEmpty()) { Text("清空") }
                    Button(
                        onClick = { protectionStep = true },
                        enabled = selectedIds.isNotEmpty() && !state.busy,
                    ) { Text("下一步") }
                }
            }
            else -> {
                val passwordError = if (encrypted && password.isNotEmpty()) {
                    transferValidationError {
                        InputValidation.validateRaw(password, 12, 128, "文件密码")
                    }
                } else null
                val repeatError = if (encrypted && repeatPassword.isNotEmpty() && password != repeatPassword) {
                    "两次输入的文件密码不一致"
                } else null
                Column(Modifier.fillMaxSize().padding(20.dp)) {
                    Text("${scope.label} · 已选 ${selectedIds.size} 项", style = MaterialTheme.typography.titleMedium)
                    Row(
                        Modifier.fillMaxWidth().clickable(enabled = !state.busy) { encrypted = !encrypted }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("使用密码加密", modifier = Modifier.weight(1f))
                        Checkbox(encrypted, { encrypted = it }, enabled = !state.busy)
                    }
                    if (encrypted) {
                        OutlinedTextField(
                            password,
                            { password = it },
                            label = { Text("文件密码（12–128 个字符）") },
                            modifier = Modifier.fillMaxWidth(),
                            visualTransformation = PasswordVisualTransformation(),
                            enabled = !state.busy,
                            isError = passwordError != null,
                            supportingText = passwordError?.let { message -> { Text(message) } },
                        )
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            repeatPassword,
                            { repeatPassword = it },
                            label = { Text("确认文件密码") },
                            modifier = Modifier.fillMaxWidth(),
                            visualTransformation = PasswordVisualTransformation(),
                            enabled = !state.busy,
                            isError = repeatError != null,
                            supportingText = repeatError?.let { message -> { Text(message) } },
                        )
                    } else {
                        Text(
                            "此文件不会加密，获得文件的人可以读取其中的账号、密码和全部导出资料。",
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(vertical = 16.dp),
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = ::returnToSelection,
                            modifier = Modifier.weight(1f),
                            enabled = !state.busy,
                        ) { Text("上一步") }
                        Button(
                            onClick = {
                                if (encrypted) {
                                    launchFilePicker(password)
                                } else plainConfirm = true
                            },
                            modifier = Modifier.weight(1f),
                            enabled = !state.busy && (!encrypted ||
                                password.isNotEmpty() && repeatPassword.isNotEmpty() &&
                                passwordError == null && repeatError == null),
                        ) { Text("选择保存位置") }
                    }
                }
            }
        }
        }
    }
    if (plainConfirm) {
        ConfirmActionSheet(
            title = "确认明文导出？",
            text = "获得文件的人可以读取账号、密码和全部导出资料。",
            confirmLabel = "继续明文导出",
            onDismiss = { plainConfirm = false },
            onConfirm = { plainConfirm = false; launchFilePicker(null) },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportScreen(
    state: KeyBookUiState,
    viewModel: KeyBookViewModel,
    navController: NavController,
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val targetWorkspaceId = remember { state.currentWorkspaceId }
    var pendingBytes by remember { mutableStateOf<ByteArray?>(null) }
    var encryptedFile by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var payload by remember { mutableStateOf<BackupPayload?>(null) }
    var completed by remember { mutableStateOf(false) }
    var replaceMode by remember { mutableStateOf(false) }
    var replaceTargets by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var appTargets by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var newCompanyNames by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var openMenuFor by remember { mutableStateOf<String?>(null) }
    var replaceConfirm by remember { mutableStateOf(false) }
    var decodeError by remember { mutableStateOf<String?>(null) }
    var transferJob by remember { mutableStateOf<Job?>(null) }

    DisposableEffect(Unit) {
        viewModel.setTransferLocked(true)
        onDispose {
            transferJob?.cancel()
            pendingBytes?.fill(0)
            pendingBytes = null
            payload = null
            decodeError = null
            password = ""
            viewModel.setTransferLocked(false)
        }
    }
    fun acceptPayload(decoded: BackupPayload) {
        pendingBytes?.fill(0)
        pendingBytes = null
        decodeError = null
        password = ""
        payload = decoded
        // Every file starts with the non-destructive default; mappings from a
        // previously inspected backup must not carry over.
        replaceMode = false
        replaceTargets = emptyMap()
        replaceConfirm = false
        appTargets = decoded.workspaces.firstOrNull()?.data?.companies
            ?.associate { it.id to "new" }.orEmpty()
        newCompanyNames = decoded.workspaces.firstOrNull()?.data?.companies
            ?.associate { it.id to it.name }.orEmpty()
    }
    val chooseFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            pendingBytes?.fill(0)
            payload = null
            decodeError = null
            transferJob?.cancel()
            transferJob = viewModel.readBackupFile(context.contentResolver, it) { bytes, encrypted ->
                pendingBytes = bytes
                encryptedFile = encrypted
                if (!encrypted) {
                    transferJob = viewModel.decodeBackup(bytes, null, ::acceptPayload)
                }
            }
        }
    }
    fun resetFile() {
        transferJob?.cancel()
        transferJob = null
        pendingBytes?.fill(0)
        pendingBytes = null
        payload = null
        decodeError = null
        password = ""
        replaceMode = false
        replaceTargets = emptyMap()
        appTargets = emptyMap()
        newCompanyNames = emptyMap()
        openMenuFor = null
        replaceConfirm = false
        chooseFile.launch(arrayOf("application/octet-stream", "application/json", "*/*"))
    }
    fun commitImport() {
        val decoded = payload ?: return
        val plan: BackupImportPlan = when (decoded.scope) {
            BackupScope.WORKSPACES -> if (replaceMode) {
                ReplaceWorkspaceImport(replaceTargets)
            } else CopyWorkspaceImport
            BackupScope.COMPANIES -> AppendCompaniesImport(requireNotNull(targetWorkspaceId))
            BackupScope.APPS -> AppendAppsImport(
                requireNotNull(targetWorkspaceId),
                decoded.workspaces.single().data.companies.associate { company ->
                    val target = appTargets[company.id] ?: "new"
                    company.id to if (target == "new") {
                        AppCompanyTarget(newCompanyName = newCompanyNames[company.id].orEmpty())
                    } else {
                        AppCompanyTarget(existingCompanyId = target)
                    }
                },
            )
        }
        transferJob = viewModel.importBackup(decoded, plan) {
            payload = null
            completed = true
        }
    }
    fun dismissOrBack() {
        if (state.busy) return
        if (payload != null || pendingBytes != null) {
            pendingBytes?.fill(0)
            pendingBytes = null
            payload = null
            decodeError = null
            password = ""
            replaceMode = false
            replaceTargets = emptyMap()
            appTargets = emptyMap()
            newCompanyNames = emptyMap()
            openMenuFor = null
            replaceConfirm = false
        } else {
            navController.popBackStack()
        }
    }
    BackHandler {
        dismissOrBack()
    }

    ModalBottomSheet(
        onDismissRequest = ::dismissOrBack,
        sheetState = sheetState,
    ) {
        Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (completed) "导入完成" else "导入数据",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = ::dismissOrBack, enabled = !state.busy) {
                Text("取消")
            }
        }
        when {
            completed -> {
                Column(
                    Modifier.fillMaxSize().padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(Icons.Outlined.Check, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(12.dp))
                    Text("备份已作为一次事务恢复，其他未选空间保持不变。")
                    Button(onClick = { navController.popBackStack() }, modifier = Modifier.padding(top = 20.dp)) {
                        Text("完成")
                    }
                }
            }
            payload == null && pendingBytes == null -> {
                Column(Modifier.fillMaxSize().padding(20.dp)) {
                    Text("选择密钥册 v2 备份文件，应用会按内容识别是否加密。")
                    Text(
                        "企业和应用备份将追加到当前空间“${state.currentWorkspace?.name.orEmpty()}”。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    Button(
                        onClick = { chooseFile.launch(arrayOf("application/octet-stream", "application/json", "*/*")) },
                        modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                        enabled = !state.busy,
                    ) { Text("选择备份文件") }
                }
            }
            payload == null && encryptedFile -> {
                val passwordError = if (password.isNotEmpty()) {
                    transferValidationError {
                        InputValidation.validateRaw(password, 12, 128, "文件密码")
                    }
                } else null
                Column(Modifier.fillMaxSize().padding(20.dp)) {
                    Text("此备份已加密。只有 GCM 认证和全部结构校验通过后才会显示摘要。")
                    OutlinedTextField(
                        password,
                        { password = it },
                        label = { Text("文件密码") },
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                        visualTransformation = PasswordVisualTransformation(),
                        enabled = !state.busy,
                        isError = passwordError != null,
                        supportingText = passwordError?.let { message -> { Text(message) } },
                    )
                    Row(
                        Modifier.fillMaxWidth().padding(top = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(onClick = ::resetFile, modifier = Modifier.weight(1f), enabled = !state.busy) {
                            Text("重新选文件")
                        }
                        Button(
                            onClick = {
                                pendingBytes?.let { bytes ->
                                    transferJob?.cancel()
                                    transferJob = viewModel.decodeBackup(
                                        bytes,
                                        password,
                                        ::acceptPayload,
                                        onError = { decodeError = it },
                                    )
                                }
                            },
                            modifier = Modifier.weight(1f),
                            enabled = password.isNotEmpty() && passwordError == null && !state.busy,
                        ) { Text("解密并校验") }
                    }
                }
            }
            payload == null && pendingBytes != null -> {
                Column(
                    Modifier.fillMaxSize().padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    if (state.busy) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text("正在校验明文备份…", modifier = Modifier.padding(top = 16.dp))
                    } else {
                        Text("文件未能通过完整校验，请检查文件是否为密钥册 v2 备份。")
                        OutlinedButton(
                            onClick = ::resetFile,
                            modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
                        ) { Text("重新选文件") }
                    }
                }
            }
            payload != null -> {
                val decoded = requireNotNull(payload)
                LazyColumn(Modifier.weight(1f).padding(horizontal = 20.dp)) {
                    item {
                        if (decoded.protection.name == "NONE") {
                            Text(
                                "这是明文备份，结构校验不能证明内容未经修改。",
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(vertical = 12.dp),
                            )
                        }
                        Text("备份摘要", style = MaterialTheme.typography.titleMedium)
                    }
                    items(decoded.workspaces, key = { it.id }) { workspace ->
                        val data = workspace.data
                        Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                            Text(workspace.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "企业 ${data.companies.size} · 应用 ${data.apps.size} · 账号 ${data.accounts.size} · 附加项 ${data.secretItems.size}",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        HorizontalDivider()
                    }
                    if (decoded.scope == BackupScope.WORKSPACES) {
                        item {
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                if (!replaceMode) {
                                    Button(onClick = { replaceMode = false }, modifier = Modifier.weight(1f)) { Text("新建副本") }
                                    OutlinedButton(onClick = { replaceMode = true }, modifier = Modifier.weight(1f)) { Text("替换空间") }
                                } else {
                                    OutlinedButton(onClick = { replaceMode = false }, modifier = Modifier.weight(1f)) { Text("新建副本") }
                                    Button(onClick = { replaceMode = true }, modifier = Modifier.weight(1f)) { Text("替换空间") }
                                }
                            }
                            Text(
                                if (replaceMode) "必须为每个源空间选择不同的现有目标。" else "同名空间会自动添加“（导入）”序号。",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (replaceMode) {
                            items(decoded.workspaces, key = { "map:${it.id}" }) { source ->
                                MappingDropdown(
                                    label = "${source.name} → 替换目标",
                                    value = replaceTargets[source.id],
                                    options = state.workspaces.map { it.id to it.name },
                                    open = openMenuFor == source.id,
                                    onOpen = { openMenuFor = source.id },
                                    onDismiss = { openMenuFor = null },
                                    onSelect = { replaceTargets = replaceTargets + (source.id to it); openMenuFor = null },
                                )
                            }
                        }
                    } else if (decoded.scope == BackupScope.APPS) {
                        items(decoded.workspaces.single().data.companies, key = { "appmap:${it.id}" }) { source ->
                            val selected = appTargets[source.id] ?: "new"
                            MappingDropdown(
                                label = "${source.name} 的应用 → 目标企业",
                                value = selected,
                                options = listOf("new" to "新建企业") + state.companies.map { it.company.id to it.company.name },
                                open = openMenuFor == source.id,
                                onOpen = { openMenuFor = source.id },
                                onDismiss = { openMenuFor = null },
                                onSelect = { appTargets = appTargets + (source.id to it); openMenuFor = null },
                            )
                            if (selected == "new") {
                                val name = newCompanyNames[source.id].orEmpty()
                                val nameError = transferValidationError { InputValidation.normalizeName(name) }
                                OutlinedTextField(
                                    name,
                                    { newCompanyNames = newCompanyNames + (source.id to it) },
                                    label = { Text("新建企业名称") },
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                                    isError = nameError != null,
                                    supportingText = nameError?.let { message -> { Text(message) } },
                                )
                            }
                        }
                    } else {
                        item {
                            Text(
                                "将追加到当前空间“${state.currentWorkspace?.name.orEmpty()}”，不按名称合并。",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 16.dp),
                            )
                        }
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(onClick = ::resetFile, modifier = Modifier.weight(1f), enabled = !state.busy) {
                        Text("重新选文件")
                    }
                    val mappingComplete = when (decoded.scope) {
                        BackupScope.WORKSPACES -> !replaceMode ||
                            replaceTargets.keys.containsAll(decoded.workspaces.map { it.id }) &&
                            replaceTargets.values.toSet().size == replaceTargets.size
                        BackupScope.COMPANIES -> true
                        BackupScope.APPS -> decoded.workspaces.single().data.companies.all { company ->
                            val target = appTargets[company.id]
                            target != null && (target != "new" || runCatching {
                                InputValidation.normalizeName(newCompanyNames[company.id].orEmpty())
                            }.isSuccess)
                        }
                    }
                    Button(
                        onClick = {
                            if (decoded.scope == BackupScope.WORKSPACES && replaceMode) replaceConfirm = true
                            else commitImport()
                        },
                        modifier = Modifier.weight(1f),
                        enabled = mappingComplete && !state.busy,
                    ) { Text("确认导入") }
                }
            }
        }
        }
    }
    decodeError?.let { message ->
        AlertDialog(
            onDismissRequest = { decodeError = null },
            title = { Text("无法导入备份") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { decodeError = null }) { Text("重新输入") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        decodeError = null
                        resetFile()
                    },
                ) { Text("重新选文件") }
            },
        )
    }
    if (replaceConfirm) {
        val decoded = payload
        ConfirmActionSheet(
            title = "确认替换指定空间？",
            text = decoded?.workspaces?.joinToString("；") { source ->
                val target = state.workspaces.firstOrNull { it.id == replaceTargets[source.id] }?.name.orEmpty()
                "${source.name} → $target"
            }.orEmpty() + "。目标空间原有记录和配置将被替换，无法撤销。",
            confirmLabel = "确认替换",
            onDismiss = { replaceConfirm = false },
            onConfirm = { replaceConfirm = false; commitImport() },
            confirmEnabled = !state.busy,
        )
    }
}

@Composable
private fun MappingDropdown(
    label: String,
    value: String?,
    options: List<Pair<String, String>>,
    open: Boolean,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    val display = options.firstOrNull { it.first == value }?.second ?: "请选择"
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box(Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
                Text(display, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Icon(Icons.Outlined.KeyboardArrowDown, null)
            }
            DropdownMenu(expanded = open, onDismissRequest = onDismiss, modifier = Modifier.fillMaxWidth()) {
                options.forEach { (id, name) ->
                    DropdownMenuItem(text = { Text(name) }, onClick = { onSelect(id) })
                }
            }
        }
    }
}

private val BackupScope.label: String
    get() = when (this) {
        BackupScope.WORKSPACES -> "工作空间"
        BackupScope.COMPANIES -> "企业"
        BackupScope.APPS -> "应用"
    }

private inline fun transferValidationError(block: () -> Unit): String? = try {
    block()
    null
} catch (error: IllegalArgumentException) {
    error.message ?: "输入无效"
}
