package uz.apkqoriqchi

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.Typeface
import android.util.TypedValue
import android.view.View
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import kotlin.random.Random

/**
 * Ilova ochilganda ko'rinadigan kirish animatsiyasi: qalqon chiziladi, ichiga belgi tushadi,
 * so'ng nom paydo bo'ladi. Hammasi bitta 0..1 vaqt chizig'iga bog'langan — har bosqich o'z oralig'ida.
 */
class IntroView(context: Context, private val spec: Style) : View(context) {

    data class Style(
        val bg: Int,
        val accent: Int,
        /** Qalqon ichidagi belgi rangi. */
        val mark: Int,
        val text: Int,
        val text2: Int,
        val title: String,
        val subtitle: String,
        /** true — hacker uslubi: neon, skaner chizig'i, "dekodlanayotgan" sarlavha. */
        val terminal: Boolean,
    )

    private val dp = resources.displayMetrics.density

    // 24x24 koordinatalardagi qalqon (ic_shield_ok bilan bir xil shakl) va belgi
    private val shield = Path().apply {
        moveTo(12f, 1f)
        lineTo(3f, 5f)
        lineTo(3f, 11f)
        cubicTo(3f, 16.55f, 6.84f, 21.74f, 12f, 23f)
        cubicTo(17.16f, 21.74f, 21f, 16.55f, 21f, 11f)
        lineTo(21f, 5f)
        close()
    }
    private val check = Path().apply {
        moveTo(7.5f, 12.2f)
        lineTo(10.5f, 15.2f)
        lineTo(16.5f, 9.2f)
    }
    private val shieldMeasure = PathMeasure(shield, true)
    private val checkMeasure = PathMeasure(check, false)
    private val shieldPart = Path()
    private val checkPart = Path()

    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = spec.text
        textSize = sp(if (spec.terminal) 30f else 27f)
        if (spec.terminal) {
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            letterSpacing = 0.25f
            setShadowLayer(12 * dp, 0f, 0f, spec.accent)
        } else {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
    }
    private val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = spec.text2
        textSize = sp(if (spec.terminal) 13f else 15f)
        if (spec.terminal) typeface = Typeface.MONOSPACE
    }

    private val overshoot = OvershootInterpolator(1.8f)
    private var t = 0f
    private var onDone: (() -> Unit)? = null

    private val timeline = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = if (spec.terminal) 2300 else 1900
        interpolator = LinearInterpolator()
        addUpdateListener { t = it.animatedValue as Float; invalidate() }
        addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                onDone?.invoke()
                onDone = null
            }
        })
    }

    init {
        isClickable = true
        contentDescription = spec.title
        // Bosilsa — kutmasdan menyuga o'tamiz
        setOnClickListener { timeline.end() }
    }

    fun play(done: () -> Unit) {
        onDone = done
        timeline.start()
    }

    override fun onDetachedFromWindow() {
        onDone = null
        timeline.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(spec.bg)
        val size = minOf(width, height) * 0.30f
        val cx = width / 2f
        val cy = height * 0.40f
        if (spec.terminal) drawTerminal(canvas, cx, cy, size) else drawFamily(canvas, cx, cy, size)
    }

    private fun drawFamily(canvas: Canvas, cx: Float, cy: Float, size: Float) {
        // Qalqon atrofidan tarqaluvchi to'lqinlar
        line.color = spec.accent
        line.strokeWidth = 2 * dp
        for (i in 0..1) {
            val start = 0.40f + i * 0.15f
            val p = seg(start, start + 0.45f)
            if (p <= 0f || p >= 1f) continue
            line.alpha = ((1 - p) * 90).toInt()
            canvas.drawCircle(cx, cy, size * 0.5f + size * 1.1f * Motion.DECELERATE.getInterpolation(p), line)
        }

        val pop = seg(0.24f, 0.50f)
        canvas.save()
        canvas.translate(cx, cy)
        val k = size / 24f * (0.88f + 0.12f * overshoot.getInterpolation(pop))
        canvas.scale(k, k)
        canvas.translate(-12f, -12f)

        // 1) kontur chiziladi  2) ichi to'ladi  3) belgi chiziladi
        traceShield(canvas, ease(seg(0f, 0.32f)), 1f, 255)
        fill.color = spec.accent
        fill.alpha = (pop * 255).toInt()
        canvas.drawPath(shield, fill)
        traceCheck(canvas, ease(seg(0.45f, 0.66f)), spec.mark, 1.9f, 255)
        canvas.restore()

        drawTexts(canvas, cx, cy + size / 2f + 64 * dp, seg(0.52f, 0.80f), seg(0.62f, 0.90f), spec.subtitle)
    }

    private fun drawTerminal(canvas: Canvas, cx: Float, cy: Float, size: Float) {
        // CRT ekran chiziqlari
        line.color = spec.accent
        line.alpha = 14
        line.strokeWidth = 1f
        var y = 0f
        while (y < height) {
            canvas.drawLine(0f, y, width.toFloat(), y, line)
            y += 4 * dp
        }

        canvas.save()
        canvas.translate(cx, cy)
        val k = size / 24f
        canvas.scale(k, k)
        canvas.translate(-12f, -12f)

        // Neon kontur: bir nechta qalin-shaffof qatlam = yorug'lik
        val trace = seg(0f, 0.30f)
        traceShield(canvas, trace, 2.6f, 35)
        traceShield(canvas, trace, 1.4f, 80)
        traceShield(canvas, trace, 0.6f, 255)

        // Skaner chizig'i qalqonni yuqoridan pastga "to'ldiradi"
        val sweep = ease(seg(0.28f, 0.60f))
        if (sweep > 0f) {
            val scanY = 1f + 22f * sweep
            canvas.save()
            canvas.clipRect(0f, 0f, 24f, scanY)
            fill.color = spec.accent
            fill.alpha = 45
            canvas.drawPath(shield, fill)
            canvas.restore()
            if (sweep < 1f) {
                line.color = spec.accent
                for ((w, a) in listOf(1.6f to 50, 0.35f to 255)) {
                    line.strokeWidth = w
                    line.alpha = a
                    canvas.drawLine(-4f, scanY, 28f, scanY, line)
                }
            }
        }

        val mark = ease(seg(0.55f, 0.70f))
        traceCheck(canvas, mark, spec.accent, 3.2f, 50)
        traceCheck(canvas, mark, spec.mark, 1.5f, 255)
        canvas.restore()

        // Sarlavha tasodifiy belgilardan chapdan o'ngga "dekodlanadi"
        val decode = seg(0.50f, 0.82f)
        val title = spec.title
        val solved = (decode * title.length).toInt()
        val shown = String(CharArray(title.length) { i ->
            if (i < solved || title[i] == ' ') title[i] else GLYPHS[Random.nextInt(GLYPHS.length)]
        })
        val typed = seg(0.74f, 0.95f)
        val sub = spec.subtitle.take((typed * spec.subtitle.length).toInt()) +
            (if (typed > 0f && (t * 12).toInt() % 2 == 0) "█" else " ")
        // Monospace: qatorni to'liq uzunlikka to'ldirsak, yozilayotganda markazdan siljimaydi
        val subLine = sub.padEnd(spec.subtitle.length + 1)
        drawTexts(canvas, cx, cy + size / 2f + 64 * dp, seg(0.48f, 0.56f), if (typed > 0f) 1f else 0f, subLine, shown)
    }

    private fun drawTexts(canvas: Canvas, cx: Float, y: Float, tp: Float, sp: Float, sub: String, title: String = spec.title) {
        val a = ease(tp)
        titlePaint.alpha = (a * 255).toInt()
        canvas.drawText(title, cx, y + (1 - a) * 18 * dp, titlePaint)
        val b = ease(sp)
        subPaint.alpha = (b * 255).toInt()
        canvas.drawText(sub, cx, y + 32 * dp + (1 - b) * 14 * dp, subPaint)
    }

    private fun traceShield(canvas: Canvas, p: Float, width: Float, alpha: Int) {
        if (p <= 0f) return
        shieldPart.reset()
        shieldMeasure.getSegment(0f, shieldMeasure.length * p, shieldPart, true)
        line.color = spec.accent
        line.strokeWidth = width
        line.alpha = alpha
        canvas.drawPath(shieldPart, line)
    }

    private fun traceCheck(canvas: Canvas, p: Float, color: Int, width: Float, alpha: Int) {
        if (p <= 0f) return
        checkPart.reset()
        checkMeasure.getSegment(0f, checkMeasure.length * p, checkPart, true)
        line.color = color
        line.strokeWidth = width
        line.alpha = alpha
        canvas.drawPath(checkPart, line)
    }

    /** Umumiy vaqt chizig'ining [a, b] oralig'idagi ulushi (0..1). */
    private fun seg(a: Float, b: Float) = ((t - a) / (b - a)).coerceIn(0f, 1f)

    private fun ease(p: Float) = Motion.EMPHASIZED.getInterpolation(p)

    private fun sp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, resources.displayMetrics)

    private companion object {
        const val GLYPHS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789#$%&@<>/"
    }
}
