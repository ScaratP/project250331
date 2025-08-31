package com.example.project250311.Map

import android.location.Location.distanceBetween
import com.google.android.gms.maps.model.LatLng
import kotlin.math.sqrt

// 預設校園路線
data class CampusRoute(
    val id: String,
    val name: String,
    val points: List<LatLng>,
    val estimatedTimeInMinutes: Int
)

// 校園路線管理器
object CampusRoutes {
    // 儲存預設路線
    private val routes = listOf(
        // 這裡可以添加預設的校園路線
        CampusRoute(
            id = "library_to_engineering",
            name = "圖書館到理工學院",
            points = listOf(
                LatLng(22.73567797363531, 121.06765063326057), // 圖書館
                LatLng(22.736200, 121.067100),
                LatLng(22.736800, 121.066800),
                LatLng(22.737400, 121.066500),
                LatLng(22.738000, 121.066300),
                LatLng(22.738542718675728, 121.06613647723158)  // 理工學院
            ),
            estimatedTimeInMinutes = 5
        ),
        CampusRoute(
            id = "dormitory_to_library",
            name = "學生宿舍到圖書館",
            points = listOf(
                LatLng(22.737311078649267, 121.06515081473918), // 第一學生宿舍
                LatLng(22.737000, 121.065500),
                LatLng(22.736500, 121.066000),
                LatLng(22.736200, 121.066500),
                LatLng(22.736000, 121.067000),
                LatLng(22.73567797363531, 121.06765063326057)  // 圖書館
            ),
            estimatedTimeInMinutes = 4
        )
        // 可以添加更多預設路線
    )
    
    // 查找最接近的路線
    fun findRoute(origin: LatLng, destination: LatLng): CampusRoute? {
        val MAX_START_DISTANCE = 100.0 // 公尺
        val MAX_END_DISTANCE = 100.0   // 公尺
        
        for (route in routes) {
            if (route.points.size < 2) continue
            
            val start = route.points.first()
            val end = route.points.last()
            
            val startDistance = distanceBetween(origin, start)
            val endDistance = distanceBetween(destination, end)
            
            if (startDistance <= MAX_START_DISTANCE && endDistance <= MAX_END_DISTANCE) {
                return route
            }
        }
        
        return null
    }
    
    // 查找反向路線
    fun findReverseRoute(origin: LatLng, destination: LatLng): CampusRoute? {
        val MAX_START_DISTANCE = 100.0 // 公尺
        val MAX_END_DISTANCE = 100.0   // 公尺
        
        for (route in routes) {
            if (route.points.size < 2) continue
            
            val start = route.points.first()
            val end = route.points.last()
            
            val startDistance = distanceBetween(origin, end)
            val endDistance = distanceBetween(destination, start)
            
            if (startDistance <= MAX_START_DISTANCE && endDistance <= MAX_END_DISTANCE) {
                return CampusRoute(
                    id = "${route.id}_reversed",
                    name = "${route.name} (反向)",
                    points = route.points.reversed(),
                    estimatedTimeInMinutes = route.estimatedTimeInMinutes
                )
            }
        }
        
        return null
    }
}