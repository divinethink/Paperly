package com.paperly.app.feature.scanner

import androidx.annotation.StringRes
import androidx.camera.core.ImageCapture
import androidx.camera.view.LifecycleCameraController
import com.paperly.app.R

internal enum class FlashChoice { Off, Auto, On }

@StringRes
internal fun flashLabel(choice: FlashChoice): Int = when (choice) {
    FlashChoice.Off -> R.string.scan_camera_flash_off
    FlashChoice.Auto -> R.string.scan_camera_flash_auto
    FlashChoice.On -> R.string.scan_camera_flash_on
}

internal fun applyFlash(controller: LifecycleCameraController, choice: FlashChoice) {
    controller.imageCaptureFlashMode = when (choice) {
        FlashChoice.Auto -> ImageCapture.FLASH_MODE_AUTO
        FlashChoice.On -> ImageCapture.FLASH_MODE_ON
        FlashChoice.Off -> ImageCapture.FLASH_MODE_OFF
    }
}
