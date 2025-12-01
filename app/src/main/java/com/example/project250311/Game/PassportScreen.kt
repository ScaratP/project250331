// 檔案路徑: com/example/project250311/Game/PassportScreen.kt
package com.example.project250311.Game

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.project250311.R // 記得確認 R 的 import 正確

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PassportScreen(
    viewModel: GameViewModel,
    onBack: () -> Unit,
    onStampClick: (String) -> Unit // 點擊已收集印章的回呼
) {
    // 訂閱資料
    val uiState by viewModel.uiState.collectAsState()

    // 計算進度
    val totalStamps = uiState.size
    val collectedCount = uiState.count { it.status == CheckpointStatus.COLLECTED }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("我的集點護照", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // --- 進度條區域 ---
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("收集進度", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "$collectedCount / $totalStamps",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { if (totalStamps > 0) collectedCount / totalStamps.toFloat() else 0f },
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                    )
                }
            }

            // --- 印章網格列表 ---
            LazyVerticalGrid(
                columns = GridCells.Fixed(2), // 一行兩個
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(uiState) { item ->
                    StampGridItem(
                        item = item,
                        onClick = {
                            if (item.status == CheckpointStatus.COLLECTED) {
                                onStampClick(item.checkpoint.id)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun StampGridItem(
    item: UiCheckpointState,
    onClick: () -> Unit
) {
    val isCollected = item.status == CheckpointStatus.COLLECTED
    val backgroundColor = if (isCollected) Color.White else Color(0xFFEEEEEE)
    val borderColor = if (isCollected) MaterialTheme.colorScheme.primary else Color.Gray

    Card(
        modifier = Modifier
            .aspectRatio(1f) // 正方形卡片
            .clickable(enabled = isCollected, onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isCollected) 4.dp else 0.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        border = androidx.compose.foundation.BorderStroke(2.dp, borderColor) // 加個邊框更有質感
    ) {
        Box(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            if (isCollected) {
                // 已收集：顯示圖片 (根據深度調整透明度)
                val alpha = item.progress.colorDepth.coerceIn(0.2f, 1.0f)

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (item.checkpoint.stampImageRes != null) {
                        Image(
                            painter = painterResource(id = item.checkpoint.stampImageRes),
                            contentDescription = null,
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            contentScale = ContentScale.Fit,
                            alpha = alpha
                        )
                    } else {
                        // 如果沒有圖片資源，顯示勾勾
                        Icon(
                            painter = painterResource(id = R.drawable.ic_launcher_foreground), // 或其他預設圖
                            contentDescription = null,
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = alpha)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        item.checkpoint.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
            } else {
                // 未收集：顯示鎖頭和地點
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Locked",
                        modifier = Modifier.size(48.dp),
                        tint = Color.Gray
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        item.checkpoint.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "未獲得",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.LightGray
                    )
                }
            }
        }
    }
}