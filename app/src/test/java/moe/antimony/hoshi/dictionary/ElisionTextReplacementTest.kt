package moe.antimony.hoshi.dictionary

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ElisionTextReplacementTest {
    @Test
    fun stripsLeadingFrenchElisions() {
        assertEquals("homme", ElisionTextReplacement.stripElision("l'homme", "fr"))
        assertEquals("homme", ElisionTextReplacement.stripElision("l’homme", "fr")) // typographic ’
        assertEquals("homme", ElisionTextReplacement.stripElision("L'homme", "fr")) // sentence-start
        assertEquals("il", ElisionTextReplacement.stripElision("qu'il", "fr"))
        assertEquals("est", ElisionTextReplacement.stripElision("c'est", "fr"))
        assertEquals("accord", ElisionTextReplacement.stripElision("d'accord", "fr"))
    }

    @Test
    fun keepsTrailingContextForMultiWordScans() {
        assertEquals("homme politique", ElisionTextReplacement.stripElision("l'homme politique", "fr"))
    }

    @Test
    fun leavesNonElisionsAndOtherLanguagesAlone() {
        assertNull(ElisionTextReplacement.stripElision("homme", "fr"))
        assertNull(ElisionTextReplacement.stripElision("don't", "fr")) // apostrophe is not after a leading elision
        assertNull(ElisionTextReplacement.stripElision("l'", "fr")) // nothing left after stripping
        assertNull(ElisionTextReplacement.stripElision("l'homme", "ja"))
        assertNull(ElisionTextReplacement.stripElision("l'homme", null))
    }
}
