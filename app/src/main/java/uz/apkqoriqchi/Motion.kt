package uz.apkqoriqchi

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.view.animation.PathInterpolator

/** Menyu animatsiyalari: kirish, elementlarning ketma-ket chiqishi, bosilish effekti. */
object Motion {
    /** Material "emphasized decelerate" — tez boshlanib, yumshoq to'xtaydi. */
    val EMPHASIZED = PathInterpolator(0.05f, 0.7f, 0.1f, 1f)
    val DECELERATE = PathInterpolator(0f, 0f, 0f, 1f)
    private val EXIT = PathInterpolator(0.3f, 0f, 0.8f, 0.15f)

    /** Tizim sozlamalarida animatsiyalar o'chirilgan bo'lsa — hech narsa o'ynatmaymiz. */
    fun enabled() = ValueAnimator.areAnimatorsEnabled()

    /** Kirish animatsiyasini menyu ustida o'ynatadi; tugashi bilan [then] chaqiriladi va u yo'qoladi. */
    fun intro(activity: Activity, style: IntroView.Style, then: () -> Unit) {
        if (!enabled()) {
            then()
            return
        }
        val intro = IntroView(activity, style)
        activity.addContentView(intro, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        intro.play {
            then()
            intro.animate()
                .alpha(0f).scaleX(1.08f).scaleY(1.08f)
                .setDuration(380)
                .setInterpolator(EXIT)
                .withEndAction { (intro.parent as? ViewGroup)?.removeView(intro) }
                .start()
        }
    }

    /** ScrollView > LinearLayout ichidagi menyu elementlari. */
    fun items(root: View): List<View> {
        val list = (root as ViewGroup).getChildAt(0) as ViewGroup
        return (0 until list.childCount).map(list::getChildAt)
    }

    /** Elementlarni chiqishdan oldingi holatga qo'yadi (ko'rinmas va biroz siljigan). */
    fun hide(views: List<View>, dxDp: Float = 0f, dyDp: Float = 0f) {
        for (v in views) {
            val d = v.resources.displayMetrics.density
            v.alpha = 0f
            v.translationX = dxDp * d
            v.translationY = dyDp * d
        }
    }

    /** Elementlar birin-ketin o'z joyiga suzib keladi. */
    fun stagger(views: List<View>, step: Long = 60) {
        views.forEachIndexed { i, v ->
            v.animate()
                .alpha(1f).translationX(0f).translationY(0f)
                .setStartDelay(i * step)
                .setDuration(520)
                .setInterpolator(EMPHASIZED)
                .start()
        }
    }

    /** Bosilganda tugma ozgina "botadi", qo'yib yuborilganda prujinadek qaytadi. */
    @SuppressLint("ClickableViewAccessibility")
    fun pressable(vararg views: View, scale: Float = 0.96f) {
        val spring = OvershootInterpolator(3f)
        for (v in views) {
            v.setOnTouchListener { view, e ->
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN ->
                        view.animate().scaleX(scale).scaleY(scale).setStartDelay(0).setDuration(110).setInterpolator(DECELERATE).start()
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                        view.animate().scaleX(1f).scaleY(1f).setStartDelay(0).setDuration(320).setInterpolator(spring).start()
                }
                false
            }
        }
    }

    /** Holat o'zgarganini ko'rsatish uchun qisqa "yonib-o'chish". */
    fun flash(view: View) {
        if (!enabled()) return
        view.alpha = 0.25f
        view.animate().alpha(1f).setStartDelay(0).setDuration(260).setInterpolator(DECELERATE).start()
    }

    /** Sekin "nafas olish" — himoya tirik ekanini bildiradi. Ekran yopilganda pause() qiling. */
    fun breathe(view: View): ObjectAnimator =
        ObjectAnimator.ofPropertyValuesHolder(
            view,
            PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.06f),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.06f),
        ).apply {
            duration = 1500
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
        }
}
