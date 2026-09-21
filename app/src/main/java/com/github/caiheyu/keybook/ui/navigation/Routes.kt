package com.github.caiheyu.keybook.ui.navigation

import kotlinx.serialization.Serializable

@Serializable data object VaultRoute
@Serializable data class GeneratorRoute(val openPresetManager: Boolean = false)
@Serializable data object SettingsRoute
@Serializable data object AboutRoute
@Serializable data object SecurityRoute
@Serializable data object CatalogRoute
@Serializable
data class CatalogEditRoute(
    val type: String,
    val targetId: String? = null,
    val copyFromId: String? = null,
    val sourceRecordId: String? = null,
)
@Serializable data class ExportRoute(val initialScope: String = "WORKSPACES", val preselectedId: String? = null)
@Serializable data object ImportRoute
@Serializable data class CompanyRoute(val companyId: String)
@Serializable data class AppRoute(val companyId: String, val appId: String)
@Serializable
data class AccountRoute(
    val companyId: String,
    val appId: String,
    val accountId: String,
    val highlightSecretId: String? = null,
)
@Serializable data class CompanyEditRoute(val companyId: String? = null)
@Serializable data class AppEditRoute(val companyId: String, val appId: String? = null)
@Serializable data class AccountEditRoute(val appId: String, val accountId: String? = null)
@Serializable data class SecretEditRoute(val accountId: String, val secretId: String? = null)
