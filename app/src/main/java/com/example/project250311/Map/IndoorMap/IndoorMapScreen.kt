@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.project250311.Map.IndoorMap

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.util.Log
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.TransformableState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.navigation.NavHostController
import com.example.project250311.Map.IndoorMap.Database.GridCacheEntity
import com.example.project250311.Map.IndoorMap.Database.IndoorMapDatabase
import com.example.project250311.Map.IndoorMap.Database.ReferencePointEntity
import com.example.project250311.R
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.util.ArrayDeque
import java.util.PriorityQueue
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

// ======================= IndoorPathfinder (封裝路徑演算法) =======================
object IndoorPathfinder {
    // --- 資料結構 ---
    data class Node(
        val x: Int,
        val y: Int,
        var g: Double = Double.POSITIVE_INFINITY,
        var h: Double = 0.0,
        var parent: Node? = null,
        var walkable: Boolean = true
    ) {
        val f: Double get() = g + h
    }

    data class Grid(val w: Int, val h: Int, val cells: BooleanArray) {
        fun walkable(x: Int, y: Int) = x in 0 until w && y in 0 until h && cells[y * w + x]
    }

    // --- A* 演算法 ---
    fun heuristic(ax: Int, ay: Int, bx: Int, by: Int): Double {
        val dx = abs(ax - bx)
        val dy = abs(ay - by)
        return (max(dx, dy) - min(dx, dy)) + 1.41421356 * min(dx, dy)
    }

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
        if (sx !in 0 until grid.w || sy !in 0 until grid.h || gx !in 0 until grid.w || gy !in 0 until grid.h) return emptyList()

        val open = PriorityQueue<Node>(compareBy<Node> { it.f }.thenBy { it.h })
        val key = { x: Int, y: Int -> (y.toLong() shl 32) or (x.toLong() and 0xffffffff) }
        val gScore = HashMap<Long, Double>()

        val start = Node(sx, sy, g = 0.0, h = heuristic(sx, sy, gx, gy))
        open.add(start)
        gScore[key(sx, sy)] = 0.0

        var loopCount = 0
        val maxLoops = grid.w * grid.h * 4

