package com.example.project250311.Schedule.Notice


import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.project250311.ui.theme.Project250311Theme

class NoticeActivity: ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        createNotificationChannel() //建立通知頻道
        setContent {
            Project250311Theme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    //WebViewScreen(url = "https://google.com")
                    NotificationButton()
                }
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) { //判斷版本是否在API26以上
            val channelId = "notify_id" //通道的唯一辨識符號
            val channelName = "通知通道" //顯示給使用者的通知名稱
            val channelDescription = "這是APP的通知通道" //用來描述通道的用途
            val importance = NotificationManager.IMPORTANCE_HIGH //通知優先順序
            val channel = NotificationChannel(channelId, channelName, importance).apply { //創建新的通知通道
                description = channelDescription //設定通知描述
            }
            //取得NotificationManager並建立通知通道
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager //取得系統通知管理氣
            notificationManager.createNotificationChannel(channel) //註冊通知通道
        }
    }
}

@Composable
fun NotificationButton(){
    val context = LocalContext.current //取得context

    Surface (
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ){
        WebViewScreen(url = "https://google.com")

        Button(onClick = {sendNotification(context)}) {
            Text("發送通知")
        }
    }

}

//這個可以顯示&登入
@Composable
fun WebViewScreen(url: String) {
    AndroidView(factory = { context ->
        WebView(context).apply {
            webViewClient = WebViewClient()
            loadUrl(url)
        }
    })
}

fun sendNotification(context: Context){
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        if (ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // 如果沒有權限，請求權限
            ActivityCompat.requestPermissions(
                context as Activity,
                arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                1001
            )
            return
        }
    }
    //建立通知
    val channelId = "notify_id"
    val builder = NotificationCompat.Builder(context,channelId)
        .setSmallIcon(android.R.drawable.ic_dialog_email)
        .setContentTitle("通知提醒!!")
        .setContentText("該去上課了!!")
        .setPriority(NotificationCompat.PRIORITY_HIGH)

    with(NotificationManagerCompat.from(context)){
        notify(1,builder.build())
    }
}