package moe.antimony.hoshi.features.kosync

import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest

/** Document identity as KOReader computes it (`plugins/kosync.koplugin`). */
object KosyncDocumentId {
    /**
     * KOReader's `util.partialMD5`: one MD5 over 1 KiB samples at 0, 1 KiB, 4 KiB, ... 1 GiB
     * (`1024 << 2i`, where the i = -1 step overflows to offset 0), stopping at the first
     * sample past EOF; a short final sample is hashed as read.
     */
    fun partialMd5(file: File): String {
        val digest = MessageDigest.getInstance("MD5")
        val buffer = ByteArray(SampleSize)
        RandomAccessFile(file, "r").use { input ->
            val length = input.length()
            for (offset in SampleOffsets) {
                if (offset >= length) break
                input.seek(offset)
                val read = input.read(buffer, 0, SampleSize)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().toHex()
    }

    fun filenameMd5(fileName: String): String =
        MessageDigest.getInstance("MD5").digest(fileName.toByteArray(Charsets.UTF_8)).toHex()

    private const val SampleSize = 1024
    private val SampleOffsets: List<Long> = listOf(0L) + (0..10).map { 1024L shl (2 * it) }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
