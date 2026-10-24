package com.morningguard.data.security

/**
 * Decides what `bible_highlights.note` value to send on upsert so locked devices never wipe ciphertext.
 */
object HighlightSyncNotePolicy {

    sealed class NotePushValue {
        data class EncryptLocal(val plainNote: String) : NotePushValue()
        data class PreserveRemote(val remoteNote: String) : NotePushValue()
        data object OmitNote : NotePushValue()
    }

    fun resolve(
        localPlainNote: String,
        remoteNote: String?,
        dekUnlocked: Boolean,
    ): NotePushValue {
        val trimmedLocal = localPlainNote.trim()
        val remote = remoteNote?.trim().orEmpty()

        if (!dekUnlocked) {
            if (NoteContentCipher.isCiphertext(remote)) {
                return NotePushValue.PreserveRemote(remote)
            }
            if (trimmedLocal.isNotEmpty()) {
                return NotePushValue.OmitNote
            }
            return NotePushValue.OmitNote
        }

        if (trimmedLocal.isEmpty()) {
            return NotePushValue.EncryptLocal("")
        }
        return NotePushValue.EncryptLocal(trimmedLocal)
    }

    fun toWireValue(decision: NotePushValue, encrypt: (String) -> String): String? = when (decision) {
        is NotePushValue.EncryptLocal -> encrypt(decision.plainNote)
        is NotePushValue.PreserveRemote -> decision.remoteNote
        NotePushValue.OmitNote -> null
    }
}
