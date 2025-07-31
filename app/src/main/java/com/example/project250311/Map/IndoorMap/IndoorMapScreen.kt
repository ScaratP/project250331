package com.example.project250311.Map.IndoorMap

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color.toArgb
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.project250311.R
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.ortiz.touchview.OnTouchImageViewListener
import com.ortiz.touchview.TouchImageView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.Locale
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

data class ReferencePoint(
    val id: String,
    val name: String,
    val x: Double,
    val y: Double,
    val imageId: Int,
    val scanCount: Int = 0
) {
    companion object {
        fun createSimplePoint(name: String, x: Double, y: Double, imageId: Int, scanCount: Int = 0): ReferencePoint {
            val id = "RP${System.currentTimeMillis()}"
            return ReferencePoint(id, name, x, y, imageId, scanCount)
        }
    }
}

data class MapImage(
    val id: Int,
    val name: String,
    val floor: Int = 0
)

data class NavigationPath(
    val points: List<ReferencePoint>,
    val totalDistance: Double
)

data class CurrentPosition(
    val x: Double,
    val y: Double,
    val mapImageId: Int
)

class MyCustomImageView(context: Context, attrs: AttributeSet? = null) : TouchImageView(context, attrs) {
    fun useTransformCoordTouchToBitmap(x: Float, y: Float, clipToBitmap: Boolean): PointF {
        return transformCoordTouchToBitmap(x, y, clipToBitmap)
    }

    fun useTransformCoordBitmapToTouch(x: Float, y: Float): PointF {
        return transformCoordBitmapToTouch(x, y)
    }

    var isGestureInProgress = false
    var overlayPoints: List<ReferencePoint> = emptyList()
    var currentImageId: Int = R.drawable.se1
    var currentMapImage: MapImage? = null
    var navigationPath: NavigationPath? = null
    var startPoint: ReferencePoint? = null
    var endPoint: ReferencePoint? = null
    var highlightedPointId: String? = null

