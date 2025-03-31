// 儲存位置: app/src/main/java/com/example/project250311/Notes/NoteScreen.kt
package com.example.project250311.Schedule.Note

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.project250311.Data.Note
import com.example.project250311.Data.NoteSegment
import com.example.project250311.Data.NoteDatabase
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteScreen(navController: NavController, noteId: Int? = null) {
    var currentBold by remember { mutableStateOf(false) }
    var currentItalic by remember { mutableStateOf(false) }
    var currentColor by remember { mutableStateOf(Color.Black) }
    var currentSize by remember { mutableStateOf(20.sp) }
    var currentMark by remember { mutableStateOf(false) }
    var textFieldValue by remember { mutableStateOf(TextFieldValue("")) }
    var isSaved by remember { mutableStateOf(false) }
    var noteIdState by remember { mutableStateOf<Int?>(noteId) }
    var noteName by remember { mutableStateOf("") }
    var annotatedString by remember { mutableStateOf(AnnotatedString("")) }
    var showOverwriteDialog by remember { mutableStateOf(false) }
    var existingNote by remember { mutableStateOf<Note?>(null) }
    var isLoading by remember { mutableStateOf(noteId != null) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = NoteDatabase.getDatabase(context)
    val noteDao = db.noteDao()

    // 如果有傳入 noteId，則從資料庫加載筆記
    LaunchedEffect(noteId) {
        if (noteId != null) {
            isLoading = true
            scope.launch {
                try {
                    val note = noteDao.getNoteById(noteId)
                    val segments = noteDao.getSegmentsForNote(noteId)
                    println("Loaded note: $note, segments: $segments")
                    if (note != null) {
                        noteName = note.name
                        val builder = AnnotatedString.Builder()
                        segments.forEach { segment ->
                            println("Appending segment: ${segment.content}, start: ${segment.start}, end: ${segment.end}")
                            builder.append(segment.content)
                            builder.addStyle(
                                SpanStyle(
                                    fontSize = segment.fontSize.sp,
                                    fontWeight = if (segment.isBold) FontWeight.Bold else FontWeight.Normal,
                                    fontStyle = if (segment.isItalic) FontStyle.Italic else FontStyle.Normal,
                                    color = Color(segment.fontColor),
                                    background = Color(segment.backgroundColor)
                                ),
                                segment.start,
                                segment.end
                            )
                        }
                        annotatedString = builder.toAnnotatedString()
                        textFieldValue = TextFieldValue(annotatedString)
                        isSaved = true
                        println("Loaded note for editing: $note, annotatedString: $annotatedString")
                    } else {
                        println("No note found with id: $noteId")
                    }
                    isLoading = false
                } catch (e: Exception) {
                    println("Failed to load note: ${e.message}")
                    isLoading = false
                }
            }
        }
    }

    // 覆蓋確認對話框
    if (showOverwriteDialog) {
        AlertDialog(
            onDismissRequest = { showOverwriteDialog = false },
            title = { Text("已有相同名稱的筆記") },
            text = { Text("是否要覆蓋現有的筆記？") },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            existingNote?.let { note ->
                                // 刪除舊的 segments
                                noteDao.deleteSegmentsForNote(note.id)
                                // 更新 Note
                                val updatedNote = note.copy(name = noteName)
                                noteDao.update(updatedNote)
                                // 儲存新的 segments
                                val segments = createSegmentsFromAnnotatedString(
                                    annotatedString,
                                    note.id
                                )
                                segments.forEach { segment ->
                                    noteDao.insertSegment(segment)
                                    println("Inserted segment: $segment")
                                }
                                noteIdState = note.id
                                isSaved = true
                                showOverwriteDialog = false
                                println("Overwritten note: $updatedNote")
                            }
                        }
                    }
                ) {
                    Text("覆蓋")
                }
            },
            dismissButton = {
                Button(onClick = { showOverwriteDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (noteId == null) "新增筆記" else "編輯筆記") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            println("SAVE button clicked")
                            scope.launch {
                                try {
                                    val existing = noteDao.getNoteByName(noteName)
                                    if (existing != null && existing.id != noteIdState) {
                                        existingNote = existing
                                        showOverwriteDialog = true
                                    } else {
                                        if (noteIdState == null) {
                                            // 新增筆記
                                            val newNote = Note(name = noteName.ifBlank { "Untitled" })
                                            val insertedId = noteDao.insert(newNote).toInt()
                                            // 儲存 segments
                                            val segments = createSegmentsFromAnnotatedString(
                                                annotatedString,
                                                insertedId
                                            )
                                            segments.forEach { segment ->
                                                noteDao.insertSegment(segment)
                                                println("Inserted segment: $segment")
                                            }
                                            noteIdState = insertedId
                                            isSaved = true
                                            println("Inserted note: $newNote, noteId: $noteIdState")
                                        } else {
                                            // 更新筆記
                                            val updatedNote = Note(
                                                id = noteIdState!!,
                                                name = noteName
                                            )
                                            noteDao.update(updatedNote)
                                            // 刪除舊的 segments
                                            noteDao.deleteSegmentsForNote(noteIdState!!)
                                            // 儲存新的 segments
                                            val segments = createSegmentsFromAnnotatedString(
                                                annotatedString,
                                                noteIdState!!
                                            )
                                            segments.forEach { segment ->
                                                noteDao.insertSegment(segment)
                                                println("Inserted segment: $segment")
                                            }
                                            isSaved = true
                                            println("Updated note: $updatedNote")
                                        }
                                    }
                                } catch (e: Exception) {
                                    println("Failed to save note: ${e.message}")
                                }
                            }
                        }
                    ) {
                        Icon(
                            Icons.Default.Save,
                            contentDescription = "儲存",
                            tint = if (isSaved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp)
            ) {
                // 檔案名稱輸入框
                OutlinedTextField(
                    value = noteName,
                    onValueChange = {
                        noteName = it
                        isSaved = false // 編輯檔案名稱時，設置為未儲存
                    },
                    placeholder = { Text("請輸入筆記標題") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    textStyle = TextStyle(
                        fontSize = 16.sp
                    ),
                    singleLine = true
                )

                // 格式工具列
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FormatButton(
                        text = "A+",
                        isSelected = currentSize == 24.sp,
                        onClick = { currentSize = if (currentSize == 24.sp) 20.sp else 24.sp },
                        modifier = Modifier.weight(1f)
                    )
                    FormatButton(
                        text = "A-",
                        isSelected = currentSize == 12.sp,
                        onClick = { currentSize = if (currentSize == 12.sp) 20.sp else 12.sp },
                        modifier = Modifier.weight(1f)
                    )
                    FormatButton(
                        text = "B",
                        isSelected = currentBold,
                        onClick = { currentBold = !currentBold },
                        modifier = Modifier.weight(1f)
                    )
                    FormatButton(
                        text = "I",
                        isSelected = currentItalic,
                        onClick = { currentItalic = !currentItalic },
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FormatButton(
                        text = "紅色",
                        isSelected = currentColor == Color.Red,
                        onClick = { currentColor = if (currentColor == Color.Red) Color.Black else Color.Red },
                        modifier = Modifier.weight(1f)
                    )
                    FormatButton(
                        text = "藍色",
                        isSelected = currentColor == Color.Blue,
                        onClick = { currentColor = if (currentColor == Color.Blue) Color.Black else Color.Blue },
                        modifier = Modifier.weight(1f)
                    )
                    FormatButton(
                        text = "螢光",
                        isSelected = currentMark,
                        onClick = { currentMark = !currentMark },
                        modifier = Modifier.weight(1f)
                    )
                }

                // 筆記內容編輯區
                // 改為完全不使用 colors 參數
                TextField(
                    value = textFieldValue,
                    onValueChange = { newValue ->
                        println("TextField onValueChange called with newValue: ${newValue.text}")
                        val previousText = textFieldValue.text
                        val newText = newValue.text
                        val previousLength = previousText.length
                        val newLength = newText.length

                        // 創建新的 AnnotatedString
                        val builder = AnnotatedString.Builder()
                        builder.append(newText)

                        // 保留之前的 SpanStyle（字體大小、顏色等）
                        annotatedString.spanStyles.forEach { style ->
                            if (style.start < previousLength && style.end <= previousLength) {
                                builder.addStyle(style.item, style.start, minOf(style.end, newLength))
                            }
                        }

                        // 如果有新輸入的文字，應用當前格式
                        if (newLength > previousLength) {
                            val start = previousLength
                            val end = newLength
                            builder.addStyle(
                                SpanStyle(
                                    fontSize = currentSize,
                                    fontWeight = if (currentBold) FontWeight.Bold else FontWeight.Normal,
                                    fontStyle = if (currentItalic) FontStyle.Italic else FontStyle.Normal,
                                    color = currentColor,
                                    background = if (currentMark) Color(0xFFFFE082) else Color.Transparent
                                ),
                                start,
                                end
                            )
                        }

                        annotatedString = builder.toAnnotatedString()
                        textFieldValue = newValue.copy(annotatedString = annotatedString)
                        isSaved = false // 編輯文字時，設置為未儲存
                        println("TextField updated successfully, annotatedString: $annotatedString")
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                )
            }
        }
    }
}

