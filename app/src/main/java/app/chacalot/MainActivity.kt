package app.chacalot

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.format.DateFormat
import android.text.format.Formatter
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.BaseAdapter
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class Mode(val label: String) { FILES("Files"), LEFTOVERS("Leftovers"), KEPT("Kept") }
private enum class Filter(val label: String, val ok: (Entry) -> Boolean) {
    ALL("All", { true }), PICS("Pics", { it.kind == Kind.IMAGE }),
    VIDEOS("Videos", { it.kind == Kind.VIDEO }), OTHER("Other", { it.kind == Kind.OTHER }),
}

class MainActivity : Activity() {
    private lateinit var clock: TextView
    private lateinit var prompt: TextView
    private lateinit var count: TextView
    private lateinit var chips: LinearLayout
    private lateinit var stage: FrameLayout
    private lateinit var message: TextView
    private lateinit var topBar: LinearLayout
    private lateinit var bottomBar: LinearLayout
    private lateinit var deleteBtn: DsButton

    private val handler = Handler(Looper.getMainLooper())
    private var mode = Mode.FILES
    private var filter = Filter.ALL
    private var sort = Sort.BIGGEST
    private var all: List<Entry>? = null
    private val seen = HashSet<Entry>()
    private val marked = ArrayList<Entry>()
    private val history = ArrayList<Entry>()
    private var scanId = 0

