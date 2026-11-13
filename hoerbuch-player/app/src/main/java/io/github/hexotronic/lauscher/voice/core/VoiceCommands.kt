package io.github.hexotronic.lauscher.voice.core

import io.github.hexotronic.lauscher.analysis.core.MiniJson

/** What a spoken command does; [defaults] are the phrases it reacts to until the listener changes them. */
enum class VoiceAction(val label: String, val defaults: List<String>) {
    PLAY("Weiterspielen", listOf("weiter", "weiterspielen", "abspielen", "fortsetzen", "play")),
    PAUSE("Pause", listOf("pause", "stopp", "anhalten", "halt")),
    REWIND("Zurückspulen", listOf("zurück", "zurückspulen", "nochmal")),
    FORWARD("Vorspulen", listOf("vor", "vorspulen", "überspringen")),
    NEXT_CHAPTER("Nächstes Kapitel", listOf("nächstes kapitel", "kapitel weiter")),
    PREVIOUS_CHAPTER("Voriges Kapitel", listOf("voriges kapitel", "vorheriges kapitel", "kapitel zurück")),
    LOUDER("Lauter", listOf("lauter")),
    QUIETER("Leiser", listOf("leiser")),
    FASTER("Schneller", listOf("schneller")),
    SLOWER("Langsamer", listOf("langsamer")),
    SLEEP_TIMER("Schlaf-Timer", listOf("schlaftimer", "schlafen", "gute nacht")),
    SLEEP_OFF("Schlaf-Timer aus", listOf("timer aus", "wach bleiben")),
    BOOKMARK("Lesezeichen setzen", listOf("lesezeichen", "merken")),
    WHERE("Wo bin ich?", listOf("wo bin ich", "welches kapitel")),
    UNDO("Sprung rückgängig", listOf("rückgängig")),
}

/** One action as the listener set it up: on or off, and the phrases that trigger it. */
data class CommandSetting(val action: VoiceAction, val enabled: Boolean = true, val phrases: List<String> = action.defaults)

/**
 * The listener's commands. With [wakeWordEnabled] only what follows [wakeWord] counts
 * ("Lauscher, Pause"), so talking to someone or the book itself does not trigger anything.
 */
data class VoiceCommands(
    val wakeWordEnabled: Boolean = true,
    val wakeWord: String = "Lauscher",
    val commands: List<CommandSetting> = VoiceAction.entries.map { CommandSetting(it) },
) {
    fun setting(action: VoiceAction): CommandSetting = commands.firstOrNull { it.action == action } ?: CommandSetting(action)

    fun update(action: VoiceAction, change: (CommandSetting) -> CommandSetting): VoiceCommands =
        copy(commands = VoiceAction.entries.map { a -> if (a == action) change(setting(a)) else setting(a) })

    /**
     * Words handed to the speech recogniser as a hint ("prompt"), so it expects them: whisper
     * then writes "Lauscher, nächstes Kapitel" instead of something that merely sounds alike.
     */
    fun recognizerHint(): String {
        val prefix = if (wakeWordEnabled) "$wakeWord, " else ""
        return commands.filter { it.enabled }.mapNotNull { it.phrases.firstOrNull() }
            .joinToString(" ") { "$prefix${it.replaceFirstChar(Char::uppercaseChar)}." } + " 30 Sekunden zurück."
    }

    fun toJson(): String = buildString {
        append("{\"wakeWordEnabled\":").append(wakeWordEnabled)
        append(",\"wakeWord\":").append(quote(wakeWord))
        append(",\"commands\":[")
        commands.forEachIndexed { i, c ->
            if (i > 0) append(',')
            append("{\"action\":").append(quote(c.action.name)).append(",\"enabled\":").append(c.enabled).append(",\"phrases\":[")
            append(c.phrases.joinToString(",") { quote(it) }).append("]}")
        }
        append("]}")
    }

    companion object {
        /** The commands in [json]; unknown or broken parts fall back to the defaults. */
        fun fromJson(json: String?): VoiceCommands {
            val root = json?.let { runCatching { MiniJson.parse(it) }.getOrNull() } as? Map<*, *> ?: return VoiceCommands()
            val saved = (root["commands"] as? List<*>).orEmpty().mapNotNull { e ->
                val m = e as? Map<*, *> ?: return@mapNotNull null
                val action = VoiceAction.entries.firstOrNull { it.name == m["action"] } ?: return@mapNotNull null
                CommandSetting(
                    action,
                    m["enabled"] as? Boolean ?: true,
                    (m["phrases"] as? List<*>)?.mapNotNull { (it as? String)?.trim()?.takeIf(String::isNotEmpty) } ?: action.defaults,
                )
            }.associateBy { it.action }
            return VoiceCommands(
                wakeWordEnabled = root["wakeWordEnabled"] as? Boolean ?: true,
                wakeWord = (root["wakeWord"] as? String)?.trim()?.takeIf(String::isNotEmpty) ?: "Lauscher",
                commands = VoiceAction.entries.map { saved[it] ?: CommandSetting(it) },
            )
        }

        private fun quote(s: String) = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
    }
}

