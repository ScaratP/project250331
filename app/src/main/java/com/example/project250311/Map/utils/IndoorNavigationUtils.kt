package com.example.project250311.Map.utils

import android.content.Context
import com.example.project250311.Map.IndoorMap.ReferencePoint
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * 室內導航工具類，提供教室點位數據讀取和路徑計算功能
 */
object IndoorNavigationUtils {
    
    /**
     * 從原始資源文件讀取教室參考點數據
     */
    fun loadReferencePointsFromRaw(context: Context, rawResourceId: Int): List<ReferencePoint> {
        try {
            val inputStream = context.resources.openRawResource(rawResourceId)
            val reader = BufferedReader(InputStreamReader(inputStream))
            val jsonString = reader.use { it.readText() }
            
            val gson = Gson()
            val listType = object : TypeToken<List<ReferencePoint>>() {}.type
            return gson.fromJson(jsonString, listType)
        } catch (e: Exception) {
            e.printStackTrace()
            return emptyList()
        }
    }
    
    /**
     * 計算兩點間的直線距離
     */
    fun calculateDistance(point1: ReferencePoint, point2: ReferencePoint): Double {
        // 考慮圖像尺寸的歸一化距離計算
        return sqrt(
            (point1.x - point2.x).pow(2) + 
            (point1.y - point2.y).pow(2)
        )
    }
    
    /**
     * 尋找最近的參考點
     */
    fun findNearestPoint(points: List<ReferencePoint>, x: Double, y: Double, imageId: Int): ReferencePoint? {
        if (points.isEmpty()) return null
        
        return points.filter { it.imageId == imageId }
            .minByOrNull { sqrt((it.x - x).pow(2) + (it.y - y).pow(2)) }
    }
    
    /**
     * 簡單的路徑規劃算法 (Dijkstra 算法的簡化版)
     * 在真實應用中，應該使用完整的 Dijkstra 或 A* 算法
     */
    fun findPathBetweenPoints(
        allPoints: List<ReferencePoint>, 
        startPoint: ReferencePoint, 
        endPoint: ReferencePoint,
        connectionThreshold: Double = 30.0 // 兩點之間可以直接連接的距離閾值
    ): List<ReferencePoint> {
        // 如果起點和終點在不同樓層，直接返回
        if (startPoint.imageId != endPoint.imageId) {
            return listOf(startPoint, endPoint)
        }
        
        // 過濾當前樓層的點
        val floorPoints = allPoints.filter { it.imageId == startPoint.imageId }
        
        // 如果只有起點和終點，或者它們距離很近，直接連接
        if (floorPoints.size <= 2 || calculateDistance(startPoint, endPoint) < connectionThreshold) {
            return listOf(startPoint, endPoint)
        }
        
        // 真實應用中，這裡應該實現 Dijkstra 或 A* 算法
        // 這裡簡化為直接連接，未來可以擴展
        return listOf(startPoint, endPoint)
    }
}
