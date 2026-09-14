package es.edgarms.weblauncher.ui.list

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import es.edgarms.weblauncher.R
import es.edgarms.weblauncher.model.Config
import es.edgarms.weblauncher.model.ImportResult
import es.edgarms.weblauncher.model.Page
import es.edgarms.weblauncher.ui.PageIcon
import kotlinx.coroutines.launch

/**
 * @param pages null while the configuration is still being read.
 * @param onAddToHome returns false when the launcher cannot pin shortcuts.
 * @param onExport writes the configuration to the file chosen; false if it failed.
 * @param onReadImport checks the file chosen, without changing anything yet.
 * @param onApplyImport replaces every page with a checked import.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageListScreen(
    pages: List<Page>?,
    onAdd: () -> Unit,
    onOpen: (Page) -> Unit,
    onEdit: (Page) -> Unit,
    onAddToHome: (Page) -> Boolean,
    onExport: suspend (Uri) -> Boolean,
    onReadImport: suspend (Uri) -> ImportResult,
    onApplyImport: (Config) -> Unit,
) {
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var pendingImport by remember { mutableStateOf<Config?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            scope.launch {
                val message = if (onExport(uri)) R.string.export_done else R.string.export_failed
                snackbar.showSnackbar(context.getString(message))
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                when (val result = onReadImport(uri)) {
                    is ImportResult.Valid -> pendingImport = result.config
                    is ImportResult.Invalid -> snackbar.showSnackbar(describe(context, result))
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    ListMenu(
                        onExport = { exportLauncher.launch(EXPORT_FILE_NAME) },
                        // Any type: phones disagree on what a .json file is, and the content is checked anyway.
                        onImport = { importLauncher.launch(arrayOf("*/*")) },
                    )
                },
            )
        },
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
                                    if (!onAddToHome(page)) {
                                        scope.launch { snackbar.showSnackbar(context.getString(R.string.pin_unsupported)) }
                                    }
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

    pendingImport?.let { imported ->
        val current = pages.orEmpty().size
        val incoming = imported.pages.size
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text(stringResource(R.string.import_title)) },
            text = {
                val replaces = if (current == 0) {
                    context.getString(R.string.import_replaces_nothing)
                } else {
                    context.resources.getQuantityString(R.plurals.import_replaces_pages, current, current)
                }
                val fileHas = context.resources.getQuantityString(R.plurals.import_file_pages, incoming, incoming)
                Text("$fileHas $replaces ${context.getString(R.string.import_icons_note)}")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingImport = null
                        onApplyImport(imported)
                        scope.launch {
                            snackbar.showSnackbar(
                                context.resources.getQuantityString(R.plurals.import_done, incoming, incoming),
                            )
                        }
                    },
                ) { Text(stringResource(R.string.import_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingImport = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

private const val EXPORT_FILE_NAME = "web-launcher.json"

private fun describe(context: Context, result: ImportResult.Invalid): String = when (result.reason) {
    ImportResult.Reason.UNREADABLE -> context.getString(R.string.import_unreadable)
    ImportResult.Reason.NOT_A_CONFIG -> context.getString(R.string.import_not_config)
    ImportResult.Reason.NEWER_VERSION -> context.getString(R.string.import_newer)
    ImportResult.Reason.BAD_PAGE -> context.getString(R.string.import_bad_page, result.detail.orEmpty())
}

@Composable
private fun ListMenu(onExport: () -> Unit, onImport: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.more_options))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.export_config)) },
                onClick = { expanded = false; onExport() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.import_config)) },
                onClick = { expanded = false; onImport() },
            )
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
