package com.example.project250311.Map.data

import com.google.android.gms.maps.model.LatLng

/**
 * 自定義地點資料類別，用於地圖標記和顯示
 */
data class CustomPoint(
    val location: LatLng,
    val name: String,
    val description: String
)