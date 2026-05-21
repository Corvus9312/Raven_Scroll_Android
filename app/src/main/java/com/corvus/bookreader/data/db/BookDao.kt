package com.corvus.bookreader.data.db

import androidx.room.*
import com.corvus.bookreader.data.model.Book
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
    @Query("SELECT * FROM books ORDER BY lastRead DESC")
    fun flowAll(): Flow<List<Book>>

    @Query("SELECT * FROM books WHERE uri = :uri LIMIT 1")
    suspend fun get(uri: String): Book?

    @Query("SELECT * FROM books WHERE folderUri = :folderUri ORDER BY title ASC")
    suspend fun getByFolder(folderUri: String): List<Book>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(book: Book)

    @Query("UPDATE books SET scrollTop = :scrollTop, percent = :percent, lastRead = :lastRead WHERE uri = :uri")
    suspend fun updateProgress(uri: String, scrollTop: Int, percent: Int, lastRead: Long)

    @Query("UPDATE books SET scrollTop = 0, percent = 0 WHERE uri = :uri")
    suspend fun resetProgress(uri: String)

    @Query("UPDATE books SET scrollTop = 0, percent = 0 WHERE folderUri = :folderUri")
    suspend fun resetFolderProgress(folderUri: String)

    @Query("DELETE FROM books WHERE uri = :uri")
    suspend fun delete(uri: String)

    @Query("DELETE FROM books WHERE folderUri = :folderUri")
    suspend fun deleteByFolder(folderUri: String)
}
