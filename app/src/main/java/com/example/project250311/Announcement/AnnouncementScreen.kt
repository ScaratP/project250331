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
import androidx.compose.foundation.lazy.rememberLazyListState // +++ 1. 匯入 LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
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
import kotlinx.coroutines.launch // +++ 2. 匯入 coroutine launch
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 主畫面 Composable
 */
@Composable
fun AnnouncementScreen(viewModel: AnnouncementViewModel) {
    // 觀察 ViewModel 中的 LiveData
    val announcements by viewModel.announcements.observeAsState(emptyList())
    val isLoading by viewModel.isLoading.observeAsState(false)
    val errorMessage by viewModel.errorMessage.observeAsState(null)
    val selectedCategory by viewModel.selectedCategory.observeAsState("全部") // 監聽分類

    val context = LocalContext.current

    // +++ 3. 建立 LazyListState 和 CoroutineScope +++
    val lazyListState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // 第一次啟動時，會執行一次
    LaunchedEffect(Unit) {
        viewModel.loadAnnouncementsFromDb()
        viewModel.refreshAnnouncementsFromWeb(::fetchAnnouncementsData)
    }

    // +++ 4. 建立一個 Effect，當 selectedCategory 改變時，滾動到頂部 +++
    LaunchedEffect(selectedCategory) {
        coroutineScope.launch {
            // 用動畫滾動到最上面
            lazyListState.animateScrollToItem(0)
        }
    }

    Scaffold(
        topBar = {
            AnnouncementTopBar(
                viewModel = viewModel,
                onRefresh = {
                    viewModel.refreshAnnouncementsFromWeb(::fetchAnnouncementsData)
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
                // 狀態 1: 正在載入中
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
                    val text = if(selectedCategory == "全部")
                        "目前沒有最近一個月的公告"
                    else
                        "「$selectedCategory」分類下沒有最近的公告"
                    Text(
                        text = text,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(16.dp),
                        textAlign = TextAlign.Center
                    )
                }

                // 狀態 4: 成功載入公告
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 8.dp),
                        state = lazyListState // +++ 5. 把 state 交給 LazyColumn +++
                    ) {
                        items(announcements, key = { it.url }) { announcement ->
                            AnnouncementItem(
                                announcement = announcement,
                                onClick = {
                                    try {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(announcement.url))
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
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
 * 客製化的 TopAppBar
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnnouncementTopBar(
    viewModel: AnnouncementViewModel,
    onRefresh: () -> Unit
) {
    val selectedCategory by viewModel.selectedCategory.observeAsState("全部")
    val lastRefreshed by viewModel.lastRefreshedTime.observeAsState(null)
    val isLoading by viewModel.isLoading.observeAsState(false)

    val categories = listOf("全部", "重要消息", "校內活動", "行政公告", "學術公告", "徵人啟事", "招生放榜")
    var showCategoryMenu by remember { mutableStateOf(false) }

    TopAppBar(
        title = {
            Column(
                modifier = Modifier.padding(end = 8.dp)
            ) {
                // +++ 6. 用 Box 把按鈕和選單包起來 +++
                Box(
                    contentAlignment = Alignment.CenterStart // 讓選單對齊左邊
                ) {
                    // 這是分類按鈕
                    Row(
                        modifier = Modifier.clickable { showCategoryMenu = true },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(selectedCategory, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Icon(Icons.Default.ArrowDropDown, contentDescription = "選擇分類")
                    }

                    // 這是下拉式選單
                    DropdownMenu(
                        expanded = showCategoryMenu,
                        onDismissRequest = { showCategoryMenu = false }
                    ) {
                        categories.forEach { category ->
                            DropdownMenuItem(
                                text = { Text(category) },
                                onClick = {
                                    viewModel.selectCategory(category)
                                    showCategoryMenu = false
                                }
                            )
                        }
                    }
                } // +++ Box 結束 +++

                // 上次刷新時間
                lastRefreshed?.let {
                    Text(
                        text = "上次刷新: ${it.format(DateTimeFormatter.ofPattern("HH:mm:ss"))}",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        },
        actions = {
            // 刷新按鈕
            IconButton(onClick = onRefresh, enabled = !isLoading) {
                if (isLoading) {
                    CircularProgressIndicator(Modifier.size(24.dp))
                } else {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
    )
}


/**
 * 顯示單一公告項目的 Composable
 */
@Composable
fun AnnouncementItem(announcement: Announcement, onClick: () -> Unit) {
    // (這部分保持不變)
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
            Text(
                text = announcement.category,
                color = MaterialTheme.colorScheme.primary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = announcement.title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = announcement.date.toString(),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.align(Alignment.End)
            )
        }
    }
}

// --- 以下是爬蟲邏輯 ---
// (這部分保持不變)

private fun parseDate(dateStr: String): LocalDate? {
    return try {
        LocalDate.parse(dateStr.trim(), DateTimeFormatter.ISO_LOCAL_DATE)
    } catch (e: Exception) {
        null
    }
}

suspend fun fetchAnnouncementsData(): List<Announcement> {
    return withContext(Dispatchers.IO) {
        val allAnnouncements = mutableListOf<Announcement>()
        val cutoffDate = LocalDate.now().minusMonths(1)
        val baseUrl = "https://www.nttu.edu.tw/"
        val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/58.0.3029.110 Safari/537.36"

        try {
            Log.d("fetchAnnouncementsData", "Start fetching from $baseUrl")
            val doc = Jsoup.connect(baseUrl)
                .userAgent(userAgent)
                .timeout(10000)
                .get()

            // --- 區塊 1: 重要消息 ---
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

            // --- 區塊 2: Tab 裡面的內容 ---
            val tabUrls = mapOf(
                "校內活動" to "https://www.nttu.edu.tw/app/index.php?Action=mobileloadmod&Type=mobile_asso_cg_mstr&Nbr=1021",
                "行政公告" to "https://www.nttu.edu.tw/app/index.php?Action=mobileloadmod&Type=mobile_asso_cg_mstr&Nbr=1010",
                "學術公告" to "https://www.nttu.edu.tw/app/index.php?Action=mobileloadmod&Type=mobile_asso_cg_mstr&Nbr=1012",
                "徵人啟事" to "https://www.nttu.edu.tw/app/index.php?Action=mobileloadmod&Type=mobile_asso_cg_mstr&Nbr=1011",
                "招生放榜" to "https://www.nttu.edu.tw/app/index.php?Action=mobileloadmod&Type=mobile_asso_cg_mstr&Nbr=1013",
            )

            for ((category, url) in tabUrls) {
                try {
                    Log.d("fetchAnnouncementsData", "Fetching tab: $category from $url")
                    val tabDoc = Jsoup.connect(url).userAgent(userAgent).timeout(10000).get()

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
                }
            }

            Log.d("fetchAnnouncementsData", "Finished fetching. Total items: ${allAnnouncements.size}")
            allAnnouncements.sortedByDescending { it.date }

        } catch (e: Exception) {
            Log.e("fetchAnnouncementsData", "Error fetching main page", e)
            emptyList<Announcement>()
        }
    }
}