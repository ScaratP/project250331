@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.project250311.Map.IndoorMap

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.res.colorResource
import com.example.project250311.Map.IndoorMap.Database.GridCacheEntity
import com.example.project250311.Map.IndoorMap.Database.IndoorMapDatabase
import com.example.project250311.Map.IndoorMap.Database.ReferencePointEntity
import com.example.project250311.R
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.*
import android.util.Log

// ======================= 資料結構 =======================
data class Node(
        val x: Int,
        val y: Int,
        var g: Double = Double.POSITIVE_INFINITY,
        var h: Double = 0.0,
        var parent: Node? = null,
        var walkable: Boolean = true
) {
    val f: Double
        get() = g + h
}

data class Grid(val w: Int, val h: Int, val cells: BooleanArray) {
    fun walkable(x: Int, y: Int) = x in 0 until w && y in 0 until h && cells[y * w + x]
}

// 八方向啟發式
fun heuristic(ax: Int, ay: Int, bx: Int, by: Int): Double {
    val dx = abs(ax - bx)
    val dy = abs(ay - by)
    return (max(dx, dy) - min(dx, dy)) + 1.41421356 * min(dx, dy)
}

// 防止斜角鑽牆角
fun neighbors(grid: Grid, n: Node): List<Node> {
    val res = ArrayList<Node>(8)
    for (dy in -1..1) for (dx in -1..1) {
        if (dx == 0 && dy == 0) continue
        val nx = n.x + dx
        val ny = n.y + dy
        if (!grid.walkable(nx, ny)) continue
        if (dx != 0 && dy != 0) {
            if (!grid.walkable(n.x + dx, n.y) || !grid.walkable(n.x, n.y + dy)) continue
        }
        res.add(Node(nx, ny, walkable = true))
    }
    return res
}

fun aStar(grid: Grid, sx: Int, sy: Int, gx: Int, gy: Int): List<Node> {
    if (!grid.walkable(sx, sy) || !grid.walkable(gx, gy)) return emptyList()
    val open = java.util.PriorityQueue<Node>(compareBy<Node> { it.f }.thenBy { it.h })
    val key = { x: Int, y: Int -> (y.toLong() shl 32) or (x.toLong() and 0xffffffff) }
    val gScore = HashMap<Long, Double>()
    val start = Node(sx, sy, g = 0.0, h = heuristic(sx, sy, gx, gy))
    open.add(start)
    gScore[key(sx, sy)] = 0.0

    while (open.isNotEmpty()) {
        val cur = open.poll()
        if (cur.x == gx && cur.y == gy) {
            val out = mutableListOf<Node>()
            var p: Node? = cur
            while (p != null) {
                out += p
                p = p.parent
            }
            return out.asReversed()
        }
        for (nb in neighbors(grid, cur)) {
            val step = if (nb.x != cur.x && nb.y != cur.y) 1.41421356 else 1.0
            val tentative = gScore.getOrDefault(key(cur.x, cur.y), Double.POSITIVE_INFINITY) + step
            val nbKey = key(nb.x, nb.y)
            if (tentative < gScore.getOrDefault(nbKey, Double.POSITIVE_INFINITY)) {
                val nnode =
                        Node(
                                nb.x,
                                nb.y,
                                g = tentative,
                                h = heuristic(nb.x, nb.y, gx, gy),
                                parent = cur
                        )
                gScore[nbKey] = tentative
                open.add(nnode)
            }
        }
    }
    return emptyList()
}

// ======================= 路徑平滑 =======================
fun lineOfSight(grid: Grid, ax: Int, ay: Int, bx: Int, by: Int): Boolean {
    var x0 = ax
    var y0 = ay
    val x1 = bx
    val y1 = by
    val dx = abs(x1 - x0)
    val dy = -abs(y1 - y0)
    val sx = if (x0 < x1) 1 else -1
    val sy = if (y0 < y1) 1 else -1
    var err = dx + dy
    while (true) {
        if (!grid.walkable(x0, y0)) return false
        if (x0 == x1 && y0 == y1) break
        val e2 = 2 * err
        if (e2 >= dy) {
            err += dy
            x0 += sx
        }
        if (e2 <= dx) {
            err += dx
            y0 += sy
        }
    }
    return true
}

