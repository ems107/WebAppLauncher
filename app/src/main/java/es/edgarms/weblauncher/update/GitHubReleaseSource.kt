package es.edgarms.weblauncher.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

sealed interface FeedResult {
    data class Fresh(val body: String, val etag: String?) : FeedResult

    /** The list has not changed since the ETag [GitHubReleaseSource.fetch] was given; this answer is free. */
    data object NotModified : FeedResult

    data class Failed(val reason: FeedFailure) : FeedResult
}

enum class FeedFailure { OFFLINE, RATE_LIMITED, SERVER }

/**
 * Asks GitHub for a repository's releases. Unauthenticated, which allows 60
 * requests an hour per public address: an hourly check with an ETag, where an
 * unchanged answer is a 304 that does not count, stays far below that.
 */
class GitHubReleaseSource(
    private val repo: String,
    private val baseUrl: String = "https://api.github.com",
    private val client: OkHttpClient = defaultClient(),
) {
    suspend fun fetch(etag: String?): FeedResult = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$baseUrl/repos/$repo/releases?per_page=30")
            .header("Accept", "application/vnd.github+json")
            // GitHub rejects API requests that carry no User-Agent.
            .header("User-Agent", "WebLauncher")
            .apply { if (etag != null) header("If-None-Match", etag) }
            .build()
        try {
            client.newCall(request).execute().use { response ->
                when {
                    response.code == 304 -> FeedResult.NotModified
                    response.isSuccessful -> FeedResult.Fresh(response.body.string(), response.header("ETag"))
                    response.code == 429 || (response.code == 403 && response.header("X-RateLimit-Remaining") == "0") ->
                        FeedResult.Failed(FeedFailure.RATE_LIMITED)
                    else -> FeedResult.Failed(FeedFailure.SERVER)
                }
            }
        } catch (_: IOException) {
            FeedResult.Failed(FeedFailure.OFFLINE)
        }
    }

    companion object {
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}
