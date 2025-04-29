package com.example.project250311.Schedule.Note

import android.util.Log
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.project250311.Data.Note
import com.example.project250311.Data.NoteDao
import com.example.project250311.Data.NoteDatabase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import java.text.SimpleDateFormat
import java.util.*

// 筆記編輯器的狀態類
class NoteEditorState(
    initialText: TextFieldValue = TextFieldValue(""),
    initialNote: Note? = null,
    val isNewNote: Boolean = true
) {
    // 可觀察狀態
    var textFieldValue by mutableStateOf(initialText)
    var annotatedString by mutableStateOf(AnnotatedString(""))
    var isModified by mutableStateOf(false)
    var isSaving by mutableStateOf(false)
    var hasErrors by mutableStateOf(false)
    var errorMessage by mutableStateOf<String?>(null)
    var lastSavedTimestamp by mutableStateOf<Long?>(null)
    var originalNote by mutableStateOf(initialNote)

    // 格式化選項
    var isBold by mutableStateOf(false)
    var isItalic by mutableStateOf(false)
    var isUnderline by mutableStateOf(false)
    var isStrikethrough by mutableStateOf(false)
    var textColor by mutableStateOf(Color.Black)
    var fontSize by mutableStateOf(20.sp)
    var isHighlighted by mutableStateOf(false)

    // 編輯歷史記錄 (簡易的還原/重做功能)
    private val historyLimit = 30
    private val history = mutableListOf<HistoryState>()
    private var currentHistoryIndex = -1

    // 撤銷/重做狀態
    var canUndo by mutableStateOf(false)
    var canRedo by mutableStateOf(false)

    // 自動保存的Job引用
    var autoSaveJob: Job? = null

    init {
        // 初始化用原始筆記的內容
        if (initialNote != null) {
            try {
                annotatedString = initialNote.toAnnotatedString()
                textFieldValue = TextFieldValue(annotatedString = annotatedString)
                lastSavedTimestamp = initialNote.timestamp
                addToHistory()
            } catch (e: Exception) {
                Log.e("NoteEditorState", "Error loading note", e)
                errorMessage = "載入筆記時發生錯誤: ${e.message}"
                hasErrors = true
            }
        } else {
            addToHistory()
        }
        updateUndoRedoState()
    }

    // 更新文本並保持格式
    // 更新文本並保持格式
    fun updateText(newValue: TextFieldValue) {
        val previousText = textFieldValue.text
        val newText = newValue.text

        // 如果文字沒有變化，只是選擇範圍變化，直接更新選擇範圍
        if (previousText == newText) {
            textFieldValue = newValue.copy(annotatedString = annotatedString)
            return
        }

        try {
            // 找出文字變化的部分
            val commonPrefixLength = findCommonPrefixLength(previousText, newText)
            val commonSuffixLength = findCommonSuffixLength(
                previousText.substring(commonPrefixLength),
                newText.substring(commonPrefixLength)
            )

            // 確定變更區域
            val oldChangeStart = commonPrefixLength
            val oldChangeEnd = previousText.length - commonSuffixLength
            val newChangeStart = commonPrefixLength
            val newChangeEnd = newText.length - commonSuffixLength

            // 計算變化量
            val oldChangeLength = oldChangeEnd - oldChangeStart
            val newChangeLength = newChangeEnd - newChangeStart
            val lengthDelta = newChangeLength - oldChangeLength

            // 創建新的 Builder，但僅包含變更部分
            val builder = AnnotatedString.Builder()
            builder.append(newText)

            // 1. 保留變更前的樣式（前綴部分）
            annotatedString.spanStyles.forEach { style ->
                // 完全在前綴區域的樣式
                if (style.end <= oldChangeStart) {
                    builder.addStyle(style.item, style.start, style.end)
                }
                // 與前綴區域部分重疊的樣式
                else if (style.start < oldChangeStart) {
                    builder.addStyle(style.item, style.start, oldChangeStart)
                }
            }

            // 2. 如果有選擇範圍，為選擇範圍添加當前樣式
            val selection = newValue.selection
            if (!selection.collapsed && selection.start != selection.end) {
                builder.addStyle(
                    createCurrentStyle(),
                    selection.min,
                    selection.max
                )
            }
            // 否則，為新增的文字添加當前樣式
            else if (newChangeLength > 0) {
                builder.addStyle(
                    createCurrentStyle(),
                    newChangeStart,
                    newChangeEnd
                )
            }

            // 3. 保留變更後的樣式（後綴部分），並調整位置
            annotatedString.spanStyles.forEach { style ->
                // 完全在後綴區域的樣式
                if (style.start >= oldChangeEnd) {
                    val newStart = style.start + lengthDelta
                    val newEnd = style.end + lengthDelta
                    if (newStart < newText.length && newEnd <= newText.length) {
                        builder.addStyle(style.item, newStart, newEnd)
                    }
                }
                // 與後綴區域部分重疊的樣式
                else if (style.end > oldChangeEnd) {
                    val newStart = Math.max(newChangeEnd, style.start + lengthDelta)
                    val newEnd = style.end + lengthDelta
                    if (newStart < newText.length && newEnd <= newText.length) {
                        builder.addStyle(style.item, newStart, newEnd)
                    }
                }
            }

            // 更新狀態
            annotatedString = builder.toAnnotatedString()
            textFieldValue = newValue.copy(annotatedString = annotatedString)
            isModified = true
            addToHistory()

        } catch (e: Exception) {
            Log.e("NoteEditorState", "Error in updateText: ${e.message}", e)
        }
    }

    // 找到兩個字串的共同前綴長度
    private fun findCommonPrefixLength(str1: String, str2: String): Int {
        val minLength = Math.min(str1.length, str2.length)
        for (i in 0 until minLength) {
            if (str1[i] != str2[i]) {
                return i
            }
        }
        return minLength
    }

    // 找到兩個字串的共同後綴長度
    private fun findCommonSuffixLength(str1: String, str2: String): Int {
        val minLength = Math.min(str1.length, str2.length)
        for (i in 0 until minLength) {
            if (str1[str1.length - 1 - i] != str2[str2.length - 1 - i]) {
                return i
            }
        }
        return minLength
    }

    // 創建當前樣式
    private fun createCurrentStyle(): SpanStyle {
        return SpanStyle(
            fontSize = fontSize,
            fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
            fontStyle = if (isItalic) FontStyle.Italic else FontStyle.Normal,
            textDecoration = getTextDecoration(),
            color = textColor,
            background = if (isHighlighted) Color(0xFFFFE082) else Color.Transparent
        )
    }

    // 獲取組合的文本裝飾
    private fun getTextDecoration(): TextDecoration? {
        return when {
            isUnderline && isStrikethrough ->
                TextDecoration.combine(listOf(TextDecoration.Underline, TextDecoration.LineThrough))
            isUnderline -> TextDecoration.Underline
            isStrikethrough -> TextDecoration.LineThrough
            else -> null
        }
    }

    // 應用樣式到文本範圍
    private fun applyStyleToRange(
        builder: AnnotatedString.Builder,
        start: Int,
        end: Int,
        style: SpanStyle
    ) {
        if (start >= end || start < 0 || end > builder.length) return
        builder.addStyle(style, start, end)
    }

    // 切換粗體
    fun toggleBold() {
        isBold = !isBold
        applyCurrentStyleToSelection()
    }

    // 切換斜體
    fun toggleItalic() {
        isItalic = !isItalic
        applyCurrentStyleToSelection()
    }

    // 切換底線
    fun toggleUnderline() {
        isUnderline = !isUnderline
        applyCurrentStyleToSelection()
    }

    // 切換刪除線
    fun toggleStrikethrough() {
        isStrikethrough = !isStrikethrough
        applyCurrentStyleToSelection()
    }

    // 設置字體大小
    fun setFontSize(size: Float) {
        fontSize = size.sp
        applyCurrentStyleToSelection()
    }

    // 設置文字顏色
    fun updateTextColor(color: Color) {
        textColor = color
        applyCurrentStyleToSelection()
    }

    // 切換高亮
    fun toggleHighlight() {
        isHighlighted = !isHighlighted
        applyCurrentStyleToSelection()
    }

    // 應用當前樣式到選擇範圍
    private fun applyCurrentStyleToSelection() {
        val selection = textFieldValue.selection
        if (selection.collapsed) return

        val start = selection.min
        val end = selection.max

        val builder = AnnotatedString.Builder()
        builder.append(textFieldValue.text)

        // 保留所有現有樣式
        annotatedString.spanStyles.forEach { style ->
            // 只保留與選擇範圍不重疊的樣式
            if (style.end <= start || style.start >= end) {
                builder.addStyle(style.item, style.start, style.end)
            } else if (style.start < start && style.end > end) {
                // 與選擇範圍部分重疊的樣式需要分割
                builder.addStyle(style.item, style.start, start)
                builder.addStyle(style.item, end, style.end)
            } else if (style.start < start && style.end > start) {
                // 與開始重疊
                builder.addStyle(style.item, style.start, start)
            } else if (style.start < end && style.end > end) {
                // 與結束重疊
                builder.addStyle(style.item, end, style.end)
            }
        }

        // 應用段落樣式
        annotatedString.paragraphStyles.forEach { paraStyle ->
            builder.addStyle(paraStyle.item, paraStyle.start, paraStyle.end)
        }

        // 應用新的樣式到選擇範圍
        builder.addStyle(createCurrentStyle(), start, end)

        // 更新狀態
        annotatedString = builder.toAnnotatedString()
        textFieldValue = textFieldValue.copy(annotatedString = annotatedString)
        isModified = true
        addToHistory()
    }

    // 應用段落樣式到選擇範圍
    private fun applyParagraphStyleToSelection() {
        val text = textFieldValue.text
        val selection = textFieldValue.selection

        // 找出選擇範圍開始和結束的段落邊界
        var paraStart = selection.min
        var paraEnd = selection.max

        // 找段落開始
        while (paraStart > 0 && text[paraStart - 1] != '\n') {
            paraStart--
        }

        // 找段落結束
        while (paraEnd < text.length && text[paraEnd] != '\n') {
            paraEnd++
        }

        val builder = AnnotatedString.Builder()
        builder.append(text)

        // 保留所有現有樣式
        annotatedString.spanStyles.forEach { style ->
            builder.addStyle(style.item, style.start, style.end)
        }

        // 保留其他段落樣式
        annotatedString.paragraphStyles.forEach { paraStyle ->
            // 只保留與選定段落不重疊的樣式
            if (paraStyle.end <= paraStart || paraStyle.start >= paraEnd) {
                builder.addStyle(paraStyle.item, paraStyle.start, paraStyle.end)
            }
        }

        // 更新狀態
        annotatedString = builder.toAnnotatedString()
        textFieldValue = textFieldValue.copy(annotatedString = annotatedString)
        isModified = true
        addToHistory()
    }

    // 添加到歷史記錄
    private fun addToHistory() {
        // 如果我們不是在歷史記錄的最新點，移除後面的歷史
        if (currentHistoryIndex < history.size - 1) {
            history.subList(currentHistoryIndex + 1, history.size).clear()
        }

        // 添加當前狀態到歷史
        history.add(HistoryState(
            textFieldValue = textFieldValue.copy(),
            annotatedString = AnnotatedString(annotatedString.text,
                annotatedString.spanStyles,
                annotatedString.paragraphStyles)
        ))

        // 如果歷史記錄過長，移除最舊的
        if (history.size > historyLimit) {
            history.removeAt(0)
        } else {
            currentHistoryIndex = history.size - 1
        }

        updateUndoRedoState()
    }

    // 撤銷
    fun undo() {
        if (currentHistoryIndex > 0) {
            currentHistoryIndex--
            val historyState = history[currentHistoryIndex]
            textFieldValue = historyState.textFieldValue.copy()
            annotatedString = historyState.annotatedString
            isModified = true
            updateUndoRedoState()
        }
    }

    // 重做
    fun redo() {
        if (currentHistoryIndex < history.size - 1) {
            currentHistoryIndex++
            val historyState = history[currentHistoryIndex]
            textFieldValue = historyState.textFieldValue.copy()
            annotatedString = historyState.annotatedString
            isModified = true
            updateUndoRedoState()
        }
    }

    // 更新撤銷/重做狀態
    private fun updateUndoRedoState() {
        canUndo = currentHistoryIndex > 0
        canRedo = currentHistoryIndex < history.size - 1
    }

    // 啟動自動保存
    fun startAutoSave(scope: CoroutineScope, saveAction: suspend () -> Unit) {
        autoSaveJob?.cancel()
        autoSaveJob = scope.launch {
            while (true) {
                delay(30000) // 30秒自動保存
                if (isModified && !isSaving) {
                    try {
                        saveAction()
                    } catch (e: CancellationException) {
                        // 忽略取消異常
                        break
                    } catch (e: Exception) {
                        Log.e("NoteEditorState", "Auto-save failed", e)
                    }
                }
            }
        }
    }

    // 停止自動保存
    fun stopAutoSave() {
        autoSaveJob?.cancel()
        autoSaveJob = null
    }

    // 創建備份資料
    fun createBackupData(): String {
        return "Backup of note from ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}\n" +
                "Text: ${textFieldValue.text}\n" +
                "Modified: $isModified\n" +
                "Styles: ${annotatedString.spanStyles.size}"
    }
}

