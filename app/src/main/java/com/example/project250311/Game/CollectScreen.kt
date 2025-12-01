// 檔案路徑: com/example/project250311/Game/CollectScreen.kt
package com.example.project250311.Game

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectScreen(
    checkpointName: String,
    initialDepth: Float,
    stampImageRes: Int? = null, // 接收圖片資源 ID
    onSaveStamp: (Float) -> Unit,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()

    // --- 狀態管理 ---
    // 判斷是否已經蓋過章 (如果有 initialDepth 代表是來看回顧的)
    val isAlreadyCollected = initialDepth > 0f

    // 完成狀態 (本次操作是否完成)
    var isMissionCompleted by remember { mutableStateOf(false) }

    // --- 動畫數值 (核心) ---
    // Alpha: 控制顏色深淺。如果是回顧模式，直接設為 initialDepth；否則從 0.1 (淡淡的) 開始
    val alphaAnim = remember { Animatable(if (isAlreadyCollected) initialDepth else 0.1f) }

    // Scale: 控制圖片縮放。預設 1.0，按下去變小，放開彈回
    val scaleAnim = remember { Animatable(1.0f) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(checkpointName, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) // 讓 TopBar稍微透一點
                )
            )
        }
    ) { paddingValues ->

        // 使用 Box 填滿螢幕，作為觸控感應區
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color.White) // 背景色，模擬紙張
                .pointerInput(Unit) {
                    // 如果已經收集過，或是剛剛才收集完，就不再偵測手勢
                    if (isAlreadyCollected || isMissionCompleted) return@pointerInput

                    detectTapGestures(
                        onPress = {
                            // --- 1. 手指按下去 (Start) ---
                            // 圖片縮小效果 (模擬用力壓)
                            // (★) 修正：加上 scope.
                            scope.launch {
                                scaleAnim.animateTo(0.85f, animationSpec = tween(200))
                            }

                            // 顏色變深 (模擬墨水滲透) - 設定 2.5秒 達到最深
                            // (★) 修正：加上 scope.
                            scope.launch {
                                alphaAnim.animateTo(
                                    targetValue = 1.0f,
                                    animationSpec = tween(durationMillis = 2500, easing = LinearEasing)
                                )
                            }

                            // --- 等待手指放開 ---
                            // tryAwaitRelease 是 PressGestureScope 的函式，所以不用加 scope.，直接呼叫即可
                            tryAwaitRelease()

                            // --- 2. 手指放開 (End) ---
                            // 停止變深，記錄當前數值
                            val finalDepth = alphaAnim.value
                            isMissionCompleted = true

                            // 圖片回彈效果 (ㄉㄨㄞㄉㄨㄞ的感覺)
                            // (★) 修正：加上 scope.
                            scope.launch {
                                scaleAnim.animateTo(
                                    targetValue = 1.0f,
                                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
                                )
                            }

                            // 執行儲存
                            onSaveStamp(finalDepth)

                            // 延遲一點點時間讓使用者看到成果，然後退出 (可選)
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {

            // --- 內容顯示區 ---
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {

                // 提示文字
                Text(
                    text = when {
                        isAlreadyCollected -> "已收集完畢"
                        isMissionCompleted -> "蓋章成功！"
                        else -> "長按螢幕任意處\n按越久顏色越深！"
                    },
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isMissionCompleted) MaterialTheme.colorScheme.primary else Color.Gray,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(bottom = 48.dp)
                )

                // --- 印章本體 ---
                Box(contentAlignment = Alignment.Center) {
                    if (stampImageRes != null) {
                        // 使用圖片
                        Image(
                            painter = painterResource(id = stampImageRes),
                            contentDescription = "Stamp",
                            modifier = Modifier
                                .size(300.dp)
                                .scale(scaleAnim.value) // 綁定縮放動畫
                                .alpha(alphaAnim.value), // 綁定透明度動畫
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        // 使用預設圖示
                        Icon(
                            imageVector = Icons.Filled.Verified,
                            contentDescription = "Stamp",
                            modifier = Modifier
                                .size(250.dp)
                                .scale(scaleAnim.value),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = alphaAnim.value)
                        )
                    }
                }

                // 為了視覺平衡加的 Spacer
                Spacer(modifier = Modifier.height(60.dp))
            }

            // 如果任務完成，顯示一個小小的打勾或提示
            if (isMissionCompleted) {
                // 這裡可以加特效
            }
        }
    }
}