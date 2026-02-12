<img src="/app/src/main/res/drawable/logo.png" align="left" width="120" height="auto" style="margin-right: 20px;">

<h1>PAMUTT</h1>

<a href="https://kotlinlang.org"><img src="https://img.shields.io/badge/Kotlin-1.9.0-purple.svg?style=flat&logo=kotlin" alt="Kotlin"></a>
<a href="https://developer.android.com/jetpack/compose"><img src="https://img.shields.io/badge/Jetpack%20Compose-Enabled-4285F4.svg?style=flat&logo=android" alt="Compose"></a>
<a href="https://www.tensorflow.org/lite"><img src="https://img.shields.io/badge/TF%20Lite-Enabled-orange.svg?style=flat&logo=tensorflow" alt="TensorFlow Lite"></a>
<a href="https://www.android.com"><img src="https://img.shields.io/badge/Platform-Android-green.svg?style=flat&logo=android" alt="Platform"></a>

<br clear="left"/>


這是一個專為學生設計的 Android 課程管理應用程式，提供課表管理、請假申請、通知提醒、筆記功能、學餐轉盤、校園公告和校園導航。

## 📱 概述

本APP旨在幫助學生有效管理學習生活，提供直觀的課表、便捷的請假方式、智能通知提醒、功能完善的筆記工具、趣味學餐選擇轉盤和完整的校園室內外導航。採用現代化的UI和架構設計，確保操作流暢且易於使用。

## ✨ 主要功能

本系統依據使用者情境與使用流程，設計了以下核心功能：

### 1. APP 導覽與模式選擇
* **多身分登入**：使用者可選擇「訪客模式」（僅使用地圖功能）或「學生模式」（解鎖課表與請假功能）。

* ~~**專題展遊戲模式**：專為成果發表會設計的互動模式，結合校園尋寶與集點活動。~~

