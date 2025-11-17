package com.example.project250311.Map

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.location.Location
import android.os.Looper
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.material3.FloatingActionButton
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
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
import com.example.project250311.Map.data.CustomPoint
import com.example.project250311.Map.data.SEEntrances
import com.example.project250311.Map.IndoorMap.Database.IndoorMapDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.project250311.Map.IndoorMap.IndoorPositioningViewModel
import androidx.compose.material.icons.filled.Domain
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff

enum class MarkerFilterState {
    ALL,          // 都顯示
    CLASSROOMS,   // 只顯示教室
    BUILDINGS,    // 只顯示建築
    NONE          // 都不顯示
}

@SuppressLint("MissingPermission")
@Composable
fun MapScreen(navController: NavHostController) {
    val context = LocalContext.current
    val fusedClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    val scope = rememberCoroutineScope()

    // 取得室內定位 ViewModel
    val indoorViewModel: IndoorPositioningViewModel = viewModel()
    // 觀察是否掃到室內 Wi-Fi
    val isLikelyIndoors by indoorViewModel.isLikelyIndoors.collectAsState()

    // 2. 初始鏡頭：校園中心
    val defaultLatLng = LatLng(22.7366, 121.0675)
    val cameraState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(defaultLatLng, 15f)
    }

    // 虛線樣式
    val dashPattern = listOf<PatternItem>(Dot(), Gap(10f))

    // 3. 狀態變數
    var permissionGranted by remember { mutableStateOf(false) }
    var currentLoc by remember { mutableStateOf<LatLng?>(null) }
    var lastRerouteLoc by remember { mutableStateOf<LatLng?>(null) } // 上次重新路線用的位置
    var destination by remember { mutableStateOf<LatLng?>(null) }
    var routePoints by remember { mutableStateOf<List<LatLng>>(emptyList()) }
    var isRouting by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var selectedPoint by remember { mutableStateOf<CustomPoint?>(null) }
    var travelTimeText by remember { mutableStateOf<String?>(null) }
    // 室內地圖顯示狀態與資源 id (0 = 無)
    var indoorResId by remember { mutableStateOf(0) }
    var showIndoorMap by remember { mutableStateOf(false) }

    // Pending indoor navigation params (set after resolving classroom -> building/floor/entry)
    data class IndoorNavParams(val buildingId: String, val floorId: Int, val targetPointId: String, val entryPointId: String?)
    var pendingIndoorParams by remember { mutableStateOf<IndoorNavParams?>(null) }

    // 建立一個 state 來記住目前的過濾選項
    var markerFilterState by remember { mutableStateOf(MarkerFilterState.ALL) }

    // Search UI states
    var searchExpanded by remember { mutableStateOf(false) }
    var startText by remember { mutableStateOf("") }
    var destText by remember { mutableStateOf("") }
    var startSelection by remember { mutableStateOf<LatLng?>(null) }
    var destSelection by remember { mutableStateOf<LatLng?>(null) }
    var startExpanded by remember { mutableStateOf(false) }
    var destExpanded by remember { mutableStateOf(false) }

    // 4. 自訂地點列表（包含校園地點與理工教室）
    val seClassrooms = remember {
        com.example.project250311.Map.data.SEClassrooms.allClassrooms.sortedBy { it.name }
    }
    val campusBuildings = remember { CampusPoints.points }

    val customPoints = remember {
        // 將教室與校園地點合併，教室放前面以便在搜尋中先顯示
        seClassrooms + CampusPoints.points
    }

    // 根據 markerFilterState，「衍生」出真正要顯示的列表
    val filteredPoints by remember(markerFilterState, seClassrooms, campusBuildings) {
        derivedStateOf { // (★) derivedStateOf 會在 filter 變化時才重算，效能很好
            when (markerFilterState) {
                MarkerFilterState.ALL -> seClassrooms + campusBuildings // 都顯示
                MarkerFilterState.CLASSROOMS -> seClassrooms           // 只顯示教室
                MarkerFilterState.BUILDINGS -> campusBuildings          // 只顯示建築
                MarkerFilterState.NONE -> emptyList()                // 都不顯示
            }
        }
    }

    // 5. 申請定位權限
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> permissionGranted = granted }
    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            launcher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        } else {
            permissionGranted = true
        }
    }

    // 6. 設定持續定位，但不自動移動鏡頭
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

                    val indoorParams = pendingIndoorParams
                    // 2. 檢查室外導航的「目的地」(建築入口) 是否還在
                    val outdoorDest = destination

                    if (indoorParams != null && outdoorDest != null) {
                        // 3. 計算目前GPS位置與「建築入口」的距離
                        val distanceToEntrance = distanceBetween(newLatLng, outdoorDest)

                        // 4. (★) 檢查觸發條件：
                        //    (距離 < 50公尺 且 掃到了室內Wi-Fi)
                        if (distanceToEntrance < 10f && isLikelyIndoors) {
                            scope.launch {
                                // 執行導航！
                                val route = "indoor/${indoorParams.buildingId}/${indoorParams.floorId}/${indoorParams.targetPointId}/${indoorParams.entryPointId ?: ""}"
                                navController.navigate(route)

                                // (重要) 清除狀態，避免重複觸發
                                pendingIndoorParams = null
                                destination = null
                                routePoints = emptyList()
                                travelTimeText = null
                            }
                            return
                        }
                    }

                    // 只有使用者移動超過 20 公尺時才重新路線
                    destination?.let { dest ->
                        val prev = lastRerouteLoc
                        if (prev == null || distanceBetween(prev, newLatLng) > 20f) {
                            lastRerouteLoc = newLatLng
                            drawRoute(
                                origin = newLatLng,
                                dest = dest,
                                onStart = { isRouting = true },
                                onSuccess = { points -> isRouting = false; routePoints = points },
                                onTime = { timeText -> travelTimeText = timeText },
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
    LaunchedEffect(permissionGranted) {
        if (permissionGranted) fusedClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
    }
    DisposableEffect(Unit) { onDispose { fusedClient.removeLocationUpdates(locationCallback) } }

    // 7. 初次定位
    LaunchedEffect(permissionGranted) {
        if (permissionGranted) {
            fusedClient.lastLocation
                .addOnSuccessListener { loc: Location? ->
                    if (loc != null) {
                        val ll = LatLng(loc.latitude, loc.longitude)
                        currentLoc = ll
                        cameraState.move(CameraUpdateFactory.newLatLng(ll))
                    } else {
                        errorMsg = "無法取得目前位置"
                        scope.launch { delay(3000); errorMsg = null }
                    }
                }
                .addOnFailureListener {
                    errorMsg = "定位失敗：${it.message}"
                    scope.launch { delay(3000); errorMsg = null }
                }
        }
    }

    // Helper: 由 classroom 名稱嘗試取得 drawable 資源 id（使用多種命名猜測）
    fun findIndoorMapResId(context: Context, pointName: String): Int {
        val normalized = pointName.lowercase().replace(Regex("[^a-z0-9]"), "")
        val candidates = listOf("se_$normalized", normalized, "classroom_$normalized")
        for (c in candidates) {
            val id = context.resources.getIdentifier(c, "drawable", context.packageName)
            if (id != 0) return id
        }
        return 0
    }

    // 8. Map 畫面
    Box(modifier = Modifier.fillMaxSize()) {
        // 預先在背景建立並快取 Marker BitmapDescriptor，避免在 Compose 組合階段重複同步 decode/scale
        // (★) 預先「同步」建立並快取 Marker，確保只執行一次
        // (★) 1. 建立兩個「空的」狀態來存放圖示
        var largeMarkerIcon by remember { mutableStateOf<com.google.android.gms.maps.model.BitmapDescriptor?>(null) }
        var smallMarkerIcon by remember { mutableStateOf<com.google.android.gms.maps.model.BitmapDescriptor?>(null) }

        GoogleMap(
            modifier = Modifier.matchParentSize(),
            cameraPositionState = cameraState,
            properties = MapProperties(isMyLocationEnabled = permissionGranted),
            onMapClick = { latLng ->
                destination = latLng
                routePoints = emptyList()
                lastRerouteLoc = currentLoc
                travelTimeText = null
                drawRoute(
                    origin = currentLoc,
                    dest = latLng,
                    onStart = { isRouting = true },
                    onSuccess = { points -> isRouting = false; routePoints = points },
                    onTime = { timeText -> travelTimeText = timeText },
                    onError = { isRouting = false; errorMsg = it; scope.launch { delay(3000); errorMsg = null } }
                )
                selectedPoint = null
            },
            // (★) 2. 加入 onMapLoaded 回呼
            onMapLoaded = {
                // (★) 3. 在地圖載入後，才建立圖示
                //    (這可以保證 BitmapDescriptorFactory 已經準備好了)
                if (largeMarkerIcon == null || smallMarkerIcon == null) {
                    scope.launch(Dispatchers.Default) {
                        try {
                            val raw = BitmapFactory.decodeResource(context.resources, R.drawable.marker)
                            val bmp120 = android.graphics.Bitmap.createScaledBitmap(raw, 120, 120, true)
                            val bmp80 = android.graphics.Bitmap.createScaledBitmap(raw, 80, 80, true)
                            // (★) 4. 在背景解碼/縮放，然後回主線程設定狀態
                            withContext(Dispatchers.Main) {
                                largeMarkerIcon = com.google.android.gms.maps.model.BitmapDescriptorFactory.fromBitmap(bmp120)
                                smallMarkerIcon = com.google.android.gms.maps.model.BitmapDescriptorFactory.fromBitmap(bmp80)
                            }
                        } catch (e: Exception) {
                            Log.e("MapScreen", "建立 Marker 圖示失敗", e)
                        }
                    }
                }
            }
        ) {
            // A. 顯示「你的位置」Marker
            currentLoc?.let {
                // (★) 5. 直接使用 state 變數 (null 也沒關係，地圖會用預設圖)
                Marker(state = MarkerState(it), title = "你的位置", icon = largeMarkerIcon)
            }

            // B. 顯示「目的地」Marker
            destination?.let { destLatLng ->
                Marker(
                    state = MarkerState(destLatLng),
                    title = "目的地",
                    // (★) 5. 直接使用 state 變數
                    icon = largeMarkerIcon,
                    onClick = {
                        destination = null
                        routePoints = emptyList()
                        travelTimeText = null
                        true
                    }
                )
            }

            // C. 顯示自訂地點 Marker
            filteredPoints.forEach { custom ->
                Marker(
                    state = MarkerState(custom.location),
                    title = custom.name,
                    // (★) 5. 直接使用 state 變數
                    icon = smallMarkerIcon,
                    onClick = {
                        selectedPoint = custom
                        if (custom.name.startsWith("se", true)) {
                            indoorResId = findIndoorMapResId(context, custom.name)
                        } else {
                            indoorResId = 0
                        }
                        false
                    }
                )
            }

            // D. 畫三段 Polyline：灰色虛線 + 藍色實線 + 灰色虛線
            if (currentLoc != null && routePoints.isNotEmpty()) {
                val firstOnRoad = routePoints.first()
                Polyline(points = listOf(currentLoc!!, firstOnRoad), width = 6f, color = Color.Gray, pattern = dashPattern)
            }
            if (routePoints.isNotEmpty()) Polyline(points = routePoints, width = 12f, color = Color.Blue)
            if (routePoints.isNotEmpty() && destination != null) {
                val lastOnRoad = routePoints.last()
                Polyline(points = listOf(lastOnRoad, destination!!), width = 6f, color = Color.Gray, pattern = dashPattern)
            }
        }


        // 在 project250311.zip/Map/MapScreen.kt 中

        Box(
            modifier = Modifier
                .align(Alignment.BottomStart) // (★) 在左下角
                .padding(bottom = 16.dp, start = 12.dp) // (★) 調整 padding
                .zIndex(3f)
        ) {
            // 讓 when 直接回傳 ImageVector
            val icon = when (markerFilterState) {
                MarkerFilterState.ALL -> Icons.Default.Visibility
                MarkerFilterState.CLASSROOMS -> Icons.Default.School
                MarkerFilterState.BUILDINGS -> Icons.Default.Domain
                MarkerFilterState.NONE -> Icons.Default.VisibilityOff
            }

            // (★) 2. (推薦) 也為無障礙功能提供動態的描述
            val description = when (markerFilterState) {
                MarkerFilterState.ALL -> "篩選器：顯示全部"
                MarkerFilterState.CLASSROOMS -> "篩選器：只顯示教室"
                MarkerFilterState.BUILDINGS -> "篩選器：只顯示建築"
                MarkerFilterState.NONE -> "篩選器：全部隱藏"
            }

            // (★) 3. 使用 FloatingActionButton (不是 Extended)
            FloatingActionButton(
                onClick = {
                    // (★) 點擊時，切換到下一個狀態
                    markerFilterState = when (markerFilterState) {
                        MarkerFilterState.ALL -> MarkerFilterState.CLASSROOMS
                        MarkerFilterState.CLASSROOMS -> MarkerFilterState.BUILDINGS
                        MarkerFilterState.BUILDINGS -> MarkerFilterState.NONE
                        MarkerFilterState.NONE -> MarkerFilterState.ALL
                    }
                },
                containerColor = MaterialTheme.colorScheme.surface, // (用淺色底)
                contentColor = MaterialTheme.colorScheme.primary // (用主色字)
            ) {
                // (★) 4. 傳入圖示和動態描述
                Icon(icon, contentDescription = description)
            }
        }

        Column(modifier = Modifier.fillMaxWidth().padding(12.dp).zIndex(2f)) {

            // 搜尋卡（加上 zIndex 確保置頂）
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .zIndex(2f),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF0F5).copy(alpha = 0.95f))
            ) {
                Column(modifier = Modifier.padding(8.dp)) {

                    // 標題 + 箭頭
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(text = if (searchExpanded) "搜尋目的地" else "點擊展開搜尋", style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.weight(1f))
                        IconButton(onClick = { searchExpanded = !searchExpanded }) {
                            Icon(imageVector = if (searchExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, contentDescription = "收合搜尋欄")
                        }
                    }

                    // 內容動畫
                    AnimatedVisibility(
                        visible = searchExpanded,
                        enter = expandVertically(animationSpec = tween(300)),
                        exit = shrinkVertically(animationSpec = tween(300))
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {

                            // ---- 起點欄位 ----
                            var startFieldWidth by remember { mutableStateOf(0) }
                            Box {
                                OutlinedTextField(
                                    value = startText,
                                    onValueChange = {
                                        startText = it
                                        startSelection = null
                                        startExpanded = it.isNotBlank()
                                        customPoints.firstOrNull { cp -> cp.name.equals(it, true) }?.let { cp -> startSelection = cp.location }
                                    },
                                    placeholder = { Text("起點") },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(54.dp)
                                        .clickable { startExpanded = true }
                                        .onGloballyPositioned { coordinates -> startFieldWidth = coordinates.size.width },
                                    singleLine = true,
                                    leadingIcon = { Icon(Icons.Default.Place, contentDescription = "起點", tint = Color(0xFFFF69B4)) },
                                    shape = RoundedCornerShape(16.dp)
                                )

                                // 替換 DropdownMenu -> 直接在下方顯示可捲動清單
                                if (startExpanded) {
                                    Card(
                                        modifier = Modifier
                                            .width(with(LocalDensity.current) { startFieldWidth.toDp() })
                                            .padding(top = 55.dp)
                                            .heightIn(max = 200.dp)   // 當建議過多時就會捲動，不會超過高度
                                            .zIndex(3f),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF0F5))
                                    ) {
                                        val startSuggestions = if (startText.isBlank()) customPoints else customPoints.filter { it.name.contains(startText, true) }
                                        LazyColumn {
                                            item {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clickable {
                                                            startText = "目前位置"
                                                            startSelection = null
                                                            startExpanded = false
                                                        }
                                                        .padding(horizontal = 12.dp, vertical = 10.dp)
                                                ) {
                                                    Text(text = "目前位置")
                                                }
                                            }
                                            items(startSuggestions) { cp ->
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clickable {
                                                            startText = cp.name
                                                            startSelection = cp.location
                                                            startExpanded = false
                                                        }
                                                        .padding(horizontal = 12.dp, vertical = 10.dp)
                                                ) {
                                                    Text(text = cp.name)
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            // ---- 目的地欄位 ----
                            var destFieldWidth by remember { mutableStateOf(0) }
                            Box {
                                OutlinedTextField(
                                    value = destText,
                                    onValueChange = {
                                        destText = it
                                        destSelection = null
                                        destExpanded = true // 總是顯示建議列表
                                        customPoints.firstOrNull { cp -> cp.name.equals(it, true) }?.let { cp -> destSelection = cp.location }
                                    },
                                    placeholder = { Text("輸入教室編號或地點名稱（如：SEC101、體育館）") },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(54.dp)
                                        .clickable { destExpanded = true }
                                        .onGloballyPositioned { coordinates -> destFieldWidth = coordinates.size.width },
                                    singleLine = true,
                                    leadingIcon = { Icon(Icons.Default.Place, contentDescription = "目的地", tint = Color(0xFF87CEFA)) },
                                    shape = RoundedCornerShape(16.dp)
                                )

                                // 替換 DropdownMenu -> 直接在下方顯示可捲動清單
                                if (destExpanded) {
                                    Card(
                                        modifier = Modifier
                                            .width(with(LocalDensity.current) { destFieldWidth.toDp() })
                                            .padding(top = 55.dp)
                                            .heightIn(max = 300.dp)
                                            .zIndex(3f),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFF87CEFA).copy(alpha = 0.15f))
                                    ) {
                                        // 分離教室和其他地點
                                        val locations = customPoints.filter { !it.name.startsWith("se", true) }
                                        val classrooms = customPoints.filter { it.name.startsWith("se", true) }

                                        // 過濾搜尋結果
                                        val filteredLocations = if (destText.isBlank()) locations else {
                                            locations.filter { 
                                                it.name.contains(destText, true) || 
                                                it.description.contains(destText, true) 
                                            }
                                        }
                                        val filteredClassrooms = if (destText.isBlank()) classrooms else {
                                            classrooms.filter {
                                                it.name.contains(destText, true) ||
                                                it.description.contains(destText, true)
                                            }
                                        }

                                        LazyColumn {
                                            // 顯示教室
                                            if (filteredClassrooms.isNotEmpty()) {
                                                item {
                                                    Text(
                                                        text = "教室清單",
                                                        style = MaterialTheme.typography.labelMedium,
                                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                                        color = Color.Gray
                                                    )
                                                }
                                                items(filteredClassrooms) { cp ->
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .clickable {
                                                                destText = cp.name.uppercase()
                                                                destSelection = cp.location
                                                                destExpanded = false
                                                            }
                                                            .padding(horizontal = 12.dp, vertical = 10.dp)
                                                    ) {
                                                        Text(
                                                            text = cp.name.uppercase(),
                                                            style = MaterialTheme.typography.bodyMedium
                                                        )
                                                    }
                                                }
                                            }

                                            // 顯示其他地點
                                            if (filteredLocations.isNotEmpty()) {
                                                item {
                                                    if (filteredClassrooms.isNotEmpty()) {
                                                        Divider(
                                                            modifier = Modifier.padding(vertical = 8.dp),
                                                            color = Color.Gray.copy(alpha = 0.3f)
                                                        )
                                                    }
                                                    Text(
                                                        text = "校園地點",
                                                        style = MaterialTheme.typography.labelMedium,
                                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                                        color = Color.Gray
                                                    )
                                                }
                                                items(filteredLocations) { cp ->
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .clickable {
                                                                destText = cp.name
                                                                destSelection = cp.location
                                                                destExpanded = false
                                                            }
                                                            .padding(horizontal = 12.dp, vertical = 10.dp)
                                                    ) {
                                                        Text(
                                                            text = cp.name,
                                                            style = MaterialTheme.typography.bodyMedium
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // 清除與導航按鈕
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                TextButton(
                                    onClick = {
                                        startText = ""
                                        destText = ""
                                        startSelection = null
                                        destSelection = null
                                        destination = null
                                        routePoints = emptyList()
                                        travelTimeText = null
                                    },
                                    modifier = Modifier.height(35.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                ) { Text("清除", style = MaterialTheme.typography.labelSmall) }

                                Spacer(modifier = Modifier.width(8.dp))

                                Button(
                                    onClick = {
                                        val originLatLng: LatLng? = when {
                                            startSelection != null -> startSelection
                                            startText.equals("目前位置", true) -> currentLoc
                                            startText.isNotBlank() -> customPoints.firstOrNull { it.name.equals(startText, true) }?.location
                                            else -> currentLoc
                                        }

                                        val destLatLng: LatLng? = destSelection ?: customPoints.firstOrNull { it.name.equals(destText, true) }?.location

                                        if (originLatLng == null) {
                                            errorMsg = "找不到起點位置（請確認輸入或開啟定位）"
                                            scope.launch { delay(3000); errorMsg = null }
                                            return@Button
                                        }
                                        if (destLatLng == null) {
                                            errorMsg = "請選擇有效的目的地（請從建議列表選擇）"
                                            scope.launch { delay(3000); errorMsg = null }
                                            return@Button
                                        }

                                        destination = destLatLng
                                        routePoints = emptyList()
                                        lastRerouteLoc = originLatLng
                                        travelTimeText = null

                                        // 檢查是否能直接在室內進行導航（當起點與目的地皆為教室）
                                        scope.launch {
                                            try {
                                                val db = IndoorMapDatabase.getDatabase(context)
                                                val refDao = db.referencePointDao()

                                                // 嘗試用名稱搜尋目的地與起點（若使用者有輸入教室名稱）
                                                val destMatches = if (destText.isNotBlank()) refDao.searchReferencePointsByName("%${destText}%").first() else emptyList()
                                                val destRef = destMatches.firstOrNull()

                                                val originRef = if (startText.isNotBlank() && !startText.equals("目前位置", true)) {
                                                    val oMatches = refDao.searchReferencePointsByName("%${startText}%").first()
                                                    oMatches.firstOrNull()
                                                } else null

                                                // 若起點與目的地都能對到室內參考點，且在同一張圖（同一樓層/建築），則直接進入室內導航
                                                if (originRef != null && destRef != null && originRef.buildingId == destRef.buildingId && originRef.floorId == destRef.floorId) {
                                                    // 使用起點教室作為 entryPointId，目的地教室作為 targetPointId
                                                    val route = "indoor/${destRef.buildingId}/${destRef.floorId}/${destRef.id}/${originRef.id}"
                                                    // 導航至室內畫面（不要再畫戶外路線）
                                                    navController.navigate(route)
                                                    return@launch
                                                }

                                                // 否則：原流程，先畫戶外路線到目的地；若目的地為教室，再嘗試找到入口並設定 pendingIndoorParams
                                                drawRoute(
                                                    origin = originLatLng, dest = destLatLng,
                                                    onStart = { isRouting = true },
                                                    onSuccess = { points -> isRouting = false; routePoints = points },
                                                    onTime = { timeText -> travelTimeText = timeText },
                                                    onError = { isRouting = false; errorMsg = it; scope.launch { delay(3000); errorMsg = null } }
                                                )

                                                cameraState.move(CameraUpdateFactory.newLatLng(originLatLng))

                                                if (destRef != null) {
                                                    val buildingId = destRef.buildingId
                                                    val floorId = destRef.floorId
                                                    val floorPoints = refDao.getReferencePointsByFloor(buildingId, floorId).first()
                                                    val entranceEntity = floorPoints.firstOrNull { it.type.equals("ENTRANCE", true) }
                                                    val entranceLatLng = SEEntrances.getNearestEntrance(destRef.name).location

                                                    // 把導航目的地改為入口
                                                    destination = entranceLatLng
                                                    lastRerouteLoc = originLatLng
                                                    travelTimeText = null
                                                    drawRoute(
                                                        origin = originLatLng,
                                                        dest = entranceLatLng,
                                                        onStart = { isRouting = true },
                                                        onSuccess = { points -> isRouting = false; routePoints = points },
                                                        onTime = { timeText -> travelTimeText = timeText },
                                                        onError = { isRouting = false; errorMsg = it; scope.launch { delay(3000); errorMsg = null } }
                                                    )

                                                    // 設定待進入室內導航的參數（entryPointId 使用 entranceEntity?.id 或 null）
                                                    pendingIndoorParams = IndoorNavParams(
                                                        buildingId = buildingId,
                                                        floorId = floorId,
                                                        targetPointId = destRef.id,
                                                        entryPointId = entranceEntity?.id
                                                    )
                                                }
                                            } catch (e: Exception) {
                                                // 不阻斷主流程，只顯示錯誤
                                                errorMsg = "解析室內教室資料失敗：${e.localizedMessage}"
                                                scope.launch { delay(3000); errorMsg = null }
                                                // 若發生錯誤，回退到一般的戶外路線畫法以免無路徑
                                                drawRoute(
                                                    origin = originLatLng, dest = destLatLng,
                                                    onStart = { isRouting = true },
                                                    onSuccess = { points -> isRouting = false; routePoints = points },
                                                    onTime = { timeText -> travelTimeText = timeText },
                                                    onError = { isRouting = false; errorMsg = it; scope.launch { delay(3000); errorMsg = null } }
                                                )
                                                cameraState.move(CameraUpdateFactory.newLatLng(originLatLng))
                                            }
                                        }

                                        // 如果目的地為教室，嘗試找室內圖
                                        val cp = customPoints.firstOrNull { it.location == destLatLng }
                                        indoorResId = if (cp != null && cp.name.startsWith("se", true)) findIndoorMapResId(context, cp.name) else 0
                                    },
                                    modifier = Modifier.height(35.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                ) { Text("導航", style = MaterialTheme.typography.labelSmall) }
                            }
                        }
                    }
                }
            }
        }



        // 顯示「路線計算中」圓形指示器
        if (isRouting) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center).size(48.dp).background(Color.White.copy(alpha = 0.6f), shape = MaterialTheme.shapes.small).padding(8.dp))
        }

        // 顯示費時文字於底部
        travelTimeText?.let { text ->
            Box(modifier = Modifier.fillMaxWidth().padding(12.dp).background(Color.White.copy(alpha = 0.8f), shape = MaterialTheme.shapes.medium).align(Alignment.BottomCenter).padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(text = "預計花費：$text", style = MaterialTheme.typography.bodyMedium, color = Color.DarkGray)
            }
        }

        // 如果已解析到室內導航參數，顯示進入室內導航按鈕（會將使用者帶到 IndoorMapScreen）
        pendingIndoorParams?.let { params ->
            Box(modifier = Modifier.fillMaxSize()) {
                Card(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(12.dp)
                        .zIndex(3f),
                    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.95f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "導航至建築入口已就緒", style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.weight(1f))
                        TextButton(onClick = {
                            // 透過 navController 導向室內地圖；路由格式為 indoor/{building}/{floor}/{target}/{entry}
                            try {
                                val route = "indoor/${params.buildingId}/${params.floorId}/${params.targetPointId}/${params.entryPointId ?: ""}"
                                navController.navigate(route)
                            } catch (e: Exception) {
                                errorMsg = "開啟室內導航失敗：${e.localizedMessage}"
                                scope.launch { delay(3000); errorMsg = null }
                            }
                        }) { Text("進入室內導航") }
                    }
                }
            }
        }

        // 顯示錯誤訊息（三秒後自動消失）
        AnimatedVisibility(visible = errorMsg != null, enter = fadeIn(tween(300)) + slideInVertically(initialOffsetY = { -100 }, animationSpec = tween(300)), exit = fadeOut(tween(300)) + slideOutVertically(targetOffsetY = { -100 }, animationSpec = tween(300))) {
            Box(modifier = Modifier.fillMaxWidth().wrapContentSize(Alignment.TopCenter).padding(top = 16.dp)) {
                Box(modifier = Modifier.background(color = Color(0xFFFFCDD2), shape = MaterialTheme.shapes.large).padding(horizontal = 20.dp, vertical = 12.dp)) {
                    Text(text = errorMsg ?: "", color = Color.DarkGray, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                }
            }
        }

        // 9. 顯示自訂介紹卡片（改為顯示在下方）
        AnimatedVisibility(
            visible = selectedPoint != null,
            enter = slideInVertically(initialOffsetY = { it }, animationSpec = tween(300)) + fadeIn(tween(200)),
            exit = slideOutVertically(targetOffsetY = { it }, animationSpec = tween(300)) + fadeOut(tween(200))
        ) {
            // 把 Card 放在底部，並確保不會被地圖其他 overlay 完全覆蓋
            Box(modifier = Modifier.fillMaxSize()) {
                selectedPoint?.let { point ->
                    Card(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 24.dp)
                            .fillMaxWidth(0.94f)
                            .shadow(8.dp, shape = RoundedCornerShape(12.dp))
                            .zIndex(2f),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(text = point.name, style = MaterialTheme.typography.titleMedium)
                                Spacer(modifier = Modifier.weight(1f))
                                TextButton(onClick = { selectedPoint = null }) { Text("關閉") }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(text = point.description, style = MaterialTheme.typography.bodyMedium)
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                                TextButton(onClick = { selectedPoint = null }) { Text("取消") }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(onClick = {
                                    // 導航到該地點
                                    destination = point.location
                                    routePoints = emptyList()
                                    lastRerouteLoc = currentLoc
                                    travelTimeText = null
                                    drawRoute(origin = currentLoc, dest = point.location,
                                        onStart = { isRouting = true },
                                        onSuccess = { points -> isRouting = false; routePoints = points },
                                        onTime = { timeText -> travelTimeText = timeText },
                                        onError = { isRouting = false; errorMsg = it; scope.launch { delay(3000); errorMsg = null } })
                                    selectedPoint = null
                                    cameraState.move(CameraUpdateFactory.newLatLng(point.location))
                                    // 若選擇的點是教室則解析室內圖資源 id
                                    indoorResId = if (point.name.startsWith("se", true)) findIndoorMapResId(context, point.name) else 0
                                }) { Text("導航") }
                            }
                        }
                    }
                }
            }

            // 若目的地為教室且找到室內圖資源，顯示右下角按鈕以打開室內圖
            if (indoorResId != 0 && destination != null) {
                Box(modifier = Modifier.fillMaxSize()) {
                    FloatingActionButton(
                        onClick = {
                            // 若資源意外失敗，顯示錯誤訊息
                            if (indoorResId == 0) {
                                errorMsg = "找不到室內地圖圖片"
                                scope.launch { delay(3000); errorMsg = null }
                            } else {
                                showIndoorMap = true
                            }
                        },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(16.dp)
                            .zIndex(3f)
                    ) {
                        Icon(imageVector = Icons.Default.Place, contentDescription = "室內地圖")
                    }
                }
            }

            // 室內地圖對話框 (顯示 drawable 圖片)
            if (showIndoorMap) {
                androidx.compose.ui.window.Dialog(onDismissRequest = { showIndoorMap = false }) {
                    Card(modifier = Modifier
                        .fillMaxWidth(0.95f)
                        .wrapContentHeight()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(text = "室內地圖", style = MaterialTheme.typography.titleMedium)
                            Spacer(modifier = Modifier.height(8.dp))
                            // 若資源 id 有效，顯示圖片
                            if (indoorResId != 0) {
                                Image(
                                    painter = painterResource(id = indoorResId),
                                    contentDescription = "室內地圖圖片",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 400.dp),
                                    contentScale = ContentScale.Fit
                                )
                            } else {
                                Text(text = "無可用的室內地圖圖片", color = Color.Gray)
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                TextButton(onClick = { showIndoorMap = false }) { Text("關閉") }
                            }
                        }
                    }
                }
            }
        }
    }
}

