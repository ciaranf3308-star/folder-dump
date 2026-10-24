package app.folderdump

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Verbatim-contract tests. Every one of these is a past exporter bug
 * in another app: a test here fails if the contract ever regresses.
 */
class TreeExportTest {

    private fun file(name: String) = TreeEntry(name, isDirectory = false)

    private fun dir(name: String, vararg children: TreeEntry) =
        TreeEntry(name, isDirectory = true, children = children.toList())

    @Test
    fun dotFilesAreIncluded() {
        val root = dir(
            "ROMs",
            file(".hidden.txt"),
            file("game.chd"),
        )
        val text = renderTree("ROMs", root, "2026-10-24 18:50")
        assertEquals(
            "Folder export\n" +
                "Source: ROMs\n" +
                "Exported: 2026-10-24 18:50\n" +
                "Folders: 0\n" +
                "Files: 2\n" +
                "\n" +
                "ROMs/\n" +
                "  .hidden.txt\n" +
                "  game.chd\n",
            text,
        )
    }

    @Test
    fun m3uDirectoriesAreDescendedIntoNotCollapsed() {
        val root = dir(
            "psx",
            dir(
                "Metal Gear Solid.m3u",
                file("Metal Gear Solid Disc 1.chd"),
                file("Metal Gear Solid Disc 2.chd"),
            ),
            file("Tekken 3.chd"),
        )
        val text = renderTree("psx", root, "2026-10-24 18:50")
        assertEquals(
            "Folder export\n" +
                "Source: psx\n" +
                "Exported: 2026-10-24 18:50\n" +
                "Folders: 1\n" +
                "Files: 3\n" +
                "\n" +
                "psx/\n" +
                "  Metal Gear Solid.m3u/\n" +
                "    Metal Gear Solid Disc 1.chd\n" +
                "    Metal Gear Solid Disc 2.chd\n" +
                "  Tekken 3.chd\n",
            text,
        )
    }

    @Test
    fun emptyFoldersAreShownAndLooseRootFilesStayUnderRoot() {
        val root = dir(
            "ROMs",
            dir("Empty Folder"),
            file("loose.bin"),
        )
        val text = renderTree("ROMs", root, "2026-10-24 18:50")
        assertEquals(
            "Folder export\n" +
                "Source: ROMs\n" +
                "Exported: 2026-10-24 18:50\n" +
                "Folders: 1\n" +
                "Files: 1\n" +
                "\n" +
                "ROMs/\n" +
                "  Empty Folder/\n" +
                "  loose.bin\n",
            text,
        )
    }

    @Test
    fun namesAreVerbatimAndChildrenSortCaseInsensitively() {
        val root = dir(
            "ROMs",
            file("Zebra (USA).chd"),
            file("apple.bin"),
            file("Banjo-Kazooie [U].n64"),
            file("Pokémon — Yellow Version.gbc"),
        )
        val text = renderTree("ROMs", root, "2026-10-24 18:50")
        // A–Z ignoring case: apple, Banjo, Pokémon, Zebra. Names
        // themselves are untouched — brackets, dash, and é survive.
        assertEquals(
            "Folder export\n" +
                "Source: ROMs\n" +
                "Exported: 2026-10-24 18:50\n" +
                "Folders: 0\n" +
                "Files: 4\n" +
                "\n" +
                "ROMs/\n" +
                "  apple.bin\n" +
                "  Banjo-Kazooie [U].n64\n" +
                "  Pokémon — Yellow Version.gbc\n" +
                "  Zebra (USA).chd\n",
            text,
        )
    }

    @Test
    fun unreadableDirectoryIsMarkedNotDropped() {
        val root = dir(
            "ROMs",
            TreeEntry("Locked", isDirectory = true, unreadable = true),
        )
        val text = renderTree("ROMs", root, "2026-10-24 18:50")
        assertEquals(
            "Folder export\n" +
                "Source: ROMs\n" +
                "Exported: 2026-10-24 18:50\n" +
                "Folders: 1\n" +
                "Files: 0\n" +
                "\n" +
                "ROMs/\n" +
                "  Locked/ (unreadable)\n",
            text,
        )
    }

    @Test
    fun nestedDepthIndentsTwoSpacesPerLevel() {
        val root = dir(
            "a",
            dir("b", dir("c", file("deep.txt"))),
        )
        val text = renderTree("a", root, "2026-10-24 18:50")
        assertEquals(
            "Folder export\n" +
                "Source: a\n" +
                "Exported: 2026-10-24 18:50\n" +
                "Folders: 2\n" +
                "Files: 1\n" +
                "\n" +
                "a/\n" +
                "  b/\n" +
                "    c/\n" +
                "      deep.txt\n",
            text,
        )
    }
}
