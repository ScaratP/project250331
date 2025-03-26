package com.example.project250311.Data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Note::class, NoteSegment::class], version = 2, exportSchema = false)
abstract class NoteDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao

    companion object {
        @Volatile
        private var INSTANCE: NoteDatabase? = null

        // 定義從版本 1 到版本 2 的遷移
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `note_segments` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `noteId` INTEGER NOT NULL,
                        `content` TEXT NOT NULL,
                        `start` INTEGER NOT NULL,
                        `end` INTEGER NOT NULL,
                        `fontSize` REAL NOT NULL,
                        `fontColor` INTEGER NOT NULL,
                        `backgroundColor` INTEGER NOT NULL,
                        `isBold` INTEGER NOT NULL,
                        `isItalic` INTEGER NOT NULL,
                        FOREIGN KEY(`noteId`) REFERENCES `notes`(`id`) ON DELETE CASCADE
                    )
                """.trimIndent())
            }
        }

        fun getDatabase(context: Context): NoteDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    NoteDatabase::class.java,
                    "note_database"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}