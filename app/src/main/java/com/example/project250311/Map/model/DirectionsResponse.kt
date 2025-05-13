package com.example.project250311.Map.model

data class DirectionsResponse(
    val routes: List<Route>
)

data class Route(
    val overview_polyline: OverviewPolyline,
    val legs: List<Leg>, // 每条路线可能包含多个 legs
    val summary: String  // 总路线说明
)

data class OverviewPolyline(
    val points: String // 加密的polyline坐标点
)

data class Leg(
    val distance: Distance,
    val duration: Duration,
    val start_address: String,
    val end_address: String,
    val steps: List<Step> // 每一小段的步骤指引
)

data class Distance(
    val text: String, // 显示的距离，如 "5 km"
    val value: Int     // 距离值（米）
)

data class Duration(
    val text: String, // 显示的时长，如 "15 mins"
    val value: Int    // 持续时间（秒）
)

data class Step(
    val distance: Distance,
    val duration: Duration,
    val end_location: Location,
    val html_instructions: String // 步骤说明
)

data class Location(
    val lat: Double,
    val lng: Double
)
