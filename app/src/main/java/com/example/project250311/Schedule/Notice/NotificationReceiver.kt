package com.example.project250311.Schedule.Notice

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.project250311.MainActivity

class NotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // 詳細日誌記錄
        Log.d("NotificationReceiver", "Notification broadcast received")

        // 記錄所有 Intent 額外資訊
        intent.extras?.keySet()?.forEach { key ->
            Log.d("NotificationReceiver", "Extra: $key = ${intent.extras?.get(key)}")
        }

        // 檢查權限並詳細記錄
        if (!checkNotificationPermission(context)) {
            Log.e("NotificationReceiver", "Notification permission denied")
            return
        }

        // 從 Intent 中取得傳遞過來的課程資訊
        val courseId = intent.getStringExtra("course_id") ?: "0"
        val courseName = intent.getStringExtra("course_name") ?: "未知課程"
        val teacherName = intent.getStringExtra("teacher_name") ?: ""
        val location = intent.getStringExtra("location") ?: ""
        val startTime = intent.getStringExtra("start_time") ?: ""
        val endTime = intent.getStringExtra("end_time") ?: ""
        val isNotificationEnabled = intent.getBooleanExtra("is_notification_enabled", false)

        // 檢查通知是否已啟用
        Log.d("NotificationReceiver", "Notification enabled: $isNotificationEnabled")
        if (!isNotificationEnabled) {
            Log.e("NotificationReceiver", "Notifications are disabled for this course")
            return
        }

        val channelId = "notify_id"
        // 使用 courseId 的 hashCode 作為通知的唯一識別碼
        val notificationId = courseId.hashCode()

        // 建立查看課表的 PendingIntent
        val scheduleIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("OPEN_SCHEDULE", true)  // 標記開啟課表頁面
        }
        val schedulePendingIntent = PendingIntent.getActivity(
            context, 0, scheduleIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 建立請假系統的 PendingIntent
        val leaveIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            // 標記開啟請假系統
            putExtra("OPEN_LEAVE", true)
            // 可以添加課程資訊幫助預填寫請假單
            putExtra("COURSE_NAME", courseName)
            putExtra("TEACHER_NAME", teacherName)
        }
        val leavePendingIntent = PendingIntent.getActivity(
            context, 1, leaveIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 使用 BigTextStyle 擴展通知的顯示內容
        val bigTextStyle = NotificationCompat.BigTextStyle()
            .bigText("課程名稱: $courseName\n" +
                    "老師名稱: $teacherName\n" +
                    "地點: $location\n" +
                    "時間: $startTime ~ $endTime")

        // 建立通知內容
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle("通知提醒: $courseName")
            .setContentText("該去上課了！")
            .setStyle(bigTextStyle) // 設定為 BigTextStyle
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(schedulePendingIntent)  // 點擊通知時開啟課表
            .addAction(android.R.drawable.ic_menu_agenda, "查看課表", schedulePendingIntent)
            .addAction(android.R.drawable.ic_menu_edit, "申請請假", leavePendingIntent)

        // 發送通知，使用 try-catch 處理可能的安全異常
        try {
            with(NotificationManagerCompat.from(context)) {
                notify(notificationId, builder.build())
            }
            Log.d("NotificationReceiver", "Notification sent successfully for course: $courseName")
        } catch (e: SecurityException) {
            Log.e("NotificationReceiver", "Failed to send notification due to permission issue", e)
            // 可以選擇引導用戶到通知設置
            tryOpenNotificationSettings(context)
        }
    }

    // 檢查通知權限
    private fun checkNotificationPermission(context: Context): Boolean {
        // 檢查 TIRAMISU (API 33) 及以上版本的通知權限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Log.w("NotificationReceiver", "Notification permission not granted")
                return false
            }
        }
        return true
    }

    // 嘗試開啟通知設定頁面
    private fun tryOpenNotificationSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e("NotificationReceiver", "Failed to open notification settings", e)
        }
    }
}