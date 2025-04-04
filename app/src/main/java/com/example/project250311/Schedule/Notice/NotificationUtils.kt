package com.example.project250311.Schedule.Notice

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.project250311.Data.Schedule
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Calendar
import java.util.Locale

object NotificationUtils {

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) { //判斷版本是否在API26以上
            val channelId = "notify_id" //通道的唯一辨識符號
            val channelName = "通知通道" //顯示給使用者的通知名稱
            val channelDescription = "這是APP的通知通道" //用來描述通道的用途
            val importance = NotificationManager.IMPORTANCE_HIGH //通知優先順序
            val channel = NotificationChannel(channelId, channelName, importance).apply { //創建新的通知通道
                description = channelDescription //設定通知描述
            }
            //取得NotificationManager並建立通知通道
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager //取得系統通知管理氣
            notificationManager.createNotificationChannel(channel) //註冊通知通道
        }
    }

    fun requestNotificationPermission(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    activity,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                // 如果沒有權限，請求權限
                ActivityCompat.requestPermissions(
                    activity,
                    arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                    1001
                )
                return
            }
        }
    }

    fun createNotificationIntent(context: Context, course: Schedule): Intent {
        return Intent(context, NotificationReceiver::class.java).apply {
            putExtra("course_id", course.id)
            putExtra("course_name", course.courseName)
            putExtra("teacher_name", course.teacherName)
            putExtra("location", course.location)
            putExtra("start_time", course.startTime.toString())
            putExtra("end_time", course.endTime.toString())
            // 保留 isNotificationEnabled 屬性以供參考
            putExtra("is_notification_enabled", course.isNotificationEnabled)
        }
    }

    fun setNotificationAlarm(context: Context, alarmTime: LocalTime, course: Schedule, weekDay: String) {
        Log.d("AlarmScheduler", "Setting alarm for course: ${course.courseName}")
        Log.d("AlarmScheduler", "Alarm time: $alarmTime")
        Log.d("AlarmScheduler", "Week day: $weekDay")
        Log.d("AlarmScheduler", "Course details: $course")

        val today = LocalDate.now()
        val currentDayOfWeek = today.dayOfWeek

        val targetDayOfWeek = when (weekDay) {
            "星期一" -> DayOfWeek.of(1)  // Monday is 1 in java.time
            "星期二" -> DayOfWeek.of(2)  // Tuesday is 2
            "星期三" -> DayOfWeek.of(3)  // Wednesday is 3
            "星期四" -> DayOfWeek.of(4)  // Thursday is 4
            "星期五" -> DayOfWeek.of(5)  // Friday is 5
            "星期六" -> DayOfWeek.of(6)  // Saturday is 6
            "星期日" -> DayOfWeek.of(7)  // Sunday is 7
            else -> currentDayOfWeek
        }

        val alarmCalendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, alarmTime.hour)
            set(Calendar.MINUTE, alarmTime.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)

            var daysToAdd = targetDayOfWeek.value - currentDayOfWeek.value
            if (daysToAdd < 0 || (daysToAdd == 0 && Calendar.getInstance().get(Calendar.HOUR_OF_DAY) > alarmTime.hour)) {
                daysToAdd += 7
            }

            Log.d("AlarmScheduler", "Days to add: $daysToAdd")
            Log.d("AlarmScheduler", "Current hour: ${Calendar.getInstance().get(Calendar.HOUR_OF_DAY)}")
            Log.d("AlarmScheduler", "Alarm hour: ${alarmTime.hour}")

            add(Calendar.DAY_OF_YEAR, daysToAdd)
        }

        Log.d("AlarmScheduler", "Alarm time in millis: ${alarmCalendar.timeInMillis}")
        Log.d("AlarmScheduler", "Alarm date: ${alarmCalendar.time}")

        val alarmIntent = Intent(context, NotificationReceiver::class.java).apply {
            putExtra("course_id", course.id)
            putExtra("course_name", course.courseName)
            putExtra("teacher_name", course.teacherName)
            putExtra("location", course.location)
            putExtra("start_time", course.startTime.toString())
            putExtra("end_time", course.endTime.toString())
            putExtra("is_notification_enabled", true)
            // 額外加入 weekDay 資訊，幫助追蹤
            putExtra("week_day", weekDay)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            course.id.hashCode(),
            alarmIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager

        // 在設置鬧鐘的地方
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager?.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                alarmCalendar.timeInMillis,
                pendingIntent
            )
        } else {
            alarmManager?.setExact(
                AlarmManager.RTC_WAKEUP,
                alarmCalendar.timeInMillis,
                pendingIntent
            )
        }

        Log.d("AlarmScheduler", "Alarm set successfully for course: ${course.courseName}")
    }

    fun cancelNotification(context: Context, course: Schedule) {
        // 首先取消鬧鐘，使用 ScheduleScreen 的邏輯
        val alarmIntent = createNotificationIntent(context, course)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            course.id.hashCode(),
            alarmIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        alarmManager?.cancel(pendingIntent)

        // 然後取消通知，使用 Activity 的邏輯但修正函數名稱拼寫
        val notificationId = course.id.hashCode()
        NotificationManagerCompat.from(context).cancel(notificationId)

        // 加入 ScheduleScreen 中的日誌記錄
        Log.d("CancelNotification", "Cancelled notification for ${course.courseName}")
    }

    // 檢查是否有通知權限
    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    // 檢查是否可以排程精確鬧鐘
    fun hasExactAlarmPermission(context: Context): Boolean {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager?.canScheduleExactAlarms() ?: false
        } else {
            true
        }
    }

    // 獲取權限狀態文字描述
    fun getPermissionStatusText(hasNotificationPermission: Boolean, canScheduleExactAlarms: Boolean): String {
        return when {
            !hasNotificationPermission && !canScheduleExactAlarms ->
                "需要通知權限和精確鬧鐘權限才能發送課程提醒"
            !hasNotificationPermission ->
                "需要通知權限才能發送課程提醒"
            !canScheduleExactAlarms ->
                "需要精確鬧鐘權限才能準時提醒"
            else ->
                "可以接收課程上課前的提醒通知"
        }
    }

    // 優化後的下次通知時間文字描述計算函數
    fun getNextNotificationTimeText(weekDay: String, startTime: LocalTime): String {
        val today = LocalDate.now()
        val now = LocalDateTime.now()
        val currentDayOfWeek = today.dayOfWeek

        // 將中文星期轉換為 DayOfWeek 枚舉
        val targetDayOfWeek = when (weekDay) {
            "星期一" -> DayOfWeek.MONDAY
            "星期二" -> DayOfWeek.TUESDAY
            "星期三" -> DayOfWeek.WEDNESDAY
            "星期四" -> DayOfWeek.THURSDAY
            "星期五" -> DayOfWeek.FRIDAY
            "星期六" -> DayOfWeek.SATURDAY
            "星期日", "星期天" -> DayOfWeek.SUNDAY
            else -> currentDayOfWeek
        }

        // 計算到下一個課程日的天數
        var daysToAdd = targetDayOfWeek.value - currentDayOfWeek.value

        // 如果今天就是課程日，且現在的時間還沒超過提醒時間，則不需要加7天
        val notificationTime = startTime.minusMinutes(10)
        val currentTimeOfDay = LocalTime.now()

        if (daysToAdd == 0) {
            // 今天就是課程日，檢查時間是否已過
            if (currentTimeOfDay.isAfter(notificationTime)) {
                // 今天的提醒時間已過，設置為下週同一天
                daysToAdd = 7
            }
        } else if (daysToAdd < 0) {
            // 課程日在下週
            daysToAdd += 7
        }

        // 計算下次通知的確切日期
        val nextNotificationDate = today.plusDays(daysToAdd.toLong())
        val nextNotificationDateTime = LocalDateTime.of(nextNotificationDate, notificationTime)

        // 計算時間差
        val diffDays = ChronoUnit.DAYS.between(today, nextNotificationDate).toInt()

        // 根據時間差生成友好的文字描述
        val timeDescription = when {
            diffDays == 0 -> "今天"
            diffDays == 1 -> "明天"
            diffDays == 2 -> "後天"
            diffDays < 7 -> "本週${weekDay.substring(2)}"
            diffDays < 14 -> "下週${weekDay.substring(2)}"
            else -> "${nextNotificationDate.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())} ${nextNotificationDate.dayOfMonth}日 (${weekDay.substring(2)})"
        }

        return "下次提醒: $timeDescription ${notificationTime.format(DateTimeFormatter.ofPattern("HH:mm"))}"
    }

    fun getCourseDates(weekDay: String): List<java.time.LocalDate> {
        val now = java.time.LocalDate.now() // 取得今天的日期
        // 以最近一個星期一作為起始日期
        val semesterStart = now.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        // 假設學期為 18 週
        val semesterEnd = semesterStart.plusWeeks(18)

        // 找到對應的 DayOfWeek
        val targetDayOfWeek = when (weekDay) {
            "星期一" -> DayOfWeek.MONDAY
            "星期二" -> DayOfWeek.TUESDAY
            "星期三" -> DayOfWeek.WEDNESDAY
            "星期四" -> DayOfWeek.THURSDAY
            "星期五" -> DayOfWeek.FRIDAY
            "星期六" -> DayOfWeek.SATURDAY
            "星期日" -> DayOfWeek.SUNDAY
            else -> return emptyList()
        }

        val dates = mutableListOf<java.time.LocalDate>()
        var date = semesterStart.with(TemporalAdjusters.nextOrSame(targetDayOfWeek)) // 找到最近的該星期幾

        while (!date.isAfter(semesterEnd)) {
            dates.add(date)
            date = date.plusWeeks(1) // 每週同一天
        }
        return dates
    }

}