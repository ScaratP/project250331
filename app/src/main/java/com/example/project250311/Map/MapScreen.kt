package com.example.project250311.Map

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.location.Location
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import com.example.project250311.Map.network.RetrofitInstance
import com.example.project250311.Map.utils.PolylineUtils
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import com.example.project250311.R
import com.google.android.gms.maps.model.Dot
import com.google.android.gms.maps.model.Gap
import com.google.android.gms.maps.model.PatternItem
import androidx.navigation.NavController
import com.google.android.gms.maps.model.BitmapDescriptorFactory

// 1. CustomPoint 包含 description，用於顯示介紹對話框
data class CustomPoint(
    val location: LatLng,
    val name: String,
    val description: String,
    val hasIndoorMap: Boolean = false // 新增標記
)

@SuppressLint("MissingPermission")
@Composable
fun MapScreen(
    navController: NavController? = null
) {
    val context = LocalContext.current
    val fusedClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    // 2. 初始鏡頭：校園中心
    val defaultLatLng = LatLng(22.7366, 121.0675)
    val cameraState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(defaultLatLng, 15f)
    }

    // 虛線樣式
    val dashPattern = listOf<PatternItem>(Dot(), Gap(10f))

    // 4. 自訂地點列表 - 移到狀態變數前面
    val customPoints = remember {
        listOf(
            CustomPoint(
                LatLng(22.738542718675728, 121.06613647723158),
                "理工學院",
                "理工學院位於校園北側，內有多間實驗室與教室。點擊導航可進入室內地圖。",
                hasIndoorMap = true
            ),
            CustomPoint(
                LatLng(22.738700601981595, 121.06497484121572),
                "資源回收站",
                "校園資源回收站，提供紙張、塑膠、金屬等回收服務。"
            ),
            CustomPoint(
                LatLng(22.737311078649267, 121.06515081473918),
                "第一學生宿舍",
                "第一學生宿舍是校園內最早啟用的一棟，設有單人間與雙人間。"
            ),
            CustomPoint(
                LatLng(22.736985102168752, 121.06541152586802),
                "一宿餐廳",
                "一宿餐廳提供多種大學餐選項，並且全天開放。"
            ),
            CustomPoint(
                LatLng(22.73667908810683, 121.065407893042),
                "7-11",
                "校園門口的 7-11，方便師生隨時購買飲料與零食。"
            ),
            CustomPoint(
                LatLng(22.736206402791673, 121.0651933530481),
                "第二學生宿舍",
                "第二學生宿舍新落成，房間採現代化設計，附有公共休息室。"
            ),
            CustomPoint(
                LatLng(22.73340361849101, 121.06581718244463),
                "操場",
                "校園操場，可供足球、慢跑與排球等活動使用。"
            ),
            CustomPoint(
                LatLng(22.73292806965856, 121.06740378782679),
                "體育館",
                "體育館內有籃球場、羽球場與健身房，對外開放時段請參考公告。"
            ),
            CustomPoint(
                LatLng(22.733878454879942, 121.06840153239384),
                "籃球場",
                "室外籃球場，夜間有照明，適合休閒籃球活動。"
            ),
            CustomPoint(
                LatLng(22.73567797363531, 121.06765063326057),
                "圖書館",
                "圖書館擁有豐富藏書與安靜閱讀区，也被稱為全球八度獨特圖書館之一。"
            ),
            CustomPoint(
                LatLng(22.73599707595406, 121.06669594919275),
                "共同教學大樓",
                "共同教學大樓提供多間多功能教室與研討室，適合大小型課程。"
            ),
            CustomPoint(
                LatLng(22.736520281361905, 121.06698965107849),
                "靜心書院",
                "靜心書院為校園的宗教與靜修中心，定期舉辦靜心活動。"
            ),
            CustomPoint(
                LatLng(22.73917469216459, 121.0670538530699),
                "師範學院",
                "師範學院為教育學系與師資培育單位所在地。"
            ),
            CustomPoint(
                LatLng(22.73863578654702, 121.06753201693554),
                "淑貞講堂",
                "淑貞講堂常舉辦演講與表演活動，座位寬敞舒適。"
            ),
            CustomPoint(
                LatLng(22.738117547140654, 121.06843506125115),
                "演藝廳",
                "演藝廳為音樂與戲劇演出場地，具備專業音響設備。"
            ),
            CustomPoint(
                LatLng(22.73795917868316, 121.06901698013174),
                "人文學院",
                "人文學院包含文學院與歷史系，教室與辦公室分布寬敞。"
            ),
            CustomPoint(
                LatLng(22.736849993509747, 121.0686699597833),
                "行政大樓",
                "行政大樓為校長室與各行政單位辦公的地方。"
            )
        )
    }

    // 3. 所有狀態變數聲明
    var permissionGranted by remember { mutableStateOf(false) }
    var currentLoc by remember { mutableStateOf<LatLng?>(null) }
    var lastRerouteLoc by remember { mutableStateOf<LatLng?>(null) }
    var destination by remember { mutableStateOf<LatLng?>(null) }
    var routePoints by remember { mutableStateOf<List<LatLng>>(emptyList()) }
    var isRouting by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var selectedPoint by remember { mutableStateOf<CustomPoint?>(null) }
    var travelTimeText by remember { mutableStateOf<String?>(null) }
    
    // 搜尋相關狀態
    var isSearchExpanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var searchSuggestions by remember { mutableStateOf<List<CustomPoint>>(emptyList()) }
    var showSearchSuggestions by remember { mutableStateOf(false) }

    // 起點搜尋相關狀態
    var startQuery by remember { mutableStateOf("") }
    var startSuggestions by remember { mutableStateOf<List<CustomPoint>>(emptyList()) }
    var showStartSuggestions by remember { mutableStateOf(false) }
    var customStartPoint by remember { mutableStateOf<LatLng?>(null) }
    var isUsingCurrentLocation by remember { mutableStateOf(true) }

    // 室內導航相關狀態
    var isNavigatingToEngineeringCollege by remember { mutableStateOf(false) }
    var showIndoorMapDialog by remember { mutableStateOf(false) }
    var indoorDestination by remember { mutableStateOf("") }

    // Snackbar狀態
    val snackbarHostState = remember { SnackbarHostState() }

    // 5. 所有函數定義
    // 新增：檢查目的地是否支援室內導航
    fun isDestinationSupportIndoor(destinationPoint: LatLng?, searchQueryText: String): Boolean {
        if (destinationPoint == null) return false
        
        val engineeringCollege = customPoints.firstOrNull { point -> point.name == "理工學院" }
        val isEngineeringCollege = engineeringCollege?.location == destinationPoint
        val isSeClassroom = searchQueryText.lowercase().startsWith("sec") || 
                           searchQueryText.lowercase().startsWith("se")
        
        return isEngineeringCollege || isSeClassroom
    }

    // 獲取實際起點位置
    fun getActualStartPoint(): LatLng? {
        return if (isUsingCurrentLocation) currentLoc else customStartPoint
    }

    // 更新搜尋建議
    fun updateSearchSuggestions(query: String) {
        if (query.length >= 1) {
            searchSuggestions = customPoints
                .filter { point -> point.name.contains(query, ignoreCase = true) }
                .take(5)
            showSearchSuggestions = searchSuggestions.isNotEmpty()
        } else {
            showSearchSuggestions = false
        }
    }

    // 更新起點搜尋建議
    fun updateStartSuggestions(query: String) {
        if (query.length >= 1) {
            startSuggestions = customPoints
                .filter { point -> point.name.contains(query, ignoreCase = true) }
                .take(5)
            showStartSuggestions = startSuggestions.isNotEmpty()
        } else {
            showStartSuggestions = false
        }
    }

    // 處理起點搜尋
    fun handleStartSearch() {
        val point = customPoints.firstOrNull { customPoint -> 
            customPoint.name.contains(startQuery, ignoreCase = true)
        }
        
        if (point != null) {
            customStartPoint = point.location
            isUsingCurrentLocation = false
            showStartSuggestions = false
            
            scope.launch {
                snackbarHostState.showSnackbar("起點設定為：${point.name}")
            }
        } else if (startQuery.lowercase() == "我的位置" || startQuery.lowercase() == "當前位置") {
            customStartPoint = null
            isUsingCurrentLocation = true
            showStartSuggestions = false
            
            scope.launch {
                snackbarHostState.showSnackbar("起點設定為：當前位置")
            }
        } else {
            scope.launch {
                snackbarHostState.showSnackbar("未找到起點：$startQuery")
            }
        }
        focusManager.clearFocus()
    }

    // 重置起點為當前位置
    fun resetToCurrentLocation() {
        startQuery = ""
        customStartPoint = null
        isUsingCurrentLocation = true
        showStartSuggestions = false
        
        scope.launch {
            snackbarHostState.showSnackbar("起點重置為當前位置")
        }
    }

    // 檢查是否到達理工學院
    fun checkArrivalAtEngineeringCollege() {
        if (isNavigatingToEngineeringCollege && currentLoc != null) {
            val engineeringCollege = customPoints.first { point -> point.name == "理工學院" }
            val distanceToDestination = distanceBetween(currentLoc!!, engineeringCollege.location)
            
            // 當距離小於50公尺時，顯示室內地圖選項
            if (distanceToDestination < 50f) {
                showIndoorMapDialog = true
                isNavigatingToEngineeringCollege = false
            }
        }
    }

    // 修正：處理目的地搜尋
    fun handleSearch() {
        val point = customPoints.firstOrNull { customPoint -> 
            customPoint.name.contains(searchQuery, ignoreCase = true)
        }
        
        val isSeClassroom = searchQuery.lowercase().startsWith("sec") || 
                           searchQuery.lowercase().startsWith("se")
        
        if (point != null) {
            destination = point.location
            
            if (point.name == "理工學院" || isSeClassroom) {
                isNavigatingToEngineeringCollege = true
                if (isSeClassroom) {
                    indoorDestination = searchQuery
                }
            }
            
            scope.launch {
                cameraState.move(CameraUpdateFactory.newLatLngZoom(point.location, 17f))
            }
            
            // 計算路線
            val startPoint = getActualStartPoint()
            android.util.Log.d("MapScreen", "起點: $startPoint, 終點: ${point.location}")
            
            drawRoute(
                origin = startPoint,
                dest = point.location,
                onStart = { 
                    isRouting = true
                    android.util.Log.d("MapScreen", "開始計算路線")
                },
                onSuccess = { points ->
                    isRouting = false
                    routePoints = points
                    android.util.Log.d("MapScreen", "路線計算成功，點數: ${points.size}")
                },
                onTime = { timeText ->
                    travelTimeText = timeText
                    android.util.Log.d("MapScreen", "預計時間: $timeText")
                },
                onError = { errorMessage ->
                    isRouting = false
                    errorMsg = errorMessage
                    android.util.Log.e("MapScreen", "路線計算失敗: $errorMessage")
                    scope.launch {
                        delay(3000)
                        errorMsg = null
                    }
                }
            )
            
            showSearchSuggestions = false
            scope.launch {
                snackbarHostState.showSnackbar("已設定路線前往：${point.name}")
            }
        } else if (isSeClassroom) {
            // 如果是se系列教室但沒找到對應建築物，直接導航到理工學院
            val engineeringCollege = customPoints.first { customPoint -> customPoint.name == "理工學院" }
            destination = engineeringCollege.location
            isNavigatingToEngineeringCollege = true
            indoorDestination = searchQuery
            
            // 移動地圖到理工學院位置
            scope.launch {
                cameraState.move(CameraUpdateFactory.newLatLngZoom(engineeringCollege.location, 17f))
            }
            
            // 計算到理工學院的路線
            drawRoute(
                origin = getActualStartPoint(),
                dest = engineeringCollege.location,
                onStart = { isRouting = true },
                onSuccess = { points ->
                    isRouting = false
                    routePoints = points
                },
                onTime = { timeText ->
                    travelTimeText = timeText
                },
                onError = {
                    isRouting = false
                    errorMsg = it
                    scope.launch {
                        delay(3000)
                        errorMsg = null
                    }
                }
            )
            
            showSearchSuggestions = false
            scope.launch {
                snackbarHostState.showSnackbar("已設定路線前往理工學院 (室內: $searchQuery)")
            }
        } else {
            scope.launch {
                snackbarHostState.showSnackbar("未找到地點：$searchQuery")
            }
        }
        focusManager.clearFocus()
    }

    // 6. 申請定位權限
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> permissionGranted = granted }

    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            launcher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        } else {
            permissionGranted = true
        }
    }

    // 7. 設定持續定位
    val locationRequest = remember {
        LocationRequest.create().apply {
            interval = 5000
            fastestInterval = 3000
            priority = Priority.PRIORITY_HIGH_ACCURACY
        }
    }

    val locationCallback = remember {
        object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc: Location? = result.lastLocation
                if (loc != null) {
                    val newLatLng = LatLng(loc.latitude, loc.longitude)
                    currentLoc = newLatLng

                    if (isUsingCurrentLocation) {
                        destination?.let { dest ->
                            val prev = lastRerouteLoc
                            if (prev == null || distanceBetween(prev, newLatLng) > 20f) {
                                lastRerouteLoc = newLatLng
                                drawRoute(
                                    origin = newLatLng,
                                    dest = dest,
                                    onStart = { isRouting = true },
                                    onSuccess = { points ->
                                        isRouting = false
                                        routePoints = points
                                    },
                                    onTime = { timeText ->
                                        travelTimeText = timeText
                                    },
                                    onError = {
                                        isRouting = false
                                        errorMsg = it
                                        scope.launch {
                                            delay(3000)
                                            errorMsg = null
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(permissionGranted) {
        if (permissionGranted) {
            fusedClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
        }
    }

    DisposableEffect(Unit) {
        onDispose { fusedClient.removeLocationUpdates(locationCallback) }
    }

    // 8. 初次定位
    LaunchedEffect(permissionGranted) {
        if (permissionGranted) {
            fusedClient.lastLocation
                .addOnSuccessListener { loc: Location? ->
                    if (loc != null) {
                        val ll = LatLng(loc.latitude, loc.longitude)
                        currentLoc = ll
                        scope.launch {
                            cameraState.move(CameraUpdateFactory.newLatLng(ll))
                        }
                    } else {
                        errorMsg = "無法取得目前位置"
                        scope.launch {
                            delay(3000)
                            errorMsg = null
                        }
                    }
                }
                .addOnFailureListener {
                    errorMsg = "定位失敗：${it.message}"
                    scope.launch {
                        delay(3000)
                        errorMsg = null
                    }
                }
        }
    }

    // 9. 監控位置變化以檢查是否到達理工學院
    LaunchedEffect(currentLoc) {
        if (currentLoc != null) {
            checkArrivalAtEngineeringCollege()
        }
    }

    // 10. Map 畫面
    Box(modifier = Modifier.fillMaxSize()) {
        
        GoogleMap(
            modifier = Modifier.matchParentSize(),
            cameraPositionState = cameraState,
            properties = MapProperties(isMyLocationEnabled = permissionGranted),
            onMapClick = { latLng ->
                destination = latLng
                routePoints = emptyList()
                lastRerouteLoc = getActualStartPoint()
                travelTimeText = null
                
                val startPoint = getActualStartPoint()
                android.util.Log.d("MapScreen", "地圖點擊 - 起點: $startPoint, 終點: $latLng")
                
                drawRoute(
                    origin = startPoint,
                    dest = latLng,
                    onStart = { isRouting = true },
                    onSuccess = { points ->
                        isRouting = false
                        routePoints = points
                        android.util.Log.d("MapScreen", "點擊路線成功，點數: ${points.size}")
                    },
                    onTime = { timeText ->
                        travelTimeText = timeText
                    },
                    onError = { errorMessage ->
                        isRouting = false
                        errorMsg = errorMessage
                        android.util.Log.e("MapScreen", "點擊路線失敗: $errorMessage")
                        scope.launch {
                            delay(3000)
                            errorMsg = null
                        }
                    }
                )
            }
        ) {
            // A. 顯示「當前位置」Marker
            currentLoc?.let { currentLocation ->
                Marker(
                    state = MarkerState(currentLocation),
                    title = "當前位置",
                    icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)
                )
            }

            // B. 显示「自定义起点」Marker
            if (!isUsingCurrentLocation) {
                customStartPoint?.let { startLocation ->
                    Marker(
                        state = MarkerState(startLocation),
                        title = "起點",
                        icon = BitmapDescriptorFactory.fromBitmap(createGrayDotBitmap(context)),
                        onClick = {
                            resetToCurrentLocation()
                            true
                        }
                    )
                }
            }

            // C. 顯示「目的地」Marker
            destination?.let { destLatLng ->
                Marker(
                    state = MarkerState(destLatLng),
                    title = "目的地",
                    icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED),
                    onClick = {
                        destination = null
                        routePoints = emptyList()
                        travelTimeText = null
                        true
                    }
                )
            }

            // D. 顯示自定義地點 Marker
            customPoints.forEach { custom ->
                Marker(
                    state = MarkerState(custom.location),
                    title = custom.name,
                    icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_CYAN),
                    onClick = {
                        selectedPoint = custom
                        false
                    }
                )
            }

            // E. 繪製路線
            val actualStartPoint = getActualStartPoint()
            
            if (actualStartPoint != null && routePoints.isNotEmpty()) {
                val firstOnRoad = routePoints.first()
                Polyline(
                    points = listOf(actualStartPoint, firstOnRoad),
                    width = 6f,
                    color = Color.Gray,
                    pattern = dashPattern
                )
            }
            
            if (routePoints.isNotEmpty()) {
                Polyline(
                    points = routePoints,
                    width = 12f,
                    color = Color.Blue
                )
            }
            
            if (routePoints.isNotEmpty() && destination != null) {
                val lastOnRoad = routePoints.last()
                Polyline(
                    points = listOf(lastOnRoad, destination!!),
                    width = 6f,
                    color = Color.Gray,
                    pattern = dashPattern
                )
            }
        }

        // 搜尋卡片
        AnimatedVisibility(
            visible = true,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 16.dp, end = 80.dp, top = 16.dp)
                .zIndex(1f)
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .background(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(12.dp)
                    ),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(8.dp)
                ) {
                    // 展開/收起按鈕
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isSearchExpanded = !isSearchExpanded },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "路線規劃",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                        Icon(
                            imageVector = if (isSearchExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (isSearchExpanded) "收起" else "展開",
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // 展開的搜索內容
                    AnimatedVisibility(
                        visible = isSearchExpanded,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Column(modifier = Modifier.padding(6.dp)) {
                            // 起點搜尋框
                            OutlinedTextField(
                                value = startQuery,
                                onValueChange = { 
                                    startQuery = it
                                    updateStartSuggestions(it)
                                    showSearchSuggestions = false
                                },
                                label = { Text("起點", style = MaterialTheme.typography.bodySmall) },
                                placeholder = { 
                                    Text(
                                        if (isUsingCurrentLocation) "當前位置" else "例如: 圖書館",
                                        style = MaterialTheme.typography.bodySmall
                                    ) 
                                },
                                modifier = Modifier.fillMaxWidth(),
                                leadingIcon = { 
                                    Icon(
                                        imageVector = Icons.Default.LocationOn, 
                                        contentDescription = null,
                                        tint = Color.Green,
                                        modifier = Modifier.size(18.dp)
                                    ) 
                                },
                                trailingIcon = {
                                    Row {
                                        if (startQuery.isNotEmpty()) {
                                            IconButton(
                                                onClick = { 
                                                    startQuery = ""
                                                    showStartSuggestions = false
                                                },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Clear,
                                                    contentDescription = "清除",
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                        if (!isUsingCurrentLocation) {
                                            IconButton(
                                                onClick = { resetToCurrentLocation() },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.LocationOn,
                                                    contentDescription = "使用當前位置",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }
                                },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodySmall,
                                keyboardOptions = KeyboardOptions.Default.copy(
                                    imeAction = ImeAction.Search
                                ),
                                keyboardActions = KeyboardActions(
                                    onSearch = { handleStartSearch() }
                                )
                            )

                            // 起點搜尋按鈕
                            Spacer(modifier = Modifier.height(4.dp))
                            Button(
                                onClick = { handleStartSearch() },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(32.dp),
                                enabled = startQuery.isNotEmpty()
                            ) {
                                Text("設定起點", style = MaterialTheme.typography.bodySmall)
                            }

                            // 起點搜尋建議
                            AnimatedVisibility(visible = showStartSuggestions) {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 2.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                                    )
                                ) {
                                    LazyColumn(
                                        modifier = Modifier.heightIn(min = 0.dp, max = 120.dp)
                                    ) {
                                        items(startSuggestions) { point ->
                                            Text(
                                                text = point.name,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable {
                                                        startQuery = point.name
                                                        showStartSuggestions = false
                                                        focusManager.clearFocus()
                                                        handleStartSearch()
                                                    }
                                                    .padding(8.dp),
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // 目的地搜尋框
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { 
                                    searchQuery = it
                                    updateSearchSuggestions(it)
                                    showStartSuggestions = false
                                },
                                label = { Text("目的地", style = MaterialTheme.typography.bodySmall) },
                                placeholder = { Text("例如: 圖書館、sec101", style = MaterialTheme.typography.bodySmall) },
                                modifier = Modifier.fillMaxWidth(),
                                leadingIcon = { 
                                    Icon(
                                        imageVector = Icons.Default.LocationOn, 
                                        contentDescription = null,
                                        tint = Color.Red,
                                        modifier = Modifier.size(18.dp)
                                    ) 
                                },
                                trailingIcon = {
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(
                                            onClick = { 
                                                searchQuery = ""
                                                showSearchSuggestions = false
                                            },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Clear,
                                                contentDescription = "清除",
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodySmall,
                                keyboardOptions = KeyboardOptions.Default.copy(
                                    imeAction = ImeAction.Search
                                ),
                                keyboardActions = KeyboardActions(
                                    onSearch = { handleSearch() }
                                )
                            )
                            
                            Spacer(modifier = Modifier.height(4.dp))
                            
                            Button(
                                onClick = { handleSearch() },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(32.dp),
                                enabled = searchQuery.isNotEmpty()
                            ) {
                                Text("搜尋路線", style = MaterialTheme.typography.bodySmall)
                            }

                            // 室內路線按鈕
                            if (destination != null && routePoints.isNotEmpty() && 
                                isDestinationSupportIndoor(destination, searchQuery)) {
                                
                                Spacer(modifier = Modifier.height(4.dp))
                                
                                Button(
                                    onClick = {
                                        val finalDestination = if (searchQuery.lowercase().startsWith("sec") || 
                                                                 searchQuery.lowercase().startsWith("se")) {
                                            searchQuery
                                        } else {
                                            ""
                                        }
                                        
                                        navController?.navigate(
                                            if (finalDestination.isNotEmpty()) {
                                                "indoormap/$finalDestination"
                                            } else {
                                                "indoormap"
                                            }
                                        )
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(32.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.secondary
                                    )
                                ) {
                                    Text("看室內路線", style = MaterialTheme.typography.bodySmall)
                                }
                            }

                            // 目的地搜尋建議
                            AnimatedVisibility(visible = showSearchSuggestions) {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 2.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                                    )
                                ) {
                                    LazyColumn(
                                        modifier = Modifier.heightIn(min = 0.dp, max = 120.dp)
                                    ) {
                                        items(searchSuggestions) { point ->
                                            Text(
                                                text = point.name,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable {
                                                        searchQuery = point.name
                                                        showSearchSuggestions = false
                                                        focusManager.clearFocus()
                                                        handleSearch()
                                                    }
                                                    .padding(8.dp),
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                    }
                                }
                            }

                            // 顯示當前設定
                            if (!isUsingCurrentLocation || destination != null) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    if (!isUsingCurrentLocation) {
                                        Surface(
                                            color = Color.Green.copy(alpha = 0.1f),
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = "起點: ${customPoints.firstOrNull { point -> point.location == customStartPoint }?.name ?: "自訂位置"}",
                                                modifier = Modifier.padding(4.dp),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Color.Green
                                            )
                                        }
                                    } else {
                                        Surface(
                                            color = Color.Blue.copy(alpha = 0.1f),
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = "起點: 當前位置",
                                                modifier = Modifier.padding(4.dp),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Color.Blue
                                            )
                                        }
                                    }
                                    
                                    destination?.let { destinationPoint ->
                                        Surface(
                                            color = Color.Red.copy(alpha = 0.1f),
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = "目的地: ${customPoints.firstOrNull { point -> point.location == destinationPoint }?.name ?: "地圖位置"}",
                                                modifier = Modifier.padding(4.dp),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Color.Red
                                            )
                                        }
                                    }
                                    
                                    // 室內導航提示
                                    if (destination != null && isDestinationSupportIndoor(destination, searchQuery)) {
                                        Surface(
                                            color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f),
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = if (searchQuery.lowercase().startsWith("sec") || searchQuery.lowercase().startsWith("se")) {
                                                    "室內目標: $searchQuery"
                                                } else {
                                                    "支援室內導航"
                                                },
                                                modifier = Modifier.padding(4.dp),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.secondary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 顯示「路線計算中」圓形指示器
        if (isRouting) {
            CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(48.dp)
                    .background(Color.White.copy(alpha = 0.6f), shape = MaterialTheme.shapes.small)
                    .padding(8.dp)
            )
        }

        // 顯示費時文字於底部
        travelTimeText?.let { text ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
                    .background(Color.White.copy(alpha = 0.8f), shape = MaterialTheme.shapes.medium)
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "預計花費：$text",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.DarkGray
                )
            }
        }

        // 顯示錯誤訊息（三秒後自動消失）
        AnimatedVisibility(
            visible = errorMsg != null,
            enter = fadeIn(tween(300)) + slideInVertically(
                initialOffsetY = { -100 }, animationSpec = tween(300)
            ),
            exit = fadeOut(tween(300)) + slideOutVertically(
                targetOffsetY = { -100 }, animationSpec = tween(300)
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentSize(Alignment.TopCenter)
                    .padding(top = 16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .background(color = Color(0xFFFFCDD2), shape = MaterialTheme.shapes.large)
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = errorMsg ?: "",
                        color = Color.DarkGray,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // 9. 顯示自訂介紹對話框
        selectedPoint?.let { point ->
            AlertDialog(
                onDismissRequest = { selectedPoint = null },
                title = { Text(text = point.name) },
                text = { Text(text = point.description) },
                dismissButton = {
                    TextButton(onClick = { selectedPoint = null }) {
                        Text("取消")
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        destination = point.location
                        routePoints = emptyList()
                        lastRerouteLoc = getActualStartPoint()
                        travelTimeText = null
                        drawRoute(
                            origin = getActualStartPoint(),
                            dest = point.location,
                            onStart = { isRouting = true },
                            onSuccess = { points ->
                                isRouting = false
                                routePoints = points
                            },
                            onTime = { timeText ->
                                travelTimeText = timeText
                            },
                            onError = {
                                isRouting = false
                                errorMsg = it
                                scope.launch {
                                    delay(3000)
                                    errorMsg = null
                                }
                            }
                        )
                        selectedPoint = null
                    }) {
                        Text("導航")
                    }
                }
            )
        }

        // 室內地圖對話框
        if (showIndoorMapDialog) {
            AlertDialog(
                onDismissRequest = { showIndoorMapDialog = false },
                title = { Text("已到達理工學院") },
                text = { 
                    Text(
                        if (indoorDestination.isNotEmpty()) {
                            "您已到達理工學院，是否要進入室內地圖導航到 $indoorDestination？"
                        } else {
                            "您已到達理工學院，是否要查看室內地圖？"
                        }
                    ) 
                },
                dismissButton = {
                    TextButton(onClick = { 
                        showIndoorMapDialog = false
                        indoorDestination = ""
                    }) {
                        Text("取消")
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        showIndoorMapDialog = false
                        navController?.navigate(
                            if (indoorDestination.isNotEmpty()) {
                                "indoormap/$indoorDestination"
                            } else {
                                "indoormap"
                            }
                        )
                    }) {
                        Text("進入室內地圖")
                    }
                }
            )
        }

        // 顯示 Snackbar
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 76.dp)
        )
    }
}

// Utility：計算兩點距離（單位：公尺）
fun distanceBetween(a: LatLng, b: LatLng): Float {
    val result = FloatArray(1)
    android.location.Location.distanceBetween(a.latitude, a.longitude, b.latitude, b.longitude, result)
    return result[0]
}

/**
 * drawRoute 新增 onTime callback：從 API 回應中取出「duration.text」並回傳
 */
fun drawRoute(
    origin: LatLng?,
    dest: LatLng,
    onStart: () -> Unit = {},
    onSuccess: (List<LatLng>) -> Unit,
    onTime: (String) -> Unit,
    onError: (String) -> Unit
) {
    if (origin == null) {
        android.util.Log.e("MapScreen", "起點為空")
        onError("尚未取得起點位置")
        return
    }
    
    // 檢查起點和終點是否太近
    val distance = android.location.Location("start").apply {
        latitude = origin.latitude
        longitude = origin.longitude
    }.distanceTo(android.location.Location("end").apply {
        latitude = dest.latitude
        longitude = dest.longitude
    })
    
    android.util.Log.d("MapScreen", "起點終點距離: ${distance}公尺")
    
    // 如果距離太近（小於10公尺），直接繪製直線
    if (distance < 10f) {
        android.util.Log.d("MapScreen", "距離太近，繪製直線路徑")
        onSuccess(listOf(origin, dest))
        onTime("1分鐘")
        return
    }
    
    android.util.Log.d("MapScreen", "開始請求路線: ${origin.latitude},${origin.longitude} -> ${dest.latitude},${dest.longitude}")
    onStart()
    
    val o = "${origin.latitude},${origin.longitude}"
    val d = "${dest.latitude},${dest.longitude}"
    
    try {
        RetrofitInstance.api.getDirections(
            origin = o,
            destination = d,
            mode = "walking",
            apiKey = "AIzaSyDbCPl8a9m7dGMgTqF2GFL_cPSRjV_hiOQ"
        ).enqueue(object : Callback<com.example.project250311.Map.model.DirectionsResponse> {
            override fun onResponse(
                call: Call<com.example.project250311.Map.model.DirectionsResponse>,
                response: Response<com.example.project250311.Map.model.DirectionsResponse>
            ) {
                android.util.Log.d("MapScreen", "API 回應碼: ${response.code()}")
                
                if (response.isSuccessful) {
                    val body = response.body()
                    android.util.Log.d("MapScreen", "回應內容: $body")
                    
                    val route = body?.routes?.firstOrNull()
                    
                    if (route == null) {
                        android.util.Log.w("MapScreen", "API 未回傳路線，可能是距離太近或無法步行到達，使用直線路徑")
                        // 使用直線路徑作為備選方案
                        onSuccess(listOf(origin, dest))
                        onTime("約${(distance / 80).toInt() + 1}分鐘") // 假設步行速度80公尺/分鐘
                        return
                    }
                    
                    val leg = route.legs?.firstOrNull()
                    val points = route.overview_polyline?.points

                    val durationText = leg?.duration?.text ?: "未知時間"
                    onTime(durationText)

                    if (!points.isNullOrEmpty()) {
                        val decodedPoints = PolylineUtils.decodePolyline(points)
                        android.util.Log.d("MapScreen", "解碼後路徑點數: ${decodedPoints.size}")
                        onSuccess(decodedPoints)
                    } else {
                        android.util.Log.w("MapScreen", "路線數據為空，使用直線路徑")
                        onSuccess(listOf(origin, dest))
                        onTime(durationText)
                    }
                } else {
                    android.util.Log.e("MapScreen", "API 錯誤: ${response.code()}, ${response.message()}")
                    
                    // API 失敗時使用直線路徑作為備選方案
                    android.util.Log.w("MapScreen", "API 失敗，使用直線路徑作為備選方案")
                    onSuccess(listOf(origin, dest))
                    onTime("約${(distance / 80).toInt() + 1}分鐘")
                    
                    // 仍然顯示錯誤信息
                    when (response.code()) {
                        400 -> onError("請求參數錯誤（已使用直線路徑）")
                        401 -> onError("API 金鑰無效（已使用直線路徑）")
                        403 -> onError("API 權限不足（已使用直線路徑）")
                        429 -> onError("API 請求超限（已使用直線路徑）")
                        else -> onError("API 錯誤 ${response.code()}（已使用直線路徑）")
                    }
                }
            }

            override fun onFailure(
                call: Call<com.example.project250311.Map.model.DirectionsResponse>,
                t: Throwable
            ) {
                android.util.Log.e("MapScreen", "網路請求失敗，使用直線路徑", t)
                // 網路失敗時使用直線路徑
                onSuccess(listOf(origin, dest))
                onTime("約${(distance / 80).toInt() + 1}分鐘")
                onError("網路錯誤（已使用直線路徑）：${t.localizedMessage}")
            }
        })
    } catch (e: Exception) {
        android.util.Log.e("MapScreen", "請求異常，使用直線路徑", e)
        onSuccess(listOf(origin, dest))
        onTime("約${(distance / 80).toInt() + 1}分鐘")
        onError("請求異常（已使用直線路徑）：${e.localizedMessage}")
    }
}

// 創建灰色圓點圖標
fun createGrayDotBitmap(context: Context): Bitmap {
    val width = 24
    val height = 24
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    
    val paint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
        color = android.graphics.Color.DKGRAY
    }
    
    // 繪製圓點
    canvas.drawCircle(width / 2f, height / 2f, width / 2f - 2, paint)
    
    // 添加白色邊框
    val borderPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = android.graphics.Color.WHITE
    }
    canvas.drawCircle(width / 2f, height / 2f, width / 2f - 2, borderPaint)
    
    return bitmap
}
