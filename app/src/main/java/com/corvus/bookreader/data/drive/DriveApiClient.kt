package ravens.scroll.data.drive

import android.content.Context
import android.util.Log
import ravens.scroll.data.model.DriveItem
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.ByteArrayContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File
import org.json.JSONObject

/**
 * Progress stored in corvus-progress.json (compatible with VS Code extension):
 *   { "fileId": { "scrollTop": N, "percent": N }, ... }
 *
 * Prefs stored in corvus-prefs.json (synced across devices):
 *   { "fontSize": N, "lineHeight": N, "fontFamily": "...", "theme": "..." }
 */
class DriveApiClient(context: Context, account: GoogleSignInAccount) {

    private val service: Drive

    private var progressCache: MutableMap<String, Pair<Int, Int>>? = null
    private var progressFileId: String? = null
    private var prefsFileId: String? = null

    init {
        val credential = GoogleAccountCredential.usingOAuth2(
            context, listOf(DriveScopes.DRIVE_READONLY, DriveScopes.DRIVE_APPDATA)
        ).apply { selectedAccount = account.account }

        service = Drive.Builder(NetHttpTransport(), GsonFactory.getDefaultInstance(), credential)
            .setApplicationName("BookReader")
            .build()
    }

    // ── File operations ──────────────────────────────────────────────────────────

    fun listFiles(folderId: String): List<DriveItem> {
        val query = if (folderId == "root") "'root' in parents and trashed = false"
                    else "'$folderId' in parents and trashed = false"
        val result = service.files().list()
            .setQ(query)
            .setFields("files(id,name,mimeType,size)")
            .setOrderBy("folder,name")
            .setPageSize(200)
            .execute()
        return result.files.map { f ->
            DriveItem(id = f.id, name = f.name, mimeType = f.mimeType, size = f.getSize() ?: 0L)
        }.filter { it.isFolder || it.name.endsWith(".txt", ignoreCase = true) }
    }

    fun downloadFile(fileId: String): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        service.files().get(fileId).executeMediaAndDownloadTo(out)
        return out.toByteArray()
    }

    // ── Progress (corvus-progress.json, VS Code compatible) ──────────────────────

    private fun loadProgressCache() {
        if (progressCache != null) return
        progressCache = mutableMapOf()
        val fileId = findAppDataFile("corvus-progress.json") ?: run {
            Log.w("DriveProgress", "corvus-progress.json not found in appDataFolder")
            return
        }
        progressFileId = fileId
        try {
            val out = java.io.ByteArrayOutputStream()
            service.files().get(fileId).executeMediaAndDownloadTo(out)
            val obj = JSONObject(out.toString(Charsets.UTF_8.name()))
            for (key in obj.keys()) {
                val entry = obj.optJSONObject(key) ?: continue
                progressCache!![key] = Pair(
                    entry.optInt("scrollTop", 0),
                    entry.optInt("percent", 0),
                )
            }
            val summary = progressCache!!.entries.joinToString { "…${it.key.takeLast(6)}→${it.value.second}%" }
            Log.d("DriveProgress", "loaded ${progressCache!!.size} entries: $summary")
        } catch (e: Exception) {
            Log.e("DriveProgress", "load failed: ${e.message}")
        }
    }

    /** 強制清除快取，下次 getProgressMap() 將從 Drive 重新下載 */
    fun invalidateProgressCache() {
        progressCache = null
        progressFileId = null
    }

    fun getProgressMap(): Map<String, Pair<Int, Int>> {
        loadProgressCache()
        return progressCache ?: emptyMap()
    }

    fun loadProgress(fileId: String): Pair<Int, Int> {
        loadProgressCache()
        return progressCache!![fileId] ?: Pair(0, 0)
    }

    fun saveProgress(fileId: String, scrollTop: Int, percent: Int) {
        loadProgressCache()
        progressCache!![fileId] = Pair(scrollTop, percent)
        Log.d("DriveProgress", "saveProgress: id=...${fileId.takeLast(8)}, scroll=$scrollTop, pct=$percent")
        flushProgress()
    }

    private fun flushProgress() {
        val cache = progressCache ?: return
        val obj = JSONObject()
        for ((id, pair) in cache) {
            obj.put(id, JSONObject().put("scrollTop", pair.first).put("percent", pair.second))
        }
        try {
            writeAppData("corvus-progress.json", progressFileId, obj.toString()) { progressFileId = it }
            Log.d("DriveProgress", "flushProgress OK: ${cache.size} entries written")
        } catch (e: Exception) {
            Log.e("DriveProgress", "flushProgress FAILED: ${e.message}")
        }
    }

    // ── Prefs (corvus-prefs.json, synced across devices) ─────────────────────────

    fun loadPrefs(): Map<String, Any>? {
        val fileId = findAppDataFile("corvus-prefs.json") ?: return null
        prefsFileId = fileId
        return try {
            val out = java.io.ByteArrayOutputStream()
            service.files().get(fileId).executeMediaAndDownloadTo(out)
            val obj = JSONObject(out.toString(Charsets.UTF_8.name()))
            buildMap {
                put("fontSize", obj.optInt("fontSize", 14))
                put("lineHeight", obj.optDouble("lineHeight", 1.2))
                put("fontFamily", obj.optString("fontFamily", "lxgw"))
                put("theme", obj.optString("theme", "dark"))
            }
        } catch (_: Exception) { null }
    }

    fun savePrefs(fontSize: Int, lineHeight: Float, fontFamily: String, theme: String) {
        val json = JSONObject()
            .put("fontSize", fontSize)
            .put("lineHeight", lineHeight.toDouble())
            .put("fontFamily", fontFamily)
            .put("theme", theme)
            .toString()
        val existingId = prefsFileId ?: findAppDataFile("corvus-prefs.json")?.also { prefsFileId = it }
        writeAppData("corvus-prefs.json", existingId, json) { prefsFileId = it }
    }

    // ── Internal ──────────────────────────────────────────────────────────────────

    private fun writeAppData(
        name: String,
        existingId: String?,
        json: String,
        onCreated: (String) -> Unit,
    ) {
        val bytes = json.toByteArray(Charsets.UTF_8)
        val media = ByteArrayContent("application/json", bytes)
        if (existingId != null) {
            service.files().update(existingId, null, media).execute()
        } else {
            val meta = File().apply { this.name = name; parents = listOf("appDataFolder") }
            val created = service.files().create(meta, media).execute()
            onCreated(created.id)
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
