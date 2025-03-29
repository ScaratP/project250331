package com.example.project250311.Schedule.Notice


import android.app.Activity
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.project250311.Data.Schedule
import com.example.project250311.Schedule.GetSchedule.GetScheduleActivity
import com.example.project250311.Schedule.GetSchedule.setNoticationAlarm
import com.example.project250311.Schedule.NoSchool.GetLeaveDataActivity
import com.example.project250311.ui.theme.Project250311Theme
import kotlinx.datetime.LocalTime
import java.util.Calendar

class NoticeActivity: ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        createNotificationChannel() //建立通知頻道
        requestNotificationPermission()

        setContent {
            Project250311Theme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    NotificationButton()
                }
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) { //判斷版本是否在API26以上
            val channelId = "notify_id" //通道的唯一辨識符號
            val channelName = "通知通道" //顯示給使用者的通知名稱
            val channelDescription = "這是APP的通知通道" //用來描述通道的用途
            val importance = NotificationManager.IMPORTANCE_HIGH //通知優先順序
            val channel = NotificationChannel(channelId, channelName, importance).apply { //創建新的通知通道
                description = channelDescription //設定通知描述
            }
            //取得NotificationManager並建立通知通道
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager //取得系統通知管理氣
            notificationManager.createNotificationChannel(channel) //註冊通知通道
        }
    }

    fun requestNotificationPermission(){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                // 如果沒有權限，請求權限
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                    1001
                )
                return
            }
        }

}



@Composable
fun NotificationButton(){
    val context = LocalContext.current //取得context

    Surface (
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ){

        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 立即發送通知的按鈕
            Button(
                onClick = { sendNotification(context) }
            ) {
                Text("立即發送通知")
            }
            // 測試未來鬧鐘通知的按鈕
            Button(
                onClick = {
                    // 建立測試用的 Schedule 物件，將時間設定為當前時間後 1 分鐘
                    val testSchedule = Schedule(
                        id = "test1",
                        courseName = "測試課程",
                        teacherName = "測試老師",
                        location = "測試教室",
                        weekDay = "今天",
                        startTime = java.time.LocalTime.now().plusMinutes(2),
                        endTime = java.time.LocalTime.now().plusMinutes(3)
                    )
                    // 設定鬧鐘為當前時間後 1 分鐘觸發通知
                    val testAlarmTime = java.time.LocalTime.now().plusMinutes(1)
                    setNoticationAlarm(context, testAlarmTime, testSchedule)
                }
            ) {
                Text("測試未來鬧鐘通知")
            }
        }
    }
}


    fun sendNotification(context: Context) {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
        }

        val channelId = "notify_id"
        val notificationId = System.currentTimeMillis().toInt() //確保通知ID唯一


        //查看課表
        val url1 = "https://www.notion.so/115-14b63e698496818bb669f9073a87823f"
        val scheduleIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url1))

        val schedulePendingIntent = PendingIntent.getActivity( //點擊開啟
            context,0,scheduleIntent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        //開起請假系統
        val leaveIntent = Intent(context,GetLeaveDataActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val leavePendingIntent = PendingIntent.getActivity(
            context, 0, leaveIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        //建立通知
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle("通知提醒!!")
            .setContentText("該去上課了!!")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .addAction(android.R.drawable.ic_menu_agenda,"查看課表",schedulePendingIntent)
            .addAction(android.R.drawable.ic_menu_view,"請假系統",leavePendingIntent)

        with(NotificationManagerCompat.from(context)) {
            notify(notificationId, builder.build())
        }
    }

    fun setNoticationAlarm(context: Context, alarmTime: LocalTime, course: Schedule) {
        // 若為 Android 12 (API 31) 以上，檢查是否可以設定精確鬧鐘
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!alarmManager.canScheduleExactAlarms()) {
                // 可在此提示使用者或返回
                return
            }
        }

        // 將 LocalTime（僅包含時間）轉換為 Calendar 時間（假設使用當天日期）
        val alarmCalendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, alarmTime.hour)
            set(Calendar.MINUTE, alarmTime.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        // 建立 Intent 傳遞課程資訊到 NotificationReceiver
        val alarmIntent = Intent(context, NotificationReceiver::class.java).apply {
            putExtra("course_id", course.id)
            putExtra("course_name", course.courseName)
            putExtra("teacher_name", course.teacherName)
            putExtra("location", course.location)
            putExtra("start_time", course.startTime.toString())
            putExtra("end_time", course.endTime.toString())
        }

        // 使用 course.id.hashCode() 來保證唯一的 PendingIntent
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            course.id.hashCode(),
            alarmIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 使用 AlarmManager 設定精確鬧鐘
        alarmManager.setExact(AlarmManager.RTC_WAKEUP, alarmCalendar.timeInMillis, pendingIntent)
    }
}