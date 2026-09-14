package es.edgarms.weblauncher.ui.edit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import es.edgarms.weblauncher.R
import es.edgarms.weblauncher.model.Page
import es.edgarms.weblauncher.model.PageUrls

/** @param page the page being edited, or null to create one. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageEditScreen(
    page: Page?,
    onSave: (Page) -> Unit,
    onDelete: (Page) -> Unit,
    onBack: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(page?.name.orEmpty()) }
    var urls by rememberSaveable { mutableStateOf(page?.urls ?: listOf("")) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    val cleanUrls = urls.map { it.trim() }.filter { it.isNotEmpty() }
    val canSave = name.isNotBlank() && cleanUrls.isNotEmpty() && cleanUrls.all(PageUrls::isValid)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (page == null) R.string.new_page else R.string.edit_page)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    if (page != null) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.delete))
                        }
                    }
                    IconButton(
                        enabled = canSave,
                        onClick = {
                            val edited = page?.copy(name = name.trim(), urls = cleanUrls)
                                ?: Page(name = name.trim(), urls = cleanUrls)
                            onSave(edited)
                        },
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.save))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.page_name)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(24.dp))
            Text(stringResource(R.string.urls_title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.urls_help),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))

            urls.forEachIndexed { index, url ->
                val invalid = url.isNotBlank() && !PageUrls.isValid(url)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = url,
                        onValueChange = { text -> urls = urls.toMutableList().also { it[index] = text } },
                        label = { Text(stringResource(R.string.url_label, index + 1)) },
                        placeholder = { Text("http://192.168.1.10:8080") },
                        isError = invalid,
                        supportingText = if (invalid) {
                            { Text(stringResource(R.string.url_invalid)) }
                        } else {
                            null
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                        modifier = Modifier.weight(1f),
                    )
                    Column(verticalArrangement = Arrangement.Center) {
                        IconButton(enabled = index > 0, onClick = { urls = urls.swapped(index, index - 1) }) {
                            Icon(Icons.Filled.KeyboardArrowUp, contentDescription = stringResource(R.string.move_up))
                        }
                        IconButton(enabled = index < urls.lastIndex, onClick = { urls = urls.swapped(index, index + 1) }) {
                            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = stringResource(R.string.move_down))
                        }
                    }
                    IconButton(
                        enabled = urls.size > 1,
                        onClick = { urls = urls.toMutableList().also { it.removeAt(index) } },
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.remove_url))
                    }
                }
            }

            TextButton(onClick = { urls = urls + "" }) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text(stringResource(R.string.add_url), modifier = Modifier.padding(start = 8.dp))
            }
        }
    }

    if (confirmDelete && page != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_page_title)) },
            text = { Text(stringResource(R.string.delete_page_message, page.name)) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete(page) }) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

private fun List<String>.swapped(a: Int, b: Int): List<String> =
    toMutableList().also { it[a] = this[b]; it[b] = this[a] }
