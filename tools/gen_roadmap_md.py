#!/usr/bin/env python3
"""Generates docs/ROADMAP.md from src/main/resources/data/aetherion/roadmap.json.

The JSON is the single source of truth: the game reads it to build the in-game Roadmap tabs, and this
script renders the same data as documentation, so the two can never drift apart.
Run from the repo root:  python tools/gen_roadmap_md.py
"""
import io, json, os

SRC = os.path.join("src", "main", "resources", "data", "aetherion", "roadmap.json")
OUT = os.path.join("docs", "ROADMAP.md")

CATEGORIES = [
    ("general", "General / Dashboard"), ("hud", "HUD"), ("mining", "Mining"), ("farming", "Farming"),
    ("combat", "Combat"), ("dungeons", "Dungeons"), ("kuudra", "Kuudra"), ("fishing", "Fishing"),
    ("foraging", "Foraging"), ("rift", "Rift"), ("economy", "Economy"), ("collections", "Collections"),
    ("events", "Events"), ("shards", "Safari / Shards"), ("cosmetics", "Cosmetics"),
]
SOURCES = {
    "hypixel-key": "Hypixel API (needs a player key)", "hypixel-public": "Hypixel API (public endpoint)",
    "client-local": "Local client only", "calendar-computed": "SkyBlock calendar (computed locally)",
    "community-data": "Community data (unconfirmed)",
}
RULES = {
    "informational": "Informational: displays data the player could already see or look up",
    "cosmetic": "Cosmetic: changes presentation only",
    "review": "**Review required:** left disabled until explicitly cleared against server rules",
}


def main():
    data = json.load(io.open(SRC, encoding="utf-8"))
    entries = data["entries"]
    out = []
    w = out.append
    w("# Aetherion roadmap\n")
    w("> Generated from `src/main/resources/data/aetherion/roadmap.json` by `tools/gen_roadmap_md.py`. "
      "Do not edit by hand.\n")
    w("**Nothing on this page is implemented.** These are plans. In the game they appear as *Planned* or *Needs review* "
      "entries that cannot be enabled. The only working features today are the framework ones: interface, theme, "
      "performance switches, the HUD framework with FPS, clock and coordinates widgets, and the HUD layout editor.\n")
    w("## Rules every feature must satisfy\n")
    w("- Passive, informational or cosmetic only. No automation, macros, packet manipulation or anything that gives a gameplay advantage.")
    w("- Never block the render thread: all network access is asynchronous, cached and rate limited.")
    w("- Missing or stale API data is shown as unavailable, never guessed.")
    w("- Where permissibility is uncertain the entry is marked **review** and stays disabled until a written decision exists.\n")
    w("## Milestones\n")
    for k, v in data["milestones"].items():
        count = sum(1 for e in entries if e["milestone"] == k)
        w(f"- **{k}** ({count} entries): {v}")
    w("")
    for cid, title in CATEGORIES:
        rows = [e for e in entries if e["category"] == cid]
        w(f"## {title}\n")
        if not rows:
            w("No planned entries beyond the framework features that already exist.\n")
            continue
        for e in rows:
            status = "needs review" if e["status"] == "review" else "planned"
            w(f"### {e['title']['en']}  \n`{e['id']}` · {status} · milestone {e['milestone']}\n")
            w(f"{e['description']['en']}\n")
            w("| | |")
            w("|---|---|")
            w(f"| Data source | {SOURCES[e['dataSource']]} |")
            w(f"| Implementation complexity | {e['complexity'].capitalize()} |")
            w(f"| Expected utility | {e['utility'].capitalize()} |")
            w(f"| API permissions | {e['apiPermission']} |")
            w(f"| Compatibility | {e['compatibility']} |")
            w(f"| Hypixel rule assessment | {RULES[e['rule']]} |")
            w("")
            w("Acceptance criteria:\n")
            for a in e["acceptance"]:
                w(f"- {a}")
            w("")
    os.makedirs("docs", exist_ok=True)
    io.open(OUT, "w", encoding="utf-8", newline="\n").write("\n".join(out) + "\n")
    print(f"wrote {OUT}: {len(entries)} entries")


if __name__ == "__main__":
    main()
