# Battlecode 2026 ("Uneasy Alliances") — rules digest

Digest of the official spec (`specs/specs.pdf` in
[battlecode/battlecode26](https://github.com/battlecode/battlecode26), engine
version 1.2.6 as of this writing — **the spec has a changelog and is still
being revised; re-check it periodically, especially balance numbers**). This
is a working reference, not a replacement for the PDF — when a number here
and the PDF disagree, the PDF (and ultimately the vendored engine source
under `tools/schema/` / `engine/src/main/battlecode/world` in the upstream
`battlecode26` repo) wins.

## Theme and objective

Two teams of robotic **rats** (Baby Rats + one-or-more Rat Kings) coexist
uneasily. NPC **cats** roam the map and attack rats of both teams
indiscriminately. **Collect cheese, stay alive, defeat the cats, and choose
whether to keep cooperating with the enemy team or backstab them.**

A **match** is best-of-3 **games**. Each game is up to **2000 rounds**.

## Cooperation and backstabbing

Every game starts in **cooperation**. A **backstab** is triggered the
instant either team:
1. bites an enemy rat,
2. triggers a trap placed by the enemy team, or
3. ratnaps (kidnaps) an enemy rat.

Backstabbing is immediate and one-way for the rest of that game (resets to
cooperation at the start of the next game in the match). There is no
"un-backstabbing."

**Who gets blamed is not symmetric, and it is not always the actor**
(verified 2026-09-03 against `GameWorld`/`InternalRobot`; this cost real
iterations before it was checked):

| trigger | engine call | team marked as backstabber |
|---|---|---|
| bite a non-cat | `InternalRobot.bite` → `backstab(this.team)` | the **attacker** |
| kidnap an enemy rat | `grabRobot` → `backstab(this.getTeam())` | the **grabber** |
| a rat triggers a rat trap | `GameWorld.triggerTrap` → `backstab(robot.getTeam().opponent())` | the **trap's OWNER** |

So walking into an enemy trap marks *them*, not you — and having your own
trap sprung marks *you*. Only the first trigger of the game counts
(`backstab()` is guarded by `if (this.isCooperation)`).

**Consequence for cat traps:** `catTrapsAllowed(team)` is
`isCooperation || (roundsSinceBackstab <= 100 && backstabber != team)`, so
the team marked as backstabber is **permanently barred from placing cat
traps** while the victim keeps them for 100 more rounds. Laying rat traps
therefore risks forfeiting your own cat-trap access.

**Win conditions:**
- If **all of a team's Rat Kings** die at any point, that team **auto-loses**
  the game immediately (regardless of coop/backstab state) — *unless* both
  teams' last Rat Kings die in the exact same round, in which case the point
  system below decides.
- **Cooperation mode, all cats defeated:** game ends immediately: points
  decide the winner.
- **Backstabbing mode, all cats defeated:** the game does **not** end — teams
  keep fighting each other until one side's Rat Kings are all dead, or round
  2000.
- **Round 2000 reached with both teams still alive:** points decide.

**Scoring** (whichever formula matches the end state):
- Cooperation-mode ending:
  `round(0.5·%dmg_to_cats + 0.3·%living_rat_kings + 0.2·%cheese_transferred)`
- Backstab-mode ending:
  `round(0.3·%dmg_to_cats + 0.5·%living_rat_kings + 0.2·%cheese_transferred)`

  (`%X` = this team's `X` divided by the sum of both teams' `X`; 0 if the sum
  is 0.) Tiebreakers in order: total global cheese, then total rats
  (baby+king) alive, then a uniform coin flip.

**Every term is a SHARE, not an absolute**, and this has a consequence that
is easy to get backwards: `%dmg_to_cats` accrues on *every point of damage
dealt* via `TeamInfo.addDamageToCats`, so **a cat never has to die** for the
damage to count. A cat has 4000 HP against `RAT_BITE_DAMAGE` 10, which makes
killing one look impossible and the whole term look unreachable — it is not.
Measured 2026-09-03: `bench_finalist` earns ~9,700 cat damage per game from
~1,000 ordinary bites and **zero** cat traps, while placing 81 rat traps.
Cat damage is won by biting volume, not by kills and not primarily by traps.

Because the terms are shares, conceding one that is currently close costs up
to its full weight, while conceding one already lost costs almost nothing —
the same change can be right against one opponent and wrong against another.

**Design implication:** a backstab decision is a first-class strategic axis
distinct from anything in Battlecode 2022 — this project has no prior
experience to draw on here and should treat "when (if ever) to backstab" as
its own hypothesis space from the start (see `TRAINING_ALGORITHM.md`).

## Map

20×20 to 60×60, guaranteed symmetric (rotation or horizontal/vertical
reflection — check the actual replay/map metadata per map, don't assume
which). ≤20% walls, ≤50% dirt. Dirt and walls are impassable; dirt (but not
walls) can be dug/placed by rats and cats.

Each robot has a **facing direction** (8-way) and a vision cone centered on
it — **not omnidirectional** except for Rat Kings (360°) — see Units below.
To respect map symmetry, robots have a **chirality** bit that flips their own
sensing/part-iteration order under the map's symmetry; the engine handles
this for its own APIs, but **our own code's tie-breaks are not automatically
symmetric just because the engine's are** — same audit discipline as BC22's
"Play symmetry" bug class applies to any absolute-direction fallback or
first-match-wins logic we write ourselves.

## Resources

- **Cheese** — primary resource. Global (team-wide) or raw (held by one baby
  rat until delivered to a Rat King). Teams start with **2500 global
  cheese**. Cheese mines (even number per map, ≥5 tiles apart) spawn 20
  cheese in their surrounding 9×9 with escalating probability
  `1-(1-0.01)^r` per round since the mine last spawned. **Raw cheese slows
  its carrier**: movement/action cooldown multiplier `0.01 × raw cheese
  held`, i.e. a baby rat hoarding cheese gets sluggish — cheese needs to be
  ferried back to a King promptly, not stockpiled on a Miner-analogue unit.
- **Dirt** — secondary resource; a global per-team stash from digging,
  spendable by any rat to block/unblock tiles. Digging costs 5 cheese;
  placing is free. Cats permanently destroy any dirt they dig (it leaves the
  game).

## Units

All robots: bytecode-limited per turn (paused mid-instruction and resumed
next round on overrun — **use `Clock.yield()`**, don't rely on the limiter to
be a safe stopping point), have movement/turning/action cooldowns that drop
by 10 at the start of every round and must be `< 10` to act.

**Canonical per-type stats** — verified directly against
`engine/src/main/battlecode/common/UnitType.java` (vendored at
`reference/engine-src/UnitType.java`; see `reference/README.md`), since the
PDF spec has at least one internal inconsistency (cat vision, below) worth
not trusting blindly:

| Type | HP | size | vision R² | vision angle | action CD | move CD | bytecode |
|---|---|---|---|---|---|---|---|
| `BABY_RAT` | 100 | 1×1 | 20 | 90° | 10 | 10 | 17500 |
| `RAT_KING` | 600 | 3×3 | 25 | 360° | 10 | 40 | 20000 |
| `CAT` | 4000 | 2×2 | **17** | 180° | 30 | 20 | 17500 (unused — NPC) |

The PDF's "Cats" section states two different cat vision figures (`√17` in
one place, `√30` in another, for what reads like the same cone) — the source
above is unambiguous: `CAT.visionConeRadiusSquared = 17`. Trust `√17`.

