package moe.antimony.hoshi.features.kosync

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class KosyncDocumentIdTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    // Expected digests computed with a Python port of KOReader's util.partialMD5 over the same LCG data.
    @Test
    fun partialMd5MatchesKoreaderSampling() {
        assertEquals("0fb3683d664e8486ed6dd0302b45574c", KosyncDocumentId.partialMd5(file(500, 1)))
        assertEquals("cfbde19bab78b2390852a21fa57a1f8b", KosyncDocumentId.partialMd5(file(1024, 2)))
        assertEquals("7a2b0a5a4e8fb9fbfbfc2647805113b8", KosyncDocumentId.partialMd5(file(5000, 3)))
        assertEquals("63ecb39f176e22b361918258f1263547", KosyncDocumentId.partialMd5(file(70000, 4)))
    }

    @Test
    fun filenameMd5HashesTheBasename() {
        assertEquals("03053ffc045564439ff7f2cabb3b58c5", KosyncDocumentId.filenameMd5("book.epub"))
    }

    private fun file(size: Int, seed: Long) = tempFolder.newFile("f-$size").apply {
        var x = seed
        val bytes = ByteArray(size) {
            x = (x * 1103515245L + 12345L) and 0x7fffffffL
            ((x shr 16) and 0xff).toByte()
        }
        writeBytes(bytes)
    }
}
