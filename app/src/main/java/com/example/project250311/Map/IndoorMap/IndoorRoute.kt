package com.example.project250311.Map.IndoorMap

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.example.project250311.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.plus
import kotlinx.coroutines.withContext
import kotlin.compareTo
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

object IndoorPathfinder{
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

    // ======================= A* 演算法 =======================
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
        val num = abs((b.x - a.x) * (a.y - p.y) - (a.x - p.x) * (b.y - a.y))
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
    internal fun BooleanArray.toBitPackedBytes(): ByteArray {
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

    internal fun ByteArray.toBooleanArray(totalBits: Int): BooleanArray {
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

    fun getMapDrawableResId(groupName: String): Int {
        return when (groupName) {
            "se1" -> R.drawable.se1
            "se2" -> R.drawable.se2
            "se3" -> R.drawable.se3
            "sea4" -> R.drawable.sea4
            "sea5" -> R.drawable.sea5
            "seb4" -> R.drawable.seb4
            "sec4" -> R.drawable.sec4
            "sec5" -> R.drawable.sec5
            else -> R.drawable.se1 // 預設
        }
    }
}
