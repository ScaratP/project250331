package com.example.project250311.Schedule.Food

import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.graphics.drawscope.Fill
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.util.Calendar
import kotlin.math.*
import kotlin.random.Random

// 添加星期幾枚舉
enum class WeekDay(val display: String) {
    MONDAY("週一"),
    TUESDAY("週二"),
    WEDNESDAY("週三"),
    THURSDAY("週四"),
    FRIDAY("週五"),
    SATURDAY("週六"),
    SUNDAY("週日")
}

// 添加營業時段枚舉
enum class OperationTime(val display: String) {
    MORNING("早上"),
    AFTERNOON("下午"),
    EVENING("晚上"),
    ALL_DAY("全天")
}

// 營業時間結構，包含星期幾和對應的營業時段
data class OperationSchedule(
    val weekDay: WeekDay,
    val times: Set<OperationTime> = setOf(OperationTime.ALL_DAY)
)

data class FoodOption(
    val id: Int,
    val name: String,
    val color: Color,
    val description: String,
    val contactNumber: String = "",
    val website: String = "",
    var isVisible: Boolean = true,
    val openDays: Set<WeekDay> = setOf(WeekDay.MONDAY, WeekDay.TUESDAY, WeekDay.WEDNESDAY, 
                                      WeekDay.THURSDAY, WeekDay.FRIDAY, WeekDay.SATURDAY, WeekDay.SUNDAY),
    val operationSchedules: List<OperationSchedule> = emptyList() // 詳細的營業時間表
)

// 定義餐點類型枚舉
enum class MealType {
    BREAKFAST, // 早餐
    LUNCH,     // 午餐
    DINNER,    // 晚餐
    BOTH,      //午晚餐
    ALL        // 全天營業
}

// 添加調試標籤
private const val TAG = "FoodScreen"
private const val DEBUG = true

// 檢查今天是否為該店家的營業日
private fun isOpenToday(calendar: Calendar, foodOption: FoodOption): Boolean {
    // 獲取當前是星期幾 (1=週日, 2=週一, ..., 7=週六)
    val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
    
    // 轉換為我們的 WeekDay 枚舉
    val today = when (dayOfWeek) {
        Calendar.MONDAY -> WeekDay.MONDAY
        Calendar.TUESDAY -> WeekDay.TUESDAY
        Calendar.WEDNESDAY -> WeekDay.WEDNESDAY
        Calendar.THURSDAY -> WeekDay.THURSDAY
        Calendar.FRIDAY -> WeekDay.FRIDAY
        Calendar.SATURDAY -> WeekDay.SATURDAY
        Calendar.SUNDAY -> WeekDay.SUNDAY
        else -> return true // 預設開放，以防萬一
    }
    
    return today in foodOption.openDays
}

// 根據時間獲取當前時段，調整為6點開始營業
private fun getCurrentOperationTime(calendar: Calendar): OperationTime {
    val hour = calendar.get(Calendar.HOUR_OF_DAY)
    return when {
        hour in 6..10 -> OperationTime.MORNING    // 6:00-10:59 為早上
        hour in 11..17 -> OperationTime.AFTERNOON // 11:00-17:59 為午後
        else -> OperationTime.EVENING             // 其他時間為晚上
    }
}

// 更新檢查營業狀態的邏輯，考慮時段因素和營業時間限制
private fun isOpenNow(calendar: Calendar, foodOption: FoodOption): Boolean {
    // 獲取當前是星期幾
    val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
    val currentHour = calendar.get(Calendar.HOUR_OF_DAY)
    val currentTime = getCurrentOperationTime(calendar)
    
    // 轉換為我們的 WeekDay 枚舉
    val today = when (dayOfWeek) {
        Calendar.MONDAY -> WeekDay.MONDAY
        Calendar.TUESDAY -> WeekDay.TUESDAY
        Calendar.WEDNESDAY -> WeekDay.WEDNESDAY
        Calendar.THURSDAY -> WeekDay.THURSDAY
        Calendar.FRIDAY -> WeekDay.FRIDAY
        Calendar.SATURDAY -> WeekDay.SATURDAY
        Calendar.SUNDAY -> WeekDay.SUNDAY
        else -> return true // 預設開放，以防萬一
    }
    
    // 首先檢查是否是營業日
    if (today !in foodOption.openDays) {
        return false
    }
    
    // 獲取店家類型
    val mealType = determineMealTypeFromSchedule(foodOption)
    
    // 應用營業時間限制
    // 1. 早餐店: 6:00 - 原有結束時間
    if (mealType == MealType.BREAKFAST && currentHour < 6) {
        return false
    }
    
    // 2. 晚餐店: 原有開始時間 - 21:00 (9PM)
    if (mealType == MealType.DINNER && currentHour >= 21) {
        return false
    }
    
    // 3. 全天營業的店家: 原有開始時間 - 23:00 (11PM)
    if (mealType == MealType.ALL && currentHour >= 23) {
        return false
    }
    
    // 如果沒有詳細時間表，到這裡就可以返回true了（因為已經確認是營業日）
    if (foodOption.operationSchedules.isEmpty()) {
        return true
    }
    
    // 檢查是否在營業時間內
    val todaySchedule = foodOption.operationSchedules.find { it.weekDay == today }
    return if (todaySchedule != null) {
        // 檢查當前時段是否營業或者全天營業
        OperationTime.ALL_DAY in todaySchedule.times || currentTime in todaySchedule.times
    } else {
        // 如果沒有找到特定的時間表，則返回true（因為已經確認是營業日）
        true
    }
}

// 格式化時間為字符串 "HH:MM"
private fun formatTime(calendar: Calendar): String {
    val hour = calendar.get(Calendar.HOUR_OF_DAY)
    val minute = calendar.get(Calendar.MINUTE)
    return String.format("%02d:%02d", hour, minute)
}

// 將營業日轉換為友好的文字顯示
private fun getOpenDaysText(openDays: Set<WeekDay>): String {
    if (openDays.isEmpty()) return "不營業"
    if (openDays.size == 7) return "每日營業"
    
    // 檢查是否為平日
    val weekdays = setOf(WeekDay.MONDAY, WeekDay.TUESDAY, WeekDay.WEDNESDAY, WeekDay.THURSDAY, WeekDay.FRIDAY)
    if (openDays.containsAll(weekdays) && openDays.size == 5) {
        return "平日營業"
    }
    
    // 檢查是否為週末
    val weekend = setOf(WeekDay.SATURDAY, WeekDay.SUNDAY)
    if (openDays.containsAll(weekend) && openDays.size == 2) {
        return "週末營業"
    }
    
    // 其他情況，直接列出所有營業日
    return openDays.joinToString { it.display }
}

