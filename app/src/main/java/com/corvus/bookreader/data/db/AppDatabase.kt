package ravens.scroll.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import ravens.scroll.data.model.Book
import ravens.scroll.data.model.BookFolder

@Database(entities = [Book::class, BookFolder::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun folderDao(): FolderDao
}
