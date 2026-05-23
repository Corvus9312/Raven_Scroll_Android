package ravens.scroll

import android.app.Application
import androidx.room.Room
import ravens.scroll.data.db.AppDatabase
import ravens.scroll.data.repository.DriveRepository

class BookReaderApp : Application() {

    lateinit var database: AppDatabase
        private set

    val driveRepository: DriveRepository by lazy { DriveRepository(this) }

    override fun onCreate() {
        super.onCreate()
        instance = this
        database = Room.databaseBuilder(this, AppDatabase::class.java, "bookreader.db")
            .fallbackToDestructiveMigration()
            .build()
    }

    companion object {
        lateinit var instance: BookReaderApp
            private set
    }
}
