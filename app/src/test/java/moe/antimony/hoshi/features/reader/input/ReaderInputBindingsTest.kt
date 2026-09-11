package moe.antimony.hoshi.features.reader.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderInputBindingsTest {
    @Test
    fun defaultsBoostOnEdgeHoldsAndLeaveDragsAndVolumeHoldOff() {
        val bindings = ReaderInputBindings()
        assertEquals(ReaderInputAction.BoostWhileHeld, bindings.action(ReaderInputSource.EdgeLeftHold))
        assertEquals(ReaderInputAction.BoostWhileHeld, bindings.action(ReaderInputSource.EdgeRightHold))
        assertEquals(ReaderInputAction.None, bindings.action(ReaderInputSource.EdgeLeftDoubleTap))
        assertEquals(ReaderInputAction.None, bindings.action(ReaderInputSource.EdgeLeftDrag))
        assertEquals(ReaderInputAction.None, bindings.action(ReaderInputSource.EdgeRightDrag))
        assertEquals(ReaderInputAction.None, bindings.action(ReaderInputSource.VolumeKeyHold))
    }

    @Test
    fun onlyActionsOfTheSourcesKindAreOffered() {
        assertEquals(
            listOf(ReaderInputAction.None, ReaderInputAction.BoostWhileHeld),
            ReaderInputBindings.compatibleActions(ReaderInputSource.EdgeLeftHold),
        )
        assertEquals(
            listOf(ReaderInputAction.None, ReaderInputAction.Scrub, ReaderInputAction.Brightness, ReaderInputAction.Volume),
            ReaderInputBindings.compatibleActions(ReaderInputSource.EdgeRightDrag),
        )
        assertTrue(ReaderInputBindings.compatibleActions(ReaderInputSource.EdgeLeftDoubleTap).contains(ReaderInputAction.TogglePlayback))
        assertTrue(runCatching { ReaderInputBindings().with(ReaderInputSource.EdgeLeftHold, ReaderInputAction.Scrub) }.isFailure)
    }

    @Test
    fun encodeDecodeRoundTripsAndDropsGarbage() {
        val bindings = ReaderInputBindings()
            .with(ReaderInputSource.EdgeRightDrag, ReaderInputAction.Scrub)
            .with(ReaderInputSource.EdgeLeftDoubleTap, ReaderInputAction.TogglePlayback)
        val decoded = ReaderInputBindings.decode(bindings.encode())
        assertEquals(ReaderInputAction.Scrub, decoded.action(ReaderInputSource.EdgeRightDrag))
        assertEquals(ReaderInputAction.TogglePlayback, decoded.action(ReaderInputSource.EdgeLeftDoubleTap))

        val messy = ReaderInputBindings.decode("""{"EdgeLeftHold":"Scrub","Unknown":"None","EdgeRightHold":"None"}""")
        assertEquals(ReaderInputAction.BoostWhileHeld, messy.action(ReaderInputSource.EdgeLeftHold))
        assertEquals(ReaderInputAction.None, messy.action(ReaderInputSource.EdgeRightHold))
        assertEquals(ReaderInputBindings(), ReaderInputBindings.decode("not json"))
    }

    @Test
    fun legacyEdgeSwipeSwitchMapsToTheDragBindings() {
        val off = ReaderInputBindings.fromLegacy(edgeSwipeControls = false)
        assertEquals(ReaderInputAction.None, off.action(ReaderInputSource.EdgeLeftDrag))
        assertEquals(ReaderInputAction.None, off.action(ReaderInputSource.EdgeRightDrag))
        assertEquals(ReaderInputAction.BoostWhileHeld, off.action(ReaderInputSource.EdgeLeftHold))
        val on = ReaderInputBindings.fromLegacy(edgeSwipeControls = true)
        assertEquals(ReaderInputAction.Brightness, on.action(ReaderInputSource.EdgeLeftDrag))
        assertEquals(ReaderInputAction.Volume, on.action(ReaderInputSource.EdgeRightDrag))
        assertFalse(off.map.containsKey(ReaderInputSource.EdgeLeftDrag))
    }
}
