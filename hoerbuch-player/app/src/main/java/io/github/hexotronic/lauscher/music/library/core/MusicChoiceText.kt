package io.github.hexotronic.lauscher.music.library.core

import io.github.hexotronic.lauscher.music.synth.MusicChoice
import io.github.hexotronic.lauscher.music.synth.MusicFollow
import io.github.hexotronic.lauscher.music.synth.describeScene

/** Puts what the music mixer chose into words for the AI log. */
object MusicChoiceText {
    /**
     * One line about [choice]. [useLibrary]: the listener chose the music library as source;
     * [installed]: how many of its pieces are on the device.
     */
    fun describe(choice: MusicChoice, follow: MusicFollow, useLibrary: Boolean, installed: Int): String {
        val spec = choice.spec
        val scene = spec?.let { describeScene(it) } ?: ""
        val track = choice.trackId?.let { MusicCatalog.byId(it) }
        val name = track?.let { "„${it.title}“ (${it.composer})" } ?: choice.trackId?.let { "„$it“" }
        val mood = spec?.mood
        val fit = if (track != null && mood != null && spec.trackId == null) {
            TrackPicker.describeFit(track, mood, spec.setting, spec.intensity, spec.cues, spec.pace)
        } else {
            null
        }
        return when (choice.what) {
            MusicChoice.What.PIECE ->
                if (spec?.trackId != null) "Hörprobe: $name" else "$scene → spielt $name" + (fit?.let { " – $it" } ?: "")
            MusicChoice.What.NEXT_PIECE -> "Nächstes Stück für dieselbe Szene: $name" + (fit?.let { " – $it" } ?: "")
            MusicChoice.What.KEPT -> when {
                name == null -> "$scene → die Musik läuft weiter (gleiches Gefühl, Auswahl „${follow.label}“)"
                follow == MusicFollow.CALM -> "$scene → $name läuft weiter: passt auch zur neuen Szene (Auswahl „Ruhig“)"
                else -> "$scene → $name läuft weiter: gleiches Gefühl, gleicher Ort, kaum andere Spannung"
            }
            MusicChoice.What.SYNTH -> "$scene → Synthesizer spielt „${mood?.label}“ – " + when {
                !useLibrary -> "Klangquelle „Erzeugt“"
                installed == 0 -> "noch keine Stücke der Musikbibliothek geladen"
                else -> "keins der $installed geladenen Stücke passt gut genug"
            }
            MusicChoice.What.OFF -> when {
                spec?.atmosphere != null -> "Nur Geräusche: „${spec.atmosphere.label}“"
                else -> "Musik aus – an dieser Stelle gibt es (noch) keine Szene mit Musik"
            }
        }
    }
}
