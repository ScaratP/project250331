package com.example.project250311

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.project250311.Announcement.AnnouncementScreen
import com.example.project250311.Data.AnnouncementRepository
import com.example.project250311.Data.AnnouncementViewModel
import com.example.project250311.Data.AppDatabase
import com.example.project250311.Data.CourseRepository
import com.example.project250311.Data.CourseViewModel
import com.example.project250311.Map.IndoorMap.IndoorMapScreen
import com.example.project250311.Map.MapScreen
import com.example.project250311.Onboarding.*
import com.example.project250311.Schedule.Food.FoodScreen
import com.example.project250311.Schedule.GetSchedule.ScheduleScreen
import com.example.project250311.Schedule.NoSchool.LeaveSystemScreen
import com.example.project250311.Schedule.Note.EnhancedNoteScreen
import com.example.project250311.Schedule.Note.NoteListScreen
import com.example.project250311.Schedule.Notice.NotificationManagerScreen
import com.example.project250311.ui.theme.Project250311Theme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import com.example.project250311.Map.IndoorMap.LocationViewModel
// (★) 新增 Import
import com.example.project250311.Game.QrcodeScreen
import com.example.project250311.Game.CollectScreen

class MainActivity : ComponentActivity() {
    private val courseViewModel: CourseViewModel by viewModels {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val db = AppDatabase.getDatabase(applicationContext)
                val repository = CourseRepository(db.courseDao())
                @Suppress("UNCHECKED_CAST") return CourseViewModel(repository) as T
            }
        }
    }

    private val announcementViewModel: AnnouncementViewModel by viewModels {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val db = AppDatabase.getDatabase(applicationContext)
                val repository = AnnouncementRepository(db.announcementDao())
                @Suppress("UNCHECKED_CAST") return AnnouncementViewModel(repository) as T
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createNotificationChannel(this)

        setContent {
            Project250311Theme {
                // 檢查是否完成導覽
                val isOnboardingDone = remember { isOnboardingCompleted(this@MainActivity) }
                val userType = remember {
                    if (isOnboardingDone) getUserType(this@MainActivity) else null
                }
                val isSetupDone = remember {
                    if (userType == UserType.STUDENT) isStudentSetupCompleted(this@MainActivity)
                    else true
                }

                var showOnboarding by remember { mutableStateOf(!isOnboardingDone) }
                var showStudentSetup by remember { mutableStateOf(false) }
                var currentUserType by remember { mutableStateOf(userType) }

                when {
                    showOnboarding -> {
                        OnboardingScreen(
                            onComplete = { selectedUserType ->
                                currentUserType = selectedUserType
                                showOnboarding = false

                                if (selectedUserType == UserType.STUDENT && !isSetupDone) {
                                    showStudentSetup = true
                                }
                            }
                        )
                    }
                    showStudentSetup -> {
                        StudentSetupScreen(
                            onComplete = { wantSchedule, wantNotifications ->
                                showStudentSetup = false
                            },
                            onSkip = {
                                showStudentSetup = false
                            }
                        )
                    }
                    else -> {
                        val startDestination =
                            when {
                                intent?.getBooleanExtra("OPEN_SCHEDULE", false) == true ->
                                    "schedule"
                                intent?.getBooleanExtra("OPEN_LEAVE", false) == true -> "leave"
                                currentUserType == UserType.GAME_PLAYER -> "game_mission"
                                else -> "map"
                            }

                        AppWithNavigation(
                            courseViewModel = courseViewModel,
                            announcementViewModel = announcementViewModel,
                            initialDestination = startDestination,
                            userType = currentUserType ?: UserType.VISITOR,
                            onUserTypeChange = {newType -> currentUserType = newType}
                        )
                    }
                }
            }
        }
    }
}

private fun createNotificationChannel(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channelId = "notify_id"
        val channelName = "課程通知"
        val channelDescription = "課程上課前提醒"
        val importance = NotificationManager.IMPORTANCE_HIGH
        val channel =
            NotificationChannel(channelId, channelName, importance).apply {
                description = channelDescription
                enableVibration(true)
                vibrationPattern = longArrayOf(100, 200, 300, 400)
            }

        val notificationManager = context.getSystemService(NotificationManager::class.java)
        notificationManager?.createNotificationChannel(channel)
    }
}

// 定義導航項目資料類別
data class NavigationItem(val route: String, val title: String, val icon: ImageVector)

