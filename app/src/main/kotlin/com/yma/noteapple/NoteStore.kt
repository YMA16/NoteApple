package com.yma.noteapple

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray
import org.json.JSONObject

data class Note(
    val id: Long = 0,
    val title: String = "",
    val body: String = "",
    val folder: String = "פתקים",
    val pinned: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
)

class NoteStore(context: Context) : SQLiteOpenHelper(context, "noteapple.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE notes (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                title TEXT NOT NULL,
                body TEXT NOT NULL,
                folder TEXT NOT NULL,
                pinned INTEGER NOT NULL DEFAULT 0,
                updated_at INTEGER NOT NULL
            )"""
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun all(): List<Note> {
        val result = mutableListOf<Note>()
        readableDatabase.query(
            "notes", null, null, null, null, null,
            "pinned DESC, updated_at DESC"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result += Note(
                    id = cursor.getLong(cursor.getColumnIndexOrThrow("id")),
                    title = cursor.getString(cursor.getColumnIndexOrThrow("title")) ?: "",
                    body = cursor.getString(cursor.getColumnIndexOrThrow("body")) ?: "",
                    folder = cursor.getString(cursor.getColumnIndexOrThrow("folder")) ?: "פתקים",
                    pinned = cursor.getInt(cursor.getColumnIndexOrThrow("pinned")) == 1,
                    updatedAt = cursor.getLong(cursor.getColumnIndexOrThrow("updated_at"))
                )
            }
        }
        return result
    }

    fun save(note: Note): Note {
        val values = ContentValues().apply {
            put("title", note.title)
            put("body", note.body)
            put("folder", note.folder.ifBlank { "פתקים" })
            put("pinned", if (note.pinned) 1 else 0)
            put("updated_at", note.updatedAt)
        }
        val savedId = if (note.id > 0) {
            writableDatabase.update("notes", values, "id = ?", arrayOf(note.id.toString()))
            note.id
        } else {
            writableDatabase.insert("notes", null, values)
        }
        return note.copy(id = savedId)
    }

    fun delete(id: Long) {
        writableDatabase.delete("notes", "id = ?", arrayOf(id.toString()))
    }

    fun replaceAll(notes: List<Note>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("notes", null, null)
            notes.forEach { note ->
                val values = ContentValues().apply {
                    if (note.id > 0) put("id", note.id)
                    put("title", note.title)
                    put("body", note.body)
                    put("folder", note.folder.ifBlank { "פתקים" })
                    put("pinned", if (note.pinned) 1 else 0)
                    put("updated_at", note.updatedAt)
                }
                db.insert("notes", null, values)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun exportJson(): String = notesToJson(all())
}

fun notesToJson(notes: List<Note>): String {
    val array = JSONArray()
    notes.forEach { note ->
        array.put(
            JSONObject()
                .put("id", note.id)
                .put("title", note.title)
                .put("body", note.body)
                .put("folder", note.folder)
                .put("pinned", note.pinned)
                .put("updatedAt", note.updatedAt)
        )
    }
    return JSONObject()
        .put("format", "noteapple-backup")
        .put("version", 1)
        .put("notes", array)
        .toString(2)
}

fun notesFromJson(json: String): List<Note> {
    val root = JSONObject(json)
    val array = root.optJSONArray("notes") ?: JSONArray()
    val notes = mutableListOf<Note>()
    for (index in 0 until array.length()) {
        val item = array.optJSONObject(index) ?: continue
        notes += Note(
            id = item.optLong("id", 0),
            title = item.optString("title", ""),
            body = item.optString("body", ""),
            folder = item.optString("folder", "פתקים"),
            pinned = item.optBoolean("pinned", false),
            updatedAt = item.optLong("updatedAt", System.currentTimeMillis())
        )
    }
    return notes
}

fun notesToText(notes: List<Note>): String = buildString {
    notes.forEachIndexed { index, note ->
        if (index > 0) append("\n\n")
        append(note.title.ifBlank { "פתק ללא כותרת" })
        append("\n")
        append(note.body)
    }
}
