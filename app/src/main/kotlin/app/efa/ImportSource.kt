package app.efa

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import efa.ImportLimits

/** A file or a piece of text that reached Spark (IM10). It is only data: it is shown in a preview and never acted on by itself (IM5). */
class Incoming(val name: String, val text: String)

object ImportSource {
    /** More than this many bytes is certainly over the limit, so reading stops there and the reader rejects it with its own message. */
    private const val READ_CEILING = ImportLimits.MAX_CHARS * 2 + 16

    fun isImportIntent(i: Intent?): Boolean = when (i?.action) {
        Intent.ACTION_VIEW -> i.data != null
        Intent.ACTION_SEND -> i.getParcelableExtra<Uri>(Intent.EXTRA_STREAM) != null || !i.getStringExtra(Intent.EXTRA_TEXT).isNullOrBlank()
        else -> false
    }

    /** Reads what an Open with or Share intent carries: a file stream, or plain text from an AI app. Call off the main thread. */
    fun read(ctx: Context, i: Intent): Incoming? {
        val uri: Uri? = if (i.action == Intent.ACTION_VIEW) i.data else i.getParcelableExtra(Intent.EXTRA_STREAM)
        if (uri != null) return read(ctx, uri)
        val text = i.getStringExtra(Intent.EXTRA_TEXT) ?: return null
        return Incoming("Shared text", text.take(ImportLimits.MAX_CHARS + 1))
    }

    fun read(ctx: Context, uri: Uri): Incoming? = try {
        val name = ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        } ?: uri.lastPathSegment ?: "File"
        val bytes = ctx.contentResolver.openInputStream(uri)?.use { s ->
            val out = java.io.ByteArrayOutputStream(); val buf = ByteArray(16 * 1024); var total = 0
            while (total < READ_CEILING) { val n = s.read(buf, 0, minOf(buf.size, READ_CEILING - total)); if (n < 0) break; out.write(buf, 0, n); total += n }
            out.toByteArray()
        }
        bytes?.let { Incoming(name.take(80), String(it, Charsets.UTF_8)) }
    } catch (e: Exception) { null }
}
