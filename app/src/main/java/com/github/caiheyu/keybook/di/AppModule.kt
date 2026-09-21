package com.github.caiheyu.keybook.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import com.github.caiheyu.keybook.data.db.KeyBookDatabase
import com.github.caiheyu.keybook.data.db.VaultDao

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): KeyBookDatabase =
        Room.databaseBuilder(context, KeyBookDatabase::class.java, "keybook.db")
            .build()

    @Provides
    fun provideVaultDao(database: KeyBookDatabase): VaultDao = database.vaultDao()

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        encodeDefaults = true
        explicitNulls = true
        ignoreUnknownKeys = false
    }
}
