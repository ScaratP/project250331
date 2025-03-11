package com.example.project250311.Schedule.GetSchedule

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.project250311.Data.AppDatabase
import com.example.project250311.Data.CourseRepository
import com.example.project250311.Data.CourseViewModel
import com.example.project250311.Data.Schedule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

class GetScheduleActivity : ComponentActivity() {
    private val viewModel: CourseViewModel by viewModels {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val db = AppDatabase.getDatabase(applicationContext)
                val repository = CourseRepository(db.courseDao())
                @Suppress("UNCHECKED_CAST")
                return CourseViewModel(repository) as T
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ScheduleScreen(viewModel)
        }
    }
}



suspend fun fetchWebData(url: String, cookies: String?): List<Schedule> {
    return withContext(Dispatchers.IO) {
        try {
            val doc: Document = Jsoup.connect(url).apply {
                cookies?.let { header("Cookie", it) }
                userAgent("Mozilla/5.0")
                timeout(10000)
            }.get()

            val table = doc.select("table.NTTU_GridView")
            val rows = table.select("tr")

            val dataList = mutableListOf<Schedule>()

            val weekDays = listOf("星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日")

            val startTimes = listOf("07:10", "08:10", "09:10", "10:10", "11:10", "12:10", "13:10", "14:10", "15:10", "16:10", "17:10", "18:10", "19:10", "20:10", "21:10")

            val endTimes = listOf("08:00", "09:00", "10:00", "11:00", "12:00", "13:00", "14:00", "15:00", "16:00", "17:00", "18:00", "19:00", "20:00", "21:00", "22:00")

            for ((rowIndex, row) in rows.withIndex()) {
                val columns = row.select("td")

                for ((colIndex, column) in columns.withIndex()) {
                    val span = column.selectFirst("span[title]")
                    val title = span?.attr("title")?.trim() ?: ""
                    val realRowIndex = rowIndex - 1
                    val id = "$colIndex$realRowIndex"

                    val weekDay = if (colIndex in 1..7) weekDays[colIndex - 1] else "未知"
                    val startTime = (if (realRowIndex in startTimes.indices) startTimes[realRowIndex] else "0")//.toLong()
                    val endTime = (if (realRowIndex in endTimes.indices) endTimes[realRowIndex] else "0")//.toLong()

                    if (title.isNotEmpty()) {
                        Log.d("WebScraper", "Extracted [$id]: $title")
                        val parsedData = parseTitle(title)

                        val courseSchedule = Schedule(
                            id = id,
                            courseName = parsedData["科目名稱"] ?: "未知課程",
                            teacherName = parsedData["授課教師"] ?: "未知教師",
                            location = parsedData["場地"] ?: "未知地點",
                            weekDay = weekDay,
                            startTime = startTime,
                            endTime = endTime
                        )
                        Log.d("WebScrapper", "fetchWebData: $courseSchedule")
                        dataList.add(courseSchedule)
                    }
                }
            }
            Log.d("WebScraper", "Final parsed data: $dataList")
            return@withContext dataList
        } catch (e: Exception) {
            Log.e("WebScraper", "Error fetching data", e)
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
        val match = regex.find(title)
        if (match != null) {
            result[key] = match.groupValues.drop(1).joinToString(" ").trim()
        }
    }
    return result
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

@Composable
fun ScheduleScreen(viewModel: CourseViewModel) {
    var cookies by remember { mutableStateOf<String?>(null) }
    val scheduleList by viewModel.allCourses.observeAsState(emptyList())
    var isLoading by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { // 使用 Unit 來確保在 Composable 初始化時執行一次
        viewModel.loadAllCourses() // 確保資料庫已加載
        val allCourses = viewModel.allCourses.value
        Log.d("DatabaseCheck", "Initial Database Content: $allCourses")
    }

    LaunchedEffect(cookies) {
        if (cookies != null) {
            isLoading = true
            val fetchedData = fetchWebData(
                "https://infosys.nttu.edu.tw/n_CourseBase_Select/WeekCourseList.aspx?ItemParam=", cookies!!
            )
            viewModel.clearAllCourses() // 避免重複儲存
            fetchedData.forEach { course ->
                viewModel.insertCourse(
                    Schedule
                        (
                        id = course.id,
                        courseName = course.courseName,
                        teacherName = course.teacherName,
                        location = course.location,
                        weekDay = course.weekDay,
                        startTime = course.startTime,
                        endTime = course.endTime
                    )
                )
            }
            viewModel.loadAllCourses()
            isLoading = false
        }
    }

    Column {
        Text(
            text = "課程表",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(16.dp)
        )

        if (scheduleList.isEmpty() && cookies == null) {
            WebViewScreen("https://infosys.nttu.edu.tw/InfoLoginNew.aspx") {
                cookies = it
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxSize()
            ) {
                item {
                    Text(
                        text = "Schedule",
                        fontSize = 24.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                if (isLoading) {
                    item {
                        CircularProgressIndicator(modifier = Modifier.padding(16.dp))
                    }
                } else if (scheduleList.isEmpty()) {
                    item {
                        Text(text = "No schedule found", fontSize = 14.sp)
                    }
                } else {
                    items(scheduleList) { course ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text(
                                    text = "ID: ${course.id}",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "課程: ${course.courseName}",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(text = "老師: ${course.teacherName}", fontSize = 14.sp)
                                Text(text = "地點: ${course.location}", fontSize = 14.sp)
                                Text(text = "星期: ${course.weekDay}", fontSize = 14.sp)
                                Text(
                                    text = "時間: ${course.startTime} - ${course.endTime}",
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