- **Baby Rat** — 100 HP, forward move cooldown 10, strafe cooldown 18, turn
  cooldown 10, bytecode limit **17500**. 90°-cone vision, radius `√20`.
  - **Bite**: 8-adjacent enemy rat/king/cat, `10` base damage, `+ceil(√X)`
    for X cheese spent on the bite (raw cheese spent first).
  - **Ratnap/carry**: pick up an adjacent baby rat (ally always; enemy only
    if it's facing away, has less HP, or is allied) and carry it — the
    carried rat is stunned (can't move/act) but immune to attacks except a
    cat pounce/move-into. Drop anytime; auto-drops after 10 rounds. A can't
    re-ratnap a B for 2 turns after B last hit something (if A was the most
    recent carrier of B).
  - **Throw**: launch a carried rat 2 tiles/turn for up to 4 turns (8 tiles
    max); it's impassable and immune while flying, takes 10 HP landing, more
    if it hits an obstacle early. A thrown rat landing on a cat gets eaten
    (cat sleeps 2 turns).
  - **Traps**: rat traps (20 cheese, action CD 15, max 25/team, `√2` trigger
    radius, 50 dmg + 3-turn stun) — enemy-only (own team immune), triggering
    one starts a backstab. Cat traps (10 cheese, action CD 10, max 10/team,
    `√2` radius, 100 dmg + 2-turn stun to a cat) — placeable during
    cooperation, or up to 100 rounds after being backstabbed.
  - **Dig/place dirt**: adjacent only, 5 cheese to dig (action CD 25), free
    to place.
- **Rat King** — one per team at game start (up to 5 total, no new ones after
  round 1200 if a team already has ≥2 living).
  - **Only Rat Kings may write the shared array.** `writeSharedArray` throws
    `CANT_DO_THAT` ("Only rat kings can write to the shared array!") for any
    other type. This is easy to violate accidentally: a Baby Rat calling it
    throws, and if the call sits inside the rat's turn logic the exception
    propagates and **aborts the rest of that rat's turn**, which looks like a
    behaviour change rather than an error. Baby Rats can still *read*. 600 HP start (capped there
  even if formed from more), move CD 40, turn CD 10, bytecode limit
  **20000**. 360° vision, radius `√25`. Consumes 2 cheese/round or takes 10
  damage if it can't. Can attack/place/dig/squeak like a baby rat (attack
  radius `√8` from center, since it's multi-tile). **Only Rat Kings can
  write the shared array** (see Communication) — this is a real coordination
  bottleneck, structurally similar to BC22's single-Archon production
  ceiling: more Kings = more write bandwidth, at the cost of splitting your
  economy and creating more single points of failure.
  - **Formation**: a baby rat with ≥7 allied rats in its surrounding 3×3
    (no impassable tiles, no existing king/cat in that 3×3) can upgrade for
    50 cheese; all rats in the 3×3 are consumed into it (HP summed, capped at
    600; raw cheese contributed to global pool). This is a real
    army-to-infrastructure conversion lever with no BC22 analogue.
  - **Spawning baby rats**: adjacent empty tile, action CD 10, cost
    `10 + 10·floor(live_baby_rats/4)` cheese — spawn cost scales with your
    own population, so a single King's throughput is a soft cap on total
    army size, not just a hard production-rate cap.

## Cats (NPC, not player-controlled — behavior is fully specified)

2×2, 4000 HP, move CD 20 (40 for a pounce), 180° vision cone radius `√17`
(see the stats table above — resolved from engine source, don't trust the
PDF's `√30` mention). Action CD for a scratch is **30**, not the PDF's
stated 15 — the changelog (V1.1.4, "Nerf cats ... 15 -> 30 action
cooldown") shows this was a balance patch the PDF's body text never caught
up to; `UnitType.CAT.actionCooldown = 30` in the current engine is
authoritative. Can't tell rats of different
teams apart. Damage: **pounce** (jump ≤3 tiles, instant-kills anything it
lands on, triggers cat traps in the landing 2×2), **scratch** (20 dmg in
vision cone, action CD 15), or **feeding** (a rat thrown into a cat's square
dies, cat sleeps 2 turns). Moving onto a baby rat's tile also instantly kills
it; cats can't move onto (but can scratch/pounce-kill) a Rat King's tile.

**Algorithm** (this is public, deterministic-ish behavior, not something to
reverse-engineer — read `engine/src/main/battlecode/world/` in the upstream
`battlecode26` repo the way BC22 read `sample_camelcase`'s source directly):
cats cycle symmetric per-map waypoints in **Explore mode** (BFS pathing,
ignore all rats except as pathing obstacles) and switch to an 8-turn
**Attack mode** on reaching each waypoint (squeak-directed facing, hyperfocus
whatever rat is in the cone, scratch > pounce > move-toward > dig-toward, in
that priority). This is fully knowable in advance — a bot can in principle
predict cat positions/behavior without ever sensing them directly, an
information-warfare angle BC22 never had (no NPC third party).

## Communication

- **Global shared array**: 64 ints, range 0–1023. Any robot can **read**
  (`readSharedArray()`); **only Rat Kings can write** (`writeSharedArray()`).
- **Squeak**: any rat can broadcast one int/turn (`squeak(int)`) to allied
  rats *and cats* within `√16` radius — cats hear it too and will turn toward
  the source in Attack mode. Squeaking is a real double-edged signal:
  coordination bandwidth vs. "please come kill me" for any cat nearby in
  Attack mode. Messages persist 5 rounds for late readers; you never hear
  your own squeak.

## Bytecode / restrictions

Baby Rat bytecode limit **17500**, Rat King **20000** (comparable order of
magnitude to BC22's Archon 20000). `Clock.getBytecodeNum()` /
`RobotType` metadata give live budget info — carry over BC22's Iteration-0
lesson: **wire up live bytecode-budget monitoring from the start**, don't
bolt it on later. Exceptions cost 500 bytecodes and can paralyze a robot if
unhandled. 8MB heap cap; violating `AllowedPackages`/`DisallowedPackages` or
the extra `java.lang`/`Object` restrictions causes the robot to **explode
even if it compiles** — check `AllowedPackages.txt`/`DisallowedPackages.txt`
(linked from the spec) before using anything unusual.

## Crossplay

Python bots are supported this year (bytecode ×3 to approximate Java cost),
but the spec itself recommends Java for anyone optimizing bytecode, and our
tooling/process assumes Java (matching the BC22 project). Not revisiting this
choice unless a strong reason emerges.
