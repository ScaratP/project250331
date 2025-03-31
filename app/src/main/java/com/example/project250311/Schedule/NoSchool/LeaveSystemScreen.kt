package com.example.project250311.Schedule.NoSchool

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.example.project250311.ui.theme.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.launch

@Composable
fun LeaveSystemScreen(navController: NavController) {
    val context = LocalContext.current
    val viewModel = remember {
        val db = LeaveDatabase.getDatabase(context)
        val repository = LeaveDatabase.Companion.LeaveRepository(db.leaveDao())
        LeaveDatabase.LeaveViewModel(repository)
    }

    var currentScreen by remember { mutableStateOf("main") }
    var cookies by remember { mutableStateOf<String?>(null) }

    when (currentScreen) {
        "main" -> LeaveMainScreen(
            onRequestLeave = { currentScreen = "request" },
            onViewHistory = { currentScreen = "history" },
            onBack = { navController.popBackStack() }
        )
        "request" -> GetLeaveDataScreen(
            viewModel = viewModel,
            context = context,
            onLoginSuccess = { cookiesValue -> cookies = cookiesValue },
            onBack = { currentScreen = "main" }
        )
        "history" -> LeaveHistoryScreen(
            viewModel = viewModel,
            onBack = { currentScreen = "main" }
        )
    }
}

@Composable
fun LeaveMainScreen(
    onRequestLeave: () -> Unit,
    onViewHistory: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 標題
        Text(
            text = "請假系統",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(32.dp))

        // 功能按鈕
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Button(
                onClick = {
                    Log.d("LeaveScreen", "我要請假按鈕點擊")
                    onRequestLeave()
                },
                modifier = Modifier.fillMaxWidth(0.7f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                elevation = ButtonDefaults.buttonElevation(0.dp)
            ) {
                Text("我要請假")
            }

            Button(
                onClick = onViewHistory,
                modifier = Modifier.fillMaxWidth(0.7f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                elevation = ButtonDefaults.buttonElevation(0.dp)
            ) {
                Text("查看請假紀錄")
            }

            Button(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth(0.7f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                elevation = ButtonDefaults.buttonElevation(0.dp)
            ) {
                Text("返回主畫面")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeaveHistoryScreen(
    viewModel: LeaveDatabase.LeaveViewModel,
    onBack: () -> Unit
) {
    val leaves by viewModel.allLeaves.observeAsState(emptyList())
    var searchQuery by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { viewModel.loadAllLeaves() }

    val groupedLeaves = leaves
        .filter {
            searchQuery.isBlank() || it.recordType.contains(searchQuery, true) ||
                    it.leave_type.contains(searchQuery, true) ||
                    it.date_leave.contains(searchQuery, true) ||
                    it.courseName.contains(searchQuery, true)
        }
        .groupBy { it.courseName }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("請假紀錄") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            // 搜尋欄
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("搜尋課程、類型、請假類型、日期") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = MorandiPrimary,
                    unfocusedIndicatorColor = MorandiSecondary,
                    cursorColor = MorandiPrimary,
                    focusedLabelColor = MorandiPrimary,
                    unfocusedLabelColor = MorandiSecondary,
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (groupedLeaves.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("沒有符合條件的請假紀錄", color = MorandiOnSurface)
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    groupedLeaves.forEach { (courseName, leavesForCourse) ->
                        item {
                            CourseCard(courseName, leavesForCourse)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CourseCard(courseName: String, leaves: List<LeaveData>) {
    var expanded by remember { mutableStateOf(false) }
    val totalHours = leaves.sumOf { it.hours }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MorandiSurface),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Text(text = courseName, style = MaterialTheme.typography.titleMedium, color = MorandiOnSurface)
                    Text(text = "總請假時數: $totalHours 小時", style = MaterialTheme.typography.bodySmall, color = MorandiOnSecondary)
                }
                Button(
                    onClick = { expanded = !expanded },
                    shape = MaterialTheme.shapes.large,
                    colors = ButtonDefaults.buttonColors(containerColor = MorandiPrimary),
                    elevation = ButtonDefaults.buttonElevation(0.dp)
                ) {
                    Text(if (expanded) "收起" else "展開")
                }
            }

            if (expanded) {
                Spacer(modifier = Modifier.height(8.dp))
                leaves.forEach { leave ->
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        Text("日期: ${leave.date_leave}", color = MorandiOnSurface)
                        Text("請假類型: ${getLeaveTypeName(leave.leave_type)}", color = MorandiOnSurface)
                        Text("時數: ${leave.hours} 小時", color = MorandiOnSurface)
                        Divider(thickness = 0.8.dp, color = MorandiSecondary)
                    }
                }
            }
        }
    }
}

fun getLeaveTypeName(type: String): String = when(type.toIntOrNull()) {
    2 -> "病假"
    3 -> "喪假"
    4 -> "事假"
    5 -> "生理假"
    6 -> "分娩假"
    7 -> "陪產假"
    8 -> "歲時祭儀假"
    9 -> "心理假"
    else -> "其他"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GetLeaveDataScreen(
    viewModel: LeaveDatabase.LeaveViewModel,
    context: Context,
    onLoginSuccess: (String) -> Unit,
    onBack: () -> Unit
) {
    var cookies by remember { mutableStateOf<String?>(null) }
    var webViewLoaded by remember { mutableStateOf(false) }
    val leaveList by viewModel.allLeaves.observeAsState(emptyList())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("請假資料") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (cookies == null) {
                if (!webViewLoaded) {
                    WebViewLeaveComponent(
                        url = "https://casauth.nttu.edu.tw/cas/login?service=https%3a%2f%2faskleave.nttu.edu.tw%2findex.aspx",
                        onLoginSuccess = {
                            cookies = it
                            webViewLoaded = true
                            onLoginSuccess(it)
                        },
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
                } else {
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

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebViewLeaveComponent(
    url: String,
    onLoginSuccess: (String) -> Unit,
    onLeaveAppClicked: (String?) -> Unit
) {
    Log.d("WebViewLeaveScreen", "WebView loading: $url")
    AndroidView(factory = { context ->
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            settings.cacheMode = android.webkit.WebSettings.LOAD_NO_CACHE
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
                        Log.d("WebViewLeaveScreen", "成功登入")
                        if (!cookies.isNullOrEmpty()) {
                            onLoginSuccess(cookies)
                        } else {
                            Log.e("WebViewLeaveScreen", "登入 Cookie 可能未成功獲取")
                        }
                    } else if (url.contains("Leave.aspx")) {
                        Log.d("WebViewLeaveScreen", "偵測到請假頁面，開始監聽按鈕")

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
        if (message.isNotEmpty()) {
            onLeaveAppClicked(message)
        }
    }
}