### 2. 室外定位與導覽
* 整合校園戶外地圖圖資。
* 提供各系館、圖書館、宿舍等主要地標的方位指引與路徑規劃。
* 沿用[17屆學生會](https://github.com/di3n0/NTTU_Coordinate_Axis)測量之建築物與教室經緯度數據。將純文字座標轉化為視覺化地標，並整合至 Google Maps。

### 3. 室內導航
* 針對理工學院（SEA, SEB, SEC）建置樓層平面圖。
* 支援跨樓層路徑規劃，系統可自動判斷樓梯與電梯節點進行路線引導。

### 4. 室內定位
* **核心技術**：利用手機蒐集的 WiFi RSSI 訊號指紋（Fingerprinting），輸入至裝置端的 AI 模型，即時推算所在的建築物、樓層與座標。
* **即時運算**：採用輕量化 TFLite 模型，無需連網即可在手機端完成定位運算。

### 5. 課表爬蟲
* 自動登入教務系統抓取學期課表。
* 將課程資訊（時間、地點、教授）本地化儲存，支援離線查看。

### 6. 請假系統爬蟲
* 簡化學校請假流程，透過 APP 介面直接送出請假申請。
* 支援查詢歷史缺曠課紀錄。

### 7. 上課通知提醒
* 依據課表時間，於上課前自動發送推播通知。
* 具備 `BootReceiver` 機制，確保手機重開機後提醒服務依然有效。

### 8. 上課筆記管理
* ~~針對特定課程~~建立筆記。
* 筆記~~與課程 ID 綁定~~，方便期中/期末考時快速回顧。

### 9. 學餐隨機選擇轉盤
* 收錄校內學餐清單。
* 提供隨機轉盤功能，解決大學生「午餐吃什麼」的選擇難題。

### 10. 校園公告爬蟲
* 定期抓取學校首頁。

### 11. 因應專題展的遊戲模式
* 結合室內定位技術的闖關遊戲。
* 參觀者到達特定展區（由定位判定）即可解鎖成就。

---


## 🔬 研究方法與核心技術

本專題的核心技術亮點在於**基於深度學習的室內定位系統**。

### 1. 資料蒐集與處理
我們自行開發[資料蒐集工具](https://github.com/ScaratP/WifiIndoorSystem)，於理工學院三棟建築 (SEA, SEB, SEC) 進行網格化採樣。
* **特徵值 (Features)**: 531 個無線存取點 (BSSID) 的訊號強度 (RSSI)。
* **資料規模**: 總計蒐集 15,630 筆指紋資料 (Fingerprints)。

### 2. 模型選擇與決策
我們參考了 [*Cha et al. (2022)*](https://doi.org/10.1016/j.asoc.2022.108624) 的 HADNN 架構，並與 CFNN (Cascaded Feedforward Neural Network) 進行比較。雖然文獻中 HADNN 表現較佳，但在我們的場域資料中，**CFNN 展現了更好的準確度與穩定性**。

**實驗結果數據 (基於驗證集):**

| 模型架構 | 平均定位誤差 (m) | 建築物準確率 | 樓層準確率 | 決策 |
| :--- | :---: | :---: | :---: | :---: |
| **CFNN2 (Selected)** | **6.01 m** | **98.08%** | **98.02%** | ✅ **採用** |
| HADNN2 | 6.07 m | 98.14% | 97.98% | - |
| Baseline (DNN) | 6.81 m | 91.84% | 39.28% | - |

* 雖然 CFNN 的執行時間略高於簡單的 DNN，但在現代手機處理器上差異僅在毫秒級別，不影響使用者體驗，故最終選擇 **CFNN** 轉換為 `.tflite` 格式部署於 APP 中。

### 3. 系統實作架構
* **資料抓取**: 使用 `Jsoup` 與 `OkHttp` 模擬 HTTP 請求，處理教務系統的 Cookie 與 Session。
* **本地資料庫**: 使用 `Room Database` 儲存課表、筆記與地圖節點資訊。
* **非同步處理**: 全面採用 Kotlin Coroutines 與 Flow 處理網路請求與 I/O 操作，確保 UI 流暢度。

## 🔧 技術特點
- **Kotlin 與 Jetpack Compose** - 現代化的 UI 開發，流暢的操作體驗
- **Navigation Component** - 標準化的頁面導航，簡化導航邏輯
- **Room Database** - 本地資料存儲，確保離線功能可用
- **JSoup** - 網頁爬蟲整合學校系統，自動化數據同步
- **LiveData 和 Flow** - 實現響應式 UI，提升應用反應速度
- **Material Design 3** - 現代化設計風格，符合 Android 最新設計語言
- **BroadcastReceiver** - 系統事件響應，增強應用與系統的整合性
- **Canvas Animation** - 自訂繪圖與動畫效果，提供豐富的互動體驗
- **TouchImageView** - 自定義圖像視圖，支援室內地圖的縮放與平移
- **MVVM 架構** - 完整的架構設計，提升代碼可維護性
- **百分比座標系統** - 響應式座標計算，適應不同螢幕尺寸

## 📋 系統需求

- Android 12.0 (API 31) 或更高版本
- 網路連接 (用於同步學校資料和地圖功能)
- 通知權限 (用於課程提醒)
- 精確鬧鐘權限 (精確的課程通知)
- 位置權限 (用於室外地圖導航功能)
- 至少 100MB 可用儲存空間 (用於地圖資料)

## 🔐 權限說明

本應用程式需要以下權限才能正常運作：

- `INTERNET` - 用於從學校系統獲取課程資料
- `POST_NOTIFICATIONS` - 用於發送課程提醒通知 (Android 13+)
- `SCHEDULE_EXACT_ALARM` - 用於設定精確的課程提醒 (Android 12+)
- `RECEIVE_BOOT_COMPLETED` - 用於裝置重啟後恢復通知設定
- `ACCESS_FINE_LOCATION` - 用於校園地圖導航功能

## 📁 目錄結構

```
app/src/main/java/com/example/project250311/
├── Data/                   # 資料庫實體 (Schedule, Note, etc.)
├── Map/                    # 地圖模組
│   ├── IndoorMap/          # 室內定位邏輯與 TFLite 整合
│   ├── network/            # 路徑規劃 API 服務
│   └── ...
├── Schedule/               # 課表與教務系統模組
│   ├── GetSchedule/        # 課表爬蟲
│   ├── NoSchool/           # 請假系統
│   ├── Note/               # 筆記功能
│   └── Notice/             # 通知廣播接收器
├── Game/                   # 專題展遊戲模式
├── Onboarding/             # 初始導覽頁
└── ui/theme/               # Morandi 風格主題設定
```

## 👨‍💻 開發者資訊

此應用程式使用以下主要框架和函式庫：
- Kotlin 2.1.0
- Jetpack Compose 2024.04.01
- Room Database 2.6.1
- JSoup 1.19.1
- Material 3 Components 1.3.1
- GSON 2.10.1
- Navigation Compose 2.8.9


## 🚀 安裝與執行

1.  Clone 本專案至本地端。
2.  使用 Android Studio (Hedgehog 或更新版本) 開啟專案。
3.  等待 Gradle Sync 完成。
4.  連接 Android 實體裝置 (建議 Android 10+ 以獲得最佳相容性)。
5.  點擊 **Run** 進行安裝。



**© 國立臺東大學 資訊工程學系 114畢業專題**

**智慧校園定位與導航系統之研究──以臺東大學理工學院為實驗場域**
