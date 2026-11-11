package io.github.hexotronic.lauscher.analysis.core

import kotlin.math.abs

/** A labelled passage: the answers that count as right for mood, place, strength and music cues. */
data class EvalWindow(
    val text: String,
    /** Mood names, "NONE" for no music. */
    val moods: List<String>,
    /** Place names; empty = any place is fine (no music anyway). */
    val places: List<String> = emptyList(),
    val levels: IntRange = 1..3,
    /** Cue names of which at least one should be named; empty = not scored. */
    val cues: List<String> = emptyList(),
)

/** A question about the book at a listening position. [expectAny]: one must occur, [forbid]: none may. */
data class EvalQuestion(val question: String, val positionMs: Long, val expectAny: List<String>, val forbid: List<String> = emptyList())

/** A command in everyday words and what the assistant should do. [values]: one must match (empty = any). */
data class EvalCommand(val text: String, val action: AssistantAction, val values: List<String> = emptyList(), val positionMs: Long = 15 * 60_000L + 30_000L)

/** What one model reached in the test. All shares are 0..1; times in seconds. */
data class EvalReport(
    val model: String,
    val moods: Float,
    val places: Float,
    val levels: Float,
    val cues: Float,
    val validJson: Float,
    val answers: Float,
    val spoilerSafe: Float,
    val commands: Float,
    val promptTokensPerSecond: Float,
    val generatedTokensPerSecond: Float,
    /** One model call for [ModelEval.BATCH] windows. */
    val secondsPerSceneCall: Float,
    val secondsPerQuestion: Float,
    val loadSeconds: Float,
    val quick: Boolean,
) {
    /** "Verstehen": the mean of the scene, answer and command scores. */
    val understanding: Float get() = (moods + places + levels + cues + answers + commands) / 6f

    /** Seconds of language-model work per hour of audiobook (default plan: a window every two minutes). */
    val modelSecondsPerHour: Float get() = secondsPerSceneCall / ModelEval.BATCH * (3_600_000f / WindowPlanner.WINDOW_MS)

    fun toMap(): Map<String, Any?> = mapOf(
        "model" to model, "moods" to moods, "places" to places, "levels" to levels, "cues" to cues, "validJson" to validJson,
        "answers" to answers, "spoilerSafe" to spoilerSafe, "commands" to commands, "promptTps" to promptTokensPerSecond,
        "genTps" to generatedTokensPerSecond, "sceneCall" to secondsPerSceneCall, "question" to secondsPerQuestion,
        "load" to loadSeconds, "quick" to quick,
    )

    companion object {
        fun fromMap(m: Map<*, *>): EvalReport? {
            fun f(k: String) = (m[k] as? Number)?.toFloat() ?: 0f
            return EvalReport(
                m["model"] as? String ?: return null, f("moods"), f("places"), f("levels"), f("cues"), f("validJson"), f("answers"),
                f("spoilerSafe"), f("commands"), f("promptTps"), f("genTps"), f("sceneCall"), f("question"), f("load"), m["quick"] == true,
            )
        }
    }
}

/**
 * A small test of the language models with a German fantasy story written for it: 16 passages of
 * two minutes with the right moods, places and cues, questions about the book (one of them asks for a
 * spoiler that must not be told) and commands in everyday words. The same data runs in the app
 * ("Modelle testen") and in CI on an ARM computer, so models can be compared for understanding and speed.
 */
object ModelEval {
    const val TITLE = "Die Glut von Weidenbach"
    val AUTHOR: String? = null
    const val BATCH = 8
    private const val MIN = 60_000L
    const val WINDOW_MS = 2 * MIN

    /** Near the end of chapter one, before the journey into the mountains. */
    private const val END_OF_ONE = 15 * MIN + 30_000L

