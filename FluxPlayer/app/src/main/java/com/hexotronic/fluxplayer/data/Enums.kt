package com.hexotronic.fluxplayer.data

/** Art der Frame-Generierung (Zwischenbildberechnung). */
enum class FrameGenMode(val label: String, val description: String) {
    OFF("Aus", "Originale Bildrate des Videos"),
    BLEND("Überblenden", "Zwischenbilder durch weiches Überblenden – sehr sparsam"),
    MOTION("Bewegungsinterpolation", "Bewegungskompensierte Zwischenbilder (optischer Fluss auf der GPU)")
}

/**
 * Qualitätsstufe der Bewegungsschätzung.
 * [fineLod] = Mipmap-Stufe des feinen Bewegungsfelds (3 = 1/8 Auflösung),
 * [coarseRadius] = Suchradius der groben Stufe, [fineRadius] = Suchradius je Kandidat der Verfeinerung.
 */
enum class FrameGenQuality(
    val label: String,
    val fineLod: Int,
    val coarseRadius: Int,
    val fineRadius: Int
) {
    PERFORMANCE("Leistung", 4, 4, 1),
    BALANCED("Ausgewogen", 3, 5, 1),
    QUALITY("Qualität", 2, 6, 1)
}

/** Seitenverhältnis / Skalierungsmodus. */
enum class AspectMode(val label: String, val ratio: Float?) {
    FIT("Einpassen", null),
    FILL("Füllen (Zuschneiden)", null),
    STRETCH("Strecken", null),
    ORIGINAL("Original (1:1 Pixel)", null),
    R16_9("16:9", 16f / 9f),
    R4_3("4:3", 4f / 3f),
    R21_9("21:9", 21f / 9f),
    R185("1.85:1", 1.85f),
    R235("2.35:1", 2.35f),
    R1_1("1:1", 1f)
}

enum class DecoderPreference(val label: String, val description: String) {
    AUTO("Automatisch", "Hardware bevorzugt, FFmpeg als Rückfall"),
    HARDWARE("Hardware", "Nur Hardware-Decoder des Geräts (MediaCodec)"),
    SOFTWARE("Software (FFmpeg)", "FFmpeg-Decoder – maximale Formatunterstützung")
}

enum class OrientationMode(val label: String) {
    AUTO("Automatisch (nach Video)"),
    SENSOR("Sensor (frei drehen)"),
    LANDSCAPE("Querformat"),
    PORTRAIT("Hochformat")
}

enum class SortOrder(val label: String) {
    NAME("Name"),
    DATE("Datum"),
    SIZE("Größe"),
    DURATION("Länge")
}
