package nl.neerdael.backdrop_launcher.ui

/** Keep public release notes readable on a remote-controlled text panel, without loading web content. */
internal fun readableNoteLines(markdown: String): List<String> = markdown.lines().mapNotNull { source ->
    if (source.trimStart().startsWith("![") || source.trimStart().startsWith("<!--")) return@mapNotNull null
    source.replace(Regex("<[^>]+>"), "")
        .replace(Regex("\\[([^]\\n]+)]\\([^)]*\\)"), "$1")
        .replace(Regex("^#{1,6}\\s*"), "")
        .replace("**", "").replace("__", "").replace("`", "")
        .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
}
