package com.example.project250311.Map.IndoorMap

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.PointF
import android.location.Location
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.project250311.Map.IndoorMap.Database.GridCacheEntity
import com.example.project250311.Map.IndoorMap.Database.IndoorMapDatabase
import com.example.project250311.Map.IndoorMap.IndoorPathfinder.Grid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.min


data class AppPositionState(
    // 1. 給 GameViewModel 計算距離用
    val location: Location? = null,

    // 2. 給地圖顯示用 (e.g., "se1", "sec5")
    val mapGroupName: String? = null,

    // 3. 給地圖畫點用 (e.g., (25.4f, 50.1f))
    val mapPercentage: PointF? = null,

    // 4. (可選) 顯示錯誤訊息
    val error: String? = null
)

// --- (2.A) 載入「經緯度標準化」設定 (保持不變) ---
//輸出zscore座標回歸經緯度
data class NormalizationParams(
    val lo_mean: Double,
    val lo_std: Double,
    val la_mean: Double,
    val la_std: Double
)

fun loadNormalizationParams(context: Context): NormalizationParams? {
    return try {
        NormalizationParams(
            lo_mean = 121.06615002236796,
            lo_std = 0.0002641819191750892,
            la_mean = 22.738322384305718,
            la_std = 0.0003638876383337944
        ).also {
            Log.d("IndoorMapScreen", "成功載入經緯度標準化設定 (normalization_params.json)")
        }
    } catch (e: Exception) {
        Log.e("IndoorMapScreen", "載入 'normalization_params.json' 失敗!", e)
        null
    }
}

// --- (★ 2.B) (核心修正) 載入「經緯度 -> 百分比」轉換矩陣 ---
data class MatrixData(
    val matrix: List<List<Double>>,
    val origin: List<Double>
)

// (★) 手動映射：將 JSON 中的舊 Resource ID 字符串 連結到 GroupName
// (這是我們唯一的橋樑)
val jsonKeyToGroupNameMap = mapOf(
    "se1" to "se1",
    "se2" to "se2",
    "se3" to "se3",
    "sea4" to "sea4",
    "sea5" to "sea5",
    "seb4" to "seb4",
    "sec4" to "sec4",
    "sec5" to "sec5"
)

// (★) (Map 的 Key 現在是 GroupName, e.g., "se1", "sec5")
fun loadTransformationMatrices(context: Context): Map<String, MatrixData> {
    return try {
        val jsonString = context.assets.open("transformation_data.json")
            .bufferedReader()
            .use { it.readText() }
        val root = JSONObject(jsonString)
        val matricesRoot = root.getJSONObject("matrices")
        val originsRoot = root.getJSONObject("origins")

        // (★) Map 的 Key 是 GroupName (String)
        val matrixMap = mutableMapOf<String, MatrixData>()

        // (★) (★) (★)
        // (★) 核心修正：我們必須使用同一個 `jsonKeyToGroupNameMap` 來
        // (★) 遍歷 `matricesRoot` 和 `originsRoot`，確保它們配對
        // (★) (★) (★)

        jsonKeyToGroupNameMap.forEach { (resIdString, groupName) ->
            // (e.g., resIdString = "2131165302", groupName = "sec5")

            if (matricesRoot.has(resIdString) && originsRoot.has(resIdString)) {
                // 1. 解析 Matrix
                val matrixJson = matricesRoot.getJSONArray(resIdString)
                val matrix = List(matrixJson.length()) { i ->
                    val row = matrixJson.getJSONArray(i)
                    List(row.length()) { j -> row.getDouble(j) }
                }

                // 2. 解析 Origin
                val originJson = originsRoot.getJSONArray(resIdString)
                val origin = List(originJson.length()) { i -> originJson.getDouble(i) }

                // 3. (★) 使用 groupName (e.g., "sec5") 作為 Map 的 Key
                matrixMap[groupName] = MatrixData(matrix, origin)

            } else {
                Log.w("loadMatrices", "JSON 鍵 $resIdString 在 'matrices' 或 'origins' 中找不到，跳過 $groupName")
            }
        }

        Log.d("IndoorMapScreen", "成功載入 ${matrixMap.size} 個轉換矩陣 (並以 GroupName 重新索引)")
        matrixMap
    } catch (e: Exception) {
        Log.e("IndoorMapScreen", "載入 'transformation_data.json' 失敗!", e)
        emptyMap()
    }
}


