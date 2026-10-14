package app.chacalot

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.abs


/** Toggle chip: a small DS panel; the active one is tinted and blue. */
class Chip(context: Context, label: String, active: Boolean) : TextView(context) {
    init {
        text = label
        typeface = Ds.font
        textSize = 13f
        includeFontPadding = false
        setTextColor(if (active) Ds.CURSOR else Ds.TEXT)
        setPadding(context.dp(10), context.dp(8), context.dp(10), context.dp(8))
        background = PanelDrawable(context.dp(1).toFloat(), if (active) Ds.PRESSED else android.graphics.Color.WHITE)
    }
}

/** The swipeable card: thumbnail, name, path, size/date pill, blue corner brackets like a selected clip. */
class FileCard(context: Context, private val onSwipe: (delete: Boolean) -> Unit) : FrameLayout(context) {
    val thumb = ImageView(context).apply {
        scaleType = ImageView.ScaleType.FIT_CENTER
        setBackgroundColor(Ds.BG)
    }
    private val name = Ds.label(context, 15f).apply { maxLines = 2; ellipsize = TextUtils.TruncateAt.END }
    private val path = Ds.label(context, 12f, Ds.SUBTLE).apply { maxLines = 2; ellipsize = TextUtils.TruncateAt.MIDDLE }
    private val info = Ds.label(context, 12f).apply {
        setPadding(context.dp(6), context.dp(4), context.dp(6), context.dp(4))
        background = PanelDrawable(context.dp(1).toFloat(), Ds.BG)
    }
    private val note = Ds.label(context, 12f, Ds.CURSOR)
    private val bracket = Paint().apply {
        color = Ds.CURSOR
        style = Paint.Style.STROKE
        strokeWidth = context.dp(3).toFloat()
    }
    private var downX = 0f

    init {
        setWillNotDraw(false)
        val gap = context.dp(10)
        setPadding(gap, context.dp(6), gap, context.dp(6))
        val panel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(context.dp(12), context.dp(12), context.dp(12), context.dp(12))
            background = PanelDrawable(context.dp(2).toFloat())
        }
        panel.addView(thumb, LinearLayout.LayoutParams(-1, 0, 1f))
        val col = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        col.addView(name)
        col.addView(path, LinearLayout.LayoutParams(-2, -2).apply { topMargin = context.dp(2) })
        col.addView(info, LinearLayout.LayoutParams(-2, -2).apply { topMargin = context.dp(8) })
        col.addView(note, LinearLayout.LayoutParams(-2, -2).apply { topMargin = context.dp(6) })
        panel.addView(col, LinearLayout.LayoutParams(-1, -2).apply { topMargin = context.dp(10) })
        addView(panel, LayoutParams(-1, -1))
    }

    fun bind(name: String, path: String, info: String, note: String) {
        this.name.text = name
        this.path.text = path
        this.info.text = info
        this.note.text = note
        this.note.visibility = if (note.isEmpty()) GONE else VISIBLE
    }

    override fun onInterceptTouchEvent(e: MotionEvent) = false

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> downX = e.rawX - translationX
            MotionEvent.ACTION_MOVE -> {
                translationX = e.rawX - downX
                rotation = translationX / 40f
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val limit = width / 3f
                if (abs(translationX) > limit) {
                    val left = translationX < 0
                    animate().translationX(if (left) -width * 1.5f else width * 1.5f).setDuration(150)
                        .withEndAction { onSwipe(left) }.start()
                } else animate().translationX(0f).rotation(0f).setDuration(120).start()
            }
        }
        return true
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        val inset = context.dp(3) + bracket.strokeWidth / 2
        val l = inset
        val t = inset - context.dp(1)
        val r = width - inset
        val b = height - inset + context.dp(1)
        val arm = context.dp(14).toFloat()
        canvas.drawPath(Path().apply {
            moveTo(l, t + arm); lineTo(l, t); lineTo(l + arm, t)
            moveTo(r - arm, t); lineTo(r, t); lineTo(r, t + arm)
            moveTo(r, b - arm); lineTo(r, b); lineTo(r - arm, b)
            moveTo(l + arm, b); lineTo(l, b); lineTo(l, b - arm)
        }, bracket)
    }
}

/** Bottom-screen style button: a lettered circle (A / B / X / Y) followed by a label. */
class DsButton(context: Context, private val letter: String, label: String) : TextView(context) {
    private val ring = Paint().apply {
        color = Ds.BORDER
        style = Paint.Style.STROKE
        strokeWidth = context.dp(2).toFloat()
        isAntiAlias = true
    }
    private val glyph = Paint().apply {
        color = Ds.BORDER
        textAlign = Paint.Align.CENTER
        typeface = Ds.font
        textSize = context.dp(13).toFloat()
        isAntiAlias = true
    }
    private val radius = context.dp(11).toFloat()

    init {
        text = label
        typeface = Ds.font
        textSize = 16f
        setTextColor(Ds.TEXT)
        gravity = Gravity.CENTER_VERTICAL or Gravity.CENTER_HORIZONTAL
        isClickable = true
        includeFontPadding = false
        minimumHeight = context.dp(48)
        background = PanelDrawable(context.dp(2).toFloat())
        setPadding(context.dp(12) + (radius * 2).toInt() + context.dp(8), 0, context.dp(12), 0)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = paddingStart - context.dp(8) - radius
        val cy = height / 2f
        canvas.drawCircle(cx, cy, radius, ring)
        val baseline = cy - (glyph.descent() + glyph.ascent()) / 2
        canvas.drawText(letter, cx, baseline, glyph)
    }
}
