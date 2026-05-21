package com.corvus.bookreader.data.model

data class DriveItem(
    val id: String,
    val name: String,
    val mimeType: String,
    val size: Long = 0L,
) {
    val isFolder: Boolean get() = mimeType == "application/vnd.google-apps.folder"
}
