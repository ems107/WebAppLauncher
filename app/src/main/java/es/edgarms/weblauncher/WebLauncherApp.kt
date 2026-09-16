package es.edgarms.weblauncher

import android.app.Application
import es.edgarms.weblauncher.data.ConfigStore
import es.edgarms.weblauncher.data.LastWinnerStore
import es.edgarms.weblauncher.data.PagesRepository
import es.edgarms.weblauncher.icons.IconFetcher
import es.edgarms.weblauncher.net.OkHttpUrlProber
import es.edgarms.weblauncher.net.UrlProber
import es.edgarms.weblauncher.update.UpdateCheckWorker
import es.edgarms.weblauncher.update.UpdateRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File
import java.util.Collections

class WebLauncherApp : Application() {
    val configStore by lazy { ConfigStore(File(filesDir, "config.json")) }
    val winners by lazy { LastWinnerStore(File(filesDir, "winners.json")) }
    val pages by lazy { PagesRepository(this) }
    val prober: UrlProber by lazy { OkHttpUrlProber() }
    val iconFetcher by lazy { IconFetcher() }
    val updates by lazy { UpdateRepository(this) }

    /** Pages whose site was already asked for an icon since the app started: once is enough. */
    val iconAttempts: MutableSet<String> = Collections.synchronizedSet(HashSet())

    /** For work that must outlive the screen that started it, like an icon download. */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        if (updates.enabled) UpdateCheckWorker.schedule(this)
    }
}
