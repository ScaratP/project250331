package com.example.project250311.Schedule.Notice

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import com.example.project250311.Data.CourseViewModel
import com.example.project250311.Data.Schedule
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationManagerScreen(
    viewModel: CourseViewModel,
    navController: NavHostController
) {
    val scheduleList by viewModel.allCourses.observeAsState(emptyList())
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showPermissionDialog by remember { mutableStateOf(false) }
    var canScheduleExactAlarms by remember { mutableStateOf(hasExactAlarmPermission(context)) }
    var hasNotificationPermission by remember { mutableStateOf(hasNotificationPermission(context)) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasNotificationPermission = isGranted
        if (!isGranted && !showPermissionDialog) {
            showPermissionDialog = true
        }
    }

    // 按星期幾分組顯示課程
    val groupedCourses = scheduleList.groupBy { it.weekDay }
    val sortedDays = listOf("星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日")
    val sortedGroupedCourses = sortedDays.filter { groupedCourses.containsKey(it) }
        .associateWith { groupedCourses[it]!! }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("課程通知管理") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                            context.startActivity(intent)
                        }
                    }) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "通知權限設定",
                            tint = if (canScheduleExactAlarms) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.error
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            // 頂部通知權限狀態指示器
            NotificationPermissionStatus(
                hasNotificationPermission = hasNotificationPermission,
                canScheduleExactAlarms = canScheduleExactAlarms,
                onRequestPermission = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        hasNotificationPermission = true
                    }
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 課程通知管理內容
            if (scheduleList.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "尚未加載任何課程資料",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    sortedGroupedCourses.forEach { (weekDay, courses) ->
                        item {
                            Text(
                                text = weekDay,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }

                        items(courses.sortedBy { it.startTime }) { course ->
                            CourseNotificationItem(
                                course = course,
                                onToggleNotification = { isEnabled ->
                                    scope.launch {
                                        // 更新課程通知狀態
                                        viewModel.updateNotificationStatus(course.id, isEnabled)

                                        if (isEnabled) {
                                            // 如果開啟通知，設定課程提醒
                                            val alarmTime = course.startTime.minusMinutes(10) // 提前10分鐘通知
                                            setNotificationAlarm(context, alarmTime, course, weekDay)
                                        } else {
                                            // 如果關閉通知，取消提醒
                                            cancelNotification(context, course)
                                        }
                                    }
                                },
                                hasPermission = hasNotificationPermission && canScheduleExactAlarms
                            )
                        }
                    }
                }
            }
        }
    }

    // 通知權限對話框
    if (showPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionDialog = false },
            title = { Text("需要通知權限") },
            text = { Text("為了發送課程提醒通知，應用需要通知權限。請前往設定開啟此權限。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showPermissionDialog = false
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        }
                        context.startActivity(intent)
                    }
                ) {
                    Text("前往設定")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionDialog = false }) {
                    Text("稍後再說")
                }
            }
        )
    }
}

@Composable
fun NotificationPermissionStatus(
    hasNotificationPermission: Boolean,
    canScheduleExactAlarms: Boolean,
    onRequestPermission: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (hasNotificationPermission && canScheduleExactAlarms)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (hasNotificationPermission && canScheduleExactAlarms)
                    Icons.Default.Notifications
                else
                    Icons.Default.NotificationsOff,
                contentDescription = "通知權限狀態",
                tint = if (hasNotificationPermission && canScheduleExactAlarms)
                    MaterialTheme.colorScheme.onPrimaryContainer
                else
                    MaterialTheme.colorScheme.onErrorContainer
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (hasNotificationPermission && canScheduleExactAlarms)
                        "通知權限已啟用"
                    else
                        "通知權限未完全啟用",
                    fontWeight = FontWeight.Bold,
                    color = if (hasNotificationPermission && canScheduleExactAlarms)
                        MaterialTheme.colorScheme.onPrimaryContainer
                    else
                        MaterialTheme.colorScheme.onErrorContainer
                )

                Text(
                    text = getPermissionStatusText(hasNotificationPermission, canScheduleExactAlarms),
                    fontSize = 12.sp,
                    color = if (hasNotificationPermission && canScheduleExactAlarms)
                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    else
                        MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f)
                )
            }

            if (!hasNotificationPermission) {
                Button(
                    onClick = onRequestPermission,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (hasNotificationPermission)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("開啟權限")
                }
            }
        }
    }
}

