package app.chacalot

import android.content.Context
import android.os.Environment
import java.io.File

enum class Kind { IMAGE, VIDEO, OTHER, FOLDER }

data class Entry(val file: File, val size: Long, val modified: Long, val kind: Kind, val note: String = "")

enum class Sort(val label: String, val cmp: Comparator<Entry>) {
    NEWEST("Newest", compareByDescending { it.modified }),
    OLDEST("Oldest", compareBy { it.modified }),
    BIGGEST("Biggest", compareByDescending { it.size }),
    SMALLEST("Smallest", compareBy { it.size }),
}

private val IMAGES = setOf("jpg", "jpeg", "png", "gif", "webp", "heic", "bmp")
private val VIDEOS = setOf("mp4", "mkv", "mov", "webm", "3gp", "avi")

fun root(): File = Environment.getExternalStorageDirectory()

private fun kindOf(f: File) = when (f.extension.lowercase()) {
    in IMAGES -> Kind.IMAGE
    in VIDEOS -> Kind.VIDEO
    else -> Kind.OTHER
}

/** Every file on shared storage except Android/data and Android/obb (blocked by Android; Android/media is readable). */
fun scanFiles(): List<Entry> = root().walkTopDown()
    .onEnter { it.parentFile?.name != "Android" || it.name == "media" }
    .filter { it.isFile }
    .map { Entry(it, it.length(), it.lastModified(), kindOf(it)) }
    .toList()

/** Android/{data,obb,media}/<pkg> folders of uninstalled apps, plus empty folders. */
fun scanLeftovers(ctx: Context): List<Entry> {
    val installed = ctx.packageManager.getInstalledPackages(0).map { it.packageName }.toSet()
    val out = mutableListOf<Entry>()
    for (sub in listOf("data", "obb", "media")) {
        File(root(), "Android/$sub").listFiles()?.filter { it.isDirectory && it.name !in installed }?.forEach {
            out += Entry(it, it.walkTopDown().filter(File::isFile).sumOf(File::length), it.lastModified(), Kind.FOLDER, "Deleted app: Android/$sub/${it.name}")
        }
    }
    fun emptyDirs(d: File) {
        val kids = d.listFiles() ?: return
        // Only report the topmost empty dir; skip Android/ (handled above, and protected on newer Android).
        if (kids.all { it.isDirectory && it.walkTopDown().none(File::isFile) } && d != root()) {
            out += Entry(d, 0, d.lastModified(), Kind.FOLDER, "Empty folder")
        } else kids.filter { it.isDirectory && !(d == root() && it.name == "Android") }.forEach(::emptyDirs)
    }
    emptyDirs(root())
    return out
}
