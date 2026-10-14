package app.chacalot

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.URL

private const val REPO = "teamomuito/chacalot"

/** Returns the APK url if the latest GitHub release (tag "v<versionCode>") is newer, else null. */
private fun latestApk(): String? {
    val json = JSONObject(URL("https://api.github.com/repos/$REPO/releases/latest").readText())
    if (json.getString("tag_name").removePrefix("v").toInt() <= BuildConfig.VERSION_CODE) return null
    val assets = json.getJSONArray("assets")
    return (0 until assets.length()).map { assets.getJSONObject(it) }
        .first { it.getString("name").endsWith(".apk") }.getString("browser_download_url")
}

private fun downloadAndInstall(ctx: Context, url: String) {
    val apk = File(ctx.cacheDir, "updates/chacalot.apk").apply { parentFile!!.mkdirs() }
    URL(url).openStream().use { i -> apk.outputStream().use { i.copyTo(it) } }
    val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", apk)
    ctx.startActivity(
        Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}

@Composable
fun UpdateButton() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf("Check for update") }
    TextButton({
        if (!ctx.packageManager.canRequestPackageInstalls()) {
            ctx.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            return@TextButton
        }
        scope.launch {
            status = "Checking…"
            status = try {
                withContext(Dispatchers.IO) {
                    latestApk()?.let { status = "Downloading…"; downloadAndInstall(ctx, it); "Installing…" } ?: "Up to date"
                }
            } catch (e: Exception) {
                "Update failed: ${e.message}"
            }
        }
    }) { Text(status) }
}
