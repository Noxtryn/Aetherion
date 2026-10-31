# Testing and verification

Everything below was run on one machine: Windows 11, Temurin 25.0.4, Gradle 9.5.1, Loom 1.17.17,
an Intel(R) Graphics device chosen by Minecraft (OpenGL 3.3, driver 32.0.101.7079) on a laptop that also
has an NVIDIA GPU. Nothing was tested on Linux or macOS, on other GPUs, or against a real Hypixel server.

## Builds

`bash tools/build-all.sh` (clean build plus tests, per target):

| Target | Result | Jar | SHA-256 |
|---|---|---|---|
| 26.2 | BUILD SUCCESSFUL | `aetherion-0.2.0+26.2.jar` | `a74564190a5f088ce4c81b5b6c9f6ef3d0fdd99aa0556e17e60e1e9414576678` |
| 26.3 | BUILD SUCCESSFUL | `aetherion-0.2.0+26.3.jar` | `b50c44f17f0e42fb710bda7b87b7432c1dc6f3c93c2f515785002eac1b5d1ff5` |

Checksums are of the jars built from the final tree. Each jar
was inspected: it pins its own Minecraft version (`~26.2` / `~26.3`), contains its own `PlatformInput`
adapter, the sprites, `roadmap.json`, both language files and the LICENSE. The jars themselves were not
loaded in a launcher; the runtime tests below use the Loom dev client, which runs the same classes
through Fabric Loader.

## Unit tests - 62, all passing (identical on both targets)

Settings schema (clamp/snap/reject), registry validation (duplicates, dangling references, cycles,
enabled-but-planned), the shipped catalog and roadmap, config persistence (round trip, defaults not
written, invalid and out-of-range values, legacy v1 backup, corrupt-file quarantine, profiles, global
vs per-profile state, import/export, HUD placement clamping, atomic save), feature lifecycle
(single activation, dependency cascade, subscription release, capability gating, crash isolation,
shutdown), animation behaviour (convergence at any frame time, interruption and reversal without a jump,
once-per-frame stepping, reduced motion), layout invariants over a grid of window sizes, UI state,
icon rectangle cover equals every glyph pixel-for-pixel, colour helpers, English/German key and
placeholder parity, and that every registered text key resolves in both languages.
A mutation check (removing the slider clamp) made the suite fail, so the tests can fail.

## Runtime verification in a real client - 47 checks per target

The dev harness (`-Daetherion.dev=gui`) opens the real screen and drives it through the same input
methods the game calls. Final run: **26.2: 47 passed, 0 failed. 26.3: 47 passed, 0 failed.**

| Area | Checks |
|---|---|
| Startup, command, ModMenu | screen opens; `/aetherion` and `/aetherion hud` registered; ModMenu entrypoint creates the screen |
| Navigation and search | sidebar click changes category; typing filters results; Esc clears |
| Controls | toggle enables/disables and activates/deactivates the feature (and its HUD widget); slider click and right-click reset; dropdown via keyboard; keybind capture, label and reset; card expand |
| Scrolling, resizing | wheel scrolls; forced 200x120 and 1700x950 sizes produce compact and full layouts; restore works |
| Language, theme, motion | German switch translates; auto follows game language; accent and Midnight variant apply; reduced motion snaps animators |
| Overlays | bell opens the notification modal; Esc dismisses it |
| Persistence | config written as schema 2; reloaded store reproduces state |
| HUD editor | opens; drag moves and snaps to the 8-unit grid; wheel scales; layout persists |
| Integrity | registry valid; no planned feature enabled; planned cards have no toggle |

Not automated, checked only by looking at screenshots: color picker popup, tooltips, toasts,
sidebar group collapse animation, and all animation *feel* (springs, transitions). Animation behaviour is
covered by unit tests, but nobody has judged the motion at full frame rate except by reading code.
Screenshots are in `docs/screenshots` (26.3 at 1920x1080, 26.2 at 1280x720 and 854x480, one German).

## Measurements

Taken by the harness on an idle category page, 215-250 frames, via `System.nanoTime` and
`ThreadMXBean.getThreadAllocatedBytes` around the screen's own frame build (not GPU time):

| | 26.2 | 26.3 |
|---|---|---|
| CPU time to build a frame (avg) | 0.49 ms | 0.43 ms |
| Draw calls per frame | 243 | 243 |
| Allocation per frame | about 203 KB | about 208 KB |

Allocation works out to roughly 0.8 KB per draw call, almost all of it Minecraft's own render-state
records, so the useful lever is fewer calls (merging icon rectangles saved about 7%). Aurora's own
per-frame string building was removed (cached label fitting and wrapping). These are CPU numbers on
one machine; no FPS figures are claimed.

## Known problem: Minecraft 26.3 crashes natively on this machine

The 26.3 dev client intermittently dies with a Windows native exit status (`0xC0000005`, `0xC0000409`
or `0xC0000374`), usually right after "Reloading ResourceManager" and occasionally at shutdown. A
controlled experiment, strictly sequential idle launches that only start the game and quit:

| Configuration | Abnormal exits |
|---|---|
| 26.3 with Aetherion | 4 of 6 |
| 26.3 with Aetherion, its sprite resources removed | 3 of 4 |
| 26.2 with Aetherion | 0 of 4 (and 0 of roughly 16 launches during development) |

The failure rate does not depend on Aetherion's sprites and happens before any Aetherion screen is
drawn, which points at 26.3 plus this Intel driver rather than at this mod, but that is an inference:
it is not proven, and I could not get a crash dump (a fast-fail exit writes none). Whether 26.3 is
stable on other hardware is untested. When running the 26.3 harness, retry on a native exit.

## Not verified

- Behaviour on a real Hypixel server (no gameplay features exist; the context chip only reads the server address).
- Mouse input in HUD editor while in a real world; the editor was only run from the title screen.
- Other GUI scales than 2 (and auto at 1080p), other languages than English and German.
- Long-session behaviour, memory growth, and any GPU other than the one above.
