package com.duplicatecleaner.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [DuplicateFileEntity::class], version = 1, exportSchema = false)
abstract class DuplicateDatabase : RoomDatabase() {
    abstract fun duplicateDao(): DuplicateDao
}
