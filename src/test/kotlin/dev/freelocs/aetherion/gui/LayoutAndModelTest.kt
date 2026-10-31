package dev.freelocs.aetherion.gui

import dev.freelocs.aetherion.common.data.Roadmap
import dev.freelocs.aetherion.common.features.FeatureRegistry
import dev.freelocs.aetherion.common.features.SettingDefinition
import dev.freelocs.aetherion.common.services.I18n
import dev.freelocs.aetherion.common.ui.UiState
import dev.freelocs.aetherion.core.Catalog
import dev.freelocs.aetherion.gui.components.AuroraLayout
import dev.freelocs.aetherion.gui.engine.Icon
import dev.freelocs.aetherion.gui.engine.Icons
import dev.freelocs.aetherion.gui.theme.Colors
import dev.freelocs.aetherion.gui.theme.Metrics
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.charset.StandardCharsets

class LayoutAndModelTest {
    @Test
    fun `wide screens show the full sidebar and the detail panel`() {
        val l = AuroraLayout(); l.compute(1000, 600, sidebarCollapsed = false, wantsDetail = true)
        assertFalse(l.compact)
        assertEquals(Metrics.SIDEBAR_W, l.sidebarW)
        assertEquals(Metrics.DETAIL_W, l.detailW)
        assertEquals(l.panelW, l.sidebarW + l.contentW + l.detailW)
        assertTrue(l.panelX >= 0 && l.panelX + l.panelW <= 1000)
    }

    @Test
    fun `narrow screens collapse the sidebar and never dock the detail panel`() {
        val l = AuroraLayout(); l.compute(500, 300, sidebarCollapsed = false, wantsDetail = true)
        assertTrue(l.compact)
        assertEquals(Metrics.SIDEBAR_COLLAPSED_W, l.sidebarW)
        assertEquals(0, l.detailW)
        assertEquals(500, l.panelW)
    }

    @Test
    fun `the user can collapse the sidebar on wide screens`() {
        val l = AuroraLayout(); l.compute(1200, 700, sidebarCollapsed = true, wantsDetail = false)
        assertTrue(l.compact)
        assertEquals(Metrics.SIDEBAR_COLLAPSED_W, l.sidebarW)
    }

    @Test
    fun `layout regions never overlap or leave the panel for any size`() {
        val l = AuroraLayout()
        for (w in 200..1600 step 37) for (h in 120..900 step 41) for (detail in listOf(true, false)) {
            l.compute(w, h, false, detail)
            assertTrue(l.contentW > 0 && l.contentH > 0, "$w x $h")
            assertTrue(l.contentX + l.contentW + l.detailW <= l.panelX + l.panelW, "$w x $h")
            assertTrue(l.panelY + l.panelH <= h, "$w x $h")
        }
    }

    @Test
    fun `ui scale shrinks small windows only and never below 0_75`() {
        assertEquals(1f, AuroraLayout.uiScale(1920, 1080))
        assertEquals(0.75f, AuroraLayout.uiScale(300, 100))
        assertTrue(AuroraLayout.uiScale(480, 270) in 0.75f..1f)
    }

    @Test
    fun `ui state navigation resets search selection and tab`() {
        val s = UiState()
        s.setSearch("clock"); s.selectFeature("hud.clock"); s.setRoadmapTab(true)
        val v = s.version
        s.openCategory("mining")
        assertEquals("mining", s.categoryId); assertEquals("", s.search); assertEquals(null, s.selectedFeatureId); assertFalse(s.roadmapTab)
        assertTrue(s.version > v)
    }

    @Test
    fun `selecting a feature does not rebuild the page but opening one does`() {
        val s = UiState(); val v = s.version
        s.selectFeature("a"); assertEquals(v, s.version)
        s.openFeature("hud", "hud.fps")
        assertTrue(s.version > v); assertTrue("hud.fps" in s.expanded)
    }

