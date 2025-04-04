package com.example.project250311.Schedule.Notice

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.project250311.Data.AppDatabase
import com.example.project250311.Data.CourseRepository
import com.example.project250311.Schedule.Notice.NotificationUtils.setNotificationAlarm
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d("BootReceiver", "設備重啟完成，重新設置所有通知")

            // 使用協程在背景執行資料庫操作
            val scope = CoroutineScope(Dispatchers.IO)
            scope.launch {
                val db = AppDatabase.getDatabase(context)
                val repository = CourseRepository(db.courseDao())
                val courses = repository.getAllCoursesWithNotificationsEnabled()

                Log.d("BootReceiver", "找到 ${courses.size} 門已啟用通知的課程")

                // 為每門課程重新設置通知
                courses.forEach { course ->
                    Log.d("BootReceiver", "重新設置課程通知: ${course.courseName}")
                    val alarmTime = course.startTime.minusMinutes(10)
                    setNotificationAlarm(context, alarmTime, course, course.weekDay)
                }
            }
        }
    }
}