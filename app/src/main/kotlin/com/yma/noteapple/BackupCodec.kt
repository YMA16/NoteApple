package com.yma.noteapple

import android.content.Context
import android.text.Html
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

object BackupCodec {
    const val FORMAT = "com.yma.noteapple.full-backup"
    const val VERSION = 3
    private const val MAX_ATTACHMENT_BYTES = 20 * 1024 * 1024

    fun encode(context: Context, snapshot: BackupSnapshot): String {
        val folderArray = JSONArray()
        snapshot.folders.forEach { folder ->
            folderArray.put(
                JSONObject().put("id", folder.id).put("name", folder.name)
                    .put("parentId", folder.parentId).put("color", folder.color)
                    .put("emoji", folder.emoji).put("smartQuery", folder.smartQuery)
            )
        }

        val noteArray = JSONArray()
        snapshot.notes.forEach { note ->
            val attachmentArray = JSONArray()
            note.attachments.forEach { attachment ->
                val file = File(attachment.path)
                val data = if (file.isFile && file.length() <= MAX_ATTACHMENT_BYTES) {
                    runCatching { Base64.encodeToString(file.readBytes(), Base64.NO_WRAP) }.getOrNull()
                } else null
                attachmentArray.put(
                    JSONObject().put("id", attachment.id).put("name", attachment.name)
                        .put("mimeType", attachment.mimeType).put("size", attachment.size)
                        .put("dataBase64", data)
                )
            }
            noteArray.put(
                JSONObject()
                    .put("id", note.id).put("title", note.title).put("bodyHtml", note.bodyHtml)
                    .put("folderId", note.folderId).put("tags", stringsJson(note.tags))
                    .put("checklist", checklistJson(note.checklist)).put("attachments", attachmentArray)
                    .put("pinned", note.pinned).put("locked", note.locked).put("trashed", note.trashed)
                    .put("trashedAt", note.trashedAt).put("createdAt", note.createdAt)
                    .put("updatedAt", note.updatedAt).put("background", note.background)
                    .put("lockSalt", note.lockSalt).put("lockIv", note.lockIv)
                    .put("lockCiphertext", note.lockCiphertext)
            )
        }
        return JSONObject()
            .put("format", FORMAT)
            .put("schemaVersion", VERSION)
            .put("app", "NoteApple for Android")
            .put("exportedAt", System.currentTimeMillis())
            .put("folders", folderArray)
            .put("notes", noteArray)
            .toString(2)
    }

