package com.example.project250311.Game

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.project250311.Data.CourseViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameMissionScreen(
    navController: NavController,
    courseViewModel: CourseViewModel,
    gameViewModel: GameViewModel
) {
    val context = LocalContext.current
    val userId = remember { GameManager.getUserId(context) }

    // --- 新增：讀取 SharedPreferences 判斷是否做過前測 ---
    // 使用 SharedPreferences 確保 APP 重啟後不會忘記已經填過
    val prefs = remember { context.getSharedPreferences("game_prefs", Context.MODE_PRIVATE) }
    var isPreSurveyDone by remember {
        mutableStateOf(prefs.getBoolean("pre_survey_done", false))
    }

    // 筆記選課對話框狀態
    var showCourseSelectionDialog by remember { mutableStateOf(false) }
    val allCourses by courseViewModel.allCourses.observeAsState(emptyList())

    val missions by gameViewModel.missions.collectAsState()

    // 進入畫面時記錄一下
    LaunchedEffect(Unit) {
        GameManager.logEvent(context, "game_mode_start", mapOf("screen" to "GameMissionScreen"))
        courseViewModel.loadAllCourses()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("校園探索任務") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                actions = {
                    // 顯示 User ID (方便測試時核對)
                    Text(
                        text = "ID: ${userId.take(4)}...",
                        fontSize = 10.sp,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // --- 判斷：如果還沒做前測，顯示強制引導對話框 ---
            if (!isPreSurveyDone) {
                AlertDialog(
                    onDismissRequest = { /* 禁止點擊外部關閉，強制填寫 */ },
                    title = { Text("歡迎來到遊戲模式！") },
                    text = {
                        Text("在開始探索校園之前，請先幫我們填寫一份簡短的「前測問卷」。\n\n這將幫助我們建立基準數據，填寫完畢後即可開始遊戲。")
                    },
                    icon = { Icon(Icons.Default.AssignmentInd, contentDescription = null) },
                    confirmButton = {
                        Button(
                            onClick = {
                                // 1. 開啟 CCT 問卷 (請確保 SurveyUtils.kt 已建立)
                                SurveyUtils.launchPreSurvey(context)

                                // 2. 標記為已完成
                                // 我們假設使用者點擊後會去填寫，返回 APP 後直接進入遊戲
                                prefs.edit().putBoolean("pre_survey_done", true).apply()
                                isPreSurveyDone = true

                                // 記錄事件
                                GameManager.logEvent(context, "pre_survey_click")
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("前往填寫問卷")
                        }
                    }
                )
            } else {
                // --- 原本的遊戲內容 (只有在前測完成後顯示) ---
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    // 進度條與說明
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                "歡迎來到遊戲模式！",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "請完成以下任務來體驗智慧校園功能。完成後將解鎖最終問卷。",
                                fontSize = 14.sp
                            )

                            Spacer(modifier = Modifier.height(12.dp))
                            val completedCount = missions.count { it.isCompleted }
                            LinearProgressIndicator(
                                progress = if (missions.isNotEmpty()) completedCount / missions.size.toFloat() else 0f,
                                modifier = Modifier.fillMaxWidth().height(8.dp),
                                trackColor = Color.White
                            )
                            Text(
                                "進度: $completedCount / ${missions.size}",
                                modifier = Modifier.align(Alignment.End),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }

                    // 任務列表
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(missions) { mission ->
                            MissionItem(
                                mission = mission,
                                onClick = {
                                    // 記錄點擊事件
                                    GameManager.logEvent(
                                        context,
                                        "mission_click",
                                        mapOf(
                                            "mission_id" to mission.id,
                                            "mission_title" to mission.title
                                        )
                                    )

                                    when (mission.id) {
                                        "3" -> {
                                            // 定位任務：跳轉到地圖 (帶參數告知是遊戲模式)
                                            // 注意：這部分需要在 MapScreen 裡處理參數，或者單純跳轉
                                            navController.navigate("map_game")

                                            // 定位任務通常要真的掃描到才算完成，這裡先不自動打勾
                                            // 如果你想簡單測試，可以把下面這行打開：
                                            // markMissionAsComplete(missions, "4")
                                        }

                                        else -> {
                                            // 一般任務：直接跳轉
                                            navController.navigate(mission.route)
                                            // 假設點擊去體驗就算完成
                                            gameViewModel.completeMission(mission.id)
                                        }
                                    }
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // 全部完成後的按鈕 (前往後測問卷)
                    // 這裡使用 enabled 控制，只有全部完成才能點
                    Button(
                        onClick = {
                            GameManager.logEvent(context, "game_complete_click")

                            // 呼叫後測問卷 (透過 SurveyUtils)
                            SurveyUtils.launchPostSurvey(context)

                            Toast.makeText(
                                context,
                                "感謝遊玩！請協助填寫後測問卷。",
                                Toast.LENGTH_LONG
                            ).show()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = missions.all { it.isCompleted }
                    ) {
                        Text("完成任務並填寫回饋")
                    }
                }
            }
        }
    }
}
// 輔助函式：標記任務為完成
fun markMissionAsComplete(missions: MutableList<Mission>, id: String) {
    val index = missions.indexOfFirst { it.id == id }
    if (index != -1) {
        missions[index] = missions[index].copy(isCompleted = true)
    }
}

@Composable
fun MissionItem(mission: Mission, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = if (mission.isCompleted) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = mission.icon,
                contentDescription = null,
                tint = if (mission.isCompleted) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = mission.title,
                    style = MaterialTheme.typography.titleMedium,
                    textDecoration = if (mission.isCompleted) TextDecoration.LineThrough else null
                )
                Text(
                    text = mission.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (mission.isCompleted) {
                Icon(Icons.Default.CheckCircle, contentDescription = "Completed", tint = MaterialTheme.colorScheme.primary)
            } else {
                Icon(Icons.Default.ChevronRight, contentDescription = "Go", tint = Color.Gray)
            }
        }
    }
}