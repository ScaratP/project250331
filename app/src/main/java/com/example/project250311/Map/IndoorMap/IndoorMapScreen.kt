@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.project250311.Map.IndoorMap

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.TransformableState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.transformable
import androidx.compose.ui.platform.LocalDensity
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
        sample: Int = 1,
        satMax: Float = 4.5f, // 放寬以容錯
        valMin: Float = 0.1f,
        wallInflate: Int = 0
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
            val blocked = android.graphics.Color.argb(20, 0, 0, 0) // 淡灰（不可走）
            val walkable = android.graphics.Color.argb(110, 0, 180, 255) // 半透明藍綠（可走）
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
    // which image resource has been loaded into imageBitmap (set when imageBitmap is assigned)
    var loadedImageRes by remember { mutableStateOf<Int?>(null) }

    // 視窗互動狀態（宣告必須在 LaunchedEffect 之前，因為 LaunchedEffect 會使用到 minScale）
    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }
    // 每張圖的動態最小縮放（以螢幕寬度為最小）
    var minScale by remember { mutableStateOf(1f) }
    val maxScale = 6f

    val metrics = Resources.getSystem().displayMetrics
    val screenWidthPx = metrics.widthPixels.toFloat()
    val screenHeightPx = metrics.heightPixels.toFloat()

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

    // Debug / telemetry
    var debugInfo by remember { mutableStateOf("") }
    var startGridCell by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var goalGridCell by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var startWalkable by remember { mutableStateOf<Boolean?>(null) }
    var goalWalkable by remember { mutableStateOf<Boolean?>(null) }

    // 新增：Overlay 與偵錯統計（原本有使用到，但未宣告）
    var overlay by remember { mutableStateOf<ImageBitmap?>(null) }
    var walkableCount by remember { mutableStateOf(0) }
    // 各樓層診斷報告（供比對 se1 與其他樓層）
    var floorStatsReport by remember { mutableStateOf("") }

    // 顯示教室點
    var showClassrooms by remember { mutableStateOf(false) }
    var classroomPoints by remember { mutableStateOf<List<ReferencePointEntity>>(emptyList()) }

    // Debug: currently resolved entry info (id/name/building/floor) for on-screen verification
    var resolvedEntranceInfo by remember { mutableStateOf<String?>(null) }

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

    // Helper: resolve preferred ENTRANCE reference point
    fun resolvePreferredEntrance(
        all: List<ReferencePointEntity>,
        targetEntity: ReferencePointEntity,
        entryPointId: String?
    ): ReferencePointEntity? {
        // 1) If explicit entryPointId provided, prefer that
        if (!entryPointId.isNullOrBlank()) {
            val byId = all.firstOrNull { it.id == entryPointId }
            if (byId != null) {
                val imageNameSafe = if (byId.imageId != 0) {
                    try { context.resources.getResourceEntryName(byId.imageId) } catch (_: Exception) { "?" }
                } else "?"
                Log.d("IndoorMap.UI", "resolvePreferredEntrance: chosen by id=${byId.id} name=${byId.name} image=$imageNameSafe floorId=${byId.floorId}")
                return byId
            }
        }

        // 2) Prefer an ENTRANCE in the same building as the target.
        //    If the DB buildingId looks wrong, derive candidates from the target id/name (e.g. "sec102" -> "SEC").
        val candidateBuildings = mutableListOf<String>()
        try {
            if (!targetEntity.buildingId.isNullOrBlank()) candidateBuildings.add(targetEntity.buildingId.uppercase())
            // derive from id/name prefix
            val prefixSource = (targetEntity.id.ifBlank { targetEntity.name }).lowercase()
            val prefix = prefixSource.takeWhile { it.isLetter() }
            when (prefix) {
                "sea", "se" -> if (!candidateBuildings.contains("SE")) candidateBuildings.add("SE")
                "seb" -> if (!candidateBuildings.contains("SEB")) candidateBuildings.add("SEB")
                "sec" -> if (!candidateBuildings.contains("SEC")) candidateBuildings.add("SEC")
            }
        } catch (_: Exception) { }

        // search entrances by candidateBuildings in order
        for (b in candidateBuildings) {
            val found = all.firstOrNull { it.type.equals("ENTRANCE", true) && it.buildingId.equals(b, true) }
            if (found != null) {
                Log.d("IndoorMap.UI", "resolvePreferredEntrance: chosen by building-candidate=$b id=${found.id}")
                return found
            }
        }

        // 3) fallback chain: prefer SEB -> SE -> any ENTRANCE
        val seb = all.firstOrNull { it.type.equals("ENTRANCE", true) && it.buildingId.equals("SEB", true) }
        if (seb != null) {
            Log.d("IndoorMap.UI", "resolvePreferredEntrance: fallback to SEB entrance=${seb.id}")
            return seb
        }
        val se = all.firstOrNull { it.type.equals("ENTRANCE", true) && it.buildingId.equals("SE", true) }
        if (se != null) {
            Log.d("IndoorMap.UI", "resolvePreferredEntrance: fallback to SE entrance=${se.id}")
            return se
        }

        val any = all.firstOrNull { it.type.equals("ENTRANCE", true) }
        if (any != null) Log.d("IndoorMap.UI", "resolvePreferredEntrance: fallback to any entrance=${any.id}")
        return any
    }

    // Preview state: when entryPointId is on a different floor than target, we first show entry floor preview
    var previewEntryPhase by remember { mutableStateOf(false) }
    var previewEntryEntity by remember { mutableStateOf<ReferencePointEntity?>(null) }
    var previewTargetEntity by remember { mutableStateOf<ReferencePointEntity?>(null) }
    var previewTargetFloorName by remember { mutableStateOf<String?>(null) }

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
        val (sx, sy) = imageToGrid(sPt, g, gridSample)
        val (gx, gy) = imageToGrid(ePt, g, gridSample)
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

        scope.launch(Dispatchers.Default) {
            // Log initial mapping
            Log.d("IndoorMap.UI", "recomputePathAsync: image->grid start=($sx,$sy) goal=($gx,$gy) grid=${g.w}x${g.h} sample=$gridSample")

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
                    Log.d("IndoorMap.UI", "start not walkable at ($sx,$sy) -> nearest walkable=($sx2,$sy2)")
                    // update start Offset on main so UI shows corrected point
                    withContext(Dispatchers.Main) {
                        start = Offset((sx2 + 0.5f) * gridSample, (sy2 + 0.5f) * gridSample)
                    }
                } else {
                    Log.d("IndoorMap.UI", "start not walkable and no nearby walkable cell found (maxRadius)")
                }
            }

            if (!goalWalk) {
                val found = findNearestWalkable(g, gx, gy, maxRadius = 40)
                if (found != null) {
                    gx2 = found.first
                    gy2 = found.second
                    Log.d("IndoorMap.UI", "goal not walkable at ($gx,$gy) -> nearest walkable=($gx2,$gy2)")
                    withContext(Dispatchers.Main) {
                        goal = Offset((gx2 + 0.5f) * gridSample, (gy2 + 0.5f) * gridSample)
                    }
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
            val px = vis.map { node -> Offset((node.x + 0.5f) * gridSample, (node.y + 0.5f) * gridSample) }
            val simplified = rdp(px, eps = (gridSample * 0.75f))
            withContext(Dispatchers.Main) { path = simplified }
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
                try {
                    val tResName = if (targetEntity.imageId != 0) {
                        try { context.resources.getResourceEntryName(targetEntity.imageId) } catch (_: Exception) { "?" }
                    } else "?"
                    Log.d("IndoorMap.UI", "LaunchedEffect target=${targetEntity.id} floorId=${targetEntity.floorId} image=$tResName")
                } catch (_: Exception) {}
                // 不要一開始就自動跳到目標樓層圖片（會導致先顯示目標再跳回入口），
                // 改為在下面分支中依情況載入對應圖片：若需要 preview 則載入 entry 的圖片，否則載入 target 的圖片。
                // 接著在各自分支等待 imageBitmap 再做後續計算。

                // 找入口：使用共用 helper（會依 entryPointId、同棟 ENTRANCE、SEB、SE、任何 ENTRANCE 依序嘗試）
                val entryEntity = resolvePreferredEntrance(all, targetEntity, entryPointId)
                resolvedEntranceInfo = entryEntity?.let { try { "${it.id}/${it.name}/${it.buildingId}/${it.floorId}" } catch (_: Exception) { it.id } }

                // 如果 entryEntity 存在且和目標不在同一樓層，預設進入 entry-preview；
                // 但若目標在 1F（floorId==1），就直接以 1F 圖導引，略過 preview。
                if (entryEntity != null && entryEntity.floorId != targetEntity.floorId && targetEntity.floorId != 1) {
                    // set preview vars
                    previewEntryPhase = true
                    previewEntryEntity = entryEntity
                    previewTargetEntity = targetEntity
                    try {
                        val floorEntity = withContext(Dispatchers.IO) { db.floorDao().getFloorById(entryEntity.floorId) }
                        previewTargetFloorName = "${targetEntity.buildingId}${targetEntity.floorId}樓"
                    } catch (_: Exception) {
                        previewTargetFloorName = "${targetEntity.floorId}樓"
                    }

                    // set current image to entry's floor image and wait for bitmap
                    currentImageRes = entryEntity.imageId
                    withContext(Dispatchers.Default) {
                        var attempts2 = 0
                        val wantRes = entryEntity.imageId
                        while (loadedImageRes != wantRes && attempts2 < 100) {
                            attempts2++
                            delay(60)
                        }
                    }

                    val bmp2 = imageBitmap?.asAndroidBitmap()
                    if (bmp2 != null) {
                        // find entrance on that floor (ENTRANCE) and the target vertical candidate (STAIRS/ELEVATOR)
                        val floorPoints = all.filter { it.imageId == entryEntity.imageId }
                        val entranceOnFloor = floorPoints.firstOrNull { it.type.equals("ENTRANCE", true) }
                        val vertical = floorPoints.filter { it.type.equals("STAIRS", true) || it.type.equals("ELEVATOR", true) }

                        // Simplified deterministic matching: use building prefix to prefer elevator/stairs and match by name fragment
                        val prefix = targetEntity.id.takeWhile { it.isLetter() }.lowercase()
                        val preferredType = when (prefix) {
                            "sea", "seb" -> "ELEVATOR"
                            "sec" -> "STAIRS"
                            else -> null
                        }
                        val entryFloorNumStr = if (entryEntity.imageId != 0) {
                            try { context.resources.getResourceEntryName(entryEntity.imageId).takeLastWhile { it.isDigit() } } catch (_: Exception) { null }
                        } else null
                        val wantFrag = if (!prefix.isBlank() && !entryFloorNumStr.isNullOrBlank()) (prefix + entryFloorNumStr).lowercase() else null

                        var targetVertical: ReferencePointEntity? = null
                        if (wantFrag != null) targetVertical = vertical.firstOrNull { it.buildingId.equals(targetEntity.buildingId, true) && it.name.lowercase().contains(wantFrag) }
                        if (targetVertical == null && preferredType != null) targetVertical = vertical.firstOrNull { it.buildingId.equals(targetEntity.buildingId, true) && it.type.equals(preferredType, true) }
                        if (targetVertical == null && prefix.isNotBlank()) targetVertical = vertical.firstOrNull { it.buildingId.equals(targetEntity.buildingId, true) && it.name.lowercase().contains(prefix) }
                        if (targetVertical == null) targetVertical = vertical.firstOrNull()

                        val sPoint = entranceOnFloor?.let { Offset((it.x.toFloat() / 100f) * bmp2.width, (it.y.toFloat() / 100f) * bmp2.height) }
                        val gPoint = targetVertical?.let { Offset((it.x.toFloat() / 100f) * bmp2.width, (it.y.toFloat() / 100f) * bmp2.height) }

                        start = sPoint
                        goal = gPoint
                        // ensure the UI shows entrance and vertical points
                        showClassrooms = true
                        if (autoStart) recomputePathAsync()
                    }
                    return@LaunchedEffect
                }

                // 若不需 entry-preview，則載入並等待目標樓層圖片後進行正常導航行為
                currentImageRes = targetEntity.imageId
                withContext(Dispatchers.Default) {
                        var attempts = 0
                        val wantRes = targetEntity.imageId
                    while (loadedImageRes != wantRes && attempts < 100) {
                        attempts++
                        delay(60)
                    }
                }

                val bmp = imageBitmap?.asAndroidBitmap()
                if (bmp != null) {
                    // 找入口：使用共用 helper（會依 entryPointId、同棟 ENTRANCE、SEB、SE、任何 ENTRANCE 依序嘗試）
                    val entryEntity = resolvePreferredEntrance(all, targetEntity, entryPointId)
                    resolvedEntranceInfo = entryEntity?.let { try { "${it.id}/${it.name}/${it.buildingId}/${it.floorId}" } catch (_: Exception) { it.id } }
                    try {
                        if (entryEntity != null) {
                            val eRes = if (entryEntity.imageId != 0) {
                                try { context.resources.getResourceEntryName(entryEntity.imageId) } catch (_: Exception) { "?" }
                            } else "?"
                            Log.d("IndoorMap.UI", "Resolved entryEntity=${entryEntity.id} floorId=${entryEntity.floorId} image=$eRes name=${entryEntity.name}")
                        } else {
                            Log.d("IndoorMap.UI", "No entryEntity resolved for entryPointId=$entryPointId; falling back to ENTRANCE lookup")
                        }
                    } catch (_: Exception) {}

                    // 若 entry 與目標不同樓層，預設進入 preview；但 1F 目標時略過 preview，直接在目標樓層導航。
                    if (entryEntity != null && entryEntity.floorId != targetEntity.floorId && targetEntity.floorId != 1) {
                        // set preview vars
                        previewEntryPhase = true
                        previewEntryEntity = entryEntity
                        previewTargetEntity = targetEntity
                        // try to resolve a human-friendly floor name for button label
                        try {
                            val floorEntity = withContext(Dispatchers.IO) { db.floorDao().getFloorById(entryEntity.floorId) }
                            previewTargetFloorName = "${targetEntity.buildingId}${targetEntity.floorId}樓"
                        } catch (_: Exception) {
                            previewTargetFloorName = "${targetEntity.floorId}樓"
                        }

                        // set current image to entry's floor image
                        currentImageRes = entryEntity.imageId

                        // wait for image bitmap for entry floor
                        withContext(Dispatchers.Default) {
                            var attempts2 = 0
                            val wantRes2 = entryEntity.imageId
                            while (loadedImageRes != wantRes2 && attempts2 < 100) {
                                attempts2++
                                delay(60)
                            }
                        }

                        val bmp2 = imageBitmap?.asAndroidBitmap()
                        if (bmp2 != null) {
                            // find entrance on that floor (ENTRANCE) and the target vertical candidate (STAIRS/ELEVATOR)
                            val floorPoints = all.filter { it.imageId == entryEntity.imageId }
                            val entranceOnFloor = floorPoints.firstOrNull { it.type.equals("ENTRANCE", true) }
                            val vertical = floorPoints.filter { it.type.equals("STAIRS", true) || it.type.equals("ELEVATOR", true) }
                            val targetVertical = vertical.firstOrNull { it.id == entryEntity.id } ?: vertical.firstOrNull()

                            val sPoint = entranceOnFloor?.let { Offset((it.x.toFloat() / 100f) * bmp2.width, (it.y.toFloat() / 100f) * bmp2.height) }
                            val gPoint = targetVertical?.let { Offset((it.x.toFloat() / 100f) * bmp2.width, (it.y.toFloat() / 100f) * bmp2.height) }

                            start = sPoint
                            goal = gPoint
                            // ensure the UI shows entrance and vertical points
                            showClassrooms = true
                            if (autoStart) recomputePathAsync()
                        }
                        return@LaunchedEffect
                    }

                    // 若無 entry preview required，正常行為：用 entryEntity 或 floor ENTRANCE 作為 start，目標為目標教室
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

    // ===== 快速診斷：掃描所有 floorPlans，計算以目前閾值判定下的可走格百分比（離線/背景執行） =====
    // 減少冗長診斷日誌以避免噪音
    val ENABLE_DIAG_LOGS = false
    LaunchedEffect(gridSample) {
        if (!ENABLE_DIAG_LOGS) return@LaunchedEffect
        scope.launch(Dispatchers.Default) {
            val sb = StringBuilder()
            try {
                for ((name, resId) in floorPlans) {
                    val bmp = BitmapFactory.decodeResource(context.resources, resId) ?: continue
                    val g = bitmapToGridFromWhiteCorridor(
                        bitmap = bmp,
                        sample = gridSample,
                        satMax = 0.12f,
                        valMin = 0.92f,
                        wallInflate = 3
                    )
                    val walkable = g.cells.count { it }
                    val total = g.w * g.h
                    val pct = if (total == 0) 0 else (walkable * 100 / total)
                    sb.append("$name: ${walkable}/$total ($pct%)\n")
                    Log.d("IndoorMap.Diag", "$name -> walkable=$walkable total=$total pct=$pct% grid=${g.w}x${g.h}")
                }
            } catch (e: Exception) {
                sb.append("diagnostic error: ${e.message}")
            }
            withContext(Dispatchers.Main) { floorStatsReport = sb.toString() }
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
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).padding(padding)
            .onSizeChanged { containerWidthPx = it.width.toFloat(); containerHeightPx = it.height.toFloat() }) {
            imageBitmap?.let { bmp ->
                Box(
                        Modifier.fillMaxSize()
                                .clipToBounds()
                                // 雙擊處理器
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

                                // 判斷是否為出入口：型別為 ENTRANCE 或 名稱包含「入口」
                                val isEntrance = try {
                                    rp.type.equals("ENTRANCE", true) || rp.name.contains("入口")
                                } catch (_: Exception) { false }

                                val pointColor = if (isEntrance) ComposeColor.Blue else colorMaterial.primary.copy(alpha = 0.9f)

                                drawCircle(
                                        color = pointColor,
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

            // 顯示目前解析到的入口資訊（除錯用）
            resolvedEntranceInfo?.let { info ->
                Box(modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
                    .zIndex(4f)) {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))) {
                        Text(text = "入口: $info", modifier = Modifier.padding(8.dp), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

                // 若處於 entry preview 階段，顯示底部按鈕讓使用者表示「已到達指定樓層」，按下後切換到目標樓層並計算從該樓層垂直點到教室的路徑
                if (previewEntryPhase && previewEntryEntity != null && previewTargetEntity != null) {
                    Box(modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(12.dp)
                        .zIndex(3f)) {
                        Button(onClick = {
                            scope.launch {
                                // 切換到目標樓層圖片
                                val target = previewTargetEntity ?: return@launch
                                val entry = previewEntryEntity ?: return@launch
                                // set image to target floor, wait until the correct resource is loaded
                                Log.d("IndoorMap.UI", "Arrival button pressed: switching to target=${target.id} imageId=${target.imageId}")
                                currentImageRes = target.imageId
                                withContext(Dispatchers.Default) {
                                    var attempts = 0
                                    val wantRes = target.imageId
                                    while (loadedImageRes != wantRes && attempts < 100) {
                                        attempts++
                                        delay(60)
                                    }
                                }

                                val bmp = if (loadedImageRes == target.imageId) imageBitmap?.asAndroidBitmap() else null
                                if (bmp == null) {
                                    Log.d("IndoorMap.UI", "Target image not ready: want=${target.imageId} loaded=${loadedImageRes}")
                                    return@launch
                                }

                                // 查找目標樓層的垂直點候選
                                val all = withContext(Dispatchers.IO) { refDao.getAllReferencePoints().first() }
                                val destCandidates = all.filter { it.imageId == target.imageId && (it.type.equals("STAIRS", true) || it.type.equals("ELEVATOR", true)) }

                                // Simplified deterministic mapping: prefer elevator/stairs by building prefix
                                var matched: ReferencePointEntity? = null
                                try {
                                    val resName = try { context.resources.getResourceEntryName(target.imageId) } catch (_: Exception) { null }
                                    val floorNumStr = resName?.takeLastWhile { it.isDigit() }
                                    val prefix = entry.name.takeWhile { it.isLetter() }.lowercase()
                                    val preferredType = when (prefix) {
                                        "sea", "seb" -> "ELEVATOR"
                                        "sec" -> "STAIRS"
                                        else -> null
                                    }

                                    val wantFragment = if (!prefix.isBlank() && !floorNumStr.isNullOrBlank()) (prefix + floorNumStr).lowercase() else null
                                    val stairsAll = all.filter { it.type.equals("STAIRS", true) || it.type.equals("ELEVATOR", true) }
                                    val destCandidatesFiltered = stairsAll.filter { it.imageId == target.imageId }

                                    if (wantFragment != null) matched = destCandidatesFiltered.firstOrNull { it.buildingId.equals(target.buildingId, true) && it.name.lowercase().contains(wantFragment) }
                                    if (matched == null && preferredType != null) matched = destCandidatesFiltered.firstOrNull { it.buildingId.equals(target.buildingId, true) && it.type.equals(preferredType, true) }
                                    if (matched == null && prefix.isNotBlank()) matched = destCandidatesFiltered.firstOrNull { it.buildingId.equals(target.buildingId, true) && it.name.lowercase().contains(prefix) }
                                    if (matched == null) matched = destCandidatesFiltered.firstOrNull()
                                } catch (e: Exception) {
                                    Log.d("IndoorMap.UI", "matching stairs failed: ${e.message}")
                                }

                                Log.d("IndoorMap.UI", "matched vertical=${matched?.id ?: "<null>"} name=${matched?.name ?: "-"} type=${matched?.type ?: "-"} destCandidates=${destCandidates.size}")

                                // 如果 matched 不為 null，依新圖片大小計算 start 與 goal
                                val startPt = matched?.let { Offset((it.x.toFloat() / 100f) * bmp.width, (it.y.toFloat() / 100f) * bmp.height) }
                                val goalPt = Offset((target.x.toFloat() / 100f) * bmp.width, (target.y.toFloat() / 100f) * bmp.height)

                                start = startPt
                                goal = goalPt
                                previewEntryPhase = false
                                // 顯示教室點並開始計算
                                showClassrooms = true
                                recomputePathAsync()
                            }
                        }) {
                            Text(text = "已到達 ${previewTargetFloorName ?: "目標樓層"}")
                        }
                    }
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
