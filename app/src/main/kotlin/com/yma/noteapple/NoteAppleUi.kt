package com.yma.noteapple

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private const val PAGE_FOLDERS = "folders"
private const val PAGE_NOTES = "notes"
private const val PAGE_EDITOR = "editor"
private const val PAGE_SETTINGS = "settings"
internal const val SMART_ALL = "__all__"
internal const val SMART_TRASH = "__trash__"

@Composable
fun NoteAppleApp(activity: MainActivity) {
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences("noteapple_v3", Context.MODE_PRIVATE) }
    var themeMode by remember { mutableStateOf(preferences.getString("theme", "dark") ?: "dark") }
    var accentKey by remember { mutableStateOf(preferences.getString("accent", "yellow") ?: "yellow") }
    val accents = remember {
        linkedMapOf(
            "yellow" to Color(0xFFFFC629), "blue" to Color(0xFF6BA8F0),
            "green" to Color(0xFF6CBF91), "pink" to Color(0xFFE895B1),
            "purple" to Color(0xFFAE98EB), "orange" to Color(0xFFEDA05E)
        )
    }
    val accent = accents[accentKey] ?: accents.getValue("yellow")
    val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
    val isDark = when (themeMode) { "dark" -> true; "light" -> false; else -> systemDark }
    val palette = if (isDark) {
        darkColorScheme(
            primary = accent, onPrimary = Color(0xFF201D16), secondary = accent,
            background = Color(0xFF08090A), surface = Color(0xFF191A1D),
            surfaceVariant = Color(0xFF25262A), onSurface = Color(0xFFF4F3EF),
            onSurfaceVariant = Color(0xFFACABA6)
        )
    } else {
        lightColorScheme(
            primary = Color(0xFF9B7416), onPrimary = Color.White, secondary = accent,
            background = Color(0xFFF6F5F0), surface = Color.White,
            surfaceVariant = Color(0xFFECEAE4), onSurface = Color(0xFF1E1E1E),
            onSurfaceVariant = Color(0xFF73716B)
        )
    }

    MaterialTheme(colorScheme = palette) {
        val notes = remember { mutableStateListOf<NoteItem>() }
        val folders = remember { mutableStateListOf<NoteFolder>() }
        var page by remember { mutableStateOf(PAGE_FOLDERS) }
        var folderId by remember { mutableStateOf(DEFAULT_FOLDER_ID) }
        var draft by remember { mutableStateOf(NoteItem()) }
        var search by remember { mutableStateOf("") }
        var lockedNote by remember { mutableStateOf<NoteItem?>(null) }
        var editorUnlockPassword by remember { mutableStateOf("") }
        var importSnapshot by remember { mutableStateOf<BackupSnapshot?>(null) }
        var importLabel by remember { mutableStateOf("") }
        var importError by remember { mutableStateOf("") }
        var appLocked by remember { mutableStateOf(false) }
        var appUnlockError by remember { mutableStateOf("") }

        fun refresh() {
            notes.clear()
            notes.addAll(activity.database.notes(includeTrash = true))
            folders.clear()
            folders.addAll(activity.database.folders())
        }
        fun goEditor(note: NoteItem) { draft = note; page = PAGE_EDITOR }
        fun protectedNote(note: NoteItem): NoteItem =
            if (editorUnlockPassword.isNotBlank() && !note.locked) NoteSecurity.lock(note, editorUnlockPassword.toCharArray()) else note
        fun autoSave(note: NoteItem) {
            if (note.id > 0 || note.title.isNotBlank() || note.bodyHtml.isNotBlank() || note.checklist.isNotEmpty() || note.attachments.isNotEmpty()) {
                draft = activity.database.save(protectedNote(note).copy(updatedAt = System.currentTimeMillis()))
                refresh()
            }
        }
        fun saveNote(note: NoteItem) {
            draft = activity.database.save(protectedNote(note).copy(updatedAt = System.currentTimeMillis()))
            editorUnlockPassword = ""
            refresh()
            page = PAGE_NOTES
        }
        fun finishImport(snapshot: BackupSnapshot, replace: Boolean) {
            runCatching {
                if (replace) activity.database.replaceAll(snapshot) else activity.database.merge(snapshot)
                refresh()
                importSnapshot = null
                Toast.makeText(context, "יובאו " + snapshot.notes.size + " פתקים", Toast.LENGTH_LONG).show()
            }.onFailure { importError = it.localizedMessage ?: "שגיאה בשמירת הגיבוי" }
        }

        LaunchedEffect(Unit) {
            refresh()
            if (!preferences.getString("app_pin_hash", null).isNullOrBlank()) appLocked = true
            if (activity.intent?.getBooleanExtra("new_note", false) == true) {
                goEditor(NoteItem())
                activity.intent?.removeExtra("new_note")
            }
            val shared = activity.intent?.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
            if (!shared.isNullOrBlank()) {
                goEditor(NoteItem(title = "פתק משותף", bodyHtml = "<p>" + escapeNoteHtml(shared).replace("\n", "<br>") + "</p>"))
                activity.intent?.removeExtra(Intent.EXTRA_TEXT)
            }
        }

        val jsonExport = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            if (uri != null) runCatching {
                val bytes = BackupCodec.encode(context, activity.database.snapshot()).toByteArray(Charsets.UTF_8)
                exportFile(context, uri, bytes)
            }.onFailure { Toast.makeText(context, it.localizedMessage ?: "שגיאת JSON", Toast.LENGTH_LONG).show() }
        }
        val textExport = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
            if (uri != null) exportFile(context, uri, NoteExports.plainText(notes.toList()).toByteArray(Charsets.UTF_8))
        }
        val zipExport = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
            if (uri != null) runCatching {
                exportFile(context, uri, NoteExports.zip(context, activity.database.snapshot()))
            }.onFailure { Toast.makeText(context, it.localizedMessage ?: "שגיאת ZIP", Toast.LENGTH_LONG).show() }
        }
        val mdExport = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/markdown")) { uri ->
            if (uri != null) exportFile(context, uri, NoteExports.markdown(notes.toList()).toByteArray(Charsets.UTF_8))
        }
        val htmlExport = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/html")) { uri ->
            if (uri != null) exportFile(context, uri, NoteExports.html(notes.toList()).toByteArray(Charsets.UTF_8))
        }
        val pdfExport = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
            if (uri != null) runCatching {
                exportFile(context, uri, NoteExports.pdf(notes.toList()))
            }.onFailure { Toast.makeText(context, it.localizedMessage ?: "שגיאת PDF", Toast.LENGTH_LONG).show() }
        }
        val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) runCatching {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: throw IllegalArgumentException("לא ניתן לקרוא את הקובץ")
                val name = uri.lastPathSegment.orEmpty().lowercase()
                val snapshot = when {
                    bytes.size > 1 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte() -> decodeZipBackup(context, bytes)
                    name.endsWith(".md") || name.endsWith(".markdown") ->
                        BackupSnapshot(folders.toList(), listOf(NoteExports.importMarkdown(bytes.toString(Charsets.UTF_8), folderId)))
                    else -> BackupCodec.decode(context, bytes.toString(Charsets.UTF_8))
                }
                importSnapshot = snapshot
                importLabel = "הקובץ מכיל " + snapshot.notes.size + " פתקים ו-" + snapshot.folders.size + " תיקיות."
                importError = ""
            }.onFailure { importError = it.localizedMessage ?: "קובץ הגיבוי אינו תקין" }
        }

        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            when (page) {
                PAGE_FOLDERS -> FolderScreen(
                    folders, notes.toList(),
                    onOpen = { folderId = it; search = ""; page = PAGE_NOTES },
                    onNewFolder = { name, parent ->
                        activity.database.saveFolder(NoteFolder(name = name, parentId = parent)); refresh()
                    },
                    onSettings = { page = PAGE_SETTINGS },
                    onTrash = { folderId = SMART_TRASH; page = PAGE_NOTES },
                    onNewNote = { folderId = DEFAULT_FOLDER_ID; goEditor(NoteItem()) }
                )
                PAGE_NOTES -> NotesScreen(
                    folderId, folders.toList(), notes.toList(), search,
                    onSearch = { search = it },
                    onBack = { page = PAGE_FOLDERS },
                    onNewNote = {
                        val target = if (folderId == SMART_ALL || folderId == SMART_TRASH || folderId.startsWith("tag:")) DEFAULT_FOLDER_ID else folderId
                        goEditor(NoteItem(folderId = target))
                    },
                    onOpen = { note -> if (note.locked) lockedNote = note else goEditor(note) },
                    onPin = { note -> activity.database.save(note.copy(pinned = !note.pinned)); refresh() },
                    onTrash = { note -> activity.database.moveToTrash(note); refresh() },
                    onRestore = { note -> activity.database.restore(note); refresh() },
                    onDeleteForever = { note -> activity.database.deleteForever(note.id); refresh() },
                    onEmptyTrash = { activity.database.emptyTrash(); refresh() }
                )
                PAGE_EDITOR -> NoteEditorScreen(
                    draft, folders.toList(), onSave = { saveNote(it) }, onAutoSave = { autoSave(it) },
                    onBack = { page = PAGE_NOTES },
                    onDelete = { activity.database.moveToTrash(it); refresh(); page = PAGE_NOTES }
                )
                else -> SettingsScreen(
                    themeMode, accentKey, accents, notes.count { !it.trashed },
                    onTheme = { themeMode = it; preferences.edit().putString("theme", it).apply() },
                    onAccent = { accentKey = it; preferences.edit().putString("accent", it).apply() },
                    onBack = { page = PAGE_FOLDERS },
                    onJson = { jsonExport.launch("NoteApple-Backup.json") },
                    onTxt = { textExport.launch("NoteApple-Notes.txt") },
                    onZip = { zipExport.launch("NoteApple-Full-Backup.zip") },
                    onMd = { mdExport.launch("NoteApple-Notes.md") },
                    onHtml = { htmlExport.launch("NoteApple-Notes.html") },
                    onPdf = { pdfExport.launch("NoteApple-Notes.pdf") },
                    onImport = { importLauncher.launch(arrayOf("application/json", "application/zip", "text/markdown", "text/plain")) },
                    onDriveBackup = {
                        activity.requestDrive(false) { result ->
                            result.onSuccess { Toast.makeText(context, "הגיבוי הושלם ב-Google Drive", Toast.LENGTH_LONG).show() }
                                .onFailure { Toast.makeText(context, it.localizedMessage ?: "גיבוי Drive נכשל", Toast.LENGTH_LONG).show() }
                        }
                    },
                    onDriveRestore = {
                        activity.requestDrive(true) { result ->
                            result.onSuccess {
                                importSnapshot = it
                                importLabel = "נמצא גיבוי עם " + it.notes.size + " פתקים. בחר כיצד לשחזר."
                            }.onFailure { Toast.makeText(context, it.localizedMessage ?: "שחזור Drive נכשל", Toast.LENGTH_LONG).show() }
                        }
                    },
                    onSetAppLock = { pin ->
                        preferences.edit().putString("app_pin_hash", NoteSecurity.createPasscodeHash(pin)).apply()
                        Toast.makeText(context, "נעילת האפליקציה הופעלה", Toast.LENGTH_SHORT).show()
                    },
                    onRemoveAppLock = { preferences.edit().remove("app_pin_hash").apply() }
                )
            }
        }

        if (lockedNote != null) {
            PasscodeDialog(
                title = "פתק נעול", confirm = "פתיחה",
                onDismiss = { lockedNote = null },
                onConfirm = { pass ->
                    val selected = lockedNote
                    if (selected != null) {
                        runCatching { NoteSecurity.unlock(selected, pass.toCharArray()) }
                            .onSuccess { lockedNote = null; editorUnlockPassword = pass; goEditor(it) }
                            .onFailure { Toast.makeText(context, "הסיסמה שגויה", Toast.LENGTH_SHORT).show() }
                    }
                }
            )
        }
        if (importSnapshot != null) {
            AlertDialog(
                onDismissRequest = { importSnapshot = null },
                title = { Text("ייבוא גיבוי") },
                text = { Text(importLabel + "\n\nמיזוג מוסיף פתקים בלי למחוק קיימים. החלפה מוחקת את הנתונים המקומיים לפני השחזור.") },
                confirmButton = { TextButton(onClick = { finishImport(importSnapshot!!, false) }) { Text("מזג") } },
                dismissButton = {
                    Row {
                        TextButton(onClick = { finishImport(importSnapshot!!, true) }) { Text("החלף הכל") }
                        TextButton(onClick = { importSnapshot = null }) { Text("ביטול") }
                    }
                }
            )
        }
        if (importError.isNotBlank()) {
            AlertDialog(
                onDismissRequest = { importError = "" }, title = { Text("שגיאה בייבוא") },
                text = { Text(importError) },
                confirmButton = { TextButton(onClick = { importError = "" }) { Text("סגור") } }
            )
        }
        if (appLocked) {
            PasscodeDialog(
                title = "NoteApple נעולה", confirm = "פתיחה", onDismiss = {},
                error = appUnlockError,
                onConfirm = { pin ->
                    val saved = preferences.getString("app_pin_hash", "").orEmpty()
                    if (NoteSecurity.verifyPasscode(pin, saved)) {
                        appLocked = false
                        appUnlockError = ""
                    } else appUnlockError = "הקוד אינו נכון"
                }
            )
        }
    }
}

