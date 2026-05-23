package ravens.scroll.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import ravens.scroll.data.model.Book
import ravens.scroll.data.model.BookFolder
import ravens.scroll.data.model.DownloadedDriveFile

@Database(
    entities = [Book::class, BookFolder::class, DownloadedDriveFile::class],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun folderDao(): FolderDao
    abstract fun downloadedDriveFileDao(): DownloadedDriveFileDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE books ADD COLUMN driveFileId TEXT")
                database.execSQL("ALTER TABLE books ADD COLUMN pendingSync INTEGER NOT NULL DEFAULT 0")
                database.execSQL(
                    """CREATE TABLE IF NOT EXISTS downloaded_drive_files (
                        driveFileId TEXT NOT NULL PRIMARY KEY,
                        localPath TEXT NOT NULL,
                        driveFileName TEXT NOT NULL,
                        driveFolderName TEXT NOT NULL,
                        downloadedAt INTEGER NOT NULL,
                        lastSyncedAt INTEGER NOT NULL
                    )"""
                )
            }
        }
    }
}
