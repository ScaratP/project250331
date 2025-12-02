package com.example.project250311.Map.IndoorMap


import android.Manifest
import android.app.Application
import android.graphics.PointF
import android.location.Location
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Paint
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.util.AttributeSet
import android.util.Log
import androidx.compose.ui.geometry.Offset
import androidx.core.content.ContextCompat
import com.example.project250311.R
import com.ortiz.touchview.TouchImageView
import kotlinx.coroutines.delay
import org.json.JSONObject
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.collections.List
import kotlin.collections.component1


fun getFloorDisplayName(groupName: String?): String {
    return when (groupName?.lowercase()) {
        "se1" -> "理工1樓"
        "se2" -> "理工2樓"
        "se3" -> "理工3樓"
        "sea4" -> "A棟4樓"
        "sea5" -> "A棟5樓"
        "seb4" -> "B棟4樓"
        "sec4" -> "C棟4樓"
        "sec5" -> "C棟5樓"
        else -> "定位中"
    }
}

val floorPlans =
    listOf(
        "SE1" to R.drawable.se1,
        "SE2" to R.drawable.se2,
        "SE3" to R.drawable.se3,
        "SEA4" to R.drawable.sea4,
        "SEA5" to R.drawable.sea5,
        "SEB4" to R.drawable.seb4,
        "SEC4" to R.drawable.sec4,
        "SEC5" to R.drawable.sec5
    )

data class AppPositionState(
    // 計算距離用
    val location: Location? = null,

    // 圖片顯示用 (e.g., "se1", "sec5")
    val mapGroupName: String? = null,

    // 給地圖畫點用 (e.g., (25.4f, 50.1f))
    val mapPercentage: PointF? = null,

    // 顯示錯誤訊息
    val error: String? = null
)

// 載入「經緯度標準化」設定
//輸出zscore座標回歸經緯度
data class NormalizationParams(
    val lo_mean: Double,
    val lo_std: Double,
    val la_mean: Double,
    val la_std: Double
)

fun getMapResId(groupName: String?): Int {
    return floorPlans.find { it.first.equals(groupName, ignoreCase = true) }?.second ?: 0
}

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

// 載入「經緯度 -> 百分比」轉換矩陣 ---
data class MatrixData(
    val matrix: List<List<Double>>,
    val origin: List<Double>
)

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

