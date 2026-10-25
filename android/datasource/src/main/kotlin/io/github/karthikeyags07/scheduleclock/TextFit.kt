package io.github.karthikeyags07.scheduleclock

import android.graphics.Paint
import android.graphics.Typeface

/**
 * Fits text the way the face will draw it (system font, sizes from RULES T1) and reports its width,
 * so the face can place an icon right before centered text: WFF can't measure text, and one line
 * can't mix colors (spike 4).
 */
object TextFit {
    private val title = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.DEFAULT, 500, false)  // face: weight MEDIUM
        textSize = 30f
    }
    private val titleMin = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.DEFAULT, 500, false)
        textSize = 24f   // RULES T1 minimum; the scrolling title (C9a) is drawn at this size
    }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.DEFAULT
        textSize = 24f
    }

    fun title(text: String, maxWidth: Int) = fit(title, text, maxWidth)
    fun line(text: String, maxWidth: Int) = fit(line, text, maxWidth)

    /** Full width in px of a title at the 24 px minimum size. */
    fun titleMinWidth(text: String) = Math.ceil(titleMin.measureText(text).toDouble()).toInt()

    /** C9: shortens with an ellipsis instead of shrinking below the minimum size. Returns text and width in px. */
    private fun fit(paint: Paint, text: String, maxWidth: Int): Pair<String, Int> {
        if (paint.measureText(text) <= maxWidth) return text to paint.measureText(text).toInt()
        var s = text
        while (s.length > 1 && paint.measureText("$s…") > maxWidth) s = s.dropLast(1).trimEnd()
        return "$s…" to paint.measureText("$s…").toInt()
    }
}