fun smoothByVisibility(raw: List<Node>, grid: Grid): List<Node> {
    if (raw.size <= 2) return raw
    val out = mutableListOf<Node>()
    var anchor = 0
    out += raw.first()
    var i = 2
    while (i < raw.size) {
        val a = raw[anchor]
        val b = raw[i]
        if (!lineOfSight(grid, a.x, a.y, b.x, b.y)) {
            out += raw[i - 1]
            anchor = i - 1
        }
        i++
    }
    out += raw.last()
    return out
}

fun rdp(points: List<Offset>, eps: Float): List<Offset> {
    if (points.size < 3) return points
    var index = 0
    var dmax = 0f
    val a = points.first()
    val b = points.last()
    for (i in 1 until points.size - 1) {
        val d = perpDist(points[i], a, b)
        if (d > dmax) {
            index = i
            dmax = d
        }
    }
    return if (dmax > eps) {
        val left = rdp(points.subList(0, index + 1), eps)
        val right = rdp(points.subList(index, points.size), eps)
        left.dropLast(1) + right
    } else listOf(a, b)
}

fun perpDist(p: Offset, a: Offset, b: Offset): Float {
    val num = kotlin.math.abs((b.x - a.x) * (a.y - p.y) - (a.x - p.x) * (b.y - a.y))
    val den = hypot((b.x - a.x).toDouble(), (b.y - a.y).toDouble()).toFloat()
    return if (den == 0f) 0f else num / den
}

// ======================= 影像 -> 可通行網格（只認近白走廊） =======================
fun bitmapToGridFromWhiteCorridor(
        bitmap: Bitmap,
        sample: Int = 2,
        satMax: Float = 0.12f, // 放寬以容錯
        valMin: Float = 0.92f,
        wallInflate: Int = 3
): Grid {
    val w = (bitmap.width / sample).coerceAtLeast(1)
    val h = (bitmap.height / sample).coerceAtLeast(1)
    val cells = BooleanArray(w * h) { false }

    fun isNearWhite(px: Int, py: Int): Boolean {
        if (px !in 0 until bitmap.width || py !in 0 until bitmap.height) return false
        val c = bitmap.getPixel(px, py)
        val r = Color.red(c) / 255f
        val g = Color.green(c) / 255f
        val b = Color.blue(c) / 255f
        val maxv = max(r, max(g, b))
        val minv = min(r, min(g, b))
        val v = maxv
        val s = if (maxv == 0f) 0f else (maxv - minv) / maxv
        return (s <= satMax && v >= valMin)
    }

    for (gy in 0 until h) for (gx in 0 until w) {
        val sx = gx * sample
        val sy = gy * sample
        cells[gy * w + gx] = isNearWhite(sx, sy)
    }

    if (wallInflate > 0) {
        val out = cells.copyOf()
        fun block(x: Int, y: Int) {
            if (x in 0 until w && y in 0 until h) out[y * w + x] = false
        }
        for (y in 0 until h) for (x in 0 until w) if (!cells[y * w + x]) {
            for (dy in -wallInflate..wallInflate) for (dx in -wallInflate..wallInflate) block(
                    x + dx,
                    y + dy
            )
        }
        return Grid(w, h, out)
    }
    return Grid(w, h, cells)
}

// ============ BooleanArray 壓縮/解壓成 ByteArray（bit-packed） ============
private fun BooleanArray.toBitPackedBytes(): ByteArray {
    val out = ByteArray((size + 7) / 8)
    for (i in indices) {
        if (this[i]) {
            val byteIndex = i ushr 3
            val bitIndex = i and 7
            out[byteIndex] = (out[byteIndex].toInt() or (1 shl bitIndex)).toByte()
        }
    }
    return out
}

private fun ByteArray.toBooleanArray(totalBits: Int): BooleanArray {
    val out = BooleanArray(totalBits)
    for (i in 0 until totalBits) {
        val byteIndex = i ushr 3
        val bitIndex = i and 7
        out[i] = (this[byteIndex].toInt() shr bitIndex) and 1 == 1
    }
    return out
}

