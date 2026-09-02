# Training log

Running record of the `TRAINING_ALGORITHM.md` loop. Newest entries at the bottom.

Hyperparameters: `WinPct = 60%`, `MaxHypothesisIterations = 10`,
`MaxSolutionsIterations = 10`, `BenchmarkEvery = 3`, `ReproSampleSize = 8`,
`NearMissMargin = 5`, `MaxNearMissRefinements = 3`, `MaxConsecutiveRejects = 3`.

The Gauntlet (loop set, `MAPSET=loop`, 10 maps x both sides = 20 games per
opponent): `tiny closeup keepout knifefight minimaze pipes rift sittingducks
thunderdome whereisthecheese`. `MAPSET=full` uses all 27 maps in
`tools/bc26-maps.txt`.

Infra: builds & games run on GCE VM `battlecode-dev` (Java 21), shared with
the sister `battlecode22-vibe` project (see `CLOUD_DRIVER.md`). Current bot
lives in `src/bot/`; accepted iterations are snapshotted to `src/g_iterN/`
via `tools/snapshot.sh`. `tools/gauntlet.sh` runs a Gauntlet;
`tools/replay-dump.sh` reads a replay back out as a text transcript (Java,
not Python -- see `tools/README.md` for why).

**Convention note (established at Iteration 1, since it wasn't obvious from
`TRAINING_ALGORITHM.md` alone): Iteration 0 is the one exception to the
Step 3 accept/reject gate.** It has nothing to diff against, so it becomes
"the last-accepted iteration" / baseline by construction (Step 1's own
instruction), not by clearing `WinPct`. Every iteration after it goes
through Step 3 normally.

---

## Iteration 0 — environment setup + baseline

**Environment.** New sister project to `battlecode22-vibe`. Cloned
`battlecode26-scaffold` (Java, engine 1.2.6 as of setup), pushed to
`terryvanbelle/battlecode26-vibe` with the scaffold's own history preserved
under the `upstream` remote (same convention as the BC22 project). Installed
JDK 21 (`~/jdk21`, Temurin 21.0.12.1) on the shared `battlecode-dev` VM
alongside BC22's `~/jdk8`. Wrote `TRAINING_ALGORITHM.md` (from-scratch
rewrite for this game, carrying over BC22's process discipline) and
`RULES.md` (working digest of the official spec). Built `tools/gauntlet.sh`,
`tools/vm-match.sh`, `tools/snapshot.sh`, `tools/compare_gauntlets.py`
(ported/adapted from BC22's equivalents) and `tools/bc26_replay.py` (new,
built against the vendored flatbuffers schema from
`github.com/battlecode/battlecode26`).

Verified end to end: `./gradlew build` succeeds on the VM; a headless match
(`examplefuncsplayer` vs itself) runs the full game and produces a valid
`.bc26` replay; the bare-`java`-per-game fast path used by
`tools/gauntlet.sh` (bypassing the Gradle daemon) produces identical results
in ~2s per game instead of Gradle's ~10-30s; `tools/gauntlet.sh` itself run
end to end for a small (4-game) sample.

**Implementation.** A single Rat King (unavoidable — every team starts with
exactly one) spawns exactly one Baby Rat, then does nothing else. The Baby
Rat wanders with random legal moves (move forward if possible, else turn to
a random direction). No cheese collection, no combat, no backstab logic.
Live bytecode-budget monitoring wired in from the start: every robot
compares `rc.getRoundNum()` before/after its own per-turn logic (to catch a
confirmed overrun — the engine pauses mid-instruction and resumes next round
with no exception) and `Clock.getBytecodeNum()` against its type's
bytecode limit (Baby Rat 17500, Rat King 20000, to catch near-misses),
surfaced via `rc.setIndicatorString()` every turn.

