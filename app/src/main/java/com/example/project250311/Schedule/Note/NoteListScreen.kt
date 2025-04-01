package com.example.project250311.Schedule.Note

import android.app.DatePickerDialog
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.project250311.Data.Note
import com.example.project250311.Data.NoteDatabase
import com.example.project250311.R
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun NoteListScreen(onNavigateToNoteEditor: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = NoteDatabase.getDatabase(context)
    val noteDao = db.noteDao()
    var notes by remember { mutableStateOf<List<Note>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedDate by remember { mutableStateOf<Long?>(null) } // For date filtering
    var deletedNote by remember { mutableStateOf<Note?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val expandedNotes = remember { mutableStateMapOf<Int, Boolean>() }
    var refreshTrigger by remember { mutableStateOf(0) }

    // Date picker dialog
    val calendar = Calendar.getInstance()
    val datePickerDialog = DatePickerDialog(
        context,
        { _, year, month, dayOfMonth ->
            calendar.set(year, month, dayOfMonth)
            selectedDate = calendar.timeInMillis
            refreshTrigger++ // Trigger refresh after selecting a date
        },
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH),
        calendar.get(Calendar.DAY_OF_MONTH)
    )

    LaunchedEffect(searchQuery, selectedDate, refreshTrigger) {
        scope.launch {
            try {
                notes = if (searchQuery.isEmpty() && selectedDate == null) {
                    noteDao.getAllNotes()
                } else if (selectedDate != null) {
                    // Filter by date
                    val startOfDay = Calendar.getInstance().apply {
                        timeInMillis = selectedDate!!
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }.timeInMillis
                    val endOfDay = startOfDay + 24 * 60 * 60 * 1000 - 1
                    noteDao.getNotesByDate(startOfDay, endOfDay).filter {
                        it.content?.contains(searchQuery, ignoreCase = true) ?: true
                    }
                } else {
                    noteDao.searchNotes("%$searchQuery%")
                }
                errorMessage = null
            } catch (e: Exception) {
                errorMessage = "Failed to load notes: ${e.message}"
            }
        }
    }

    LaunchedEffect(Unit) {
        refreshTrigger++
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Column {
            Text("便利貼們", fontSize = 24.sp)
            Spacer(modifier = Modifier.height(8.dp))

            // Search bar with date filter
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search notes...") },
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(onClick = { datePickerDialog.show() }) {
                    Image(
                        painter = painterResource(id = R.drawable.date_select),
                        contentDescription = "Select Date",
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            errorMessage?.let { message ->
                Text(
                    text = message,
                    color = Color.Red,
                    fontSize = 16.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            LazyColumn {
                items(notes) { note ->
                    val isExpanded = expandedNotes[note.id] ?: false
                    AnimatedVisibility(
                        visible = note != deletedNote,
                        exit = fadeOut(animationSpec = tween(500))
                    ) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(4.dp)
                                .clickable { expandedNotes[note.id] = !isExpanded },
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF9C4))
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                AnimatedVisibility(
                                    visible = isExpanded,
                                    enter = expandVertically(animationSpec = tween(300))
                                ) {
                                    Text(
                                        text = note.toAnnotatedString(),
                                        fontSize = 16.sp
                                    )
                                }
                                if (!isExpanded) {
                                    val truncatedText = try {
                                        note.toAnnotatedString().let { annotated ->
                                            if (annotated.length > 50) {
                                                annotated.subSequence(0, 50) + AnnotatedString("...")
                                            } else {
                                                annotated
                                            }
                                        }
                                    } catch (e: Exception) {
                                        AnnotatedString(note.content ?: "")
                                    }
                                    Text(
                                        text = truncatedText,
                                        fontSize = 16.sp,
                                        modifier = Modifier.size(100.dp)
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = SimpleDateFormat("yyyy-MM-dd HH:mm").format(note.timestamp),
                                        fontSize = 12.sp,
                                        color = Color.Gray,
                                        modifier = Modifier.padding(end = 8.dp)
                                    )
                                    IconButton(onClick = {
                                        scope.launch {
                                            try {
                                                deletedNote = note
                                                noteDao.delete(note)
                                                notes = notes.filter { it != note }
                                            } catch (e: Exception) {
                                                errorMessage = "Failed to delete note: ${e.message}"
                                            }
                                        }
                                    }) {
                                        Image(
                                            painter = painterResource(id = R.drawable.delete),
                                            contentDescription = "Delete Note",
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 將兩個 FAB 放入 Column，垂直排列
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp) // 兩個按鈕之間的間距
        ) {
            // Floating Action Button for "New Note" (在上方)
            FloatingActionButton(
                onClick = { onNavigateToNoteEditor() },
            ) {
                Image(
                    painter = painterResource(id = R.drawable.new_note),
                    contentDescription = "New Note",
                    modifier = Modifier.size(48.dp)
                )
            }
        }
    }
}