// 歷史記錄狀態
data class HistoryState(
    val textFieldValue: TextFieldValue,
    val annotatedString: AnnotatedString
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnhancedNoteScreen(
    onNavigateToNoteList: () -> Unit,
    noteId: Int? = null // 新增參數，如果有提供 noteId 表示是編輯模式
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = NoteDatabase.getDatabase(context)
    val noteDao = db.noteDao()
    val snackbarHostState = remember { SnackbarHostState() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val density = LocalDensity.current

    // 狀態
    var isLoading by remember { mutableStateOf(noteId != null) }
    var showDialog by remember { mutableStateOf(false) }
    var pendingNavigation by remember { mutableStateOf<String?>(null) }
    var showFormatPanel by remember { mutableStateOf(true) }
    var showUnsavedIndicator by remember { mutableStateOf(false) }

    // 創建焦點請求器
    val focusRequester = remember { FocusRequester() }

    // 筆記編輯器狀態
    val editorState = remember { NoteEditorState(isNewNote = noteId == null) }

    // 加載現有筆記的資料
    LaunchedEffect(noteId) {
        if (noteId != null) {
            try {
                withContext(Dispatchers.IO) {
                    val note = noteDao.getNoteById(noteId)
                    if (note != null) {
                        editorState.originalNote = note
                        val loadedAnnotatedString = note.toAnnotatedString()
                        editorState.annotatedString = loadedAnnotatedString
                        editorState.textFieldValue = TextFieldValue(
                            annotatedString = loadedAnnotatedString,
                            selection = TextRange(loadedAnnotatedString.length) // 游標位置在文本結尾
                        )
                        editorState.lastSavedTimestamp = note.timestamp
                    }
                }
            } catch (e: Exception) {
                Log.e("EnhancedNoteScreen", "Error loading note", e)
                snackbarHostState.showSnackbar("載入筆記時發生錯誤: ${e.message}")
            } finally {
                isLoading = false
            }
        }
    }

    // 啟動自動保存
    LaunchedEffect(Unit) {
        editorState.startAutoSave(scope) {
            saveNote(
                editorState = editorState,
                noteDao = noteDao,
                snackbarHostState = snackbarHostState,
                showSnackbar = false
            )
        }
    }

    // 自動聚焦到文本框
    LaunchedEffect(isLoading) {
        if (!isLoading) {
            try {
                // 延遲一下再聚焦，確保UI已經準備好
                delay(300)
                focusRequester.requestFocus()
            } catch (e: Exception) {
                Log.e("EnhancedNoteScreen", "Error requesting focus", e)
            }
        }
    }

    // 顯示未保存指示器
    LaunchedEffect(editorState.isModified) {
        if (editorState.isModified) {
            delay(500) // 延遲顯示以避免頻繁閃爍
            showUnsavedIndicator = true
        } else {
            showUnsavedIndicator = false
        }
    }

    // 處理退出前的保存對話框
    BackHandler(enabled = editorState.isModified) {
        showDialog = true
        pendingNavigation = "Back"
    }

    // 儲存便利貼的函數
    fun saveNoteAndNavigate(onSuccess: () -> Unit = {}) {
        scope.launch {
            val success = saveNote(
                editorState = editorState,
                noteDao = noteDao,
                snackbarHostState = snackbarHostState
            )
            if (success) {
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

    // 確認對話框
    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("便利貼還未儲存") },
            text = { Text("請問是否儲存?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        saveNoteAndNavigate {
                            showDialog = false
                            pendingNavigation?.let { handleNavigation(it) }
                            pendingNavigation = null
                        }
                    },
                    enabled = !editorState.isSaving
                ) {
                    Text("是")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        // 如果不儲存，可以在這裡創建一個備份
                        val backupData = editorState.createBackupData()
                        Log.d("EnhancedNoteScreen", "Unsaved note backup: $backupData")

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

    // 使用 Scaffold 提供 SnackBar 支援
    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(if (noteId == null) "新增便利貼" else "編輯便利貼")
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (editorState.isModified) {
                                showDialog = true
                                pendingNavigation = "Back"
                            } else {
                                onNavigateToNoteList()
                            }
                        }
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    // 自動保存指示器
                    AnimatedVisibility(visible = showUnsavedIndicator) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            if (editorState.isSaving) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "未保存",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    // 撤銷按鈕
                    IconButton(
                        onClick = { editorState.undo() },
                        enabled = editorState.canUndo
                    ) {
                        Icon(
                            imageVector = Icons.Default.Undo,
                            contentDescription = "撤銷",
                            tint = if (editorState.canUndo)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        )
                    }

                    // 重做按鈕
                    IconButton(
                        onClick = { editorState.redo() },
                        enabled = editorState.canRedo
                    ) {
                        Icon(
                            imageVector = Icons.Default.Redo,
                            contentDescription = "重做",
                            tint = if (editorState.canRedo)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        )
                    }

                    // 格式工具欄切換按鈕
                    IconButton(
                        onClick = { showFormatPanel = !showFormatPanel }
                    ) {
                        Icon(
                            imageVector = if (showFormatPanel)
                                Icons.Default.FormatColorReset
                            else
                                Icons.Default.FormatPaint,
                            contentDescription = if (showFormatPanel) "隱藏格式工具" else "顯示格式工具"
                        )
                    }

                    // 保存按鈕
                    IconButton(
                        onClick = { saveNoteAndNavigate() },
                        enabled = !editorState.isSaving && editorState.isModified
                    ) {
                        Icon(
                            imageVector = Icons.Default.Save,
                            contentDescription = "儲存",
                            tint = if (!editorState.isSaving && editorState.isModified)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
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
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    // 格式工具列
                    AnimatedVisibility(visible = showFormatPanel) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            // 第一行工具列
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                // 粗體按鈕
                                FormatButton(
                                    icon = Icons.Default.FormatBold,
                                    description = "粗體",
                                    isSelected = editorState.isBold,
                                    onClick = { editorState.toggleBold() }
                                )

                                // 斜體按鈕
                                FormatButton(
                                    icon = Icons.Default.FormatItalic,
                                    description = "斜體",
                                    isSelected = editorState.isItalic,
                                    onClick = { editorState.toggleItalic() }
                                )

                                // 底線按鈕
                                FormatButton(
                                    icon = Icons.Default.FormatUnderlined,
                                    description = "底線",
                                    isSelected = editorState.isUnderline,
                                    onClick = { editorState.toggleUnderline() }
                                )

                                // 刪除線按鈕
                                FormatButton(
                                    icon = Icons.Default.FormatStrikethrough,
                                    description = "刪除線",
                                    isSelected = editorState.isStrikethrough,
                                    onClick = { editorState.toggleStrikethrough() }
                                )

                                // 大字體按鈕
                                FormatButton(
                                    icon = Icons.Default.FormatSize,
                                    description = "大字體",
                                    isSelected = editorState.fontSize.value >= 24,
                                    onClick = {
                                        if (editorState.fontSize.value >= 24) {
                                            editorState.setFontSize(20f)
                                        } else {
                                            editorState.setFontSize(24f)
                                        }
                                    }
                                )

                                // 小字體按鈕
                                FormatButton(
                                    content = {
                                        Text(
                                            text = "小",
                                            fontSize = 12.sp,
                                            color = if (editorState.fontSize.value <= 16)
                                                MaterialTheme.colorScheme.primary
                                            else
                                                MaterialTheme.colorScheme.onSurface
                                        )
                                    },
                                    description = "小字體",
                                    isSelected = editorState.fontSize.value <= 16,
                                    onClick = {
                                        if (editorState.fontSize.value <= 16) {
                                            editorState.setFontSize(20f)
                                        } else {
                                            editorState.setFontSize(16f)
                                        }
                                    }
                                )
                                // 紅色文字按鈕
                                FormatButton(
                                    content = {
                                        Icon(
                                            imageVector = Icons.Default.FormatColorText,
                                            contentDescription = "紅色",
                                            tint = Color.Red
                                        )
                                    },
                                    description = "紅色文字",
                                    isSelected = editorState.textColor == Color.Red,
                                    onClick = {
                                        if (editorState.textColor == Color.Red) {
                                            editorState.updateTextColor(Color.Black)
                                        } else {
                                            editorState.updateTextColor(Color.Red)
                                        }
                                    }
                                )

                                // 藍色文字按鈕
                                FormatButton(
                                    content = {
                                        Icon(
                                            imageVector = Icons.Default.FormatColorText,
                                            contentDescription = "藍色",
                                            tint = Color.Blue
                                        )
                                    },
                                    description = "藍色文字",
                                    isSelected = editorState.textColor == Color.Blue,
                                    onClick = {
                                        if (editorState.textColor == Color.Blue) {
                                            editorState.updateTextColor(Color.Black)
                                        } else {
                                            editorState.updateTextColor(Color.Blue)
                                        }
                                    }
                                )

                                // 綠色文字按鈕
                                FormatButton(
                                    content = {
                                        Icon(
                                            imageVector = Icons.Default.FormatColorText,
                                            contentDescription = "綠色",
                                            tint = Color.Green
                                        )
                                    },
                                    description = "綠色文字",
                                    isSelected = editorState.textColor == Color.Green,
                                    onClick = {
                                        if (editorState.textColor == Color.Green) {
                                            editorState.updateTextColor(Color.Black)
                                        } else {
                                            editorState.updateTextColor(Color.Green)
                                        }
                                    }
                                )

                                // 背景標記按鈕
                                FormatButton(
                                    icon = Icons.Default.Colorize,
                                    description = "背景標記",
                                    isSelected = editorState.isHighlighted,
                                    onClick = { editorState.toggleHighlight() }
                                )
                            }
                        }
                    }
                    // 上次保存時間提示
                    editorState.lastSavedTimestamp?.let { timestamp ->
                        val formattedTime = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                            .format(Date(timestamp))
                        Text(
                            text = "上次儲存: $formattedTime",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                        )
                    }

                    // 文本編輯區域
                    TextField(
                        value = editorState.textFieldValue,
                        onValueChange = { newValue ->
                            editorState.updateText(newValue)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(horizontal = 16.dp)
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                shape = MaterialTheme.shapes.medium
                            )
                            .focusRequester(focusRequester),
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

                }
            }
        }
    }
}

