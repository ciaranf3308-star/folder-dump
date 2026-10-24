package app.folderdump

import androidx.documentfile.provider.DocumentFile

/**
 * Converts a SAF tree ([DocumentFile]) into a [TreeEntry] tree for
 * [renderTree]. This is the only place that touches Android storage,
 * and it is strictly READ-ONLY: it only calls [DocumentFile.getName],
 * [DocumentFile.isDirectory], and [DocumentFile.listFiles]. It never
 * renames, moves, deletes, or writes anything inside the picked folder.
 *
 * SAF quirks handled here, and nowhere else:
 * - An entry with no name (SAF returned null) cannot be shown
 *   verbatim, so it is shown as "<unnamed>" — the only invented name
 *   in the tree, and only for entries SAF itself could not name.
 * - A directory whose listing throws (grant revoked mid-scan, for
 *   example) is kept in the tree with [TreeEntry.unreadable] set, so
 *   the export never silently drops a folder it could not read.
 * - Depth is capped at 64. Real folder trees are far shallower; the
 *   cap only stops a pathological tree from recursing forever.
 *   A folder hit by the cap is marked unreadable.
 *
 * [onProgress] is called with the running entry count roughly every
 * 50 entries, so the UI can show "Scanning… N entries" without being
 * spammed once per file.
 */
private const val MAX_DEPTH = 64
private const val PROGRESS_EVERY = 50

fun documentFileToTree(
    root: DocumentFile,
    onProgress: (Int) -> Unit = {},
): TreeEntry {
    var seen = 0
    fun tick() {
        seen++
        if (seen % PROGRESS_EVERY == 0) onProgress(seen)
    }

    fun walk(dir: DocumentFile, depth: Int): TreeEntry {
        val children = mutableListOf<TreeEntry>()
        var unreadable = false
        if (depth >= MAX_DEPTH) {
            unreadable = true
        } else {
            try {
                for (child in dir.listFiles()) {
                    tick()
                    val name = child.name ?: "<unnamed>"
                    if (child.isDirectory) {
                        children.add(walk(child, depth + 1).copy(name = name))
                    } else {
                        children.add(TreeEntry(name = name, isDirectory = false))
                    }
                }
            } catch (_: SecurityException) {
                unreadable = true
            } catch (_: IllegalStateException) {
                unreadable = true
            }
        }
        // The root's own name is replaced by the caller (see renderTree),
        // so walk() keeps whatever name SAF gave it here; only the
        // children list and the unreadable flag matter for the root.
        return TreeEntry(
            name = dir.name ?: "<unnamed>",
            isDirectory = true,
            children = children,
            unreadable = unreadable,
        )
    }

    val tree = walk(root, depth = 0)
    onProgress(seen)
    return tree
}
