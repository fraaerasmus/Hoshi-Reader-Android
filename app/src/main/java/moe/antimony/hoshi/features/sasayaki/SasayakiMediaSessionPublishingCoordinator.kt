package moe.antimony.hoshi.features.sasayaki

class SasayakiMediaSessionPublishingCoordinator(
    private val mediaSessionHandle: SasayakiMediaSessionHandleCoordinator,
) {
    fun activate() {
        mediaSessionHandle.activate()
    }

    fun update(
        isPlaying: Boolean,
        currentTime: Double,
        duration: Double,
        rate: Float,
    ) {
        SasayakiPlaybackStatePublisher.snapshot.value = SasayakiPlaybackSnapshot(
            isPlaying = isPlaying,
            positionMs = (currentTime * 1000.0).toLong(),
            durationMs = (duration * 1000.0).toLong(),
            speed = rate,
        )
        mediaSessionHandle.update(
            isPlaying = isPlaying,
            currentTimeMs = (currentTime * 1000.0).toLong(),
            durationMs = (duration * 1000.0).toLong(),
            rate = rate,
        )
    }
}
