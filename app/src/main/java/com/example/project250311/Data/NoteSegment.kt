package com.example.project250311.Data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "note_segments",
    foreignKeys = [ForeignKey(
        entity = Note::class,
        parentColumns = ["id"],
        childColumns = ["noteId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class NoteSegment(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val noteId: Int, // 關聯到 Note 的 id
    val content: String, // 該段的文字內容
    val start: Int, // 該段在整個文字中的起始位置
    val end: Int, // 該段在整個文字中的結束位置
    val fontSize: Float, // 字體大小
    val fontColor: Long, // 字體顏色
    val backgroundColor: Long, // 背景顏色
    val isBold: Boolean, // 是否粗體
    val isItalic: Boolean // 是否斜體
)