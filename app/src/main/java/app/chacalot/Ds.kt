package app.chacalot

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.Shader
import android.util.TypedValue
import android.widget.TextView

/** Nintendo DS system-menu / PictoChat flavoured look: grid paper, notched panels, corner brackets. */
object Ds {
    val BG = Color.parseColor("#F4F5F6")
    val GRID = Color.parseColor("#E1E4E7")
    val BORDER = Color.parseColor("#4A4F55")
    val SHADOW = Color.parseColor("#C3C8CD")
    val TEXT = Color.parseColor("#3A3F45")
    val SUBTLE = Color.parseColor("#8A9096")
    val PRESSED = Color.parseColor("#D5E2EE")
    val CURSOR = Color.parseColor("#3E6DB0")
    val TOP_LIGHT = Color.parseColor("#6F8C99")
    val TOP_DARK = Color.parseColor("#4A6573")
    val BAR = Color.parseColor("#E3E6E9")

    lateinit var font: Typeface

    fun init(context: Context) {
        font = context.resources.getFont(R.font.dotgothic16)
    }

    fun gridBackground(context: Context): Drawable {
        val cell = context.dp(16)
        val bmp = Bitmap.createBitmap(cell, cell, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(BG)
        val line = maxOf(1, context.dp(1) / 2)
        for (i in 0 until cell) for (w in 0 until line) {
            bmp.setPixel(i, w, GRID)
            bmp.setPixel(w, i, GRID)
        }
        return BitmapDrawable(context.resources, bmp).apply {
            setTileModeXY(Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        }
    }

    fun label(context: Context, sizeSp: Float, color: Int = TEXT): TextView = TextView(context).apply {
        typeface = font
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        setTextColor(color)
        includeFontPadding = false
    }
}

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()

/** White panel with a dark, corner-notched border and a soft inner shadow, like the DS buttons. */
class PanelDrawable(
    private val px: Float,
    private val fill: Int = Color.WHITE,
    private val pressedFill: Int = Ds.PRESSED,
) : Drawable() {
    private val paint = Paint().apply { isAntiAlias = false }
    private var pressed = false

    override fun isStateful() = true

    override fun onStateChange(state: IntArray): Boolean {
        val p = state.contains(android.R.attr.state_pressed)
        if (p == pressed) return false
        pressed = p
        invalidateSelf()
        return true
    }

    override fun draw(canvas: Canvas) {
        val b = bounds
        val outer = RectF(b)
        paint.color = Ds.BORDER
        canvas.drawPath(notched(outer, px), paint)
        val inner = RectF(outer).apply { inset(px, px) }
        paint.color = if (pressed) pressedFill else fill
        canvas.drawRect(inner, paint)
        if (!pressed) {
            paint.color = Ds.SHADOW
            canvas.drawRect(inner.left, inner.bottom - px, inner.right, inner.bottom, paint)
            canvas.drawRect(inner.right - px, inner.top, inner.right, inner.bottom, paint)
            paint.color = fill
            canvas.drawRect(inner.left, inner.top, inner.right - px, inner.bottom - px, paint)
        }
    }

    private fun notched(r: RectF, c: Float) = Path().apply {
        moveTo(r.left + c, r.top)
        lineTo(r.right - c, r.top)
        lineTo(r.right, r.top + c)
        lineTo(r.right, r.bottom - c)
        lineTo(r.right - c, r.bottom)
        lineTo(r.left + c, r.bottom)
        lineTo(r.left, r.bottom - c)
        lineTo(r.left, r.top + c)
        close()
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(cf: ColorFilter?) {}
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}

/** Tiny pixel-art "page" icon drawn on a coarse grid. */
class PageIconDrawable(private val unit: Float) : Drawable() {
    private val paint = Paint().apply { isAntiAlias = false }

    override fun getIntrinsicWidth() = (ART[0].length * unit).toInt()
    override fun getIntrinsicHeight() = (ART.size * unit).toInt()

    override fun draw(canvas: Canvas) {
        val b = bounds
        ART.forEachIndexed { y, row ->
            row.forEachIndexed { x, ch ->
                paint.color = when (ch) {
                    '#' -> Ds.BORDER
                    '.' -> Color.WHITE
                    '=' -> Ds.SUBTLE
                    else -> return@forEachIndexed
                }
                val l = b.left + x * unit
                val t = b.top + y * unit
                canvas.drawRect(l, t, l + unit, t + unit, paint)
            }
        }
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(cf: ColorFilter?) {}
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT

    private companion object {
        val ART = arrayOf(
            "####### ",
            "#.....##",
            "#.....#.#",
            "#.....###",
            "#.......#",
            "#.=====.#",
            "#.......#",
            "#.=====.#",
            "#.......#",
            "#.===...#",
            "#########",
        ).map { it.padEnd(9) }.toTypedArray()
    }
}