Smoke test: `bot` vs `examplefuncsplayer` on `tiny`, both directions — ran to
completion (round 1310, ended by coin-flip tiebreak on tied points) with no
exceptions or bytecode overruns reported. `examplefuncsplayer`'s own
`RobotPlayer.java` doesn't differentiate Rat King vs. Baby Rat logic at all
(just tries to move forward every turn regardless of type) and never calls
`buildRat` — so as of Iteration 0 it functions as a single wandering Rat
King that never grows its population, structurally not too different from
our own minimal bot. Neither side fights cats or collects cheese, so most
games are expected to end in a near-tie coin flip rather than a clean win
— this makes Iteration 0's Gauntlet result mostly a pipeline sanity check,
not a meaningful strength signal.

**Gauntlet (step 2/3).** 7/20 (35%) vs. `examplefuncsplayer`. Every single
game ran to exactly round 1310, decided by the tied-points coin flip
(`WinType.COIN_FLIP` in the schema) -- confirmed mechanistic explanation:
`INITIAL_TEAM_CHEESE=2500`, `RAT_KING_CHEESE_CONSUMPTION=2`/round ->
2500/2=1250 rounds until a team with zero income runs out, then
`RAT_KING_HEALTH_LOSS=10`/round starvation damage against the King's 600 HP
-> 60 more rounds -> 1310 exactly. With neither side collecting cheese or
fighting cats, both Rat Kings starve in near-lockstep and the outcome is
essentially random. Established as baseline per the convention note above
-- not snapshotted into the Gauntlet as an opponent (a random-wanderer
provides no real signal and would qualify for immediate retirement).

---

## Iteration 1 — real economy/combat strategy; accepted after 3 rounds of fixes

