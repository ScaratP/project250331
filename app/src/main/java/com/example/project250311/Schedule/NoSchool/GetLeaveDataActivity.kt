package com.example.project250311.Schedule.NoSchool

import android.annotation.SuppressLint
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
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
            LeaveScreen(viewModel,this)
        }
    }
}

suspend fun fetchLeaveData(url: String, cookies: String?): List<LeaveData> {
    return withContext(Dispatchers.IO) {
        try {
            val doc = Jsoup.connect(url).apply {
                cookies?.let { header("Cookie", it) }
                userAgent("Mozilla/5.0")
                timeout(10000)
            }.get()

            val leaveType = doc.select("#Sel_Leave option[selected]").text()
            val tableRows = doc.select("#TAB_Course tbody tr")
            val leaveDataList = mutableListOf<LeaveData>()

            tableRows.forEach { row ->
                if (row.select("span.switch").isNotEmpty()) {
                    val date = row.select("td[data-label='請假日期：']").text()
                    val courseName = row.select("td[data-label='課目名稱：']").text()
                    val hours = row.select("input[type='radio'][checked]").attr("value").toInt()

                    leaveDataList.add(LeaveData(leave_type = leaveType, date_leave = date, courseName = courseName, hours = hours))
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
fun WebViewLeaveScreen(url: String, onLoginSuccess: (String) -> Unit, onLeaveAppClicked: (String?) -> Unit) {
    Log.d("WebViewLeaveScreen", "WebView loading: $url")
    AndroidView(factory = { context ->
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            CookieManager.getInstance().setAcceptCookie(true)


            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    if (url.contains(".aspx")) {
                        view.scrollTo(0, 0)
                        return
                    }
                    val cookies = CookieManager.getInstance().getCookie(url)
                    onLoginSuccess(cookies ?: "")
                }


                override fun onPageCommitVisible(view: WebView, url: String) {
                    super.onPageCommitVisible(view, url)
                    Log.d("WebViewLeaveScreen", "Page commit visible: $url")
                    view.evaluateJavascript(
                        """
                        (function() {
                            const button = document.getElementById('btn_Leaveapp');
                            if (button) {
                                button.onclick = function() {
                                    window.LeaveAppClicked.postMessage('LeaveAppClicked');
                                };
                            }
                        })();
                        """,
                        null
                    )
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
        if (message == "LeaveAppClicked") {
            Log.d("LeaveAppClickInterface", "LeaveAppClicked message received")
            onLeaveAppClicked(CookieManager.getInstance().getCookie("https://casauth.nttu.edu.tw/cas/login?service=https%3a%2f%2faskleave.nttu.edu.tw%2findex.aspx"))
        }
    }
}

@Composable
fun LeaveScreen(viewModel: LeaveDatabase.LeaveViewModel, activity: ComponentActivity) {
    var cookies by remember { mutableStateOf<String?>(null) }
    var leaveList by remember { mutableStateOf(emptyList<LeaveData>()) }
    val observedLeaveList by viewModel.allLeaves.observeAsState(emptyList())
    var webViewLoaded by remember { mutableStateOf(false) }

    LaunchedEffect(observedLeaveList) {
        leaveList = observedLeaveList
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "請假系統",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Column(
            modifier = Modifier
                .wrapContentWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Button(onClick = {
                Log.d("LeaveScreen", "我要請假 button clicked")
                webViewLoaded = false
                cookies = null
                leaveList = emptyList()
            }, enabled = true) {
                Text("我要請假")
            }

            Button(onClick = {
                viewModel.loadAllLeaves()
            }) {
                Text("所有請假紀錄")
            }

            Button(onClick = {
                activity.finish()
            }) {
                Text("返回主畫面")
            }
        }

        if ( cookies == null) {
            if (!webViewLoaded) {
                Log.d("LeaveScreen", "Loading WebViewLeaveScreen")
                WebViewLeaveScreen(
                    "https://casauth.nttu.edu.tw/cas/login?service=https%3a%2f%2faskleave.nttu.edu.tw%2findex.aspx",
                    { cookies = it; webViewLoaded = true },
                    { leaveAppCookies ->
                        if (leaveAppCookies != null) {
                            Log.d("LeaveScreen", "LeaveApp cookies received")
                            viewModel.viewModelScope.launch {
                                val fetchedData = fetchLeaveData(
                                    "https://casauth.nttu.edu.tw/cas/login?service=https%3a%2f%2faskleave.nttu.edu.tw%2findex.aspx",
                                    leaveAppCookies
                                )
                                if (fetchedData.isNotEmpty()) {
                                    fetchedData.forEach { viewModel.insertOrUpdateLeave(it) }
                                    viewModel.loadAllLeaves()
                                    leaveList = fetchedData;
                                }
                            }
                        } else {
                            Log.e("LeaveScreen", "LeaveApp cookies are null")
                        }
                    }
                )
            }
        } else if (webViewLoaded) {
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(leaveList) { leave ->
                    Text(text = "假別: ${leave.leave_type}, 日期: ${leave.date_leave}, 科目: ${leave.courseName}, 時數: ${leave.hours}")
                }
            }
        }
    }
}