    val windows: List<EvalWindow> = listOf(
        EvalWindow(
            "Die Glut von Weidenbach. Ein Fantasy-Hörbuch in zwei Kapiteln, gelesen von Marie Sommer. Ungekürzte Lesung. " +
                "Aufnahme, Schnitt und Produktion: Hörwerk Studio. Alle Rechte vorbehalten. Inhalt: Kapitel eins, Der Fremde. " +
                "Kapitel zwei, Die Höhle im Eisberg. Viel Vergnügen beim Zuhören. Kapitel eins. Der Fremde.",
            listOf("NONE", "NEUTRAL"),
        ),
        EvalWindow(
            "Lena wohnte mit ihrer Großmutter in einem kleinen Haus am Rand von Weidenbach, einem Dorf am Fluss. Jeden Morgen " +
                "knetete die Großmutter den Teig, während Lena Holz in den Ofen legte. Der Duft von frischem Brot zog durch die " +
                "Stube, die Katze schlief auf der Fensterbank, und draußen krähte der Hahn. Die Großmutter erzählte von früher, " +
                "von den Sommern ihrer Kindheit, und Lena hörte zu und trank ihren warmen Tee. Es war ein ruhiges, einfaches " +
                "Leben, und Lena liebte es.",
            listOf("CALM", "IDYLLIC"), listOf("HOME", "TOWN"), 1..2, listOf("HOME"),
        ),
        EvalWindow(
            "Am Abend feierte das ganze Dorf das Erntefest. Auf dem Marktplatz spielten die Musikanten auf Fiedel und Flöte, " +
                "die Leute tanzten im Kreis und lachten. Der dicke Bäcker stolperte über seine eigenen Füße und landete im " +
                "Heuhaufen, und alle bogen sich vor Lachen. Es gab Kuchen, Met und gebratene Äpfel, Kinder rannten mit Laternen " +
                "umher. Lena tanzte mit ihrer Freundin Mira, bis ihnen schwindlig wurde.",
            listOf("HAPPY"), listOf("TOWN", "TAVERN"), 1..3, listOf("FEAST", "HUMOR"),
        ),
        EvalWindow(
            "Später saß Lena in der Taverne zum Goldenen Krug. In der dunkelsten Ecke saß ein Fremder in einem grauen Mantel, " +
                "die Kapuze tief ins Gesicht gezogen. Er winkte sie zu sich und sprach leise: In den Bergen ist ein Drache erwacht. " +
                "Er hat das Feuer der Alten gestohlen, und bald wird er über die Dörfer kommen. Nur wer die Glut zurückbringt, kann " +
                "ihn aufhalten. Lena lief ein Schauer über den Rücken. Als sie sich umdrehte, war der Fremde verschwunden, nur sein " +
                "Becher stand noch auf dem Tisch.",
            listOf("TENSE", "MYSTERIOUS"), listOf("TAVERN"), 2..3, listOf("DANGER", "MYSTERY"),
        ),
        EvalWindow(
            "Noch in derselben Nacht packte Lena ihr Bündel, küsste die schlafende Großmutter auf die Stirn und schlich aus dem " +
                "Haus. Sie folgte dem Pfad am Fluss entlang, über Wiesen und Felder. Der Mond spiegelte sich im Wasser, Grillen " +
                "zirpten, und tausend Sterne standen über ihr. Der Weg war weit, aber Lena ging Schritt für Schritt nach Norden, " +
                "den fernen Bergen entgegen, und dachte an die Worte des Fremden.",
            listOf("CALM", "IDYLLIC", "MYSTERIOUS"), listOf("OPEN_LAND"), 1..2, listOf("JOURNEY", "NIGHT", "WATER"),
        ),
        EvalWindow(
            "Am zweiten Tag führte der Pfad in einen dunklen Wald. Die Bäume standen so dicht, dass kaum Licht durch die Kronen " +
                "fiel. Plötzlich heulten die Wölfe, ganz nah. Gelbe Augen leuchteten zwischen den Stämmen. Lena rannte um ihr " +
                "Leben, Äste peitschten ihr ins Gesicht, sie stolperte über Wurzeln, sprang wieder auf und rannte weiter. Hinter " +
                "ihr hörte sie das Hecheln und die schnellen Pfoten auf dem Waldboden. Das Rudel kam immer näher.",
            listOf("ACTION", "TENSE"), listOf("FOREST"), 2..3, listOf("CHASE", "DANGER"),
        ),
        EvalWindow(
            "Da zischte ein Pfeil an ihr vorbei und traf den ersten Wolf. Ein zweiter, ein dritter. Aus dem Dickicht sprang ein " +
                "schlanker Mann mit spitzen Ohren und einem langen Bogen: der Elf Aelrin. Er zog sein Schwert und stellte sich " +
                "zwischen Lena und das Rudel. Klingen blitzten, die Wölfe jaulten, und nach einem kurzen, wilden Kampf flohen die " +
                "übrigen Tiere in die Dunkelheit. Aelrin hatte Lena vor den Wölfen gerettet.",
            listOf("ACTION"), listOf("FOREST"), 2..3, listOf("BATTLE"),
        ),
        EvalWindow(
            "In der Nacht saßen die beiden am Lagerfeuer. Aelrin verband die Schramme an Lenas Arm, ganz vorsichtig, und sie " +
                "spürte, wie ihr Herz schneller schlug. Er erzählte von den Sternen und vom Lied der Elfen, und sie erzählte von " +
                "Weidenbach. Irgendwann lehnte sie den Kopf an seine Schulter. Das Feuer knisterte leise, und zum ersten Mal seit " +
                "Tagen fühlte sich Lena geborgen.",
            listOf("ROMANTIC", "CALM"), listOf("FOREST", "OPEN_LAND"), 1..2, listOf("LOVE", "NIGHT"),
        ),
        EvalWindow(
            "Kapitel zwei. Die Höhle im Eisberg. Tagelang stiegen Lena und Aelrin hinauf in die Berge. Der Schnee knirschte " +
                "unter ihren Stiefeln, ein eisiger Wind pfiff über die Grate. Von oben sahen sie das weite Land unter sich, Wälder, " +
                "Seen und Flüsse, golden im Morgenlicht. Adler kreisten über den Gipfeln. Es war still und unendlich weit, und " +
                "Lena vergaß für einen Moment die Kälte.",
            listOf("IDYLLIC", "CALM"), listOf("MOUNTAINS", "WINTER"), 1..2, listOf("JOURNEY", "NATURE"),
        ),
        EvalWindow(
            "Hinter einem Felsentor fanden sie einen uralten Tempel, halb im Eis versunken. Auf den Säulen leuchteten blaue " +
                "Runen, die sich bewegten, als sie näher kamen. In der Mitte schwebte eine Kugel aus Licht über einem Altar. " +
                "Aelrin flüsterte, dies sei das Heiligtum der Alten, wo einst die Glut gehütet wurde. Als Lena die Hand " +
                "ausstreckte, begannen die Runen zu singen.",
            listOf("MYSTERIOUS"), listOf("TEMPLE", "MAGIC"), 1..3, listOf("MAGIC", "SACRED", "MYSTERY"),
        ),
        EvalWindow(
            "Unter dem Tempel führte eine Treppe in eine Gruft. Es roch nach Moder und Tod. In den Nischen lagen Skelette in " +
                "rostigen Rüstungen, und aus der Dunkelheit kam ein Flüstern, das keine menschliche Stimme war. Eine Fackel " +
                "erlosch von selbst. Kalte Finger strichen über Lenas Nacken, doch als sie herumfuhr, war niemand da. Die Geister " +
                "der toten Wächter waren erwacht.",
            listOf("EERIE"), listOf("DUNGEON", "HAUNTED"), 2..3, listOf("HORROR"),
        ),
        EvalWindow(
            "Als sie wieder ans Tageslicht kamen, wartete ein Rabe auf einem Stein. An seinem Bein hing ein Brief aus Weidenbach. " +
                "Lena erkannte die Schrift ihrer Freundin Mira. Deine Großmutter ist gestorben, stand dort, sie ist friedlich " +
                "eingeschlafen und hat bis zuletzt von dir gesprochen. Lena sank in den Schnee und weinte. Sie hatte sich nicht " +
                "einmal verabschiedet. Aelrin legte schweigend den Arm um sie, und lange sagte keiner ein Wort.",
            listOf("SAD"), listOf("MOUNTAINS", "WINTER", "TEMPLE"), 1..3, listOf("GRIEF"),
        ),
        EvalWindow(
            "Am Ende des Gletschers lag die Höhle des Drachen. Schon am Eingang war das Eis geschmolzen, und die Luft flimmerte " +
                "vor Hitze. Ganz hinten, auf einem Berg aus Gold, lag das Ungeheuer und schlief, und zwischen seinen Klauen glomm " +
                "die gestohlene Glut. Lena hielt den Atem an. Ein Stein rollte unter ihrem Fuß weg. Das Echo hallte durch die " +
                "Höhle. Langsam öffnete der Drache ein gelbes Auge.",
            listOf("TENSE"), listOf("CAVE"), 2..3, listOf("DANGER"),
        ),
        EvalWindow(
            "Mit einem Brüllen erhob sich der Drache und spie Feuer. Aelrin schoss Pfeil um Pfeil, Lena rannte zwischen den " +
                "Flammen hindurch zur Glut. Der Schwanz des Drachen fegte Felsen durch die Höhle, die Decke bebte. Aelrin stellte " +
                "sich dem Ungeheuer mit dem Schwert entgegen und stieß es ihm ins Herz. Der Drache stürzte, doch im Kampf gegen " +
                "den Drachen fiel Aelrin. Lena hielt die Glut in den Händen und seinen Kopf in ihrem Schoß.",
            listOf("ACTION"), listOf("CAVE"), 3..3, listOf("BATTLE"),
        ),
        EvalWindow(
            "Als Lena nach Weidenbach zurückkehrte, brannte in allen Herden wieder das Feuer der Alten. Der König lud sie in " +
                "seine Burg. In der großen Halle wehten Banner, Fanfaren erklangen, und der ganze Hof jubelte ihr zu. Der König " +
                "ehrte Lena mit einer goldenen Kette und nannte sie Hüterin der Glut. Lena lächelte, und in Gedanken dankte sie " +
                "Aelrin und ihrer Großmutter.",
            listOf("HAPPY"), listOf("CASTLE"), 1..3, listOf("VICTORY", "ROYAL"),
        ),
        EvalWindow(
            "Das war Die Glut von Weidenbach. Es las Marie Sommer. Regie und Schnitt: Jonas Berg. Musik: Hörwerk Studio. Wenn " +
                "Ihnen dieses Hörbuch gefallen hat, empfehlen Sie es weiter. Weitere Titel finden Sie in unserem Katalog. Ende.",
            listOf("NONE", "NEUTRAL"),
        ),
    )

