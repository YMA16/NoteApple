package com.yma.noteapple

import android.util.Base64
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object NoteSecurity {
    fun createPasscodeHash(passcode: String): String {
        val salt = ByteArray(16).also(SecureRandom()::nextBytes)
        return Base64.encodeToString(salt, Base64.NO_WRAP) + "." +
            Base64.encodeToString(derive(passcode.toCharArray(), salt).encoded, Base64.NO_WRAP)
    }

    fun verifyPasscode(passcode: String, stored: String): Boolean {
        val pieces = stored.split(".")
        if (pieces.size != 2) return false
        return runCatching {
            val salt = Base64.decode(pieces[0], Base64.NO_WRAP)
            val expected = Base64.decode(pieces[1], Base64.NO_WRAP)
            val actual = derive(passcode.toCharArray(), salt).encoded
            MessageDigest.isEqual(expected, actual)
        }.getOrDefault(false)
    }

    fun lock(note: NoteItem, passcode: CharArray): NoteItem {
        require(passcode.size >= 4) { "הסיסמה חייבת להכיל לפחות 4 תווים" }
        val random = SecureRandom()
        val salt = ByteArray(16).also(random::nextBytes)
        val iv = ByteArray(12).also(random::nextBytes)
        val key = derive(passcode, salt)
        val payload = JSONObject()
            .put("bodyHtml", note.bodyHtml)
            .put("tags", stringsJson(note.tags))
            .put("checklist", checklistJson(note.checklist))
            .toString()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        val ciphertext = cipher.doFinal(payload.toByteArray(Charsets.UTF_8))
        return note.copy(
            bodyHtml = "",
            tags = emptyList(),
            checklist = emptyList(),
            locked = true,
            lockSalt = Base64.encodeToString(salt, Base64.NO_WRAP),
            lockIv = Base64.encodeToString(iv, Base64.NO_WRAP),
            lockCiphertext = Base64.encodeToString(ciphertext, Base64.NO_WRAP)
        )
    }

    fun unlock(note: NoteItem, passcode: CharArray): NoteItem {
        require(note.locked && note.lockSalt.isNotBlank()) { "הפתק אינו נעול" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            derive(passcode, Base64.decode(note.lockSalt, Base64.NO_WRAP)),
            GCMParameterSpec(128, Base64.decode(note.lockIv, Base64.NO_WRAP))
        )
        val clear = cipher.doFinal(Base64.decode(note.lockCiphertext, Base64.NO_WRAP))
        val payload = JSONObject(clear.toString(Charsets.UTF_8))
        return note.copy(
            locked = false,
            bodyHtml = payload.optString("bodyHtml"),
            tags = parseStrings(payload.optJSONArray("tags")?.toString()),
            checklist = parseChecklist(payload.optJSONArray("checklist")?.toString()),
            lockSalt = "",
            lockIv = "",
            lockCiphertext = ""
        )
    }

    private fun derive(passcode: CharArray, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(passcode, salt, 180_000, 256)
        return try {
            SecretKeySpec(
                SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded,
                "AES"
            )
        } finally {
            spec.clearPassword()
        }
    }
}
