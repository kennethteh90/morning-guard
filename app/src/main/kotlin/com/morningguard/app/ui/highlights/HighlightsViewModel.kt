package com.morningguard.app.ui.highlights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.morningguard.app.sync.UserDataSyncService
import com.morningguard.data.bible.BibleHighlightRepository
import com.morningguard.domain.bible.BibleHighlight
import com.morningguard.domain.bible.BibleTranslations
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HighlightsUiState(
    val query: String = "",
    /** null = all colors */
    val colorFilter: String? = null,
    val highlights: List<BibleHighlight> = emptyList(),
)

@HiltViewModel
class HighlightsViewModel @Inject constructor(
    private val bibleHighlightRepository: BibleHighlightRepository,
    private val userDataSyncService: UserDataSyncService,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val colorFilter = MutableStateFlow<String?>(null)

    val uiState: StateFlow<HighlightsUiState> = combine(
        bibleHighlightRepository.highlights,
        query,
        colorFilter,
    ) { all, q, color ->
        val trimmed = q.trim()
        var filtered = all
        if (color != null) {
            filtered = filtered.filter { it.colorKey == color }
        }
        if (trimmed.isNotEmpty()) {
            filtered = filtered.filter { highlight ->
                highlight.excerpt.contains(trimmed, ignoreCase = true) ||
                    highlight.note.contains(trimmed, ignoreCase = true) ||
                    highlight.label.contains(trimmed, ignoreCase = true) ||
                    highlight.book.contains(trimmed, ignoreCase = true) ||
                    BibleTranslations.chipLabel(highlight.translationId)
                        .contains(trimmed, ignoreCase = true)
            }
        }
        HighlightsUiState(query = q, colorFilter = color, highlights = filtered)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HighlightsUiState())

    fun setQuery(value: String) {
        query.value = value
    }

    fun setColorFilter(colorKey: String?) {
        colorFilter.value = colorKey
    }

    fun deleteHighlight(highlight: BibleHighlight) {
        viewModelScope.launch {
            userDataSyncService.deleteHighlight(highlight)
                .onFailure { /* logged in UserDataSyncService */ }
        }
    }
}
