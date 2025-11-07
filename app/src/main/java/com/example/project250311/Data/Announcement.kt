// 檔案名稱: Announcement.kt
// 檔案路徑: com/example/project250311/Data/Announcement.kt
package com.example.project250311.Data

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.TypeConverter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

// --- 1. 公告的資料實體 (Entity) ---
// (這部分保持不變)
@Entity(tableName = "announcement_table")
data class Announcement(
    @PrimaryKey val url: String,
    val title: String,
    val category: String,
    val date: LocalDate
)

// --- 2. 公告的資料庫存取物件 (DAO) ---
// (這部分保持不變)
@Dao
interface AnnouncementDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(announcement: Announcement)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(announcements: List<Announcement>)

    @Query("SELECT * FROM announcement_table ORDER BY date DESC")
    suspend fun getAllAnnouncements(): List<Announcement>

    @Query("SELECT * FROM announcement_table WHERE date >= :sinceDate ORDER BY date DESC")
    suspend fun getAnnouncementsSince(sinceDate: LocalDate): List<Announcement>

    @Query("DELETE FROM announcement_table WHERE date < :beforeDate")
    suspend fun deleteAnnouncementsBefore(beforeDate: LocalDate)

    @Query("DELETE FROM announcement_table")
    suspend fun clearAllAnnouncements()
}

// --- 3. 公告的資料庫操作邏輯 (Repository) ---
// (這部分保持不變)
class AnnouncementRepository(private val announcementDao: AnnouncementDao) {

    suspend fun insertAll(announcements: List<Announcement>) {
        announcementDao.insertAll(announcements)
    }

    suspend fun getRecentAnnouncements(): List<Announcement> {
        val cutoffDate = LocalDate.now().minusMonths(1)
        return announcementDao.getAnnouncementsSince(cutoffDate)
    }

    suspend fun deleteOldAnnouncements() {
        val cutoffDate = LocalDate.now().minusMonths(1)
        announcementDao.deleteAnnouncementsBefore(cutoffDate)
        Log.d("AnnouncementRepo", "Deleted announcements before $cutoffDate")
    }

    suspend fun clearAllAnnouncements() {
        announcementDao.clearAllAnnouncements()
    }
}

// --- 4. 公告的畫面邏輯 (ViewModel) ---
// (*** 這是修改過的新版本 ***)
class AnnouncementViewModel(private val repository: AnnouncementRepository) : ViewModel() {

    // 存放「所有」從資料庫抓回來的公告
    private val _allAnnouncements = MutableLiveData<List<Announcement>>(emptyList())

    // 存放「上次刷新時間」
    private val _lastRefreshedTime = MutableLiveData<LocalDateTime?>(null)
    val lastRefreshedTime: LiveData<LocalDateTime?> get() = _lastRefreshedTime

    // 存放「目前選擇的分類」
    private val _selectedCategory = MutableLiveData<String>("全部")
    val selectedCategory: LiveData<String> get() = _selectedCategory

    // 存放「是否正在載入」
    private val _isLoading = MutableLiveData<Boolean>(false)
    val isLoading: LiveData<Boolean> get() = _isLoading

    // 存放「錯誤訊息」
    private val _errorMessage = MutableLiveData<String?>(null)
    val errorMessage: LiveData<String?> get() = _errorMessage

    // --- 這是公開給 UI 觀察的「已過濾」公告列表 ---
    val announcements: LiveData<List<Announcement>> = MediatorLiveData<List<Announcement>>().apply {
        // 來源1: 觀察「所有公告」列表
        addSource(_allAnnouncements) { allList ->
            val category = _selectedCategory.value ?: "全部"
            value = filterList(allList, category)
        }
        // 來源2: 觀察「選擇的分類」
        addSource(_selectedCategory) { category ->
            val allList = _allAnnouncements.value ?: emptyList()
            value = filterList(allList, category)
        }
    }

    // 過濾邏輯的 helper function
    private fun filterList(list: List<Announcement>, category: String): List<Announcement> {
        return if (category == "全部") {
            list
        } else {
            list.filter { it.category == category }
        }
    }

    /**
     * 從「資料庫」載入最近一個月的公告
     */
    fun loadAnnouncementsFromDb() {
        viewModelScope.launch(Dispatchers.IO) {
            val data = repository.getRecentAnnouncements()
            _allAnnouncements.postValue(data)
        }
    }

    /**
     * 從「網路」刷新公告資料
     */
    fun refreshAnnouncementsFromWeb(fetchFunction: suspend () -> List<Announcement>) {
        _isLoading.value = true
        _errorMessage.value = null

        viewModelScope.launch {
            try {
                val fetchedData = withContext(Dispatchers.IO) {
                    fetchFunction()
                }

                if (fetchedData.isNotEmpty()) {
                    Log.d("AnnounceViewModel", "Fetched ${fetchedData.size} new announcements")
                    repository.insertAll(fetchedData)
                    repository.deleteOldAnnouncements()

                    val freshData = repository.getRecentAnnouncements()
                    _allAnnouncements.postValue(freshData) // 更新「所有」公告
                    _lastRefreshedTime.postValue(LocalDateTime.now()) // 更新刷新時間
                } else {
                    Log.d("AnnounceViewModel", "Fetched 0 announcements, maybe error or no data")
                    loadAnnouncementsFromDb()
                }
            } catch (e: Exception) {
                Log.e("AnnounceViewModel", "Error refreshing announcements", e)
                _errorMessage.postValue("載入公告失敗，請稍後再試")
                loadAnnouncementsFromDb()
            } finally {
                _isLoading.postValue(false)
            }
        }
    }

    /**
     * 讓 UI 呼叫，用來更新選擇的分類
     */
    fun selectCategory(category: String) {
        _selectedCategory.value = category
    }
}