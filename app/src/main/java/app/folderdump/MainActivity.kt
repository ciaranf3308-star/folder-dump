package app.folderdump

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Folder Dump — one screen, one job.
 *
 * 1. Pick a folder (system folder picker, Storage Access Framework).
 *    The grant is persisted, so "Scan again" works without re-picking.
 * 2. The app walks that folder recursively and renders the verbatim
 *    tree from [renderTree] — every child folder and file, exact names,
 *    dot-files included, `.m3u` directories descended into, empty
 *    folders shown. Read-only throughout.
 * 3. Save the result as a `.txt` (system save picker), share it, or
 *    copy the whole text. The preview shows the exact text that will
 *    be saved/shared/copied — no separate, shorter version.
 *
 * No internet, no account, no database, no analytics. Nothing in the
 * picked folder is ever modified.
 */
class MainActivity : ComponentActivity() {

    companion object {
        private const val PREFS = "folder_dump"
        private const val KEY_TREE_URI = "tree_uri"
        private const val KEY_ROOT_NAME = "root_name"
    }

    private var treeUriString by mutableStateOf<String?>(null)
    private var rootName by mutableStateOf<String?>(null)
    private var exportText by mutableStateOf("")
    private var statsLine by mutableStateOf("No folder scanned yet.")
    private var status by mutableStateOf("")
    private var scanning by mutableStateOf(false)
    private var scannedCount by mutableStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        treeUriString = prefs.getString(KEY_TREE_URI, null)
        rootName = prefs.getString(KEY_ROOT_NAME, null)

        setContent {
            MaterialTheme {
                val context = LocalContext.current
                val scope = rememberCoroutineScope()
                val scrollState = rememberScrollState()
                val previewHorizontalScroll = rememberScrollState()

                val pickFolder = rememberLauncherForActivityResult(
                    ActivityResultContracts.OpenDocumentTree(),
                ) { uri ->
                    if (uri == null) {
                        status = "No folder picked."
                        return@rememberLauncherForActivityResult
                    }
                    try {
                        contentResolver.takePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION,
                        )
                    } catch (_: SecurityException) {
                        // Some providers refuse persistable grants; the
                        // one-shot grant still lets this scan run.
                    }
                    val root = DocumentFile.fromTreeUri(context, uri)
                    val name = root?.name?.takeIf { it.isNotBlank() } ?: "folder"
                    treeUriString = uri.toString()
                    rootName = name
                    prefs.edit()
                        .putString(KEY_TREE_URI, uri.toString())
                        .putString(KEY_ROOT_NAME, name)
                        .apply()
                    scanFolder(context, uri, name, scope)
                }

                val saveFile = rememberLauncherForActivityResult(
                    ActivityResultContracts.CreateDocument("text/plain"),
                ) { uri ->
                    if (uri == null) {
                        status = "Save cancelled — nothing was written."
                        return@rememberLauncherForActivityResult
                    }
                    scope.launch(Dispatchers.IO) {
                        try {
                            contentResolver.openOutputStream(uri)?.use { out ->
                                out.write(exportText.toByteArray(Charsets.UTF_8))
                            }
                            withContext(Dispatchers.Main) {
                                status = "Saved. The .txt is where you chose to put it."
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                status = "Could not save: ${e.message ?: "unknown error"}"
                            }
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("Folder Dump", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        "Pick a folder. Get a .txt of every child folder " +
                            "and file inside it — exact names, nothing skipped, nothing renamed.",
                        style = MaterialTheme.typography.bodyMedium,
                    )

                    Text(
                        text = rootName?.let { "Folder: $it" } ?: "No folder picked yet.",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(statsLine, style = MaterialTheme.typography.bodyMedium)
                    if (status.isNotBlank()) {
                        Text(status, style = MaterialTheme.typography.bodyMedium)
                    }
                    if (scanning) {
                        Text("Scanning… $scannedCount entries so far.")
                    }

                    Button(
                        onClick = { pickFolder.launch(null) },
                        enabled = !scanning,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Pick a folder") }

                    OutlinedButton(
                        onClick = {
                            val uriString = treeUriString
                            val name = rootName
                            if (uriString != null && name != null) {
                                scanFolder(context, Uri.parse(uriString), name, scope)
                            } else {
                                status = "No saved folder yet — pick one first."
                            }
                        },
                        enabled = !scanning && treeUriString != null,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Scan the same folder again") }

                    OutlinedButton(
                        onClick = {
                            if (exportText.isBlank()) {
                                status = "Nothing to save yet — scan a folder first."
                            } else {
                                saveFile.launch(suggestedFileName(rootName))
                            }
                        },
                        enabled = !scanning,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Save as .txt") }

                    OutlinedButton(
                        onClick = {
                            if (exportText.isBlank()) {
                                status = "Nothing to share yet — scan a folder first."
                            } else {
                                shareExport(context, exportText)
                                status = "Share sheet opened."
                            }
                        },
                        enabled = !scanning,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Share .txt") }

                    OutlinedButton(
                        onClick = {
                            if (exportText.isBlank()) {
                                status = "Nothing to copy yet — scan a folder first."
                            } else {
                                val clipboard = context.getSystemService(
                                    Context.CLIPBOARD_SERVICE,
                                ) as ClipboardManager
                                clipboard.setPrimaryClip(
                                    ClipData.newPlainText("Folder export", exportText),
                                )
                                status = "Copied — the whole export is on the clipboard."
                            }
                        },
                        enabled = !scanning,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Copy all") }

                    if (exportText.isNotBlank()) {
                        Text("Preview — exactly what gets saved, shared, or copied:")
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 480.dp)
                                .verticalScroll(rememberScrollState())
                                .horizontalScroll(previewHorizontalScroll),
                        ) {
                            SelectionContainer {
                                Text(
                                    exportText,
                                    fontFamily = FontFamily.Monospace,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun scanFolder(
        context: Context,
        uri: Uri,
        name: String,
        scope: CoroutineScope,
    ) {
        scanning = true
        scannedCount = 0
        status = ""
        scope.launch(Dispatchers.IO) {
            try {
                val root = DocumentFile.fromTreeUri(context, uri)
                if (root == null || !root.exists()) {
                    withContext(Dispatchers.Main) {
                        scanning = false
                        status = "That folder is no longer available. Pick it again " +
                            "to renew access."
                    }
                    return@launch
                }
                val tree = documentFileToTree(root) { count ->
                    scope.launch(Dispatchers.Main) { scannedCount = count }
                }
                val stats = countTree(tree)
                val exported = LocalDateTime.now()
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                val text = renderTree(name, tree, exported)
                withContext(Dispatchers.Main) {
                    exportText = text
                    statsLine = "${stats.folders} folders, ${stats.files} files."
                    status = "Scan complete."
                    scanning = false
                }
            } catch (e: SecurityException) {
                withContext(Dispatchers.Main) {
                    scanning = false
                    status = "Access was revoked for that folder. Pick it again."
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    scanning = false
                    status = "Scan failed: ${e.message ?: "unknown error"}"
                }
            }
        }
    }

    private fun shareExport(context: Context, text: String) {
        val file = File(context.cacheDir, "folder-export.txt")
        file.writeText(text, Charsets.UTF_8)
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Folder export")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share folder export"))
    }

    /**
     * Suggested save filename only — this is never a tree entry, so
     * stripping filename-illegal characters here does not touch the
     * verbatim names inside the export itself.
     */
    private fun suggestedFileName(root: String?): String {
        val base = root
            ?.filter { it !in "/\\:*?\"<>|" }
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: "folder"
        return "$base-folder-export.txt"
    }
}
