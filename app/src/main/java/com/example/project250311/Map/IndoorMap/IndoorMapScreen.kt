@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.project250311.Map.IndoorMap

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.TransformableState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.runtime.*
import androidx.navigation.NavHostController
import kotlinx.coroutines.flow.first
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.ui.res.colorResource
import com.example.project250311.Map.IndoorMap.Database.IndoorMapDatabase
import com.example.project250311.Map.IndoorMap.Database.ReferencePointEntity
import com.example.project250311.Map.IndoorMap.IndoorPathfinder.Grid
import com.example.project250311.R
import kotlinx.coroutines.*
import android.util.Log
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.project250311.Map.IndoorMap.IndoorPathfinder.aStar
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.MaterialTheme.colorScheme


// ======================= 主畫面 =======================
@Composable
fun IndoorMapScreen(
    navController: NavHostController? = null,
    modifier: Modifier = Modifier,
    positioningViewModel: IndoorPositioningViewModel,
    buildingId: String? = null,
    floorId: Int? = null,
    targetPointId: String? = null,
    entryPointId: String? = null,
    autoStart: Boolean = true,
) {
    val context = LocalContext.current
    val colorMaterial = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()

    // 取得移植過來的 ViewModel
    val positionState by positioningViewModel.positionState.collectAsState()

    val currentFloorName = positionState.mapGroupName

    // 用來儲存「目標教室」的資訊
    var targetRefPoint by remember { mutableStateOf<ReferencePointEntity?>(null) }
    var targetFloorName by remember { mutableStateOf<String?>(null) }

    // 決定要顯示「地圖」還是「8按鈕樓層選擇器」
    var showCrossFloorView by remember { mutableStateOf(!autoStart) }
    // 在 8按鈕 畫面中，使用者「正在預覽」哪一層樓的路徑
    var previewFloorName by remember { mutableStateOf<String?>(null) }

    // 直接訂閱 ViewModel 裡的重資料快取
    val imageBitmap by positioningViewModel.imageBitmap.collectAsState()
    val grid by positioningViewModel.grid.collectAsState()
    val overlay by positioningViewModel.overlay.collectAsState()
    val walkableCount by positioningViewModel.walkableCount.collectAsState()

    // 取得即時位置 (百分比)
    val currentPositionPercent = positionState.mapPercentage

    // DB 與 DAO（快取）
    val db = remember { IndoorMapDatabase.getDatabase(context) }
    val gridDao = remember(db) { db.gridCacheDao() }
    val refDao = remember(db) { db.referencePointDao() } // 新增：參考點 DAO


    val floorPlans =
        listOf(
            "SE1" to R.drawable.se1,
            "SE2" to R.drawable.se2,
            "SE3" to R.drawable.se3,
            "SEA4" to R.drawable.sea4,
            "SEA5" to R.drawable.sea5,
            "SEB4" to R.drawable.seb4,
            "SEC4" to R.drawable.sec4,
            "SEC5" to R.drawable.sec5
        )

    var expanded by remember { mutableStateOf(false) }
    var selectedFloorName by remember { mutableStateOf(floorPlans.first().first) }
    var currentImageRes by remember { mutableStateOf(floorPlans.first().second) }


    // 導航狀態
    var gridSample by remember { mutableStateOf(2) } // 2 或 3
    var start by remember { mutableStateOf<Offset?>(null) }
    var goal by remember { mutableStateOf<Offset?>(null) }
    var path by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var showGridOverlay by remember { mutableStateOf(false) }

    // Debug / telemetry
    var debugInfo by remember { mutableStateOf("") }
    var startGridCell by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var goalGridCell by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var startWalkable by remember { mutableStateOf<Boolean?>(null) }
    var goalWalkable by remember { mutableStateOf<Boolean?>(null) }

    // 顯示教室點
    var showClassrooms by remember { mutableStateOf(false) }
    var classroomPoints by remember { mutableStateOf<List<ReferencePointEntity>>(emptyList()) }

    // 視窗互動狀態
    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }
    val transformState = remember {
        TransformableState { zoom, pan, _ ->
            scale = (scale * zoom).coerceIn(0.5f, 6f)
            offsetX += pan.x
            offsetY += pan.y
        }
    }

    // (★) 記住 Canvas 的尺寸，給「一鍵尋找」按鈕用
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    // (★) 記住動畫狀態
    val animScale = remember { Animatable(1f) }
    val animOffsetX = remember { Animatable(0f) }
    val animOffsetY = remember { Animatable(0f) }

    // 這個 Effect 只在「導航模式」下啟動
    LaunchedEffect(targetPointId, entryPointId, autoStart) {

        // 1. 只在「導航模式」(autoStart=true) 且有「目標」時才執行
        if (!autoStart || targetPointId == null) {
            return@LaunchedEffect
        }

        Log.d("IndoorMapScreen", "導航模式啟動：目標 $targetPointId")

        try {
            // 2. 找出「目標點」的資料 (用 ID 查比較準)
            val targetEntity = withContext(Dispatchers.IO) {
                refDao.getPointById(targetPointId)
            }
            if (targetEntity == null) {
                debugInfo = "錯誤：在DB中找不到目標 $targetPointId"
                return@LaunchedEffect
            }

            // 3. 找出「目標樓層」的 groupName (e.g., "sec4")
            val floorName = floorPlans.find { it.second == targetEntity.imageId }?.first
            if (floorName == null) {
                debugInfo = "錯誤：找不到 $targetPointId 對應的樓層名稱"
                return@LaunchedEffect
            }

            Log.d("IndoorMapScreen", "目標在 $floorName。強制 ViewModel 載入此地圖。")

            // 4. (★) 關鍵修復 1: 強制 ViewModel 載入「目標地圖」
            // 這會觸發 imageBitmap, grid, overlay 重新載入
            positioningViewModel.loadMapData(floorName.lowercase())

            // 5. (★) 關鍵修復 2: 強制 UI 重置 (解決「停留在上次操作」)
            scale = 1f
            offsetX = 0f
            offsetY = 0f

            // 6. (★) 關鍵修復 3: 確保我們顯示的是「地圖」
            // (解決「顯示8個按鈕」的問題)
            showCrossFloorView = false
            previewFloorName = null // 清除預覽狀態

            // 7. (★) 關鍵修復 4: 同步更新頂部下拉選單的顯示
            selectedFloorName = floorName
            currentImageRes = targetEntity.imageId

            // 8. (★) 關鍵修復 5: 設定 A* 演算法的起點和終點
            //    我們需要等待 ViewModel 載入新的地圖 (grid)
            var newGrid = grid // 讀取當前的 grid
            var attempt = 0
            while (newGrid == null || newGrid.w == 0 || imageBitmap == null) {
                delay(100) // 等 0.1 秒
                newGrid = grid // 重新讀取
                attempt++
                if (attempt > 30) { // 等超過 3 秒
                    debugInfo = "錯誤：等待 $floorName 網格載入超時"
                    return@LaunchedEffect
                }
            }

            Log.d("IndoorMapScreen", "網格已載入。開始設定路徑。")

            // 9. 找出「入口點」
            val entryRef: ReferencePointEntity? = withContext(Dispatchers.IO) {
                if (entryPointId != null && entryPointId.isNotBlank()) {
                    Log.d("IndoorMapScreen", "正在尋找指定的入口 ID: $entryPointId")

                    // (★) (★) (★) 這是【修正點 1】(★) (★) (★)
                    // 之前這裡是錯的 searchReferencePointsByName
                    refDao.getPointById(entryPointId) // (★) 改成用 ID 查詢！

                } else {
                    // 如果沒指定入口，就自動找該樓層的樓梯/電梯
                    Log.d("IndoorMapScreen", "未指定入口，自動尋找 $floorName 的樓梯/電梯")
                    val floorImageId = floorPlans.find { it.first == floorName }?.second ?: 0

                    // (★) (★) (★) 這是【修正點 2】(★) (★) (★)
                    // 呼叫我們新增的 getTransitionPointsByImageId (這會回傳一個 Flow)
                    val transitionPoints = refDao.getTransitionPointsByImageId(floorImageId).first()
                    transitionPoints.firstOrNull() // (★) 取第一個找到的樓梯/電梯
                }
            }

            if (entryRef == null) {
                debugInfo = "錯誤：在 $floorName 上找不到任何入口點(樓梯/電梯)"
                Log.e("IndoorMapScreen", "entryRef is NULL. 檢查 .txt 檔是否已加入 STAIRS/ELEVATOR 點位")
                return@LaunchedEffect
            }

            // 10. 取得地圖 bitmap 來計算座標
            val bmp = imageBitmap?.asAndroidBitmap()
            if (bmp == null) {
                debugInfo = "錯誤：Bitmap 為空，無法計算路徑"
                return@LaunchedEffect
            }

            // 11. (★) 設定路徑並觸發計算
            // (這會自動觸發 recomputePathAsync)
            start = Offset((entryRef.x.toFloat() / 100f) * bmp.width, (entryRef.y.toFloat() / 100f) * bmp.height)
            goal = Offset((targetEntity.x.toFloat() / 100f) * bmp.width, (targetEntity.y.toFloat() / 100f) * bmp.height)

            Log.d("IndoorMapScreen", "路徑設定: Start (${start?.x}, ${start?.y}), Goal (${goal?.x}, ${goal?.y})")

        } catch (e: Exception) {
            debugInfo = "錯誤：載入導航時失敗: ${e.message}"
            Log.e("IndoorMapScreen", "導航 LaunchedEffect 失敗", e)
        }
    }
    // (★) (★) (★) 新增區塊結束 (★) (★) (★)

    // 根據 ViewModel 的位置，自動切換樓層
    LaunchedEffect(positionState.mapGroupName, autoStart) { // (★ 1. 把 autoStart 加入監聽)

        // 如果在「瀏覽模式」(autoStart=false)，就「永遠不要」讓即時位置切換地圖
        // 這樣使用者才能自由點擊 8 按鈕或下拉選單
        if (!autoStart) {
            return@LaunchedEffect
        }

        // 以下是 autoStart=true (導航模式) 才要跑的邏輯
        val liveMapGroup = positionState.mapGroupName ?: return@LaunchedEffect
        // 透過 mapGroupName 找到對應的圖片資源 ID
        val liveImageRes = IndoorPathfinder.getMapDrawableResId(liveMapGroup) // (這段假設你有 IndoorPathfinder 物件)

        // (這段也需要 floorPlans，你原本就有)
        val floorName = floorPlans.find { it.second == liveImageRes }?.first ?: "Unknown"

        if (liveImageRes != 0 && liveImageRes != currentImageRes) {
            // (★) 強制切換UI狀態
            selectedFloorName = floorName
            currentImageRes = liveImageRes

            // 清空舊樓層的導航狀態
            start = null
            goal = null
            path = emptyList()
        }
    }

    // (★) 當`transformable`手勢更新時，同步更新動畫狀態，避免衝突
    LaunchedEffect(scale, offsetX, offsetY) {
        scope.launch { animScale.snapTo(scale) }
        scope.launch { animOffsetX.snapTo(offsetX) }
        scope.launch { animOffsetY.snapTo(offsetY) }
    }


    // 工具函式
    fun screenToImage(p: Offset): Offset? {
        val bmp = imageBitmap ?: return null
        val ix = ((p.x - offsetX) / scale)
        val iy = ((p.y - offsetY) / scale)
        if (ix < 0 || iy < 0 || ix >= bmp.width || iy >= bmp.height) return null
        return Offset(ix, iy)
    }
    fun imageToGrid(pt: Offset, g: Grid, s: Int): Pair<Int, Int> {
        val gx = (pt.x / s).toInt()
        val gy = (pt.y / s).toInt()
        return gx to gy
    }

    // 依當前樓層圖片載入/清除教室點
    LaunchedEffect(currentImageRes, showClassrooms) {
        if (showClassrooms) {
            refDao.getClassroomPointsByImageId(currentImageRes).collect { classroomPoints = it }
        } else {
            classroomPoints = emptyList()
        }
    }

    // ===== (3) 路徑計算搬到背景執行緒 =====
    fun recomputePathAsync() {
        val g = grid ?: return
        val sPt = start ?: return
        val ePt = goal ?: return
        val (sx, sy) = imageToGrid(sPt, g, gridSample)
        val (gx, gy) = imageToGrid(ePt, g, gridSample)

        scope.launch(Dispatchers.Default) {
            try {
                // set grid cell debug info
                startGridCell = sx to sy
                goalGridCell = gx to gy
                startWalkable = g.walkable(sx, sy)
                goalWalkable = g.walkable(gx, gy)

                if (!startWalkable!! || !goalWalkable!!) {
                    val msg = "startWalkable=${startWalkable} goalWalkable=${goalWalkable}"
                    Log.d("IndoorMap", "recomputePathAsync abort: $msg")
                    withContext(Dispatchers.Main) {
                        debugInfo = "無法計算：起點/終點不可通行 ($msg)"
                        path = emptyList()
                    }
                    return@launch
                }

                val t0 = System.currentTimeMillis()
                val raw = aStar(g, sx, sy, gx, gy)
                val took = System.currentTimeMillis() - t0
                Log.d("IndoorMap", "aStar finished size=${raw.size} took=${took}ms grid=${g.w}x${g.h}")

                if (raw.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        debugInfo = "aStar 無路徑 (計算 ${took}ms)"
                        path = emptyList()
                    }
                    return@launch
                }
                // (★) 呼叫 IndoorPathfinder 的函數
                val vis = IndoorPathfinder.smoothByVisibility(raw, g)
                val px = vis.map { node -> Offset((node.x + 0.5f) * gridSample, (node.y + 0.5f) * gridSample) }
                // (★) 呼叫 IndoorPathfinder 的函數
                val simplified = IndoorPathfinder.rdp(px, eps = (gridSample * 0.75f))
                withContext(Dispatchers.Main) {
                    path = simplified
                    debugInfo = "找到路徑: raw=${raw.size} vis=${vis.size} simplified=${simplified.size} (${took}ms)"
                }
            } catch (e: CancellationException) {
                Log.d("IndoorMap", "recomputePathAsync cancelled")
            } catch (e: Exception) {
                Log.e("IndoorMap", "recomputePathAsync error", e)
                withContext(Dispatchers.Main) {
                    debugInfo = "計算錯誤: ${e.localizedMessage}"
                    path = emptyList()
                }
            }
        }
    }

    // 影像座標 -> 螢幕座標（用於畫點/路徑時）
    fun imgToScreen(p: Offset) = Offset(p.x * scale + offsetX, p.y * scale + offsetY)


    // (★) 1. 取得「目標教室」的資料
    LaunchedEffect(targetPointId, autoStart) {
        if (targetPointId == null || !autoStart) return@LaunchedEffect
        try {
            // (★) 從 DB 找出目標教室的實體
            val targetEntity = withContext(Dispatchers.IO) {
                refDao.searchReferencePointsByName("%$targetPointId%").first().firstOrNull()
            }
            if (targetEntity != null) {
                // (★) 儲存目標教室的資料
                targetRefPoint = targetEntity
                // (★) 找出它對應的樓層名稱 (例如 "sec4")
                val floorName = floorPlans.find { it.second == targetEntity.imageId }?.first
                targetFloorName = floorName
            }
        } catch (e: Exception) {
            debugInfo = "錯誤：找不到目標 $targetPointId"
        }
    }

    // (★) 2. 判斷「目前樓層」和「目標樓層」是否一致
    LaunchedEffect(currentFloorName, targetFloorName, autoStart, imageBitmap, currentPositionPercent, targetRefPoint) {

        if (autoStart) {
            return@LaunchedEffect
        }

        if (!autoStart || targetFloorName == null || imageBitmap == null) {
            // 如果不是在導航模式，或者目標還沒載入，或者圖片還沒載入，就什麼都不做
            return@LaunchedEffect
        }

        if (currentFloorName != null && currentFloorName == targetFloorName) {
            // (★) 情況 A: 同樓層！
            showCrossFloorView = false    // 隱藏 8 按鈕
            previewFloorName = null       // 清除預覽

            // (★) 直接設定起點 (目前位置) 和終點 (目標教室)
            val bmp = imageBitmap?.asAndroidBitmap() ?: return@LaunchedEffect
            start = currentPositionPercent?.let { Offset((it.x / 100f) * bmp.width, (it.y / 100f) * bmp.height) }
            goal = targetRefPoint?.let { Offset((it.x.toFloat() / 100f) * bmp.width, (it.y.toFloat() / 100f) * bmp.height) }

            // recomputePathAsync() 會因為 start/goal 改變而自動被觸發

        } else {
            // (★) 情況 B: 不同樓層！
            showCrossFloorView = true // 顯示 8 按鈕
            // (★) 預設先預覽「目前所在樓層」的路徑 (例如 "se1" 樓)
            previewFloorName = currentFloorName
        }
    }

    // (★) 3. 專門給「跨樓層預覽」計算路徑
    LaunchedEffect(previewFloorName, grid, currentPositionPercent, targetRefPoint) {
        // 必須在「跨樓層模式」且「grid 載入完成」才執行
        if (!showCrossFloorView || previewFloorName == null || grid == null) {
            path = emptyList() // 清除路徑
            return@LaunchedEffect
        }

        val bmp = imageBitmap?.asAndroidBitmap() ?: return@LaunchedEffect

        // (★) 找出這層樓的「樓梯/電梯」在哪
        val floorImageId = floorPlans.find { it.first == previewFloorName }?.second ?: 0
        val allPointsOnFloor = withContext(Dispatchers.IO) { refDao.getReferencePointsByImageId(floorImageId).first() }
        val transitionPoint = allPointsOnFloor.firstOrNull { it.type == "STAIRS" || it.type == "ELEVATOR" }

        if (transitionPoint == null) {
            debugInfo = "錯誤: 樓層 $previewFloorName 找不到樓梯/電梯"
            path = emptyList()
            return@LaunchedEffect
        }

        // (★) 轉換樓梯的座標
        val transitionOffset = Offset((transitionPoint.x.toFloat() / 100f) * bmp.width, (transitionPoint.y.toFloat() / 100f) * bmp.height)

        // (★) 根據預覽的是「起點樓層」還是「終點樓層」來設定 A* 的起點/終點
        when (previewFloorName) {
            currentFloorName -> {
                // 情況 A: 正在預覽「起點樓層」
                // 路徑： (目前位置) -> (樓梯)
                start = currentPositionPercent?.let { Offset((it.x / 100f) * bmp.width, (it.y / 100f) * bmp.height) }
                goal = transitionOffset
            }
            targetFloorName -> {
                // 情況 B: 正在預覽「終點樓層」
                // 路徑： (樓梯) -> (目標教室)
                start = transitionOffset
                goal = targetRefPoint?.let { Offset((it.x.toFloat() / 100f) * bmp.width, (it.y.toFloat() / 100f) * bmp.height) }
            }
            else -> {
                // 情況 C: 正在預覽中間樓層 (例如從 1F -> 5F, 正在看 3F)
                // 這裡可以顯示 樓梯 -> 樓梯，但我們先簡化
                start = null
                goal = null
                path = emptyList()
            }
        }

        // (★) 呼叫 A* 演算法
        recomputePathAsync()
    }

    /*
    // 當透過外部參數 (targetPointId) 呼叫時，自動載入目標與入口並計算路徑
    LaunchedEffect(targetPointId, entryPointId, imageBitmap) {
        if (targetPointId == null || !autoStart) return@LaunchedEffect
        val bmp = imageBitmap?.asAndroidBitmap() ?: return@LaunchedEffect
        val targetEntity = withContext(Dispatchers.IO) {
            refDao.searchReferencePointsByName("%$targetPointId%").first().firstOrNull()
        }
        if (targetEntity == null) return@LaunchedEffect

        // (★) 檢查 ViewModel 當前載入的地圖是否就是目標地圖
        val currentMapResByVM =
            positionState.mapGroupName?.let { IndoorPathfinder.getMapDrawableResId(it) }
        if (currentMapResByVM == targetEntity.imageId) {
            try {
                val all = withContext(Dispatchers.IO) { refDao.getAllReferencePoints().first() }

                val entryEntity =
                    if (!entryPointId.isNullOrBlank()) all.firstOrNull { it.id == entryPointId } else {
                        all.firstOrNull {
                            it.buildingId == targetEntity.buildingId && it.floorId == targetEntity.floorId && it.type.equals(
                                "ENTRANCE",
                                true
                            )
                        }
                    }

                val s = entryEntity?.let {
                    Offset(
                        (it.x.toFloat() / 100f) * bmp.width,
                        (it.y.toFloat() / 100f) * bmp.height
                    )
                }
                val g = Offset(
                    (targetEntity.x.toFloat() / 100f) * bmp.width,
                    (targetEntity.y.toFloat() / 100f) * bmp.height
                )

                start = s
                goal = g

                // (★) recomputePathAsync 依賴 grid，而 grid 是由 ViewModel 提供的
                //    我們需要檢查 grid 是否也載入完成了
                if (grid != null) {
                    recomputePathAsync()
                }

            } catch (e: Exception) {
                // 忽略錯誤，UI 可顯示或回傳
            }
        }
    }

    // 當 start/goal 與 grid 都就緒時，自動計算路徑（補強：避免 target/entry 的 LaunchedEffect 在 grid 構建前就呼叫 recomputePathAsync）
    LaunchedEffect(start, goal, grid) {
        if (start != null && goal != null && grid != null) {
            recomputePathAsync()
        }
    }

    */

    Scaffold(
        topBar = {
            TopAppBar(
                /*
                title = {
                    val percentString = currentPositionPercent?.let { point ->
                        " (%.1f, %.1f)".format(point.x, point.y)
                    } ?: ""
                    Text("平面圖導航 Demo", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium) },
                */
                title = {
                    val titleText = if (showCrossFloorView) "預覽樓層: $previewFloorName" else "室內導航"
                    Text(titleText, fontWeight = FontWeight.SemiBold)
                },
                actions = {
                    // 樓層下拉
                    ExposedDropdownMenuBox(
                            expanded = expanded,
                            onExpandedChange = { expanded = !expanded }
                    ) {
                        TextButton(
                                onClick = { expanded = true },
                                modifier = Modifier.menuAnchor()
                        ) { Text(selectedFloorName) }
                        ExposedDropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false }
                        ) {
                            floorPlans.forEach { (name, resId) ->
                                DropdownMenuItem(
                                    text = { Text(name) },
                                    onClick = {
                                        selectedFloorName = name
                                        currentImageRes = resId
                                        expanded = false
                                        // 當手動切換時，
                                        // 呼叫 ViewModel 去載入新地圖
                                        positioningViewModel.loadMapData(name.lowercase())
                                        // 清除手動路徑
                                        start = null
                                        goal = null
                                        path = emptyList()
                                    }
                                )
                            }
                        }
                    }

                            // 建網格 -> 改為 重建網格（手動覆蓋快取）
//                            TextButton(
//                                    onClick = {
//                                        val bmp =
//                                                imageBitmap?.asAndroidBitmap() ?: return@TextButton
//                                        scope.launch(Dispatchers.Default) {
//                                            val g =
//                                                    bitmapToGridFromWhiteCorridor(
//                                                            bitmap = bmp,
//                                                            sample = gridSample,
//                                                            satMax = 0.12f,
//                                                            valMin = 0.92f,
//                                                            wallInflate = 3
//                                                    )
//                                            val ov = buildGridOverlayBitmap(g)
//                                            withContext(Dispatchers.Main) {
//                                                grid = g
//                                                overlay = ov
//                                                start = null
//                                                goal = null
//                                                path = emptyList()
//                                                walkableCount = g.cells.count { it }
//                                            }
//                                            val packed = g.cells.toBitPackedBytes()
//                                            withContext(Dispatchers.IO) {
//                                                gridDao.upsert(
//                                                        GridCacheEntity(
//                                                                imageId = currentImageRes,
//                                                                sample = gridSample,
//                                                                width = g.w,
//                                                                height = g.h,
//                                                                cells = packed
//                                                        )
//                                                )
//                                            }
//                                        }
//                                    }
//                            ) { Text("重建網格") }
//
//                            // 顯示/隱藏網格覆蓋
//                            TextButton(onClick = { showGridOverlay = !showGridOverlay }) {
//                                Text(if (showGridOverlay) "隱藏網格" else "顯示網格")
//                            }
//
//                            // 新增：顯示/隱藏 教室點
//                            TextButton(onClick = { showClassrooms = !showClassrooms }) {
//                                Text(if (showClassrooms) "隱藏教室" else "顯示教室")
//                            }
//
                            // 偵錯：可走格數
                    Text(
                        "walkable=$walkableCount",
                        style = MaterialTheme.typography.labelSmall
                    )

                        // 清除
                    TextButton(
                            onClick = {
                                start = null
                                goal = null
                                path = emptyList()
                            }
                    ) { Text("清除路徑") }
                }
            )
        }
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .padding(padding)
                .onGloballyPositioned { canvasSize = it.size }
        ) {

            if (showCrossFloorView) {
                // --- 情況 A: 顯示 8 按鈕樓層選擇器 ---
                CrossFloorSelection(
                    // floorPlans = floorPlans.map { it.first }, // (★ 1. 移除或註解掉這行)
                    startFloor = currentFloorName,            // (保留)
                    targetFloor = targetFloorName,          // (保留)
                    onFloorSelected = { floorName ->
                        // (★) 1. 告訴 ViewModel 去載入這張地圖
                        positioningViewModel.loadMapData(floorName.lowercase())

                        // (★) 2. 更新 TopAppBar 的標題
                        previewFloorName = floorName
                        selectedFloorName = floorName // (★) 同步更新 TopAppBar 下拉選單的狀態
                        val resId = floorPlans.find { it.first == floorName }?.second
                        if (resId != null) {
                            currentImageRes = resId
                        }

                        // (★) 3. 隱藏 8 按鈕介面，切換回地圖畫面
                        showCrossFloorView = false

                        // (★) 4. 清除可能殘留的舊路徑
                        start = null
                        goal = null
                        path = emptyList()
                    }
                )

            } else if (imageBitmap != null) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(padding)
                        .onGloballyPositioned { canvasSize = it.size }
                ) {
                    imageBitmap?.let { bmp ->
                        Box(
                            Modifier
                                .fillMaxSize()
                                .clipToBounds()
                                .transformable(transformState)
                                .pointerInput(Unit) {
                                    detectTapGestures(
                                        onDoubleTap = { p ->
                                            val ip =
                                                screenToImage(p) ?: return@detectTapGestures
                                            if (start == null) start = ip
                                            else if (goal == null) {
                                                goal = ip
                                                recomputePathAsync()
                                            } else {
                                                start = ip
                                                goal = null
                                                path = emptyList()
                                            }
                                        }
                                    )
                                }
                        ) {
                            // ===== (1) 單一 Canvas，且只畫「可見區域」 =====
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                // 可見區域（影像座標系）
                                val invScale = 1f / scale
                                val visLeft = (-offsetX) * invScale
                                val visTop = (-offsetY) * invScale
                                val visRight = (size.width - offsetX) * invScale
                                val visBottom = (size.height - offsetY) * invScale

                                val srcLeft = visLeft.coerceIn(0f, bmp.width.toFloat())
                                val srcTop = visTop.coerceIn(0f, bmp.height.toFloat())
                                val srcRight = visRight.coerceIn(0f, bmp.width.toFloat())
                                val srcBottom = visBottom.coerceIn(0f, bmp.height.toFloat())

                                val dstLeft = srcLeft * scale + offsetX
                                val dstTop = srcTop * scale + offsetY
                                val dstRight = srcRight * scale + offsetX
                                val dstBottom = srcBottom * scale + offsetY

                                val srcW = (srcRight - srcLeft).coerceAtLeast(0f)
                                val srcH = (srcBottom - srcTop).coerceAtLeast(0f)
                                val dstW = (dstRight - dstLeft).coerceAtLeast(0f)
                                val dstH = (dstBottom - dstTop).coerceAtLeast(0f)

                                // 背景圖：只畫可見塊
                                if (srcW > 0f && srcH > 0f && dstW > 0f && dstH > 0f) {
                                    drawImage(
                                        image = bmp,
                                        srcOffset = IntOffset(srcLeft.toInt(), srcTop.toInt()),
                                        srcSize = IntSize(srcW.toInt(), srcH.toInt()),
                                        dstOffset = IntOffset(dstLeft.toInt(), dstTop.toInt()),
                                        dstSize = IntSize(dstW.toInt(), dstH.toInt())
                                    )

                                    // Overlay：用自己的像素座標裁切（grid 像素）
                                    val g = grid
                                    val ov = overlay
                                    if (showGridOverlay && g != null && ov != null) {
                                        // 取樣倍率：原始影像像素 / Grid 像素（通常 = gridSample）
                                        val ovScaleX = imageBitmap!!.width / g.w.toFloat()
                                        val ovScaleY = imageBitmap!!.height / g.h.toFloat()

                                        // 把背景圖的 src 矩形換算成 overlay 的 src 矩形（各自用自己的座標系）
                                        val oSrcLeft =
                                            (srcLeft / ovScaleX).coerceIn(0f, g.w.toFloat())
                                        val oSrcTop =
                                            (srcTop / ovScaleY).coerceIn(0f, g.h.toFloat())
                                        val oSrcRight =
                                            (srcRight / ovScaleX).coerceIn(0f, g.w.toFloat())
                                        val oSrcBottom =
                                            (srcBottom / ovScaleY).coerceIn(0f, g.h.toFloat())

                                        val oSrcW = (oSrcRight - oSrcLeft).coerceAtLeast(0f)
                                        val oSrcH = (oSrcBottom - oSrcTop).coerceAtLeast(0f)

                                        if (oSrcW > 0f && oSrcH > 0f) {
                                            drawImage(
                                                image = ov,
                                                srcOffset = IntOffset(
                                                    oSrcLeft.toInt(),
                                                    oSrcTop.toInt()
                                                ),
                                                srcSize = IntSize(oSrcW.toInt(), oSrcH.toInt()),
                                                // 目的地矩形仍用背景圖的 dst，這樣就能貼齊
                                                dstOffset = IntOffset(
                                                    dstLeft.toInt(),
                                                    dstTop.toInt()
                                                ),
                                                dstSize = IntSize(dstW.toInt(), dstH.toInt())
                                            )
                                        }
                                    }
                                }

                                // 可視樣式大小（可依需求微調）
                                val classroomRadius = 10f
                                val startGoalRadius = 14f
                                val pathWidth = 8f

                                // 起訖點（將影像座標轉螢幕座標後繪製）
                                start?.let {
                                    val s = imgToScreen(it)
                                    drawCircle(
                                        color = colorMaterial.primary,
                                        radius = startGoalRadius,
                                        center = s
                                    )
                                }
                                goal?.let {
                                    val g2 = imgToScreen(it)
                                    drawCircle(
                                        color = colorMaterial.tertiary,
                                        radius = startGoalRadius,
                                        center = g2
                                    )
                                }

                                // 路徑（逐點轉換成螢幕座標繪製）
                                if (path.isNotEmpty()) {
                                    val p =
                                        Path().apply {
                                            val first = imgToScreen(path.first())
                                            moveTo(first.x, first.y)
                                            for (i in 1 until path.size) {
                                                val sp = imgToScreen(path[i])
                                                lineTo(sp.x, sp.y)
                                            }
                                        }
                                    drawPath(
                                        path = p,
                                        color = colorMaterial.secondary,
                                        style = Stroke(width = pathWidth)
                                    )
                                }

                                // 教室點（影像百分比 -> 影像像素 -> 螢幕座標）
                                if (showClassrooms && classroomPoints.isNotEmpty()) {
                                    classroomPoints.forEach { rp ->
                                        val imgX = (rp.x.toFloat() / 100f) * bmp.width
                                        val imgY = (rp.y.toFloat() / 100f) * bmp.height
                                        val scr = imgToScreen(Offset(imgX, imgY))
                                        drawCircle(
                                            color = colorMaterial.primary.copy(alpha = 0.9f),
                                            radius = classroomRadius,
                                            center = scr
                                        )
                                    }
                                }
                                currentPositionPercent?.let { percent ->
                                    // 檢查當前顯示的地圖 (currentImageRes)
                                    // 是否就是定位到的地圖 (positionState.mapGroupName)
                                    val liveMapRes = positionState.mapGroupName?.let {
                                        IndoorPathfinder.getMapDrawableResId(it)
                                    }

                                    if (liveMapRes == currentImageRes) {
                                        // 1. 百分比 -> 影像像素
                                        val imgX = (percent.x / 100.0f) * bmp.width
                                        val imgY = (percent.y / 100.0f) * bmp.height

                                        // 2. 影像像素 -> 螢幕座標
                                        val scr = imgToScreen(Offset(imgX, imgY))

                                        // 3. 畫出藍點 (三層圓)
                                        // 透明大圈
                                        drawCircle(
                                            color = colorMaterial.primary.copy(alpha = 0.2f),
                                            radius = 40f, // (★) 縮小一點，才不會太大
                                            center = scr
                                        )
                                        // 實心藍點
                                        drawCircle(
                                            color = colorMaterial.primary,
                                            radius = 20f,
                                            center = scr
                                        )
                                        // 白色圓心
                                        drawCircle(
                                            color = androidx.compose.ui.graphics.Color.White,
                                            radius = 8f,
                                            center = scr
                                        )
                                    }
                                }
                            }
                        }
                    }
                        ?: run {
                            Box(
                                Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) { Text("尚未載入平面圖資源") }
                        }

                    // 左下角返回室外地圖按鈕
                    FloatingActionButton(
                        onClick = {
                            if (autoStart) {
                                // --- 導航模式 (從室外進來) ---
                                // 點擊一律返回室外地圖
                                navController?.popBackStack()
                            } else {
                                // --- 瀏覽模式 (從分頁進來) ---
                                if (showCrossFloorView) {
                                    // 1. 如果「正在」顯示8按鈕，就返回室外地圖
                                    navController?.popBackStack()
                                } else {
                                    // 2. 如果「正在」顯示地圖，就返回8按鈕介面
                                    showCrossFloorView = true

                                    // 清除路徑並重置地圖到「目前所在樓層」
                                    start = null
                                    goal = null
                                    path = emptyList()
                                    previewFloorName = currentFloorName // 重置預覽

                                    // 確保 ViewModel 載入的是「目前所在」的樓層地圖
                                    positioningViewModel.loadMapData(currentFloorName ?: "SE1")
                                }
                            }
                        },
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(12.dp)
                            .zIndex(3f),
                        containerColor = colorScheme.primary
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "返回地圖",
                            tint = colorScheme.onPrimary
                        )
                    }

                    // 「一鍵尋找」按鈕
                    FloatingActionButton(
                        onClick = {
                            val locationPercent = positionState.mapPercentage
                            val bmp = imageBitmap

                            // 檢查：有位置、有地圖、有拿到Canvas尺寸
                            if (locationPercent != null && bmp != null && canvasSize != IntSize.Zero) {
                                scope.launch {
                                    val targetScale = 8f // 放大8倍

                                    // 1. 百分比 -> 影像像素
                                    val imgX = (locationPercent.x / 100.0f) * bmp.width
                                    val imgY = (locationPercent.y / 100.0f) * bmp.height

                                    // 2. 計算目標 offset (置中)
                                    //    要用「目標縮放 8f」去計算，而不是用「目前縮放」
                                    val targetOffsetX = (canvasSize.width / 2f) - (imgX * targetScale)
                                    val targetOffsetY = (canvasSize.height / 2f) - (imgY * targetScale)

                                    // 3. 用 Animatable 產生平滑動畫
                                    launch {
                                        animScale.animateTo(targetScale, spring()) {
                                            scale = value // 在動畫過程中同步更新狀態
                                        }
                                    }
                                    launch {
                                        animOffsetX.animateTo(targetOffsetX, spring()) {
                                            offsetX = value
                                        }
                                    }
                                    launch {
                                        animOffsetY.animateTo(targetOffsetY, spring()) {
                                            offsetY = value
                                        }
                                    }
                                }
                            }
                        },
                        containerColor = colorScheme.secondaryContainer,
                        contentColor = colorScheme.onSecondaryContainer
                    ) {
                        Icon(Icons.Default.MyLocation, contentDescription = "Find Me")
                    }

                    // 顯示 debug 訊息（若有）
                    if (debugInfo.isNotBlank()) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 8.dp)
                        ) {
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = colorResource(id = R.color.yellow).copy(
                                        alpha = 0.9f
                                    )
                                )
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text(
                                        text = debugInfo,
                                        color = colorResource(id = R.color.black)
                                    )
                                    startGridCell?.let { (x, y) ->
                                        Text(
                                            text = "start cell: $x,$y walkable=${startWalkable}",
                                            color = colorResource(id = R.color.black)
                                        )
                                    }
                                    goalGridCell?.let { (x, y) ->
                                        Text(
                                            text = "goal cell: $x,$y walkable=${goalWalkable}",
                                            color = colorResource(id = R.color.black)
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
}

/**
 * 跨樓層選擇介面 (8按鈕) - (★ 已更新為你指定的固定排版)
 */
@OptIn(ExperimentalLayoutApi::class) // (FlowRow 還是用在 LegendItem 裡，所以保留)
@Composable
private fun CrossFloorSelection(
    startFloor: String?,         // 目前所在樓層
    targetFloor: String?,        // 最終目標樓層
    onFloorSelected: (String) -> Unit // 點擊按鈕時的回調
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "跨樓層導航",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            "您與目標在不同樓層",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        // (★) 改用固定的 Column/Row 排版
        val spacing = 8.dp
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(spacing) // 每一「行」的垂直間距
        ) {
            // Row 1: SEA5, (empty), SEC5
            Row(horizontalArrangement = Arrangement.spacedBy(spacing)) {
                FloorButton("SEA5", startFloor, targetFloor, onFloorSelected)
                FloorButton("", startFloor, targetFloor, onFloorSelected) // 空白佔位
                FloorButton("SEC5", startFloor, targetFloor, onFloorSelected)
            }

            // Row 2: SEA4, SEB4, SEC4
            Row(horizontalArrangement = Arrangement.spacedBy(spacing)) {
                FloorButton("SEA4", startFloor, targetFloor, onFloorSelected)
                FloorButton("SEB4", startFloor, targetFloor, onFloorSelected)
                FloorButton("SEC4", startFloor, targetFloor, onFloorSelected)
            }

            // Row 3: SE3
            Row(horizontalArrangement = Arrangement.spacedBy(spacing)) {
                FloorButton("SE3", startFloor, targetFloor, onFloorSelected)
            }

            // Row 4: SE2
            Row(horizontalArrangement = Arrangement.spacedBy(spacing)) {
                FloorButton("SE2", startFloor, targetFloor, onFloorSelected)
            }

            // Row 5: SE1
            Row(horizontalArrangement = Arrangement.spacedBy(spacing)) {
                FloorButton("SE1", startFloor, targetFloor, onFloorSelected)
            }
        }
        // (★) 排版結束

        Spacer(modifier = Modifier.height(24.dp))

        // (★) 圖例說明 (從 FlowRow 改成 Column)
        Column(
            modifier = Modifier.padding(start = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            LegendItem(color = MaterialTheme.colorScheme.primary, text = "您的目前樓層 ($startFloor)")
            LegendItem(color = MaterialTheme.colorScheme.tertiary, text = "您的目標樓層 ($targetFloor)")
            LegendItem(color = MaterialTheme.colorScheme.outline, text = "中繼樓層")
        }
    }
}

