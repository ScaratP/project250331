package com.example.project250311.Map

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.location.Location
import android.os.Looper
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.project250311.Map.network.RetrofitInstance
import com.example.project250311.Map.utils.PolylineUtils
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import com.example.project250311.R
import com.google.android.gms.maps.model.Dot
import com.google.android.gms.maps.model.Gap
import com.google.android.gms.maps.model.PatternItem

// 1. CustomPoint 包含 description，用於顯示介紹對話框
data class CustomPoint(
    val location: LatLng,
    val name: String,
    val description: String
)

@SuppressLint("MissingPermission")
@Composable
fun MapScreen() {
    val context = LocalContext.current
    val fusedClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    val scope = rememberCoroutineScope()

    // 2. 初始鏡頭：校園中心
    val defaultLatLng = LatLng(22.7366, 121.0675)
    val cameraState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(defaultLatLng, 15f)
    }

    // 虛線樣式
    val dashPattern = listOf<PatternItem>(Dot(), Gap(10f))

    // 3. 狀態變數
    var permissionGranted by remember { mutableStateOf(false) }
    var currentLoc by remember { mutableStateOf<LatLng?>(null) }
    var lastRerouteLoc by remember { mutableStateOf<LatLng?>(null) } // 上次重新路線用的位置
    var destination by remember { mutableStateOf<LatLng?>(null) }
    var routePoints by remember { mutableStateOf<List<LatLng>>(emptyList()) }
    var isRouting by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var selectedPoint by remember { mutableStateOf<CustomPoint?>(null) }
    var travelTimeText by remember { mutableStateOf<String?>(null) }

    // 4. 自訂地點列表
    val customPoints = remember {
        listOf(
            CustomPoint(
                LatLng(22.738542718675728, 121.06613647723158),
                "理工學院",
                "理工學院位於校園北側，內有多間實驗室與教室。"
            ),
            CustomPoint(
                LatLng(22.738700601981595, 121.06497484121572),
                "資源回收站",
                "校園資源回收站，提供紙張、塑膠、金屬等回收服務。"
            ),
            CustomPoint(
                LatLng(22.737311078649267, 121.06515081473918),
                "第一學生宿舍",
                "第一學生宿舍是校園內最早啟用的一棟，設有單人間與雙人間。"
            ),
            CustomPoint(
                LatLng(22.736985102168752, 121.06541152586802),
                "一宿餐廳",
                "一宿餐廳提供多種大學餐選項，並且全天開放。"
            ),
            CustomPoint(
                LatLng(22.73667908810683, 121.065407893042),
                "7-11",
                "校園門口的 7-11，方便師生隨時購買飲料與零食。"
            ),
            CustomPoint(
                LatLng(22.736206402791673, 121.0651933530481),
                "第二學生宿舍",
                "第二學生宿舍新落成，房間採現代化設計，附有公共休息室。"
            ),
            CustomPoint(
                LatLng(22.73340361849101, 121.06581718244463),
                "操場",
                "校園操場，可供足球、慢跑與排球等活動使用。"
            ),
            CustomPoint(
                LatLng(22.73292806965856, 121.06740378782679),
                "體育館",
                "體育館內有籃球場、羽球場與健身房，對外開放時段請參考公告。"
            ),
            CustomPoint(
                LatLng(22.733878454879942, 121.06840153239384),
                "籃球場",
                "室外籃球場，夜間有照明，適合休閒籃球活動。"
            ),
            CustomPoint(
                LatLng(22.73567797363531, 121.06765063326057),
                "圖書館",
                "圖書館擁有豐富藏書與安靜閱讀區，也被稱為全球八度獨特圖書館之一。"
            ),
            CustomPoint(
                LatLng(22.73599707595406, 121.06669594919275),
                "共同教學大樓",
                "共同教學大樓提供多間多功能教室與研討室，適合大小型課程。"
            ),
            CustomPoint(
                LatLng(22.736520281361905, 121.06698965107849),
                "靜心書院",
                "靜心書院為校園的宗教與靜修中心，定期舉辦靜心活動。"
            ),
            CustomPoint(
                LatLng(22.73917469216459, 121.0670538530699),
                "師範學院",
                "師範學院為教育學系與師資培育單位所在地。"
            ),
            CustomPoint(
                LatLng(22.73863578654702, 121.06753201693554),
                "淑貞講堂",
                "淑貞講堂常舉辦演講與表演活動，座位寬敞舒適。"
            ),
            CustomPoint(
                LatLng(22.738117547140654, 121.06843506125115),
                "演藝廳",
                "演藝廳為音樂與戲劇演出場地，具備專業音響設備。"
            ),
            CustomPoint(
                LatLng(22.73795917868316, 121.06901698013174),
                "人文學院",
                "人文學院包含文學院與歷史系，教室與辦公室分布寬敞。"
            ),
            CustomPoint(
                LatLng(22.736849993509747, 121.0686699597833),
                "行政大樓",
                "行政大樓為校長室與各行政單位辦公的地方。"
            )
        )
    }

    // 5. 申請定位權限
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

    // 6. 設定持續定位，但不自動移動鏡頭
    val locationRequest = remember {
        LocationRequest.create().apply {
            interval = 5000
            fastestInterval = 3000
            priority = Priority.PRIORITY_HIGH_ACCURACY
        }
    }
    val locationCallback = remember {
        object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc: Location? = result.lastLocation
                if (loc != null) {
                    val newLatLng = LatLng(loc.latitude, loc.longitude)
                    currentLoc = newLatLng

                    // 只有使用者移動超過 20 公尺時才重新路線
                    destination?.let { dest ->
                        val prev = lastRerouteLoc
                        if (prev == null || distanceBetween(prev, newLatLng) > 20f) {
                            lastRerouteLoc = newLatLng
                            drawRoute(
                                origin = newLatLng,
                                dest = dest,
                                onStart = { isRouting = true },
                                onSuccess = { points ->
                                    isRouting = false
                                    routePoints = points
                                },
                                onTime = { timeText ->
                                    travelTimeText = timeText
                                },
                                onError = {
                                    isRouting = false
                                    errorMsg = it
                                    scope.launch {
                                        delay(3000)
                                        errorMsg = null
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
    LaunchedEffect(permissionGranted) {
        if (permissionGranted) {
            fusedClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
        }
    }
    DisposableEffect(Unit) {
        onDispose { fusedClient.removeLocationUpdates(locationCallback) }
    }

    // 7. 初次定位
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
                        scope.launch {
                            delay(3000)
                            errorMsg = null
                        }
                    }
                }
                .addOnFailureListener {
                    errorMsg = "定位失敗：${it.message}"
                    scope.launch {
                        delay(3000)
                        errorMsg = null
                    }
                }
        }
    }

    // 8. Map 畫面
    Box(modifier = Modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.matchParentSize(),
            cameraPositionState = cameraState,
            properties = MapProperties(isMyLocationEnabled = permissionGranted),
            onMapClick = { latLng ->
                // 使用者在空白處點擊：設定新的目的地，重置計數並立即計算
                destination = latLng
                routePoints = emptyList()
                lastRerouteLoc = currentLoc  // 確保下次會重新繪一條
                travelTimeText = null
                drawRoute(
                    origin = currentLoc,
                    dest = latLng,
                    onStart = { isRouting = true },
                    onSuccess = { points ->
                        isRouting = false
                        routePoints = points
                    },
                    onTime = { timeText ->
                        travelTimeText = timeText
                    },
                    onError = {
                        isRouting = false
                        errorMsg = it
                        scope.launch {
                            delay(3000)
                            errorMsg = null
                        }
                    }
                )
            }
        ) {
            // A. 顯示「你的位置」Marker
            currentLoc?.let {
                Marker(
                    state = MarkerState(it),
                    title = "你的位置",
                    icon = getResizedBitmapDescriptor(context, R.drawable.marker, 120, 120)
                )
            }

            // B. 顯示「目的地」Marker，點擊直接清除
            destination?.let { destLatLng ->
                Marker(
                    state = MarkerState(destLatLng),
                    title = "目的地",
                    icon = getResizedBitmapDescriptor(context, R.drawable.marker, 120, 120),
                    onClick = {
                        destination = null
                        routePoints = emptyList()
                        travelTimeText = null
                        true
                    }
                )
            }

            // C. 顯示自訂地點 Marker
            customPoints.forEach { custom ->
                Marker(
                    state = MarkerState(custom.location),
                    title = custom.name,
                    icon = getResizedBitmapDescriptor(context, R.drawable.marker, 80, 80),
                    onClick = {
                        selectedPoint = custom
                        false
                    }
                )
            }

            // D. 畫三段 Polyline：灰色虛線 + 藍色實線 + 灰色虛線
            // D1. 起點（currentLoc）→ 路線第一點（灰色虛線）
            if (currentLoc != null && routePoints.isNotEmpty()) {
                val firstOnRoad = routePoints.first()
                Polyline(
                    points = listOf(currentLoc!!, firstOnRoad),
                    width = 6f,
                    color = Color.Gray,
                    pattern = dashPattern
                )
            }
            // D2. 路線主段（藍色實線）
            if (routePoints.isNotEmpty()) {
                Polyline(
                    points = routePoints,
                    width = 12f,
                    color = Color.Blue
                )
            }
            // D3. 路線最後一點→目的地（灰色虛線）
            if (routePoints.isNotEmpty() && destination != null) {
                val lastOnRoad = routePoints.last()
                Polyline(
                    points = listOf(lastOnRoad, destination!!),
                    width = 6f,
                    color = Color.Gray,
                    pattern = dashPattern
                )
            }
        }

        // 顯示「路線計算中」圓形指示器
        if (isRouting) {
            CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(48.dp)
                    .background(Color.White.copy(alpha = 0.6f), shape = MaterialTheme.shapes.small)
                    .padding(8.dp)
            )
        }

        // 顯示費時文字於底部
        travelTimeText?.let { text ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
                    .background(Color.White.copy(alpha = 0.8f), shape = MaterialTheme.shapes.medium)
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "預計花費：$text",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.DarkGray
                )
            }
        }

        // 顯示錯誤訊息（三秒後自動消失）
        AnimatedVisibility(
            visible = errorMsg != null,
            enter = fadeIn(tween(300)) + slideInVertically(
                initialOffsetY = { -100 }, animationSpec = tween(300)
            ),
            exit = fadeOut(tween(300)) + slideOutVertically(
                targetOffsetY = { -100 }, animationSpec = tween(300)
            )
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

        // 9. 顯示自訂介紹對話框
        selectedPoint?.let { point ->
            AlertDialog(
                onDismissRequest = { selectedPoint = null },
                title = { Text(text = point.name) },
                text = { Text(text = point.description) },
                dismissButton = {
                    TextButton(onClick = { selectedPoint = null }) {
                        Text("取消")
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        // 按下「導航」才設定目的地並畫路線
                        destination = point.location
                        routePoints = emptyList()
                        lastRerouteLoc = currentLoc
                        travelTimeText = null
                        drawRoute(
                            origin = currentLoc,
                            dest = point.location,
                            onStart = { isRouting = true },
                            onSuccess = { points ->
                                isRouting = false
                                routePoints = points
                            },
                            onTime = { timeText ->
                                travelTimeText = timeText
                            },
                            onError = {
                                isRouting = false
                                errorMsg = it
                                scope.launch {
                                    delay(3000)
                                    errorMsg = null
                                }
                            }
                        )
                        selectedPoint = null
                    }) {
                        Text("導航")
                    }
                }
            )
        }
    }
}

