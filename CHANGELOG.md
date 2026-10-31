# Changelog

## 0.2.0 - Aurora rebuild (foundation milestone)

Complete reset of the project foundation. Nothing from 0.1.0 other than the mod identity, the build
infrastructure, the LICENSE and the ModMenu/command entry points was kept; 0.1.0 contained only empty
placeholder categories, so no feature was lost.

### Removed
- All placeholder category packages (`analytics`, `hud`, `market`, `notifications`, `waypoints`).
- The old config schema (`categories` map of `enabled` flags), `AetherionTheme`, `GuiUtil` and the
  old `AetherionScreen`. **Zero legacy feature implementations remain.**

### Added
- **Aurora GUI**: responsive panel with an animated sidebar (collapsible groups, favourites, icon rail
  on narrow windows), header (search, profile and context display, notification bell, theme controls),
  dashboard, category, search and performance pages, feature cards with expandable settings, and a
  detail panel (status and why, dependencies in both directions, requirements, compatibility, roadmap facts).
- **Controls**: toggle (spring), slider (interpolated), dropdown (animated, flips near edges), search and
  text fields, keybind capture, HSV colour picker, tabs, scrollbar with smooth scrolling, tooltips,
  modals and toasts, with keyboard navigation and correct focus and input propagation.
- **Design system**: colour, radius, spacing, duration and opacity tokens; Aurora and Midnight surface
  variants; pink, ice and violet accents plus a custom colour.
- **Animation framework**: interruptible spring and tween animators on a frame-time clock,
  reduced-motion mode, and a switch for decorative effects.
- **Rendering layer**: generated nine-slice sprites for anti-aliased rounded shapes, hairline borders and
  soft glow; pixel icons; real backdrop blur through the game's own pipeline with a dimmed fallback.
- **Feature framework**: registry-driven features, typed schema-validated settings, dependencies,
  capability requirements, an idempotent lifecycle manager, crash isolation, and registry validation.
- **Configuration**: profile-aware JSON (schema 2) with atomic debounced saves, legacy-config backup,
  corrupt-file quarantine, favourites, import/export and reset to defaults.
- **HUD**: framework plus three passive widgets (FPS, clock, coordinates), and an interactive layout
  editor with drag, grid snapping, alignment guides, anchors, scale, opacity, layer ordering, presets
  (Default, Corners, Stack), three save slots and live preview.
- **Languages**: English and German, switchable at runtime or following the game language.
- **Roadmap**: 32 planned features across all 15 requested groups with data source, complexity, utility,
  API permissions, compatibility and rule assessment (`docs/ROADMAP.md`). They are visible in the game
  but cannot be enabled.
- **Two-target build**: separate jars for Minecraft 26.2 and 26.3 from one project, with a per-target
  adapter layer for the API differences found between them.
- **Tests**: 62 unit tests and an automated runtime harness (47 checks) that drives a real client.

### Changed
- Target Minecraft moved from 26.1.2 to 26.2 and 26.3; Fabric Loader 0.20.1, Fabric Language Kotlin
  1.14.2 (Kotlin 2.4.21), Fabric API 0.161.0 (26.2) and 0.165.0 (26.3), ModMenu 20.0.3 and 21.0.0.
- Mod metadata now pins each jar to its Minecraft version, and the stale source link was corrected to
  this repository.

### Known limitations
- No SkyBlock gameplay module and no Hypixel API client exist yet; the dashboard says so.
- The planned-feature details (data source, API permission, compatibility, acceptance criteria) are
  English only; titles and descriptions are translated.
- The HUD editor draws no world behind it when opened from the title screen.
- See `docs/TESTING.md` for the full list of what was and was not verified.
