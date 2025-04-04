package com.example.project250311.Data

import android.content.Context
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalTime

@Entity(tableName = "course_table")
data class Schedule(
    @PrimaryKey val id: String,
    val courseName: String,
    val teacherName: String,
    val location: String,
    val weekDay: String,// 星期幾，例如 "Monday"
    val startTime: LocalTime,
    val endTime: LocalTime,
    val courseDates: List<LocalDate>,// 每門課程的日期清單
    val isNotificationEnabled: Boolean = false // 從 250311 中保留的通知開關
)

@Dao
interface CourseDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(course: Schedule)

    @Query("SELECT * FROM course_table WHERE weekDay = :weekDay AND startTime = :startTime")
    fun getCourseByTime(weekDay: String, startTime: String): LiveData<List<Schedule>>

    @Query("SELECT * FROM course_table")
    suspend fun getAllCourses(): List<Schedule>

    @Query("DELETE FROM course_table")
    suspend fun clearAllCourses()

    @Query("UPDATE course_table SET isNotificationEnabled = :isEnabled WHERE id = :courseId")
    suspend fun updateNotificationStatus(courseId: String, isEnabled: Boolean)

    @Query("SELECT * FROM course_table WHERE isNotificationEnabled = 1")
    suspend fun getAllCoursesWithNotificationsEnabled(): List<Schedule>
}

class Converters {
    @TypeConverter
    fun fromLocalTime(time: LocalTime?): String? {
        return time?.toString() // 例如 "09:10"
    }

    @TypeConverter
    fun toLocalTime(timeString: String?): LocalTime? {
        return timeString?.let { LocalTime.parse(it) }
    }

    @TypeConverter
    fun fromBoolean(value: Boolean): Int {
        return if (value) 1 else 0
    }

    @TypeConverter
    fun toBoolean(value: Int): Boolean {
        return value == 1
    }

    @TypeConverter
    fun fromLocalDateList(dates: List<LocalDate>?): String? {
        return dates?.joinToString(",") { it.toString() }
    }

    @TypeConverter
    fun toLocalDateList(datesString: String?): List<LocalDate>? {
        return datesString?.split(",")?.map { LocalDate.parse(it) }
    }
}

@Database(entities = [Schedule::class], version = 3, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun courseDao(): CourseDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "course_database"
                )
                    .fallbackToDestructiveMigration() // 升級數據庫結構時允許清除舊資料
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

class CourseRepository(private val courseDao: CourseDao) {
    suspend fun insert(course: Schedule) = courseDao.insert(course)
    fun getCourseByTime(weekDay: String, startTime: String): LiveData<List<Schedule>> =
        courseDao.getCourseByTime(weekDay, startTime)
    suspend fun getAllCourses(): List<Schedule> = courseDao.getAllCourses()
    suspend fun clearAllCourses() = courseDao.clearAllCourses()
    suspend fun updateNotificationStatus(courseId: String, isEnabled: Boolean) =
        courseDao.updateNotificationStatus(courseId, isEnabled)
    suspend fun getAllCoursesWithNotificationsEnabled(): List<Schedule> =
        courseDao.getAllCoursesWithNotificationsEnabled()
}

class CourseViewModel(private val repository: CourseRepository) : ViewModel() {
    private val _allCourses = MutableLiveData<List<Schedule>>()
    val allCourses: LiveData<List<Schedule>> get() = _allCourses

    private val _selectedCourses = MutableLiveData<List<Schedule>?>(null)
    val selectedCourses: LiveData<List<Schedule>?> get() = _selectedCourses

    // 改為 suspend 函式直接回傳 Boolean
    suspend fun loadAllCourses(): Boolean = withContext(Dispatchers.IO) {
        val courses = repository.getAllCourses()
        Log.d("CourseViewModel", "Loaded courses: $courses")
        withContext(Dispatchers.Main) {
            _allCourses.value = courses
        }
        courses.isNotEmpty()
    }

    fun insertCourse(course: Schedule): Job {
        return viewModelScope.launch(Dispatchers.IO) {
            repository.insert(course)
            Log.d("DatabaseCheck", "Inserted Course: $course")
        }
    }

    fun clearAllCourses(): Job {
        return viewModelScope.launch(Dispatchers.IO) {
            repository.clearAllCourses()
            _allCourses.postValue(emptyList())
            Log.d("CourseViewModel", "Cleared all courses")
        }
    }

    fun selectCourses(courses: List<Schedule>?) {
        _selectedCourses.value = courses
    }

    fun updateCourseLocation(courseId: String, newLocation: String): Job {
        return viewModelScope.launch {
            // 取得目前的課程資料，請在 Main 執行緒中讀取 LiveData
            val currentCourses = withContext(Dispatchers.Main) {
                _allCourses.value?.toMutableList() ?: mutableListOf()
            }
            val courseToUpdate = currentCourses.find { it.id == courseId }
            courseToUpdate?.let {
                val updatedCourse = it.copy(location = newLocation)
                // 只在 IO 執行緒中執行一次資料庫插入操作
                withContext(Dispatchers.IO) {
                    repository.insert(updatedCourse)
                }
                // 更新 LiveData 值請在 Main 執行緒中進行
                val updatedCourses = currentCourses.map { course ->
                    if (course.id == courseId) updatedCourse else course
                }
                _allCourses.value = updatedCourses

                _selectedCourses.value?.let { selected ->
                    val updatedSelected = selected.map { course ->
                        if (course.id == courseId) updatedCourse else course
                    }
                    _selectedCourses.value = updatedSelected
                }
            }
        }
    }

    // 新增更新通知開關狀態的函數
    fun updateNotificationStatus(courseId: String, isEnabled: Boolean): Job {
        return viewModelScope.launch {
            // 先更新資料庫
            withContext(Dispatchers.IO) {
                repository.updateNotificationStatus(courseId, isEnabled)
            }

            // 接著更新 LiveData
            val currentCourses = withContext(Dispatchers.Main) {
                _allCourses.value?.toMutableList() ?: mutableListOf()
            }

            val updatedCourses = currentCourses.map { course ->
                if (course.id == courseId) course.copy(isNotificationEnabled = isEnabled) else course
            }

            _allCourses.value = updatedCourses

            // 如果當前有選中的課程，也需要更新
            _selectedCourses.value?.let { selected ->
                val updatedSelected = selected.map { course ->
                    if (course.id == courseId) course.copy(isNotificationEnabled = isEnabled) else course
                }
                _selectedCourses.value = updatedSelected
            }
        }
    }
}