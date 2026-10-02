package com.paperly.app.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paperly.app.domain.document.LibrarySort
import com.paperly.app.domain.document.LibraryType
import com.paperly.app.domain.document.LibraryViewStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Sort + type choices (persisted). Picking the active sort again flips its direction; Name starts A-Z. */
@HiltViewModel
class LibraryViewViewModel @Inject constructor(private val store: LibraryViewStore) : ViewModel() {

    fun setSort(sort: LibrarySort) {
        viewModelScope.launch {
            val current = store.view.first()
            val flip = sort == current.sort && sort != LibrarySort.DEFAULT
            val next = if (flip) {
                current.copy(ascending = !current.ascending)
            } else {
                current.copy(sort = sort, ascending = sort == LibrarySort.NAME)
            }
            store.save(next)
        }
    }

    /** Tapping the active type clears it. */
    fun setType(type: LibraryType) {
        viewModelScope.launch {
            val current = store.view.first()
            store.save(current.copy(type = type.takeIf { it != current.type }))
        }
    }
}
