# Aetherion roadmap

> Generated from `src/main/resources/data/aetherion/roadmap.json` by `tools/gen_roadmap_md.py`. Do not edit by hand.

**Nothing on this page is implemented.** These are plans. In the game they appear as *Planned* or *Needs review* entries that cannot be enabled. The only working features today are the framework ones: interface, theme, performance switches, the HUD framework with FPS, clock and coordinates widgets, and the HUD layout editor.

## Rules every feature must satisfy

- Passive, informational or cosmetic only. No automation, macros, packet manipulation or anything that gives a gameplay advantage.
- Never block the render thread: all network access is asynchronous, cached and rate limited.
- Missing or stale API data is shown as unavailable, never guessed.
- Where permissibility is uncertain the entry is marked **review** and stays disabled until a written decision exists.

## Milestones

- **M2** (2 entries): Data services: Hypixel API client (async, cached, rate-limited), profile selection
- **M3** (9 entries): Dashboard, HUD widgets and economy lookups
- **M4** (9 entries): Skill-specific modules (mining, farming, combat, foraging, fishing)
- **M5** (10 entries): Dungeons, Kuudra, Rift, collections, shards, events
- **M6** (2 entries): Items marked for rule review (only after an explicit decision)

## General / Dashboard

### Profile overview  
`general.profile-overview` · planned · milestone M2

Read-only summary of the selected SkyBlock profile: skill average, purse, fairy souls and member list.

| | |
|---|---|
| Data source | Hypixel API (needs a player key) |
| Implementation complexity | Medium |
| Expected utility | High |
| API permissions | Hypixel API key, /v2/skyblock/profiles (read-only) |
| Compatibility | Pure data and UI, identical on 26.2 and 26.3. |
| Hypixel rule assessment | Informational: displays data the player could already see or look up |

Acceptance criteria:

- Shows data within 5 s of opening or a clear 'unavailable' state
- Never blocks the render thread
- Works without a key by showing setup instructions

### API status and rate limits  
`general.api-status` · planned · milestone M2

Shows whether the Hypixel API is reachable and how much of the rate limit Aetherion has used.

| | |
|---|---|
| Data source | Hypixel API (public endpoint) |
| Implementation complexity | Low |
| Expected utility | Medium |
| API permissions | None (public endpoint, own request counter) |
| Compatibility | Pure data and UI, identical on 26.2 and 26.3. |
| Hypixel rule assessment | Informational: displays data the player could already see or look up |

Acceptance criteria:

- Status refreshes at most once per minute
- Rate-limit counter matches response headers

## HUD

### SkyBlock stat widgets  
`hud.stat-widgets` · planned · milestone M3

Movable widgets for stats Hypixel already shows to the player (tab list and sidebar), reformatted.

| | |
|---|---|
| Data source | Local client only |
| Implementation complexity | Medium |
| Expected utility | High |
| API permissions | None (reads text the client already received and displays) |
| Compatibility | Depends on the HUD framework; text parsing is version independent. |
| Hypixel rule assessment | Informational: displays data the player could already see or look up |

Acceptance criteria:

- Only displays values already visible to the player
- Widgets hide when the source line is absent
- Layout persists per profile

### Active effects display  
`hud.effects` · planned · milestone M3

Compact list of active potion effects and their remaining time.

| | |
|---|---|
| Data source | Local client only |
| Implementation complexity | Low |
| Expected utility | Medium |
| API permissions | None |
| Compatibility | Uses vanilla effect data only. |
| Hypixel rule assessment | Informational: displays data the player could already see or look up |

Acceptance criteria:

- Matches vanilla effect timers
- Respects HUD editor layout and opacity

## Mining

### Mining skill progress  
`mining.skill-progress` · planned · milestone M4

Level, XP to next level and progress bar for the Mining skill.

| | |
|---|---|
| Data source | Hypixel API (needs a player key) |
| Implementation complexity | Low |
| Expected utility | Medium |
| API permissions | Hypixel API key (SkyBlock profile read) |
| Compatibility | Identical on both targets. |
| Hypixel rule assessment | Informational: displays data the player could already see or look up |

Acceptance criteria:

- Numbers match the in-game skill menu
- Cached for at least 60 s

### Heart of the Mountain overview  
`mining.hotm-overview` · planned · milestone M4

Tree level, unlocked perks and powder totals at a glance.

