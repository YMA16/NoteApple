package com.yma.noteapple

import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Html
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object NoteExports {
    fun plainText(notes: List<NoteItem>): String = buildString {
        notes.filterNot { it.trashed }.forEachIndexed { index, note ->
            if (index > 0) append("\n\n────────────────────────\n\n")
            append(note.title.ifBlank { "פתק ללא כותרת" }).append('\n')
            if (note.locked) append("[פתק נעול]")
            else {
                append(Html.fromHtml(note.bodyHtml, Html.FROM_HTML_MODE_COMPACT).toString().trim())
                note.checklist.forEach { item ->
                    append('\n').append(if (item.checked) "☑ " else "☐ ").append(item.text)
                }
            }
            if (note.tags.isNotEmpty()) append("\n\n").append(note.tags.joinToString(" ") { "#$it" })
        }
    }

    fun markdown(notes: List<NoteItem>): String = buildString {
        notes.filterNot { it.trashed }.forEachIndexed { index, note ->
            if (index > 0) append("\n\n---\n\n")
            append("# ").append(note.title.ifBlank { "פתק ללא כותרת" }).append("\n\n")
            if (note.locked) append(">[פתק נעול]\n")
            else {
                val body = Html.fromHtml(note.bodyHtml, Html.FROM_HTML_MODE_COMPACT).toString()
                append(body.lines().joinToString("\n") { line ->
                    val trimmed = line.trim()
                    when {
                        trimmed.startsWith("• ") -> "- " + trimmed.removePrefix("• ")
                        trimmed.startsWith("☐ ") -> "- [ ] " + trimmed.removePrefix("☐ ")
                        trimmed.startsWith("☑ ") -> "- [x] " + trimmed.removePrefix("☑ ")
                        else -> trimmed
                    }
                })
                note.checklist.forEach { append("\n- [").append(if (it.checked) "x" else " ").append("] ").append(it.text) }
            }
            if (note.tags.isNotEmpty()) append("\n\n").append(note.tags.joinToString(" ") { "#$it" })
            append("\n")
        }
    }

    fun html(notes: List<NoteItem>): String = buildString {
        append("<!doctype html><html lang=\"he\"><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width\"><title>NoteApple</title>")
        append("<style>body{font-family:Arial,sans-serif;max-width:850px;margin:2rem auto;padding:0 1rem;line-height:1.65;color:#222}article{border-bottom:1px solid #ddd;padding:1rem 0}small{color:#777}</style>")
        append("</head><body>")
        notes.filterNot { it.trashed }.forEach { note ->
            append("<article dir=\"auto\"><h2>").append(escapeHtml(note.title)).append("</h2>")
            if (note.locked) append("<p>[פתק נעול]</p>") else append(note.bodyHtml)
            if (note.checklist.isNotEmpty()) {
                append("<ul>")
                note.checklist.forEach { append("<li>").append(if (it.checked) "✓ " else "□ ").append(escapeHtml(it.text)).append("</li>") }
                append("</ul>")
            }
            append("<small>").append(note.tags.joinToString(" ") { "#${escapeHtml(it)}" }).append("</small></article>")
        }
        append("</body></html>")
    }

    fun zip(context: android.content.Context, snapshot: BackupSnapshot): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry("NoteApple-Backup.json"))
            zip.write(BackupCodec.encode(context, snapshot).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("Notes.txt"))
            zip.write(plainText(snapshot.notes).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("Notes.md"))
            zip.write(markdown(snapshot.notes).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("Notes.html"))
            zip.write(html(snapshot.notes).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("README.txt"))
            zip.write("Restore NoteApple-Backup.json in NoteApple to recover notes and attachments.".toByteArray())
            zip.closeEntry()
        }
        return output.toByteArray()
    }

    fun pdf(notes: List<NoteItem>): ByteArray {
        val document = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842
        val margin = 42f
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.BLACK
            textSize = 21f
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
        }
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.DKGRAY
            textSize = 13f
        }
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.LTGRAY
            strokeWidth = 1f
        }
        var pageNumber = 1
        var page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
        var canvas = page.canvas
        var y = margin
        fun newPage() {
            document.finishPage(page)
            pageNumber++
            page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
            canvas = page.canvas
            y = margin
        }
        fun writeWrapped(text: String, paint: Paint, indent: Float = 0f, gap: Float = 0f) {
            val maxWidth = pageWidth - margin * 2 - indent
            val words = text.split(Regex("\\s+"))
            var line = ""
            for (word in words) {
                val candidate = if (line.isBlank()) word else "$line $word"
                if (paint.measureText(candidate) > maxWidth && line.isNotBlank()) {
                    if (y > pageHeight - margin) newPage()
                    canvas.drawText(line, margin + indent, y, paint)
                    y += paint.textSize * 1.55f
                    line = word
                } else line = candidate
            }
            if (line.isNotBlank()) {
                if (y > pageHeight - margin) newPage()
                canvas.drawText(line, margin + indent, y, paint)
                y += paint.textSize * 1.55f + gap
            }
        }
        notes.filterNot { it.trashed }.forEach { note ->
            if (y > pageHeight - 100) newPage()
            writeWrapped(note.title.ifBlank { "פתק ללא כותרת" }, titlePaint, gap = 7f)
            val text = if (note.locked) "[פתק נעול]" else
                Html.fromHtml(note.bodyHtml, Html.FROM_HTML_MODE_COMPACT).toString()
            text.lines().forEach { line ->
                if (line.isNotBlank()) writeWrapped(line, bodyPaint)
                else y += bodyPaint.textSize
            }
            note.checklist.forEach { item ->
                writeWrapped((if (item.checked) "☑ " else "☐ ") + item.text, bodyPaint, 12f)
            }
            if (note.tags.isNotEmpty()) writeWrapped(note.tags.joinToString(" ") { "#$it" }, bodyPaint, gap = 10f)
            canvas.drawLine(margin, y, pageWidth - margin, y, linePaint)
            y += 18f
        }
        document.finishPage(page)
        val output = ByteArrayOutputStream()
        document.writeTo(output)
        document.close()
        return output.toByteArray()
    }

    fun importMarkdown(text: String, folderId: String = DEFAULT_FOLDER_ID): NoteItem {
        val normalized = text.replace("\r\n", "\n")
        val title = normalized.lineSequence().firstOrNull()?.removePrefix("#")?.trim().orEmpty()
        val body = normalized.lineSequence().drop(if (normalized.startsWith("#")) 1 else 0).joinToString("\n").trim()
        return NoteItem(
            title = title.ifBlank { "פתק שיובא" },
            bodyHtml = "<p>" + escapeHtml(body).replace("\n", "<br>") + "</p>",
            folderId = folderId,
            tags = Regex("#([\\p{L}\\p{N}_-]+)").findAll(text).map { it.groupValues[1] }.distinct().toList()
        )
    }

    private fun escapeHtml(value: String): String =
        value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            .replace("\"", "&quot;").replace("'", "&#39;")
}
