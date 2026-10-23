package com.paperly.app.data.scanner

import com.paperly.app.domain.scanner.ScanOutputSettings
import com.paperly.app.domain.scanner.ScanOutputSize
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

@Singleton
class ScanOutputSettingsImpl @Inject constructor() : ScanOutputSettings {
    // The old "scan_output_size" preference is no longer read (nothing is deleted from the data store).
    override val size: Flow<ScanOutputSize> = flowOf(ScanOutputSize.LARGE)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class ScanOutputSettingsModule {
    @Binds
    abstract fun bindScanOutputSettings(impl: ScanOutputSettingsImpl): ScanOutputSettings
}
