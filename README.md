# Aetherion

Client-side Fabric mod scaffold for Hypixel Skyblock (Minecraft 26.1.2), built with Fabric API
and Fabric Language Kotlin. This is **structure only** — no feature logic yet. It sets up:

- a clean Gradle project (`dev.freelocs.aetherion`, mod id `aetherion`),
- one package per planned module (`hud`, `analytics`, `notifications`, `waypoints`, `market`),
  each holding a single placeholder marker class,
- `core/` for mod init, the theme and config persistence,
- `gui/` with a fully styled but content-empty config screen, opened via `/aetherion` or the
  ModMenu config icon — sidebar navigation across the categories above, each showing a
  "Coming soon" placeholder until its module ships.

## Design

Colors, radii and layout sizes come from the Aetherion style guide and live in one place:
`core/AetherionTheme.kt`. Minecraft's 2D renderer has no native rounded-rect or backdrop-blur
primitive, so the mockup's glass/glow look is approximated in `gui/GuiUtil.kt`:
- `fillRounded` draws a real rounded rect (circular corner masking via `GuiGraphics.fill`,
  not a fake corner-cut), so it works correctly over any background.
- `drawGlowBorder` fakes a soft accent glow with a few nested, fading rounded outlines,
  since there's no gaussian blur available without a custom shader.

## Minecraft version note

The brief asked for Minecraft 26.1.2. Checked live against Fabric's version metadata: 26.1.2
*is* an existing, stable release (Fabric Loader, Fabric API `0.155.2+26.1.2` and Fabric Language
Kotlin all publish builds for it), so this project targets it directly — no fallback needed.

## Building

```bash
./gradlew build
```

The output jar is in `build/libs/`.

## Usage

| Command      | Action              |
|--------------|----------------------|
| `/aetherion` | Open the config GUI (also reachable via ModMenu) |

## License

MIT — see [LICENSE](LICENSE)
