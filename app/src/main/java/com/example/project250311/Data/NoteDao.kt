package com.example.project250311.Data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Delete

@Dao
interface NoteDao {
    @Insert
    suspend fun insert(note: Note): Long

    @Delete
    suspend fun delete(note: Note)

    @Query("SELECT * FROM notes ORDER BY timestamp DESC")
    suspend fun getAllNotes(): List<Note>

    @Query("SELECT * FROM notes WHERE content LIKE :query ORDER BY timestamp DESC")
    suspend fun searchNotes(query: String): List<Note>

    // 新增按日期範圍查詢的方法
    @Query("SELECT * FROM notes WHERE timestamp BETWEEN :start AND :end ORDER BY timestamp DESC")
    suspend fun getNotesByDate(start: Long, end: Long): List<Note>
}