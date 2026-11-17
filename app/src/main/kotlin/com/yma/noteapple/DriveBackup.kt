package com.yma.noteapple

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class DriveBackup(private val context: Context) {
    companion object {
        const val SCOPE = "https://www.googleapis.com/auth/drive.appdata"
    }

    fun upload(accessToken: String, snapshot: BackupSnapshot) {
        val json = BackupCodec.encode(context, snapshot)
        val bytes = zipBackup(json)
        val boundary = "noteapple-" + UUID.randomUUID().toString().replace("-", "")
        val metadata = JSONObject()
            .put("name", "NoteApple-backup.zip")
            .put("mimeType", "application/zip")
            .put("parents", JSONArray().put("appDataFolder"))
        val prefix = "--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n" +
            metadata.toString() + "\r\n--$boundary\r\nContent-Type: application/zip\r\n\r\n"
        val suffix = "\r\n--$boundary--\r\n"
        val body = ByteArrayOutputStream().apply {
            write(prefix.toByteArray(Charsets.UTF_8))
            write(bytes)
            write(suffix.toByteArray(Charsets.UTF_8))
        }.toByteArray()
        request(
            "POST",
            "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id,name",
            accessToken, "multipart/related; boundary=$boundary", body
        )
    }

    fun download(accessToken: String): BackupSnapshot {
        val query = URLEncoder.encode("name='NoteApple-backup.zip' and trashed=false", "UTF-8")
        val listText = request(
            "GET",
            "https://www.googleapis.com/drive/v3/files?spaces=appDataFolder&q=$query&fields=files(id,name,modifiedTime)&orderBy=modifiedTime%20desc&pageSize=1",
            accessToken, null, null
        ).toString(Charsets.UTF_8)
        val files = JSONObject(listText).optJSONArray("files") ?: JSONArray()
        if (files.length() == 0) throw IOException("לא נמצא גיבוי בחשבון Google Drive")
        val id = files.getJSONObject(0).getString("id")
        val zip = request(
            "GET",
            "https://www.googleapis.com/drive/v3/files/$id?alt=media",
            accessToken, null, null
        )
        return BackupCodec.decode(context, unzipBackup(zip))
    }

    private fun zipBackup(json: String): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry("backup.json"))
            zip.write(json.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("README.txt"))
            zip.write("NoteApple backup. Restore this file inside NoteApple.".toByteArray())
            zip.closeEntry()
        }
        return output.toByteArray()
    }

    private fun unzipBackup(bytes: ByteArray): String {
        ZipInputStream(bytes.inputStream()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (!entry.isDirectory && entry.name == "backup.json") {
                    val output = ByteArrayOutputStream()
                    zip.copyTo(output)
                    return output.toString("UTF-8")
                }
                zip.closeEntry()
            }
        }
        throw IOException("לא נמצא backup.json בגיבוי Drive")
    }

    private fun request(method: String, url: String, token: String, contentType: String?, body: ByteArray?): ByteArray {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 20000
            readTimeout = 40000
            setRequestProperty("Authorization", "Bearer $token")
            if (contentType != null) setRequestProperty("Content-Type", contentType)
            if (body != null) doOutput = true
        }
        try {
            if (body != null) connection.outputStream.use { it.write(body) }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val data = stream?.use { it.readBytes() } ?: ByteArray(0)
            if (status !in 200..299) {
                throw IOException("Google Drive HTTP $status: " + data.toString(Charsets.UTF_8).take(160))
            }
            return data
        } finally {
            connection.disconnect()
        }
    }
}
