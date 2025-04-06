package com.example.project250311.Schedule.Notice

import android.app.PendingIntent
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.project250311.Data.Schedule
import com.example.project250311.Schedule.Notice.NotificationUtils
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime


@Composable
fun NotificationTestScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

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
                    // 創建一個測試課程
                    val testCourse = Schedule(
                        id = "test_${System.currentTimeMillis()}",
                        courseName = "測試課程",
                        teacherName = "測試教師",
                        location = "測試教室",
                        weekDay = "星期一", // 或當天的星期
                        startTime = LocalTime.now().plusMinutes(1),
                        endTime = LocalTime.now().plusMinutes(30),
                        courseDates = listOf(LocalDate.now()),
                        isNotificationEnabled = true
                    )

                    // 設置1分鐘後發送的通知
                    val alarmTime = LocalTime.now().plusMinutes(1)
                    Toast.makeText(
                        context,
                        "設置測試通知，將在1分鐘後發送",
                        Toast.LENGTH_LONG
                    ).show()

                    // 使用您的通知工具類設置通知
                    NotificationUtils.setNotificationAlarm(
                        context,
                        alarmTime,
                        testCourse,
                        "測試"
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(0.8f)
        ) {
            Text("發送1分鐘後的測試通知")
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 立即發送通知的按鈕
        Button(
            onClick = {
                // 創建一個測試課程
                val testCourse = Schedule(
                    id = "immediate_test_${System.currentTimeMillis()}",
                    courseName = "立即測試課程",
                    teacherName = "測試教師",
                    location = "測試教室",
                    weekDay = "今天",
                    startTime = LocalTime.now(),
                    endTime = LocalTime.now().plusMinutes(30),
                    courseDates = listOf(LocalDate.now()),
                    isNotificationEnabled = true
                )

                // 創建通知意圖
                val intent = NotificationUtils.createNotificationIntent(context, testCourse)

                // 直接發送通知
                try {
                    val pendingIntent = PendingIntent.getBroadcast(
                        context,
                        testCourse.id.hashCode(),
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )

                    // 模擬收到廣播
                    intent.putExtra("test_direct_send", true)
                    context.sendBroadcast(intent)

                    Toast.makeText(
                        context,
                        "已直接發送測試通知",
                        Toast.LENGTH_SHORT
                    ).show()
                } catch (e: Exception) {
                    Toast.makeText(
                        context,
                        "發送測試通知失敗: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                    Log.e("NotificationTest", "發送通知失敗", e)
                }
            },
            modifier = Modifier.fillMaxWidth(0.8f)
        ) {
            Text("立即發送測試通知")
        }
    }
}