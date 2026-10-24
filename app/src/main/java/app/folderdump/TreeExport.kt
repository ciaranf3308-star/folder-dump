package app.folderdump

/**
 * Folder Dump — the verbatim tree export.
 *
 * This file has NO Android dependency on purpose: the export contract can
 * be unit-tested on the JVM, and the Android side ([SafTree]) only has to
 * convert a SAF tree into [TreeEntry] values. Nothing here renames,
 * filters, collapses, or invents anything.
 *
 * The contract, in full:
 * - Every child of the picked folder appears, at every depth, with its
 *   exact name, including its extension. No name is ever normalised,
 *   stripped, or title-cased.
 * - No exclusions: entries whose names start with '.' are listed like
 *   everything else.
 * - No collapsing: a directory whose name ends in '.m3u' is descended
 *   into like any other directory, and every file inside it is listed.
 * - No invented names: loose files sitting directly in the picked
 *   folder are listed under that folder, whose real name is the root
 *   line. The only invented text in the whole file is the header
 *   (source / date / counts) and the "(unreadable)" marker, both of
 *   which are clearly separate from entry names — see below.
 * - Empty folders are listed (a directory line with nothing under it).
 * - Children are sorted A–Z ignoring case, with ties broken by the
 *   exact name, so a repeat export of an unchanged folder is identical
 *   apart from the header date.
 *
 * A directory SAF could not list is marked "(unreadable)" on its own
 * line instead of being silently dropped. That marker is a suffix on
 * the directory line, never a fake file entry, and it is the only case
 * where a line is not a pure name.
 */

/** One entry in the real tree. [children] is empty for files. */
data class TreeEntry(
    val name: String,
    val isDirectory: Boolean,
    val children: List<TreeEntry> = emptyList(),
    /** True when the directory's children could not be listed. */
    val unreadable: Boolean = false,
)

/** Counts of entries below the root. The root itself is not counted. */
data class TreeStats(val folders: Int, val files: Int) {
    val total: Int get() = folders + files
}

/** Sorts children the way [renderTree] does, so callers can agree. */
fun sortChildren(children: List<TreeEntry>): List<TreeEntry> =
    children.sortedWith(compareBy({ it.name.lowercase() }, { it.name }))

/** Counts folders and files below [root], at every depth. */
fun countTree(root: TreeEntry): TreeStats {
    var folders = 0
    var files = 0
    val stack = ArrayDeque<TreeEntry>()
    stack.addAll(root.children)
    while (stack.isNotEmpty()) {
        val entry = stack.removeLast()
        if (entry.isDirectory) {
            folders++
            stack.addAll(entry.children)
        } else {
            files++
        }
    }
    return TreeStats(folders, files)
}

/**
 * Renders the whole export as plain text.
 *
 * [rootName] is the picked folder's real name and becomes the root
 * line, e.g. `ROMs/`. [exportedLabel] is a human-readable date/time
 * supplied by the caller (the header is the only part that changes
 * between identical exports). [root] holds the picked folder's
 * children; its own [TreeEntry.name] is ignored so a nameless SAF
 * root cannot leak a wrong name into the tree.
 *
 * Example output:
 *
 *     Folder export
 *     Source: ROMs
 *     Exported: 2026-10-24 18:50
 *     Folders: 2
 *     Files: 3
 *
 *     ROMs/
 *       .hidden.txt
 *       psx/
 *         Metal Gear Solid.m3u/
 *           Metal Gear Solid Disc 1.chd
 *           Metal Gear Solid Disc 2.chd
 *         Tekken 3.chd
 */
fun renderTree(rootName: String, root: TreeEntry, exportedLabel: String): String {
    val stats = countTree(root)
    return buildString {
        append("Folder export\n")
        append("Source: ").append(rootName).append('\n')
        append("Exported: ").append(exportedLabel).append('\n')
        append("Folders: ").append(stats.folders).append('\n')
        append("Files: ").append(stats.files).append('\n')
        append('\n')
        append(rootName).append("/\n")
        for (child in sortChildren(root.children)) {
            appendEntry(child, depth = 1)
        }
    }
}

private fun StringBuilder.appendEntry(entry: TreeEntry, depth: Int) {
    repeat(depth) { append("  ") }
    append(entry.name)
    if (entry.isDirectory) {
        append('/')
        if (entry.unreadable) append(" (unreadable)")
        append('\n')
        for (child in sortChildren(entry.children)) {
            appendEntry(child, depth + 1)
        }
    } else {
        append('\n')
    }
}
