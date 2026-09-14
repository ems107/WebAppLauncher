package es.edgarms.weblauncher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import es.edgarms.weblauncher.ui.PagesViewModel
import es.edgarms.weblauncher.ui.edit.PageEditScreen
import es.edgarms.weblauncher.ui.list.PageListScreen
import es.edgarms.weblauncher.ui.theme.WebLauncherTheme
import es.edgarms.weblauncher.web.WebActivity
import kotlinx.serialization.Serializable

@Serializable
private object PageListRoute

@Serializable
private data class PageEditRoute(val pageId: String? = null)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WebLauncherTheme {
                LauncherNavHost()
            }
        }
    }
}

@Composable
private fun LauncherNavHost(viewModel: PagesViewModel = viewModel()) {
    val nav = rememberNavController()
    val context = LocalContext.current
    val config by viewModel.config.collectAsStateWithLifecycle()

    NavHost(nav, startDestination = PageListRoute) {
        composable<PageListRoute> {
            PageListScreen(
                pages = config?.pages,
                onAdd = { nav.navigate(PageEditRoute()) },
                onOpen = { context.startActivity(WebActivity.intent(context, it.id)) },
                onEdit = { nav.navigate(PageEditRoute(it.id)) },
            )
        }
        composable<PageEditRoute> { entry ->
            val pageId = entry.toRoute<PageEditRoute>().pageId
            // Captured once: after a delete the page vanishes from the config while
            // this screen is still animating out, and must not turn into "new page".
            val page = remember(pageId) { pageId?.let { config?.page(it) } }
            PageEditScreen(
                page = page,
                onSave = { nav.popFrom(entry); viewModel.save(it) },
                onDelete = { nav.popFrom(entry); viewModel.delete(it.id) },
                onBack = { nav.popFrom(entry) },
            )
        }
    }
}

/** Pops [entry] only if it is still the screen on top, so a double tap cannot pop twice. */
private fun NavController.popFrom(entry: NavBackStackEntry) {
    if (entry.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) popBackStack()
}