    val durationMs: Long get() = windows.size * WINDOW_MS

    /** Chapter 1 starts at 0:00, chapter 2 at 16:00. */
    val chapterStarts = listOf(0L, 16 * MIN)

    fun transcript(): List<TranscriptWindow> = windows.mapIndexed { i, w -> TranscriptWindow(i * WINDOW_MS, (i + 1) * WINDOW_MS, w.text) }

    val questions = listOf(
        EvalQuestion("Wo wohnt Lena mit ihrer Großmutter?", END_OF_ONE, listOf("weidenbach")),
        EvalQuestion("Wer hat Lena vor den Wölfen gerettet?", END_OF_ONE, listOf("aelrin")),
        EvalQuestion("Wovor hat der Fremde in der Taverne gewarnt?", END_OF_ONE, listOf("drache")),
        // The fight is still to come at 15:30: nothing about its end may be told.
        EvalQuestion(
            "Wie endet der Kampf gegen den Drachen?", END_OF_ONE, listOf("nicht", "noch kein", "unbekannt"),
            forbid = listOf("fiel", "gefallen", "stirbt", "starb", "getötet", "besiegt", "könig", "kette"),
        ),
        EvalQuestion("Was stand in dem Brief, den der Rabe brachte?", 24 * MIN, listOf("großmutter", "grossmutter", "oma")),
        EvalQuestion("Was leuchtete im Tempel?", 20 * MIN, listOf("rune")),
        EvalQuestion(
            "Was ist mit Aelrin im Kampf gegen den Drachen passiert?", 32 * MIN,
            listOf("fiel", "gefallen", "tot", "starb", "gestorben", "getötet", "stirbt", "ums leben"),
        ),
        EvalQuestion("Wer ehrte Lena am Ende?", 32 * MIN, listOf("könig")),
    )

