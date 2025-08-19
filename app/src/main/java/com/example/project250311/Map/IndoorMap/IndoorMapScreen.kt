package com.example.project250311.Map.IndoorMap

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.project250311.R
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.ortiz.touchview.OnTouchImageViewListener
import com.ortiz.touchview.TouchImageView
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
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
    var navigationPath: NavigationPath? = null
    var startPoint: ReferencePoint? = null
    var endPoint: ReferencePoint? = null
    var highlightedPointId: String? = null

    // 修正路徑繪製問題 - 移除多地圖支援的錯誤邏輯
    fun drawNavigationPath(canvas: Canvas, pathPoints: List<ReferencePoint>) {
        if (pathPoints.size < 2) return
        
        val path = Path()
        
        pathPoints.forEachIndexed { index, point ->
            val bitmapWidth = drawable.intrinsicWidth.toFloat()
            val bitmapHeight = drawable.intrinsicHeight.toFloat()
            
            val pointXOnBitmap = (point.x / 100f * bitmapWidth).toFloat()
            val pointYOnBitmap = (point.y / 100f * bitmapHeight).toFloat()
            
            val mappedPoint = useTransformCoordBitmapToTouch(pointXOnBitmap, pointYOnBitmap)
            
            if (index == 0) {
                path.moveTo(mappedPoint.x, mappedPoint.y)
            } else {
                path.lineTo(mappedPoint.x, mappedPoint.y)
            }
        }
        
        canvas.drawPath(path, routePaint)
    }

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

    // 移除多地圖相關的函數

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

