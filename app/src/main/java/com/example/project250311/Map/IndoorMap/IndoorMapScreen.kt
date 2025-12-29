@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.project250311.Map.IndoorMap

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.ui.res.colorResource
import com.example.project250311.Map.IndoorMap.Database.GridCacheEntity
import com.example.project250311.Map.IndoorMap.Database.IndoorMapDatabase
import com.example.project250311.Map.IndoorMap.Database.ReferencePointEntity
import com.example.project250311.R
import kotlin.math.min
import kotlinx.coroutines.*
import android.util.Log
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.PI
import kotlin.math.sqrt
import kotlin.math.hypot

// 多段式導航資料結構與規劃器
data class RouteSegment(
    val floorId: Int,
    val startTargetName: String,
    val endTargetName: String,
    val description: String,
    val isElevatorNav: Boolean = false
)

// 解析名稱，回傳 (棟別前綴, 樓層)，例如 "sea504" -> ("sea", 5)
fun parseLocationInfo(name: String): Pair<String, Int> {
    val lowerName = name.trim().lowercase()
    val building = when {
        lowerName.startsWith("sea") -> "sea"
        lowerName.startsWith("seb") -> "seb"
        lowerName.startsWith("sec") -> "sec"
        else -> ""
    }
    val floor = if (lowerName.length >= 4 && lowerName[3].isDigit()) lowerName[3].digitToInt() else 1
    return Pair(building, floor)
}

// 單段處理：將 RouteSegment 轉換為實際 start/goal 並切換圖片與觸發路徑
suspend fun processSegment(
    segment: RouteSegment,
    db: IndoorMapDatabase,
    refDao: com.example.project250311.Map.IndoorMap.Database.ReferencePointDao,
    scope: CoroutineScope,
    onUpdate: (Offset?, Offset?, Int) -> Unit,
    onImageWait: suspend (Int) -> Boolean,
    getImageBitmap: () -> android.graphics.Bitmap?,
    waitForGridReady: suspend () -> Unit,
    triggerPathCalc: () -> Unit
) {
    withContext(Dispatchers.IO) {
        val allPoints = refDao.getAllReferencePoints().first()

        fun matchesName(p: ReferencePointEntity, name: String): Boolean {
            val t = p.name.trim()
            val n = name.trim()
            return t.equals(n, ignoreCase = true) || t.contains(n, ignoreCase = true)
        }

        // 優先在指定樓層挑選同名點，否則退回不過濾樓層
        val startCandidatesAll = allPoints.filter { matchesName(it, segment.startTargetName) }
        val endCandidatesAll = allPoints.filter { matchesName(it, segment.endTargetName) }
        // 嚴格限制樓層：以 floorNumber 比對，不再退回其他樓層（避免跨樓層座標造成路徑亂跑）
        val startCandidates = startCandidatesAll.filter {
            try { db.floorDao().getFloorById(it.floorId)?.floorNumber == segment.floorId } catch (_: Exception) { false }
        }
        val endCandidates = endCandidatesAll.filter {
            try { db.floorDao().getFloorById(it.floorId)?.floorNumber == segment.floorId } catch (_: Exception) { false }
        }

        if (startCandidates.isEmpty() || endCandidates.isEmpty()) {
            Log.e("IndoorMap", "processSegment: 找不到起點/終點 start='${segment.startTargetName}' end='${segment.endTargetName}' on floor=${segment.floorId}")
            return@withContext
        }

        // 嘗試找出「同一張圖」的配對，避免跨圖片
        val startImgSet = startCandidates.mapNotNull { it.imageId }.toSet().filter { it != 0 }
        val endImgSet = endCandidates.mapNotNull { it.imageId }.toSet().filter { it != 0 }
        val commonImages = startImgSet.intersect(endImgSet)

        // 根據可用性挑選圖與實際點位
        data class Chosen(val imageRes: Int, val s: ReferencePointEntity, val e: ReferencePointEntity)
        fun firstExactThenContains(cands: List<ReferencePointEntity>, name: String, imageId: Int? = null): ReferencePointEntity? {
            val exact = cands.firstOrNull { (imageId == null || it.imageId == imageId) && it.name.trim().equals(name.trim(), ignoreCase = true) }
            if (exact != null) return exact
            return cands.firstOrNull { (imageId == null || it.imageId == imageId) && it.name.contains(name.trim(), ignoreCase = true) }
        }

        // 特別處理：1F 跨棟段落優先固定使用 SE 1F 的整合圖 (se1)
        val se1ImageId: Int = try {
            db.floorDao().getFloorByBuildingAndNumber("SE", 1)?.imageId ?: 0
        } catch (_: Exception) { 0 }

        val chosen: Chosen? = when {
            segment.floorId == 1 && se1ImageId != 0 -> {
                val s = firstExactThenContains(startCandidates, segment.startTargetName, se1ImageId)
                val e = firstExactThenContains(endCandidates, segment.endTargetName, se1ImageId)
                if (s != null && e != null) Chosen(se1ImageId, s, e) else null
            }
            commonImages.isNotEmpty() -> {
                val img = commonImages.first()
                val s = firstExactThenContains(startCandidates, segment.startTargetName, img)
                val e = firstExactThenContains(endCandidates, segment.endTargetName, img)
                if (s != null && e != null) Chosen(img, s, e) else null
            }
            // 若沒有共同圖片，優先使用「終點所在圖片」，嘗試在該圖找到起點同名鏡像點
            endImgSet.isNotEmpty() -> {
                val img = endImgSet.first()
                val e = firstExactThenContains(endCandidates, segment.endTargetName, img)
                val s = firstExactThenContains(startCandidates, segment.startTargetName, img)
                if (s != null && e != null) Chosen(img, s, e) else null
            }
            // 再退回起點圖片
            startImgSet.isNotEmpty() -> {
                val img = startImgSet.first()
                val s = firstExactThenContains(startCandidates, segment.startTargetName, img)
                val e = firstExactThenContains(endCandidates, segment.endTargetName, img)
                if (s != null && e != null) Chosen(img, s, e) else null
            }
            else -> null
        }

        if (chosen == null) {
            Log.e("IndoorMap", "processSegment: 找不到同圖配對或鏡像點，無法在單張圖上規劃路徑 floor=${segment.floorId} start='${segment.startTargetName}' end='${segment.endTargetName}'")
            return@withContext
        }

        // 切換到目標圖片（先清空點以避免前一張圖的點殘留），然後等待圖片與網格
        withContext(Dispatchers.Main) {
            onUpdate(null, null, chosen.imageRes)
        }
        val isLoaded = onImageWait(chosen.imageRes)
        if (!isLoaded) return@withContext

        // 等待該樓層圖的網格構建完成
        waitForGridReady()
        val bmp = getImageBitmap() ?: return@withContext

        val sPoint = Offset(
            (chosen.s.x.toFloat() / 100f) * bmp.width,
            (chosen.s.y.toFloat() / 100f) * bmp.height
        )
        val gPoint = Offset(
            (chosen.e.x.toFloat() / 100f) * bmp.width,
            (chosen.e.y.toFloat() / 100f) * bmp.height
        )

        withContext(Dispatchers.Main) {
            onUpdate(sPoint, gPoint, chosen.imageRes)
            triggerPathCalc()
        }
    }
}

