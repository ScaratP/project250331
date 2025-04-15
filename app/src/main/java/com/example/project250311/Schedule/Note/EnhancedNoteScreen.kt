package com.example.project250311.Schedule.Note

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.project250311.Data.Note
import com.example.project250311.Data.NoteDatabase
import kotlinx.coroutines.launch

@Composable
fun EnhancedNoteScreen(
    onNavigateToNoteList: () -> Unit,
    noteId: Int? = null // 新增參數，如果有提供 noteId 表示是編輯模式
) {
    var currentBold by remember { mutableStateOf(false) }
    var currentItalic by remember { mutableStateOf(false) }
    var currentUnderline by remember { mutableStateOf(false) }
    var currentStrikeThrough by remember { mutableStateOf(false) }
    var currentColor by remember { mutableStateOf(Color.Black) }
    var currentSize by remember { mutableStateOf(20.sp) }
    var currentMark by remember { mutableStateOf(false) }
    var currentAlign by remember { mutableStateOf(TextAlign.Start) }

    var textFieldValue by remember { mutableStateOf(TextFieldValue("")) }
    var annotatedString by remember { mutableStateOf(AnnotatedString("")) }

    var saveColor by remember { mutableStateOf(Color.Gray) }
    var isTextModified by remember { mutableStateOf(false) }
    var originalNote by remember { mutableStateOf<Note?>(null) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = NoteDatabase.getDatabase(context)
    val noteDao = db.noteDao()
    val snackbarHostState = remember { SnackbarHostState() }

    var showDialog by remember { mutableStateOf(false) }
    var pendingNavigation by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(noteId != null) }

    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    // 加載現有筆記的資料
    LaunchedEffect(noteId) {
        if (noteId != null) {
            scope.launch {
                try {
                    // 假設 noteDao 有一個 getNoteById 方法
                    val allNotes = noteDao.getAllNotes()
                    val note = allNotes.find { it.id == noteId }

                    if (note != null) {
                        originalNote = note
                        val loadedAnnotatedString = note.toAnnotatedString()
                        annotatedString = loadedAnnotatedString
                        textFieldValue = TextFieldValue(annotatedString = loadedAnnotatedString)
                    }
                } finally {
                    isLoading = false
                }
            }
        }
    }

    // 儲存便利貼的函數
    fun saveNote(onSuccess: () -> Unit) {
        // 如果已經在儲存中，則直接返回
        if (isSaving) return
        // 關閉鍵盤
        keyboardController?.hide()
        // 清除焦點
        focusManager.clearFocus()

        scope.launch {
            isSaving = true // 設置儲存狀態為 true
            saveColor = Color.Gray // 將按鈕顏色變更為禁用狀態

            try {
                if (noteId != null && originalNote != null) {
                    // 編輯現有筆記，保留原始 ID
                    val updatedNote = originalNote!!.copy(
                        content = textFieldValue.text,
                        formattedContent = Note(0, null, null).toFormattedContent(annotatedString),
                        timestamp = System.currentTimeMillis()
                    )
                    noteDao.update(updatedNote)
                    snackbarHostState.showSnackbar("筆記已更新!")
                } else {
                    // 新增筆記，確保ID為0讓Room自動生成
                    val newNote = Note(
                        id = 0, // ID為0，Room會自動生成
                        content = textFieldValue.text,
                        formattedContent = Note(0, null, null).toFormattedContent(annotatedString),
                        timestamp = System.currentTimeMillis()
                    )
                    noteDao.insert(newNote)
                    snackbarHostState.showSnackbar("新筆記已儲存!")
                }
                isTextModified = false
            } catch (e: Exception) {
                snackbarHostState.showSnackbar("儲存失敗: ${e.message}")
            } finally {
                isSaving = false // 儲存完成後重置狀態
                saveColor = Color.Black // 恢復按鈕顏色
                onSuccess()
            }
        }
    }

    // 處理導航的函數
    fun handleNavigation(target: String) {
        when (target) {
            "Back" -> onNavigateToNoteList()
        }
    }

    // 對話框
    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("便利貼還未儲存!!!") },
            text = { Text("請問是否儲存?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (!isSaving) {
                            saveNote {
                                showDialog = false
                                pendingNavigation?.let { handleNavigation(it) }
                                pendingNavigation = null
                            }
                        }
                    },
                    enabled = !isSaving
                ) {
                    Text("是")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDialog = false
                        pendingNavigation?.let { handleNavigation(it) }
                        pendingNavigation = null
                    }
                ) {
                    Text("否")
                }
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(16.dp)
    ) {
        if (isLoading) {
            // 顯示加載中的指示器
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center)
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White)
            ) {
                // 頂部操作欄
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .height(36.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = {
                            if (isTextModified) {
                                showDialog = true
                                pendingNavigation = "Back"
                            } else {
                                onNavigateToNoteList()
                            }
                        },
                        modifier = Modifier.height(36.dp)
                    ) {
                        Text("返回", fontSize = 16.sp)
                    }

                    Text(
                        text = "儲存",
                        color = saveColor,
                        fontSize = 16.sp,
                        modifier = Modifier
                            .height(36.dp)
                            .clickable(enabled = !isSaving) {
                                if (!isSaving) {
                                    saveNote {
                                        onNavigateToNoteList()
                                    }
                                }
                            }
                            .wrapContentHeight(align = Alignment.CenterVertically),
                        style = MaterialTheme.typography.labelLarge.copy(
                            color = if (isSaving) Color.Gray else saveColor
                        )
                    )
                }

                // 標題顯示當前模式
                Text(
                    text = if (noteId == null) "新增便利貼" else "編輯便利貼",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )

                // 格式工具列
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    // 第一行工具列
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IconButton(
                            onClick = { currentBold = !currentBold },
                            modifier = Modifier.size(40.dp),
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = if (currentBold) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.FormatBold,
                                contentDescription = "粗體",
                                tint = if (currentBold) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }

                        IconButton(
                            onClick = { currentItalic = !currentItalic },
                            modifier = Modifier.size(40.dp),
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = if (currentItalic) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.FormatItalic,
                                contentDescription = "斜體",
                                tint = if (currentItalic) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }

                        IconButton(
                            onClick = { currentUnderline = !currentUnderline },
                            modifier = Modifier.size(40.dp),
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = if (currentUnderline) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.FormatUnderlined,
                                contentDescription = "底線",
                                tint = if (currentUnderline) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }

                        IconButton(
                            onClick = { currentStrikeThrough = !currentStrikeThrough },
                            modifier = Modifier.size(40.dp),
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = if (currentStrikeThrough) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.FormatStrikethrough,
                                contentDescription = "刪除線",
                                tint = if (currentStrikeThrough) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }

                        IconButton(
                            onClick = { currentSize = if (currentSize == 24.sp) 20.sp else 24.sp },
                            modifier = Modifier.size(40.dp),
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = if (currentSize == 24.sp) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.FormatSize,
                                contentDescription = "大字體",
                                tint = if (currentSize == 24.sp) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }

                        IconButton(
                            onClick = { currentSize = if (currentSize == 16.sp) 20.sp else 16.sp },
                            modifier = Modifier.size(40.dp),
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = if (currentSize == 16.sp) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                            )
                        ) {
                            Text(
                                text = "小",
                                color = if (currentSize == 16.sp) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                fontSize = 14.sp
                            )
                        }
                    }

                    // 第二行工具列
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IconButton(
                            onClick = { currentColor = if (currentColor == Color.Red) Color.Black else Color.Red },
                            modifier = Modifier.size(40.dp),
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = if (currentColor == Color.Red) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.FormatColorText,
                                contentDescription = "紅色",
                                tint = if (currentColor == Color.Red) Color.Red else MaterialTheme.colorScheme.onSurface
                            )
                        }

                        IconButton(
                            onClick = { currentColor = if (currentColor == Color.Blue) Color.Black else Color.Blue },
                            modifier = Modifier.size(40.dp),
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = if (currentColor == Color.Blue) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.FormatColorText,
                                contentDescription = "藍色",
                                tint = if (currentColor == Color.Blue) Color.Blue else MaterialTheme.colorScheme.onSurface
                            )
                        }

                        IconButton(
                            onClick = { currentMark = !currentMark },
                            modifier = Modifier.size(40.dp),
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = if (currentMark) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Colorize,
                                contentDescription = "標記",
                                tint = if (currentMark) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // 對齊方式
                        IconButton(
                            onClick = { currentAlign = TextAlign.Start },
                            modifier = Modifier.size(40.dp),
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = if (currentAlign == TextAlign.Start) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.FormatAlignLeft,
                                contentDescription = "左對齊",
                                tint = if (currentAlign == TextAlign.Start) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }

                        IconButton(
                            onClick = { currentAlign = TextAlign.Center },
                            modifier = Modifier.size(40.dp),
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = if (currentAlign == TextAlign.Center) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.FormatAlignCenter,
                                contentDescription = "居中對齊",
                                tint = if (currentAlign == TextAlign.Center) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }

                        IconButton(
                            onClick = { currentAlign = TextAlign.End },
                            modifier = Modifier.size(40.dp),
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = if (currentAlign == TextAlign.End) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.FormatAlignRight,
                                contentDescription = "右對齊",
                                tint = if (currentAlign == TextAlign.End) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 文本編輯區域
                TextField(
                    value = textFieldValue,
                    onValueChange = { newValue: TextFieldValue ->
                        // 處理可能的非意圖換行問題
                        val normalisedText = if (newValue.text.contains("\n") &&
                            !textFieldValue.text.contains("\n") &&
                            newValue.text.length == textFieldValue.text.length + 1) {
                            newValue.text.replace("\n", "")
                        } else {
                            newValue.text
                        }

                        // 建立新的 Builder
                        val builder = AnnotatedString.Builder()
                        builder.append(normalisedText)

                        val newLength = normalisedText.length
                        val oldLength = textFieldValue.text.length

                        // 重新應用所有已有樣式
                        for (style in annotatedString.spanStyles) {
                            if (style.start < newLength && style.end <= newLength) {
                                builder.addStyle(style.item, style.start, style.end)
                            } else if (style.start < newLength) {
                                builder.addStyle(style.item, style.start, newLength)
                            }
                        }

                        // 處理段落樣式
                        for (paraStyle in annotatedString.paragraphStyles) {
                            if (paraStyle.start < newLength && paraStyle.end <= newLength) {
                                builder.addStyle(paraStyle.item, paraStyle.start, paraStyle.end)
                            } else if (paraStyle.start < newLength) {
                                builder.addStyle(paraStyle.item, paraStyle.start, newLength)
                            }
                        }

                        // 為新增的文字添加當前樣式
                        if (newLength > oldLength) {
                            builder.addStyle(
                                SpanStyle(
                                    fontSize = currentSize,
                                    fontWeight = if (currentBold) FontWeight.Bold else FontWeight.Normal,
                                    fontStyle = if (currentItalic) FontStyle.Italic else FontStyle.Normal,
                                    textDecoration = when {
                                        currentUnderline && currentStrikeThrough -> androidx.compose.ui.text.style.TextDecoration.combine(
                                            listOf(
                                                androidx.compose.ui.text.style.TextDecoration.Underline,
                                                androidx.compose.ui.text.style.TextDecoration.LineThrough
                                            )
                                        )
                                        currentUnderline -> androidx.compose.ui.text.style.TextDecoration.Underline
                                        currentStrikeThrough -> androidx.compose.ui.text.style.TextDecoration.LineThrough
                                        else -> null
                                    },
                                    color = currentColor,
                                    background = if (currentMark) Color(0xFFFFE082) else Color.Transparent
                                ),
                                oldLength,
                                newLength
                            )
                        }

                        // 創建新的 AnnotatedString 並更新全局變數
                        val newAnnotatedString = builder.toAnnotatedString()
                        annotatedString = newAnnotatedString

                        // 使用 AnnotatedString 建構函數，移除 text 參數
                        textFieldValue = TextFieldValue(
                            annotatedString = newAnnotatedString,
                            selection = newValue.selection,
                            composition = newValue.composition
                        )
                        isTextModified = true
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 16.dp)
                        .border(2.dp, Color.Black),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFFFFF9C4),
                        unfocusedContainerColor = Color(0xFFFFF9C4),
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    textStyle = MaterialTheme.typography.bodyLarge,
                    singleLine = false,
                    maxLines = Int.MAX_VALUE
                )

                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(bottom = 16.dp)
                )
            }
        }
    }
}