@Composable
fun FormatButton(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        shadowElevation = if (isSelected) 0.dp else 1.dp
    ) {
        Box(
            modifier = Modifier
                .padding(vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                fontSize = 14.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}

private fun createSegmentsFromAnnotatedString(
    annotatedString: AnnotatedString,
    noteId: Int
): List<NoteSegment> {
    val segments = mutableListOf<NoteSegment>()
    val text = annotatedString.text
    val spanStyles = annotatedString.spanStyles

    println("AnnotatedString text: $text, spanStyles: $spanStyles")

    if (spanStyles.isEmpty()) {
        if (text.isNotEmpty()) {
            segments.add(
                NoteSegment(
                    noteId = noteId,
                    content = text,
                    start = 0,
                    end = text.length,
                    fontSize = 20f,
                    fontColor = Color.Black.value.toLong(),
                    backgroundColor = Color.Transparent.value.toLong(),
                    isBold = false,
                    isItalic = false
                )
            )
            println("Added default segment: $text")
        }
    } else {
        var lastEnd = 0
        spanStyles.forEach { style ->
            if (style.start > lastEnd) {
                val unformattedContent = text.substring(lastEnd, style.start)
                if (unformattedContent.isNotEmpty()) {
                    segments.add(
                        NoteSegment(
                            noteId = noteId,
                            content = unformattedContent,
                            start = lastEnd,
                            end = style.start,
                            fontSize = 20f,
                            fontColor = Color.Black.value.toLong(),
                            backgroundColor = Color.Transparent.value.toLong(),
                            isBold = false,
                            isItalic = false
                        )
                    )
                    println("Added unformatted segment: $unformattedContent, start: $lastEnd, end: ${style.start}")
                }
            }
            val formattedContent = text.substring(style.start, style.end)
            if (formattedContent.isNotEmpty()) {
                segments.add(
                    NoteSegment(
                        noteId = noteId,
                        content = formattedContent,
                        start = style.start,
                        end = style.end,
                        fontSize = style.item.fontSize.value,
                        fontColor = style.item.color.value.toLong(),
                        backgroundColor = style.item.background.value.toLong(),
                        isBold = style.item.fontWeight == FontWeight.Bold,
                        isItalic = style.item.fontStyle == FontStyle.Italic
                    )
                )
                println("Added formatted segment: $formattedContent, start: ${style.start}, end: ${style.end}")
            }
            lastEnd = style.end
        }
        if (lastEnd < text.length) {
            val remainingContent = text.substring(lastEnd, text.length)
            if (remainingContent.isNotEmpty()) {
                segments.add(
                    NoteSegment(
                        noteId = noteId,
                        content = remainingContent,
                        start = lastEnd,
                        end = text.length,
                        fontSize = 20f,
                        fontColor = Color.Black.value.toLong(),
                        backgroundColor = Color.Transparent.value.toLong(),
                        isBold = false,
                        isItalic = false
                    )
                )
                println("Added remaining segment: $remainingContent, start: $lastEnd, end: ${text.length}")
            }
        }
    }
    println("Generated segments: $segments")
    return segments
}