// 產生多段導航列表（只使用 1F 作為連通層）
fun planMultiStageRoute(
    startEntity: ReferencePointEntity,
    endEntity: ReferencePointEntity,
    startFloor: Int,
    endFloor: Int
): List<RouteSegment> {
    val segments = mutableListOf<RouteSegment>()
    val (startBuild, _) = parseLocationInfo(startEntity.name)
    val (endBuild, _) = parseLocationInfo(endEntity.name)

    // 0) 同一張圖：單段「出發教室 → 目的教室」
    if (startEntity.imageId != 0 && startEntity.imageId == endEntity.imageId) {
        segments.add(
            RouteSegment(
                floorId = startFloor,
                startTargetName = startEntity.name,
                endTargetName = endEntity.name,
                description = "已到達目的地",
                isElevatorNav = false
            )
        )
        return segments
    }

    // 1) 同棟：
    if (startBuild == endBuild) {
        if (startFloor == endFloor) {
            // 同樓層但不同圖，仍直接規劃單段（資料允許時）
            segments.add(
                RouteSegment(
                    floorId = startFloor,
                    startTargetName = startEntity.name,
                    endTargetName = endEntity.name,
                    description = "已到達目的地"
                )
            )
        } else {
            // 「出發教室 → 本樓層電梯」→「目的樓層電梯 → 教室」
            segments.add(
                RouteSegment(
                    floorId = startFloor,
                    startTargetName = startEntity.name,
                    endTargetName = "${startBuild}${startFloor}電梯",
                    description = "已到達電梯",
                    isElevatorNav = true
                )
            )
            segments.add(
                RouteSegment(
                    floorId = endFloor,
                    startTargetName = "${endBuild}${endFloor}電梯",
                    endTargetName = endEntity.name,
                    description = "已到達目的地"
                )
            )
        }
        return segments
    }

    // 2) 不同棟：
    when {
        // 起點在 1F、終點不在 1F: 「出發教室 → 目的棟 1F 電梯」，之後「目的樓層電梯 → 教室」
        startFloor == 1 && endFloor != 1 -> {
            segments.add(
                RouteSegment(
                    floorId = 1,
                    startTargetName = startEntity.name,
                    endTargetName = "${endBuild}1電梯",
                    description = "已到達目的棟電梯",
                    isElevatorNav = true
                )
            )
            segments.add(
                RouteSegment(
                    floorId = endFloor,
                    startTargetName = "${endBuild}${endFloor}電梯",
                    endTargetName = endEntity.name,
                    description = "已到達目的地"
                )
            )
        }
        // 起點不在 1F、終點在 1F: 「出發教室 → 本樓層電梯」→「出發棟 1F 電梯 → 目的教室」
        startFloor != 1 && endFloor == 1 -> {
            segments.add(
                RouteSegment(
                    floorId = startFloor,
                    startTargetName = startEntity.name,
                    endTargetName = "${startBuild}${startFloor}電梯",
                    description = "已到達電梯 (前往 1 樓)",
                    isElevatorNav = true
                )
            )
            segments.add(
                RouteSegment(
                    floorId = 1,
                    startTargetName = "${startBuild}1電梯",
                    endTargetName = endEntity.name,
                    description = "已到達目的地"
                )
            )
        }
        // 起點不在 1F、終點不在 1F: 「出發教室 → 本樓層電梯」→「1F 跨棟到目的棟 1F 電梯」→「目的樓層電梯 → 教室」
        startFloor != 1 && endFloor != 1 -> {
            segments.add(
                RouteSegment(
                    floorId = startFloor,
                    startTargetName = startEntity.name,
                    endTargetName = "${startBuild}${startFloor}電梯",
                    description = "已到達電梯 (前往 1 樓)",
                    isElevatorNav = true
                )
            )
            segments.add(
                RouteSegment(
                    floorId = 1,
                    startTargetName = "${startBuild}1電梯",
                    endTargetName = "${endBuild}1電梯",
                    description = "已到達目的棟電梯 (前往 ${endFloor} 樓)",
                    isElevatorNav = true
                )
            )
            segments.add(
                RouteSegment(
                    floorId = endFloor,
                    startTargetName = "${endBuild}${endFloor}電梯",
                    endTargetName = endEntity.name,
                    description = "已到達目的地"
                )
            )
        }
        // 其他未涵蓋情形（例如兩者皆在 1F 但不同圖），預設嘗試單段
        else -> {
            segments.add(
                RouteSegment(
                    floorId = 1,
                    startTargetName = startEntity.name,
                    endTargetName = endEntity.name,
                    description = "已到達目的地"
                )
            )
        }
    }

    return segments
}

// ENTRANCE → 教室 規劃器（沿用相同規則，將「出發教室」替換為「入口點」）
fun planEntranceToClassroomRoute(
    startEntrance: ReferencePointEntity,
    endEntity: ReferencePointEntity,
    startFloor: Int,
    endFloor: Int
): List<RouteSegment> {
    val segments = mutableListOf<RouteSegment>()
    val startBuild = when (startEntrance.buildingId?.uppercase()) {
        "SEB" -> "seb"
        "SEC" -> "sec"
        else -> "sea"
    }
    val (endBuild, _) = parseLocationInfo(endEntity.name)

    // 同一張圖：入口 → 教室
    if (startEntrance.imageId != 0 && startEntrance.imageId == endEntity.imageId) {
        segments.add(
            RouteSegment(
                floorId = startFloor,
                startTargetName = startEntrance.name,
                endTargetName = endEntity.name,
                description = "已到達目的地",
                isElevatorNav = false
            )
        )
        return segments
    }

    // 同棟
    if (startBuild == endBuild) {
        if (startFloor == endFloor) {
            segments.add(
                RouteSegment(
                    floorId = startFloor,
                    startTargetName = startEntrance.name,
                    endTargetName = endEntity.name,
                    description = "已到達目的地",
                    isElevatorNav = false
                )
            )
        } else {
            segments.add(
                RouteSegment(
                    floorId = startFloor,
                    startTargetName = startEntrance.name,
                    endTargetName = "${startBuild}${startFloor}電梯",
                    description = "已到達電梯",
                    isElevatorNav = true
                )
            )
            segments.add(
                RouteSegment(
                    floorId = endFloor,
                    startTargetName = "${endBuild}${endFloor}電梯",
                    endTargetName = endEntity.name,
                    description = "已到達目的地",
                    isElevatorNav = false
                )
            )
        }
        return segments
    }

    // 不同棟
    when {
        // 起點在 1F、終點不在 1F
        startFloor == 1 && endFloor != 1 -> {
            segments.add(
                RouteSegment(
                    floorId = 1,
                    startTargetName = startEntrance.name,
                    endTargetName = "${endBuild}1電梯",
                    description = "已到達目的棟電梯",
                    isElevatorNav = true
                )
            )
            segments.add(
                RouteSegment(
                    floorId = endFloor,
                    startTargetName = "${endBuild}${endFloor}電梯",
                    endTargetName = endEntity.name,
                    description = "已到達目的地",
                    isElevatorNav = false
                )
            )
        }
        // 起點不在 1F、終點在 1F
        startFloor != 1 && endFloor == 1 -> {
            segments.add(
                RouteSegment(
                    floorId = startFloor,
                    startTargetName = startEntrance.name,
                    endTargetName = "${startBuild}${startFloor}電梯",
                    description = "已到達電梯 (前往 1 樓)",
                    isElevatorNav = true
                )
            )
            segments.add(
                RouteSegment(
                    floorId = 1,
                    startTargetName = "${startBuild}1電梯",
                    endTargetName = endEntity.name,
                    description = "已到達目的地",
                    isElevatorNav = false
                )
            )
        }
        // 起點不在 1F、終點不在 1F
        startFloor != 1 && endFloor != 1 -> {
            segments.add(
                RouteSegment(
                    floorId = startFloor,
                    startTargetName = startEntrance.name,
                    endTargetName = "${startBuild}${startFloor}電梯",
                    description = "已到達電梯 (前往 1 樓)",
                    isElevatorNav = true
                )
            )
            segments.add(
                RouteSegment(
                    floorId = 1,
                    startTargetName = "${startBuild}1電梯",
                    endTargetName = "${endBuild}1電梯",
                    description = "已到達目的棟電梯 (前往 ${endFloor} 樓)",
                    isElevatorNav = true
                )
            )
            segments.add(
                RouteSegment(
                    floorId = endFloor,
                    startTargetName = "${endBuild}${endFloor}電梯",
                    endTargetName = endEntity.name,
                    description = "已到達目的地",
                    isElevatorNav = false
                )
            )
        }
        else -> {
            segments.add(
                RouteSegment(
                    floorId = 1,
                    startTargetName = startEntrance.name,
                    endTargetName = endEntity.name,
                    description = "已到達目的地",
                    isElevatorNav = false
                )
            )
        }
    }

    return segments
}


