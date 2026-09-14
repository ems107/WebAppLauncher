package es.edgarms.weblauncher.web

import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import es.edgarms.weblauncher.R
import es.edgarms.weblauncher.net.Attempt
import es.edgarms.weblauncher.net.FailureKind
import es.edgarms.weblauncher.net.ProbeResult

/**
 * The page itself, once there is something to show, with whatever is going on
 * laid over it: looking for a server, or explaining why none answered.
 *
 * @param pageView the WebView, inside its pull-to-refresh.
 */
@Composable
fun PageScreen(
    state: PageState,
    pageView: View,
    onRetry: () -> Unit,
    onClose: () -> Unit,
) {
    var pageShown by remember { mutableStateOf(false) }
    LaunchedEffect(state) {
        if (state is PageState.Ready) pageShown = true
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        if (pageShown) {
            AndroidView(
                factory = {
                    (pageView.parent as? ViewGroup)?.removeView(pageView)
                    pageView
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
        when (state) {
            PageState.Loading -> Overlay {}
            is PageState.Probing -> Overlay { Probing(state.page.name) }
            is PageState.Unreachable -> Overlay { Unreachable(state, onRetry, onClose) }
            PageState.Missing -> Overlay { Missing(onClose) }
            is PageState.Ready -> Unit
        }
    }
}

@Composable
private fun Overlay(content: @Composable () -> Unit) {
    Surface(Modifier.fillMaxSize()) { content() }
}

@Composable
private fun Probing(name: String) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(24.dp))
        Text(stringResource(R.string.probing, name), style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun Unreachable(state: PageState.Unreachable, onRetry: () -> Unit, onClose: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(stringResource(R.string.unreachable_title, state.page.name), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.unreachable_help),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        state.attempts.forEach { AttemptRow(it) }
        Spacer(Modifier.height(24.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)) {
            OutlinedButton(onClick = onClose) { Text(stringResource(R.string.close)) }
            Button(onClick = onRetry) { Text(stringResource(R.string.retry)) }
        }
    }
}

@Composable
private fun AttemptRow(attempt: Attempt) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(attempt.url, style = MaterialTheme.typography.bodyLarge, fontFamily = FontFamily.Monospace)
        Text(
            describe(attempt.result),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun Missing(onClose: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.page_missing), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        Button(onClick = onClose) { Text(stringResource(R.string.close)) }
    }
}

@Composable
private fun describe(result: ProbeResult): String = when (result) {
    is ProbeResult.Alive -> stringResource(R.string.probe_alive, result.code)
    is ProbeResult.Failed -> when (result.kind) {
        FailureKind.TIMEOUT -> stringResource(R.string.failure_timeout)
        FailureKind.REFUSED -> stringResource(R.string.failure_refused)
        FailureKind.UNREACHABLE -> stringResource(R.string.failure_unreachable)
        FailureKind.UNKNOWN_HOST -> stringResource(R.string.failure_unknown_host)
        FailureKind.OTHER -> result.detail ?: stringResource(R.string.failure_other)
    }
}
