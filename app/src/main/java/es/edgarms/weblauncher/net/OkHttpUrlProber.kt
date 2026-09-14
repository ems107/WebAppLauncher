package es.edgarms.weblauncher.net

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/**
 * Probes with a HEAD request. No retry with GET is needed for servers that
 * reject HEAD: a 405 is still an answer, and an answer is all that is asked.
 */
class OkHttpUrlProber(private val client: OkHttpClient = defaultClient()) : UrlProber {

    override suspend fun probe(url: String): ProbeResult {
        val request = try {
            Request.Builder().url(url).head().build()
        } catch (e: IllegalArgumentException) {
            return ProbeResult.Failed(FailureKind.OTHER, e.message)
        }
        return suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onResponse(call: Call, response: Response) {
                    response.close()
                    continuation.resume(ProbeResult.Alive(response.code))
                }

                override fun onFailure(call: Call, e: IOException) {
                    continuation.resume(classify(e))
                }
            })
        }
    }

    companion object {
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            // Short on purpose: on a LAN a live server connects in milliseconds.
            .connectTimeout(1500, TimeUnit.MILLISECONDS)
            .readTimeout(3, TimeUnit.SECONDS)
            .callTimeout(4, TimeUnit.SECONDS)
            .followRedirects(false)
            .retryOnConnectionFailure(false)
            .build()

        /**
         * OkHttp wraps the real reason: "Failed to connect to /10.0.0.2:8731" is a
         * ConnectException whose cause says timed out, refused or no route. So the
         * whole cause chain is read, and the first thing it recognises decides.
         */
        internal fun classify(e: IOException): ProbeResult.Failed {
            val chain = generateSequence<Throwable>(e) { it.cause }.take(8).toList()
            val kind = chain.firstNotNullOfOrNull(::kindOf) ?: FailureKind.OTHER
            val detail = chain.last().message ?: e.message
            return ProbeResult.Failed(kind, detail?.ifEmpty { null })
        }

        private fun kindOf(t: Throwable): FailureKind? {
            val lower = t.message.orEmpty().lowercase()
            return when {
                t is UnknownHostException -> FailureKind.UNKNOWN_HOST
                t is NoRouteToHostException -> FailureKind.UNREACHABLE
                "refused" in lower -> FailureKind.REFUSED
                "unreachable" in lower || "no route" in lower -> FailureKind.UNREACHABLE
                "timed out" in lower || "timeout" in lower -> FailureKind.TIMEOUT
                // SocketTimeoutException, and the call timeout, are both InterruptedIOException.
                t is InterruptedIOException -> FailureKind.TIMEOUT
                else -> null
            }
        }
    }
}