    fun decode(context: Context, json: String): BackupSnapshot {
        require(json.isNotBlank()) { "קובץ הגיבוי ריק" }
        val parsed = JSONTokener(json).nextValue()
        val root = when (parsed) {
            is JSONObject -> parsed
            is JSONArray -> JSONObject().put("notes", parsed).put("version", 1)
            else -> throw IllegalArgumentException("מבנה JSON לא מוכר")
        }
        val version = root.optInt("schemaVersion", root.optInt("version", 1))
        require(version in 1..VERSION) { "גרסת גיבוי לא נתמכת: $version" }
        val noteArray = root.optJSONArray("notes")
            ?: throw IllegalArgumentException("לא נמצא שדה notes מסוג מערך בקובץ")
        require(noteArray.length() <= 100_000) { "הגיבוי מכיל יותר מדי פתקים" }

        val folders = mutableListOf<NoteFolder>()
        val folderArray = root.optJSONArray("folders") ?: JSONArray()
        for (index in 0 until folderArray.length()) {
            val item = folderArray.optJSONObject(index) ?: continue
            val name = item.optString("name").trim()
            if (name.isNotEmpty()) folders += NoteFolder(
                id = item.optString("id").ifBlank { UUID.randomUUID().toString() },
                name = name,
                parentId = item.optString("parentId").takeIf(String::isNotBlank),
                color = item.optString("color", "yellow"),
                emoji = item.optString("emoji", "📁"),
                smartQuery = item.optString("smartQuery")
            )
        }
        if (folders.none { it.id == DEFAULT_FOLDER_ID }) {
            folders.add(0, NoteFolder(id = DEFAULT_FOLDER_ID, name = "פתקים"))
        }
        val folderByName = folders.associate { it.name to it.id }.toMutableMap()
        val attachmentDir = File(context.filesDir, "attachments").apply { mkdirs() }
        val notes = mutableListOf<NoteItem>()

        for (index in 0 until noteArray.length()) {
            val item = noteArray.optJSONObject(index)
                ?: throw IllegalArgumentException("הפתק מספר §{index + 1} אינו אובייקט JSON")
            val legacyFolder = item.optString("folder", "פתקים").trim().ifBlank { "פתקים" }
            val folderId = if (version < 2 || item.has("folder")) {
                folderByName.getOrPut(legacyFolder) {
                    val created = NoteFolder(name = legacyFolder)
                    folders += created
                    created.id
                }
            } else item.optString("folderId", DEFAULT_FOLDER_ID).ifBlank { DEFAULT_FOLDER_ID }

            val attachments = mutableListOf<NoteAttachment>()
            val rawAttachments = item.optJSONArray("attachments") ?: JSONArray()
            for (attachmentIndex in 0 until rawAttachments.length()) {
                val a = rawAttachments.optJSONObject(attachmentIndex) ?: continue
                val id = a.optString("id").ifBlank { UUID.randomUUID().toString() }
                val name = a.optString("name", "attachment")
                val base64 = a.optString("dataBase64").takeIf { it.isNotBlank() && it != "null" }
                val restoredFile = if (base64 != null) {
                    val bytes = try {
                        Base64.decode(base64, Base64.DEFAULT)
                    } catch (_: Exception) {
                        throw IllegalArgumentException("קובץ מצורף בפתק §{index + 1} פגום")
                    }
                    require(bytes.size <= MAX_ATTACHMENT_BYTES) { "קובץ מצורף גדול מדי" }
                    val safeName = name.replace(Regex("[^A-Za-z0-9._-]"), "_").take(80).ifBlank { "attachment" }
                    File(attachmentDir, "§{id}_§safeName").also { file ->
                        FileOutputStream(file).use { it.write(bytes) }
                    }
                } else null
                attachments += NoteAttachment(
                    id = id, name = name, mimeType = a.optString("mimeType", "application/octet-stream"),
                    path = restoredFile?.absolutePath ?: "", size = restoredFile?.length() ?: a.optLong("size")
                )
            }

            val tags = item.optJSONArray("tags")?.let { array ->
                (0 until array.length()).mapNotNull { array.optString(it).trim().takeIf(String::isNotBlank) }
            } ?: extractTags(item.optString("body", item.optString("bodyHtml", "")))
            val body = when {
                item.has("bodyHtml") -> item.optString("bodyHtml")
                item.has("body") -> escapeLegacyText(item.optString("body"))
                else -> ""
            }
            notes += NoteItem(
                id = item.optLong("id", 0L), title = item.optString("title", ""),
                bodyHtml = body, folderId = folderId, tags = tags,
                checklist = parseChecklist(item.optJSONArray("checklist")?.toString()),
                attachments = attachments,
                pinned = item.optBoolean("pinned"), locked = item.optBoolean("locked"),
                trashed = item.optBoolean("trashed"), trashedAt = item.optLong("trashedAt"),
                createdAt = item.optLong("createdAt", item.optLong("updatedAt", System.currentTimeMillis())),
                updatedAt = item.optLong("updatedAt", System.currentTimeMillis()),
                background = item.optString("background", "default"),
                lockSalt = item.optString("lockSalt"), lockIv = item.optString("lockIv"),
                lockCiphertext = item.optString("lockCiphertext")
            )
        }
        require(notes.size == noteArray.length()) { "לא כל הפתקים נקראו מהקובץ" }
        return BackupSnapshot(folders, notes, version)
    }

    private fun escapeLegacyText(value: String): String =
        Html.escapeHtml(value).replace("\n", "<br>")

    private fun extractTags(text: String): List<String> =
        Regex("(?<![A-Za-z0-9_])#([\\p{L}\\p{N}_-]+)")
            .findAll(text).map { it.groupValues[1] }.distinct().toList()
}