@Composable
fun CourseNotificationItem(
    course: Schedule,
    onToggleNotification: (Boolean) -> Unit,
    hasPermission: Boolean
) {
    var expanded by remember { mutableStateOf(false) }
    val rotationState by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // 課程主要資訊和通知開關
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = course.courseName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )

                    Text(
                        text = "${course.startTime.format(DateTimeFormatter.ofPattern("HH:mm"))} - ${course.endTime.format(DateTimeFormatter.ofPattern("HH:mm"))}",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // 通知開關
                Switch(
                    checked = course.isNotificationEnabled,
                    onCheckedChange = { isEnabled ->
                        if (!hasPermission && isEnabled) {
                            // 如果沒有權限，不允許開啟通知
                            return@Switch
                        }
                        onToggleNotification(isEnabled)
                    },
                    enabled = hasPermission,
                    modifier = Modifier.alpha(if (hasPermission) 1f else 0.5f)
                )

                // 展開/收起按鈕
                IconButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier.rotate(rotationState)
                ) {
                    Icon(
                        imageVector = Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "收起" else "展開"
                    )
                }
            }

            // 展開後顯示的詳細資訊
            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(16.dp)
                ) {
                    DetailRow(label = "授課教師", value = course.teacherName)
                    DetailRow(label = "上課地點", value = course.location)

                    if (course.isNotificationEnabled) {
                        DetailRow(
                            label = "提醒時間",
                            value = "${course.startTime.minusMinutes(10).format(DateTimeFormatter.ofPattern("HH:mm"))} (上課前10分鐘)"
                        )

                        Text(
                            text = getNextNotificationTimeText(course.weekDay, course.startTime),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$label: ",
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Text(
            text = value,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
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

// 獲取下次通知時間文字描述
fun getNextNotificationTimeText(weekDay: String, startTime: LocalTime): String {
    val today = LocalDate.now()
    val currentDayOfWeek = today.dayOfWeek

    val targetDayOfWeek = when (weekDay) {
        "星期一" -> DayOfWeek.MONDAY
        "星期二" -> DayOfWeek.TUESDAY
        "星期三" -> DayOfWeek.WEDNESDAY
        "星期四" -> DayOfWeek.THURSDAY
        "星期五" -> DayOfWeek.FRIDAY
        "星期六" -> DayOfWeek.SATURDAY
        "星期日" -> DayOfWeek.SUNDAY
        else -> currentDayOfWeek
    }

    var daysToAdd = targetDayOfWeek.value - currentDayOfWeek.value
    if (daysToAdd <= 0) {
        // 如果是今天或已經過去的日子，計算到下週的時間
        daysToAdd += 7
    }

    val nextNotificationDate = today.plusDays(daysToAdd.toLong())

    return "下次提醒: ${nextNotificationDate.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())} " +
            "${nextNotificationDate.dayOfMonth}日 (${weekDay.substring(2)}) " +
            "${startTime.minusMinutes(10).format(DateTimeFormatter.ofPattern("HH:mm"))}"
}

// 設定課程通知鬧鐘
fun setNotificationAlarm(context: Context, alarmTime: LocalTime, course: Schedule, weekDay: String) {
    val today = LocalDate.now()
    val currentDayOfWeek = today.dayOfWeek

    val targetDayOfWeek = when (weekDay) {
        "星期一" -> DayOfWeek.MONDAY
        "星期二" -> DayOfWeek.TUESDAY
        "星期三" -> DayOfWeek.WEDNESDAY
        "星期四" -> DayOfWeek.THURSDAY
        "星期五" -> DayOfWeek.FRIDAY
        "星期六" -> DayOfWeek.SATURDAY
        "星期日" -> DayOfWeek.SUNDAY
        else -> currentDayOfWeek
    }

    val alarmCalendar = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, alarmTime.hour)
        set(Calendar.MINUTE, alarmTime.minute)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)

        // 計算下一次課程的日期
        var daysToAdd = targetDayOfWeek.value - currentDayOfWeek.value
        if (daysToAdd < 0 || (daysToAdd == 0 && Calendar.getInstance().get(Calendar.HOUR_OF_DAY) > alarmTime.hour)) {
            daysToAdd += 7 // 如果今天的課已經上過了或者是過去的日子，計算到下週
        }
        add(Calendar.DAY_OF_YEAR, daysToAdd)
    }

    // 建立通知Intent
    val alarmIntent = Intent(context, NotificationReceiver::class.java).apply {
        putExtra("course_id", course.id)
        putExtra("course_name", course.courseName)
        putExtra("teacher_name", course.teacherName)
        putExtra("location", course.location)
        putExtra("start_time", course.startTime.toString())
        putExtra("end_time", course.endTime.toString())
        putExtra("is_notification_enabled", true)
    }

    val pendingIntent = PendingIntent.getBroadcast(
        context,
        course.id.hashCode(),
        alarmIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    // 設定鬧鐘
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
    alarmManager?.setExact(AlarmManager.RTC_WAKEUP, alarmCalendar.timeInMillis, pendingIntent)

    Log.d("NotificationManager", "Set alarm for ${course.courseName} at ${alarmCalendar.time}")
}

// 取消課程通知
fun cancelNotification(context: Context, course: Schedule) {
    // 取消鬧鐘
    val alarmIntent = Intent(context, NotificationReceiver::class.java)
    val pendingIntent = PendingIntent.getBroadcast(
        context,
        course.id.hashCode(),
        alarmIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
    alarmManager?.cancel(pendingIntent)

    // 取消已存在的通知
    val notificationId = course.id.hashCode()
    NotificationManagerCompat.from(context).cancel(notificationId)

    Log.d("NotificationManager", "Cancelled notification for ${course.courseName}")
}