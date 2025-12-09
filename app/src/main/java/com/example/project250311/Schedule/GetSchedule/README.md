# GetSchedule (課表爬蟲與顯示模組)

## 📌 簡介
此模組負責從臺東大學校務系統中同步使用者的個人課表，並將雜亂的網頁表格資料轉化為直觀的行動版課表介面。它整合了 `WebView` 登入機制與 `Jsoup` 爬蟲技術，解決了校務系統需要身分驗證才能獲取資料的問題，並支援離線瀏覽與課程提醒設定。

## ✨ 功能特色
* **無縫登入整合**：內嵌 `WebView` 引導使用者登入校務系統，自動攔截身分驗證 Cookie，無需手動複製貼上。
* **智慧爬蟲解析**：
    * 自動解析 HTML 表格結構 (`table.NTTU_GridView`)。
    * 使用 **Regex** 精準提取課程名稱、教授與教室資訊。
    * **自動合併時段**：演算法會自動偵測並合併連續的課程（例如：第 3, 4 節同一門課），顯示為單一區塊。
* **視覺化課表**：
    * 響應式網格佈局，清楚展示週一至週日的課程安排。
    * 動態計算「空堂」與「上課」區塊，介面整潔不擁擠。
* **互動功能**：
    * **課程詳情**：點擊課程區塊可查看完整資訊。
    * **教室編輯**：允許使用者手動修正或備註上課地點。
    * **一鍵提醒**：整合通知系統，可針對單一課程開啟「上課前 10 分鐘提醒」。

## 📂 檔案結構與職責

本資料夾 (`GetSchedule/`) 主要包含核心 UI 與爬蟲邏輯：

* **`ScheduleScreen.kt`**
    * **UI Composable**:
        * `ScheduleScreen`: 主控制器，判斷是否需要登入 (`cookies == null`) 或顯示課表。
        * `WebViewScreen`: 負責載入校務系統登入頁面，並透過 `CookieManager` 提取 Session ID。
        * `ScheduleTable`: 核心課表視圖，繪製時間軸與課程網格。
        * `CourseDetailCard`: 底部彈出的課程詳細資訊卡片。
    * **爬蟲與資料邏輯**:
        * `fetchWebData()`: 核心爬蟲函式。使用 Jsoup 帶入 Cookie 請求課表頁面。
        * `parseTitle()`: 使用正規表達式解析原始 HTML 中的 `title` 屬性字串。
        * `mergeConsecutiveCourses()` (內嵌邏輯): 將連續節次的相同課程合併為單一物件。

## 🛠️ 技術實作細節

### 1. 登入與 Cookie 攔截
由於校務系統不提供 API，我們使用 `WebView` 載入 `https://infosys.nttu.edu.tw/InfoLoginNew.aspx`。
* 當使用者在網頁上登入成功後，`WebViewClient.onPageFinished` 會偵測到 URL 變化。
* 此時透過 `CookieManager.getInstance().getCookie(url)` 取得有效的 Session Cookie，供後續爬蟲使用。

### 2. 爬蟲解析策略
目標 URL: `https://infosys.nttu.edu.tw/n_CourseBase_Select/WeekCourseList.aspx`

1.  **定位元素**: 鎖定 `table.NTTU_GridView` 中的每一列 (`tr`) 與每一格 (`td`)。
2.  **提取資訊**: 課程資訊藏在 `span` 標籤的 `title` 屬性中，格式為：
    ```text
    科目名稱：計算機概論
    授課教師：王小明
    場地：SEA101
    ```
3.  **Regex 解析**: 使用 `科目名稱：(.+?)\n` 等樣式提取關鍵字。
4.  **座標映射**: 根據 HTML 的 `tr` (節次) 與 `td` (星期) 索引，映射到 `WeekDay` 與 `LocalTime`。

### 3. 資料合併演算法
原始爬蟲資料是「每一節課」一筆紀錄。為了讓 UI 顯示如「10:00 - 12:00」的長條區塊，系統在 `fetchWebData` 中執行合併邏輯：
* 先按星期與開始時間排序。
* 遍歷列表，若「當前課程」與「上一堂課程」名稱、地點相同且時間連續，則修改上一堂的「結束時間」，而非新增一筆紀錄。

## 🚀 如何使用
在 `Schedule` 主畫面中呼叫即可：

```kotlin
ScheduleScreen(viewModel = courseViewModel)
