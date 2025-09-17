package com.example.project250311

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.Bundle
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
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat.getSystemService
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.project250311.Data.AppDatabase
import com.example.project250311.Data.CourseRepository
import com.example.project250311.Data.CourseViewModel
import com.example.project250311.Map.IndoorMap.IndoorMapScreen
import com.example.project250311.Map.IndoorMap.IndoorRouteEditorScreen
import com.example.project250311.Map.MapScreen // 新增 import
import com.example.project250311.Schedule.Food.FoodScreen // 新增 import
import com.example.project250311.Schedule.GetSchedule.ScheduleScreen
import com.example.project250311.Schedule.NoSchool.LeaveSystemScreen
import com.example.project250311.Schedule.Note.EnhancedNoteScreen
import com.example.project250311.Schedule.Note.NoteListScreen
import com.example.project250311.Schedule.Notice.NotificationManagerScreen
import com.example.project250311.ui.theme.Project250311Theme

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

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        createNotificationChannel(this)
        setContent {
            Project250311Theme {
                val startDestination =
                        when {
                            intent?.getBooleanExtra("OPEN_SCHEDULE", false) == true -> "schedule"
                            intent?.getBooleanExtra("OPEN_LEAVE", false) == true -> "leave"
                            else -> "map"
                        }

                AppWithNavigation(
                        courseViewModel = courseViewModel,
                        initialDestination = startDestination
                )
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

        val notificationManager = getSystemService(context, NotificationManager::class.java)
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
fun AppWithNavigation(courseViewModel: CourseViewModel, initialDestination: String = "map") {
    val navController = rememberNavController()
    var showSubMenu by remember { mutableStateOf(false) }
    var selectedCategory by remember { mutableStateOf<MainCategory?>(null) }
    val bottomSheetState = rememberModalBottomSheetState()

    // 定義三個主分類及其子選項
    val mainCategories =
            listOf(
                    MainCategory(
                            id = "map_category",
                            title = "地圖",
                            icon = Icons.Default.Map,
                            subItems =
                                    listOf(
                                            NavigationItem("map", "地圖", Icons.Default.Map),
                                            NavigationItem("indoormap", "室內地圖", Icons.Default.Star),
                                            //                NavigationItem("route_editor", "路線編輯",
                                            // Icons.Default.Edit),
                                            //                NavigationItem("indoor_route_editor",
                                            // "室內路線編輯", Icons.Default.Edit)
                                            )
                    ),
                    MainCategory(
                            id = "schedule_category",
                            title = "課表",
                            icon = Icons.Default.CalendarToday,
                            subItems =
                                    listOf(
                                            NavigationItem(
                                                    "schedule",
                                                    "課表",
                                                    Icons.Default.CalendarToday
                                            ),
                                            NavigationItem(
                                                    "leave",
                                                    "請假系統",
                                                    Icons.Default.ExitToApp
                                            ),
                                            NavigationItem("notes", "筆記", Icons.Default.Note),
                                            NavigationItem(
                                                    "notice",
                                                    "通知",
                                                    Icons.Default.Notifications
                                            )
                                    )
                    ),
                    MainCategory(
                            id = "food_category",
                            title = "學餐轉盤",
                            icon = Icons.Default.Restaurant,
                            subItems =
                                    listOf(NavigationItem("food", "學餐轉盤", Icons.Default.Restaurant))
                    )
            )

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route?.split("/")?.firstOrNull()

    // 根據當前路由找到對應的主分類
    val currentCategory =
            mainCategories.find { category -> category.subItems.any { it.route == currentRoute } }

    // 獲取當前頁面標題
    val currentTitle =
            currentCategory?.subItems?.find { it.route == currentRoute }?.title
                    ?: mainCategories.find { it.id == currentRoute }?.title ?: "應用"

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
                                    if (category.id == "food_category") {
                                        // 學餐轉盤直接跳轉
                                        navController.navigate("food") {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    } else {
                                        // 其他分類顯示子選項
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
                    initialDestination = initialDestination,
                    modifier = Modifier.fillMaxSize()
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
        initialDestination: String = "map",
        modifier: Modifier = Modifier
) {
    NavHost(
            navController = navController,
            startDestination = initialDestination,
            modifier = modifier
    ) {
        // 地圖畫面
        composable("map") {
            MapScreen(navController) // 傳遞 navController
        }

        // 室內地圖畫面（不帶參數）
        composable("indoormap") { IndoorMapScreen() }

        // 室內地圖畫面（帶目的地參數）
        composable(
                route = "indoormap/{destination}",
                arguments =
                        listOf(
                                navArgument("destination") {
                                    type = NavType.StringType
                                    defaultValue = ""
                                }
                        )
        ) { backStackEntry ->
            val destination = backStackEntry.arguments?.getString("destination") ?: ""
            IndoorMapScreen(
                    initialDestination = if (destination.isNotEmpty()) destination else null
            )
        }

        // 新增：室內地圖資料管理
        composable("indoor_map_manager") {
            // 可以在這裡添加室內地圖資料管理界面
        }

        // 新增：室內路線編輯器
        composable("indoor_route_editor") { IndoorRouteEditorScreen(navController) }

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

        // 編輯筆記（帶參數的路由，使用不同的路徑名稱）
        composable(
                route = "note_edit_with_id/{noteId}",
                arguments =
                        listOf(
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
    }
}
