package es.edgarms.weblauncher.update

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface CheckOutcome {
    data object UpToDate : CheckOutcome

    data class Found(val update: AvailableUpdate) : CheckOutcome

    data class Failed(val reason: FeedFailure) : CheckOutcome
}

/** Whether a newer version is published, asked no more often than it needs to be. */
class UpdateChecker(
    private val store: UpdateStore,
    private val source: GitHubReleaseSource,
    private val running: Version,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val mutex = Mutex()

    /** What the last check found, without asking again. */
    fun available(): AvailableUpdate? = usable(store.load()).available

    /**
     * Asks GitHub, unless [force] is false and the last answer is recent enough.
     * A failed check keeps what was known before: an update found an hour ago
     * is still there when the network is not.
     */
    suspend fun check(force: Boolean): CheckOutcome = mutex.withLock {
        val record = usable(store.load())
        if (!force && now() - record.checkedAt in 0 until RECENT_MILLIS) return@withLock outcomeOf(record)

        val checked = when (val result = source.fetch(record.etag)) {
            is FeedResult.Fresh -> {
                val newest = try {
                    ReleaseFeed.newest(result.body, running)
                } catch (_: IllegalArgumentException) {
                    return@withLock CheckOutcome.Failed(FeedFailure.SERVER)
                }
                record.copy(etag = result.etag, available = newest)
            }
            FeedResult.NotModified -> record
            is FeedResult.Failed -> return@withLock CheckOutcome.Failed(result.reason)
        }
        checked.copy(checkedAt = now(), checkedFrom = running.toString()).also(store::save).let(::outcomeOf)
    }

    /** A record made by another version says nothing about this one, not even through its ETag. */
    private fun usable(record: UpdateRecord): UpdateRecord =
        if (record.checkedFrom == running.toString()) record else UpdateRecord()

    private fun outcomeOf(record: UpdateRecord): CheckOutcome =
        record.available?.let { CheckOutcome.Found(it) } ?: CheckOutcome.UpToDate

    companion object {
        /**
         * Short of an hour on purpose: the periodic check does not run to the
         * minute, and one that comes a little early must not be skipped.
         */
        const val RECENT_MILLIS = 50 * 60 * 1000L
    }
}
