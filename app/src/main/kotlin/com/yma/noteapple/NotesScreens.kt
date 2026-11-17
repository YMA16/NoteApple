package com.yma.noteapple

import android.Manifest
import android.content.Context
import android.content.Intent
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.speech.RecognizerIntent
import android.text.Editable
import android.text.Html
import android.text.Spannable
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import android.widget.EditText
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

@Composable
fun NotesScreen(
    folderId: String,
    folders: List<NoteFolder>,
    notes: List<NoteItem>,
    search: String,
    onSearch: (String) -> Unit,
    onBack: () -> Unit,
    onNewNote: () -> Unit,
    onOpen: (NoteItem) -> Unit,
    onPin: (NoteItem) -> Unit,
    onTrash: (NoteItem) -> Unit,
    onRestore: (NoteItem) -> Unit,
    onDeleteForever: (NoteItem) -> Unit,
    onEmptyTrash: () -> Unit
) {
    var searchOpen by remember { mutableStateOf(false) }
    var sortMode by remember { mutableStateOf("date") }
    val folder = folders.firstOrNull { it.id == folderId }
    val base = when {
        folderId == SMART_TRASH -> notes.filter { it.trashed }
        folderId == SMART_ALL -> notes.filterNot { it.trashed }
        folderId.startsWith("tag:") -> notes.filter { !it.trashed && folderId.removePrefix("tag:") in it.tags }
        folder?.smartQuery?.startsWith("tag:") == true ->
            notes.filter { !it.trashed && folder.smartQuery.removePrefix("tag:") in it.tags }
        else -> notes.filter { !it.trashed && it.folderId == folderId }
    }
    val filtered = base.filter {
        search.isBlank() || it.title.contains(search, true) ||
            Html.fromHtml(it.bodyHtml, Html.FROM_HTML_MODE_COMPACT).toString().contains(search, true) ||
            it.tags.any { tag -> tag.contains(search, true) }
    }.let { items ->
        when (sortMode) {
            "title" -> items.sortedBy { it.title.lowercase(Locale.getDefault()) }
            "created" -> items.sortedByDescending { it.createdAt }
            else -> items.sortedWith(compareByDescending<NoteItem> { it.pinned }.thenByDescending { it.updatedAt })
        }
    }
    val pinned = filtered.filter { it.pinned && folderId != SMART_TRASH }
    val regular = filtered.filterNot { it.pinned && folderId != SMART_TRASH }
    val dateToday = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
    val title = when {
        folderId == SMART_TRASH -> "Recently Deleted"
        folderId == SMART_ALL -> "All Notes"
        folderId.startsWith("tag:") -> "#" + folderId.removePrefix("tag:")
        else -> folder?.name ?: "Notes"
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 15.dp, end = 12.dp, top = 18.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onBack) { Text("‹ Folders") }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { searchOpen = !searchOpen }) { Text("⌕") }
                Box {
                    var sortMenu by remember { mutableStateOf(false) }
                    TextButton(onClick = { sortMenu = true }) { Text("⋯") }
                    DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                        DropdownMenuItem(text = { Text("תאריך עדכון") }, onClick = { sortMode = "date"; sortMenu = false })
                        DropdownMenuItem(text = { Text("תאריך יצירה") }, onClick = { sortMode = "created"; sortMenu = false })
                        DropdownMenuItem(text = { Text("שם הפתק") }, onClick = { sortMode = "title"; sortMenu = false })
                    }
                }
            }
            Text(title, modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp), fontSize = 31.sp, fontWeight = FontWeight.Bold)
            if (searchOpen) {
                OutlinedTextField(
                    value = search, onValueChange = onSearch,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 5.dp),
                    placeholder = { Text("חיפוש בפתקים") }, singleLine = true
                )
            }

            if (filtered.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(bottom = 70.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (folderId == SMART_TRASH) "הסל ריק" else "אין פתקים עדיין", fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                        Text(if (search.isBlank()) "צור פתק חדש כדי להתחיל" else "לא נמצאו תוצאות", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                    }
                }
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 15.dp, vertical = 6.dp, bottom = 100.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (pinned.isNotEmpty()) {
                        item { Text("Pinned", color = MaterialTheme.colorScheme.primary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 7.dp, top = 6.dp)) }
                        items(pinned, key = { it.id }) { note ->
                            NoteCard(note, folders, folderId == SMART_TRASH, onOpen, onPin, onTrash, onRestore, onDeleteForever)
                        }
                    }
                    if (regular.isNotEmpty()) {
                        item { Text(if (pinned.isNotEmpty()) "Notes" else "Notes", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 7.dp, top = 7.dp)) }
                        items(regular, key = { it.id }) { note ->
                            NoteCard(note, folders, folderId == SMART_TRASH, onOpen, onPin, onTrash, onRestore, onDeleteForever)
                        }
                    }
                }
            }
        }
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 20.dp, vertical = 13.dp)) {
            if (folderId == SMART_TRASH && filtered.isNotEmpty()) {
                TextButton(onClick = onEmptyTrash, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("רוקן סל מחזור") }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(filtered.size.toString() + if (filtered.size == 1) " Note" else " Notes", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                Spacer(Modifier.weight(1f))
                FloatingActionButton(
                    onClick = onNewNote,
                    modifier = Modifier.size(52.dp),
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = CircleShape
                ) { Text("✎", fontSize = 22.sp) }
            }
        }
    }
}

