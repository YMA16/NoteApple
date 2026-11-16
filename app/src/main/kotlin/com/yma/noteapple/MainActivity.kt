package com.yma.noteapple

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

private const val DRIVE_APPDATA_SCOPE = "https://www.googleapis.com/auth/drive.appdata"

class MainActivity : ComponentActivity() {
    internal lateinit var store: NoteStore
    internal var dataRevision by mutableIntStateOf(0)
        private set

    private var restoreAfterAuthorization = false
    private lateinit var driveAuthorizationLauncher:
        androidx.activity.result.ActivityResultLauncher<IntentSenderRequest>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = NoteStore(applicationContext)

        driveAuthorizationLauncher = registerForActivityResult(
            ActivityResultContracts.StartIntentSenderForResult()
        ) { result ->
            if (result.resultCode == RESULT_OK) {
                try {
                    val authorization = Identity.getAuthorizationClient(this)
                        .getAuthorizationResultFromIntent(result.data)
                    continueDriveAction(authorization.accessToken, restoreAfterAuthorization)
                } catch (error: Exception) {
                    toast("לא ניתן להשלים את ההרשאה ל-Google Drive")
                }
            } else {
                toast("ההרשאה ל-Google Drive בוטלה")
            }
        }

        setContent { NotesApp(this) }
    }

    fun requestDriveAccess(restore: Boolean) {
        restoreAfterAuthorization = restore
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(DRIVE_APPDATA_SCOPE)))
            .build()

        Identity.getAuthorizationClient(this)
            .authorize(request)
            .addOnSuccessListener { authorization ->
                if (authorization.hasResolution()) {
                    val pendingIntent = authorization.pendingIntent
                    if (pendingIntent != null) {
                        driveAuthorizationLauncher.launch(
                            IntentSenderRequest.Builder(pendingIntent.intentSender).build()
                        )
                    } else {
                        toast("לא התקבלה בקשת הרשאה תקינה")
                    }
                } else {
                    continueDriveAction(authorization.accessToken, restore)
                }
            }
            .addOnFailureListener {
                toast("שגיאה בהרשאה ל-Google Drive: " + (it.localizedMessage ?: "לא ידועה"))
            }
    }

    private fun continueDriveAction(accessToken: String?, restore: Boolean) {
        if (accessToken.isNullOrBlank()) {
            toast("Google לא החזירה אסימון גישה")
            return
        }
        Thread {
            try {
                if (restore) {
                    val json = DriveBackup.download(accessToken)
                    val imported = notesFromJson(json)
                    store.replaceAll(imported)
                    runOnUiThread {
                        dataRevision++
                        toast("השחזור הושלם · " + imported.size + " פתקים")
                    }
                } else {
                    DriveBackup.upload(accessToken, store.exportJson())
                    runOnUiThread { toast("הגיבוי נשמר ב-Google Drive") }
                }
            } catch (error: Exception) {
                runOnUiThread {
                    val detail = error.localizedMessage?.take(100) ?: "שגיאה לא ידועה"
                    toast("פעולת Drive נכשלה: " + detail)
                }
            }
        }.start()
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
}