// ======================= 主畫面 =======================
@Composable
fun IndoorMapScreen(
    navController: NavHostController? = null,
    modifier: Modifier = Modifier,
    buildingId: String? = null,
    floorId: Int? = null,
    targetPointId: String? = null,
    entryPointId: String? = null,
    autoStart: Boolean = true,
    locationViewModel: LocationViewModel = viewModel()
) {
    val context = LocalContext.current
    val colorMaterial = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()

    // DB 與 DAO（快取）
    val db = remember { IndoorMapDatabase.getDatabase(context) }
    val gridDao = remember(db) { db.gridCacheDao() }
    val refDao = remember(db) { db.referencePointDao() }

    //觀察定位狀態
    val positionState by locationViewModel.positionState.collectAsState()
    val isLikelyIndoors by locationViewModel.isLikelyIndoors.collectAsState()

    var expanded by remember { mutableStateOf(false) }
    var selectedFloorName by remember { mutableStateOf(floorPlans.first().first) }
    var currentImageRes by remember { mutableStateOf(floorPlans.first().second) }
    var imageBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var loadedImageRes by remember { mutableStateOf<Int?>(null) }

    // 視窗互動狀態（宣告必須在 LaunchedEffect 之前，因為 LaunchedEffect 會使用到 minScale）
    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }
    // 每張圖的動態最小縮放（以螢幕寬度為最小）
    var minScale by remember { mutableStateOf(1f) }
    val maxScale = 6f


    // 容器實際大小（Canvas / Box 可用空間），用來計算置中與 fit-scale
    var containerWidthPx by remember { mutableStateOf(0f) }
    var containerHeightPx by remember { mutableStateOf(0f) }

    // ===== (2) 載入時先縮圖（依裝置寬高上限） =====
    LaunchedEffect(currentImageRes) {
    // Capture requested resource id so loadedImageRes reflects the decoded resource (avoid race with state changes)
    val requestedRes = currentImageRes
    // Decode and scale bitmap off the main thread to avoid UI freezes (was blocking main thread)
    val finalBmp = withContext(Dispatchers.Default) {
        try {
        // decodeResource is safe to call off main thread
        val decoded = BitmapFactory.decodeResource(context.resources, requestedRes) ?: return@withContext null
        val metrics = Resources.getSystem().displayMetrics
        val maxW = (metrics.widthPixels * 2f).toInt()
        val maxH = (metrics.heightPixels * 2f).toInt()
        val scale = min(maxW / decoded.width.toFloat(), maxH / decoded.height.toFloat())
        if (scale < 1f) {
            Bitmap.createScaledBitmap(
            decoded,
            (decoded.width * scale).toInt().coerceAtLeast(1),
            (decoded.height * scale).toInt().coerceAtLeast(1),
            true
            )
        } else decoded
        } catch (e: Exception) {
        null
        }
    }

    // set imageBitmap on main thread and mark which res was loaded
    imageBitmap = finalBmp?.asImageBitmap()
    loadedImageRes = if (finalBmp != null) requestedRes else null

    // (imageBitmap will be processed by separate effect that depends on container size)
    }

    // ★★★ 關鍵修正：當 imageBitmap 改變（代表圖片載入完成）時，通知邏輯層 ★★★
    LaunchedEffect(imageBitmap) {
        if (imageBitmap != null) {
            // 告知 processSegment 的等待迴圈：圖片已載入完成
            loadedImageRes = currentImageRes
            // 保險：不在此處重置 grid；重置已在 LaunchedEffect(currentImageRes, gridSample) 處理
        }
    }

    // 輔助：限制偏移，避免圖片被平移出畫面
    fun clampOffsets(imageWidth: Float, imageHeight: Float) {
    val dispW = imageWidth * scale
    val dispH = imageHeight * scale
    // 當影像顯示寬度小於容器，水平置中
    if (dispW <= containerWidthPx) {
        offsetX = (containerWidthPx - dispW) / 2f
    } else {
        val minX = containerWidthPx - dispW
        val maxX = 0f
        if (offsetX < minX) offsetX = minX
        if (offsetX > maxX) offsetX = maxX
    }
    // 當影像顯示高度小於容器，垂直置中
    if (dispH <= containerHeightPx) {
        offsetY = (containerHeightPx - dispH) / 2f
    } else {
        val minY = containerHeightPx - dispH
        val maxY = 0f
        if (offsetY < minY) offsetY = minY
        if (offsetY > maxY) offsetY = maxY
    }
    }

    // 當圖片或容器尺寸改變時，根據容器寬度計算 minScale 並置中圖片
    LaunchedEffect(imageBitmap, containerWidthPx, containerHeightPx) {
    val bmp = imageBitmap?.asAndroidBitmap() ?: return@LaunchedEffect
    if (containerWidthPx <= 0f || containerHeightPx <= 0f) return@LaunchedEffect

    val fitMin = if (bmp.width > 0) (containerWidthPx / bmp.width.toFloat()) else 1f
    minScale = fitMin.coerceAtMost(maxScale)
    // 初始化 scale 與置中偏移（以 container 為基準）
    scale = minScale
    offsetX = (containerWidthPx - bmp.width * scale) / 2f
    offsetY = (containerHeightPx - bmp.height * scale) / 2f
    clampOffsets(bmp.width.toFloat(), bmp.height.toFloat())
    }

    // 導航狀態
    var grid by remember { mutableStateOf<Grid?>(null) }
    var gridSample by remember { mutableStateOf(2) } // 2 或 3
    var start by remember { mutableStateOf<Offset?>(null) }
    var goal by remember { mutableStateOf<Offset?>(null) }
    var path by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var showGridOverlay by remember { mutableStateOf(false) }
    // 單一路徑計算工作，新的請求會取消舊的以避免同時大量配置
    var pathJob by remember { mutableStateOf<Job?>(null) }



    // Overlay 僅在需要顯示時才建立，避免造成不必要的記憶體配置與 GC 壓力
    var overlay by remember { mutableStateOf<ImageBitmap?>(null) }
    var walkableCount by remember { mutableStateOf(0) }
    // 各樓層診斷報告（供比對 se1 與其他樓層）
    var floorStatsReport by remember { mutableStateOf("") }

    // 顯示教室點
    var showClassrooms by remember { mutableStateOf(false) }
    var classroomPoints by remember { mutableStateOf<List<ReferencePointEntity>>(emptyList()) }

    // 多段式導航狀態（保留原室外→室內邏輯，不取代）
    var navigationQueue by remember { mutableStateOf<List<RouteSegment>>(emptyList()) }
    var currentSegment by remember { mutableStateOf<RouteSegment?>(null) }
    var showNextStepButton by remember { mutableStateOf(false) }

    // （移除入口解析的偵錯資訊）

    fun imageToGrid(pt: Offset, g: Grid, imgW: Int, imgH: Int): Pair<Int, Int> {
        // Map image-pixel coordinates to grid cells using actual ratios
        if (imgW <= 0 || imgH <= 0 || g.w <= 0 || g.h <= 0) return 0 to 0
        val scaleX = g.w.toFloat() / imgW.toFloat()
        val scaleY = g.h.toFloat() / imgH.toFloat()
        val gx = (pt.x * scaleX).toInt()
        val gy = (pt.y * scaleY).toInt()
        return gx.coerceIn(0, g.w - 1) to gy.coerceIn(0, g.h - 1)
    }

    // 讀取 raw 參考點（入口/電梯/樓梯等），僅回傳對應指定 imageRes 的點
    fun loadRawReferencePointsForImage(imageRes: Int): List<ReferencePointEntity> {
        return try {
            val rawId = context.resources.getIdentifier("reference_entrance_points_output", "raw", context.packageName)
            if (rawId == 0 || imageRes == 0) return emptyList()
            val txt = context.resources.openRawResource(rawId).bufferedReader().use { it.readText() }
            val regex = Regex("ReferencePointEntity\\(\"([^\"]+)\",\\s*\"([^\"]*)\",\\s*([0-9eE.+-]+),\\s*([0-9eE.+-]+),\\s*R\\.drawable\\.([^,\\)\\s]+),\\s*([0-9]+),\\s*\"([^\"]*)\",\\s*\"([^\"]*)\",\\s*([0-9]+)\\)")
            val out = mutableListOf<ReferencePointEntity>()
            val imageResName = try { context.resources.getResourceEntryName(imageRes) } catch (_: Exception) { null }
            for (m in regex.findAll(txt)) {
                try {
                    val id = m.groups[1]?.value ?: continue
                    val name = m.groups[2]?.value ?: ""
                    val x = m.groups[3]?.value?.toDoubleOrNull() ?: 0.0
                    val y = m.groups[4]?.value?.toDoubleOrNull() ?: 0.0
                    val drawableToken = m.groups[5]?.value ?: ""
                    val scan = m.groups[6]?.value?.toIntOrNull() ?: 0
                    val type = m.groups[7]?.value ?: "CLASSROOM"
                    val buildingId = m.groups[8]?.value ?: ""
                    val floorId = m.groups[9]?.value?.toIntOrNull() ?: 0

                    val numeric = drawableToken.toIntOrNull()
                    val tokenResName = try {
                        if (numeric != null && numeric != 0) try { context.resources.getResourceEntryName(numeric) } catch (_: Exception) { drawableToken } else drawableToken
                    } catch (_: Exception) { drawableToken }

                    val matches = imageResName != null && tokenResName.equals(imageResName, true)
                    if (matches) {
                        out.add(
                            ReferencePointEntity(
                                id = id,
                                name = name,
                                x = x,
                                y = y,
                                imageId = imageRes,
                                type = type,
                                buildingId = buildingId,
                                floorId = floorId
                            )
                        )
                    }
                } catch (_: Exception) {}
            }
            out
        } catch (_: Exception) { emptyList() }
    }

    // 移除入口、預覽相關邏輯（僅保留教室→教室導航）

    // 依當前樓層圖片載入/清除教室點（同時包含 STAIRS）
    LaunchedEffect(currentImageRes, showClassrooms) {
    if (showClassrooms) {
        // Collect the full reference-points flow and filter locally so we show both CLASSROOM and STAIRS
        // This avoids adding a separate DAO method for STAIRS while keeping the UI reactive to DB changes.
        refDao.getAllReferencePoints().collect { all ->
        // 先過濾資料庫中的參考點
        val dbPoints = all.filter { rp ->
            rp.imageId == currentImageRes && (rp.type.equals("CLASSROOM", true) || rp.type.equals("STAIRS", true))
        }

        // 再嘗試從 raw 資源檔載入 entrance points（如果存在）並合併
        val entrancePoints = try {
            val rawId = context.resources.getIdentifier("reference_entrance_points_output", "raw", context.packageName)
            if (rawId != 0) {
            val txt = context.resources.openRawResource(rawId).bufferedReader().use { it.readText() }
            // 解析檔案中的 ReferencePointEntity(...) 行
            val regex = Regex("ReferencePointEntity\\(\"([^\"]+)\",\\s*\"([^\"]*)\",\\s*([0-9eE.+-]+),\\s*([0-9eE.+-]+),\\s*R\\.drawable\\.([^,\\)\\s]+),\\s*([0-9]+),\\s*\"([^\"]*)\",\\s*\"([^\"]*)\",\\s*([0-9]+)\\)")
            val out = mutableListOf<ReferencePointEntity>()
            val currentResName = if (currentImageRes != null && currentImageRes != 0) {
                try { context.resources.getResourceEntryName(currentImageRes) } catch (_: Exception) { null }
            } else null
            for (m in regex.findAll(txt)) {
                try {
                val id = m.groups[1]?.value ?: continue
                val name = m.groups[2]?.value ?: ""
                val x = m.groups[3]?.value?.toDoubleOrNull() ?: 0.0
                val y = m.groups[4]?.value?.toDoubleOrNull() ?: 0.0
                val drawableToken = m.groups[5]?.value ?: ""
                val scan = m.groups[6]?.value?.toIntOrNull() ?: 0
                val type = m.groups[7]?.value ?: "CLASSROOM"
                val buildingId = m.groups[8]?.value ?: "A"
                val floorId = m.groups[9]?.value?.toIntOrNull() ?: 0

                // 解析 token：可能為數字 resource id 或 drawable 名稱
                val numeric = drawableToken.toIntOrNull()
                var imageId = numeric ?: context.resources.getIdentifier(drawableToken, "drawable", context.packageName)

                // 嘗試解析 token 對應的 resource entry name（若 numeric 提供），否則使用 token
                val tokenResName = try {
                    if (numeric != null && numeric != 0) try { context.resources.getResourceEntryName(numeric) } catch (_: Exception) { drawableToken } else drawableToken
                } catch (_: Exception) {
                    drawableToken
                }

                // 若 entry name 與目前圖片相同，視為配對（不同 build 的 numeric id 也能對上）
                val matches = (imageId != 0 && imageId == currentImageRes) || (currentResName != null && tokenResName.equals(currentResName, true))
                if (matches) {
                    val finalImageId = if (imageId == currentImageRes) imageId else currentImageRes
                    out.add(ReferencePointEntity(id, name, x, y, finalImageId, scan, type, buildingId, floorId))
                } else {
                    Log.d("IndoorMap.UI", "entrance point ignored: token=$drawableToken parsedId=$imageId currentRes=$currentImageRes resName=$currentResName tokenResName=$tokenResName")
                }
                } catch (_: Exception) {
                }
            }
            out
            } else emptyList()
        } catch (e: Exception) {
            emptyList()
        }

        // 合併 DB 點與 raw 檔中的 entrance 點，去重 (以 id 為唯一鍵)
        val merged = (dbPoints + entrancePoints).distinctBy { it.id }
        classroomPoints = merged
        // Debug: log count and sample of returned points so we can confirm STAIRS are present
        try {
            Log.d(
            "IndoorMap.UI",
            "showClassrooms=$showClassrooms currentImageRes=$currentImageRes classroomPoints=${classroomPoints.size} sample=${classroomPoints.firstOrNull()?.let { "${it.id}/${it.type}/${it.imageId}" } }"
            )
        } catch (_: Exception) {
        }
        }
    } else {
        classroomPoints = emptyList()
    }
    }

    // ===== (3) 路徑計算搬到背景執行緒 =====
    fun recomputePathAsync() {
    val g = grid ?: return
    val sPt = start ?: return
    val ePt = goal ?: return
    val imgW = imageBitmap?.width ?: return
    val imgH = imageBitmap?.height ?: return
    val (sx, sy) = imageToGrid(sPt, g, imgW, imgH)
    val (gx, gy) = imageToGrid(ePt, g, imgW, imgH)
    // helper: find nearest walkable cell (breadth by radius) - returns Pair<x,y> or null
    fun findNearestWalkable(g: Grid, cx: Int, cy: Int, maxRadius: Int = 30): Pair<Int, Int>? {
        if (g.walkable(cx, cy)) return cx to cy
        val w = g.w
        val h = g.h
        for (r in 1..maxRadius) {
        val xmin = (cx - r).coerceAtLeast(0)
        val xmax = (cx + r).coerceAtMost(w - 1)
        val ymin = (cy - r).coerceAtLeast(0)
        val ymax = (cy + r).coerceAtMost(h - 1)
        // iterate ring
        for (x in xmin..xmax) {
            if (g.walkable(x, ymin)) return x to ymin
            if (g.walkable(x, ymax)) return x to ymax
        }
        for (y in (ymin + 1) until (ymax)) {
            if (g.walkable(xmin, y)) return xmin to y
            if (g.walkable(xmax, y)) return xmax to y
        }
        }
        return null
    }
    // 取消舊任務，避免同時多條路徑計算造成 GC 壓力
    pathJob?.cancel()
    pathJob = scope.launch(Dispatchers.Default) {
        // Log initial mapping
        Log.d("IndoorMap.UI", "recomputePathAsync: image->grid start=($sx,$sy) goal=($gx,$gy) grid=${g.w}x${g.h} img=${imgW}x${imgH} sample=$gridSample")

        // If start/goal are not walkable, attempt to find nearest walkable cell and update start/goal
        val startWalk = g.walkable(sx, sy)
        val goalWalk = g.walkable(gx, gy)
        var sx2 = sx
        var sy2 = sy
        var gx2 = gx
        var gy2 = gy

        if (!startWalk) {
        val found = findNearestWalkable(g, sx, sy, maxRadius = 40)
        if (found != null) {
            sx2 = found.first
            sy2 = found.second
            Log.d("IndoorMap.UI", "start not walkable at ($sx,$sy) -> internal snap to ($sx2,$sy2)")
            // 不更新 UI start，僅內部採用校正格
        } else {
            Log.d("IndoorMap.UI", "start not walkable and no nearby walkable cell found (maxRadius)")
        }
        }

        if (!goalWalk) {
        val found = findNearestWalkable(g, gx, gy, maxRadius = 40)
        if (found != null) {
            gx2 = found.first
            gy2 = found.second
            Log.d("IndoorMap.UI", "goal not walkable at ($gx,$gy) -> internal snap to ($gx2,$gy2)")
            // 不更新 UI goal，僅內部採用校正格
        } else {
            Log.d("IndoorMap.UI", "goal not walkable and no nearby walkable cell found (maxRadius)")
        }
        }

        // Recompute with possibly adjusted coords
        val raw = aStar(g, sx2, sy2, gx2, gy2)
        if (raw.isEmpty()) {
        Log.d("IndoorMap.UI", "aStar returned empty route from ($sx2,$sy2) to ($gx2,$gy2) -- walkable start=$startWalk goal=$goalWalk")
        withContext(Dispatchers.Main) { path = emptyList() }
        return@launch
        }
        val vis = smoothByVisibility(raw, g)
        val pixelPerCellX = (imgW.toFloat() / g.w.toFloat())
        val pixelPerCellY = (imgH.toFloat() / g.h.toFloat())
        val px = vis.map { node -> Offset((node.x + 0.5f) * pixelPerCellX, (node.y + 0.5f) * pixelPerCellY) }
        val simplified = rdp(px, eps = (min(pixelPerCellX, pixelPerCellY) * 0.75f))
        withContext(Dispatchers.Main) { path = simplified }
    }
    }

    // 影像座標 -> 螢幕座標（用於畫點/路徑時）
    fun imgToScreen(p: Offset) = Offset(p.x * scale + offsetX, p.y * scale + offsetY)

    // 初始化：僅處理「教室 → 教室」多段式導航
    LaunchedEffect(targetPointId, entryPointId, autoStart) {
        if (targetPointId == null) return@LaunchedEffect
        try {
            // 取得所有參考點（一次性）
            val all = withContext(Dispatchers.IO) { refDao.getAllReferencePoints().first() }
            val targetEntity = all.firstOrNull { it.id == targetPointId }
            if (targetEntity != null) {
                // 僅在 entryPointId 與 target 皆為教室時啟用
                val startClassroom = all.firstOrNull { it.id == entryPointId && it.type.equals("CLASSROOM", true) }
                if (autoStart && startClassroom != null && targetEntity.type.equals("CLASSROOM", true)) {
                    // 計算樓層號（以 floorNumber 優先）
                    val startFloor = withContext(Dispatchers.IO) { db.floorDao().getFloorById(startClassroom.floorId)?.floorNumber } ?: startClassroom.floorId
                    val endFloor = withContext(Dispatchers.IO) { db.floorDao().getFloorById(targetEntity.floorId)?.floorNumber } ?: targetEntity.floorId

                    val segments = planMultiStageRoute(startClassroom, targetEntity, startFloor, endFloor)
                    if (segments.isNotEmpty()) {
                        val firstSeg = segments.first()
                        navigationQueue = segments.drop(1)
                        currentSegment = firstSeg
                        showNextStepButton = navigationQueue.isNotEmpty()
                        // 不立刻處理段落，改由下方 orchestrator 在圖片與網格完成後統一執行
                    }
                }

                // 室外→室內銜接：入口(ENTRANCE) 作為起點
                val startEntrance = all.firstOrNull { it.id == entryPointId && it.type.equals("ENTRANCE", true) }
                if (autoStart && startEntrance != null && targetEntity.type.equals("CLASSROOM", true)) {
                    val startFloor = withContext(Dispatchers.IO) { db.floorDao().getFloorById(startEntrance.floorId)?.floorNumber } ?: startEntrance.floorId
                    val endFloor = withContext(Dispatchers.IO) { db.floorDao().getFloorById(targetEntity.floorId)?.floorNumber } ?: targetEntity.floorId

                    val segments = planEntranceToClassroomRoute(startEntrance, targetEntity, startFloor, endFloor)
                    if (segments.isNotEmpty()) {
                        val firstSeg = segments.first()
                        navigationQueue = segments.drop(1)
                        currentSegment = firstSeg
                        showNextStepButton = navigationQueue.isNotEmpty()
                        // 不立刻處理段落，改由下方 orchestrator 在圖片與網格完成後統一執行
                    }
                }
            }
        } catch (e: Exception) {
            // 忽略錯誤，UI 可顯示或回傳
        }

    }

    // 移除重複的佇列式初始化 LaunchedEffect，改由上方的單一初始化流程統一處理


        // 先嘗試從 DB 讀取快取（不需等待圖片載入）。此處不主動建立 overlay，改為在需要顯示時生成。
        LaunchedEffect(currentImageRes, gridSample) {
            // 清理目前狀態
            grid = null
            overlay = null
            start = null
            goal = null
            path = emptyList()
            walkableCount = 0

            val cached = withContext(Dispatchers.IO) { gridDao.get(currentImageRes, gridSample) }
            if (cached != null) {
                val cells = cached.cells.toBooleanArray(cached.width * cached.height)
                val g = Grid(cached.width, cached.height, cells)
                // update UI state on Main (we're in a LaunchedEffect with Main dispatcher)
                grid = g
                walkableCount = cells.count { it }
                // overlay 將在 showGridOverlay=true 時才建立
            }
        }

        // ===== 快速診斷：掃描所有 floorPlans，計算以目前閾值判定下的可走格百分比（離線/背景執行） =====
        // 減少冗長診斷日誌以避免噪音
        val ENABLE_DIAG_LOGS = false // 想測試時改成 true

        LaunchedEffect(gridSample) {
            if (!ENABLE_DIAG_LOGS) return@LaunchedEffect

            // 使用 IO Dispatcher 適合讀檔操作，Default 適合運算，這邊兩者皆可，保持 Default 即可
            scope.launch(Dispatchers.Default) {
                val sb = StringBuilder()

                // 設定圖片讀取參數：省記憶體模式
                val options = BitmapFactory.Options().apply {
                    // 1. 使用 RGB_565 格式，比預設的 ARGB_8888 節省 50% 記憶體
                    inPreferredConfig = android.graphics.Bitmap.Config.RGB_565

                    // 2. 降採樣 (Downsampling)：讀取 1/2 的寬高
                    // 記憶體佔用會變成原本的 1/4。如果還是爆，可以改成 4 (變成 1/16)
                    inSampleSize = 2
                }

                try {
                    for ((name, resId) in floorPlans) {
                        // 3. 檢查記憶體，如果已經很吃緊就暫停一下讓 GC 跑
                        System.gc()

                        // 載入圖片 (使用上面的省記憶體參數)
                        val bmp = BitmapFactory.decodeResource(context.resources, resId, options)

                        if (bmp == null) continue

                        try {
                            // 注意：因為圖片縮小了 (inSampleSize=2)，這裡傳入的 sample 可能需要調整
                            // 或者您接受診斷數據是基於縮小圖的近似值
                            val g = bitmapToGridFromWhiteCorridor(
                                bitmap = bmp,
                                sample = gridSample, // 如果您的算法依賴絕對像素，縮圖後可能會有誤差
                                satMax = 0.12f,
                                valMin = 0.92f,
                                wallInflate = 3
                            )

                            val walkable = g.cells.count { it }
                            val total = g.w * g.h
                            val pct = if (total == 0) 0 else (walkable * 100 / total)

                            sb.append("$name: ${walkable}/$total ($pct%)\n")
                            Log.d(
                                "IndoorMap.Diag",
                                "$name -> walkable=$walkable total=$total pct=$pct% grid=${g.w}x${g.h}"
                            )

                        } finally {
                            // 4. 【關鍵】無論成功或失敗，強制立即釋放圖片記憶體！
                            bmp.recycle()
                        }

                        // 5. 喘口氣，讓系統有時間回收剛剛釋放的記憶體，避免短時間內連續 Allocation
                        delay(100)
                    }
                } catch (e: Exception) {
                    sb.append("diagnostic error: ${e.message}")
                    Log.e("IndoorMap.Diag", "Error running diagnostics", e)
                }

                withContext(Dispatchers.Main) {
                    floorStatsReport = sb.toString()
                }
            }
        }

        // 若沒快取且圖片已載入，則計算並寫回 DB（不立即建立 overlay，改由顯示需求觸發）
        LaunchedEffect(imageBitmap, gridSample, currentImageRes) {
            val bmp = imageBitmap?.asAndroidBitmap() ?: return@LaunchedEffect

            // If a cached grid exists but its size doesn't match current image/gridSample ratio, drop and rebuild
            val expectedW = if (gridSample > 0) (bmp.width / gridSample) else bmp.width
            val expectedH = if (gridSample > 0) (bmp.height / gridSample) else bmp.height
            val sizeMatches = grid?.let { it.w == expectedW && it.h == expectedH } ?: false
            if (grid != null && !sizeMatches) {
                Log.d("IndoorMap.UI", "Grid size mismatch, rebuilding: grid=${grid?.w}x${grid?.h} expected=${expectedW}x${expectedH} img=${bmp.width}x${bmp.height} sample=$gridSample")
                grid = null
            }
            if (grid != null) return@LaunchedEffect

            val g =
                withContext(Dispatchers.Default) {
                    bitmapToGridFromWhiteCorridor(
                        bitmap = bmp,
                        sample = gridSample,
                        satMax = 0.12f,
                        valMin = 0.92f,
                        wallInflate = 3
                    )
                }

            // 更新 UI 狀態 (LaunchedEffect runs on Main dispatcher)
            grid = g
            // overlay 延後建立
            walkableCount = g.cells.count { it }

            // 寫入 DB 快取 - pack bytes off main thread then IO upsert
            val packed = withContext(Dispatchers.Default) { g.cells.toBitPackedBytes() }
            withContext(Dispatchers.IO) {
                gridDao.upsert(
                    GridCacheEntity(
                        imageId = currentImageRes,
                        sample = gridSample,
                        width = g.w,
                        height = g.h,
                        cells = packed
                    )
                )
            }

        }

        // 僅在顯示需求時建立/更新 overlay，降低記憶體與 GC 壓力（需置於可組合範疇的最外層，避免嵌套）
        LaunchedEffect(showGridOverlay, grid) {
            if (!showGridOverlay) {
                overlay = null
                return@LaunchedEffect
            }
            val g = grid ?: return@LaunchedEffect
            overlay = withContext(Dispatchers.Default) { buildGridOverlayBitmap(g) }
        }

        // 當 start/goal 與 grid 都就緒時，自動計算路徑（補強：避免 target/entry 的 LaunchedEffect 在 grid 構建前就呼叫 recomputePathAsync）
        LaunchedEffect(start, goal, grid) {
            if (start != null && goal != null && grid != null) {
                recomputePathAsync()
            }
        }

        // 將室內導航判斷與段落處理延後到「圖片已載入且網格建立完成」後統一執行
        LaunchedEffect(currentSegment) {
            val seg = currentSegment ?: return@LaunchedEffect
            // 這裡不直接假設已載入/有網格，交由 processSegment 內部透過 onImageWait 與 waitForGridReady 等待
            processSegment(
                segment = seg,
                db = db,
                refDao = refDao,
                scope = this,
                onUpdate = { s, g, imgRes ->
                    // 先清空舊路徑與點，避免視覺殘留
                    path = emptyList()
                    start = null
                    goal = null
                    // 切換到目標圖片
                    currentImageRes = imgRes
                    // 設定新起終點（等待圖片載入與網格生成後再自動觸發路徑）
                    start = s
                    goal = g
                },
                onImageWait = { wantRes ->
                    withContext(Dispatchers.Default) {
                        var attempts = 0
                        while (loadedImageRes != wantRes && attempts < 200) {
                            attempts++
                            delay(50)
                        }
                    }
                    loadedImageRes == wantRes
                },
                getImageBitmap = { imageBitmap?.asAndroidBitmap() },
                waitForGridReady = {
                    withContext(Dispatchers.Default) {
                        var guard = 0
                        while (grid == null && guard < 400) {
                            guard++
                            delay(50)
                        }
                    }
                },
                // 不需主動呼叫計算：當 start/goal/grid 就緒時，上方的 LaunchedEffect(start, goal, grid) 會自動觸發
                triggerPathCalc = { Log.d("IndoorMap", "Segment ready, waiting auto path calc via state effect") }
            )
        }

        // 新增：Overlay 與偵錯統計（原本有使用到，但未宣告）

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("平面圖導航", fontWeight = FontWeight.SemiBold) },
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
                                            // 清狀態
                                            start = null
                                            goal = null
                                            path = emptyList()
                                            grid = null
                                            overlay = null
                                            walkableCount = 0
                                        }
                                    )
                                }
                            }
                        }

                        // 建網格 -> 改為 重建網格（手動覆蓋快取）
