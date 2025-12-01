package com.example.project250311.Onboarding

import android.Manifest
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

// 導覽頁資料
data class OnboardingPage(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val iconColor: Color
)

// 用戶類型枚舉
enum class UserType {
    VISITOR,
    STUDENT,
    GAME_PLAYER // (★) 確保這裡有 GAME_PLAYER
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    showGameMode: Boolean = true,
    onComplete: (UserType) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var currentPage by remember { mutableStateOf(0) }
    var selectedUserType by remember { mutableStateOf<UserType?>(null) }

    // (★) 1. 定義要請求的權限列表
    val permissionsToRequest = remember {
        val list = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.CAMERA
        )
        // Android 13 (TIRAMISU) 以上才需要動態申請通知權限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            list.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        list.toTypedArray()
    }

    // (★) 2. 建立權限請求啟動器
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // 這裡可以處理權限結果，例如如果有權限被拒絕，顯示提示
        val locationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true

        if (locationGranted) {
            // (選擇性) Toast.makeText(context, "定位權限已取得", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "部分功能需要定位權限才能正常運作", Toast.LENGTH_LONG).show()
        }
    }

    // (★) 3. 一進入畫面就請求權限
    LaunchedEffect(Unit) {
        permissionLauncher.launch(permissionsToRequest)
    }

    val pages =
        listOf(
            OnboardingPage(
                title = "歡迎使用 PAMUTT",
                description = "台東大學專屬的智慧校園助理，讓您的校園生活更便利",
                icon = Icons.Default.School,
                iconColor = Color(0xFF6200EE)
            ),
            OnboardingPage(
                title = "室內外地圖導航",
                description = "精準的校園地圖與路線規劃，幫助您快速找到目的地",
                icon = Icons.Default.Map,
                iconColor = Color(0xFF03DAC5)
            ),
            OnboardingPage(
                title = "學餐轉盤選擇",
                description = "不知道吃什麼？讓轉盤幫您決定今天的美食",
                icon = Icons.Default.Restaurant,
                iconColor = Color(0xFFFF6B6B)
            ),
            OnboardingPage(
                title = "選擇您的身份",
                description = "請選擇訪客或學生模式以開始使用",
                icon = Icons.Default.Person,
                iconColor = Color(0xFF4CAF50)
            )
        )

    // 動畫效果
    val animatedOffset by
    animateFloatAsState(
        targetValue = -currentPage.toFloat(),
        animationSpec =
            spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            ),
        label = "page_offset"
    )

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
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // 頁面內容
            AnimatedContent(
                targetState = currentPage,
                transitionSpec = {
                    slideInHorizontally { width -> width } + fadeIn() togetherWith
                            slideOutHorizontally { width -> -width } + fadeOut()
                },
                label = "page_content"
            ) { page -> OnboardingPageContent(page = pages[page], modifier = Modifier.weight(1f)) }

            // 身份選擇（僅在最後一頁顯示）
            AnimatedVisibility(
                visible = currentPage == pages.size - 1,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    UserTypeCard(
                        title = "訪客模式",
                        description = "體驗地圖導航、學餐轉盤等功能",
                        icon = Icons.Default.PersonOutline,
                        isSelected = selectedUserType == UserType.VISITOR,
                        onClick = { selectedUserType = UserType.VISITOR }
                    )

                    UserTypeCard(
                        title = "學生模式",
                        description = "解鎖課表、請假、筆記、通知等完整功能",
                        icon = Icons.Default.Person,
                        isSelected = selectedUserType == UserType.STUDENT,
                        onClick = { selectedUserType = UserType.STUDENT }
                    )

                    if(showGameMode) {
                        // (★) 新增：遊戲模式選項
                        UserTypeCard(
                            title = "遊戲模式 (專題測試)",
                            description = "體驗智慧校園任務，協助我們收集數據",
                            icon = Icons.Default.SportsEsports, // 記得確認你有這個 icon，如果沒有可以換別的
                            isSelected = selectedUserType == UserType.GAME_PLAYER,
                            onClick = { selectedUserType = UserType.GAME_PLAYER }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // 頁面指示器
            if (currentPage < pages.size - 1) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(vertical = 16.dp)
                ) {
                    repeat(pages.size - 1) { index ->
                        Box(
                            modifier =
                                Modifier.padding(horizontal = 4.dp)
                                    .size(if (index == currentPage) 12.dp else 8.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (index == currentPage)
                                            MaterialTheme.colorScheme.primary
                                        else
                                            MaterialTheme.colorScheme.primary
                                                .copy(alpha = 0.3f)
                                    )
                        )
                    }
                }
            }

            // 按鈕
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (currentPage > 0 && currentPage < pages.size - 1) {
                    TextButton(onClick = { currentPage-- }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "上一頁")
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("上一頁")
                    }
                } else {
                    Spacer(modifier = Modifier.width(100.dp))
                }

                if (currentPage < pages.size - 1) {
                    Button(onClick = { currentPage++ }, shape = RoundedCornerShape(24.dp)) {
                        Text("下一步")
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(Icons.Default.ArrowForward, contentDescription = "下一步")
                    }
                } else {
                    Button(
                        onClick = {
                            selectedUserType?.let { userType ->
                                saveUserType(context, userType)
                                onComplete(userType)
                            }
                        },
                        enabled = selectedUserType != null,
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Text("開始使用")
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(Icons.Default.Check, contentDescription = "完成")
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun OnboardingPageContent(page: OnboardingPage, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // 圖標
        Box(
            modifier =
                Modifier.size(120.dp)
                    .clip(CircleShape)
                    .background(page.iconColor.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = page.icon,
                contentDescription = page.title,
                modifier = Modifier.size(64.dp),
                tint = page.iconColor
            )
        }

        Spacer(modifier = Modifier.height(48.dp))

        // 標題
        Text(
            text = page.title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 描述
        Text(
            text = page.description,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            modifier = Modifier.padding(horizontal = 32.dp)
        )
    }
}

@Composable
fun UserTypeCard(
    title: String,
    description: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(120.dp),
        shape = RoundedCornerShape(16.dp),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    if (isSelected) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surface
            ),
        border =
            if (isSelected)
                androidx.compose.foundation.BorderStroke(
                    2.dp,
                    MaterialTheme.colorScheme.primary
                )
            else null,
        elevation =
            CardDefaults.cardElevation(defaultElevation = if (isSelected) 8.dp else 2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier =
                    Modifier.size(56.dp)
                        .clip(CircleShape)
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant
                        ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    modifier = Modifier.size(32.dp),
                    tint =
                        if (isSelected) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color =
                        if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color =
                        if (isSelected)
                            MaterialTheme.colorScheme.onPrimaryContainer.copy(
                                alpha = 0.8f
                            )
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }

            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "已選擇",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

// SharedPreferences 輔助函數
fun saveUserType(context: Context, userType: UserType) {
    val prefs = context.getSharedPreferences("onboarding_prefs", Context.MODE_PRIVATE)
    prefs.edit().apply {
        putBoolean("onboarding_completed", true)
        putString("user_type", userType.name)
        apply()
    }
}

fun isOnboardingCompleted(context: Context): Boolean {
    val prefs = context.getSharedPreferences("onboarding_prefs", Context.MODE_PRIVATE)
    return prefs.getBoolean("onboarding_completed", false)
}

fun getUserType(context: Context): UserType {
    val prefs = context.getSharedPreferences("onboarding_prefs", Context.MODE_PRIVATE)
    val typeString = prefs.getString("user_type", UserType.VISITOR.name)
    return try {
        UserType.valueOf(typeString ?: UserType.VISITOR.name)
    } catch (e: Exception) {
        UserType.VISITOR
    }
}