// --- (★ 3) Hadnn2Model (★ 加入 Log 輸出) ---
class Hadnn2Model(private val context: Context) {

    private var tfLiteInterpreter: Interpreter? = null
    var isModelLoaded = false
        private set
    var bssidToIndexMap = mutableMapOf<String, Int>()
    private var mappedBssidCount = 0
    private val buildingOutputCount = 3
    private val floorOutputCount = 5

    data class PredictionResult(
        val scaled_lon: Float,
        val scaled_lat: Float,
        val buildingIndex: Int,
        val floorIndex: Int
    )

    private lateinit var outputBufferCoords: Array<FloatArray>
    private lateinit var outputBufferBuilding: Array<FloatArray>
    private lateinit var outputBufferFloor: Array<FloatArray>
    private lateinit var outputs: MutableMap<Int, Any>

    init {
        // (E) 載入 BSSID (保持不變)
        try {
            val inputStream = context.assets.open("bssid_mapping.csv")
            val reader = inputStream.bufferedReader()
            reader.readLine()
            var maxIndex = -1
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val parts = line?.split(",")
                if (parts != null && parts.size >= 3) {
                    val bssid = parts[1].trim()
                    val index = parts[2].trim().toIntOrNull() ?: continue
                    bssidToIndexMap[bssid] = index
                    if (index > maxIndex) maxIndex = index
                }
            }
            mappedBssidCount = 531 // (★) 來自 Logcat
            Log.d("Hadnn2Model", "BSSID 映射已載入, $mappedBssidCount features")
        } catch (e: Exception) {
            Log.e("Hadnn2Model", "載入 BSSID 映射出錯: ${e.message}")
        }
    }

    // (F) 載入 TFLite 檔案 (保持不變)
    @Throws(IOException::class)
    fun loadModelFile(modelFileName: String): MappedByteBuffer {
        val assetManager = context.assets
        val fileDescriptor = assetManager.openFd(modelFileName)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    // (G) 載入模型 (保持不變，使用 0=B, 1=C, 2=F)
    fun loadModel(): Boolean {
        if (isModelLoaded) return true
        val modelFileName = "model_signed.tflite"
        try {
            Log.d("Hadnn2Model", "正在載入 HADNN2 模型: $modelFileName")
            val modelBuffer = loadModelFile(modelFileName)
            tfLiteInterpreter = Interpreter(modelBuffer)

            val inputShape = tfLiteInterpreter!!.getInputTensor(0).shape()
            if (inputShape.size != 2 || inputShape[0] != 1 || inputShape[1] != mappedBssidCount) {
                Log.e("Hadnn2Model", "模型 $modelFileName 輸入 Shape 不符預期! " +
                        "Shape: ${inputShape.joinToString()} (Expected [1, $mappedBssidCount])")
                return false
            }

            // (★ 最終順序：0=Building, 1=Coords, 2=Floor)
            val outputBuildingShape = tfLiteInterpreter!!.getOutputTensor(0).shape()
            val outputCoordsShape = tfLiteInterpreter!!.getOutputTensor(1).shape()
            val outputFloorShape = tfLiteInterpreter!!.getOutputTensor(2).shape()

            if (outputBuildingShape.joinToString() != "1, $buildingOutputCount") {
                Log.e("Hadnn2Model", "輸出 0 (Building) Shape 不符: ${outputBuildingShape.joinToString()} (Expected [1, $buildingOutputCount])")
                return false
            }
            if (outputCoordsShape.joinToString() != "1, 2") {
                Log.e("Hadnn2Model", "輸出 1 (Coords) Shape 不符: ${outputCoordsShape.joinToString()} (Expected [1, 2])")
                return false
            }
            if (outputFloorShape.joinToString() != "1, $floorOutputCount") {
                Log.e("Hadnn2Model", "輸出 2 (Floor) Shape 不符: ${outputFloorShape.joinToString()} (Expected [1, $floorOutputCount])")
                return false
            }

            outputBufferCoords = Array(1) { FloatArray(2) }
            outputBufferBuilding = Array(1) { FloatArray(buildingOutputCount) }
            outputBufferFloor = Array(1) { FloatArray(floorOutputCount) }

            outputs = mutableMapOf(
                0 to outputBufferBuilding, // (★) 輸出 0: Building
                1 to outputBufferCoords,   // (★) 輸出 1: 座標
                2 to outputBufferFloor     // (★) 輸出 2: Floor
            )

            isModelLoaded = true
            Log.d("Hadnn2Model", "HADNN2 模型 $modelFileName 載入成功")
            return true

        } catch (e: Exception) {
            Log.e("Hadnn2Model", "載入 $modelFileName 出錯: ${e.message}")
            isModelLoaded = false
            tfLiteInterpreter = null
            return false
        }
    }

    // (H) 預測函數 (★ 加入 Log 輸出)
    fun predict(scanResults: List<ScanResult>): PredictionResult? {
        if (!isModelLoaded || tfLiteInterpreter == null || scanResults.isEmpty()) {
            Log.w("Hadnn2Model", "模型尚未準備好或掃描為空")
            return null
        }
        try {
            val wifiSignalMap = scanResults.associate { it.BSSID to it.level }
            val inputBuffer = prepareInputRssVector(wifiSignalMap)
            val inputs = arrayOf<Any>(inputBuffer)
            tfLiteInterpreter?.runForMultipleInputsOutputs(inputs, outputs)

            // (★) 讀取原始輸出
            val scaled_lon = outputBufferCoords[0][0]
            val scaled_lat = outputBufferCoords[0][1]
            val predictedBuildingIndex = outputBufferBuilding[0].indices.maxByOrNull { outputBufferBuilding[0][it] } ?: -1
            val predictedFloorIndex = outputBufferFloor[0].indices.maxByOrNull { outputBufferFloor[0][it] } ?: -1

            if (predictedBuildingIndex == -1 || predictedFloorIndex == -1) {
                Log.e("Hadnn2Model", "無法從 Building 或 Floor 輸出中找到最大值")
                return null
            }

            // (★) (★) (★)
            // (★) 使用者要求的 Log 輸出
            // (★) (★) (★)
            Log.d("Hadnn2Model", "--- (★) 原始模型輸出 (★) ---")
            Log.d("Hadnn2Model", "Building Index (b_idx): $predictedBuildingIndex")
            Log.d("Hadnn2Model", "Floor Index (f_idx): $predictedFloorIndex")
            Log.d("Hadnn2Model", "Scaled Lon (scaled_lon): $scaled_lon")
            Log.d("Hadnn2Model", "Scaled Lat (scaled_lat): $scaled_lat")
            Log.d("Hadnn2Model", "---------------------------------")

            return PredictionResult(scaled_lon, scaled_lat, predictedBuildingIndex, predictedFloorIndex)

        } catch (e: Exception) {
            Log.e("Hadnn2Model", "HADNN2 預測出錯: ${e.message}")
            return null
        }
    }

    /**
     * 對單筆的 Wi-Fi 掃描陣列 (inputArray) 執行 Z-Score 標準化 (axis=1)。
     * 這會複製 call_data.py 中 Normalize_data 的行為。
     */
    fun normalizeInputArray(inputArray: FloatArray): FloatArray {
        // 1. 計算 mean (平均值)
        // 注意：kotlin.Array.average() 返回 Double，我們轉回 Float
        val mean = inputArray.average().toFloat()

        // 2. 計算 std (標準差)
        var sumOfSquares = 0.0f
        for (value in inputArray) {
            sumOfSquares += (value - mean) * (value - mean)
        }
        var std = kotlin.math.sqrt(sumOfSquares / inputArray.size)

        // 3. 處理 std == 0 的情況 (避免除以零)
        // (★ 這一步就是 Python 裡的 std[std == 0] = 1.0 ★)
        if (std == 0.0f) {
            std = 1.0f
        }

        // 4. 執行 (value - mean) / std，產生新的標準化陣列
        val normalizedArray = FloatArray(inputArray.size)
        for (i in inputArray.indices) {
            normalizedArray[i] = (inputArray[i] - mean) / std
        }

        return normalizedArray
    }

    // (I) 準備輸入向量 (保持不變)
    fun prepareInputRssVector(wifiSignals: Map<String, Int>): ByteBuffer {
        val signalStrengths = FloatArray(mappedBssidCount) { 100f }
        wifiSignals.forEach { (bssid, level) ->
            val index = bssidToIndexMap[bssid]
            if (index != null && index < signalStrengths.size) {
                signalStrengths[index] = level.toFloat()
            }
        }
        // (B) (★ 關鍵修正 3: 呼叫你新增的 Z-Score 函數 ★)
        //     這會執行 (value - mean) / std
        val normalizedStrengths = normalizeInputArray(signalStrengths)

        // (C) (★ 關鍵修正 4: 將 "標準化後" 的資料填入 ByteBuffer)
        val inputBuffer = ByteBuffer.allocateDirect(mappedBssidCount * 4) // 4 bytes per float
        inputBuffer.order(ByteOrder.nativeOrder())

        // 迴圈遍歷 "標準化後" 的陣列
        normalizedStrengths.forEach { normalizedValue ->
            inputBuffer.putFloat(normalizedValue) // <-- 直接放入 Z-Score 後的值
        }

        inputBuffer.rewind()
        return inputBuffer
    }
}
// --- (Hadnn2Model 結束) ---

