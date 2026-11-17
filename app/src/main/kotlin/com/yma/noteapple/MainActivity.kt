package com.yma.noteapple

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import java.io.IOException

class MainActivity : ComponentActivity() {
    lateinit var database: NotesDatabase
        private set

    private lateinit var driveAuthorizationLauncher:
        androidx.activity.result.ActivityResultLauncher<IntentSenderRequest>
    private var pendingRestore = false
    private var driveCallback: ((Result<BackupSnapshot>) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        database = NotesDatabase(applicationContext)

        driveAuthorizationLauncher = registerForActivityResult(
            ActivityResultContracts.StartIntentSenderForResult()
        ) { result ->
            if (result.resultCode != RESULT_OK) {
                finishDrive(Result.failure(IOException("הרשאת Google Drive בוטלה")))
            } else {
                runCatching {
                    Identity.getAuthorizationClient(this).getAuthorizationResultFromIntent(result.data)
                }.onSuccess { authorization ->
                    performDrive(pendingRestore, authorization.accessToken)
                }.onFailure { finishDrive(Result.failure(it)) }
            }
        }

        setContent {
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Rtl
            ) { NoteAppleApp(this) }
        }
    }

    fun requestDrive(restore: Boolean, callback: (Result<BackupSnapshot>) -> Unit) {
        pendingRestore = restore
        driveCallback = callback
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(DriveBackup.SCOPE)))
            .build()
        Identity.getAuthorizationClient(this).authorize(request)
            .addOnSuccessListener { authorization ->
                if (authorization.hasResolution()) {
                    val pending = authorization.pendingIntent
                    if (pending == null) {
                        finishDrive(Result.failure(IOException("Google לא החזירה בקשת הרשאה")))
                    } else {
                        driveAuthorizationLauncher.launch(
                            IntentSenderRequest.Builder(pending.intentSender).build()
                        )
                    }
                } else {
                    performDrive(restore, authorization.accessToken)
                }
            }
            .addOnFailureListener { finishDrive(Result.failure(it)) }
    }

    private fun performDrive(restore: Boolean, token: String?) {
        if (token.isNullOrBlank()) {
            finishDrive(Result.failure(IOException("לא התקבל אסימון גישה מ-Google")))
            return
        }
        Thread {
            try {
                val drive = DriveBackup(applicationContext)
                val result = if (restore) drive.download(token) else {
                    val snapshot = database.snapshot()
                    drive.upload(token, snapshot)
                    snapshot
                }
                runOnUiThread { finishDrive(Result.success(result)) }
            } catch (error: Exception) {
                runOnUiThread { finishDrive(Result.failure(error)) }
            }
        }.start()
    }

    private fun finishDrive(result: Result<BackupSnapshot>) {
        driveCallback?.invoke(result)
        driveCallback = null
    }
}