// Map 的 Key 現在是 GroupName, e.g., "se1", "sec5"
fun loadTransformationMatrices(context: Context): Map<String, MatrixData> {
    return try {
        val jsonString = context.assets.open("transformation_data.json")
            .bufferedReader()
            .use { it.readText() }
        val root = JSONObject(jsonString)
        val matricesRoot = root.getJSONObject("matrices")
        val originsRoot = root.getJSONObject("origins")

        // Map 的 Key 是 GroupName (String)
        val matrixMap = mutableMapOf<String, MatrixData>()

        jsonKeyToGroupNameMap.forEach { (resIdString, groupName) ->
            // (e.g., resIdString = "2131165302", groupName = "sec5")

            if (matricesRoot.has(resIdString) && originsRoot.has(resIdString)) {
                // 解析 Matrix
                val matrixJson = matricesRoot.getJSONArray(resIdString)
                val matrix = List(matrixJson.length()) { i ->
                    val row = matrixJson.getJSONArray(i)
                    List(row.length()) { j -> row.getDouble(j) }
                }

                // 解析 Origin
                val originJson = originsRoot.getJSONArray(resIdString)
                val origin = List(originJson.length()) { i -> originJson.getDouble(i) }

                // 使用 groupName (e.g., "sec5") 作為 Map 的 Key
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
        // 載入 BSSID
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
            mappedBssidCount = 531
            Log.d("Hadnn2Model", "BSSID 映射已載入, $mappedBssidCount features")
        } catch (e: Exception) {
            Log.e("Hadnn2Model", "載入 BSSID 映射出錯: ${e.message}")
        }
    }

    // 載入 TFLite 檔案
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

    // 載入模型 (保持不變，使用 0=B, 1=C, 2=F)
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
                0 to outputBufferBuilding,
                1 to outputBufferCoords,
                2 to outputBufferFloor
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

    // 預測函數，加入 Log 輸出
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

            // 讀取原始輸出
            val scaled_lon = outputBufferCoords[0][0]
            val scaled_lat = outputBufferCoords[0][1]
            val predictedBuildingIndex = outputBufferBuilding[0].indices.maxByOrNull { outputBufferBuilding[0][it] } ?: -1
            val predictedFloorIndex = outputBufferFloor[0].indices.maxByOrNull { outputBufferFloor[0][it] } ?: -1

            if (predictedBuildingIndex == -1 || predictedFloorIndex == -1) {
                Log.e("Hadnn2Model", "無法從 Building 或 Floor 輸出中找到最大值")
                return null
            }

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

    // 對單筆的 Wi-Fi 掃描陣列 (inputArray) 執行 Z-Score 標準化 (axis=1)。
    fun normalizeInputArray(inputArray: FloatArray): FloatArray {
        // 計算 mean (平均值)
        // 注意：kotlin.Array.average() 返回 Double，我們轉回 Float
        val mean = inputArray.average().toFloat()

        // 計算 std (標準差)
        var sumOfSquares = 0.0f
        for (value in inputArray) {
            sumOfSquares += (value - mean) * (value - mean)
        }
        var std = kotlin.math.sqrt(sumOfSquares / inputArray.size)

        // 處理 std == 0 的情況 (避免除以零)
        if (std == 0.0f) {
            std = 1.0f
        }

        // 執行 (value - mean) / std，產生新的標準化陣列
        val normalizedArray = FloatArray(inputArray.size)
        for (i in inputArray.indices) {
            normalizedArray[i] = (inputArray[i] - mean) / std
        }

        return normalizedArray
    }

    // 準備輸入Wifi向量
    fun prepareInputRssVector(wifiSignals: Map<String, Int>): ByteBuffer {
        val signalStrengths = FloatArray(mappedBssidCount) { 100f }
        wifiSignals.forEach { (bssid, level) ->
            val index = bssidToIndexMap[bssid]
            if (index != null && index < signalStrengths.size) {
                signalStrengths[index] = level.toFloat()
            }
        }
        // 呼叫你新增的 Z-Score 函數，執行 (value - mean) / std
        val normalizedStrengths = normalizeInputArray(signalStrengths)

        // 將 "標準化後" 的資料填入 ByteBuffer
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

fun hasLocationPermission(context: Context): Boolean {
    return ContextCompat.checkSelfPermission(
        context, Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
        context, Manifest.permission.ACCESS_COARSE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
}
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


// 這是一個「共享的」ViewModel，獨立於 UI，在背景持續執行定位， APP 中「唯一」的位置來源。

class LocationViewModel(application: Application) : AndroidViewModel(application) {

    private val context = getApplication<Application>().applicationContext

    // 狀態
    private val _positionState = MutableStateFlow(AppPositionState(error = "正在初始化..."))
    val positionState: StateFlow<AppPositionState> = _positionState.asStateFlow()

    private val _isLikelyIndoors = MutableStateFlow(false)
    val isLikelyIndoors: StateFlow<Boolean> = _isLikelyIndoors.asStateFlow()

    // 模型與設定檔
    private val hadnn2Model: Hadnn2Model
    private val normalizationParams: NormalizationParams?
    private val matricesConfig: Map<String, MatrixData>

    // 在 ViewModel 初始化時，就載入所有東西
    init {
        _positionState.value = AppPositionState(error = "正在載入模型...")
        hadnn2Model = Hadnn2Model(context)
        normalizationParams = loadNormalizationParams(context)
        matricesConfig = loadTransformationMatrices(context)

        // 啟動「背景」預測迴圈
        startPredictionLoop()
    }

    private fun startPredictionLoop() {
        viewModelScope.launch(Dispatchers.IO) { // (★) 在 IO 執行緒執行

            // 載入模型
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

            // 預測迴圈
            while (true) {
                if (normalizationParams == null || matricesConfig.isEmpty()) {
                    _positionState.value = AppPositionState(error = "座標設定檔尚未載入")
                    delay(1000) // 1秒後重試
                    continue // 跳過這次迴圈
                }

                val scanResults = getScanResults(context)
                if (scanResults.isEmpty()) {
                    _positionState.value = _positionState.value.copy(error = "未掃描到 WiFi 訊號")
                    _isLikelyIndoors.value = false // 沒掃到Wi-Fi，肯定不在室內
                    delay(1000)
                    continue
                }

                // 取得 bssid_mapping.csv 裡所有的 BSSID 列表
                val indoorBssids = hadnn2Model.bssidToIndexMap.keys

                // 取得目前掃描到的 BSSID 列表
                val currentScanBssids = scanResults.map { it.BSSID }.toSet()

                // 找出兩者的交集 (你掃到的，同時也在 bssid_mapping.csv 裡的)
                val commonBssidsCount = currentScanBssids.intersect(indoorBssids).size

                // 設定一個閾值，例如掃到 1 個以上才算在室內
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

                            // 成功！更新「共享狀態」
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

class IndoorLocationView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : TouchImageView(context, attrs) {
    var predictedPercentage: PointF? = null
        set(value) {
            field = value
            postInvalidate()
        }
    var checkpointLocations: List<PointF>? = null
        set(value) {
            field = value
            postInvalidate() // (當集點列表更新時，也重畫)
        }
    val locationPaint = Paint().apply {
        color = android.graphics.Color.BLUE
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    val locationAccuracyPaint = Paint().apply {
        color = 0x4D0000FF
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    val locationCenterPaint = Paint().apply {
        color = android.graphics.Color.WHITE
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    val checkpointPaint = Paint().apply {
        color = android.graphics.Color.RED // (我用紅色)
        style = Paint.Style.STROKE // (空心)
        strokeWidth = 10f // (線條粗度)
        isAntiAlias = true
    }
    fun setLocationColors(primaryColor: Int, accuracyColor: Int, centerColor: Int) {
        locationPaint.color = primaryColor
        locationAccuracyPaint.color = accuracyColor
        locationCenterPaint.color = centerColor
    }
    fun useTransformCoordBitmapToTouch(x: Float, y: Float): PointF {
        return transformCoordBitmapToTouch(x, y)
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        predictedPercentage?.let { percent ->
            drawable?.let { d ->
                val bitmapWidth = d.intrinsicWidth.toFloat()
                val bitmapHeight = d.intrinsicHeight.toFloat()
                val pixelX = (percent.x / 100.0f) * bitmapWidth
                val pixelY = (percent.y / 100.0f) * bitmapHeight
                val screenPoint = useTransformCoordBitmapToTouch(pixelX, pixelY)
                val viewX = screenPoint.x
                val viewY = screenPoint.y

                // 預估位置圈 150f
                canvas.drawCircle(viewX, viewY, 150f, locationAccuracyPaint)

                // 主圓點28f
                canvas.drawCircle(viewX, viewY, 28f, locationPaint)

                // 圓心10f
                canvas.drawCircle(viewX, viewY, 10f, locationCenterPaint)
            }
        }

        //繪製集點任務的紅圈圈 (Checkpoints)
        checkpointLocations?.let { points ->
            drawable?.let { d ->
                val bitmapWidth = d.intrinsicWidth.toFloat()
                val bitmapHeight = d.intrinsicHeight.toFloat()

                points.forEach { pt ->
                    // 將百分比座標轉換為圖片像素座標
                    val pixelX = (pt.x / 100.0f) * bitmapWidth
                    val pixelY = (pt.y / 100.0f) * bitmapHeight

                    // 將圖片像素座標轉換為螢幕觸控座標 (考慮縮放和平移)
                    val screenPoint = useTransformCoordBitmapToTouch(pixelX, pixelY)

                    // 繪製紅圈圈 (半徑設為 25f，可依需求調整)
                    canvas.drawCircle(screenPoint.x, screenPoint.y, 25f, checkpointPaint)
                }
            }
        }

    }
    /**
     * 以動畫方式縮放並平移到指定的「百分比」座標。
     * @param percent 要居中的百分比座標 (來自 LocationViewModel)
     * @param targetScale 目標縮放倍率 (例如 4f)
     */
    fun centerOnLocation(percent: PointF, targetScale: Float) {
        val d = drawable ?: return // 圖片還沒載入
        val normalizedX = percent.x / 100.0f
        val normalizedY = percent.y / 100.0f

        setZoom(targetScale, normalizedX, normalizedY)
        setScrollPosition(normalizedX, normalizedY)
    }
}