// ======================= 將 Grid 轉成覆蓋圖 =======================
suspend fun buildGridOverlayBitmap(g: Grid): ImageBitmap =
        withContext(Dispatchers.Default) {
            val bmp = Bitmap.createBitmap(g.w, g.h, Bitmap.Config.ARGB_8888)
            val blocked = Color.argb(20, 0, 0, 0) // 淡灰（不可走）
            val walkable = Color.argb(110, 0, 180, 255) // 半透明藍綠（可走）
            var idx = 0
            for (y in 0 until g.h) {
                for (x in 0 until g.w) {
                    bmp.setPixel(x, y, if (g.cells[idx]) walkable else blocked)
                    idx++
                }
            }
            bmp.asImageBitmap()
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
    autoStart: Boolean = true
) {
    val context = LocalContext.current
    val colorMaterial = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()

    // DB 與 DAO（快取）
    val db = remember { IndoorMapDatabase.getDatabase(context) }
    val gridDao = remember(db) { db.gridCacheDao() }
    val refDao = remember(db) { db.referencePointDao() } // 新增：參考點 DAO

    // (moved) LaunchedEffect that auto-loads target/entry and computes path is placed after recomputePathAsync

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
    var imageBitmap by remember { mutableStateOf<ImageBitmap?>(null) }

    // ===== (2) 載入時先縮圖（依裝置寬高上限） =====
    LaunchedEffect(currentImageRes) {
        // Decode and scale bitmap off the main thread to avoid UI freezes (was blocking main thread)
        val finalBmp = withContext(Dispatchers.Default) {
            try {
                // decodeResource is safe to call off main thread
                val decoded = BitmapFactory.decodeResource(context.resources, currentImageRes) ?: return@withContext null
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

        // set imageBitmap on main thread
        imageBitmap = finalBmp?.asImageBitmap()
    }

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

    // 導航狀態
    var grid by remember { mutableStateOf<Grid?>(null) }
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

    // 新增：Overlay 與偵錯統計（原本有使用到，但未宣告）
    var overlay by remember { mutableStateOf<ImageBitmap?>(null) }
    var walkableCount by remember { mutableStateOf(0) }

    // 顯示教室點
    var showClassrooms by remember { mutableStateOf(false) }
    var classroomPoints by remember { mutableStateOf<List<ReferencePointEntity>>(emptyList()) }

    // 新增：工具函式（先前使用但未宣告，導致型別推論錯誤）
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
                val vis = smoothByVisibility(raw, g)
                val px = vis.map { node -> Offset((node.x + 0.5f) * gridSample, (node.y + 0.5f) * gridSample) }
                val simplified = rdp(px, eps = (gridSample * 0.75f))
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

    // 當透過外部參數 (targetPointId) 呼叫時，自動載入目標與入口並計算路徑
    LaunchedEffect(targetPointId, entryPointId, currentImageRes, imageBitmap) {
        if (targetPointId == null) return@LaunchedEffect
        try {
            // 取得所有參考點（一次性）
            val all = withContext(Dispatchers.IO) { refDao.getAllReferencePoints().first() }
            val targetEntity = all.firstOrNull { it.id == targetPointId }
            if (targetEntity != null) {
                // 設定樓層圖片資源
                currentImageRes = targetEntity.imageId

                // 等待 imageBitmap 載入
                // imageBitmap 會因 currentImageRes 而在另一個 LaunchedEffect 載入
                // 監聽 imageBitmap 非空
                withContext(Dispatchers.Default) {
                    var attempts = 0
                    while (imageBitmap == null && attempts < 50) {
                        attempts++
                        delay(60)
                    }
                }

                val bmp = imageBitmap?.asAndroidBitmap()
                if (bmp != null) {
                    // 找入口：優先使用 entryPointId，否則尋找 building+floor 的 ENTRANCE
                    val entryEntity = if (!entryPointId.isNullOrBlank()) all.firstOrNull { it.id == entryPointId } else {
                        all.firstOrNull { it.buildingId == targetEntity.buildingId && it.floorId == targetEntity.floorId && it.type.equals("ENTRANCE", true) }
                    }

                    // 若無入口仍嘗試從 DB 取得任一教室點作為 goal（不會導致 crash）
                    val s = entryEntity?.let { Offset((it.x.toFloat() / 100f) * bmp.width, (it.y.toFloat() / 100f) * bmp.height) }
                    val g = Offset((targetEntity.x.toFloat() / 100f) * bmp.width, (targetEntity.y.toFloat() / 100f) * bmp.height)

                    start = s
                    goal = g

                    if (autoStart) {
                        recomputePathAsync()
                    }
                }
            }
        } catch (e: Exception) {
            // 忽略錯誤，UI 可顯示或回傳
        }
    }

    // 先嘗試從 DB 讀取快取（不需等待圖片載入）
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

            // build overlay off the main thread
            val ov = withContext(Dispatchers.Default) { buildGridOverlayBitmap(g) }

            // update UI state on Main (we're in a LaunchedEffect with Main dispatcher)
            grid = g
            walkableCount = cells.count { it }
            overlay = ov
        }
    }

    // 若沒快取且圖片已載入，則計算並寫回 DB
    LaunchedEffect(imageBitmap, gridSample, currentImageRes) {
        if (grid != null) return@LaunchedEffect
        val bmp = imageBitmap?.asAndroidBitmap() ?: return@LaunchedEffect

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

    // build overlay off the main thread
    val ov = withContext(Dispatchers.Default) { buildGridOverlayBitmap(g) }

    // 更新 UI 狀態 (LaunchedEffect runs on Main dispatcher)
    grid = g
    overlay = ov
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

    // 當 start/goal 與 grid 都就緒時，自動計算路徑（補強：避免 target/entry 的 LaunchedEffect 在 grid 構建前就呼叫 recomputePathAsync）
    LaunchedEffect(start, goal, grid) {
        if (start != null && goal != null && grid != null) {
            recomputePathAsync()
        }
    }

    Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("平面圖導航 Demo", fontWeight = FontWeight.SemiBold) },
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
                            TextButton(
                                    onClick = {
                                        val bmp =
                                                imageBitmap?.asAndroidBitmap() ?: return@TextButton
                                        scope.launch(Dispatchers.Default) {
                                            val g =
                                                    bitmapToGridFromWhiteCorridor(
                                                            bitmap = bmp,
                                                            sample = gridSample,
                                                            satMax = 0.12f,
                                                            valMin = 0.92f,
                                                            wallInflate = 3
                                                    )
                                            val ov = buildGridOverlayBitmap(g)
                                            withContext(Dispatchers.Main) {
                                                grid = g
                                                overlay = ov
                                                start = null
                                                goal = null
                                                path = emptyList()
                                                walkableCount = g.cells.count { it }
                                            }
                                            val packed = g.cells.toBitPackedBytes()
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
                                    }
                            ) { Text("重建網格") }

                            // 顯示/隱藏網格覆蓋
                            TextButton(onClick = { showGridOverlay = !showGridOverlay }) {
                                Text(if (showGridOverlay) "隱藏網格" else "顯示網格")
                            }

                            // 新增：顯示/隱藏 教室點
                            TextButton(onClick = { showClassrooms = !showClassrooms }) {
                                Text(if (showClassrooms) "隱藏教室" else "顯示教室")
                            }

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
                            ) { Text("清除") }
                        }
                )
            }
    ) { padding ->
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).padding(padding)) {
            imageBitmap?.let { bmp ->
                Box(
                        Modifier.fillMaxSize()
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
                                val oSrcLeft = (srcLeft / ovScaleX).coerceIn(0f, g.w.toFloat())
                                val oSrcTop = (srcTop / ovScaleY).coerceIn(0f, g.h.toFloat())
                                val oSrcRight = (srcRight / ovScaleX).coerceIn(0f, g.w.toFloat())
                                val oSrcBottom = (srcBottom / ovScaleY).coerceIn(0f, g.h.toFloat())

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

                                    // Debug: 顯示 start/goal 對應的 grid cell 與 walkable 狀態（只用文字提示）
                                    // (更詳細的格子可視化可再加入)
                                    // note: debugInfo 由 recomputePathAsync 更新
                                    // 在 Canvas 內無法直接 draw text easily; 改在外層顯示 box
                    }
                }
                    }
                    ?: run {
                        Box(
                                Modifier.fillMaxSize(),
                                contentAlignment = androidx.compose.ui.Alignment.Center
                        ) { Text("尚未載入平面圖資源") }
                    }
            
            // 左下角返回室外地圖按鈕
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

            // 顯示 debug 訊息（若有）
            if (debugInfo.isNotBlank()) {
                Box(modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 8.dp)) {
                    Card(colors = CardDefaults.cardColors(containerColor = colorResource(id = R.color.yellow).copy(alpha = 0.9f))) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text(text = debugInfo, color = colorResource(id = R.color.black))
                            startGridCell?.let { (x, y) ->
                                Text(text = "start cell: $x,$y walkable=${startWalkable}", color = colorResource(id = R.color.black))
                            }
                            goalGridCell?.let { (x, y) ->
                                Text(text = "goal cell: $x,$y walkable=${goalWalkable}", color = colorResource(id = R.color.black))
                            }
                        }
                    }
                }
            }

        }
    }
}