@Composable
private fun NoteCard(
    note: NoteItem,
    folders: List<NoteFolder>,
    inTrash: Boolean,
    onOpen: (NoteItem) -> Unit,
    onPin: (NoteItem) -> Unit,
    onTrash: (NoteItem) -> Unit,
    onRestore: (NoteItem) -> Unit,
    onDeleteForever: (NoteItem) -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    val folderName = folders.firstOrNull { it.id == note.folderId }?.name ?: "פתקים"
    val preview = if (note.locked) "פתק נעול" else
        Html.fromHtml(note.bodyHtml, Html.FROM_HTML_MODE_COMPACT).toString().trim().replace("\n", " ").take(96)
    Card(
        Modifier.fillMaxWidth().clickable { onOpen(note) },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 7.dp, top = 11.dp, bottom = 11.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(note.title.ifBlank { preview.ifBlank { "פתק ללא כותרת" } }, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (note.pinned) Text("  📌", fontSize = 10.sp)
                    if (note.locked) Text("  🔒", fontSize = 10.sp)
                }
                if (note.title.isNotBlank() && preview.isNotBlank()) {
                    Text(preview, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(
                    formatNoteDate(note.updatedAt) + "   ·   " + folderName,
                    fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (note.attachments.isNotEmpty() || note.checklist.isNotEmpty()) {
                    Text(
                        (if (note.attachments.isNotEmpty()) "📎 " + note.attachments.size + "  " else "") +
                            (if (note.checklist.isNotEmpty()) "☑ " + note.checklist.count { it.checked } + "/" + note.checklist.size else ""),
                        fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Box {
                TextButton(onClick = { menu = true }) { Text("⋯", fontSize = 19.sp) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    if (!inTrash) {
                        DropdownMenuItem(text = { Text(if (note.pinned) "בטל הצמדה" else "הצמד למעלה") }, onClick = { menu = false; onPin(note) })
                        DropdownMenuItem(text = { Text("מחק") }, onClick = { menu = false; onTrash(note) })
                    } else {
                        DropdownMenuItem(text = { Text("שחזר") }, onClick = { menu = false; onRestore(note) })
                        DropdownMenuItem(text = { Text("מחק לצמיתות") }, onClick = { menu = false; onDeleteForever(note) })
                    }
                }
            }
        }
    }
}

@Composable
fun NoteEditorScreen(
    note: NoteItem,
    folders: List<NoteFolder>,
    onSave: (NoteItem) -> Unit,
    onAutoSave: (NoteItem) -> Unit,
    onBack: () -> Unit,
    onDelete: (NoteItem) -> Unit
) {
    val context = LocalContext.current
    var title by remember(note.id) { mutableStateOf(note.title) }
    var body by remember(note.id) { mutableStateOf(Html.fromHtml(note.bodyHtml, Html.FROM_HTML_MODE_COMPACT).toString()) }
    var html by remember(note.id) { mutableStateOf(note.bodyHtml) }
    val checklist = remember(note.id) { mutableStateListOf<ChecklistItem>().apply { addAll(note.checklist) } }
    val attachments = remember(note.id) { mutableStateListOf<NoteAttachment>().apply { addAll(note.attachments) } }
    var tags by remember(note.id) { mutableStateOf(note.tags.joinToString(" ")) }
    var folderId by remember(note.id) { mutableStateOf(note.folderId) }
    var background by remember(note.id) { mutableStateOf(note.background) }
    var pinned by remember(note.id) { mutableStateOf(note.pinned) }
    var showChecklistInput by remember { mutableStateOf(false) }
    var checklistText by remember { mutableStateOf("") }
    var showLockDialog by remember { mutableStateOf(false) }
    var showMath by remember { mutableStateOf(false) }
    var mathInput by remember { mutableStateOf("") }
    var formatAction by remember { mutableStateOf("") }
    var formatToken by remember { mutableIntStateOf(0) }
    var autoSaveEnabled by remember { mutableStateOf(true) }
    var lastSaved by remember { mutableStateOf("") }
    var recording by remember { mutableStateOf(false) }
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var recordPath by remember { mutableStateOf("") }

    fun currentNote(): NoteItem {
        val allText = body + "\n" + html
        val foundTags = Regex("#([\\p{L}\\p{N}_-]+)").findAll(allText).map { it.groupValues[1] }.toList()
        val explicitTags = tags.split(Regex("[,\\s]+")).map { it.removePrefix("#").trim() }.filter { it.isNotBlank() }
        return note.copy(
            title = title.trim(),
            bodyHtml = html.ifBlank { "<p>" + escapeNoteHtml(body).replace("\n", "<br>") + "</p>" },
            folderId = folderId,
            tags = (explicitTags + foundTags).distinct(),
            checklist = checklist.toList(),
            attachments = attachments.toList(),
            pinned = pinned,
            background = background,
            updatedAt = System.currentTimeMillis()
        )
    }

    LaunchedEffect(title, body, html, tags, folderId, checklist.toList(), attachments.toList(), pinned, background, autoSaveEnabled) {
        if (autoSaveEnabled && (note.id > 0 || title.isNotBlank() || body.isNotBlank() || html.isNotBlank() || checklist.isNotEmpty() || attachments.isNotEmpty())) {
            delay(700)
            val item = currentNote()
            onAutoSave(item)
            lastSaved = "נשמר"
        }
    }
    BackHandler { onSave(currentNote()) }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
                val displayName = queryDisplayName(context, uri)
                val dir = File(context.filesDir, "attachments").apply { mkdirs() }
                val target = File(dir, UUID.randomUUID().toString() + "_" + displayName.replace(Regex("[^A-Za-z0-9._-]"), "_"))
                context.contentResolver.openInputStream(uri)?.use { input -> target.outputStream().use { output -> input.copyTo(output) } }
                    ?: throw IllegalArgumentException("לא ניתן לקרוא את הקובץ")
                attachments.add(NoteAttachment(name = displayName, mimeType = mime, path = target.absolutePath, size = target.length()))
                Toast.makeText(context, "הקובץ צורף", Toast.LENGTH_SHORT).show()
            }.onFailure { Toast.makeText(context, it.localizedMessage ?: "שגיאה בצירוף קובץ", Toast.LENGTH_LONG).show() }
        }
    }
    val speechLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val words = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (!words.isNullOrBlank()) {
            body = if (body.isBlank()) words else body + "\n" + words
            html = "<p>" + escapeNoteHtml(body).replace("\n", "<br>") + "</p>"
        }
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startVoiceRecorder(context, recorderSetter = { recorder = it }, pathSetter = { recordPath = it }, recordingSetter = { recording = it })
        else Toast.makeText(context, "נדרשת הרשאת מיקרופון", Toast.LENGTH_SHORT).show()
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 9.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { onSave(currentNote()) }) { Text("‹ חזרה") }
            Spacer(Modifier.weight(1f))
            Text(lastSaved, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
            TextButton(onClick = { pinned = !pinned }) { Text(if (pinned) "📌" else "◇") }
            TextButton(onClick = { showLockDialog = true }) { Text("🔒") }
            TextButton(onClick = { shareNote(context, currentNote()) }) { Text("↗") }
            TextButton(onClick = { onSave(currentNote()) }) { Text("✓", color = MaterialTheme.colorScheme.primary, fontSize = 19.sp) }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            listOf("B", "I", "U", "S", "H", "•", "☑", "▦").forEach { symbol ->
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.clickable {
                        formatAction = when (symbol) {
                            "B" -> "bold"; "I" -> "italic"; "U" -> "underline"; "S" -> "strike"
                            "H" -> "highlight"; "•" -> "bullet"; "☑" -> "checklist"; else -> "table"
                        }
                        formatToken++
                        if (symbol == "☑") showChecklistInput = true
                        if (symbol == "▦") {
                            body += "\n|  |  |\n|--|--|\n|  |  |\n"
                            html = "<p>" + escapeNoteHtml(body).replace("\n", "<br>") + "</p>"
                        }
                    }
                ) {
                    Text(symbol, modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { showMath = true }) { Text("Σ") }
        }
        OutlinedTextField(
            value = title, onValueChange = { title = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            singleLine = true, placeholder = { Text("כותרת") },
            textStyle = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
        )
        Row(Modifier.fillMaxWidth().padding(horizontal = 17.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(formatNoteDate(note.updatedAt), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
            Spacer(Modifier.weight(1f))
            var folderMenu by remember { mutableStateOf(false) }
            Box {
                TextButton(onClick = { folderMenu = true }) {
                    Text(folders.firstOrNull { it.id == folderId }?.name ?: "פתקים", fontSize = 11.sp)
                }
                DropdownMenu(expanded = folderMenu, onDismissRequest = { folderMenu = false }) {
                    folders.forEach { folder ->
                        DropdownMenuItem(text = { Text(folder.name) }, onClick = { folderId = folder.id; folderMenu = false })
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("default" to "רגיל", "cream" to "קרם", "lavender" to "סגלגל", "blue" to "כחול", "green" to "ירוק").forEach { (key, label) ->
                val color = when (key) {
                    "cream" -> Color(0xFF594D31); "lavender" -> Color(0xFF9D86C5)
                    "blue" -> Color(0xFF5F97CC); "green" -> Color(0xFF5EA984)
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                Box(Modifier.size(24.dp).background(color, CircleShape).clickable { background = key })
            }
        }
        HtmlRichEditor(
            html = html, onHtmlChange = { html = it; body = Html.fromHtml(it, Html.FROM_HTML_MODE_COMPACT).toString() },
            action = formatAction, actionToken = formatToken,
            modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 16.dp, vertical = 8.dp)
        )
        if (checklist.isNotEmpty()) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Text("To‑Do", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                checklist.forEachIndexed { index, item ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = item.checked, onCheckedChange = { checked ->
                            checklist[index] = item.copy(checked = checked)
                            val sorted = checklist.sortedBy { it.checked }
                            checklist.clear(); checklist.addAll(sorted)
                        })
                        BasicTextField(item.text, { checklist[index] = item.copy(text = it) }, modifier = Modifier.weight(1f), textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface))
                        TextButton(onClick = { checklist.removeAt(index) }) { Text("×") }
                    }
                }
            }
        }
        if (attachments.isNotEmpty()) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Text("קבצים מצורפים", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                attachments.forEach { attachment ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("📎 " + attachment.name, modifier = Modifier.weight(1f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        TextButton(onClick = { openAttachment(context, attachment) }) { Text("פתח") }
                        TextButton(onClick = { attachments.remove(attachment) }) { Text("×") }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { filePicker.launch(arrayOf("*/*")) }) { Text("📎 קובץ") }
            TextButton(onClick = { filePicker.launch(arrayOf("image/*")) }) { Text("▧ תמונה") }
            TextButton(onClick = {
                if (!recording) permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                else stopVoiceRecorder(recorder, recordPath, attachments) { recorder = null; recording = false }
            }) { Text(if (recording) "■ עצור" else "● הקלט") }
            TextButton(onClick = {
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
                    putExtra(RecognizerIntent.EXTRA_PROMPT, "דבר עכשיו")
                }
                runCatching { speechLauncher.launch(intent) }.onFailure { Toast.makeText(context, "זיהוי דיבור אינו זמין במכשיר", Toast.LENGTH_SHORT).show() }
            }) { Text("🎙 טקסט") }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { showChecklistInput = true }) { Text("☑") }
        }
        OutlinedTextField(
            value = tags, onValueChange = { tags = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            singleLine = true, placeholder = { Text("תגיות: #עבודה #רעיונות", fontSize = 12.sp) }
        )
    }

    if (showChecklistInput) {
        AlertDialog(
            onDismissRequest = { showChecklistInput = false },
            title = { Text("משימה חדשה") },
            text = { OutlinedTextField(checklistText, { checklistText = it }, label = { Text("תיאור המשימה") }) },
            confirmButton = {
                TextButton(onClick = {
                    if (checklistText.isNotBlank()) checklist.add(ChecklistItem(text = checklistText.trim()))
                    checklistText = ""; showChecklistInput = false
                }) { Text("הוסף") }
            },
            dismissButton = { TextButton(onClick = { showChecklistInput = false }) { Text("ביטול") } }
        )
    }
    if (showMath) {
        AlertDialog(
            onDismissRequest = { showMath = false },
            title = { Text("מחשבון") },
            text = { OutlinedTextField(mathInput, { mathInput = it }, label = { Text("לדוגמה: 125*0.17=") }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = {
                    val result = runCatching { SimpleMath.evaluate(mathInput.removeSuffix("=").trim()) }.getOrNull()
                    if (result != null) {
                        body += "\n" + mathInput + " = " + result
                        html = "<p>" + escapeNoteHtml(body).replace("\n", "<br>") + "</p>"
                    }
                    else Toast.makeText(context, "הביטוי אינו תקין", Toast.LENGTH_SHORT).show()
                    showMath = false; mathInput = ""
                }) { Text("חשב והוסף") }
            },
            dismissButton = { TextButton(onClick = { showMath = false }) { Text("ביטול") } }
        )
    }
    if (showLockDialog) {
        PasscodeDialog(
            title = "נעילת הפתק", confirm = "נעל פתק",
            onDismiss = { showLockDialog = false },
            onConfirm = { pass ->
                runCatching { NoteSecurity.lock(currentNote(), pass.toCharArray()) }
                    .onSuccess { showLockDialog = false; onSave(it) }
                    .onFailure { Toast.makeText(context, it.localizedMessage ?: "לא ניתן לנעול", Toast.LENGTH_LONG).show() }
            }
        )
    }
}

@Composable
private fun HtmlRichEditor(
    html: String,
    onHtmlChange: (String) -> Unit,
    action: String,
    actionToken: Int,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lastReported = remember { mutableStateOf(html) }
    val lastAction = remember { mutableIntStateOf(0) }
    AndroidView(
        modifier = modifier,
        factory = {
            EditText(context).apply {
                setTextColor(android.graphics.Color.WHITE.takeIf { isSystemInDarkThemeSafe(context) } ?: android.graphics.Color.DKGRAY)
                textSize = 17f
                setPadding(5, 10, 5, 10)
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                minLines = 8
                gravity = android.view.Gravity.TOP or android.view.Gravity.START
                setText(Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY))
                addTextChangedListener(object : android.text.TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                    override fun afterTextChanged(s: Editable?) {
                        val converted = Html.toHtml(s, Html.TO_HTML_PARAGRAPH_LINES_INDIVIDUAL)
                        lastReported.value = converted
                        onHtmlChange(converted)
                    }
                })
            }
        },
        update = { view ->
            if (lastReported.value != html) {
                lastReported.value = html
                if (view.text.toString() != Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY).toString()) {
                    view.setText(Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY))
                    view.setSelection(view.text.length)
                }
            }
            if (actionToken != lastAction.intValue && action.isNotBlank()) {
                lastAction.intValue = actionToken
                val start = view.selectionStart.coerceAtLeast(0)
                val end = view.selectionEnd.coerceAtLeast(0)
                val from = minOf(start, end)
                val to = maxOf(start, end)
                val text = view.text
                if (from <= to && to <= text.length) {
                    when (action) {
                        "bold" -> text.setSpan(StyleSpan(android.graphics.Typeface.BOLD), from, to, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        "italic" -> text.setSpan(StyleSpan(android.graphics.Typeface.ITALIC), from, to, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        "underline" -> text.setSpan(UnderlineSpan(), from, to, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        "strike" -> text.setSpan(StrikethroughSpan(), from, to, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        "highlight" -> text.setSpan(BackgroundColorSpan(android.graphics.Color.YELLOW), from, to, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        "bullet" -> text.insert(from, "• ")
                    }
                }
                val converted = Html.toHtml(view.text, Html.TO_HTML_PARAGRAPH_LINES_INDIVIDUAL)
                lastReported.value = converted
                onHtmlChange(converted)
            }
        }
    )
}

@Composable
fun SettingsScreen(
    themeMode: String,
    accent: String,
    accents: Map<String, Color>,
    count: Int,
    onTheme: (String) -> Unit,
    onAccent: (String) -> Unit,
    onBack: () -> Unit,
    onJson: () -> Unit,
    onTxt: () -> Unit,
    onZip: () -> Unit,
    onMd: () -> Unit,
    onHtml: () -> Unit,
    onPdf: () -> Unit,
    onImport: () -> Unit,
    onDriveBackup: () -> Unit,
    onDriveRestore: () -> Unit,
    onSetAppLock: (String) -> Unit,
    onRemoveAppLock: () -> Unit
) {
    var newPin by remember { mutableStateOf("") }
    var showPinDialog by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("‹ חזרה") }
            Text("Settings", fontSize = 27.sp, fontWeight = FontWeight.Bold)
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 17.dp, vertical = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(15.dp)
        ) {
            item {
                SettingsCard("Appearance") {
                    Text("ערכת נושא", fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.padding(top = 9.dp)) {
                        listOf("system" to "מערכת", "light" to "בהיר", "dark" to "כהה").forEach { (key, label) ->
                            ChoiceChip(label, themeMode == key) { onTheme(key) }
                        }
                    }
                    Text("צבע הדגשה", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(13.dp)) {
                        accents.forEach { (key, value) ->
                            Box(Modifier.size(33.dp).background(value, CircleShape).clickable { onAccent(key) }, contentAlignment = Alignment.Center) {
                                if (key == accent) Text("✓", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
            item {
                SettingsCard("Backup & Export") {
                    Text("שמירת גיבוי מלא עם תיקיות, תגיות, רשימות וקבצים מצורפים", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    SettingAction("ייצוא גיבוי JSON", onJson)
                    SettingAction("ייצוא ZIP מלא (JSON + TXT + MD + HTML)", onZip)
                    SettingAction("ייצוא TXT", onTxt)
                    SettingAction("ייצוא Markdown", onMd)
                    SettingAction("ייצוא HTML", onHtml)
                    SettingAction("ייצוא PDF", onPdf)
                    Divider(color = MaterialTheme.colorScheme.surfaceVariant)
                    SettingAction("ייבוא גיבוי (JSON / ZIP / Markdown)", onImport)
                }
            }
            item {
                SettingsCard("Google Drive") {
                    Text("גיבוי ידני לתיקייה הפרטית של האפליקציה ב-Google Drive.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    SettingAction("גבה עכשיו", onDriveBackup)
                    SettingAction("שחזר מ-Drive", onDriveRestore)
                }
            }
            item {
                SettingsCard("Privacy & Security") {
                    Text("נעילת האפליקציה בקוד גישה", fontWeight = FontWeight.SemiBold)
                    SettingAction("הגדר קוד גישה", { showPinDialog = true })
                    SettingAction("בטל נעילת אפליקציה", onRemoveAppLock)
                }
            }
            item {
                Text("NoteApple 2.0 · " + count + " פתקים במכשיר", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center, fontSize = 11.sp)
            }
        }
    }
    if (showPinDialog) {
        PasscodeDialog(
            title = "קביעת קוד לאפליקציה", confirm = "שמור",
            onDismiss = { showPinDialog = false },
            onConfirm = { onSetAppLock(it); showPinDialog = false }
        )
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface, RoundedCornerShape(17.dp)).padding(16.dp)) {
        Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(11.dp))
        content()
    }
}

@Composable
private fun SettingAction(title: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 7.dp)) {
        Text(title, modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun ChoiceChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(10.dp)
    ) {
        Text(label, modifier = Modifier.padding(horizontal = 13.dp, vertical = 8.dp), fontSize = 12.sp,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
fun PasscodeDialog(
    title: String,
    confirm: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    error: String = ""
) {
    var passcode by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = passcode, onValueChange = { passcode = it },
                    label = { Text("קוד גישה") }, singleLine = true,
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation()
                )
                if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            }
        },
        confirmButton = {
            TextButton(onClick = { if (passcode.length >= 4) onConfirm(passcode) }) { Text(confirm) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("ביטול") } }
    )
}

private fun formatNoteDate(timestamp: Long): String =
    SimpleDateFormat("d MMM · HH:mm", Locale("he", "IL")).format(Date(timestamp))

internal fun escapeNoteHtml(text: String): String =
    text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .replace("\"", "&quot;").replace("'", "&#39;")

internal fun exportFile(context: Context, uri: Uri, bytes: ByteArray) {
    runCatching {
        context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
            ?: throw IllegalArgumentException("אין אפשרות לכתוב לקובץ")
        Toast.makeText(context, "הקובץ נשמר בהצלחה", Toast.LENGTH_SHORT).show()
    }.onFailure { Toast.makeText(context, it.localizedMessage ?: "שגיאה בשמירת הקובץ", Toast.LENGTH_LONG).show() }
}

private fun queryDisplayName(context: Context, uri: Uri): String {
    val cursor = context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
    cursor?.use { if (it.moveToFirst()) return it.getString(0) ?: "attachment" }
    return uri.lastPathSegment?.substringAfterLast('/') ?: "attachment"
}

private fun openAttachment(context: Context, attachment: NoteAttachment) {
    runCatching {
        val file = File(attachment.path)
        val uri = androidx.core.content.FileProvider.getUriForFile(context, context.packageName + ".files", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, attachment.mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    }.onFailure { Toast.makeText(context, "לא נמצאה אפליקציה לפתיחת הקובץ", Toast.LENGTH_SHORT).show() }
}

private fun shareNote(context: Context, note: NoteItem) {
    val text = note.title + "\n\n" + Html.fromHtml(note.bodyHtml, Html.FROM_HTML_MODE_COMPACT).toString()
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
        putExtra(Intent.EXTRA_SUBJECT, note.title)
    }
    context.startActivity(Intent.createChooser(intent, "שיתוף פתק"))
}

private fun isSystemDarkThemeSafe(context: Context): Boolean =
    (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
        android.content.res.Configuration.UI_MODE_NIGHT_YES

private fun startVoiceRecorder(
    context: Context,
    recorderSetter: (MediaRecorder?) -> Unit,
    pathSetter: (String) -> Unit,
    recordingSetter: (Boolean) -> Unit
) {
    runCatching {
        val dir = File(context.filesDir, "attachments").apply { mkdirs() }
        val path = File(dir, "voice-" + UUID.randomUUID() + ".m4a").absolutePath
        val media = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else MediaRecorder()
        media.setAudioSource(MediaRecorder.AudioSource.MIC)
        media.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        media.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        media.setOutputFile(path)
        media.prepare()
        media.start()
        recorderSetter(media); pathSetter(path); recordingSetter(true)
    }.onFailure { Toast.makeText(context, "לא ניתן להתחיל הקלטה: " + (it.localizedMessage ?: ""), Toast.LENGTH_LONG).show() }
}

private fun stopVoiceRecorder(
    recorder: MediaRecorder?,
    path: String,
    attachments: MutableList<NoteAttachment>,
    finished: () -> Unit
) {
    runCatching {
        recorder?.stop()
        recorder?.release()
        val file = File(path)
        if (file.exists() && file.length() > 0) {
            attachments.add(NoteAttachment(name = file.name, mimeType = "audio/mp4", path = file.absolutePath, size = file.length()))
        } else file.delete()
    }.onFailure { recorder?.release() }
    finished()
}

internal fun decodeZipBackup(context: Context, bytes: ByteArray): BackupSnapshot {
    java.util.zip.ZipInputStream(bytes.inputStream()).use { zip ->
        while (true) {
            val entry = zip.nextEntry ?: break
            if (!entry.isDirectory && entry.name.endsWith(".json", true)) {
                val output = java.io.ByteArrayOutputStream()
                zip.copyTo(output)
                return BackupCodec.decode(context, output.toString("UTF-8"))
            }
            zip.closeEntry()
        }
    }
    throw IllegalArgumentException("לא נמצא קובץ JSON בתוך ZIP")
}

private object SimpleMath {
    fun evaluate(expression: String): Double {
        val parser = Parser(expression)
        val value = parser.expression()
        require(parser.atEnd())
        return value
    }

    private class Parser(private val source: String) {
        private var pos = 0
        fun atEnd(): Boolean { skip(); return pos >= source.length }
        fun expression(): Double {
            var value = term()
            while (true) {
                skip()
                if (take('+')) value += term()
                else if (take('-')) value -= term()
                else return value
            }
        }
        private fun term(): Double {
            var value = factor()
            while (true) {
                skip()
                if (take('*') || take('×')) value *= factor()
                else if (take('/') || take('÷')) value /= factor()
                else if (take('%')) value /= 100.0
                else return value
            }
        }
        private fun factor(): Double {
            skip()
            if (take('(')) { val v = expression(); require(take(')')); return v }
            val start = pos
            if (pos < source.length && (source[pos] == '-' || source[pos] == '+')) pos++
            while (pos < source.length && (source[pos].isDigit() || source[pos] == '.')) pos++
            require(pos > start)
            return source.substring(start, pos).toDouble()
        }
        private fun take(char: Char): Boolean {
            skip()
            if (pos < source.length && source[pos] == char) { pos++; return true }
            return false
        }
        private fun skip() { while (pos < source.length && source[pos].isWhitespace()) pos++ }
    }
}
