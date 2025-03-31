package com.example.project250311

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavController
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
import com.example.project250311.Schedule.Note.NoteScreen
import com.example.project250311.Schedule.GetSchedule.ScheduleScreen
import com.example.project250311.Schedule.NoSchool.LeaveSystemScreen
import com.example.project250311.Schedule.Note.NoteViewerScreen
import com.example.project250311.Schedule.Note.NotesScreen
import com.example.project250311.Schedule.Notice.NotificationManagerScreen
import com.example.project250311.ui.theme.Project250311Theme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    // 使用 viewModels 委託創建 CourseViewModel 實例
    private val courseViewModel: CourseViewModel by viewModels {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val db = AppDatabase.getDatabase(applicationContext)
                val repository = CourseRepository(db.courseDao())
                @Suppress("UNCHECKED_CAST")
                return CourseViewModel(repository) as T
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Project250311Theme {
                // 將 startDestination 傳遞給函數
                val startDestination =
                    if (intent?.getBooleanExtra("OPEN_SCHEDULE", false) == true) "schedule"
                    else "map"

                AppWithNavigation(
                    courseViewModel = courseViewModel,
                    initialDestination = startDestination
                )
            }
        }
    }
}

// 定義導航項目資料類別
data class NavigationItem(
    val route: String,
    val title: String,
    val icon: ImageVector
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppWithNavigation(
    courseViewModel: CourseViewModel,
    initialDestination: String = "map"
) {
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val items = listOf(
        NavigationItem("map", "地圖", Icons.Default.Map),
        NavigationItem("schedule", "課程表", Icons.Default.CalendarToday),
        NavigationItem("leave", "請假系統", Icons.Default.ExitToApp),
        NavigationItem("notes", "筆記", Icons.Default.Note),
        NavigationItem("notice", "通知", Icons.Default.Notifications)
    )

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route?.split("/")?.firstOrNull()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "應用選單",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.titleLarge
                )
                Divider()
                Spacer(modifier = Modifier.height(16.dp))

                items.forEach { item ->
                    NavigationDrawerItem(
                        icon = { Icon(item.icon, contentDescription = item.title) },
                        label = { Text(item.title) },
                        selected = currentRoute == item.route,
                        onClick = {
                            navController.navigate(item.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                            scope.launch {
                                drawerState.close()
                            }
                        },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(items.find { it.route == currentRoute }?.title ?: "學校助手")
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = {
                                scope.launch {
                                    drawerState.open()
                                }
                            }
                        ) {
                            Icon(Icons.Default.Menu, contentDescription = "開啟選單")
                        }
                    }
                )
            }
        ) { paddingValues ->
            AppNavHost(
                navController = navController,
                courseViewModel = courseViewModel,
                initialDestination = initialDestination,
                modifier = Modifier.padding(paddingValues)
            )
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
            MapScreen()
        }

        // 課程表畫面
        composable("schedule") {
            ScheduleScreen(courseViewModel)
        }

        // 請假系統
        composable("leave") {
            LeaveSystemScreen(navController)
        }

        // 筆記列表
        composable("notes") {
            NotesScreen(navController)
        }

        // 通知設定
        composable("notice") {
            NotificationManagerScreen(navController)
        }

        // 筆記編輯/新增
        composable(
            route = "note_edit",
        ) {
            val noteId = null
            NoteScreen(navController, noteId)
        }

        // 筆記編輯（根據 ID）
        composable(
            route = "note_edit/{noteId}",
            arguments = listOf(navArgument("noteId") { type = NavType.IntType })
        ) { backStackEntry ->
            val noteId = backStackEntry.arguments?.getInt("noteId")
            NoteScreen(navController, noteId)
        }

        // 筆記查看頁面
        composable(
            route = "note_view/{noteId}",
            arguments = listOf(navArgument("noteId") { type = NavType.IntType })
        ) { backStackEntry ->
            val noteId = backStackEntry.arguments?.getInt("noteId")
            noteId?.let {
                NoteViewerScreen(navController, it)
            }
        }
    }
}

// 基本畫面元件
@Composable
fun MapScreen() {
    Box(modifier = Modifier.fillMaxSize()) {
        Text("地圖畫面 - 尚未實現")
    }
}