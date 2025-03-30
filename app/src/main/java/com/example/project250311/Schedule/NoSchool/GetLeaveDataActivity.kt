package com.example.project250311.Schedule.NoSchool

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.material3.ScaffoldDefaults.contentWindowInsets
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup

class GetLeaveDataActivity : ComponentActivity() {
    private val viewModel: LeaveDatabase.LeaveViewModel by viewModels {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val db = LeaveDatabase.getDatabase(applicationContext)
                val repository = LeaveDatabase.Companion.LeaveRepository(db.leaveDao())
                @Suppress("UNCHECKED_CAST")
                return LeaveDatabase.LeaveViewModel(repository) as T
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LeaveScreen(viewModel, this)
            WebViewLeaveScreen(
                url = "https://casauth.nttu.edu.tw/cas/login?service=https%3a%2f%2faskleave.nttu.edu.tw%2findex.aspx",
                onLoginSuccess = { cookies ->
                    Log.d("GetLeaveDataActivity", "登入成功，Cookies: $cookies")
                },
                onLeaveAppClicked = { jsonString ->
                    if (!jsonString.isNullOrEmpty()) {
                        viewModel.viewModelScope.launch {
                            val gson = Gson()
                            val type = object : TypeToken<List<LeaveData>>() {}.type
                            val leaveList: List<LeaveData> = gson.fromJson(jsonString, type)

                            leaveList.forEach { leave ->
                                Log.d("LeaveScreen", "準備插入: $leave")
                                viewModel.insert(leave)
                            }
                        }
                    }
                }

            )
        }
    }

    suspend fun fetchLeaveData(url: String, cookies: String?): List<LeaveData> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d("fetchLeaveData", "開始請求請假資料，Cookies: $cookies")
                val doc = Jsoup.connect(url).apply {
                    cookies?.let { header("Cookie", it) }
                    userAgent("Mozilla/5.0")
                    timeout(10000)
                }.get()

                val leaveType = doc.select("#Sel_Leave option[selected]").text() // 請假類型
                val leaveDataList = mutableListOf<LeaveData>()

                // 解析「課程」請假
                doc.select("#TAB_Course tbody tr").forEach { row ->
                    if (row.select("span.switch").isNotEmpty()) {
                        val date = row.select("td[data-label='請假日期：']").text()
                        val courseName = row.select("td[data-label='課目名稱：']").text()
                        val hours = row.select("input[type='radio'][checked]").attr("value").toIntOrNull() ?: 0

                        leaveDataList.add(LeaveData(
                            recordType = "課程",
                            leave_type = leaveType,
                            date_leave = date,
                            courseName = courseName,
                            hours = hours
                        ))
                    }
                }

                // 解析「集會」請假
                doc.select("#TAB_Assembly tbody tr").forEach { row ->
                    if (row.select("input[type='checkbox']").isNotEmpty()) {
                        val date = row.select("td[data-label='集會日：']").text()
                        val assemblyName = row.select("td[data-label='集會名稱：']").text()
                        val hours = row.select("input[type='radio'][checked]").attr("value").toIntOrNull() ?: 0

                        leaveDataList.add(LeaveData(
                            recordType = "集會",
                            leave_type = leaveType,  // 集會請假類別
                            date_leave = date,
                            courseName = assemblyName,
                            hours = hours
                        ))
                    }
                }

                Log.d("fetchLeaveData", "Fetched data: $leaveDataList")
                return@withContext leaveDataList
            } catch (e: Exception) {
                Log.e("fetchLeaveData", "Error fetching data", e)
                emptyList()
            }
        }
    }


    @SuppressLint("SetJavaScriptEnabled")
    @Composable
    fun WebViewLeaveScreen(
        url: String,
        onLoginSuccess: (String) -> Unit,
        onLeaveAppClicked: (String?) -> Unit
    ) {
        Log.d("WebViewLeaveScreen", "WebView loading: $url")
        AndroidView(factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.databaseEnabled = true  // 啟用 WebView 資料庫
                settings.cacheMode = android.webkit.WebSettings.LOAD_NO_CACHE  // 避免加載緩存
                //禁止使用網頁縮放，並自動縮放網頁到適當大小
                settings.setSupportZoom(false)
                settings.builtInZoomControls = false
                settings.useWideViewPort = true
                settings.loadWithOverviewMode = true

                val cookieManager = CookieManager.getInstance()
                cookieManager.setAcceptCookie(true)
                cookieManager.setAcceptThirdPartyCookies(this, true)

                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String) {
                        val cookies = CookieManager.getInstance().getCookie(url)
                        Log.d("WebViewLeaveScreen", "Cookies: $cookies")

                        if (url.contains("index.aspx")) {
                            // 登入成功
                            Log.d("WebViewLeaveScreen", "成功登入")
                            if (!cookies.isNullOrEmpty()) {
                                onLoginSuccess(cookies)
                            } else {
                                Log.e("WebViewLeaveScreen", "登入 Cookie 可能未成功獲取")
                            }
                        } else if (url.contains("Leave.aspx")) {
                            // 用戶已經手動跳轉到請假頁面，監聽 btn_Leaveapp 點擊
                            Log.d("WebViewLeaveScreen", "偵測到請假頁面，開始監聽按鈕")

                            // 用戶填寫資料後，執行爬蟲
                            view.evaluateJavascript(
                                """
                                    (function() {
                                    document.getElementById("btn_Leaveapp").addEventListener('click', function() {
                                        setTimeout(() => {
                                            let leaveType = document.getElementById('Sel_Leave')?.value || '';
                                            let startDate = document.getElementById('Inp_Start_Date')?.value || '';
                                            let endDate = document.getElementById('Inp_End_Date')?.value || '';
                                            let leaveDataList = [];
                                
                                            // 擷取「課程」請假資訊
                                            document.querySelectorAll('#TAB_Course tbody tr').forEach(row => {
                                                let checkbox = row.querySelector('input[type=checkbox]');
                                                if (checkbox && checkbox.checked) {
                                                    let date = row.querySelector('[data-label="請假日期："]').innerText.trim();
                                                    let courseName = row.querySelector('[data-label="課目名稱："]').innerText.trim();
                                                    let hours = '';
                                                    let hourRadios = row.querySelectorAll('input[type=radio]');
                                                    hourRadios.forEach(radio => {
                                                        if (radio.checked) {
                                                            hours = radio.value;
                                                        }
                                                    });
                                                    leaveDataList.push({
                                                        recordType: "課程",
                                                        leave_type: leaveType,
                                                        date_leave: date,
                                                        courseName: courseName,
                                                        hours: parseInt(hours, 10) || 0
                                                    });
                                                }
                                            });
                                
                                            // 擷取「集會」請假資訊
                                            document.querySelectorAll('#TAB_Assembly tbody tr').forEach(row => {
                                                let checkbox = row.querySelector('input[type=checkbox]');
                                                if (checkbox && checkbox.checked) {
                                                    let date = row.querySelector('[data-label="集會日："]').innerText.trim();
                                                    let assemblyName = row.querySelector('[data-label="集會名稱："]').innerText.trim();
                                                    let hours = '';
                                                    let hourRadios = row.querySelectorAll('input[type=radio]');
                                                    hourRadios.forEach(radio => {
                                                        if (radio.checked) {
                                                            hours = radio.value;
                                                        }
                                                    });
                                                    leaveDataList.push({
                                                        recordType: "集會",
                                                        leave_type: leaveType,
                                                        date_leave: date,
                                                        courseName: assemblyName,
                                                        hours: parseInt(hours, 10) || 0
                                                    });
                                                }
                                            });
                                
                                            // 回傳資料給 Android 應用
                                            window.LeaveAppClicked.postMessage(JSON.stringify(leaveDataList));
                                        }, 1000); 
                                    });
                                })();
                            """,
                                null
                            )
                        }
                    }
                }
                addJavascriptInterface(LeaveAppClickInterface(onLeaveAppClicked), "LeaveAppClicked")
                loadUrl(url)
            }
        })
    }

    class LeaveAppClickInterface(private val onLeaveAppClicked: (String?) -> Unit) {
        @JavascriptInterface
        fun postMessage(message: String) {
            Log.d("LeaveAppClickInterface", "Message received: $message")
            if (message != null && message.isNotEmpty()) {
                onLeaveAppClicked(message)
            }
        }
    }
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun LeaveScreen(viewModel: LeaveDatabase.LeaveViewModel, activity: ComponentActivity) {
        var cookies by remember { mutableStateOf<String?>(null) }
        var leaveList by remember { mutableStateOf(emptyList<LeaveData>()) }
        val observedLeaveList by viewModel.allLeaves.observeAsState(emptyList())
        var webViewLoaded by remember { mutableStateOf(false) }

        LaunchedEffect(observedLeaveList) {
            leaveList = observedLeaveList


        }

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            contentWindowInsets = WindowInsets.systemBars // 確保內容不會被系統列遮擋
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
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            val intent = Intent(activity, LeaveActivity::class.java)
                            activity.startActivity(intent)
                            activity.finish()
                        }
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = "請假記錄",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (cookies == null) {
                    if (!webViewLoaded) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            WebViewLeaveScreen(
                                url = "https://casauth.nttu.edu.tw/cas/login?service=https%3a%2f%2faskleave.nttu.edu.tw%2findex.aspx",
                                onLoginSuccess = { cookies = it; webViewLoaded = true },
                                onLeaveAppClicked = { jsonString ->
                                    if (jsonString != null) {
                                        viewModel.viewModelScope.launch {
                                            // 解析 JSON
                                            val gson = Gson()
                                            val type = object : TypeToken<List<LeaveData>>() {}.type
                                            val fetchedData: List<LeaveData> = gson.fromJson(jsonString, type)

                                            fetchedData.forEach { leaveData ->
                                                Log.d("LeaveScreen", "解析到請假資料: $leaveData")
                                                viewModel.insert(leaveData)
                                            }
                                        }
                                    }
                                }
                            )
                        }
                    } else {
                        // 當 WebView 已載入完成，可顯示請假資料列表
                        LazyColumn(
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(leaveList) { leave ->
                                Text(
                                    text = "類型: ${leave.recordType}, 假別: ${leave.leave_type}, 日期: ${leave.date_leave}, 科目: ${leave.courseName}, 時數: ${leave.hours}",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(8.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

