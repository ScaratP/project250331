package com.example.project250311.Game

import android.content.Context
import android.graphics.PointF
import android.location.Location
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.example.project250311.Map.IndoorMap.IndoorLocationView
import com.example.project250311.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.math.sqrt
import com.example.project250311.Game.GameManager
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.filled.*
import android.app.Application
import androidx.lifecycle.AndroidViewModel

fun getMapDrawableResId(groupName: String): Int {
    return when (groupName) {
        "se1" -> R.drawable.se1
        "se2" -> R.drawable.se2
        "se3" -> R.drawable.se3
        "sea4" -> R.drawable.sea4
        "sea5" -> R.drawable.sea5
        "seb4" -> R.drawable.seb4
        "sec4" -> R.drawable.sec4
        "sec5" -> R.drawable.sec5
        else -> R.drawable.se1
    }
}

// --- Data Models ---
data class CheckpointInfo(
    val id: String,
    val name: String,
    val targetMapGroup: String,
    val targetPercentageX: Float,
    val targetPercentageY: Float,
    val requiredQrCodeContent: String,
    val stampImageRes: Int? = null
)

data class StampProgress(
    val checkpointId: String,
    var isCollected: Boolean = false,
    var colorDepth: Float = 0.0f,
    var collectedTime: Long = 0L // (★) 新增：記錄收集時間
)

data class UiCheckpointState(
    val checkpoint: CheckpointInfo,
    val progress: StampProgress,
    val distance: Float,
    val status: CheckpointStatus
)

enum class CheckpointStatus {
    COLLECTED,
    TOO_FAR,
    IN_RANGE
}

data class Mission(
    val id: String,
    val title: String,
    val description: String,
    val icon: ImageVector,
    val route: String,
    val isCompleted: Boolean = false
)

// =================================================================================
//    ViewModel
// =================================================================================

class GameViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs by lazy {
        getApplication<Application>().getSharedPreferences("game_local_data", Context.MODE_PRIVATE)
    }

    // (★) 定義集點資料 (建議之後可以移到 Repository 或從 JSON 讀取)
    private val _checkpoints = MutableStateFlow<List<CheckpointInfo>>(
        listOf(
            CheckpointInfo("sec511", "SEC511 教室", "sec5", 28.41f, 59.07f, "NTTU_SEC_511", stampImageRes = R.drawable.sec5),
//            CheckpointInfo("sec304", "SEC304 教室", "se3", 22.73f, 21.12f, "NTTU_SEC_304", stampImageRes = R.drawable.sec5),
//            CheckpointInfo("test123", "測試點 (A棟入口)", "se1", 46.30f, 56.27f, "TEST_QR"),
        )
    )

    private val _missions = MutableStateFlow(
        listOf(
            Mission("1", "查看今日課表", "確認今天的上課地點", Icons.Default.CalendarToday, "schedule"),
//            Mission("2", "新增課程筆記", "為一門課程寫下筆記", Icons.Default.Edit, "note_edit"),
            Mission("2", "學餐轉盤", "不知道吃什麼？轉一下！", Icons.Default.Restaurant, "food"),
            Mission("3", "前往理工學院", "移動到指定地點並掃描", Icons.Default.Map, "map_game")
        )
    )
    val missions = _missions.asStateFlow()

    // (★) 新增：完成任務的函式
    fun completeMission(missionId: String) {
        _missions.value = _missions.value.map {
            if (it.id == missionId) it.copy(isCompleted = true) else it
        }
        saveLocalProgress()
    }

    private val _progress = mutableStateMapOf<String, StampProgress>()
    private val _currentLocation = MutableStateFlow<Location?>(null)

    // (★) UI 狀態
    private val _uiState = MutableStateFlow<List<UiCheckpointState>>(emptyList())
    val uiState: StateFlow<List<UiCheckpointState>> = _uiState.asStateFlow()

    private val _currentMapGroup = MutableStateFlow<String?>(null)
    val currentMapGroup: StateFlow<String?> = _currentMapGroup.asStateFlow()

    private val _currentPercentage = MutableStateFlow<PointF?>(null)
    val currentPercentage: StateFlow<PointF?> = _currentPercentage.asStateFlow()

    private val _currentMapCheckpoints = MutableStateFlow<List<CheckpointInfo>>(emptyList())
    val currentMapCheckpoints: StateFlow<List<CheckpointInfo>> = _currentMapCheckpoints.asStateFlow()

    // (★) 觸發距離設為 15 公尺 (比較合理，100公尺有點太遠容易誤判)
    private val TRIGGER_DISTANCE_METERS = 15.0f

    init {
        loadLocalProgress()

        viewModelScope.launch {
            combine(_checkpoints, _currentMapGroup, _currentPercentage) { checkpoints, mapGroup, percentage ->
                updateUiState(checkpoints, mapGroup, percentage)
                if (mapGroup != null) {
                    _currentMapCheckpoints.value = checkpoints.filter { it.targetMapGroup == mapGroup }
                } else {
                    _currentMapCheckpoints.value = emptyList()
                }
            }.collect {}
        }
    }

    private fun loadLocalProgress() {
        // 讀取已收集的集點 ID
        val collectedIds = prefs.getStringSet("collected_stamps", emptySet()) ?: emptySet()
        collectedIds.forEach { id ->
            val progress = _progress.getOrPut(id) { StampProgress(id) }
            progress.isCollected = true
            progress.colorDepth = 1.0f // 恢復為已蓋章狀態
        }

        // 讀取已完成的任務 ID
        val completedMissionIds = prefs.getStringSet("completed_missions", emptySet()) ?: emptySet()
        _missions.value = _missions.value.map {
            if (completedMissionIds.contains(it.id)) it.copy(isCompleted = true) else it
        }
    }

    private fun saveLocalProgress() {
        // 整理目前的資料
        val collectedIds = _progress.filter { it.value.isCollected }.map { it.key }.toSet()
        val completedMissionIds = _missions.value.filter { it.isCompleted }.map { it.id }.toSet()

        // 寫入手機
        prefs.edit()
            .putStringSet("collected_stamps", collectedIds)
            .putStringSet("completed_missions", completedMissionIds)
            .apply()
    }



    private fun updateUiState(
        checkpoints: List<CheckpointInfo>,
        currentMapGroup: String?,
        currentPercentage: PointF?
    ) {
        _uiState.value = checkpoints.map { checkpoint ->
            val progress = _progress.getOrPut(checkpoint.id) { StampProgress(checkpoint.id) }

            // 計算距離 (簡單的歐幾里得距離估算，僅供參考)
            // 注意：百分比距離轉公尺需要地圖比例尺，這裡先用百分比差異作為 "相對距離"
            // 實務上建議：如果兩點在同一張圖，直接算百分比距離，若 < 5% 則視為接近
            val distance = if (currentMapGroup == checkpoint.targetMapGroup && currentPercentage != null) {
                val dx = currentPercentage.x - checkpoint.targetPercentageX
                val dy = currentPercentage.y - checkpoint.targetPercentageY
                // 假設地圖寬度約 50米 (這只是估算，用於顯示 UI)
                sqrt(dx * dx + dy * dy) // 這是百分比距離
            } else {
                Float.MAX_VALUE
            }

            // (★) 判定邏輯修正：使用百分比距離閾值 (例如 10%)
            // 假設地圖長寬 100單位，距離 10單位約為有效範圍
            val isNearby = distance < 15.0f

            val status = when {
                progress.isCollected -> CheckpointStatus.COLLECTED
                isNearby -> CheckpointStatus.IN_RANGE
                else -> CheckpointStatus.TOO_FAR
            }

            // 如果 distance 是 MAX_VALUE，顯示時可以過濾掉
            UiCheckpointState(checkpoint, progress, distance, status)
        }
    }

    // --- 功能函式 ---

    /** 當掃描 QR Code 成功時呼叫 */
    fun onQrCodeScanned(context: Context, checkpointId: String, scannedContent: String): Boolean {
        val checkpoint = _checkpoints.value.find { it.id == checkpointId }

        // 1. 驗證內容
        val isValid = (checkpoint != null && checkpoint.requiredQrCodeContent == scannedContent)

        // 2. (★) 上傳掃描紀錄到 Firebase (無論成功失敗)
        GameManager.logEvent(context, "qrcode_scan", mapOf(
            "checkpoint_id" to checkpointId,
            "scanned_content" to scannedContent,
            "is_valid" to isValid,
            "location_map" to (_currentMapGroup.value ?: "unknown"),
            "location_x" to (_currentPercentage.value?.x ?: 0f),
            "location_y" to (_currentPercentage.value?.y ?: 0f)
        ))

        return isValid
    }

    /** 當蓋章動畫完成後呼叫 */
    fun onStampSaved(context: Context, checkpointId: String, depth: Float) {
        val progress = _progress.getOrPut(checkpointId) { StampProgress(checkpointId) }

        val logData = mapOf(
            "checkpoint_id" to checkpointId,
            "stamp_depth" to depth,
            "timestamp" to System.currentTimeMillis(),
            // 這裡就是你要的「蓋章當下模型輸出座標」
            "predicted_map" to (_currentMapGroup.value ?: "unknown"),
            "predicted_x" to (_currentPercentage.value?.x ?: 0f),
            "predicted_y" to (_currentPercentage.value?.y ?: 0f),
            // 如果有經緯度也可以記
            // "lat" to ..., "lon" to ...
        )

        // 上傳到 Firebase
        GameManager.logEvent(context, "stamp_collected_with_loc", logData)

        if (!progress.isCollected) { // 避免重複紀錄
            progress.isCollected = true
            progress.colorDepth = depth
            progress.collectedTime = System.currentTimeMillis()

            // 1. 更新 UI
            updateUiState(_checkpoints.value, _currentMapGroup.value, _currentPercentage.value)

            if (!missions.value.find { it.id == "3" }?.isCompleted!!) {
                completeMission("3")
            }

            // 2. (★) 上傳「蓋章成功」事件到 Firebase
            GameManager.logEvent(context, "stamp_collected", mapOf(
                "checkpoint_id" to checkpointId,
                "depth" to depth
            ))

            // 3. (★) 檢查是否全部收集完成
            checkAllCompleted(context)
            saveLocalProgress()
        }


    }

    // (★) 檢查遊戲進度
    private fun checkAllCompleted(context: Context) {
        val total = _checkpoints.value.size
        val collected = _progress.count { it.value.isCollected }

        if (collected == total) {
            // 全部完成！
            GameManager.logEvent(context, "game_all_cleared")
            // 這裡可以發送一個 SharedFlow 事件通知 UI 彈出恭喜視窗
        }
    }

    fun getCheckpoint(checkpointId: String): CheckpointInfo? = _checkpoints.value.find { it.id == checkpointId }

    fun getStampProgress(checkpointId: String): StampProgress = _progress.getOrPut(checkpointId) { StampProgress(checkpointId) }

    // (★) 這兩個函式是用來從外部 (MainActivity) 接收定位資料的
    fun updateRealLocation(newLocation: Location) { _currentLocation.value = newLocation }
    fun updateVisualState(group: String?, percentage: PointF?) {
        _currentMapGroup.value = group
        _currentPercentage.value = percentage
    }


}

