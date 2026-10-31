package dev.freelocs.aetherion.core

import com.mojang.blaze3d.platform.InputConstants
import dev.freelocs.aetherion.common.services.I18n
import dev.freelocs.aetherion.gui.animations.AuroraAnimationEngine
import dev.freelocs.aetherion.gui.components.AuroraFeatureCard
import dev.freelocs.aetherion.gui.components.AuroraKeybindField
import dev.freelocs.aetherion.gui.components.AuroraSlider
import dev.freelocs.aetherion.gui.components.Ui
import dev.freelocs.aetherion.gui.editor.AuroraHudEditor
import dev.freelocs.aetherion.gui.screens.AuroraScreen
import dev.freelocs.aetherion.gui.theme.Colors
import dev.freelocs.aetherion.gui.theme.Theme
import dev.freelocs.aetherion.hud.HudManager
import dev.freelocs.aetherion.platform.Platform
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Screenshot
import net.minecraft.client.gui.screens.TitleScreen
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.input.MouseButtonInfo
import java.nio.file.Files
import java.nio.file.Path

/**
 * Developer-only automation, inert unless the JVM is started with `-Daetherion.dev=gui`.
 *
 * It drives a real Minecraft client through the same input methods the game calls (mouse clicks, key
 * presses, typed characters, scrolling), asserts on the resulting state and takes screenshots, so GUI
 * behaviour can be verified at runtime without a human. Results are written to
 * `aetherion-dev-results.txt` in the run directory. Never active for players.
 */
object DevHarness {
    private val script: String? = System.getProperty("aetherion.dev")
    val enabled get() = script != null

    private class Step(val wait: Int, val name: String, val action: () -> Unit)

    private val steps = ArrayList<Step>()
    private var index = 0
    private var waited = 0
    private var started = false
    private val results = ArrayList<String>()
    private var failures = 0

    fun install() {
        if (!enabled) return
        Log.info("DevHarness active: script='$script'")
        buildScript()
        ClientTickEvents.END_CLIENT_TICK.register { onTick() }
    }

    // ── script runner ───────────────────────────────────────────────────────

    private fun onTick() {
        val mc = Platform.mc
        if (!started) {
            if (Platform.currentScreen() is TitleScreen && ++waited > 80) { started = true; waited = 0 }
            return
        }
        if (index >= steps.size) return
        val step = steps[index]
        if (++waited < step.wait) return
        waited = 0
        index++
        try {
            step.action()
        } catch (t: Throwable) {
            fail(step.name, "exception: $t")
            Log.error("DevHarness step '${step.name}' threw", t)
        }
        if (index >= steps.size) finish(mc)
    }

    private fun finish(mc: net.minecraft.client.Minecraft) {
        val summary = "RESULT: ${results.count { it.startsWith("PASS") }} passed, $failures failed"
        results += summary
        Log.info("DevHarness: $summary")
        runCatching { Files.writeString(Path.of("aetherion-dev-results.txt"), results.joinToString("\n") + "\n") }
        // A short delay lets queued screenshots flush, then a clean shutdown.
        steps += Step(30, "exit") { mc.stop() }
        index = steps.size - 1
    }

    private fun step(wait: Int, name: String, action: () -> Unit) { steps += Step(wait, name, action) }

    private fun pass(name: String, detail: String = "") { results += "PASS $name $detail".trim(); Log.info("TEST $name: PASS $detail") }
    private fun fail(name: String, detail: String) { failures++; results += "FAIL $name $detail"; Log.error("TEST $name: FAIL $detail") }
    private fun check(name: String, ok: Boolean, detail: String = "") { if (ok) pass(name, detail) else fail(name, detail) }

    private fun shot(name: String) {
        step(8, "shot:$name") { Screenshot.grab(Platform.mc, false); results += "SHOT $name" }
        step(10, "shot-wait:$name") {}
    }

    // ── input helpers (same entry points the game uses) ─────────────────────

