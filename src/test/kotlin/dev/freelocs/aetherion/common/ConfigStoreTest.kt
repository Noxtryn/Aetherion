package dev.freelocs.aetherion.common

import dev.freelocs.aetherion.common.config.ConfigStore
import dev.freelocs.aetherion.common.config.WidgetPlacement
import dev.freelocs.aetherion.common.features.FeatureRegistry
import dev.freelocs.aetherion.common.services.I18n
import dev.freelocs.aetherion.core.Catalog
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class ConfigStoreTest {
    @TempDir
    lateinit var dir: Path

    private lateinit var file: Path

    @BeforeEach
    fun setUp() {
        I18n.reset()
        Catalog.registerAll()
        file = dir.resolve("aetherion.json")
    }

    private fun store(): ConfigStore = ConfigStore(file).also { it.load() }
    private fun def(id: String) = FeatureRegistry.feature(id)!!

    @Test
    fun `fresh config uses schema defaults and writes a v2 file`() {
        val c = store()
        assertFalse(c.isEnabled("hud.fps"))
        assertTrue(c.isEnabled("hud.core"))
        assertEquals(8.0, c.double("hud.core", "grid"))
        assertTrue(Files.exists(file))
        assertTrue(Files.readString(file).contains("\"schema\": 2"))
    }

    @Test
    fun `settings and toggles survive a save and reload`() {
        val c = store()
        c.setEnabled(def("hud.fps"), true)
        c.set(def("hud.core"), def("hud.core").setting("grid")!!, 16.0)
        c.set(def("cosmetics.theme"), def("cosmetics.theme").setting("accent")!!, "ice")
        c.saveNow()
        val again = store()
        assertTrue(again.isEnabled("hud.fps"))
        assertEquals(16.0, again.double("hud.core", "grid"))
        assertEquals("ice", again.string("cosmetics.theme", "accent"))
    }

    @Test
    fun `values equal to the default are not written`() {
        val c = store()
        c.set(def("hud.core"), def("hud.core").setting("grid")!!, 16.0)
        c.set(def("hud.core"), def("hud.core").setting("grid")!!, 8.0)
        c.saveNow()
        assertFalse(Files.readString(file).contains("\"grid\""))
    }

    @Test
    fun `invalid values are ignored and fall back to defaults`() {
        Files.writeString(file, """{"schema":2,"profiles":{"default":{"features":{"hud.core":{"settings":{"grid":"huge","snap":7,"unknown":1}}}}}}""")
        val c = store()
        assertEquals(8.0, c.double("hud.core", "grid"))
        assertTrue(c.bool("hud.core", "snap"))
    }

    @Test
    fun `out of range numbers are clamped by the schema`() {
        Files.writeString(file, """{"schema":2,"profiles":{"default":{"features":{"hud.core":{"settings":{"grid":9999}}}}}}""")
        assertEquals(32.0, store().double("hud.core", "grid"))
    }

    @Test
    fun `planned features can never be enabled even by a hand edited file`() {
        Files.writeString(file, """{"schema":2,"profiles":{"default":{"features":{"mining.skill-progress":{"enabled":true}}}}}""")
        val c = store()
        assertFalse(c.isEnabled("mining.skill-progress"))
        c.setEnabled(def("mining.skill-progress"), true)
        assertFalse(c.isEnabled("mining.skill-progress"))
    }

    @Test
    fun `non toggleable features stay on`() {
        val c = store()
        assertTrue(c.isEnabled("general.interface"))
        c.setEnabled(def("general.interface"), false)
        assertTrue(c.isEnabled("general.interface"))
    }

    @Test
    fun `a legacy v1 config is backed up and replaced by defaults`() {
        Files.writeString(file, """{"categories":{"hud":{"enabled":true}}}""")
        val c = store()
        assertTrue(c.loadOutcome.contains("legacy v1"), c.loadOutcome)
        assertTrue(Files.exists(dir.resolve("aetherion.json.v1-backup")))
        assertEquals("""{"categories":{"hud":{"enabled":true}}}""", Files.readString(dir.resolve("aetherion.json.v1-backup")))
        assertFalse(c.isEnabled("hud.fps"))
        assertTrue(Files.readString(file).contains("\"schema\": 2"))
    }

    @Test
    fun `a corrupt config is quarantined instead of crashing`() {
        Files.writeString(file, "{ this is not json")
        val c = store()
        assertTrue(c.loadOutcome.contains("corrupt"))
        assertTrue(Files.list(dir).anyMatch { it.fileName.toString().contains(".corrupt-") })
        assertTrue(c.isEnabled("hud.core"))
    }

    @Test
    fun `profiles keep separate feature state and hud layout`() {
        val c = store()
        c.setEnabled(def("hud.fps"), true)
        c.replaceHudLayout(linkedMapOf("hud.fps" to WidgetPlacement(anchor = 2, x = -10f, y = 5f)))
        val other = c.createProfile("Mining")
        assertTrue(c.switchProfile(other))
        assertFalse(c.isEnabled("hud.fps"))
        assertTrue(c.hudLayout.isEmpty())
        c.setEnabled(def("hud.clock"), true)
        c.saveNow()
        val again = store()
        assertEquals(other, again.activeProfile)
        assertTrue(again.isEnabled("hud.clock"))
        again.switchProfile("default")
        assertTrue(again.isEnabled("hud.fps"))
        assertEquals(2, again.hudLayout["hud.fps"]!!.anchor)
    }

    @Test
    fun `global settings are shared across profiles`() {
        val c = store()
        c.set(def("cosmetics.theme"), def("cosmetics.theme").setting("variant")!!, "midnight")
        c.switchProfile(c.createProfile("p2"))
        assertEquals("midnight", c.string("cosmetics.theme", "variant"))
    }

    @Test
    fun `the default profile cannot be deleted and deleting the active profile falls back`() {
        val c = store()
        assertFalse(c.deleteProfile("default"))
        val p = c.createProfile("temp"); c.switchProfile(p)
        assertTrue(c.deleteProfile(p))
        assertEquals("default", c.activeProfile)
    }

    @Test
    fun `profile names are sanitised and made unique`() {
        val c = store()
        val a = c.createProfile("  we<ird>/name!  ")
        val b = c.createProfile("  we<ird>/name!  ")
        assertEquals("weirdname", a)
        assertEquals("weirdname 2", b)
    }

    @Test
    fun `export then import round trips a profile`() {
        val c = store()
        c.setEnabled(def("hud.clock"), true)
        c.set(def("hud.clock"), def("hud.clock").setting("seconds")!!, true)
        c.replaceHudLayout(linkedMapOf("hud.clock" to WidgetPlacement(anchor = 8, x = -3f, y = -4f, scale = 1.5f, opacity = 0.5f, layer = 3)))
        val json = c.exportActiveProfile()
        val name = c.importProfile(json).getOrThrow()
        c.switchProfile(name)
        assertTrue(c.isEnabled("hud.clock"))
        assertTrue(c.bool("hud.clock", "seconds"))
        val p = c.hudLayout["hud.clock"]!!
        assertEquals(8, p.anchor); assertEquals(1.5f, p.scale); assertEquals(0.5f, p.opacity); assertEquals(3, p.layer)
    }

    @Test
    fun `import rejects garbage and newer schemas`() {
        val c = store()
        assertTrue(c.importProfile("not json").isFailure)
        assertTrue(c.importProfile("""{"aetherion":"other"}""").isFailure)
        assertTrue(c.importProfile("""{"aetherion":"profile-export","schema":99,"profile":{}}""").isFailure)
    }

    @Test
    fun `hud placements are clamped when loaded`() {
        Files.writeString(file, """{"schema":2,"profiles":{"default":{"hud":{"hud.fps":{"anchor":42,"x":1e9,"scale":50,"opacity":-3,"layer":999999}}}}}""")
        val p = store().hudLayout["hud.fps"]!!
        assertEquals(8, p.anchor); assertEquals(8192f, p.x); assertEquals(3f, p.scale); assertEquals(0.1f, p.opacity); assertEquals(1000, p.layer)
    }

    @Test
    fun `reset feature and reset all restore defaults`() {
        val c = store()
        c.setEnabled(def("hud.fps"), true)
        c.set(def("hud.core"), def("hud.core").setting("grid")!!, 24.0)
        c.resetFeature(def("hud.core"))
        assertEquals(8.0, c.double("hud.core", "grid"))
        assertTrue(c.isEnabled("hud.fps"))
        c.set(def("cosmetics.theme"), def("cosmetics.theme").setting("accent")!!, "violet")
        c.resetAll(includeGlobal = false)
        assertFalse(c.isEnabled("hud.fps"))
        assertEquals("violet", c.string("cosmetics.theme", "accent"))
        c.resetAll(includeGlobal = true)
        assertEquals("pink", c.string("cosmetics.theme", "accent"))
    }

    @Test
    fun `favourites persist and unknown ids are dropped`() {
        Files.writeString(file, """{"schema":2,"favorites":["hud.fps","ghost.feature"]}""")
        val c = store()
        assertEquals(listOf("hud.fps"), c.favorites)
        c.toggleFavorite("hud.clock")
        c.saveNow()
        assertEquals(listOf("hud.fps", "hud.clock"), store().favorites)
        assertNotNull(c.favoritesVersion)
    }

    @Test
    fun `saves are atomic and leave no temp file behind`() {
        val c = store()
        c.setEnabled(def("hud.fps"), true)
        c.saveNow()
        assertFalse(Files.exists(dir.resolve("aetherion.json.tmp")))
    }
}
