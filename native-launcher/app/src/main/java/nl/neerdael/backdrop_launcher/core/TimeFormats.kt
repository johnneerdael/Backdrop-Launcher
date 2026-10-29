package nl.neerdael.backdrop_launcher.core

/** Literal text in SimpleDateFormat patterns must not wake a clock every second. */
fun needsSecondTicks(vararg patterns: String): Boolean = patterns.any { pattern ->
    var quoted = false
    var index = 0
    var seconds = false
    while (index < pattern.length) {
        val char = pattern[index]
        if (char == '\'') {
            if (index + 1 < pattern.length && pattern[index + 1] == '\'') index++
            else quoted = !quoted
        } else if (!quoted && (char == 's' || char == 'S')) seconds = true
        index++
    }
    seconds
}
