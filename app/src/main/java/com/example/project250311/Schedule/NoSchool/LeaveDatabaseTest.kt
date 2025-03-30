package com.example.project250311.Schedule.NoSchool

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LeaveDatabaseTest {

    private lateinit var database: LeaveDatabase
    private lateinit var leaveDao: LeaveDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        // 建立一個 in-memory 資料庫，測試期間使用
        database = Room.inMemoryDatabaseBuilder(context, LeaveDatabase::class.java)
            .allowMainThreadQueries() // 測試時允許在主執行緒操作
            .build()
        leaveDao = database.leaveDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun testInsertAndGetLeave() = runBlocking {
        // 建立一筆假資料
        val leave = LeaveData(
            recordType = "課程",
            leave_type = "病假",
            date_leave = "2025-03-30",
            courseName = "數學",
            hours = 2
        )
        // 插入資料
        leaveDao.insert(leave)
        // 根據日期與事件名稱取得資料
        val retrievedLeave = leaveDao.getLeaveByDate("2025-03-30", )
        assertNotNull(retrievedLeave)
        assertEquals("病假", retrievedLeave?.leave_type)
    }

    @Test
    fun testDeleteLeave() = runBlocking {
        // 建立一筆假資料
        val leave = LeaveData(
            recordType = "課程",
            leave_type = "事假",
            date_leave = "2025-03-30",
            courseName = "英語",
            hours = 1
        )
        // 插入資料
        leaveDao.insert(leave)
        // 取得資料並驗證不為 null
        val retrievedLeave = leaveDao.getLeaveByDate("2025-03-30")
        assertNotNull(retrievedLeave)
        // 驗證刪除成功
        val deletedLeave = leaveDao.getLeaveByDate("2025-03-30")
        assertNull(deletedLeave)
    }
}
