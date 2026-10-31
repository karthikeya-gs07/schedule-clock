package io.github.karthikeyags07.scheduleclock

import android.graphics.Color

/**
 * Every colourway's colours that this app draws itself (RULES §9): the ring image's fixed-colour parts
 * (double bookings, private meetings), the Palette themes' sweep, and the colour-change screen.
 * The face draws the rest. Keep in step with tools/gen_spike_face.py, the phone app's Ui.kt and
 * prototype/index.html. The Figma themes come from Frame 6 (node 38:3237).
 */
data class Theme(
    val id: String,
    val name: String,
    val background: Int,
    val accent: Int,
    val clash: Int,
    val private: Int,
    /** Palette themes: the colours the meeting arcs run through over the 12 hours ahead (panel 3). */
    val palette: IntArray? = null,
)

object Themes {
    private fun c(hex: String) = Color.parseColor("#$hex")
    private val GREY = c("8e8e93")
    private val CORAL = c("ff7a59")

    private fun black(id: String, accent: String) = Theme(id, id.replaceFirstChar(Char::uppercase), Color.BLACK, c(accent), CORAL, GREY)
    private fun solid(id: String, name: String, bg: String, accent: String, clash: String, minor: String) =
        Theme(id, name, c(bg), c(accent), c(clash), c(minor))
    private fun palette(id: String, name: String, bg: String, clash: String, minor: String, vararg colours: String) =
        Theme(id, name, c(bg), c(colours[0]), c(clash), c(minor), colours.map(::c).toIntArray())

    val ALL = listOf(
        black("amber", "ffb547"), black("mint", "5fd4a0"), black("violet", "b48cff"), black("rose", "ff7a9c"),
        Theme("solaris", "Solaris", Color.BLACK, c("ff9447"), CORAL, GREY),
        palette("quartz", "Rose Quartz", "25151e", "f99bb2", "957483", "ffd5ae", "ffacaa", "f58fb8", "cc93dd", "a6b4f7"),
        palette("copper", "Copper Garden", "061617", "d8936b", "708d8b", "e8cba0", "e6aa72", "cf8467", "6ab9af", "7ba6bf"),
        palette("ocean", "Ocean", "061524", "3fb6e0", "647f97", "8ceadb", "45ccd8", "3ba7e5", "688bff", "b4b7ff"),
        palette("aurora", "Aurora", "071a21", "43cfcb", "688b90", "9dfad0", "40d7ad", "45c9df", "6d9bff", "b59aff"),
        palette("acid", "Acid Circuit", "12180b", "91da78", "768562", "e7ff8a", "bddf4b", "73d696", "4ccdc3", "9aacee"),
        solid("lunar", "Lunar Mono", "14171c", "b2bfce", "f4faff", "8994a2"),
        solid("desert", "Desert Dusk", "1f1716", "e79775", "f6d69a", "ae8d7c"),
        solid("lime", "Racing Lime", "202729", "d0f85a", "65d8ff", "859c94"),
        solid("plum", "Plum Rose", "1e121f", "eea0bd", "b6a2ff", "ae86a5"),
        solid("forest", "Forest Signal", "0c1e1a", "79e4b7", "ffbe55", "759d8b"),
        solid("arctic", "Arctic Lagoon", "0b1622", "66d6e8", "a3a5ff", "7094ae"),
    )
    val IDS = ALL.map { it.id }

    fun of(id: String?) = ALL.firstOrNull { it.id == id } ?: ALL[0]
}
