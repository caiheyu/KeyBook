package com.github.caiheyu.keybook

import android.content.Context
import android.graphics.Bitmap
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.ByteArrayOutputStream
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import com.github.caiheyu.keybook.core.crypto.VaultCrypto
import com.github.caiheyu.keybook.data.VaultRepository
import com.github.caiheyu.keybook.data.catalog.BuiltinCatalogRepository
import com.github.caiheyu.keybook.data.catalog.CatalogType
import com.github.caiheyu.keybook.data.db.KeyBookDatabase
import com.github.caiheyu.keybook.data.model.AccountDraft
import com.github.caiheyu.keybook.data.model.AppDraft
import com.github.caiheyu.keybook.data.model.CompanyDraft
import com.github.caiheyu.keybook.data.model.CatalogPresetDraft
import com.github.caiheyu.keybook.data.model.GeneratorPresetDraft
import com.github.caiheyu.keybook.data.model.GeneratorSelection
import com.github.caiheyu.keybook.data.model.GeneratorSelectionKind
import com.github.caiheyu.keybook.data.model.IconDraft
import com.github.caiheyu.keybook.data.model.SecretItemDraft
import com.github.caiheyu.keybook.feature.generator.BuiltinGeneratorPresets
import com.github.caiheyu.keybook.feature.generator.PasswordRules
import com.github.caiheyu.keybook.feature.transfer.BackupCodec
import com.github.caiheyu.keybook.feature.transfer.BackupImporter
import com.github.caiheyu.keybook.feature.transfer.BackupScope
import com.github.caiheyu.keybook.feature.transfer.BackupTransferRepository
import com.github.caiheyu.keybook.feature.transfer.BackupValidator
import com.github.caiheyu.keybook.feature.transfer.AppendAppsImport
import com.github.caiheyu.keybook.feature.transfer.AppCompanyTarget
import com.github.caiheyu.keybook.feature.transfer.CopyWorkspaceImport
import com.github.caiheyu.keybook.feature.transfer.ReplaceWorkspaceImport
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

private fun testIcon(): IconDraft {
    val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
    val output = ByteArrayOutputStream()
    check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
    bitmap.recycle()
    return IconDraft(output.toByteArray(), 2, 2)
}

