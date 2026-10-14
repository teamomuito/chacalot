package app.chacalot

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.URL

private const val REPO = "teamomuito/chacalot"

/** Latest GitHub release (tag "v<versionCode>") newer than this build -> download the APK and open the installer. */
fun checkForUpdate(act: Activity, say: (String) -> Unit) {
    if (!act.packageManager.canRequestPackageInstalls()) {
        act.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${act.packageName}")))
        say("Allow installs, then retry.")
        return
    }
    say("Checking...")
    Thread {
        val msg = try {
            val json = JSONObject(URL("https://api.github.com/repos/$REPO/releases/latest").readText())
            if (json.getString("tag_name").removePrefix("v").toInt() <= BuildConfig.VERSION_CODE) {
                "Up to date!"
            } else {
                val assets = json.getJSONArray("assets")
                val url = (0 until assets.length()).map { assets.getJSONObject(it) }
                    .first { it.getString("name").endsWith(".apk") }.getString("browser_download_url")
                act.runOnUiThread { say("Downloading...") }
                val apk = File(act.cacheDir, "updates/chacalot.apk").apply { parentFile!!.mkdirs() }
                URL(url).openStream().use { i -> apk.outputStream().use { i.copyTo(it) } }
                val uri = FileProvider.getUriForFile(act, "${act.packageName}.files", apk)
                act.startActivity(
                    Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                )
                "Installing..."
            }
        } catch (e: Exception) {
            "Update failed!"
        }
        act.runOnUiThread { say(msg) }
    }.start()
}
