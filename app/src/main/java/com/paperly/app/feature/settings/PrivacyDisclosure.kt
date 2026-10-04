package com.paperly.app.feature.settings

import androidx.annotation.StringRes
import com.paperly.app.R

/** One third-party service (or "nothing leaves"): what it gets and under which condition. Text lives in strings.xml. */
data class DisclosureItem(@StringRes val title: Int, @StringRes val body: Int, @StringRes val condition: Int)

/**
 * The single list behind "What leaves this phone?". Keep it in step with the code: a new SDK, Firestore field,
 * or Drive scope means a new/updated entry here (and in firestore.rules / the sync mappers).
 */
object PrivacyDisclosure {
    val items: List<DisclosureItem> = listOf(
        DisclosureItem(R.string.privacy_local_title, R.string.privacy_local_body, R.string.privacy_local_when),
        DisclosureItem(R.string.privacy_auth_title, R.string.privacy_auth_body, R.string.privacy_auth_when),
        DisclosureItem(
            R.string.privacy_firestore_title,
            R.string.privacy_firestore_body,
            R.string.privacy_firestore_when,
        ),
        DisclosureItem(R.string.privacy_drive_title, R.string.privacy_drive_body, R.string.privacy_drive_when),
        DisclosureItem(R.string.privacy_crash_title, R.string.privacy_crash_body, R.string.privacy_crash_when),
        DisclosureItem(R.string.privacy_scan_title, R.string.privacy_scan_body, R.string.privacy_scan_when),
    )
}
