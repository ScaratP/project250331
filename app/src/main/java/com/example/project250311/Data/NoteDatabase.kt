package com.example.project250311.Data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Note::class], version = 5, exportSchema = false) // Incremented to version 5
abstract class NoteDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao

    companion object {
        @Volatile
        private var INSTANCE: NoteDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS `notes_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `content` TEXT, `timestamp` INTEGER NOT NULL)")
                database.execSQL("INSERT INTO `notes_new` (`id`, `content`, `timestamp`) SELECT `id`, `content`, `timestamp` FROM `notes`")
                database.execSQL("DROP TABLE `notes`")
                database.execSQL("ALTER TABLE `notes_new` RENAME TO `notes`")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS `notes_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `content` TEXT, `timestamp` INTEGER NOT NULL)")
                database.execSQL("INSERT INTO `notes_new` (`id`, `content`, `timestamp`) SELECT `id`, `content`, `timestamp` FROM `notes`")
                database.execSQL("DROP TABLE `notes`")
                database.execSQL("ALTER TABLE `notes_new` RENAME TO `notes`")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS `notes_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `content` TEXT, `formattedContent` TEXT, `timestamp` INTEGER NOT NULL)")
                database.execSQL("INSERT INTO `notes_new` (`id`, `content`, `timestamp`) SELECT `id`, `content`, `timestamp` FROM `notes`")
                database.execSQL("DROP TABLE `notes`")
                database.execSQL("ALTER TABLE `notes_new` RENAME TO `notes`")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Since the serialization format has changed, we'll clear the formattedContent to force re-creation
                database.execSQL("CREATE TABLE IF NOT EXISTS `notes_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `content` TEXT, `formattedContent` TEXT, `timestamp` INTEGER NOT NULL)")
                database.execSQL("INSERT INTO `notes_new` (`id`, `content`, `timestamp`) SELECT `id`, `content`, `timestamp` FROM `notes`")
                database.execSQL("DROP TABLE `notes`")
                database.execSQL("ALTER TABLE `notes_new` RENAME TO `notes`")
            }
        }

        fun getDatabase(context: Context): NoteDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    NoteDatabase::class.java,
                    "note_database"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}