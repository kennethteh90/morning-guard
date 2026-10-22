package com.morningguard.app.ui.privacy

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.morningguard.app.BuildConfig
import com.morningguard.app.R
import com.morningguard.app.ui.theme.MorningGuardTheme

class PrivacyPolicyActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MorningGuardTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val disclosure = stringResource(R.string.notification_access_disclosure)
                    val policyUrl = BuildConfig.PRIVACY_POLICY_URL.trim()
                    Column(
                        modifier = Modifier
                            .padding(24.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Text(
                            stringResource(R.string.privacy_policy_title),
                            style = MaterialTheme.typography.headlineSmall,
                        )
                        Text(disclosure, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Morning Guard may read step counts through Health Connect when you choose a step goal. " +
                                "Step data is used only on this device to verify your goal.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (policyUrl.isNotBlank()) {
                            TextButton(
                                onClick = {
                                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(policyUrl)))
                                },
                            ) {
                                Text(policyUrl)
                            }
                        }
                    }
                }
            }
        }
    }
}
