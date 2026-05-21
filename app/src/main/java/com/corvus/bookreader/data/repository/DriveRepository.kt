package com.corvus.bookreader.data.repository

import android.content.Context
import com.corvus.bookreader.data.drive.DriveApiClient
import com.corvus.bookreader.data.model.DriveItem
import com.corvus.bookreader.domain.CharsetDetector
import com.google.android.gms.auth.api.signin.GoogleSignIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DriveRepository(private val context: Context) {

    private var client: DriveApiClient? = null

    fun isSignedIn(): Boolean = GoogleSignIn.getLastSignedInAccount(context) != null

    fun initClient() {
        val account = GoogleSignIn.getLastSignedInAccount(context) ?: return
        client = DriveApiClient(context, account)
    }

    suspend fun listFolder(folderId: String = "root"): List<DriveItem> = withContext(Dispatchers.IO) {
        client?.listFiles(folderId) ?: emptyList()
    }

    suspend fun readFile(fileId: String): String = withContext(Dispatchers.IO) {
        val bytes = client?.downloadFile(fileId) ?: return@withContext ""
        CharsetDetector.decode(bytes)
    }

    suspend fun saveProgress(fileId: String, scrollTop: Int, percent: Int) = withContext(Dispatchers.IO) {
        client?.saveAppData(fileId, scrollTop, percent)
    }

    suspend fun loadProgress(fileId: String): Pair<Int, Int> = withContext(Dispatchers.IO) {
        client?.loadAppData(fileId) ?: Pair(0, 0)
    }

    fun signOut() {
        GoogleSignIn.getClient(context, com.google.android.gms.auth.api.signin.GoogleSignInOptions.DEFAULT_SIGN_IN)
            .signOut()
        client = null
    }
}
