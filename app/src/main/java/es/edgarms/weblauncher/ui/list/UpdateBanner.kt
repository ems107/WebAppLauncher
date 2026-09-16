package es.edgarms.weblauncher.ui.list

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import es.edgarms.weblauncher.R
import es.edgarms.weblauncher.update.CheckOutcome
import es.edgarms.weblauncher.update.FeedFailure
import es.edgarms.weblauncher.update.InstallFailure
import es.edgarms.weblauncher.update.InstallProgress
import es.edgarms.weblauncher.update.UpdateState

/** What the list needs from the updater. Absent in builds that do not update themselves. */
class UpdateControls(
    val state: UpdateState,
    val runningVersion: String,
    val onCheck: suspend () -> CheckOutcome,
    val canInstall: () -> Boolean,
    val onInstall: () -> Unit,
)

/**
 * Stays at the top of the list for as long as a newer version is published:
 * nothing to dismiss, nothing that interrupts. Tapping it shows the notes.
 */
@Composable
fun UpdateBanner(controls: UpdateControls, modifier: Modifier = Modifier) {
    val update = controls.state.available ?: return
    val install = controls.state.install
    val context = LocalContext.current
    var showNotes by rememberSaveable { mutableStateOf(false) }
    var askPermission by rememberSaveable { mutableStateOf(false) }
    var awaitingPermission by rememberSaveable { mutableStateOf(false) }

    fun startInstall() {
        if (controls.canInstall()) controls.onInstall() else askPermission = true
    }

    // Back from the system settings: carry on if the permission was given.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (awaitingPermission) {
            awaitingPermission = false
            if (controls.canInstall()) controls.onInstall()
        }
    }

    val downloading = install is InstallProgress.Downloading
    Card(
        onClick = { if (!downloading) showNotes = true },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.SystemUpdate, contentDescription = null)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.update_available, update.version), style = MaterialTheme.typography.titleMedium)
                when (install) {
                    InstallProgress.Idle -> Text(stringResource(R.string.update_see_notes), style = MaterialTheme.typography.bodyMedium)
                    is InstallProgress.Downloading -> {
                        Text(stringResource(R.string.update_downloading), style = MaterialTheme.typography.bodyMedium)
                        LinearProgressIndicator(
                            progress = { install.fraction },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp, end = 8.dp),
                        )
                    }
                    InstallProgress.Confirming -> Text(stringResource(R.string.update_confirming), style = MaterialTheme.typography.bodyMedium)
                    is InstallProgress.Failed -> Text(
                        describe(context, install),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            when (install) {
                InstallProgress.Idle -> TextButton(onClick = { showNotes = true }) { Text(stringResource(R.string.update_action)) }
                is InstallProgress.Failed -> TextButton(onClick = ::startInstall) { Text(stringResource(R.string.retry)) }
                else -> Unit
            }
        }
    }

    if (showNotes) {
        AlertDialog(
            onDismissRequest = { showNotes = false },
            title = { Text(stringResource(R.string.update_notes_title, update.version)) },
            text = {
                Text(
                    update.notes.ifBlank { stringResource(R.string.update_no_notes) },
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                )
            },
            confirmButton = {
                TextButton(onClick = { showNotes = false; startInstall() }) { Text(stringResource(R.string.update_install)) }
            },
            dismissButton = {
                TextButton(onClick = { showNotes = false }) { Text(stringResource(R.string.update_later)) }
            },
        )
    }

    if (askPermission) {
        AlertDialog(
            onDismissRequest = { askPermission = false },
            title = { Text(stringResource(R.string.update_permission_title)) },
            text = { Text(stringResource(R.string.update_permission_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        askPermission = false
                        awaitingPermission = true
                        context.startActivity(
                            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")),
                        )
                    },
                ) { Text(stringResource(R.string.update_permission_open)) }
            },
            dismissButton = {
                TextButton(onClick = { askPermission = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

/** The snackbar after a check the user asked for. */
fun describe(context: Context, outcome: CheckOutcome, runningVersion: String): String = when (outcome) {
    CheckOutcome.UpToDate -> context.getString(R.string.update_up_to_date, runningVersion)
    is CheckOutcome.Found -> context.getString(R.string.update_available, outcome.update.version)
    is CheckOutcome.Failed -> when (outcome.reason) {
        FeedFailure.OFFLINE -> context.getString(R.string.update_offline)
        FeedFailure.RATE_LIMITED -> context.getString(R.string.update_rate_limited)
        FeedFailure.SERVER -> context.getString(R.string.update_server_error)
    }
}

private fun describe(context: Context, failed: InstallProgress.Failed): String = when (failed.reason) {
    InstallFailure.DOWNLOAD -> context.getString(R.string.update_failed_download)
    InstallFailure.NOT_THIS_UPDATE -> context.getString(R.string.update_failed_not_this)
    InstallFailure.SIGNATURE -> context.getString(R.string.update_failed_signature)
    InstallFailure.CANCELLED -> context.getString(R.string.update_failed_cancelled)
    InstallFailure.OTHER -> context.getString(R.string.update_failed_other, failed.detail.orEmpty())
}