    private val pointPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
    }

    private val textPaint = Paint().apply {
        isAntiAlias = true
        textSize = 30f
        color = android.graphics.Color.WHITE
        textAlign = Paint.Align.CENTER
    }

    private val borderPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = android.graphics.Color.WHITE
    }

    private val routePaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = 10f
        color = android.graphics.Color.BLUE
    }

    private val startEndPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
        color = android.graphics.Color.RED
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        // 繪製導航路徑
        navigationPath?.let { path ->
            drawNavigationPath(canvas, path.points)
        }
        
        // 繪製參考點
        drawReferencePointsOnCanvas(canvas, overlayPoints)
        
        // 繪製起點和終點
        startPoint?.let { drawSpecialPoint(canvas, it, android.graphics.Color.GREEN, "S") }
        endPoint?.let { drawSpecialPoint(canvas, it, android.graphics.Color.RED, "E") }
    }

    internal fun drawSpecialPoint(canvas: Canvas, point: ReferencePoint, color: Int, label: String) {
        val bitmapWidth = drawable.intrinsicWidth.toFloat()
        val bitmapHeight = drawable.intrinsicHeight.toFloat()
        
        val pointXOnBitmap = (point.x / 100f * bitmapWidth)
        val pointYOnBitmap = (point.y / 100f * bitmapHeight)
        
        val bitmapPoint = PointF(pointXOnBitmap.toFloat(), pointYOnBitmap.toFloat())
        val mappedPoint = useTransformCoordBitmapToTouch(bitmapPoint.x, bitmapPoint.y)
        
        startEndPaint.color = color
        
        // 繪製大圓點
        canvas.drawCircle(mappedPoint.x, mappedPoint.y, 40f, startEndPaint)
        
        // 繪製標籤
        textPaint.textSize = 40f
        canvas.drawText(
            label,
            mappedPoint.x,
            mappedPoint.y + textPaint.textSize / 3,
            textPaint
        )
    }

    internal fun drawNavigationPath(canvas: Canvas, pathPoints: List<ReferencePoint>) {
        if (pathPoints.size < 2) return
        
        val bitmapWidth = drawable.intrinsicWidth.toFloat()
        val bitmapHeight = drawable.intrinsicHeight.toFloat()
        
        val path = Path()
        
        // 第一個點
        val firstPoint = pathPoints.first()
        val firstX = (firstPoint.x / 100f * bitmapWidth)
        val firstY = (firstPoint.y / 100f * bitmapHeight)
        val firstMapped = useTransformCoordBitmapToTouch(firstX.toFloat(), firstY.toFloat())
        
        path.moveTo(firstMapped.x, firstMapped.y)
        
        // 其餘的點
        for (i in 1 until pathPoints.size) {
            val point = pathPoints[i]
            val pointX = (point.x / 100f * bitmapWidth)
            val pointY = (point.y / 100f * bitmapHeight)
            val mappedPoint = useTransformCoordBitmapToTouch(pointX.toFloat(), pointY.toFloat())
            
            path.lineTo(mappedPoint.x, mappedPoint.y)
        }
        
        canvas.drawPath(path, routePaint)
    }

    fun drawReferencePointsOnCanvas(canvas: Canvas, points: List<ReferencePoint>) {
        if (drawable == null) return

        val bitmapWidth = drawable.intrinsicWidth.toFloat()
        val bitmapHeight = drawable.intrinsicHeight.toFloat()
        val shouldDrawSimplified = isGestureInProgress

        points.forEach { point ->
            val pointXOnBitmap = (point.x / 100f * bitmapWidth)
            val pointYOnBitmap = (point.y / 100f * bitmapHeight)
            val bitmapPoint = PointF(pointXOnBitmap.toFloat(), pointYOnBitmap.toFloat())
            val mappedPoint = useTransformCoordBitmapToTouch(bitmapPoint.x, bitmapPoint.y)

            // 如果是高亮點，繪製不同顏色
            val isHighlighted = point.id == highlightedPointId
            val pointColor = if (isHighlighted) {
                android.graphics.Color.YELLOW
            } else {
                getPointColor(point).toArgb()
            }
            
            pointPaint.color = pointColor

            // 繪製外圓
            canvas.drawCircle(
                mappedPoint.x, 
                mappedPoint.y, 
                if (shouldDrawSimplified) 15f else if (isHighlighted) 35f else 30f, 
                pointPaint
            )

            if (!shouldDrawSimplified) {
                // 繪製邊框
                canvas.drawCircle(mappedPoint.x, mappedPoint.y, if (isHighlighted) 35f else 30f, borderPaint)

                // 繪製標籤文字
                canvas.drawText(
                    point.name.take(1),
                    mappedPoint.x,
                    mappedPoint.y + textPaint.textSize / 3,
                    textPaint
                )

                // 繪製名稱標籤
                textPaint.textAlign = Paint.Align.LEFT
                textPaint.color = pointColor
                canvas.drawText(
                    point.name,
                    mappedPoint.x + 40f,
                    mappedPoint.y + 10f,
                    textPaint
                )
                textPaint.textAlign = Paint.Align.CENTER
                textPaint.color = android.graphics.Color.WHITE
            }
        }
    }
}

// 計算兩點之間的距離
fun calculateDistance(point1: ReferencePoint, point2: ReferencePoint): Double {
    return sqrt(
        (point1.x - point2.x).pow(2) + 
        (point1.y - point2.y).pow(2)
    )
}

// 尋找最近的參考點
fun findNearestPoint(points: List<ReferencePoint>, x: Double, y: Double, imageId: Int): ReferencePoint? {
    if (points.isEmpty()) return null
    
    return points.filter { it.imageId == imageId }
        .minByOrNull { sqrt((it.x - x).pow(2) + (it.y - y).pow(2)) }
}

// A* 路徑規劃算法
fun findPath(points: List<ReferencePoint>, start: ReferencePoint, end: ReferencePoint): NavigationPath {
    // 簡化版本：只尋找同一地圖上的直接路徑
    if (start.imageId == end.imageId) {
        return NavigationPath(listOf(start, end), calculateDistance(start, end))
    }
    
    // 否則返回簡單路徑
    return NavigationPath(listOf(start, end), calculateDistance(start, end))
}

// 為參考點生成顏色
fun getPointColor(point: ReferencePoint): Color {
    val hash = point.id.hashCode()
    return Color(
        red = ((hash and 0xFF0000) shr 16) / 255f,
        green = ((hash and 0x00FF00) shr 8) / 255f,
        blue = (hash and 0x0000FF) / 255f,
        alpha = 1f
    )
}