/** A recognised command: the [action], an amount if one was said ("30 Sekunden zurück"), and what was heard. */
data class Recognized(val action: VoiceAction, val seconds: Int?, val phrase: String, val heard: String)

/**
 * Finds the command in what the speech recogniser wrote. Tolerant of small mishearings
 * ("Lauscha", "nächstes Capitel"), but short words must match exactly, and without a wake word
 * the utterance has to consist mostly of the command (so a sentence that merely contains
 * "weiter" does nothing).
 */
object CommandMatcher {
    private val FILLERS = setOf("bitte", "mal", "doch", "jetzt", "hey", "hallo", "ok", "okay", "und", "noch", "einmal", "um", "den", "die", "das")
    private val UNITS = mapOf("sekunde" to 1, "sekunden" to 1, "minute" to 60, "minuten" to 60, "stunde" to 3600, "stunden" to 3600)

    fun match(heard: String, commands: VoiceCommands): Recognized? {
        val words = afterWakeWord(words(collapseRepeats(heard)), commands) ?: return null
        val content = words.filter { it !in FILLERS }
        if (content.isEmpty()) return null
        val amount = amountSeconds(content)
        var best: Pair<CommandSetting, String>? = null
        var bestScore = 0.0
        for (setting in commands.commands.filter { it.enabled }) {
            for (phrase in setting.phrases) {
                val p = words(phrase)
                // Compared with everything said, so own phrases may contain filler words ("moment mal").
                if (p.isEmpty() || p.size > words.size) continue
                val target = p.joinToString(" ")
                for (i in 0..words.size - p.size) {
                    val window = words.subList(i, i + p.size).joinToString(" ")
                    val score = similarity(window, target)
                    val needed = when {
                        target.length <= 4 -> 1.0
                        target.length <= 7 -> 0.8
                        else -> 0.75
                    }
                    // Higher score first; on a tie the longer phrase ("kapitel zurück" over "zurück").
                    if (score >= needed && (score > bestScore + 1e-9 || (score > bestScore - 1e-9 && target.length > (best?.second?.length ?: 0)))) {
                        best = setting to target
                        bestScore = score
                    }
                }
            }
        }
        val (setting, phrase) = best ?: return null
        if (!commands.wakeWordEnabled) {
            // Without a wake word only short utterances that are mostly the command count.
            val rest = content.count { it !in UNITS && number(it) == null } - words(phrase).count { it !in FILLERS }
            if (content.size > 6 || rest > 1) return null
        }
        return Recognized(setting.action, amount, phrase, heard.trim())
    }

    /**
     * Whether it is worth listening to [heard] more carefully when [match] found nothing: the wake
     * word was said (or, without one, the utterance was short enough to be a command).
     */
    fun worthASecondTry(heard: String, commands: VoiceCommands): Boolean {
        val words = afterWakeWord(words(collapseRepeats(heard)), commands) ?: return false
        return if (commands.wakeWordEnabled) true else words.size in 1..4
    }

    /** The words after the wake word (all words without one); null if the wake word is missing. */
    private fun afterWakeWord(words: List<String>, commands: VoiceCommands): List<String>? {
        if (!commands.wakeWordEnabled) return words
        val wake = words(commands.wakeWord)
        if (wake.isEmpty()) return null
        val at = (0..words.size - wake.size).firstOrNull { i ->
            similarity(words.subList(i, i + wake.size).joinToString(" "), wake.joinToString(" ")) >= 0.7
        } ?: return null
        return words.drop(at + wake.size)
    }

