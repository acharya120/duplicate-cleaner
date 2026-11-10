package com.duplicatecleaner.data.documents

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.webkit.MimeTypeMap
import androidx.documentfile.provider.DocumentFile
import com.duplicatecleaner.domain.model.DuplicateFile
import com.duplicatecleaner.domain.model.FileCategory
import com.duplicatecleaner.domain.model.FileSource
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.ensureActive

/**
 * Recursive SAF scan of user-selected folders. Directory listing uses DocumentsContract child queries
 * (one query per folder) instead of DocumentFile.listFiles(), which issues one query per child and is
 * far too slow on large trees. DocumentFile is used to validate the granted root.
 */
class DocumentScanner @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    data class Result(val files: List<DuplicateFile>, val inaccessibleFolders: List<String>)

    suspend fun scan(treeUris: Set<String>, onDiscovered: (Int) -> Unit = {}): Result {
        val files = ArrayList<DuplicateFile>()
        val bad = ArrayList<String>()
        for (raw in treeUris) {
            coroutineContext.ensureActive()
            val tree = Uri.parse(raw)
            val root = try { DocumentFile.fromTreeUri(context, tree) } catch (e: IllegalArgumentException) { null }
            if (root == null || !root.exists() || !root.canRead()) { bad += raw; continue }
            try {
                walk(tree, files, onDiscovered)
            } catch (e: SecurityException) {
                bad += raw
            }
        }
        onDiscovered(files.size)
        return Result(files, bad)
    }

    private suspend fun walk(tree: Uri, out: MutableList<DuplicateFile>, onDiscovered: (Int) -> Unit) {
        val rootId = DocumentsContract.getTreeDocumentId(tree)
        val stack = ArrayDeque<Pair<String, Int>>()
        val visited = HashSet<String>()
        stack.add(rootId to 0)
        val cols = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        )
        while (stack.isNotEmpty()) {
            coroutineContext.ensureActive()
            val (dirId, depth) = stack.removeLast()
            if (!visited.add(dirId) || depth > MAX_DEPTH) continue
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, dirId)
            try {
                context.contentResolver.query(children, cols, null, null, null)?.use { c ->
                    while (c.moveToNext()) {
                        val docId = c.getString(0) ?: continue
                        val name = c.getString(1) ?: continue
                        val mime = c.getString(2).orEmpty()
                        if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                            stack.add(docId to depth + 1)
                            continue
                        }
                        val ext = name.substringAfterLast('.', "").lowercase()
                        val size = if (c.isNull(3)) 0L else c.getLong(3)
                        if (ext !in DOCUMENT_EXTENSIONS || size <= 0) continue
                        out += DuplicateFile(
                            uri = DocumentsContract.buildDocumentUriUsingTree(tree, docId).toString(),
                            name = name,
                            mimeType = if (mime.isNotEmpty() && mime != "application/octet-stream") mime
                            else MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream",
                            sizeBytes = size,
                            lastModified = if (c.isNull(4)) 0L else c.getLong(4),
                            location = dirId.substringAfter(':', dirId),
                            category = FileCategory.DOCUMENT,
                            source = FileSource.SAF,
                            physicalKey = docId.lowercase(),
                        )
                        if (out.size % 100 == 0) onDiscovered(out.size)
                    }
                }
            } catch (e: SecurityException) {
                // folder became inaccessible: skip it, keep scanning the rest
            } catch (e: IllegalArgumentException) {
            } catch (e: UnsupportedOperationException) {
            } catch (e: java.io.FileNotFoundException) {
            }
        }
    }

    companion object {
        const val MAX_DEPTH = 40
        val DOCUMENT_EXTENSIONS = setOf(
            "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "csv", "rtf", "zip",
            "odt", "ods", "odp", "md", "epub", "7z", "rar", "tar", "gz", "json", "xml", "html", "htm",
        )
    }
}