// 獲取營業時間的友好顯示文本
private fun getOperationScheduleText(schedules: List<OperationSchedule>): String {
    if (schedules.isEmpty()) return "無營業資訊"
    
    // 檢查是否所有天都是同一種營業模式
    val allSamePattern = schedules.all { it.times == schedules[0].times }
    
    // 如果所有天都是全天營業
    if (allSamePattern && schedules.all { OperationTime.ALL_DAY in it.times }) {
        // 檢查是否包含所有天
        if (schedules.map { it.weekDay }.containsAll(WeekDay.values().toList())) {
            return "每天全天營業"
        }
        
        // 檢查是否為平日
        val weekdays = setOf(WeekDay.MONDAY, WeekDay.TUESDAY, WeekDay.WEDNESDAY, WeekDay.THURSDAY, WeekDay.FRIDAY)
        val scheduledDays = schedules.map { it.weekDay }.toSet()
        if (scheduledDays.containsAll(weekdays) && scheduledDays.size == 5) {
            return "平日全天營業"
        }
        
        // 檢查是否為週末
        val weekend = setOf(WeekDay.SATURDAY, WeekDay.SUNDAY)
        if (scheduledDays.containsAll(weekend) && scheduledDays.size == 2) {
            return "週末全天營業"
        }
        
        // 列出特定日期的全天營業
        return "營業日: ${scheduledDays.joinToString { it.display }} (全天)"
    }
    
    // 如果所有天都是相同的特定時段營業
    if (allSamePattern) {
        val timeText = schedules[0].times.joinToString("、") { it.display }
        // 檢查是否包含所有天
        if (schedules.map { it.weekDay }.containsAll(WeekDay.values().toList())) {
            return "每天營業時段: $timeText"
        }
        
        // 檢查是否為平日
        val weekdays = setOf(WeekDay.MONDAY, WeekDay.TUESDAY, WeekDay.WEDNESDAY, WeekDay.THURSDAY, WeekDay.FRIDAY)
        val scheduledDays = schedules.map { it.weekDay }.toSet()
       
        if (scheduledDays.containsAll(weekdays) && scheduledDays.size == 5) {
            return "平日營業時段: $timeText"
        }
        
        // 檢查是否為週末
        val weekend = setOf(WeekDay.SATURDAY, WeekDay.SUNDAY)
        if (scheduledDays.containsAll(weekend) && scheduledDays.size == 2) {
            return "週末營業時段: $timeText"
        }
    }
    
    // 其他情況，逐天列出營業時段
    return schedules.joinToString("、") { "${it.weekDay.display}: ${it.times.joinToString("、") { it.display }}" }
}

