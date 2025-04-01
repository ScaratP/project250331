package com.example.project250311.Schedule.GetSchedule

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import android.app.NotificationChannel
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.NotificationManagerCompat
import com.example.project250311.Data.CourseViewModel
import com.example.project250311.Data.Schedule
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import com.example.project250311.Schedule.Notice.NotificationReceiver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import androidx.core.app.ActivityCompat
import android.app.Activity
import android.app.NotificationManager
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.Calendar

// 用來保存通知狀態的全局變量，這樣在重組時狀態不會丟失
//private val globalNotificationStates = mutableStateMapOf<String, Boolean>()

@Composable
fun ScheduleScreen(viewModel: CourseViewModel) {
    var cookies by remember { mutableStateOf<String?>(null) }
    val scheduleList by viewModel.allCourses.observeAsState(emptyList())
    val selectedCourses by viewModel.selectedCourses.observeAsState(null)
    var isLoading by remember { mutableStateOf(false) }
    var isDataRefreshing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    val onCourseSelected = remember(viewModel) { { courses: List<Schedule>? -> viewModel.selectCourses(courses) } }
    val closeCardView = remember(viewModel) { { viewModel.selectCourses(null) } }

    suspend fun initializeData() {
        isLoading = true
        val hasData = viewModel.loadAllCourses()
        cookies = if (hasData) "existing" else null
        isLoading = false
    }

    suspend fun refreshData(newCookies: String) {
        isDataRefreshing = true
        val success = fetchNewData(viewModel, newCookies)
        if (success) {
            viewModel.loadAllCourses()
        } else {
            errorMessage = "無法刷新課程資料，請稍後再試"
        }
        isDataRefreshing = false
    }

    LaunchedEffect(Unit) { initializeData() }
    LaunchedEffect(cookies) {
        cookies?.takeIf { it != "existing" }?.let { refreshData(it) }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.systemBars
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "課程表",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Button(
                    onClick = {
                        coroutineScope.launch {
                            isLoading = true
                            cookies = null  // 觸發刷新
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    if (isLoading || isDataRefreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onSecondary
                        )
                    } else {
                        Text("刷新")
                    }
                }
            }
            errorMessage?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(16.dp)
                )
            }

            when {
                cookies == null -> WebViewScreen("https://infosys.nttu.edu.tw/InfoLoginNew.aspx") { cookies = it }
                isDataRefreshing -> LoadingIndicator()
                else -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            ScheduleTable(scheduleList, onCourseSelected)
                        }
                        selectedCourses?.let { courses ->
                            Spacer(modifier = Modifier.height(16.dp))
                            CourseDetailCard(courses, viewModel, onClose = closeCardView, modifier = Modifier.fillMaxWidth().wrapContentHeight())
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LoadingIndicator() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(16.dp))
            Text("正在載入課程資料...", color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
fun CourseDetailCard(
    courses: List<Schedule>,
    viewModel: CourseViewModel,
    onClose: () -> Unit,  // Add this parameter
    modifier: Modifier = Modifier
) {
    if (courses.isEmpty()) return

    var showEditDialog by remember { mutableStateOf(false) }
    var newLocation by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()


    Card(
        modifier = modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clickable { showEditDialog = true },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "課程: ${courses.first().courseName}",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                IconButton(onClick = onClose) {  // Add a close button
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "老師: ${courses.map { it.teacherName }.distinct().joinToString(", ")}",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "地點: ${courses.map { it.location }.distinct().joinToString(", ")}",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "時間安排:",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            courses.forEach { course ->
                CourseItem(course = course, viewModel = viewModel)
            }
        }
    }

    if (showEditDialog) {
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text("編輯地點") },
            text = {
                TextField(
                    value = newLocation,
                    onValueChange = { newLocation = it },
                    label = { Text("輸入新地點") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newLocation.isNotBlank()) {
                            val updatedLocation = newLocation
                            scope.launch {
                                courses.forEach { course ->
                                    viewModel.updateCourseLocation(course.id, updatedLocation)
                                }
                            }
                            showEditDialog = false
                            newLocation = ""
                        }
                    }
                ) {
                    Text("確定")
                }
            },
            dismissButton = {
                Button(onClick = { showEditDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
fun ScheduleTable(scheduleList: List<Schedule>, onCourseSelected: (List<Schedule>?) -> Unit) {
    val timeSlots = remember {
        listOf("07:00" to "08:00", "08:00" to "09:00", "09:00" to "10:00", "10:00" to "11:00",
            "11:00" to "12:00", "12:00" to "13:00", "13:00" to "14:00", "14:00" to "15:00",
            "15:00" to "16:00", "16:00" to "17:00", "17:00" to "18:00", "18:00" to "19:00",
            "19:00" to "20:00", "20:00" to "21:00", "21:00" to "22:00")
            .map { LocalTime.parse(it.first) to LocalTime.parse(it.second) }
    }
    val weekDays = remember { listOf("星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日") }
    val activeCols = remember(scheduleList) {
        scheduleList.map { weekDays.indexOf(it.weekDay) + 1 }.filter { it > 0 }.distinct().sorted()
    }
    val activeRows = remember(scheduleList) {
        (0 until timeSlots.size).filter { rowIndex ->
            scheduleList.any { course ->
                val (start, end) = timeSlots[rowIndex]
                course.startTime < end && course.endTime > start
            }
        }
    }

    LazyColumn(modifier = Modifier.fillMaxWidth()) {
        // Header and empty state as a single item
        item {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                if (scheduleList.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = "正在努力放入資料，先去其他地方看看吧！",
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shadowElevation = 4.dp
                    ) {
                        Column {
                            Row(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                                Text(
                                    text = "",
                                    modifier = Modifier.weight(1f),
                                    textAlign = TextAlign.Center,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                activeCols.forEach { colIndex ->
                                    Text(
                                        text = weekDays[colIndex - 1],
                                        modifier = Modifier.weight(1f),
                                        textAlign = TextAlign.Center,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                            if (activeRows.isEmpty()) {
                                Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "課程表已載入但無時段資料",
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Rows as lazy items
        items(count = activeRows.size) { index ->
            val rowIndex = activeRows[index]
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        if (index % 2 == 0) MaterialTheme.colorScheme.surface
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "第${rowIndex}節",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "${timeSlots[rowIndex].first.toString().take(5)}-${timeSlots[rowIndex].second.toString().take(5)}",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center
                    )
                }
                activeCols.forEach { colIndex ->
                    val (slotStart, slotEnd) = timeSlots[rowIndex]
                    val weekDay = weekDays[colIndex - 1]
                    val courseAtSlot = scheduleList.firstOrNull {
                        it.weekDay == weekDay && it.startTime < slotEnd && it.endTime > slotStart
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(60.dp)
                            .padding(2.dp)
                            .background(
                                if (courseAtSlot != null) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                shape = RoundedCornerShape(4.dp)
                            )
                            .clickable(enabled = courseAtSlot != null) {
                                courseAtSlot?.let {
                                    onCourseSelected(scheduleList.filter { course -> course.courseName == it.courseName })
                                }
                            }
                            .border(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(4.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = courseAtSlot?.courseName ?: "",
                            fontSize = 12.sp,
                            color = if (courseAtSlot != null) MaterialTheme.colorScheme.onPrimaryContainer else Color.Transparent,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CourseItem(course: Schedule, viewModel: CourseViewModel) {
    val context = LocalContext.current
    // 使用課程自身的通知狀態，而非臨時狀態
    val isNotificationEnabled = course.isNotificationEnabled
    val scope = rememberCoroutineScope()
    val today = LocalDate.now()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "${course.weekDay} ${
                    course.startTime.format(DateTimeFormatter.ofPattern("HH:mm"))
                } - ${course.endTime.format(DateTimeFormatter.ofPattern("HH:mm"))}",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Switch(
            checked = isNotificationEnabled,
            onCheckedChange = { isEnabled ->
                scope.launch {
                    // 更新資料庫中的通知狀態
                    viewModel.updateNotificationStatus(course.id, isEnabled)

                    if (isEnabled) {
                        // 如果開啟通知，設定鬧鐘提醒
                        val alarmTime = course.startTime.minusMinutes(10) // 提前 10 分鐘通知
                        setNotificationAlarm(context, alarmTime, course, today.dayOfWeek.toString())
                    } else {
                        // 如果關閉通知，取消提醒
                        cancelNotification(context, course)
                    }
                }
            }
        )
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebViewScreen(url: String, onLoginSuccess: (String) -> Unit) {
    AndroidView(factory = { context ->
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            CookieManager.getInstance().setAcceptCookie(true)

            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    if (url.contains("InfoLoginNew.aspx")) {
                        view.scrollTo(0, 0)
                        return
                    }
                    val cookies = CookieManager.getInstance().getCookie(url)
                    onLoginSuccess(cookies ?: "")
                }
            }
            loadUrl(url)
        }
    })
}

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
        if (course::class.java.declaredFields.any { it.name == "isNotificationEnabled" }) {
            putExtra("is_notification_enabled", course.isNotificationEnabled)
        }
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

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val canScheduleExactAlarms = alarmManager?.canScheduleExactAlarms() ?: false
        Log.d("AlarmScheduler", "Can schedule exact alarms: $canScheduleExactAlarms")
    }

    alarmManager?.setExact(AlarmManager.RTC_WAKEUP, alarmCalendar.timeInMillis, pendingIntent)

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

suspend fun fetchWebData(url: String, cookies: String?): List<Schedule> {
    return withContext(Dispatchers.IO) {
        try {
            val doc = Jsoup.connect(url).apply {
                cookies?.let { header("Cookie", it) }
                userAgent("Mozilla/5.0")
                timeout(10000)
            }.get()

            val table = doc.select("table.NTTU_GridView")
            val rows = table.select("tr")
            val tempDataList = mutableListOf<Schedule>()

            val weekDays = listOf("星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日")
            val startTimes = listOf(
                "07:00", "08:00", "09:00", "10:00", "11:00", "12:00", "13:00",
                "14:00", "15:00", "16:00", "17:00", "18:00", "19:00", "20:00", "21:00"
            ).map { LocalTime.parse(it) }
            val endTimes = listOf(
                "08:00", "09:00", "10:00", "11:00", "12:00", "13:00", "14:00",
                "15:00", "16:00", "17:00", "18:00", "19:00", "20:00", "21:00", "22:00"
            ).map { LocalTime.parse(it) }

            // 收集階段：使用原始時間，不提前調整
            val contentRows = rows.drop(1)
            for ((rowIndex, row) in contentRows.withIndex()) {
                val columns = row.select("td")
                for ((colIndex, column) in columns.withIndex()) {
                    val span = column.selectFirst("span[title]")
                    val title = span?.attr("title")?.trim() ?: ""

                    if (rowIndex >= startTimes.size) continue

                    val weekDay = if (colIndex in 1..7) weekDays[colIndex - 1] else "未知"
                    val startTime = startTimes[rowIndex]
                    val endTime = endTimes[rowIndex]

                    //如果不為空，解析後存入
                    if (title.isNotEmpty()) {
                        val parsedData = parseTitle(title)
                        val courseSchedule = Schedule(
                            id = "$colIndex$rowIndex",
                            courseName = parsedData["科目名稱"] ?: "未知課程",
                            teacherName = parsedData["授課教師"] ?: "未知教師",
                            location = parsedData["場地"] ?: "其它",
                            weekDay = weekDay,
                            startTime = startTime,
                            endTime = endTime
                        )
                        tempDataList.add(courseSchedule)
                    }
                }
            }

            // 合併連續時間並在最後調整開始時間
            val mergedDataList = mutableListOf<Schedule>()
            tempDataList.groupBy { it.courseName to it.weekDay }.forEach { (key, courses) ->
                val sortedCourses = courses.sortedBy { it.startTime }
                var currentStartTime: LocalTime? = null
                var currentEndTime: LocalTime? = null
                var currentId: String = sortedCourses.first().id

                for (course in sortedCourses) {
                    if (currentStartTime == null) {
                        currentStartTime = course.startTime
                        currentEndTime = course.endTime
                    } else if (currentEndTime == course.startTime) {
                        // 連續時間，更新結束時間
                        currentEndTime = course.endTime
                    } else {
                        mergedDataList.add(
                            Schedule(
                                id = currentId,
                                courseName = key.first,
                                teacherName = courses.first().teacherName,
                                location = courses.first().location,
                                weekDay = key.second,
                                startTime = currentStartTime.plusMinutes(10),
                                endTime = currentEndTime!!
                            )
                        )
                        currentStartTime = course.startTime
                        currentEndTime = course.endTime
                        currentId = course.id
                    }
                }
                if (currentStartTime != null && currentEndTime != null) {
                    mergedDataList.add(
                        Schedule(
                            id = currentId,
                            courseName = key.first,
                            teacherName = courses.first().teacherName,
                            location = courses.first().location,
                            weekDay = key.second,
                            startTime = currentStartTime.plusMinutes(10),
                            endTime = currentEndTime
                        )
                    )
                }
            }

            Log.d("fetchWebData", "Final merged data: $mergedDataList")
            mergedDataList
        } catch (e: Exception) {
            Log.e("fetchWebData", "Error fetching data", e)
            emptyList()
        }
    }
}

fun parseTitle(title: String): Map<String, String> {
    val regexMap = mapOf(
        "科目名稱" to """科目名稱：(.+?)\n""",
        "授課教師" to """授課教師：(.+?)\n""",
        "場地" to """場地：(.+?)\n"""
    )
    val result = mutableMapOf<String, String>()
    regexMap.forEach { (key, pattern) ->
        val regex = Regex(pattern)
        regex.find(title)?.let { match ->
            result[key] = match.groupValues.drop(1).joinToString(" ").trim()
        }
    }
    return result
}

suspend fun fetchNewData(viewModel: CourseViewModel, cookies: String): Boolean {
    val fetchedData = fetchWebData(
        "https://infosys.nttu.edu.tw/n_CourseBase_Select/WeekCourseList.aspx?ItemParam=",
        cookies
    )
    return if (fetchedData.isNotEmpty()) {
        withContext(Dispatchers.IO) {
            viewModel.clearAllCourses()
            Log.d("fetchNewData", "All courses cleared")
            fetchedData.forEach { course ->
                viewModel.insertCourse(course)
                Log.d("fetchNewData", "Inserted course: $course")
            }
            viewModel.loadAllCourses()
            Log.d("fetchNewData", "Courses loaded")
        }
        true
    } else {
        false
    }
}