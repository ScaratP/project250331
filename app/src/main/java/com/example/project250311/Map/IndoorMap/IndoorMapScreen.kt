@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.project250311.Map.IndoorMap

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.TransformableState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
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
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.*
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import com.example.project250311.R

// ======================= 資料結構 =======================
data class Node(
    val x: Int,
    val y: Int,
    var g: Double = Double.POSITIVE_INFINITY,
    var h: Double = 0.0,
    var parent: Node? = null,
    var walkable: Boolean = true
) { val f: Double get() = g + h }

data class Grid(val w: Int, val h: Int, val cells: BooleanArray) {
    fun walkable(x: Int, y: Int) = x in 0 until w && y in 0 until h && cells[y * w + x]
}

// 八方向啟發式
fun heuristic(ax: Int, ay: Int, bx: Int, by: Int): Double {
    val dx = abs(ax - bx); val dy = abs(ay - by)
    return (max(dx, dy) - min(dx, dy)) + 1.41421356 * min(dx, dy)
}

// 防止斜角鑽牆角
fun neighbors(grid: Grid, n: Node): List<Node> {
    val res = ArrayList<Node>(8)
    for (dy in -1..1) for (dx in -1..1) {
        if (dx == 0 && dy == 0) continue
        val nx = n.x + dx; val ny = n.y + dy
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
    open.add(start); gScore[key(sx, sy)] = 0.0

    while (open.isNotEmpty()) {
        val cur = open.poll()
        if (cur.x == gx && cur.y == gy) {
            val out = mutableListOf<Node>()
            var p: Node? = cur
            while (p != null) { out += p; p = p.parent }
            return out.asReversed()
        }
        for (nb in neighbors(grid, cur)) {
            val step = if (nb.x != cur.x && nb.y != cur.y) 1.41421356 else 1.0
            val tentative = gScore.getOrDefault(key(cur.x, cur.y), Double.POSITIVE_INFINITY) + step
            val nbKey = key(nb.x, nb.y)
            if (tentative < gScore.getOrDefault(nbKey, Double.POSITIVE_INFINITY)) {
                val nnode = Node(nb.x, nb.y, g = tentative, h = heuristic(nb.x, nb.y, gx, gy), parent = cur)
                gScore[nbKey] = tentative
                open.add(nnode)
            }
        }
    }
    return emptyList()
}

// ======================= 路徑平滑 =======================
fun lineOfSight(grid: Grid, ax: Int, ay: Int, bx: Int, by: Int): Boolean {
    var x0 = ax; var y0 = ay; val x1 = bx; val y1 = by
    val dx = abs(x1 - x0); val dy = -abs(y1 - y0)
    val sx = if (x0 < x1) 1 else -1; val sy = if (y0 < y1) 1 else -1
    var err = dx + dy
    while (true) {
        if (!grid.walkable(x0, y0)) return false
        if (x0 == x1 && y0 == y1) break
        val e2 = 2 * err
        if (e2 >= dy) { err += dy; x0 += sx }
        if (e2 <= dx) { err += dx; y0 += sy }
    }
    return true
}

fun smoothByVisibility(raw: List<Node>, grid: Grid): List<Node> {
    if (raw.size <= 2) return raw
    val out = mutableListOf<Node>(); var anchor = 0
    out += raw.first()
    var i = 2
    while (i < raw.size) {
        val a = raw[anchor]; val b = raw[i]
        if (!lineOfSight(grid, a.x, a.y, b.x, b.y)) { out += raw[i - 1]; anchor = i - 1 }
        i++
    }
    out += raw.last()
    return out
}

fun rdp(points: List<Offset>, eps: Float): List<Offset> {
    if (points.size < 3) return points
    var index = 0; var dmax = 0f
    val a = points.first(); val b = points.last()
    for (i in 1 until points.size - 1) {
        val d = perpDist(points[i], a, b)
        if (d > dmax) { index = i; dmax = d }
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
    satMax: Float = 0.12f,   // 放寬以容錯
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
        val sx = gx * sample; val sy = gy * sample
        cells[gy * w + gx] = isNearWhite(sx, sy)
    }

    if (wallInflate > 0) {
        val out = cells.copyOf()
        fun block(x: Int, y: Int) { if (x in 0 until w && y in 0 until h) out[y * w + x] = false }
        for (y in 0 until h) for (x in 0 until w) if (!cells[y * w + x]) {
            for (dy in -wallInflate..wallInflate) for (dx in -wallInflate..wallInflate) block(x + dx, y + dy)
        }
        return Grid(w, h, out)
    }
    return Grid(w, h, cells)
}

// ======================= 將 Grid 轉成覆蓋圖 =======================
suspend fun buildGridOverlayBitmap(g: Grid): ImageBitmap = withContext(Dispatchers.Default) {
    val bmp = Bitmap.createBitmap(g.w, g.h, Bitmap.Config.ARGB_8888)
    val blocked = android.graphics.Color.argb(20, 0, 0, 0)       // 淡灰（不可走）
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
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IndoorMapScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val colorMaterial = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()

    val floorPlans = listOf(
        "一樓平面圖" to R.drawable.se1,
        "二樓平面圖" to R.drawable.se2,
        "三樓平面圖" to R.drawable.se3,
    )

    var expanded by remember { mutableStateOf(false) }
    var selectedFloorName by remember { mutableStateOf(floorPlans.first().first) }
    var currentImageRes by remember { mutableStateOf(floorPlans.first().second) }
    var imageBitmap by remember { mutableStateOf<ImageBitmap?>(null) }

    // ===== (2) 載入時先縮圖（依裝置寬高上限） =====
    LaunchedEffect(currentImageRes) {
        val d = context.getDrawable(currentImageRes) ?: return@LaunchedEffect
        val raw = d.toBitmap()

        val metrics = Resources.getSystem().displayMetrics
        val maxW = (metrics.widthPixels * 2f).toInt()
        val maxH = (metrics.heightPixels * 2f).toInt()

        val scale = min(maxW / raw.width.toFloat(), maxH / raw.height.toFloat())
        val finalBmp = if (scale < 1f) {
            Bitmap.createScaledBitmap(
                raw,
                (raw.width * scale).toInt().coerceAtLeast(1),
                (raw.height * scale).toInt().coerceAtLeast(1),
                true
            )
        } else raw

        imageBitmap = finalBmp.asImageBitmap()
    }

    // 視窗互動狀態
    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }
    val transformState = remember {
        TransformableState { zoom, pan, _ ->
            scale = (scale * zoom).coerceIn(0.5f, 6f)
            offsetX += pan.x; offsetY += pan.y
        }
    }

    // 導航狀態
    var grid by remember { mutableStateOf<Grid?>(null) }
    var gridSample by remember { mutableStateOf(2) } // 2 或 3
    var start by remember { mutableStateOf<Offset?>(null) }
    var goal by remember { mutableStateOf<Offset?>(null) }
    var path by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var showGridOverlay by remember { mutableStateOf(false) }

    // 覆蓋圖 & 偵錯
    var overlay by remember { mutableStateOf<ImageBitmap?>(null) }
    var walkableCount by remember { mutableStateOf(0) }

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

    // ===== (3) 路徑計算搬到背景執行緒 =====
    fun recomputePathAsync() {
        val g = grid ?: return
        val sPt = start ?: return
        val ePt = goal ?: return
        val (sx, sy) = imageToGrid(sPt, g, gridSample)
        val (gx, gy) = imageToGrid(ePt, g, gridSample)

        scope.launch(Dispatchers.Default) {
            val raw = aStar(g, sx, sy, gx, gy)
            if (raw.isEmpty()) {
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("平面圖導航 Demo", fontWeight = FontWeight.SemiBold) },
                actions = {
                    // 樓層下拉
                    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                        TextButton(onClick = { expanded = true }, modifier = Modifier.menuAnchor()) {
                            Text(selectedFloorName)
                        }
                        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            floorPlans.forEach { (name, resId) ->
                                DropdownMenuItem(
                                    text = { Text(name) },
                                    onClick = {
                                        selectedFloorName = name
                                        currentImageRes = resId
                                        expanded = false
                                        // 清狀態
                                        start = null; goal = null; path = emptyList()
                                        grid = null; overlay = null; walkableCount = 0
                                    }
                                )
                            }
                        }
                    }

                    // 建網格（並生成覆蓋圖）
                    TextButton(onClick = {
                        val bmp = imageBitmap?.asAndroidBitmap() ?: return@TextButton
                        val g = bitmapToGridFromWhiteCorridor(
                            bitmap = bmp,
                            sample = gridSample,
                            satMax = 0.12f,
                            valMin = 0.92f,
                            wallInflate = 3
                        )
                        grid = g
                        start = null; goal = null; path = emptyList()
                        walkableCount = g.cells.count { it }
                        overlay = null
                        scope.launch { overlay = buildGridOverlayBitmap(g) } // 背景生成，不卡 UI
                    }) { Text("建網格") }

                    // 顯示/隱藏網格覆蓋
                    TextButton(onClick = { showGridOverlay = !showGridOverlay }) {
                        Text(if (showGridOverlay) "隱藏網格" else "顯示網格")
                    }

                    // 偵錯：可走格數
                    Text("walkable=$walkableCount", style = MaterialTheme.typography.labelSmall)

                    // 清除
                    TextButton(onClick = { start = null; goal = null; path = emptyList() }) { Text("清除") }
                }
            )
        }
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .padding(padding)
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
                                    val ip = screenToImage(p) ?: return@detectTapGestures
                                    if (start == null) start = ip
                                    else if (goal == null) { goal = ip; recomputePathAsync() }
                                    else { start = ip; goal = null; path = emptyList() }
                                }
                            )
                        }
                ) {
                    // ===== (1) 單一 Canvas，且只畫「可見區域」 =====
                    Canvas(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        // 可見區域（影像座標系）
                        val invScale = 1f / scale
                        val visLeft   = (-offsetX) * invScale
                        val visTop    = (-offsetY) * invScale
                        val visRight  = (size.width  - offsetX) * invScale
                        val visBottom = (size.height - offsetY) * invScale

                        val srcLeft   = visLeft.coerceIn(0f, bmp.width.toFloat())
                        val srcTop    = visTop.coerceIn(0f, bmp.height.toFloat())
                        val srcRight  = visRight.coerceIn(0f, bmp.width.toFloat())
                        val srcBottom = visBottom.coerceIn(0f, bmp.height.toFloat())

                        val dstLeft   = srcLeft  * scale + offsetX
                        val dstTop    = srcTop   * scale + offsetY
                        val dstRight  = srcRight * scale + offsetX
                        val dstBottom = srcBottom* scale + offsetY

                        val srcW = (srcRight - srcLeft).coerceAtLeast(0f)
                        val srcH = (srcBottom - srcTop).coerceAtLeast(0f)
                        val dstW = (dstRight - dstLeft).coerceAtLeast(0f)
                        val dstH = (dstBottom - dstTop).coerceAtLeast(0f)

                        // 背景圖：只畫可見塊
                        if (srcW > 0f && srcH > 0f && dstW > 0f && dstH > 0f) {
                            drawImage(
                                image = bmp,
                                srcOffset = IntOffset(srcLeft.toInt(), srcTop.toInt()),
                                srcSize   = IntSize(srcW.toInt(), srcH.toInt()),
                                dstOffset = IntOffset(dstLeft.toInt(), dstTop.toInt()),
                                dstSize   = IntSize(dstW.toInt(), dstH.toInt())
                            )

                            // Overlay：用自己的像素座標裁切（grid 像素）
                            val g = grid; val ov = overlay
                            if (showGridOverlay && g != null && ov != null) {
                                // 取樣倍率：原始影像像素 / Grid 像素（通常 = gridSample）
                                val ovScaleX = imageBitmap!!.width  / g.w.toFloat()
                                val ovScaleY = imageBitmap!!.height / g.h.toFloat()

                                // 把背景圖的 src 矩形換算成 overlay 的 src 矩形（各自用自己的座標系）
                                val oSrcLeft   = (srcLeft   / ovScaleX).coerceIn(0f, g.w.toFloat())
                                val oSrcTop    = (srcTop    / ovScaleY).coerceIn(0f, g.h.toFloat())
                                val oSrcRight  = (srcRight  / ovScaleX).coerceIn(0f, g.w.toFloat())
                                val oSrcBottom = (srcBottom / ovScaleY).coerceIn(0f, g.h.toFloat())

                                val oSrcW = (oSrcRight - oSrcLeft).coerceAtLeast(0f)
                                val oSrcH = (oSrcBottom - oSrcTop).coerceAtLeast(0f)

                                if (oSrcW > 0f && oSrcH > 0f) {
                                    drawImage(
                                        image = ov,
                                        srcOffset = IntOffset(oSrcLeft.toInt(), oSrcTop.toInt()),
                                        srcSize   = IntSize(oSrcW.toInt(), oSrcH.toInt()),
                                        // 目的地矩形仍用背景圖的 dst，這樣就能貼齊
                                        dstOffset = IntOffset(dstLeft.toInt(), dstTop.toInt()),
                                        dstSize   = IntSize(dstW.toInt(), dstH.toInt())
                                    )
                                }
                            }
                        }

                        // 起訖點（將影像座標轉螢幕座標後繪製）
                        start?.let {
                            val s = imgToScreen(it)
                            drawCircle(color = colorMaterial.primary, radius = 8f, center = s)
                        }
                        goal?.let  {
                            val g2 = imgToScreen(it)
                            drawCircle(color = colorMaterial.tertiary, radius = 8f, center = g2)
                        }

                        // 路徑（逐點轉換成螢幕座標繪製）
                        if (path.isNotEmpty()) {
                            val p = Path().apply {
                                val first = imgToScreen(path.first())
                                moveTo(first.x, first.y)
                                for (i in 1 until path.size) {
                                    val sp = imgToScreen(path[i])
                                    lineTo(sp.x, sp.y)
                                }
                            }
                            drawPath(path = p, color = colorMaterial.secondary, style = Stroke(width = 5f))
                        }
                    }
                }
            } ?: run {
                Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                    Text("尚未載入平面圖資源")
                }
            }
        }
    }
}
