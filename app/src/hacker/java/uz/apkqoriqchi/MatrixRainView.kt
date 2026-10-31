package uz.apkqoriqchi

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.os.SystemClock
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import kotlin.random.Random

/**
 * Bosh menyu orqasidagi "matrix yomg'iri": yashil belgilar ustun-ustun bo'lib pastga oqadi.
 * Xira chiziladi (yozuvlar o'qilsin), soniyasiga ~30 kadr, ekran ko'rinmasa to'xtaydi,
 * tizimda animatsiyalar o'chirilgan bo'lsa — qimirlamaydi.
 */
class MatrixRainView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    private companion object {
        const val FRAME_MS = 33L
        const val GLYPHS = "01ｱｲｳｴｵｶｷｸｹｺｻｼｽｾｿﾀﾁﾂﾃﾄﾅﾆﾇﾈﾉﾊﾋﾌﾍﾎﾏﾐﾑﾒﾓﾔﾕﾖﾗﾘﾙﾚﾛﾜﾝ0123456789ABCDEF<>/#\$%&*+=:"
    }

    private class Drop(var row: Float, var speed: Float, var length: Int)

    private val textSizePx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 14f, resources.displayMetrics)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.MONOSPACE
        textSize = textSizePx
        textAlign = Paint.Align.CENTER
    }
    private val green = 0x00FF41
    private val head = 0xC8FFD4

    private var columnWidth = 0f
    private var rowHeight = 0f
    private var rows = 0
    private var drops: Array<Drop> = emptyArray()
    /** Har ustun uchun belgilar to'ri — vaqti-vaqti bilan bittasi almashib, "tirik" ko'rinadi. */
    private var grid: Array<CharArray> = emptyArray()
    private var last = 0L

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        columnWidth = textSizePx * 1.25f
        rowHeight = textSizePx * 1.2f
        val columns = (w / columnWidth).toInt().coerceAtLeast(1)
        rows = (h / rowHeight).toInt() + 2
        grid = Array(columns) { CharArray(rows) { glyph() } }
        drops = Array(columns) { newDrop(start = true) }
    }

    private fun glyph() = GLYPHS[Random.nextInt(GLYPHS.length)]

    private fun newDrop(start: Boolean) = Drop(
        row = if (start) Random.nextFloat() * rows else -Random.nextInt(4, 30).toFloat(),
        speed = Random.nextFloat() * 9f + 5f,
        length = Random.nextInt(8, 22),
    )

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility == VISIBLE) {
            last = 0L
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        if (drops.isEmpty()) return
        val moving = Motion.enabled()
        val now = SystemClock.uptimeMillis()
        val dt = if (last == 0L || !moving) 0f else ((now - last) / 1000f).coerceAtMost(0.1f)
        last = now

        for (c in drops.indices) {
            val drop = drops[c]
            drop.row += drop.speed * dt
            if (drop.row - drop.length > rows) drops[c] = newDrop(start = false)
            if (moving && Random.nextInt(12) == 0) grid[c][Random.nextInt(rows)] = glyph()

            val x = c * columnWidth + columnWidth / 2
            val headRow = drop.row.toInt()
            for (i in 0 until drop.length) {
                val r = headRow - i
                if (r < 0 || r >= rows) continue
                if (i == 0) {
                    paint.color = head
                    paint.alpha = 150
                } else {
                    paint.color = green
                    paint.alpha = (95 * (1f - i.toFloat() / drop.length)).toInt()
                }
                canvas.drawText(grid[c], r, 1, x, (r + 1) * rowHeight, paint)
            }
        }
        if (moving) postInvalidateDelayed(FRAME_MS)
    }
}
