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

// 定義餐點類型枚舉
enum class MealType {
    BREAKFAST, // 早餐
    LUNCH,     // 午餐
    DINNER,    // 晚餐
    BOTH       // 適合任何時間
}

data class FoodOption(
    val id: Int,
    val name: String,
    val color: Color,
    val description: String,
    val contactNumber: String = "",
    val website: String = "",
    var isVisible: Boolean = true,
    val mealType: MealType = MealType.BOTH // 添加餐點類型欄位
)

// 添加調試標籤
private const val TAG = "FoodScreen"
private const val DEBUG = true

// 將 getCurrentMealType 函數移動到 Composable 函數外部
private fun getCurrentMealType(calendar: Calendar): MealType {
    val currentHour = calendar.get(Calendar.HOUR_OF_DAY)
    val mealType = when {
        currentHour in 5..9 -> MealType.BREAKFAST // 5:00-9:59為早餐時段
        currentHour in 10..14 -> MealType.LUNCH   // 10:00-14:59為午餐時段
        currentHour in 15..21 -> MealType.DINNER  // 15:00-21:59為晚餐時段
        else -> MealType.BOTH                     // 其他時間顯示全部
    }
    
    if (DEBUG) {
        Log.d(TAG, "當前時間: ${calendar.get(Calendar.HOUR_OF_DAY)}:${calendar.get(Calendar.MINUTE)}, 餐點類型: $mealType")
    }
    
    return mealType
}

// 格式化時間為字符串 "HH:MM"
private fun formatTime(calendar: Calendar): String {
    val hour = calendar.get(Calendar.HOUR_OF_DAY)
    val minute = calendar.get(Calendar.MINUTE)
    return String.format("%02d:%02d", hour, minute)
}

