package com.example.project250311.Schedule.NoSchool

import android.content.Context
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
import java.time.LocalTime

@Entity(tableName = "leave_table")
data class LeaveData(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val leave_type: String,
    val date_leave:String,
    val courseName: String,
    val hours: Int
)
@Dao
interface LeaveDao {

    @Query("SELECT * FROM leave_table WHERE date_leave = :date_leave AND courseName = :courseName")
    suspend fun getLeaveByDateAndCourse(date_leave: String, courseName: String): LeaveData?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(leave: LeaveData)

    @Update
    suspend fun update(leave: LeaveData)

    @Query("SELECT * FROM leave_table WHERE leave_type = :leave_type")
    fun getLeavesByType(leave_type: String): LiveData<List<LeaveData>>

    @Query("SELECT * FROM leave_table WHERE courseName = :courseName")
    fun getLeavesByCourse(courseName: String): LiveData<List<LeaveData>>

    @Query("SELECT * FROM leave_table")
    suspend fun getAllLeaves(): List<LeaveData>

    @Query("DELETE FROM leave_table WHERE id = :leaveId")
    suspend fun deleteLeaveById(leaveId: Int)

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
                val existingLeave =
                    leaveDao.getLeaveByDateAndCourse(leave.date_leave, leave.courseName)
                if (existingLeave == null) {
                    leaveDao.insert(leave)
                } else {
                    leaveDao.update(leave)
                }
            }

            suspend fun getAllLeaves(): List<LeaveData> = leaveDao.getAllLeaves()

            fun getLeavesByType(leaveType: String): LiveData<List<LeaveData>> =
                leaveDao.getLeavesByType(leaveType)

            fun getLeavesByCourse(courseName: String): LiveData<List<LeaveData>> =
                leaveDao.getLeavesByCourse(courseName)

            suspend fun deleteLeaveById(leaveId: Int) {
                leaveDao.deleteLeaveById(leaveId)
            }

        }
    }

    class LeaveViewModel(private val repository: LeaveRepository) : ViewModel() {

        private val _allLeaves = MutableLiveData<List<LeaveData>>()
        val allLeaves: LiveData<List<LeaveData>> get() = _allLeaves

        fun getLeavesByType(leaveType: String): LiveData<List<LeaveData>> {
            return repository.getLeavesByType(leaveType)
        }

        fun getLeavesByCourse(courseName: String): LiveData<List<LeaveData>> {
            return repository.getLeavesByCourse(courseName)
        }

        fun insertOrUpdateLeave(leave: LeaveData) {
            viewModelScope.launch(Dispatchers.IO) {
                repository.insertOrUpdateLeave(leave)
            }
        }

        fun loadAllLeaves() {
            viewModelScope.launch(Dispatchers.IO) {
                val leaves = repository.getAllLeaves()
                _allLeaves.postValue(leaves)
            }
        }

        fun deleteLeaveById(leaveId: Int) {
            viewModelScope.launch(Dispatchers.IO) {
                repository.deleteLeaveById(leaveId)
            }
        }
    }
}