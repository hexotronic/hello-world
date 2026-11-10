package io.github.hexotronic.lauscher.music.synth

/**
 * What the music of a scene should carry beyond its mood and place – the AI names up to three of
 * these per scene, and every piece of the library has its own, so the piece that fits the moment
 * (a fight, a chase, a funeral, a feast …) is preferred. [hint] explains the cue to the language model.
 */
enum class MusicCue(val label: String, val hint: String) {
    BATTLE("Kampf", "fight, duel, battle, war"),
    CHASE("Verfolgung", "chase, escape, pursuit, running"),
    DANGER("Gefahr", "threat, ambush, monster approaching"),
    HORROR("Grauen", "ghosts, undead, gore, nightmare"),
    MYSTERY("Geheimnis", "secret, riddle, discovery, ruins"),
    MAGIC("Magie", "spells, wizards, fairies, wonders"),
    GRIEF("Trauer", "death, loss, funeral, despair"),
    LOVE("Liebe", "romance, tenderness, longing"),
    FEAST("Fest", "celebration, tavern, dance, market"),
    VICTORY("Sieg", "triumph, rescue, heroic moment"),
    JOURNEY("Reise", "travel, road, setting out, adventure"),
    NATURE("Natur", "forest, fields, animals, landscape"),
    NIGHT("Nacht", "night, darkness, sleep, dreams, stars"),
    WATER("Wasser", "sea, river, ship, rain, harbour"),
    ROYAL("Hof", "king, queen, court, palace, ceremony"),
    SACRED("Heilig", "temple, prayer, priests, gods"),
    HOME("Geborgenheit", "home, family, fireplace, safety"),
    HUMOR("Heiter", "comedy, mischief, playful moments"),
    ;

    companion object {
        fun parse(name: String?): MusicCue? = entries.firstOrNull { it.name.equals(name?.trim(), ignoreCase = true) }

        /** "BATTLE,CHASE" as stored in the database. */
        fun parseList(text: String?): List<MusicCue> = text.orEmpty().split(',').mapNotNull { parse(it) }.distinct()

        fun toText(cues: List<MusicCue>): String? = cues.takeIf { it.isNotEmpty() }?.joinToString(",") { it.name }
    }
}

/** How fast the music of a scene should move. */
enum class Pace(val label: String) {
    SLOW("langsam"),
    MEDIUM("mittel"),
    FAST("schnell"),
    ;

    companion object {
        fun parse(name: String?): Pace? = entries.firstOrNull { it.name.equals(name?.trim(), ignoreCase = true) }

        /** Without an answer of the model: from the mood and how strong it is. */
        fun of(mood: Mood?, level: Int): Pace = when {
            mood == null -> SLOW
            mood == Mood.ACTION || level >= 3 -> FAST
            mood in setOf(Mood.CALM, Mood.IDYLLIC, Mood.SAD, Mood.ROMANTIC) && level <= 2 -> SLOW
            else -> MEDIUM
        }
    }
}
