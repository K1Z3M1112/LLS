package com.lsfg.android.prefs

enum class CpuPostProcessingPreset(val nativeValue: Int, val label: String) {
    OFF(0, "Off"),
    ENHANCE_LUT(1, "Enhance"),
    GAMER_SHARP(2, "Sharp"),
    WARM(3, "Warm"),
    COOL(4, "Cool"),
    VIGNETTE(5, "Vignette"),
    CINEMATIC(6, "Cinematic"),
}
