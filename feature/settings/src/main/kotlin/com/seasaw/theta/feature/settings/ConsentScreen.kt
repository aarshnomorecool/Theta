package com.seasaw.theta.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.seasaw.theta.core.data.settings.SettingsRepository
import com.seasaw.theta.core.ui.ThemePreviews
import com.seasaw.theta.core.ui.ThetaIcons
import com.seasaw.theta.core.ui.ThetaTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

internal object ConsentTestTags {
    const val CONTINUE = "consent_continue"
    const val DIAGNOSTICS = "consent_diagnostics"
}

@HiltViewModel
class ConsentViewModel @Inject constructor(private val settings: SettingsRepository) : ViewModel() {
    suspend fun accept(diagnosticsConsent: Boolean) = settings.acceptPrivacyNotice(diagnosticsConsent)
}

/** The DPDP Act notice, shown once on first launch. The app waits on this before ads, analytics or crash reports. */
@Composable
fun ConsentRoute(viewModel: ConsentViewModel = hiltViewModel()) {
    val scope = rememberCoroutineScope()
    ConsentScreen(onContinue = { diagnostics -> scope.launch { viewModel.accept(diagnostics) } })
}

/** Diagnostics default to off: the user opts in, as consent must be a clear choice rather than a default. */
@Composable
internal fun ConsentScreen(onContinue: (Boolean) -> Unit) {
    var diagnostics by remember { mutableStateOf(false) }
    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(24.dp),
        ) {
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text("θ", fontSize = 48.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Text(stringResource(R.string.consent_title), style = MaterialTheme.typography.headlineSmall)
                NoticeItem(ThetaIcons.Shield, R.string.consent_device_title, R.string.consent_device_body)
                NoticeItem(ThetaIcons.Tune, R.string.consent_ads_title, R.string.consent_ads_body)
                NoticeItem(ThetaIcons.History, R.string.consent_delete_title, R.string.consent_delete_body)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { diagnostics = !diagnostics }
                        .testTag(ConsentTestTags.DIAGNOSTICS)
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.consent_diagnostics_title),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            stringResource(R.string.consent_diagnostics_body),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Switch(checked = diagnostics, onCheckedChange = { diagnostics = it })
                }
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { onContinue(diagnostics) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(ConsentTestTags.CONTINUE),
            ) { Text(stringResource(R.string.consent_continue)) }
        }
    }
}

@Composable
private fun NoticeItem(icon: ImageVector, title: Int, body: Int) {
    Row {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(12.dp))
        Column {
            Text(stringResource(title), style = MaterialTheme.typography.titleSmall)
            Text(
                stringResource(body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@ThemePreviews
@Composable
private fun ConsentPreview() {
    ThetaTheme { ConsentScreen(onContinue = {}) }
}
