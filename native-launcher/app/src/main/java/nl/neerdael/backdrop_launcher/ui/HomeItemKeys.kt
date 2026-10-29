package nl.neerdael.backdrop_launcher.ui

import nl.neerdael.backdrop_launcher.core.AppRow

/** Scene v1 has names/packages, so distinguish repeated identities by occurrence within each list. */
internal class HomeItemKeys(sourceRows: List<AppRow>) {
    val rows = occurrenceKeys(sourceRows.map { it.name })
    val apps = sourceRows.map { row -> occurrenceKeys(row.apps.map { it.packageName }) }
}

private fun occurrenceKeys(identities: List<String>): List<String> {
    val counts = mutableMapOf<String, Int>()
    return identities.map { identity ->
        val occurrence = counts[identity] ?: 0
        counts[identity] = occurrence + 1
        "$occurrence:$identity"
    }
}
