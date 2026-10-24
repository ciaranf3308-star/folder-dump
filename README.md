# Folder Dump

A tiny standalone Android app: point it at a folder on your device and
it creates a `.txt` export of every child folder and file inside it.

No internet, no account, no database, no analytics. Read-only — it
never renames, moves, or deletes anything in the folder you pick.

## Use it

1. Open the app, tap **Pick a folder** (Android's system folder picker).
2. It walks the folder recursively and shows the export with folder
   and file counts.
3. Tap **Save as .txt** (system save picker — you choose where the
   file goes), **Share .txt**, or **Copy all**.

The grant is remembered, so **Scan the same folder again** re-exports
without re-picking. If Android revokes the grant, the app tells you to
pick the folder again instead of silently producing a partial export.

## The export format

Plain text, 2 spaces of indent per level, folders with a trailing `/`:

```
Folder export
Source: ROMs
Exported: 2026-10-24 18:50
Folders: 2
Files: 3

ROMs/
  .hidden.txt
  psx/
    Metal Gear Solid.m3u/
      Metal Gear Solid Disc 1.chd
      Metal Gear Solid Disc 2.chd
    Tekken 3.chd
```

The preview in the app is exactly the text that gets saved, shared,
or copied — there is no shorter second version.

## The verbatim contract

This app exists because "helpful" exporters kept getting this wrong.
Its rules are deliberately boring:

- **Every entry, exact name.** Extensions, brackets, spaces, and
  unicode are kept as-is. Nothing is normalised or title-cased.
- **No exclusions.** Files and folders starting with `.` are listed
  like everything else.
- **No collapsing.** A directory ending in `.m3u` is descended into
  like any other directory; every file inside it is listed.
- **No invented names.** Loose files at the top of the picked folder
  stay under that folder's real name, which is the root line.
- **Empty folders are shown** — a directory line with nothing under it.
- **Sorting is A–Z ignoring case**, ties broken by the exact name,
  so a repeat export of an unchanged folder is identical apart from
  the header date.

The only text in the file that is not a real name is the header
(source / date / counts) and a `(unreadable)` suffix on a directory
line when Android would not let the app list that directory's
contents. An unreadable folder is marked, never silently dropped.

## Permissions, honestly

The app declares **no storage permission at all**. Folder access comes
entirely from the Storage Access Framework: you pick the folder, and
Android grants this app access to that tree only. The export leaves
the device only when you tap Save (to a location you choose) or
Share — the app itself sends nothing anywhere.

One Android limitation to know: the system folder picker refuses some
protected roots (for example the top-level Downloads folder on
Android 11+). If it refuses a folder, pick a subfolder inside it
instead — that is Android's rule, not the app hiding anything.

## Build it

Local Gradle does not work in the sandbox this was developed in, so
GitHub Actions is the build route (same pattern as the Crystal Nova
Manager project):

```
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

That runs the unit tests in `app/src/test` (which pin the verbatim
contract: dot-files, `.m3u` directories, empty folders, loose root
files, sorting, unreadable markers) and produces a debug APK under
`app/build/outputs/apk/debug/`, which can be sideloaded on a phone or
handheld. No signing key is baked in — a release keystore can be
added later if a signed release is ever wanted.

## Layout

- `app/src/main/java/app/folderdump/TreeExport.kt` — pure export
  logic, no Android dependency, unit-tested on the JVM.
- `app/src/main/java/app/folderdump/SafTree.kt` — converts a SAF
  `DocumentFile` tree into that logic. Read-only.
- `app/src/main/java/app/folderdump/MainActivity.kt` — the single
  Compose screen: pick, scan, preview, save, share, copy.
- `app/src/test/java/app/folderdump/TreeExportTest.kt` — the contract tests.
