package moe.antimony.hoshi.features.kosync

/**
 * Mirrors `hoshi-web/reader/reader-text-semantics.js` `countChars`: the ttu character
 * alphabet (`[0-9A-Za-z○◯々-〇〻ぁ-ゖゝ-ゞァ-ヺー０-９Ａ-Ｚａ-ｚｦ-ﾝ\p{Radical}\p{Unified_Ideograph}]`).
 * Reader progress is a ratio over these counts, so XPointer mapping must count the same way.
 */
object KosyncTextSemantics {
    fun countChars(text: CharSequence): Int {
        var count = 0
        var i = 0
        while (i < text.length) {
            val codePoint = Character.codePointAt(text, i)
            if (isCounted(codePoint)) count++
            i += Character.charCount(codePoint)
        }
        return count
    }

    fun isCounted(cp: Int): Boolean = when {
        cp in 0x30..0x39 || cp in 0x41..0x5A || cp in 0x61..0x7A -> true
        cp == 0x25CB || cp == 0x25EF -> true
        cp in 0x3005..0x3007 || cp == 0x303B -> true
        cp in 0x3041..0x3096 || cp in 0x309D..0x309E -> true
        cp in 0x30A1..0x30FA || cp == 0x30FC -> true
        cp in 0xFF10..0xFF19 || cp in 0xFF21..0xFF3A || cp in 0xFF41..0xFF5A || cp in 0xFF66..0xFF9D -> true
        // \p{Radical}
        cp in 0x2E80..0x2E99 || cp in 0x2E9B..0x2EF3 || cp in 0x2F00..0x2FD5 -> true
        // \p{Unified_Ideograph}
        cp in 0x3400..0x4DBF || cp in 0x4E00..0x9FFF -> true
        cp == 0xFA0E || cp == 0xFA0F || cp == 0xFA11 || cp == 0xFA13 || cp == 0xFA14 || cp == 0xFA1F ||
            cp == 0xFA21 || cp == 0xFA23 || cp == 0xFA24 || cp in 0xFA27..0xFA29 -> true
        cp in 0x20000..0x2A6DF || cp in 0x2A700..0x2B739 || cp in 0x2B740..0x2B81D ||
            cp in 0x2B820..0x2CEA1 || cp in 0x2CEB0..0x2EBE0 || cp in 0x2EBF0..0x2EE5D ||
            cp in 0x30000..0x3134A || cp in 0x31350..0x323AF -> true
        else -> false
    }
}
