# Announcement (校園公告模組)

## 📌 簡介
此模組是智慧校園 APP 的資訊核心之一，負責擷取並顯示臺東大學（NTTU）官方網站的各類公告訊息。透過即時爬蟲技術，將分散在學校網頁不同區塊的資訊整合至單一介面，並提供分類篩選功能，讓師生能快速掌握校園最新動態。

## ✨ 功能特色
* **即時爬蟲**：應用程式啟動或刷新時，直接解析學校網站 HTML 獲取最新資料，無需等待後端 API 更新。
* **分類瀏覽**：整合並區分「重要消息」、「校內活動」、「行政公告」、「學術公告」、「徵人啟事」、「招生放榜」及「媒體報導」等七大類別。
* **時效過濾**：系統自動過濾掉 **一個月以前** 的舊公告，確保使用者專注於近期資訊。
* **一鍵刷新**：透過頂部應用列的刷新按鈕，可隨時手動重新載入最新公告。
* **自動置頂**：切換公告分類時，列表會自動滾動至頂部，提供流暢的瀏覽體驗。
* **外部跳轉**：點擊公告卡片即可透過預設瀏覽器開啟原始網頁查看詳情。

## 📂 檔案結構與職責

本資料夾 (`Announcement/`) 主要包含 UI 呈現與資料擷取邏輯：

* **`AnnouncementScreen.kt`**
    * **UI Composable**:
        * `AnnouncementScreen`: 主畫面容器，負責監聽 ViewModel 狀態 (Loading/Error/Success) 並顯示對應畫面。
        * `AnnouncementTopBar`: 客製化頂部應用列，包含「分類下拉選單」與「手動刷新按鈕」。
        * `AnnouncementItem`: 單則公告的卡片式設計組件。
    * **爬蟲邏輯 (Scraper)**:
        * `fetchAnnouncementsData()`: 核心爬蟲函式。使用 `Jsoup` 連線至學校官網，解析 DOM 結構並轉換為 `Announcement` 物件列表。

> **注意**: 資料模型 (`Announcement.kt`) 與視圖模型 (`AnnouncementViewModel.kt`) 位於上層的 `Data` 資料夾中，以此模組進行引用。

## 🛠️ 技術實作細節

### 1. 資料來源解析
爬蟲針對臺東大學官網 (`https://www.nttu.edu.tw/`) 的不同區塊進行解析：

* **重要消息**:
    * 來源: 首頁 HTML。
    * 選擇器: `div.mouter:has(h2 img[alt="重要消息"]) div.d-item`
* **其他分類 (Tab 內容)**:
    * 來源: 使用特定參數的 Mobile Load URL (例如 `Action=mobileloadmod&Type=mobile_asso_cg_mstr...`)。
    * 選擇器: `div.d-item` (擷取標題 `div.mtitle a` 與日期 `i.mdate.after`)。

### 2. 關鍵相依套件
* **Jsoup**: 用於處理 HTML 連線、解析與選取元素。
* **Jetpack Compose**: 100% Kotlin 宣告式 UI。
* **Kotlin Coroutines**: 使用 `Dispatchers.IO` 在背景執行網路請求，確保 UI 流暢不卡頓。
* **LazyColumn**: 用於高效渲染長列表資料。

## 🚀 如何使用
在 `MainActivity` 或導航主機中，直接呼叫 `AnnouncementScreen` 並傳入 `AnnouncementViewModel` 即可：

```kotlin
AnnouncementScreen(viewModel = announcementViewModel)