    @Test
    fun `icon rectangle cover reproduces every glyph exactly`() {
        val all = Icons::class.java.declaredFields.filter { Icon::class.java.isAssignableFrom(it.type) }
        assertTrue(all.size >= 30)
        for (f in all) {
            f.isAccessible = true
            val icon = f.get(Icons) as Icon
            val painted = Array(8) { BooleanArray(8) }
            val r = icon.rects
            var i = 0
            while (i < r.size) {
                for (yy in r[i + 1] until r[i + 1] + r[i + 3]) for (xx in r[i] until r[i] + r[i + 2]) {
                    assertFalse(painted[yy][xx], "${f.name}: pixel painted twice")
                    painted[yy][xx] = true
                }
                i += 4
            }
            for (y in 0 until 8) for (x in 0 until 8) assertEquals(icon.bit(x, y), painted[y][x], "${f.name} ($x,$y)")
            assertTrue(r.size / 4 <= 8 * 4, "${f.name}: cover should be small")
        }
    }

    @Test
    fun `colour helpers round trip and clamp`() {
        val c = 0xFF336699.toInt(); val hsv = FloatArray(3)
        Colors.rgbToHsv(c, hsv)
        assertEquals(c, Colors.hsvToRgb(hsv[0], hsv[1], hsv[2]))
        assertEquals(0x80FFFFFF.toInt(), Colors.alpha(0xFFFFFFFF.toInt(), 0.5f) or 0, "half alpha")
        assertEquals(0xFF000000.toInt(), Colors.lerp(0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0f))
        assertEquals(0xFFFFFFFF.toInt(), Colors.lerp(0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 2f))
    }

    @Test
    fun `english and german tables have identical keys and placeholders`() {
        val en = table("en_us"); val de = table("de_de")
        assertEquals(en.keys - de.keys, emptySet<String>(), "missing in German")
        assertEquals(de.keys - en.keys, emptySet<String>(), "missing in English")
        val fmt = Regex("%[sd]")
        for (k in en.keys) assertEquals(fmt.findAll(en[k]!!).map { it.value }.toList(), fmt.findAll(de[k]!!).map { it.value }.toList(), "placeholders of $k")
        for (k in en.keys) assertTrue(en[k]!!.isNotBlank() && de[k]!!.isNotBlank(), "blank $k")
    }

    @Test
    fun `every registered text key resolves in both languages`() {
        Catalog.registerAll()
        for (lang in listOf("en", "de")) {
            I18n.apply(lang, lang)
            val missing = ArrayList<String>()
            fun need(key: String?) { if (key != null && !I18n.has(key)) missing += "$lang:$key" }
            for (g in FeatureRegistry.groups) need(g.titleKey)
            for (c in FeatureRegistry.categories) { need(c.titleKey); need(c.descriptionKey) }
            for (f in FeatureRegistry.features) {
                need(f.titleKey); need(f.descriptionKey); need(f.compatibilityKey)
                for (s in f.settings) {
                    need(s.titleKey); need(s.descriptionKey)
                    if (s is SettingDefinition.Choice) s.options.forEach { need(s.optionKey(it)) }
                }
            }
            for (e in Roadmap.entries) { need("roadmap.source.${e.dataSource}"); need("roadmap.level.${e.complexity}"); need("roadmap.level.${e.utility}"); need("roadmap.rule.${e.rule}") }
            assertEquals(emptyList<String>(), missing)
        }
        I18n.reset()
    }

    @Test
    fun `german differs from english where it should`() {
        Catalog.registerAll()
        I18n.apply("en", "en"); val en = I18n.tr("category.combat.title")
        I18n.apply("de", "de"); val de = I18n.tr("category.combat.title")
        assertEquals("Combat", en); assertEquals("Kampf", de)
        I18n.apply("auto", "de_de"); assertEquals("de", I18n.language)
        I18n.apply("auto", "en_gb"); assertEquals("en", I18n.language)
        I18n.reset()
    }

    private fun table(name: String): Map<String, String> {
        val text = LayoutAndModelTest::class.java.getResourceAsStream("/assets/aetherion/lang/$name.json")!!.readBytes().toString(StandardCharsets.UTF_8)
        return com.google.gson.JsonParser.parseString(text).asJsonObject.entrySet().associate { it.key to it.value.asString }
    }
}
