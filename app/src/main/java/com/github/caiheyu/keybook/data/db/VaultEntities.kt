package com.github.caiheyu.keybook.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(tableName = "workspaces")
data class WorkspaceEntity(
    @androidx.room.PrimaryKey val id: String,
    val wrappedKeyNonce: ByteArray,
    val wrappedKeyCiphertext: ByteArray,
    val payloadNonce: ByteArray,
    val payloadCiphertext: ByteArray,
    val formatVersion: Int,
    val keyVersion: Int,
)

@Entity(
    tableName = "companies",
    primaryKeys = ["workspaceId", "id"],
    foreignKeys = [
        ForeignKey(
            entity = WorkspaceEntity::class,
            parentColumns = ["id"],
            childColumns = ["workspaceId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("workspaceId")],
)
data class CompanyEntity(
    val workspaceId: String,
    val id: String,
    val payloadNonce: ByteArray,
    val payloadCiphertext: ByteArray,
    val formatVersion: Int,
    val keyVersion: Int,
) {
    companion object
}

@Entity(
    tableName = "apps",
    primaryKeys = ["workspaceId", "id"],
    foreignKeys = [
        ForeignKey(
            entity = CompanyEntity::class,
            parentColumns = ["workspaceId", "id"],
            childColumns = ["workspaceId", "companyId"],
            onDelete = ForeignKey.NO_ACTION,
        ),
    ],
    indices = [Index(value = ["workspaceId", "companyId"])],
)
data class AppEntity(
    val workspaceId: String,
    val id: String,
    val companyId: String,
    val payloadNonce: ByteArray,
    val payloadCiphertext: ByteArray,
    val formatVersion: Int,
    val keyVersion: Int,
) {
    companion object
}

@Entity(
    tableName = "accounts",
    primaryKeys = ["workspaceId", "id"],
    foreignKeys = [
        ForeignKey(
            entity = AppEntity::class,
            parentColumns = ["workspaceId", "id"],
            childColumns = ["workspaceId", "appId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["workspaceId", "appId"])],
)
data class AccountEntity(
    val workspaceId: String,
    val id: String,
    val appId: String,
    val payloadNonce: ByteArray,
    val payloadCiphertext: ByteArray,
    val formatVersion: Int,
    val keyVersion: Int,
) {
    companion object
}

@Entity(
    tableName = "secret_items",
    primaryKeys = ["workspaceId", "id"],
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["workspaceId", "id"],
            childColumns = ["workspaceId", "accountId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["workspaceId", "accountId"])],
)
data class SecretItemEntity(
    val workspaceId: String,
    val id: String,
    val accountId: String,
    val payloadNonce: ByteArray,
    val payloadCiphertext: ByteArray,
    val formatVersion: Int,
    val keyVersion: Int,
) {
    companion object
}

@Entity(
    tableName = "icon_assets",
    primaryKeys = ["workspaceId", "id"],
    foreignKeys = [
        ForeignKey(
            entity = WorkspaceEntity::class,
            parentColumns = ["id"],
            childColumns = ["workspaceId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("workspaceId")],
)
data class IconAssetEntity(
    val workspaceId: String,
    val id: String,
    val payloadNonce: ByteArray,
    val payloadCiphertext: ByteArray,
    val formatVersion: Int,
    val keyVersion: Int,
)

@Entity(
    tableName = "generator_presets",
    primaryKeys = ["workspaceId", "id"],
    foreignKeys = [
        ForeignKey(
            entity = WorkspaceEntity::class,
            parentColumns = ["id"],
            childColumns = ["workspaceId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("workspaceId")],
)
data class GeneratorPresetEntity(
    val workspaceId: String,
    val id: String,
    val payloadNonce: ByteArray,
    val payloadCiphertext: ByteArray,
    val formatVersion: Int,
    val keyVersion: Int,
) {
    companion object
}

@Entity(
    tableName = "catalog_presets",
    primaryKeys = ["workspaceId", "id"],
    foreignKeys = [
        ForeignKey(
            entity = WorkspaceEntity::class,
            parentColumns = ["id"],
            childColumns = ["workspaceId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("workspaceId")],
)
data class CatalogPresetEntity(
    val workspaceId: String,
    val id: String,
    val payloadNonce: ByteArray,
    val payloadCiphertext: ByteArray,
    val formatVersion: Int,
    val keyVersion: Int,
) {
    companion object
}

@Entity(
    tableName = "builtin_catalog_overrides",
    primaryKeys = ["workspaceId", "builtinId"],
    foreignKeys = [
        ForeignKey(
            entity = WorkspaceEntity::class,
            parentColumns = ["id"],
            childColumns = ["workspaceId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("workspaceId")],
)
data class BuiltinCatalogOverrideEntity(
    val workspaceId: String,
    val builtinId: String,
    val payloadNonce: ByteArray,
    val payloadCiphertext: ByteArray,
    val formatVersion: Int,
    val keyVersion: Int,
) {
    companion object
}

@Entity(
    tableName = "workspace_configs",
    foreignKeys = [
        ForeignKey(
            entity = WorkspaceEntity::class,
            parentColumns = ["id"],
            childColumns = ["workspaceId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class WorkspaceConfigEntity(
    @androidx.room.PrimaryKey val workspaceId: String,
    val payloadNonce: ByteArray,
    val payloadCiphertext: ByteArray,
    val formatVersion: Int,
    val keyVersion: Int,
)
