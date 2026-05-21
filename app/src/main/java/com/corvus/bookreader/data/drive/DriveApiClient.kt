package com.corvus.bookreader.data.drive

import android.content.Context
import com.corvus.bookreader.data.model.DriveItem
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File
import org.json.JSONObject

class DriveApiClient(context: Context, account: GoogleSignInAccount) {

    private val service: Drive

    init {
        val credential = GoogleAccountCredential.usingOAuth2(
            context,
            listOf(DriveScopes.DRIVE_READONLY, DriveScopes.DRIVE_APPDATA)
        ).apply { selectedAccount = account.account }

        service = Drive.Builder(NetHttpTransport(), GsonFactory.getDefaultInstance(), credential)
            .setApplicationName("BookReader")
            .build()
    }

    fun listFiles(folderId: String): List<DriveItem> {
        val query = if (folderId == "root")
            "'root' in parents and trashed = false"
        else
            "'$folderId' in parents and trashed = false"

        val result = service.files().list()
            .setQ(query)
            .setFields("files(id,name,mimeType,size)")
            .setOrderBy("folder,name")
            .setPageSize(200)
            .execute()

        return result.files.map { f ->
            DriveItem(
                id = f.id,
                name = f.name,
                mimeType = f.mimeType,
                size = f.getSize() ?: 0L,
            )
        }.filter { it.isFolder || it.name.endsWith(".txt", ignoreCase = true) }
    }

    fun downloadFile(fileId: String): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        service.files().get(fileId).executeMediaAndDownloadTo(out)
        return out.toByteArray()
    }

    fun saveAppData(fileId: String, scrollTop: Int, percent: Int) {
        val fileName = "progress_${fileId.hashCode()}.json"
        val content = JSONObject().apply {
            put("scrollTop", scrollTop)
            put("percent", percent)
        }.toString()

        val existing = findAppDataFile(fileName)
        val bytes = content.toByteArray(Charsets.UTF_8)
        val mediaContent = com.google.api.client.http.ByteArrayContent("application/json", bytes)

        if (existing != null) {
            service.files().update(existing, null, mediaContent).execute()
        } else {
            val meta = File().apply {
                name = fileName
                parents = listOf("appDataFolder")
            }
            service.files().create(meta, mediaContent).execute()
        }
    }

    fun loadAppData(fileId: String): Pair<Int, Int> {
        val fileName = "progress_${fileId.hashCode()}.json"
        val id = findAppDataFile(fileName) ?: return Pair(0, 0)
        val out = java.io.ByteArrayOutputStream()
        service.files().get(id).executeMediaAndDownloadTo(out)
        return try {
            val obj = JSONObject(out.toString(Charsets.UTF_8.name()))
            Pair(obj.optInt("scrollTop", 0), obj.optInt("percent", 0))
        } catch (_: Exception) {
            Pair(0, 0)
        }
    }

    private fun findAppDataFile(name: String): String? {
        val result = service.files().list()
            .setSpaces("appDataFolder")
            .setQ("name = '$name'")
            .setFields("files(id)")
            .setPageSize(1)
            .execute()
        return result.files.firstOrNull()?.id
    }
}