// Utility：計算兩點距離（單位：公尺）
fun distanceBetween(a: LatLng, b: LatLng): Float {
    val result = FloatArray(1)
    Location.distanceBetween(a.latitude, a.longitude, b.latitude, b.longitude, result)
    return result[0]
}

// Utility：縮放 Bitmap 並回傳給 Marker 用
fun getResizedBitmapDescriptor(context: Context, resId: Int, width: Int, height: Int): com.google.android.gms.maps.model.BitmapDescriptor {
    val imageBitmap = BitmapFactory.decodeResource(context.resources, resId)
    val scaledBitmap = android.graphics.Bitmap.createScaledBitmap(imageBitmap, width, height, false)
    return com.google.android.gms.maps.model.BitmapDescriptorFactory.fromBitmap(scaledBitmap)
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
        onError("尚未取得目前位置")
        return
    }
    onStart()
    val o = "${origin.latitude},${origin.longitude}"
    val d = "${dest.latitude},${dest.longitude}"
    RetrofitInstance.api.getDirections(origin = o, destination = d, mode = "walking", apiKey = "AIzaSyDj1CTmLJMsvCTRwwVJrCFHp6Cqt7wVKp8")
        .enqueue(object : Callback<com.example.project250311.Map.model.DirectionsResponse> {
            override fun onResponse(call: Call<com.example.project250311.Map.model.DirectionsResponse>, response: Response<com.example.project250311.Map.model.DirectionsResponse>) {
                if (response.isSuccessful) {
                    val body = response.body()
                    val route = body?.routes?.firstOrNull()
                    val leg = route?.legs?.firstOrNull()
                    val points = route?.overview_polyline?.points

                    val durationText = leg?.duration?.text ?: "未知時間"
                    onTime(durationText)

                    if (!points.isNullOrEmpty()) {
                        onSuccess(PolylineUtils.decodePolyline(points))
                    } else {
                        onError("找不到路線")
                    }
                } else {
                    onError("API 回傳 ${response.code()}")
                }
            }

            override fun onFailure(call: Call<com.example.project250311.Map.model.DirectionsResponse>, t: Throwable) {
                onError("網路錯誤：${t.localizedMessage}")
            }
        })
}