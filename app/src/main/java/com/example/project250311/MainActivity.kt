package com.example.project250311

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Note
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.project250311.Schedule.GetSchedule.GetScheduleActivity
import com.example.project250311.Schedule.NoSchool.GetLeaveDataActivity
import com.example.project250311.Schedule.NoSchool.LeaveActivity
//import com.example.project250311.Schedule.Note.NoteActivity
//import com.example.project250311.Schedule.Note.NoteListActivity
import com.example.project250311.Schedule.Notice.NoticeActivity
import com.example.project250311.ui.theme.Project250311Theme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Project250311Theme {
                // 主畫面的 Compose UI
                MainScreen()
            }
        }
    }
}

@Composable
fun MainScreen() {
    val context = LocalContext.current

    Project250311Theme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 上半部兩個按鈕
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                MenuButton(
                    text = "Schedule",
                    icon = Icons.Default.CalendarToday,
                    onClick = {
                        context.startActivity(Intent(context, GetScheduleActivity::class.java))
                    },
                    modifier = Modifier.weight(1f)
                )
                MenuButton(
                    text = "Notice",
                    icon = Icons.Default.Notifications,
                    onClick = {
                        context.startActivity(Intent(context, NoticeActivity::class.java))
                    },
                    modifier = Modifier.weight(1f)
                )
            }

            // 下半部三個按鈕
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                MenuButton(
                    text = "Leave",
                    icon = Icons.Default.ExitToApp,
                    onClick = {
                        context.startActivity(Intent(context, LeaveActivity::class.java))
                    },
                    modifier = Modifier.weight(1f)
                )
//                MenuButton(
//                    text = "Notes",
//                    icon = Icons.Default.Note,
//                    onClick = {
//                        context.startActivity(Intent(context, NoteListActivity::class.java))
//                    },
//                    modifier = Modifier.weight(1f)
//                )
//                MenuButton(
//                    text = "More",
//                    icon = Icons.Default.MoreHoriz,
//                    onClick = {
//                        context.startActivity(Intent(context, NoteActivity::class.java))
//                    },
//                    modifier = Modifier.weight(1f)
//                )
            }
        }
    }
}

@Composable
fun MenuButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .padding(8.dp)
            .fillMaxHeight(),
        elevation = CardDefaults.cardElevation(4.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .clickable(onClick = onClick)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = text,
                modifier = Modifier
                    .size(48.dp)
                    .padding(bottom = 8.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
        }
    }
}