package com.example.project250311.Schedule.Note

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.project250311.Data.Note
import com.example.project250311.Data.NoteDatabase
import kotlinx.coroutines.launch

@Composable
fun NotesScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = NoteDatabase.getDatabase(context)
    val noteDao = db.noteDao()
    var notes by remember { mutableStateOf<List<Note>>(emptyList()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // 加載所有筆記
    LaunchedEffect(Unit) {
        scope.launch {
            try {
                notes = noteDao.getAllNotes()
                errorMessage = null
                println("Loaded notes: $notes")
            } catch (e: Exception) {
                errorMessage = "無法載入筆記：${e.message}"
                println("Failed to load notes: ${e.message}")
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "筆記列表",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = {
                // 使用導航控制器而不是啟動新的 Activity
                navController.navigate("note/create")
            }
        ) {
            Text("新增筆記")
        }
        Spacer(modifier = Modifier.height(16.dp))

        // 顯示錯誤訊息（如果有）
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
                Text(
                    text = note.name,
                    fontSize = 18.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            // 使用導航控制器而不是啟動新的 Activity
                            navController.navigate("note/edit/${note.id}")
                        }
                        .padding(8.dp)
                )
                Divider(color = Color.Gray, thickness = 1.dp)
            }
        }
    }
}

@Composable
fun NoteListScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = NoteDatabase.getDatabase(context)
    val noteDao = db.noteDao()
    var notes by remember { mutableStateOf<List<Note>>(emptyList()) }

    // Load notes from database
    LaunchedEffect(key1 = true) {
        scope.launch {
            notes = noteDao.getAllNotes()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "筆記列表",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )

            Button(
                onClick = {
                    // Navigate to create new note
                    navController.navigate("note_edit")
                }
            ) {
                Text("新增筆記")
            }
        }

        Divider()

        LazyColumn {
            items(notes) { note ->
                NoteItem(note = note, onClick = {
                    // Navigate to edit existing note
                    navController.navigate("note_edit/${note.id}")
                })
            }
        }
    }
}

@Composable
fun NoteItem(note: Note, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = note.name,
                fontSize = 18.sp
            )
        }
    }
}