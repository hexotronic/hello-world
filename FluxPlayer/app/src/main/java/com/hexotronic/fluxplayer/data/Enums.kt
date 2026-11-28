package com.hexotronic.fluxplayer.data

/** Art der Frame-Generierung (Zwischenbildberechnung). */
enum class FrameGenMode(val label: String, val description: String) {
    OFF("Aus", "Originale Bildrate des Videos"),
    BLEND("Überblenden", "Zwischenbilder durch weiches Überblenden – sehr sparsam"),
    MOTION("Bewegungsinterpolation", "Bewegungskompensierte Zwischenbilder (optischer Fluss auf der GPU)")
}

/**
 * Qualitätsstufe der Bewegungsschätzung.
 * [fineLod] = Mipmap-Stufe des feinsten Bewegungsfelds (3 = 1/8 Auflösung, 1 = 1/2),
 * [coarseLod] = Stufe der groben Vollsuche, dazwischen wird auf jeder Stufe verfeinert,
 * [coarseRadius] = Suchradius der groben Stufe, [fineRadius] = Suchradius je Kandidat der Verfeinerung,
 * [bidirectional] = zusätzlich Rückwärtsfluss + Verdeckungserkennung.
 */
enum class FrameGenQuality(
    val label: String,
    val description: String,
    val fineLod: Int,
    val coarseLod: Int,
    val coarseRadius: Int,
    val fineRadius: Int,
    val bidirectional: Boolean
) {
    PERFORMANCE("Leistung", "Schnell, für ältere Geräte", 4, 6, 4, 1, false),
    BALANCED("Ausgewogen", "Empfohlen für normale Wiedergabe", 3, 5, 5, 1, false),
    QUALITY("Qualität", "Feineres Bewegungsfeld, braucht eine starke GPU", 2, 4, 6, 1, false),
    ULTRA(
        "Ultra (Zeitlupe)",
        "Halbe Auflösung, 4 Pyramidenstufen, bidirektional mit Verdeckungserkennung – sehr GPU-intensiv, ideal für Zeitlupe",
        1, 5, 5, 1, true
    )
}

/** Upscaler: wird einmal pro Videobild angewendet (nicht pro Display-Refresh). */
enum class UpscalerMode(val label: String, val description: String, val asset: String? = null) {
    OFF("Aus", "Keine Hochskalierung – Bild wird nur von der GPU gefiltert"),
    BICUBIC("Bikubisch", "Catmull-Rom, schnell und schärfer als bilinear"),
    EDGE("Kantenadaptiv", "Glättet Treppen an Kanten (FSR-artig), sehr schnell"),
    AI_S("KI S (Anime4K)", "Neuronales Netz, klein – läuft auf fast allen Handys flüssig", "upscalers/Anime4K_Upscale_CNN_x2_S.glsl"),
    AI_M("KI M (Anime4K)", "Neuronales Netz, mittel – deutlich schärfere Linien und Details", "upscalers/Anime4K_Upscale_CNN_x2_M.glsl"),
    AI_L("KI L (Anime4K)", "Neuronales Netz, groß – beste Qualität, nur für starke GPUs", "upscalers/Anime4K_Upscale_CNN_x2_L.glsl");

    val isAi: Boolean get() = asset != null
}

/** Wie stark hochgerechnet wird (bezogen auf die Videoauflösung). */
enum class UpscaleFactor(val label: String, val factor: Float) {
    AUTO("Automatisch (Displayauflösung)", 0f),
    X1_5("1,5×", 1.5f),
    X2("2×", 2f),
    X3("3×", 3f),
    X4("4×", 4f)
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
