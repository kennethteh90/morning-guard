package com.morningguard.data.bible

import android.content.Context
import com.morningguard.domain.bible.BibleHighlightColors
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BibleHighlightColorStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _colorKey = MutableStateFlow(readColorKey())
    val colorKey: StateFlow<String> = _colorKey.asStateFlow()

    fun set(colorKey: String) {
        val normalized = colorKey.takeIf { it in BibleHighlightColors.keys }
            ?: BibleHighlightColors.DEFAULT_KEY
        prefs.edit().putString(KEY_COLOR, normalized).apply()
        _colorKey.value = normalized
    }

    fun current(): String = _colorKey.value

    private fun readColorKey(): String =
        prefs.getString(KEY_COLOR, BibleHighlightColors.DEFAULT_KEY)
            ?.takeIf { it in BibleHighlightColors.keys }
            ?: BibleHighlightColors.DEFAULT_KEY

    private companion object {
        const val PREFS_NAME = "bible_highlight_ui"
        const val KEY_COLOR = "last_color_key"
    }
}