    private val tick = object : Runnable {
        override fun run() {
            clock.text = SimpleDateFormat("HH:mm  MM/dd", Locale.getDefault()).format(Date())
            handler.postDelayed(this, 10_000)
        }
    }
    private val resetPrompt = Runnable { prompt.text = PROMPT }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Ds.init(this)
        setContentView(buildUi())
        rebuildChips()
    }

    override fun onResume() {
        super.onResume()
        handler.post(tick)
        if (all == null && Environment.isExternalStorageManager()) scan() else render()
    }

    override fun onPause() {
        handler.removeCallbacks(tick)
        super.onPause()
    }

    // ---- UI (same building blocks as Pictoclip: Ds, PanelDrawable, DsButton) ---------------------

    private fun buildUi(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = Ds.gridBackground(this@MainActivity)
        }
        topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(Ds.TOP_LIGHT, Ds.TOP_DARK))
            minimumHeight = dp(32)
            setPadding(dp(10), 0, dp(10), 0)
        }
        topBar.addView(
            Ds.label(this, 14f, Color.WHITE).apply { text = "CHACALOT" },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
        )
        clock = Ds.label(this, 14f, Color.WHITE)
        topBar.addView(clock)
        root.addView(topBar, LinearLayout.LayoutParams(-1, -2))

        val promptPanel = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = PanelDrawable(dp(2).toFloat())
            setPadding(dp(14), dp(10), dp(14), dp(10))
        }
        prompt = Ds.label(this, 15f).apply { text = PROMPT }
        promptPanel.addView(prompt, LinearLayout.LayoutParams(0, -2, 1f))
        count = Ds.label(this, 13f, Ds.SUBTLE)
        promptPanel.addView(count)
        root.addView(promptPanel, LinearLayout.LayoutParams(-1, -2).apply { setMargins(dp(10), dp(10), dp(10), dp(4)) })

        chips = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val chipScroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(chips) }
        root.addView(chipScroll, LinearLayout.LayoutParams(-1, -2).apply { setMargins(dp(10), dp(4), dp(10), dp(4)) })

        stage = FrameLayout(this)
        message = Ds.label(this, 15f, Ds.SUBTLE).apply {
            gravity = Gravity.CENTER
            setLineSpacing(0f, 1.2f)
            background = PanelDrawable(dp(2).toFloat(), Ds.BG)
            setPadding(dp(16), dp(24), dp(16), dp(24))
        }
        root.addView(stage, LinearLayout.LayoutParams(-1, 0, 1f))

        bottomBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Ds.BAR)
            setPadding(dp(10), dp(8), dp(10), dp(8))
        }
        fun button(letter: String, label: String, onClick: () -> Unit): DsButton {
            val lp = LinearLayout.LayoutParams(0, -2, 1f).apply { if (bottomBar.childCount > 0) marginStart = dp(10) }
            return DsButton(this, letter, label).apply { setOnClickListener { onClick() } }.also { bottomBar.addView(it, lp) }
        }
        deleteBtn = button("B", "Delete") { if (mode == Mode.KEPT) confirmReset() else confirmDelete() }
        button("A", "Undo", ::undo)
        button("X", "Update") { checkForUpdate(this, ::flash) }
        root.addView(bottomBar, LinearLayout.LayoutParams(-1, -2))

        root.setOnApplyWindowInsetsListener { _, insets ->
            val l: Int; val t: Int; val r: Int; val b: Int
            if (Build.VERSION.SDK_INT >= 30) {
                val bars = insets.getInsets(WindowInsets.Type.systemBars())
                l = bars.left; t = bars.top; r = bars.right; b = bars.bottom
            } else {
                @Suppress("DEPRECATION")
                run {
                    l = insets.systemWindowInsetLeft; t = insets.systemWindowInsetTop
                    r = insets.systemWindowInsetRight; b = insets.systemWindowInsetBottom
                }
            }
            topBar.minimumHeight = dp(32) + t
            topBar.setPadding(dp(10) + l, t, dp(10) + r, 0)
            bottomBar.setPadding(dp(10) + l, dp(8), dp(10) + r, dp(8) + b)
            insets
        }
        return root
    }

    private fun rebuildChips() {
        chips.removeAllViews()
        deleteBtn.text = if (mode == Mode.KEPT) "Reset" else "Delete"
        fun chip(label: String, active: Boolean, onClick: () -> Unit) =
            chips.addView(Chip(this, label, active).apply { setOnClickListener { onClick() } },
                LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(6) })
        Mode.entries.forEach { m -> chip(m.label, mode == m) { mode = m; rebuildChips(); scan() } }
        if (mode == Mode.FILES) Filter.entries.forEach { f -> chip(f.label, filter == f) { filter = f; rebuildChips(); render() } }
        Sort.entries.forEach { s -> chip(s.label, sort == s) { sort = s; rebuildChips(); render() } }
    }

    // ---- actions -------------------------------------------------------------------------------

    private fun flash(msg: String) {
        handler.removeCallbacks(resetPrompt)
        prompt.text = msg
        handler.postDelayed(resetPrompt, 1500)
    }

    private fun scan() {
        val id = ++scanId
        all = null; seen.clear(); marked.clear(); history.clear()
        showMessage("Scanning...")
        Thread {
            val kept = Kept.all(this)
            val result = when (mode) {
                Mode.FILES -> scanFiles().filter { it.file.path !in kept }
                Mode.LEFTOVERS -> scanLeftovers(this).filter { it.file.path !in kept }
                Mode.KEPT -> kept.map(::File).filter { it.exists() }.map(::entryOf)
            }
            runOnUiThread { if (id == scanId) { all = result; render() } }
        }.start()
    }

    private fun showMessage(text: String, onClick: (() -> Unit)? = null) {
        stage.removeAllViews()
        message.text = text
        message.setOnClickListener(onClick?.let { c -> View.OnClickListener { c() } })
        (message.parent as? ViewGroup)?.removeView(message)
        stage.addView(message, FrameLayout.LayoutParams(-1, -2, Gravity.TOP).apply { setMargins(dp(10), dp(10), dp(10), 0) })
    }

    private fun render() {
        count.text = if (marked.isEmpty()) "" else "${marked.size} / ${Formatter.formatFileSize(this, marked.sumOf { it.size })}"
        if (!Environment.isExternalStorageManager()) {
            showMessage("Chacalot needs all-files access.\nTap here to allow.") {
                startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:$packageName")))
            }
            return
        }
        val list = all ?: return
        if (mode == Mode.KEPT) return renderKept(list)
        val item = list.filter { mode == Mode.LEFTOVERS || filter.ok(it) }.sortedWith(sort.cmp).firstOrNull { it !in seen }
        if (item == null) {
            showMessage(if (seen.isEmpty()) "Nothing found." else "Deck finished!\nPress B to delete the marked ones.")
            return
        }
        stage.removeAllViews()
        val card = FileCard(this) { swipe ->
            seen += item; history += item
            if (swipe == Swipe.DELETE) marked += item
            if (swipe == Swipe.KEEP) { Kept.add(this, item.file.path); flash("Kept forever!") }
            render()
        }
        card.bind(item.file.name, item.file.parent.orEmpty(),
            "${Formatter.formatFileSize(this, item.size)}  ${DateFormat.format("yyyy-MM-dd", item.modified)}", item.note)
        stage.addView(card, FrameLayout.LayoutParams(-1, -1).apply { setMargins(0, dp(6), 0, dp(6)) })
        if (item.kind == Kind.FOLDER || item.kind == Kind.OTHER) card.thumb.setImageDrawable(PageIconDrawable(dp(10).toFloat()))
        else Thread {
            val bmp = thumbnail(item)
            runOnUiThread { if (card.isAttachedToWindow) card.thumb.setImageBitmap(bmp) }
        }.start()
    }

    /** Kept tab: tap a row to make that file swipeable again. */
    private fun renderKept(list: List<Entry>) {
        count.text = "${list.size}"
        if (list.isEmpty()) return showMessage("Nothing kept yet.\nSwipe a card up to keep it forever.")
        val rows = list.sortedWith(sort.cmp)
        stage.removeAllViews()
        stage.addView(ListView(this).apply {
            divider = null; dividerHeight = 0; setSelector(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
            setBackgroundColor(Color.TRANSPARENT)
            adapter = object : BaseAdapter() {
                override fun getCount() = rows.size
                override fun getItem(i: Int) = rows[i]
                override fun getItemId(i: Int) = i.toLong()
                override fun getView(i: Int, v: View?, parent: ViewGroup): View {
                    val e = rows[i]
                    val tv = Ds.label(this@MainActivity, 14f).apply {
                        text = "${e.file.name}\n${e.file.parent.orEmpty()}\n${Formatter.formatFileSize(this@MainActivity, e.size)}"
                        background = PanelDrawable(dp(2).toFloat())
                        setPadding(dp(12), dp(10), dp(12), dp(10))
                    }
                    return FrameLayout(this@MainActivity).apply {
                        setPadding(dp(10), dp(4), dp(10), dp(4)); addView(tv)
                    }
                }
            }
            setOnItemClickListener { _, _, i, _ ->
                Kept.remove(this@MainActivity, rows[i].file.path)
                all = all?.filter { it != rows[i] }
                flash("Swipeable again!")
                render()
            }
        })
    }

    private fun confirmReset() {
        if (all.isNullOrEmpty()) return
        AlertDialog.Builder(this)
            .setMessage("Make all kept files swipeable again?")
            .setPositiveButton("Yes") { _, _ -> Kept.clear(this); all = emptyList(); render(); flash("All restored!") }
            .setNegativeButton("No", null)
            .show()
    }

    private fun thumbnail(e: Entry): Bitmap? = try {
        if (e.kind == Kind.VIDEO) {
            MediaMetadataRetriever().run { try { setDataSource(e.file.path); getFrameAtTime(0) } finally { release() } }
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(e.file.path, bounds)
            val opts = BitmapFactory.Options().apply { inSampleSize = maxOf(1, maxOf(bounds.outWidth, bounds.outHeight) / 1024) }
            BitmapFactory.decodeFile(e.file.path, opts)
        }
    } catch (ex: Exception) { null }

    private fun undo() {
        val last = history.removeLastOrNull() ?: return flash("Nothing to undo!")
        seen -= last; marked -= last; Kept.remove(this, last.file.path)
        render()
    }

    private fun confirmDelete() {
        if (marked.isEmpty()) return flash("Swipe left first!")
        AlertDialog.Builder(this)
            .setMessage("Permanently delete ${marked.size} items?")
            .setPositiveButton("Yes") { _, _ ->
                val doomed = marked.toList()
                Thread {
                    val gone = doomed.filter { it.file.deleteRecursively() }.toSet()
                    runOnUiThread {
                        all = all?.filter { it !in gone }
                        marked.removeAll(gone); seen.removeAll(gone); history.removeAll(gone)
                        render()
                        flash("Deleted!")
                    }
                }.start()
            }
            .setNegativeButton("No", null)
            .show()
    }

    private companion object {
        const val PROMPT = "Left: delete. Right: skip. Up: keep forever."
    }
}
