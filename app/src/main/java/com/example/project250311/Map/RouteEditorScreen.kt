package com.example.project250311.Map

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.location.Location
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.example.project250311.Map.data.CustomPoint
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.google.maps.android.compose.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

// 自定義路線數據模型
data class CustomRoute(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String,
    val points: List<LatLng>,
    val color: Int = AndroidColor.BLUE,
    val estimatedTimeInMinutes: Int,
    val createdAt: Long = System.currentTimeMillis(),
    val lastModified: Long = System.currentTimeMillis()
)

// 路線管理器 - 處理路線保存和讀取
object RouteManager {
    private const val ROUTES_PREF_KEY = "custom_routes"
    
    fun saveRoute(context: Context, route: CustomRoute) {
        val sharedPrefs = context.getSharedPreferences("routes_prefs", Context.MODE_PRIVATE)
        val existingRoutesJson = sharedPrefs.getString(ROUTES_PREF_KEY, "[]")
        val gson = Gson()
        
        val type = object : TypeToken<MutableList<CustomRoute>>() {}.type
        val existingRoutes = gson.fromJson<MutableList<CustomRoute>>(existingRoutesJson, type) ?: mutableListOf()
        
        // 檢查是否為更新現有路線
        val existingIndex = existingRoutes.indexOfFirst { it.id == route.id }
        if (existingIndex >= 0) {
            existingRoutes[existingIndex] = route
        } else {
            existingRoutes.add(route)
        }
        
        val updatedJson = gson.toJson(existingRoutes)
        sharedPrefs.edit().putString(ROUTES_PREF_KEY, updatedJson).apply()
    }
    
    fun deleteRoute(context: Context, routeId: String) {
        val sharedPrefs = context.getSharedPreferences("routes_prefs", Context.MODE_PRIVATE)
        val existingRoutesJson = sharedPrefs.getString(ROUTES_PREF_KEY, "[]")
        val gson = Gson()
        
        val type = object : TypeToken<MutableList<CustomRoute>>() {}.type
        val existingRoutes = gson.fromJson<MutableList<CustomRoute>>(existingRoutesJson, type) ?: mutableListOf()
        
        val updatedRoutes = existingRoutes.filter { it.id != routeId }
        val updatedJson = gson.toJson(updatedRoutes)
        sharedPrefs.edit().putString(ROUTES_PREF_KEY, updatedJson).apply()
    }
    
    fun getAllRoutes(context: Context): List<CustomRoute> {
        val sharedPrefs = context.getSharedPreferences("routes_prefs", Context.MODE_PRIVATE)
        val routesJson = sharedPrefs.getString(ROUTES_PREF_KEY, "[]")
        val gson = Gson()
        
        val type = object : TypeToken<List<CustomRoute>>() {}.type
        return gson.fromJson(routesJson, type) ?: listOf()
    }
}

