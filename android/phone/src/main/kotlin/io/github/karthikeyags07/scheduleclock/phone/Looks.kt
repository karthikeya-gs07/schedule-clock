package io.github.karthikeyags07.scheduleclock.phone

/**
 * The colourways the Watchface tab offers (RULES §9), in carousel order: the first five on a black dial, then
 * the Figma themes (Frame 6, node 38:3237), which tint the whole dial. Palette themes' arcs run through five
 * colours over the 12 hours ahead. Keep in step with the watch app's Themes.kt, tools/gen_spike_face.py and
 * prototype/index.html (whose THEMES are keyed by [name]).
 */
data class Look(val id: String, val name: String, val accent: Long, val line: String, val palette: List<Long>? = null)

object Looks {
    val ALL = listOf(
        Look("solaris", "Solaris", 0xFFFF9447, "Colours that follow the sun through your day."),
        Look("amber", "Amber", 0xFFFFB547, "Warm amber arcs, the original Calen-Time."),
        Look("mint", "Mint", 0xFF5FD4A0, "Cool mint arcs with a soft blue lane."),
        Look("violet", "Violet", 0xFFB48CFF, "Violet arcs with a cyan lane."),
        Look("rose", "Rose", 0xFFFF7A9C, "Rose arcs with a peach lane."),
        Look("quartz", "Rose Quartz", 0xFFFFD5AE, "Peach to lilac arcs on a deep rose dial.",
            listOf(0xFFFFD5AE, 0xFFFFACAA, 0xFFF58FB8, 0xFFCC93DD, 0xFFA6B4F7)),
        Look("copper", "Copper Garden", 0xFFE8CBA0, "Copper to teal arcs on a petrol dial.",
            listOf(0xFFE8CBA0, 0xFFE6AA72, 0xFFCF8467, 0xFF6AB9AF, 0xFF7BA6BF)),
        Look("ocean", "Ocean", 0xFF8CEADB, "Sea green to periwinkle arcs on a deep navy dial.",
            listOf(0xFF8CEADB, 0xFF45CCD8, 0xFF3BA7E5, 0xFF688BFF, 0xFFB4B7FF)),
        Look("aurora", "Aurora", 0xFF9DFAD0, "Northern-lights greens and blues on a dark teal dial.",
            listOf(0xFF9DFAD0, 0xFF40D7AD, 0xFF45C9DF, 0xFF6D9BFF, 0xFFB59AFF)),
        Look("acid", "Acid Circuit", 0xFFE7FF8A, "Lime to blue arcs on a dark olive dial.",
            listOf(0xFFE7FF8A, 0xFFBDDF4B, 0xFF73D696, 0xFF4CCDC3, 0xFF9AACEE)),
        Look("lunar", "Lunar Mono", 0xFFB2BFCE, "Cool silver arcs on a slate dial."),
        Look("desert", "Desert Dusk", 0xFFE79775, "Terracotta arcs on a warm brown dial."),
        Look("lime", "Racing Lime", 0xFFD0F85A, "Bright lime arcs on a charcoal dial."),
        Look("plum", "Plum Rose", 0xFFEEA0BD, "Pink arcs on a deep plum dial."),
        Look("forest", "Forest Signal", 0xFF79E4B7, "Mint green arcs on a forest dial."),
        Look("arctic", "Arctic Lagoon", 0xFF66D6E8, "Cyan arcs on a midnight blue dial."),
    )
    val IDS = ALL.map { it.id }

    fun of(id: String) = ALL.firstOrNull { it.id == id } ?: ALL[1]
}
