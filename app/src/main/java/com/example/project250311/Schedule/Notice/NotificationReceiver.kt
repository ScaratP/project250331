package com.example.project250311.Schedule.Notice

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.project250311.Schedule.NoSchool.GetLeaveDataActivity

class NotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
        }

        // 從 Intent 中取得傳遞過來的課程資訊
        val courseId = intent.getStringExtra("course_id") ?: "0"
        val courseName = intent.getStringExtra("course_name") ?: "未知課程"
        val teacherName = intent.getStringExtra("teacher_name") ?: ""
        val location = intent.getStringExtra("location") ?: ""
        val startTime = intent.getStringExtra("start_time") ?: ""
        val endTime = intent.getStringExtra("end_time") ?: ""

        val channelId = "notify_id"
        // 使用 courseId 的 hashCode 作為通知的唯一識別碼
        val notificationId = courseId.hashCode()

        // 建立查看課表的 PendingIntent（點擊通知後開啟指定網址）
        val url1 = "https://www.notion.so/115-14b63e698496818bb669f9073a87823f"
        val scheduleIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url1))
        val schedulePendingIntent = PendingIntent.getActivity(
            context, 0, scheduleIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 建立請假系統的 PendingIntent
        val leaveIntent = Intent(context, GetLeaveDataActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val leavePendingIntent = PendingIntent.getActivity(
            context, 0, leaveIntent,
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
            .addAction(android.R.drawable.ic_menu_agenda, "查看課表", schedulePendingIntent)
            .addAction(android.R.drawable.ic_menu_view, "請假系統", leavePendingIntent)

        // 發送通知
        with(NotificationManagerCompat.from(context)) {
            notify(notificationId, builder.build())
        }
    }
}
