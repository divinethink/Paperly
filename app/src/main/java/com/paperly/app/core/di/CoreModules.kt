package com.paperly.app.core.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import com.paperly.app.core.database.DocumentDao
import com.paperly.app.core.database.PaperlyDatabase
import com.paperly.app.core.file.AppPrivateDocumentFileStore
import com.paperly.app.core.file.DocumentFileStore
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    // No destructive fallback: schema changes need explicit, backed-up migrations (zero data loss).
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): PaperlyDatabase =
        Room.databaseBuilder(context, PaperlyDatabase::class.java, "paperly.db").build()

    @Provides
    fun provideDocumentDao(db: PaperlyDatabase): DocumentDao = db.documentDao()
}

@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {
    @Provides
    @Singleton
    fun provideSettingsDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("settings") }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class FileModule {
    @Binds
    abstract fun bindDocumentFileStore(impl: AppPrivateDocumentFileStore): DocumentFileStore
}
