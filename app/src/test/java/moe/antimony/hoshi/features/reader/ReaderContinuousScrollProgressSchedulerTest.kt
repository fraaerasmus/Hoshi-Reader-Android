package moe.antimony.hoshi.features.reader

import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderContinuousScrollProgressSchedulerTest {
    @Test
    fun scrollEventsWhileProgressIsRunningRequestOnlyOneFollowUpEvaluation() {
        var now = 1_000L
        val scheduler = ReaderContinuousScrollProgressScheduler(nowMillis = { now })
        val evaluations = mutableListOf<(String?) -> Unit>()
        val displayed = mutableListOf<Double>()
        val saved = mutableListOf<Double>()
        val throttleCallbacks = mutableListOf<Runnable>()
        var idleSaveCallback: Runnable? = null

        fun scroll() {
            scheduler.onScrollChanged(
                isRestoring = false,
                restoreEpoch = 3,
                evaluateProgress = { callback -> evaluations += callback },
                onProgressChanged = { progress, _ -> displayed += progress },
                onProgressIdle = { progress, _ -> saved += progress },
                onClearLookupPopup = {},
                postDelayed = { callback, _ -> throttleCallbacks += callback },
                removeCallback = { callback -> throttleCallbacks.remove(callback) },
                cancelIdleSave = { idleSaveCallback = null },
                scheduleIdleSave = { callback, _ -> idleSaveCallback = callback },
            )
        }

        scroll()
        assertEquals(1, evaluations.size)

        repeat(6) {
            now += 50L
            scroll()
        }

        assertEquals(1, evaluations.size)

        evaluations.removeAt(0)("0.25")
        assertEquals(listOf(0.25), displayed)
        assertEquals(1, evaluations.size)

        evaluations.removeAt(0)("0.5")
        assertEquals(listOf(0.25, 0.5), displayed)
        assertEquals(0, evaluations.size)
        assertEquals(emptyList<Double>(), saved)

        idleSaveCallback?.run()
        assertEquals(listOf(0.5), saved)
    }

    @Test
    fun scrollWithinThrottleSchedulesOneDelayedProgressEvaluation() {
        var now = 1_000L
        val scheduler = ReaderContinuousScrollProgressScheduler(nowMillis = { now })
        val evaluations = mutableListOf<(String?) -> Unit>()
        val displayed = mutableListOf<Double>()
        val throttleCallbacks = mutableListOf<Runnable>()
        var idleSaveCallback: Runnable? = null

        fun scroll() {
            scheduler.onScrollChanged(
                isRestoring = false,
                restoreEpoch = 4,
                evaluateProgress = { callback -> evaluations += callback },
                onProgressChanged = { progress, _ -> displayed += progress },
                onProgressIdle = { _, _ -> },
                onClearLookupPopup = {},
                postDelayed = { callback, _ -> throttleCallbacks += callback },
                removeCallback = { callback -> throttleCallbacks.remove(callback) },
                cancelIdleSave = { idleSaveCallback = null },
                scheduleIdleSave = { callback, _ -> idleSaveCallback = callback },
            )
        }

        scroll()
        evaluations.removeAt(0)("0.1")

        now += 20L
        scroll()
        scroll()

        assertEquals(0, evaluations.size)
        assertEquals(1, throttleCallbacks.size)
        assertEquals(null, idleSaveCallback)

        now += 30L
        throttleCallbacks.removeAt(0).run()
        assertEquals(1, evaluations.size)

        evaluations.removeAt(0)("0.2")
        assertEquals(listOf(0.1, 0.2), displayed)
    }
}
