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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import es.edgarms.weblauncher.R
import es.edgarms.weblauncher.model.Page
import es.edgarms.weblauncher.ui.PageTile

/** @param pages null while the configuration is still being read. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageListScreen(
    pages: List<Page>?,
    onAdd: () -> Unit,
    onPageClick: (Page) -> Unit,
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.app_name)) }) },
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
                        leadingContent = { PageTile(page.name) },
                        modifier = Modifier.clickable { onPageClick(page) },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}
