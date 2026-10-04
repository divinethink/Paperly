package com.paperly.app.data.sync.drive

import com.paperly.app.domain.auth.DriveAuth
import com.paperly.app.domain.sync.CloudInventory
import com.paperly.app.domain.sync.RemoteFileStore
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DriveBindingsModule {
    @Binds
    abstract fun bindRemoteFileStore(impl: DriveFileStore): RemoteFileStore

    @Binds
    abstract fun bindDriveAuth(impl: GoogleDriveAuth): DriveAuth

    @Binds
    abstract fun bindUploadSessions(impl: DataStoreUploadSessions): UploadSessionStore
}

@Module
@InstallIn(SingletonComponent::class)
object DriveProvidesModule {
    @Provides
    @Singleton
    fun provideDriveApi(): DriveFileApi = DriveFileApi(DriveEndpoints.PRODUCTION)

    @Provides
    @Singleton
    fun provideCloudInventory(auth: DriveAuth): CloudInventory = DriveInventory(DriveEndpoints.PRODUCTION, auth)
}