**Implementation (first cut).** Rat King grows population (build whenever
affordable, nearest open tile) and self-defends; Baby Rats deliver carried
cheese to the King (tracked via a King-writes/baby-reads shared-array
location, falling back to the freshest directly-sensed King position),
swarm a cat when >=3 allies are within range 8 of it, flee a lone cat
otherwise, retaliate against enemy rats only once `!rc.isCooperation()`
(never initiates a backstab), else collect visible cheese, else explore.
Fixed two BC22-documented anti-patterns from the start: the shared `Random`
is now seeded per-robot from `rc.getID()` (not a fixed shared seed --
BC22's LEARNINGS.md: "a shared, fixed-seed Random instance produces
identical output for corresponding robots on both teams"), and all
movement/tie-break logic is resolved via `MapLocation.directionTo()` or
`Direction.rotateLeft()/rotateRight()` relative to a target, never a fixed
compass fallback or `Direction[]` iteration order.

**Also this iteration:** a fork built `src/pure_cooperator/` and
`src/immediate_defector/` (backstab-policy Gauntlet archetypes called for in
`TRAINING_ALGORITHM.md`'s "Backstab-policy coverage") -- smoke-tested,
committed, not yet added to a Gauntlet run.

**Smoke test (first cut):** lost to `examplefuncsplayer` on `tiny`, round
145, no exceptions. Built `tools/replay-dump.sh` /
`tools/replaydump/ReplayDump.java` specifically to root-cause this (see
`tools/README.md` for the two real wrinkles it took to build -- Struct-typed
union members needing `package com.google.flatbuffers` for protected
access, and `Round.teamAliveRatKings` actually being
`numRatKings + 10*globalCheese` packed together per a comment in the
engine's own `GameWorld.java`). **Finding:** team cheese hit 0 by round
~100 (40 Baby Rats built from a starting 2500, `examplefuncsplayer` -- who
never builds anything -- still had 2300 at the same point) and the King
starved to death 45-60 rounds later. Root cause: the King built whenever
affordable with no regard for whether income was keeping pace.

**Fix 1 — cheese reserve.** Require `globalCheese - buildCost >= 150`
before building (a fixed 75-round grace period of King upkeep, not yet
evidence-tuned). Re-tested on `tiny`: round 145 -> 240 (progress, still a
loss). Full Gauntlet: **2/20 (10%)** -- *worse* than the first cut's
untested guess would suggest, and far below the first cut's own quick
sample. Traced the `knifefight` loss (40x40 map, much bigger than `tiny`):
cheese declined smoothly and *exactly* at the pure-upkeep rate (2/round, 0
income) for the entire game, King starved at round 210 -- and, tellingly,
`findBuildLocation()` had used only **2 distinct tiles** across 40 builds,
both immediately adjacent to the King. Every single Baby Rat stayed alive
the whole game (`aliveBabies=[40,0]` unchanged for 150+ rounds) with zero
cat damage and zero cheese ever collected.

**Diagnosis:** that's not bad luck, it's gridlock. Cramming dozens of Baby
Rats into a small spawn area (this map's name suggests a chokepoint) means
most of them can never leave -- every adjacent tile is already occupied by
another rat -- so nobody reaches cheese regardless of search quality. A
secondary, independently-real issue: `explore()` always continued straight
ahead and only picked a *fresh random* direction when blocked, so a
population that *could* spread out still tends to walk as one front from a
shared default spawn facing rather than fanning out.

**Fix 2 — exploration diversity.** Each Baby Rat now commits once (from
`rc.getID() % 8`, not team-correlated) to a personal preferred heading and
returns to it after being deflected, instead of re-randomizing every time.
Re-tested on `knifefight`: **round 210, unchanged** -- same death round to
the round, proving this alone wasn't the bottleneck (spending is
deterministic once income is truly zero, so the death round is fixed by the
King's build schedule regardless of how Baby Rats wander).

**Fix 3 — population cap.** Added a second, independent throttle:
`builtCount < 15` (cumulative-ever-built, tracked in a King-local static
int -- there's no RobotController API for a live team-wide census, and a
King's own vision, radius^2 25, can't see rats that wandered off; this
undercounts attrition but errs in the *safe* direction for a cap, unlike
BC22's cumulative-vs-live pitfall which was a dangerous direction for a
*floor* check). Re-tested on `knifefight`: **round 210 -> 1200**, a >5x
survival improvement. Cheese still declined smoothly to 0 with zero cat
damage the entire game (no cheese mine or cat was ever found/used on this
map by either fix) -- the population cap didn't fix income, it fixed how
long a zero-income economy can coast on the starting 2500 before the
smaller, non-gridlocked population's lower total upkeep runs it out.

**Full Gauntlet (all 3 fixes together): 14/20 (70%) -- ACCEPT.** Clears
`WinPct=60%`. Losses are clean and concentrated, not scattered noise: both
sides lose on exactly 3 maps (`knifefight` r1200/r1180, `pipes` r1130/r1130,
`whereisthecheese` r1130/r1130 -- both sides *the same round* on the latter
two), everything else won cleanly at r1310 (the coin-flip round, meaning
these wins likely still aren't "real" combat/economy wins yet either --
see Next). Snapshotted as `src/g_iter1/`. This Gauntlet run
(`gauntlet/20260902-004554/`) is the new baseline. Replay checked in:
`replays/iter1_examplefuncsplayer_knifefight_botA.bc26` (the `knifefight`
loss, most illustrative of the still-unsolved "never actually finds
cheese/cats" pattern).

**Functional areas touched this iteration:** Rat King build economy/
throttling, Baby Rat exploration, cheese delivery/shared-array
coordination, cat engagement threshold. **Not yet touched:** cat-damage
generation (still 0 in every traced game -- cooperation-mode scoring
weights this at 0.5, the single largest component), actual cheese-mine
seeking (vision-radius-limited local sensing only, no map memory), traps,
dirt, ratnap/throw, multi-King economies, backstab policy.

**Next.** The 3 losing maps all show the same signature (smooth
zero-income cheese decline, zero cat damage) as the maps that "won" purely
via the round-1310 coin flip -- strong suggestion that *no* map in this
Gauntlet has actually exercised real cheese collection or cat combat yet,
meaning the 70% accept may be measuring "who starves slower," not real
strategic strength. Prefer, as the next Step 4 target: trace a *won* game
(not just a loss) to check this directly -- if wins are coin-flips too, the
next fix (better cheese-mine seeking -- map memory via the shared array
written by the King instead of only ever-forgetting local vision, since
Baby Rats currently have no way to remember or share a cheese sighting once
out of vision) is higher-priority than anything specific to the 3 losses.

---

## Iteration 2 — King dirt-digging; accepted at exactly WinPct

**User instruction this session:** "Once an iteration finishes, I'd like
you to immediately start the next iteration" -- standing rule from here on,
recorded in memory, not just this session.

**Investigating the "Next" lead above.** Re-ran `bot` vs `examplefuncsplayer`
on `closeup` directly (not from a saved Gauntlet replay, since Gauntlet only
keeps losses) -- and it *flipped to a loss* on a repeat run, same round
1310, same `COIN_FLIP` win type. Confirms the suspicion directly: these
aren't stable wins, they're noise. Dumped the replay: `aliveBabies=[0,0]`
for **both teams**, the entire game -- our King never built a single Baby
Rat on this map, ever.

**Root cause (needed a new tool feature to see):** added a terrain
dump to `tools/replaydump/ReplayDump.java` (prints wall/dirt in a radius
around each Rat King's spawn, reading `GameMap.walls()/dirt()` directly,
row-major index `x + width*y` confirmed from the engine's own
`GameMapIO.java`). Both Kings on `closeup` spawn completely boxed in by
**dirt** (impassable until dug -- not a permanent wall). Iteration 1
explicitly never implemented dirt digging (`RobotController.canRemoveDirt`/
`removeDirt`, `DIG_DIRT_CHEESE_COST=5`), so `findBuildLocation()` always
returned `null` and the King just... did nothing, forever, on this map.

**Fix.** When `findBuildLocation()` finds no open build tile, the King digs
the nearest adjacent dirt tile instead (`digTowardOpenSpace()`). Re-tested
on `closeup`: King dug through in round 1-2, started building at round 3,
reached population 7 -- but still lost, `RATKING_DESTROYED` at round 1242
(a *real* loss now, not a coin flip). Traced it: cheese declined smoothly
to 0 with **zero cat damage and zero cheese collected the entire 1200+
round game**, exactly the same "never actually finds anything" signature
as the coin-flip games. So the dig fix is real and mechanistically
confirmed (King builds now, where it categorically couldn't before), but
it exposes rather than solves the deeper problem.

**Full Gauntlet: 12/20 (60%) -- ACCEPT, exactly at `WinPct=60%`.** Diff vs.
the Iteration 1 baseline (`gauntlet/20260902-004554/`): `closeup` flipped
win->loss on both sides -- by "reading a diff's shape" this looks like the
one-directional, single-map pattern the algorithm says to treat as a likely
real regression, *except* it's already understood and intentional: the old
"win" was the coin-flip artifact the dig fix was specifically meant to
replace with real economic activity, and the new result is a real,
consistent `RATKING_DESTROYED` loss, not noise (see Step 3.1's carve-out
for "real-but-already-intentional changes this iteration was specifically
trying to make"). Nothing else changed outcome. Snapshotted as
`src/g_iter2/`; this Gauntlet run (`gauntlet/20260902-011100/`) is the new
baseline. Replay checked in:
`replays/iter2_examplefuncsplayer_closeup_botA.bc26`.

**The real finding, now confirmed across two independent maps
(`knifefight` post-fix, `closeup` post-fix):** a *working*, unblocked,
freely-roaming population of 7-15 Baby Rats still finds **zero cheese and
zero cats over 1000+ rounds**. This is no longer explainable by gridlock or
being boxed in -- both of those are now fixed on the traced maps. The
bottleneck is exploration/search itself: `explore()`'s per-robot preferred
heading (Iteration 1's fix 2) gives *initial* directional diversity but no
map memory, no systematic coverage, and no digging for Baby Rats (only the
King digs) -- a rat that wanders into a *second* ring of dirt farther from
spawn just stops making progress in that direction the same way the King
used to.

**Next.** This is now the dominant, best-evidenced lead and should be
Step 4's target over anything map-specific: either (a) Baby Rats need
dirt-digging too, not just the King, or (b) the search strategy itself
needs real map coverage (e.g. King-relayed "go look over there" via the
shared array, or a systematic sweep instead of a fixed personal heading),
or both. Recommend tracing one more currently-losing map
(`knifefight`/`pipes`/`whereisthecheese`) with the terrain dump before
picking between (a) and (b) -- if they're also dirt-ringed beyond the
spawn pocket, (a) is the higher-leverage fix; if they're open and just
never searched effectively, (b) is.