| | |
|---|---|
| Data source | Hypixel API (needs a player key) |
| Implementation complexity | Medium |
| Expected utility | Medium |
| API permissions | Hypixel API key (SkyBlock profile read) |
| Compatibility | Identical on both targets. |
| Hypixel rule assessment | Informational: displays data the player could already see or look up |

Acceptance criteria:

- All perk levels match the in-game tree
- Handles profiles without mining data

### Mining Fiesta information  
`mining.fiesta-info` · planned · milestone M5

Shows when the event runs and your own recorded progress. No automation.

| | |
|---|---|
| Data source | SkyBlock calendar (computed locally) |
| Implementation complexity | Low |
| Expected utility | Low |
| API permissions | None (calendar is computed locally) |
| Compatibility | Identical on both targets. |
| Hypixel rule assessment | Informational: displays data the player could already see or look up |

Acceptance criteria:

- Event times match the official calendar
- Progress is read-only

## Farming

### Garden dashboard  
`farming.garden-dashboard` · planned · milestone M4

Garden level, copper, unlocked plots and crop milestones.

| | |
|---|---|
| Data source | Hypixel API (needs a player key) |
| Implementation complexity | Medium |
| Expected utility | Medium |
| API permissions | Hypixel API key (SkyBlock garden read) |
| Compatibility | Identical on both targets. |
| Hypixel rule assessment | Informational: displays data the player could already see or look up |

Acceptance criteria:

- Values match the in-game Garden menu

### Farming event schedule  
`farming.event-schedule` · planned · milestone M5

Upcoming farming contests derived from the SkyBlock calendar.

| | |
|---|---|
| Data source | SkyBlock calendar (computed locally) |
| Implementation complexity | Low |
| Expected utility | Medium |
| API permissions | None |
| Compatibility | Identical on both targets. |
| Hypixel rule assessment | Informational: displays data the player could already see or look up |

Acceptance criteria:

- Next 5 contests match the official calendar

## Combat

### Slayer progress  
`combat.slayer-progress` · planned · milestone M4

XP and boss kill counts per Slayer line.

| | |
|---|---|
| Data source | Hypixel API (needs a player key) |
| Implementation complexity | Low |
| Expected utility | Medium |
| API permissions | Hypixel API key (SkyBlock profile read) |
| Compatibility | Identical on both targets. |
| Hypixel rule assessment | Informational: displays data the player could already see or look up |

Acceptance criteria:

- Totals match the Slayer menu

### Personal combat history  
`combat.history` · planned · milestone M4

Your own combat statistics over time, stored locally.

| | |
|---|---|
| Data source | Local client only |
| Implementation complexity | Medium |
| Expected utility | Low |
| API permissions | None |
| Compatibility | Identical on both targets. |
| Hypixel rule assessment | Informational: displays data the player could already see or look up |

Acceptance criteria:

- History survives restarts
- Can be exported and deleted

## Dungeons

### Dungeon run history  
`dungeons.run-history` · planned · milestone M5

Local log of your finished runs with floor, time and score.

| | |
|---|---|
| Data source | Local client only |
| Implementation complexity | Medium |
| Expected utility | Medium |
| API permissions | None (reads the end-of-run summary shown to the player) |
| Compatibility | Identical on both targets. |
| Hypixel rule assessment | Informational: displays data the player could already see or look up |

Acceptance criteria:

- Each finished run is logged once
- Exportable

### Party information  
`dungeons.party-info` · planned · milestone M5

Public profile progression of your party members.

| | |
|---|---|
| Data source | Hypixel API (needs a player key) |
| Implementation complexity | Medium |
| Expected utility | Medium |
| API permissions | Hypixel API key (SkyBlock profile read) |
| Compatibility | Identical on both targets. |
| Hypixel rule assessment | Informational: displays data the player could already see or look up |

Acceptance criteria:

- Uses only publicly available API data
- Rate-limit aware

## Kuudra

### Kuudra run history  
`kuudra.run-history` · planned · milestone M5

Local log of your own Kuudra runs and completion times.

| | |
|---|---|
| Data source | Local client only |
| Implementation complexity | Medium |
| Expected utility | Medium |
| API permissions | None |
| Compatibility | Identical on both targets. |
| Hypixel rule assessment | Informational: displays data the player could already see or look up |

Acceptance criteria:

- Each run is logged once
- Exportable

### Reward summary  
`kuudra.reward-summary` · planned · milestone M5

Totals of the rewards you received, valued with public Bazaar prices.