    /**
     * Drops sentences the recogniser repeated ("Lauscher, Pause. Lauscher, Pause. Lauscher,") –
     * with a prompt it sometimes keeps going in the prompt's pattern. A cut-off last sentence that
     * merely begins like an earlier one goes as well.
     */
    fun collapseRepeats(text: String): String {
        val sentences = text.trim().split(Regex("(?<=[.!?])\\s+")).filter { it.isNotBlank() }
        val seen = ArrayList<List<String>>()
        val kept = ArrayList<String>()
        for ((i, sentence) in sentences.withIndex()) {
            val w = words(sentence)
            if (w.isEmpty() || w in seen) continue
            val cutOff = i == sentences.lastIndex && !sentence.trimEnd().matches(Regex(".*[.!?]$"))
            if (cutOff && seen.any { it.size > w.size && it.subList(0, w.size) == w }) continue
            seen += w
            kept += sentence.trim()
        }
        return kept.joinToString(" ")
    }

    /** Lower case, without punctuation, ß as ss and umlauts folded, so spelling variants compare equal. */
    fun words(text: String): List<String> = text.lowercase()
        .replace("ß", "ss").replace("ä", "a").replace("ö", "o").replace("ü", "u")
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .trim().split(' ').filter { it.isNotEmpty() }

    /** 1 for equal strings, falling with the edit distance. */
    fun similarity(a: String, b: String): Double {
        if (a == b) return 1.0
        val n = maxOf(a.length, b.length)
        return if (n == 0) 1.0 else 1.0 - distance(a, b).toDouble() / n
    }

    private fun distance(a: String, b: String): Int {
        var prev = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val cur = IntArray(b.length + 1)
            cur[0] = i
            for (j in 1..b.length) {
                cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
            }
            prev = cur
        }
        return prev[b.length]
    }

    /**
     * An amount said with the command, in seconds: "30 sekunden" → 30, "zwei minuten" → 120;
     * a bare number counts in the unit the action expects (the caller decides).
     */
    fun amountSeconds(words: List<String>): Int? {
        for ((i, w) in words.withIndex()) {
            val n = number(w) ?: continue
            val unit = words.getOrNull(i + 1)?.let { UNITS[it] }
            return if (unit != null) n * unit else n
        }
        // "halbe stunde"
        if (words.windowed(2).any { it == listOf("halbe", "stunde") }) return 1800
        return null
    }

    /** Whether the amount in [words] came with a unit ("30 sekunden") or bare ("30"). */
    fun hasUnit(heard: String): Boolean = words(heard).any { it in UNITS }

    private val ONES = mapOf(
        "ein" to 1, "eine" to 1, "eins" to 1, "einen" to 1, "zwei" to 2, "drei" to 3, "vier" to 4, "funf" to 5, "fuenf" to 5,
        "sechs" to 6, "sieben" to 7, "acht" to 8, "neun" to 9,
    )
    private val TEENS = mapOf("zehn" to 10, "elf" to 11, "zwolf" to 12, "zwoelf" to 12, "sechzehn" to 16, "siebzehn" to 17)
    private val TENS = mapOf(
        "zwanzig" to 20, "dreissig" to 30, "vierzig" to 40, "funfzig" to 50, "fuenfzig" to 50, "sechzig" to 60,
        "siebzig" to 70, "achtzig" to 80, "neunzig" to 90, "hundert" to 100,
    )

    /** "30", "dreissig", "funfundvierzig", "dreizehn" → the number; null for other words. */
    fun number(word: String): Int? {
        word.toIntOrNull()?.let { return it }
        TEENS[word]?.let { return it }
        TENS[word]?.let { return it }
        ONES[word]?.let { return it }
        if (word.endsWith("zehn")) ONES[word.removeSuffix("zehn")]?.let { return 10 + it }
        val und = word.indexOf("und")
        if (und > 0) {
            val one = ONES[word.substring(0, und)] ?: (if (word.startsWith("ein")) 1 else null)
            val ten = TENS[word.substring(und + 3)]
            if (one != null && ten != null) return ten + one
        }
        return null
    }
}
