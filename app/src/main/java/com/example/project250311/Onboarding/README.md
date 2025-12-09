# Onboarding (導覽與初始設定模組)

## 📌 簡介
此模組負責處理使用者 **首次啟動應用程式** 時的引導流程。透過直覺的圖文介紹與動畫轉場，引導使用者了解 APP 核心功能，並完成必要的權限授權與身分設定，確保後續功能（如定位、課表）能順利運作。

## ✨ 功能特色
* **動態權限請求**：在導覽開始前，自動檢查並請求 **位置 (Location)**、**相機 (Camera)** 與 **通知 (Notification)** 權限，符合 Android 運行時權限規範。
* **功能導覽輪播**：使用動畫頁面切換，介紹「地圖導航」、「學餐轉盤」等核心功能。
* **身分分流機制**：
    * **訪客模式**：僅使用地圖與基本功能，無需登入。
    * **學生模式**：解鎖課表、請假、筆記等完整功能，並進入額外的設定流程。
    * **遊戲模式**：專題展演專用，開啟實境解謎與數據收集功能。
* **偏好記憶**：使用 `SharedPreferences` 永久儲存使用者的身分選擇與設定狀態，避免重複導覽。

## 📂 檔案結構與職責

本資料夾 (`Onboarding/`) 包含兩個主要的 Composable 畫面：

* **`OnboardingScreen.kt`**
    * **職責**: 應用程式的進入點（首次啟動）。
    * **核心邏輯**:
        * `permissionLauncher`: 處理 Android 權限請求回調。
        * `OnboardingPage`: 定義導覽頁面的資料結構（標題、描述、圖示）。
        * `UserType`: 定義使用者列舉 (`VISITOR`, `STUDENT`, `GAME_PLAYER`)。
        * **狀態保存**: 選擇身分後，將 `user_type` 寫入 `onboarding_prefs`。

* **`StudentSetupScreen.kt`**
    * **職責**: 針對「學生模式」的第二階段設定。
    * **功能**:
        * 詢問是否開啟「同步課表」功能。
        * 詢問是否開啟「上課前通知」提醒。
        * 設定完成後更新 `setup_completed` 標記。

## 🛠️ 技術實作細節

### 1. 權限管理
使用 `ActivityResultContracts.RequestMultiplePermissions` 同時請求多項權限。針對 Android 13 (SDK 33) 以上版本，會自動加入 `POST_NOTIFICATIONS` 權限請求。

### 2. 資料持久化 (Persistence)
使用 `SharedPreferences` (檔名: `onboarding_prefs`) 記錄狀態：
* `onboarding_completed`: 是否已看過導覽頁。
* `setup_completed`: 是否已完成學生設定（僅學生模式需要）。
* `user_type`: 使用者身分 (`VISITOR` / `STUDENT` / `GAME_PLAYER`)。
* `want_schedule` / `want_notifications`: 學生的功能偏好。

### 3. UI 動畫
* 使用 `AnimatedContent` 與 `slideInHorizontally` / `fadeIn` 實作流暢的頁面切換效果。
* 使用 `AnimatedVisibility` 在最後一頁動態展開身分選擇卡片。

## 🚀 如何使用
在 `MainActivity` 或導航邏輯中，應先檢查 `onboarding_completed` 狀態：

```kotlin
// 範例邏輯
val context = LocalContext.current
if (!isOnboardingCompleted(context)) {
    // 顯示導覽頁
    OnboardingScreen { userType ->
        if (userType == UserType.STUDENT) {
            // 導航至學生設定頁
            navController.navigate("student_setup")
        } else {
            // 導航至主畫面
            navController.navigate("home")
        }
    }
}