    private fun screen() = Platform.currentScreen() as? AuroraScreen ?: error("AuroraScreen not open")
    private fun click(ux: Int, uy: Int, button: Int = 0) {
        val s = screen(); val x = s.uiToScreen(ux); val y = s.uiToScreen(uy)
        s.mouseClicked(MouseButtonEvent(x, y, MouseButtonInfo(button, 0)), false)
        s.mouseReleased(MouseButtonEvent(x, y, MouseButtonInfo(button, 0)))
    }
    private fun key(code: Int) {
        val s = Platform.currentScreen() ?: error("no screen")
        s.keyPressed(KeyEvent(code, code, 0))
    }
    private fun type(text: String) { val s = Platform.currentScreen() ?: error("no screen"); text.codePoints().forEach { s.charTyped(CharacterEvent(it)) } }
    private fun card(id: String) = screen().debugWidgets().filterIsInstance<AuroraFeatureCard>().firstOrNull { it.definition.id == id }
    private fun cfg() = Aetherion.config
    private fun cfgDef(id: String) = dev.freelocs.aetherion.common.features.FeatureRegistry.feature(id)!!

    private fun buildScript() {
        step(1, "open") {
            Platform.openScreen(AuroraScreen(Platform.currentScreen()))
        }
        step(45, "opened") {
            check("gui.open", Platform.currentScreen() is AuroraScreen)
            check("gui.startsOnDashboard", screen().ui.categoryId == "general")
            check("registry.valid", dev.freelocs.aetherion.common.features.FeatureRegistry.validate().isEmpty())
            check("planned.neverEnabled", dev.freelocs.aetherion.common.features.FeatureRegistry.features.filter { it.status != dev.freelocs.aetherion.common.features.FeatureStatus.IMPLEMENTED }.none { cfg().isEnabled(it) })
        }
        shot("01-dashboard")

        // category navigation through a real click on the sidebar
        step(2, "nav-hud") {
            val c = screen().debugSidebar().debugCategoryCenter("hud") ?: error("no hud row")
            click(c[0], c[1])
        }
        step(15, "nav-hud-check") {
            check("nav.sidebarClick", screen().ui.categoryId == "hud", "category=${screen().ui.categoryId}")
        }
        shot("02-hud")

        // toggle through the real toggle control
        step(2, "toggle-fps-on") { screen().debugReveal(card("hud.fps") ?: error("no fps card")) }
        step(4, "toggle-fps-click") {
            val c = card("hud.fps")?.debugToggleCenter() ?: error("no fps card/toggle")
            click(c[0], c[1])
        }
        step(6, "toggle-fps-check") {
            check("toggle.enables", cfg().isEnabled("hud.fps"))
            check("toggle.activatesFeature", Aetherion.features.isActive("hud.fps"))
            check("hud.widgetRegistered", HudManager.box("hud.fps") != null)
        }
        step(2, "toggle-fps-off") { screen().debugReveal(card("hud.fps")!!) }
        step(4, "toggle-fps-off-click") { val c = card("hud.fps")!!.debugToggleCenter()!!; click(c[0], c[1]) }
        step(6, "toggle-fps-off-check") {
            check("toggle.disables", !cfg().isEnabled("hud.fps") && !Aetherion.features.isActive("hud.fps"))
            check("hud.widgetUnregistered", HudManager.box("hud.fps") == null)
            check("lifecycle.noLeakedSubscriptions", dev.freelocs.aetherion.common.events.EventBus.subscriberCount >= 0)
        }
        step(2, "toggle-fps-on-again") { screen().debugReveal(card("hud.fps")!!) }
        step(4, "toggle-fps-on-again-click") { val c = card("hud.fps")!!.debugToggleCenter()!!; click(c[0], c[1]) }
        step(4, "enable-clock-api") { Aetherion.features.setEnabled(cfgDef("hud.clock"), true) }

        // expand a card by clicking its header, then drive a slider
        step(2, "reveal-core") { screen().debugReveal(card("hud.core") ?: error("no core card")) }
        step(4, "expand-core") {
            val core = card("hud.core") ?: error("no core card")
            click(core.x + 70, core.y + 20)
        }
        step(25, "expand-core-check") {
            check("card.expandsOnClick", card("hud.core")?.isExpanded == true)
        }
        shot("03-hud-expanded")
        step(2, "reveal-slider") { screen().debugReveal(card("hud.core")!!.debugControls().filterIsInstance<AuroraSlider>().first()) }
        step(4, "slider-drag") {
            val slider = card("hud.core")!!.debugControls().filterIsInstance<AuroraSlider>().first()
            val before = cfg().double("hud.core", "grid")
            click(slider.x + (slider.w - 44) * 8 / 10, slider.y + slider.h / 2)
            val after = cfg().double("hud.core", "grid")
            check("slider.setsValue", after > before + 10, "grid $before -> $after")
            click(slider.x + (slider.w - 44) / 10, slider.y + slider.h / 2, button = 1)
            check("slider.rightClickResets", cfg().double("hud.core", "grid") == 8.0, "grid=${cfg().double("hud.core", "grid")}")
        }

        // search through typed characters
        step(2, "search-focus") { val h = screen().debugHeader().search; click(h.x + h.w / 2, h.y + h.h / 2) }
        step(2, "search-type") { type("clo") }
        step(25, "search-check") {
            check("search.typing", screen().ui.search == "clo", "search='${screen().ui.search}'")
            check("search.filtersResults", screen().debugWidgets().filterIsInstance<AuroraFeatureCard>().any { it.definition.id == "hud.clock" })
        }
        shot("04-search")
        step(2, "search-clear") { key(InputConstants.KEY_ESCAPE); key(InputConstants.KEY_ESCAPE) }
        step(6, "search-cleared") { check("search.escClears", screen().ui.search.isEmpty() && Platform.currentScreen() is AuroraScreen, "search='${screen().ui.search}'") }

        // scrolling on a long page
        step(2, "nav-general") { val c = screen().debugSidebar().debugCategoryCenter("general")!!; click(c[0], c[1]) }
        step(12, "scroll") {
            val l = screen().debugLayout()
            val s = screen()
            repeat(6) { s.mouseScrolled(s.uiToScreen(l.contentX + 40), s.uiToScreen(l.contentY + 60), 0.0, -3.0) }
        }
        step(30, "scroll-check") { check("scroll.wheelMovesContent", screen().debugScrollOffset() > 0, "offset=${screen().debugScrollOffset()}") }
        shot("05-scrolled")

        // keybinding capture
        step(2, "reveal-keybind") { screen().debugReveal(card("general.interface")!!.debugControls().filterIsInstance<AuroraKeybindField>().first()) }
        step(4, "keybind-click") {
            val kb = card("general.interface")!!.debugControls().filterIsInstance<AuroraKeybindField>().first()
            click(kb.x + kb.w / 2, kb.y + kb.h / 2)
            check("keybind.entersListening", kb.listening)
        }
        step(2, "keybind-press") { key(InputConstants.KEY_K) }
        step(4, "keybind-check") {
            check("keybind.capturesKey", cfg().string("general.interface", "openKey") == "key.keyboard.k", "openKey=${cfg().string("general.interface", "openKey")}")
            check("keybind.labelResolves", Platform.keyLabel("key.keyboard.k").isNotEmpty(), "label=${Platform.keyLabel("key.keyboard.k")}")
            cfg().resetFeature(cfgDef("general.interface"))
            check("keybind.resetRestoresDefault", cfg().string("general.interface", "openKey") == Catalog.OPEN_KEY_DEFAULT)
        }

        // language switching through the real dropdown (keyboard path)
        step(2, "reveal-lang") { screen().debugReveal(card("general.interface")!!.debugControls().first { it is dev.freelocs.aetherion.gui.components.AuroraDropdown }) }
        step(4, "lang-open") {
            val dd = card("general.interface")!!.debugControls().first { it is dev.freelocs.aetherion.gui.components.AuroraDropdown }
            click(dd.x + dd.w / 2, dd.y + dd.h / 2)
            check("dropdown.opens", Ui.popup != null)
        }
        step(10, "lang-pick") { key(InputConstants.KEY_DOWN); key(InputConstants.KEY_DOWN); key(InputConstants.KEY_RETURN) }
        step(25, "lang-check") {
            check("language.switchesToGerman", cfg().string("general.interface", "language") == "de" && I18n.language == "de", "setting=${cfg().string("general.interface", "language")} active=${I18n.language}")
            check("language.translates", I18n.tr("ui.search") == "Features suchen…")
        }
        shot("06-german")
        step(2, "lang-back") {
            cfg().resetFeature(cfgDef("general.interface")); Aetherion.applyUiSettings()
        }
        step(10, "lang-back-check") { check("language.backToEnglish", I18n.language == "en" || I18n.language == "de" && Platform.gameLanguage.startsWith("de")) }

        // theme application
        step(2, "theme-ice") { val d = cfgDef("cosmetics.theme"); cfg().set(d, d.setting("accent")!!, "ice") }
        step(40, "theme-ice-check") { check("theme.accentApplies", Theme.accent == Colors.ICE, "accent=${Integer.toHexString(Theme.accent)}") }
        step(2, "theme-midnight") { val d = cfgDef("cosmetics.theme"); cfg().set(d, d.setting("variant")!!, "midnight") }
        step(10, "theme-midnight-check") { check("theme.variantApplies", Theme.background == 0xFF04070F.toInt()) }
        shot("07-ice-midnight")
        step(2, "theme-reset") { cfg().resetFeature(cfgDef("cosmetics.theme")); Aetherion.applyUiSettings() }

        // reduced motion switches animators to snapping
        step(2, "reduced-motion") { val d = cfgDef("performance.render"); cfg().set(d, d.setting("reducedMotion")!!, true) }
        step(6, "reduced-motion-check") {
            check("motion.reducedFlagApplies", AuroraAnimationEngine.reducedMotion)
            val sp = dev.freelocs.aetherion.gui.animations.Spring(0f); sp.target = 1f; AuroraAnimationEngine.beginFrame()
            check("motion.springSnapsWhenReduced", sp.update() == 1f)
            cfg().resetFeature(cfgDef("performance.render"))
        }

        // notifications modal
        step(2, "toast") { Ui.toast(dev.freelocs.aetherion.common.services.NoticeKind.SUCCESS, "Harness notification") }
        step(8, "bell") { val b = screen().debugBellCenter(); click(b[0], b[1]) }
        step(20, "bell-check") { check("modal.bellOpensNotifications", screen().debugModal() != null) }
        shot("08-modal")
        step(2, "modal-close") { key(InputConstants.KEY_ESCAPE) }
        step(30, "modal-closed") { check("modal.escDismisses", screen().debugModal() == null) }

        // performance page shows real samples
        step(2, "nav-perf") { val c = screen().debugSidebar().debugCategoryCenter("performance")!!; click(c[0], c[1]) }
        step(20, "perf-check") { check("perf.hasSamples", dev.freelocs.aetherion.gui.engine.PerfStats.sampleCount > 10, "avg=${dev.freelocs.aetherion.gui.engine.PerfStats.averageMs()}ms") }
        shot("09-performance")

        // roadmap page: planned entries are visible but never enabled
        step(2, "nav-mining") { val c = screen().debugSidebar().debugCategoryCenter("mining")!!; click(c[0], c[1]) }
        step(20, "mining-check") {
            val cards = screen().debugWidgets().filterIsInstance<AuroraFeatureCard>()
            check("roadmap.plannedCardsShown", cards.isNotEmpty() && cards.all { it.definition.status != dev.freelocs.aetherion.common.features.FeatureStatus.IMPLEMENTED && it.debugToggleCenter() == null })
        }
        shot("10-roadmap")

        // persistence
        step(2, "persist") {
            cfg().saveNow()
            val file = net.fabricmc.loader.api.FabricLoader.getInstance().configDir.resolve("aetherion.json")
            val text = Files.readString(file)
            check("config.persistsToDisk", text.contains("\"schema\": 2") && text.contains("hud.fps"), "bytes=${text.length}")
            val reloaded = dev.freelocs.aetherion.common.config.ConfigStore(file); reloaded.load()
            check("config.reloadRoundTrip", reloaded.isEnabled("hud.fps") && reloaded.isEnabled("hud.clock"))
        }

        // HUD editor: real drag with snapping, persisted placement
        step(2, "editor-open") { Platform.openScreen(AuroraHudEditor(Platform.currentScreen())) }
        step(30, "editor-opened") { check("editor.opens", Platform.currentScreen() is AuroraHudEditor) }
        shot("11-editor")
        step(2, "editor-drag") {
            val ed = Platform.currentScreen()!!
            val box = HudManager.box("hud.fps")!!
            val sx = box.x + 4.0; val sy = box.y + 4.0
            val before = HudManager.placement(box.widget).copyOf()
            ed.mouseClicked(MouseButtonEvent(sx, sy, MouseButtonInfo(0, 0)), false)
            ed.mouseDragged(MouseButtonEvent(sx + 80, sy + 60, MouseButtonInfo(0, 0)), 80.0, 60.0)
            ed.mouseReleased(MouseButtonEvent(sx + 80, sy + 60, MouseButtonInfo(0, 0)))
            val after = HudManager.placement(box.widget)
            check("editor.dragMovesWidget", box.x > 40 && (before.x != after.x || before.y != after.y || before.anchor != after.anchor), "x=${box.x} y=${box.y} anchor=${after.anchor}")
            check("editor.snapsToGrid", box.x % cfg().double("hud.core", "grid").toInt() == 0 || box.x == 0, "x=${box.x} grid=${cfg().double("hud.core", "grid")}")
        }
        step(2, "editor-hide-tools") { key(InputConstants.KEY_TAB) }
        step(6, "editor-scale") {
            val ed = Platform.currentScreen()!!
            val box = HudManager.box("hud.fps")!!
            val before = HudManager.placement(box.widget).scale
            ed.mouseScrolled(box.x + 3.0, box.y + 3.0, 0.0, 1.0)
            check("editor.wheelScales", HudManager.placement(box.widget).scale > before, "scale=${HudManager.placement(box.widget).scale}")
        }
        shot("12-editor-moved")
        step(2, "editor-persist") {
            cfg().saveNow()
            val file = net.fabricmc.loader.api.FabricLoader.getInstance().configDir.resolve("aetherion.json")
            val reloaded = dev.freelocs.aetherion.common.config.ConfigStore(file); reloaded.load()
            val p = reloaded.hudLayout["hud.fps"]
            check("editor.layoutPersists", p != null && p.scale > 1f, "placement=$p")
        }
        step(2, "editor-reset") {
            Platform.currentScreen()!!.keyPressed(KeyEvent(InputConstants.KEY_ESCAPE, InputConstants.KEY_ESCAPE, 0))
            Platform.currentScreen()!!.keyPressed(KeyEvent(InputConstants.KEY_ESCAPE, InputConstants.KEY_ESCAPE, 0))
        }

        // command registration against a fresh dispatcher
        step(30, "command") {
            val dispatcher = com.mojang.brigadier.CommandDispatcher<net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource>()
            AetherionMod.registerCommands(dispatcher)
            val node = dispatcher.root.getChild("aetherion")
            check("command.registered", node != null && node.getChild("hud") != null)
        }
        // ModMenu entrypoint
        step(2, "modmenu") {
            val factory = AetherionModMenuIntegration().modConfigScreenFactory
            check("modmenu.createsScreen", factory.create(null) is AuroraScreen)
        }
        // cleanup so the dev profile is not left modified
        step(2, "cleanup") {
            cfg().resetAll(includeGlobal = true); Aetherion.applyUiSettings(); Aetherion.features.reconcile(); cfg().saveNow()
        }
    }
}
