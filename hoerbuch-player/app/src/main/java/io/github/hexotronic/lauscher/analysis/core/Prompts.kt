package io.github.hexotronic.lauscher.analysis.core

import io.github.hexotronic.lauscher.music.synth.Mood
import io.github.hexotronic.lauscher.music.synth.MusicCue
import io.github.hexotronic.lauscher.music.synth.Pace
import io.github.hexotronic.lauscher.music.synth.Setting

/** The instructions ("system prompts") the on-device language model gets for each of its jobs. */
enum class PromptKind(val label: String, val purpose: String, val answerForm: String) {
    SCENES(
        "Szenen-Analyse",
        "Liest je zwei Minuten Hörbuch und bestimmt Stimmung, Stärke, Ort, Tempo und Musik-Merkmale – danach wählt die App " +
            "die Hintergrundmusik. Schreibt nebenbei die kurze Zusammenfassung „Was bisher geschah“ fort.",
        "Fest vorgegeben (JSON): je Abschnitt mood, level, place, pace, cues, title und dazu story. Andere Werte als die " +
            "erlaubten kann das Modell gar nicht schreiben.",
    ),
    SCENE_CUT(
        "Szenenwechsel",
        "Hört an der Grenze zweier Szenen kurz nach und entscheidet, ob die Stelle noch zur alten Szene (A) oder schon zur " +
            "neuen (B) gehört – so wechselt die Musik auf etwa 15 Sekunden genau.",
        "Fest vorgegeben: nur „A“ oder „B“.",
    ),
    RECAP(
        "Rückblick",
        "Schreibt „Was bisher geschah“ für die Rückkehr nach einer Pause – nur bis zur Hörstelle.",
        "Freier Text.",
    ),
    ASSISTANT(
        "Frag das Buch",
        "Beantwortet Fragen zum Buch aus den mitgeschriebenen Stellen und übersetzt Befehle in Aktionen des Players.",
        "Fest vorgegeben (JSON): action (aus der Liste), value, answer.",
    ),
    ;

    companion object {
        fun parse(name: String?) = entries.firstOrNull { it.name == name }
    }
}

/** What the listener changed: a whole own text per kind, and/or an addition that is appended. */
data class PromptSettings(
    val custom: Map<PromptKind, String> = emptyMap(),
    val extra: Map<PromptKind, String> = emptyMap(),
) {
    fun isCustom(kind: PromptKind) = !custom[kind].isNullOrBlank()
    fun hasExtra(kind: PromptKind) = !extra[kind].isNullOrBlank()
    fun isChanged(kind: PromptKind) = isCustom(kind) || hasExtra(kind)

    /** True when any instruction differs from the built-in one (the model test marks its results so). */
    val changed: Boolean get() = PromptKind.entries.any { isChanged(it) }

    fun withCustom(kind: PromptKind, text: String?) =
        copy(custom = if (text.isNullOrBlank() || text.trim() == Prompts.default(kind).trim()) custom - kind else custom + (kind to text.trim()))

    fun withExtra(kind: PromptKind, text: String?) = copy(extra = if (text.isNullOrBlank()) extra - kind else extra + (kind to text.trim()))

    fun reset(kind: PromptKind) = copy(custom = custom - kind, extra = extra - kind)

    fun toJson(): String = MiniJson.write(
        mapOf(
            "custom" to custom.mapKeys { it.key.name },
            "extra" to extra.mapKeys { it.key.name },
        ),
    )

    companion object {
        fun fromJson(json: String?): PromptSettings {
            val root = runCatching { MiniJson.parse(json ?: return PromptSettings()) }.getOrNull() as? Map<*, *> ?: return PromptSettings()
            fun read(key: String) = (root[key] as? Map<*, *>).orEmpty().mapNotNull { (k, v) ->
                val kind = PromptKind.parse(k as? String) ?: return@mapNotNull null
                (v as? String)?.takeIf { it.isNotBlank() }?.let { kind to it }
            }.toMap()
            return PromptSettings(read("custom"), read("extra"))
        }
    }
}

/** The built-in instructions, the ones in use, checks for edited ones and examples of what the model gets. */
object Prompts {
    /** Longer instructions leave less room for the book's text in the model's 4096 tokens. */
    const val MAX_CHARS = 8000

    fun default(kind: PromptKind): String = when (kind) {
        PromptKind.SCENES -> ScenePrompt.SYSTEM
        PromptKind.SCENE_CUT -> ScenePrompt.CUT_SYSTEM
        PromptKind.RECAP -> ScenePrompt.RECAP_SYSTEM
        PromptKind.ASSISTANT -> BookAssistant.SYSTEM
    }

