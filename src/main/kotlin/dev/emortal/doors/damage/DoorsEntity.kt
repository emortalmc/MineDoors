package dev.emortal.doors.damage

enum class DoorsEntity(val messages: Set<Pair<String, String>>) {
    RUSH(setOf(
        "You died to Rush..." to "Pay attention to any cues that may hint at its arrival.",
        "You died to Rush again..." to "Pay attention to the lights. They are related to its arrival.",
    )),
    HIDE(setOf(
        "You died to Hide..." to "Minimize the time you spend hiding. You may need to hop in and out of a hiding spot repeatedly to avoid Hide."
    )),
    AMBUSH(setOf()),
    SCREECH(setOf()),
    HALT(setOf()),
    GLITCH(setOf()),
    SEEK(setOf()),
    EYES(setOf(
        "You died to the Eyes..." to "They don't like to be stared at.",
        "You died to the Eyes again..." to "They have a unique audio cue. Once you hear it, be prepared. Use their blue light to work out their location. Look away from them at all times. "
    ))
}