package dev.freelocs.aetherion.gui.engine

/**
 * 8x8 pixel-art glyphs, authored as text so they are easy to review and edit. They are rendered with
 * GUI quads (one per horizontal run), so they need no texture assets and stay crisp at every GUI scale.
 */
class Icon(art: List<String>) {
    /** One bit mask per row, MSB = leftmost pixel. */
    val rows = IntArray(8)

    init {
        require(art.size == 8 && art.all { it.length == 8 }) { "Icons are 8x8" }
        art.forEachIndexed { y, line -> line.forEachIndexed { x, c -> if (c == '#') rows[y] = rows[y] or (0x80 shr x) } }
    }

    /** Hollow variant: keeps only pixels that touch an empty neighbour. */
    fun outlined(): Icon {
        val art = (0 until 8).map { y ->
            (0 until 8).joinToString("") { x ->
                val on = bit(x, y)
                val edge = !bit(x - 1, y) || !bit(x + 1, y) || !bit(x, y - 1) || !bit(x, y + 1)
                if (on && edge) "#" else "."
            }
        }
        return Icon(art)
    }

    fun flippedVertically() = Icon((7 downTo 0).map { y -> (0 until 8).joinToString("") { x -> if (bit(x, y)) "#" else "." } })

    val flippedCached: Icon by lazy { flippedVertically() }

    /**
     * Minimal rectangle cover of the glyph as flat (x, y, w, h) quadruples in pixel units. Horizontal runs
     * that repeat on the next row are merged into one taller rectangle, so an icon costs far fewer draw
     * calls than one quad per run (each call allocates render state inside Minecraft).
     */
    val rects: IntArray by lazy {
        val out = ArrayList<Int>()
        // open[x] = index in `out` (of the rect's y) for a run starting at x with a given width, to extend downward
        val openByStart = HashMap<Long, Int>()
        for (row in 0 until 8) {
            val nextOpen = HashMap<Long, Int>()
            var col = 0
            while (col < 8) {
                if (!bit(col, row)) { col++; continue }
                var end = col
                while (bit(end + 1, row)) end++
                val key = col.toLong() * 16 + (end - col + 1)
                val prev = openByStart[key]
                if (prev != null) {
                    out[prev + 3] = out[prev + 3] + 1 // extend height of the rect above
                    nextOpen[key] = prev
                } else {
                    val at = out.size
                    out.add(col); out.add(row); out.add(end - col + 1); out.add(1)
                    nextOpen[key] = at
                }
                col = end + 1
            }
            openByStart.clear(); openByStart.putAll(nextOpen)
        }
        out.toIntArray()
    }

    fun bit(x: Int, y: Int) = x in 0..7 && y in 0..7 && (rows[y] and (0x80 shr x)) != 0
}