//                TextButton(
//                    onClick = {
//                    val bmp =
//                        imageBitmap?.asAndroidBitmap() ?: return@TextButton
//                    scope.launch(Dispatchers.Default) {
//                        val g =
//                            bitmapToGridFromWhiteCorridor(
//                                bitmap = bmp,
//                                sample = gridSample,
//                                satMax = 0.12f,
//                                valMin = 0.92f,
//                                wallInflate = 3
//                            )
//                        val ov = buildGridOverlayBitmap(g)
//                        withContext(Dispatchers.Main) {
//                        grid = g
//                        overlay = ov
//                        start = null
//                        goal = null
//                        path = emptyList()
//                        walkableCount = g.cells.count { it }
//                        }
//                        val packed = g.cells.toBitPackedBytes()
//                        withContext(Dispatchers.IO) {
//                        gridDao.upsert(
//                            GridCacheEntity(
//                                imageId = currentImageRes,
//                                sample = gridSample,
//                                width = g.w,
//                                height = g.h,
//                                cells = packed
//                            )
//                        )
//                        }
//                    }
//                    }
//                ) { Text("重建網格") }
//
//                // 顯示/隱藏網格覆蓋
//                TextButton(onClick = { showGridOverlay = !showGridOverlay }) {
//                Text(if (showGridOverlay) "隱藏網格" else "顯示網格")
//                }
//
//                // 新增：顯示/隱藏 教室點
//                TextButton(onClick = { showClassrooms = !showClassrooms }) {
//                Text(if (showClassrooms) "隱藏教室" else "顯示教室")
//                }
//
//                // 偵錯：可走格數
//                Text(
//                    "walkable=$walkableCount",
//                    style = MaterialTheme.typography.labelSmall
//                )
//
//                // 清除
//                TextButton(
//                    onClick = {
//                    start = null
//                    goal = null
//                    path = emptyList()
//                    }
//                ) { Text("清除") }
                    }
                )
            }
        ) { padding ->
            Box(
                Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)
                    .padding(padding)
                    .onSizeChanged {
                        containerWidthPx = it.width.toFloat(); containerHeightPx =
                        it.height.toFloat()
                    }) {
                // 定義文字畫筆 (紅色字體 + 白色陰影)
                val textPaint = remember {
                    android.graphics.Paint().apply {
                        color = android.graphics.Color.RED
                        textSize = 40f // 字體大小，可依需求調整
                        isAntiAlias = true
                        textAlign = android.graphics.Paint.Align.CENTER
                        typeface = android.graphics.Typeface.DEFAULT_BOLD
                        // 設定陰影 (半徑, dx, dy, 顏色) - 讓文字在複雜背景上更清楚
                        setShadowLayer(3f, 0f, 0f, android.graphics.Color.WHITE)
                    }
                }
                imageBitmap?.let { bmp ->
                    Box(
                        Modifier.fillMaxSize()
                            .clipToBounds()
//                // 雙擊處理器
//                .pointerInput(Unit) {
//                    detectTapGestures(onDoubleTap = { p ->
//                    val ip = screenToImage(p) ?: return@detectTapGestures
//                    if (start == null) start = ip
//                    else if (goal == null) {
//                        goal = ip
//                        recomputePathAsync()
//                    } else {
//                        start = ip
//                        goal = null
//                        path = emptyList()
//                    }
//                    })
//                }
                            // 變形（捏合 + 平移）處理器，錨點為手勢中心
                            .pointerInput(Unit) {
                                detectTransformGestures { centroid, pan, zoom, _ ->
                                    val bmp = imageBitmap ?: return@detectTransformGestures
                                    val oldScale = scale
                                    val newScale = (oldScale * zoom).coerceIn(minScale, maxScale)

                                    // 縮放前手勢中心在影像座標的對應點
                                    val imgX = (centroid.x - offsetX) / oldScale
                                    val imgY = (centroid.y - offsetY) / oldScale

                                    // compute new offsets so imgX,imgY stays under centroid
                                    val newOffsetX = centroid.x - imgX * newScale
                                    val newOffsetY = centroid.y - imgY * newScale

                                    // apply pan delta
                                    offsetX = newOffsetX + pan.x
                                    offsetY = newOffsetY + pan.y

                                    scale = newScale
                                    // clamp to keep image visible
                                    clampOffsets(bmp.width.toFloat(), bmp.height.toFloat())
                                }
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
                                    val oSrcLeft = (srcLeft / ovScaleX).coerceIn(0f, g.w.toFloat())
                                    val oSrcTop = (srcTop / ovScaleY).coerceIn(0f, g.h.toFloat())
                                    val oSrcRight =
                                        (srcRight / ovScaleX).coerceIn(0f, g.w.toFloat())
                                    val oSrcBottom =
                                        (srcBottom / ovScaleY).coerceIn(0f, g.h.toFloat())

                                    val oSrcW = (oSrcRight - oSrcLeft).coerceAtLeast(0f)
                                    val oSrcH = (oSrcBottom - oSrcTop).coerceAtLeast(0f)

                                    if (oSrcW > 0f && oSrcH > 0f) {
                                        drawImage(
                                            image = ov,
                                            srcOffset =
                                                IntOffset(oSrcLeft.toInt(), oSrcTop.toInt()),
                                            srcSize = IntSize(oSrcW.toInt(), oSrcH.toInt()),
                                            // 目的地矩形仍用背景圖的 dst，這樣就能貼齊
                                            dstOffset = IntOffset(dstLeft.toInt(), dstTop.toInt()),
                                            dstSize = IntSize(dstW.toInt(), dstH.toInt())
                                        )
                                    }
                                }
                            }

                            // 可視樣式大小（可依需求微調）
                            val classroomRadius = 10f
                            val startGoalRadius = 16f
                            val pathWidth = 12f // 路徑稍微加粗一點

                            // === 1. 繪製路徑與連續箭頭 ===
                            if (path.isNotEmpty()) {
                                // 先將所有點轉換為螢幕座標
                                val screenPoints = path.map { imgToScreen(it) }

                                // A. 畫路徑底線
                                val p = Path().apply {
                                    moveTo(screenPoints.first().x, screenPoints.first().y)
                                    for (i in 1 until screenPoints.size) {
                                        lineTo(screenPoints[i].x, screenPoints[i].y)
                                    }
                                }

                                // 畫主線條 (使用 secondary 顏色，或是您想要的顏色)
                                drawPath(
                                    path = p,
                                    color = Color.LightGray,
                                    style = Stroke(
                                        width = pathWidth,
                                        cap = StrokeCap.Round,
                                        join = StrokeJoin.Round
                                    )
                                )

                                // B. 畫等距方向箭頭
                                val arrowSpacing = 80f // 箭頭間距 (您可以依需求調整，例如 60f~100f)
                                val arrowSize = 25f    // 箭頭大小
                                var distanceAccumulator = 0f // 累計距離，確保跨線段時箭頭間距均勻

                                for (i in 0 until screenPoints.size - 1) {
                                    val p1 = screenPoints[i]
                                    val p2 = screenPoints[i + 1]

                                    val dx = p2.x - p1.x
                                    val dy = p2.y - p1.y
                                    val segmentDist = hypot(dx, dy)

                                    if (segmentDist == 0f) continue

                                    val angle = atan2(dy, dx)

                                    // 計算這一點開始的第一個箭頭位置
                                    var currentPos = arrowSpacing - distanceAccumulator

                                    while (currentPos <= segmentDist) {
                                        // 計算箭頭在當前線段上的位置比例 t (0.0 ~ 1.0)
                                        val t = currentPos / segmentDist
                                        val ax = p1.x + t * dx
                                        val ay = p1.y + t * dy

                                        // 計算箭頭兩翼座標
                                        val wingAngle = 0.5f // 箭頭開合角度 (弧度)

                                        // 畫紅色箭頭 (或是白色，視您的路徑顏色而定)
                                        // 這裡使用紅色讓它在藍/黃色路徑上更顯眼

                                        val arrowPath = Path().apply {
                                            moveTo(ax, ay) // 箭頭尖端
                                            // 左翼
                                            lineTo(
                                                ax - arrowSize * cos(angle - wingAngle),
                                                ay - arrowSize * sin(angle - wingAngle)
                                            )
                                            // 右翼 (這裡用 moveTo 回到尖端再畫，或者直接畫成三角形)
                                            moveTo(ax, ay)
                                            lineTo(
                                                ax - arrowSize * cos(angle + wingAngle),
                                                ay - arrowSize * sin(angle + wingAngle)
                                            )
                                        }

                                        drawPath(
                                            path = arrowPath,
                                            color = ComposeColor.Blue,
                                            style = Stroke(
                                                width = 6f,
                                                cap = androidx.compose.ui.graphics.StrokeCap.Round
                                            )
                                        )

                                        // 前進到下一個箭頭位置
                                        currentPos += arrowSpacing
                                    }

                                    // 計算這段線段「剩餘」的距離，留給下一段線段使用
                                    // 這樣箭頭在轉彎處的間距才會準確
                                    distanceAccumulator =
                                        (distanceAccumulator + segmentDist) % arrowSpacing
                                }
                            }

                            // === 2. 繪製起點 (綠色 + 文字) ===
                            start?.let {
                                val s = imgToScreen(it)
                                drawCircle(color = ComposeColor.Green, radius = startGoalRadius, center = s)
                                drawCircle(color = ComposeColor.White, radius = startGoalRadius * 0.5f, center = s)
                                drawContext.canvas.nativeCanvas.drawText("起點", s.x, s.y - 30f, textPaint)
                            }

                            // === 3. 繪製終點 (紅色 + 文字) ===
                            goal?.let {
                                val g2 = imgToScreen(it)
                                drawCircle(color = ComposeColor.Red, radius = startGoalRadius, center = g2)
                                drawCircle(color = ComposeColor.White, radius = startGoalRadius * 0.5f, center = g2)
                                drawContext.canvas.nativeCanvas.drawText("終點", g2.x, g2.y - 30f, textPaint)
                            }

                            // 教室點（影像百分比 -> 影像像素 -> 螢幕座標）
                            if (showClassrooms && classroomPoints.isNotEmpty()) {
                                classroomPoints.forEach { rp ->
                                    val imgX = (rp.x.toFloat() / 100f) * bmp.width
                                    val imgY = (rp.y.toFloat() / 100f) * bmp.height
                                    val scr = imgToScreen(Offset(imgX, imgY))

                                    // 判斷是否為出入口：型別為 ENTRANCE 或 名稱包含「入口」
                                    val isEntrance = try {
                                        rp.type.equals("ENTRANCE", true) || rp.name.contains("入口")
                                    } catch (_: Exception) {
                                        false
                                    }

                                    val pointColor =
                                        if (isEntrance) ComposeColor.Blue else colorMaterial.primary.copy(
                                            alpha = 0.9f
                                        )

                                    drawCircle(
                                        color = pointColor,
                                        radius = classroomRadius,
                                        center = scr
                                    )
                                }
                            }
                            if (isLikelyIndoors && positionState.mapGroupName != null && positionState.mapPercentage != null) {

                                // 2. 檢查「目前顯示的圖片」是否等於「定位所在的樓層」
                                // 利用 LocationViewModel 中的 getMapResId 來取得定位樓層的 Resource ID
                                val userMapResId = getMapResId(positionState.mapGroupName)

                                if (userMapResId == currentImageRes) {
                                    val userPercent = positionState.mapPercentage!!
                                    // 3. 座標轉換：百分比 -> 圖片像素 -> 螢幕座標
                                    // bmp.width 是原始圖片寬度，bmp.height 是原始圖片高度
                                    val imgX = (userPercent.x / 100f) * bmp.width
                                    val imgY = (userPercent.y / 100f) * bmp.height

                                    // 使用你現有的 imgToScreen 函式轉換成縮放平移後的螢幕座標
                                    val screenPos = imgToScreen(Offset(imgX, imgY))

                                    // 4. 開始繪製
                                    // (A) 繪製半透明的精確度範圍圈 (淺藍色光暈)
                                    drawCircle(
                                        color = colorMaterial.primary.copy(alpha = 0.3f),
                                        radius = 50f, // 可以根據需求調整大小
                                        center = screenPos
                                    )

                                    // (B) 繪製外圈白框 (讓藍點在深色背景也看得到)
                                    drawCircle(
                                        color = ComposeColor.White,
                                        radius = 25f,
                                        center = screenPos,
                                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 5f)
                                    )

                                    // (C) 繪製實心藍點 (你的位置)
                                    drawCircle(
                                        color = ComposeColor.Blue, // 或者使用 colorMaterial.primary
                                        radius = 20f,
                                        center = screenPos
                                    )
                                }
                            }
                        }
                        // 顯示定位樓層切換按鈕：只要有樓層名稱與圖片已載入即可顯示（不再受 isLikelyIndoors 影響）
                        if (isLikelyIndoors && positionState.mapGroupName != null) {
                            ExtendedFloatingActionButton(
                                // 1. 設定點擊行為
                                onClick = {
                                    val targetResId = getMapResId(positionState.mapGroupName)

                                    // 只有在資源 ID 有效時才執行
                                    if (targetResId != 0) {
                                        // A. 切換圖片
                                        currentImageRes = targetResId
                                        selectedFloorName =
                                            floorPlans.find { it.second == targetResId }?.first
                                                ?: positionState.mapGroupName!!

                                        // B. 計算自動置中與縮放 (取代舊的 setZoom/setScrollPosition)
                                        // 必須確認有定位百分比，且圖片 Bitmap 已載入才能計算
                                        val currentPercent = positionState.mapPercentage
                                        val currentBmp = imageBitmap?.asAndroidBitmap()

                                        if (currentPercent != null && currentBmp != null) {
                                            // 設定一個目標縮放值 (例如放大到 3 倍)
                                            val targetScale = 3f
                                            scale = targetScale

                                            // 計算目標點在圖片上的原始像素位置
                                            val imgX = (currentPercent.x / 100f) * currentBmp.width
                                            val imgY = (currentPercent.y / 100f) * currentBmp.height

                                            // 計算 Offset 讓該點位於畫面中心
                                            // 公式: (容器一半寬度) - (圖片點位置 * 縮放倍率)
                                            offsetX = (containerWidthPx / 2f) - (imgX * targetScale)
                                            offsetY =
                                                (containerHeightPx / 2f) - (imgY * targetScale)

                                            // 限制範圍，避免留白 (呼叫你現有的 clampOffsets)
                                            clampOffsets(
                                                currentBmp.width.toFloat(),
                                                currentBmp.height.toFloat()
                                            )
                                        }
                                    }
                                },
                                // 2. 設定位置與樣式
                                modifier = Modifier
                                    .align(Alignment.BottomEnd) // 靠右下角
                                    .padding(16.dp)             // 邊距
                                    .zIndex(3f),                // 確保浮在最上層
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                // 3. 設定圖示
                                icon = {
                                    Icon(
                                        imageVector = Icons.Default.MyLocation,
                                        contentDescription = "我的位置"
                                    )
                                },
                                // 4. 設定文字
                                text = {
                                    Text(
                                        text = getFloorDisplayName(positionState.mapGroupName),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            )
                        }
                    }
                }
                    ?: run {
                        Box(
                            Modifier.fillMaxSize(),
                            contentAlignment = androidx.compose.ui.Alignment.Center
                        ) { Text("尚未載入平面圖資源") }
                    }


                // 左下角返回室外地圖按鈕（僅在有導航時顯示：start 與 goal 皆存在）
                if (start != null && goal != null) {
                    FloatingActionButton(
                        onClick = { navController?.popBackStack() },
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(12.dp)
                            .zIndex(3f),
                        containerColor = MaterialTheme.colorScheme.primary
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "返回地圖",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }


                // 移除預覽階段按鈕（入口/樓層預覽不再使用）

                // 多段式導航：下一步按鈕（僅在有佇列時顯示；不影響原 preview 按鈕）
                if (showNextStepButton && currentSegment != null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(12.dp)
                            .zIndex(3f)
                    ) {
                        Button(onClick = {
                            scope.launch {
                                if (navigationQueue.isNotEmpty()) {
                                    val nextSeg = navigationQueue.first()
                                    navigationQueue = navigationQueue.drop(1)
                                    // 清理上一段的可視狀態，避免誤導
                                    start = null
                                    goal = null
                                    path = emptyList()
                                    // 僅更新段落，實際處理交由 orchestrator 進行（等待圖片與網格）
                                    currentSegment = nextSeg
                                    showNextStepButton = navigationQueue.isNotEmpty()
                                }
                            }
                        }) {
                            Text(text = currentSegment?.description ?: "下一步")
                        }
                    }
                }

//        // 顯示 debug 訊息（若有）
//        if (debugInfo.isNotBlank()) {
//        Box(modifier = Modifier
//            .align(Alignment.TopCenter)
//            .padding(top = 8.dp)) {
//                Card(colors = CardDefaults.cardColors(containerColor = colorResource(id = R.color.yellow).copy(alpha = 0.9f))) {
//                    Column(modifier = Modifier.padding(8.dp)) {
//                        Text(text = debugInfo, color = colorResource(id = R.color.black))
//                        startGridCell?.let { (x, y) ->
//                            Text(text = "start cell: $x,$y walkable=${startWalkable}", color = colorResource(id = R.color.black))
//                        }
//                        goalGridCell?.let { (x, y) ->
//                            Text(text = "goal cell: $x,$y walkable=${goalWalkable}", color = colorResource(id = R.color.black))
//                        }
//                    }
//                }
//            }
//        }

            }
        }
    }
