package com.example.project250311.Onboarding

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentSetupScreen(
        onComplete: (wantSchedule: Boolean, wantNotifications: Boolean) -> Unit,
        onSkip: () -> Unit
) {
    val context = LocalContext.current
    var wantSchedule by remember { mutableStateOf(false) }
    var wantNotifications by remember { mutableStateOf(false) }

    Box(
            modifier =
                    Modifier.fillMaxSize()
                            .background(
                                    Brush.verticalGradient(
                                            colors =
                                                    listOf(
                                                            Color(0xFF6200EE).copy(alpha = 0.1f),
                                                            Color(0xFF03DAC5).copy(alpha = 0.1f)
                                                    )
                                    )
                            )
    ) {
        Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(48.dp))

            // 標題區
            Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "設定",
                    modifier = Modifier.size(80.dp),
                    tint = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                    text = "初始設定",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                    text = "設定您的學習助理",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
            )

            Spacer(modifier = Modifier.height(48.dp))

            // 選項卡片
            Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                SetupOptionCard(
                        title = "同步課表",
                        description = "自動從學校系統獲取您的課程安排",
                        icon = Icons.Default.CalendarToday,
                        isChecked = wantSchedule,
                        onCheckedChange = { wantSchedule = it }
                )

                SetupOptionCard(
                        title = "開啟課程通知",
                        description = "在上課前收到提醒通知，不錯過任何課程",
                        icon = Icons.Default.Notifications,
                        isChecked = wantNotifications,
                        onCheckedChange = { wantNotifications = it }
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // 提示文字
            Text(
                    text = "您可以隨時在設定中修改這些選項",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // 按鈕區
            Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                        onClick = onSkip,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(24.dp)
                ) { Text("稍後設定") }

                Button(
                        onClick = {
                            saveStudentSetup(context, wantSchedule, wantNotifications)
                            onComplete(wantSchedule, wantNotifications)
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(24.dp)
                ) { Text("完成") }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun SetupOptionCard(
        title: String,
        description: String,
        icon: androidx.compose.ui.graphics.vector.ImageVector,
        isChecked: Boolean,
        onCheckedChange: (Boolean) -> Unit
) {
    Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors =
                    CardDefaults.cardColors(
                            containerColor =
                                    if (isChecked) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surface
                    ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                    imageVector = icon,
                    contentDescription = title,
                    modifier = Modifier.size(40.dp),
                    tint =
                            if (isChecked) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Switch(checked = isChecked, onCheckedChange = onCheckedChange)
        }
    }
}

fun saveStudentSetup(context: Context, wantSchedule: Boolean, wantNotifications: Boolean) {
    val prefs = context.getSharedPreferences("onboarding_prefs", Context.MODE_PRIVATE)
    prefs.edit().apply {
        putBoolean("setup_completed", true)
        putBoolean("want_schedule", wantSchedule)
        putBoolean("want_notifications", wantNotifications)
        apply()
    }
}

fun isStudentSetupCompleted(context: Context): Boolean {
    val prefs = context.getSharedPreferences("onboarding_prefs", Context.MODE_PRIVATE)
    return prefs.getBoolean("setup_completed", false)
}
