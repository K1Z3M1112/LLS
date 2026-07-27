package com.lsfg.android.prefs

enum class NpuPostProcessingPreset(val nativeValue: Int, val label: String) {
    OFF(0, "Off"),
    SHARPEN(1, "Sharpen"),
    DETAIL_BOOST(2, "Detail Boost"),
    CHROMA_CLEAN(3, "Chroma Clean"),
    GAME_CRISP(4, "Game Crisp"),
}
