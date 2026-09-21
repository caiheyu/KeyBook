package com.github.caiheyu.keybook.data.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "keybook_preferences")

enum class ThemeMode { FOLLOW_SYSTEM, LIGHT, DARK }

data class ClipboardOwnershipMetadata(
    val token: String,
    val expiresAt: Long,
)

@Singleton
class SettingsRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    val themeMode: Flow<ThemeMode> = context.dataStore.data.map { preferences ->
        preferences[THEME_MODE]?.let { stored ->
            ThemeMode.entries.firstOrNull { it.name == stored }
        } ?: ThemeMode.FOLLOW_SYSTEM
    }

    val currentWorkspaceId: Flow<String?> = context.dataStore.data.map { it[CURRENT_WORKSPACE_ID] }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[THEME_MODE] = mode.name }
    }

    suspend fun setCurrentWorkspaceId(id: String) {
        context.dataStore.edit { it[CURRENT_WORKSPACE_ID] = id }
    }

    suspend fun setClipboardOwnership(token: String, expiresAt: Long) {
        context.dataStore.edit {
            it[CLIPBOARD_OWNER_TOKEN] = token
            it[CLIPBOARD_EXPIRES_AT] = expiresAt
        }
    }

    suspend fun getClipboardOwnership(): ClipboardOwnershipMetadata? {
        val preferences = context.dataStore.data.first()
        val token = preferences[CLIPBOARD_OWNER_TOKEN] ?: return null
        val expiresAt = preferences[CLIPBOARD_EXPIRES_AT] ?: return null
        return ClipboardOwnershipMetadata(token, expiresAt)
    }

    suspend fun clearClipboardOwnership(expectedToken: String? = null) {
        context.dataStore.edit {
            if (expectedToken == null || it[CLIPBOARD_OWNER_TOKEN] == expectedToken) {
                it.remove(CLIPBOARD_OWNER_TOKEN)
                it.remove(CLIPBOARD_EXPIRES_AT)
            }
        }
    }

    companion object {
        private val THEME_MODE = stringPreferencesKey("theme_mode")
        private val CURRENT_WORKSPACE_ID = stringPreferencesKey("current_workspace_id")
        private val CLIPBOARD_OWNER_TOKEN = stringPreferencesKey("clipboard_owner_token")
        private val CLIPBOARD_EXPIRES_AT = longPreferencesKey("clipboard_expires_at")
    }
}
