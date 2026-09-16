package es.edgarms.weblauncher.update

import es.edgarms.weblauncher.data.Files
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Downloads an update's APK into [dir]. A dropped connection is not the end of
 * it: the bytes already on disk stay in a .part file and the next attempt asks
 * only for the rest.
 */
class ApkDownloader(
    private val dir: File,
    private val client: OkHttpClient = defaultClient(),
    private val retryDelayMillis: Long = 2_000,
) {
    /** @throws IOException once every attempt has failed. */
    suspend fun download(update: AvailableUpdate, onProgress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val target = File(dir, "weblauncher-${update.version}.apk")
        if (target.length() == update.apkSize) return@withContext target
        dir.mkdirs()
        val part = File(dir, target.name + ".part")
        var failure: IOException? = null
        repeat(ATTEMPTS) { attempt ->
            ensureActive()
            if (attempt > 0) delay(retryDelayMillis)
            try {
                fetchInto(part, update, onProgress)
                if (part.length() == update.apkSize) {
                    Files.replace(part, target)
                    return@withContext target
                }
                failure = IOException("Expected ${update.apkSize} bytes, got ${part.length()}")
                part.delete()
            } catch (e: IOException) {
                failure = e
            }
        }
        throw failure ?: IOException("Download failed")
    }

    /** Deletes every download, finished or not, except [keepVersion]'s. */
    fun prune(keepVersion: String?) {
        val kept = keepVersion?.let { "weblauncher-$it.apk" }
        dir.listFiles()?.filterNot { kept != null && it.name.startsWith(kept) }?.forEach { it.delete() }
    }

    private fun fetchInto(part: File, update: AvailableUpdate, onProgress: (Float) -> Unit) {
        val offset = part.length().takeIf { it in 1 until update.apkSize } ?: 0L
        val request = Request.Builder()
            .url(update.apkUrl)
            .apply { if (offset > 0) header("Range", "bytes=$offset-") }
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            // A server that ignores the range sends the whole file again, so it is written from the start.
            val append = offset > 0 && response.code == 206
            var written = if (append) offset else 0L
            FileOutputStream(part, append).use { out ->
                val input = response.body.byteStream()
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    out.write(buffer, 0, read)
                    written += read
                    onProgress((written.toFloat() / update.apkSize).coerceIn(0f, 1f))
                }
            }
        }
    }

    companion object {
        private const val ATTEMPTS = 5

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            // A limit on silence, not on total time: a slow download is still a download.
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}