@Composable
private fun FloorButton(
    floorName: String,
    startFloor: String?,
    targetFloor: String?,
    onFloorSelected: (String) -> Unit
) {
    // (★ 1. 決定按鈕外觀)
    val isStart = (floorName == startFloor)
    val isTarget = (floorName == targetFloor)

    val buttonColors = when {
        isStart -> ButtonDefaults.buttonColors( // (★ 目前樓層的顏色)
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        )
        isTarget -> ButtonDefaults.buttonColors( // (★ 目標樓層的顏色)
            containerColor = MaterialTheme.colorScheme.tertiary,
            contentColor = MaterialTheme.colorScheme.onTertiary
        )
        else -> ButtonDefaults.outlinedButtonColors( // (★ 中繼樓層的顏色)
            contentColor = MaterialTheme.colorScheme.onSurface
        )
    }

    // (★ 2. 決定按鈕大小)
    val buttonSize = Modifier.size(width = 80.dp, height = 50.dp)

    // (★ 3. 如果 floorName 是空的，就畫一個透明的佔位方塊)
    if (floorName.isEmpty()) {
        Spacer(modifier = buttonSize)
    } else {
        // (★ 4. 否則，畫出正常的按鈕)
        Button(
            onClick = { onFloorSelected(floorName) },
            modifier = buttonSize,
            colors = buttonColors,
            border = if (isStart || isTarget) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Text(floorName)
        }
    }
}

@Composable
private fun LegendItem(color: androidx.compose.ui.graphics.Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(16.dp)
                .background(color, RoundedCornerShape(4.dp))
                .border(1.dp, MaterialTheme.colorScheme.onSurface, RoundedCornerShape(4.dp))
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}