object Icons {
    val DASHBOARD = Icon(listOf("###.###.", "###.###.", "###.###.", "........", "###.###.", "###.###.", "###.###.", "........"))
    val HUD = Icon(listOf("########", "#......#", "#.##...#", "#.##...#", "#......#", "########", "..####..", "........"))
    val MINING = Icon(listOf("..####..", ".#....#.", "#...#..#", "....#...", "...#....", "..#.....", ".#......", "#......."))
    val FARMING = Icon(listOf("...##...", "..####..", "...##...", ".#.##.#.", "..####..", "...##...", "....#...", "....#..."))
    val COMBAT = Icon(listOf("......##", ".....###", "....###.", "...###..", "#.###...", ".###....", ".#.#....", "#...#..."))
    val DUNGEONS = Icon(listOf(".######.", "########", "##.##.##", "##.##.##", "########", ".######.", "..#.#.#.", "..#####."))
    val KUUDRA = Icon(listOf("....#...", "...##...", "..###.#.", "..#####.", ".#######", ".###.###", ".##...##", "..#####."))
    val FISHING = Icon(listOf("........", "..###..#", ".######.", "#.#####.", "#.#####.", ".######.", "..###..#", "........"))
    val FORAGING = Icon(listOf("...##...", "..####..", ".######.", "..####..", ".######.", "########", "...##...", "...##..."))
    val RIFT = Icon(listOf("...##...", "..#..#..", ".#.##.#.", "#.#..#.#", "#.#..#.#", ".#.##.#.", "..#..#..", "...##..."))
    val ECONOMY = Icon(listOf("..####..", ".#....#.", "#..##..#", "#.####.#", "#.####.#", "#..##..#", ".#....#.", "..####.."))
    val COLLECTIONS = Icon(listOf("#######.", "#.....#.", "#.###.#.", "#.....#.", "#.###.#.", "#.....#.", "#######.", ".#######"))
    val EVENTS = Icon(listOf(".#....#.", "########", "#......#", "#.#.#..#", "#......#", "#.#.#.##", "#......#", "########"))
    val SHARDS = Icon(listOf("..####..", ".#.##.#.", "########", ".######.", "..####..", "...##...", "........", "........"))
    val COSMETICS = Icon(listOf("...##...", "...##...", "..####..", "########", "########", "..####..", "...##...", "...##..."))
    val PERFORMANCE = Icon(listOf("....###.", "...###..", "..###...", ".######.", "...###..", "..###...", ".###....", ".#......"))
    val GEAR = Icon(listOf("...##...", ".#.##.#.", "..####..", "###..###", "###..###", "..####..", ".#.##.#.", "...##..."))
    val SEARCH = Icon(listOf("..###...", ".#...#..", "#.....#.", "#.....#.", "#.....#.", ".#...#..", "..###.#.", ".....#.#"))
    val BELL = Icon(listOf("...##...", "..####..", "..####..", ".######.", ".######.", "########", "........", "...##..."))
    val STAR = Icon(listOf("...##...", "...##...", "########", ".######.", "..####..", "..####..", ".##..##.", ".#....#."))
    val STAR_OUTLINE = STAR.outlined()
    val CHEVRON_RIGHT = Icon(listOf("..#.....", "...#....", "....#...", ".....#..", "....#...", "...#....", "..#.....", "........"))
    val CHEVRON_DOWN = Icon(listOf("........", "........", "##....##", ".##..##.", "..####..", "...##...", "........", "........"))
    val CHECK = Icon(listOf("........", "......#.", ".....##.", "#...##..", "##.##...", ".###....", "..#.....", "........"))
    val CROSS = Icon(listOf("##....##", ".##..##.", "..####..", "...##...", "..####..", ".##..##.", "##....##", "........"))
    val RESET = Icon(listOf("..#.....", ".##.###.", "####...#", ".##....#", "..#....#", ".......#", "#.....#.", ".#####.."))
    val USER = Icon(listOf("..####..", "..####..", "..####..", "...##...", ".######.", "########", "########", "........"))
    val INFO = Icon(listOf("...##...", "........", "..###...", "...##...", "...##...", "...##...", "..####..", "........"))
    val GLOBE = Icon(listOf("..####..", ".#.##.#.", "#..##..#", "########", "#..##..#", ".#.##.#.", "..####..", "........"))
    val PLUS = Icon(listOf("...##...", "...##...", "...##...", "########", "########", "...##...", "...##...", "...##..."))
    val MOVE = Icon(listOf("...##...", "..####..", "#..##..#", "########", "########", "#..##..#", "..####..", "...##..."))
    val EXPORT = Icon(listOf("...##...", "..####..", ".######.", "...##...", "...##...", "........", "#......#", "########"))
    val IMPORT = EXPORT.flippedVertically()
    val MENU = Icon(listOf("........", "########", "........", "########", "........", "########", "........", "........"))
    val LOCK = Icon(listOf("..####..", ".#....#.", ".#....#.", "########", "########", "###..###", "########", "########"))
    val WARNING = Icon(listOf("...##...", "...##...", "..####..", "..####..", ".##..##.", ".##..##.", "########", "........"))

    private val byName: Map<String, Icon> = mapOf(
        "dashboard" to DASHBOARD, "hud" to HUD, "mining" to MINING, "farming" to FARMING, "combat" to COMBAT,
        "dungeons" to DUNGEONS, "kuudra" to KUUDRA, "fishing" to FISHING, "foraging" to FORAGING, "rift" to RIFT,
        "economy" to ECONOMY, "collections" to COLLECTIONS, "events" to EVENTS, "shards" to SHARDS,
        "cosmetics" to COSMETICS, "performance" to PERFORMANCE, "gear" to GEAR, "info" to INFO
    )

    fun byName(name: String): Icon = byName[name] ?: INFO
}