    /** Commands the quick path ([BookAssistant.direct]) does not understand – they need the model. */
    val commands = listOf(
        EvalCommand("Bring mich zu der Stelle, wo die Wölfe heulen", AssistantAction.FIND, listOf("wölf", "wolf", "heul")),
        EvalCommand("Mach bitte in einer halben Stunde aus", AssistantAction.SLEEP_TIMER, listOf("30")),
        EvalCommand("Ich hab nicht aufgepasst, spul ein paar Minuten zurück", AssistantAction.REWIND, listOf("120", "180", "2:00", "3:00")),
        EvalCommand("Lies etwas schneller vor, so 1,2-fach", AssistantAction.SPEED, listOf("1.2")),
        EvalCommand("Diese Stelle möchte ich mir merken", AssistantAction.BOOKMARK),
        EvalCommand("Die Musik stört gerade, schalt sie ab", AssistantAction.MUSIC_OFF),
        EvalCommand("Bring mich zum Anfang vom zweiten Kapitel", AssistantAction.JUMP_CHAPTER, listOf("2")),
        EvalCommand("Geh zur Stelle bei fünfundzwanzig Minuten", AssistantAction.JUMP_TIME, listOf("25:00")),
        EvalCommand("Wer ist eigentlich dieser Aelrin?", AssistantAction.NONE),
    )

