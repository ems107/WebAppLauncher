package es.edgarms.weblauncher.update

import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import es.edgarms.weblauncher.BuildConfig
import es.edgarms.weblauncher.WebLauncherApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException

data class UpdateState(
    val available: AvailableUpdate? = null,
    val install: InstallProgress = InstallProgress.Idle,
)

sealed interface InstallProgress {
    data object Idle : InstallProgress

    data class Downloading(val fraction: Float) : InstallProgress

    /** Handed to Android, which asks the user to confirm. */
    data object Confirming : InstallProgress

    data class Failed(val reason: InstallFailure, val detail: String? = null) : InstallProgress
}

enum class InstallFailure { DOWNLOAD, NOT_THIS_UPDATE, SIGNATURE, CANCELLED, OTHER }

/**
 * The one copy of the update state while the app runs: the hourly check, the
 * list's banner and the install result all go through it.
 */
class UpdateRepository(private val app: WebLauncherApp) {
    val running: Version? = Version.parse(BuildConfig.VERSION_NAME)

    /** Off in debug builds: they are signed with another key, so no release could install over them. */
    val enabled: Boolean = BuildConfig.UPDATES_ENABLED && running != null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val checker = running?.let {
        UpdateChecker(UpdateStore(File(app.filesDir, "update.json")), GitHubReleaseSource(BuildConfig.UPDATE_REPO), it)
    }
    private val downloader = ApkDownloader(File(app.cacheDir, "updates"))

    private val _state = MutableStateFlow(UpdateState())
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    init {
        if (enabled) {
            scope.launch {
                val available = checker!!.available()
                _state.update { it.copy(available = available) }
                // A download for the version now running, or for one no longer offered, is dead weight.
                downloader.prune(keepVersion = available?.version)
            }
        }
    }

    /** Asks whether a newer version exists. Without [force], an answer from the last hour is reused. */
    suspend fun check(force: Boolean): CheckOutcome {
        val checker = checker?.takeIf { enabled } ?: return CheckOutcome.UpToDate
        val outcome = checker.check(force)
        val available = (outcome as? CheckOutcome.Found)?.update
        if (outcome !is CheckOutcome.Failed) {
            _state.update { state ->
                // A different version on offer makes an earlier failure irrelevant.
                val install = if (state.available?.version == available?.version) state.install else InstallProgress.Idle
                UpdateState(available, install)
            }
        }
        return outcome
    }

    fun checkSoon() {
        if (enabled) scope.launch { check(force = false) }
    }

    /** False until the user lets this app install apps; Android asks per app since 8.0. */
    fun canInstall(): Boolean = app.packageManager.canRequestPackageInstalls()

    /** Downloads the update on offer and hands it to Android, which asks the user to confirm. */
    fun install() {
        val update = _state.value.available ?: return
        val busy = _state.value.install.let { it is InstallProgress.Downloading || it is InstallProgress.Confirming }
        if (busy) return
        setInstall(InstallProgress.Downloading(0f))
        scope.launch {
            val apk = try {
                downloader.download(update) { fraction -> setInstall(InstallProgress.Downloading(fraction)) }
            } catch (e: IOException) {
                setInstall(InstallProgress.Failed(InstallFailure.DOWNLOAD, e.message))
                return@launch
            }
            if (!isThisUpdate(apk, update)) {
                apk.delete()
                setInstall(InstallProgress.Failed(InstallFailure.NOT_THIS_UPDATE))
                return@launch
            }
            try {
                commitSession(apk)
                setInstall(InstallProgress.Confirming)
            } catch (e: IOException) {
                setInstall(InstallProgress.Failed(InstallFailure.OTHER, e.message))
            } catch (e: SecurityException) {
                setInstall(InstallProgress.Failed(InstallFailure.OTHER, e.message))
            }
        }
    }

    /** What Android answered, from [InstallResultReceiver]. On success this process is about to be replaced. */
    fun onInstallResult(status: Int, message: String?) {
        val progress = when (status) {
            PackageInstaller.STATUS_SUCCESS -> InstallProgress.Idle
            PackageInstaller.STATUS_FAILURE_ABORTED -> InstallProgress.Failed(InstallFailure.CANCELLED)
            PackageInstaller.STATUS_FAILURE_CONFLICT, PackageInstaller.STATUS_FAILURE_INCOMPATIBLE ->
                InstallProgress.Failed(InstallFailure.SIGNATURE, message)
            else -> InstallProgress.Failed(InstallFailure.OTHER, message)
        }
        setInstall(progress)
    }

    private fun setInstall(progress: InstallProgress) = _state.update { it.copy(install = progress) }

    /** The file must be this app, at the version the release said: anything else is not installed. */
    private fun isThisUpdate(apk: File, update: AvailableUpdate): Boolean {
        val info = app.packageManager.getPackageArchiveInfo(apk.path, 0) ?: return false
        return info.packageName == app.packageName && info.versionName == update.version
    }

    private fun commitSession(apk: File) {
        val installer = app.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(app.packageName)
            setSize(apk.length())
        }
        val sessionId = installer.createSession(params)
        try {
            installer.openSession(sessionId).use { session ->
                session.openWrite("base.apk", 0, apk.length()).use { out ->
                    apk.inputStream().use { it.copyTo(out) }
                    session.fsync(out)
                }
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                    // Android fills in the result, so the intent has to be mutable.
                    (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
                val callback = PendingIntent.getBroadcast(app, sessionId, Intent(app, InstallResultReceiver::class.java), flags)
                session.commit(callback.intentSender)
            }
        } catch (e: IOException) {
            installer.abandonSession(sessionId)
            throw e
        }
    }
}
