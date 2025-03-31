//package com.example.project250311.Schedule.Note
//
//import android.content.Intent
//import android.os.Bundle
//import androidx.activity.ComponentActivity
//import androidx.activity.compose.setContent
//import androidx.compose.foundation.clickable
//import androidx.compose.foundation.layout.*
//import androidx.compose.foundation.lazy.LazyColumn
//import androidx.compose.foundation.lazy.items
//import androidx.compose.material3.*
//import androidx.compose.runtime.*
//import androidx.compose.ui.Modifier
//import androidx.compose.ui.graphics.Color
//import androidx.compose.ui.platform.LocalContext
//import androidx.compose.ui.text.font.FontWeight
//import androidx.compose.ui.unit.dp
//import androidx.compose.ui.unit.sp
//import com.example.project250311.Data.Note
//import com.example.project250311.Data.NoteDatabase
//import kotlinx.coroutines.launch
//
//class NoteListActivity : ComponentActivity() {
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//        setContent {
//            NoteListScreen()
//        }
//    }
//}
//
//@Composable
//fun NoteListScreen() {
//    val context = LocalContext.current
//    val scope = rememberCoroutineScope()
//    val db = NoteDatabase.getDatabase(context)
//    val noteDao = db.noteDao()
//    var notes by remember { mutableStateOf<List<Note>>(emptyList()) }
//    var errorMessage by remember { mutableStateOf<String?>(null) }
//
//    // 加載所有筆記
//    LaunchedEffect(Unit) {
//        scope.launch {
//            try {
//                notes = noteDao.getAllNotes()
//                errorMessage = null
//                println("Loaded notes: $notes")
//            } catch (e: Exception) {
//                errorMessage = "無法載入筆記：${e.message}"
//                println("Failed to load notes: ${e.message}")
//            }
//        }
//    }
//
//    Column(
//        modifier = Modifier
//            .fillMaxSize()
//            .padding(16.dp)
//    ) {
//        Text(
//            text = "筆記列表",
//            fontSize = 24.sp,
//            fontWeight = FontWeight.Bold
//        )
//        Spacer(modifier = Modifier.height(16.dp))
//        Button(
//            onClick = {
//                val intent = Intent(context, NoteActivity::class.java)
//                context.startActivity(intent)
//            }
//        ) {
//            Text("新增筆記")
//        }
//        Spacer(modifier = Modifier.height(16.dp))
//
//        // 顯示錯誤訊息（如果有）
//        errorMessage?.let { message ->
//            Text(
//                text = message,
//                color = Color.Red,
//                fontSize = 16.sp,
//                modifier = Modifier.padding(bottom = 8.dp)
//            )
//        }
//
//        LazyColumn {
//            items(notes) { note ->
//                Text(
//                    text = note.name,
//                    fontSize = 18.sp,
//                    modifier = Modifier
//                        .fillMaxWidth()
//                        .clickable {
//                            val intent = Intent(context, NoteActivity::class.java)
//                            intent.putExtra("NOTE_ID", note.id)
//                            context.startActivity(intent)
//                        }
//                        .padding(8.dp)
//                )
//                Divider(color = Color.Gray, thickness = 1.dp)
//            }
//        }
//    }
//}