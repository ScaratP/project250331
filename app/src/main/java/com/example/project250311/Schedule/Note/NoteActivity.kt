package com.example.project250311.Schedule.Note

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.window.Dialog
import com.example.project250311.Data.Note
import com.example.project250311.Data.NoteSegment
import com.example.project250311.Data.NoteDatabase
import kotlinx.coroutines.launch

class NoteActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val noteId = intent.getIntExtra("NOTE_ID", -1)
        setContent {
            NoteScreen(noteId = if (noteId == -1) null else noteId)
        }
    }
}

@Composable
fun NoteScreen(noteId: Int? = null) {
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

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = NoteDatabase.getDatabase(context)
    val noteDao = db.noteDao()

    // 如果有傳入 noteId，則從資料庫加載筆記
    LaunchedEffect(noteId) {
        if (noteId != null) {
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
                } catch (e: Exception) {
                    println("Failed to load note: ${e.message}")
                }
            }
        }
    }

    // 覆蓋確認對話框
    if (showOverwriteDialog) {
        Dialog(onDismissRequest = { showOverwriteDialog = false }) {
            Surface(
                modifier = Modifier
                    .width(300.dp)
                    .background(Color.White, RoundedCornerShape(8.dp)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "已有相同名稱的檔案，是否覆蓋？",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Button(onClick = {
                            showOverwriteDialog = false
                            println("Overwrite dialog canceled")
                        }) {
                            Text("取消")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
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
                    }
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F5F5))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 0.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左側返回按鈕
            TextButton(
                onClick = {
                    val intent = Intent(context, NoteListActivity::class.java)
                    context.startActivity(intent)
                },
                modifier = Modifier
                    .size(40.dp),
                colors = ButtonDefaults.textButtonColors(
                    containerColor = Color.Transparent,
                    contentColor = Color(0xFF666666)
                ),
                contentPadding = PaddingValues(0.dp)
            ) {
                Text(
                    text = "⬅\uFE0F",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // 右側儲存按鈕
            Text(
                text = "SAVE",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = if (isSaved) Color(0xFF5C727D) else Color(0xFF8CAFBF),
                modifier = Modifier
                    .height(18.dp)
                    .clickable {
                        println("SAVE button clicked")
                        scope.launch {
                            try {
                                val existing = noteDao.getNoteByName(noteName)
                                if (existing != null && existing.id != noteIdState) {
                                    existingNote = existing
                                    showOverwriteDialog = true
                                } else {
                                    if (!isSaved) {
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
                    .padding(horizontal = 8.dp)
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 檔案名稱輸入框
            TextField(
                value = noteName,
                onValueChange = {
                    noteName = it
                    isSaved = false // 編輯檔案名稱時，設置為未儲存
                },
                placeholder = { Text("請輸入檔案名稱", color = Color.Gray) },
                modifier = Modifier
                    .weight(2f)
                    .padding(end = 8.dp),
                textStyle = TextStyle(
                    fontSize = 16.sp,
                    color = Color.Black
                ),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color(0xFFF5F5F5),
                    unfocusedContainerColor = Color(0xFFF5F5F5),
                    focusedIndicatorColor = Color.Black,
                    unfocusedIndicatorColor = Color.Black,
                    cursorColor = Color.Black
                ),
                singleLine = true
            )

            // "Big" 按鈕
            CustomButton(
                text = "Big",
                isSelected = currentSize == 24.sp,
                onClick = { currentSize = if (currentSize == 24.sp) 20.sp else 24.sp },
                modifier = Modifier.weight(1f)
            )

            // "Small" 按鈕
            CustomButton(
                text = "Small",
                isSelected = currentSize == 12.sp,
                onClick = { currentSize = if (currentSize == 12.sp) 20.sp else 12.sp },
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CustomButton(
                text = "B",
                isSelected = currentBold,
                onClick = { currentBold = !currentBold },
                modifier = Modifier.weight(1f)
            )
            CustomButton(
                text = "I",
                isSelected = currentItalic,
                onClick = { currentItalic = !currentItalic },
                modifier = Modifier.weight(1f)
            )
            CustomButton(
                text = "Red",
                isSelected = currentColor == Color.Red,
                onClick = { currentColor = if (currentColor == Color.Red) Color.Black else Color.Red },
                modifier = Modifier.weight(1f)
            )
            CustomButton(
                text = "Blue",
                isSelected = currentColor == Color.Blue,
                onClick = { currentColor = if (currentColor == Color.Blue) Color.Black else Color.Blue },
                modifier = Modifier.weight(1f)
            )
            CustomButton(
                text = "Mark",
                isSelected = currentMark,
                onClick = { currentMark = !currentMark },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
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
                .background(Color.White)
                .border(1.dp, Color(0xFFD3D3D3), RoundedCornerShape(8.dp))
        )
    }
}

@Composable
fun CustomButton(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) Color(0xFF606060) else Color(0xFF808080))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 14.sp
        )
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