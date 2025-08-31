package com.example.project250311.Map.IndoorMap

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.util.Log
import android.view.MotionEvent
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.zIndex
import androidx.navigation.NavController
import com.example.project250311.R
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.*

// 室內自定義路線數據模型
data class IndoorCustomRoute(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String,
    val points: List<ReferencePoint>,
    val color: Int = AndroidColor.BLUE,
    val estimatedTimeInMinutes: Int,
    val imageId: Int = R.drawable.se1,  // 默認為1樓地圖
    val createdAt: Long = System.currentTimeMillis(),
    val lastModified: Long = System.currentTimeMillis()
)

// 修改：室內路線管理器 - 確保可以被其他檔案訪問
object IndoorRouteManager {
    private const val ROUTES_PREF_KEY = "indoor_custom_routes"
    
    fun saveRoute(context: Context, route: IndoorCustomRoute) {
        val sharedPrefs = context.getSharedPreferences("indoor_routes_prefs", Context.MODE_PRIVATE)
        val existingRoutesJson = sharedPrefs.getString(ROUTES_PREF_KEY, "[]")
        val gson = Gson()
        
        val type = object : TypeToken<MutableList<IndoorCustomRoute>>() {}.type
        val existingRoutes = gson.fromJson<MutableList<IndoorCustomRoute>>(existingRoutesJson, type) ?: mutableListOf()
        
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
        val sharedPrefs = context.getSharedPreferences("indoor_routes_prefs", Context.MODE_PRIVATE)
        val existingRoutesJson = sharedPrefs.getString(ROUTES_PREF_KEY, "[]")
        val gson = Gson()
        
        val type = object : TypeToken<MutableList<IndoorCustomRoute>>() {}.type
        val existingRoutes = gson.fromJson<MutableList<IndoorCustomRoute>>(existingRoutesJson, type) ?: mutableListOf()
        
        val updatedRoutes = existingRoutes.filter { it.id != routeId }
        val updatedJson = gson.toJson(updatedRoutes)
        sharedPrefs.edit().putString(ROUTES_PREF_KEY, updatedJson).apply()
    }
    
    fun getAllRoutes(context: Context): List<IndoorCustomRoute> {
        val sharedPrefs = context.getSharedPreferences("indoor_routes_prefs", Context.MODE_PRIVATE)
        val routesJson = sharedPrefs.getString(ROUTES_PREF_KEY, "[]")
        val gson = Gson()
        
        val type = object : TypeToken<List<IndoorCustomRoute>>() {}.type
        return gson.fromJson(routesJson, type) ?: listOf()
    }
    
    // 新增：根據起點和終點查找匹配的路線
    fun findRouteByStartAndEnd(context: Context, startName: String, endName: String): IndoorCustomRoute? {
        val allRoutes = getAllRoutes(context)
        return allRoutes.firstOrNull { route ->
            val start = route.points.firstOrNull()
            val end = route.points.lastOrNull()
            
            (start?.name?.equals(startName, ignoreCase = true) == true && 
             end?.name?.equals(endName, ignoreCase = true) == true)
        }
    }
}

