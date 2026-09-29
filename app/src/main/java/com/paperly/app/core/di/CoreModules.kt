package com.paperly.app.core.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import com.paperly.app.core.database.DATABASE_NAME
import com.paperly.app.core.database.DATABASE_VERSION
import com.paperly.app.core.database.DatabaseBackup
import com.paperly.app.core.database.DocumentDao
import com.paperly.app.core.database.FolderDao
import com.paperly.app.core.database.PaperlyDatabase
import com.paperly.app.core.database.PaperlyMigrations
import com.paperly.app.core.database.TrashDao
import com.paperly.app.core.file.AppPrivateDocumentFileStore
import com.paperly.app.core.file.DocumentFileStore
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    // No destructive fallback: schema changes need explicit migrations, each preceded by a verified backup.
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): PaperlyDatabase {
        DatabaseBackup.backupBeforeMigration(
            dbFile = context.getDatabasePath(DATABASE_NAME),
            backupDir = File(context.filesDir, "db-backups"),
            targetVersion = DATABASE_VERSION,
        )
        return Room.databaseBuilder(context, PaperlyDatabase::class.java, DATABASE_NAME)
            .addMigrations(PaperlyMigrations.MIGRATION_1_2)
            .build()
    }

    @Provides
    fun provideDocumentDao(db: PaperlyDatabase): DocumentDao = db.documentDao()

    @Provides
    fun provideTrashDao(db: PaperlyDatabase): TrashDao = db.trashDao()

    @Provides
    fun provideFolderDao(db: PaperlyDatabase): FolderDao = db.folderDao()
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
