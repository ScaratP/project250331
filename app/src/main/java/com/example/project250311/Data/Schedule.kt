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
import kotlinx.coroutines.launch
import java.time.LocalTime

@Entity(tableName = "course_table")
data class CourseEntity(
    @PrimaryKey val id: String,
    val courseName: String,
    val teacherName: String,
    val location: String,
    val weekDay: String,
    val startTime: LocalTime, // 改為 LocalTime
    val endTime: LocalTime   // 改為 LocalTime
)


@Dao
interface CourseDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(course: CourseEntity)

    @Query("SELECT * FROM course_table WHERE weekDay = :weekDay AND startTime = :startTime")
    fun getCourseByTime(weekDay: String, startTime: String): LiveData<List<CourseEntity>>

    @Query("SELECT * FROM course_table")
    suspend fun getAllCourses(): List<CourseEntity>

    @Query("DELETE FROM course_table")
    suspend fun clearAllCourses()
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
}

@Database(entities = [CourseEntity::class], version = 2, exportSchema = false)
@TypeConverters(Converters::class) // 註冊轉換器
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
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}

class CourseRepository(private val courseDao: CourseDao) {
    suspend fun insert(course: CourseEntity) = courseDao.insert(course)
    fun getCourseByTime(weekDay: String, startTime: String): LiveData<List<CourseEntity>> = courseDao.getCourseByTime(weekDay, startTime)
    suspend fun getAllCourses(): List<CourseEntity> = courseDao.getAllCourses()
    suspend fun clearAllCourses() = courseDao.clearAllCourses()
}

class CourseViewModel(private val repository: CourseRepository) : ViewModel() {
    private val _allCourses = MutableLiveData<List<CourseEntity>>()
    val allCourses: LiveData<List<CourseEntity>> get() = _allCourses

    private val _selectedCourses = MutableLiveData<List<CourseEntity>?>(null)
    val selectedCourses: LiveData<List<CourseEntity>?> get() = _selectedCourses

    fun loadAllCourses() {
        viewModelScope.launch(Dispatchers.IO) {
            val courses = repository.getAllCourses()
            _allCourses.postValue(courses)
        }
    }

    fun insertCourse(course: CourseEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.insert(course)
            Log.d("DatabaseCheck", "Inserted Course: $course")
        }
    }

    fun clearAllCourses() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.clearAllCourses()
        }
    }

    fun selectCourses(courses: List<CourseEntity>?) {
        _selectedCourses.value = courses
    }
}