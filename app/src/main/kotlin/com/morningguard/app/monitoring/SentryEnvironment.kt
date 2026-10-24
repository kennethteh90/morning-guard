package com.morningguard.app.monitoring

import android.content.Context
import android.provider.Settings

object SentryEnvironment {
    /** Firebase Test Lab / Play pre-launch devices set this system property. */
    fun isFirebaseTestLab(context: Context): Boolean =
        Settings.System.getString(context.contentResolver, "firebase.test.lab") == "true"
}
