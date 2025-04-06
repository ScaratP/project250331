package com.example.project250311.Schedule.Notice

import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.project250311.Data.AppDatabase
import com.example.project250311.Data.CourseRepository
import com.example.project250311.Schedule.Notice.BootReceiver
import com.example.project250311.Schedule.Notice.NotificationUtils
import kotlinx.coroutines.launch

@Composable
fun BootReceiverTestScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val (testResults, setTestResults) = remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Button(
            onClick = {
                scope.launch {
                    try {
                        // 模擬 BootReceiver 的行為
                        val db = AppDatabase.getDatabase(context)
                        val repository = CourseRepository(db.courseDao())
                        val courses = repository.getAllCoursesWithNotificationsEnabled()

                        val results = StringBuilder()
                        results.append("找到 ${courses.size} 門已啟用通知的課程\n\n")

                        courses.forEach { course ->
                            results.append("課程: ${course.courseName}\n")
                            results.append("時間: ${course.weekDay} ${course.startTime}-${course.endTime}\n")

                            // 嘗試為每門課程設置通知
                            val alarmTime = course.startTime.minusMinutes(10)
                            try {
                                NotificationUtils.setNotificationAlarm(
                                    context,
                                    alarmTime,
                                    course,
                                    course.weekDay
                                )
                                results.append("✅ 通知設置成功\n\n")
                            } catch (e: Exception) {
                                results.append("❌ 通知設置失敗: ${e.message}\n\n")
                            }
                        }

                        setTestResults(results.toString())
                    } catch (e: Exception) {
                        Log.e("BootReceiverTest", "測試失敗", e)
                        setTestResults("測試失敗: ${e.message}")
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(0.8f)
        ) {
            Text("測試 BootReceiver 功能")
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 顯示測試結果
        testResults?.let {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp)),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Text(
                    text = it,
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 直接觸發 BootReceiver
        Button(
            onClick = {
                try {
                    val bootIntent = Intent(context, BootReceiver::class.java).apply {
                        action = Intent.ACTION_BOOT_COMPLETED
                    }
                    context.sendBroadcast(bootIntent)
                    Toast.makeText(
                        context,
                        "已發送 BOOT_COMPLETED 廣播",
                        Toast.LENGTH_SHORT
                    ).show()
                } catch (e: Exception) {
                    Toast.makeText(
                        context,
                        "發送廣播失敗: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            },
            modifier = Modifier.fillMaxWidth(0.8f)
        ) {
            Text("直接觸發 BootReceiver")
        }
        // Instead of sending a broadcast, call the BootReceiver's onReceive method directly
        Button(
            onClick = {
                try {
                    val bootIntent = Intent().apply {
                        action = Intent.ACTION_BOOT_COMPLETED
                    }
                    val bootReceiver = BootReceiver()
                    bootReceiver.onReceive(context, bootIntent)

                    Toast.makeText(
                        context,
                        "已直接調用 BootReceiver.onReceive()",
                        Toast.LENGTH_SHORT
                    ).show()
                } catch (e: Exception) {
                    Log.e("BootReceiverTest", "調用失敗", e)
                    Toast.makeText(
                        context,
                        "調用失敗: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            },
            modifier = Modifier.fillMaxWidth(0.8f)
        ) {
            Text("直接調用 BootReceiver")
        }
    }
}