| | |
|---|---|
| Data source | Hypixel API (public endpoint) |
| Implementation complexity | Medium |
| Expected utility | Medium |
| API permissions | None (public Bazaar endpoint) |
| Compatibility | Identical on both targets. |
| Hypixel rule assessment | Informational: displays data the player could already see or look up |

Acceptance criteria:

- Prices are timestamped
- Missing prices are shown as unknown, never guessed

## Fishing

### Catch tracker  
`fishing.catch-tracker` · planned · milestone M4

Counts your own catches per session and in total. Passive; no rod automation.

| | |
|---|---|
| Data source | Local client only |
| Implementation complexity | Medium |
| Expected utility | Medium |
| API permissions | None |
| Compatibility | Identical on both targets. |
| Hypixel rule assessment | Informational: displays data the player could already see or look up |

Acceptance criteria:

- Counts match catch messages
- Never interacts with the game

### Fishing collection statistics  
`fishing.collection-stats` · planned · milestone M4

Fishing skill progress and collection tiers.

| | |
|---|---|
| Data source | Hypixel API (needs a player key) |
| Implementation complexity | Low |
| Expected utility | Low |
| API permissions | Hypixel API key (SkyBlock profile read) |
| Compatibility | Identical on both targets. |
| Hypixel rule assessment | Informational: displays data the player could already see or look up |

Acceptance criteria:

- Matches the in-game collection menu

## Foraging

### Foraging progression  
`foraging.progression` · planned · milestone M4

Foraging skill level and XP progress.

| | |
|---|---|
| Data source | Hypixel API (needs a player key) |
| Implementation complexity | Low |
| Expected utility | Medium |
| API permissions | Hypixel API key (SkyBlock profile read) |
| Compatibility | Identical on both targets. |
| Hypixel rule assessment | Informational: displays data the player could already see or look up |

Acceptance criteria:

- Numbers match the skill menu

### Foraging collections  
`foraging.collections` · planned · milestone M4

Collection tiers for all foraging items.

| | |
|---|---|
| Data source | Hypixel API (needs a player key) |
| Implementation complexity | Low |
| Expected utility | Low |
| API permissions | Hypixel API key (SkyBlock profile read) |
| Compatibility | Identical on both targets. |
| Hypixel rule assessment | Informational: displays data the player could already see or look up |

Acceptance criteria:

- Matches the in-game collection menu

## Rift

### Rift progression  
`rift.progression` · planned · milestone M5

Rift profile progress and completed objectives.

| | |
|---|---|
| Data source | Hypixel API (needs a player key) |
| Implementation complexity | Medium |
| Expected utility | Low |
| API permissions | Hypixel API key (SkyBlock profile read) |
| Compatibility | Identical on both targets. |
| Hypixel rule assessment | Informational: displays data the player could already see or look up |

Acceptance criteria:

- Objective list matches the in-game journal

### Rift timer display  
`rift.timers` · needs review · milestone M6

Reformats timers the game already shows. Left disabled until reviewed against server rules.

| | |
|---|---|
| Data source | Local client only |
| Implementation complexity | Low |
| Expected utility | Medium |
| API permissions | None |
| Compatibility | Identical on both targets. |
| Hypixel rule assessment | **Review required:** left disabled until explicitly cleared against server rules |

Acceptance criteria:

- Shows nothing the player cannot already see
- Written rule review exists before enabling

## Economy

### Bazaar prices  
`economy.bazaar` · planned · milestone M3

Public Bazaar buy and sell prices with search.

| | |
|---|---|
| Data source | Hypixel API (public endpoint) |
| Implementation complexity | Low |
| Expected utility | High |
| API permissions | None (public endpoint) |
| Compatibility | Identical on both targets. |
| Hypixel rule assessment | Informational: displays data the player could already see or look up |

Acceptance criteria:

- Prices refresh at most every 60 s
- Stale data is labelled
- Search is case-insensitive

### Auction House lookup  
`economy.auction-lookup` · planned · milestone M3

Look up lowest BIN and recent sales for an item.

| | |
|---|---|
| Data source | Hypixel API (public endpoint) |
| Implementation complexity | Medium |
| Expected utility | High |
| API permissions | None (public endpoint) |
| Compatibility | Identical on both targets. |
| Hypixel rule assessment | Informational: displays data the player could already see or look up |

Acceptance criteria:

- Never blocks the render thread
- Paginated data is cached

