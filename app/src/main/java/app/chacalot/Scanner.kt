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

fun kindOf(f: File) = when (f.extension.lowercase()) {
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

fun entryOf(f: File) = Entry(f, if (f.isFile) f.length() else 0, f.lastModified(), if (f.isDirectory) Kind.FOLDER else kindOf(f))

/** Paths the user swiped up on: never shown in a deck until restored from the Kept tab. */
object Kept {
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("kept", Context.MODE_PRIVATE)
    fun all(ctx: Context): Set<String> = prefs(ctx).getStringSet("paths", emptySet())!!
    private fun save(ctx: Context, s: Set<String>) = prefs(ctx).edit().putStringSet("paths", s).apply()
    fun add(ctx: Context, path: String) = save(ctx, all(ctx) + path)
    fun remove(ctx: Context, path: String) = save(ctx, all(ctx) - path)
    fun clear(ctx: Context) = save(ctx, emptySet())
}

private val JUNK_EXT = setOf("tmp", "temp", "thumb", "cache")
private val JUNK_WORDS = listOf("thumb", "sticker", "cache", "temp", "trash", "animated gif", "backup")

/** Disposable files: WhatsApp thumbnails/stickers/hidden helper folders, .thumbnails folders and temp files. */
fun isJunk(f: File): Boolean {
    if (f.extension.lowercase() in JUNK_EXT) return true
    val dirs = f.parentFile?.path.orEmpty().lowercase().split('/')
    if (dirs.any { it == ".thumbnails" || it == ".trash" }) return true
    if (dirs.none { "whatsapp" in it || it == "com.whatsapp" || it == "com.whatsapp.w4b" }) return false
    val name = f.name.lowercase()
    if (name.startsWith("stk-") || "thumb" in name) return true
    // Hidden helper folders (.Thumbs, .Shared, ...) but never the user-visible .Statuses.
    return dirs.any { d -> (d.startsWith(".") && d != ".statuses") || JUNK_WORDS.any { it in d } }
}

/** Opt-in: delete junk automatically while scanning for swipe cards. */
object AutoClean {
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)
    fun enabled(ctx: Context) = prefs(ctx).getBoolean("autoclean", false)
    fun set(ctx: Context, on: Boolean) = prefs(ctx).edit().putBoolean("autoclean", on).apply()
}
