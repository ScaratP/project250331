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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.LocalTextStyle
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.VisualTransformation

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
    var showSaveSuccessMessage by remember { mutableStateOf(false) }
    var debugText by remember { mutableStateOf("") } // 用於調試

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
                    debugText = "Loaded note: $note, segments: ${segments.size}"

                    if (note != null) {
                        noteName = note.name

                        // 檢查是否有段落
                        if (segments.isEmpty()) {
                            debugText += "\nNo segments found"
                            textFieldValue = TextFieldValue("")
                        } else {
                            // 按照 start 位置排序段落
                            val sortedSegments = segments.sortedBy { it.start }
                            val fullText = sortedSegments.joinToString("") { it.content }

                            var currentPosition = 0
                            val recalculatedSegments = sortedSegments.map { segment ->
                                val newStart = currentPosition
                                val newEnd = newStart + segment.content.length
                                currentPosition = newEnd
                                segment.copy(start = newStart, end = newEnd)
                            }
                            debugText += "\nFull text: $fullText"



                            // 直接使用純文本設置 TextFieldValue
                            textFieldValue = TextFieldValue(fullText)

                            // 如果需要應用樣式，可以再次嘗試 AnnotatedString
                            try {
                                val builder = AnnotatedString.Builder(fullText)
                                recalculatedSegments.forEach { segment ->
                                    if (segment.start >= 0 && segment.start < segment.end && segment.end <= fullText.length) {
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
                                }
                                annotatedString = builder.toAnnotatedString()
                                // 嘗試重新設置帶有樣式的 TextFieldValue
                                textFieldValue = TextFieldValue(annotatedString)
                                debugText += "\nApplied styles to text"
                            } catch (e: Exception) {
                                debugText += "\nError applying styles: ${e.message}"
                                textFieldValue = TextFieldValue(fullText)
                            }
                        }

                        isSaved = true
                        debugText += "\nNote load completed"
                    } else {
                        debugText = "No note found with id: $noteId"
                    }
                    isLoading = false
                } catch (e: Exception) {
                    debugText = "Failed to load note: ${e.message}"
                    isLoading = false
                }
            }
        }
    }

    // 儲存成功提示對話框
    if (showSaveSuccessMessage) {
        AlertDialog(
            onDismissRequest = { showSaveSuccessMessage = false },
            title = { Text("儲存成功") },
            text = { Text("筆記已成功儲存") },
            confirmButton = {
                TextButton(onClick = { showSaveSuccessMessage = false }) {
                    Text("確定")
                }
            }
        )
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
                                }
                                noteIdState = note.id
                                isSaved = true
                                showOverwriteDialog = false
                                showSaveSuccessMessage = true // 顯示儲存成功提示
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
                                            }
                                            noteIdState = insertedId
                                            isSaved = true
                                            showSaveSuccessMessage = true // 顯示儲存成功提示
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
                                            }
                                            isSaved = true
                                            showSaveSuccessMessage = true // 顯示儲存成功提示
                                        }
                                    }
                                } catch (e: Exception) {
                                    // 處理錯誤
                                    debugText = "Failed to save note: ${e.message}"
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

                // 顯示調試信息（可以在發布版本中移除）
                if (debugText.isNotEmpty()) {
                    Text(
                        text = debugText,
                        color = Color.Red,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .background(Color.LightGray.copy(alpha = 0.3f))
                            .padding(4.dp)
                    )
                }

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
                BasicTextField(
                    value = textFieldValue,
                    onValueChange = { newValue ->
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
                        // 保持選擇區域和輸入法組合
                        textFieldValue = TextFieldValue(
                            annotatedString = annotatedString,
                            selection = newValue.selection,
                            composition = newValue.composition
                        )
                        isSaved = false // 編輯文字時，設置為未儲存
                    },
                    textStyle = LocalTextStyle.current.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                        .padding(16.dp),
                    // 以下是控制外觀的參數
                    visualTransformation = VisualTransformation.None,
                    interactionSource = remember { MutableInteractionSource() },
                    singleLine = false
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

    // 如果沒有文本，返回空列表
    if (text.isEmpty()) {
        return segments
    }

    // 如果沒有樣式，創建一個默認段落
    if (annotatedString.spanStyles.isEmpty()) {
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
        return segments
    }

    // 有樣式的情況
    val spanRanges = mutableListOf<Triple<Int, Int, SpanStyle>>()
    annotatedString.spanStyles.forEach { spanStyle ->
        spanRanges.add(Triple(spanStyle.start, spanStyle.end, spanStyle.item))
    }

    // 排序樣式區間
    spanRanges.sortBy { it.first }

    // 處理每個區間
    var currentPosition = 0
    spanRanges.forEach { (start, end, style) ->
        // 處理樣式區間前的無樣式文本
        if (start > currentPosition) {
            val plainText = text.substring(currentPosition, start)
            segments.add(
                NoteSegment(
                    noteId = noteId,
                    content = plainText,
                    start = currentPosition,
                    end = start,
                    fontSize = 20f,
                    fontColor = Color.Black.value.toLong(),
                    backgroundColor = Color.Transparent.value.toLong(),
                    isBold = false,
                    isItalic = false
                )
            )
        }

        // 處理有樣式的文本
        if (end > start) {
            val styledText = text.substring(start, end)
            segments.add(
                NoteSegment(
                    noteId = noteId,
                    content = styledText,
                    start = start,
                    end = end,
                    fontSize = style.fontSize?.value ?: 20f,
                    fontColor = style.color.value.toLong(),
                    backgroundColor = style.background.value.toLong(),
                    isBold = style.fontWeight == FontWeight.Bold,
                    isItalic = style.fontStyle == FontStyle.Italic
                )
            )
        }

        currentPosition = end
    }

    // 處理最後一個區間後的無樣式文本
    if (currentPosition < text.length) {
        val remainingText = text.substring(currentPosition, text.length)
        segments.add(
            NoteSegment(
                noteId = noteId,
                content = remainingText,
                start = currentPosition,
                end = text.length,
                fontSize = 20f,
                fontColor = Color.Black.value.toLong(),
                backgroundColor = Color.Transparent.value.toLong(),
                isBold = false,
                isItalic = false
            )
        )
    }

    return segments
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteItem(
    note: Note,
    onView: () -> Unit,
    onEdit: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onView),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = note.name,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )

            Row {
                // 查看按鈕
                IconButton(onClick = onView) {
                    Icon(
                        Icons.Default.Visibility,
                        contentDescription = "查看",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                // 編輯按鈕
                IconButton(onClick = onEdit) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "編輯",
                        tint = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }
    }
}