package com.example.project250311

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.project250311.ui.theme.Project250311Theme
import com.example.project250311.Schedule.GetSchedule.GetScheduleActivity


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

    // 這裡設置一個按鈕，點擊後跳轉至 SecondActivity
    Column(
        modifier = Modifier
            .fillMaxSize()
            .wrapContentHeight()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Button(onClick = {
            // 創建 Intent 並啟動 SecondActivity
            val intent = Intent(context, GetScheduleActivity::class.java)
            context.startActivity(intent)
        }) {
            Text(text = "Go to Get Schedule Activity")
        }
//        Button(onClick = {
//            val intent = Intent(context,)
//        })
    }
}
