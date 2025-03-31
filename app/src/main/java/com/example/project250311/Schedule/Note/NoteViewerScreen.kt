// 儲存位置: app/src/main/java/com/example/project250311/Schedule/Note/NoteViewerScreen.kt
package com.example.project250311.Schedule.Note

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.project250311.Data.NoteDatabase
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteViewerScreen(navController: NavController, noteId: Int) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = NoteDatabase.getDatabase(context)
    val noteDao = db.noteDao()

    var noteTitle by remember { mutableStateOf("") }
    var noteContent by remember { mutableStateOf<AnnotatedString>(AnnotatedString("")) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // 載入筆記內容
    LaunchedEffect(noteId) {
        scope.launch {
            try {
                val note = noteDao.getNoteById(noteId)
                val segments = noteDao.getSegmentsForNote(noteId)

                if (note != null) {
                    noteTitle = note.name

                    if (segments.isNotEmpty()) {
                        // 按照 start 位置排序段落
                        val sortedSegments = segments.sortedBy { it.start }
                        val fullText = sortedSegments.joinToString("") { it.content }

                        // 重新計算位置
                        var currentPosition = 0
                        val recalculatedSegments = sortedSegments.map { segment ->
                            val newStart = currentPosition
                            val newEnd = newStart + segment.content.length
                            currentPosition = newEnd
                            segment.copy(start = newStart, end = newEnd)
                        }

                        // 構建包含樣式的 AnnotatedString
                        noteContent = buildAnnotatedString {
                            append(fullText)
                            recalculatedSegments.forEach { segment ->
                                // 確保位置有效
                                if (segment.start >= 0 && segment.end <= fullText.length && segment.start < segment.end) {
                                    addStyle(
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
                        }
                    } else {
                        // 沒有段落時顯示空文本
                        noteContent = AnnotatedString("")
                    }
                    isLoading = false
                } else {
                    errorMessage = "找不到筆記"
                    isLoading = false
                }
            } catch (e: Exception) {
                errorMessage = "載入筆記時發生錯誤: ${e.message}"
                isLoading = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = noteTitle) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    // 編輯按鈕
                    IconButton(onClick = { navController.navigate("note_edit/$noteId") }) {
                        Icon(Icons.Default.Edit, contentDescription = "編輯筆記")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { navController.navigate("note_edit/$noteId") }
            ) {
                Icon(Icons.Default.Edit, contentDescription = "編輯筆記")
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            when {
                isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                errorMessage != null -> {
                    Text(
                        text = errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.Center)
                    )
                }
                else -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                    ) {
                        // 標題區域
                        Text(
                            text = noteTitle,
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp)
                        )

                        // 內容區域
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp)),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surface)
                                    .padding(16.dp)
                            ) {
                                if (noteContent.text.isEmpty()) {
                                    Text(
                                        text = "此筆記沒有內容",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                } else {
                                    Text(
                                        text = noteContent,
                                        modifier = Modifier.fillMaxWidth(),
                                        style = MaterialTheme.typography.bodyLarge, // 添加合適的文字樣式
                                        lineHeight = 24.sp  // 調整行高以改善可讀性
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}