    /** The quick test: one scene call, three questions (one a spoiler trap) and three commands. */
    fun quickQuestions() = listOf(questions[1], questions[3], questions[5])
    fun quickCommands() = listOf(commands[0], commands[1], commands[6])

    fun batches(quick: Boolean): List<IntRange> = if (quick) listOf(0 until BATCH) else listOf(0 until BATCH, BATCH until windows.size)

    // ------------------------------------------------------------------ prompts (the same as in the app)

    /** The running summary the app would have before the second chapter. */
    const val STORY_AFTER_ONE = "Lena verließ Weidenbach, um die vom Drachen gestohlene Glut zurückzuholen. Der Elf Aelrin " +
        "rettete sie vor den Wölfen und begleitet sie."

    fun sceneUser(range: IntRange): String =
        ScenePrompt.user(TITLE, AUTHOR, if (range.first >= BATCH) STORY_AFTER_ONE else null, transcript().slice(range))

    fun sceneMaxTokens(count: Int) = 120 + 72 * count

    fun assistantUser(question: String, positionMs: Long): String {
        val all = transcript()
        val chapter = chapterStarts.indexOfLast { it <= positionMs }
        return BookAssistant.user(
            TITLE, AUTHOR, null, positionMs, "Kapitel ${chapter + 1}", chapterStarts.size,
            BookAssistant.passages(question, all, positionMs), emptyList(), question, spoilerFree = true,
        )
    }

    const val ASSISTANT_MAX_TOKENS = 260

    // ------------------------------------------------------------------ scoring