### Price history and market analysis  
`economy.price-history` · planned · milestone M3

Locally recorded price history with simple trend analysis. Informational only.

| | |
|---|---|
| Data source | Hypixel API (public endpoint) |
| Implementation complexity | High |
| Expected utility | Medium |
| API permissions | None (derived from public endpoints) |
| Compatibility | Identical on both targets. |
| Hypixel rule assessment | Informational: displays data the player could already see or look up |

Acceptance criteria:

- History is bounded in size
- Charts render without per-frame allocation

## Collections

### Collections and Minions  
`collections.overview` · planned · milestone M5

Collection tiers and crafted minion slots in one overview.

| | |
|---|---|
| Data source | Hypixel API (needs a player key) |
| Implementation complexity | Medium |
| Expected utility | Medium |
| API permissions | Hypixel API key (SkyBlock profile read) |
| Compatibility | Identical on both targets. |
| Hypixel rule assessment | Informational: displays data the player could already see or look up |

Acceptance criteria:

- Matches in-game collection and minion menus

### Museum and Bestiary progress  
`collections.museum` · planned · milestone M5

Museum donations and Bestiary milestones.

| | |
|---|---|
| Data source | Hypixel API (needs a player key) |
| Implementation complexity | Medium |
| Expected utility | Low |
| API permissions | Hypixel API key (SkyBlock museum read) |
| Compatibility | Identical on both targets. |
| Hypixel rule assessment | Informational: displays data the player could already see or look up |

Acceptance criteria:

- Totals match in-game values

## Events

### SkyBlock event schedule  
`events.schedule` · planned · milestone M3

Upcoming official events computed from the SkyBlock calendar.

| | |
|---|---|
| Data source | SkyBlock calendar (computed locally) |
| Implementation complexity | Low |
| Expected utility | High |
| API permissions | None |
| Compatibility | Identical on both targets. |
| Hypixel rule assessment | Informational: displays data the player could already see or look up |

Acceptance criteria:

- Event times match the official calendar for the next 24 h

### Mayor information  
`events.mayor` · planned · milestone M3

Current Mayor, perks and the running election.

| | |
|---|---|
| Data source | Hypixel API (public endpoint) |
| Implementation complexity | Low |
| Expected utility | Medium |
| API permissions | None (public election endpoint) |
| Compatibility | Identical on both targets. |
| Hypixel rule assessment | Informational: displays data the player could already see or look up |

Acceptance criteria:

- Perks match the in-game Mayor menu

### Event reminders  
`events.reminders` · planned · milestone M3

Configurable in-game reminders before events start.

| | |
|---|---|
| Data source | SkyBlock calendar (computed locally) |
| Implementation complexity | Low |
| Expected utility | Medium |
| API permissions | None |
| Compatibility | Identical on both targets. |
| Hypixel rule assessment | Informational: displays data the player could already see or look up |

Acceptance criteria:

- Reminder fires once per event
- Lead time configurable

## Safari / Shards

### Shard catalog  
`shards.catalog` · needs review · milestone M6

Shards by rarity with perk categories. Data source still to be confirmed.

| | |
|---|---|
| Data source | Community data (unconfirmed) |
| Implementation complexity | Medium |
| Expected utility | Medium |
| API permissions | To be determined (no confirmed official endpoint) |
| Compatibility | Identical on both targets. |
| Hypixel rule assessment | **Review required:** left disabled until explicitly cleared against server rules |

Acceptance criteria:

- Official or licensed data source documented before implementation

### Shard checklist  
`shards.checklist` · planned · milestone M5

Manual progress checklist for your own shard collection, stored locally.

| | |
|---|---|
| Data source | Local client only |
| Implementation complexity | Low |
| Expected utility | Medium |
| API permissions | None |
| Compatibility | Identical on both targets. |
| Hypixel rule assessment | Informational: displays data the player could already see or look up |

Acceptance criteria:

- Checklist persists per profile
- Exportable

## Cosmetics

### HUD theme presets  
`cosmetics.hud-presets` · planned · milestone M3

Ready-made combinations of accent, text style and widget background.

| | |
|---|---|
| Data source | Local client only |
| Implementation complexity | Low |
| Expected utility | Low |
| API permissions | None |
| Compatibility | Rendering through the Aurora draw layer on both targets. |
| Hypixel rule assessment | Cosmetic: changes presentation only |

Acceptance criteria:

- Applying a preset is undoable
- Presets are exportable