        while (open.isNotEmpty()) {
            if (loopCount++ > maxLoops) break

            val cur = open.poll() ?: break
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
                    val nnode = Node(
                        nb.x, nb.y,
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

    // --- (✨) 尋找最近可通行點 (BFS 吸附機制) ---
    fun findNearestWalkableNode(grid: Grid, x: Int, y: Int, maxRadius: Int = 40): Pair<Int, Int>? {
        if (grid.walkable(x, y)) return x to y

        val queue = ArrayDeque<Pair<Int, Int>>()
        val visited = HashSet<Long>()
        val key = { px: Int, py: Int -> (py.toLong() shl 32) or (px.toLong() and 0xffffffff) }

        queue.add(x to y)
        visited.add(key(x, y))

        while (!queue.isEmpty()) {
            val (cx, cy) = queue.poll()!!
            if (abs(cx - x) > maxRadius || abs(cy - y) > maxRadius) continue

            for (dy in -1..1) {
                for (dx in -1..1) {
                    if (dx == 0 && dy == 0) continue
                    val nx = cx + dx
                    val ny = cy + dy

                    if (nx in 0 until grid.w && ny in 0 until grid.h) {
                        val k = key(nx, ny)
                        if (k !in visited) {
                            if (grid.walkable(nx, ny)) {
                                return nx to ny
                            }
                            visited.add(k)
                            queue.add(nx to ny)
                        }
                    }
                }
            }
        }
        return null
    }

    // --- 路徑平滑 ---
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

    // --- 影像 -> 可通行網格 ---
    fun bitmapToGridFromWhiteCorridor(
        bitmap: Bitmap,
        sample: Int = 2,
        satMax: Float = 0.12f,
        valMin: Float = 0.92f,
        wallInflate: Int = 3,
        referencePoints: List<ReferencePointEntity> = emptyList()
    ): Grid {
        val w = (bitmap.width / sample).coerceAtLeast(1)
        val h = (bitmap.height / sample).coerceAtLeast(1)

        val pointGridCoords = mutableSetOf<Pair<Int, Int>>()
        referencePoints.forEach {
            if (it.type == "CLASSROOM" || it.type == "CORRIDOR" || it.type == "STAIRS" || it.type == "ELEVATOR" || it.type == "ENTRANCE" || it.type == "TOILET" || it.type == "OTHER") {
                val imgX = (it.x.toFloat() / 100f) * bitmap.width
                val imgY = (it.y.toFloat() / 100f) * bitmap.height
                val gx = (imgX / sample).toInt()
                val gy = (imgY / sample).toInt()
                for (dy in -10..10) {
                    for (dx in -10..10) {
                        pointGridCoords.add((gx + dx) to (gy + dy))
                    }
                }
            }
        }

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

        val cells = BooleanArray(w * h) { true }

        for (gy in 0 until h) {
            for (gx in 0 until w) {
                val sx = gx * sample
                val sy = gy * sample
                if (!isNearWhite(sx, sy) && (gx to gy) !in pointGridCoords) {
                    cells[gy * w + gx] = false
                }
            }
        }

        if (wallInflate > 0) {
            val out = cells.copyOf()
            fun block(x: Int, y: Int) {
                if (x in 0 until w && y in 0 until h) out[y * w + x] = false
            }
            for (y in 0 until h) {
                for (x in 0 until w) {
                    if (!cells[y * w + x]) {
                        for (dy in -wallInflate..wallInflate) {
                            for (dx in -wallInflate..wallInflate) {
                                block(x + dx, y + dy)
                            }
                        }
                    }
                }
            }
            pointGridCoords.forEach { (x, y) ->
                if (x in 0 until w && y in 0 until h) {
                    out[y * w + x] = true
                }
            }
            return Grid(w, h, out)
        }
        return Grid(w, h, cells)
    }

    // --- Bit Packing ---
    fun BooleanArray.toBitPackedBytes(): ByteArray {
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

    fun ByteArray.toBooleanArray(totalBits: Int): BooleanArray {
        val out = BooleanArray(totalBits)
        for (i in 0 until totalBits) {
            val byteIndex = i ushr 3
            val bitIndex = i and 7
            out[i] = (this[byteIndex].toInt() shr bitIndex) and 1 == 1
        }
        return out
    }

    suspend fun buildGridOverlayBitmap(g: Grid): ImageBitmap =
        withContext(Dispatchers.Default) {
            val bmp = Bitmap.createBitmap(g.w, g.h, Bitmap.Config.ARGB_8888)
            val blocked = android.graphics.Color.argb(20, 0, 0, 0)
            val walkable = android.graphics.Color.argb(110, 0, 180, 255)
            var idx = 0
            for (y in 0 until g.h) {
                for (x in 0 until g.w) {
                    bmp.setPixel(x, y, if (g.cells[idx]) walkable else blocked)
                    idx++
                }
            }
            bmp.asImageBitmap()
        }
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

    // DB 與 DAO
    val db = remember { IndoorMapDatabase.getDatabase(context) }
    val gridDao = remember(db) { db.gridCacheDao() }
    val refDao = remember(db) { db.referencePointDao() }

    // 持續觀察參考點，確保資料庫匯入完成後網格能自動重建
    var currentImageRes by remember { mutableStateOf(R.drawable.se1) }
    val currentRefPoints by refDao.getReferencePointsByImageId(currentImageRes).collectAsState(initial = emptyList())

    val floorPlans = listOf(
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
    var imageBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var loadedImageRes by remember { mutableStateOf<Int?>(null) }

    // 視窗互動狀態
    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }
    var minScale by remember { mutableStateOf(1f) }
    val maxScale = 6f

    var containerWidthPx by remember { mutableStateOf(0f) }
    var containerHeightPx by remember { mutableStateOf(0f) }

    // 載入圖片與縮圖
    LaunchedEffect(currentImageRes) {
        val requestedRes = currentImageRes
        val finalBmp = withContext(Dispatchers.Default) {
            try {
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
        imageBitmap = finalBmp?.asImageBitmap()
        loadedImageRes = if (finalBmp != null) requestedRes else null
    }

    fun clampOffsets(imageWidth: Float, imageHeight: Float) {
        val dispW = imageWidth * scale
        val dispH = imageHeight * scale
        if (dispW <= containerWidthPx) {
            offsetX = (containerWidthPx - dispW) / 2f
        } else {
            val minX = containerWidthPx - dispW
            val maxX = 0f
            if (offsetX < minX) offsetX = minX
            if (offsetX > maxX) offsetX = maxX
        }
        if (dispH <= containerHeightPx) {
            offsetY = (containerHeightPx - dispH) / 2f
        } else {
            val minY = containerHeightPx - dispH
            val maxY = 0f
            if (offsetY < minY) offsetY = minY
            if (offsetY > maxY) offsetY = maxY
        }
    }

    LaunchedEffect(imageBitmap, containerWidthPx, containerHeightPx) {
        val bmp = imageBitmap?.asAndroidBitmap() ?: return@LaunchedEffect
        if (containerWidthPx <= 0f || containerHeightPx <= 0f) return@LaunchedEffect

        val fitMin = if (bmp.width > 0) (containerWidthPx / bmp.width.toFloat()) else 1f
        minScale = fitMin.coerceAtMost(maxScale)
        scale = minScale
        offsetX = (containerWidthPx - bmp.width * scale) / 2f
        offsetY = (containerHeightPx - bmp.height * scale) / 2f
        clampOffsets(bmp.width.toFloat(), bmp.height.toFloat())
    }

    // 導航狀態
    var grid by remember { mutableStateOf<IndoorPathfinder.Grid?>(null) }
    var gridSample by remember { mutableStateOf(2) }
    var start by remember { mutableStateOf<Offset?>(null) }
    var goal by remember { mutableStateOf<Offset?>(null) }
    var path by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var showGridOverlay by remember { mutableStateOf(false) }

    // Debug
    var debugInfo by remember { mutableStateOf("") }
    var startGridCell by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var goalGridCell by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var startWalkable by remember { mutableStateOf<Boolean?>(null) }
    var goalWalkable by remember { mutableStateOf<Boolean?>(null) }

    var overlay by remember { mutableStateOf<ImageBitmap?>(null) }
    var walkableCount by remember { mutableStateOf(0) }

    var showClassrooms by remember { mutableStateOf(false) }
    // 使用 currentRefPoints 進行過濾
    val classroomPoints by remember(currentRefPoints, showClassrooms) {
        derivedStateOf {
            if (showClassrooms) {
                currentRefPoints.filter { rp ->
                    rp.type.equals("CLASSROOM", true) || rp.type.equals("STAIRS", true) || rp.type.equals("ENTRANCE", true)
                }.distinctBy { it.id }
            } else {
                emptyList()
            }
        }
    }
    var resolvedEntranceInfo by remember { mutableStateOf<String?>(null) }

    fun screenToImage(p: Offset): Offset? {
        val bmp = imageBitmap ?: return null
        val ix = ((p.x - offsetX) / scale)
        val iy = ((p.y - offsetY) / scale)
        if (ix < 0 || iy < 0 || ix >= bmp.width || iy >= bmp.height) return null
        return Offset(ix, iy)
    }

    fun imageToGrid(pt: Offset, g: IndoorPathfinder.Grid, s: Int): Pair<Int, Int> {
        val gx = (pt.x / s).toInt()
        val gy = (pt.y / s).toInt()
        return gx to gy
    }

    fun resolvePreferredEntrance(
        all: List<ReferencePointEntity>,
        targetEntity: ReferencePointEntity,
        entryPointId: String?
    ): ReferencePointEntity? {
        if (!entryPointId.isNullOrBlank()) {
            val byId = all.firstOrNull { it.id == entryPointId }
            if (byId != null) return byId
        }
        val candidateBuildings = mutableListOf<String>()
        try {
            if (!targetEntity.buildingId.isNullOrBlank()) candidateBuildings.add(targetEntity.buildingId.uppercase())
            val prefixSource = (targetEntity.id.ifBlank { targetEntity.name }).lowercase()
            val prefix = prefixSource.takeWhile { it.isLetter() }
            when (prefix) {
                "sea", "se" -> if (!candidateBuildings.contains("SE")) candidateBuildings.add("SE")
                "seb" -> if (!candidateBuildings.contains("SEB")) candidateBuildings.add("SEB")
                "sec" -> if (!candidateBuildings.contains("SEC")) candidateBuildings.add("SEC")
            }
        } catch (_: Exception) { }

        for (b in candidateBuildings) {
            val found = all.firstOrNull { it.type.equals("ENTRANCE", true) && it.buildingId.equals(b, true) }
            if (found != null) return found
        }

        val seb = all.firstOrNull { it.type.equals("ENTRANCE", true) && it.buildingId.equals("SEB", true) }
        if (seb != null) return seb
        val se = all.firstOrNull { it.type.equals("ENTRANCE", true) && it.buildingId.equals("SE", true) }
        if (se != null) return se

        return all.firstOrNull { it.type.equals("ENTRANCE", true) }
    }

    var previewEntryPhase by remember { mutableStateOf(false) }
    var previewEntryEntity by remember { mutableStateOf<ReferencePointEntity?>(null) }
    var previewTargetEntity by remember { mutableStateOf<ReferencePointEntity?>(null) }
    var previewTargetFloorName by remember { mutableStateOf<String?>(null) }

    // ===== (3) 路徑計算 (吸附 + A*) =====
    fun recomputePathAsync() {
        val g = grid ?: return
        val sPt = start ?: return
        val ePt = goal ?: return
        val (rawSx, rawSy) = imageToGrid(sPt, g, gridSample)
        val (rawGx, rawGy) = imageToGrid(ePt, g, gridSample)

        scope.launch(Dispatchers.Default) {
            try {
                // (✨) 吸附：找最近的可通行點
                val startNode = IndoorPathfinder.findNearestWalkableNode(g, rawSx, rawSy)
                val goalNode = IndoorPathfinder.findNearestWalkableNode(g, rawGx, rawGy)

                if (startNode == null || goalNode == null) {
                    withContext(Dispatchers.Main) {
                        debugInfo = "無法計算：起點或終點周圍沒有可通行區域"
                        path = emptyList()
                    }
                    return@launch
                }

                val (sx, sy) = startNode
                val (gx, gy) = goalNode

                startGridCell = sx to sy
                goalGridCell = gx to gy
                startWalkable = true
                goalWalkable = true

                val t0 = System.currentTimeMillis()
                val raw = IndoorPathfinder.aStar(g, sx, sy, gx, gy)
                val took = System.currentTimeMillis() - t0

                if (raw.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        debugInfo = "A* 無路徑 (計算 ${took}ms)"
                        path = emptyList()
                    }
                    return@launch
                }

                val vis = IndoorPathfinder.smoothByVisibility(raw, g)

                // (✨) 視覺連接：把原始點加回去
                val pxPath = vis.map { node ->
                    Offset((node.x + 0.5f) * gridSample, (node.y + 0.5f) * gridSample)
                }.toMutableList()
                pxPath.add(0, sPt)
                pxPath.add(ePt)

                val simplified = IndoorPathfinder.rdp(pxPath, eps = (gridSample * 0.75f))

                withContext(Dispatchers.Main) {
                    path = simplified
                    debugInfo = "路徑計算成功: ${simplified.size} 點 (${took}ms)"
                }
            } catch (e: Exception) {
                Log.e("IndoorMap", "recomputePathAsync error", e)
                withContext(Dispatchers.Main) {
                    debugInfo = "計算錯誤: ${e.localizedMessage}"
                    path = emptyList()
                }
            }
        }
    }

    fun imgToScreen(p: Offset) = Offset(p.x * scale + offsetX, p.y * scale + offsetY)

    // 自動載入目標
    LaunchedEffect(targetPointId, entryPointId, currentImageRes, imageBitmap) {
        if (targetPointId == null) return@LaunchedEffect
        try {
            val all = withContext(Dispatchers.IO) { refDao.getAllReferencePoints().first() }
            val targetEntity = all.firstOrNull { it.id == targetPointId }
            if (targetEntity != null) {
                val entryEntity = resolvePreferredEntrance(all, targetEntity, entryPointId)
                resolvedEntranceInfo = entryEntity?.let { "${it.id}/${it.name}" }

                if (entryEntity != null && entryEntity.floorId != targetEntity.floorId && targetEntity.floorId != 1) {
                    previewEntryPhase = true
                    previewEntryEntity = entryEntity
                    previewTargetEntity = targetEntity
                    previewTargetFloorName = "${targetEntity.buildingId}${targetEntity.floorId}樓"

                    currentImageRes = entryEntity.imageId
                    withContext(Dispatchers.Default) {
                        var attempts = 0
                        while (loadedImageRes != entryEntity.imageId && attempts < 100) {
                            attempts++
                            delay(60)
                        }
                    }
                    val bmp2 = imageBitmap?.asAndroidBitmap()
                    if (bmp2 != null) {
                        val floorPoints = all.filter { it.imageId == entryEntity.imageId }
                        val entranceOnFloor = floorPoints.firstOrNull { it.type.equals("ENTRANCE", true) }
                        val vertical = floorPoints.filter { it.type.equals("STAIRS", true) || it.type.equals("ELEVATOR", true) }
                        val targetVertical = vertical.firstOrNull()

                        val sPoint = entranceOnFloor?.let { Offset((it.x.toFloat() / 100f) * bmp2.width, (it.y.toFloat() / 100f) * bmp2.height) }
                        val gPoint = targetVertical?.let { Offset((it.x.toFloat() / 100f) * bmp2.width, (it.y.toFloat() / 100f) * bmp2.height) }

                        start = sPoint
                        goal = gPoint
                        showClassrooms = true
                        if (autoStart) recomputePathAsync()
                    }
                    return@LaunchedEffect
                }

                currentImageRes = targetEntity.imageId
                withContext(Dispatchers.Default) {
                    var attempts = 0
                    while (loadedImageRes != targetEntity.imageId && attempts < 100) {
                        attempts++
                        delay(60)
                    }
                }
                val bmp = imageBitmap?.asAndroidBitmap()
                if (bmp != null) {
                    val s = entryEntity?.let { Offset((it.x.toFloat() / 100f) * bmp.width, (it.y.toFloat() / 100f) * bmp.height) }
                    val g = Offset((targetEntity.x.toFloat() / 100f) * bmp.width, (targetEntity.y.toFloat() / 100f) * bmp.height)
                    start = s
                    goal = g
                    if (autoStart) recomputePathAsync()
                }
            }
        } catch (e: Exception) {}
    }

    // 從 DB 讀取 Grid Cache
    LaunchedEffect(currentImageRes, gridSample) {
        grid = null
        overlay = null
        start = null
        goal = null
        path = emptyList()
        walkableCount = 0

        val cached = withContext(Dispatchers.IO) { gridDao.get(currentImageRes, gridSample) }
        if (cached != null) {
            val cells = with(IndoorPathfinder) { cached.cells.toBooleanArray(cached.width * cached.height) }
            val g = IndoorPathfinder.Grid(cached.width, cached.height, cells)
            val ov = IndoorPathfinder.buildGridOverlayBitmap(g)
            grid = g
            walkableCount = cells.count { it }
            overlay = ov
        }
    }

    // 若沒 Cache 則計算；即使有 Cache，若參考點更新了也重建 (關鍵修正)
    LaunchedEffect(imageBitmap, gridSample, currentImageRes, currentRefPoints) {
        if (currentRefPoints.isEmpty()) return@LaunchedEffect

        val bmp = imageBitmap?.asAndroidBitmap() ?: return@LaunchedEffect

        val g = withContext(Dispatchers.Default) {
            IndoorPathfinder.bitmapToGridFromWhiteCorridor(
                bitmap = bmp,
                sample = gridSample,
                satMax = 0.12f,
                valMin = 0.92f,
                wallInflate = 3,
                referencePoints = currentRefPoints
            )
        }

        val ov = IndoorPathfinder.buildGridOverlayBitmap(g)
        grid = g
        overlay = ov
        walkableCount = g.cells.count { it }

        val packed = with(IndoorPathfinder) { g.cells.toBitPackedBytes() }
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
                                        start = null
                                        goal = null
                                        path = emptyList()
                                        grid = null
                                        overlay = null
                                    }
                                )
                            }
                        }
                    }

