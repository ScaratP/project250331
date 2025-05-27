package com.example.project250311.Map

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.location.Location
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.project250311.Map.network.RetrofitInstance
import com.example.project250311.Map.utils.PolylineUtils
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.*
import kotlinx.coroutines.delay
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import com.example.project250311.R
import com.google.android.gms.maps.model.BitmapDescriptor
import kotlinx.coroutines.launch


data class CustomPoint(val location: LatLng, val name: String)

@SuppressLint("MissingPermission")
@Composable
fun MapScreen() {
    val context = LocalContext.current
    val fusedClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    val scope = rememberCoroutineScope()


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

    val customPoints = remember {
        listOf(
            CustomPoint(LatLng(22.7380, 121.0700), "教室 A101"),
            CustomPoint(LatLng(22.7370, 121.0650), "校內咖啡廳"),
            CustomPoint(LatLng(22.7355, 121.0680), "圖書館入口")
        )
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> permissionGranted = granted }

    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            launcher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        } else {
            permissionGranted = true
        }
    }

    LaunchedEffect(permissionGranted) {
        if (permissionGranted) {
            fusedClient.lastLocation
                .addOnSuccessListener { loc: Location? ->
                    if (loc != null) {
                        val ll = LatLng(loc.latitude, loc.longitude)
                        currentLoc = ll
                        cameraState.move(CameraUpdateFactory.newLatLng(ll))
                    } else {
                        errorMsg = "無法取得目前位置"
                    }
                }
                .addOnFailureListener {
                    errorMsg = "定位失敗：${it.message}"
                }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.matchParentSize(),
            cameraPositionState = cameraState,
            properties = MapProperties(isMyLocationEnabled = permissionGranted),
            onMapClick = { latLng ->
                destination = latLng
                routePoints = emptyList()
                drawRoute(
                    origin = currentLoc,
                    dest = latLng,
                    onStart = { isRouting = true },
                    onSuccess = {
                        isRouting = false
                        routePoints = it
                    },
                    onError = {
                        isRouting = false
                        errorMsg = it

                        scope.launch {
                            delay(3000) // 等待 3 秒
                            errorMsg = null // 清除錯誤訊息
                        }
                    }
                )
            }
        ) {
            currentLoc?.let {
                Marker(
                    state = MarkerState(it),
                    title = "你的位置",
                    icon = getResizedBitmapDescriptor(context, R.drawable.marker, 120, 120)
                )
            }

            destination?.let {
                Marker(
                    state = MarkerState(it),
                    title = "目的地",
                    icon = getResizedBitmapDescriptor(context, R.drawable.marker, 120, 120)
                )
            }

            customPoints.forEach { custom ->
                Marker(
                    state = MarkerState(custom.location),
                    title = custom.name,
                    icon = getResizedBitmapDescriptor(context, R.drawable.marker, 100, 100)
                )
            }

            if (routePoints.isNotEmpty()) {
                Polyline(points = routePoints, width = 6f, color = Color.Blue)
            }
        }

        if (isRouting) {
            CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(48.dp)
                    .background(Color.White.copy(alpha = 0.6f), shape = MaterialTheme.shapes.small)
                    .padding(8.dp)
            )
        }

        AnimatedVisibility(
            visible = errorMsg != null,
            enter = fadeIn(tween(300)) + slideInVertically(initialOffsetY = { -100 }, animationSpec = tween(300)),
            exit = fadeOut(tween(300)) + slideOutVertically(targetOffsetY = { -100 }, animationSpec = tween(300))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentSize(Alignment.TopCenter)
                    .padding(top = 16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .background(color = Color(0xFFFFCDD2), shape = MaterialTheme.shapes.large)
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
        }
    }
}

fun getResizedBitmapDescriptor(context: Context, resId: Int, width: Int, height: Int): BitmapDescriptor {
    val imageBitmap = BitmapFactory.decodeResource(context.resources, resId)
    val scaledBitmap = Bitmap.createScaledBitmap(imageBitmap, width, height, false)
    return BitmapDescriptorFactory.fromBitmap(scaledBitmap)
}


fun drawRoute(
    origin: LatLng?,
    dest: LatLng,
    onStart: () -> Unit = {},
    onSuccess: (List<LatLng>) -> Unit,
    onError: (String) -> Unit
) {
    if (origin == null) {
        onError("尚未取得目前位置")
        return
    }
    onStart()
    val o = "${origin.latitude},${origin.longitude}"
    val d = "${dest.latitude},${dest.longitude}"
    RetrofitInstance.api.getDirections(o, d, apiKey = "AIzaSyDbCPl8a9m7dGMgTqF2GFL_cPSRjV_hiOQ")
        .enqueue(object : Callback<com.example.project250311.Map.model.DirectionsResponse> {
            override fun onResponse(
                call: Call<com.example.project250311.Map.model.DirectionsResponse>,
                response: Response<com.example.project250311.Map.model.DirectionsResponse>
            ) {
                if (response.isSuccessful) {
                    val points = response.body()?.routes
                        ?.firstOrNull()
                        ?.overview_polyline
                        ?.points
                    if (!points.isNullOrEmpty()) {
                        onSuccess(PolylineUtils.decodePolyline(points))
                    } else {
                        onError("請按右下角導航")
                    }
                } else {
                    onError("API 回傳 ${response.code()}")
                }
            }

            override fun onFailure(
                call: Call<com.example.project250311.Map.model.DirectionsResponse>,
                t: Throwable
            ) {
                onError("網路錯誤：${t.localizedMessage}")
            }
        })
}



