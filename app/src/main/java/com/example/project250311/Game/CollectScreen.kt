// 檔案路徑: com/example/project250311/Game/CollectScreen.kt
package com.example.project250311.Game

import androidx.compose.foundation.Image // (★) 新增：Image 元件
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale // (★) 新增：控制圖片縮放
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.ranges.coerceIn

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectScreen(
    checkpointName: String,
    initialDepth: Float,
    stampImageRes: Int? = null, // 接收圖片資源 ID
    onSaveStamp: (Float) -> Unit,
    onBack: () -> Unit
) {
    var currentDepth by remember { mutableStateOf(initialDepth) }
    var isPressing by remember { mutableStateOf(false) }
    var pressStartTime by remember { mutableStateOf(0L) }
    val scope = rememberCoroutineScope()
    val pressJob = remember { mutableStateOf<Job?>(null) }

    // 這是給向量圖示用的顏色邏輯
    val stampColor = MaterialTheme.colorScheme.primary
    val finalStampTint = if (currentDepth == 0f) Color.Gray.copy(alpha = 0.2f)
    else stampColor.copy(alpha = currentDepth)

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(checkpointName, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {

            if (initialDepth == 0f) {
                Text(
                    text = if (isPressing) "持續按壓來加深顏色..." else "請長按圖章來蓋章！",
                    fontSize = 18.sp,
                    style = MaterialTheme.typography.titleMedium
                )
            } else {
                Text(
                    text = "你已經收集過這個印章了！",
                    fontSize = 18.sp,
                    style = MaterialTheme.typography.titleMedium
                )
            }
            Spacer(modifier = Modifier.height(40.dp))

            // 共用的 Modifier，處理點擊手勢
            val gestureModifier = Modifier
                .size(250.dp)
                .pointerInput(initialDepth) {
                    detectTapGestures(
                        onPress = {
                            if (initialDepth > 0f) return@detectTapGestures
                            pressStartTime = System.currentTimeMillis()
                            isPressing = true
                            pressJob.value?.cancel()
                            pressJob.value = scope.launch {
                                while (isPressing) {
                                    val duration = System.currentTimeMillis() - pressStartTime
                                    // 3秒內達到最大深度
                                    currentDepth = (duration / 3000f).coerceIn(0.0f, 1.0f)
                                    delay(50)
                                }
                            }
                            try {
                                tryAwaitRelease()
                            } finally {
                                isPressing = false
                                pressJob.value?.cancel()
                                val duration = System.currentTimeMillis() - pressStartTime
                                currentDepth = (duration / 3000f).coerceIn(0.0f, 1.0f)
                            }
                        }
                    )
                }

            // (★) 修改處：根據是否傳入圖片資源決定使用 Image 或 Icon
            if (stampImageRes != null) {
                // 如果有指定圖片：使用 Image 顯示原圖，並用 alpha 控制透明度

                // 計算 alpha 值：
                // 1. 如果完全沒蓋過 (currentDepth == 0)，顯示淡淡的預覽 (0.3f)
                // 2. 如果正在蓋或已蓋過，則使用 currentDepth (越深越不透明)
                val imageAlpha = if (currentDepth == 0f) 0.3f else currentDepth

                Image(
                    painter = painterResource(id = stampImageRes),
                    contentDescription = "Stamp",
                    modifier = gestureModifier, // 套用手勢
                    alpha = imageAlpha,         // 控制透明度
                    contentScale = ContentScale.Fit // 保持比例
                )
            } else {
                // 如果沒有圖片：使用預設向量圖示 (Icon)，維持原本的 Tint 行為
                Icon(
                    imageVector = Icons.Filled.Verified,
                    contentDescription = "Stamp",
                    modifier = gestureModifier, // 套用手勢
                    tint = finalStampTint       // 控制顏色與透明度
                )
            }

            Spacer(modifier = Modifier.height(40.dp))

            Button(
                onClick = {
                    onSaveStamp(currentDepth)
                    onBack()
                },
                enabled = (initialDepth == 0f && currentDepth > 0f)
            ) {
                Text("儲存並返回")
            }
        }
    }
}