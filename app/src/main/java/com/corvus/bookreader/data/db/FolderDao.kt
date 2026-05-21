package com.corvus.bookreader.data.db

import androidx.room.*
import com.corvus.bookreader.data.model.BookFolder
import kotlinx.coroutines.flow.Flow

@Dao
interface FolderDao {
    @Query("SELECT * FROM folders ORDER BY sortOrder ASC, name ASC")
    fun flowAll(): Flow<List<BookFolder>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(folder: BookFolder)

    @Delete
    suspend fun delete(folder: BookFolder)

    @Query("SELECT COUNT(*) FROM folders")
    suspend fun count(): Int
}