// 圖片選項卡組件
@Composable
fun MapImageTabItem(
    mapImage: MapImage,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .height(36.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = if (isSelected)
            MaterialTheme.colorScheme.primary
        else
            MaterialTheme.colorScheme.surface,
        border = if (!isSelected)
            androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
        else null
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Map,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = if (isSelected)
                    MaterialTheme.colorScheme.onPrimary
                else
                    MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.width(4.dp))

            Text(
                text = mapImage.name,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isSelected)
                    MaterialTheme.colorScheme.onPrimary
                else
                    MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IndoorMapScreen() {
    val context = LocalContext.current
    var imageSize by remember { mutableStateOf(IntSize.Zero) }
    val touchImageViewRef = remember { mutableStateOf<TouchImageView?>(null) }
    val customImageViewRef = remember { mutableStateOf<MyCustomImageView?>(null) }
    
    // 導航相關狀態
    var navigationMode by remember { mutableStateOf(false) }
    var startPoint by remember { mutableStateOf<ReferencePoint?>(null) }
    var endPoint by remember { mutableStateOf<ReferencePoint?>(null) }
    var navigationPath by remember { mutableStateOf<NavigationPath?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var highlightedPointId by remember { mutableStateOf<String?>(null) }
    
    // 當前選擇的圖片ID
    var currentImageId by remember { mutableStateOf(AvailableMapImages.maps.first().id) }
    
    // 參考點列表和過濾
    var allReferencePoints by remember { mutableStateOf<List<ReferencePoint>>(emptyList()) }
    val filteredReferencePoints = remember(allReferencePoints, currentImageId, searchQuery) {
        allReferencePoints
            .filter { 
                it.imageId == currentImageId && 
                (searchQuery.isEmpty() || 
                 it.name.contains(searchQuery, ignoreCase = true))
            }
    }
    
    // 控制參考點列表的展開/收起狀態
    var isListExpanded by remember { mutableStateOf(true) }
    
    // Snackbar狀態
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    // 讀取參考點數據
    LaunchedEffect(Unit) {
        try {
            // 從JSON文件加載參考點
            val inputStream = context.resources.openRawResource(R.raw.classroom_points)
            val reader = BufferedReader(InputStreamReader(inputStream))
            val jsonString = reader.use { it.readText() }
            
            val gson = Gson()
            val listType = object : TypeToken<List<ReferencePoint>>() {}.type
            allReferencePoints = gson.fromJson(jsonString, listType)
            
            Log.d("IndoorMapScreen", "Loaded ${allReferencePoints.size} reference points from JSON")
        } catch (e: Exception) {
            Log.e("IndoorMapScreen", "Error loading reference points", e)
            scope.launch {
                snackbarHostState.showSnackbar("載入教室數據失敗: ${e.localizedMessage}")
            }
        }
    }

    // 主界面
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(if (navigationMode) "室內導航" else "室內地圖") },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary,
                        actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    navigationIcon = {
                        if (navigationMode) {
                            IconButton(onClick = {
                                navigationMode = false
                                startPoint = null
                                endPoint = null
                                navigationPath = null
                                highlightedPointId = null
                                customImageViewRef.value?.invalidate()
                            }) {
                                Icon(
                                    imageVector = Icons.Default.ArrowBack,
                                    contentDescription = "退出導航模式"
                                )
                            }
                        }
                    },
                    actions = {
                        if (!navigationMode) {
                            IconButton(
                                onClick = {
                                    navigationMode = true
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Route,
                                    contentDescription = "導航模式",
                                    tint = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }
                    }
                )

                // 地圖樓層選擇
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp)
                    ) {
                        // 顯示所有可用地圖圖片供選擇
                        AvailableMapImages.maps.forEachIndexed { index, mapImage ->
                            MapImageTabItem(
                                mapImage = mapImage,
                                isSelected = currentImageId == mapImage.id,
                                onClick = {
                                    // 如果當前有導航中，提示切換樓層會清除導航
                                    if (navigationPath != null) {
                                        scope.launch {
                                            snackbarHostState.showSnackbar("切換樓層會清除當前導航")
                                            delay(1500)
                                            currentImageId = mapImage.id
                                            customImageViewRef.value?.setImageResource(mapImage.id)
                                            startPoint = null
                                            endPoint = null
                                            navigationPath = null
                                            customImageViewRef.value?.startPoint = null
                                            customImageViewRef.value?.endPoint = null
                                            customImageViewRef.value?.navigationPath = null
                                            customImageViewRef.value?.invalidate()
                                        }
                                    } else {
                                        currentImageId = mapImage.id
                                        customImageViewRef.value?.setImageResource(mapImage.id)
                                    }
                                }
                            )

                            if (index < AvailableMapImages.maps.size - 1) {
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                        }
                    }
                    HorizontalDivider()
                }
                
                // 導航控制欄
                AnimatedVisibility(visible = navigationMode) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .padding(8.dp)
                    ) {
                        Text(
                            text = "選擇起點和終點進行導航",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 起點
                            Box(
                                modifier = Modifier.weight(1f)
                            ) {
                                OutlinedTextField(
                                    value = startPoint?.name ?: "",
                                    onValueChange = { },
                                    label = { Text("起點") },
                                    readOnly = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.LocationOn,
                                            contentDescription = null,
                                            tint = Color.Green
                                        )
                                    },
                                    trailingIcon = {
                                        if (startPoint != null) {
                                            IconButton(onClick = {
                                                startPoint = null
                                                navigationPath = null
                                                customImageViewRef.value?.startPoint = null
                                                customImageViewRef.value?.navigationPath = null
                                                customImageViewRef.value?.invalidate()
                                            }) {
                                                Icon(Icons.Default.Clear, "清除起點")
                                            }
                                        }
                                    }
                                )
                            }
                            
                            Spacer(modifier = Modifier.width(8.dp))
                            
                            // 終點
                            Box(
                                modifier = Modifier.weight(1f)
                            ) {
                                OutlinedTextField(
                                    value = endPoint?.name ?: "",
                                    onValueChange = { },
                                    label = { Text("終點") },
                                    readOnly = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.LocationOn,
                                            contentDescription = null,
                                            tint = Color.Red
                                        )
                                    },
                                    trailingIcon = {
                                        if (endPoint != null) {
                                            IconButton(onClick = {
                                                endPoint = null
                                                navigationPath = null
                                                customImageViewRef.value?.endPoint = null
                                                customImageViewRef.value?.navigationPath = null
                                                customImageViewRef.value?.invalidate()
                                            }) {
                                                Icon(Icons.Default.Clear, "清除終點")
                                            }
                                        }
                                    }
                                )
                            }
                        }
                        
                        // 路徑計算按鈕
                        Button(
                            onClick = {
                                if (startPoint != null && endPoint != null) {
                                    // 如果起點和終點在不同樓層，提示
                                    if (startPoint!!.imageId != endPoint!!.imageId) {
                                        scope.launch {
                                            snackbarHostState.showSnackbar("暫不支援不同樓層間導航")
                                        }
                                        return@Button
                                    }
                                    
                                    // 計算路徑
                                    navigationPath = findPath(allReferencePoints, startPoint!!, endPoint!!)
                                    
                                    // 切換到路徑所在樓層
                                    currentImageId = startPoint!!.imageId
                                    customImageViewRef.value?.setImageResource(currentImageId)
                                    
                                    // 更新視圖
                                    customImageViewRef.value?.navigationPath = navigationPath
                                    customImageViewRef.value?.startPoint = startPoint
                                    customImageViewRef.value?.endPoint = endPoint
                                    customImageViewRef.value?.invalidate()
                                    
                                    // 顯示路徑信息
                                    val distance = String.format("%.2f", navigationPath!!.totalDistance)
                                    scope.launch {
                                        snackbarHostState.showSnackbar("找到路徑，總距離: $distance 單位")
                                    }
                                } else {
                                    scope.launch {
                                        snackbarHostState.showSnackbar("請選擇起點和終點")
                                    }
                                }
                            },
                            enabled = startPoint != null && endPoint != null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Route,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("計算路徑")
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
        ) {
            // 搜索欄
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("搜索教室...") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .focusRequester(focusRequester),
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "搜索") },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { 
                            searchQuery = ""
                            highlightedPointId = null
                            customImageViewRef.value?.highlightedPointId = null
                            customImageViewRef.value?.invalidate()
                        }) {
                            Icon(Icons.Default.Clear, contentDescription = "清除")
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions.Default.copy(
                    imeAction = ImeAction.Search
                ),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        focusManager.clearFocus()
                        // 搜索並高亮第一個結果
                        val result = allReferencePoints.firstOrNull { 
                            it.name.contains(searchQuery, ignoreCase = true)
                        }
                        if (result != null) {
                            // 切換到該點所在樓層
                            currentImageId = result.imageId
                            customImageViewRef.value?.setImageResource(currentImageId)
                            
                            // 高亮該點
                            highlightedPointId = result.id
                            customImageViewRef.value?.highlightedPointId = result.id
                            customImageViewRef.value?.invalidate()
                            
                            scope.launch {
                                snackbarHostState.showSnackbar("已找到教室: ${result.name}")
                            }
                        } else {
                            scope.launch {
                                snackbarHostState.showSnackbar("未找到符合的教室")
                            }
                        }
                    }
                )
            )
            
            // 地圖部分
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .padding(16.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                AndroidView(
                    factory = { ctx ->
                        MyCustomImageView(ctx).apply {
                            setLayerType(View.LAYER_TYPE_HARDWARE, null)
                            customImageViewRef.value = this
                            touchImageViewRef.value = this
                            setImageResource(currentImageId)
                            maxZoom = 4f
                            minZoom = 0.8f

                            setOnTouchListener { v, event ->
                                when (event.action) {
                                    MotionEvent.ACTION_DOWN -> {
                                        isGestureInProgress = true
                                        postInvalidateOnAnimation()
                                        false
                                    }
                                    MotionEvent.ACTION_UP -> {
                                        if (navigationMode) {
                                            // 導航模式下點擊選擇起點或終點
                                            val mappedPoint = useTransformCoordTouchToBitmap(event.x, event.y, true)
                                            val bitmapWidth = drawable.intrinsicWidth
                                            val bitmapHeight = drawable.intrinsicHeight
                                            
                                            if (mappedPoint.x >= 0 && mappedPoint.x <= bitmapWidth &&
                                                mappedPoint.y >= 0 && mappedPoint.y <= bitmapHeight) {
                                                
                                                val percentX = mappedPoint.x / bitmapWidth * 100f
                                                val percentY = mappedPoint.y / bitmapHeight * 100f
                                                
                                                // 找到最近的參考點
                                                val nearestPoint = findNearestPoint(
                                                    allReferencePoints,
                                                    percentX.toDouble(),
                                                    percentY.toDouble(),
                                                    currentImageId
                                                )
                                                
                                                if (nearestPoint != null) {
                                                    // 如果起點未設置，設為起點
                                                    if (startPoint == null) {
                                                        startPoint = nearestPoint
                                                        this.startPoint = nearestPoint
                                                        invalidate()
                                                        v.performClick()
                                                        scope.launch {
                                                            snackbarHostState.showSnackbar("起點設置為: ${nearestPoint.name}")
                                                        }
                                                        return@setOnTouchListener true
                                                    } 
                                                    // 如果終點未設置，設為終點
                                                    else if (endPoint == null) {
                                                        endPoint = nearestPoint
                                                        this.endPoint = nearestPoint
                                                        invalidate()
                                                        v.performClick()
                                                        scope.launch {
                                                            snackbarHostState.showSnackbar("終點設置為: ${nearestPoint.name}")
                                                        }
                                                        return@setOnTouchListener true
                                                    }
                                                    // 如果都已設置，詢問要替換哪個
                                                    else {
                                                        scope.launch {
                                                            snackbarHostState.showSnackbar("請先清除起點或終點")
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                        
                                        isGestureInProgress = false
                                        postDelayed({ postInvalidateOnAnimation() }, 100)
                                        false
                                    }
                                    MotionEvent.ACTION_CANCEL -> {
                                        isGestureInProgress = false
                                        postDelayed({ postInvalidateOnAnimation() }, 100)
                                        false
                                    }
                                    MotionEvent.ACTION_MOVE -> {
                                        if (!isGestureInProgress) {
                                            isGestureInProgress = true
                                            postInvalidateOnAnimation()
                                        }
                                        false
                                    }
                                    else -> false
                                }
                            }

                            post {
                                imageSize = IntSize(drawable.intrinsicWidth, drawable.intrinsicHeight)

                                val overlayView = object : View(context) {
                                    init {
                                        setLayerType(View.LAYER_TYPE_HARDWARE, null)
                                    }

                                    override fun onDraw(canvas: Canvas) {
                                        super.onDraw(canvas)
                                        (touchImageViewRef.value as? MyCustomImageView)?.let { view ->
                                            // 繪製導航路徑
                                            view.navigationPath?.let { path ->
                                                view.drawNavigationPath(canvas, path.points)
                                            }
                                            
                                            // 繪製參考點
                                            view.drawReferencePointsOnCanvas(canvas, filteredReferencePoints)
                                            
                                            // 繪製起點和終點
                                            view.startPoint?.let { view.drawSpecialPoint(canvas, it, android.graphics.Color.GREEN, "S") }
                                            view.endPoint?.let { view.drawSpecialPoint(canvas, it, android.graphics.Color.RED, "E") }
                                        }
                                    }
                                }

                                setOnTouchImageViewListener(object : OnTouchImageViewListener {
                                    private var lastCallTime = 0L

                                    override fun onMove() {
                                        val currentTime = System.currentTimeMillis()
                                        if (currentTime - lastCallTime > 16) {
                                            overlayView.postInvalidateOnAnimation()
                                            lastCallTime = currentTime
                                        }
                                    }
                                })

                                (parent as? android.view.ViewGroup)?.addView(overlayView,
                                    android.view.ViewGroup.LayoutParams(
                                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                        android.view.ViewGroup.LayoutParams.MATCH_PARENT
                                    )
                                )
                            }
                        }
                    },
                    update = { view ->
                        if ((view as? MyCustomImageView)?.drawable?.constantState?.newDrawable()?.constantState !=
                            context.getDrawable(currentImageId)?.constantState) {
                            view.setImageResource(currentImageId)
                        }
                        
                        (view as? MyCustomImageView)?.let { mv ->
                            mv.overlayPoints = filteredReferencePoints
                            mv.currentImageId = currentImageId
                            mv.navigationPath = navigationPath
                            mv.startPoint = startPoint
                            mv.endPoint = endPoint
                            mv.highlightedPointId = highlightedPointId
                            mv.invalidate()
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // 導航模式提示
                if (navigationMode) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 16.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.9f),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "點擊地圖選擇起點和終點",
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }

            // 參考點列表
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp)
                    .weight(1f),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // 列表標題
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isListExpanded = !isListExpanded }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "教室列表 (${filteredReferencePoints.size})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // 展開/收起箭頭
                        Icon(
                            imageVector = if (isListExpanded)
                                Icons.Default.KeyboardArrowUp
                            else
                                Icons.Default.KeyboardArrowDown,
                            contentDescription = if (isListExpanded) "收起" else "展開",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    HorizontalDivider()

                    // 教室列表內容
                    AnimatedVisibility(
                        visible = isListExpanded,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        if (filteredReferencePoints.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp)
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (searchQuery.isEmpty()) 
                                        "此樓層暫無教室數據" 
                                    else 
                                        "沒有符合「$searchQuery」的搜索結果",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 100.dp, max = 400.dp)
                            ) {
                                items(filteredReferencePoints) { point ->
                                    ElevatedCard(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 4.dp)
                                            .clickable {
                                                // 在導航模式中點擊列表項可設置起點或終點
                                                if (navigationMode) {
                                                    if (startPoint == null) {
                                                        startPoint = point
                                                        customImageViewRef.value?.startPoint = point
                                                        customImageViewRef.value?.invalidate()
                                                        scope.launch {
                                                            snackbarHostState.showSnackbar("起點設置為: ${point.name}")
                                                        }
                                                    } else if (endPoint == null) {
                                                        endPoint = point
                                                        customImageViewRef.value?.endPoint = point
                                                        customImageViewRef.value?.invalidate()
                                                        scope.launch {
                                                            snackbarHostState.showSnackbar("終點設置為: ${point.name}")
                                                        }
                                                    } else {
                                                        scope.launch {
                                                            snackbarHostState.showSnackbar("請先清除起點或終點")
                                                        }
                                                    }
                                                } else {
                                                    // 非導航模式下，點擊高亮該點
                                                    highlightedPointId = if (highlightedPointId == point.id) null else point.id
                                                    customImageViewRef.value?.highlightedPointId = highlightedPointId
                                                    customImageViewRef.value?.invalidate()
                                                }
                                            },
                                        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .padding(8.dp)
                                                .fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            // 教室標記點
                                            Box(
                                                modifier = Modifier
                                                    .size(24.dp)
                                                    .clip(CircleShape)
                                                    .background(
                                                        if (point.id == highlightedPointId) 
                                                            Color.Yellow 
                                                        else 
                                                            getPointColor(point)
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = point.name.take(1),
                                                    color = Color.White,
                                                    fontSize = 12.sp
                                                )
                                            }

                                            Spacer(modifier = Modifier.width(8.dp))

                                            // 教室信息
                                            Column {
                                                Text(
                                                    text = point.name,
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = if (point.id == highlightedPointId) 
                                                        FontWeight.Bold 
                                                    else 
                                                        FontWeight.Normal
                                                )
                                                Text(
                                                    text = "位置: ${String.format(Locale.US, "%.1f", point.x)}%, ${String.format(Locale.US, "%.1f", point.y)}%",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }

                                            Spacer(modifier = Modifier.weight(1f))

                                            // 導航按鈕
                                            if (navigationMode) {
                                                Row {
                                                    // 設為起點按鈕
                                                    IconButton(onClick = {
                                                        startPoint = point
                                                        customImageViewRef.value?.startPoint = point
                                                        customImageViewRef.value?.invalidate()
                                                        scope.launch {
                                                            snackbarHostState.showSnackbar("起點設置為: ${point.name}")
                                                        }
                                                    }) {
                                                        Icon(
                                                            painter = painterResource(R.drawable.marker),
                                                            contentDescription = "設為起點",
                                                            tint = Color.Green,
                                                            modifier = Modifier.size(24.dp)
                                                        )
                                                    }
                                                    
                                                    // 設為終點按鈕
                                                    IconButton(onClick = {
                                                        endPoint = point
                                                        customImageViewRef.value?.endPoint = point
                                                        customImageViewRef.value?.invalidate()
                                                        scope.launch {
                                                            snackbarHostState.showSnackbar("終點設置為: ${point.name}")
                                                        }
                                                    }) {
                                                        Icon(
                                                            painter = painterResource(R.drawable.marker),
                                                            contentDescription = "設為終點",
                                                            tint = Color.Red,
                                                            modifier = Modifier.size(24.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                
                                // 底部間隔
                                item {
                                    Spacer(modifier = Modifier.height(80.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// 可用地圖資源
object AvailableMapImages {
    val maps = listOf(
        MapImage(R.drawable.se1, "綜合教學大樓 1F", 1),
        MapImage(R.drawable.se2, "綜合教學大樓 2F", 2),
        MapImage(R.drawable.se3, "綜合教學大樓 3F", 3),
        MapImage(R.drawable.sea4, "綜合教學大樓 4F", 4),
        MapImage(R.drawable.sea5, "綜合教學大樓 5F", 5)
    )
}

// ReferencePointDatabase 簡化實現
object ReferencePointDatabase {
    val availableMapImages = AvailableMapImages.maps
    
    private var instance: ReferencePointDatabaseImpl? = null
    
    fun getInstance(context: Context): ReferencePointDatabaseImpl {
        if (instance == null) {
            instance = ReferencePointDatabaseImpl(context)
        }
        return instance!!
    }
    
    class ReferencePointDatabaseImpl(private val context: Context) {
        private val sharedPreferences = context.getSharedPreferences("reference_points", Context.MODE_PRIVATE)
        var referencePoints: List<ReferencePoint> = emptyList()
        
        suspend fun addReferencePoint(point: ReferencePoint) {
            referencePoints = referencePoints + point
        }
        
        suspend fun deleteReferencePoint(id: String) {
            referencePoints = referencePoints.filter { it.id != id }
        }
    }
}