                    TextButton(
                        onClick = {
                            val bmp = imageBitmap?.asAndroidBitmap() ?: return@TextButton
                            scope.launch(Dispatchers.Default) {
                                val g = IndoorPathfinder.bitmapToGridFromWhiteCorridor(
                                    bitmap = bmp,
                                    sample = gridSample,
                                    referencePoints = currentRefPoints
                                )
                                val ov = IndoorPathfinder.buildGridOverlayBitmap(g)
                                withContext(Dispatchers.Main) {
                                    grid = g
                                    overlay = ov
                                    start = null
                                    goal = null
                                    path = emptyList()
                                    walkableCount = g.cells.count { it }
                                }
                                val packed = with(IndoorPathfinder) { g.cells.toBitPackedBytes() }
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
                    ) { Text("重建網格") }

                    TextButton(onClick = { showGridOverlay = !showGridOverlay }) {
                        Text(if (showGridOverlay) "隱藏網格" else "顯示網格")
                    }

                    TextButton(onClick = { showClassrooms = !showClassrooms }) {
                        Text(if (showClassrooms) "隱藏教室" else "顯示教室")
                    }

                    Text("walkable=$walkableCount", style = MaterialTheme.typography.labelSmall)

                    TextButton(onClick = {
                        start = null
                        goal = null
                        path = emptyList()
                    }) { Text("清除") }
                }
            )
        }
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .padding(padding)
                .onSizeChanged {
                    containerWidthPx = it.width.toFloat()
                    containerHeightPx = it.height.toFloat()
                }
        ) {
            imageBitmap?.let { bmp ->
                Box(
                    Modifier
                        .fillMaxSize()
                        .clipToBounds()
                        .pointerInput(Unit) {
                            detectTapGestures(onDoubleTap = { p ->
                                val ip = screenToImage(p) ?: return@detectTapGestures
                                if (start == null) start = ip
                                else if (goal == null) {
                                    goal = ip
                                    recomputePathAsync()
                                } else {
                                    start = ip
                                    goal = null
                                    path = emptyList()
                                }
                            })
                        }
                        .pointerInput(Unit) {
                            detectTransformGestures { centroid, pan, zoom, _ ->
                                val oldScale = scale
                                val newScale = (oldScale * zoom).coerceIn(minScale, maxScale)
                                val imgX = (centroid.x - offsetX) / oldScale
                                val imgY = (centroid.y - offsetY) / oldScale
                                val newOffsetX = centroid.x - imgX * newScale
                                val newOffsetY = centroid.y - imgY * newScale
                                offsetX = newOffsetX + pan.x
                                offsetY = newOffsetY + pan.y
                                scale = newScale
                                clampOffsets(bmp.width.toFloat(), bmp.height.toFloat())
                            }
                        }
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
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

                        if (srcW > 0f && srcH > 0f && dstW > 0f && dstH > 0f) {
                            drawImage(
                                image = bmp,
                                srcOffset = IntOffset(srcLeft.toInt(), srcTop.toInt()),
                                srcSize = IntSize(srcW.toInt(), srcH.toInt()),
                                dstOffset = IntOffset(dstLeft.toInt(), dstTop.toInt()),
                                dstSize = IntSize(dstW.toInt(), dstH.toInt())
                            )

                            val g = grid
                            val ov = overlay
                            if (showGridOverlay && g != null && ov != null) {
                                val ovScaleX = imageBitmap!!.width / g.w.toFloat()
                                val ovScaleY = imageBitmap!!.height / g.h.toFloat()
                                val oSrcLeft = (srcLeft / ovScaleX).coerceIn(0f, g.w.toFloat())
                                val oSrcTop = (srcTop / ovScaleY).coerceIn(0f, g.h.toFloat())
                                val oSrcRight = (srcRight / ovScaleX).coerceIn(0f, g.w.toFloat())
                                val oSrcBottom = (srcBottom / ovScaleY).coerceIn(0f, g.h.toFloat())
                                val oSrcW = (oSrcRight - oSrcLeft).coerceAtLeast(0f)
                                val oSrcH = (oSrcBottom - oSrcTop).coerceAtLeast(0f)

                                if (oSrcW > 0f && oSrcH > 0f) {
                                    drawImage(
                                        image = ov,
                                        srcOffset = IntOffset(oSrcLeft.toInt(), oSrcTop.toInt()),
                                        srcSize = IntSize(oSrcW.toInt(), oSrcH.toInt()),
                                        dstOffset = IntOffset(dstLeft.toInt(), dstTop.toInt()),
                                        dstSize = IntSize(dstW.toInt(), dstH.toInt())
                                    )
                                }
                            }
                        }

                        val classroomRadius = 10f
                        val startGoalRadius = 14f
                        val pathWidth = 8f

                        start?.let {
                            drawCircle(color = colorMaterial.primary, radius = startGoalRadius, center = imgToScreen(it))
                        }
                        goal?.let {
                            drawCircle(color = colorMaterial.tertiary, radius = startGoalRadius, center = imgToScreen(it))
                        }

                        if (path.isNotEmpty()) {
                            // 1. 畫線 (維持原顏色)
                            val screenPoints = path.map { imgToScreen(it) }
                            val p = Path().apply {
                                moveTo(screenPoints.first().x, screenPoints.first().y)
                                for (i in 1 until screenPoints.size) {
                                    lineTo(screenPoints[i].x, screenPoints[i].y)
                                }
                            }
                            drawPath(path = p, color = colorMaterial.secondary, style = Stroke(width = pathWidth))

                            // 2. (✨修正) 畫方向箭頭：改成白色 + 加大間距
                            val arrowSpacing = 100f // (✨) 增加間距到 60f
                            val arrowSize = 30f
                            var distanceAccumulator = 0f

                            for (i in 0 until screenPoints.size - 1) {
                                val p1 = screenPoints[i]
                                val p2 = screenPoints[i+1]
                                val dx = p2.x - p1.x
                                val dy = p2.y - p1.y
                                val segmentDist = hypot(dx, dy)

                                if (segmentDist == 0f) continue

                                val angle = atan2(dy, dx)
                                var currentPos = arrowSpacing - distanceAccumulator

                                while (currentPos <= segmentDist) {
                                    val t = currentPos / segmentDist
                                    val ax = p1.x + t * dx
                                    val ay = p1.y + t * dy

                                    // 畫箭頭
                                    val wingAngle = 0.5f
                                    val wingLen = arrowSize

                                    val w1x = ax - wingLen * cos(angle - wingAngle)
                                    val w1y = ay - wingLen * sin(angle - wingAngle)
                                    val w2x = ax - wingLen * cos(angle + wingAngle)
                                    val w2y = ay - wingLen * sin(angle + wingAngle)

                                    val arrowPath = Path().apply {
                                        moveTo(ax, ay)
                                        lineTo(w1x, w1y)
                                        lineTo(w2x, w2y)
                                        close()
                                    }

                                    // (✨) 改成白色，確保在深色路徑上清晰可見
                                    drawPath(path = arrowPath, color = ComposeColor.Red)

                                    currentPos += arrowSpacing
                                }
                                distanceAccumulator = (distanceAccumulator + segmentDist) % arrowSpacing
                            }
                        }

                        if (showClassrooms && classroomPoints.isNotEmpty()) {
                            classroomPoints.forEach { rp ->
                                val imgX = (rp.x.toFloat() / 100f) * bmp.width
                                val imgY = (rp.y.toFloat() / 100f) * bmp.height
                                val scr = imgToScreen(Offset(imgX, imgY))
                                val isEntrance = try { rp.type.equals("ENTRANCE", true) || rp.name.contains("入口") } catch (_: Exception) { false }
                                drawCircle(color = if (isEntrance) ComposeColor.Blue else colorMaterial.primary.copy(alpha = 0.9f), radius = classroomRadius, center = scr)
                            }
                        }
                    }
                }
            } ?: Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("尚未載入平面圖資源") }

            FloatingActionButton(
                onClick = { navController?.popBackStack() },
                modifier = Modifier.align(Alignment.BottomStart).padding(12.dp).zIndex(3f),
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "返回地圖", tint = MaterialTheme.colorScheme.onPrimary)
            }

            resolvedEntranceInfo?.let { info ->
                Box(modifier = Modifier.align(Alignment.TopEnd).padding(12.dp).zIndex(4f)) {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))) {
                        Text(text = "入口: $info", modifier = Modifier.padding(8.dp), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            if (previewEntryPhase && previewEntryEntity != null && previewTargetEntity != null) {
                Box(modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp).zIndex(3f)) {
                    Button(onClick = {
                        scope.launch {
                            val target = previewTargetEntity ?: return@launch
                            currentImageRes = target.imageId
                            withContext(Dispatchers.Default) {
                                var attempts = 0
                                while (loadedImageRes != target.imageId && attempts < 100) { attempts++; delay(60) }
                            }
                            val bmp = if (loadedImageRes == target.imageId) imageBitmap?.asAndroidBitmap() else null
                            if (bmp == null) return@launch

                            val all = refDao.getAllReferencePoints().first()
                            val vertical = all.filter { it.imageId == target.imageId && (it.type.equals("STAIRS", true) || it.type.equals("ELEVATOR", true)) }
                            val targetVertical = vertical.firstOrNull()

                            val startPt = targetVertical?.let { Offset((it.x.toFloat() / 100f) * bmp.width, (it.y.toFloat() / 100f) * bmp.height) }
                            val goalPt = Offset((target.x.toFloat() / 100f) * bmp.width, (target.y.toFloat() / 100f) * bmp.height)

                            start = startPt
                            goal = goalPt
                            previewEntryPhase = false
                            showClassrooms = true
                            recomputePathAsync()
                        }
                    }) {
                        Text(text = "已到達 ${previewTargetFloorName ?: "目標樓層"}")
                    }
                }
            }

            if (debugInfo.isNotBlank()) {
                Box(modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp)) {
                    Card(colors = CardDefaults.cardColors(containerColor = colorResource(id = R.color.yellow).copy(alpha = 0.9f))) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            // (✨) 顯示路徑點數，確認路徑是否有生成
                            Text(text = "path: ${path.size} points", color = colorResource(id = R.color.black), fontWeight = FontWeight.Bold)
                            Text(text = debugInfo, color = colorResource(id = R.color.black))
                            startGridCell?.let { (x, y) -> Text(text = "start: $x,$y walk=$startWalkable", color = colorResource(id = R.color.black)) }
                            goalGridCell?.let { (x, y) -> Text(text = "goal: $x,$y walk=$goalWalkable", color = colorResource(id = R.color.black)) }
                        }
                    }
                }
            }
        }
    }
}