// 路線編輯器畫面
@SuppressLint("MissingPermission")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteEditorScreen(
    navController: NavController? = null
) {
    val context = LocalContext.current
    val fusedClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    
    // 狀態變數
    var permissionGranted by remember { mutableStateOf(false) }
    var currentLoc by remember { mutableStateOf<LatLng?>(null) }
    var editPoints by remember { mutableStateOf<List<LatLng>>(emptyList()) }
    var selectedPointIndex by remember { mutableStateOf<Int?>(null) }
    var isInAddMode by remember { mutableStateOf(true) } // true = 添加模式，false = 編輯模式
    var editingRoute by remember { mutableStateOf<CustomRoute?>(null) }
    var routeName by remember { mutableStateOf("") }
    var routeDescription by remember { mutableStateOf("") }
    var estimatedMinutes by remember { mutableStateOf("5") } // 預設為5分鐘
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var showSaveDialog by remember { mutableStateOf(false) }
    var showManageDialog by remember { mutableStateOf(false) }
    var allRoutes by remember { mutableStateOf<List<CustomRoute>>(emptyList()) }
    
    // 新增：顯示校園預定義點選擇對話框
    var showCampusPointsDialog by remember { mutableStateOf(false) }
    // 新增：選中的校園點
    var selectedCampusPoint by remember { mutableStateOf<CustomPoint?>(null) }
    // 新增：搜尋校園點的過濾文字
    var campusPointFilterText by remember { mutableStateOf("") }
    // 新增：過濾後的校園點列表
    val filteredCampusPoints = remember(campusPointFilterText) {
        if (campusPointFilterText.isBlank()) {
            CampusPoints.points
        } else {
            CampusPoints.points.filter { 
                it.name.contains(campusPointFilterText, ignoreCase = true) 
            }
        }
    }
    
    // 初始鏡頭：校園中心
    val defaultLatLng = LatLng(22.7366, 121.0675)
    val cameraState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(defaultLatLng, 15f)
    }
    
    // Snackbar狀態
    val snackbarHostState = remember { SnackbarHostState() }
    
    // 計算路線總長度
    fun calculateRouteLength(): Float {
        if (editPoints.size < 2) return 0f
        
        var totalDistance = 0f
        for (i in 0 until editPoints.size - 1) {
            val start = editPoints[i]
            val end = editPoints[i + 1]
            val results = FloatArray(1)
            Location.distanceBetween(start.latitude, start.longitude, end.latitude, end.longitude, results)
            totalDistance += results[0]
        }
        
        return totalDistance
    }
    
    // 添加路線點
    fun addPoint(latLng: LatLng) {
        if (isInAddMode) {
            editPoints = editPoints + latLng
        }
    }
    
    // 更新選中點的位置
    fun updateSelectedPoint(latLng: LatLng) {
        if (selectedPointIndex != null) {
            val updatedPoints = editPoints.toMutableList()
            updatedPoints[selectedPointIndex!!] = latLng
            editPoints = updatedPoints
        }
    }
    
    // 刪除選中點
    fun deleteSelectedPoint() {
        if (selectedPointIndex != null) {
            val updatedPoints = editPoints.toMutableList()
            updatedPoints.removeAt(selectedPointIndex!!)
            editPoints = updatedPoints
            selectedPointIndex = null
        }
    }
    
    // 保存路線
    fun saveRoute() {
        if (editPoints.size < 2) {
            scope.launch {
                snackbarHostState.showSnackbar("路線至少需要兩個點")
            }
            return
        }
        
        if (routeName.isBlank()) {
            scope.launch {
                snackbarHostState.showSnackbar("請輸入路線名稱")
            }
            return
        }
        
        val timeMinutes = estimatedMinutes.toIntOrNull() ?: 5
        
        val route = CustomRoute(
            id = editingRoute?.id ?: UUID.randomUUID().toString(),
            name = routeName,
            description = routeDescription,
            points = editPoints,
            estimatedTimeInMinutes = timeMinutes,
            lastModified = System.currentTimeMillis()
        )
        
        RouteManager.saveRoute(context, route)
        
        // 重置編輯狀態
        editingRoute = null
        editPoints = emptyList()
        routeName = ""
        routeDescription = ""
        estimatedMinutes = "5"
        selectedPointIndex = null
        
        // 重新載入路線列表
        allRoutes = RouteManager.getAllRoutes(context)
        
        // 顯示成功訊息
        scope.launch {
            snackbarHostState.showSnackbar("路線已保存")
        }
    }
    
    // 編輯現有路線
    fun editExistingRoute(route: CustomRoute) {
        editingRoute = route
        editPoints = route.points
        routeName = route.name
        routeDescription = route.description
        estimatedMinutes = route.estimatedTimeInMinutes.toString()
        showManageDialog = false
    }
    
    // 刪除路線
    fun deleteRoute(route: CustomRoute) {
        RouteManager.deleteRoute(context, route.id)
        allRoutes = RouteManager.getAllRoutes(context)
        
        // 如果正在編輯的是被刪除的路線，重置編輯狀態
        if (editingRoute?.id == route.id) {
            editingRoute = null
            editPoints = emptyList()
            routeName = ""
            routeDescription = ""
            estimatedMinutes = "5"
        }
        
        scope.launch {
            snackbarHostState.showSnackbar("路線已刪除")
        }
    }
    
    // 格式化創建時間
    fun formatTime(timestamp: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }
    
    // 重置編輯器
    fun resetEditor() {
        editingRoute = null
        editPoints = emptyList()
        routeName = ""
        routeDescription = ""
        estimatedMinutes = "5"
        selectedPointIndex = null
        isInAddMode = true
    }
    
    // 新增：添加選中的校園點到路線中
    fun addCampusPointToRoute(point: CustomPoint) {
        addPoint(point.location)
        scope.launch {
            snackbarHostState.showSnackbar("已添加 ${point.name} 到路線")
            // 移動地圖到該點
            cameraState.animate(CameraUpdateFactory.newLatLngZoom(point.location, 17f))
        }
    }
    
    // 申請定位權限
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> permissionGranted = granted }
    
    // 初始化
    LaunchedEffect(Unit) {
        // 載入已保存的路線
        allRoutes = RouteManager.getAllRoutes(context)
        
        // 檢查定位權限
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
    
    // 設定定位更新
    val locationRequest = remember {
        LocationRequest.create().apply {
            interval = 10000 // 10秒更新一次
            fastestInterval = 5000
            priority = Priority.PRIORITY_HIGH_ACCURACY
        }
    }
    
    val locationCallback = remember {
        object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { loc ->
                    currentLoc = LatLng(loc.latitude, loc.longitude)
                }
            }
        }
    }
    
    // 啟動定位更新
    LaunchedEffect(permissionGranted) {
        if (permissionGranted) {
            fusedClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
        }
    }
    
    // 清理定位資源
    DisposableEffect(Unit) {
        onDispose {
            fusedClient.removeLocationUpdates(locationCallback)
        }
    }
    
    // 主界面
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("路線編輯器") },
                navigationIcon = {
                    IconButton(onClick = {
                        navController?.navigateUp()
                    }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    // 新增：校園點按鈕
                    IconButton(onClick = {
                        showCampusPointsDialog = true
                        campusPointFilterText = ""  // 重置搜尋
                    }) {
                        Icon(Icons.Default.LocationOn, contentDescription = "選擇校園點")
                    }
                    
                    // 管理按鈕
                    IconButton(onClick = {
                        showManageDialog = true
                    }) {
                        Icon(Icons.Default.List, contentDescription = "管理路線")
                    }
                    
                    // 新增按鈕
                    IconButton(onClick = {
                        resetEditor()
                    }) {
                        Icon(Icons.Default.Add, contentDescription = "新增路線")
                    }
                    
                    // 保存按鈕
                    IconButton(onClick = {
                        showSaveDialog = true
                    }) {
                        Icon(Icons.Default.Save, contentDescription = "保存路線")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // 地圖
            GoogleMap(
                modifier = Modifier.matchParentSize(),
                cameraPositionState = cameraState,
                properties = MapProperties(isMyLocationEnabled = permissionGranted),
                onMapClick = { latLng ->
                    if (selectedPointIndex == null) {
                        // 添加新點
                        addPoint(latLng)
                    } else {
                        // 更新選中點的位置
                        updateSelectedPoint(latLng)
                        selectedPointIndex = null
                    }
                }
            ) {
                // 繪製當前編輯的路線
                if (editPoints.size > 1) {
                    Polyline(
                        points = editPoints,
                        width = 8f,
                        color = Color.Blue
                    )
                }
                
                // 繪製路線點
                editPoints.forEachIndexed { index, point ->
                    val isSelected = index == selectedPointIndex
                    
                    Marker(
                        state = MarkerState(point),
                        icon = BitmapDescriptorFactory.defaultMarker(
                            if (isSelected) BitmapDescriptorFactory.HUE_GREEN
                            else BitmapDescriptorFactory.HUE_AZURE
                        ),
                        title = "點 ${index + 1}",
                        onClick = {
                            selectedPointIndex = index
                            true
                        }
                    )
                }
                
                // 起點和終點特殊標記
                if (editPoints.size >= 2) {
                    // 起點
                    Marker(
                        state = MarkerState(editPoints.first()),
                        title = "起點",
                        icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN),
                        zIndex = 2f
                    )
                    
                    // 終點
                    Marker(
                        state = MarkerState(editPoints.last()),
                        title = "終點",
                        icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED),
                        zIndex = 2f
                    )
                }
                
                // 新增：顯示所有校園點（較淡的顏色）
                CampusPoints.points.forEach { point ->
                    Marker(
                        state = MarkerState(point.location),
                        title = point.name,
                        snippet = point.description,
                        icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_CYAN),
                        alpha = 0.6f,  // 半透明
                        onClick = {
                            if (isInAddMode) {
                                addCampusPointToRoute(point)
                                true
                            } else {
                                false
                            }
                        }
                    )
                }
            }
            
            // 工具欄
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(Color.White)
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 路線資訊
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    val routeLength = calculateRouteLength()
                    Text(
                        text = "${editPoints.size} 個點 | ${String.format("%.1f", routeLength)} 公尺",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = if (editingRoute != null) "編輯: ${editingRoute?.name}" else "新建路線",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
                
                // 新增：選擇校園點按鈕
                Button(
                    onClick = { 
                        showCampusPointsDialog = true 
                        campusPointFilterText = ""  // 重置搜尋
                    },
                    modifier = Modifier.padding(horizontal = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = "選擇校園點",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("選擇點")
                }
                
                // 模式切換
                Switch(
                    checked = isInAddMode,
                    onCheckedChange = {
                        isInAddMode = it
                        selectedPointIndex = null
                    },
                    thumbContent = {
                        Icon(
                            imageVector = if (isInAddMode) Icons.Default.Add else Icons.Default.Edit,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
                
                // 動作按鈕
                if (selectedPointIndex != null) {
                    IconButton(onClick = {
                        deleteSelectedPoint()
                    }) {
                        Icon(Icons.Default.Delete, contentDescription = "刪除點", tint = Color.Red)
                    }
                    
                    IconButton(onClick = {
                        selectedPointIndex = null
                    }) {
                        Icon(Icons.Default.Close, contentDescription = "取消選擇")
                    }
                } else {
                    IconButton(onClick = {
                        if (editPoints.isNotEmpty()) {
                            val updatedPoints = editPoints.dropLast(1)
                            editPoints = updatedPoints
                        }
                    }) {
                        Icon(Icons.Default.Undo, contentDescription = "撤銷")
                    }
                    
                    IconButton(onClick = {
                        showSaveDialog = true
                    }) {
                        Icon(Icons.Default.Save, contentDescription = "保存")
                    }
                }
            }
            
            // 模式提示
            Card(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isInAddMode) Color(0xFFE0F7FA) else Color(0xFFF3E5F5)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    modifier = Modifier.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = if (isInAddMode) Icons.Default.Add else Icons.Default.Edit,
                        contentDescription = null,
                        tint = if (isInAddMode) Color.Blue else Color.Purple700
                    )
                    Text(
                        text = if (isInAddMode) "添加模式：點擊地圖或預定義點" else "編輯模式：選擇點後移動或刪除",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isInAddMode) Color.Blue else Color.Purple700
                    )
                }
            }
            
            // 新增：校園點選擇對話框
            if (showCampusPointsDialog) {
                Dialog(onDismissRequest = { showCampusPointsDialog = false }) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 500.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                text = "選擇校園點",
                                style = MaterialTheme.typography.titleLarge,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            
                            // 搜尋框
                            OutlinedTextField(
                                value = campusPointFilterText,
                                onValueChange = { campusPointFilterText = it },
                                label = { Text("搜尋") },
                                modifier = Modifier.fillMaxWidth(),
                                leadingIcon = {
                                    Icon(Icons.Default.Search, contentDescription = "搜尋")
                                },
                                trailingIcon = {
                                    if (campusPointFilterText.isNotEmpty()) {
                                        IconButton(onClick = { campusPointFilterText = "" }) {
                                            Icon(Icons.Default.Clear, contentDescription = "清除")
                                        }
                                    }
                                },
                                singleLine = true
                            )
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            // 校園點列表
                            LazyColumn(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                            ) {
                                items(filteredCampusPoints) { point ->
                                    ListItem(
                                        headlineContent = { Text(point.name) },
                                        supportingContent = { 
                                            Text(
                                                point.description,
                                                maxLines = 1,
                                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                            ) 
                                        },
                                        leadingContent = {
                                            Box(
                                                modifier = Modifier
                                                    .size(32.dp)
                                                    .background(Color.Cyan, CircleShape)
                                                    .border(1.dp, Color.Blue, CircleShape),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    Icons.Default.LocationOn,
                                                    contentDescription = null,
                                                    tint = Color.White
                                                )
                                            }
                                        },
                                        trailingContent = {
                                            Button(
                                                onClick = {
                                                    addCampusPointToRoute(point)
                                                    showCampusPointsDialog = false
                                                },
                                                modifier = Modifier.height(36.dp)
                                            ) {
                                                Text("添加")
                                            }
                                        },
                                        modifier = Modifier
                                            .clickable {
                                                scope.launch {
                                                    cameraState.animate(CameraUpdateFactory.newLatLngZoom(point.location, 17f))
                                                }
                                                selectedCampusPoint = point
                                            }
                                            .padding(vertical = 4.dp)
                                    )
                                    
                                    if (filteredCampusPoints.indexOf(point) < filteredCampusPoints.size - 1) {
                                        Divider()
                                    }
                                }
                            }
                            
                            // 底部按鈕
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 16.dp),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(onClick = {
                                    showCampusPointsDialog = false
                                }) {
                                    Text("取消")
                                }
                                
                                Spacer(modifier = Modifier.width(8.dp))

                                val point = selectedCampusPoint

                                // 使用 if 判斷式來有條件地顯示 Composable
                                if (point != null) {
                                    Button(onClick = {
                                        addCampusPointToRoute(point)
                                        showCampusPointsDialog = false
                                    }) {
                                        Text("添加選中點")
                                    }
                                }
                            }
                        }
                    }
                }
            }
            
            // 保存對話框
            if (showSaveDialog) {
                Dialog(onDismissRequest = { showSaveDialog = false }) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = if (editingRoute != null) "編輯路線" else "保存路線",
                                style = MaterialTheme.typography.titleLarge
                            )
                            
                            OutlinedTextField(
                                value = routeName,
                                onValueChange = { routeName = it },
                                label = { Text("路線名稱") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            
                            OutlinedTextField(
                                value = routeDescription,
                                onValueChange = { routeDescription = it },
                                label = { Text("描述") },
                                modifier = Modifier.fillMaxWidth(),
                                maxLines = 3
                            )
                            
                            OutlinedTextField(
                                value = estimatedMinutes,
                                onValueChange = { 
                                    if (it.isEmpty() || it.matches(Regex("^\\d+$"))) {
                                        estimatedMinutes = it
                                    }
                                },
                                label = { Text("預計時間 (分鐘)") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions.Default.copy(
                                    imeAction = ImeAction.Done
                                ),
                                keyboardActions = KeyboardActions(
                                    onDone = { focusManager.clearFocus() }
                                )
                            )
                            
                            // 路線資訊
                            val routeLength = calculateRouteLength()
                            Text(
                                text = "路線長度：${String.format("%.1f", routeLength)} 公尺",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "點數：${editPoints.size}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(onClick = {
                                    showSaveDialog = false
                                }) {
                                    Text("取消")
                                }
                                
                                Spacer(modifier = Modifier.width(8.dp))
                                
                                Button(onClick = {
                                    saveRoute()
                                    showSaveDialog = false
                                }) {
                                    Text("保存")
                                }
                            }
                        }
                    }
                }
            }
            
            // 路線管理對話框
            if (showManageDialog) {
                Dialog(onDismissRequest = { showManageDialog = false }) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 500.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "管理路線",
                                style = MaterialTheme.typography.titleLarge,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            
                            if (allRoutes.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(100.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "尚未創建任何路線",
                                        color = Color.Gray,
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier.weight(1f)
                                ) {
                                    items(allRoutes) { route ->
                                        Card(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 4.dp),
                                            colors = CardDefaults.cardColors(
                                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                                            )
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(12.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.Top
                                                ) {
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(
                                                            text = route.name,
                                                            style = MaterialTheme.typography.titleMedium
                                                        )
                                                        
                                                        Text(
                                                            text = "${route.points.size}個點 | ${route.estimatedTimeInMinutes}分鐘 | ${String.format("%.1f", calculateRouteLength(route.points))}公尺",
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = Color.Gray
                                                        )
                                                        
                                                        if (route.description.isNotEmpty()) {
                                                            Text(
                                                                text = route.description,
                                                                style = MaterialTheme.typography.bodyMedium,
                                                                modifier = Modifier.padding(top = 4.dp)
                                                            )
                                                        }
                                                        
                                                        Text(
                                                            text = "最後修改: ${formatTime(route.lastModified)}",
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = Color.Gray,
                                                            modifier = Modifier.padding(top = 4.dp)
                                                        )
                                                    }
                                                    
                                                    Row {
                                                        IconButton(onClick = {
                                                            editExistingRoute(route)
                                                        }) {
                                                            Icon(
                                                                imageVector = Icons.Default.Edit,
                                                                contentDescription = "編輯"
                                                            )
                                                        }
                                                        
                                                        IconButton(onClick = {
                                                            deleteRoute(route)
                                                        }) {
                                                            Icon(
                                                                imageVector = Icons.Default.Delete,
                                                                contentDescription = "刪除",
                                                                tint = Color.Red
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            
                            // 底部按鈕
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(onClick = {
                                    showManageDialog = false
                                }) {
                                    Text("關閉")
                                }
                            }
                        }
                    }
                }
            }
            
            // 錯誤訊息
            AnimatedVisibility(
                visible = errorMsg != null,
                enter = fadeIn(tween(300)) + slideInVertically(
                    initialOffsetY = { -100 },
                    animationSpec = tween(300)
                ),
                exit = fadeOut(tween(300)) + slideOutVertically(
                    targetOffsetY = { -100 },
                    animationSpec = tween(300)
                ),
                modifier = Modifier.align(Alignment.TopCenter)
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
        }
    }
}

// 計算路線長度的輔助函數
private fun calculateRouteLength(points: List<LatLng>): Float {
    if (points.size < 2) return 0f
    
    var totalDistance = 0f
    for (i in 0 until points.size - 1) {
        val start = points[i]
        val end = points[i + 1]
        val results = FloatArray(1)
        Location.distanceBetween(start.latitude, start.longitude, end.latitude, end.longitude, results)
        totalDistance += results[0]
    }
    
    return totalDistance
}

// 自定義顏色
val Color.Companion.Purple700: Color
    get() = Color(0xFF7B1FA2)
