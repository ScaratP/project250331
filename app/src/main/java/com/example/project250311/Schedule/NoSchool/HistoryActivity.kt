package com.example.project250311.Schedule.NoSchool

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class HistoryActivity : ComponentActivity() {

    private val viewModel: LeaveDatabase.LeaveViewModel by viewModels {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val db = LeaveDatabase.getDatabase(applicationContext)
                val repository = LeaveDatabase.Companion.LeaveRepository(db.leaveDao())
                @Suppress("UNCHECKED_CAST")
                return LeaveDatabase.LeaveViewModel(repository) as T
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LeaveHistoryScreen(viewModel=viewModel, onRequestLeave = {})
        }

    }
}

@Composable
fun LeaveHistoryScreen(viewModel: LeaveDatabase.LeaveViewModel, onRequestLeave: () -> Unit) {
    val leaves by viewModel.allLeaves.observeAsState(emptyList())  // 監聽 LiveData

    // 使用 LaunchedEffect 在 Composable 首次組合時載入資料
    LaunchedEffect(Unit) {
        viewModel.loadAllLeaves()
    }
    Log.d("LeaveHistoryScreen", "leaves 的值: $leaves") // 添加這行
    Scaffold(
        /*topBar = {
            TopAppBar(
                title = { Text("請假紀錄") }
            )
        }*/
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (leaves.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("沒有請假紀錄")
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(leaves) { leave ->
                        LeaveItem(leave)
                    }
                }
            }
        }
    }
}

@Composable
fun LeaveItem(leave: LeaveData) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "類型: ${leave.recordType}", style = MaterialTheme.typography.bodyLarge)
            Text(text = "請假類型: ${leave.leave_type}", style = MaterialTheme.typography.bodyMedium)
            Text(text = "日期: ${leave.date_leave}", style = MaterialTheme.typography.bodyMedium)
            Text(text = "課程/集會: ${leave.courseName}", style = MaterialTheme.typography.bodyMedium)
            Text(text = "時數: ${leave.hours} 小時", style = MaterialTheme.typography.bodyMedium)
        }
    }
}