// 新增：添加常用教室快捷方式的函數
fun addFrequentClassroomsIfNeeded(loadedPoints: List<ReferencePoint>) {
    // 這裡可以添加一些常用教室的快捷參考點，如果在載入的點中找不到的話
    val commonClassrooms = listOf(
        "SEC101", "SEC102", "SEC201", "SEC301", "SEC401", "SEC501",
        "SE106", "SE107", "SE219", "SE315", "SE405", "SE505"
    )

    for (classroom in commonClassrooms) {
        if (loadedPoints.none { it.name.equals(classroom, ignoreCase = true) }) {
            // 如果常用教室不存在，你可以在這裡添加預設位置
            // 這裡僅為示例，實際位置需要根據真實情況調整
            Log.d("IndoorRouteEditor", "未找到常用教室: $classroom")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IndoorRouteEditorScreen(
    navController: NavController? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    
    // 室內地圖相關狀態
    var currentImageId by remember { mutableStateOf(R.drawable.se1) } // 默認顯示1樓
    var customImageViewRef = remember { mutableStateOf<MyCustomImageView?>(null) }
    
    // 新增：顯示圖片ID資訊的狀態
    var showImageIdDebugInfo by remember { mutableStateOf(false) }
    
    // 新增：起點和終點
    var startPoint by remember { mutableStateOf<ReferencePoint?>(null) }
    var endPoint by remember { mutableStateOf<ReferencePoint?>(null) }
    
    // 編輯狀態變數
    var editPoints by remember { mutableStateOf<List<ReferencePoint>>(emptyList()) }
    var selectedPointIndex by remember { mutableStateOf<Int?>(null) }
    var isInAddMode by remember { mutableStateOf(true) } // true = 添加模式，false = 編輯模式
    var editingRoute by remember { mutableStateOf<IndoorCustomRoute?>(null) }
    var routeName by remember { mutableStateOf("") }
    var routeDescription by remember { mutableStateOf("") }
    var estimatedMinutes by remember { mutableStateOf("5")} // 預設為5分鐘
    var errorMsg by remember { mutableStateOf<String?>(null) }
    
    // 新增：參考點添加模式
    var isAddingReferencePoint by remember { mutableStateOf(false) }
    var newPointName by remember { mutableStateOf("") }
    var showAddPointDialog by remember { mutableStateOf(false) }
    var tempClickedPointX by remember { mutableStateOf(0.0) }
    var tempClickedPointY by remember { mutableStateOf(0.0) }
    
    // 對話框控制
    var showSaveDialog by remember { mutableStateOf(false) }
    var showManageDialog by remember { mutableStateOf(false) }
    var showPointsDialog by remember { mutableStateOf(false) }
    
    // 搜尋與過濾
    var pointFilterText by remember { mutableStateOf("") }
    var selectedFloor by remember { mutableStateOf(1) } // 默認顯示1樓
    
    // 路線管理
    var allRoutes by remember { mutableStateOf<List<IndoorCustomRoute>>(emptyList()) }
    
    // 參考點列表
    var allReferencePoints by remember { mutableStateOf<List<ReferencePoint>>(emptyList()) }
    
    // 新增：預設理工學院入口參考點
    val defaultEntrancePoint = remember {
        ReferencePoint.createSimplePoint(
            name = "理工學院入口",
            x = 36.21,  // x=36.21%
            y = 68.26,  // y=68.26%
            imageId = R.drawable.se1,
            scanCount = 0
        )
    }
    
    // 顯示的參考點（基於當前選擇的樓層）
    val displayReferencePoints = remember(allReferencePoints, selectedFloor) {
        val floorImageId = when (selectedFloor) {
            1 -> R.drawable.se1
            2 -> R.drawable.se2
            3 -> R.drawable.se3
            4 -> R.drawable.sea4
            5 -> R.drawable.sea5
            else -> R.drawable.se1
        }
        
        allReferencePoints.filter { it.imageId == floorImageId }
    }
    
    // 過濾後的參考點（用於搜尋）
    val filteredReferencePoints = remember(pointFilterText, displayReferencePoints) {
        if (pointFilterText.isBlank()) {
            displayReferencePoints
        } else {
            displayReferencePoints.filter {
                it.name.contains(pointFilterText, ignoreCase = true)
            }
        }
    }
    
    // 高亮顯示的點ID
    var highlightedPointId by remember { mutableStateOf<String?>(null) }
    
    // Snackbar狀態
    val snackbarHostState = remember { SnackbarHostState() }
    
    // 新增：創建參考點的函數
    fun createNewReferencePoint(name: String, x: Double, y: Double) {
        if (name.isBlank()) {
            scope.launch {
                snackbarHostState.showSnackbar("參考點名稱不能為空")
            }
            return
        }
        
        val imageId = when (selectedFloor) {
            1 -> R.drawable.se1
            2 -> R.drawable.se2
            3 -> R.drawable.se3
            4 -> R.drawable.sea4
            5 -> R.drawable.sea5
            else -> R.drawable.se1
        }
        
        val newPoint = ReferencePoint.createSimplePoint(
            name = name,
            x = x,
            y = y,
            imageId = imageId,
            scanCount = 0
        )
        
        allReferencePoints = allReferencePoints + newPoint
        isAddingReferencePoint = false
        showAddPointDialog = false
        newPointName = ""
        
        scope.launch {
            snackbarHostState.showSnackbar("已添加參考點: $name")
        }
        
        // 刷新視圖以顯示新點
        customImageViewRef.value?.invalidate()
    }
    
    // 新增：獲取圖片ID的字符串表示
    fun getImageIdName(imageId: Int): String {
        return when(imageId) {
            R.drawable.se1 -> "se1 (${R.drawable.se1})"
            R.drawable.se2 -> "se2 (${R.drawable.se2})"
            R.drawable.se3 -> "se3 (${R.drawable.se3})"
            R.drawable.sea4 -> "sea4 (${R.drawable.sea4})"
            R.drawable.sea5 -> "sea5 (${R.drawable.sea5})"
            else -> "未知圖片ID: $imageId"
        }
    }
    
    // 新增：圖片ID映射函數 - 確保使用正確的資源ID
    fun mapImageId(imageId: Int): Int {
        // 這裡實現將JSON中可能不同的ID映射到正確的資源ID
        return when(imageId) {
            // 如果發現某些特定ID需要映射，可以在這裡添加
            // 例如 123456 -> R.drawable.se1
            R.drawable.se1, 2131165346, 2131165344 -> R.drawable.se1
            R.drawable.se2, 2131165347, 2131165345 -> R.drawable.se2
            R.drawable.se3, 2131165348 -> R.drawable.se3
            R.drawable.sea4, 2131165342 -> R.drawable.sea4
            R.drawable.sea5, 2131165343 -> R.drawable.sea5
            else -> R.drawable.se1 // 默認回落到1樓
        }
    }
    
    // 載入參考點數據
    LaunchedEffect(Unit) {
        try {
            // 載入已保存的路線
            allRoutes = IndoorRouteManager.getAllRoutes(context)
            
            // 載入參考點
            val inputStream = context.resources.openRawResource(R.raw.classroom_points)
            val reader = BufferedReader(InputStreamReader(inputStream))
            val jsonString = reader.use { it.readText() }
            
            val gson = Gson()
            val listType = object : TypeToken<List<ReferencePoint>>() {}.type
            val loadedPoints = gson.fromJson<List<ReferencePoint>>(jsonString, listType)
            
            Log.d("IndoorRouteEditor", "從JSON載入了 ${loadedPoints.size} 個參考點")
            
            // 顯示前幾個參考點的圖片ID，用於診斷
            loadedPoints.take(5).forEachIndexed { index, point ->
                Log.d("IndoorRouteEditor", "參考點 $index: ${point.name}, 原始圖片ID=${point.imageId}, 映射後ID=${mapImageId(point.imageId)}, 資源名稱=${getImageIdName(mapImageId(point.imageId))}")
            }
            
            // 修正：對每個參考點的圖片ID進行映射修正
            val correctedPoints = loadedPoints.map { point ->
                point.copy(imageId = mapImageId(point.imageId))
            }
            
            // 確保理工學院入口在參考點列表中
            allReferencePoints = listOf(defaultEntrancePoint) + correctedPoints
            
            // 添加常用教室快捷方式，如果還沒有的話
            addFrequentClassroomsIfNeeded(correctedPoints)
        } catch (e: Exception) {
            Log.e("IndoorRouteEditor", "載入參考點或路線時出錯", e)
            // 至少添加理工學院入口點
            allReferencePoints = listOf(defaultEntrancePoint)
            errorMsg = "載入數據失敗：${e.message}"
            scope.launch {
                delay(3000)
                errorMsg = null
            }
        }
    }
    

    
    // 新增：快速添加預設起點（理工學院入口）到路線
    fun addEntranceToRoute() {
        if (editPoints.isEmpty()) {
            editPoints = listOf(defaultEntrancePoint)
            scope.launch {
                snackbarHostState.showSnackbar("已添加理工學院入口作為起點")
            }
        }
    }

    // 計算路線總長度
    fun calculateRouteLength(): Double {
        if (editPoints.size < 2) return 0.0
        
        var totalDistance = 0.0
        for (i in 0 until editPoints.size - 1) {
            totalDistance += calculateDistance(editPoints[i], editPoints[i+1])
        }
        
        return totalDistance
    }
    
    // 添加參考點到路線
    fun addPointToRoute(point: ReferencePoint) {
        if (isInAddMode) {
            // 如果參考點屬於不同的樓層，切換到對應樓層
            val floor = when (point.imageId) {
                R.drawable.se1 -> 1
                R.drawable.se2 -> 2
                R.drawable.se3 -> 3
                R.drawable.sea4 -> 4
                R.drawable.sea5 -> 5
                else -> 1
            }
            
            if (selectedFloor != floor) {
                selectedFloor = floor
                currentImageId = point.imageId
            }
            
            editPoints = editPoints + point
            scope.launch {
                snackbarHostState.showSnackbar("已添加 ${point.name} 到路線")
            }
        }
    }
    
    // 更新選中點
    fun updateSelectedPoint(point: ReferencePoint) {
        if (selectedPointIndex != null) {
            val updatedPoints = editPoints.toMutableList()
            updatedPoints[selectedPointIndex!!] = point
            editPoints = updatedPoints
            selectedPointIndex = null
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
    
    // 修改：加入直接點擊添加路線點的函數
    fun addRoutePointFromClick(x: Double, y: Double) {
        if (isInAddMode) {
            // 創建一個臨時參考點（非保存到參考點列表）
            val tempPoint = ReferencePoint.createSimplePoint(
                name = "路線點 #${editPoints.size + 1}",
                x = x,
                y = y,
                imageId = currentImageId,
                scanCount = 0
            )
            
            // 添加到編輯中的路線
            editPoints = editPoints + tempPoint
            scope.launch {
                snackbarHostState.showSnackbar("已添加路線點 #${editPoints.size}")
            }
            
            // 刷新視圖
            customImageViewRef.value?.invalidate()
        } else if (selectedPointIndex != null) {
            // 編輯模式：移動選中的點
            val updatedPoints = editPoints.toMutableList()
            val updatedPoint = updatedPoints[selectedPointIndex!!].copy(
                x = x,
                y = y
            )
            updatedPoints[selectedPointIndex!!] = updatedPoint
            editPoints = updatedPoints
            selectedPointIndex = null
            
            scope.launch {
                snackbarHostState.showSnackbar("已移動選中點")
            }
            
            // 刷新視圖
            customImageViewRef.value?.invalidate()
        }
    }
    
    // 新增：設置起點函數
    fun setStartPoint(point: ReferencePoint) {
        startPoint = point
        
        // 如果起點不在路線中，添加到路線的開頭
        if (!editPoints.contains(point)) {
            editPoints = listOf(point) + editPoints
        }
        
        scope.launch {
            snackbarHostState.showSnackbar("已設置 ${point.name} 為起點")
        }
        
        // 更新視圖
        customImageViewRef.value?.startPoint = point
        customImageViewRef.value?.invalidate()
    }
    
    // 新增：設置終點函數
    fun setEndPoint(point: ReferencePoint) {
        endPoint = point
        
        // 如果終點不在路線中，添加到路線的末尾
        if (!editPoints.contains(point)) {
            editPoints = editPoints + point
        }
        
        scope.launch {
            snackbarHostState.showSnackbar("已設置 ${point.name} 為終點")
        }
        
        // 更新視圖
        customImageViewRef.value?.endPoint = point
        customImageViewRef.value?.invalidate()
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
        
        // 確保有起點和終點
        if (startPoint == null) {
            startPoint = editPoints.first()
        }
        
        if (endPoint == null) {
            endPoint = editPoints.last()
        }
        
        val timeMinutes = estimatedMinutes.toIntOrNull() ?: 5
        
        val route = IndoorCustomRoute(
            id = editingRoute?.id ?: UUID.randomUUID().toString(),
            name = routeName,
            description = routeDescription,
            points = editPoints,
            estimatedTimeInMinutes = timeMinutes,
            imageId = currentImageId,
            lastModified = System.currentTimeMillis()
        )
        
        IndoorRouteManager.saveRoute(context, route)
        
        // 重置編輯狀態
        editingRoute = null
        editPoints = emptyList()
        routeName = ""
        routeDescription = ""
        estimatedMinutes = "5"
        selectedPointIndex = null
        startPoint = null
        endPoint = null
        
        // 重新載入路線列表
        allRoutes = IndoorRouteManager.getAllRoutes(context)
        
        // 顯示成功訊息
        scope.launch {
            snackbarHostState.showSnackbar("室內路線已保存")
        }
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
        startPoint = null
        endPoint = null
        
        // 更新視圖
        customImageViewRef.value?.startPoint = null
        customImageViewRef.value?.endPoint = null
        customImageViewRef.value?.invalidate()
    }
    
    // 編輯現有路線
    fun editExistingRoute(route: IndoorCustomRoute) {
        editingRoute = route
        editPoints = route.points
        routeName = route.name
        routeDescription = route.description
        estimatedMinutes = route.estimatedTimeInMinutes.toString()
        currentImageId = route.imageId
        
        // 設置起點和終點
        if (route.points.isNotEmpty()) {
            startPoint = route.points.first()
            endPoint = route.points.last()
        }
        
        // 設定當前樓層
        selectedFloor = when (route.imageId) {
            R.drawable.se1 -> 1
            R.drawable.se2 -> 2
            R.drawable.se3 -> 3
            R.drawable.sea4 -> 4
            R.drawable.sea5 -> 5
            else -> 1
        }
        
        showManageDialog = false
    }
    
    // 刪除路線
    fun deleteRoute(route: IndoorCustomRoute) {
        IndoorRouteManager.deleteRoute(context, route.id)
        allRoutes = IndoorRouteManager.getAllRoutes(context)
        
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
    
    // 格式化時間
    fun formatTime(timestamp: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }
    
    // 切換樓層
    fun switchFloor(floor: Int) {
        selectedFloor = floor
        currentImageId = when (floor) {
            1 -> R.drawable.se1
            2 -> R.drawable.se2
            3 -> R.drawable.se3
            4 -> R.drawable.sea4
            5 -> R.drawable.sea5
            else -> R.drawable.se1
        }
    }
    
    // 主界面
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("室內路線編輯器") },
                navigationIcon = {
                    IconButton(onClick = {
                        navController?.navigateUp()
                    }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    // 新增：圖片ID調試按鈕
                    IconButton(
                        onClick = { showImageIdDebugInfo = !showImageIdDebugInfo }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "圖片ID資訊",
                            tint = if (showImageIdDebugInfo) Color.Green else LocalContentColor.current
                        )
                    }
                    
                    // 新增：參考點添加模式切換按鈕
                    IconButton(
                        onClick = { 
                            isAddingReferencePoint = !isAddingReferencePoint
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    if (isAddingReferencePoint) "已進入參考點添加模式，點擊地圖添加點" 
                                    else "已退出參考點添加模式"
                                )
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "添加參考點",
                            tint = if (isAddingReferencePoint) Color.Green else LocalContentColor.current
                        )
                    }
                    
                    // 選擇參考點按鈕
                    IconButton(onClick = {
                        showPointsDialog = true
                        pointFilterText = ""
                    }) {
                        Icon(Icons.Default.LocationOn, contentDescription = "選擇參考點")
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
            Column {
                // 樓層選擇區
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    for (floor in 1..5) {
                        Button(
                            onClick = { switchFloor(floor) },
                            modifier = Modifier.padding(horizontal = 4.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (selectedFloor == floor) 
                                    MaterialTheme.colorScheme.primary 
                                else 
                                    MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f)
                            )
                        ) {
                            Text("${floor}F")
                        }
                    }
                }
                
                // 新增：圖片ID調試資訊
                AnimatedVisibility(visible = showImageIdDebugInfo) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFFE3F2FD)
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "圖片ID診斷資訊",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("當前樓層圖片ID: ${getImageIdName(currentImageId)}")
                            Text("當前樓層參考點: ${displayReferencePoints.size}個")
                            Text("總參考點: ${allReferencePoints.size}個")
                            
                            // 顯示前3個參考點的圖片ID
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("當前樓層參考點資訊:")
                            displayReferencePoints.take(3).forEachIndexed { index, point ->
                                Text("點${index+1}: ${point.name}, ID=${getImageIdName(point.imageId)}")
                            }
                        }
                    }
                }
                
                // 室內地圖顯示
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    AndroidView(
                        factory = { ctx ->
                            MyCustomImageView(ctx).apply {
                                customImageViewRef.value = this
                                setImageResource(currentImageId)
                                maxZoom = 8f
                                minZoom = 0.5f
                                isZoomEnabled = true
                                setScaleType(android.widget.ImageView.ScaleType.MATRIX)
                                
                                // 修改: 點擊地圖添加參考點或路線點
                                setOnTouchListener { view, event ->
                                    if (event.action == MotionEvent.ACTION_DOWN) {
                                        val transformedPoint = useTransformCoordTouchToBitmap(
                                            event.x,
                                            event.y,
                                            true
                                        )
                                        
                                        val bitmapWidth = drawable.intrinsicWidth.toFloat()
                                        val bitmapHeight = drawable.intrinsicHeight.toFloat()
                                        
                                        // 將坐標轉換為百分比
                                        tempClickedPointX = (transformedPoint.x / bitmapWidth * 100).toDouble()
                                        tempClickedPointY = (transformedPoint.y / bitmapHeight * 100).toDouble()
                                        
                                        Log.d("IndoorRouteEditor", "點擊位置: x=${tempClickedPointX}%, y=${tempClickedPointY}%")
                                        
                                        if (isAddingReferencePoint) {
                                            // 參考點添加模式
                                            showAddPointDialog = true
                                            return@setOnTouchListener true
                                        } else {
                                            // 路線點添加模式
                                            addRoutePointFromClick(tempClickedPointX, tempClickedPointY)
                                            return@setOnTouchListener true
                                        }
                                    }
                                    false
                                }
                            }
                        },
                        update = { view ->
                            view.setImageResource(currentImageId)
                            view.overlayPoints = displayReferencePoints
                            view.currentImageId = currentImageId
                            view.navigationPath = editPoints.takeIf { it.size >= 2 }?.let {
                                NavigationPath(it, calculateRouteLength())
                            }
                            view.highlightedPointId = highlightedPointId
                            
                            // 設定起點和終點
                            view.startPoint = startPoint
                            view.endPoint = endPoint
                            
                            view.invalidate()
                        }
                    )
                    
                    // 編輯模式提示
                    Card(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = when {
                                isAddingReferencePoint -> Color(0xFFE8F5E9)  // 綠色背景表示參考點添加模式
                                isInAddMode -> Color(0xFFE0F7FA)             // 藍色背景表示添加路線點模式
                                else -> Color(0xFFF3E5F5)                    // 紫色背景表示編輯模式
                            }
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = when {
                                    isAddingReferencePoint -> Icons.Default.Add
                                    isInAddMode -> Icons.Default.Add
                                    else -> Icons.Default.Edit
                                },
                                contentDescription = null,
                                tint = when {
                                    isAddingReferencePoint -> Color.Green
                                    isInAddMode -> Color.Blue
                                    else -> Color(0xFF7B1FA2)
                                }
                            )
                            Text(
                                text = when {
                                    isAddingReferencePoint -> "參考點添加模式"
                                    isInAddMode -> "添加模式"
                                    else -> "編輯模式"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = when {
                                    isAddingReferencePoint -> Color.Green
                                    isInAddMode -> Color.Blue
                                    else -> Color(0xFF7B1FA2)
                                }
                            )
                        }
                    }
                    
                    // 當前樓層參考點數量顯示
                    Card(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Text(
                            text = "${displayReferencePoints.size} 個參考點",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
                
                // 底部工具欄
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
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
                            text = "${editPoints.size} 個點 | ${String.format("%.1f", routeLength)} 單位",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = if (editingRoute != null) "編輯: ${editingRoute?.name}" else "新建室內路線",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }
                    
                    // 選擇參考點按鈕
                    Button(
                        onClick = { 
                            showPointsDialog = true 
                            pointFilterText = ""
                        },
                        modifier = Modifier.padding(horizontal = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = "選擇參考點",
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
                        IconButton(
                            onClick = {
                                if (editPoints.isNotEmpty()) {
                                    val updatedPoints = editPoints.dropLast(1)
                                    editPoints = updatedPoints
                                }
                            },
                            enabled = editPoints.isNotEmpty()
                        ) {
                            Icon(Icons.Default.Undo, contentDescription = "撤銷")
                        }
                        
                        IconButton(onClick = {
                            showSaveDialog = true
                        }) {
                            Icon(Icons.Default.Save, contentDescription = "保存")
                        }
                    }
                }
            }
            
            // 新增：添加參考點對話框
            if (showAddPointDialog) {
                AlertDialog(
                    onDismissRequest = { 
                        showAddPointDialog = false 
                        newPointName = ""
                    },
                    title = { Text("添加參考點") },
                    text = {
                        Column {
                            OutlinedTextField(
                                value = newPointName,
                                onValueChange = { newPointName = it },
                                label = { Text("參考點名稱") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            Text(
                                text = "位置: (${String.format("%.2f", tempClickedPointX)}%, ${String.format("%.2f", tempClickedPointY)}%)",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            
                            Text(
                                text = "樓層: ${selectedFloor}F",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                createNewReferencePoint(
                                    name = newPointName,
                                    x = tempClickedPointX,
                                    y = tempClickedPointY
                                )
                            }
                        ) {
                            Text("添加")
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = { 
                                showAddPointDialog = false
                                newPointName = ""
                            }
                        ) {
                            Text("取消")
                        }
                    }
                )
            }
            
            // 參考點選擇對話框
            if (showPointsDialog) {
                Dialog(onDismissRequest = { showPointsDialog = false }) {
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
                                text = "選擇參考點 (${selectedFloor}樓 - ${displayReferencePoints.size}個點)",
                                style = MaterialTheme.typography.titleLarge,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            
                            // 搜尋框
                            OutlinedTextField(
                                value = pointFilterText,
                                onValueChange = { pointFilterText = it },
                                label = { Text("搜尋") },
                                modifier = Modifier.fillMaxWidth(),
                                leadingIcon = {
                                    Icon(Icons.Default.Search, contentDescription = "搜尋")
                                },
                                trailingIcon = {
                                    if (pointFilterText.isNotEmpty()) {
                                        IconButton(onClick = { pointFilterText = "" }) {
                                            Icon(Icons.Default.Clear, contentDescription = "清除")
                                        }
                                    }
                                },
                                singleLine = true
                            )
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            // 參考點列表 - 添加為空時的提示
                            if (filteredReferencePoints.isEmpty()) {
                                if (pointFilterText.isNotEmpty()) {
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxWidth(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "未找到符合「${pointFilterText}」的參考點",
                                            color = Color.Gray,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxWidth(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Text(
                                                text = "此樓層暫無參考點",
                                                color = Color.Gray,
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                            
                                            Spacer(modifier = Modifier.height(16.dp))
                                            
                                            Button(
                                                onClick = { 
                                                    showPointsDialog = false
                                                    isAddingReferencePoint = true
                                                    scope.launch {
                                                        snackbarHostState.showSnackbar("請在地圖上點擊添加參考點")
                                                    }
                                                }
                                            ) {
                                                Icon(Icons.Default.Add, contentDescription = null)
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("在地圖上添加參考點")
                                            }
                                        }
                                    }
                                }
                            } else {
                                // 顯示參考點列表 - 修改為添加起終點按鈕
                                LazyColumn(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth()
                                ) {
                                    items(filteredReferencePoints) { point ->
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    highlightedPointId = point.id
                                                    customImageViewRef.value?.invalidate()
                                                }
                                                .padding(vertical = 4.dp)
                                        ) {
                                            ListItem(
                                                headlineContent = { Text(point.name) },
                                                leadingContent = {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(32.dp)
                                                            .background(getPointColor(point), CircleShape)
                                                            .border(1.dp, Color.White, CircleShape),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Text(
                                                            text = point.name.take(1),
                                                            color = Color.White
                                                        )
                                                    }
                                                },
                                                trailingContent = {
                                                    Row {
                                                        // 添加到路線按鈕
                                                        Button(
                                                            onClick = {
                                                                addPointToRoute(point)
                                                                showPointsDialog = false
                                                            },
                                                            modifier = Modifier.height(36.dp),
                                                            colors = ButtonDefaults.buttonColors(
                                                                containerColor = Color.Blue
                                                            )
                                                        ) {
                                                            Text("添加", fontSize = 12.sp)
                                                        }
                                                        
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        
                                                        // 設為起點按鈕
                                                        IconButton(
                                                            onClick = {
                                                                setStartPoint(point)
                                                                showPointsDialog = false
                                                            },
                                                            modifier = Modifier.size(36.dp)
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Default.LocationOn,
                                                                contentDescription = "設為起點",
                                                                tint = Color.Green,
                                                                modifier = Modifier.size(18.dp)
                                                            )
                                                        }
                                                        
                                                        // 設為終點按鈕
                                                        IconButton(
                                                            onClick = {
                                                                setEndPoint(point)
                                                                showPointsDialog = false
                                                            },
                                                            modifier = Modifier.size(36.dp)
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Default.LocationOn,
                                                                contentDescription = "設為終點",
                                                                tint = Color.Red,
                                                                modifier = Modifier.size(18.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                            )
                                            
                                            if (filteredReferencePoints.indexOf(point) < filteredReferencePoints.size - 1) {
                                                Divider()
                                            }
                                        }
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
                                    showPointsDialog = false
                                }) {
                                    Text("取消")
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
                                text = if (editingRoute != null) "編輯室內路線" else "保存室內路線",
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
                                text = "路線長度：${String.format("%.1f", routeLength)} 單位",
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
                                text = "管理室內路線",
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
                                        text = "尚未創建任何室內路線",
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
                                                        
                                                        // 樓層信息
                                                        val floor = when (route.imageId) {
                                                            R.drawable.se1 -> "1樓"
                                                            R.drawable.se2 -> "2樓"
                                                            R.drawable.se3 -> "3樓"
                                                            R.drawable.sea4 -> "4樓"
                                                            R.drawable.sea5 -> "5樓"
                                                            else -> "1樓"
                                                        }
                                                        
                                                        Text(
                                                            text = "${floor} | ${route.points.size}個點 | ${route.estimatedTimeInMinutes}分鐘",
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

// 新增：預設教室資料 - 如果JSON資料載入失敗，可以作為備用
private val defaultClassrooms = listOf(
    ReferencePoint.createSimplePoint("SEC101", 45.0, 42.0, R.drawable.se1),
    ReferencePoint.createSimplePoint("SEC102", 62.0, 42.0, R.drawable.se1),
    ReferencePoint.createSimplePoint("SEC103", 79.0, 42.0, R.drawable.se1),
    ReferencePoint.createSimplePoint("SE106", 45.0, 78.0, R.drawable.se1),
    ReferencePoint.createSimplePoint("SE107", 62.0, 78.0, R.drawable.se1),
    ReferencePoint.createSimplePoint("SEA114", 32.68525314331055, 27.297943115234375, R.drawable.se1),
    
    ReferencePoint.createSimplePoint("SEC201", 45.0, 42.0, R.drawable.se2),
    ReferencePoint.createSimplePoint("SEC202", 62.0, 42.0, R.drawable.se2),
    ReferencePoint.createSimplePoint("SE219", 45.0, 78.0, R.drawable.se2),
    
    ReferencePoint.createSimplePoint("SEC301", 45.0, 42.0, R.drawable.se3),
    ReferencePoint.createSimplePoint("SE315", 45.0, 78.0, R.drawable.se3),
    
    ReferencePoint.createSimplePoint("SEC401", 45.0, 42.0, R.drawable.sea4),
    ReferencePoint.createSimplePoint("SE405", 45.0, 78.0, R.drawable.sea4),
    
    ReferencePoint.createSimplePoint("SEC501", 45.0, 42.0, R.drawable.sea5),
    ReferencePoint.createSimplePoint("SE505", 45.0, 78.0, R.drawable.sea5)
)
