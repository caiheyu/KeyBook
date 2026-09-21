package com.github.caiheyu.keybook.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface VaultDao {
    @Query("SELECT * FROM workspaces")
    suspend fun getWorkspaces(): List<WorkspaceEntity>

    @Query("SELECT * FROM workspaces WHERE id = :workspaceId")
    suspend fun getWorkspace(workspaceId: String): WorkspaceEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertWorkspace(entity: WorkspaceEntity)

    @Update
    suspend fun updateWorkspace(entity: WorkspaceEntity)

    @Delete
    suspend fun deleteWorkspace(entity: WorkspaceEntity)

    @Query("SELECT COUNT(*) FROM workspaces")
    suspend fun workspaceCount(): Int

    @Query("SELECT * FROM companies WHERE workspaceId = :workspaceId")
    suspend fun getCompanies(workspaceId: String): List<CompanyEntity>

    @Query("SELECT * FROM companies WHERE workspaceId = :workspaceId AND id = :id")
    suspend fun getCompany(workspaceId: String, id: String): CompanyEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCompany(entity: CompanyEntity)

    @Update
    suspend fun updateCompany(entity: CompanyEntity)

    @Delete
    suspend fun deleteCompany(entity: CompanyEntity)

    @Query("SELECT COUNT(*) FROM companies WHERE workspaceId = :workspaceId")
    suspend fun companyCount(workspaceId: String): Int

    @Query("SELECT * FROM apps WHERE workspaceId = :workspaceId AND companyId = :companyId")
    suspend fun getApps(workspaceId: String, companyId: String): List<AppEntity>

    @Query("SELECT * FROM apps WHERE workspaceId = :workspaceId")
    suspend fun getAllApps(workspaceId: String): List<AppEntity>

    @Query("SELECT * FROM apps WHERE workspaceId = :workspaceId AND id = :id")
    suspend fun getApp(workspaceId: String, id: String): AppEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertApp(entity: AppEntity)

    @Update
    suspend fun updateApp(entity: AppEntity)

    @Delete
    suspend fun deleteApp(entity: AppEntity)

    @Query("SELECT COUNT(*) FROM apps WHERE workspaceId = :workspaceId AND companyId = :companyId")
    suspend fun appCount(workspaceId: String, companyId: String): Int

    @Query("SELECT * FROM accounts WHERE workspaceId = :workspaceId AND appId = :appId")
    suspend fun getAccounts(workspaceId: String, appId: String): List<AccountEntity>

    @Query("SELECT * FROM accounts WHERE workspaceId = :workspaceId")
    suspend fun getAllAccounts(workspaceId: String): List<AccountEntity>

    @Query("SELECT * FROM accounts WHERE workspaceId = :workspaceId AND id = :id")
    suspend fun getAccount(workspaceId: String, id: String): AccountEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAccount(entity: AccountEntity)

    @Update
    suspend fun updateAccount(entity: AccountEntity)

    @Delete
    suspend fun deleteAccount(entity: AccountEntity)

    @Query("SELECT COUNT(*) FROM accounts WHERE workspaceId = :workspaceId AND appId = :appId")
    suspend fun accountCount(workspaceId: String, appId: String): Int

    @Query("SELECT COUNT(*) FROM accounts WHERE workspaceId = :workspaceId")
    suspend fun workspaceAccountCount(workspaceId: String): Int

    @Query(
        "SELECT " +
            "(SELECT COUNT(*) FROM companies WHERE workspaceId = :workspaceId) + " +
            "(SELECT COUNT(*) FROM apps WHERE workspaceId = :workspaceId) + " +
            "(SELECT COUNT(*) FROM accounts WHERE workspaceId = :workspaceId) + " +
            "(SELECT COUNT(*) FROM secret_items WHERE workspaceId = :workspaceId)",
    )
    suspend fun recordCount(workspaceId: String): Int

    @Query("SELECT * FROM secret_items WHERE workspaceId = :workspaceId AND accountId = :accountId")
    suspend fun getSecretItems(workspaceId: String, accountId: String): List<SecretItemEntity>

    @Query("SELECT * FROM secret_items WHERE workspaceId = :workspaceId")
    suspend fun getAllSecretItems(workspaceId: String): List<SecretItemEntity>

    @Query("SELECT * FROM secret_items WHERE workspaceId = :workspaceId AND id = :id")
    suspend fun getSecretItem(workspaceId: String, id: String): SecretItemEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSecretItem(entity: SecretItemEntity)

    @Update
    suspend fun updateSecretItem(entity: SecretItemEntity)

    @Delete
    suspend fun deleteSecretItem(entity: SecretItemEntity)

    @Query("SELECT COUNT(*) FROM secret_items WHERE workspaceId = :workspaceId AND accountId = :accountId")
    suspend fun secretItemCount(workspaceId: String, accountId: String): Int

    @Query("SELECT * FROM icon_assets WHERE workspaceId = :workspaceId AND id = :id")
    suspend fun getIconAsset(workspaceId: String, id: String): IconAssetEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertIconAsset(entity: IconAssetEntity)

    @Delete
    suspend fun deleteIconAsset(entity: IconAssetEntity)

    @Query("SELECT * FROM icon_assets WHERE workspaceId = :workspaceId")
    suspend fun getIconAssets(workspaceId: String): List<IconAssetEntity>

    @Query("SELECT * FROM generator_presets WHERE workspaceId = :workspaceId")
    suspend fun getGeneratorPresets(workspaceId: String): List<GeneratorPresetEntity>

    @Query("SELECT * FROM generator_presets WHERE workspaceId = :workspaceId AND id = :id")
    suspend fun getGeneratorPreset(workspaceId: String, id: String): GeneratorPresetEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertGeneratorPreset(entity: GeneratorPresetEntity)

    @Update
    suspend fun updateGeneratorPreset(entity: GeneratorPresetEntity)

    @Delete
    suspend fun deleteGeneratorPreset(entity: GeneratorPresetEntity)

    @Query("SELECT * FROM catalog_presets WHERE workspaceId = :workspaceId")
    suspend fun getCatalogPresets(workspaceId: String): List<CatalogPresetEntity>

    @Query("SELECT * FROM catalog_presets WHERE workspaceId = :workspaceId AND id = :id")
    suspend fun getCatalogPreset(workspaceId: String, id: String): CatalogPresetEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCatalogPreset(entity: CatalogPresetEntity)

    @Update
    suspend fun updateCatalogPreset(entity: CatalogPresetEntity)

    @Delete
    suspend fun deleteCatalogPreset(entity: CatalogPresetEntity)

    @Query("SELECT * FROM builtin_catalog_overrides WHERE workspaceId = :workspaceId")
    suspend fun getBuiltinCatalogOverrides(workspaceId: String): List<BuiltinCatalogOverrideEntity>

    @Query("SELECT * FROM builtin_catalog_overrides WHERE workspaceId = :workspaceId AND builtinId = :builtinId")
    suspend fun getBuiltinCatalogOverride(workspaceId: String, builtinId: String): BuiltinCatalogOverrideEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBuiltinCatalogOverride(entity: BuiltinCatalogOverrideEntity)

    @Delete
    suspend fun deleteBuiltinCatalogOverride(entity: BuiltinCatalogOverrideEntity)

    @Query("SELECT * FROM workspace_configs WHERE workspaceId = :workspaceId")
    suspend fun getWorkspaceConfig(workspaceId: String): WorkspaceConfigEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertWorkspaceConfig(entity: WorkspaceConfigEntity)

    @Query("DELETE FROM apps WHERE workspaceId = :workspaceId")
    suspend fun deleteAllApps(workspaceId: String)

    @Query("DELETE FROM companies WHERE workspaceId = :workspaceId")
    suspend fun deleteAllCompanies(workspaceId: String)

    @Query("DELETE FROM generator_presets WHERE workspaceId = :workspaceId")
    suspend fun deleteAllGeneratorPresets(workspaceId: String)

    @Query("DELETE FROM catalog_presets WHERE workspaceId = :workspaceId")
    suspend fun deleteAllCatalogPresets(workspaceId: String)

    @Query("DELETE FROM builtin_catalog_overrides WHERE workspaceId = :workspaceId")
    suspend fun deleteAllBuiltinCatalogOverrides(workspaceId: String)

    @Query("DELETE FROM workspace_configs WHERE workspaceId = :workspaceId")
    suspend fun deleteWorkspaceConfig(workspaceId: String)

    @Query("DELETE FROM icon_assets WHERE workspaceId = :workspaceId")
    suspend fun deleteAllIconAssets(workspaceId: String)
}
