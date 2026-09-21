package com.github.caiheyu.keybook.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        WorkspaceEntity::class,
        CompanyEntity::class,
        AppEntity::class,
        AccountEntity::class,
        SecretItemEntity::class,
        IconAssetEntity::class,
        GeneratorPresetEntity::class,
        CatalogPresetEntity::class,
        BuiltinCatalogOverrideEntity::class,
        WorkspaceConfigEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class KeyBookDatabase : RoomDatabase() {
    abstract fun vaultDao(): VaultDao
}
