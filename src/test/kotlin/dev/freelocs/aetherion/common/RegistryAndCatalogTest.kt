package dev.freelocs.aetherion.common

import dev.freelocs.aetherion.common.data.Roadmap
import dev.freelocs.aetherion.common.features.CategoryDefinition
import dev.freelocs.aetherion.common.features.FeatureDefinition
import dev.freelocs.aetherion.common.features.FeatureRegistry
import dev.freelocs.aetherion.common.features.FeatureStatus
import dev.freelocs.aetherion.common.features.GroupDefinition
import dev.freelocs.aetherion.common.features.SettingDefinition
import dev.freelocs.aetherion.common.services.I18n
import dev.freelocs.aetherion.core.Catalog
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class RegistryAndCatalogTest {
    @BeforeEach
    fun reset() { FeatureRegistry.clear(); I18n.reset() }

    private fun base() {
        FeatureRegistry.registerGroup(GroupDefinition("g", "g"))
        FeatureRegistry.registerCategory(CategoryDefinition("c", "g", "c", "c", "info"))
    }

    private fun feature(id: String, deps: List<String> = emptyList(), status: FeatureStatus = FeatureStatus.IMPLEMENTED, default: Boolean = false, toggleable: Boolean = true) =
        FeatureDefinition(id, "c", "t.$id", "d.$id", default, dependencies = deps, status = status, toggleable = toggleable)

    @Test
    fun `duplicate and dangling registrations are rejected`() {
        base()
        FeatureRegistry.registerFeature(feature("a"))
        assertThrows(IllegalArgumentException::class.java) { FeatureRegistry.registerFeature(feature("a")) }
        assertThrows(IllegalArgumentException::class.java) { FeatureRegistry.registerCategory(CategoryDefinition("x", "nope", "x", "x", "info")) }
        assertThrows(IllegalArgumentException::class.java) { FeatureRegistry.registerFeature(FeatureDefinition("b", "nope", "t", "d", false)) }
    }

    @Test
    fun `validation reports unknown dependencies and cycles`() {
        base()
        FeatureRegistry.registerFeature(feature("a", listOf("ghost")))
        FeatureRegistry.registerFeature(feature("b", listOf("c")))
        FeatureRegistry.registerFeature(feature("c", listOf("b")))
        val problems = FeatureRegistry.validate()
        assertTrue(problems.any { "unknown dependency 'ghost'" in it })
        assertTrue(problems.any { "cycle" in it })
    }

    @Test
    fun `validation reports duplicate settings and enabled planned features`() {
        base()
        FeatureRegistry.registerFeature(FeatureDefinition("a", "c", "t", "d", false, settings = listOf(
            SettingDefinition.Toggle("x", "t", null, true), SettingDefinition.Toggle("x", "t", null, false))))
        FeatureRegistry.registerFeature(feature("planned", status = FeatureStatus.PLANNED, default = true, toggleable = false))
        val problems = FeatureRegistry.validate()
        assertTrue(problems.any { "duplicate setting 'x'" in it })
        assertTrue(problems.any { "unimplemented feature enabled by default" in it })
    }

    @Test
    fun `dependants are found transitively`() {
        base()
        FeatureRegistry.registerFeature(feature("a"))
        FeatureRegistry.registerFeature(feature("b", listOf("a")))
        FeatureRegistry.registerFeature(feature("c", listOf("b")))
        assertEquals(setOf("b", "c"), FeatureRegistry.dependantsOf("a").map { it.id }.toSet())
    }

    @Test
    fun `search matches every term across title description and id`() {
        base()
        FeatureRegistry.registerFeature(feature("alpha.one"))
        FeatureRegistry.registerFeature(feature("beta.two"))
        val tr = { k: String -> if (k == "t.alpha.one") "Fast clock" else if (k == "d.alpha.one") "shows time" else k }
        assertEquals(listOf("alpha.one"), FeatureRegistry.search("fast TIME", tr).map { it.id })
        assertTrue(FeatureRegistry.search("   ", tr).isEmpty())
        assertTrue(FeatureRegistry.search("nothing", tr).isEmpty())
    }

    @Test
    fun `the shipped catalog is valid`() {
        Catalog.registerAll()
        assertEquals(emptyList<String>(), FeatureRegistry.validate())
    }

    @Test
    fun `no planned or review feature is ever enabled or has a runtime`() {
        Catalog.registerAll()
        val planned = FeatureRegistry.features.filter { it.status != FeatureStatus.IMPLEMENTED }
        assertTrue(planned.size >= 25, "expected the roadmap to be loaded, got ${planned.size}")
        assertTrue(planned.none { it.defaultEnabled || it.toggleable || it.factory != null })
    }

    @Test
    fun `every roadmap entry belongs to a registered category and has full metadata`() {
        Catalog.registerAll()
        for (e in Roadmap.entries) {
            assertTrue(FeatureRegistry.category(e.categoryId) != null, "${e.id}: unknown category")
            assertTrue(e.acceptance.isNotEmpty(), "${e.id}: no acceptance criteria")
            assertTrue(e.apiPermission.isNotBlank() && e.compatibility.isNotBlank(), "${e.id}: missing metadata")
            assertTrue(e.rule in setOf("informational", "cosmetic", "review"), "${e.id}: bad rule ${e.rule}")
            assertEquals(e.rule == "review", e.status == FeatureStatus.REVIEW_REQUIRED, "${e.id}: rule/status mismatch")
        }
    }

    @Test
    fun `every planned skyblock group from the brief has a category`() {
        Catalog.registerAll()
        val required = listOf("general", "hud", "mining", "farming", "combat", "dungeons", "kuudra", "fishing", "foraging", "rift", "economy", "collections", "events", "shards", "cosmetics")
        for (id in required) assertTrue(FeatureRegistry.category(id) != null, "missing category $id")
        assertFalse(FeatureRegistry.features.isEmpty())
    }
}
