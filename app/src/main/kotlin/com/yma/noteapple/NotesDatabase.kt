package com.yma.noteapple

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.text.Html
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

const val DEFAULT_FOLDER_ID = "local-notes"

data class ChecklistItem(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val checked: Boolean = false,
    val indent: Int = 0
)

data class NoteAttachment(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val mimeType: String,
    val path: String,
    val size: Long = 0
)

data class NoteFolder(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val parentId: String? = null,
    val color: String = "yellow",
    val emoji: String = "📁",
    val smartQuery: String = ""
)

data class NoteItem(
    val id: Long = 0,
    val title: String = "",
    val bodyHtml: String = "",
    val folderId: String = DEFAULT_FOLDER_ID,
    val tags: List<String> = emptyList(),
    val checklist: List<ChecklistItem> = emptyList(),
    val attachments: List<NoteAttachment> = emptyList(),
    val pinned: Boolean = false,
    val locked: Boolean = false,
    val trashed: Boolean = false,
    val trashedAt: Long = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val background: String = "default",
    val lockSalt: String = "",
    val lockIv: String = "",
    val lockCiphertext: String = ""
)

data class BackupSnapshot(
    val folders: List<NoteFolder>,
    val notes: List<NoteItem>,
    val sourceVersion: Int = 3
)

