package com.corvus.bookreader.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "folders")
data class BookFolder(
    @PrimaryKey val treeUri: String,
    val name: String,
    val sortOrder: Int = 0,
)
