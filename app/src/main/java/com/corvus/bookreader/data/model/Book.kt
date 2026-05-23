package ravens.scroll.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "books")
data class Book(
    @PrimaryKey val uri: String,
    val title: String,
    val folderUri: String = "",
    val scrollTop: Int = 0,
    val percent: Int = 0,
    val lastRead: Long = 0L,
    val driveFileId: String? = null,
    val pendingSync: Boolean = false,
)
