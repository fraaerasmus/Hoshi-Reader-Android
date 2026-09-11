package moe.antimony.hoshi.features.sync

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncBackoffTest {
    @Test
    fun coolsDownAfterAFailureUntilTheWindowPassesOrASuccess() {
        var now = 1_000L
        val backoff = SyncBackoff(cooldownMillis = 60_000L) { now }

        assertFalse(backoff.isCoolingDown())
        backoff.recordFailure()
        assertTrue(backoff.isCoolingDown())
        now += 59_999L
        assertTrue(backoff.isCoolingDown())
        now += 1L
        assertFalse(backoff.isCoolingDown())

        backoff.recordFailure()
        backoff.recordSuccess()
        assertFalse(backoff.isCoolingDown())
    }
}
