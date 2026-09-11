package moe.antimony.hoshi.features.sync

/** Skips network attempts for a while after a failure so a dead server costs one short try, not one per hook. */
class SyncBackoff(
    private val cooldownMillis: Long = 60_000L,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    @Volatile
    private var failedAt: Long? = null

    fun isCoolingDown(): Boolean = failedAt?.let { nowMillis() - it < cooldownMillis } == true

    fun recordFailure() {
        failedAt = nowMillis()
    }

    fun recordSuccess() {
        failedAt = null
    }
}
