package moe.antimony.hoshi.features.reader

import moe.antimony.hoshi.R
import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderBehaviorSasayakiTest {
    @Test
    fun behaviorKeepsOnlyNonGestureRows() {
        assertEquals(
            listOf(
                R.string.reader_behavior_keep_screen_on,
                R.string.reader_behavior_lock_current_orientation,
                R.string.reader_behavior_open_last_read_book_on_launch,
                R.string.reader_behavior_auto_check_updates,
            ),
            readerBehaviorRows(),
        )
    }
}
