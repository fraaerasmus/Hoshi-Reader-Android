package moe.antimony.hoshi.features.sasayaki

import org.junit.Assert.assertEquals
import org.junit.Test

class SasayakiCycleSpeedTest {
    @Test
    fun stepsThroughDiscreteSpeedsAndWrapsToOne() {
        assertEquals(1.25f, sasayakiNextCycleSpeed(1.0f), 0f)
        assertEquals(1.5f, sasayakiNextCycleSpeed(1.25f), 0f)
        assertEquals(1.75f, sasayakiNextCycleSpeed(1.5f), 0f)
        assertEquals(2.0f, sasayakiNextCycleSpeed(1.75f), 0f)
        assertEquals(2.5f, sasayakiNextCycleSpeed(2.0f), 0f)
        assertEquals(3.0f, sasayakiNextCycleSpeed(2.5f), 0f)
        assertEquals(1.0f, sasayakiNextCycleSpeed(3.0f), 0f)
    }

    @Test
    fun snapsSliderValuesOntoTheNextStep() {
        assertEquals(1.0f, sasayakiNextCycleSpeed(0.5f), 0f)
        assertEquals(1.25f, sasayakiNextCycleSpeed(1.1f), 0f)
        assertEquals(3.0f, sasayakiNextCycleSpeed(2.85f), 0f)
    }
}
