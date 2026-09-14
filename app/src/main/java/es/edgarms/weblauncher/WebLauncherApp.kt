package es.edgarms.weblauncher

import android.app.Application
import es.edgarms.weblauncher.data.ConfigStore
import es.edgarms.weblauncher.data.LastWinnerStore
import java.io.File

class WebLauncherApp : Application() {
    val configStore by lazy { ConfigStore(File(filesDir, "config.json")) }
    val winners by lazy { LastWinnerStore(File(filesDir, "winners.json")) }
}