// =================================================================================
//    UI Composable
// =================================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameScreen(
    navController: NavController,
    viewModel: GameViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    val currentMapGroup by viewModel.currentMapGroup.collectAsState()
    val currentPercentage by viewModel.currentPercentage.collectAsState()
    val currentMapCheckpoints by viewModel.currentMapCheckpoints.collectAsState()

    val context = LocalContext.current
    val primaryColor = MaterialTheme.colorScheme.primary
    val accuracyColor = primaryColor.copy(alpha = 0.3f)

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("校園集點地圖", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            // --- 地圖區域 ---
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(4f / 3f), // 保持 4:3 比例
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                if (currentMapGroup != null) {
                    val mapImageRes = getMapDrawableResId(currentMapGroup!!)
                    val checkpointPoints = remember(currentMapCheckpoints) {
                        currentMapCheckpoints.map { PointF(it.targetPercentageX, it.targetPercentageY) }
                    }

                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            IndoorLocationView(ctx).apply {
                                maxZoom = 10f
                                minZoom = 1f // 鎖定最小縮放
                            }
                        },
                        update = { view ->
                            view.setImageResource(mapImageRes)
                            view.predictedPercentage = currentPercentage
                            view.checkpointLocations = checkpointPoints
                            view.setLocationColors(
                                primaryColor = primaryColor.toArgb(),
                                accuracyColor = accuracyColor.toArgb(),
                                centerColor = android.graphics.Color.WHITE
                            )
                        }
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("正在定位中...", color = Color.Gray)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                "附近的集點任務",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(8.dp))

            // --- 集點列表 ---
            if (uiState.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("目前沒有集點任務", color = Color.Gray)
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // 根據狀態排序：可收集 > 已收集 > 太遠
                    val sortedList = uiState.sortedBy {
                        when(it.status) {
                            CheckpointStatus.IN_RANGE -> 0
                            CheckpointStatus.COLLECTED -> 1
                            CheckpointStatus.TOO_FAR -> 2
                        }
                    }

                    items(sortedList) { itemState ->
                        CheckpointItem(
                            item = itemState,
                            distanceUnit = "%", // 這裡目前是百分比距離
                            onClick = {
                                when (itemState.status) {
                                    CheckpointStatus.COLLECTED -> {
                                        // 查看已收集的印章
                                        navController.navigate("collect/${itemState.checkpoint.id}")
                                    }
                                    CheckpointStatus.IN_RANGE -> {
                                        // 開啟掃描
                                        navController.navigate("qrcode/${itemState.checkpoint.id}")
                                        // (★) 記錄點擊「開始掃描」
                                        GameManager.logEvent(context, "start_scan_click", mapOf("checkpoint" to itemState.checkpoint.name))
                                    }
                                    CheckpointStatus.TOO_FAR -> {
                                        Toast.makeText(context, "請移動到 ${itemState.checkpoint.name} 附近再試", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CheckpointItem(
    item: UiCheckpointState,
    distanceUnit: String,
    onClick: () -> Unit
) {
    // 即使太遠也可以點擊，只是會跳 Toast 提示
    val isClickable = true

    val backgroundColor = when (item.status) {
        CheckpointStatus.COLLECTED -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        CheckpointStatus.IN_RANGE -> MaterialTheme.colorScheme.tertiaryContainer // 強調色，提示可互動
        CheckpointStatus.TOO_FAR -> MaterialTheme.colorScheme.surface
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon
            val icon = when (item.status) {
                CheckpointStatus.COLLECTED -> Icons.Filled.CheckCircle
                CheckpointStatus.IN_RANGE -> Icons.Filled.QrCodeScanner
                CheckpointStatus.TOO_FAR -> Icons.Filled.Lock
            }
            val iconTint = when (item.status) {
                CheckpointStatus.COLLECTED -> MaterialTheme.colorScheme.primary
                CheckpointStatus.IN_RANGE -> MaterialTheme.colorScheme.tertiary
                CheckpointStatus.TOO_FAR -> Color.Gray
            }

            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = iconTint
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.checkpoint.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (item.status == CheckpointStatus.TOO_FAR) Color.Gray else Color.Black
                )

                val infoText = when (item.status) {
                    CheckpointStatus.COLLECTED -> "已收集"
                    CheckpointStatus.IN_RANGE -> "點擊掃描 QR Code！"
                    CheckpointStatus.TOO_FAR -> {
                        if (item.distance == Float.MAX_VALUE) "位於不同樓層/區域"
                        else "距離約 ${item.distance.roundToInt()} $distanceUnit"
                    }
                }

                Text(
                    text = infoText,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (item.status == CheckpointStatus.IN_RANGE) MaterialTheme.colorScheme.tertiary else Color.Gray
                )
            }
        }
    }
}