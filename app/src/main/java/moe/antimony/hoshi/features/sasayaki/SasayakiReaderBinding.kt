package moe.antimony.hoshi.features.sasayaki

import moe.antimony.hoshi.epub.SasayakiMatch

/**
 * The reader-screen couplings the playback holder forwards into while a reader is visible.
 * The holder holds one of these only while a reader is attached; when it is null, playback
 * keeps running headless (cue/scroll/chapter callbacks simply no-op).
 */
class SasayakiReaderBinding(
    val getCurrentChapterIndex: () -> Int,
    val onCue: (SasayakiMatch, Boolean) -> Unit,
    val onClearCue: () -> Unit,
    val onLoadChapter: (Int) -> Unit,
)
