package com.example.project250311.Schedule.NoSchool

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.project250311.ui.theme.*

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
            MorandiTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    LeaveHistoryScreen(viewModel = viewModel)
                }
            }
        }
    }
}

@Composable
fun LeaveHistoryScreen(viewModel: LeaveDatabase.LeaveViewModel) {
    val leaves by viewModel.allLeaves.observeAsState(emptyList())
    var searchQuery by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { viewModel.loadAllLeaves() }

    val groupedLeaves = leaves
        .filter {
            searchQuery.isBlank() || it.recordType.contains(searchQuery, true) ||
                    it.leave_type.contains(searchQuery, true) ||
                    it.date_leave.contains(searchQuery, true) ||
                    it.courseName.contains(searchQuery, true)
        }
        .groupBy { it.courseName }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            // 搜尋欄
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("搜尋課程、類型、請假類型、日期") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = MorandiPrimary,
                    unfocusedIndicatorColor = MorandiSecondary,
                    cursorColor = MorandiPrimary,
                    focusedLabelColor = MorandiPrimary,
                    unfocusedLabelColor = MorandiSecondary,

                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (groupedLeaves.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("沒有符合條件的請假紀錄", color = MorandiOnSurface)
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    groupedLeaves.forEach { (courseName, leavesForCourse) ->
                        item {
                            CourseCard(courseName, leavesForCourse)
                        }
                    }
                }
            }
        }
    }
}

fun getLeaveTypeName(type: String): String = when(type.toIntOrNull()) {
    2 -> "病假"
    3 -> "喪假"
    4 -> "事假"
    5 -> "生理假"
    6 -> "分娩假"
    7 -> "陪產假"
    8 -> "歲時祭儀假"
    9 -> "心理假"
    else -> "其他"
}

@Composable
fun CourseCard(courseName: String, leaves: List<LeaveData>) {
    var expanded by remember { mutableStateOf(false) }
    val totalHours = leaves.sumOf { it.hours }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MorandiSurface),
        elevation = CardDefaults.cardElevation(0.dp) // ✅ 取消陰影
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Text(text = courseName, style = MaterialTheme.typography.titleMedium, color = MorandiOnSurface)
                    Text(text = "總請假時數: $totalHours 小時", style = MaterialTheme.typography.bodySmall, color = MorandiOnSecondary)
                }
                Button(
                    onClick = { expanded = !expanded },
                    shape = MaterialTheme.shapes.large,
                    colors = ButtonDefaults.buttonColors(containerColor = MorandiPrimary),
                    elevation = ButtonDefaults.buttonElevation(0.dp) // ✅ 取消陰影
                ) {
                    Text(if (expanded) "收起" else "展開")
                }
            }

            if (expanded) {
                Spacer(modifier = Modifier.height(8.dp))
                leaves.forEach { leave ->
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        Text("日期: ${leave.date_leave}", color = MorandiOnSurface)
                        Text("請假類型: ${getLeaveTypeName(leave.leave_type)}", color = MorandiOnSurface)
                        Text("時數: ${leave.hours} 小時", color = MorandiOnSurface)
                        Divider(thickness = 0.8.dp, color = MorandiSecondary)
                    }
                }
            }
        }
    }
}