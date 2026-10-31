package dev.freelocs.aetherion.common

import com.google.gson.JsonPrimitive
import dev.freelocs.aetherion.common.features.SettingDefinition
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class SettingDefinitionTest {
    private val slider = SettingDefinition.Slider("s", "t", null, min = 2.0, max = 32.0, step = 2.0, default = 8.0)

    @Test
    fun `slider clamps and snaps to its step`() {
        assertEquals(2.0, slider.coerce(-50))
        assertEquals(32.0, slider.coerce(1000.0))
        assertEquals(10.0, slider.coerce(9.4))
        assertEquals(8.0, slider.coerce(8))
    }

    @Test
    fun `slider rejects non numeric and non finite values`() {
        assertNull(slider.coerce("12"))
        assertNull(slider.coerce(Double.NaN))
        assertNull(slider.coerce(Double.POSITIVE_INFINITY))
        assertNull(slider.coerce(null))
    }

    @Test
    fun `slider schema itself is validated`() {
        assertThrows(IllegalArgumentException::class.java) { SettingDefinition.Slider("s", "t", null, 5.0, 1.0, 1.0, 3.0) }
        assertThrows(IllegalArgumentException::class.java) { SettingDefinition.Slider("s", "t", null, 0.0, 10.0, 0.0, 3.0) }
        assertThrows(IllegalArgumentException::class.java) { SettingDefinition.Slider("s", "t", null, 0.0, 10.0, 1.0, 11.0) }
    }

    @Test
    fun `choice only accepts declared options`() {
        val c = SettingDefinition.Choice("c", "t", null, listOf("a", "b"), "a", "opt")
        assertEquals("b", c.coerce("b"))
        assertNull(c.coerce("z"))
        assertNull(c.coerce(3))
        assertEquals("opt.a", c.optionKey("a"))
        assertThrows(IllegalArgumentException::class.java) { SettingDefinition.Choice("c", "t", null, listOf("a"), "x", "opt") }
    }

    @Test
    fun `color parses hex strings and forces full alpha`() {
        val c = SettingDefinition.Color("c", "t", null, 0xFFFF0000.toInt())
        assertEquals(0xFF112233.toInt(), c.fromJson(JsonPrimitive("#112233")))
        assertEquals(0xFF112233.toInt(), c.fromJson(JsonPrimitive("112233")))
        assertNull(c.fromJson(JsonPrimitive("#12")))
        assertNull(c.fromJson(JsonPrimitive("#GGGGGG")))
        assertEquals("#112233", c.toJson(0xFF112233.toInt()).asString)
        assertEquals(0xFF000001.toInt(), c.coerce(1))
    }

    @Test
    fun `keybind accepts minecraft key names or empty`() {
        val k = SettingDefinition.Keybind("k", "t")
        assertEquals("key.keyboard.k", k.coerce("key.keyboard.k"))
        assertEquals("", k.coerce(""))
        assertNull(k.coerce("K"))
        assertNull(k.coerce(75))
    }

    @Test
    fun `toggle accepts booleans only`() {
        val t = SettingDefinition.Toggle("t", "t", null, true)
        assertEquals(false, t.coerce(false))
        assertNull(t.coerce("true"))
        assertNull(t.fromJson(JsonPrimitive("true")))
        assertEquals(true, t.fromJson(JsonPrimitive(true)))
    }
}
