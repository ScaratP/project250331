# 課程管理應用程式 (Course Management App)

這是一個專為學生設計的 Android 課程管理應用程式，提供課程表管理、請假申請、通知提醒和筆記功能。本專案是對原始 project250311 的改進版本，使用了更現代的架構與功能。

## 主要改進

與原始 project250311 相比，本專案有以下主要改進：

### 1. 現代化導航系統
- **新增導航抽屜 (Navigation Drawer)** 取代原先的網格介面
- **導入 Jetpack Navigation** 實現標準化的頁面導航
- **統一的返回行為** 實現更一致的用戶體驗

### 2. 通知系統重構
- **系統重啟通知恢復** - 新增 BootReceiver 支援裝置重啟時恢復課程通知
- **通知權限管理** - 全面支援 Android 13 (API 33) 的通知權限模型
- **通知管理介面** - 專門的通知管理頁面

### 3. 筆記功能強化
- **格式化選項** - 增強的文本編輯工具列
- **編輯記憶體功能** - 修改現有筆記時保留格式

### 4. 架構優化
- **MVVM 架構** - 更嚴格地遵循 MVVM 架構模式
- **解耦組件** - 更好的代碼組織和維護性
- **集中式通知工具** - 統一的 NotificationUtils 工具類

## 功能特色

### 1. 課程表管理 (Schedule)
- 自動從學校系統同步課程資料
- 視覺化的課程表界面
- 課程詳細資訊查看
- 支援修改課程地點

### 2. 通知提醒系統 (Notice)
- 課程開始前自動通知
- 可設定提前提醒時間
- 支援裝置重啟後恢復通知
- 統一管理所有課程通知

### 3. 請假系統 (Leave)
- 整合學校請假系統
- 記錄請假歷史
- 支援不同類型的請假申請
- 可查看請假統計資料

### 4. 筆記功能 (Notes)
- 支援富文本編輯
- 可變更字體大小、顏色和樣式
- 支援日期和關鍵字搜尋
- 便利貼風格的筆記列表

## 技術特點

- **Kotlin 與 Jetpack Compose** - 現代化的 UI 開發
- **Navigation Component** - 標準化的頁面導航
- **Room Database** - 本地資料存儲
- **JSoup** - 網頁爬蟲整合學校系統
- **LiveData 和 Flow** - 實現響應式 UI
- **Material Design 3** - 現代化設計風格
- **BroadcastReceiver** - 系統事件響應

## 系統需求

- Android 12.0 (API 31) 或更高版本
- 網路連接（用於同步學校資料）
- 通知權限（用於課程提醒）
- 精確鬧鐘權限 (精確的課程通知)

## 安裝方式

1. 從 GitHub Releases 下載最新的 APK 檔案
2. 在 Android 裝置上開啟檔案並安裝
3. 首次啟動時需授予必要權限
4. 使用學校系統帳號登入以同步課程資料

## 權限說明

本應用程式需要以下權限才能正常運作：

- `INTERNET` - 用於從學校系統獲取課程資料
- `POST_NOTIFICATIONS` - 用於發送課程提醒通知 (Android 13+)
- `SCHEDULE_EXACT_ALARM` - 用於設定精確的課程提醒 (Android 12+)
- `RECEIVE_BOOT_COMPLETED` - 用於裝置重啟後恢復通知設定

## 目錄結構

```
app/src/main/java/com/example/project250311/
├── Data/                    # 數據層
│   ├── Note.kt             # 筆記數據模型
│   ├── NoteDao.kt          # 筆記數據訪問對象
│   ├── NoteDatabase.kt     # 筆記數據庫
│   └── Schedule.kt         # 課程數據模型與數據庫
├── Schedule/                # 功能模組
│   ├── GetSchedule/        # 課程表功能
│   ├── NoSchool/           # 請假系統
│   ├── Note/               # 筆記功能
│   └── Notice/             # 通知系統
├── ui/theme/                # UI 主題
└── MainActivity.kt          # 主活動與導航控制
```

## 開發者資訊

此應用程式使用以下主要框架和函式庫：
- Kotlin 2.1.0
- Jetpack Compose 2024.04.01
- Room Database 2.6.1
- JSoup 1.19.1
- Material 3 Components 1.3.1
- GSON 2.10.1
- Navigation Compose 2.8.9