@Composable
private fun FolderScreen(
    folders: List<NoteFolder>,
    notes: List<NoteItem>,
    onOpen: (String) -> Unit,
    onNewFolder: (String, String?) -> Unit,
    onSettings: () -> Unit,
    onTrash: () -> Unit,
    onNewNote: () -> Unit
) {
    var creating by remember { mutableStateOf(false) }
    var folderName by remember { mutableStateOf("") }
    var parentId by remember { mutableStateOf<String?>(null) }
    var parentMenu by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(start = 22.dp, end = 13.dp, top = 28.dp, bottom = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Folders", fontSize = 32.sp, fontWeight = FontWeight.Bold)
                    Text("הפתקים שלך, מסודרים במקום אחד", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                }
                TextButton(onClick = { creating = true }) { Text("תיקייה +") }
                TextButton(onClick = onSettings) { Text("⋯") }
            }
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 100.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item { FolderCard("כל הפתקים", "כל מה ששמור במכשיר", notes.count { !it.trashed }, "▤") { onOpen(SMART_ALL) } }
                item { FolderCard("פתקים", "התיקייה הראשית", notes.count { it.folderId == DEFAULT_FOLDER_ID && !it.trashed }, "🗒") { onOpen(DEFAULT_FOLDER_ID) } }
                val roots = folders.filter { it.id != DEFAULT_FOLDER_ID && it.parentId == null }
                items(roots, key = { it.id }) { folder ->
                    FolderCard(folder.name, if (folder.smartQuery.isBlank()) "תיקייה" else "תיקייה חכמה",
                        notes.count { !it.trashed && matchesFolder(it, folder) }, folder.emoji) { onOpen(folder.id) }
                    folders.filter { it.parentId == folder.id }.forEach { child ->
                        FolderCard("　↳ " + child.name, "תת־תיקייה", notes.count { !it.trashed && it.folderId == child.id }, child.emoji) { onOpen(child.id) }
                    }
                }
                item {
                    Divider(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.padding(vertical = 12.dp))
                    FolderCard("נמחקו לאחרונה", "פריטים נמחקים אוטומטית אחרי 30 יום", notes.count { it.trashed }, "⌫", onTrash)
                }
                item {
                    val tags = notes.filterNot { it.trashed }.flatMap { it.tags }.distinct().sorted()
                    if (tags.isNotEmpty()) {
                        Text("תגיות", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 16.dp, bottom = 7.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            tags.take(7).forEach { tag ->
                                Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(50.dp), modifier = Modifier.clickable { onOpen("tag:" + tag) }) {
                                    Text("#" + tag, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
        FloatingActionButton(onClick = onNewNote, modifier = Modifier.align(Alignment.BottomEnd).padding(22.dp),
            containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary) {
            Text("＋", fontSize = 25.sp)
        }
    }

    if (creating) {
        AlertDialog(
            onDismissRequest = { creating = false; folderName = "" },
            title = { Text("תיקייה חדשה") },
            text = {
                Column {
                    OutlinedTextField(folderName, { folderName = it }, label = { Text("שם התיקייה") }, singleLine = true)
                    Spacer(Modifier.height(8.dp))
                    Box {
                        TextButton(onClick = { parentMenu = true }) {
                            val parentName = folders.firstOrNull { it.id == parentId }?.name ?: "ללא"
                            Text("תיקיית אב: " + parentName)
                        }
                        DropdownMenu(expanded = parentMenu, onDismissRequest = { parentMenu = false }) {
                            DropdownMenuItem(text = { Text("ללא") }, onClick = { parentId = null; parentMenu = false })
                            folders.forEach { f ->
                                DropdownMenuItem(text = { Text(f.name) }, onClick = { parentId = f.id; parentMenu = false })
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = {
                if (folderName.isNotBlank()) onNewFolder(folderName.trim(), parentId)
                creating = false; folderName = ""; parentId = null
            }) { Text("צור") } },
            dismissButton = { TextButton(onClick = { creating = false }) { Text("ביטול") } }
        )
    }
}

@Composable
private fun FolderCard(name: String, subtitle: String, count: Int, emoji: String, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(15.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(emoji, fontSize = 21.sp)
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Text(name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
            }
            Text(count.toString(), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            Spacer(Modifier.width(5.dp))
            Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 22.sp)
        }
    }
}

private fun matchesFolder(note: NoteItem, folder: NoteFolder): Boolean {
    if (folder.smartQuery.startsWith("tag:")) return folder.smartQuery.removePrefix("tag:") in note.tags
    return note.folderId == folder.id
}
