# 🗺️ 室內定位與導航模組串接說明書

本文件說明如何在 Jetpack Compose 的導航畫面中，串接 `LocationViewModel` 以取得即時的室內定位資訊，並在地圖上繪製使用者位置。

## 1\. 核心概念

定位系統的所有複雜運算（Wi-Fi 訊號掃描、HADNN 模型推論、座標轉換）都已封裝在 `LocationViewModel` 中。

UI 層（Activity 或 Composable）**不需要**知道定位是如何算出來的，只需要**觀察（Observe）** ViewModel 中的狀態變化，並根據狀態更新畫面即可。

## 2\. 實作步驟

### 步驟一：取得 ViewModel 實例

在你的地圖 Composable 函式（例如 `IndoorMapScreen`）中，請取得 `LocationViewModel` 的實例。

```kotlin
// 取得 ViewModel 實例 (確保已加入相關 dependencies)
val locationViewModel: LocationViewModel = viewModel()
```

### 步驟二：觀察定位狀態 (State Flow)

我們使用 `collectAsState()` 來觀察 ViewModel 中的數據流。當使用者的位置改變時，這些變數會自動更新，並觸發 UI 重繪。

```kotlin
// 1. 主要位置狀態：包含地圖名稱、座標百分比、經緯度等
val positionState by locationViewModel.positionState.collectAsState()

// 2. 室內判定狀態：(可選) 用來判斷是否在室內範圍，可用於切換室內/室外模式
val isLikelyIndoors by locationViewModel.isLikelyIndoors.collectAsState()
```

### 步驟三：理解 `AppPositionState` 資料結構

`positionState` 是我們溝通的核心，請務必理解其中欄位的意義：

| 欄位名稱 | 類型 | 說明 | 用途 |
| :--- | :--- | :--- | :--- |
| **`mapGroupName`** | `String?` | 地圖群組代碼 (如 `"se1"`, `"sec5"`) | **決定要顯示哪一張底圖**。你需要寫一個輔助函式將此字串轉為 `R.drawable.xxx`。 |
| **`mapPercentage`** | `PointF?` | 相對座標百分比 (0\~100) | **決定藍點畫在哪裡**。<br>`x=50, y=50` 代表在地圖正中心。 |
| **`location`** | `Location?` | 虛擬經緯度物件 | **計算距離用**。<br>例如：計算與「集點關卡」的距離，或用於導航路徑規劃。 |
| `error` | `String?` | 錯誤訊息 | 用於 Debug 或顯示 Toast 提示使用者（如：未掃描到 Wi-Fi）。 |

### 步驟四：實作地圖顯示 (Compose UI)

我們使用 `AndroidView` 來嵌入自定義的 `IndoorLocationView`，因為它負責了圖片的縮放手勢與座標繪製。

請將以下程式碼整合進你的 `IndoorMapScreen`：

```kotlin
@Composable
fun IndoorMapScreen(
    // 可以從參數傳入，或是直接在裡面宣告 ViewModel
    viewModel: LocationViewModel = viewModel()
) {
    // 1. 觀察狀態
    val positionState by viewModel.positionState.collectAsState()

    // 2. 根據 mapGroupName 決定資源 ID 的輔助邏輯
    // 你需要實作 getMapDrawableResId 函式，或使用現有的
    val mapResId = getMapDrawableResId(positionState.mapGroupName ?: "se1")

    // 3. 介面佈局
    Box(modifier = Modifier.fillMaxSize()) {
        
        // 嵌入自定義 View
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                // 初始化 IndoorLocationView
                IndoorLocationView(context).apply {
                    // 設定最大/最小縮放倍率
                    maxZoom = 4f 
                    minZoom = 1f
                }
            },
            update = { view ->
                // === 這裡是最重要的地方：當 State 改變時會執行 ===

                // A. 設定地圖底圖
                // 注意：只有當 mapGroupName 改變時才需要重新 setImageResource，
                // 如果 View 內部已經有防呆判斷則沒關係。
                view.setImageResource(mapResId)

                // B. 更新藍點位置
                // 直接把 ViewModel 算好的百分比丟給 View，View 會自己畫
                view.predictedPercentage = positionState.mapPercentage
                
                // C. (選配) 如果有集點活動的座標，也可以傳進去
                // view.checkpointLocations = ...
            }
        )

        // 4. (選配) 顯示 Debug 資訊或樓層名稱
        Text(
            text = "目前樓層: ${positionState.mapGroupName ?: "定位中..."}",
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(16.dp)
                .background(Color.White.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                .padding(8.dp)
        )
    }
}
```

## 3\. 常見問題 (Q\&A)

* **Q: 為什麼 `mapGroupName` 是 null？**

    * A: 剛進入 APP 尚未完成第一次模型推論，或是掃描不到 Wi-Fi 訊號時會是 null。建議 UI 上給一個預設值（如 "se1"）或顯示「定位中...」的 Loading 畫面。

* **Q: 藍點為什麼不會動？**

    * A: 請確認 `LocationViewModel` 中的 `startPredictionLoop` 有在背景執行，且 Logcat 有印出 `HADNN2` 相關的預測數值。如果是在模擬器上，因為沒有 Wi-Fi，所以不會動是正常的。

* **Q: 如何新增地圖資源？**

    * A: 請確保 `res/drawable` 資料夾中有對應的圖片（如 `se1.png`, `sec5.png`），並在 `getMapDrawableResId` 函式中加入對應的 `case`。