// (B/F 映射 保持不變)
private val buildingIndexMap = mapOf(
    0 to "sea",
    1 to "seb",
    2 to "sec"
)
private val floorIndexMap = mapOf(
    0 to 1,
    1 to 2,
    2 to 3,
    3 to 4,
    4 to 5
)
fun getGroupName(buildingIdx: Int, floorIdx: Int): String {
    // ... (程式碼保持不變) ...
    val building = buildingIndexMap[buildingIdx]
    val floor = floorIndexMap[floorIdx]

    if (building != null && floor != null) {
        val groupName = "$building$floor"
        return when (groupName) {
            "sea1", "seb1", "sec1" -> "se1"
            "sea2", "seb2", "sec2" -> "se2"
            "sea3", "seb3", "sec3" -> "se3"
            "sea4", "sea5", "seb4", "sec4", "sec5" -> groupName
            else -> "se1"
        }
    }
    Log.w("getGroupName", "未知的 Building($buildingIdx) 或 Floor($floorIdx) index")
    return "se1"
}

// (getScanResults, hasLocationPermission 保持不變)
fun hasLocationPermission(context: Context): Boolean {
    // ... (程式碼保持不變) ...
    return ContextCompat.checkSelfPermission(
        context, Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
        context, Manifest.permission.ACCESS_COARSE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
}
@RequiresPermission(Manifest.permission.ACCESS_WIFI_STATE)
@Suppress("DEPRECATION")
suspend fun getScanResults(context: Context): List<ScanResult> {
    if (!hasLocationPermission(context)) {
        Log.w("getScanResults", "缺少位置權限，無法掃描")
        return emptyList()
    }
    val wifiManager =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    return try {
        @Suppress("MissingPermission")
        wifiManager.startScan()
        delay(1000)
        wifiManager.scanResults
    } catch (se: SecurityException) {
        Log.e("getScanResults", "權限不足: ${se.message}")
        emptyList()
    } catch (e: Exception) {
        Log.e("getScanResults", "掃描時出錯: ${e.message}")
        emptyList()
    }
}

/**
 * 這是一個「共享的」ViewModel，獨立於 UI，在背景執行定位。
 * 它會成為 APP 中「唯一」的位置來源。
 */
class IndoorPositioningViewModel(application: Application) : AndroidViewModel(application) {

    private val context = getApplication<Application>().applicationContext

    // --- 1. 狀態 ---
    private val _positionState = MutableStateFlow(AppPositionState(error = "正在初始化..."))
    val positionState: StateFlow<AppPositionState> = _positionState.asStateFlow()

    private val _isLikelyIndoors = MutableStateFlow(false)
    val isLikelyIndoors: StateFlow<Boolean> = _isLikelyIndoors.asStateFlow()

    // 新增的「重資料」快取 ---
    // 這三個 StateFlow 就是用來取代 IndoorMapScreen 裡的 remember
    private val _imageBitmap = MutableStateFlow<ImageBitmap?>(null)
    val imageBitmap: StateFlow<ImageBitmap?> = _imageBitmap.asStateFlow()

    private val _grid = MutableStateFlow<Grid?>(null)
    val grid: StateFlow<Grid?> = _grid.asStateFlow()

    private val _overlay = MutableStateFlow<ImageBitmap?>(null)
    val overlay: StateFlow<ImageBitmap?> = _overlay.asStateFlow()

    private val _walkableCount = MutableStateFlow(0)
    val walkableCount: StateFlow<Int> = _walkableCount.asStateFlow()

    // DB 的 DAO (ViewModel 需要自己存取DB來快取)
    private val db = IndoorMapDatabase.getDatabase(context)
    private val gridDao = IndoorMapDatabase.getDatabase(context).gridCacheDao()
    private val refDao = db.referencePointDao()
    private val gridSample = 2 // (和 IndoorMapScreen 用的網格取樣率一樣)

    // 一個 Coroutine Job 來追蹤載入任務
    private var loadMapDataJob: Job? = null

    // 追蹤 ViewModel 目前載入的地圖是哪一張
    private var lastLoadedMapGroup: String? = null

    // --- 2. 模型與設定檔 ---
    private val hadnn2Model: Hadnn2Model
    private val normalizationParams: NormalizationParams?
    private val matricesConfig: Map<String, MatrixData>

    init {
        _positionState.value = AppPositionState(error = "正在載入模型...")
        hadnn2Model = Hadnn2Model(context)
        normalizationParams = loadNormalizationParams(context)
        matricesConfig = loadTransformationMatrices(context)

        loadMapData("SE1")
        // 啟動「背景」預測迴圈
        startPredictionLoop()
    }

    // (★) 4. 新增：載入地圖資料的函式 (取代 IndoorMapScreen 的 LaunchedEffect)
    @RequiresPermission(Manifest.permission.ACCESS_WIFI_STATE)
    internal fun loadMapData(mapGroupName: String) {
        // 如果正在載入，就取消舊的
        loadMapDataJob?.cancel()

        loadMapDataJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                // 取得圖片資源 ID
                val imageRes = IndoorPathfinder.getMapDrawableResId(mapGroupName)
                if (imageRes == 0) return@launch

                // (A) 清空舊狀態 (在 Main Thread)
                withContext(Dispatchers.Main) {
                    _imageBitmap.value = null
                    _grid.value = null
                    _overlay.value = null
                    _walkableCount.value = 0
                }

                // (B) 載入圖片 (同 IndoorMapScreen 的邏輯)
                val finalBmp = loadAndScaleBitmap(context, imageRes) ?: return@launch

                // 在建立網格前，先抓取這張圖上的所有「參考點」
                val pointsOnMap = refDao.getReferencePointsByImageId(imageRes).first()

                // (C) 嘗試從 DB 快取載入 Grid
                val cached = gridDao.get(imageRes, gridSample)
                val g: Grid

                if (cached != null) {
                    val cells = IndoorPathfinder.run { cached.cells.toBooleanArray(cached.width * cached.height) }
                    g = Grid(cached.width, cached.height, cells)
                } else {
                    // (D) 如果 DB 沒快取，自己算一次 Grid
                    g = IndoorPathfinder.bitmapToGridFromWhiteCorridor(
                        bitmap = finalBmp,
                        sample = gridSample,
                        satMax = 0.12f,
                        valMin = 0.92f,
                        wallInflate = 3,
                        referencePoints = pointsOnMap
                    )

                    // (E) 算完存回 DB
                    val packed = IndoorPathfinder.run { g.cells.toBitPackedBytes() }
                    gridDao.upsert(
                        GridCacheEntity(
                            imageId = imageRes,
                            sample = gridSample,
                            width = g.w,
                            height = g.h,
                            cells = packed
                        )
                    )
                }

                // (F) 建立 Overlay
                val ov = IndoorPathfinder.buildGridOverlayBitmap(g)
                val cellsCount = g.cells.count { it }

                // (G) 全部完成，一次更新到 Main Thread
                withContext(Dispatchers.Main) {
                    _imageBitmap.value = finalBmp.asImageBitmap()
                    _grid.value = g
                    _overlay.value = ov
                    _walkableCount.value = cellsCount
                }

            } catch (e: Exception) {
                Log.e("IndoorPositioningVM", "載入地圖資料失敗", e)
            }
        }
    }

    // (★) 5. 新增：載入並縮放圖片的輔助函式
    private suspend fun loadAndScaleBitmap(context: Context, imageRes: Int): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val decoded = BitmapFactory.decodeResource(context.resources, imageRes) ?: return@withContext null
            val metrics = Resources.getSystem().displayMetrics
            val maxW = (metrics.widthPixels * 2f).toInt()
            val maxH = (metrics.heightPixels * 2f).toInt()
            val scale = min(maxW / decoded.width.toFloat(), maxH / decoded.height.toFloat())
            if (scale < 1f) {
                Bitmap.createScaledBitmap(
                    decoded,
                    (decoded.width * scale).toInt().coerceAtLeast(1),
                    (decoded.height * scale).toInt().coerceAtLeast(1),
                    true
                )
            } else decoded
        } catch (e: Exception) {
            null
        }
    }

    private fun startPredictionLoop() {
        viewModelScope.launch(Dispatchers.IO) @RequiresPermission(Manifest.permission.ACCESS_WIFI_STATE) { // (★) 在 IO 執行緒執行

            // --- (A) 載入模型 (從 IndoorMapScreen 搬過來的) ---
            if (!hadnn2Model.isModelLoaded) {
                Log.d("LocationViewModel", "HADNN2 模型尚未載入，開始載入...")
                hadnn2Model.loadModel()
                if (!hadnn2Model.isModelLoaded) {
                    Log.e("LocationViewModel", "HADNN2 模型載入失敗。")
                    _positionState.value = AppPositionState(error = "HADNN2 模型載入失敗")
                    return@launch // 載入失敗，結束迴圈
                }
                Log.d("LocationViewModel", "HADNN2 模型載入成功！")
            }

            // --- (B) 預測迴圈 (從 IndoorMapScreen 搬過來的) ---
            while (true) {
                if (normalizationParams == null || matricesConfig.isEmpty()) {
                    _positionState.value = AppPositionState(error = "座標設定檔尚未載入")
                    delay(1000) // 1秒後重試
                    continue // 跳過這次迴圈
                }

                val scanResults = getScanResults(context)
                if (scanResults.isEmpty()) {
                    _positionState.value = _positionState.value.copy(error = "未掃描到 WiFi 訊號")
                    _isLikelyIndoors.value = false // (★) 沒掃到Wi-Fi，肯定不在室內
                    delay(1000)
                    continue
                }

                // 1. 取得 bssid_mapping.csv 裡所有的 BSSID 列表
                val indoorBssids = hadnn2Model.bssidToIndexMap.keys

                // 2. 取得目前掃描到的 BSSID 列表
                val currentScanBssids = scanResults.map { it.BSSID }.toSet()

                // 3. 找出兩者的交集 (你掃到的，同時也在 bssid_mapping.csv 裡的)
                val commonBssidsCount = currentScanBssids.intersect(indoorBssids).size

                // 4. 設定一個閾值，例如掃到 1 個以上才算在室內
                // (這個 '1' 你可以自己調整，數字越大越準確，但觸發越慢)
                val likelyIndoors = commonBssidsCount >= 1
                _isLikelyIndoors.value = likelyIndoors

                Log.d("LocationViewModel", "Indoor BSSIDs detected: $commonBssidsCount -> isLikelyIndoors: $likelyIndoors")

                val prediction = hadnn2Model.predict(scanResults)
                if (prediction != null) {
                    val (scaled_lon, scaled_lat, b_idx, f_idx) = prediction

                    val newGroupName = getGroupName(b_idx, f_idx)

                    // Z-Score -> 經緯度
                    val normParams = normalizationParams!!
                    val lon = (scaled_lon * normParams.lo_std + normParams.lo_mean)
                    val lat = (scaled_lat * normParams.la_std + normParams.la_mean)

                    val transformData = matricesConfig[newGroupName]

                    if (transformData != null) {
                        try {
                            // 經緯度 -> 百分比 (反解矩陣)
                            val M = transformData.matrix
                            val a = M[0][0]; val b = M[0][1]; val tx = M[0][2]
                            val c = M[1][0]; val d = M[1][1]; val ty = M[1][2]
                            val det = (a * d) - (b * c)

                            if (Math.abs(det) < 1e-15) {
                                throw Exception("矩陣無法反解 (det=0)")
                            }

                            val lon_prime = lon - tx
                            val lat_prime = lat - ty
                            val percent_x = ((d * lon_prime - b * lat_prime) / det).toFloat()
                            val percent_y = ((-c * lon_prime + a * lat_prime) / det).toFloat()

                            // (★) 成功！更新「共享狀態」
                            _positionState.value = AppPositionState(
                                location = Location("hadnn_prediction").apply {
                                    latitude = lat
                                    longitude = lon
                                },
                                mapGroupName = newGroupName,
                                mapPercentage = PointF(percent_x, percent_y),
                                error = null // 成功，清除錯誤
                            )

                        } catch (e: Exception) {
                            // 計算百分比時出錯
                            _positionState.value = _positionState.value.copy(
                                error = "計算 $newGroupName 百分比時出錯: ${e.message}"
                            )
                        }
                    } else {
                        // 找不到轉換矩陣
                        _positionState.value = _positionState.value.copy(
                            error = "在 matricesConfig 中找不到 '$newGroupName' 的轉換矩陣"
                        )
                    }
                } else if (!likelyIndoors) {
                    _positionState.value = _positionState.value.copy(error = "未進入室內Wi-Fi範圍")
                    if (lastLoadedMapGroup != null && lastLoadedMapGroup != "SE1") {
                        lastLoadedMapGroup = null // 清除標記
                        loadMapDataJob?.cancel()
                        // (★) 載入預設的 SE1 地圖
                        loadMapData("SE1")
                        lastLoadedMapGroup = "SE1" // 重新標記為 SE1
                    }
                } else {
                    // 模型無法計算位置
                    _positionState.value = _positionState.value.copy(
                        error = "HADNN2 模型無法計算位置"
                    )
                }
                delay(1000) // 每 1 秒預測一次
            }
        }
    }
}