// 定義主分類和子分類結構
data class MainCategory(
    val id: String,
    val title: String,
    val icon: ImageVector,
    val subItems: List<NavigationItem>
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppWithNavigation(
    courseViewModel: CourseViewModel,
    announcementViewModel: AnnouncementViewModel,
    initialDestination: String = "map",
    userType: UserType = UserType.VISITOR,
    onUserTypeChange:(UserType) -> Unit
) {
    val navController = rememberNavController()
    var showSubMenu by remember { mutableStateOf(false) }
    var selectedCategory by remember { mutableStateOf<MainCategory?>(null) }
    val bottomSheetState = rememberModalBottomSheetState()
    val gameViewModel: com.example.project250311.Game.GameViewModel = viewModel()

    // 根據用戶類型定義可用的主分類
    val mainCategories = remember(userType) {
        // --- 原有分類 ---
        val mapCategory = MainCategory(
            id = "map_category",
            title = "地圖",
            icon = Icons.Default.Map,
            subItems = listOf(
                NavigationItem("map", "地圖", Icons.Default.Map),
                NavigationItem("indoormap", "室內地圖", Icons.Default.Star),
            )
        )

        val foodCategory = MainCategory(
            id = "food_category",
            title = "學餐轉盤",
            icon = Icons.Default.Restaurant,
            subItems = listOf(
                NavigationItem("food", "學餐轉盤", Icons.Default.Restaurant)
            )
        )

        val announcementCategory = MainCategory(
            id = "announcement_category",
            title = "公告",
            icon = Icons.Default.Campaign,
            subItems = listOf(
                NavigationItem("announcement", "校網公告", Icons.Default.Campaign)
            )
        )

        val scheduleCategory = MainCategory(
            id = "schedule_category",
            title = "學生管理",
            icon = Icons.Default.CalendarToday,
            subItems = listOf(
                NavigationItem("schedule", "課表", Icons.Default.CalendarToday),
                NavigationItem("leave", "請假系統", Icons.Default.ExitToApp),
                NavigationItem("notes", "筆記", Icons.Default.Note),
                NavigationItem("notice", "通知", Icons.Default.Notifications)
            )
        )

        // --- (★) 遊戲模式專屬分類 (平鋪式設計) ---
        // 為了讓底部導航欄直接顯示這 5 個按鈕，我們把它們包裝成 5 個獨立的 MainCategory
        // 每個 Category 只有 1 個 subItem，這樣點擊時就會直接導航，不會跳出選單

        val gameMissionCat = MainCategory(
            id = "gm_mission", title = "任務", icon = Icons.Default.Assignment,
            subItems = listOf(NavigationItem("game_mission", "任務列表", Icons.Default.Assignment))
        )
        val gameScheduleCat = MainCategory(
            id = "gm_schedule", title = "課表", icon = Icons.Default.CalendarToday,
            subItems = listOf(NavigationItem("schedule", "課表", Icons.Default.CalendarToday))
        )
        val gameFoodCat = MainCategory(
            id = "gm_food", title = "轉盤", icon = Icons.Default.Restaurant,
            subItems = listOf(NavigationItem("food", "學餐轉盤", Icons.Default.Restaurant))
        )
        val gameMapCat = MainCategory(
            id = "gm_map", title = "集點地圖", icon = Icons.Default.Map,
            subItems = listOf(NavigationItem("map_game", "集點地圖", Icons.Default.Map))
        )

        // (★) 根據 UserType 回傳對應的列表
        when (userType) {
            UserType.STUDENT -> listOf(mapCategory, scheduleCategory, foodCategory, announcementCategory)
            UserType.GAME_PLAYER -> listOf(
                gameMissionCat,   // 1. 任務列表
                gameScheduleCat,  // 2. 課表
                gameFoodCat,      // 3. 轉盤
                gameMapCat        // 4. 集點地圖
            )
            else -> listOf(mapCategory, foodCategory, announcementCategory) // 訪客
        }
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route?.split("/")?.firstOrNull()

    // 根據當前路由找到對應的主分類
    val currentCategory =
        mainCategories.find { category -> category.subItems.any { it.route == currentRoute } }

    // 獲取當前頁面標題
    val currentTitle =
        currentCategory?.subItems?.find { it.route == currentRoute }?.title
            ?: mainCategories.find { it.id == currentRoute }?.title ?: "應用"

    val positioningViewModel: LocationViewModel = viewModel()

    Scaffold(
        topBar = { TopAppBar(title = { Text(currentTitle) }) },
        bottomBar = {
            NavigationBar {
                mainCategories.forEach { category ->
                    NavigationBarItem(
                        icon = { Icon(category.icon, contentDescription = category.title) },
                        label = { Text(category.title) },
                        selected = currentCategory?.id == category.id,
                        onClick = {
                            // 如果該分類只有一個子項目（例如遊戲模式的所有按鈕），就直接導航
                            if (category.subItems.size == 1) {
                                navController.navigate(category.subItems.first().route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            } else {
                                // 否則（例如地圖或課表），才顯示子選單
                                selectedCategory = category
                                showSubMenu = true
                            }
                        }
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            AppNavHost(
                navController = navController,
                courseViewModel = courseViewModel,
                announcementViewModel = announcementViewModel,
                positioningViewModel = positioningViewModel,
                gameViewModel = gameViewModel,
                initialDestination = initialDestination,
                modifier = Modifier.fillMaxSize(),
                onUserTypeChange = onUserTypeChange
            )
        }
    }

    // 將 ModalBottomSheet 移到 Scaffold 外面
    if (showSubMenu && selectedCategory != null) {
        ModalBottomSheet(
            onDismissRequest = {
                showSubMenu = false
                selectedCategory = null
            },
            sheetState = bottomSheetState,
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            SubMenuBottomSheet(
                category = selectedCategory!!,
                onItemSelected = { item ->
                    navController.navigate(item.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                    showSubMenu = false
                    selectedCategory = null
                }
            )
        }
    }
}

@Composable
fun SubMenuBottomSheet(category: MainCategory, onItemSelected: (NavigationItem) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 32.dp)) {
        // 標題
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(vertical = 16.dp)
        ) {
            Icon(
                imageVector = category.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = "選擇${category.title}功能",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }

        // 選項列表
        category.subItems.forEach { item ->
            Surface(
                onClick = { onItemSelected(item) },
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.title,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun AppNavHost(
    navController: NavHostController,
    courseViewModel: CourseViewModel,
    announcementViewModel: AnnouncementViewModel,
    positioningViewModel: LocationViewModel,
    gameViewModel: com.example.project250311.Game.GameViewModel,
    initialDestination: String = "map",
    modifier: Modifier = Modifier,
    onUserTypeChange: (UserType) -> Unit
) {

    NavHost(
        navController = navController,
        startDestination = initialDestination,
        modifier = modifier
    ) {
        // 地圖畫面
        composable("map") {
            MapScreen(navController)
        }

        // 室內地圖畫面（不帶參數）
        composable("indoormap") { IndoorMapScreen() }

        // 室內地圖畫面（帶目的地參數）
        composable(
            route = "indoormap/{destination}",
            arguments = listOf(
                navArgument("destination") {
                    type = NavType.StringType
                    defaultValue = ""
                }
            )
        ) { backStackEntry ->
            val destination = backStackEntry.arguments?.getString("destination") ?: ""
            IndoorMapScreen(modifier = Modifier)
        }

        // 室外 MapScreen 透過 indoor/{building}/{floor}/{target}/{entry} 進入室內導航
        composable(
            route = "indoor/{building}/{floor}/{target}/{entry}",
            arguments = listOf(
                navArgument("building") { type = NavType.StringType; defaultValue = "" },
                navArgument("floor") { type = NavType.IntType; defaultValue = -1 },
                navArgument("target") { type = NavType.StringType; defaultValue = "" },
                navArgument("entry") { type = NavType.StringType; defaultValue = "" }
            )
        ) { backStackEntry ->
            val building = backStackEntry.arguments?.getString("building") ?: ""
            val floor = backStackEntry.arguments?.getInt("floor") ?: -1
            val target = backStackEntry.arguments?.getString("target") ?: ""
            val entry = backStackEntry.arguments?.getString("entry") ?: ""

            IndoorMapScreen(
                navController = navController,
                modifier = Modifier,
                buildingId = if (building.isBlank()) null else building,
                floorId = if (floor < 0) null else floor,
                targetPointId = if (target.isBlank()) null else target,
                entryPointId = if (entry.isBlank()) null else entry,
                autoStart = true
            )
        }

        // 課表畫面
        composable("schedule") { ScheduleScreen(courseViewModel) }

        // 請假系統
        composable("leave") { LeaveSystemScreen(navController) }

        // 筆記列表
        composable("notes") {
            NoteListScreen(
                onNavigateToNoteEditor = { navController.navigate("note_edit") },
                onNavigateToEditNote = { noteId ->
                    navController.navigate("note_edit_with_id/$noteId")
                }
            )
        }

        // 新增筆記（不帶參數的路由）
        composable("note_edit") {
            EnhancedNoteScreen(
                onNavigateToNoteList = {
                    navController.navigate("notes") { popUpTo("notes") { inclusive = true } }
                }
            )
        }

        // 編輯筆記（帶參數的路由）
        composable(
            route = "note_edit_with_id/{noteId}",
            arguments = listOf(
                navArgument("noteId") {
                    type = NavType.IntType
                    defaultValue = -1
                }
            )
        ) { backStackEntry ->
            val noteId = backStackEntry.arguments?.getInt("noteId") ?: -1
            val actualNoteId = if (noteId == -1) null else noteId

            EnhancedNoteScreen(
                onNavigateToNoteList = {
                    navController.navigate("notes") { popUpTo("notes") { inclusive = true } }
                },
                noteId = actualNoteId
            )
        }

        // 通知設定
        composable("notice") { NotificationManagerScreen(navController) }

        // 學餐轉盤
        composable("food") { FoodScreen() }

        // 公告
        composable(route = "announcement") { AnnouncementScreen(viewModel = announcementViewModel) }

        // 遊戲任務列表
        composable("game_mission") {
            com.example.project250311.Game.GameMissionScreen(
                navController = navController,
                courseViewModel = courseViewModel,
                gameViewModel = gameViewModel
            )
        }

        // 遊戲地圖路由
        composable("map_game") {
            val positionStateState = positioningViewModel.positionState.collectAsState()
            val positionState = positionStateState.value

            LaunchedEffect(positionState) {
                gameViewModel.updateVisualState(
                    group = positionState.mapGroupName,
                    percentage = positionState.mapPercentage
                )
            }

            com.example.project250311.Game.GameScreen(
                navController = navController,
                viewModel = gameViewModel
            )
        }

        // (★) 新增：QR Code 掃描畫面路由
        composable(
            route = "qrcode/{checkpointId}",
            arguments = listOf(navArgument("checkpointId") { type = NavType.StringType })
        ) { backStackEntry ->
            val checkpointId = backStackEntry.arguments?.getString("checkpointId") ?: return@composable
            val context = LocalContext.current

            QrcodeScreen(
                onQrCodeScanned = { scannedContent ->
                    // 呼叫 ViewModel 驗證
                    val isValid = gameViewModel.onQrCodeScanned(context, checkpointId, scannedContent)
                    if (isValid) {
                        // 驗證成功 -> 跳轉到蓋章畫面
                        // popUpTo("map_game") 確保按返回時回到地圖，而不是回到相機
                        navController.navigate("collect/$checkpointId") {
                            popUpTo("map_game") { saveState = true }
                        }
                    } else {
                        Toast.makeText(context, "無效的 QR Code", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }

        // (★) 新增：蓋章畫面路由
        composable(
            route = "collect/{checkpointId}",
            arguments = listOf(navArgument("checkpointId") { type = NavType.StringType })
        ) { backStackEntry ->
            val checkpointId = backStackEntry.arguments?.getString("checkpointId") ?: return@composable
            val context = LocalContext.current

            val progress = gameViewModel.getStampProgress(checkpointId)
            val checkpoint = gameViewModel.getCheckpoint(checkpointId)

            if (checkpoint != null) {
                CollectScreen(
                    checkpointName = checkpoint.name,
                    initialDepth = progress.colorDepth,
                    stampImageRes = checkpoint.stampImageRes,
                    onSaveStamp = { depth ->
                        // 儲存進度 (Firebase 紀錄已在 ViewModel 內處理)
                        gameViewModel.onStampSaved(context, checkpointId, depth)
                    },
                    onBack = {
                        navController.popBackStack()
                    }
                )
            }
        }

        composable("onboarding_reset") {
            OnboardingScreen(
                showGameMode = false, // (★) 隱藏遊戲模式，避免無限迴圈
                onComplete = { newUserType ->
                    // 1. 更新全域身分狀態 (讓底下的導航列變更)
                    onUserTypeChange(newUserType)

                    // 2. 根據選擇的身分導航
                    if (newUserType == UserType.STUDENT) {
                        // 如果選學生，去設定頁面 (需要把 StudentSetupScreen 也加進 NavHost，見下方補充)
                        navController.navigate("student_setup")
                    } else {
                        // 如果選訪客，直接去地圖
                        navController.navigate("map") {
                            // 清除堆疊，讓使用者按返回鍵不會回到問卷或歡迎頁
                            popUpTo(0) { inclusive = true }
                        }
                    }
                }
            )
        }
    }
}