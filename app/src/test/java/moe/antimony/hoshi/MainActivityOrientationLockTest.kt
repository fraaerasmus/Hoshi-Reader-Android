package moe.antimony.hoshi

import android.content.pm.ActivityInfo
import org.junit.Assert.assertEquals
import org.junit.Test

class MainActivityOrientationLockTest {
    @Test
    fun orientationLockSettingMapsToLockedRequest() {
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_LOCKED,
            requestedOrientationForLockCurrentOrientation(lockCurrentOrientation = true),
        )
    }

    @Test
    fun disabledOrientationLockRestoresUnspecifiedRequest() {
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED,
            requestedOrientationForLockCurrentOrientation(lockCurrentOrientation = false),
        )
    }
}
