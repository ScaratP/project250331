package com.example.project250311.Map.data

import com.google.android.gms.maps.model.LatLng
import com.example.project250311.Map.data.CustomPoint

/**
 * 理工學院各棟大樓出入口位置
 */
object SEEntrances {
    val entrancePoints = listOf(
        CustomPoint(
            LatLng(22.738571, 121.065730),
            "SEA_MAIN",
            "理工A棟主要出入口"
        ),
        CustomPoint(
            LatLng(22.738315, 121.065829),
            "SEB_MAIN",
            "理工B棟主要出入口"
        ),
        CustomPoint(
            LatLng(22.737967, 121.066180),
            "SEC_MAIN",
            "理工C棟主要出入口"
        )
    )

    /**
     * 根據教室編號找到最近的出入口
     * @param classroomCode 教室編號（如：SEA101, SEB201, SEC301等）
     * @return 最近的出入口點
     */
    fun getNearestEntrance(classroomCode: String): CustomPoint {
        return when {
            classroomCode.startsWith("SEA") -> entrancePoints[0]
            classroomCode.startsWith("SEB") -> entrancePoints[1]
            classroomCode.startsWith("SEC") -> entrancePoints[2]
            else -> entrancePoints[1] // 默認返回B棟出入口
        }
    }
}