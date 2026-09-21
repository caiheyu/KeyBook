package com.github.caiheyu.keybook

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import com.github.caiheyu.keybook.core.clipboard.SensitiveClipboard
import com.github.caiheyu.keybook.data.settings.SettingsRepository

@EntryPoint
@InstallIn(SingletonComponent::class)
interface DebugClipboardEntryPoint {
    fun sensitiveClipboard(): SensitiveClipboard

    fun settingsRepository(): SettingsRepository
}
