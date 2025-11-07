package com.example.project250311.Data

import android.util.Log
import androidx.lifecycle.LiveData
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

// --- 1. 公告的資料實體 (Entity) ---
// 這就是我們要在資料庫裡儲存的公告長相
@Entity(tableName = "announcement_table")
data class Announcement(
    @PrimaryKey val url: String, // 用公告的網址當作 PrimaryKey，這樣就不會儲存到重複的公告
    val title: String,          // 公告標題
    val category: String,       // 公告分類 (例如: "重要消息", "校內活動")
    val date: LocalDate         // 公告日期
)

// --- 2. 公告的資料庫存取物件 (DAO) ---
// 這裡定義了所有對 announcement_table 的資料庫操作
@Dao
interface AnnouncementDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(announcement: Announcement)

    // 一次插入所有爬下來的公告，如果網址(PrimaryKey)已存在，就取代舊的
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(announcements: List<Announcement>)

    // 依照日期排序，最新的在最上面
    @Query("SELECT * FROM announcement_table ORDER BY date DESC")
    suspend fun getAllAnnouncements(): List<Announcement>

    // 關鍵！只抓取 "某個日期之後" 的公告 (用來篩選一個月內)
    // :sinceDate 會傳入 "一個月前的日期"
    @Query("SELECT * FROM announcement_table WHERE date >= :sinceDate ORDER BY date DESC")
    suspend fun getAnnouncementsSince(sinceDate: LocalDate): List<Announcement>

    // 用來刪除 "某個日期之前" 的舊資料，保持資料庫乾淨
    @Query("DELETE FROM announcement_table WHERE date < :beforeDate")
    suspend fun deleteAnnouncementsBefore(beforeDate: LocalDate)

    // 清除所有公告 (爬蟲刷新時可能會用到)
    @Query("DELETE FROM announcement_table")
    suspend fun clearAllAnnouncements()
}

// --- 3. 公告的資料庫操作邏輯 (Repository) ---
// 這是 ViewModel 和資料庫溝通的橋樑，把資料庫操作包裝起來
class AnnouncementRepository(private val announcementDao: AnnouncementDao) {

    // 插入所有公告
    suspend fun insertAll(announcements: List<Announcement>) {
        announcementDao.insertAll(announcements)
    }

    // 拿取最近一個月的公告
    suspend fun getRecentAnnouncements(): List<Announcement> {
        val cutoffDate = LocalDate.now().minusMonths(1)
        return announcementDao.getAnnouncementsSince(cutoffDate)
    }

    // 刪除一個月前的舊公告
    suspend fun deleteOldAnnouncements() {
        val cutoffDate = LocalDate.now().minusMonths(1)
        announcementDao.deleteAnnouncementsBefore(cutoffDate)
        Log.d("AnnouncementRepo", "Deleted announcements before $cutoffDate")
    }

    // 清除所有公告
    suspend fun clearAllAnnouncements() {
        announcementDao.clearAllAnnouncements()
    }
}

// --- 4. 公告的畫面邏輯 (ViewModel) ---
// 這是 UI (Screen) 和 Repository 溝通的橋樑
class AnnouncementViewModel(private val repository: AnnouncementRepository) : ViewModel() {

    // 存放公告的 LiveData，UI 會觀察這個資料
    private val _announcements = MutableLiveData<List<Announcement>>()
    val announcements: LiveData<List<Announcement>> get() = _announcements

    // 標記是否正在載入中
    private val _isLoading = MutableLiveData<Boolean>(false)
    val isLoading: LiveData<Boolean> get() = _isLoading

    // 存放錯誤訊息
    private val _errorMessage = MutableLiveData<String?>(null)
    val errorMessage: LiveData<String?> get() = _errorMessage

    /**
     * 從「資料庫」載入最近一個月的公告
     * (這個是給 UI 呼叫的，只讀取 App 內部資料)
     */
    fun loadAnnouncementsFromDb() {
        viewModelScope.launch(Dispatchers.IO) {
            val data = repository.getRecentAnnouncements()
            withContext(Dispatchers.Main) {
                _announcements.value = data
            }
        }
    }

    /**
     * 從「網路」刷新公告資料
     * (這個會觸發爬蟲 -> 存入資料庫 -> 刪除舊資料 -> 更新 UI)
     * * @param fetchFunction 這是從外部 (Screen) 傳入的爬蟲函式
     */
    fun refreshAnnouncementsFromWeb(fetchFunction: suspend () -> List<Announcement>) {
        _isLoading.value = true
        _errorMessage.value = null // 清除舊的錯誤訊息

        viewModelScope.launch {
            try {
                // 1. 執行從 Screen 傳進來的爬蟲 function
                val fetchedData = withContext(Dispatchers.IO) {
                    fetchFunction()
                }

                if (fetchedData.isNotEmpty()) {
                    Log.d("AnnounceViewModel", "Fetched ${fetchedData.size} new announcements")
                    // 2. 存入資料庫
                    repository.insertAll(fetchedData)

                    // 3. 刪掉太舊的資料
                    repository.deleteOldAnnouncements()

                    // 4. 重新從資料庫載入一次資料來更新 LiveData
                    val freshData = repository.getRecentAnnouncements()
                    _announcements.postValue(freshData) // 用 postValue 確保在主執行緒更新
                } else {
                    Log.d("AnnounceViewModel", "Fetched 0 announcements, maybe error or no data")
                    // 如果爬不到東西，至少也載入一次舊資料
                    loadAnnouncementsFromDb()
                }
            } catch (e: Exception) {
                Log.e("AnnounceViewModel", "Error refreshing announcements", e)
                _errorMessage.postValue("載入公告失敗，請稍後再試")
                // 就算失敗，也嘗試載入舊資料
                loadAnnouncementsFromDb()
            } finally {
                _isLoading.postValue(false)
            }
        }
    }
}