@Composable
fun FoodScreen() {
    // 更新預設食物選項庫的顏色 - 使用圖片中的色盤顏色
    val allPredefinedFoodOptions = remember {
        mutableStateListOf(
            // 第一學生宿舍餐飲中心
            FoodOption(
                id = 1,
                name = "台東佳學便利商店",
                color = Color(0xFF66C9DD), // 天藍色
                description = "生活用品、雜貨、冷藏、零嘴等商品，只收現金，可刷載具",
                contactNumber = "0921-599-075",
                isVisible = true,
                openDays = setOf(WeekDay.MONDAY, WeekDay.TUESDAY, WeekDay.WEDNESDAY, 
                                WeekDay.THURSDAY, WeekDay.FRIDAY, WeekDay.SATURDAY, WeekDay.SUNDAY),
                operationSchedules = listOf(
                    OperationSchedule(WeekDay.MONDAY, setOf(OperationTime.ALL_DAY)),
                    OperationSchedule(WeekDay.TUESDAY, setOf(OperationTime.ALL_DAY)),
                    OperationSchedule(WeekDay.WEDNESDAY, setOf(OperationTime.ALL_DAY)),
                    OperationSchedule(WeekDay.THURSDAY, setOf(OperationTime.ALL_DAY)),
                    OperationSchedule(WeekDay.FRIDAY, setOf(OperationTime.ALL_DAY)),
                    OperationSchedule(WeekDay.SATURDAY, setOf(OperationTime.ALL_DAY)),
                    OperationSchedule(WeekDay.SUNDAY, setOf(OperationTime.ALL_DAY))
                )
            ),
            FoodOption(
                id = 2,
                name = "天使麻辣滷味",
                color = Color(0xFF90D5E4), // 淺藍色
                description = "麻辣湯、不辣辣湯滷，鍋物(各類滷品，自由配料)，只收現金，現點現做",
                contactNumber = "517-937",
                isVisible = true,
                openDays = setOf(WeekDay.MONDAY, WeekDay.TUESDAY, WeekDay.WEDNESDAY, 
                                WeekDay.THURSDAY, WeekDay.FRIDAY, WeekDay.SUNDAY),
                operationSchedules = listOf(
                    OperationSchedule(WeekDay.MONDAY, setOf(OperationTime.AFTERNOON, OperationTime.EVENING)),
                    OperationSchedule(WeekDay.TUESDAY, setOf(OperationTime.AFTERNOON, OperationTime.EVENING)),
                    OperationSchedule(WeekDay.WEDNESDAY, setOf(OperationTime.AFTERNOON, OperationTime.EVENING)),
                    OperationSchedule(WeekDay.THURSDAY, setOf(OperationTime.AFTERNOON, OperationTime.EVENING)),
                    OperationSchedule(WeekDay.FRIDAY, setOf(OperationTime.AFTERNOON, OperationTime.EVENING)),
                    OperationSchedule(WeekDay.SUNDAY, setOf(OperationTime.EVENING))
                )
            ),
            FoodOption(
                id = 3,
                name = "厚道",
                color = Color(0xFF247A8C), // 深青色
                description = "便當、飯麵、水果、飲料，不定時公休，需先在社群上點餐",
                contactNumber = "0905-817-827",
                website = "https://line.me/ti/g2/bfYTZDASyaeNjPYwpmKRpmoGi0fxrrd4P0VOpg?utm_source=invitation&utm_medium=link_copy&utm_campaign=default",
                isVisible = true,
                openDays = setOf(WeekDay.MONDAY, WeekDay.TUESDAY, WeekDay.WEDNESDAY, 
                                WeekDay.THURSDAY, WeekDay.FRIDAY, WeekDay.SATURDAY),
                operationSchedules = listOf(
                    OperationSchedule(WeekDay.MONDAY, setOf(OperationTime.AFTERNOON, OperationTime.EVENING)),
                    OperationSchedule(WeekDay.TUESDAY, setOf(OperationTime.AFTERNOON, OperationTime.EVENING)),
                    OperationSchedule(WeekDay.WEDNESDAY, setOf(OperationTime.AFTERNOON, OperationTime.EVENING)),
                    OperationSchedule(WeekDay.THURSDAY, setOf(OperationTime.AFTERNOON, OperationTime.EVENING)),
                    OperationSchedule(WeekDay.FRIDAY, setOf(OperationTime.AFTERNOON, OperationTime.EVENING)),
                    OperationSchedule(WeekDay.SATURDAY, setOf(OperationTime.AFTERNOON))
                )
            ),
            FoodOption(
                id = 4,
                name = "我家的店",
                color = Color(0xFF388A94), // 青綠色
                description = "便當類，現點現做，售完為止",
                contactNumber = "0908-911-546",
                isVisible = true,
                openDays = setOf(WeekDay.MONDAY, WeekDay.TUESDAY, WeekDay.WEDNESDAY, 
                                WeekDay.THURSDAY, WeekDay.FRIDAY, WeekDay.SUNDAY),
                operationSchedules = listOf(
                    OperationSchedule(WeekDay.MONDAY, setOf(OperationTime.AFTERNOON, OperationTime.EVENING)),
                    OperationSchedule(WeekDay.TUESDAY, setOf(OperationTime.AFTERNOON, OperationTime.EVENING)),
                    OperationSchedule(WeekDay.WEDNESDAY, setOf(OperationTime.AFTERNOON, OperationTime.EVENING)),
                    OperationSchedule(WeekDay.THURSDAY, setOf(OperationTime.AFTERNOON, OperationTime.EVENING)),
                    OperationSchedule(WeekDay.FRIDAY, setOf(OperationTime.AFTERNOON, OperationTime.EVENING)),
                    OperationSchedule(WeekDay.SUNDAY, setOf(OperationTime.AFTERNOON, OperationTime.EVENING))
                )
            ),
            FoodOption(
                id = 5,
                name = "ALOHA POKE",
                color = Color(0xFF44A8BC), // 藍綠色
                description = "夏威夷食材(漢堡類、沙拉、主食、側食、多元搭配)",
                contactNumber = "0909-955-545",
                website = "https://line.me/ti/p/~@047gtfpr",
                isVisible = true,
                openDays = setOf(WeekDay.MONDAY, WeekDay.TUESDAY, WeekDay.WEDNESDAY, 
                                WeekDay.THURSDAY, WeekDay.FRIDAY),
                operationSchedules = listOf(
                    OperationSchedule(WeekDay.MONDAY, setOf(OperationTime.AFTERNOON, OperationTime.EVENING)),
                    OperationSchedule(WeekDay.TUESDAY, setOf(OperationTime.AFTERNOON, OperationTime.EVENING)),
                    OperationSchedule(WeekDay.WEDNESDAY, setOf(OperationTime.AFTERNOON, OperationTime.EVENING)),
                    OperationSchedule(WeekDay.THURSDAY, setOf(OperationTime.AFTERNOON, OperationTime.EVENING)),
                    OperationSchedule(WeekDay.FRIDAY, setOf(OperationTime.AFTERNOON))
                )
            ),
            FoodOption(
                id = 6,
                name = "小鐵匠廚房",
                color = Color(0xFF5DBFD1), // 亮青色
                description = "【便當】蔬食/經濟/雞排/豬排/雞腿【炒飯】雞排/豬排/雞腿，可先在LINE上預訂及查看當天菜色",
                contactNumber = "0907-292-117",
                isVisible = true,
                openDays = setOf(WeekDay.MONDAY, WeekDay.TUESDAY, WeekDay.WEDNESDAY, 
                                WeekDay.THURSDAY, WeekDay.FRIDAY, WeekDay.SATURDAY, WeekDay.SUNDAY),
                operationSchedules = listOf(
                    OperationSchedule(WeekDay.MONDAY, setOf(OperationTime.AFTERNOON, OperationTime.EVENING)),
                    OperationSchedule(WeekDay.TUESDAY, setOf(OperationTime.AFTERNOON, OperationTime.EVENING)),
                    OperationSchedule(WeekDay.WEDNESDAY, setOf(OperationTime.AFTERNOON, OperationTime.EVENING)),
                    OperationSchedule(WeekDay.THURSDAY, setOf(OperationTime.AFTERNOON, OperationTime.EVENING)),
                    OperationSchedule(WeekDay.FRIDAY, setOf(OperationTime.AFTERNOON, OperationTime.EVENING)),
                    OperationSchedule(WeekDay.SATURDAY, setOf(OperationTime.AFTERNOON, OperationTime.EVENING)),
                    OperationSchedule(WeekDay.SUNDAY, setOf(OperationTime.AFTERNOON, OperationTime.EVENING))
                )
            ),
            // 行政大樓餐廳
            FoodOption(
                id = 7,
                name = "采冉食坊",
                color = Color(0xFF177785), // 深青綠色
                description = "早/午餐、甜點/麵包、飲品/水果類",
                isVisible = true,
                openDays = setOf(WeekDay.MONDAY, WeekDay.TUESDAY, WeekDay.WEDNESDAY, 
                                WeekDay.THURSDAY, WeekDay.FRIDAY),
                operationSchedules = listOf(
                    OperationSchedule(WeekDay.MONDAY, setOf(OperationTime.MORNING)),
                    OperationSchedule(WeekDay.TUESDAY, setOf(OperationTime.MORNING)),
                    OperationSchedule(WeekDay.WEDNESDAY, setOf(OperationTime.MORNING)),
                    OperationSchedule(WeekDay.THURSDAY, setOf(OperationTime.MORNING)),
                    OperationSchedule(WeekDay.FRIDAY, setOf(OperationTime.MORNING))
                )
            ),
            // 第一學生商會餐飲中心
            FoodOption(
                id = 8,
                name = "東大膳務部",
                color = Color(0xFF5DBFD1), // 亮青色
                description = "早餐類",
                contactNumber = "518-003",
                isVisible = true,
                openDays = setOf(WeekDay.MONDAY, WeekDay.TUESDAY, WeekDay.WEDNESDAY, 
                                WeekDay.THURSDAY, WeekDay.FRIDAY),
                operationSchedules = listOf(
                    OperationSchedule(WeekDay.MONDAY, setOf(OperationTime.ALL_DAY)),
                    OperationSchedule(WeekDay.TUESDAY, setOf(OperationTime.ALL_DAY)),
                    OperationSchedule(WeekDay.WEDNESDAY, setOf(OperationTime.ALL_DAY)),
                    OperationSchedule(WeekDay.THURSDAY, setOf(OperationTime.ALL_DAY)),
                    OperationSchedule(WeekDay.FRIDAY, setOf(OperationTime.ALL_DAY))
                )
            ),
            FoodOption(
                id = 9,
                name = "炒鬧食堂",
                color = Color(0xFF177785), // 深青綠色
                description = "炒飯、鍋燒麵、湯品、便當",
                contactNumber = "0953-391-961",
                isVisible = true,
                openDays = setOf(WeekDay.MONDAY, WeekDay.TUESDAY, WeekDay.WEDNESDAY, 
                                WeekDay.THURSDAY, WeekDay.FRIDAY, WeekDay.SUNDAY),
                operationSchedules = listOf(
                    OperationSchedule(WeekDay.MONDAY, setOf(OperationTime.AFTERNOON, OperationTime.EVENING)),
                    OperationSchedule(WeekDay.TUESDAY, setOf(OperationTime.AFTERNOON, OperationTime.EVENING)),
                    OperationSchedule(WeekDay.WEDNESDAY, setOf(OperationTime.AFTERNOON, OperationTime.EVENING)),
                    OperationSchedule(WeekDay.THURSDAY, setOf(OperationTime.AFTERNOON, OperationTime.EVENING)),
                    OperationSchedule(WeekDay.FRIDAY, setOf(OperationTime.AFTERNOON, OperationTime.EVENING)),
                    OperationSchedule(WeekDay.SUNDAY, setOf(OperationTime.EVENING))
                )
            ),
            FoodOption(
                id = 10,
                name = "鼎泰珍牛排館",
                color = Color(0xFF44A8BC), // 藍綠色
                description = "排餐、便當、麵類、菜飯、肉燥飯、咖哩飯、雞肉飯",
                contactNumber = "0933-626-695",
                isVisible = true,
                openDays = setOf(WeekDay.MONDAY, WeekDay.TUESDAY, WeekDay.WEDNESDAY, 
                                WeekDay.THURSDAY, WeekDay.SATURDAY, WeekDay.SUNDAY),
                operationSchedules = listOf(
                    OperationSchedule(WeekDay.MONDAY, setOf(OperationTime.AFTERNOON, OperationTime.EVENING)),
                    OperationSchedule(WeekDay.TUESDAY, setOf(OperationTime.AFTERNOON, OperationTime.EVENING)),
                    OperationSchedule(WeekDay.WEDNESDAY, setOf(OperationTime.AFTERNOON, OperationTime.EVENING)),
                    OperationSchedule(WeekDay.THURSDAY, setOf(OperationTime.AFTERNOON, OperationTime.EVENING)),
                    OperationSchedule(WeekDay.SATURDAY, setOf(OperationTime.AFTERNOON, OperationTime.EVENING)),
                    OperationSchedule(WeekDay.SUNDAY, setOf(OperationTime.AFTERNOON, OperationTime.EVENING))
                )
            ),
            FoodOption(
                id = 11,
                name = "巴布阿甘飲食店",
                color = Color(0xFF66C9DD), // 天藍色
                description = "便當、咖哩飯、壽喜燒、牛肉麵、乾拌麵、鐵板麵、冰淇淋",
                contactNumber = "517-518",
                isVisible = true,
                openDays = setOf(WeekDay.MONDAY, WeekDay.TUESDAY, WeekDay.WEDNESDAY, WeekDay.THURSDAY, WeekDay.FRIDAY, WeekDay.SATURDAY, WeekDay.SUNDAY),
                operationSchedules = listOf(
                    OperationSchedule(WeekDay.MONDAY, setOf(OperationTime.AFTERNOON, OperationTime.EVENING)),
                    OperationSchedule(WeekDay.TUESDAY, setOf(OperationTime.AFTERNOON, OperationTime.EVENING)),
                    OperationSchedule(WeekDay.WEDNESDAY, setOf(OperationTime.AFTERNOON, OperationTime.EVENING)),
                    OperationSchedule(WeekDay.THURSDAY, setOf(OperationTime.AFTERNOON, OperationTime.EVENING)),
                    OperationSchedule(WeekDay.FRIDAY, setOf(OperationTime.AFTERNOON, OperationTime.EVENING)),
                    OperationSchedule(WeekDay.SATURDAY, setOf(OperationTime.AFTERNOON, OperationTime.EVENING)),
                    OperationSchedule(WeekDay.SUNDAY, setOf(OperationTime.AFTERNOON, OperationTime.EVENING))
                )
            ),
            FoodOption(
                id = 12,
                name = "鮮茶道",
                color = Color(0xFF5DBFD1), // 亮青色
                description = "飲料店",
                contactNumber = "510-168",
                isVisible = true,
                openDays = setOf(WeekDay.MONDAY, WeekDay.TUESDAY, WeekDay.WEDNESDAY, WeekDay.THURSDAY, WeekDay.FRIDAY, WeekDay.SATURDAY, WeekDay.SUNDAY),
                operationSchedules = listOf(
                    OperationSchedule(WeekDay.MONDAY, setOf(OperationTime.AFTERNOON, OperationTime.EVENING)),
                    OperationSchedule(WeekDay.TUESDAY, setOf(OperationTime.AFTERNOON, OperationTime.EVENING)),
                    OperationSchedule(WeekDay.WEDNESDAY, setOf(OperationTime.AFTERNOON, OperationTime.EVENING)),
                    OperationSchedule(WeekDay.THURSDAY, setOf(OperationTime.AFTERNOON, OperationTime.EVENING)),
                    OperationSchedule(WeekDay.FRIDAY, setOf(OperationTime.AFTERNOON, OperationTime.EVENING)),
                    OperationSchedule(WeekDay.SATURDAY, setOf(OperationTime.AFTERNOON, OperationTime.EVENING)),
                    OperationSchedule(WeekDay.SUNDAY, setOf(OperationTime.AFTERNOON, OperationTime.EVENING))
                )
            ),
            // 第二學生宿舍餐飲中心
            FoodOption(
                id = 14,
                name = "東大健康茶飲",
                color = Color(0xFF44A8BC), // 藍綠色
                description = "各式飲品(杯裝/桶裝)、水餃、厚片/熱壓吐司、各式麵類/麺線",
                contactNumber = "517-968",
                isVisible = true,
                openDays = setOf(WeekDay.MONDAY, WeekDay.TUESDAY, WeekDay.WEDNESDAY, 
                                WeekDay.THURSDAY, WeekDay.FRIDAY, WeekDay.SATURDAY),
                operationSchedules = listOf(
                    OperationSchedule(WeekDay.MONDAY, setOf(OperationTime.AFTERNOON, OperationTime.EVENING)),
                    OperationSchedule(WeekDay.TUESDAY, setOf(OperationTime.AFTERNOON, OperationTime.EVENING)),
                    OperationSchedule(WeekDay.WEDNESDAY, setOf(OperationTime.AFTERNOON, OperationTime.EVENING)),
                    OperationSchedule(WeekDay.THURSDAY, setOf(OperationTime.AFTERNOON, OperationTime.EVENING)),
                    OperationSchedule(WeekDay.FRIDAY, setOf(OperationTime.AFTERNOON, OperationTime.EVENING)),
                    OperationSchedule(WeekDay.SATURDAY, setOf(OperationTime.AFTERNOON))
                )
            ),
            FoodOption(
                id = 15,
                name = "7-ELEVEN",
                color = Color(0xFF177785), // 深青綠色
                description = "便利商店",
                contactNumber = "518-145",
                isVisible = true,
                openDays = setOf(WeekDay.MONDAY, WeekDay.TUESDAY, WeekDay.WEDNESDAY, 
                                WeekDay.THURSDAY, WeekDay.FRIDAY, WeekDay.SATURDAY, WeekDay.SUNDAY),
                operationSchedules = listOf(
                    OperationSchedule(WeekDay.MONDAY, setOf(OperationTime.ALL_DAY)),
                    OperationSchedule(WeekDay.TUESDAY, setOf(OperationTime.ALL_DAY)),
                    OperationSchedule(WeekDay.WEDNESDAY, setOf(OperationTime.ALL_DAY)),
                    OperationSchedule(WeekDay.THURSDAY, setOf(OperationTime.ALL_DAY)),
                    OperationSchedule(WeekDay.FRIDAY, setOf(OperationTime.ALL_DAY)),
                    OperationSchedule(WeekDay.SATURDAY, setOf(OperationTime.ALL_DAY)),
                    OperationSchedule(WeekDay.SUNDAY, setOf(OperationTime.ALL_DAY))
                )
            ),
            FoodOption(
                id = 16,
                name = "黎饗食光美食",
                color = Color(0xFF90D5E4), // 淺藍色
                description = "海苔飯手卷、麵/飯食類、飲品、熱壓吐司、鬆/捲餅",
                contactNumber = "0960-771-020",
                isVisible = true,
                openDays = setOf(WeekDay.MONDAY, WeekDay.TUESDAY, WeekDay.WEDNESDAY, WeekDay.THURSDAY, WeekDay.FRIDAY),
                operationSchedules = listOf(
                    OperationSchedule(WeekDay.MONDAY, setOf(OperationTime.AFTERNOON, OperationTime.EVENING)),
                    OperationSchedule(WeekDay.TUESDAY, setOf(OperationTime.AFTERNOON, OperationTime.EVENING)),
                    OperationSchedule(WeekDay.WEDNESDAY, setOf(OperationTime.AFTERNOON, OperationTime.EVENING)),
                    OperationSchedule(WeekDay.THURSDAY, setOf(OperationTime.AFTERNOON, OperationTime.EVENING)),
                    OperationSchedule(WeekDay.FRIDAY, setOf(OperationTime.AFTERNOON))
                )
            ),
            FoodOption(
                id = 17,
                name = "妙軒早餐店",
                color = Color(0xFF388A94), // 青綠色
                description = "早餐類，老闆很看心情上班",
                contactNumber = "0912-759-332",
                isVisible = true,
                openDays = setOf(WeekDay.MONDAY, WeekDay.TUESDAY, WeekDay.WEDNESDAY, WeekDay.THURSDAY, WeekDay.FRIDAY, WeekDay.SATURDAY, WeekDay.SUNDAY),
                operationSchedules = listOf(
                    OperationSchedule(WeekDay.MONDAY, setOf(OperationTime.MORNING,OperationTime.AFTERNOON)),
                    OperationSchedule(WeekDay.TUESDAY, setOf(OperationTime.MORNING,OperationTime.AFTERNOON)),
                    OperationSchedule(WeekDay.WEDNESDAY, setOf(OperationTime.MORNING,OperationTime.AFTERNOON)),
                    OperationSchedule(WeekDay.THURSDAY, setOf(OperationTime.MORNING,OperationTime.AFTERNOON)),
                    OperationSchedule(WeekDay.FRIDAY, setOf(OperationTime.MORNING,OperationTime.AFTERNOON)),
                    OperationSchedule(WeekDay.SATURDAY, setOf(OperationTime.MORNING,OperationTime.AFTERNOON)),
                    OperationSchedule(WeekDay.SUNDAY, setOf(OperationTime.MORNING,OperationTime.AFTERNOON))
                )
            )
        )
    }
    
    // 添加調試狀態
    var debugInfo by remember { mutableStateOf("") }
    var userChangedMealType by remember { mutableStateOf(false) }
    
    // 當前時間和餐點類型的狀態
    var currentTime by remember { mutableStateOf(Calendar.getInstance()) }
    var currentMealType by remember { mutableStateOf(getCurrentMealType(currentTime)) }
    
    var isSpinning by remember { mutableStateOf(false) }
    var selectedFood by remember { mutableStateOf<FoodOption?>(null) }
    var rotationAngle by remember { mutableStateOf(0f) }
    var showResult by remember { mutableStateOf(false) }
    
    // 添加缺少的函數
    fun changeMealType(newMealType: MealType) {
        currentMealType = newMealType
        userChangedMealType = true
        // 重置結果顯示
        showResult = false
        selectedFood = null
    }
    
    fun resetToAutoMode() {
        userChangedMealType = false
        currentMealType = getCurrentMealType(currentTime)
        showResult = false
        selectedFood = null
    }
    
    // 自動更新餐點類型（僅在非手動模式下）
    LaunchedEffect(currentTime) {
        if (!userChangedMealType) {
            val autoMealType = getCurrentMealType(currentTime)
            if (autoMealType != currentMealType) {
                currentMealType = autoMealType
                showResult = false
                selectedFood = null
            }
        }
    }
    
    // 使用協程定期更新時間
    val scope = rememberCoroutineScope()
    LaunchedEffect(key1 = true) {
        scope.launch {
            try {
                while (true) {
                    delay(30000) // 每30秒更新一次
                    currentTime = Calendar.getInstance()
                }
            } catch (e: Exception) {
                debugInfo = "時間更新錯誤: ${e.message}"
                Log.e(TAG, "時間更新錯誤", e)
            }
        }
    }
    
    // 根據營業時間和餐點類型篩選選項
    val foodOptions by remember { 
        derivedStateOf { 
            allPredefinedFoodOptions.filter { option ->
                if (!option.isVisible) {
                    return@filter false
                }
                
                val optionMealType = determineMealTypeFromSchedule(option)
                
                // 修改篩選邏輯，讓 ALL 和 BOTH 類型的店家在所有時段都顯示
                when (currentMealType) {
                    MealType.BREAKFAST -> optionMealType == MealType.BREAKFAST || optionMealType == MealType.ALL
                    MealType.LUNCH -> optionMealType == MealType.LUNCH || optionMealType == MealType.ALL || optionMealType == MealType.BOTH
                    MealType.DINNER -> optionMealType == MealType.DINNER || optionMealType == MealType.ALL || optionMealType == MealType.BOTH
                    MealType.BOTH -> optionMealType == MealType.BOTH || optionMealType == MealType.ALL
                    MealType.ALL -> true // 顯示所有營業中的店家
                }
            }.also { 
                if (DEBUG) {
                    val today = when (currentTime.get(Calendar.DAY_OF_WEEK)) {
                        Calendar.MONDAY -> "週一"
                        Calendar.TUESDAY -> "週二"
                        Calendar.WEDNESDAY -> "週三"
                        Calendar.THURSDAY -> "週四"
                        Calendar.FRIDAY -> "週五"
                        Calendar.SATURDAY -> "週六"
                        Calendar.SUNDAY -> "週日"
                        else -> "未知"
                    }
                    val currentOpTime = getCurrentOperationTime(currentTime).display
                    Log.d(TAG, "篩選結果: ${it.size} 個選項，今天是: $today, 當前時段: $currentOpTime, 餐點類型: $currentMealType")
                    
                    // 調試：列出每個店家的分類
                    allPredefinedFoodOptions.forEach { option ->
                        if (option.isVisible) {
                            val mealType = determineMealTypeFromSchedule(option)
                            val isOpen = isOpenNow(currentTime, option)
                            Log.d(TAG, "${option.name}: 餐點類型=$mealType, 營業中=$isOpen")
                        }
                    }
                }
            }
        } 
    }
    
    // 獲取 Context 以便啟動 Intent
    val context = LocalContext.current

    // 只保留選項管理相關狀態，移除添加食物選項功能
    var showFoodSelectorDialog by remember { mutableStateOf(false) }
    
    // 預設顏色選項 (僅用於顯示)
    val colorOptions = remember {
        listOf(
            Color(0xFF177785), // 深青綠色
            Color(0xFF247A8C), // 深青色
            Color(0xFF388A94), // 青綠色
            Color(0xFF44A8BC), // 藍綠色
            Color(0xFF5DBFD1), // 亮青色
            Color(0xFF66C9DD), // 天藍色
            Color(0xFF90D5E4), // 淺藍色
            Color(0xFFB6E3EC)  // 最淺的藍色
        )
    }

    val animatedRotation by animateFloatAsState(
        targetValue = rotationAngle,
        animationSpec = tween(
            durationMillis = 3000,
            easing = FastOutSlowInEasing
        ),
        finishedListener = {
            isSpinning = false
            showResult = true
            calculateSelectedFood(rotationAngle, foodOptions) { food ->
                selectedFood = food
            }
        }
    )

    // 計算拖拽角度
    fun calculateDragAngle(center: Offset, touchPoint: Offset): Float {
        val deltaX = touchPoint.x - center.x
        val deltaY = touchPoint.y - center.y
        return atan2(deltaY, deltaX) * 180f / PI.toFloat()
    }

    suspend fun spinWheel() {
        if (isSpinning) return
        
        isSpinning = true
        showResult = false
        selectedFood = null
        
        // 產生隨機轉動角度（至少轉3圈）
        val baseRotation = 360f * (3 + Random.nextFloat() * 3) // 3-6圈
        val finalAngle = Random.nextFloat() * 360f
        
        rotationAngle += baseRotation + finalAngle
    }

    // 手動轉動轉盤 - 改進版本
    fun manualSpin(angle: Float) {
        if (isSpinning) return
        
        isSpinning = true
        showResult = false
        selectedFood = null
        
        // 添加慣性效果，減少倍數使轉動更可控
        val inertiaRotation = angle * 1.5f + 360f * (0.5f + Random.nextFloat() * 0.5f)
        rotationAngle += inertiaRotation
    }


    // 顯示/隱藏選項
    fun toggleFoodOptionVisibility(id: Int) {
        val index = allPredefinedFoodOptions.indexOfFirst { it.id == id }
        if (index >= 0) {
            allPredefinedFoodOptions[index] = allPredefinedFoodOptions[index].copy(
                isVisible = !allPredefinedFoodOptions[index].isVisible
            )
            
            if (selectedFood?.id == id && !allPredefinedFoodOptions[index].isVisible) {
                selectedFood = null
                showResult = false
            }
        }
    }
    
    // 選擇預設食物選項對話框
    if (showFoodSelectorDialog) {
        AlertDialog(
            onDismissRequest = { showFoodSelectorDialog = false },
            title = { Text("管理食物選項") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(350.dp)
                ) {
                    // 添加餐點類型篩選器
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "選擇餐點類型:",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            FilterChip(
                                selected = currentMealType == MealType.BREAKFAST,
                                onClick = { changeMealType(MealType.BREAKFAST) },
                                label = { Text("早餐") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Coffee,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            )
                            
                            FilterChip(
                                selected = currentMealType == MealType.LUNCH,
                                onClick = { changeMealType(MealType.LUNCH) },
                                label = { Text("午餐") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.WbSunny,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            )
                            
                            FilterChip(
                                selected = currentMealType == MealType.DINNER,
                                onClick = { changeMealType(MealType.DINNER) },
                                label = { Text("晚餐") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Nightlight,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = "選擇要顯示的選項:",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(allPredefinedFoodOptions.filter { option ->
                            val optionMealType = determineMealTypeFromSchedule(option)
                            when (currentMealType) {
                                MealType.BREAKFAST -> optionMealType == MealType.BREAKFAST || optionMealType == MealType.ALL
                                MealType.LUNCH -> optionMealType == MealType.LUNCH || optionMealType == MealType.ALL || optionMealType == MealType.BOTH
                                MealType.DINNER -> optionMealType == MealType.DINNER || optionMealType == MealType.ALL || optionMealType == MealType.BOTH
                                MealType.BOTH -> optionMealType == MealType.BOTH || optionMealType == MealType.ALL
                                MealType.ALL -> true
                            }
                        }) { option ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        toggleFoodOptionVisibility(option.id)
                                    }
                                    .padding(vertical = 12.dp, horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = option.isVisible,
                                    onCheckedChange = { 
                                        toggleFoodOptionVisibility(option.id)
                                    }
                                )
                                
                                Box(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .background(option.color, CircleShape)
                                        .padding(end = 8.dp)
                                )
                                
                                Spacer(modifier = Modifier.width(8.dp))
                                
                                Column(
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = option.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    
                                    Text(
                                        text = option.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    
                                    // 顯示營業日資訊
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(top = 4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.DateRange,
                                            contentDescription = "營業日",
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.secondary
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = getOpenDaysText(option.openDays),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { 
                    showFoodSelectorDialog = false 
                }) {
                    Text("確定")
                }
            },
            dismissButton = {
                TextButton(onClick = { showFoodSelectorDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 標題行
        item {
            // 標題行 - 包含切換按鈕和當前時間
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 標題
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = when(currentMealType) {
                            MealType.BREAKFAST -> "今天早餐吃什麼？"
                            MealType.LUNCH -> "今天午餐吃什麼？"
                            MealType.DINNER -> "今天晚餐吃什麼？"
                            else -> "今天吃什麼？"
                        },
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    
                    // 添加當前時間顯示
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "現在時間: ${formatTime(currentTime)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        
                        Spacer(modifier = Modifier.width(8.dp))
                        
                        // 顯示星期幾
                        val dayOfWeek = when(currentTime.get(Calendar.DAY_OF_WEEK)) {
                            Calendar.MONDAY -> "週一"
                            Calendar.TUESDAY -> "週二"
                            Calendar.WEDNESDAY -> "週三"
                            Calendar.THURSDAY -> "週四"
                            Calendar.FRIDAY -> "週五"
                            Calendar.SATURDAY -> "週六"
                            Calendar.SUNDAY -> "週日"
                            else -> ""
                        }
                        Text(
                            text = dayOfWeek,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    
                    // 顯示是否處於自動模式
                    if (userChangedMealType) {
                        Text(
                            text = "手動模式 (點擊重置按鈕返回自動)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    
                    // 調試信息 (只在DEBUG模式顯示)
                    if (DEBUG && debugInfo.isNotEmpty()) {
                        Text(
                            text = debugInfo,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Red,
                            fontSize = 10.sp
                        )
                    }
                }
                
                // 添加模式重置按鈕 (只在手動模式下顯示)
                if (userChangedMealType) {
                    IconButton(
                        onClick = { resetToAutoMode() }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "重置為自動模式",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
                
                // 切換餐點類型按鈕
                IconButton(
                    onClick = {
                        changeMealType(
                            when(currentMealType) {
                                MealType.BREAKFAST -> MealType.LUNCH
                                MealType.LUNCH -> MealType.DINNER
                                MealType.DINNER -> MealType.BREAKFAST
                                else -> MealType.BREAKFAST
                            }
                        )
                    }
                ) {
                    Icon(
                        imageVector = when(currentMealType) {
                            MealType.BREAKFAST -> Icons.Default.Coffee
                            MealType.LUNCH -> Icons.Default.WbSunny
                            MealType.DINNER -> Icons.Default.Nightlight
                            else -> Icons.Default.Coffee
                        },
                        contentDescription = "切換餐點類型",
                        tint = when(currentMealType) {
                            MealType.BREAKFAST -> Color(0xFFF39C12) 
                            MealType.LUNCH -> Color(0xFF3498DB)     
                            MealType.DINNER -> Color(0xFF9B59B6)    
                            else -> MaterialTheme.colorScheme.primary
                        }
                    )
                }
            }
        }
        
        // 轉盤
        item {
            Box(
                modifier = Modifier.size(300.dp),
                contentAlignment = Alignment.Center
            ) {
                if (foodOptions.isNotEmpty()) {
                    // 轉盤主體
                    Canvas(
                        modifier = Modifier
                            .size(280.dp)
                            .rotate(animatedRotation)
                            .pointerInput(Unit) {
                                var lastAngle = 0f
                                var totalRotation = 0f
                                var isDragging = false
                                
                                detectDragGestures(
                                    onDragStart = { offset ->
                                        if (!isSpinning) {
                                            val center = Offset(size.width / 2f, size.height / 2f)
                                            lastAngle = calculateDragAngle(center, offset)
                                            totalRotation = 0f
                                            isDragging = true
                                            showResult = false
                                        }
                                    },
                                    onDragEnd = {
                                        if (!isSpinning && isDragging && abs(totalRotation) > 15f) {
                                            manualSpin(totalRotation)
                                        } else if (!isSpinning) {
                                            // 如果沒有足夠的拖拽距離，顯示當前指向的結果
                                            calculateSelectedFood(rotationAngle, foodOptions) { food ->
                                                selectedFood = food
                                                showResult = true
                                            }
                                        }
                                        isDragging = false
                                    }
                                ) { change, _ ->
                                    if (!isSpinning && isDragging) {
                                        val center = Offset(size.width / 2f, size.height / 2f)
                                        val currentAngle = calculateDragAngle(center, change.position)
                                        var deltaAngle = currentAngle - lastAngle
                                        
                                        // 處理角度跨越問題
                                        if (deltaAngle > 180f) deltaAngle -= 360f
                                        else if (deltaAngle < -180f) deltaAngle += 360f
                                        
                                        totalRotation += deltaAngle
                                        rotationAngle += deltaAngle
                                        lastAngle = currentAngle
                                        
                                        // 拖拽時即時更新選中的食物
                                        calculateSelectedFood(rotationAngle, foodOptions) { food ->
                                            selectedFood = food
                                            showResult = true
                                        }
                                    }
                                }
                            }
                    ) {
                        drawFoodWheel(foodOptions)
                    }
                    
                    // 指針 - 重新設計更美觀的箭頭
                    Box(
                        modifier = Modifier
                            .offset(y = (-140).dp)
                            .size(30.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.size(30.dp)) {
                            val trianglePath = Path().apply {
                                // 繪製向下指的三角形箭頭
                                moveTo(size.width / 2, size.height * 0.8f) // 底部尖端
                                lineTo(size.width * 0.2f, size.height * 0.2f) // 左上角
                                lineTo(size.width * 0.8f, size.height * 0.2f) // 右上角
                                close()
                            }
                            
                            // 繪製陰影
                            drawPath(
                                path = trianglePath,
                                color = Color.Black.copy(alpha = 0.3f),
                                style = Fill
                            )
                            
                            // 繪製主要箭頭
                            drawPath(
                                path = trianglePath,
                                color = Color(0xFFFF4444), // 更鮮艷的紅色
                                style = Fill
                            )
                            
                            // 繪製邊框
                            drawPath(
                                path = trianglePath,
                                color = Color.White,
                                style = Stroke(width = 2.dp.toPx())
                            )
                            
                            // 添加小圓點裝飾
                            drawCircle(
                                color = Color.White,
                                radius = 3.dp.toPx(),
                                center = Offset(size.width / 2, size.height * 0.4f)
                            )
                        }
                    }
                } else {
                    // 當沒有選項時顯示提示
                    Text(
                        text = "請添加食物選項",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 提示文字 - 簡化
            Text(
                text = "點擊按鈕轉動，或直接拖拽轉盤",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            // 按鈕行
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 轉動按鈕
                Button(
                    onClick = {
                        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                            spinWheel()
                        }
                    },
                    enabled = !isSpinning && foodOptions.isNotEmpty(),
                    modifier = Modifier
                        .height(56.dp)
                        .weight(0.7f)
                ) {
                    if (isSpinning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("轉動中...")
                    } else {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "轉動",
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "隨機轉動",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
        }
        
        // 結果顯示
        item {
            if (showResult && selectedFood != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = selectedFood!!.color.copy(alpha = 0.1f)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "🎯 箭頭指向",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Text(
                            text = selectedFood!!.name,
                            style = MaterialTheme.typography.headlineMedium,
                            color = selectedFood!!.color,
                            fontWeight = FontWeight.Bold
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text(
                            text = selectedFood!!.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        
                        // 添加聯絡資訊和連結按鈕
                        if (selectedFood!!.contactNumber.isNotBlank() || selectedFood!!.website.isNotBlank()) {
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            // 分隔線
                            Divider(
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f),
                                thickness = 1.dp
                            )
                            
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                // 電話按鈕
                                if (selectedFood!!.contactNumber.isNotBlank()) {
                                    FilledTonalButton(
                                        onClick = {
                                            // 使用 Intent 撥打電話
                                            val intent = Intent(Intent.ACTION_DIAL)
                                            intent.data = Uri.parse("tel:${selectedFood!!.contactNumber}")
                                            context.startActivity(intent)
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            Icons.Default.Phone, 
                                            contentDescription = "撥打電話",
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("撥打電話")
                                    }
                                }
                                
                                // 如果兩個都有，添加間距
                                if (selectedFood!!.contactNumber.isNotBlank() && selectedFood!!.website.isNotBlank()) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                
                                // 網站按鈕
                                if (selectedFood!!.website.isNotBlank()) {
                                    FilledTonalButton(
                                        onClick = {
                                            // 使用 Intent 打開瀏覽器
                                            val intent = Intent(Intent.ACTION_VIEW)
                                            intent.data = Uri.parse(
                                                if (selectedFood!!.website.startsWith("http")) 
                                                    selectedFood!!.website 
                                                else "https://${selectedFood!!.website}"
                                            )
                                            context.startActivity(intent)
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            Icons.Default.Language, 
                                            contentDescription = "訪問網站",
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("訪問網站")
                                    }
                                }
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
        
        // 食物選項列表標題
        item {
            // 食物選項列表標題行
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val mealTypeText = when(currentMealType) {
                        MealType.BREAKFAST -> "早餐"
                        MealType.LUNCH -> "午餐"
                        MealType.DINNER -> "晚餐"
                        else -> "全部"
                    }
                    
                    Text(
                        text = "$mealTypeText",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    
                    Text(
                        text = " (${formatTime(currentTime)})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    
                    Text(
                        text = " · ${foodOptions.size}個選項",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                
                // 餐點類型切換和管理按鈕
                Row {
                    // 早餐按鈕
                    IconButton(onClick = { changeMealType(MealType.BREAKFAST) }) {
                        Icon(
                            imageVector = Icons.Default.Coffee,
                            contentDescription = "早餐",
                            tint = if (currentMealType == MealType.BREAKFAST) 
                                    MaterialTheme.colorScheme.primary 
                                else 
                                    MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f)
                        )
                    }
                    
                    // 午餐按鈕
                    IconButton(onClick = { changeMealType(MealType.LUNCH) }) {
                        Icon(
                            imageVector = Icons.Default.WbSunny,
                            contentDescription = "午餐",
                            tint = if (currentMealType == MealType.LUNCH) 
                                    MaterialTheme.colorScheme.primary 
                                else 
                                    MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f)
                        )
                    }
                    
                    // 晚餐按鈕
                    IconButton(onClick = { changeMealType(MealType.DINNER) }) {
                        Icon(
                            imageVector = Icons.Default.Nightlight,
                            contentDescription = "晚餐",
                            tint = if (currentMealType == MealType.DINNER) 
                                    MaterialTheme.colorScheme.primary 
                                else 
                                    MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f)
                        )
                    }
                    
                    // 管理選項按鈕
                    IconButton(onClick = { showFoodSelectorDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "管理選項",
                            tint = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(4.dp))
        }
        
        // 食物選項列表
        items(foodOptions) { option ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp, horizontal = 4.dp),
                colors = CardDefaults.cardColors(
                    containerColor = option.color.copy(alpha = 0.1f)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // 選項信息
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .background(option.color, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = option.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = option.color
                                    )
                                    
                                    // 顯示營業狀態指示器
                                    Spacer(modifier = Modifier.width(8.dp))
                                    if (isOpenNow(currentTime, option)) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .background(Color.Green, CircleShape)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "營業中",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color.Green.copy(alpha = 0.8f)
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .background(Color.Gray, CircleShape)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "休息中",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color.Gray
                                        )
                                    }
                                    
                                    // 顯示非全週營業的圖示
                                    if (option.openDays.size < 7) {
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(
                                            imageVector = Icons.Default.DateRange,
                                            contentDescription = "特定日營業",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                                if (option.description.isNotBlank()) {
                                    Text(
                                        text = option.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        
                        // "隱藏"按鈕
                        IconButton(
                            onClick = { toggleFoodOptionVisibility(option.id) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.VisibilityOff,
                                contentDescription = "隱藏選項",
                                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    
                    // 營業日資訊
                    if (option.openDays.size < 7) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(start = 48.dp, bottom = 12.dp)
                        ) {
                            Text(
                                text = "營業日: ${getOpenDaysText(option.openDays)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    } else if (option.operationSchedules.isNotEmpty()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(start = 48.dp, bottom = 12.dp)
                        ) {
                            Text(
                                text = getOperationScheduleText(option.operationSchedules),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }
            }
        }
        
        // 底部間隔
        item {
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

private fun DrawScope.drawFoodWheel(foodOptions: List<FoodOption>) {
    if (foodOptions.isEmpty()) return
    
    val center = size.center
    val radius = size.minDimension / 2
    val sectionAngle = 360f / foodOptions.size
    val calendar = Calendar.getInstance() // 獲取當前時間以檢查營業狀態
    
    // 繪製輪盤的外圈陰影 - 更淡的陰影
    drawCircle(
        color = Color.Black.copy(alpha = 0.15f),
        radius = radius + 3.dp.toPx(),
        center = center,
        style = Fill
    )
    
    // 繪製輪盤底層 - 改為淺藍綠色背景
    drawCircle(
        color = Color(0xFF90D5E4).copy(alpha = 0.3f),
        radius = radius,
        center = center,
        style = Fill
    )
    
    foodOptions.forEachIndexed { index, option ->
        // 使扇形從正上方開始
        val startAngle = index * sectionAngle - 90f
        
        // 判斷當前是否營業
        val isOpen = isOpenNow(calendar, option)
        
        // 創建更和諧的顏色 - 使用色盤中的顏色
        val baseColor = option.color
        
        // 從中心到邊緣的漸變色 - 更柔和的過渡
        val colors = if (isOpen) {
            listOf(
                baseColor.copy(alpha = 0.85f),  // 接近中心的顏色（輕微透明）
                baseColor.copy(alpha = 0.95f),  // 中間過渡色
                baseColor                        // 邊緣顏色（完全不透明）
            )
        } else {
            listOf(
                baseColor.copy(alpha = 0.4f),   // 接近中心的顏色（更透明）
                baseColor.copy(alpha = 0.5f),   // 中間過渡色
                baseColor.copy(alpha = 0.6f)    // 邊緣顏色（半透明）
            )
        }
        
        // 繪製帶有漸變的扇形 - 使用柔和的漸變
        drawArc(
            brush = Brush.radialGradient(
                colors = colors,
                center = center,
                radius = radius
            ),
            startAngle = startAngle,
            sweepAngle = sectionAngle,
            useCenter = true,
            topLeft = Offset.Zero,
            size = size
        )
        
        // 繪製更美觀的邊框 - 白色半透明
        drawArc(
            color = Color.White.copy(alpha = 0.6f),
            startAngle = startAngle,
            sweepAngle = sectionAngle,
            useCenter = true,
            topLeft = Offset.Zero,
            size = size,
            style = Stroke(width = 1.5f.dp.toPx())
        )
        
        // 繪製文字
        val textAngle = startAngle + sectionAngle / 2
        val textRadius = radius * 0.65f // 調整文字距離中心點的距離
        val textX = center.x + textRadius * cos(Math.toRadians(textAngle.toDouble())).toFloat()
        val textY = center.y + textRadius * sin(Math.toRadians(textAngle.toDouble())).toFloat()
        
        rotate(textAngle + 90f, pivot = Offset(textX, textY)) {
            drawContext.canvas.nativeCanvas.apply {
                val fontSize = if (option.name.length > 6) 24f else 32f // 根據文字長度調整大小
                
                // 繪製文字陰影 - 增強可讀性
                val shadowPaint = android.graphics.Paint().apply {
                    this.color = android.graphics.Color.BLACK
                    textSize = fontSize
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                    textAlign = android.graphics.Paint.Align.CENTER
                    alpha = 180 // 陰影透明度
                }
                
                // 繪製更多層的陰影增強立體感
                for (i in 1..3) {
                    val offset = i * 1f
                    drawText(option.name, textX + offset, textY + offset, shadowPaint)
                }
                
                // 主要文字
                val textPaint = android.graphics.Paint().apply {
                    this.color = android.graphics.Color.WHITE
                    textSize = fontSize
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                    textAlign = android.graphics.Paint.Align.CENTER
                    setShadowLayer(2f, 1f, 1f, android.graphics.Color.BLACK)
                }
                drawText(option.name, textX, textY, textPaint)
                
                // 如果不營業，添加小標記
                if (!isOpen) {
                    val statusText = "休息中"
                    val smallPaint = android.graphics.Paint().apply {
                        this.color = android.graphics.Color.rgb(255, 200, 200)
                        textSize = 18f
                        typeface = android.graphics.Typeface.DEFAULT_BOLD
                        textAlign = android.graphics.Paint.Align.CENTER
                        setShadowLayer(3f, 1f, 1f, android.graphics.Color.BLACK)
                    }
                    drawText(statusText, textX, textY + 24f, smallPaint)
                }
            }
        }
    }
    
    // 繪製中心點裝飾 - 使用圖片中淺藍色
    drawCircle(
        color = Color(0xFF90D5E4),
        radius = radius * 0.12f,
        center = center,
        style = Fill
    )
    
    // 中心點外圈 - 使用深青綠色
    drawCircle(
        color = Color(0xFF177785),
        radius = radius * 0.12f,
        center = center,
        style = Stroke(width = 2.dp.toPx())
    )
    
    // 添加中心點內部裝飾
    drawCircle(
        color = Color.White.copy(alpha = 0.7f),
        radius = radius * 0.06f,
        center = center,
        style = Fill
    )
}

// 修正選中食物的計算邏輯，添加錯誤處理
private fun calculateSelectedFood(
    currentRotation: Float,
    foodOptions: List<FoodOption>,
    onResult: (FoodOption) -> Unit
) {
    if (foodOptions.isEmpty()) {
        Log.w(TAG, "沒有可用的食物選項")
        return
    }
    
    try {
        val sectionAngle = 360f / foodOptions.size
        
        // 正規化角度到 0-360 範圍
        val normalizedAngle = ((currentRotation % 360f) + 360f) % 360f
        
        // 箭頭在頂部(270度)，轉盤順時針旋轉
        val adjustedAngle = (360f - normalizedAngle) % 360f
        
        // 計算選中的選項索引
        val selectedIndex = (adjustedAngle / sectionAngle).toInt() % foodOptions.size
        
        if (DEBUG) {
            Log.d(TAG, "角度計算 - 角度: $normalizedAngle, 調整後: $adjustedAngle, 索引: $selectedIndex/${foodOptions.size}")
        }
        
        if (selectedIndex in foodOptions.indices) {
            onResult(foodOptions[selectedIndex])
        } else {
            Log.e(TAG, "索引超出範圍: $selectedIndex, 選項數量: ${foodOptions.size}")
            // 發生錯誤時選擇第一個選項
            onResult(foodOptions[0])
        }
    } catch (e: Exception) {
        Log.e(TAG, "計算選中食物時出錯", e)
        if (foodOptions.isNotEmpty()) {
            onResult(foodOptions[0])
        }
    }
}

// 根據營業時間判斷店家適合的餐點類型
private fun determineMealTypeFromSchedule(foodOption: FoodOption): MealType {
    if (foodOption.operationSchedules.isEmpty()) {
        return MealType.ALL // 沒有詳細時間表就歸類為全天
    }
    
    val operationTimes = foodOption.operationSchedules.flatMap { it.times }.toSet()
    
    return when {
        // 全天營業的店家歸類為全天
        operationTimes.contains(OperationTime.ALL_DAY) -> MealType.ALL        
        // 只有早上營業 → 早餐店
        operationTimes.contains(OperationTime.MORNING) &&
        !operationTimes.contains(OperationTime.AFTERNOON) &&
        !operationTimes.contains(OperationTime.EVENING) -> MealType.BREAKFAST
        
        // 只有下午營業 → 午餐店
        operationTimes.contains(OperationTime.AFTERNOON) &&
        !operationTimes.contains(OperationTime.MORNING) &&
        !operationTimes.contains(OperationTime.EVENING) -> MealType.LUNCH
        
        // 只有晚上營業 → 晚餐店
        operationTimes.contains(OperationTime.EVENING) &&
        !operationTimes.contains(OperationTime.MORNING) &&
        !operationTimes.contains(OperationTime.AFTERNOON) -> MealType.DINNER

        // 包含早上營業時段的歸類為早餐（優先級較高）
        operationTimes.contains(OperationTime.MORNING) -> MealType.BREAKFAST
        
        // 包含下午和晚上營業的歸類為BOTH
        operationTimes.contains(OperationTime.AFTERNOON) && 
        operationTimes.contains(OperationTime.EVENING) -> MealType.BOTH
        
        else -> MealType.ALL // 其他情況歸類為全天
    }
}

// 將 getCurrentMealType 函數移動到 Composable 函數外部
private fun getCurrentMealType(calendar: Calendar): MealType {
    val currentHour = calendar.get(Calendar.HOUR_OF_DAY)
    val mealType = when {
        currentHour in 6..10 -> MealType.BREAKFAST  // 6:00-10:59為早餐時段 (與MORNING一致)
        currentHour in 11..17 -> MealType.LUNCH     // 11:00-17:59為午餐時段 (與AFTERNOON一致)
        else -> MealType.DINNER                     // 其他時間為晚餐時段 (與EVENING一致)
    }
    
    if (DEBUG) {
        Log.d(TAG, "當前時間: ${calendar.get(Calendar.HOUR_OF_DAY)}:${calendar.get(Calendar.MINUTE)}, 餐點類型: $mealType")
    }
    
    return mealType
}
