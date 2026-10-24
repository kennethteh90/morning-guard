package com.morningguard.data.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.SecureRandom

class HighlightSyncNotePolicyTest {

    @Test
    fun `locked dek preserves remote ciphertext`() {
        val dek = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val remote = NoteContentCipher.encrypt("secret", dek)
        val decision = HighlightSyncNotePolicy.resolve(
            localPlainNote = "local edit",
            remoteNote = remote,
            dekUnlocked = false,
        )
        assertTrue(decision is HighlightSyncNotePolicy.NotePushValue.PreserveRemote)
    }

    @Test
    fun `locked dek omits push when remote empty but local has note`() {
        val decision = HighlightSyncNotePolicy.resolve(
            localPlainNote = "only on device",
            remoteNote = "",
            dekUnlocked = false,
        )
        assertEquals(HighlightSyncNotePolicy.NotePushValue.OmitNote, decision)
    }

    @Test
    fun `unlocked dek encrypts local note`() {
        val decision = HighlightSyncNotePolicy.resolve(
            localPlainNote = "note",
            remoteNote = null,
            dekUnlocked = true,
        )
        assertTrue(decision is HighlightSyncNotePolicy.NotePushValue.EncryptLocal)
    }
}
