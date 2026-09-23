package com.github.caiheyu.keybook

import android.content.pm.ActivityInfo
import android.view.View
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.swipeDown
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.runBlocking
import com.github.caiheyu.keybook.data.model.AccountDraft
import com.github.caiheyu.keybook.data.model.AppDraft
import com.github.caiheyu.keybook.data.model.CompanyDraft
import com.github.caiheyu.keybook.data.model.SecretItemDraft
import com.github.caiheyu.keybook.ui.KeyBookViewModel
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class KeyBookNavigationTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun wakeDevice() {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        automation.executeShellCommand("input keyevent KEYCODE_WAKEUP").close()
        automation.executeShellCommand("wm dismiss-keyguard").close()
        composeRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
    }

    @Test
    fun firstRunNavigationAndEditorAreReachable() {
        waitForHome()
        val currentWorkspaceName = requireNotNull(activityViewModel().state.value.currentWorkspace?.name)
        composeRule.activityRule.scenario.onActivity {
            assertEquals(
                View.IMPORTANT_FOR_AUTOFILL_NO,
                it.window.decorView.importantForAutofill,
            )
        }
        assertTrue(composeRule.onAllNodesWithText(currentWorkspaceName).fetchSemanticsNodes().isNotEmpty())
        composeRule.onNodeWithText("生成器").performClick()
        composeRule.onNodeWithText("密码生成器").fetchSemanticsNode()
        composeRule.onNodeWithText("设置").performClick()
        composeRule.onNodeWithText("本地数据与安全").performScrollTo().performClick()
        composeRule.onNodeWithText("本地加密").assertIsDisplayed()
        composeRule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        composeRule.onNodeWithText("关于 / 更新").performScrollTo().performClick()
        composeRule.onNodeWithText("https://github.com/caiheyu/KeyBook").performScrollTo().assertIsDisplayed()
        composeRule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        composeRule.onNodeWithText("记录").performClick()
        composeRule.onNodeWithText("新增企业").performClick()
        composeRule.onNodeWithText("新增企业").fetchSemanticsNode()
        assertTrue(composeRule.onAllNodesWithText("生成器").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun settingsGeneratorPresetEntryOpensManager() {
        waitForHome()
        composeRule.onNodeWithText("设置").performClick()
        composeRule.onNodeWithText("生成器预设").performScrollTo().performClick()

        composeRule.onNodeWithText("密码生成器").assertIsDisplayed()
        composeRule.onNodeWithText("管理生成器预设").assertIsDisplayed()
        composeRule.onNodeWithText("完成").performClick()
    }

    @Test
    fun catalogEditorUsesAFullScreenFormAndPersistsThePreset() {
        waitForHome()
        val viewModel = activityViewModel()
        val repository = EntryPointAccessors.fromApplication(
            composeRule.activity.applicationContext,
            DebugVaultEntryPoint::class.java,
        ).vaultRepository()
        val workspaceId = requireNotNull(viewModel.state.value.currentWorkspaceId)
        val name = "整页预定义项-${System.nanoTime()}"

        try {
            composeRule.onNodeWithText("设置").performClick()
            composeRule.onNodeWithText("预定义项").performScrollTo().performClick()
            composeRule.onNodeWithContentDescription("新增企业预定义项").performClick()
            composeRule.onNodeWithText("新建预定义项").assertIsDisplayed()
            assertTrue(composeRule.onAllNodesWithText("生成器").fetchSemanticsNodes().isEmpty())
            composeRule.onNodeWithText("名称").performTextInput(name)
            composeRule.onAllNodesWithText("保存")[0].performClick()
            composeRule.waitUntil(timeoutMillis = 10_000) {
                !viewModel.state.value.busy && runBlocking {
                    repository.listCatalogPresets(workspaceId).any { it.name == name }
                }
            }
            composeRule.onNodeWithTag("catalogList").performScrollToNode(hasText(name))
            composeRule.onNodeWithText(name).assertIsDisplayed()
        } finally {
            runBlocking {
                repository.listCatalogPresets(workspaceId).firstOrNull { it.name == name }?.let {
                    repository.deleteCatalogPreset(workspaceId, it.id)
                }
            }
        }
    }

    @Test
    fun unchangedEditorReturnsDirectlyButDirtyEditorRequiresConfirmation() {
        waitForHome()
        composeRule.onNodeWithText("新增企业").performClick()
        composeRule.onNodeWithText("名称").assertIsDisplayed()
        composeRule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        waitForHome()
        assertTrue(composeRule.onAllNodesWithText("放弃修改？").fetchSemanticsNodes().isEmpty())

        composeRule.onNodeWithText("新增企业").performClick()
        composeRule.onNodeWithText("名称").performTextInput("未保存企业")
        composeRule.onNodeWithText("未保存企业").assertIsDisplayed()
        composeRule.waitForIdle()
        composeRule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        composeRule.onNodeWithText("放弃修改？").assertIsDisplayed()
        composeRule.onNodeWithText("继续编辑").performClick()
        composeRule.onNodeWithText("未保存企业").assertIsDisplayed()
        composeRule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        composeRule.onNodeWithText("放弃修改").performClick()
        waitForHome()
    }

    @Test
    fun rapidDuplicateCreateCommitsOnlyOnce() {
        waitForHome()
        val viewModel = activityViewModel()
        val repository = EntryPointAccessors.fromApplication(
            composeRule.activity.applicationContext,
            DebugVaultEntryPoint::class.java,
        ).vaultRepository()
        val workspaceId = requireNotNull(viewModel.state.value.currentWorkspaceId)
        val name = "防重复企业-${System.nanoTime()}"

        viewModel.saveCompany(null, CompanyDraft(name)) {}
        viewModel.saveCompany(null, CompanyDraft(name)) {}
        composeRule.waitUntil(timeoutMillis = 10_000) {
            !viewModel.state.value.busy && runBlocking {
                repository.listCompanies(workspaceId).count { it.name == name } == 1
            }
        }
        val created = runBlocking { repository.listCompanies(workspaceId).single { it.name == name } }
        runBlocking { repository.deleteCompany(workspaceId, created.id) }
    }

    @Test
    fun currentEmptyWorkspaceRequiresPermanentDeleteConfirmation() {
        waitForHome()
        val name = "待删除空间-${System.nanoTime()}"
        val viewModel = activityViewModel()
        val repository = EntryPointAccessors.fromApplication(
            composeRule.activity.applicationContext,
            DebugVaultEntryPoint::class.java,
        ).vaultRepository()

        runBlocking {
            repository.listWorkspaces()
                .filter { it.name.startsWith("待删除空间-") }
                .forEach { stale ->
                    if (repository.listWorkspaces().size > 1 &&
                        repository.getWorkspaceRecordCounts(stale.id).companyCount == 0
                    ) {
                        repository.deleteWorkspace(stale.id)
                    }
                }
        }

        try {
            composeRule.onNodeWithText("设置").performClick()
            composeRule.onNodeWithText("工作空间").performScrollTo().performClick()
            composeRule.onNodeWithText("新建工作空间").performClick()
            composeRule.onNodeWithText("工作空间名称").performTextInput(name)
            composeRule.onNodeWithText("保存").performClick()
            composeRule.waitUntil(timeoutMillis = 10_000) {
                !viewModel.state.value.busy &&
                    composeRule.onAllNodesWithContentDescription("删除$name").fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithContentDescription("删除$name").performClick()
            composeRule.waitUntil(timeoutMillis = 10_000) {
                composeRule.onAllNodesWithText("删除工作空间？").fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithText("删除工作空间？").assertIsDisplayed()
            composeRule.onNodeWithText("永久删除").performClick()
            composeRule.waitUntil(timeoutMillis = 10_000) {
                composeRule.onAllNodesWithContentDescription("删除$name").fetchSemanticsNodes().isEmpty()
            }
            assertTrue(composeRule.onAllNodesWithText(name).fetchSemanticsNodes().isEmpty())
        } finally {
            runBlocking {
                repository.listWorkspaces().firstOrNull { it.name == name }?.let { workspace ->
                    if (repository.listWorkspaces().size > 1 &&
                        repository.getWorkspaceRecordCounts(workspace.id).companyCount == 0
                    ) {
                        repository.deleteWorkspace(workspace.id)
                    }
                }
            }
        }
    }

    @Test
    fun secretSearchOpensHighlightedMaskedItem() {
        waitForHome()
        val viewModel = activityViewModel()
        val repository = EntryPointAccessors.fromApplication(
            composeRule.activity.applicationContext,
            DebugVaultEntryPoint::class.java,
        ).vaultRepository()
        val workspaceId = requireNotNull(viewModel.state.value.currentWorkspaceId)
        val suffix = System.nanoTime().toString()
        val secretName = "搜索定位项-$suffix"
        val secretPassword = "NeverShow-$suffix"
        val company = runBlocking {
            val createdCompany = repository.saveCompany(workspaceId, null, CompanyDraft("搜索企业-$suffix"))
            val app = repository.saveApp(workspaceId, createdCompany.id, null, AppDraft("搜索应用-$suffix"))
            val account = repository.saveAccount(
                workspaceId,
                app.id,
                null,
                AccountDraft("搜索账号-$suffix", "search-$suffix", "AccountPassword-$suffix"),
            )
            repeat(20) { index ->
                repository.saveSecretItem(
                    workspaceId,
                    account.id,
                    null,
                    SecretItemDraft("前置项-$index-$suffix", "Hidden-$index-$suffix"),
                )
            }
            val secret = repository.saveSecretItem(
                workspaceId,
                account.id,
                null,
                SecretItemDraft(secretName, secretPassword),
            )
            Triple(createdCompany, app, secret)
        }

        try {
            composeRule.onNodeWithText("搜索企业、应用、账号或附加项").performTextInput(secretName)
            composeRule.waitUntil(timeoutMillis = 10_000) {
                composeRule.onAllNodesWithText(secretName).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithTag(
                "search-result:SECRET_ITEM:${company.third.id}",
                useUnmergedTree = true,
            ).performClick()
            composeRule.waitUntil(timeoutMillis = 10_000) {
                runCatching {
                    composeRule.onNodeWithTag("highlighted-secret:${company.third.id}")
                        .fetchSemanticsNode()
                }.isSuccess
            }
            composeRule.onNodeWithTag("highlighted-secret:${company.third.id}").assertIsDisplayed()
            assertTrue(
                composeRule.onAllNodesWithText(secretPassword, useUnmergedTree = true)
                    .fetchSemanticsNodes().isEmpty(),
            )
        } finally {
            runBlocking {
                repository.deleteApp(workspaceId, company.second.id)
                repository.deleteCompany(workspaceId, company.first.id)
            }
        }
    }

    @Test
    fun searchQueryAndScrollPositionSurviveDetailNavigation() {
        waitForHome()
        val viewModel = activityViewModel()
        val repository = EntryPointAccessors.fromApplication(
            composeRule.activity.applicationContext,
            DebugVaultEntryPoint::class.java,
        ).vaultRepository()
        val workspaceId = requireNotNull(viewModel.state.value.currentWorkspaceId)
        val suffix = System.nanoTime().toString()
        val query = "返回搜索-$suffix"
        val companies = runBlocking {
            List(24) { index ->
                repository.saveCompany(
                    workspaceId,
                    null,
                    CompanyDraft("$query-${index.toString().padStart(2, '0')}"),
                )
            }
        }
        val target = companies.last()

        try {
            viewModel.refreshCompanies()
            composeRule.onNodeWithTag("vaultSearchField").performTextInput(query)
            composeRule.waitUntil(timeoutMillis = 10_000) {
                viewModel.state.value.searchResults.count { it.title.startsWith(query) } == companies.size
            }
            composeRule.onNodeWithTag("searchResultsList").performScrollToNode(hasText(target.name))
            composeRule.onNodeWithText(target.name).assertIsDisplayed().performClick()
            composeRule.waitUntil(timeoutMillis = 10_000) {
                composeRule.onAllNodesWithText("新增应用").fetchSemanticsNodes().isNotEmpty()
            }

            composeRule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            composeRule.onNodeWithTag("vaultSearchField").assertTextContains(query)
            composeRule.onNodeWithText(target.name).assertIsDisplayed()
        } finally {
            viewModel.clearSearch()
            runBlocking {
                companies.asReversed().forEach { repository.deleteCompany(workspaceId, it.id) }
            }
        }
    }

    @Test
    fun backgroundReleasesAndForegroundReloadsDecryptedAccountDetails() {
        waitForHome()
        val viewModel = activityViewModel()
        val repository = EntryPointAccessors.fromApplication(
            composeRule.activity.applicationContext,
            DebugVaultEntryPoint::class.java,
        ).vaultRepository()
        val workspaceId = requireNotNull(viewModel.state.value.currentWorkspaceId)
        val suffix = System.nanoTime().toString()
        val records = runBlocking {
            val company = repository.saveCompany(workspaceId, null, CompanyDraft("后台企业-$suffix"))
            val app = repository.saveApp(workspaceId, company.id, null, AppDraft("后台应用-$suffix"))
            val account = repository.saveAccount(
                workspaceId,
                app.id,
                null,
                AccountDraft("后台账号-$suffix", "background-$suffix", "Background-Secret-$suffix"),
            )
            repository.saveSecretItem(
                workspaceId,
                account.id,
                null,
                SecretItemDraft("后台附加项-$suffix", "Background-Extra-$suffix"),
            )
            Triple(company, app, account)
        }

        try {
            viewModel.refreshCompanies()
            composeRule.waitUntil(timeoutMillis = 10_000) {
                composeRule.onAllNodesWithText(records.first.name).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithText(records.first.name).performClick()
            composeRule.waitUntil(timeoutMillis = 10_000) {
                composeRule.onAllNodesWithText(records.second.name).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithText(records.second.name).performClick()
            composeRule.waitUntil(timeoutMillis = 10_000) {
                composeRule.onAllNodesWithText(records.third.name).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithText(records.third.name).performClick()
            composeRule.waitUntil(timeoutMillis = 10_000) {
                viewModel.state.value.account?.id == records.third.id &&
                    viewModel.state.value.secretItems.size == 1
            }

            composeRule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            assertTrue(viewModel.state.value.account == null)
            assertTrue(viewModel.state.value.secretItems.isEmpty())

            composeRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
            composeRule.waitUntil(timeoutMillis = 10_000) {
                viewModel.state.value.account?.id == records.third.id &&
                    viewModel.state.value.secretItems.size == 1
            }
        } finally {
            composeRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
            runBlocking {
                repository.deleteApp(workspaceId, records.second.id)
                repository.deleteCompany(workspaceId, records.first.id)
            }
        }
    }

    @Test
    fun quickGeneratorStaysFixedDuringFastContentFlings() {
        waitForHome()
        val viewModel = activityViewModel()
        val repository = EntryPointAccessors.fromApplication(
            composeRule.activity.applicationContext,
            DebugVaultEntryPoint::class.java,
        ).vaultRepository()
        val workspaceId = requireNotNull(viewModel.state.value.currentWorkspaceId)
        val suffix = System.nanoTime().toString()
        val company = runBlocking {
            val createdCompany = repository.saveCompany(workspaceId, null, CompanyDraft("滑动企业-$suffix"))
            val createdApp = repository.saveApp(workspaceId, createdCompany.id, null, AppDraft("滑动应用-$suffix"))
            createdCompany to createdApp
        }

        try {
            viewModel.refreshCompanies()
            composeRule.waitUntil(timeoutMillis = 10_000) {
                composeRule.onAllNodesWithText(company.first.name).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithText(company.first.name).performClick()
            composeRule.waitUntil(timeoutMillis = 10_000) {
                composeRule.onAllNodesWithText(company.second.name).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithText(company.second.name).performClick()
            composeRule.onNodeWithText("新增账号").performClick()
            composeRule.onNodeWithContentDescription("生成密码").performClick()
            composeRule.waitForIdle()

            val sheet = composeRule.onNodeWithTag("quickGeneratorSheet")
            val top = sheet.fetchSemanticsNode().boundsInRoot.top
            repeat(3) {
                sheet.performTouchInput { swipeUp(durationMillis = 50) }
                composeRule.waitForIdle()
                assertEquals(top, sheet.fetchSemanticsNode().boundsInRoot.top, 1f)
            }
            composeRule.onNodeWithText("应用").assertIsDisplayed()
            composeRule.onNodeWithText("生成密码").performScrollTo()
            composeRule.waitForIdle()
            sheet.performTouchInput { swipeDown(startY = centerY, endY = bottom - 20f, durationMillis = 150) }
            composeRule.waitUntil(timeoutMillis = 10_000) {
                composeRule.onAllNodesWithTag("quickGeneratorSheet").fetchSemanticsNodes().isEmpty()
            }
        } finally {
            runBlocking {
                repository.deleteApp(workspaceId, company.second.id)
                repository.deleteCompany(workspaceId, company.first.id)
            }
        }
    }

    @Test
    fun rotationKeepsUnsavedPasswordDraftInMemory() {
        waitForHome()
        val viewModel = activityViewModel()
        val repository = EntryPointAccessors.fromApplication(
            composeRule.activity.applicationContext,
            DebugVaultEntryPoint::class.java,
        ).vaultRepository()
        val workspaceId = requireNotNull(viewModel.state.value.currentWorkspaceId)
        val suffix = System.nanoTime().toString()
        val company = runBlocking {
            val createdCompany = repository.saveCompany(
                workspaceId,
                null,
                CompanyDraft("旋转企业-$suffix"),
            )
            val createdApp = repository.saveApp(
                workspaceId,
                createdCompany.id,
                null,
                AppDraft("旋转应用-$suffix"),
            )
            createdCompany to createdApp
        }
        val accountName = "旋转账号-$suffix"
        val login = "rotation-$suffix@example.com"
        val password = "Rotation-Secret-$suffix!"

        try {
            viewModel.refreshCompanies()
            composeRule.waitUntil(timeoutMillis = 10_000) {
                composeRule.onAllNodesWithText(company.first.name).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithText(company.first.name).performClick()
            composeRule.waitUntil(timeoutMillis = 10_000) {
                composeRule.onAllNodesWithText(company.second.name).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithText(company.second.name).performClick()
            composeRule.onNodeWithText("新增账号").performClick()
            composeRule.onNodeWithText("账号名称").performTextInput(accountName)
            composeRule.onNodeWithText("登录账号").performTextInput(login)
            composeRule.onNodeWithText("登录密码").performTextInput(password)

            composeRule.activityRule.scenario.onActivity {
                it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            }
            composeRule.waitForIdle()
            composeRule.onAllNodesWithText("保存")[0].performClick()
            composeRule.waitUntil(timeoutMillis = 10_000) {
                !viewModel.state.value.busy && runBlocking {
                    repository.listAccounts(workspaceId, company.second.id).any { it.name == accountName }
                }
            }
            val saved = runBlocking {
                val summary = repository.listAccounts(workspaceId, company.second.id)
                    .single { it.name == accountName }
                requireNotNull(repository.getAccount(workspaceId, summary.id))
            }
            assertEquals(login, saved.login)
            assertEquals(password, saved.password)
        } finally {
            composeRule.activityRule.scenario.onActivity {
                it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
            runBlocking {
                repository.deleteApp(workspaceId, company.second.id)
                repository.deleteCompany(workspaceId, company.first.id)
            }
        }
    }

    private fun waitForHome() {
        composeRule.waitUntil(timeoutMillis = 20_000) {
            runCatching {
                composeRule.onAllNodesWithText("密钥册").fetchSemanticsNodes().isNotEmpty()
            }.getOrDefault(false)
        }
    }

    private fun activityViewModel(): KeyBookViewModel {
        lateinit var viewModel: KeyBookViewModel
        composeRule.activityRule.scenario.onActivity {
            viewModel = ViewModelProvider(it)[KeyBookViewModel::class.java]
        }
        return viewModel
    }
}
