package com.example.project250311.Map

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Location
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.project250311.Map.network.RetrofitInstance
import com.example.project250311.Map.utils.PolylineUtils
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.*
import com.google.maps.android.compose.*
import kotlinx.coroutines.delay
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

@SuppressLint("MissingPermission")
@Composable
fun MapScreen() {
    val context = LocalContext.current
    val fusedClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    // 預設攝影機位置（台東大學）
    val defaultLatLng = LatLng(22.7366, 121.0675)
    val cameraState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(defaultLatLng, 15f)
    }

    var permissionGranted by remember { mutableStateOf(false) }
    var currentLoc by remember { mutableStateOf<LatLng?>(null) }
    var destination by remember { mutableStateOf<LatLng?>(null) }
    var routePoints by remember { mutableStateOf<List<LatLng>>(emptyList()) }
    var isRouting by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    // 1. 請求定位權限
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permissionGranted = granted
    }
    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            launcher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        } else permissionGranted = true
    }

    // 2. 拿一次 lastLocation
    LaunchedEffect(permissionGranted) {
        if (permissionGranted) {
            fusedClient.lastLocation
                .addOnSuccessListener { loc: Location? ->
                    if (loc != null) {
                        val ll = LatLng(loc.latitude, loc.longitude)
                        currentLoc = ll
                        cameraState.move(CameraUpdateFactory.newLatLng(ll))
                    } else errorMsg = "無法取得目前位置"
                }
                .addOnFailureListener {
                    errorMsg = "定位失敗：${it.message}"
                }
        }
    }

    // 3. 畫地圖、標記、路線
    Box(Modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.matchParentSize(),
            cameraPositionState = cameraState,
            properties = MapProperties(isMyLocationEnabled = permissionGranted),
            onMapClick = { latLng ->
                destination = latLng
                currentLoc?.let { origin ->
                    // 開始 routing
                    isRouting = true
                    errorMsg = null
                    val o = "${origin.latitude},${origin.longitude}"
                    val d = "${latLng.latitude},${latLng.longitude}"
                    RetrofitInstance.api.getDirections(o, d, apiKey = "AIzaSyDbCPl8a9m7dGMgTqF2GFL_cPSRjV_hiOQ")
                        .enqueue(object : Callback<com.example.project250311.Map.model.DirectionsResponse> {
                            override fun onResponse(
                                call: Call<com.example.project250311.Map.model.DirectionsResponse>,
                                response: Response<com.example.project250311.Map.model.DirectionsResponse>
                            ) {
                                isRouting = false
                                if (response.isSuccessful) {
                                    val pts = response.body()?.routes?.firstOrNull()?.overview_polyline?.points
                                    if (!pts.isNullOrEmpty()) {
                                        routePoints = PolylineUtils.decodePolyline(pts)
                                    } else errorMsg = "找不到路線"
                                } else errorMsg = "API 回傳 ${response.code()}"
                            }
                            override fun onFailure(
                                call: Call<com.example.project250311.Map.model.DirectionsResponse>,
                                t: Throwable
                            ) {
                                isRouting = false
                                errorMsg = "網路錯誤：${t.localizedMessage}"
                            }
                        })
                } ?: run {
                    errorMsg = "尚未取得當前位置"
                }
            }
        ) {
            // 當前位置標記
            currentLoc?.let { Marker(state = MarkerState(it), title = "你的位置") }
            // 目的地標記
            destination?.let { Marker(state = MarkerState(it), title = "目的地") }
            // 路線 polyline
            if (routePoints.isNotEmpty()) {
                Polyline(points = routePoints, width = 6f, color = Color.Blue)
            }
        }

        // Overlay: 錯誤訊息 with animation
        LaunchedEffect(errorMsg) {
            if (errorMsg != null) {
                delay(3000) // wait for 3 seconds before clearing the error message
                errorMsg = null
            }
        }

        AnimatedVisibility(
            visible = errorMsg != null,
            modifier = Modifier
                .fillMaxWidth() // 讓 AnimatedVisibility 撐滿寬度，才可進行正中間對齊
                .align(Alignment.TopCenter)
                .padding(16.dp), // 讓錯誤訊息顯示在最上方正中間
            enter = fadeIn(animationSpec = tween(300)) + slideInVertically(initialOffsetY = { -100 }, animationSpec = tween(300)),
            exit = fadeOut(animationSpec = tween(300)) + slideOutVertically(targetOffsetY = { -100 }, animationSpec = tween(300))
        ) {
            Box(
                modifier = Modifier
                    .wrapContentWidth()
                    .background(Color(0xFFFFCDD2), shape = MaterialTheme.shapes.large)
                    .padding(horizontal = 20.dp, vertical = 12.dp)

            ) {
                Text(
                    text = errorMsg ?: "",
                    color = Color.DarkGray,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
            }
        }
        // Overlay: Routing loading
        if (isRouting) {
            CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .size(48.dp)
                    .background(Color.White.copy(alpha = 0.6f), shape = MaterialTheme.shapes.small)
                    .padding(8.dp)
            )
        }
    }
}
