package ravens.scroll.data.repository

import android.content.Context
import ravens.scroll.data.drive.DriveApiClient
import ravens.scroll.data.model.DriveItem
import ravens.scroll.domain.CharsetDetector
import com.google.android.gms.auth.api.signin.GoogleSignIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class DriveRepository(private val context: Context) {

    private var client: DriveApiClient? = null

    fun isSignedIn(): Boolean = GoogleSignIn.getLastSignedInAccount(context) != null

    fun hasClient(): Boolean = client != null

    fun initClient() {
        if (client != null) return
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

    suspend fun downloadFileBytes(fileId: String): ByteArray = withContext(Dispatchers.IO) {
        client?.downloadFile(fileId) ?: ByteArray(0)
    }

    /** Downloads a Drive file to Raven's Scroll/{folderName}/{fileName} and returns the local path. */
    suspend fun downloadAndSave(
        fileId: String,
        fileName: String,
        folderName: String,
        ravensScrollDir: File,
    ): String = withContext(Dispatchers.IO) {
        val bytes = client!!.downloadFile(fileId)
        val dir = if (folderName.isEmpty()) ravensScrollDir
                  else File(ravensScrollDir, folderName).also { it.mkdirs() }
        val file = File(dir, fileName)
        file.writeBytes(bytes)
        file.absolutePath
    }

    suspend fun getProgressMap(): Map<String, Pair<Int, Int>> = withContext(Dispatchers.IO) {
        client?.getProgressMap() ?: emptyMap()
    }

    suspend fun loadProgress(fileId: String): Pair<Int, Int> = withContext(Dispatchers.IO) {
        client?.loadProgress(fileId) ?: Pair(0, 0)
    }

    suspend fun getProgressUpdatedAt(fileId: String): Long = withContext(Dispatchers.IO) {
        client?.getProgressUpdatedAt(fileId) ?: 0L
    }

    suspend fun getProgressTimeMap(): Map<String, Long> = withContext(Dispatchers.IO) {
        client?.getProgressTimeMap() ?: emptyMap()
    }

    suspend fun saveProgress(fileId: String, scrollTop: Int, percent: Int) = withContext(Dispatchers.IO) {
        client?.saveProgress(fileId, scrollTop, percent)
    }

    suspend fun loadPrefs(): Map<String, Any>? = withContext(Dispatchers.IO) {
        client?.loadPrefs()
    }

    suspend fun savePrefs(fontSize: Int, lineHeight: Float, fontFamily: String, theme: String) =
        withContext(Dispatchers.IO) {
            client?.savePrefs(fontSize, lineHeight, fontFamily, theme)
        }

    fun invalidateProgressCache() {
        client?.invalidateProgressCache()
    }

    fun signOut() {
        GoogleSignIn.getClient(
            context,
            com.google.android.gms.auth.api.signin.GoogleSignInOptions.DEFAULT_SIGN_IN,
        ).revokeAccess()
        client = null
    }
}