// 簡化路徑規劃算法，移除多地圖支援
fun findPath(points: List<ReferencePoint>, start: ReferencePoint, end: ReferencePoint): NavigationPath {
    // 簡單的直線路徑
    return NavigationPath(listOf(start, end), calculateDistance(start, end))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IndoorMapScreen() {
    val context = LocalContext.current
    var imageSize by remember { mutableStateOf(IntSize.Zero) }
    val touchImageViewRef = remember { mutableStateOf<TouchImageView?>(null) }
    val customImageViewRef = remember { mutableStateOf<MyCustomImageView?>(null) }
    
    // 新增狀態 - 移除 showDestinationInput，改為分別控制
    var isSearchExpanded by remember { mutableStateOf(false) }
    var searchSuggestions by remember { mutableStateOf<List<ReferencePoint>>(emptyList()) }
    var showStartSuggestions by remember { mutableStateOf(false) }
    var showDestinationSuggestions by remember { mutableStateOf(false) }
    
    // 簡化的搜索和導航狀態
    var startQuery by remember { mutableStateOf("") }
    var destinationQuery by remember { mutableStateOf("") }
    var startPoint by remember { mutableStateOf<ReferencePoint?>(null) }
    var endPoint by remember { mutableStateOf<ReferencePoint?>(null) }
    var navigationPath by remember { mutableStateOf<NavigationPath?>(null) }
    var highlightedPointId by remember { mutableStateOf<String?>(null) }
    
    // 固定顯示第一張地圖 (se1.png)
    val currentImageId = R.drawable.se1
    
    // 參考點列表
    var allReferencePoints by remember { mutableStateOf<List<ReferencePoint>>(emptyList()) }
    
    // Snackbar狀態
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    // 讀取參考點數據
    LaunchedEffect(Unit) {
        try {
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

    // 更新起點搜尋建議
    fun updateStartSearchSuggestions(query: String) {
        if (query.length >= 1) {
            searchSuggestions = allReferencePoints
                .filter { it.name.contains(query, ignoreCase = true) }
                .take(5)
            showStartSuggestions = searchSuggestions.isNotEmpty()
        } else {
            showStartSuggestions = false
        }
    }

    // 更新終點搜尋建議
    fun updateDestinationSearchSuggestions(query: String) {
        if (query.length >= 1) {
            searchSuggestions = allReferencePoints
                .filter { it.name.contains(query, ignoreCase = true) }
                .take(5)
            showDestinationSuggestions = searchSuggestions.isNotEmpty()
        } else {
            showDestinationSuggestions = false
        }
    }

    // 搜索起點
    fun searchStart() {
        val start = allReferencePoints.firstOrNull { 
            it.name.contains(startQuery, ignoreCase = true)
        }
        if (start != null) {
            startPoint = start
            highlightedPointId = start.id
            customImageViewRef.value?.highlightedPointId = start.id
            customImageViewRef.value?.startPoint = start
            customImageViewRef.value?.invalidate()
            
            scope.launch {
                snackbarHostState.showSnackbar("起點設定為: ${start.name}")
            }
        } else {
            scope.launch {
                snackbarHostState.showSnackbar("未找到起點: $startQuery")
            }
        }
    }

    // 搜索目的地
    fun searchDestination() {
        val destination = allReferencePoints.firstOrNull { 
            it.name.contains(destinationQuery, ignoreCase = true)
        }
        if (destination != null) {
            endPoint = destination
            
            scope.launch {
                snackbarHostState.showSnackbar("終點設定為: ${destination.name}")
            }
        } else {
            scope.launch {
                snackbarHostState.showSnackbar("未找到目的地: $destinationQuery")
            }
        }
    }

    // 規劃路線
    fun planRoute() {
        if (startPoint != null && endPoint != null) {
            navigationPath = findPath(allReferencePoints, startPoint!!, endPoint!!)
            
            customImageViewRef.value?.startPoint = startPoint
            customImageViewRef.value?.endPoint = endPoint
            customImageViewRef.value?.navigationPath = navigationPath
            customImageViewRef.value?.invalidate()
            
            val distance = "%.2f".format(navigationPath!!.totalDistance)
            scope.launch {
                snackbarHostState.showSnackbar("路徑規劃完成，總距離: $distance 單位")
            }
        } else {
            scope.launch {
                snackbarHostState.showSnackbar("請先設定起點和終點")
            }
        }
    }

    // 對調起點和終點
    fun swapStartAndEnd() {
        val tempPoint = startPoint
        val tempQuery = startQuery
        
        startPoint = endPoint
        startQuery = destinationQuery
        
        endPoint = tempPoint
        destinationQuery = tempQuery
        
        // 更新視圖
        customImageViewRef.value?.startPoint = startPoint
        customImageViewRef.value?.endPoint = endPoint
        
        // 如果兩個點都存在，重新規劃路線
        if (startPoint != null && endPoint != null) {
            planRoute()
        } else {
            customImageViewRef.value?.invalidate()
        }
        
        scope.launch {
            snackbarHostState.showSnackbar("起點和終點已對調")
        }
    }

    // 重置搜索
    fun resetSearch() {
        startQuery = ""
        destinationQuery = ""
        startPoint = null
        endPoint = null
        navigationPath = null
        highlightedPointId = null
        showStartSuggestions = false
        showDestinationSuggestions = false
        customImageViewRef.value?.startPoint = null
        customImageViewRef.value?.endPoint = null
        customImageViewRef.value?.navigationPath = null
        customImageViewRef.value?.highlightedPointId = null
        customImageViewRef.value?.invalidate()
    }

    // 主界面 - 移除TopAppBar
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
        ) {
            // 可收起的搜索區域
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
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
                            style = MaterialTheme.typography.titleMedium
                        )
                        Icon(
                            imageVector = if (isSearchExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (isSearchExpanded) "收起" else "展開"
                        )
                    }

                    // 展開的搜索內容
                    AnimatedVisibility(
                        visible = isSearchExpanded,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Column {
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            // 起點搜索
                            OutlinedTextField(
                                value = startQuery,
                                onValueChange = { 
                                    startQuery = it
                                    updateStartSearchSuggestions(it)
                                    showDestinationSuggestions = false
                                },
                                label = { Text("輸入起點") },
                                placeholder = { Text("例如: 大門") },
                                modifier = Modifier.fillMaxWidth(),
                                leadingIcon = { 
                                    Icon(
                                        imageVector = Icons.Default.LocationOn, 
                                        contentDescription = null,
                                        tint = Color.Green
                                    ) 
                                },
                                trailingIcon = {
                                    Button(
                                        onClick = { 
                                            focusManager.clearFocus()
                                            searchStart()
                                            showStartSuggestions = false
                                        },
                                        enabled = startQuery.isNotEmpty()
                                    ) {
                                        Text("設定起點")
                                    }
                                },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions.Default.copy(
                                    imeAction = ImeAction.Search
                                ),
                                keyboardActions = KeyboardActions(
                                    onSearch = {
                                        focusManager.clearFocus()
                                        if (startQuery.isNotEmpty()) {
                                            searchStart()
                                            showStartSuggestions = false
                                        }
                                    }
                                )
                            )

                            // 起點搜尋建議
                            AnimatedVisibility(visible = showStartSuggestions) {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 4.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                                    )
                                ) {
                                    LazyColumn(
                                        modifier = Modifier.height(200.dp)
                                    ) {
                                        items(searchSuggestions) { point ->
                                            Text(
                                                text = point.name,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable {
                                                        startQuery = point.name
                                                        showStartSuggestions = false
                                                        focusManager.clearFocus()
                                                        searchStart()
                                                    }
                                                    .padding(12.dp),
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            
                            // 終點搜索
                            OutlinedTextField(
                                value = destinationQuery,
                                onValueChange = { 
                                    destinationQuery = it
                                    updateDestinationSearchSuggestions(it)
                                    showStartSuggestions = false
                                },
                                label = { Text("輸入目的地") },
                                placeholder = { Text("例如: sec101") },
                                modifier = Modifier.fillMaxWidth(),
                                leadingIcon = { 
                                    Icon(
                                        imageVector = Icons.Default.LocationOn, 
                                        contentDescription = null,
                                        tint = Color.Red
                                    ) 
                                },
                                trailingIcon = {
                                    Button(
                                        onClick = { 
                                            focusManager.clearFocus()
                                            searchDestination()
                                            showDestinationSuggestions = false
                                        },
                                        enabled = destinationQuery.isNotEmpty()
                                    ) {
                                        Text("設定終點")
                                    }
                                },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions.Default.copy(
                                    imeAction = ImeAction.Search
                                ),
                                keyboardActions = KeyboardActions(
                                    onSearch = {
                                        focusManager.clearFocus()
                                        if (destinationQuery.isNotEmpty()) {
                                            searchDestination()
                                            showDestinationSuggestions = false
                                        }
                                    }
                                )
                            )

                            // 終點搜尋建議
                            AnimatedVisibility(visible = showDestinationSuggestions) {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 4.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                                    )
                                ) {
                                    LazyColumn(
                                        modifier = Modifier.height(200.dp)
                                    ) {
                                        items(searchSuggestions) { point ->
                                            Text(
                                                text = point.name,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable {
                                                        destinationQuery = point.name
                                                        showDestinationSuggestions = false
                                                        focusManager.clearFocus()
                                                        searchDestination()
                                                    }
                                                    .padding(12.dp),
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // 控制按鈕區域
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // 規劃路線按鈕
                                Button(
                                    onClick = { planRoute() },
                                    enabled = startPoint != null && endPoint != null,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("規劃路線")
                                }

                                // 對調按鈕
                                IconButton(
                                    onClick = { swapStartAndEnd() },
                                    enabled = startPoint != null || endPoint != null
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.SwapVert,
                                        contentDescription = "對調起點終點",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }

                                // 清除按鈕
                                IconButton(
                                    onClick = { resetSearch() }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Clear,
                                        contentDescription = "清除全部",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }

                            // 顯示當前設定的起點和終點
                            if (startPoint != null || endPoint != null) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    if (startPoint != null) {
                                        Surface(
                                            color = Color.Green.copy(alpha = 0.1f),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text(
                                                text = "起點: ${startPoint!!.name}",
                                                modifier = Modifier.padding(8.dp),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Color.Green
                                            )
                                        }
                                    }
                                    if (endPoint != null) {
                                        Surface(
                                            color = Color.Red.copy(alpha = 0.1f),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text(
                                                text = "目的地: ${endPoint!!.name}",
                                                modifier = Modifier.padding(8.dp),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Color.Red
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            
            // 地圖部分
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                AndroidView(
                    factory = { ctx ->
                        MyCustomImageView(ctx).apply {
                            setLayerType(View.LAYER_TYPE_HARDWARE, null)
                            customImageViewRef.value = this
                            touchImageViewRef.value = this
                            setImageResource(currentImageId)
                            maxZoom = 8f
                            minZoom = 0.5f
                            isZoomEnabled = true
                            setScaleType(android.widget.ImageView.ScaleType.MATRIX)

                            setOnTouchListener { _, event ->
                                when (event.action) {
                                    MotionEvent.ACTION_DOWN -> {
                                        isGestureInProgress = true
                                        postInvalidateOnAnimation()
                                        false
                                    }
                                    MotionEvent.ACTION_UP -> {
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
                                            
                                            // 繪製參考點（只顯示當前地圖的點）
                                            val currentMapPoints = allReferencePoints.filter { it.imageId == currentImageId }
                                            view.drawReferencePointsOnCanvas(canvas, currentMapPoints)
                                            
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
                        (view as? MyCustomImageView)?.let { mv ->
                            mv.overlayPoints = allReferencePoints.filter { it.imageId == currentImageId }
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
            }

            Spacer(modifier = Modifier.height(16.dp))
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