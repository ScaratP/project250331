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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
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
import com.example.project250311.Map.data.CustomPoint
import com.example.project250311.Map.data.SEEntrances

// 使用 data/CustomPoint.kt 中的定義，移除本地定義
/*
*/

@SuppressLint("MissingPermission")
@Composable
fun MapScreen(navController: NavHostController) {
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

    // Search UI states
    var searchExpanded by remember { mutableStateOf(false) }
    var startText by remember { mutableStateOf("") }
    var destText by remember { mutableStateOf("") }
    var startSelection by remember { mutableStateOf<LatLng?>(null) }
    var destSelection by remember { mutableStateOf<LatLng?>(null) }
    var startExpanded by remember { mutableStateOf(false) }
    var destExpanded by remember { mutableStateOf(false) }

    // 4. 自訂地點列表（包含校園地點與理工教室）
    val seClassrooms = remember {
        com.example.project250311.Map.data.SEClassrooms.allClassrooms.sortedBy { it.name }
    }

    val customPoints = remember {
        val campusPoints = listOf(
            CustomPoint(LatLng(22.73881963044863, 121.06574371741712), "理工學院A棟入口", "臺東大學理工學院A棟，設有多間專業教學研究實驗室與系所辦公室，提供師生學習與科研環境。"),
            CustomPoint(LatLng(22.7384658854431, 121.06639817640176), "理工學院B棟入口", "B 棟主要集合與資訊科技、綠能科技、應用數學等理工相關科系的教學與研究空間，教學設施與系辦公室齊備，有利於跨領域合作與科技應用課程的推動。"),
            CustomPoint(LatLng(22.738103481392233, 121.06599785671906), "理工學院廣場", "距離學餐最近的入口，同時可以進熱AB棟。"),
            CustomPoint(LatLng(22.73773241822817, 121.06606692360623), "理工學院C棟入口", "C 棟在理工學院三大棟之一，用於資訊類、管理類系所，較多的教學與研究用途，此外三樓以上還有實驗室和教授研究室。"),
            CustomPoint(LatLng(22.737859271065286, 121.06536003955178), "學餐入口(近理工)", "一宿餐廳提供多種大學餐選項滿足不同學生口味，並且全天開放。"),
            CustomPoint(LatLng(22.736882703556198, 121.06535922599979), "學餐入口(近7-11)", "一宿餐廳提供多種大學餐選項滿足不同學生口味，並且全天開放。"),
            CustomPoint(LatLng(22.73667924113307, 121.06540546477464), "7-11(東大門市)", "提供思樂冰、ATM、座位區、Ibon、ibon WiFi、現萃茶、現蒸地瓜等服務。"),
            CustomPoint(LatLng(22.73604427120851, 121.06556193495481), "第二學生宿舍", "第二學生宿舍新落成，房間採現代化設計，附有公共休息室。"),
            CustomPoint(LatLng(22.737447706638765, 121.06513631521499), "第一學生宿舍", "第一學生宿舍是校園內最早啟用的一棟，設有單人間與雙人間。"),
            CustomPoint(LatLng(22.733205795559435, 121.06580202471531), "操場", "校園操場，可供足球、慢跑與排球等活動使用。"),
            CustomPoint(LatLng(22.733471492854004, 121.06703820593026), "東大游泳池", "游泳池設有25公尺長的主池，並提供兒童戲水池、超音波池、SPA池等設施，適合各年齡層使用。\n" +
                    "\n" +
                    "開放時間：\n" +
                    "\n" +
                    "每學期第一至十七週： 每週一至週五，18:00 至 21:00。\n" +
                    "\n" +
                    "每年11月及12月： 僅開放週二至週四，18:00 至 21:00。\n" +
                    "\n" +
                    "國定假日、例假日、寒暑假： 開放時間及收費標準另定。"),
            CustomPoint(LatLng(22.733005518553, 121.06721074863363), "體育館", "體育館內有籃球場、羽球場與健身房，對外開放時段請參考公告。"),
            CustomPoint(LatLng(22.73611793378069, 121.06653276459784), "共同教學大樓", "共同教學大樓是一座多功能的教學設施，主要用於舉辦通識課程、共同必修課程及選修課程等。"),
            CustomPoint(LatLng(22.73650880319903, 121.06655617651063), "靜心書院入口(近理工)", "該書院結合了教室、會議室與休憩區，旨在為學生、教職員及外部來賓提供舒適的學習與生活環境。"),
            CustomPoint(LatLng(22.73651940864311, 121.06721064437424), "靜心書院入口(近圖書館)", "該書院結合了教室、會議室與休憩區，旨在為學生、教職員及外部來賓提供舒適的學習與生活環境。"),
            CustomPoint(LatLng(22.738700601981595, 121.06497484121572), "資源回收站", "校園資源回收站，提供紙張、塑膠、金屬等回收服務。"),
            CustomPoint(LatLng(22.73667908810683, 121.065407893042), "7-11", "校園門口的 7-11，方便師生隨時購買飲料與零食。"),
            CustomPoint(LatLng(22.733878454879942, 121.06840153239384), "籃球場", "室外籃球場，夜間有照明，適合休閒籃球活動。"),
            CustomPoint(LatLng(22.735963782832926, 121.06770378016085), "圖書館", "為全校師生提供豐富的學術資源與舒適的閱讀環境。圖書館設有多樣化的閱覽區、電子資料庫、視聽設備及自習空間，也被稱為全球八度獨特圖書館之一。"),
            CustomPoint(LatLng(22.736834829795928, 121.06865836893749), "行政大樓", "國立臺東大學的行政服務大樓位於校本部，是學校行政運作的核心建築。該大樓內設有多個行政單位，包括秘書室、總務處、教務處、學生事務處等，負責學校日常行政管理與服務。"),
            CustomPoint(LatLng(22.73583698679327, 121.0682959798721), "颯德固講堂", "為於圖書館旁的一個長廊，主要拿來邀請著名講師來演講。"),
            CustomPoint(LatLng(22.73954853435361, 121.06738946442893), "師範學院A棟", "主要作為師範學院的行政與教學中心，包含院辦公室、教師研究室及會議室等，提供師生辦公與學術交流的空間。"),
            CustomPoint(LatLng(22.73916983421318, 121.06759949777546), "師範學院B棟", "B 棟內的「淑真講堂」為大型教學與活動空間，適合舉辦講座、研討會等。"),
            CustomPoint(LatLng(22.738813950809405, 121.06710692651174), "師範學院C棟", "C 棟主要用於體育與休閒相關課程的教學，設有體育系教室及相關設施支援學生的實習與學習需求。"),
            CustomPoint(LatLng(22.73863578654702, 121.06753201693554), "淑貞講堂", "淑貞講堂常舉辦演講與表演活動。"),
            CustomPoint(LatLng(22.737955956237787, 121.06842922214723), "演藝廳", "是學校內主要的表演與活動場地，適合舉辦音樂會、戲劇、講座等文化與學術活動其座位數約有300席。"),
            CustomPoint(LatLng(22.738135710196378, 121.06869042365535), "人文學院(近演藝廳)", "是學校的核心學術單位之一，致力於培養學生的語言能力、文化素養、批判思維與跨文化理解。學院內設有多個系所，涵蓋中文、外語、歷史、哲學等領域，並積極推動國際交流與跨領域合作。"),
            CustomPoint(LatLng(22.73762494117075, 121.06907164188596), "人文學院(近大門)", "是學校的核心學術單位之一，致力於培養學生的語言能力、文化素養、批判思維與跨文化理解。學院內設有多個系所，涵蓋中文、外語、歷史、哲學等領域，並積極推動國際交流與跨領域合作。")
        )

        // 將教室與校園地點合併，教室放前面以便在搜尋中先顯示
        seClassrooms + campusPoints
    }

    // 5. 申請定位權限
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> permissionGranted = granted }
    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
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
                                onSuccess = { points -> isRouting = false; routePoints = points },
                                onTime = { timeText -> travelTimeText = timeText },
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
        if (permissionGranted) fusedClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
    }
    DisposableEffect(Unit) { onDispose { fusedClient.removeLocationUpdates(locationCallback) } }

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
                        scope.launch { delay(3000); errorMsg = null }
                    }
                }
                .addOnFailureListener {
                    errorMsg = "定位失敗：${it.message}"
                    scope.launch { delay(3000); errorMsg = null }
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
                destination = latLng
                routePoints = emptyList()
                lastRerouteLoc = currentLoc
                travelTimeText = null
                drawRoute(
                    origin = currentLoc,
                    dest = latLng,
                    onStart = { isRouting = true },
                    onSuccess = { points -> isRouting = false; routePoints = points },
                    onTime = { timeText -> travelTimeText = timeText },
                    onError = { isRouting = false; errorMsg = it; scope.launch { delay(3000); errorMsg = null } }
                )
                // 點地圖也順便關掉 selectedPoint（避免資訊卡蓋住）
                selectedPoint = null
            }
        ) {
            // A. 顯示「你的位置」Marker
            currentLoc?.let {
                Marker(state = MarkerState(it), title = "你的位置", icon = getResizedBitmapDescriptor(context, R.drawable.marker, 120, 120))
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
                Marker(state = MarkerState(custom.location), title = custom.name, icon = getResizedBitmapDescriptor(context, R.drawable.marker, 80, 80), onClick = {
                    selectedPoint = custom
                    false
                })
            }

            // D. 畫三段 Polyline：灰色虛線 + 藍色實線 + 灰色虛線
            if (currentLoc != null && routePoints.isNotEmpty()) {
                val firstOnRoad = routePoints.first()
                Polyline(points = listOf(currentLoc!!, firstOnRoad), width = 6f, color = Color.Gray, pattern = dashPattern)
            }
            if (routePoints.isNotEmpty()) Polyline(points = routePoints, width = 12f, color = Color.Blue)
            if (routePoints.isNotEmpty() && destination != null) {
                val lastOnRoad = routePoints.last()
                Polyline(points = listOf(lastOnRoad, destination!!), width = 6f, color = Color.Gray, pattern = dashPattern)
            }
        }

        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {

            // 搜尋卡（加上 zIndex 確保置頂）
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .zIndex(2f),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF0F5).copy(alpha = 0.95f))
            ) {
                Column(modifier = Modifier.padding(8.dp)) {

                    // 標題 + 箭頭
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(text = if (searchExpanded) "搜尋目的地" else "點擊展開搜尋", style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.weight(1f))
                        IconButton(onClick = { searchExpanded = !searchExpanded }) {
                            Icon(imageVector = if (searchExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, contentDescription = "收合搜尋欄")
                        }
                    }

                    // 內容動畫
                    AnimatedVisibility(
                        visible = searchExpanded,
                        enter = expandVertically(animationSpec = tween(300)),
                        exit = shrinkVertically(animationSpec = tween(300))
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {

                            // ---- 起點欄位 ----
                            var startFieldWidth by remember { mutableStateOf(0) }
                            Box {
                                OutlinedTextField(
                                    value = startText,
                                    onValueChange = {
                                        startText = it
                                        startSelection = null
                                        startExpanded = it.isNotBlank()
                                        customPoints.firstOrNull { cp -> cp.name.equals(it, true) }?.let { cp -> startSelection = cp.location }
                                    },
                                    placeholder = { Text("起點") },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(54.dp)
                                        .clickable { startExpanded = true }
                                        .onGloballyPositioned { coordinates -> startFieldWidth = coordinates.size.width },
                                    singleLine = true,
                                    leadingIcon = { Icon(Icons.Default.Place, contentDescription = "起點", tint = Color(0xFFFF69B4)) },
                                    shape = RoundedCornerShape(16.dp)
                                )

                                // 替換 DropdownMenu -> 直接在下方顯示可捲動清單
                                if (startExpanded) {
                                    Card(
                                        modifier = Modifier
                                            .width(with(LocalDensity.current) { startFieldWidth.toDp() })
                                            .padding(top = 55.dp)
                                            .heightIn(max = 200.dp)   // 當建議過多時就會捲動，不會超過高度
                                            .zIndex(3f),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF0F5))
                                    ) {
                                        val startSuggestions = if (startText.isBlank()) customPoints else customPoints.filter { it.name.contains(startText, true) }
                                        LazyColumn {
                                            item {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clickable {
                                                            startText = "目前位置"
                                                            startSelection = null
                                                            startExpanded = false
                                                        }
                                                        .padding(horizontal = 12.dp, vertical = 10.dp)
                                                ) {
                                                    Text(text = "目前位置")
                                                }
                                            }
                                            items(startSuggestions) { cp ->
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clickable {
                                                            startText = cp.name
                                                            startSelection = cp.location
                                                            startExpanded = false
                                                        }
                                                        .padding(horizontal = 12.dp, vertical = 10.dp)
                                                ) {
                                                    Text(text = cp.name)
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            // ---- 目的地欄位 ----
                            var destFieldWidth by remember { mutableStateOf(0) }
                            Box {
                                OutlinedTextField(
                                    value = destText,
                                    onValueChange = {
                                        destText = it
                                        destSelection = null
                                        destExpanded = true // 總是顯示建議列表
                                        customPoints.firstOrNull { cp -> cp.name.equals(it, true) }?.let { cp -> destSelection = cp.location }
                                    },
                                    placeholder = { Text("輸入教室編號或地點名稱（如：SEC101、體育館）") },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(54.dp)
                                        .clickable { destExpanded = true }
                                        .onGloballyPositioned { coordinates -> destFieldWidth = coordinates.size.width },
                                    singleLine = true,
                                    leadingIcon = { Icon(Icons.Default.Place, contentDescription = "目的地", tint = Color(0xFF87CEFA)) },
                                    shape = RoundedCornerShape(16.dp)
                                )

                                // 替換 DropdownMenu -> 直接在下方顯示可捲動清單
                                if (destExpanded) {
                                    Card(
                                        modifier = Modifier
                                            .width(with(LocalDensity.current) { destFieldWidth.toDp() })
                                            .padding(top = 55.dp)
                                            .heightIn(max = 300.dp)
                                            .zIndex(3f),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFF87CEFA).copy(alpha = 0.15f))
                                    ) {
                                        // 分離教室和其他地點
                                        val locations = customPoints.filter { !it.name.startsWith("se", true) }
                                        val classrooms = customPoints.filter { it.name.startsWith("se", true) }

                                        // 過濾搜尋結果
                                        val filteredLocations = if (destText.isBlank()) locations else {
                                            locations.filter { 
                                                it.name.contains(destText, true) || 
                                                it.description.contains(destText, true) 
                                            }
                                        }
                                        val filteredClassrooms = if (destText.isBlank()) classrooms else {
                                            classrooms.filter {
                                                it.name.contains(destText, true) ||
                                                it.description.contains(destText, true)
                                            }
                                        }

                                        LazyColumn {
                                            // 顯示教室
                                            if (filteredClassrooms.isNotEmpty()) {
                                                item {
                                                    Text(
                                                        text = "教室清單",
                                                        style = MaterialTheme.typography.labelMedium,
                                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                                        color = Color.Gray
                                                    )
                                                }
                                                items(filteredClassrooms) { cp ->
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .clickable {
                                                                destText = cp.name.uppercase()
                                                                destSelection = cp.location
                                                                destExpanded = false
                                                            }
                                                            .padding(horizontal = 12.dp, vertical = 10.dp)
                                                    ) {
                                                        Text(
                                                            text = cp.name.uppercase(),
                                                            style = MaterialTheme.typography.bodyMedium
                                                        )
                                                    }
                                                }
                                            }

                                            // 顯示其他地點
                                            if (filteredLocations.isNotEmpty()) {
                                                item {
                                                    if (filteredClassrooms.isNotEmpty()) {
                                                        Divider(
                                                            modifier = Modifier.padding(vertical = 8.dp),
                                                            color = Color.Gray.copy(alpha = 0.3f)
                                                        )
                                                    }
                                                    Text(
                                                        text = "校園地點",
                                                        style = MaterialTheme.typography.labelMedium,
                                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                                        color = Color.Gray
                                                    )
                                                }
                                                items(filteredLocations) { cp ->
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .clickable {
                                                                destText = cp.name
                                                                destSelection = cp.location
                                                                destExpanded = false
                                                            }
                                                            .padding(horizontal = 12.dp, vertical = 10.dp)
                                                    ) {
                                                        Text(
                                                            text = cp.name,
                                                            style = MaterialTheme.typography.bodyMedium
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // 清除與導航按鈕
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                TextButton(
                                    onClick = {
                                        startText = ""
                                        destText = ""
                                        startSelection = null
                                        destSelection = null
                                        destination = null
                                        routePoints = emptyList()
                                        travelTimeText = null
                                    },
                                    modifier = Modifier.height(35.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                ) { Text("清除", style = MaterialTheme.typography.labelSmall) }

                                Spacer(modifier = Modifier.width(8.dp))

                                Button(
                                    onClick = {
                                        val originLatLng: LatLng? = when {
                                            startSelection != null -> startSelection
                                            startText.equals("目前位置", true) -> currentLoc
                                            startText.isNotBlank() -> customPoints.firstOrNull { it.name.equals(startText, true) }?.location
                                            else -> currentLoc
                                        }

                                        val destLatLng: LatLng? = destSelection ?: customPoints.firstOrNull { it.name.equals(destText, true) }?.location

                                        if (originLatLng == null) {
                                            errorMsg = "找不到起點位置（請確認輸入或開啟定位）"
                                            scope.launch { delay(3000); errorMsg = null }
                                            return@Button
                                        }
                                        if (destLatLng == null) {
                                            errorMsg = "請選擇有效的目的地（請從建議列表選擇）"
                                            scope.launch { delay(3000); errorMsg = null }
                                            return@Button
                                        }

                                        destination = destLatLng
                                        routePoints = emptyList()
                                        lastRerouteLoc = originLatLng
                                        travelTimeText = null
                                        drawRoute(
                                            origin = originLatLng, dest = destLatLng,
                                            onStart = { isRouting = true },
                                            onSuccess = { points -> isRouting = false; routePoints = points },
                                            onTime = { timeText -> travelTimeText = timeText },
                                            onError = { isRouting = false; errorMsg = it; scope.launch { delay(3000); errorMsg = null } }
                                        )

                                        cameraState.move(CameraUpdateFactory.newLatLng(originLatLng))
                                    },
                                    modifier = Modifier.height(35.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                ) { Text("導航", style = MaterialTheme.typography.labelSmall) }
                            }
                        }
                    }
                }
            }
        }



        // 顯示「路線計算中」圓形指示器
        if (isRouting) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center).size(48.dp).background(Color.White.copy(alpha = 0.6f), shape = MaterialTheme.shapes.small).padding(8.dp))
        }

        // 顯示費時文字於底部
        travelTimeText?.let { text ->
            Box(modifier = Modifier.fillMaxWidth().padding(12.dp).background(Color.White.copy(alpha = 0.8f), shape = MaterialTheme.shapes.medium).align(Alignment.BottomCenter).padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(text = "預計花費：$text", style = MaterialTheme.typography.bodyMedium, color = Color.DarkGray)
            }
        }

        // 顯示錯誤訊息（三秒後自動消失）
        AnimatedVisibility(visible = errorMsg != null, enter = fadeIn(tween(300)) + slideInVertically(initialOffsetY = { -100 }, animationSpec = tween(300)), exit = fadeOut(tween(300)) + slideOutVertically(targetOffsetY = { -100 }, animationSpec = tween(300))) {
            Box(modifier = Modifier.fillMaxWidth().wrapContentSize(Alignment.TopCenter).padding(top = 16.dp)) {
                Box(modifier = Modifier.background(color = Color(0xFFFFCDD2), shape = MaterialTheme.shapes.large).padding(horizontal = 20.dp, vertical = 12.dp)) {
                    Text(text = errorMsg ?: "", color = Color.DarkGray, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                }
            }
        }

        // 9. 顯示自訂介紹卡片（改為顯示在下方）
        AnimatedVisibility(
            visible = selectedPoint != null,
            enter = slideInVertically(initialOffsetY = { it }, animationSpec = tween(300)) + fadeIn(tween(200)),
            exit = slideOutVertically(targetOffsetY = { it }, animationSpec = tween(300)) + fadeOut(tween(200))
        ) {
            // 把 Card 放在底部，並確保不會被地圖其他 overlay 完全覆蓋
            Box(modifier = Modifier.fillMaxSize()) {
                selectedPoint?.let { point ->
                    Card(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 24.dp)
                            .fillMaxWidth(0.94f)
                            .shadow(8.dp, shape = RoundedCornerShape(12.dp))
                            .zIndex(2f),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(text = point.name, style = MaterialTheme.typography.titleMedium)
                                Spacer(modifier = Modifier.weight(1f))
                                TextButton(onClick = { selectedPoint = null }) { Text("關閉") }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(text = point.description, style = MaterialTheme.typography.bodyMedium)
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                                TextButton(onClick = { selectedPoint = null }) { Text("取消") }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(onClick = {
                                    // 導航到該地點
                                    destination = point.location
                                    routePoints = emptyList()
                                    lastRerouteLoc = currentLoc
                                    travelTimeText = null
                                    drawRoute(origin = currentLoc, dest = point.location,
                                        onStart = { isRouting = true },
                                        onSuccess = { points -> isRouting = false; routePoints = points },
                                        onTime = { timeText -> travelTimeText = timeText },
                                        onError = { isRouting = false; errorMsg = it; scope.launch { delay(3000); errorMsg = null } })
                                    selectedPoint = null
                                    cameraState.move(CameraUpdateFactory.newLatLng(point.location))
                                }) { Text("導航") }
                            }
                        }
                    }
                }
            }
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
fun getResizedBitmapDescriptor(context: Context, resId: Int, width: Int, height: Int): com.google.android.gms.maps.model.BitmapDescriptor {
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


// 需要 import com.example.project250311.Map.data.SEClassrooms
    val allPoints = com.example.project250311.Map.data.SEClassrooms.allClassrooms

// 檢查目的地是否在理工學院內
// 然後在 allPoints (而不是 customPoints) 上執行 .find
    val isDestInSE = allPoints.find { it.location == dest }?.name?.startsWith("SE") == true

// 如果目的地在理工學院內，找到最近的出入口作為中間點
    val waypoint = if (isDestInSE) {
        val destCode = allPoints.find { it.location == dest }?.name ?: ""
        SEEntrances.getNearestEntrance(destCode) // 假設 SEEntrances 是可存取的
    } else null

    // 如果有中間點，先導航到中間點
    if (waypoint != null) {
        val o = "${origin.latitude},${origin.longitude}"
        val w = "${waypoint.location.latitude},${waypoint.location.longitude}"
        val d = "${dest.latitude},${dest.longitude}"
        
        // 先取得到出入口的路線
        RetrofitInstance.api.getDirections(
            origin = o, 
            destination = w, 
            mode = "walking", 
            apiKey = "AIzaSyDj1CTmLJMsvCTRwwVJrCFHp6Cqt7wVKp8"
        ).enqueue(object : Callback<com.example.project250311.Map.model.DirectionsResponse> {
            override fun onResponse(
                call: Call<com.example.project250311.Map.model.DirectionsResponse>,
                response: Response<com.example.project250311.Map.model.DirectionsResponse>
            ) {
                if (response.isSuccessful) {
                    val body = response.body()
                    val route1 = body?.routes?.firstOrNull()
                    val points1 = route1?.overview_polyline?.points
                    val duration1 = route1?.legs?.firstOrNull()?.duration?.text ?: "未知時間"

                    // 再取得從出入口到目的地的路線
                    RetrofitInstance.api.getDirections(
                        origin = w,
                        destination = d,
                        mode = "walking",
                        apiKey = "AIzaSyDj1CTmLJMsvCTRwwVJrCFHp6Cqt7wVKp8"
                    ).enqueue(object : Callback<com.example.project250311.Map.model.DirectionsResponse> {
                        override fun onResponse(
                            call: Call<com.example.project250311.Map.model.DirectionsResponse>,
                            response: Response<com.example.project250311.Map.model.DirectionsResponse>
                        ) {
                            if (response.isSuccessful) {
                                val body = response.body()
                                val route2 = body?.routes?.firstOrNull()
                                val points2 = route2?.overview_polyline?.points
                                val duration2 = route2?.legs?.firstOrNull()?.duration?.text ?: "未知時間"

                                // 合併兩段路線
                                if (!points1.isNullOrEmpty() && !points2.isNullOrEmpty()) {
                                    val combinedPoints = PolylineUtils.decodePolyline(points1) + 
                                                       PolylineUtils.decodePolyline(points2)
                                    onSuccess(combinedPoints)
                                    // 合併時間顯示
                                    onTime("第一段：$duration1，第二段：$duration2")
                                } else {
                                    onError("無法生成完整路線")
                                }
                            } else {
                                onError("第二段路線規劃失敗")
                            }
                        }
                        override fun onFailure(call: Call<com.example.project250311.Map.model.DirectionsResponse>, t: Throwable) {
                            onError("第二段路線規劃失敗：${t.message}")
                        }
                    })
                } else {
                    onError("第一段路線規劃失敗")
                }
            }
            override fun onFailure(call: Call<com.example.project250311.Map.model.DirectionsResponse>, t: Throwable) {
                onError("第一段路線規劃失敗：${t.message}")
            }
        })
    } else {
        // 如果目的地不在理工學院內，直接規劃路線
        val o = "${origin.latitude},${origin.longitude}"
        val d = "${dest.latitude},${dest.longitude}"
        RetrofitInstance.api.getDirections(
            origin = o, 
            destination = d, 
            mode = "walking", 
            apiKey = "AIzaSyDj1CTmLJMsvCTRwwVJrCFHp6Cqt7wVKp8"
        ).enqueue(object : Callback<com.example.project250311.Map.model.DirectionsResponse> {
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

            override fun onFailure(call: Call<com.example.project250311.Map.model.DirectionsResponse>, t: Throwable) {
                onError("網路錯誤：${t.localizedMessage}")
            }
        })
    }
}