// 自定義格式按鈕
@Composable
fun FormatButton(
    icon: ImageVector? = null,
    content: @Composable (() -> Unit)? = null,
    description: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .background(
                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                else Color.Transparent,
                shape = MaterialTheme.shapes.small
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = description,
                tint = if (isSelected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface
            )
        } else if (content != null) {
            content()
        }
    }
}

// 處理返回事件
@Composable
fun BackHandler(enabled: Boolean = true, onBack: () -> Unit) {
    val currentOnBack by rememberUpdatedState(onBack)
    val backCallback = remember {
        object : androidx.activity.OnBackPressedCallback(enabled) {
            override fun handleOnBackPressed() {
                currentOnBack()
            }
        }
    }

    // 更新啟用狀態
    SideEffect {
        backCallback.isEnabled = enabled
    }

    val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    DisposableEffect(backDispatcher) {
        backDispatcher?.addCallback(backCallback)
        onDispose {
            backCallback.remove()
        }
    }
}

// 保存筆記功能
private suspend fun saveNote(
    editorState: NoteEditorState,
    noteDao: NoteDao,
    snackbarHostState: SnackbarHostState,
    showSnackbar: Boolean = true
): Boolean {
    if (editorState.isSaving) return false

    editorState.isSaving = true

    return try {
        withContext(Dispatchers.IO) {
            val currentTimestamp = System.currentTimeMillis()

            if (editorState.originalNote != null) {
                // 更新現有筆記
                val updatedNote = editorState.originalNote!!.copy(
                    content = editorState.textFieldValue.text,
                    formattedContent = Note(0, null, null)
                        .toFormattedContent(editorState.annotatedString),
                    timestamp = currentTimestamp
                )
                noteDao.update(updatedNote)
                editorState.originalNote = updatedNote
            } else {
                // 創建新筆記
                val newNote = Note(
                    id = 0, // Room 會自動生成 ID
                    content = editorState.textFieldValue.text,
                    formattedContent = Note(0, null, null)
                        .toFormattedContent(editorState.annotatedString),
                    timestamp = currentTimestamp
                )
                val newId = noteDao.insert(newNote).toInt()
                // 使用返回的 ID 更新原始筆記
                editorState.originalNote = newNote.copy(id = newId)
            }

            editorState.lastSavedTimestamp = currentTimestamp
            editorState.isModified = false

            if (showSnackbar) {
                withContext(Dispatchers.Main) {
                    snackbarHostState.showSnackbar(
                        message = if (editorState.isNewNote) "便利貼已新增" else "便利貼已更新",
                        duration = SnackbarDuration.Short
                    )
                }
            }

            true
        }
    } catch (e: Exception) {
        Log.e("EnhancedNoteScreen", "Error saving note", e)

        if (showSnackbar) {
            withContext(Dispatchers.Main) {
                snackbarHostState.showSnackbar(

                    message = "儲存失敗: ${e.message}",
                    duration = SnackbarDuration.Short
                )
            }
        }

        false
    } finally {
        editorState.isSaving = false
    }
}