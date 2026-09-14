package es.edgarms.weblauncher.icons

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Downloads a site's icon, following [IconCandidates] until something decodes. */
class IconFetcher(private val client: OkHttpClient = defaultClient()) {

    /** A square ready for an adaptive icon, or null when the site offers nothing usable. */
    suspend fun fetch(pageUrl: String): Bitmap? = withContext(Dispatchers.IO) {
        val html = text(pageUrl)
        val manifestIcons = html
            ?.let { IconCandidates.manifestUrl(pageUrl, it) }
            ?.let { url -> text(url)?.let { IconCandidates.fromManifest(url, it) } }
            .orEmpty()

        for (ref in IconCandidates.chain(pageUrl, html, manifestIcons)) {
            ensureActive()
            val bytes = bytes(ref.url) ?: continue
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: continue
            // A 16-pixel favicon blown up to an icon looks worse than the generated tile.
            if (minOf(bitmap.width, bitmap.height) < MIN_SOURCE_PX) continue
            return@withContext if (ref.maskable) {
                IconBitmaps.fullBleed(bitmap, PageIcons.SIZE_PX)
            } else {
                IconBitmaps.padded(bitmap, PageIcons.SIZE_PX)
            }
        }
        null
    }

    private fun text(url: String): String? = bytes(url)?.toString(Charsets.UTF_8)

    private fun bytes(url: String): ByteArray? {
        return try {
            client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                if (!response.isSuccessful || response.body.contentLength() > MAX_BYTES) return null
                response.body.bytes().takeIf { it.size <= MAX_BYTES }
            }
        } catch (_: IOException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    companion object {
        private const val MIN_SOURCE_PX = 48
        private const val MAX_BYTES = 2L * 1024 * 1024

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .callTimeout(10, TimeUnit.SECONDS)
            .build()
    }
}
