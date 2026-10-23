package com.morningguard.app.ui.goal

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.morningguard.domain.bible.BibleCanon
import com.morningguard.domain.bible.BibleTranslationOption
import com.morningguard.domain.bible.BibleTranslations

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BibleTranslationPickerSheet(
    selectedTranslationId: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(bottom = 32.dp)) {
            Text(
                "Bible translation",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            SectionLabel("Limited use")
            TranslationList(
                options = BibleTranslations.apiBibleOptions(),
                selectedTranslationId = selectedTranslationId,
                onSelect = onSelect,
                onDismiss = onDismiss,
            )
            SectionLabel("Open access")
            TranslationList(
                options = BibleTranslations.openBibleOptions(),
                selectedTranslationId = selectedTranslationId,
                onSelect = onSelect,
                onDismiss = onDismiss,
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 8.dp),
    )
}

@Composable
private fun TranslationList(
    options: List<BibleTranslationOption>,
    selectedTranslationId: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    options.forEach { option ->
        val selected = option.id.equals(selectedTranslationId, ignoreCase = true)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    onSelect(option.id)
                    onDismiss()
                }
                .padding(horizontal = 24.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                option.chipLabel,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(end = 4.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(option.fullName, style = MaterialTheme.typography.bodyLarge)
            }
            if (selected) {
                Icon(Icons.Outlined.Check, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary)
            }
        }
        HorizontalDivider(modifier = Modifier.padding(horizontal = 24.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BibleBookPickerSheet(
    selectedBook: String,
    onSelectBook: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            "Book",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        LazyColumn(modifier = Modifier.padding(bottom = 32.dp)) {
            items(BibleCanon.books, key = { it.name }) { book ->
                val selected = book.name.equals(selectedBook, ignoreCase = true)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onSelectBook(book.name)
                            onDismiss()
                        }
                        .padding(horizontal = 24.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        book.name,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                    if (selected) {
                        Icon(Icons.Outlined.Check, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary)
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(horizontal = 24.dp))
            }
        }
    }
}
