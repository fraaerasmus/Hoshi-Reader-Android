package moe.antimony.hoshi.features.sasayaki

/** Sleep-timer choices: off, a fixed duration, or "stop when the current chapter ends". */
enum class SasayakiSleepTimerOption(val minutes: Int?) {
    Off(null),
    Min15(15),
    Min30(30),
    Min45(45),
    Min60(60),
    EndOfChapter(null),
}

data class SasayakiSleepTimerState(
    val option: SasayakiSleepTimerOption = SasayakiSleepTimerOption.Off,
    val remainingSeconds: Int = 0,
) {
    val isActive: Boolean get() = option != SasayakiSleepTimerOption.Off
}
