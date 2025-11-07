// 檔案名稱: AnnouncementScreen.kt
// 檔案路徑: com/example/project250311/Announcement/AnnouncementScreen.kt
package com.example.project250311.Announcement

import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.project250311.Data.Announcement
import com.example.project250311.Data.AnnouncementViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 主畫面 Composable
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnnouncementScreen(viewModel: AnnouncementViewModel) {
    // 觀察 ViewModel 中的 LiveData
    val announcements by viewModel.announcements.observeAsState(emptyList())
    val isLoading by viewModel.isLoading.observeAsState(false)
    val errorMessage by viewModel.errorMessage.observeAsState(null)

    val context = LocalContext.current

    // 關鍵！當這個 Composable 第一次啟動時，會執行一次
    LaunchedEffect(Unit) {
        // 1. 馬上從資料庫載入舊資料，這樣畫面才不會空白
        viewModel.loadAnnouncementsFromDb()
        // 2. 接著在背景執行網路爬蟲刷新
        viewModel.refreshAnnouncementsFromWeb(::fetchAnnouncementsData)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("校網公告", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                actions = {
                    // 重新整理按鈕
                    IconButton(onClick = {
                        // 點擊時，呼叫 ViewModel 執行網路爬蟲
                        viewModel.refreshAnnouncementsFromWeb(::fetchAnnouncementsData)
                    }) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            when {
                // 狀態 1: 正在載入中，且沒有舊資料
                isLoading && announcements.isEmpty() -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }

                // 狀態 2: 發生錯誤
                errorMessage != null -> {
                    Text(
                        text = errorMessage ?: "發生未知錯誤",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(16.dp),
                        textAlign = TextAlign.Center
                    )
                }

                // 狀態 3: 載入完畢，但沒有任何公告
                !isLoading && announcements.isEmpty() -> {
                    Text(
                        text = "目前沒有最近一個月的公告",
                        modifier = Modifier.align(Alignment.Center),
                        textAlign = TextAlign.Center
                    )
                }

                // 狀態 4: 成功載入公告
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        items(announcements, key = { it.url }) { announcement ->
                            AnnouncementItem(
                                announcement = announcement,
                                onClick = {
                                    // 點擊後開啟瀏覽器
                                    try {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(announcement.url))
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        // 備用方案，如果 URL 有問題
                                        Log.e("AnnouncementClick", "Could not open URL", e)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 顯示單一公告項目的 Composable
 */
@Composable
fun AnnouncementItem(announcement: Announcement, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // 1. 分類標籤
            Text(
                text = announcement.category,
                color = MaterialTheme.colorScheme.primary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))

            // 2. 標題
            Text(
                text = announcement.title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))

            // 3. 日期
            Text(
                text = announcement.date.toString(), // 顯示 "2025-11-07"
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.align(Alignment.End)
            )
        }
    }
}

// --- 以下是爬蟲邏輯 ---

/**
 * 幫助我們解析日期字串 (例如 "2025-11-07 ")
 */
private fun parseDate(dateStr: String): LocalDate? {
    return try {
        // .trim() 是為了去掉日期後面可能有的空白
        LocalDate.parse(dateStr.trim(), DateTimeFormatter.ISO_LOCAL_DATE)
    } catch (e: Exception) {
        null // 如果格式不對 (例如 "更多...") 就回傳 null
    }
}

/**
 * 這是最主要的爬蟲 Function
 * (已修正為可抓取動態載入內容的版本)
 */
suspend fun fetchAnnouncementsData(): List<Announcement> {
    // 確保爬蟲在 IO 執行緒上跑
    return withContext(Dispatchers.IO) {
        val allAnnouncements = mutableListOf<Announcement>()
        val cutoffDate = LocalDate.now().minusMonths(1) // 只抓一個月內的
        val baseUrl = "https://www.nttu.edu.tw/"
        val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/58.0.3029.110 Safari/537.36"

        try {
            Log.d("fetchAnnouncementsData", "Start fetching from $baseUrl")
            // 1. First, fetch the main page to get "重要消息"
            val doc = Jsoup.connect(baseUrl)
                .userAgent(userAgent)
                .timeout(10000) // 10 秒超時
                .get()

            // --- 區塊 1: 重要消息 (FIXED SELECTOR) ---
            // 修正! 選擇器改為抓取 <img> 的 alt 屬性
            val importantItems = doc.select("div.mouter:has(h2 img[alt=\"重要消息\"]) div.d-item")
            Log.d("fetchAnnouncementsData", "Found ${importantItems.size} '重要消息' items (before date filter)")

            for (item in importantItems) {
                val aTag = item.selectFirst("div.mtitle a")
                val dateTag = item.selectFirst("i.mdate.after")
                val title = aTag?.text()?.trim()
                val url = aTag?.attr("abs:href")
                val dateStr = dateTag?.text()?.trim()

                if (title.isNullOrEmpty() || url.isNullOrEmpty() || dateStr.isNullOrEmpty()) continue

                val date = parseDate(dateStr)
                if (date != null && (date.isAfter(cutoffDate) || date.isEqual(cutoffDate))) {
                    allAnnouncements.add(
                        Announcement(url = url, title = title, category = "重要消息", date = date)
                    )
                }
            }
            Log.d("fetchAnnouncementsData", "Added ${allAnnouncements.size} '重要消息' items (after date filter)")

            // --- 區塊 2: Tab 裡面的內容 (NEW STRATEGY) ---
            // 這些是從 HTML 原始碼 <script> 標籤中找到的 AJAX 網址
            val tabUrls = mapOf(
                "校內活動" to "https://www.nttu.edu.tw/app/index.php?Action=mobileloadmod&Type=mobile_asso_cg_mstr&Nbr=1021",
                "行政公告" to "https://www.nttu.edu.tw/app/index.php?Action=mobileloadmod&Type=mobile_asso_cg_mstr&Nbr=1010",
                "學術公告" to "https://www.nttu.edu.tw/app/index.php?Action=mobileloadmod&Type=mobile_asso_cg_mstr&Nbr=1012",
                "徵人啟事" to "https://www.nttu.edu.tw/app/index.php?Action=mobileloadmod&Type=mobile_asso_cg_mstr&Nbr=1011",
                "招生放榜" to "https://www.nttu.edu.tw/app/index.php?Action=mobileloadmod&Type=mobile_asso_cg_mstr&Nbr=1013",
                "媒體報導" to "https://www.nttu.edu.tw/app/index.php?Action=mobileloadmod&Type=asso_share&Nbr=10"
            )

            for ((category, url) in tabUrls) {
                try {
                    Log.d("fetchAnnouncementsData", "Fetching tab: $category from $url")
                    // 連線到該分類的 AJAX 網址
                    val tabDoc = Jsoup.connect(url).userAgent(userAgent).timeout(10000).get()

                    // 這些 AJAX 頁面的 HTML 結構剛好都一樣
                    val items = tabDoc.select("div.d-item")
                    Log.d("fetchAnnouncementsData", "Found ${items.size} '$category' items (before date filter)")

                    for (item in items) {
                        val aTag = item.selectFirst("div.mtitle a")
                        val dateTag = item.selectFirst("i.mdate.after")
                        val title = aTag?.text()?.trim()
                        val itemUrl = aTag?.attr("abs:href")
                        val dateStr = dateTag?.text()?.trim()

                        if (title.isNullOrEmpty() || itemUrl.isNullOrEmpty() || dateStr.isNullOrEmpty()) continue

                        val date = parseDate(dateStr)
                        if (date != null && (date.isAfter(cutoffDate) || date.isEqual(cutoffDate))) {
                            allAnnouncements.add(
                                Announcement(url = itemUrl, title = title, category = category, date = date)
                            )
                        }
                    }
                } catch (e: Exception) {
                    Log.e("fetchAnnouncementsData", "Error fetching tab $category", e)
                    // 即使一個分類失敗了，也繼續抓下一個
                }
            }

            Log.d("fetchAnnouncementsData", "Finished fetching. Total items: ${allAnnouncements.size}")
            // 回傳並依照日期排序
            allAnnouncements.sortedByDescending { it.date }

        } catch (e: Exception) {
            Log.e("fetchAnnouncementsData", "Error fetching main page", e)
            emptyList<Announcement>() // 發生錯誤時回傳空 List
        }
    }
}