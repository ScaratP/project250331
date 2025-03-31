package com.example.project250311.Schedule.Notice

import android.app.NotificationManager
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
import com.example.project250311.Schedule.GetSchedule.ScheduleScreen

class NotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // 記錄廣播接收的詳細信息
        logReceivedIntent(intent)

        // 完整的權限和通知狀態檢查
        if (!checkNotificationAccess(context)) {
            Log.e("NotificationReceiver", "Cannot send notification")
            return
        }

        // 提取課程信息
        val courseInfo = extractCourseInfo(intent)

        // 檢查通知是否啟用
        if (!courseInfo.isNotificationEnabled) {
            Log.d("NotificationReceiver", "Notification is disabled for course: ${courseInfo.courseName}")
            return
        }

        // 檢查時間是否合適
        if (!isValidNotificationTime(courseInfo)) {
            Log.d("NotificationReceiver", "Current time not suitable for notification")
            return
        }

        // 發送通知
        try {
            sendCourseNotification(context, courseInfo)
        } catch (e: SecurityException) {
            Log.e("NotificationReceiver", "Security exception when sending notification", e)
            // 可以選擇引導用戶到通知設置
            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        }
    }

    private fun checkNotificationAccess(context: Context): Boolean {
        // 檢查通知權限
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

        // 檢查是否可以發送通知
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (!notificationManager.areNotificationsEnabled()) {
            Log.w("NotificationReceiver", "Notifications are disabled for the app")
            return false
        }

        // 檢查特定通道是否啟用
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = notificationManager.getNotificationChannel("notify_id")
            if (channel?.importance == NotificationManager.IMPORTANCE_NONE) {
                Log.w("NotificationReceiver", "Notification channel is blocked")
                return false
            }
        }

        return true
    }

    private fun logReceivedIntent(intent: Intent) {
        Log.d("NotificationReceiver", "Notification broadcast received")
        intent.extras?.keySet()?.forEach { key ->
            Log.d("NotificationReceiver", "Extra: $key = ${intent.extras?.get(key)}")
        }
    }

    private fun extractCourseInfo(intent: Intent): CourseNotificationInfo {
        return CourseNotificationInfo(
            courseId = intent.getStringExtra("course_id") ?: "0",
            courseName = intent.getStringExtra("course_name") ?: "未知課程",
            teacherName = intent.getStringExtra("teacher_name") ?: "",
            location = intent.getStringExtra("location") ?: "",
            startTime = intent.getStringExtra("start_time") ?: "",
            endTime = intent.getStringExtra("end_time") ?: "",
            isNotificationEnabled = intent.getBooleanExtra("is_notification_enabled", false),
            scheduledTime = intent.getLongExtra("scheduled_time", 0L)
        )
    }

    private fun isValidNotificationTime(courseInfo: CourseNotificationInfo): Boolean {
        // 如果沒有提供預定時間，默認允許
        if (courseInfo.scheduledTime == 0L) {
            Log.d("NotificationReceiver", "No scheduled time provided")
            return true
        }

        val currentTime = System.currentTimeMillis()
        val timeDifference = Math.abs(currentTime - courseInfo.scheduledTime)

        // 允許在預定時間前後 15 分鐘內觸發通知
        val allowedTimeDifference = 15 * 60 * 1000L

        return if (timeDifference <= allowedTimeDifference) {
            Log.d("NotificationReceiver", "Time difference within allowed range: $timeDifference ms")
            true
        } else {
            Log.d("NotificationReceiver", "Time difference out of range: $timeDifference ms")
            false
        }
    }

    private fun sendCourseNotification(context: Context, courseInfo: CourseNotificationInfo) {
        val channelId = "notify_id"
        val notificationId = courseInfo.courseId.hashCode()

        Log.d("NotificationReceiver", "Sending notification for course: ${courseInfo.courseName}")

        // Final permission check before sending notification
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Log.w("NotificationReceiver", "Notification permission denied at send time")
                return
            }
        }

        // Establish PendingIntent to open the main screen with focus on schedule
        val scheduleIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("OPEN_SCHEDULE", true)
        }
        val schedulePendingIntent = PendingIntent.getActivity(
            context, 0, scheduleIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Use BigTextStyle for expanded notification content
        val bigTextStyle = NotificationCompat.BigTextStyle()
            .bigText("課程名稱: ${courseInfo.courseName}\n" +
                    "老師名稱: ${courseInfo.teacherName}\n" +
                    "地點: ${courseInfo.location}\n" +
                    "時間: ${courseInfo.startTime} ~ ${courseInfo.endTime}")

        // Build notification
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle("通知提醒: ${courseInfo.courseName}")
            .setContentText("該去上課了！")
            .setStyle(bigTextStyle)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(schedulePendingIntent)
            .addAction(android.R.drawable.ic_menu_agenda, "查看課程表", schedulePendingIntent)

        // Send notification with explicit exception handling
        val notificationManager = NotificationManagerCompat.from(context)
        try {
            notificationManager.notify(notificationId, builder.build())
            Log.d("NotificationReceiver", "Notification sent successfully")
        } catch (e: SecurityException) {
            Log.e("NotificationReceiver", "Failed to send notification due to permission issue", e)
            // Optionally guide the user to notification settings
            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        }
    }

    // 課程通知信息的數據類
    data class CourseNotificationInfo(
        val courseId: String,
        val courseName: String,
        val teacherName: String,
        val location: String,
        val startTime: String,
        val endTime: String,
        val isNotificationEnabled: Boolean,
        val scheduledTime: Long
    )
}