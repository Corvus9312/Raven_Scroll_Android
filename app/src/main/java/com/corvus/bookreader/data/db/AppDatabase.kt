package com.corvus.bookreader.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.corvus.bookreader.data.model.Book
import com.corvus.bookreader.data.model.BookFolder

@Database(entities = [Book::class, BookFolder::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun folderDao(): FolderDao
}
