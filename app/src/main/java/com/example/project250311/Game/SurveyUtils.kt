package com.example.project250311.Game

import android.content.Context
import android.net.Uri
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.content.ContextCompat
import com.example.project250311.R // 記得確認你的 R class路徑

object SurveyUtils {
    // 定義你的表單網址 (請用 viewform 結尾的長網址，不要用短網址以防參數錯誤)
    // 前測表單網址 (請換成你的長網址)
    private const val PRE_SURVEY_URL = "https://docs.google.com/forms/d/e/1FAIpQLScwdZnQBKcHKc-VV8Fd1tSbi1BxdJok69y4Jo-GYkQEdjXqvw/viewform?usp=header"
    // 前測 User ID 欄位代碼 (請換成步驟2找到的代碼)
    private const val PRE_SURVEY_ENTRY_ID = "entry.1420032583"

    // 後測表單網址
    private const val POST_SURVEY_URL = "https://docs.google.com/forms/d/e/1FAIpQLSfur8MY4Z6vf8q820mJKMo7LkWuf3QxE1p0MT2CN30n_FeryQ/viewform?usp=header"
    // 後測 User ID 欄位代碼
    private const val POST_SURVEY_ENTRY_ID = "entry.835134596"

    /**
     * 開啟前測問卷
     */
    fun launchPreSurvey(context: Context) {
        val userId = GameManager.getUserId(context)
        val fullUrl = "$PRE_SURVEY_URL?usp=pp_url&$PRE_SURVEY_ENTRY_ID=$userId"
        launchCct(context, fullUrl)

        // 紀錄 Log
        GameManager.logEvent(context, "open_pre_survey", mapOf("url" to fullUrl))
    }

    /**
     * 開啟後測問卷
     */
    fun launchPostSurvey(context: Context) {
        val userId = GameManager.getUserId(context)
        val fullUrl = "$POST_SURVEY_URL?usp=pp_url&$POST_SURVEY_ENTRY_ID=$userId"
        launchCct(context, fullUrl)

        // 紀錄 Log
        GameManager.logEvent(context, "open_post_survey", mapOf("url" to fullUrl))
    }

    // 私有輔助函式：啟動 CCT
    // 私有輔助函式：啟動 CCT
    private fun launchCct(context: Context, url: String) {
        // 1. 設定 Toolbar 顏色
        // 使用 CustomTabColorSchemeParams (新版寫法)
        val colorParams = CustomTabColorSchemeParams.Builder()
            .setToolbarColor(0xFF177785.toInt()) // 深青綠色
            .build()

        // 2. 建立 Builder 並套用設定
        val builder = CustomTabsIntent.Builder()
            .setDefaultColorSchemeParams(colorParams) // 套用顏色設定
            .setShowTitle(true) // 顯示網頁標題

        // 3. 建立 Intent
        val customTabsIntent = builder.build()

        // 4. 開啟網址 (加上錯誤處理以確保穩健性)
        try {
            customTabsIntent.launchUrl(context, Uri.parse(url))
        } catch (e: Exception) {
            // 萬一使用者手機完全沒安裝瀏覽器 (極少見但需防範)
            e.printStackTrace()
        }
    }
}