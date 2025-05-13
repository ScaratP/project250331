package com.example.project250311.Map.utils

import com.google.android.gms.maps.model.LatLng

object PolylineUtils {
    fun decodePolyline(encoded: String): List<LatLng> {
        val polylineList = mutableListOf<LatLng>()
        var index = 0
        val len = encoded.length
        var lat = 0
        var lng = 0

        while (index < len) {
            var b: Int
            var shift = 0
            var result = 0
            do {
                b = encoded[index++].code - 63
                result = result or ((b and 0x1f) shl shift)
                shift += 5
            } while (b >= 0x20)

            // 用 inv() 取代 ~
            val dLat = if ((result and 1) != 0) {
                (result shr 1).inv()
            } else {
                result shr 1
            }
            lat += dLat

            // 重置，繼續解析經度
            shift = 0
            result = 0
            do {
                b = encoded[index++].code - 63
                result = result or ((b and 0x1f) shl shift)
                shift += 5
            } while (b >= 0x20)

            val dLng = if ((result and 1) != 0) {
                (result shr 1).inv()
            } else {
                result shr 1
            }
            lng += dLng

            // 把微度值轉回實際經緯度
            val latLng = LatLng(lat / 1E5, lng / 1E5)
            polylineList.add(latLng)
        }

        return polylineList
    }
}