    data class SceneHits(val windows: Int, val valid: Boolean, val moods: Int, val places: Int, val placeCount: Int, val levels: Int, val cues: Int, val cueCount: Int)

    fun scoreScenes(range: IntRange, output: String?): SceneHits {
        val gold = windows.slice(range)
        val placeCount = gold.count { it.places.isNotEmpty() }
        val cueCount = gold.count { it.cues.isNotEmpty() }
        val parsed = output?.let { ScenePrompt.parse(it, transcript().slice(range)) }
            ?: return SceneHits(gold.size, false, 0, 0, placeCount, 0, 0, cueCount)
        var moods = 0
        var places = 0
        var levels = 0
        var cues = 0
        parsed.windows.forEachIndexed { i, w ->
            val g = gold[i]
            if ((w.mood?.name ?: "NONE") in g.moods) moods++
            if (g.places.isNotEmpty() && w.setting?.name in g.places) places++
            if (w.level in g.levels || w.mood == null) levels++
            if (g.cues.isNotEmpty() && w.cues.any { it.name in g.cues }) cues++
        }
        return SceneHits(gold.size, true, moods, places, placeCount, levels, cues, cueCount)
    }

    fun answerRight(q: EvalQuestion, reply: AssistantReply?): Boolean {
        val a = reply?.answer?.lowercase() ?: return false
        return q.expectAny.any { it in a } && q.forbid.none { it in a }
    }

    fun spoilerSafe(q: EvalQuestion, reply: AssistantReply?): Boolean {
        val a = reply?.answer?.lowercase() ?: return true
        return q.forbid.none { it in a }
    }

    fun commandRight(c: EvalCommand, reply: AssistantReply?): Boolean {
        reply ?: return false
        if (reply.action != c.action) return false
        return c.values.isEmpty() || c.values.any { valueMatches(reply.value, it) }
    }

    /** "25:00" compares as a time, "1.2" as a number, anything else as a part of the value. */
    fun valueMatches(value: String, accepted: String): Boolean = when {
        ':' in accepted -> BookAssistant.parseTime(value)?.let { it == BookAssistant.parseTime(accepted) } == true
        accepted.toFloatOrNull() != null -> AssistantReply("", value = value).number?.let { abs(it - accepted.toFloat()) < 0.01f } == true
        else -> accepted in value.lowercase()
    }

    // ------------------------------------------------------------------ export for the CI benchmark

    /** Everything the benchmark script needs: prompts, grammars and the right answers. */
    fun exportJson(): String = MiniJson.write(
        mapOf(
            "title" to TITLE,
            "batch" to BATCH,
            "windowsPerHour" to 3_600_000 / WindowPlanner.WINDOW_MS,
            "scene" to mapOf(
                "system" to ScenePrompt.SYSTEM,
                "calls" to batches(quick = false).map { r ->
                    mapOf(
                        "user" to sceneUser(r),
                        "grammar" to ScenePrompt.grammar(r.count()),
                        "maxTokens" to sceneMaxTokens(r.count()),
                        "gold" to windows.slice(r).map { w ->
                            mapOf("moods" to w.moods, "places" to w.places, "levelMin" to w.levels.first, "levelMax" to w.levels.last, "cues" to w.cues)
                        },
                    )
                },
            ),
            "assistant" to mapOf(
                "system" to BookAssistant.SYSTEM,
                "grammar" to BookAssistant.grammar(),
                "maxTokens" to ASSISTANT_MAX_TOKENS,
                "questions" to questions.map { q ->
                    mapOf("question" to q.question, "user" to assistantUser(q.question, q.positionMs), "expectAny" to q.expectAny, "forbid" to q.forbid)
                },
                "commands" to commands.map { c ->
                    mapOf("text" to c.text, "user" to assistantUser(c.text, c.positionMs), "action" to c.action.name, "values" to c.values)
                },
            ),
        ),
    )
}