@Composable
private fun NotesApp(activity: MainActivity) {
    val context = LocalContext.current
    val preferences = remember {
        context.getSharedPreferences("noteapple_preferences", Context.MODE_PRIVATE)
    }
    var themeMode by remember { mutableStateOf(preferences.getString("theme", "system") ?: "system") }
    var accentName by remember { mutableStateOf(preferences.getString("accent", "gold") ?: "gold") }
    val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
    val dark = when (themeMode) {
        "dark" -> true
        "light" -> false
        else -> systemDark
    }
    val accents = accentColors()
    val accent = accents[accentName] ?: Color(0xFFE8B83F)
    val scheme = if (dark) {
        darkColorScheme(
            primary = accent,
            onPrimary = Color(0xFF171717),
            secondary = accent,
            background = Color(0xFF111214),
            surface = Color(0xFF1B1D20),
            surfaceVariant = Color(0xFF282B30),
            onSurface = Color(0xFFF2F1ED),
            onSurfaceVariant = Color(0xFFB6B5B0)
        )
    } else {
        lightColorScheme(
            primary = accent,
            onPrimary = Color(0xFF171717),
            secondary = accent,
            background = Color(0xFFF8F7F3),
            surface = Color.White,
            surfaceVariant = Color(0xFFF0EEE8),
            onSurface = Color(0xFF222222),
            onSurfaceVariant = Color(0xFF6D6B65)
        )
    }

    MaterialTheme(colorScheme = scheme) {
        androidx.compose.runtime.CompositionLocalProvider(
            LocalLayoutDirection provides LayoutDirection.Rtl
        ) {
            val notes = remember { mutableStateListOf<Note>() }
            LaunchedEffect(activity.dataRevision) {
                notes.clear()
                notes.addAll(activity.store.all())
            }
            var page by remember { mutableStateOf("home") }
            var selectedNote by remember { mutableStateOf<Note?>(null) }

            val jsonExport = androidx.activity.compose.rememberLauncherForActivityResult(
                ActivityResultContracts.CreateDocument("application/json")
            ) { uri -> uri?.let { writeExport(context, it, notesToJson(notes).toByteArray()) } }
            val textExport = androidx.activity.compose.rememberLauncherForActivityResult(
                ActivityResultContracts.CreateDocument("text/plain")
            ) { uri -> uri?.let { writeExport(context, it, notesToText(notes).toByteArray()) } }
            val zipExport = androidx.activity.compose.rememberLauncherForActivityResult(
                ActivityResultContracts.CreateDocument("application/zip")
            ) { uri -> uri?.let { writeExport(context, it, makeZip(notes)) } }
            val importLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                ActivityResultContracts.OpenDocument()
            ) { uri ->
                if (uri != null) {
                    try {
                        val json = context.contentResolver.openInputStream(uri)
                            ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                            ?: throw IOException("לא ניתן לקרוא את הקובץ")
                        val imported = notesFromJson(json)
                        activity.store.replaceAll(imported)
                        notes.clear()
                        notes.addAll(activity.store.all())
                        Toast.makeText(context, "יובאו " + imported.size + " פתקים", Toast.LENGTH_LONG).show()
                    } catch (error: Exception) {
                        Toast.makeText(context, "הייבוא נכשל: " + (error.localizedMessage ?: ""), Toast.LENGTH_LONG).show()
                    }
                }
            }

            fun refresh() {
                notes.clear()
                notes.addAll(activity.store.all())
            }

            fun saveNote(note: Note) {
                if (note.id == 0L && note.title.isBlank() && note.body.isBlank()) {
                    page = "home"
                    return
                }
                activity.store.save(note.copy(updatedAt = System.currentTimeMillis()))
                refresh()
                page = "home"
            }

            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                when (page) {
                    "settings" -> SettingsScreen(
                        noteCount = notes.size,
                        themeMode = themeMode,
                        accentName = accentName,
                        accentColors = accents,
                        onThemeChange = {
                            themeMode = it
                            preferences.edit().putString("theme", it).apply()
                        },
                        onAccentChange = {
                            accentName = it
                            preferences.edit().putString("accent", it).apply()
                        },
                        onBack = { page = "home" },
                        onExportJson = { jsonExport.launch("NoteApple-Backup.json") },
                        onExportText = { textExport.launch("NoteApple-Notes.txt") },
                        onExportZip = { zipExport.launch("NoteApple-Backup.zip") },
                        onImport = { importLauncher.launch(arrayOf("application/json", "text/*")) },
                        onDriveBackup = { activity.requestDriveAccess(false) },
                        onDriveRestore = { activity.requestDriveAccess(true) }
                    )
                    "editor" -> NoteEditorScreen(
                        note = selectedNote,
                        onBack = { note -> saveNote(note) },
                        onDelete = { id ->
                            activity.store.delete(id)
                            refresh()
                            page = "home"
                        }
                    )
                    else -> NotesHomeScreen(
                        notes = notes,
                        onAdd = {
                            selectedNote = null
                            page = "editor"
                        },
                        onEdit = {
                            selectedNote = it
                            page = "editor"
                        },
                        onSettings = { page = "settings" },
                        onPin = {
                            activity.store.save(it.copy(pinned = !it.pinned, updatedAt = System.currentTimeMillis()))
                            refresh()
                        },
                        onDelete = {
                            activity.store.delete(it.id)
                            refresh()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun NotesHomeScreen(
    notes: List<Note>,
    onAdd: () -> Unit,
    onEdit: (Note) -> Unit,
    onSettings: () -> Unit,
    onPin: (Note) -> Unit,
    onDelete: (Note) -> Unit
) {
    var searchOpen by remember { mutableStateOf(false) }
    var searchText by remember { mutableStateOf("") }
    val filtered = notes.filter {
        searchText.isBlank() ||
            it.title.contains(searchText, true) ||
            it.body.contains(searchText, true)
    }
    val pinned = filtered.filter { it.pinned }
    val regular = filtered.filterNot { it.pinned }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 22.dp, end = 12.dp, top = 26.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("פתקים", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                    Text(
                        if (notes.size == 1) "פתק אחד" else notes.size.toString() + " פתקים",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { searchOpen = !searchOpen; searchText = "" }) {
                    Icon(Icons.Default.Search, contentDescription = "חיפוש")
                }
                IconButton(onClick = onSettings) {
                    Icon(Icons.Default.Settings, contentDescription = "הגדרות")
                }
            }

            if (searchOpen) {
                OutlinedTextField(
                    value = searchText,
                    onValueChange = { searchText = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
                    singleLine = true,
                    placeholder = { Text("חיפוש בפתקים") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }
                )
            }

            if (notes.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(bottom = 70.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(54.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(12.dp))
                        Text("אין עדיין פתקים", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                        Text("הפתק הראשון שלך מחכה להיכתב", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else if (filtered.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("לא נמצאו פתקים", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 112.dp),
                    verticalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    if (pinned.isNotEmpty()) {
                        item { SectionLabel("מוצמדים") }
                        items(pinned, key = { it.id }) { note ->
                            NoteRow(note, onClick = { onEdit(note) }, onPin = { onPin(note) }, onDelete = { onDelete(note) })
                        }
                    }
                    if (regular.isNotEmpty()) {
                        item { SectionLabel(if (pinned.isNotEmpty()) "פתקים" else "כל הפתקים") }
                        items(regular, key = { it.id }) { note ->
                            NoteRow(note, onClick = { onEdit(note) }, onPin = { onPin(note) }, onDelete = { onDelete(note) })
                        }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = onAdd,
            modifier = Modifier.align(Alignment.BottomEnd).padding(22.dp),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ) {
            Icon(Icons.Default.Add, contentDescription = "פתק חדש")
        }
    }
}

@Composable
private fun SectionLabel(label: String) {
    Text(
        label,
        modifier = Modifier.padding(start = 5.dp, top = 5.dp, bottom = 2.dp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
private fun NoteRow(
    note: Note,
    onClick: () -> Unit,
    onPin: () -> Unit,
    onDelete: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(15.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 15.dp, end = 7.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    note.title.ifBlank { note.body.lineSequence().firstOrNull()?.take(48) ?: "פתק ללא כותרת" },
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (note.title.isNotBlank() && note.body.isNotBlank()) {
                    Text(note.body.replace('\n', ' '), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(formatDate(note.updatedAt), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (note.folder != "פתקים") {
                        Text("  ·  " + note.folder, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (note.pinned) {
                Icon(Icons.Default.PushPin, contentDescription = "פתק מוצמד", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(17.dp))
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "אפשרויות")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(if (note.pinned) "בטל הצמדה" else "הצמד למעלה") },
                        leadingIcon = { Icon(Icons.Default.PushPin, contentDescription = null) },
                        onClick = { menuOpen = false; onPin() }
                    )
                    DropdownMenuItem(
                        text = { Text("מחק פתק") },
                        leadingIcon = { Icon(Icons.Default.DeleteOutline, contentDescription = null) },
                        onClick = { menuOpen = false; onDelete() }
                    )
                }
            }
        }
    }
}

@Composable
private fun NoteEditorScreen(
    note: Note?,
    onBack: (Note) -> Unit,
    onDelete: (Long) -> Unit
) {
    var title by remember(note?.id) { mutableStateOf(note?.title ?: "") }
    var body by remember(note?.id) { mutableStateOf(note?.body ?: "") }
    var folder by remember(note?.id) { mutableStateOf(note?.folder ?: "פתקים") }
    var pinned by remember(note?.id) { mutableStateOf(note?.pinned ?: false) }
    var confirmDelete by remember { mutableStateOf(false) }

    fun saveAndLeave() {
        onBack(
            Note(
                id = note?.id ?: 0,
                title = title.trim(),
                body = body,
                folder = folder.trim().ifBlank { "פתקים" },
                pinned = pinned,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    BackHandler { saveAndLeave() }

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { saveAndLeave() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "חזרה")
            }
            Spacer(Modifier.weight(1f))
            if (note != null) {
                IconButton(onClick = { pinned = !pinned }) {
                    Icon(
                        Icons.Default.PushPin,
                        contentDescription = "הצמדה",
                        tint = if (pinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { confirmDelete = true }) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "מחק")
                }
            }
            TextButton(onClick = { saveAndLeave() }) { Text("סיום", fontWeight = FontWeight.SemiBold) }
        }

        if (note != null) {
            Text(formatDate(note.updatedAt), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
        }

        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("כותרת") },
            textStyle = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            singleLine = true
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = body,
            onValueChange = { body = it },
            modifier = Modifier.fillMaxWidth().weight(1f),
            placeholder = { Text("מתחילים לכתוב...") },
            textStyle = MaterialTheme.typography.bodyLarge.copy(lineHeight = 25.sp),
            minLines = 8
        )
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = {
                body += (if (body.isBlank() || body.endsWith("\n")) "" else "\n") + "☐ "
            }) {
                Icon(Icons.Default.CheckBox, contentDescription = null)
                Spacer(Modifier.width(5.dp))
                Text("רשימת משימות")
            }
            Spacer(Modifier.weight(1f))
            Icon(Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(5.dp))
            BasicTextField(
                value = folder,
                onValueChange = { folder = it },
                singleLine = true,
                textStyle = MaterialTheme.typography.labelMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.End),
                modifier = Modifier.width(92.dp)
            )
        }
    }

    if (confirmDelete && note != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("למחוק את הפתק?") },
            text = { Text("הפתק יימחק מהמכשיר.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete(note.id) }) { Text("מחק") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("ביטול") }
            }
        )
    }
}

@Composable
private fun SettingsScreen(
    noteCount: Int,
    themeMode: String,
    accentName: String,
    accentColors: Map<String, Color>,
    onThemeChange: (String) -> Unit,
    onAccentChange: (String) -> Unit,
    onBack: () -> Unit,
    onExportJson: () -> Unit,
    onExportText: () -> Unit,
    onExportZip: () -> Unit,
    onImport: () -> Unit,
    onDriveBackup: () -> Unit,
    onDriveRestore: () -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "חזרה")
            }
            Text("הגדרות", fontSize = 25.sp, fontWeight = FontWeight.Bold)
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                SettingsSectionTitle("מראה", Icons.Default.Palette)
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text("ערכת נושא", fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ThemeOption("מערכת", "system", themeMode, onThemeChange)
                            ThemeOption("בהיר", "light", themeMode, onThemeChange)
                            ThemeOption("כהה", "dark", themeMode, onThemeChange)
                        }
                        Spacer(Modifier.height(18.dp))
                        Text("צבע הדגשה", fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            accentColors.forEach { (name, color) ->
                                Box(
                                    modifier = Modifier.size(34.dp).clip(CircleShape).background(color)
                                        .clickable { onAccentChange(name) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (name == accentName) {
                                        Icon(Icons.Default.Check, contentDescription = "נבחר", tint = Color(0xFF202020), modifier = Modifier.size(19.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
            item {
                SettingsSectionTitle("גיבוי וייצוא", Icons.Default.Archive)
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text("ייצוא פתקים", fontWeight = FontWeight.SemiBold)
                        Text("שמירת כל הפתקים בקובץ לבחירתך", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = onExportJson, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Description, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("ייצוא JSON")
                        }
                        Button(onClick = onExportText, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Description, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("ייצוא TXT")
                        }
                        Button(onClick = onExportZip, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Archive, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("ייצוא ZIP · JSON + TXT")
                        }
                        TextButton(onClick = onImport, modifier = Modifier.fillMaxWidth()) {
                            Text("ייבוא גיבוי JSON")
                        }
                    }
                }
            }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CloudUpload, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(9.dp))
                            Text("Google Drive", fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                        }
                        Spacer(Modifier.height(5.dp))
                        Text(
                            "הגיבוי נשמר באזור הפרטי של האפליקציה ב-Drive. נדרשת הרשאה חד-פעמית לחשבון Google.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = onDriveBackup, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.CloudUpload, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("גבה עכשיו ל-Drive")
                        }
                        TextButton(onClick = onDriveRestore, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.CloudDownload, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("שחזר מ-Drive")
                        }
                    }
                }
            }
            item {
                Text(
                    "NoteApple · גרסה 1.0.0\n" + noteCount + " פתקים שמורים במכשיר",
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun SettingsSectionTitle(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 3.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(8.dp))
        Text(title, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
    }
}

@Composable
private fun ThemeOption(
    label: String,
    value: String,
    selected: String,
    onSelect: (String) -> Unit
) {
    Surface(
        modifier = Modifier.width(90.dp).height(40.dp).clip(RoundedCornerShape(12.dp))
            .clickable { onSelect(value) },
        color = if (selected == value) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                label,
                color = if (selected == value) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                fontWeight = if (selected == value) FontWeight.Bold else FontWeight.Normal,
                fontSize = 13.sp
            )
        }
    }
}

private fun accentColors(): Map<String, Color> = linkedMapOf(
    "gold" to Color(0xFFE8B83F),
    "blue" to Color(0xFF69A7E8),
    "green" to Color(0xFF65B987),
    "pink" to Color(0xFFE28AA9),
    "purple" to Color(0xFFAA91E8),
    "orange" to Color(0xFFE99555)
)

private fun formatDate(timestamp: Long): String =
    SimpleDateFormat("d MMM · HH:mm", Locale("he", "IL")).format(Date(timestamp))

private fun writeExport(context: Context, uri: Uri, bytes: ByteArray) {
    try {
        context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
        Toast.makeText(context, "הקובץ נשמר בהצלחה", Toast.LENGTH_SHORT).show()
    } catch (error: Exception) {
        Toast.makeText(context, "לא ניתן לשמור את הקובץ", Toast.LENGTH_LONG).show()
    }
}

private fun makeZip(notes: List<Note>): ByteArray {
    val output = ByteArrayOutputStream()
    ZipOutputStream(output).use { zip ->
        zip.putNextEntry(ZipEntry("notes.json"))
        zip.write(notesToJson(notes).toByteArray(Charsets.UTF_8))
        zip.closeEntry()
        zip.putNextEntry(ZipEntry("notes.txt"))
        zip.write(notesToText(notes).toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }
    return output.toByteArray()
}

private object DriveBackup {
    fun upload(accessToken: String, json: String) {
        val boundary = "noteapple-" + UUID.randomUUID().toString().replace("-", "")
        val metadata = JSONObject()
            .put("name", "NoteApple-backup.json")
            .put("mimeType", "application/json")
            .put("parents", JSONArray().put("appDataFolder"))
        val body = buildString {
            append("--").append(boundary).append("\r\n")
            append("Content-Type: application/json; charset=UTF-8\r\n\r\n")
            append(metadata.toString()).append("\r\n")
            append("--").append(boundary).append("\r\n")
            append("Content-Type: application/json; charset=UTF-8\r\n\r\n")
            append(json).append("\r\n")
            append("--").append(boundary).append("--\r\n")
        }
        request(
            "POST",
            "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id,name",
            accessToken,
            "multipart/related; boundary=" + boundary,
            body.toByteArray(Charsets.UTF_8)
        )
    }

    fun download(accessToken: String): String {
        val query = URLEncoder.encode("name='NoteApple-backup.json' and trashed=false", "UTF-8")
        val listUrl = "https://www.googleapis.com/drive/v3/files?spaces=appDataFolder&q=" +
            query + "&fields=files(id,name,modifiedTime)&orderBy=modifiedTime%20desc&pageSize=1"
        val response = JSONObject(request("GET", listUrl, accessToken, null, null))
        val files = response.optJSONArray("files") ?: JSONArray()
        if (files.length() == 0) throw IOException("לא נמצא גיבוי בחשבון Drive הזה")
        val id = files.getJSONObject(0).getString("id")
        return request(
            "GET",
            "https://www.googleapis.com/drive/v3/files/" + id + "?alt=media",
            accessToken,
            null,
            null
        )
    }

    private fun request(
        method: String,
        address: String,
        token: String,
        contentType: String?,
        body: ByteArray?
    ): String {
        val connection = (URL(address).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 20000
            readTimeout = 30000
            setRequestProperty("Authorization", "Bearer " + token)
            if (contentType != null) setRequestProperty("Content-Type", contentType)
            if (body != null) doOutput = true
        }
        try {
            if (body != null) connection.outputStream.use { it.write(body) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            if (code !in 200..299) {
                throw IOException("Drive HTTP " + code + ": " + response.take(180))
            }
            return response
        } finally {
            connection.disconnect()
        }
    }
}
