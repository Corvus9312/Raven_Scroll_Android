package ravens.scroll.data.db

import androidx.room.*
import ravens.scroll.data.model.DownloadedDriveFile
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadedDriveFileDao {

    @Query("SELECT * FROM downloaded_drive_files")
    suspend fun getAll(): List<DownloadedDriveFile>

    @Query("SELECT driveFileId FROM downloaded_drive_files")
    fun flowAllIds(): Flow<List<String>>

    @Query("SELECT * FROM downloaded_drive_files WHERE driveFileId = :id LIMIT 1")
    suspend fun get(id: String): DownloadedDriveFile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(file: DownloadedDriveFile)

    @Query("DELETE FROM downloaded_drive_files WHERE driveFileId = :id")
    suspend fun delete(id: String)

    @Query("UPDATE downloaded_drive_files SET lastSyncedAt = :ts WHERE driveFileId = :id")
    suspend fun updateLastSynced(id: String, ts: Long)
}