@Composable
fun FoodScreen() {
    // 預設食物選項庫
    val allPredefinedFoodOptions = remember {
        mutableStateListOf(
            // 早餐選項
            FoodOption(
                id = 1,
                name = "美而美",
                color = Color(0xFF9B59B6),
                description = "早餐店，提供三明治、蛋餅等各式早餐",
                contactNumber = "02-2233-4455",
                isVisible = true,
                mealType = MealType.BREAKFAST
            ),
            FoodOption(
                id = 2,
                name = "永和豆漿",
                color = Color(0xFFD35400),
                description = "傳統早餐店，提供豆漿、燒餅油條等",
                contactNumber = "02-3344-5566",
                isVisible = true,
                mealType = MealType.BREAKFAST
            ),
            FoodOption(
                id = 3,
                name = "麥當勞早餐",
                color = Color(0xFFE74C3C),
                description = "速食早餐，提供漢堡、薯餅等選項",
                website = "www.mcdonalds.com.tw",
                isVisible = true,
                mealType = MealType.BREAKFAST
            ),
            // 午餐選項
            FoodOption(
                id = 4,
                name = "7-11",
                color = Color(0xFF2E8B57),
                description = "便利商店，提供各種即食商品、飲料和零食",
                contactNumber = "02-1234-5678",
                isVisible = true,
                mealType = MealType.BOTH
            ),
            FoodOption(
                id = 5,
                name = "八部",
                color = Color(0xFFFF6B35),
                description = "傳統台式料理，提供經濟實惠的家常菜",
                contactNumber = "02-8765-4321",
                isVisible = true,
                mealType = MealType.LUNCH
            ),
            FoodOption(
                id = 6,
                name = "煎餃",
                color = Color(0xFF4A90E2),
                description = "專業煎餃店，提供各種口味的手工煎餃",
                website = "www.jiaozi.com",
                isVisible = true,
                mealType = MealType.BOTH
            ),
            // 晚餐選項
            FoodOption(
                id = 7,
                name = "牛肉麵",
                color = Color(0xFFD35400),
                description = "專賣各式牛肉麵，湯頭濃郁",
                contactNumber = "02-6677-8899",
                isVisible = true,
                mealType = MealType.DINNER
            ),
            FoodOption(
                id = 8,
                name = "滷肉飯",
                color = Color(0xFF7F8C8D),
                description = "台灣傳統美食，香Q軟嫩",
                contactNumber = "02-9988-7766",
                isVisible = true,
                mealType = MealType.BOTH
            ),
            FoodOption(
                id = 9,
                name = "炒飯專門店",
                color = Color(0xFF8E44AD),
                description = "各式炒飯，香氣四溢",
                isVisible = true,
                mealType = MealType.DINNER
            ),
            FoodOption(
                id = 10,
                name = "自助餐",
                color = Color(0xFF1ABC9C),
                description = "自助餐廳，可以自選多種菜色",
                isVisible = true,
                mealType = MealType.LUNCH
            ),
            FoodOption(
                id = 11,
                name = "素食餐廳",
                color = Color(0xFF34495E),
                description = "提供各種素食料理",
                website = "www.vegfood.com",
                isVisible = true,
                mealType = MealType.BOTH
            ),
            FoodOption(
                id = 12,
                name = "學生餐廳",
                color = Color(0xFFF39C12),
                description = "校內餐廳，提供多種平價餐點",
                contactNumber = "02-3344-5566",
                isVisible = true,
                mealType = MealType.LUNCH
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
    
    // 重置為自動模式函數 - 移到 Composable 內部
    fun resetToAutoMode() {
        userChangedMealType = false
        currentMealType = getCurrentMealType(Calendar.getInstance())
        debugInfo = "已重置為自動模式: $currentMealType"
        if (DEBUG) {
            Log.d(TAG, "已重置為自動模式: $currentMealType")
        }
        selectedFood = null
        showResult = false
    }
    
    // 變更餐點類型函數 - 移到 Composable 內部
    fun changeMealType(newMealType: MealType) {
        currentMealType = newMealType
        userChangedMealType = true
        debugInfo = "已手動切換到: $newMealType"
        if (DEBUG) {
            Log.d(TAG, "已手動切換到: $newMealType")
        }
        selectedFood = null
        showResult = false
    }
    
    // 使用協程定期更新時間和餐點類型
    val scope = rememberCoroutineScope()
    LaunchedEffect(key1 = true) {
        scope.launch {
            try {
                while (true) {
                    delay(30000) // 每30秒更新一次
                    val newTime = Calendar.getInstance()
                    currentTime = newTime
                    
                    // 只有在用戶沒有手動修改餐點類型時，才自動更新
                    if (!userChangedMealType) {
                        val newMealType = getCurrentMealType(newTime)
                        if (newMealType != currentMealType) {
                            currentMealType = newMealType
                            debugInfo = "自動更新餐點類型: $newMealType"
                            if (DEBUG) {
                                Log.d(TAG, "自動更新餐點類型: $newMealType")
                            }
                        }
                    } else {
                        debugInfo = "用戶已手動設置餐點類型: $currentMealType"
                    }
                }
            } catch (e: Exception) {
                debugInfo = "時間更新錯誤: ${e.message}"
                Log.e(TAG, "時間更新錯誤", e)
            }
        }
    }
    
    // 使用 derivedStateOf 來篩選當前應顯示的選項
    val foodOptions by remember { 
        derivedStateOf { 
            allPredefinedFoodOptions.filter { 
                it.isVisible && (it.mealType == currentMealType || it.mealType == MealType.BOTH) 
            }.also { 
                if (DEBUG) {
                    Log.d(TAG, "篩選結果: ${it.size} 個選項")
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
            Color(0xFF2E8B57), // 深綠色
            Color(0xFFFF6B35), // 橘紅色
            Color(0xFF4A90E2), // 藍色
            Color(0xFF9B59B6), // 紫色
            Color(0xFFE74C3C), // 紅色
            Color(0xFFF39C12), // 橙色
            Color(0xFF27AE60), // 綠色
            Color(0xFF1ABC9C)  // 青綠色
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
                                onClick = { 
                                    currentMealType = MealType.BREAKFAST
                                    selectedFood = null
                                    showResult = false
                                },
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
                                onClick = { 
                                    currentMealType = MealType.LUNCH
                                    selectedFood = null
                                    showResult = false
                                },
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
                                onClick = { 
                                    currentMealType = MealType.DINNER
                                    selectedFood = null
                                    showResult = false
                                },
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
                        items(allPredefinedFoodOptions.filter {
                            it.mealType == currentMealType || it.mealType == MealType.BOTH
                        }) { option ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        toggleFoodOptionVisibility(option.id)
                                    }
                                    .padding(vertical = 8.dp, horizontal = 4.dp),
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
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = option.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    
                                    Text(
                                        text = option.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
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
                Text(
                    text = "現在時間: ${formatTime(currentTime)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
                
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
                    // 切換到下一個餐點類型
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
                        MealType.BREAKFAST -> Icons.Default.WbSunny // 早餐->午餐
                        MealType.LUNCH -> Icons.Default.Nightlight  // 午餐->晚餐
                        MealType.DINNER -> Icons.Default.Coffee     // 晚餐->早餐
                        else -> Icons.Default.Coffee                // 預設
                    },
                    contentDescription = "切換餐點類型",
                    tint = when(currentMealType) {
                        MealType.BREAKFAST -> Color(0xFFF39C12) // 早餐-暖黃色
                        MealType.LUNCH -> Color(0xFF3498DB)     // 午餐-藍色
                        MealType.DINNER -> Color(0xFF9B59B6)    // 晚餐-紫色
                        else -> MaterialTheme.colorScheme.primary
                    }
                )
            }
        }
        
        // 轉盤
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
        
        Spacer(modifier = Modifier.height(16.dp)) // 減少間距
        
        // 提示文字 - 簡化
        Text(
            text = "點擊按鈕轉動，或直接拖拽轉盤",
            style = MaterialTheme.typography.bodySmall, // 使用更小的文字風格
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 8.dp) // 減少底部填充
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
        
        Spacer(modifier = Modifier.height(12.dp)) // 減少間距
        
        // 結果顯示 - 使其更緊湊
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
                                        Icons.Default.Call, 
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
        }
        
        Spacer(modifier = Modifier.height(8.dp)) // 減少間距
        
        // 食物選項列表標題行
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp), // 添加些許內邊距
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val mealTypeText = when(currentMealType) {
                    MealType.BREAKFAST -> "早餐"
                    MealType.LUNCH -> "午餐"
                    MealType.DINNER -> "晚餐"
                    else -> ""
                }
                
                Text(
                    text = "$mealTypeText",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                
                // 使用與頂部相同的時間格式
                Text(
                    text = " (${formatTime(currentTime)})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
                
                // 顯示可用選項數量
                Text(
                    text = " · ${foodOptions.size}個選項",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            
            // 添加餐點類型切換和管理按鈕
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
        
        // 選項列表 - 使用 LazyColumn 實現可滾動列表
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(foodOptions) { option ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = option.color.copy(alpha = 0.1f)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
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
                                Spacer(modifier = Modifier.width(12.dp))
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
                            
                            // "隱藏"按鈕 (原"刪除"按鈕)
                            IconButton(
                                onClick = { toggleFoodOptionVisibility(option.id) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.VisibilityOff, // 改為隱藏圖示
                                    contentDescription = "隱藏選項",
                                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f), // 降低顏色強度
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        
                        // 添加聯絡資訊顯示
                        if (option.contactNumber.isNotBlank() || option.website.isNotBlank()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(start = 40.dp, bottom = 8.dp)
                            ) {
                                if (option.contactNumber.isNotBlank()) {
                                    Icon(
                                        imageVector = Icons.Default.Call,
                                        contentDescription = "電話",
                                        modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = option.contactNumber,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                
                                if (option.contactNumber.isNotBlank() && option.website.isNotBlank()) {
                                    Spacer(modifier = Modifier.width(12.dp))
                                }
                                
                                if (option.website.isNotBlank()) {
                                    Icon(
                                        imageVector = Icons.Default.Language,
                                        contentDescription = "網站",
                                        modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "網站連結",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun DrawScope.drawFoodWheel(foodOptions: List<FoodOption>) {
    if (foodOptions.isEmpty()) return
    
    val center = size.center
    val radius = size.minDimension / 2
    val sectionAngle = 360f / foodOptions.size
    
    foodOptions.forEachIndexed { index, option ->
        // 使扇形從正上方開始
        val startAngle = index * sectionAngle - 90f
        
        // 繪製扇形
        drawArc(
            color = option.color,
            startAngle = startAngle,
            sweepAngle = sectionAngle,
            useCenter = true,
            topLeft = Offset.Zero,
            size = size
        )
        
        // 繪製邊框
        drawArc(
            color = Color.White,
            startAngle = startAngle,
            sweepAngle = sectionAngle,
            useCenter = true,
            topLeft = Offset.Zero,
            size = size,
            style = Stroke(width = 3.dp.toPx())
        )
        
        // 繪製文字
        val textAngle = startAngle + sectionAngle / 2
        val textRadius = radius * 0.65f // 調整文字距離中心點的距離
        val textX = center.x + textRadius * cos(Math.toRadians(textAngle.toDouble())).toFloat()
        val textY = center.y + textRadius * sin(Math.toRadians(textAngle.toDouble())).toFloat()
        
        rotate(textAngle + 90f, pivot = Offset(textX, textY)) {
            drawContext.canvas.nativeCanvas.apply {
                val fontSize = if (option.name.length > 6) 24f else 32f // 根據文字長度調整大小
                val paint = android.graphics.Paint().apply {
                    this.color = android.graphics.Color.WHITE
                    textSize = fontSize
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                    textAlign = android.graphics.Paint.Align.CENTER
                    setShadowLayer(4f, 2f, 2f, android.graphics.Color.BLACK)
                }
                drawText(option.name, textX, textY, paint)
            }
        }
    }
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