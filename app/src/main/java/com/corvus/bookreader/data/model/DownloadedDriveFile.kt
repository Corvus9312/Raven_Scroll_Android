package ravens.scroll.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "downloaded_drive_files")
data class DownloadedDriveFile(
    @PrimaryKey val driveFileId: String,
    val localPath: String,
    val driveFileName: String,
    val driveFolderName: String,
    val downloadedAt: Long = System.currentTimeMillis(),
    val lastSyncedAt: Long = 0L,
)
