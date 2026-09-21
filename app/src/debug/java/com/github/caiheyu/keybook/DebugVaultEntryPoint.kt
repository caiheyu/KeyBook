package com.github.caiheyu.keybook

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import com.github.caiheyu.keybook.data.VaultRepository

@EntryPoint
@InstallIn(SingletonComponent::class)
interface DebugVaultEntryPoint {
    fun vaultRepository(): VaultRepository
}