    /** The instructions in use: the own text or the built-in one, with the listener's addition at the end. */
    fun effective(kind: PromptKind, s: PromptSettings): String {
        val base = s.custom[kind]?.trim()?.takeIf { it.isNotEmpty() } ?: default(kind)
        val extra = s.extra[kind]?.trim().orEmpty()
        return if (extra.isEmpty()) base else "$base\n\n$EXTRA_HEADER\n$extra"
    }

    const val EXTRA_HEADER = "Additional instructions from the listener (follow them):"

    /** The values each instruction should name, so the model knows what it may answer (grouped, German names). */
    fun requiredNames(kind: PromptKind): List<Pair<String, List<String>>> = when (kind) {
        PromptKind.SCENES -> listOf(
            "Stimmungen" to listOf("NONE") + Mood.entries.map { it.name },
            "Orte" to Setting.entries.map { it.name },
            "Tempo" to Pace.entries.map { it.name },
            "Musik-Merkmale" to MusicCue.entries.map { it.name },
        )
        PromptKind.ASSISTANT -> listOf("Aktionen" to AssistantAction.entries.map { it.name })
        else -> emptyList()
    }

    /** Hints about an edited text: names of allowed values it no longer mentions, too long, empty. */
    fun warnings(kind: PromptKind, text: String): List<String> {
        if (text.isBlank()) return listOf("Leer – es gilt der eingebaute Text.")
        val out = ArrayList<String>()
        for ((group, names) in requiredNames(kind)) {
            val missing = names.filter { !Regex("\\b${Regex.escape(it)}\\b").containsMatchIn(text) }
            if (missing.isNotEmpty()) {
                out += "$group fehlen: ${missing.take(6).joinToString()}${if (missing.size > 6) " …" else ""} – das Modell kennt sie dann nicht " +
                    "und wählt sie kaum (erlaubt bleiben sie)."
            }
        }
        if (text.length > MAX_CHARS) out += "Sehr lang (${text.length} Zeichen) – bleibt weniger Platz für den Text des Buchs."
        return out
    }

    /** What the model gets besides the instructions, shown with the built-in test story. */
    fun example(kind: PromptKind): String = when (kind) {
        PromptKind.SCENES -> ModelEval.sceneUser(3..4)
        PromptKind.SCENE_CUT -> {
            val t = ModelEval.transcript()
            val a = WindowMood(t[2].startMs, t[2].endMs, Mood.HAPPY, 2, "Erntefest", SceneMerger.SOURCE_LLM, Setting.TOWN)
            val b = WindowMood(t[3].startMs, t[3].endMs, Mood.TENSE, 2, "Der Fremde", SceneMerger.SOURCE_LLM, Setting.TAVERN)
            ScenePrompt.cutUser(a, b, t[2].text, t[3].text, t[3].text.take(220))
        }
        PromptKind.RECAP -> ScenePrompt.recapUser(ModelEval.TITLE, ModelEval.STORY_AFTER_ONE, ModelEval.transcript().subList(5, 8), 1500)
        PromptKind.ASSISTANT -> ModelEval.assistantUser("Wer hat Lena vor den Wölfen gerettet?", 15 * 60_000L + 30_000L)
    }

    /** A typical answer of the model to [example]. */
    fun exampleAnswer(kind: PromptKind): String = when (kind) {
        PromptKind.SCENES -> """{"windows":[{"w":0,"mood":"TENSE","level":2,"place":"TAVERN","pace":"MEDIUM","cues":["DANGER","MYSTERY"],""" +
            """"title":"Der Fremde"},{"w":1,"mood":"CALM","level":1,"place":"OPEN_LAND","pace":"SLOW","cues":["JOURNEY","NIGHT"],""" +
            """"title":"Aufbruch bei Nacht"}],"story":"Lena bricht nachts auf, um die gestohlene Glut zurückzuholen."}"""
        PromptKind.SCENE_CUT -> "B"
        PromptKind.RECAP -> "Lena hat ihr Dorf Weidenbach verlassen, um die gestohlene Glut zurückzuholen. Im Wald rettete sie der Elf Aelrin …"
        PromptKind.ASSISTANT -> """{"action":"NONE","value":"","answer":"Der Elf Aelrin hat Lena mit Pfeil und Schwert vor den Wölfen gerettet."}"""
    }
}
