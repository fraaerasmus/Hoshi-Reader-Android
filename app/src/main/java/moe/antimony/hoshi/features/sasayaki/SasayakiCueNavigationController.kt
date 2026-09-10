package moe.antimony.hoshi.features.sasayaki

import moe.antimony.hoshi.epub.SasayakiMatchData
import moe.antimony.hoshi.epub.SasayakiMatch

import kotlin.math.abs
import kotlin.math.max

class SasayakiCueNavigationController(matchData: SasayakiMatchData?) {
    private var timeline = CueTimeline(matchData)

    fun updateMatchData(matchData: SasayakiMatchData?) {
        timeline = CueTimeline(matchData)
    }

    fun nextCueSeekTime(
        currentTime: Double,
        delay: Double,
    ): Double? {
        val playbackTime = currentTime - delay
        val anchor = timeline.cueAt(playbackTime)?.startTime ?: playbackTime
        val next = timeline.nextCue(after = anchor) ?: return null
        return next + delay
    }

    fun previousCueSeekTime(
        currentTime: Double,
        delay: Double,
    ): Double {
        val playbackTime = max(0.0, currentTime - delay)
        val anchor = timeline.cueAt(playbackTime)?.startTime ?: playbackTime
        val previous = timeline.previousCue(before = anchor) ?: 0.0
        return previous + delay
    }

    /** Seek time after moving [steps] cues (negative = backward) from [currentTime], stopping at either end. */
    fun cueSeekTimeForSteps(
        currentTime: Double,
        delay: Double,
        steps: Int,
    ): Double {
        var time = currentTime
        repeat(abs(steps)) {
            time = if (steps > 0) {
                nextCueSeekTime(currentTime = time, delay = delay) ?: return time
            } else {
                previousCueSeekTime(currentTime = time, delay = delay)
            }
        }
        return time
    }

    fun cueAtPlaybackTime(time: Double, delay: Double): SasayakiMatch? =
        timeline.cueAt(time - delay)

    fun findCue(chapterIndex: Int, offset: Int): SasayakiMatch? =
        timeline.findCue(chapterIndex = chapterIndex, offset = offset)
}
