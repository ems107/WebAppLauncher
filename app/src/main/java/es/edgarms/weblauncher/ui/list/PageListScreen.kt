package es.edgarms.weblauncher.ui.list

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import es.edgarms.weblauncher.R
import es.edgarms.weblauncher.model.Page
import es.edgarms.weblauncher.ui.PageIcon
import kotlinx.coroutines.launch

/**
 * @param pages null while the configuration is still being read.
 * @param onAddToHome returns false when the launcher cannot pin shortcuts.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageListScreen(
    pages: List<Page>?,
    onAdd: () -> Unit,
    onOpen: (Page) -> Unit,
    onEdit: (Page) -> Unit,
    onAddToHome: (Page) -> Boolean,
) {
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val pinUnsupported = stringResource(R.string.pin_unsupported)

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.app_name)) }) },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            FloatingActionButton(onClick = onAdd) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.add_page))
            }
        },
    ) { padding ->
        when {
            pages == null -> Box(Modifier.fillMaxSize().padding(padding))
            pages.isEmpty() -> Box(
                Modifier.fillMaxSize().padding(padding).padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(R.string.no_pages),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                )
            }
            else -> LazyColumn(
                contentPadding = PaddingValues(
                    top = padding.calculateTopPadding(),
                    // Room for the button, so it never covers the last page.
                    bottom = padding.calculateBottomPadding() + 88.dp,
                ),
            ) {
                items(pages, key = { it.id }) { page ->
                    ListItem(
                        headlineContent = { Text(page.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = {
                            Text(page.urls.joinToString("\n"), maxLines = 3, overflow = TextOverflow.Ellipsis)
                        },
                        leadingContent = { PageIcon(page) },
                        trailingContent = {
                            PageMenu(
                                onEdit = { onEdit(page) },
                                onAddToHome = {
                                    if (!onAddToHome(page)) scope.launch { snackbar.showSnackbar(pinUnsupported) }
                                },
                            )
                        },
                        modifier = Modifier.clickable { onOpen(page) },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun PageMenu(onEdit: () -> Unit, onAddToHome: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.more_options))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.edit)) },
                leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                onClick = { expanded = false; onEdit() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.add_to_home)) },
                leadingIcon = { Icon(Icons.Filled.Home, contentDescription = null) },
                onClick = { expanded = false; onAddToHome() },
            )
        }
    }
}