// Utility：計算兩點距離（單位：公尺）
fun distanceBetween(a: LatLng, b: LatLng): Float {
    val result = FloatArray(1)
    Location.distanceBetween(a.latitude, a.longitude, b.latitude, b.longitude, result)
    return result[0]
}

// Utility：縮放 Bitmap 並回傳給 Marker 用
fun getResizedBitmapDescriptor(
    context: Context,
    resId: Int,
    width: Int,
    height: Int
): com.google.android.gms.maps.model.BitmapDescriptor {
    val imageBitmap = BitmapFactory.decodeResource(context.resources, resId)
    val scaledBitmap = android.graphics.Bitmap.createScaledBitmap(imageBitmap, width, height, false)
    return com.google.android.gms.maps.model.BitmapDescriptorFactory.fromBitmap(scaledBitmap)
}

/**
 * drawRoute 新增 onTime callback：從 API 回應中取出「duration.text」並回傳
 */
fun drawRoute(
    origin: LatLng?,
    dest: LatLng,
    onStart: () -> Unit = {},
    onSuccess: (List<LatLng>) -> Unit,
    onTime: (String) -> Unit,
    onError: (String) -> Unit
) {
    if (origin == null) {
        onError("尚未取得目前位置")
        return
    }
    onStart()
    val o = "${origin.latitude},${origin.longitude}"
    val d = "${dest.latitude},${dest.longitude}"
    RetrofitInstance.api.getDirections(
        origin = o,
        destination = d,
        mode = "walking",
        apiKey = "AIzaSyDbCPl8a9m7dGMgTqF2GFL_cPSRjV_hiOQ"
    )
        .enqueue(object : Callback<com.example.project250311.Map.model.DirectionsResponse> {
            override fun onResponse(
                call: Call<com.example.project250311.Map.model.DirectionsResponse>,
                response: Response<com.example.project250311.Map.model.DirectionsResponse>
            ) {
                if (response.isSuccessful) {
                    val body = response.body()
                    val route = body?.routes?.firstOrNull()
                    val leg = route?.legs?.firstOrNull()
                    val points = route?.overview_polyline?.points

                    val durationText = leg?.duration?.text ?: "未知時間"
                    onTime(durationText)

                    if (!points.isNullOrEmpty()) {
                        onSuccess(PolylineUtils.decodePolyline(points))
                    } else {
                        onError("找不到路線")
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
