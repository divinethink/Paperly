package com.paperly.app.feature.reader

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentFactory
import com.paperly.app.R
import kotlinx.coroutines.delay
import org.readium.r2.navigator.epub.EpubNavigatorFragment

/**
 * Installed on MainActivity BEFORE super.onCreate so a restored EpubNavigatorFragment (rotation/process restore)
 * never crashes: with no delegate yet it becomes an empty placeholder that the screen replaces.
 */
private const val NAVIGATOR_POLL_MS = 100L

object EpubFragmentHost : FragmentFactory() {
    @Volatile
    var delegate: FragmentFactory? = null

    override fun instantiate(classLoader: ClassLoader, className: String): Fragment {
        if (className != EpubNavigatorFragment::class.java.name) return super.instantiate(classLoader, className)
        return delegate?.instantiate(classLoader, className) ?: Fragment()
    }

    /** The live navigator, or null before it is attached. */
    fun navigator(activity: FragmentActivity): EpubNavigatorFragment? =
        activity.supportFragmentManager.findFragmentById(R.id.epub_fragment_container) as? EpubNavigatorFragment

    /** Waits for the fragment to attach (it appears right after first layout). */
    suspend fun awaitNavigator(activity: FragmentActivity): EpubNavigatorFragment {
        var navigator = navigator(activity)
        while (navigator == null) {
            delay(NAVIGATOR_POLL_MS)
            navigator = navigator(activity)
        }
        return navigator
    }
}