class NotesDatabase(context: Context) : SQLiteOpenHelper(context, "noteapple.db", null, 3) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE folders (
                id TEXT PRIMARY KEY NOT NULL,
                name TEXT NOT NULL,
                parent_id TEXT,
                color TEXT NOT NULL DEFAULT 'yellow',
                emoji TEXT NOT NULL DEFAULT '📁',
                smart_query TEXT NOT NULL DEFAULT ''
            )"""
        )
        db.execSQL(
            """CREATE TABLE notes (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                title TEXT NOT NULL DEFAULT '',
                body TEXT NOT NULL DEFAULT '',
                folder TEXT NOT NULL DEFAULT 'פתקים',
                body_html TEXT NOT NULL DEFAULT '',
                folder_id TEXT NOT NULL,
                tags_json TEXT NOT NULL DEFAULT '[]',
                checklist_json TEXT NOT NULL DEFAULT '[]',
                attachments_json TEXT NOT NULL DEFAULT '[]',
                pinned INTEGER NOT NULL DEFAULT 0,
                locked INTEGER NOT NULL DEFAULT 0,
                trashed INTEGER NOT NULL DEFAULT 0,
                trashed_at INTEGER NOT NULL DEFAULT 0,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL,
                background TEXT NOT NULL DEFAULT 'default',
                lock_salt TEXT NOT NULL DEFAULT '',
                lock_iv TEXT NOT NULL DEFAULT '',
                lock_ciphertext TEXT NOT NULL DEFAULT ''
            )"""
        )
        db.insert("folders", null, folderValues(NoteFolder(id = DEFAULT_FOLDER_ID, name = "פתקים")))
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) migrateLegacyNotes(db)
        if (oldVersion < 3) {
            runCatching { db.execSQL("ALTER TABLE notes ADD COLUMN trashed_at INTEGER NOT NULL DEFAULT 0") }
        }
    }

    private fun migrateLegacyNotes(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS folders (
                id TEXT PRIMARY KEY NOT NULL, name TEXT NOT NULL, parent_id TEXT,
                color TEXT NOT NULL DEFAULT 'yellow', emoji TEXT NOT NULL DEFAULT '📁',
                smart_query TEXT NOT NULL DEFAULT ''
            )"""
        )
        db.insertWithOnConflict(
            "folders", null, folderValues(NoteFolder(id = DEFAULT_FOLDER_ID, name = "פתקים")),
            SQLiteDatabase.CONFLICT_IGNORE
        )
        listOf(
            "body_html TEXT NOT NULL DEFAULT ''",
            "folder_id TEXT",
            "created_at INTEGER NOT NULL DEFAULT 0",
            "tags_json TEXT NOT NULL DEFAULT '[]'",
            "checklist_json TEXT NOT NULL DEFAULT '[]'",
            "attachments_json TEXT NOT NULL DEFAULT '[]'",
            "locked INTEGER NOT NULL DEFAULT 0",
            "trashed INTEGER NOT NULL DEFAULT 0",
            "background TEXT NOT NULL DEFAULT 'default'",
            "lock_salt TEXT NOT NULL DEFAULT ''",
            "lock_iv TEXT NOT NULL DEFAULT ''",
            "lock_ciphertext TEXT NOT NULL DEFAULT ''"
        ).forEach { declaration ->
            val column = declaration.substringBefore(' ')
            val exists = db.rawQuery("PRAGMA table_info(notes)", null).use { c ->
                var found = false
                while (c.moveToNext()) if (c.getString(1) == column) found = true
                found
            }
            if (!exists) db.execSQL("ALTER TABLE notes ADD COLUMN $declaration")
        }

        val columns = db.rawQuery("PRAGMA table_info(notes)", null).use { c ->
            buildSet { while (c.moveToNext()) add(c.getString(1)) }
        }
        if ("body" in columns) {
            db.execSQL(
                "UPDATE notes SET body_html=REPLACE(REPLACE(REPLACE(body, '&', '&amp;'), '<', '&lt;'), char(10), '<br>')"
            )
        }
        val legacyFolderColumn = if ("folder" in columns) "folder" else null
        if (legacyFolderColumn != null) {
            val folderMap = linkedMapOf<String, String>()
            db.rawQuery("SELECT DISTINCT folder FROM notes", null).use { c ->
                while (c.moveToNext()) {
                    val name = c.getString(0)?.trim().orEmpty().ifBlank { "פתקים" }
                    if (name == "פתקים") folderMap[name] = DEFAULT_FOLDER_ID
                    else if (name !in folderMap) {
                        val folder = NoteFolder(name = name)
                        folderMap[name] = folder.id
                        db.insert("folders", null, folderValues(folder))
                    }
                }
            }
            folderMap.forEach { (name, id) ->
                db.execSQL("UPDATE notes SET folder_id=? WHERE folder=?", arrayOf(id, name))
            }
        }
        db.execSQL("UPDATE notes SET folder_id='$DEFAULT_FOLDER_ID' WHERE folder_id IS NULL OR folder_id=''")
        if ("created_at" in columns && "updated_at" in columns) {
            db.execSQL("UPDATE notes SET created_at=updated_at WHERE created_at=0")
        }
    }

    fun folders(): List<NoteFolder> {
        val result = mutableListOf<NoteFolder>()
        readableDatabase.query("folders", null, null, null, null, null, "name COLLATE NOCASE ASC").use { c ->
            while (c.moveToNext()) result += c.toFolder()
        }
        return result
    }

    fun saveFolder(folder: NoteFolder) {
        writableDatabase.insertWithOnConflict("folders", null, folderValues(folder), SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun deleteFolder(id: String) {
        if (id == DEFAULT_FOLDER_ID) return
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.execSQL("UPDATE notes SET folder_id=? WHERE folder_id=?", arrayOf(DEFAULT_FOLDER_ID, id))
            db.execSQL("UPDATE folders SET parent_id=NULL WHERE parent_id=?", arrayOf(id))
            db.delete("folders", "id=?", arrayOf(id))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun notes(includeTrash: Boolean = false): List<NoteItem> {
        cleanupExpiredTrash()
        val result = mutableListOf<NoteItem>()
        readableDatabase.query(
            "notes", null, if (includeTrash) null else "trashed=0",
            null, null, null, "pinned DESC, updated_at DESC"
        ).use { c ->
            while (c.moveToNext()) result += c.toNote()
        }
        return result
    }

    fun notesInFolder(folderId: String): List<NoteItem> = notes().filter { it.folderId == folderId }

    fun note(id: Long): NoteItem? {
        readableDatabase.query("notes", null, "id=?", arrayOf(id.toString()), null, null, null).use { c ->
            return if (c.moveToFirst()) c.toNote() else null
        }
    }

    fun save(note: NoteItem): NoteItem {
        val values = noteValues(note)
        val id = if (note.id > 0) {
            writableDatabase.update("notes", values, "id=?", arrayOf(note.id.toString()))
            note.id
        } else {
            writableDatabase.insert("notes", null, values)
        }
        return note.copy(id = id)
    }

    fun moveToTrash(note: NoteItem) {
        save(note.copy(trashed = true, trashedAt = System.currentTimeMillis(), updatedAt = System.currentTimeMillis()))
    }

    fun restore(note: NoteItem) {
        save(note.copy(trashed = false, trashedAt = 0, updatedAt = System.currentTimeMillis()))
    }

    fun deleteForever(id: Long) {
        writableDatabase.delete("notes", "id=?", arrayOf(id.toString()))
    }

    fun emptyTrash() {
        writableDatabase.delete("notes", "trashed=1", null)
    }

    private fun cleanupExpiredTrash() {
        val cutoff = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000
        writableDatabase.delete("notes", "trashed=1 AND trashed_at>0 AND trashed_at<?", arrayOf(cutoff.toString()))
    }

    fun snapshot(): BackupSnapshot = BackupSnapshot(folders(), notes(includeTrash = true))

    fun replaceAll(snapshot: BackupSnapshot) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("notes", null, null)
            db.delete("folders", null, null)
            val importedFolders = snapshot.folders.ifEmpty { listOf(NoteFolder(id = DEFAULT_FOLDER_ID, name = "פתקים")) }
            importedFolders.forEach { db.insertWithOnConflict("folders", null, folderValues(it), SQLiteDatabase.CONFLICT_REPLACE) }
            if (importedFolders.none { it.id == DEFAULT_FOLDER_ID }) {
                db.insert("folders", null, folderValues(NoteFolder(id = DEFAULT_FOLDER_ID, name = "פתקים")))
            }
            snapshot.notes.forEach { incoming ->
                val folderValid = importedFolders.any { it.id == incoming.folderId } || incoming.folderId == DEFAULT_FOLDER_ID
                val note = if (folderValid) incoming else incoming.copy(folderId = DEFAULT_FOLDER_ID)
                db.insertWithOnConflict("notes", null, noteValues(note), SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun merge(snapshot: BackupSnapshot) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            snapshot.folders.forEach { folder ->
                db.insertWithOnConflict("folders", null, folderValues(folder), SQLiteDatabase.CONFLICT_IGNORE)
            }
            snapshot.notes.forEach { incoming ->
                val existing = if (incoming.id > 0) note(incoming.id) else null
                val target = when {
                    existing == null -> incoming
                    incoming.updatedAt > existing.updatedAt -> incoming
                    else -> incoming.copy(id = 0)
                }
                db.insertWithOnConflict("notes", null, noteValues(target), SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun folderValues(f: NoteFolder) = ContentValues().apply {
        put("id", f.id); put("name", f.name); put("parent_id", f.parentId)
        put("color", f.color); put("emoji", f.emoji); put("smart_query", f.smartQuery)
    }

    private fun noteValues(n: NoteItem) = ContentValues().apply {
        if (n.id > 0) put("id", n.id)
        put("title", n.title)
        put("body", Html.fromHtml(n.bodyHtml, Html.FROM_HTML_MODE_COMPACT).toString())
        put("folder", n.folderId)
        put("body_html", n.bodyHtml); put("folder_id", n.folderId)
        put("tags_json", stringsJson(n.tags).toString())
        put("checklist_json", checklistJson(n.checklist).toString())
        put("attachments_json", attachmentsJson(n.attachments).toString())
        put("pinned", if (n.pinned) 1 else 0); put("locked", if (n.locked) 1 else 0)
        put("trashed", if (n.trashed) 1 else 0); put("trashed_at", n.trashedAt)
        put("created_at", n.createdAt); put("updated_at", n.updatedAt)
        put("background", n.background); put("lock_salt", n.lockSalt)
        put("lock_iv", n.lockIv); put("lock_ciphertext", n.lockCiphertext)
    }

    private fun Cursor.toFolder() = NoteFolder(
        id = getString(getColumnIndexOrThrow("id")),
        name = getString(getColumnIndexOrThrow("name")),
        parentId = getString(getColumnIndexOrThrow("parent_id")),
        color = getString(getColumnIndexOrThrow("color")) ?: "yellow",
        emoji = getString(getColumnIndexOrThrow("emoji")) ?: "📁",
        smartQuery = getString(getColumnIndexOrThrow("smart_query")) ?: ""
    )

    private fun Cursor.toNote() = NoteItem(
        id = getLong(getColumnIndexOrThrow("id")),
        title = getString(getColumnIndexOrThrow("title")) ?: "",
        bodyHtml = getString(getColumnIndexOrThrow("body_html")) ?: "",
        folderId = getString(getColumnIndexOrThrow("folder_id")) ?: DEFAULT_FOLDER_ID,
        tags = parseStrings(getString(getColumnIndexOrThrow("tags_json"))),
        checklist = parseChecklist(getString(getColumnIndexOrThrow("checklist_json"))),
        attachments = parseAttachments(getString(getColumnIndexOrThrow("attachments_json"))),
        pinned = getInt(getColumnIndexOrThrow("pinned")) == 1,
        locked = getInt(getColumnIndexOrThrow("locked")) == 1,
        trashed = getInt(getColumnIndexOrThrow("trashed")) == 1,
        trashedAt = getLong(getColumnIndexOrThrow("trashed_at")),
        createdAt = getLong(getColumnIndexOrThrow("created_at")),
        updatedAt = getLong(getColumnIndexOrThrow("updated_at")),
        background = getString(getColumnIndexOrThrow("background")) ?: "default",
        lockSalt = getString(getColumnIndexOrThrow("lock_salt")) ?: "",
        lockIv = getString(getColumnIndexOrThrow("lock_iv")) ?: "",
        lockCiphertext = getString(getColumnIndexOrThrow("lock_ciphertext")) ?: ""
    )
}

internal fun stringsJson(values: List<String>) = JSONArray().apply { values.forEach { put(it) } }
internal fun checklistJson(values: List<ChecklistItem>) = JSONArray().apply {
    values.forEach { put(JSONObject().put("id", it.id).put("text", it.text).put("checked", it.checked).put("indent", it.indent)) }
}
internal fun attachmentsJson(values: List<NoteAttachment>) = JSONArray().apply {
    values.forEach {
        put(JSONObject().put("id", it.id).put("name", it.name).put("mimeType", it.mimeType).put("path", it.path).put("size", it.size))
    }
}
internal fun parseStrings(value: String?): List<String> = runCatching {
    val array = JSONArray(value ?: "[]")
    (0 until array.length()).mapNotNull { array.optString(it).trim().takeIf(String::isNotBlank) }
}.getOrDefault(emptyList())

internal fun parseChecklist(value: String?): List<ChecklistItem> = runCatching {
    val array = JSONArray(value ?: "[]")
    (0 until array.length()).mapNotNull { i ->
        val item = array.optJSONObject(i) ?: return@mapNotNull null
        ChecklistItem(item.optString("id").ifBlank { UUID.randomUUID().toString() }, item.optString("text"), item.optBoolean("checked"), item.optInt("indent"))
    }
}.getOrDefault(emptyList())

internal fun parseAttachments(value: String?): List<NoteAttachment> = runCatching {
    val array = JSONArray(value ?: "[]")
    (0 until array.length()).mapNotNull { i ->
        val item = array.optJSONObject(i) ?: return@mapNotNull null
        NoteAttachment(item.optString("id").ifBlank { UUID.randomUUID().toString() }, item.optString("name"),
            item.optString("mimeType", "application/octet-stream"), item.optString("path"), item.optLong("size"))
    }
}.getOrDefault(emptyList())
