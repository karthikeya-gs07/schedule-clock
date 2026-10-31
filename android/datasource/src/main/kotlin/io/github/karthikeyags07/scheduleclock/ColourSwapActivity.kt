package io.github.karthikeyags07.scheduleclock

import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.SweepGradient
import android.graphics.Typeface
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.view.animation.DecelerateInterpolator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The colour change, seen on the watch (RULES §9): when the phone app picks a colourway, this covers the watch
 * while the face is swapped. Swapping a pushed face makes Wear OS show its own plain clock for a moment between
 * the old face and the new one; here a ring sweeps round in the new colourway with its name in the middle, the
 * swap happens underneath, and once the new face has had time to load its meetings this fades out onto it.
 * Opened by the phone with `app://<package>/colour?name=mint`.
 */
class ColourSwapActivity : Activity(), CoroutineScope by MainScope() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val colourway = intent.getStringExtra(EXTRA_COLOUR)?.takeIf { it in FacePush.COLOURWAYS } ?: return finish()
        val view = SwapView(this, colourway).apply { alpha = 0f }
        setContentView(view)
        view.animate().alpha(1f).setDuration(200).start()
        view.sweep()
        launch {
            val started = SystemClock.uptimeMillis()
            try {
                if (FacePush.supported()) FacePush.install(applicationContext, colourway)
            } catch (e: Exception) {
                Log.w(TAG, "colour swap to $colourway failed", e)
            }
            // Let the ring finish, then give the new face time to load its meetings before showing it. Meanwhile,
            // whenever the swap brings the watch face to the front (with Wear OS's stand-in clock), onStop puts
            // this cover straight back on top.
            delay((SWEEP_MS - (SystemClock.uptimeMillis() - started)).coerceAtLeast(0) + SETTLE_MS)
            holding = false
            view.animate().alpha(0f).setDuration(350).withEndAction {
                finish()
                overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
            }.start()
        }
    }

    private var holding = true

    override fun onStop() {
        super.onStop()
        if (holding && !isFinishing) {   // pushed behind the watch face mid-swap: come back
            startActivity(Intent(this, ColourSwapActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_NEW_TASK))
            overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, 0, 0)
        }
    }

    override fun onNewIntent(intent: Intent) = Unit   // the reorder in onStop: just come back to the front

    override fun onDestroy() {
        cancel()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_COLOUR = "colour"
        private const val SWEEP_MS = 1100L
        private const val SETTLE_MS = 1600L
    }

    /** The new face's background, a ring track, the new colourway sweeping round it, and the colourway's name. */
    private class SwapView(context: Context, colourway: String) : View(context) {
        private val theme = Themes.of(colourway)
        private var progress = 0f
        private fun dp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics)
        private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = dp(6f); color = Color.argb(40, 255, 255, 255) }
        private val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = dp(6f); strokeCap = Paint.Cap.ROUND }
        private val name = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#f2f2f2"); textAlign = Paint.Align.CENTER; textSize = dp(22f)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
        private val caption = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(150, 255, 255, 255); textAlign = Paint.Align.CENTER; textSize = dp(13f)
        }
        private val box = RectF()

        fun sweep() = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = SWEEP_MS
            interpolator = DecelerateInterpolator(1.6f)
            addUpdateListener { progress = it.animatedValue as Float; invalidate() }
            start()
        }

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            val r = minOf(w, h) / 2f - dp(10f)
            box.set(w / 2f - r, h / 2f - r, w / 2f + r, h / 2f + r)
            // Solaris sweeps through the day's colours, a Palette theme through its own; the rest are one colour.
            val colours = if (theme.id == "solaris") SOLARIS.map(Color::parseColor).toIntArray() else theme.palette
            arc.shader = colours?.let {
                SweepGradient(w / 2f, h / 2f, it, null).apply {
                    setLocalMatrix(Matrix().apply { setRotate(-90f, w / 2f, h / 2f) })   // starting at 12
                }
            }
            arc.color = theme.accent
        }

        override fun onDraw(canvas: Canvas) {
            canvas.drawColor(theme.background)
            canvas.drawArc(box, 0f, 360f, false, track)
            canvas.drawArc(box, -90f, 360f * progress, false, arc)
            val cx = width / 2f
            canvas.drawText(theme.name, cx, height / 2f + dp(4f), name)
            canvas.drawText("Changing colours", cx, height / 2f + dp(26f), caption)
        }

        private companion object {
            val SOLARIS = listOf("#3f66e6", "#e88ab2", "#ffcc5c", "#fff06a", "#ff7438", "#a957dc", "#3f66e6")
        }
    }
}
