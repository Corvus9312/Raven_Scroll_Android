package ravens.scroll.data.repository

import android.content.Context
import ravens.scroll.data.drive.DriveApiClient
import ravens.scroll.data.model.DriveItem
import ravens.scroll.domain.CharsetDetector
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

    suspend fun getProgressMap(): Map<String, Pair<Int, Int>> = withContext(Dispatchers.IO) {
        client?.getProgressMap() ?: emptyMap()
    }

    suspend fun loadProgress(fileId: String): Pair<Int, Int> = withContext(Dispatchers.IO) {
        client?.loadProgress(fileId) ?: Pair(0, 0)
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
