package com.example.project250311.Data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface NoteDao {
    @Insert
    suspend fun insert(note: Note): Long

    @Insert
    suspend fun insertSegment(segment: NoteSegment)

    @Update
    suspend fun update(note: Note)

    @Query("SELECT * FROM notes")
    suspend fun getAllNotes(): List<Note>

    @Query("SELECT * FROM note_segments WHERE noteId = :noteId")
    suspend fun getSegmentsForNote(noteId: Int): List<NoteSegment>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun getNoteById(id: Int): Note?

    @Query("SELECT * FROM notes WHERE name = :name")
    suspend fun getNoteByName(name: String): Note?

    @Query("DELETE FROM note_segments WHERE noteId = :noteId")
    suspend fun deleteSegmentsForNote(noteId: Int)
}