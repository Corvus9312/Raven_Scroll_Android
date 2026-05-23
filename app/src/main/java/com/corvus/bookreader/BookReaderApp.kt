package ravens.scroll

import android.app.Application
import androidx.room.Room
import ravens.scroll.data.db.AppDatabase
import ravens.scroll.data.repository.BookRepository
import ravens.scroll.data.repository.DriveRepository

class BookReaderApp : Application() {

    lateinit var database: AppDatabase
        private set

    val driveRepository: DriveRepository by lazy { DriveRepository(this) }

    val bookRepository: BookRepository by lazy {
        BookRepository(this, database.bookDao(), database.downloadedDriveFileDao(), driveRepository)
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        database = Room.databaseBuilder(this, AppDatabase::class.java, "bookreader.db")
            .addMigrations(AppDatabase.MIGRATION_1_2)
            .fallbackToDestructiveMigration()
            .build()
    }

    companion object {
        lateinit var instance: BookReaderApp
            private set
    }
}
