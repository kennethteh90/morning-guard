package com.morningguard.app.ui.goal

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DirectionsWalk
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.ui.text.style.TextOverflow
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.runtime.Composable
import androidx.health.connect.client.PermissionController
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.morningguard.app.platform.AppDebugFlags
import com.morningguard.app.ui.components.ConfirmRemoveHighlightDialog
import com.morningguard.app.ui.components.ScreenContentColumn
import com.morningguard.app.ui.components.ScreenScaffold
import com.morningguard.domain.bible.BibleCanon
import com.morningguard.domain.bible.BibleHighlight
import com.morningguard.domain.bible.BibleTranslations
import com.morningguard.domain.bible.BibleVerseCopyFormat
import com.morningguard.domain.bible.BibleVerseLine
import com.morningguard.domain.model.GoalType
import kotlin.math.min

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MorningGoalScreen(
    browseOnly: Boolean = false,
    onBack: () -> Unit,
    onGoalCompleted: (heldCount: Int) -> Unit,
    viewModel: MorningGoalViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val snackbarScope = rememberCoroutineScope()
    var verseHighlightBar by remember { mutableStateOf<VerseHighlightBarState?>(null) }
    var highlightPendingRemove by remember { mutableStateOf<BibleHighlight?>(null) }
    val clipboard = LocalClipboardManager.current

    LaunchedEffect(browseOnly) {
        viewModel.setBrowseOnly(browseOnly)
    }

    if (!browseOnly) {
        LaunchedEffect(viewModel) {
            viewModel.goalCompleted.collect { heldCount ->
                onGoalCompleted(heldCount)
            }
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.highlightSaved.collect {
            snackbarHostState.showSnackbar("Saved to highlights")
        }
    }

    LifecycleResumeEffect(Unit) {
        viewModel.setGoalScreenForeground(true)
        onPauseOrDispose { viewModel.setGoalScreenForeground(false) }
    }

    val elapsedSec = state.progress?.onScreenSeconds?.toLong() ?: 0L
    val healthConnectLauncher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract(),
    ) {
        viewModel.onHealthConnectPermissionResult()
    }

    LaunchedEffect(state.config.type) {
        if (state.config.type == GoalType.STEP_COUNT) {
            viewModel.refreshHealthConnectAccess()
        }
    }

    highlightPendingRemove?.let { highlight ->
        ConfirmRemoveHighlightDialog(
            highlight = highlight,
            onConfirm = {
                viewModel.removeBibleHighlight(highlight)
                highlightPendingRemove = null
                verseHighlightBar = null
            },
            onDismiss = { highlightPendingRemove = null },
        )
    }

    val showBible = browseOnly || state.config.type == GoalType.BIBLE_CHAPTER
    val scrollState = rememberScrollState()
    val fadeRangePx = with(LocalDensity.current) { 96.dp.toPx() }
    val bottomNavTitleAlpha by remember(scrollState, fadeRangePx) {
        derivedStateOf { (scrollState.value / fadeRangePx).coerceIn(0f, 1f) }
    }
    val topSelectorAlpha by remember(scrollState, fadeRangePx) {
        derivedStateOf { (1f - scrollState.value / fadeRangePx).coerceIn(0f, 1f) }
    }
    var bookSheetOpen by remember { mutableStateOf(false) }
    var translationSheetOpen by remember { mutableStateOf(false) }

    if (bookSheetOpen) {
        BibleBookPickerSheet(
            selectedBook = state.config.bibleBook,
            onSelectBook = viewModel::setBibleBook,
            onDismiss = { bookSheetOpen = false },
        )
    }
    if (translationSheetOpen) {
        BibleTranslationPickerSheet(
            selectedTranslationId = state.config.bibleTranslation,
            onSelect = viewModel::setBibleTranslation,
            onDismiss = { translationSheetOpen = false },
        )
    }

    ScreenScaffold(
        title = if (browseOnly) "Bible" else "Morning goal",
        onBack = onBack,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            when {
                verseHighlightBar != null -> {
                    val barState = verseHighlightBar!!
                VerseHighlightBottomBar(
                    state = barState,
                    onColorSelected = { key ->
                        viewModel.setLastHighlightColorKey(key)
                        verseHighlightBar = barState.copy(colorKey = key)
                    },
                    onNoteChange = { note ->
                        verseHighlightBar = verseHighlightBar?.copy(note = note)
                    },
                    onSave = {
                        val current = verseHighlightBar ?: return@VerseHighlightBottomBar
                        val selected = current.verseSnippets.filter { it.number in current.selectedVerses }
                        viewModel.saveBibleHighlight(
                            BibleVerseHighlightRequest(
                                verses = selected,
                                colorKey = current.colorKey,
                                note = current.note,
                            ),
                        )
                        verseHighlightBar = null
                    },
                    onDelete = {
                        barState.existingHighlight?.let { highlightPendingRemove = it }
                    },
                    onCopy = {
                        val selected = barState.verseSnippets.filter { it.number in barState.selectedVerses }
                        val text = BibleVerseCopyFormat.format(
                            book = state.config.bibleBook,
                            chapter = state.config.bibleChapter,
                            translationId = state.config.bibleTranslation,
                            verses = selected.map { BibleVerseLine(it.number, it.text) },
                        )
                        clipboard.setText(AnnotatedString(text))
                        snackbarScope.launch { snackbarHostState.showSnackbar("Verses copied") }
                    },
                    onDismissSelection = { verseHighlightBar = null },
                )
                }
                showBible -> {
                    BibleChapterNavBar(
                        book = state.config.bibleBook,
                        chapter = state.config.bibleChapter,
                        titleAlpha = bottomNavTitleAlpha,
                        onOpenBookChapter = { bookSheetOpen = true },
                        onPrevious = { viewModel.stepBibleChapter(-1) },
                        onNext = { viewModel.stepBibleChapter(1) },
                    )
                }
            }
        },
    ) { padding ->
        ScreenContentColumn(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(scrollState),
        ) {
            if (!browseOnly) {
                GoalHeaderCard(type = state.config.type)
            }

            when {
                showBible -> BibleGoalBody(
                    browseOnly = browseOnly,
                    book = state.config.bibleBook,
                    chapter = state.config.bibleChapter,
                    translationId = state.config.bibleTranslation,
                    loading = state.loadingBible,
                    passage = state.biblePassage,
                    chapterHighlights = state.bibleChapterHighlights,
                    elapsedSec = elapsedSec,
                    requiredSec = 60,
                    topSelectorAlpha = topSelectorAlpha,
                    onOpenBookPicker = { bookSheetOpen = true },
                    onOpenTranslationPicker = { translationSheetOpen = true },
                    highlightBarState = verseHighlightBar,
                    onHighlightBarStateChange = { verseHighlightBar = it },
                    defaultHighlightColorKey = state.lastHighlightColorKey,
                )
                state.config.type == GoalType.TIMED_FOCUS -> TimedFocusBody(
                    elapsedSec = elapsedSec,
                    requiredMin = state.config.focusMinutes,
                )
                state.config.type == GoalType.STEP_COUNT -> StepCountBody(
                    stepsAccumulated = state.progress?.stepsAccumulated ?: 0,
                    target = state.config.stepTarget,
                    trackingUnavailable = !state.stepHardwareAvailable,
                    healthConnectNeedsPermission = state.healthConnectNeedsPermission,
                    onRequestHealthConnect = {
                        healthConnectLauncher.launch(viewModel.healthConnectReadPermissions())
                    },
                    debug = if (AppDebugFlags.showDebugPanels) state.stepDebug else null,
                )
            }

            state.error?.let {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Text(it, modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }

            if (!browseOnly) {
                Button(
                    onClick = { viewModel.completeGoal() },
                    enabled = state.canComplete,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.CheckCircle, contentDescription = null)
                        Text("Complete morning goal")
                    }
                }

                if (!state.canComplete) {
                    Text(
                        completionHint(
                            state.config.type,
                            elapsedSec,
                            state.config,
                            state.progress?.stepsAccumulated ?: 0,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                Text("Back to home")
            }
        }
    }
}

@Composable
private fun GoalHeaderCard(type: GoalType) {
    val (icon, title, subtitle) = when (type) {
        GoalType.BIBLE_CHAPTER -> Triple(Icons.Outlined.AutoStories, "Read your chapter", "Stay on this screen while you read.")
        GoalType.TIMED_FOCUS -> Triple(Icons.Outlined.Schedule, "Timed focus", "Keep Morning Guard open until the timer finishes.")
        GoalType.STEP_COUNT -> Triple(
            Icons.Outlined.DirectionsWalk,
            "Walk it out (Experimental)",
            "Step counts can lag on some Samsung devices; Health Connect may update slowly after you walk.",
        )
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(title, style = MaterialTheme.typography.headlineMedium)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BibleGoalBody(
    browseOnly: Boolean,
    book: String,
    chapter: Int,
    translationId: String,
    loading: Boolean,
    passage: List<com.morningguard.domain.bible.BiblePassageBlock>,
    chapterHighlights: List<com.morningguard.domain.bible.BibleHighlight>,
    elapsedSec: Long,
    requiredSec: Int,
    topSelectorAlpha: Float,
    onOpenBookPicker: () -> Unit,
    onOpenTranslationPicker: () -> Unit,
    highlightBarState: VerseHighlightBarState?,
    onHighlightBarStateChange: (VerseHighlightBarState?) -> Unit,
    defaultHighlightColorKey: String,
) {
    val translationLabel = BibleTranslations.chipLabel(translationId)

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(topSelectorAlpha),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AssistChip(
                    onClick = onOpenBookPicker,
                    enabled = topSelectorAlpha > 0.2f,
                    label = {
                        Text(
                            "$book $chapter",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    modifier = Modifier.weight(1f),
                )
                AssistChip(
                    onClick = onOpenTranslationPicker,
                    enabled = topSelectorAlpha > 0.2f,
                    label = {
                        Text(
                            translationLabel,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                )
            }
            if (!browseOnly) {
                Text(
                    "Chapter $chapter of ${BibleCanon.chapterCount(book)} · next chapter after you complete today",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text("Reading progress", style = MaterialTheme.typography.titleLarge)
                LinearProgressIndicator(
                    progress = { min(1f, elapsedSec.toFloat() / requiredSec) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("$elapsedSec / $requiredSec seconds on screen")
            }
            Text(
                "Tap verses to highlight, or tap a highlighted verse to remove. The bottom bar has Save or Delete.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (loading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            } else if (passage.isEmpty()) {
                Text(
                    "Chapter text is not loaded yet. Check your connection — it should load automatically.",
                    style = MaterialTheme.typography.bodyLarge,
                )
            } else {
                BiblePassageText(
                    blocks = passage,
                    savedHighlights = chapterHighlights,
                    highlightBarState = highlightBarState,
                    onHighlightBarStateChange = onHighlightBarStateChange,
                    defaultHighlightColorKey = defaultHighlightColorKey,
                )
            }
        }
    }
}

@Composable
private fun BibleChapterNavBar(
    book: String,
    chapter: Int,
    titleAlpha: Float,
    onOpenBookChapter: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
    ) {
        HorizontalDivider()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ChapterNavCaret(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = "Previous chapter",
                onClick = onPrevious,
            )
            Text(
                "$book $chapter",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .alpha(titleAlpha)
                    .then(
                        if (titleAlpha > 0.2f) {
                            Modifier.clickable(onClick = onOpenBookChapter, role = Role.Button)
                        } else {
                            Modifier
                        },
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
            ChapterNavCaret(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Next chapter",
                onClick = onNext,
            )
        }
    }
}

@Composable
private fun ChapterNavCaret(
    imageVector: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Icon(
        imageVector = imageVector,
        contentDescription = contentDescription,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .clickable(onClick = onClick, role = Role.Button)
            .padding(horizontal = 4.dp, vertical = 2.dp)
            .size(22.dp),
    )
}

@Composable
private fun TimedFocusBody(elapsedSec: Long, requiredMin: Int) {
    val requiredSec = requiredMin * 60L
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Focus timer", style = MaterialTheme.typography.titleLarge)
            LinearProgressIndicator(
                progress = { min(1f, elapsedSec.toFloat() / requiredSec) },
                modifier = Modifier.fillMaxWidth(),
            )
            Text("${elapsedSec / 60}m ${elapsedSec % 60}s / ${requiredMin}m")
        }
    }
}

@Composable
private fun StepCountBody(
    stepsAccumulated: Int,
    target: Int,
    trackingUnavailable: Boolean,
    healthConnectNeedsPermission: Boolean,
    onRequestHealthConnect: () -> Unit,
    debug: StepGoalDebugInfo? = null,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Steps", style = MaterialTheme.typography.titleLarge)
            if (trackingUnavailable && stepsAccumulated == 0) {
                Text("Step sensor unavailable on this device.")
            } else {
                LinearProgressIndicator(
                    progress = { min(1f, stepsAccumulated.toFloat() / target) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "$stepsAccumulated / $target steps since you started today",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    "On Samsung, allow Health Connect below so steps sync while you walk.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (healthConnectNeedsPermission) {
                OutlinedButton(onClick = onRequestHealthConnect, modifier = Modifier.fillMaxWidth()) {
                    Text("Allow Health Connect steps")
                }
            }
            if (debug != null) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        debug.toDisplayLines().forEach { line ->
                            Text(
                                line,
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun completionHint(
    type: GoalType,
    elapsedSec: Long,
    config: com.morningguard.domain.model.MorningGoalConfig,
    stepsAccumulated: Int,
): String {
    return when (type) {
        GoalType.BIBLE_CHAPTER -> "Keep reading — ${(60 - elapsedSec).coerceAtLeast(0)}s remaining."
        GoalType.TIMED_FOCUS -> {
            val need = config.focusMinutes * 60 - elapsedSec
            "Stay on this screen — ${(need / 60).coerceAtLeast(0)}m ${(need % 60).coerceAtLeast(0)}s left."
        }
        GoalType.STEP_COUNT -> {
            "${(config.stepTarget - stepsAccumulated).coerceAtLeast(0)} more steps while on this screen."
        }
    }
}
