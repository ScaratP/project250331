package com.example.project250311.Schedule.NoSchool

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
import androidx.room.Update
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Entity(tableName = "leave_table")
data class LeaveData(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val recordType: String,   // "課程" 或 "集會"
    val leave_type: String,   // 課程 & 集會請假類型
    val date_leave: String,   // 日期 (課程 or 集會)
    val courseName: String,    // 課程名稱 或 集會名稱
    val hours: Int            // 時數
)

@Dao
interface LeaveDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(leave: LeaveData)

    @Update
    suspend fun update(leave: LeaveData)

    @Query("SELECT * FROM leave_table WHERE recordType = :recordType")
    fun getLeavesByRecordType(recordType: String): LiveData<List<LeaveData>> // 新增：查詢「課程」或「集會」

    @Query("SELECT * FROM leave_table WHERE leave_type = :leave_type")
    fun getLeavesByType(leave_type: String): LiveData<List<LeaveData>>

    @Query("SELECT * FROM leave_table WHERE date_leave = :date_leave")
    suspend fun getLeaveByDate(date_leave: String): LeaveData?

    @Query("SELECT * FROM leave_table WHERE courseName = :courseName")
    suspend fun getLeaveByEvent(courseName: String): LeaveData?

    @Query("SELECT * FROM leave_table")
    suspend fun getAllLeaves(): List<LeaveData>

    @Query("SELECT * FROM leave_table WHERE recordType = :recordType AND courseName = :courseName")
    suspend fun getLeaveByRecordTypeAndCourse(recordType: String, courseName: String): List<LeaveData>


}


@Database(entities = [LeaveData::class], version = 2, exportSchema = false)
abstract class LeaveDatabase : RoomDatabase() {
    abstract fun leaveDao(): LeaveDao

    companion object {
        @Volatile
        private var INSTANCE: LeaveDatabase? = null
        fun getDatabase(context: Context): LeaveDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    LeaveDatabase::class.java,
                    "leave_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }

        class LeaveRepository(private val leaveDao: LeaveDao) {

            suspend fun insertOrUpdateLeave(leave: LeaveData) {
                // 先找相同 recordType 和 courseName 的舊資料
                val sameCourseLeaves = leaveDao.getLeaveByRecordTypeAndCourse(leave.recordType, leave.courseName)

                val existWithSameDate = sameCourseLeaves.find { it.date_leave == leave.date_leave }

                if (existWithSameDate != null) {
                    // 完全相同（recordType、courseName、date_leave），直接 update
                    val updatedLeave = existWithSameDate.copy(hours = leave.hours)
                    leaveDao.update(updatedLeave)
                    Log.d("LeaveRepository", "update成功: $updatedLeave")
                } else if (sameCourseLeaves.isNotEmpty()) {
                    // recordType 和 courseName 相同，但 date 不同
                    // 加總 hours
                    val totalHours = sameCourseLeaves.sumOf { it.hours } + leave.hours
                    val newLeave = leave.copy(hours = totalHours)
                    leaveDao.insert(newLeave)
                    Log.d("LeaveRepository", "新增並合併hours成功: $newLeave")
                } else {
                    // 完全新資料
                    leaveDao.insert(leave)
                    Log.d("LeaveRepository", "insert成功: $leave")
                }
            }


            suspend fun getAllLeaves(): List<LeaveData> = leaveDao.getAllLeaves()

            fun getLeavesByType(leaveType: String): LiveData<List<LeaveData>> =
                leaveDao.getLeavesByType(leaveType)

            fun getLeavesByRecordType(recordType: String): LiveData<List<LeaveData>> =
                leaveDao.getLeavesByRecordType(recordType)

        }
    }

    class LeaveViewModel(private val repository: LeaveRepository) : ViewModel() {

        private val _allLeaves = MutableLiveData<List<LeaveData>>()
        val allLeaves: LiveData<List<LeaveData>> get() = _allLeaves

        fun getLeavesByType(leaveType: String): LiveData<List<LeaveData>> {
            return repository.getLeavesByType(leaveType)
        }

        fun getLeavesByRecordType(recordType: String): LiveData<List<LeaveData>> {
            return repository.getLeavesByRecordType(recordType)
        }

        fun insert(leave: LeaveData) {
            viewModelScope.launch(Dispatchers.IO) {
                repository.insertOrUpdateLeave(leave)
                loadAllLeaves()
            }
        }

        fun loadAllLeaves() {
            Log.d("LeaveViewModel", "loadAllLeaves() 被調用") // 添加這行
            viewModelScope.launch(Dispatchers.IO) {
                val leaves = repository.getAllLeaves()
                Log.d("LeaveViewModel", "從資料庫獲取到 ${leaves.size} 筆資料") // 添加這行
                _allLeaves.postValue(leaves)
            }
        }

    }

}