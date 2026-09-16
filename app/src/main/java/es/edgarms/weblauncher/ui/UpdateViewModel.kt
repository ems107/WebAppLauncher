package es.edgarms.weblauncher.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import es.edgarms.weblauncher.BuildConfig
import es.edgarms.weblauncher.WebLauncherApp
import es.edgarms.weblauncher.update.CheckOutcome
import es.edgarms.weblauncher.update.UpdateState
import kotlinx.coroutines.flow.StateFlow

class UpdateViewModel(application: Application) : AndroidViewModel(application) {
    private val updates = (application as WebLauncherApp).updates

    val enabled: Boolean = updates.enabled
    val runningVersion: String = BuildConfig.VERSION_NAME
    val state: StateFlow<UpdateState> = updates.state

    suspend fun checkNow(): CheckOutcome = updates.check(force = true)

    /** Checks only if the last answer is older than the hourly job's. */
    fun checkIfDue() = updates.checkSoon()

    fun canInstall(): Boolean = updates.canInstall()

    fun install() = updates.install()
}
