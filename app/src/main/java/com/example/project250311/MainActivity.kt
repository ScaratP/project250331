package com.example.project250311

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Note
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
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
import com.example.project250311.Notes.NoteScreen
import com.example.project250311.Schedule.GetSchedule.ScheduleScreen
import com.example.project250311.Schedule.NoSchool.LeaveSystemScreen
import com.example.project250311.Schedule.Note.NoteListScreen
import com.example.project250311.ui.theme.Project250311Theme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    // 使用 viewModels 委託創建 CourseViewModel 實例
    private val viewModel: CourseViewModel by viewModels {
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
                AppWithNavigation(viewModel)
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
fun AppWithNavigation(viewModel: CourseViewModel) {
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val items = listOf(
        NavigationItem("map", "Map", Icons.Default.Map),
        NavigationItem("schedule", "Schedule", Icons.Default.CalendarToday),
        NavigationItem("leave", "Leave", Icons.Default.ExitToApp),
        NavigationItem("notes", "Notes", Icons.Default.Note),
        NavigationItem("more", "More", Icons.Default.MoreHoriz)
    )

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

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
                        Text(items.find { it.route == currentRoute }?.title ?: "Project 250311")
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
            NavHost(
                navController = navController,
                startDestination = "map",
                modifier = Modifier.padding(paddingValues)
            ) {
                composable("map"){
                    MapScreen()
                }
                composable("schedule") {
                    ScheduleScreen(viewModel)
                }
                composable("leave") {
                    LeaveSystemScreen(navController)
                }
                composable("notes") {
                    NoteListScreen(navController)
                }
                composable("more") {
                    MoreScreen()
                }
                // Add routes for note editing
                composable(
                    route = "note_edit",
                ) {
                    NoteScreen(navController)
                }
                composable(
                    route = "note_edit/{noteId}",
                    arguments = listOf(navArgument("noteId") { type = NavType.IntType })
                ) { backStackEntry ->
                    val noteId = backStackEntry.arguments?.getInt("noteId")
                    NoteScreen(navController, noteId)
                }
            }
        }
    }
}

// 以下是其他畫面的佔位符，需根據實際需求實現
@Composable
fun MoreScreen() {
    Text("More Screen")
}

@Composable
fun MapScreen(){
    Text("Main Map")
}