@RunWith(AndroidJUnit4::class)
class VaultRepositoryInstrumentedTest {
    private lateinit var database: KeyBookDatabase
    private lateinit var repository: VaultRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, KeyBookDatabase::class.java).build()
        repository = VaultRepository(
            database,
            database.vaultDao(),
            VaultCrypto(),
            Json { encodeDefaults = true; explicitNulls = true },
            BuiltinCatalogRepository(context),
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun encryptedFourLevelTreePersistsAndCascades() = runBlocking {
        val workspaceId = repository.initialize()
        val company = repository.saveCompany(
            workspaceId,
            null,
            CompanyDraft("测试企业-${UUID.randomUUID()}", "企业备注", "https://example.com"),
        )
        val app = repository.saveApp(workspaceId, company.id, null, AppDraft("测试应用"))
        val account = repository.saveAccount(
            workspaceId,
            app.id,
            null,
            AccountDraft("日常账号", "person@example.com", "Sensitive-Password-123!", "账号备注"),
        )
        repository.saveSecretItem(
            workspaceId,
            account.id,
            null,
            SecretItemDraft("支付密码", "884422", "六位数字"),
        )

        assertEquals("Sensitive-Password-123!", repository.getAccount(workspaceId, account.id)?.password)
        assertEquals(1, repository.listSecretItems(workspaceId, account.id).size)

        val raw = database.vaultDao().getAccount(workspaceId, account.id)!!
        assertFalse(raw.payloadCiphertext.toString(Charsets.UTF_8).contains("Sensitive-Password-123!"))
        assertFalse(raw.payloadCiphertext.toString(Charsets.UTF_8).contains("person@example.com"))
        assertEquals(12, raw.payloadNonce.size)

        repository.deleteApp(workspaceId, app.id)
        assertEquals(0, repository.listAccounts(workspaceId, app.id).size)
        assertEquals(0, repository.listSecretItems(workspaceId, account.id).size)
        repository.deleteCompany(workspaceId, company.id)
        assertEquals(0, repository.listCompanies(workspaceId).size)
    }

    @Test
    fun arbitraryReorderMovesEachRecordLevelInOneOperation() = runBlocking {
        val workspaceId = repository.initialize()
        val companies = (1..3).map { index ->
            repository.saveCompany(workspaceId, null, CompanyDraft("企业$index"))
        }
        repository.moveCompanyBy(workspaceId, companies[2].id, -2)
        assertEquals(listOf("企业3", "企业1", "企业2"), repository.listCompanies(workspaceId).map { it.name })

        val apps = (1..3).map { index ->
            repository.saveApp(workspaceId, companies[0].id, null, AppDraft("应用$index"))
        }
        repository.moveAppBy(workspaceId, apps[2].id, -2)
        assertEquals(listOf("应用3", "应用1", "应用2"), repository.listApps(workspaceId, companies[0].id).map { it.name })

        val accounts = (1..3).map { index ->
            repository.saveAccount(
                workspaceId,
                apps[0].id,
                null,
                AccountDraft("账号$index", "login$index", "password$index"),
            )
        }
        repository.moveAccountBy(workspaceId, accounts[2].id, -2)
        assertEquals(listOf("账号3", "账号1", "账号2"), repository.listAccounts(workspaceId, apps[0].id).map { it.name })

        val secrets = (1..3).map { index ->
            repository.saveSecretItem(
                workspaceId,
                accounts[0].id,
                null,
                SecretItemDraft("附加项$index", "secret$index"),
            )
        }
        repository.moveSecretItemBy(workspaceId, secrets[2].id, -2)
        assertEquals(
            listOf("附加项3", "附加项1", "附加项2"),
            repository.listSecretItems(workspaceId, accounts[0].id).map { it.name },
        )
    }

    @Test
    fun aadChangePreventsCiphertextReuseUnderAnotherParent() = runBlocking {
        val workspaceId = repository.initialize()
        val companyA = repository.saveCompany(workspaceId, null, CompanyDraft("A"))
        val companyB = repository.saveCompany(workspaceId, null, CompanyDraft("B"))
        val app = repository.saveApp(workspaceId, companyA.id, null, AppDraft("应用"))
        val raw = database.vaultDao().getApp(workspaceId, app.id)!!
        val movedWithoutReencryption = raw.copy(companyId = companyB.id)

        assertThrows(Exception::class.java) {
            runBlocking {
                database.vaultDao().updateApp(movedWithoutReencryption)
                repository.getApp(workspaceId, app.id)
            }
        }
        assertArrayEquals(raw.payloadCiphertext, movedWithoutReencryption.payloadCiphertext)
    }

    @Test
    fun workspaceDeletionRequiresAnEmptyWorkspaceAndKeepsAtLeastOne() = runBlocking {
        val firstId = repository.initialize()

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repository.deleteWorkspace(firstId) }
        }

        val second = repository.createWorkspace("第二空间")
        repository.saveCompany(second.id, null, CompanyDraft("仍有记录"))
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repository.deleteWorkspace(second.id) }
        }

        repository.deleteWorkspace(firstId)
        assertEquals(listOf(second.id), repository.listWorkspaces().map { it.id })
    }

    @Test
    fun destructiveDeleteRejectsScopeChangesAfterConfirmationSnapshot() = runBlocking {
        val workspaceId = repository.initialize()
        val company = repository.saveCompany(workspaceId, null, CompanyDraft("范围核对企业"))
        val app = repository.saveApp(workspaceId, company.id, null, AppDraft("范围核对应用"))
        val account = repository.saveAccount(
            workspaceId,
            app.id,
            null,
            AccountDraft("范围核对账号", "login", "password"),
        )

        val appSnapshot = repository.getAppDeletionCounts(workspaceId, app.id)
        val accountSnapshot = repository.getAccountDeletionCounts(workspaceId, account.id)
        repository.saveAccount(
            workspaceId,
            app.id,
            null,
            AccountDraft("后来新增账号", "later", "password"),
        )
        repository.saveSecretItem(
            workspaceId,
            account.id,
            null,
            SecretItemDraft("后来新增附加项", "secret"),
        )

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repository.deleteApp(workspaceId, app.id, appSnapshot) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repository.deleteAccount(workspaceId, account.id, accountSnapshot) }
        }
        assertEquals(app.id, repository.getApp(workspaceId, app.id)?.id)
        assertEquals(account.id, repository.getAccount(workspaceId, account.id)?.id)
    }

    @Test
    fun catalogIsEmptyAndCopiedCustomIconIsEncrypted() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val catalog = BuiltinCatalogRepository(context).entries()
        assertTrue(catalog.isEmpty())

        val source = testIcon()
        val workspaceId = repository.initialize()
        val company = repository.saveCompany(
            workspaceId,
            null,
            CompanyDraft(
                name = "自定义图标企业",
                newIcon = source,
            ),
        )
        val iconId = company.iconAssetId!!
        val raw = database.vaultDao().getIconAsset(workspaceId, iconId)!!
        assertFalse(raw.payloadCiphertext.copyOfRange(0, 8).contentEquals(source.pngBytes.copyOfRange(0, 8)))
        assertArrayEquals(source.pngBytes, repository.getIconAsset(workspaceId, iconId)?.pngBytes)
    }

    @Test
    fun iconAssetsAreDeletedOnlyAfterTheirLastEncryptedReferenceIsRemoved() = runBlocking {
        val iconDraft = testIcon()
        val workspaceId = repository.initialize()

        val company = repository.saveCompany(
            workspaceId,
            null,
            CompanyDraft("共享图标企业", newIcon = iconDraft),
        )
        val sharedIconId = requireNotNull(company.iconAssetId)
        val preset = repository.saveCatalogPreset(
            workspaceId,
            null,
            CatalogPresetDraft(CatalogType.COMPANY, "共享图标预设", iconAssetId = sharedIconId),
        )

        repository.saveCompany(
            workspaceId,
            company.id,
            CompanyDraft("共享图标企业", removeIcon = true),
        )
        assertEquals(1, database.vaultDao().getIconAssets(workspaceId).size)

        repository.deleteCatalogPreset(workspaceId, preset.id)
        assertEquals(0, database.vaultDao().getIconAssets(workspaceId).size)

        val parent = repository.saveCompany(workspaceId, null, CompanyDraft("应用父企业"))
        val app = repository.saveApp(
            workspaceId,
            parent.id,
            null,
            AppDraft("图标应用", newIcon = iconDraft),
        )
        val oldIconId = requireNotNull(app.iconAssetId)
        val updated = repository.saveApp(
            workspaceId,
            parent.id,
            app.id,
            AppDraft("图标应用", newIcon = iconDraft),
        )
        assertEquals(null, repository.getIconAsset(workspaceId, oldIconId))
        assertEquals(1, database.vaultDao().getIconAssets(workspaceId).size)

        repository.deleteApp(workspaceId, updated.id)
        assertEquals(0, database.vaultDao().getIconAssets(workspaceId).size)
    }

    @Test
    fun workspaceGeneratorPresetsAreEncryptedAndDefaultFallsBackAfterDelete() = runBlocking {
        val workspaceId = repository.initialize()
        assertEquals(
            GeneratorSelection(GeneratorSelectionKind.BUILTIN, BuiltinGeneratorPresets.standard.id),
            repository.getDefaultGenerator(workspaceId),
        )

        val preset = repository.saveGeneratorPreset(
            workspaceId,
            null,
            GeneratorPresetDraft("仅测试预设", PasswordRules(length = 32, symbols = false)),
        )
        val laterPreset = repository.saveGeneratorPreset(
            workspaceId,
            null,
            GeneratorPresetDraft("稍后创建预设", PasswordRules(length = 24, symbols = false)),
        )
        val ordered = repository.listGeneratorPresets(workspaceId)
        assertEquals(listOf(preset.id, laterPreset.id), ordered.map { it.id })
        assertTrue(ordered[1].createdAt > ordered[0].createdAt)
        repository.setDefaultGenerator(
            workspaceId,
            GeneratorSelection(GeneratorSelectionKind.CUSTOM, preset.id),
        )
        assertEquals(preset.id, repository.getDefaultGenerator(workspaceId).id)
        assertFalse(
            database.vaultDao().getGeneratorPreset(workspaceId, preset.id)!!
                .payloadCiphertext.toString(Charsets.UTF_8).contains("仅测试预设"),
        )

        repository.deleteGeneratorPreset(workspaceId, preset.id)
        assertEquals(BuiltinGeneratorPresets.standard.id, repository.getDefaultGenerator(workspaceId).id)
    }

    @Test
    fun customCatalogAndBuiltinOverridesAreWorkspaceScopedAndEncrypted() = runBlocking {
        val workspaceId = repository.initialize()
        val custom = repository.saveCatalogPreset(
            workspaceId,
            null,
            CatalogPresetDraft(CatalogType.APP, "自定义模板", websiteUrl = "https://example.com"),
        )
        assertEquals("自定义模板", repository.listCatalogPresets(workspaceId).single().name)
        assertFalse(
            database.vaultDao().getCatalogPreset(workspaceId, custom.id)!!
                .payloadCiphertext.toString(Charsets.UTF_8).contains("自定义模板"),
        )

        assertEquals(0, repository.listBuiltinCatalogOverrides(workspaceId).size)
    }

    @Test
    fun encryptedWorkspaceBackupRoundTripRestoresTreeIconsAndWorkspaceConfiguration() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val catalogRepository = BuiltinCatalogRepository(context)
        val json = Json { encodeDefaults = true; explicitNulls = true; ignoreUnknownKeys = false }
        val transfer = BackupTransferRepository(
            repository,
            BackupCodec(json),
            BackupValidator(catalogRepository),
            BackupImporter(database, database.vaultDao(), VaultCrypto(), json),
        )
        val workspaceId = repository.initialize()
        repository.renameWorkspace(workspaceId, "备份源")
        val icon = testIcon()
        val company = repository.saveCompany(
            workspaceId,
            null,
            CompanyDraft(
                "往返企业",
                websiteUrl = "https://example.com",
                newIcon = icon,
            ),
        )
        val app = repository.saveApp(workspaceId, company.id, null, AppDraft("往返应用"))
        val account = repository.saveAccount(
            workspaceId,
            app.id,
            null,
            AccountDraft("往返账号", "backup@example.com", "RoundTrip-Password!"),
        )
        repository.saveSecretItem(
            workspaceId,
            account.id,
            null,
            SecretItemDraft("交易密码", "778899"),
        )
        val generatorPreset = repository.saveGeneratorPreset(
            workspaceId,
            null,
            GeneratorPresetDraft("备份规则", PasswordRules(length = 28)),
        )
        repository.saveGeneratorPreset(
            workspaceId,
            null,
            GeneratorPresetDraft("备份规则二", PasswordRules(length = 36)),
        )
        repository.setDefaultGenerator(
            workspaceId,
            GeneratorSelection(GeneratorSelectionKind.CUSTOM, generatorPreset.id),
        )
        repository.saveCatalogPreset(
            workspaceId,
            null,
            CatalogPresetDraft(CatalogType.APP, "备份模板", websiteUrl = "https://example.com/app"),
        )

        val encoded = transfer.export(
            BackupScope.WORKSPACES,
            setOf(workspaceId),
            currentWorkspaceId = null,
            password = "测试文件密码🔐123456",
        )
        val decoded = transfer.decodeAndValidate(encoded, "测试文件密码🔐123456")
        val result = transfer.import(decoded, CopyWorkspaceImport)
        val restoredId = result.affectedWorkspaceIds.single()

        assertFalse(restoredId == workspaceId)
        assertEquals("备份源（导入）", repository.listWorkspaces().first { it.id == restoredId }.name)
        val restoredCompany = repository.listCompanies(restoredId).single()
        val restoredApp = repository.listApps(restoredId, restoredCompany.id).single()
        val restoredAccount = repository.getAccount(
            restoredId,
            repository.listAccounts(restoredId, restoredApp.id).single().id,
        )!!
        assertEquals("RoundTrip-Password!", restoredAccount.password)
        assertEquals("778899", repository.listSecretItems(restoredId, restoredAccount.id).single().password)
        assertArrayEquals(icon.pngBytes, repository.getIconAsset(restoredId, restoredCompany.iconAssetId!!)?.pngBytes)
        assertEquals(
            listOf("备份规则", "备份规则二"),
            repository.listGeneratorPresets(restoredId).map { it.name },
        )
        assertEquals(
            repository.listGeneratorPresets(restoredId).first().id,
            repository.getDefaultGenerator(restoredId).id,
        )
        assertEquals("备份模板", repository.listCatalogPresets(restoredId).single().name)
    }

    @Test
    fun workspaceReplacementRollsBackWhenImportFailsAfterClearingTarget() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val catalogRepository = BuiltinCatalogRepository(context)
        val json = Json { encodeDefaults = true; explicitNulls = true; ignoreUnknownKeys = false }
        val importer = BackupImporter(database, database.vaultDao(), VaultCrypto(), json)
        val transfer = BackupTransferRepository(
            repository,
            BackupCodec(json),
            BackupValidator(catalogRepository),
            importer,
        )

        val sourceId = repository.initialize()
        val sourceCompany = repository.saveCompany(sourceId, null, CompanyDraft("替换来源"))
        repository.saveApp(sourceId, sourceCompany.id, null, AppDraft("来源应用"))
        val encoded = transfer.export(BackupScope.WORKSPACES, setOf(sourceId), null, null)
        val decoded = transfer.decodeAndValidate(encoded, null)

        val target = repository.createWorkspace("替换目标")
        val originalCompany = repository.saveCompany(target.id, null, CompanyDraft("目标旧企业"))
        val originalApp = repository.saveApp(target.id, originalCompany.id, null, AppDraft("目标旧应用"))
        val originalAccount = repository.saveAccount(
            target.id,
            originalApp.id,
            null,
            AccountDraft("目标旧账号", "old@example.com", "Old-Password!"),
        )

        val brokenGroup = decoded.workspaces.single().let { group ->
            group.copy(
                data = group.data.copy(
                    companies = group.data.companies.mapIndexed { index, company ->
                        if (index == 0) company.copy(iconAssetId = UUID.randomUUID().toString()) else company
                    },
                ),
            )
        }
        val broken = decoded.copy(workspaces = listOf(brokenGroup))

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                importer.import(
                    broken,
                    ReplaceWorkspaceImport(mapOf(brokenGroup.id to target.id)),
                )
            }
        }

        assertEquals("目标旧企业", repository.listCompanies(target.id).single().name)
        assertEquals("目标旧应用", repository.listApps(target.id, originalCompany.id).single().name)
        assertEquals("Old-Password!", repository.getAccount(target.id, originalAccount.id)?.password)
    }

    @Test
    fun appImportMapsEachSourceCompanyAndRenamesConflictingApps() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val catalogRepository = BuiltinCatalogRepository(context)
        val json = Json { encodeDefaults = true; explicitNulls = true; ignoreUnknownKeys = false }
        val transfer = BackupTransferRepository(
            repository,
            BackupCodec(json),
            BackupValidator(catalogRepository),
            BackupImporter(database, database.vaultDao(), VaultCrypto(), json),
        )

        val sourceId = repository.initialize()
        val firstSourceCompany = repository.saveCompany(sourceId, null, CompanyDraft("来源甲"))
        val conflictingSourceApp = repository.saveApp(
            sourceId,
            firstSourceCompany.id,
            null,
            AppDraft("重复应用"),
        )
        val sourceAccount = repository.saveAccount(
            sourceId,
            conflictingSourceApp.id,
            null,
            AccountDraft("导入账号", "import@example.com", "Imported-Password!"),
        )
        repository.saveSecretItem(
            sourceId,
            sourceAccount.id,
            null,
            SecretItemDraft("导入附加项", "246810"),
        )
        val secondSourceCompany = repository.saveCompany(sourceId, null, CompanyDraft("来源乙", note = "来源备注"))
        val secondSourceApp = repository.saveApp(sourceId, secondSourceCompany.id, null, AppDraft("独立应用"))

        val encoded = transfer.export(
            BackupScope.APPS,
            setOf(conflictingSourceApp.id, secondSourceApp.id),
            sourceId,
            null,
        )
        val decoded = transfer.decodeAndValidate(encoded, null)
        val sourceCompanies = decoded.workspaces.single().data.companies.associateBy { it.name }

        val target = repository.createWorkspace("应用导入目标")
        val existingCompany = repository.saveCompany(
            target.id,
            null,
            CompanyDraft("已有企业", note = "保留资料"),
        )
        repository.saveApp(target.id, existingCompany.id, null, AppDraft("重复应用"))

        transfer.import(
            decoded,
            AppendAppsImport(
                targetWorkspaceId = target.id,
                companyTargets = mapOf(
                    requireNotNull(sourceCompanies["来源甲"]).id to AppCompanyTarget(
                        existingCompanyId = existingCompany.id,
                    ),
                    requireNotNull(sourceCompanies["来源乙"]).id to AppCompanyTarget(
                        newCompanyName = "新建企业",
                    ),
                ),
            ),
        )

        assertEquals("保留资料", repository.getCompany(target.id, existingCompany.id)?.note)
        val importedConflict = repository.listApps(target.id, existingCompany.id)
            .single { it.name == "重复应用（导入）" }
        val importedAccount = repository.listAccounts(target.id, importedConflict.id).single()
        assertEquals("Imported-Password!", repository.getAccount(target.id, importedAccount.id)?.password)
        assertEquals("246810", repository.listSecretItems(target.id, importedAccount.id).single().password)

        val newCompany = repository.listCompanies(target.id).single { it.name == "新建企业" }
        assertEquals("来源备注", newCompany.note)
        assertEquals("独立应用", repository.listApps(target.id, newCompany.id).single().name)
    }

    @Test
    fun partialExportRejectsAnySelectionThatNoLongerExists() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val json = Json { encodeDefaults = true; explicitNulls = true; ignoreUnknownKeys = false }
        val transfer = BackupTransferRepository(
            repository,
            BackupCodec(json),
            BackupValidator(BuiltinCatalogRepository(context)),
            BackupImporter(database, database.vaultDao(), VaultCrypto(), json),
        )
        val workspaceId = repository.initialize()
        val company = repository.saveCompany(workspaceId, null, CompanyDraft("仍存在的企业"))
        val app = repository.saveApp(workspaceId, company.id, null, AppDraft("仍存在的应用"))
        val missingId = UUID.randomUUID().toString()

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                transfer.export(
                    BackupScope.COMPANIES,
                    setOf(company.id, missingId),
                    workspaceId,
                    null,
                )
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                transfer.export(
                    BackupScope.APPS,
                    setOf(app.id, missingId),
                    workspaceId,
                    null,
                )
            }
        }
        Unit
    }

    @Test
    fun manualCreateHonorsWorkspaceRecordLimitWithoutBlockingEdits() = runBlocking {
        val workspaceId = repository.initialize()
        val company = repository.saveCompany(workspaceId, null, CompanyDraft("容量企业"))
        database.openHelper.writableDatabase.execSQL(
            """
            WITH RECURSIVE counter(value) AS (
                SELECT 0
                UNION ALL
                SELECT value + 1 FROM counter WHERE value < 99
            )
            INSERT INTO apps (
                workspaceId, id, companyId, payloadNonce, payloadCiphertext, formatVersion, keyVersion
            )
            SELECT ?, 'capacity-' || (a.value * 100 + b.value), ?,
                X'000000000000000000000000', X'00', 1, 1
            FROM counter AS a CROSS JOIN counter AS b
            LIMIT 9999
            """.trimIndent(),
            arrayOf(workspaceId, company.id),
        )
        assertEquals(10_000, database.vaultDao().recordCount(workspaceId))

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                repository.saveCompany(workspaceId, null, CompanyDraft("超限企业"))
            }
        }
        assertEquals(10_000, database.vaultDao().recordCount(workspaceId))

        val edited = repository.saveCompany(
            workspaceId,
            company.id,
            CompanyDraft("容量企业已编辑"),
        )
        assertEquals("容量企业已编辑", edited.name)
        assertEquals(10_000, database.vaultDao().recordCount(workspaceId))
    }
}
