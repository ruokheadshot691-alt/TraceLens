package com.tracelens.app.index

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract

object SafScanner {
    class Found(val uri: String, val name: String, val mime: String?, val size: Long, val modified: Long)

    /** Telusuri folder (SAF tree) secara rekursif dan kumpulkan semua file gambar. */
    fun listImages(context: Context, tree: Uri, maxFiles: Int = 200_000): List<Found> {
        val out = ArrayList<Found>()
        val stack = ArrayDeque<String>()
        stack.addLast(DocumentsContract.getTreeDocumentId(tree))
        val cols = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        )
        while (stack.isNotEmpty() && out.size < maxFiles) {
            val parent = stack.removeLast()
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parent)
            try {
                context.contentResolver.query(children, cols, null, null, null)?.use { c ->
                    while (c.moveToNext()) {
                        val id = c.getString(0) ?: continue
                        val name = c.getString(1) ?: id
                        val mime = c.getString(2)
                        if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                            stack.addLast(id)
                        } else if (mime != null && mime.startsWith("image/")) {
                            out.add(
                                Found(
                                    DocumentsContract.buildDocumentUriUsingTree(tree, id).toString(),
                                    name, mime,
                                    if (c.isNull(3)) 0L else c.getLong(3),
                                    if (c.isNull(4)) 0L else c.getLong(4),
                                )
                            )
                        }
                    }
                }
            } catch (_: Exception) {
                // folder tidak bisa dibaca → lewati
            }
        }
        return out
    }
}
