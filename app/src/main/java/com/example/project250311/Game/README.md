# Game (實境解謎與數據收集模組)

## 📌 簡介
此模組為專題成果展設計的「實境解謎遊戲」模式。透過遊戲化的機制（任務、集點、成就），引導使用者前往理工學院內的特定地點（Checkpoints）。
這不僅是為了娛樂，其核心目的是 **驗證室內定位模型的準確度**。當使用者在指定地點掃描 QR Code 時，系統會記錄當下的「模型預測座標」與「真實座標（QR Code 位置）」，作為後續優化 AI 模型的 Ground Truth 數據。

## ✨ 功能特色
* **LBS 位置觸發**：結合室內定位技術，使用者必須移動到目標地點附近（例如都在同一個建築樓層/圖片上）才能解鎖掃描功能。
* **QR Code 驗證**：到達定點後，需掃描實體 QR Code 確認「到此一遊」，防止作弊並確保數據真實性。
* **數位護照 (Passport)**：視覺化呈現使用者的集點進度與成就徽章，提升探索動力。
* **數據收集 (Data Logging)**：在背景自動記錄使用者的移動軌跡、掃描成功率與定位誤差，並上傳至後端（Firebase）供研究分析。
* **任務系統**：引導使用者體驗 APP 不同功能（如：查看課表、使用轉盤、前往特定教室）。

## 📂 檔案結構與職責

本資料夾 (`Game/`) 包含遊戲邏輯、UI 與數據記錄模組：

* **核心 UI**:
    * **`GameScreen.kt`**: 遊戲主畫面。上半部顯示即時室內定位地圖，下半部列出附近的集點任務列表。
    * **`QrcodeScreen.kt`**: QR Code 掃描器介面，處理相機預覽與條碼解析。
    * **`PassportScreen.kt`**: 數位護照頁面，展示已收集的印章與完成度。
    * **`CollectScreen.kt`**: 單一印章的詳細介紹頁面（獲得印章後的獎勵畫面）。
    * **`GameMissionScreen.kt`**: 任務列表介面，顯示非地點類的任務（如「使用學餐轉盤」）。

* **邏輯與工具**:
    * **`GameManager.kt`**: 遊戲狀態管理器與 **事件紀錄器**。負責將 `stamp_collected`、`qrcode_scan` 等事件發送至 Firebase Analytics。
    * **`SurveyUtils.kt`**: 問卷或回饋相關的工具函式（若有）。

## 🛠️ 技術實作細節

### 1. 位置檢核演算法
為了判斷使用者是否到達關卡，`GameViewModel` 會即時計算：
* **輸入**: 當前定位座標 $(x, y)$ 、目標關卡座標 $(target_x, target_y)$
* **計算**: 歐幾里得距離 $Distance = \sqrt{(x - target_x)^2 + (y - target_y)^2}$
* **判定**:
    * 若 $Distance \le Threshold$ (例如 15單位) 且 樓層正確 $\rightarrow$ 狀態設為 `IN_RANGE` (允許掃描)。
    * 否則 $\rightarrow$ 狀態設為 `TOO_FAR` (鎖定)。

### 2. 真實數據收集 (Ground Truth Collection)
當使用者掃描 QR Code 成功當下，系統會觸發 `GameManager.logEvent`，記錄並上傳到firebase以下關鍵數據：
* `checkpoint_id`: 真實位置 ID (Ground Truth)。
* `predicted_x`, `predicted_y`: 掃描當下的模型預測座標。
* `map_group`: 所在的樓層/區域。
* `timestamp`: 時間戳記。

這些數據是評估 CFNN/HADNN 模型在實際場域表現的關鍵依據。

## 🚀 如何使用
此模式通常透過 `Onboarding` 頁面的「遊戲模式」進入，或從側邊選單啟動：

```kotlin
// 範例：導航至遊戲主畫面
navController.navigate("game_home")
