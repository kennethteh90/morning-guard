package com.morningguard.app.sync

import com.morningguard.domain.bible.BibleHighlight
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BibleHighlightPushRowsTest {

    @Test
    fun `insert payload JSON omits server id column`() {
        val payload = BibleHighlightPushRows.fromWireRow(
            userId = "user-1",
            highlight = sampleHighlight(remoteId = 0, localId = 42),
            noteForRow = "",
            createdAtIso = "2026-11-01T00:00:00Z",
            remoteServerId = null,
        ) as BibleHighlightPushPayload.InsertNew

        val json = BibleHighlightPushRows.encodeInsertRow(payload.row)
        assertFalse(json.contains("\"id\""))
        assertTrue(json.contains("\"client_id\":42"))
    }

    @Test
    fun `update payload JSON includes server id`() {
        val payload = BibleHighlightPushRows.fromWireRow(
            userId = "user-1",
            highlight = sampleHighlight(remoteId = 99, localId = 42),
            noteForRow = "",
            createdAtIso = "2026-11-01T00:00:00Z",
            remoteServerId = 99,
        ) as BibleHighlightPushPayload.UpdateExisting

        val json = BibleHighlightPushRows.encodeUpdateRow(payload.row)
        assertTrue(json.contains("\"id\":99"))
    }

    @Test
    fun `highlights without local id are skipped`() {
        val payload = BibleHighlightPushRows.fromWireRow(
            userId = "user-1",
            highlight = sampleHighlight(remoteId = 0, localId = 0),
            noteForRow = "",
            createdAtIso = "2026-11-01T00:00:00Z",
            remoteServerId = null,
        )
        assertTrue(payload == null)
    }

    private fun sampleHighlight(remoteId: Long, localId: Long) = BibleHighlight(
        id = localId,
        remoteId = remoteId,
        translationId = "kjv",
        book = "John",
        chapter = 3,
        label = "John 3:16",
        excerpt = "For God so loved the world",
        verse = 16,
        createdAtEpochMs = 1L,
    )
}
