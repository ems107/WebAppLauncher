package es.edgarms.weblauncher.web

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import es.edgarms.weblauncher.R
import es.edgarms.weblauncher.model.Page
import es.edgarms.weblauncher.ui.PageIcon
import kotlin.math.roundToInt

/**
 * How an open page is being drawn, and whether its header is showing. None of
 * it is saved: every time a page opens it starts hidden, at 100 % and in its own
 * layout. It outlives a rotation only because the activity does.
 */
class PageChrome {
    var desktop by mutableStateOf(false)
    var zoom by mutableIntStateOf(Viewport.ZOOM_DEFAULT)
    var barHidden by mutableStateOf(true)

    /** How far the page has loaded, 0..100. */
    var progress by mutableIntStateOf(100)

    /** Where the hidden header's tab was left, in pixels from the centre. */
    var tabOffset by mutableFloatStateOf(0f)
}

/**
 * The one thing the launcher adds above a page, and the only way to reload it.
 *
 * There is no back button: the phone already has one, and it walks the page's
 * history first. What is here instead is which page this is, how wide to lay it
 * out, how big, load it again, and get out of the way.
 */
@Composable
fun PageBar(page: Page, chrome: PageChrome, onReload: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shadowElevation = 3.dp) {
        Column {
            // Inside the Surface, as Material3 does in its own top bar: the padding
            // moves the buttons out from under the clock, and the Surface still
            // paints the strip they left behind.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(
                        WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                    )
                    .height(52.dp)
                    .padding(start = 12.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                PageIcon(page, size = 28.dp)
                Text(
                    page.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                )
                BarButton(
                    icon = Icons.Filled.DesktopWindows,
                    description = stringResource(if (chrome.desktop) R.string.desktop_on else R.string.desktop_off),
                    // Shown rather than left to be inferred from the page: on a wide
                    // page at 100 % both layouts look plausible.
                    active = chrome.desktop,
                    onClick = { chrome.desktop = !chrome.desktop },
                )
                ZoomPill(chrome.zoom) { chrome.zoom = it }
                BarButton(Icons.Filled.Refresh, stringResource(R.string.reload), onClick = onReload)
                BarButton(Icons.Filled.KeyboardArrowUp, stringResource(R.string.hide_bar), onClick = { chrome.barHidden = true })
            }

            // Only while something is actually loading.
            if (chrome.progress in 1..99) {
                LinearProgressIndicator(
                    progress = { chrome.progress / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                        .height(2.dp),
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    drawStopIndicator = {},
                    gapSize = 0.dp,
                )
            }
        }
    }
}

/**
 * What is left of the header while it is hidden: a tab hanging from the top.
 * A tap brings the header back; a sideways drag moves the tab off whatever the
 * page has under it, and it stays where it is let go.
 */
@Composable
fun BoxScope.ShowBarTab(chrome: PageChrome, maxOffset: Float) {
    val tabHalf = with(LocalDensity.current) { TAB_WIDTH.toPx() / 2 }
    val limit = (maxOffset - tabHalf).coerceAtLeast(0f)
    Surface(
        shape = RoundedCornerShape(bottomStart = 14.dp, bottomEnd = 14.dp),
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.85f),
        shadowElevation = 2.dp,
        modifier = Modifier
            .align(Alignment.TopCenter)
            .offset { IntOffset(chrome.tabOffset.coerceIn(-limit, limit).roundToInt(), 0) }
            .pointerInput(limit) {
                detectHorizontalDragGestures { change, dragAmount ->
                    change.consume()
                    chrome.tabOffset = (chrome.tabOffset.coerceIn(-limit, limit) + dragAmount).coerceIn(-limit, limit)
                }
            }
            .clickable { chrome.barHidden = false },
    ) {
        Box(Modifier.size(TAB_WIDTH, 26.dp), contentAlignment = Alignment.Center) {
            Icon(
                Icons.Filled.KeyboardArrowDown,
                contentDescription = stringResource(R.string.show_bar),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

private val TAB_WIDTH = 64.dp

@Composable
private fun BarButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    active: Boolean = false,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(40.dp),
        colors = if (active) {
            IconButtonDefaults.iconButtonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        } else {
            IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
        },
    ) {
        Icon(icon, contentDescription = description, modifier = Modifier.size(20.dp))
    }
}

/**
 * Zoom out, the number, zoom in -- one control, because they are one idea. The
 * number is the LAYOUT zoom only; a pinch does not move it (see [Viewport]).
 */
@Composable
private fun ZoomPill(zoom: Int, onZoom: (Int) -> Unit) {
    Surface(
        shape = RoundedCornerShape(percent = 50),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ZoomStep("−", stringResource(R.string.zoom_out), enabled = zoom > Viewport.ZOOM_MIN) {
                onZoom((zoom - Viewport.ZOOM_STEP).coerceAtLeast(Viewport.ZOOM_MIN))
            }
            Text(
                "$zoom%",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(38.dp),
            )
            ZoomStep("+", stringResource(R.string.zoom_in), enabled = zoom < Viewport.ZOOM_MAX) {
                onZoom((zoom + Viewport.ZOOM_STEP).coerceAtMost(Viewport.ZOOM_MAX))
            }
        }
    }
}

/** The glyph is drawn and the label is spoken: "minus" read aloud says nothing about what it takes away. */
@Composable
private fun ZoomStep(glyph: String, description: String, enabled: Boolean, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(36.dp).semantics { contentDescription = description },
    ) {
        Text(
            glyph,
            style = MaterialTheme.typography.titleMedium,
            color = if (enabled) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
            },
        )
    }
}
