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

---

## Iteration 3 — fixed a permanent-stuck bug in cheese/delivery/engage; accepted (mechanistic, no benchmark flip)

**User instruction, standing from here on:** "Once an iteration finishes,
I'd like you to immediately start the next iteration." Recorded in memory.

**Investigating the Iteration 2 "Next" lead.** Added `--robot <id>` to
`tools/replaydump/ReplayDump.java` (prints one robot's `x/y/dir/health/
cheese/moveCooldown/turningCooldown` every round it acts) and tracked three
independently-spawned Baby Rats on the `knifefight` loss. **All three got
permanently stuck** at a fixed `(x,y)` after ~15-60 initial rounds of real
movement -- `moveCooldown=0, turningCooldown=0` (fully able to act) every
single round, yet position and facing never changed again for hundreds of
rounds straight (one case: stuck from round ~34 to round ~274, then
suddenly moved once). That "occasionally unsticks" pattern ruled out a
pure-luck explanation (0.75^240 consecutive-miss probability if it were
really a 25%-per-round random retry) and pointed at a deterministic logic
bug instead.

**Root cause.** `collectCheese()` (and, same shape, `deliverCheese()` and
`engage()`) unconditionally returned `true` once *any* target was
identified -- a sighted cheese tile, the King's location, a cat -- even
when the single-step `moveToward()` call right before it completely failed
to move. Since `runBabyRat()` treats a `true` return as "handled this
turn, don't fall through," a robot that ever sighted an unreachable target
(behind an obstacle its naive `directionTo()`-based routing can't route
around) would re-select the *same* unreachable target every subsequent
round, forever, and `explore()` would never run again for that robot.

**Fix.** `moveToward()` now returns whether it actually moved (delegates
`tryMove()`'s result); `collectCheese()`/`deliverCheese()`/`engage()` now
return that result instead of an unconditional `true`, so a blocked path
falls through to the next priority (ultimately `explore()`) the very next
call instead of camping forever.

**Verification.** Re-traced the same three robot IDs on a fresh
`knifefight` run: all now move and turn continuously for the full 1200+
round game -- the permanent-freeze pattern is gone, mechanistically
confirmed via direct before/after position tracking (Step 6.4.2's
"demonstrably engaged and produced the behavior change it was designed to
produce" standard). **Full Gauntlet: 12/20 (60%), identical losing maps at
essentially identical rounds** (`knifefight` 1200->1230, `closeup`
1242->1242 exactly, `pipes`/`whereisthecheese` unchanged) -- no regression,
but no benchmark movement either. Expected: `examplefuncsplayer` never
fights or builds, so a zero-cheese-income economy starves the same way
whether the Baby Rats are frozen in place or wandering uselessly nearby --
this fix's value won't show up against *this* opponent regardless of how
real and important it is. **ACCEPT** on mechanistic grounds per Step 6.4.2,
same as several of BC22's best-verified iterations took this path.
Snapshotted as `src/g_iter3/`; new baseline `gauntlet/20260902-012034/`.
Replay: `replays/iter3_examplefuncsplayer_knifefight_botA.bc26`.

**Bonus finding while verifying:** the now-unstuck rat still only wanders
within a small ~5x8 pocket the entire 1200-round game, never reaching any
of the map's 6 cheese mines (nearest one ~10 tiles from spawn). Added
`--terrain X,Y` to the replay tool (was previously hardcoded to King spawn
locations only) and rendered the pocket directly: it's a real wall/dirt
chokepoint (`knifefight`'s namesake, presumably) with **dirt tiles the rat
could dig through to escape, but Baby Rats have no digging code at all** --
only the King digs, from Iteration 2, and only when finding zero build
tiles specifically. This directly answers Iteration 2's open (a)-vs-(b)
question: it's (a), not (b) -- confirmed by direct visual terrain evidence,
not inference.

**Next.** Give Baby Rats the same dirt-digging capability as the King
(generalize `digTowardOpenSpace()` rather than duplicating it), gated on
being stuck/blocked rather than run unconditionally every turn (a King-style
"no build location -> dig" trigger doesn't apply to Baby Rats, who have
other things to do most turns -- likely trigger: `explore()`'s fallback
path, when even the random-direction attempt fails, try digging an
adjacent dirt tile instead of giving up for the turn).

---

## Iteration 4 — Baby Rat digging; 100% vs examplefuncsplayer, real economy confirmed

**Implementation.** `explore()`'s existing King-only `digTowardOpenSpace()`
(Iteration 2) is already unit-type-generic (plain `RobotController` calls)
-- just needed calling from the right place. Added it as `explore()`'s
final fallback: preferred heading blocked, forward blocked, a fresh random
direction also blocked -> dig the nearest adjacent dirt tile instead of
doing nothing.

**Smoke test:** `knifefight` -- **won for the first time**, `RATKING_DESTROYED`
(not a coin flip). Team cheese: 2185 at round 100 -> **5995 at round
1200**, real sustained positive income far exceeding the 2500 starting
amount, while `examplefuncsplayer` (never builds) declined the whole game
as always. This is the first genuinely functioning cheese-collection loop
this project has produced.

**Full Gauntlet: 20/20 (100%).** Complete sweep, every map, both sides.
Snapshotted as `src/g_iter4/`; new baseline `gauntlet/20260902-012436/`.
Replay: `replays/iter4_examplefuncsplayer_knifefight_botA.bc26` (the first
real, non-coin-flip `knifefight` win).

**Gauntlet pool decision.** `examplefuncsplayer` is now at 100% (one
Gauntlet short of the two-consecutive-≥80% retirement threshold -- see
"Retiring bots from the Gauntlet"). Per "Growing the Gauntlet" /
"Backstab-policy coverage", adding `pure_cooperator` and
`immediate_defector` (built by a fork earlier this session, smoke-tested
but never added to a real Gauntlet run) as opponents now, rather than
waiting for `examplefuncsplayer` to fully retire first -- `bot` has never
actually been tested against an opponent with a real economy or that
fights back, and both archetypes share Iteration 1-4's economy/combat code
so this is a clean first read on backstab-policy behavior specifically,
not a confound with base competence.

**Still open** (unchanged from Iteration 2/3's findings, not yet directly
addressed): zero cat damage in every game traced so far. Once a real
opponent forces longer/different games this may surface on its own; if
not, it's the next dedicated target.

---

## Gauntlet pool update — synced archetypes, retired examplefuncsplayer

Not a `src/bot/` code change (no new iteration), so no accept/reject
decision here -- this is "Growing/retiring the Gauntlet" (TRAINING_ALGORITHM.md).

**Kept archetypes in sync.** First real test of `pure_cooperator`/
`immediate_defector` (`OPPONENTS="pure_cooperator immediate_defector"
tools/gauntlet.sh`) landed at 80%/100% -- suspiciously easy. Reading the
source showed why: both were frozen at Iteration 1's *very first* cut,
before any of Iterations 2-4's fixes (no cheese reserve, no population
cap, no King dirt-digging, no fix for the permanent-stuck bug, no
exploration fan-out). They'd have inherited the same starvation/gridlock/
freeze failures `bot` no longer has, which would dominate the result and
say nothing about backstab policy specifically -- the whole point of
these archetypes. Rewrote both to share `src/bot/`'s current economy/
search/movement code exactly, keeping only their distinctive policy logic
(pure_cooperator: no retaliation clause, ever; immediate_defector: attacks
on sight from turn 1 + stays leashed near its King). Re-ran:
**`pure_cooperator` 50%, `immediate_defector` 75%** -- both now genuine
peers (30-90% range), `pure_cooperator` especially: many games run the
full 2000 rounds to the points cap rather than a kill, which never
happened once before this session (every prior game ended by starvation,
coin flip, or `RATKING_DESTROYED` well under round 1300).

**Full Gauntlet with the updated pool (`gauntlet/20260902-013402/`):
45/60 (75%) overall -- `examplefuncsplayer` 20/20 (100%), `pure_cooperator`
10/20 (50%), `immediate_defector` 15/20 (75%).**

**Retirement.** `examplefuncsplayer` has now hit 100% in two consecutive
Gauntlets it appeared in (`gauntlet/20260902-012436/` and this one) --
meets the ≥80%-in-two-consecutive-Gauntlets retirement rule. Removed from
`tools/gauntlet.sh`'s default `OPPONENTS`. `pure_cooperator` and
`immediate_defector` are the standing peer roster going forward (both
30-90%, no benchmark opponents exist yet -- nothing has beaten `bot` even
once this session).

**Next (the real Step 4 target now):** pick a losing game against
`pure_cooperator` -- it's the hardest, most informative opponent available
and the only one currently forcing full 2000-round games decided by
points. `keepout`/`knifefight`/`minimaze`/`sittingducks`/`rift`/
`thunderdome` all lost this way; worth tracing one to see which of the
three point components (cat damage %, living-rat-king %, cheese-transferred
%) is actually costing the game, since "zero cat damage" has been a
constant across every replay traced all session and a competent-economy
mirror match is the first real chance to see whether that alone explains a
loss.

---

## Iteration 5 — Baby Rats take a free hit on a cat already in range; accepted, 67.5%

**Diagnosis.** Traced `pure_cooperator__keepout__botA.bc26` (a full
2000-round, `MORE_POINTS` loss). Added `teamCheeseTransferred` to the
replay tool's per-round summary (was tracking current `globalCheese`
before, a different quantity from the scoring formula's cumulative
`%cheese_transferred`). Both sides: `catDamage=[0,0]`, rat kings tied 1-1
-- with the cat-damage sum at 0, that whole 0.5-weighted component
contributes equally (0) to both sides per RULES.md's "0 if the sum is 0",
so this **entire game, and by extension every close game this session,
reduces to cheese-transferred % alone**, the smallest-weighted component
(0.2) in cooperation-mode scoring.

**Since `bot` and `pure_cooperator` run identical code when neither
backstabs** (confirmed after this session's archetype sync), a 50/50
aggregate split across the roster is exactly `TRAINING_ALGORITHM.md`'s
"Play symmetry" mirror-match check succeeding, not a strategic weakness --
worth recording as a positive symmetry-audit result, not chasing further.

**The real finding:** grepped the full `keepout` replay for cat-attack
actions -- **17 `CatScratch`/`DieAction` events**, our own rats dying to
cats repeatedly the entire game, while `catDamage` never left `[0,0]`.
Cats are being encountered constantly; we were never landing a single hit
back. Root cause: `runBabyRat()`'s cat-response logic only ever called
`engage()` when `>=3` allies were within range 8 of the cat, and `flee()`
otherwise -- a lone rat *already in bite range* of a cat that's about to
hit it anyway still fled instead of attacking, wasting the one turn where
damage was free (the cat isn't going to skip its attack because we
didn't take ours).

**Fix.** Check `rc.canAttack(nearestCat.getLocation())` before the
ally-count gate; if already in range, attack regardless of ally count,
then fall through to the existing swarm/flee logic only if not. Re-tested
`bot` vs `pure_cooperator` on `keepout`: **`catDamage=[20,0]`** -- first
nonzero cat damage this entire session -- and the game flipped from a
loss to a win, since a 100%-vs-0% cat-damage split is worth the full 0.5
weight even at just 20 raw damage (out of 4000 HP).

**Full Gauntlet: 27/40 (67.5%)**, vs. the pool-update baseline's
`pure_cooperator 50%` / `immediate_defector 75%` (62.5% combined):
**`pure_cooperator` 50%->60%**, `immediate_defector` unchanged at 75%.
Diff by shape: mostly one-directional (loss->win) with one isolated
opposite flip (`closeup` bot=B, pure_cooperator) -- consistent with the
"mirror-match games are chaos-sensitive" pattern already documented, not a
regression. **ACCEPT.** Snapshotted as `src/g_iter5/`; new baseline
`gauntlet/20260902-014054/`. Replay:
`replays/iter5_pure_cooperator_keepout_botA.bc26`.

**Next.** 20 damage is a token amount against a 4000 HP cat -- this fix
stops the *bleeding* (a free hit when already exposed) but doesn't make
cat damage a real offensive strategy. The swarm-engage threshold
(`allies >= 3` within range 8) is still probably too strict given rats are
deliberately spread out for cheese search most of the time (Iteration
1-4's exploration fixes) -- worth checking whether it ever actually fires,
and whether a lower threshold or an explicit "rally toward a spotted cat"
signal (shared array, King-relayed) would let cat damage become a real
scoring lever instead of an incidental one.

### Progress charts

Ported `tools/plot_progress.py` and `tools/plot_alt_metrics.py` from
`battlecode22-vibe`, trimmed to this project's current scale (no
benchmark-opponent overlay yet -- nothing has beaten `bot` even once this
session; no `MIN_PEERS=14` gate -- this project has run at most 3
opponents in a single Gauntlet so far, set to 2 instead). `tools/
plot_vs_old_bots.py` intentionally not ported yet, per
`TRAINING_ALGORITHM.md`'s own stated policy: not worth it before there's a
roster of retired old snapshots to track, expected around the 10th
accepted iteration.

`progress/cumulative_iterations.png`: 5 accepted iterations, all within
about 90 minutes (one continuous session) -- steps at each accept, with
the `examplefuncsplayer` retirement marked. `progress/peer_win_spread.png`:
a clear V-shape -- starts high (80-100%) while the peer roster still had
BC22-style easy/undertuned reference bots, drops to 50-75% once
`pure_cooperator`/`immediate_defector` were synced to `bot`'s actual
economy (a *harder*, fairer roster, not a regression -- see the "Gauntlet
pool update" entry above), then partially recovers after Iteration 5's
cat-damage fix. Regenerate either with `tools/.venv/bin/python3
tools/plot_progress.py` / `tools/plot_alt_metrics.py`; commit updated PNGs
periodically, not after every single iteration (same casual cadence BC22
settled on).

---

## Iteration 6 — engage cats unconditionally instead of fleeing below 3 allies; accepted, 70%

**High-risk structural change** (TRAINING_ALGORITHM.md's "High-risk
structural exploration" -- picked as a first-class move, not a last
resort, given Iteration 5's fix only ever produced token cat damage).
Checked whether the `>=3`-ally swarm-engage threshold from Iteration 1
ever actually fired: grepped two more full replays (`sittingducks`,
`whereisthecheese` losses) for `RatAttack` events -- **zero**, despite 10
cat-attack events each. Rats deliberately spread out for cheese search
(Iteration 1-4's own fixes), so 3 of them converging on one cat at the
same moment was luck, not policy.

**Reasoned about the underlying combat math directly** (not evidence yet,
a hypothesis to verify): `CAT_SCRATCH_DAMAGE=20` every ~3 rounds
(`actionCooldown=30`) is ~6.67 dmg/round average; `RAT_BITE_DAMAGE=10`
every round (`actionCooldown=10`, always ready) is 10 dmg/round -- a lone
Baby Rat that reaches bite range actually out-trades a cat on DPS, even
though it obviously can't out-tank one (100 HP vs. 4000). Also: a cat's
scratch reaches its whole vision cone (radius² 17, ~4.1 tiles) but a Baby
Rat's bite only reaches range² 2 (~1.4 tiles) -- the old "flee anything
within 8" threshold kept a fleeing rat inside the cat's engagement range
the whole time it was trying to escape, without ever closing to bite
range either. Worst of both outcomes.

**Fix.** Removed the ally-count gate. A Baby Rat now engages (moves
toward + attacks) any cat within range 8 unless it's both critically low
HP (`<=30`) *and* has no ally nearby, in which case it still flees (not
worth dying on the approach for a hit that likely never lands).

**Smoke test:** `sittingducks` -- previously a loss, now a decisive win.
`catDamage=[350,60]` (vs. `[0,0]` baseline-wide all session), despite
losing far more Baby Rats than the opponent (`aliveBabies=[6,13]` at
round 2000) -- confirms the trade is working as reasoned: cheap units for
real cat damage, not previously-existing.

**Full Gauntlet: 28/40 (70%)**, up from 27/40 (67.5%). `pure_cooperator`
60%->65%, `immediate_defector` unchanged at 75% in aggregate but with a
different losing-map set (`closeup` newly lost both sides, `keepout`/
`sittingducks` newly won). Diff by shape: mixed-direction, no single
map/side concentrated across both opponents -- reads as the expected
chaos-sensitivity of an aggressiveness change interacting differently
with each map's specific cat/spawn geometry, not a systematic regression,
and the net movement is positive. **ACCEPT.** Snapshotted as
`src/g_iter6/`; new baseline `gauntlet/20260902-015534/`. Replay:
`replays/iter6_pure_cooperator_sittingducks_botA.bc26`.

Regenerated `progress/cumulative_iterations.png` and
`progress/peer_win_spread.png` (6 accepted iterations now).

**Next.** `closeup` is now a fresh, concentrated weak spot against
`immediate_defector` specifically (lost both sides, previously split) --
worth tracing given it's the map with the dirt-boxed-King mechanic
(Iteration 2); possible interaction between digging out and the newly
more-aggressive cat policy competing for the same early turns. Separately,
Baby Rats still never *approach* a cat proactively (only within range 8,
i.e. already fairly close) -- extending engagement range, or exploiting
that cats are deterministic/predictable (RULES.md: waypoint cycling,
~8-round Attack-mode windows) to approach safely during a cat's harmless
Explore-mode phase, remains unexplored and is probably the next real lever
once this round of tuning settles.

---

## Iteration 7 — chase a fleeing/distant enemy rat post-backstab; accepted (mechanistic, no aggregate flip)

**Traced the `closeup` vs. `immediate_defector` loss directly** (the fresh
weak spot Iteration 6 flagged). Both Kings dug out fine and built normally
(economy itself healthy) -- the actual cause: `aliveBabies` for `bot`
dropped to **0 by round ~800** (all Baby Rats killed by
`immediate_defector`'s always-hostile rats, which actively hunt) while
`immediate_defector` still had 5+ alive; the King then starved alone with
no economy left, dying at round 1318. Root cause: post-backstab
retaliation (`!rc.isCooperation()`) only ever attacked an enemy rat
already in bite range -- a Baby Rat that could *see* a hostile enemy but
wasn't yet adjacent just continued its normal cheese-priority behavior,
never closing distance. Purely passive defense against an opponent that
actively pursues.

**Fix.** Chase like cat-engagement already does: if a hostile enemy rat is
sighted within range 8 but not yet attackable, move toward it instead of
ignoring it.

**Smoke test:** `closeup` vs. `immediate_defector` -- still a loss
(round 1318 -> 1328, barely moved), but the underlying mechanism improved
measurably: `catDamage` 540->690 (chasing enemies evidently doesn't come
at the cost of cat engagement) and the population wipeout point moved from
round ~800 to round ~1200 (`aliveBabies` still hits 0 eventually, but
survives ~400 rounds longer). **Full Gauntlet: 28/40 (70%), unchanged in
aggregate** -- `pure_cooperator` identical loss set; `immediate_defector`
same 5 losses by count with one flip each direction (`tiny` bot=A: loss->win;
`whereisthecheese` bot=A: win->loss), reading as noise, not regression.

**ACCEPT on mechanistic grounds** (Step 6.4.2, same basis as Iteration 3):
demonstrably engaged as designed (measurable cat-damage and survival-time
improvement) with no unresolved regression, even without an aggregate
Gauntlet flip. Snapshotted as `src/g_iter7/`; new baseline
`gauntlet/20260902-020332/`. Replay:
`replays/iter7_immediate_defector_closeup_botA.bc26`.

**Next.** `bot` still has no organized response to a sustained hunt --
each Baby Rat reacts individually with no coordination (no rally point, no
retreat-to-King-for-safety-in-numbers), so a persistent aggressor can still
attrit the population down one rat at a time even though each individual
fight is now winnable. Worth checking whether Baby Rats retreating toward
the King when threatened (concentrating defenders near where the King can
also help fight, per `attackNearestHostile`) would end the slow bleed
`closeup` still shows.

### Win % vs. a fixed old-bot roster

Prompted directly by the user, who pointed out that `g_iter1` was already
available as a fixed comparison point rather than waiting for
`TRAINING_ALGORITHM.md`'s own stated "~10th accepted iteration" threshold
(that guidance was written for BC22's much larger scale, where the peer
Gauntlet's retirement churn made a fixed yardstick necessary much later
into the project -- no reason to withhold the same idea early just because
the specific number "10" hadn't been hit). Ported `tools/track_vs_old_bots.py`
/ `tools/plot_vs_old_bots.py` from `battlecode22-vibe`, started tracking
`g_iter1` now instead:

```
OPPONENTS="g_iter1" MAPSET=loop tools/gauntlet.sh   # gauntlet/20260902-020745/
tools/.venv/bin/python3 tools/track_vs_old_bots.py gauntlet/20260902-020745/
tools/.venv/bin/python3 tools/plot_vs_old_bots.py
```

**90% vs. `g_iter1`** (18/20; both losses on `tiny`) -- `g_iter7`'s
absolute-strength read on how far the bot has come since the very first
accepted iteration (which itself only beat `examplefuncsplayer`, a
non-adversarial reference, 70%). First data point in
`progress/vs_old_bots_history.csv`/`progress/vs_old_bots.png`; the chart
is understandably sparse with one point (a single marker, odd wide
default x-axis) -- will become genuinely useful as future sessions add
more checks. Add further old snapshots to the tracked roster as the
project grows (BC22's cadence was every 10th accepted iteration; this
project can pick its own once "every 10th" means something at this
scale) -- append, don't replace `g_iter1`.

---

## Iteration 8 attempt — retreat-when-wounded during a rat chase; REJECTED (didn't engage)

Targeting the Iteration 7 "Next" note directly: gave a Baby Rat chasing a
hostile enemy rat (post-backstab) the same low-HP bail-out the cat-engage
logic already has -- below 30 HP, retreat toward the King instead of
continuing to close distance.

**Smoke test:** re-ran the exact `closeup` vs. `immediate_defector` loss
Iteration 7 traced -- **byte-identical result** (round 1328, same
`catDamage`/`aliveBabies` progression at every checkpoint). No evidence
the change ever fired in this game.

**Full Gauntlet: 27/40 (67.5%), down from 28/40 (70%).** `pure_cooperator`
unchanged (identical loss set -- expected, it never backstabs, so this
code path never runs against it at all). `immediate_defector` gained one
new loss (`knifefight` bot=A, previously a win). Traced it directly
(Step 6.4.3): `catDamage=[0,0]` the entire game, no combat evidence at
all -- a pure economic race (cheese-transferred %, close the whole game)
that happened to go the other way this time, on `knifefight`, a map
already flagged as chaos-sensitive. Not caused by this change; the
retaliation code path this iteration touched was never exercised in that
game either.

**REJECT** -- no evidence the fix ever engaged anywhere in this Gauntlet
(Step 6.4.3's third outcome: "no evidence the fix changed anything, or it
demonstrably didn't engage"), and the one aggregate flip is independently
explained as unrelated noise. Reverted.

**Diagnosis for next time:** `RAT_BITE_DAMAGE=10` against 100 HP means a
rat needs ~7 hits to reach the 30-HP bail-out threshold -- in practice,
1v1-ish skirmishes against a dedicated aggressor (`immediate_defector`)
apparently resolve faster than that (a rat wins, loses outright, or the
encounter ends before HP drops that far), so an individual-HP-threshold
retreat rarely has a chance to matter. A group-level response (regroup
near the King *before* individual rats are already low, or the King
itself sallying out to help) is more likely to be the real lever than a
per-robot panic threshold -- `closeup`'s population-bleed problem is
still open.

---

## Iteration 9 attempt — retreat when locally outnumbered; REJECTED (also never engaged)

Different signal than Iteration 8's rejected HP threshold: instead of
reacting to accumulated damage, retreat toward the King the instant a
Baby Rat is outnumbered nearby (`countEnemyRatsNear > countAlliesNear`
within range 8), an immediately-assessable condition rather than one that
needs several rounds of taking hits first.

**Smoke test:** same `closeup` vs. `immediate_defector` game -- again
byte-identical (round 1328, identical checkpoints). **Full Gauntlet:
28/40 (70%), and every single game -- not just the aggregate -- identical
to the `g_iter7` baseline** (`gauntlet/20260902-020332/`): same maps, same
sides, same rounds, zero diffs anywhere. Stronger evidence than
Iteration 8's rejection that this code path never fires at all, not just
in the motivating case.

**REJECT** (Step 6.4.3, no engagement anywhere). Reverted.

**Refined diagnosis:** two different retreat signals in a row (damage
accumulated, local numerical disadvantage) both turned out inert across
the entire Gauntlet. The common thread: combat encounters here are
apparently almost always 1-on-1 -- both `pure_cooperator` and
`immediate_defector`'s own Baby Rats explore/hunt independently rather
than in coordinated groups (this project's own bot does too, by design,
since Iteration 1-4's exploration fixes deliberately spread the
population out), so "locally outnumbered" essentially never arises from
either side. This reframes `closeup`'s population bleed as a
**cumulative-attrition** problem (a long series of individually-fair 1v1
fights, some won some lost, netting out to slow decline) rather than a
tactical-retreat problem -- no amount of smarter fight/flee logic fixes a
attrition race if the *replacement rate* can't keep up. Next: raise
`MAX_POPULATION` (currently a flat 15, set in Iteration 2 purely to solve
`knifefight`'s spawn-gridlock problem, never revisited since) -- `closeup`
traces showed a healthy, competitive economy (`cheeseTransferred` roughly
even) that could plausibly sustain a larger standing population if the
cap weren't holding it back.

---

## Iteration 10 — raise MAX_POPULATION 15->25; accepted, 72.5%

Following directly from Iteration 9's refined diagnosis: raised
`MAX_POPULATION` from 15 (picked in Iteration 2 purely to solve
`knifefight`'s spawn gridlock, never revisited) to 25.

**Verified the gridlock risk first**, since that's specifically what 15
was chosen to prevent: `bot` vs. `pure_cooperator` on `knifefight` --
won on points at round 2000, `aliveBabies=[12,13]`, `catDamage=1090` for
us. No sign of the old gridlock (both sides fielded healthy, active
populations well past the old cap). `closeup` vs. `immediate_defector`
smoke test: still a loss, and actually died a little *faster* (r1328 ->
r1270) in this single sample -- not a promising sign in isolation, but
single-game round count is a noisy signal on its own (see "reading a
diff's shape"), so went to the full Gauntlet rather than reading too much
into one game.

**Full Gauntlet: 29/40 (72.5%), up from 28/40 (70%).** `pure_cooperator`
65%->75% (`rift` bot=B and `tiny` bot=B both flipped to wins);
`immediate_defector` 75%->70% (one new loss, `tiny` bot=A). Net +1 win.
Round counts dropped noticeably across the board (many games now resolve
in the 900-1300 range instead of drifting to 2000) -- larger standing
armies clash and resolve faster, consistent with the population-cap
theory. `knifefight` doesn't appear in the losses list at all -- won both
games against both opponents, confirming the raised cap didn't
reintroduce the gridlock it was originally set to prevent. **ACCEPT.**
Snapshotted as `src/g_iter8/` (the two rejected attempts, Iterations 8-9,
don't get snapshots -- see the vs-old-bots convention note this session
already established: `g_iterN` tracks accepted count, not the log's
`Iteration NNN` numbering). New baseline `gauntlet/20260902-025219/`.
Replay: `replays/iter10_immediate_defector_closeup_botA.bc26`.

**Next.** `closeup` still lost both sides against `immediate_defector`
even with more population -- worth checking whether 25 is still the
binding constraint there or whether the population-bleed problem (now
resolving *faster*, not slower) has a different character at this scale.
`whereisthecheese` was already a loss against both opponents in the
`g_iter7` baseline (not new), but now resolves much faster (970-1030
rounds vs. 1235-1345 before) -- worth tracing directly since it's the one
losing map common to *both* opponents, unlike the others which are mostly
`pure_cooperator`- or `immediate_defector`-specific.

---

## Iteration 11 attempt — economy-struggle latch, raised RESERVE, and a first backstab-desperation trigger; REJECTED (inert)

Traced `whereisthecheese` (only 2 cheese mines) directly: `bot` was
**crushing** `pure_cooperator` on cat damage (1770 vs. 0 by round 600, a
near-total share of that 0.5-weighted scoring component) but our own King
still starved to death and **auto-lost outright** regardless -- RULES.md:
all-Rat-Kings-dead is an immediate loss overriding every other scoring
component. Root cause: the King's own 2-cheese/round upkeep is
unconditional (not a spending decision at all), and this map's income
(~0.5/round from 2 mines) can't cover it for either side -- both teams'
cheese declined the entire game, we just ran out first.

**Three things tried in sequence, same motivating game, each measured
before moving to the next:**

1. **200-round economy-checkpoint latch** (stop building if cheese
   dropped >150 over a window): re-traced the same game -- byte-identical
   population/cheese curve at every checkpoint. Population had already
   reached ~19 well before round 200 (the first possible checkpoint), so
   a check that only evaluates every 200 rounds structurally can't catch
   overspending that already happened.
2. **Raised RESERVE 150->500**: also byte-identical. Recomputed the real
   build-cost math this time instead of assuming: 25 rats total cost only
   ~910 cheese out of a ~2488 starting balance -- `MAX_POPULATION`, not
   `RESERVE`, was already the binding constraint the entire time, so
   raising the reserve couldn't have changed anything regardless of value.
3. **Backstab-desperation trigger**: since neither `bot` nor
   `pure_cooperator` ever backstabs, our combat dominance had no way to
   convert into winning the game outright. Added a King-broadcast
   "desperate" signal (shared array slot 2: economy latched *and*
   cheese already below `RESERVE`) that lets Baby Rats treat a sighted
   enemy rat as attackable even pre-backstab. Full Gauntlet: **29/40
   (72.5%), identical to the `g_iter8` baseline in every single game
   except one round-count shift** (`tiny` bot=A vs. `pure_cooperator`:
   r895->r1005, still a loss either way). The signal likely did latch in
   the motivating game (economy checkpoints show a real decline crossing
   both thresholds by round ~600-800) but apparently never had an enemy
   rat in range to act on -- Kings spawn at opposite corners on this map
   and neither side's Baby Rats hunt proactively, only react to what's
   already sighted.

**REJECT all three** (Step 6.4.3 -- no measurable engagement anywhere).
Reverted; `src/bot/` is back to `g_iter8`.

**Refined diagnosis, now well-evidenced across three attempts:** this
project has a real, structural gap it's run into from multiple angles in
a row -- Baby Rats (and the King) are entirely *reactive*. Nothing ever
initiates contact; everything only responds to what's already in
immediate sensor range. Fixing `whereisthecheese`'s specific starvation
race needs either (a) proactively pathing toward the map's mirror-image
symmetric point (a reasonable guess at the enemy King's location without
ever having seen it, since maps are guaranteed symmetric per RULES.md) to
actually force a decisive fight once desperate, rather than passively
waiting to get lucky, or (b) accepting some cheese-poor maps are simply
unwinnable via economy and finding a different lever entirely (e.g.
`squeak`-based coordination to rally a rescue/attack party, unused all
session). (a) is the more directly actionable next attempt.

---

## Iteration 12 — proactive hunt toward a guessed enemy-King location when desperate; accepted, 75%

Directly implements Iteration 11's refined diagnosis: re-added the
economy-struggle latch and desperation signal, and this time also
broadcast a **guessed enemy-King location** (shared array slots 3/4) --
`mapWidth-1-x, mapHeight-1-y` (a 180-degree-rotation guess off our own
King's own location; the actual symmetry type isn't exposed by
`RobotController`, only width/height are, so this is a real, accepted
source of error, same caveat BC22's LEARNINGS.md logged for the
equivalent guess there). When desperate with no enemy currently sighted,
a Baby Rat paths toward that guess instead of continuing normal
cheese/explore behavior -- deliberately forcing a crossing instead of
passively waiting for one, which is exactly what Iteration 11 showed
never happens on its own.

**Smoke test:** same `whereisthecheese` vs. `pure_cooperator` game --
still a loss (King starvation), but the guess happened to be exactly
correct on this map (symmetry actually is 180-degree rotational here),
and the effect was dramatic: `cheeseTransferred` **more than doubled**
(520->1160 by round 1300, vs. the `g_iter8` baseline's 520 total by
round 1000) -- rats hunting toward the guessed location evidently pass
near the map's other cheese mine en route, so the "hunt" doubles as
better map coverage. We actually **took the lead** on cheese-transferred
late in the game (1160 vs. 1010) while still dominating cat damage (1870
vs. 0) -- had the King survived another ~50-100 rounds, this specific
game likely flips to a decisive points win. The King still starves just
before that would matter (round 1340 vs. 1020 baseline -- 320 rounds
later, a real improvement, just not enough).

**Full Gauntlet: 30/40 (75%), up from 29/40 (72.5%).** `immediate_defector`
70%->75% (`tiny` bot=A flipped to a win); `pure_cooperator` unchanged at
75% in win/loss count but with meaningfully different round numbers on
the `whereisthecheese` losses (longer, closer games). **ACCEPT.**
Snapshotted as `src/g_iter9/`; new baseline `gauntlet/20260902-031317/`.
Replay: `replays/iter12_pure_cooperator_whereisthecheese_botA.bc26` (the
`whereisthecheese` game showing the cheese-transferred lead).

**Next.** `whereisthecheese` is now a near-miss rather than a blowout --
the King dying ~50-100 rounds too early is the last piece. Candidates:
tighten the desperation trigger to fire earlier (currently gated on
cheese already below `RESERVE=150`, i.e. very late), or have the King
itself dig in defensively / stop all further spending the instant
`economyStruggling` latches rather than waiting for the separate
`desperate` threshold. Separately, the guessed-location symmetry
assumption (180-degree rotation) is unverified on any map where it's
*wrong* -- worth checking whether a bad guess on some other map actively
hurts (sends desperate rats the wrong way, away from both the enemy and
useful cheese) rather than just failing to help.

---

## Iteration 13 attempt — trigger desperation earlier; REJECTED (regression)

Followed Iteration 12's own "Next" note: dropped the `globalCheese <
RESERVE` sub-condition, making `desperate = economyStruggling` alone (the
trend-latch, which fires earlier -- around round 400 rather than
round ~600-800). Reasoning: the hunt needed more runway before starvation,
so trigger it sooner.

**Smoke test:** same `whereisthecheese` game -- **worse**, not better:
King died at round 1155 (vs. 1340 in `g_iter9`). Traced why:
`aliveBabies` collapsed to **0 by round ~600** (vs. round ~1000+ before)
-- committing the population to hunting/fighting earlier, while it was
still smaller and the economy less established, got the whole army killed
off faster than it found the enemy, and `cheeseTransferred` (795) then
flatlined for the rest of the game with nobody left to gather or fight.
**Full Gauntlet: 29/40 (72.5%), down from 30/40 (75%)** --
`immediate_defector` 75%->70% (`tiny` bot=A flipped back to a loss).
Confirmed regression, not noise.

**REJECT.** Reverted; `src/bot/` is back to `g_iter9`.

**Diagnosis:** the *timing* of Iteration 12's original threshold
(cheese already below `RESERVE`) wasn't arbitrary bad luck -- it let the
population build up to a meaningful size and establish some economy
*before* committing to a risky hunt, which apparently matters more than
the extra runway an earlier trigger provides. The real lever for closing
`whereisthecheese`'s remaining gap is probably elsewhere: e.g. making the
guessed-location hunt itself more efficient (currently a single
`moveToward` call per turn, no coordination across rats -- several might
independently discover the same path), or having only *some* rats commit
to the hunt while others keep the economy alive, rather than an
all-or-nothing population-wide switch.

---

## Iteration 14 attempt — split hunters vs. economy by ID parity; REJECTED (regression)

Tried Iteration 13's other suggested angle: kept the original (later)
desperation trigger timing, but only half the Baby Rats (`rc.getID() %
2 == 0`) join the proactive hunt when desperate; the other half keep
gathering/defending normally.

**Smoke test:** same `whereisthecheese` game -- worse than `g_iter9`
(round 1050 vs. 1340), though still better than the pre-Iteration-12
baseline (1020). Matches identically through round 800 (desperate hasn't
triggered yet), then the halved hunting force reaches/uses the guessed
location much less effectively than the full population did. **Full
Gauntlet: 29/40 (72.5%), down from 30/40 (75%)** -- confirmed regression,
same shape as Iteration 13's.

**REJECT.** Reverted; `src/bot/` is back to `g_iter9`.

**This is the third attempt in a row targeting `whereisthecheese`
specifically** (Iterations 11, 13, 14 -- with only Iteration 12's
original full-population, late-trigger version actually working). Two
consecutive rejects since the last accept -- one more failed attempt in
this exact area would hit `MaxConsecutiveRejects=3`. Per
`TRAINING_ALGORITHM.md`'s "prefer fresh territory" guidance, moving to a
different functional area next rather than a fourth narrow tweak to the
same desperation/hunt mechanism: `g_iter9`'s own commit trail spans 12
iterations without ever running a formal mirror-match check
(`TRAINING_ALGORITHM.md`'s own standing "Play symmetry" requirement --
`bot` vs. a byte-identical copy of itself, both sides, checking for a
lopsided split). Worth doing now, both because it's overdue and because
several of this session's changes (per-robot ID-seeded RNG, ID-parity
tiebreaks in `tryMove`, the guessed-location symmetry assumption) are
exactly the kind of tie-breaking logic that class of bug hides in.

---

## Symmetry audit — mirror-match check, g_iter9 vs. itself, full map set

Not a `src/bot/` code change -- a standing verification pass
(`TRAINING_ALGORITHM.md`'s "Play symmetry" section), overdue after 12
accepted iterations without one. `tools/snapshot.sh mirror_check`
(byte-identical copy of `g_iter9`), `OPPONENTS=mirror_check MAPSET=full
tools/gauntlet.sh` -- all 27 maps in `tools/bc26-maps.txt`, both sides,
54 games.

**Result: exactly 50% (27/54), and every single one of the 27 maps split
1-1 between sides** -- not one map won or lost on both sides. This is a
clean pass, not just "no severe skew": `TRAINING_ALGORITHM.md`'s own
guidance expects *some* noise (the coin-flip tiebreak on tied points,
chaos-sensitive maps), so a perfectly even per-map split is a
better-than-expected result, not a suspicious one -- most games ran to
the round-2000 points cap (genuinely symmetric play producing genuinely
close outcomes), with a handful of faster, decisive games
(`whereisthecheese` r938, `streetsofnewyork` r505, `peaceinourtime` r484)
also splitting cleanly.

**No team-correlated bias detected**, despite this session adding
substantial new tie-breaking/symmetry-sensitive logic since the
project's original symmetry-safety design (Iteration 1): per-robot
`rc.getID()`-seeded RNG, `rc.getID() % 2` tiebreaks in `tryMove()` and
the (rejected) hunter-split attempt, and the 180-degree-rotation
enemy-King-location guess. Worth re-running this check periodically as
more tie-breaking logic accumulates, same as BC22's own history found
value in -- but no action needed right now.

---

## Iteration 15 attempt — rat traps near the King as surplus defense; REJECTED (never engages)

Fresh functional area, explicitly deferred since Iteration 1 (traps,
ratnap/throw, cheese-spend-on-bite were all listed as unexplored).
King places a rat trap (20 cheese, 50 dmg + 3-turn stun, enemy-only) on
an adjacent tile once population is capped and cheese is comfortably
above `RESERVE` -- pure discretionary spending of genuine surplus,
targeting `closeup`'s unresolved `immediate_defector` loss.

**Added `PlaceTrap`/`TriggerTrap` cases to `tools/replaydump/ReplayDump.java`
first** (previously silently dropped by the default case) specifically
to verify this mechanistically, not just by outcome. Smoke test on
`closeup`: 5 traps placed in quick succession right after hitting the
population cap (round 34-40, all adjacent to the King) -- **zero ever
triggered**, the entire game. `immediate_defector`'s hunters go after
individual scattered Baby Rats, not the King's own tile specifically, so
traps sitting in the King's build ring are never in anyone's path.

**Full Gauntlet: 30/40 (75%), unchanged in aggregate** -- but
`pure_cooperator` 75%->80% and `immediate_defector` 75%->70%, a mixed,
scattered, net-zero shape consistent with noise, not a real effect
either direction (traps costing cheese but essentially never triggering
is mechanistically incapable of producing a real directional effect).

**REJECT** (Step 6.4.3: confirmed non-engagement, not just absence of
a Gauntlet flip) despite the nominally-unchanged aggregate -- keeping
code that provably never does anything, while still spending real
cheese, isn't worth carrying forward on the strength of "didn't measurably
hurt." Reverted; `src/bot/` is back to `g_iter9`. The
`PlaceTrap`/`TriggerTrap` replay-tool support is kept (real, reusable
diagnostic capability, independent of this specific attempt's outcome).

**Diagnosis for a future attempt:** placement needs to target where the
enemy actually goes, not just "near the King because there's spare
cheese." Candidates: place along the path between the King and the
map's cheese mines (where both economies' traffic concentrates) rather
than in the build ring specifically, or gate placement on an enemy
having been *recently sighted* nearby rather than blind post-cap
surplus spending.

---

## Iteration 16 attempt — King spends cheese on cat bites for bonus damage; REJECTED (regression, real interaction effect found)

RULES.md's bite formula (`10 + ceil(sqrt(X))` for `X` cheese spent) had
gone completely unused all session. Had the King spend up to 100 bonus
cheese (floored at the same 150 `RESERVE`) when attacking a cat
specifically -- doubling down on cat damage, this bot's most consistent
strength, rather than patching a weakness, and restricted to the King
(reliable large cheese reserves) rather than Baby Rats (carried cheese
almost never survives to an attack -- delivery is priority 1).

**Smoke test:** `whereisthecheese` vs. `pure_cooperator` -- worse
(round 912 vs. 1340). Traced why: the King *did* engage and spend (cheese
900 at round 400 vs. 1000 baseline, 160 at round 600 vs. 660) -- real
mechanistic engagement, unlike Iterations 14/15. But faster cheese
depletion pulled the `economyStruggling`/`desperate` trigger **much**
earlier than intended, recreating Iteration 13's already-rejected
regression (population committing to the hunt too early, collapsing to 0
by round 800) through a different mechanism -- a genuine interaction
effect between two features that each work fine in isolation.

**Full Gauntlet: 29/40 (72.5%), down from 30/40 (75%)** -- confirmed
broadly, not just on the motivating map: `closeup` dropped to r749/r770
(from ~r1260/r1270), a new loss appeared (`sittingducks` vs.
`immediate_defector`), consistent with the same premature-desperation
pattern firing on multiple maps, not just `whereisthecheese`.

**REJECT.** Reverted; `src/bot/` is back to `g_iter9`.

**This is the fourth consecutive reject** (Iterations 13, 14, 15, 16),
past `MaxConsecutiveRejects=3` even counting only genuinely distinct
areas (desperation-tuning x2, traps, cheese-bite-bonus). The common
thread across three of these four: anything that touches the King's
cheese balance interacts with the `economyStruggling`/`desperate` latch
in ways that are easy to get wrong, because that latch currently reacts
to *any* decline, not specifically to the structural income-vs-upkeep
problem it was built to detect. A more targeted trigger -- e.g. tracking
net income (cheese delivered) against base upkeep specifically, rather
than total cheese-on-hand -- would decouple discretionary King spending
(traps, bite bonuses, whatever comes next) from the starvation-detection
system entirely, instead of each new spending feature needing to
independently avoid tripping it. Worth doing before another attempt in
this neighborhood, rather than continuing to discover the same
interaction from a new angle each time.

---

## Iteration 17 attempt — prefer ratnap over biting an enemy rat; REJECTED (asymmetric trade)

Fresh area (ratnap/throw unused all session): when a post-backstab/
desperate Baby Rat has both `canCarryRat` and `canAttack` available
against an enemy, prefer carrying (`RULES.md`: adjacent enemy eligible if
facing away, lower HP, or allied; carried = stunned, immune to
everything but a cat, for up to 10 rounds) over biting -- reasoning: a
capture costs nothing (no damage exchanged) versus a bite trade.

**Smoke test:** `closeup` vs. `immediate_defector` -- unchanged (round
1270, matches baseline exactly), ratnap never engaged there. **Full
Gauntlet: 29/40 (72.5%), down from 30/40 (75%)**, one new loss
(`knifefight` vs. `immediate_defector`, previously a win). That specific
game showed ratnap engaging heavily (76 logged events) -- traced it
directly and found the real mechanism: `aliveBabies=[2, 6-7]` the entire
game, a severe, sustained population deficit that wasn't there before.
**Preferring capture over biting means we stopped landing permanent
kills on `immediate_defector`'s rats** -- a captured rat auto-drops and
re-enters the fight after 10 rounds, unharmed, while `immediate_defector`
itself keeps landing real, permanent kills on us the whole time (it never
carries, only attacks). Trading a permanent kill for a temporary
inconvenience is a bad exchange against an opponent that's still killing
us for real.

**REJECT.** Reverted; `src/bot/` is back to `g_iter9`.

**Fifth consecutive reject** (Iterations 13-17). The diagnosis here is
clean and specific enough to be directly actionable, unlike the more
diffuse King-economy interactions of 13/14/16: ratnap's value proposition
depends on the trade actually being favorable, which requires *not*
replacing kills we'd otherwise land, only supplementing situations where
we couldn't win the fight anyway (e.g. outnumbered, or the enemy is about
to reach the King/economy and delaying it matters more than killing it).
A future attempt should gate ratnap on those specific conditions rather
than a blanket "prefer capture" rule -- but per `TRAINING_ALGORITHM.md`'s
"prefer fresh territory," the next attempt should look elsewhere first
rather than immediately re-entering this same combat-tuning area a sixth
time.

---

## Iteration 18 attempt — high-risk structural exploration: opportunistic multi-King formation; REJECTED (catastrophic)

Fifth consecutive incremental reject (13-17) is exactly
`TRAINING_ALGORITHM.md`'s trigger for "High-risk structural
exploration" -- a first-class move, not a last resort. Multi-King
formation (`RULES.md`: a Baby Rat with >=7 allied rats in its
surrounding 3x3 can upgrade for 50 cheese, consuming all of them into
the new King) was completely unused all session. Hypothesis: several
recurring losses trace back to single-King structural limits (one
shared-array writer, one production source, cheese-transport distance
from mines) that a second, well-placed King could route around. First
cut deliberately opportunistic: any Baby Rat calls `becomeRatKing()`
whenever eligible, no engineered placement.

**Smoke test on `closeup` vs. `immediate_defector`: catastrophic --
round 229, vs. the `g_iter9` baseline's 1270.** Traced immediately (no
full Gauntlet needed, Step 6.5's "unambiguous regression already visible
at small scale" carve-out): **`kings=3` by round 100.** Each Rat King
consumes `RAT_KING_CHEESE_CONSUMPTION=2`/round from the *same shared
global cheese pool* -- three Kings means 6/round base upkeep instead of
2, with no proportional income increase, plus each formation directly
cannibalized 7 Baby Rats' worth of population and HP into the new King.
Cheese crashed from ~200 (round 50, 1 extra King already) to 23 (round
100, 2 extra) to effectively 0 by round 150, and stayed there. The
Kings themselves then started starving each other via the shared
upkeep drain.

**REJECT, decisively.** Reverted; `src/bot/` is back to `g_iter9`. Also
noted: `tools/replaydump/ReplayDump.java` has no case for
`Action.UpgradeToRatKing` (silently dropped by the default case) --
formation was only visible via the `teamAliveRatKings`-packed `kings=N`
field, not a direct action log. Worth adding a case if multi-King is
revisited.

**This was still a valuable, real exploration**, not a wasted attempt --
per `TRAINING_ALGORITHM.md`'s own framing, a rejected structural attempt
is not a failure state, and this one produced an unambiguous,
mechanistically-clear answer instead of another inconclusive small-scale
tweak: **uncontrolled King formation is a hard no** given the shared,
per-King upkeep cost. A future attempt in this space would need
formation gated on a specific, verified payoff (e.g. only forming near
an already-discovered, currently-unreached cheese mine, where the new
King's *income* would plausibly exceed its own 2/round upkeep quickly)
rather than opportunistic triggering whenever 7 rats happen to cluster.

---

## Infrastructure fix — archetypes had gone stale again since Iteration 10, silently inflating the score

While investigating `tiny` (a loss against both opponents in every
recent Gauntlet, never directly root-caused) as the next Step 4 target
after six straight rejects, found something more important than a
per-map bug: `pure_cooperator` was gathering cheese **13x faster** than
`bot` (2905 vs. 215 by round 800) despite the two archetypes being
supposed to run near-identical economy code. Checked directly:
`pure_cooperator`/`immediate_defector` were still at `MAX_POPULATION=15`
and missing every fix since Iteration 4 (the cat-engagement DPS fix,
Iteration 5-6; unconditional engage, Iteration 6; `MAX_POPULATION`
raised to 25, Iteration 10) -- the one-time sync during the "Gauntlet
pool update" episode was never repeated as `bot` kept evolving.

**This means every accept/reject decision from Iteration 10 onward
(10, 12, and the rejected 13-18) was partly measured against a
stale, weaker-than-intended `pure_cooperator`.** The `pure_cooperator`
side of those Gauntlets was easier than the "current bot's real peer
strength" the algorithm is supposed to be tracking -- not stale enough
to flip WinPct below 60% on any of them retroactively (checked: none of
Iterations 10-18's WinPct margins were close enough to 60% that a ~20
percentage-point-easier opponent on one side of the roster would flip
the verdict), but the peer-spread and absolute-strength picture
(`progress/peer_win_spread.png`, `progress/vs_old_bots.png`) has been
overstating `bot`'s edge against `pure_cooperator` specifically for the
last 6+ iterations.

**Re-synced both archetypes properly** this time (`MAX_POPULATION=25`,
full cat-engagement/King-dig fixes; deliberately did *not* add the
Iteration 11-12 desperation/backstab-hunt system to either -- it
contradicts `pure_cooperator`'s identity outright, and would override
`immediate_defector`'s own distinctive leash behavior for no benefit
since it's already always-hostile). Verified the fix directly:
re-ran the same `tiny` game, and the suspicious 13x gap is gone --
a real, close, back-and-forth economy race (both sides trading cat-
damage and cheese-transferred leads) resolving in a genuine win, not an
artifact.

**Full Gauntlet with corrected opponents: 27/40 (67.5%)**, down from the
stale-opponent reading of 30/40 (75%) -- `pure_cooperator` 75%->55% (a
real, much harder peer now, many games running the full 2000-round
cap), `immediate_defector` 70%->80%. **This 67.5% is the honest current
reading of `g_iter9`'s strength** -- not a regression to act on (no
`src/bot/` code changed), but the corrected baseline every future
Gauntlet comparison should measure against. `gauntlet/20260902-141237/`
is the new reference run.

**Standing lesson, added to memory:** synchronizing the archetypes once
isn't enough -- they need to be checked (and re-synced) periodically as
`bot` keeps evolving, or the peer roster silently drifts easier over
time and inflates every subsequent Gauntlet reading without any signal
that it's happening. A 13x gap on one map is what made this visible;
smaller staleness could hide for a long time. Worth adding a periodic
"are the archetypes still in sync" check to this project's own version
of BC22's periodic-maintenance habits (progress charts, vs-old-bots
tracking), not just a one-time fix.

---

## Iteration 19 attempt — unconditional enemy-rat chase; REJECTED (fixed one map, broke two others)

Traced the corrected baseline's `knifefight` vs. `immediate_defector`
loss (`gauntlet/20260902-141237/losses/immediate_defector__knifefight__botA.bc26`,
r1422 RATKING_DESTROYED). Round-by-round: our cheese drained steadily
2452->1 over 1400 rounds while population stayed flat at 3-5 the whole
game (never approaching `MAX_POPULATION=25`) -- a slow-bleed attrition
loss, not a specific tactical bug, similar in shape to `closeup`'s
original Iteration 8/9 context but showing up here against
`immediate_defector` specifically.

**Root cause found:** cat-engagement (`engage(rc, nearestCat...)`)
chases a sighted cat unconditionally, out to full vision (radius^2 20).
Enemy-rat engagement had an extra `dist<=8` gate before chasing -- a
sighted-but-not-yet-close enemy rat was simply ignored, ceding the
first-move initiative to an always-hostile opponent in *every* single
encounter, all game. Dropped the gate to mirror cat-engagement.

**Smoke test on the motivating game:** `knifefight` vs. `immediate_defector`
died *faster* (r1100, down from r1422) -- a bad sign. Traced it: at
round 100 we led 18 alive babies to their 1; by round 200 that had
flipped to 1 vs. 13. Death tally in that window: 17 of ours vs. 10 of
theirs, and critically, the enemy's own RAT_KING (attack range^2 8, far
larger than a Baby Rat's bite range^2 2) directly killed 3 of our rats.
**Mechanism:** chasing a retreating enemy rat with no distance limit
walks straight into its King's kill zone. Cats have no equivalent
ranged backup, so the logic that's safe against a cat is a trap against
a rat.

**Iteration 20 refinement, same turn:** kept the free chase (fixes the
original ceded-initiative problem) but added a guard -- fall back to the
old conservative `dist<=8` gate specifically when a visible enemy
RAT_KING is within its own attack range of the target, so a pursuit
still finishes off anything already close but won't press into
contested territory near their King.

**Re-ran the smoke test: clean win**, r1585 (`bot (A) wins`), confirmed
again on repeat (r1500). The exact motivating loss flipped to a win.

**Full Gauntlet: 26/40 (65.0%), down from the 67.5% baseline.**
`pure_cooperator` unchanged at 55% -- byte-identical loss list to the
baseline run, as expected (this branch only fires once cooperation is
already broken, which barely happens against a non-hostile peer).
`immediate_defector` 80%->75%: `knifefight` flipped win as intended, but
two *new* losses appeared that weren't losses before -- `tiny` (r937)
and `closeup` (r1003). Net -1 win. The entire delta is concentrated in
the `immediate_defector` column (a real, one-directional effect of this
change, not scattered noise), so trusted the result without needing a
second run.

Traced the new `closeup` loss
(`gauntlet/20260902-155035/losses/immediate_defector__closeup__botA.bc26`):
the identical slow-bleed shape as `knifefight`'s original problem --
population 25 at r50 (healthy!) declining steadily to 0 by r625 despite
a commanding `catDamage` lead (740 vs. 460) the whole time, cheese
draining in lockstep. The guard (checking for a *visible* enemy King)
evidently doesn't catch every case where chasing overextends a Baby Rat
into a losing exchange -- on a tight map like `closeup`, a King doesn't
need to be in vision for the chase to still be a bad trade.

**REJECT** (net regression on the full Gauntlet despite a confirmed,
worthwhile mechanism and a clean fix for the motivating case). Reverted
`src/bot/RobotPlayer.java` to the `g_iter9` baseline.

**Refined diagnosis for a future attempt:** the King-proximity guard is
too narrow a condition -- it only prevents the *specific* mechanism
found on `knifefight` (dying to the enemy King's own attack), not the
broader problem that unconditionally chasing a retreating rat is
sometimes just a bad trade on its own (overextending away from the
King's cheese-delivery range, walking past a cat, etc.), independent of
whether an enemy King happens to be nearby. A more conservative guard --
e.g. only chase past `dist<=8` when already at high HP and the target
is fleeing *toward* our own King rather than into unknown territory --
is untried and more promising than trying to special-case King
proximity again.

**This was Iteration 19/20's rejection -- 7 consecutive rejects now
(13-20), all in the combat-targeting-vs-rats / backstab-trigger-policy
functional area. Per *MaxConsecutiveRejects* (TRAINING_ALGORITHM.md,
threshold 3), the next attempt must leave that area** -- picked
"High-risk structural exploration" in a genuinely fresh area (cat
behavior) as an equally-legitimate first move, not a last resort.

---

## Iteration 21 attempt — flee only when actually inside a cat's vision cone; REJECTED (confirmed inert)

**Mechanism research (verified against engine source, not guesswork):**
RULES.md already states a cat's scratch only hits "in vision cone," but
traced the actual enforcement path to confirm exactly how it's gated --
`RobotControllerImpl.assertCanAttackCat` -> `assertCanActLocation` ->
`assertCanSenseLocation`, which applies the same cone test as
`MapLocation.isWithinDistanceSquared(..., facingDir, theta)` (dot
product of facing direction and target-relative vector >= 0 for a
180-degree cone). Also confirmed in `InternalRobot.java`'s cat AI: in
Attack mode, target acquisition itself scans `senseNearbyRobots()`,
which is *also* cone-filtered -- a cat doesn't just miss an out-of-cone
rat, it can't select one as a target at all, and loses lock entirely if
a locked target steps out of the cone. This is a hard, provable,
already-exposed-data (`RobotInfo.getDirection()`) exploit, not a guess.

**Implementation:** added `inCatVisionCone()` (dot-product test,
approximating the cat's 2x2 cone origin as its reported tile -- a
disclosed, small, boundary-only error since
`usesBottomLeftLocationForDistance()` isn't exposed via
`RobotController`). Gated the existing critically-low-HP-no-ally flee
fallback on it: only flee if actually inside the cone.

**Smoke test:** compiled clean, sane result. **Full Gauntlet: 27/40
(67.5%), byte-identical to the baseline** -- not just the same win
count, the exact same 13 losses on the exact same maps/sides, at the
exact same round numbers, across all 40 games. Stronger than a null
result: this is Step 6.4.3's "confirmed non-engagement" signature.

**Diagnosis:** correct per Iteration 6's own text -- the flee fallback
only fires when critically low HP (`<=30`) *and* no ally nearby *and*
not yet adjacent, an already-narrow combination Iteration 6 flagged as
rare. The cone mechanism is real and confirmed; this specific
integration point just has no surface area for it to matter.

**REJECT** (Step 6.4.3, no engagement anywhere -- not a wrong idea, a
too-narrow application of a right one).

---

## Iteration 22 attempt — seek remembered cat location when idle; REJECTED (broad regression)

Refined target for the same underlying idea (`catDamage` is a real
scoring component -- 0.3-0.5 weight depending on coop/backstab mode --
and 8 of 9 `pure_cooperator` losses are decided on points at the r2000
cap) plus Iteration 6's own still-outstanding "Next" note: Baby Rats
never seek a cat beyond whatever's already visible. Added
`lastKnownCatLoc` (updated for free whenever a cat's already sensed),
and -- as the lowest-priority fallback, only after cheese-delivery,
cat-handling, backstab-hunt, and cheese-collection all decline the turn
-- move toward it instead of plain `explore()`. Included the same
"don't claim the turn if we arrive and it's not there" fix as
`collectCheese()`/`deliverCheese()`/`engage()` (clear the memory and
fall through to `explore()` on arrival) to avoid re-creating Iteration
3's stuck-forever bug.

**Smoke test** (`sittingducks` vs. `pure_cooperator`): compiled clean,
but died early to RATKING_DESTROYED at r1443 -- baseline lost this exact
matchup too, but on points at r2000, not elimination. Went to the full
Gauntlet rather than reading one game, per this project's own precedent.

**Full Gauntlet: 22/40 (55.0%), down sharply from 67.5%.**
`pure_cooperator` 55%->45%, `immediate_defector` 80%->65%. New
early-elimination losses appeared broadly, not on one map: both sides
of `closeup`, both sides of `knifefight`, both sides of `sittingducks`,
`tiny`, and both `immediate_defector` sides of `keepout`. Broad-based
and decisive.

**REJECT** (Step 6.5, unambiguous regression, no further verification
needed). Reverted `src/bot/RobotPlayer.java` to the `g_iter9` baseline.

**Diagnosis:** sending an otherwise-idle rat to go looking for a cat
trades away real, ongoing cheese-collection/King-defense time across
*every* map, for a payoff (extra `catDamage` points) that's only
decisive in the narrow subset of games that end in a points decision
anyway. The opportunity cost is paid every game; the benefit only shows
up in some. Diverting an otherwise-idle rat toward economic value
(cheese search) instead of combat value would need a much cheaper way
to bias the search than a full dedicated detour -- e.g. weighting
`explore()`'s own direction choice slightly toward the remembered
location instead of overriding it outright, so cheese-search isn't
fully abandoned. Untried.

**8 consecutive rejects now** across two functional areas
(combat-targeting-vs-rats, then cat-behavior). Both cat-behavior
attempts confirmed the underlying mechanism was real and correctly
understood (cone-gating verified against engine source; catDamage's
scoring weight is real) -- the failures were in integration point and
opportunity-cost accounting, not in the reasoning about the game.

---

## Iteration 23 attempt — flee once critically low HP even while already adjacent; REJECTED (confirmed inert)

Went back to Step 4's normal single-losing-game process rather than
another broad structural swing. Traced `closeup` vs. `pure_cooperator`
(`gauntlet/20260902-141237/losses/pure_cooperator__closeup__botA.bc26`,
r1240 RATKING_DESTROYED) with the round-summary tracker: `catDamage`
1450 vs. 240 (we dominate decisively) yet our own King still starves --
the identical death-spiral shape as `whereisthecheese`. Since
`pure_cooperator` never backstabs, this attrition can't be from enemy
rats -- it has to be from cats killing our own Baby Rats faster than the
economy can replace them.

**Found a real gap while reading the code:** the very first line of
cat-handling is `if (rc.canAttack(cat)) { attack; return; }` --
unconditional, no HP check at all. The `allies>1 || health>30`
threshold only ever gated the *approach* decision (`engage()`), never
whether an already-adjacent rat should keep fighting. Since a 100 HP
Baby Rat can never actually kill a 4000 HP cat solo, an adjacent rat
with no exit condition fights to the death on every single engagement.

**Fix:** reused the existing threshold to also gate the already-adjacent
attack, falling back to `flee()` once critically low HP with no ally,
even mid-fight.

**Smoke test** (the motivating `closeup` game): same exact loss, same
exact round (r1240) as baseline -- suspicious. Full round-by-round diff
against the baseline replay: **byte-identical**, not just similar.
Checked a second map (`whereisthecheese`) showing the same failure
shape: also byte-identical (r938, full diff clean). **Full Gauntlet:
27/40 (67.5%), byte-identical loss list and round numbers to baseline
across all 13 losses.** Confirmed non-engagement on two independent
maps before trusting the aggregate result.

**REJECT** (Step 6.4.3). Reverted `src/bot/RobotPlayer.java`.

**Root cause of the inertness, found via `tools/replay-dump.sh --robot`
tracking a specific dying rat (`id10405` on `closeup`):** it sat at HP=100
the *entire* time right up until one round before death -- this map's
population attrition isn't from gradual multi-hit combat damage at all,
so a HP-threshold flee gate has nothing to catch. See Iteration 24 for
what's actually killing these rats.

---

## Iteration 24 — break 2-tile oscillation traps in tryMove(); [pending Gauntlet result]

Following directly from Iteration 23's dead end: traced `id10405`
(`closeup` vs. `pure_cooperator`, `tools/replay-dump.sh --robot 10405`)
end to end. It spawned round 7, and from at least round 43 through its
death at round 95 it did **not move at all in any net sense** -- position
alternated between exactly `(19,8)` and `(20,9)` for 50+ rounds straight,
`cheese=40` the entire time (carrying cheese it could never deliver),
`hp=100` unchanged. Then: one `CatScratch` at round 93 (100->80 HP), and
by round 95 it's dead with **no further logged scratch or pounce action**
-- RULES.md: "moving onto a baby rat's tile also instantly kills it." The
cat didn't hunt it down; it just walked through on its own patrol and
happened to step on a stationary target.

**Terrain at `(19,8)`/`(20,9)`** (`tools/replay-dump.sh --terrain`):
a comb-like alternating wall pattern (`# # #`) immediately adjacent.
`tryMove()`'s only escape logic when blocked is a single 45-degree
sidestep, tie-broken deterministically by `rc.getID() % 2` -- no real
pathfinding, no backtracking. Against this specific terrain shape, the
direction recomputed fresh each round from the new tile apparently
points right back the way the rat came, producing a stable 2-cycle the
existing sidestep logic can never break out of on its own.

**This is the same class of problem the engine's own cat AI hits and
explicitly fixes** (`InternalRobot.java`'s `EXPLORE` state: after
`catTurnsStuck >= 4`, turn to a random direction instead of retrying the
same blocked path) -- precedent for the fix, not a novel technique.

**Fix:** track position from 2 rounds ago once per round (top of
`runBabyRat`, so it's exactly once per round regardless of how many
`tryMove()` attempts happen within a turn -- `deliverCheese()`/
`collectCheese()`/`engage()`/`flee()` can each fall through to a further
attempt in the same turn on failure). If the current tile matches the
tile from 2 rounds ago, increment a stuck-cycle counter; reset it
whenever a genuinely new tile is reached. Once the counter hits 2 (two
full oscillations confirmed, not just one -- avoids overreacting to a
single incidental repeat), `tryMove()`'s blocked-path fallback switches
from the deterministic left/right-of-`want` sidestep to a randomly
shuffled search across all 8 directions, using the already-seeded
per-robot `rng` (no new import needed; avoided `java.util`
collections/`Collections.shuffle` entirely -- manual Fisher-Yates on a
plain `Direction[]` array, to stay clear of `AllowedPackages.txt` risk
for a change this far from anything previously exercised).

**Smoke tests:** `closeup` and `whereisthecheese` vs. `pure_cooperator`
(the two games that originally motivated Iteration 23's dead end) both
flipped from losses to clean wins (r1135, r936).

**First full Gauntlet (stuck-escape applied everywhere, unscoped): 26/40
(65.0%), down from 67.5%.** `pure_cooperator` improved 55%->60% (both
`whereisthecheese` losses fixed) but `immediate_defector` dropped
80%->70%, with `knifefight` newly losing on both sides. Traced the new
`knifefight` loss: population collapsed 13->2 within 75 rounds, much
faster than baseline -- diagnosed as the random-direction escape firing
during legitimate combat back-and-forth (which can trip the same
2-tile-repeat detector as a genuine terrain trap) against a mobile,
always-hostile opponent, on a map where Kings spawn only 5 tiles apart
and combat starts almost immediately (Iteration 19's own finding).

**Refinement:** scoped `allowStuckEscape` to "economic" travel only --
`deliverCheese()`, `collectCheese()`, the backstab-hunt guessed-location
chase, and `explore()`'s two movement attempts all pass `true`;
`engage()` and `flee()` keep the old deterministic tiebreak
unconditionally (`false`, via the existing no-arg overloads, so their
call sites needed no changes at all).

**Re-smoke-tested:** `closeup` still a clean win (r1077). `knifefight`
vs. `immediate_defector` and `whereisthecheese` vs. `pure_cooperator`
both still showed single-game results similar to pre-refinement (one
still a loss, one flipped back to a loss) -- read as RNG-cascade noise
rather than the refinement failing, since disabling `rng` consumption in
`engage()`/`flee()` shifts the entire downstream random stream for that
game regardless of whether the *logic* is better or worse; went straight
to the full Gauntlet rather than over-reading single flipped games (this
project's own repeated precedent).

**Full Gauntlet with the scoped fix: 28/40 (70.0%), up from 67.5%.**
`pure_cooperator` 55%->60% (`closeup` now wins on both sides,
`thunderdome` also newly wins). `immediate_defector` steady at 80%
(16/20, same count as baseline, `minimaze`/`pipes` newly won balancing
`closeup`/`keepout` newly lost -- reshuffled, not concentrated).
`rift` picked up a second losing side (`pure_cooperator` bot=A, was only
bot=B before) -- the one loose thread in an otherwise clean improvement,
not chased further this iteration since it's a single new loss amid
several fixes, not a concentrated pattern.

**ACCEPT.** Meets *WinPct* (60%) with margin, exceeds the running
baseline (70.0% vs. 67.5%), and the diff reads as real improvement with
reshuffled-not-regressed noise elsewhere, not an unresolved regression.
Snapshotted as `src/g_iter10/`; new baseline `gauntlet/20260902-163148/`.

This closes out the death-spiral investigation that started with
Iteration 23's dead end: the actual mechanism killing rats on
`closeup`/`whereisthecheese` was never gradual combat attrition (no HP
threshold could ever have caught it) -- it was rats getting physically
trapped in a 2-tile movement oscillation against maze-like terrain,
then getting walked over by a wandering cat while stationary
(RULES.md: moving onto a Baby Rat's tile is an instant kill, no combat
action required). `catDamage` looking dominant (1450 vs. 240 on
`closeup`) was real but beside the point -- the King was starving from
population loss that had nothing to do with fighting cats at all.

---

## Diagnostic pass over the new baseline's remaining losses

With `g_iter10` as the new baseline (`gauntlet/20260902-163148/`, 28/40),
traced two more losing games before attempting anything:

**`rift` vs. `pure_cooperator`** (newly picked up a second losing side
this iteration): genuinely healthy, not a regression to chase. Both
sides thriving (20-21 alive babies steady all game, cheese growing into
the thousands for both), and we actually *lead* on cheese (11465 vs.
8090) and `cheeseTransferred` (13935 vs. 10545) by round 2000 -- lose
narrowly on `catDamage` (200 vs. 270), which carries the heaviest
scoring weight (0.5 in coop mode per TRAINING_ALGORITHM.md). A close,
clean points decision, not a bug.

**`keepout`** turned out to have two *different* failure modes depending
on opponent:
- vs. `pure_cooperator`: the same shape as `rift` -- we lead economically
  (population 9 vs. 6, cheese 5850 vs. 3605) but lose on points via the
  `catDamage` gap (270 vs. 380). Not chased further this round -- see
  "Next" below.
- vs. `immediate_defector`: a real population collapse (25->2 over the
  full game), and *not* a cat problem (`catDamage` actually favors us,
  370 vs. 320). Death tally in the first 400 rounds: 19 of ours vs. 10 of
  theirs, 173 `RatAttack` events vs. only 20 cat-related ones -- rat-vs-rat
  attrition, the same functional area as the 7 straight rejects in
  Iterations 13-20.

---

## Iteration 25 attempt — cap exploration distance from King; REJECTED (broad regression)

Traced the `keepout` vs. `immediate_defector` population collapse with
`tools/replay-dump.sh --robot`: `id10869` spawned near our King at
`(41,23)` and walked in a dead-straight line the *entire* width of the
44-tile map -- its fixed `preferredExploreDir` (assigned once from
`rc.getID()` in Iteration 4's 8-way fanout) happened to point roughly
toward the enemy side, and nothing in `explore()` has any notion of
"far enough." It died at `(15,21)`, 10 tiles from the enemy King, HP
dropping 100->20 across 4 rounds with -30 jumps -- multiple simultaneous
attackers, not a 1v1 fight. A genuinely fresh mechanism: exploration-
range control, not reactive combat-decision tuning (distinct from
everything tried in Iterations 13-20).

**Fix:** `explore()` now takes `kingLoc`; once a rat wanders past half
the map's larger dimension from its own King, it heads home instead of
continuing in its preferred direction.

**Smoke test** (the motivating `keepout` game): flipped from a loss to a
win. **Full Gauntlet: 24/40 (60.0%), down sharply from 70.0%** --
`pure_cooperator` 60%->45% (10 of 20 games now losses, up from 8, spread
across `closeup`/`keepout`(both sides)/`knifefight`/`minimaze`/`pipes`/
`sittingducks`/`rift`(both sides)/`whereisthecheese`/`thunderdome`),
`immediate_defector` 80%->75%. Broad-based regression, not concentrated
on the motivating map.

**REJECT** (Step 6.5). Reverted `src/bot/RobotPlayer.java`.

**Diagnosis:** the fix is real for the specific case it targeted, but
the cap (half the map's larger dimension) is evidently too aggressive
across most maps -- turning a rat back home mid-search, even one that
hasn't found trouble, likely produces wasted back-and-forth ("yo-yo")
travel that costs real cheese-search time on maps where wandering that
far is normal and safe, not evidence of having wandered into danger.
**Untried refinement:** condition the retreat on some signal of actual
danger (e.g. only cap distance once an enemy has been sighted recently,
or only on maps large enough that half-width is already a very long
walk) rather than applying a blanket distance cap on every map
regardless of that map's actual risk profile.

**Next.** Two open, well-evidenced but unresolved threads: (1) the
`catDamage`-vs-economy tradeoff seen on `rift`/`keepout` vs.
`pure_cooperator` -- we're economically ahead but lose the heavily-
weighted `catDamage` score component; Iterations 21/22 already showed
naive fixes here regress broadly, so this needs a more conditional
approach than "seek cats when idle." (2) `keepout`'s rat-vs-rat
attrition vs. `immediate_defector` -- Iteration 25 found and partially
addressed the "lone wanderer" mechanism but the fix was too blunt;
refining it (danger-conditional retreat instead of a blanket distance
cap) is the natural next attempt, not a new area.

---

## Iteration 26 attempt — raise the exploration-distance cap to 85%; REJECTED (net zero, no improvement)

Refinement of Iteration 25's rejected fix rather than a new idea: same
mechanism, same threshold-based retreat, but raised from 50% to 85% of
the map's larger dimension, on the theory that the traced failure case
(a rat crossing essentially the *entire* map width) was extreme enough
that a much higher bar would still catch it without interrupting the
far more common case of moderate, safe exploration that Iteration 25's
50% threshold was needlessly cutting off.

**Smoke test** (`keepout` vs. `immediate_defector`, the motivating
game): a loss this time (points, not elimination) -- different from
Iteration 25's clean win on the same matchup. Went straight to the full
Gauntlet rather than reading into one flipped game (RNG-cascade
sensitivity, same reasoning as Iteration 24's refinement pass).

**Full Gauntlet: 28/40 (70.0%), exactly tying the baseline.** Diffed the
two loss lists precisely rather than eyeballing percentages: 2 losses
fixed (`keepout` bot=A vs. `pure_cooperator`, `sittingducks` bot=A vs.
`pure_cooperator`), 2 new losses appeared (`tiny` bot=A vs.
`pure_cooperator`, `whereisthecheese` bot=A vs. `immediate_defector`),
the other 10 losses unchanged either way. Notably, `keepout` vs.
`immediate_defector` -- the matchup that originally motivated this whole
investigation -- is completely unaffected by the higher threshold: both
sides still lose, same as the `g_iter10` baseline before either attempt.

**REJECT** (net zero -- ties *WinPct*'s 60% floor comfortably but shows
no improvement over the running baseline, and the diff is a straight
swap rather than a directional fix). Reverted `src/bot/RobotPlayer.java`.

**Diagnosis:** two attempts at the same lever (blanket exploration-
distance cap, at 50% then 85% of map size) have now shown opposite
failure modes -- too aggressive costs real search time broadly (25), too
conservative doesn't reliably catch the actual "lone wanderer" case
either (26, still loses `keepout`/`immediate_defector` outright). A
*fixed threshold* fraction of map size isn't the right lever regardless
of where it's set; the earlier "Next" note's suggestion (condition the
retreat on some direct signal of danger -- e.g. distance to the last
sighted enemy, or only retreating after an actual near-death encounter
rather than a distance-only trigger) remains the untried, more
promising direction. Not attempting a third threshold value without a
qualitatively different trigger condition -- two data points already
show this axis alone doesn't reliably separate "safe" from "dangerous."

**Status:** back to the clean `g_iter10` baseline (70.0%,
`gauntlet/20260902-163148/`). No accepted changes since Iteration 24.

---

## Diagnostic: sittingducks vs. pure_cooperator -- map-asymmetric cat danger, not a bot bug

Fresh-territory trace per Step 4 (not yet examined this session). Unlike
`rift`/`keepout`, this one isn't close: by round 300 we're already at
12 alive babies vs. their 20, ending 7 vs. 14, behind on `catDamage`
(1700 vs. 1930) and `cheeseTransferred` (4095 vs. 4820) too.

Tallied cat kills by attacker and victim team across the full game: cat
`id10` killed **18 of our rats and 1 of theirs**; cat `id9` killed **10
of theirs and 0 of ours**. A heavily lopsided split -- one cat is almost
purely a threat to us, the other almost purely a threat to them, and
the one near us is nearly twice as lethal.

Since `pure_cooperator` runs the *same* economy/exploration code as
`bot` (only its backstab policy differs -- see the "Infrastructure fix"
entry), this isn't an asymmetry in either side's AI. It has to be a
genuine map-geometry effect: whichever cat happens to patrol nearer a
given King's side is simply more dangerous on this specific map. Maps
are guaranteed symmetric *in principle* (RULES.md), but this project's
own King-location-guess heuristic already documents the precedent that
"several maps turned out non-rotational" in BC22 -- not every map's
symmetry is as clean in practice as the guarantee suggests, and this
reads as the same class of issue playing out through cat danger instead
of King-position guessing.

**Not treating this as a fixable bug for now** -- no code change
attempted. If a future session wants to pursue it, the plausible lever
would be adaptive rather than reactive: detect an unusually lethal
nearby cat (e.g. track own-team death rate attributable to a specific
cat ID over a rolling window) and respond with more conservative
population/exposure near it, rather than anything that assumes cat
danger is symmetric across a match by default.

---

## Diagnostic: minimaze vs. pure_cooperator -- a third confirmation of the catDamage-vs-economy pattern

Fresh-territory trace. Same shape as `rift`/`keepout` vs.
`pure_cooperator`, but starker: we lead cheese (3215 vs. 2395),
`cheeseTransferred` (5810 vs. 4990), and population (23 vs. 16), yet
lose on points because `catDamage` favors the opponent 70 vs. 420 -- a
6x gap. Confirmed this isn't rat-vs-rat combat (`pure_cooperator` never
backstabs): only 49 total `RatAttack` events in the whole 2000-round
game, almost entirely against cats. Three independent maps now show
this same pattern against the same opponent -- a real, recurring
weakness in `catDamage` output specifically, not noise.

## Iteration 27 attempt — economy-gated idle cat-seeking; REJECTED (broad regression)

A more targeted version of Iteration 22's rejected idea: rather than
having idle Baby Rats seek `lastKnownCatLoc` unconditionally, gate it on
a new King-broadcast signal (shared array slot 5) -- only seek a
remembered cat when population is fully built (`builtCount >=
MAX_POPULATION`) and the economy isn't currently struggling, on the
theory that Iteration 22's broad regression came from paying the
cheese-search opportunity cost in every game when the payoff only
mattered in the ones we could actually afford it in.

**Smoke test** (`minimaze` vs. `pure_cooperator`, the motivating game):
flipped from a loss to a win.

**Full Gauntlet: 24/40 (60.0%), down sharply from 70.0%.**
`pure_cooperator` 65%->55%, `immediate_defector` 80%->65% -- both peers
dropped hard, with new losses spread broadly across unrelated maps
(`closeup` newly losing *both* sides, `sittingducks`/`whereisthecheese`
flipping from clean points losses to early eliminations, a new `tiny`
loss). Even the motivating `minimaze` matchup still lost on one side
despite the smoke-tested win on the other.

**REJECT** (Step 6.5, broad regression). Reverted
`src/bot/RobotPlayer.java`.

**Meta-diagnosis, now backed by three separate attempts at the same
underlying idea (Iterations 21, 22, 27):** "make idle Baby Rats spend
time seeking out cats" keeps failing regardless of the specific trigger
condition -- inert when gated too narrowly (21: HP-threshold flee,
never fired), broadly regressive when unconditional (22), and still
broadly regressive even with an economy-health gate that looked
well-reasoned on paper (27, `builtCount >= MAX_POPULATION` evidently
triggers far more often, and in far more contexts, than "comfortably
ahead and safe to spend a turn on this" actually means in practice --
a fully-built population doesn't mean cheese-search is no longer
valuable, since sources deplete and new rats still need to find fresh
ones continuously). **This specific lever (redirecting otherwise-idle
movement toward cats) looks like a dead end** for closing the `catDamage`
gap seen on `rift`/`keepout`/`minimaze` -- three attempts, three
different gating conditions, three failures. A fix for that gap, if one
exists, more likely needs to come from combat *efficiency* during
engagements that already happen (rather than seeking out more of them),
or should be set aside as an accepted limitation rather than continuing
to guess at trigger conditions for this same mechanism.

---

## Diagnostic: pipes and knifefight vs. pure_cooperator -- closing out the catDamage question

`pipes`: a different, broader shape than the other three -- `pure_cooperator`
leads on cheese, `cheeseTransferred`, *and* `catDamage` all at once, not
just the narrow catDamage-only gap. Only 2 total cat-combat events in
the whole 2000-round game, though, so this reads more as general
exploration/spawn-position variance on a corridor-constrained map
("pipes") than a new distinct mechanism.

`knifefight`: a **fourth** clean instance of the exact `rift`/`keepout`/
`minimaze` pattern -- we dominate cheese (4950 vs. 925) and
`cheeseTransferred` (7440 vs. 3420) by a wide margin, yet lose the game
because `catDamage` favors the opponent 610 vs. 1460 (2.4x).

**Checked whether the cat-engagement code has actually diverged between
`bot` and `pure_cooperator`** (they're supposed to share it, differing
only in backstab policy) rather than assuming it, given how central this
gap has become: diffed the cat-handling block of both files with
comments stripped. **Byte-identical.** Not a code-divergence bug.

**This changes the read on the whole investigation.** Since both bots
run the exact same combat decision logic, a recurring catDamage gap
between them can't be a decision-*quality* problem -- it has to be a
decision-*exposure* problem: whichever side's rats happen to path nearer
cats (map geometry, spawn corner, exploration RNG) ends up with more
engagements and more cumulative bite damage, independent of how good
either side's fight-or-flee logic is. This is the same underlying
phenomenon as the `sittingducks` map-asymmetry finding, just showing up
as a damage-output gap between identical opponents instead of a
lopsided-lethality gap between two different cats.

It also explains, retroactively, why Iterations 21/22/27 all failed:
none of them were actually fixing a *decision* -- `bot` was already
making the same in-combat decisions `pure_cooperator` makes. "Seek out
more cats when idle" was trying to force more *exposure*, but a
scripted detour toward one remembered location isn't a reliable way to
out-expose an opponent whose *natural, unmodified* exploration pattern
already happens to run through better cat territory on a given map.

**Conclusion: treating this catDamage variance as accepted Gauntlet
noise from here, not a further-pursuable bug.** Four maps, one
mechanism, three failed fix attempts, and now a code-identity check
confirming there's no decision-logic gap left to close. Redirecting
future investigation toward losses where `bot` is *not* already
economically dominant -- those are more likely to reflect genuine,
fixable decision-quality gaps rather than map-luck variance between two
bots running identical code.

**Status:** back to the clean `g_iter10` baseline (70.0%,
`gauntlet/20260902-163148/`). No accepted changes since Iteration 24.

---

## Diagnostic: remaining immediate_defector losses -- same known pattern, no new mechanism

`knifefight` (bot=A): same game already traced in depth during the
Iteration 19/20 investigation (population collapse to near-zero by
round 125). `closeup` (bot=A): 18 vs. 11 rat deaths in the first 300
rounds, 143 `RatAttack` events vs. only 17 cat-related ones -- the same
rat-vs-rat attrition mechanism as `knifefight` and `keepout`, not a new
finding. This is the same functional area behind 9 rejected attempts
this session (Iterations 13-20's combat-targeting tuning, plus 25/26's
lone-wanderer distance-cap attempts). Not attempting a repeat
implementation without a genuinely new angle -- the untried lead
remains the danger-conditional retreat trigger noted after Iteration 26
(condition on an actual signal of danger, e.g. distance to last sighted
enemy or a near-death HP event, rather than a distance-only threshold),
which needs real design work before another attempt, not a quick
variation.

---

## Iteration 28 attempt — raise MAX_POPULATION well past 25; REJECTED (boom-bust overbuild)

A genuinely fresh lever, not a variation on the 9 already-rejected
combat/exploration attempts: confirmed via replay that `builtCount`
(cumulative-ever-built, not a live census) hit exactly 25 by round 300
in the `closeup` vs. `immediate_defector` loss and the King *never
built again* for the remaining 800 rounds, even as population crashed
to 1-2 alive. The code's own comments already named this exact risk
(BC22's cumulative-vs-live pitfall) but it had never actually been
tested. Raised `MAX_POPULATION` from 25 to 200, on the theory that the
existing `RESERVE` cheese throttle (economic) and `findBuildLocation()`
returning null with no open tile (spatial -- what actually solved
`knifefight`'s original spawn-gridlock, not the cumulative count) should
be sufficient rate-limiters on their own.

**Smoke test** (`closeup` vs. `immediate_defector`, the motivating
game): **worse**, not better -- died at r380, down from the baseline's
r1095. Traced it: population exploded to 37 alive by round 50 (cheap
early build cost, `RESERVE` only checks whether *this one* build is
currently affordable, not whether the resulting population is
sustainable), crashing cheese to near-zero by round 75 and keeping it
pinned there for the rest of the tracked window -- a boom-bust
overbuild, not the intended "replace losses as they happen" behavior.

**REJECT without a full Gauntlet run** (Step 6.5 -- the motivating game
itself got unambiguously worse). Reverted `src/bot/RobotPlayer.java`.

**Diagnosis:** the boom happens by round 50, long before any moderate
cap increase (e.g. 35-40) would even become the binding constraint --
so this isn't really about *which* cap value to pick, a lower raise
would likely hit the same boom-bust for the same reason. The real gap is
that `RESERVE` is a flat threshold that doesn't scale with population:
it can't tell "building the 5th rat this round is fine" from "building
the 30th rat this round, on top of 29 others built in the last 50
rounds with none delivering cheese yet, is not." A fix needs either a
build-rate limiter (not just an affordability check) or a `RESERVE` that
scales with `builtCount`, so the King naturally throttles itself as
population grows rather than spending every round it technically can
afford to. Untried; the original diagnosis (cumulative cap silently
becomes a starvation lockout in high-attrition games) is still correct
and still worth fixing -- just not via a bare number change.

---

## Iteration 29 attempt — scale the build reserve with population; REJECTED (partial improvement, still net negative)

Direct follow-up targeting Iteration 28's diagnosed mechanism: kept
`MAX_POPULATION` high (200) but replaced the flat `RESERVE` in the
build-affordability check with `buildReserve = RESERVE + 10 *
builtCount`, so the required cheese buffer grows as population grows,
throttling build *rate* instead of only capping the eventual total.
Left the desperate-trigger's `RESERVE` unchanged at a flat 150 to avoid
conflating two different concerns.

**Smoke test** (`closeup` vs. `immediate_defector`): improved over
Iteration 28 (died at r835, up from r380) but still worse than the
`g_iter10` baseline (r1095). Traced it: population still overshoots to
36 by round 50 (same as Iteration 28 -- at that population, `builtCount`
~30 means `buildReserve` ~450, which a ~2000-cheese King can still
easily clear, so the scaling reserve doesn't meaningfully throttle the
*initial* burst, only later spending). Notably, for the rest of the
midgame we actually win the fight decisively -- `catDamage` 1190 vs.
340, `cheeseTransferred` consistently ahead -- before eventually
collapsing near the end anyway.

**REJECT without a full Gauntlet run** (motivating game still net worse
than baseline). Reverted `src/bot/RobotPlayer.java`.

**Diagnosis:** the scaling reserve is the right *idea* but starts from
too low a base and grows too slowly to catch the initial overbuild --
by the time it meaningfully bites, population has already overshot past
what the early-game economy (still ramping up, few rats delivering
cheese yet) can sustain. A steeper scaling factor, or a *separate* cap
specifically on early-game build rate (e.g. a hard per-round build limit
for the first N rounds, independent of cheese affordability entirely)
would likely need to replace or supplement this. Also worth noting: even
mid-attempt, we were *winning* the fight for hundreds of rounds
(catDamage/cheeseTransferred both favored us) before still losing --
this population-rebuilding thread has real upside once the initial
overbuild is actually fixed, not a dead end like the catDamage-seeking
attempts.

**Status after this investigation arc (Iterations 25-29, all rejected):
still the clean `g_iter10` baseline, 70.0%.** Two genuinely promising,
partially-understood threads remain open for a future session: (1) a
danger-conditional retreat trigger for the "lone wanderer" exploration
problem (not a distance threshold), and (2) a properly-tuned build-rate
throttle that catches the early-game overbuild specifically (not just a
reserve that scales too slowly). Both are real, replay-confirmed
mechanisms with failed-but-informative first attempts, not
speculation -- worth returning to with fresh design rather than more
parameter guesses.

---

## Iteration 30 attempt — hard per-round build cooldown; REJECTED (still net negative, new mechanism found)

One more direct attempt at Iteration 28/29's target: instead of a
cheese-based throttle at all, a hard cooldown between builds
(`BUILD_COOLDOWN_ROUNDS = 4`), independent of cheese affordability, on
the theory that the early-game burst (36 rats by round 50) needed a
*rate* cap that no cheese-based check could express cleanly.

**Smoke test** (`closeup` vs. `immediate_defector`): the initial ramp
was genuinely fixed -- population growth now matches roughly the
original pace (25ish by round 50-75, not 36) -- but the game still
ended worse than baseline, dying at r430. Traced it: this time the
population/combat picture is *strong* right up to the end -- ahead on
population (18 vs. 9), `catDamage` (1090 vs. 330), and
`cheeseTransferred` (635 vs. 575) at round 425 -- but the King's own
cheese hit 0 by round 375 and stayed there while its unconditional 2/
round upkeep (RULES.md) bled it out, dying roughly 55 rounds later
(600 HP / 10 HP-loss-per-round-unpaid ~= 60 rounds, consistent).

**REJECT without a full Gauntlet run** (still net worse than baseline
on the motivating game). Reverted `src/bot/RobotPlayer.java`.

**A third, distinct mechanism in this same investigation, not a repeat
of 28/29's finding:** the King correctly *stopped* building once cheese
approached `RESERVE` (no further overspend), but by then cumulative
spending had already left too thin a margin -- the flat 2/round upkeep
alone (with no more builds happening at all) was enough to finish the
job. This is the *original* Iteration 1 problem ("King was spending
itself into starvation") resurfacing in a new form: `RESERVE=150` was
calibrated for the old, slower/lower spending pattern, and evidently
isn't a large enough buffer once the King is allowed to build more
aggressively for longer. Notably, the army itself was *winning*
decisively when the King died -- this isn't a combat-quality problem at
all, purely a King-side cash-management one.

**Closing this investigation arc for this session.** Three attempts (28
unbounded cap, 29 scaling reserve, 30 hard cooldown) found three
distinct, real mechanisms (cumulative-cap lockout, boom-bust overbuild,
King-bankruptcy-despite-army-strength) but none produced a net
improvement. The population-cap system has more moving parts interacting
than a single-parameter fix can address -- a proper solution likely
needs a fundamentally different building policy (e.g. hysteresis: stop
well above `RESERVE` and don't resume until cheese has recovered with
real margin above it, rather than a hard threshold either direction) --
real design work for a future session, not another quick guess. Back to
the clean `g_iter10` baseline (70.0%, `gauntlet/20260902-163148/`). No
accepted changes since Iteration 24.

---

## Iteration 31 attempt — hysteresis + build cooldown together; REJECTED (identical to Iteration 30, wrong mechanism)

Real design attempt rather than another parameter guess: paused
building entirely once cheese dropped to `RESERVE` (not just "this one
build is unaffordable"), only resuming once cheese recovered to `2x
RESERVE` -- real margin, not the same edge it just fell below. Tested
hysteresis alone first: **identical result to Iteration 28's unbounded
cap (r380)** -- confirms hysteresis doesn't touch the *initial* burst at
all, since starting cheese (2500) is high enough that dozens of builds
happen before cheese ever dips below `RESERVE` for the first time, so
the pause-and-resume logic never even engages until it's already too
late. Added Iteration 30's per-round build cooldown back in alongside
it, to cap the rate everywhere including before the first dip.

**Smoke test with both combined: r430 -- identical to Iteration 30's
result**, down to the exact same round number. Hysteresis added nothing
on top of the cooldown alone.

**REJECT without a full Gauntlet run.** Reverted
`src/bot/RobotPlayer.java`.

**This pins down exactly why the whole thread has failed four times
now:** hysteresis controls *when building resumes*, but the King isn't
dying from resuming too early -- by the time cheese first reaches
`RESERVE`, the population commitment is already locked in (rats already
built, already fighting, already needing to be fed indirectly through
the economy), and the flat 2/round King upkeep keeps draining regardless
of whether a single additional rat is ever built again. **No build
*policy* change -- cap value, reserve scaling, cooldown, or resumption
hysteresis -- addresses a King that's already committed past what its
income can sustain.** The actual fix, if one exists, would need to act
*before* that commitment happens (e.g. a much more conservative
early-game pace matched to actual measured income rather than a fixed
starting-cheese-derived affordability check) or accept smaller
populations as a deliberate tradeoff rather than trying to extract more
army out of the same starting economy.

**Closing this investigation arc for the session** (Iterations 28-31,
four attempts, four distinct confirmed mechanisms, zero net
improvements). Recommend a future session treat this as a "redesign the
King's spending model from scratch" project, not a "fix the current
one" task -- everything in the current model (flat cap, flat reserve,
flat cooldown, flat hysteresis margin) has now been shown to have a
real failure mode once population and combat losses interact with it in
a high-attrition matchup.

---

## Iteration 32 — permanently redirect a rat once its exploration heading is confirmed stuck; accepted, 75.0%

**User-reported priority, not self-directed:** Baby Rats tend to get
stuck in one small region rather than moving freely to and from cheese.
Investigated directly rather than continuing the previous Step 4 loop.

Generated a fresh clean-baseline replay (`bot` vs. `pure_cooperator` on
`rift`, a large 60x30 open map) and used `tools/replay-dump.sh --robot`
to trace individual rat lifetimes. `id11086` (spawned round 2 at
`(4,23)`) was tracked for the *entire* 2000-round game and never once
carried cheese (`max cheese=0` across all 1998 tracked rounds) --
confined the whole time to a roughly 4x7 tile box (`x: 0-3, y: 23-29`).
Its early movement showed exactly what was happening: steady progress
toward `(0,29)` -- the map's exact corner -- reached by round ~15, then
permanent in-place oscillation for the remaining ~1985 rounds.

**Root cause:** `preferredExploreDir` (Iteration 4) is a fixed, ID-based
heading a robot commits to for its entire lifetime and "returns to...
whenever unblocked" -- a deliberate design choice to keep the
population fanned out, but one that never accounted for map boundaries.
A rat whose fixed heading points at a nearby edge or corner reaches it
quickly, and every subsequent round `explore()` unconditionally retries
the *same* heading -- the existing stuck-cycle escape (`tryMove()`) can
nudge it one tile away for a turn, but next round it immediately
re-attempts the doomed heading and re-hits the same boundary. A
one-off escape move can't fix a heading that's permanently wrong for
that robot's spawn position; only replacing the heading does.

Also fixed a related latent bug found while reading this code:
`Direction.allDirections()` returns 9 values including `CENTER`
(`tryMove` treats `CENTER` as an immediate no-op), so roughly 1 in 9
robots got an initial preferred heading that never did anything at all,
purely from `rc.getID()` arithmetic. Switched the initial assignment
and all fallback direction picks in `explore()` to `ALL_DIRECTIONS`
(the 8 real headings, already defined for Iteration 24's stuck-escape
shuffle).

**First attempt (reused the shared `stuckCycles` counter) regressed
broadly:** 23/40 (57.5%), down from 70.0%, despite fixing the
motivating `rift` game cleanly (turned a loss into a win with `catDamage`,
cheese, and `cheeseTransferred` all flipping in our favor). Root cause:
the shared counter (tracked once per round, also driving `tryMove()`'s
one-turn escape for `deliverCheese()`/`collectCheese()`) fires on *any*
2-tile repeat regardless of cause -- a rat briefly jammed delivering
cheese near a crowded King (common and totally benign) would get its
perfectly fine exploration heading needlessly reassigned the next time
it happened to call `explore()`, undermining the population fan-out
Iteration 4 relied on.

**Fix:** a dedicated explore-call-to-explore-call position history,
completely separate from the shared per-round one -- only repeated,
consecutive *exploration* stalls (not incidental blocking during
delivery/collection) trigger a heading reassignment.

**Re-verified the motivating case still fixed:** `rift` smoke test
still a clean win. Re-traced `id11086` in the new replay: instead of
2000 rounds trapped near the corner, it covered a 20x11 tile area in
just 80 rounds before dying to a cat while actively exploring far from
home -- a normal gameplay risk, not a bug, and a night-and-day
difference from being permanently useless.

**Full Gauntlet: 30/40 (75.0%), up from 70.0%.** `pure_cooperator`
60%->70%, `immediate_defector` steady at 80%. Diffed the loss lists
precisely: 8 losses fixed (`keepout`/coop-A, `knifefight`/coop-B,
`pipes`/coop-A, both `rift` losses, `whereisthecheese`/coop-A,
`knifefight`/id-A, `keepout`/id-B), 6 new losses appeared (`tiny`
newly weak against both opponents on both/one side, `closeup`/coop-A,
`whereisthecheese`/coop-B and `knifefight`/id-B both side-flips from
already-losing maps), 4 unchanged. Net +2 wins.

Traced the most concentrated new weakness (`tiny`, 3 new losses) before
accepting: a genuine, fast attrition loss (population 21->1 within 150
rounds, `catDamage` already past 1000 for both sides by round
200-350) -- `tiny` is a small, cat-dense map, and the believable
mechanism is that rats now explore more aggressively into it sooner
instead of being passively kept safer by the old stuck-near-spawn bug.
A real, bounded trade-off with a comprehensible cause, not a sign of a
new fundamental bug.

**ACCEPT.** Exceeds *WinPct* comfortably, exceeds the running baseline,
and the diff is broad-based improvement with a believable, bounded new
weak spot, not a concentrated regression. Snapshotted as `src/g_iter11/`;
new baseline `gauntlet/20260902-194330/`.

**Next.** `tiny` is now the standout weak map (worth a future trace to
see if it's fixable, e.g. gating early-exploration aggressiveness on a
sensed cat, or whether it's an inherent tight-map tradeoff). The three
still-open threads from before this fix remain queued: a
danger-conditional retreat trigger for exploration safety generally
(now partially addressed by this fix, but not the same mechanism as
Iterations 25/26's rejected distance-cap attempts), a full King
spending-model redesign (Iterations 28-31), and `catDamage`-vs-
`pure_cooperator` is closed out as accepted map-luck noise.

**Standing habit change (user request):** run the vs-old-bots comparison
(`OPPONENTS="g_iter1" tools/gauntlet.sh` + `track_vs_old_bots.py` +
`plot_vs_old_bots.py`) after *every* accepted iteration from now on,
not periodically -- added to memory as
`update-vs-old-bots-every-accept`. Backfilled for this iteration:
`g_iter11` vs. `g_iter1`, `gauntlet/20260902-195419/` -- **16/20
(80.0%)**, continuing a gradual decline from `g_iter7`'s 90.0% and
`g_iter10`'s 85.0%. Losses: both sides of `tiny` (consistent with this
iteration's own new `tiny` weakness, see above) and both sides of
`whereisthecheese`. Not alarming on its own -- 20-game samples against
a single frozen old snapshot are noisy, and the primary peer-roster
signal has been climbing over the same span (67.5%->70.0%->75.0%) -- but
worth watching if the trend continues over the next couple of accepts.

---

## Diagnostic: tiny vs. pure_cooperator -- the new weakness is the already-known economy issue, not a new one

Traced `tiny` (20x20, genuinely small): both Kings spawn close together
near the top (`(7,2)`/`(12,2)`), both cats sit south (`(2,15)`/`(16,15)`),
and the only two cheese mines (`(6,9)`/`(13,9)`) sit squarely between
them -- there's no way to reach the map's entire cheese supply without
passing through cat territory. Combat starts almost immediately (round
33) and is heavy for both sides (40 cat-attack events and 208
`RatAttack` events in just the first 300 rounds). Death tally through
round 550: 25 ours vs. 18 theirs -- real but not overwhelming.

**The decisive asymmetry is that our population hit zero and stayed
there, while `pure_cooperator` kept a standing population of ~7 the
whole game.** Checked why: our King had already hit exactly 25 builds
(`MAX_POPULATION`) well before the crash. This is not a new bug from
Iteration 32 -- it's the identical cumulative-cap starvation lockout
already diagnosed and left unsolved across Iterations 28-31 (root cause:
`builtCount` is cumulative-ever-built, and once it hits the cap the King
can never rebuild losses no matter how low live population drops).
`tiny` is newly exposed to it specifically *because* Iteration 32 fixed
exploration -- rats that used to get passively trapped near spawn on
this map (accidentally avoiding the cat-dense middle) now actually reach
it, take real combat losses, and then hit a King that's already spent
its entire cumulative build allowance with no way to recover.

**Not re-attempting the economy fix right now** -- four prior attempts
(28 unbounded cap, 29 scaling reserve, 30 build cooldown, 31 hysteresis)
already showed this needs a genuine spending-model redesign, not another
parameter guess, and `tiny` doesn't add a new angle on *how* to fix it,
just a second confirmed instance of *why* it needs fixing. Recording
this now so a future redesign attempt has two independent motivating
maps (`closeup` and `tiny`) to validate against instead of one.

---

## Iteration 33 attempts — fix cheese held forever without delivery; four attempts, all REJECTED

Traced the new `closeup` loss (task, not Step 4 self-direction) with
`--robot`: `id12609` carried `cheese=20` for **340+ rounds** (round 519
through at least 859), cycling through 4 distinct tiles `(17,5)-(17,8)`
near a maze pocket, `deliverCheese()` never once succeeding. Root cause:
`moveToward()` recomputes direction-to-King fresh every turn, and the
shared `stuckCycles` escape (`tryMove()`) only catches an *exact*
2-round-ago repeat -- a clean period-4 cycle never matches "2 rounds
ago" at all (A->B->C->D->A..., 2 rounds prior to C is A, never C), so it
silently never fires. This is directly on-topic for the user's original
"stuck in a small region" report -- Iteration 32 fixed the exploration
path, but this is the same class of bug in the delivery path, which
Iteration 32 didn't touch.

Four attempts, in order, each one a reasoned refinement of the last, and
each one **worse** than the last -- a real signal to stop, not push
harder:

1. **Widen the shared detection window** (match any of last 2-4 rounds,
   not just exactly 2) using the existing single-hop escape. Full
   Gauntlet: **67.5%, down from 75.0%.** Diagnosis: checking 3 reference
   points instead of 1 is inherently more likely to false-positive on
   ordinary non-cyclic movement, and this fires for *every* caller
   (`explore()`'s own inner movement, `collectCheese()`, not just
   delivery).
2. **Raise the confirmation threshold to 3** (same shared widened
   window). Full Gauntlet: **72.5%**, better than #1 but still down from
   baseline. `knifefight` (a known tight, crowded-spawn map) still showed
   collateral false positives.
3. **Scope to `deliverCheese()` alone** (dedicated 4-call-deep position
   history, mirroring Iteration 32's `explore()`-specific pattern) plus a
   **sustained 6-round random detour** once confirmed stuck, since a
   single hop isn't enough before the very next call recomputes
   direction-to-King and walks straight back into the same pocket. Full
   Gauntlet: **67.5%**, no better than #1 despite being properly scoped
   -- diagnosis revised: a crowded King with several rats jostling for
   the same nearby tiles can incidentally revisit a tile within a few
   rounds *without* being in a real maze trap, and forcing 6 wasted
   rounds on that false positive is far more costly than the old
   single-hop escape ever was.
4. **Exclude "near the King" from stuck-tracking entirely** (skip the
   whole apparatus within `distanceSquared <= 20` of the King, on the
   theory that congestion there resolves itself) plus a shortened
   4-round detour. Full Gauntlet: **60.0%, the worst of all four** --
   `keepout` lost on all four possible pairings.

**All four reverted. Nothing accepted.** `src/bot/RobotPlayer.java` is
back to the clean `g_iter11` state.

**Honest assessment:** the bug is real and confirmed (340+ rounds
undelivered cheese is not in dispute), but four attempts each getting
*worse* than the last -- not converging, not oscillating around
baseline, actually trending down -- means the mental model behind these
fixes is missing something. Possibilities not yet investigated: (a) the
Gauntlet's per-game results may be more RNG-cascade-sensitive at this
resolution than these comparisons assume, since each code change shifts
every subsequent robot's random draw sequence for the whole game --
40-game samples may not be enough to reliably rank these four variants
against each other, only against the much larger swing Iterations 24/32
produced; (b) the "sustained detour" mechanism itself may have an
un-diagnosed side effect (e.g. interacting badly with the priority order
in `runBabyRat()` -- a rat mid-detour still re-checks
`deliverCheese()` first every subsequent call, so the detour and the
delivery-seeking logic may be fighting each other in a way not
accounted for). **Not attempting a fifth guess.** If this is picked up
again, start by instrumenting a specific map (`indicatorString` showing
detour state) and watching it happen live, rather than reasoning from
aggregate Gauntlet deltas alone -- four rounds of "plausible-sounding
refinement, Gauntlet says worse" is a sign the debugging loop itself
needs to change, not just the parameters inside it.

**Follow-up before moving on:** checked the actual terrain between the
King (`(1,1)`) and the stuck location (`(17,6)`) with a wider
`--terrain` view -- a genuine maze of wall/dirt corridors, not a simple
local pocket. This reframes the whole investigation: the four failed
attempts weren't failing because of bad parameter choices, they were
failing because **greedy direction-toward-target movement (recompute
`directionTo()`, sidestep if blocked) cannot reliably solve maze
navigation at all**, regardless of how the stuck-detection is tuned --
no amount of "detect stuck, then escape" patching fixes a movement
*strategy* that has no notion of a path, only a heading. A real fix
needs local pathfinding (BFS over `senseNearbyMapInfos()`'s visible
passability, similar in spirit to what the engine's own cat AI gets via
`getBfsDir()` -- not exposed to player bots, so it'd need to be
implemented from scratch). That's a legitimately larger project, not a
quick iteration -- noting it as an architectural limitation rather than
continuing to patch around it.

---

## Diagnostic: whereisthecheese (botB) -- a third confirmed instance of the cumulative-cap lockout

Traced `whereisthecheese` vs. `pure_cooperator` (bot=B side): our
population (as the King-losing side) crashed from 25 to 0 within 300
rounds while `pure_cooperator` stabilized at 7. Confirmed our King had
already built exactly 25 (`MAX_POPULATION`) before the crash, then could
never rebuild -- identical mechanism to `closeup` and `tiny`. Three
independent maps now confirmed, making the King's build-policy the
single most-evidenced remaining weakness in the bot.

## Iteration 34 attempt — trend-based build throttle (real redesign, not a parameter guess); REJECTED after three internal fixes, still net negative

Given four quick-parameter guesses already failed this exact problem
(Iterations 28-31), attempted a genuine redesign instead: replace the
flat cap with a throttle tied to the *actual observed cheese trend*
rather than a point-in-time snapshot or a fixed count. Design: allow
unconstrained building up to `INITIAL_RAMP_POPULATION` (matching the old
cap exactly, so early-game behavior is unchanged from the accepted
baseline), then require the rolling ~50-round cheese trend to be
non-negative before continuing past that -- throttling immediately when
it turns negative and un-throttling automatically once it recovers, not
a permanent latch like `economyStruggling`.

**Found and fixed three real bugs in the design itself before it was
even Gauntlet-tested**, each caught by smoke-testing against the actual
motivating maps rather than jumping straight to the full Gauntlet:

1. **First cut (trend alone, no absolute floor): all three motivating
   maps got *worse*, not better** (`whereisthecheese` died at r350, even
   faster than the r943 baseline loss). Diagnosis: cheese declines during
   *any* active population growth, healthy or not -- a pure trend check
   throttles legitimate, sustainable growth as readily as a real
   overspend. Fixed by requiring the decline to *also* leave cheese below
   `3x RESERVE` before counting as a throttle signal.
2. **That fix produced byte-identical smoke-test results to the first
   cut** (same exact round numbers) -- a strong signal the throttle logic
   wasn't even being exercised. Traced it: `INITIAL_RAMP_POPULATION` was
   set to 15, not 25 -- lower than the old cap's proven-working ramp
   target, so population was crashing before the ramp phase even ended.
   Fixed by matching the old cap's exact value (25).
3. **Still byte-identical results.** Checked `builtCount` directly in the
   new replay: 39 builds -- population *did* exceed 25, exploding to 37
   alive by round 50, reproducing Iteration 28's exact original boom-bust
   mistake. Root cause: `buildThrottled` (a `boolean` field) defaults to
   Java's `false`, and the code read `builtCount < RAMP || !buildThrottled`
   -- before the trend detector's first 50-round checkpoint ever fires,
   `!buildThrottled` is unconditionally `true`, so building past the ramp
   threshold was *already unthrottled* from round 1, with nothing
   pacing it until 50 rounds' worth of unconstrained growth had already
   happened. Fixed by defaulting `buildThrottled = true` (stay capped
   until a healthy trend is *positively confirmed*, not just absent of a
   detected problem).

**With all three fixes: real, different behavior finally confirmed**
(`whereisthecheese` flipped to a clean win in the smoke test, `closeup`/
`tiny` showed different round counts than before, `knifefight` still won
cleanly -- no gridlock regression, the original reason `MAX_POPULATION`
existed).

**Full Gauntlet: 24/40 (60.0%), still down from 75.0%.** A new pattern
appeared: many very fast eliminations (400-800 rounds, vs. the
baseline's mostly r2000 point-decisions or 900-1200-round eliminations)
spread broadly -- `closeup` now loses all four pairings, `tiny` three of
four, both with unusually fast round counts. Broader than the three
originally-targeted maps.

**REJECT.** Reverted `src/bot/RobotPlayer.java`.

**Honest assessment:** this was a real design, not a guess, and it did
partially work (`whereisthecheese` improved) -- but the net effect
across the full map set is still clearly negative, with a concerning new
fast-elimination pattern that wasn't present in any prior attempt this
session. Given how much surface area the King's spending model touches
(build rate, combat sustainability, `desperate`/backstab-hunt triggering
via the same `RESERVE` constant, digging behavior when boxed in) each
change to it seems to trade one map's problem for a different map's
regression, suggesting the *shared* `RESERVE`/build logic has more
implicit coupling between these systems than is safe to change
piecemeal. **Five total attempts now across Iterations 28-34, all
rejected.** Not attempting a sixth today. If revisited, the design
should probably decouple `RESERVE` (used for `desperate` triggering) from
whatever governs the build-rate throttle specifically, rather than
layering more logic onto the same shared constant and King loop.

---

## Diagnostic: minimaze (botB) -- comprehensive underperformance, not a single fixable mechanism

Last untraced loss in the `g_iter11` baseline. Different shape from
everything else this session: population gradually stabilizes at 5-6
(vs. `pure_cooperator`'s steady 20-22) rather than crashing to 0 --
economies on both sides keep growing the whole game (cheese and
`cheeseTransferred` both climb steadily to round 2000 for both teams),
and `pure_cooperator` simply leads on every single metric
(population, cheese, `cheeseTransferred`, *and* `catDamage`) -- not a
narrow points-margin loss like `rift`/`keepout`/earlier `minimaze`
traces, and not a sudden collapse like the economy-cap games. Reads as
accumulated small differences (early combat/exploration variance)
compounding over a full 2000-round game rather than one fixable
mechanism -- the same broad "map-luck exposure between two bots running
identical code" territory already closed out for the `catDamage`
investigation. Not pursuing further without a fresh, specific lead.

**All losses in the `g_iter11` baseline (`gauntlet/20260902-194330/`)
have now been traced at least once this session.** Summary: `rift`/
`keepout`(coop)/earlier-`minimaze` = accepted map-luck `catDamage`
variance (closed out); `sittingducks` = map-asymmetric cat danger
(closed out); `closeup`/`tiny`/`whereisthecheese` = the King
cumulative-build-cap lockout (five failed fix attempts, needs a bigger
redesign); `closeup`/`knifefight`/`keepout` vs. `immediate_defector` =
rat-vs-rat attrition (nine failed fix attempts across the session,
needs a fresh angle); the delivery-path stuck-cheese bug = needs real
pathfinding (architectural limitation). No further quick wins visible
in the current loss set without one of these larger projects.

---

## Iteration 35 — Bug2 wall-following navigation for cheese delivery; accepted (mechanistic), 75.0%

Took the architectural limitation identified in Iteration 33 and actually
built the fix, guided by **BC22's `RESEARCH.md` section 2** (re-read in
full this session on user instruction). That document records the
cross-year convergent solution every strong Battlecode team arrives at:
start with textbook A-star/BFS, blow the bytecode budget, and end up on
bug-navigation -- greedy step toward the target, boundary-following when
blocked, a memory-based escape for concave obstacles, and a randomized
tie-break as a last-resort safety valve. That's precisely the shape of
problem Iteration 33's four failed patches were flailing at.

**Implementation** (`moveToward`): Bug2-style. Try the direct step; when
blocked by terrain, commit to tracing the obstacle boundary, rotating
consistently in one per-robot direction (`rc.getID() % 2`, not a fixed
compass order -- BC22's largest recurring bug class); resume direct
movement only once strictly closer to the target than when the obstacle
was hit (the concave-escape condition); bail out and flip rotation
direction after 16 rounds of fruitless following. No dynamic collections
or allocation, per `RESEARCH.md` section 10.

**Three real bugs found and fixed during development**, each caught by
Gauntlet measurement rather than reasoning:

1. **Applied to every `moveToward()` caller: 65.0%** (down from 75.0%),
   concentrated on `minimaze` (lost all four pairings). Bug-navigation's
   premise is monotonic progress toward a *stationary* goal -- the
   closest-distance memory is what escapes concave obstacles. A moving
   goal (chasing an enemy in `engage()`, fleeing a cat, re-picking the
   nearest cheese tile as tiles deplete) invalidates that memory every
   round, so the state thrashes and the wall-following scan displaces the
   responsive sidestep combat actually needs. **Scoped to
   `deliverCheese()`** -- the only caller with a genuinely fixed target
   (the King), and exactly where the confirmed 340-round stuck-cheese bug
   was traced.
2. **Scoped, but still 60.0%** -- worse. Found a genuine logic inversion:
   the "strictly closer than ever before" test gated *the direct-move
   attempt itself*, not just the exit from wall-following. So a rat pushed
   backwards (delivery congestion, or the King relocating to flee a cat)
   could never satisfy it again and would wall-follow forever -- the exact
   permanently-stuck failure class this iteration exists to remove,
   reintroduced in a new form. Restructured to proper Bug2: always attempt
   the direct step when not committed to a trace; the distance memory only
   governs *leaving* the trace.
3. **Corrected, but 62.5%** -- still below baseline, `minimaze` still
   losing all four. Root cause: **most blockers here are other rats, not
   terrain.** Bug-navigation assumes static obstacles; committing to a
   multi-round boundary trace because an ally stood in the way for one
   round is strictly worse than the old one-step sidestep, and delivery
   traffic near the King is full of exactly that. Gated boundary-tracing
   on `senseMapInfo(ahead).isPassable()` being genuinely false --
   `RESEARCH.md` section 2 names this distinction directly ("treat
   friendly units as soft, not hard, obstacles"). **Recovered 12.5
   points**, confirming the diagnosis.

**Full Gauntlet: 30/40 (75.0%) -- exactly tied with the `g_iter11`
baseline.** `pure_cooperator` 70%->65%, `immediate_defector` 80%->85%.
Ran a second full Gauntlet on identical code and got a **byte-identical
result** (same 30/40, same ten losses, same round numbers) -- a useful
methodological finding in its own right: **the Gauntlet is fully
deterministic for identical code**, so repeat runs add no information and
all observed run-to-run variation this session was genuinely
cross-version RNG-cascade, not sampling noise. Don't re-run to "check
variance" again.

**Diff shape:** `closeup` -- the confirmed maze map that motivated this
whole thread -- is now **won on both sides** (was lost on both), with
`cheeseTransferred` 2010 vs. 715 and our King's cheese holding at
1290-1700 all game instead of draining to zero. Offsetting that,
`knifefight` newly lost both `pure_cooperator` sides; traced it, and it's
a close, gradual points decision (`catDamage` 1420 vs. 1480, both sides
healthy at round 1900) -- the same attrition/map-luck variance already
characterized elsewhere, not a new collapse mode.

**ACCEPT, on mechanistic grounds** (Step 6.4: engaged-as-designed with a
concrete account, even absent an aggregate flip). Stated plainly: this
does **not** improve the win rate -- it is exactly break-even. It's
accepted because (a) it fixes the specific, user-prioritized failure the
session was asked to target (rats stuck in a small region, not delivering
cheese), verified directly in replay rather than inferred; (b) the
offsetting losses are close points decisions, not new failure modes; and
(c) it replaces a structurally-incapable movement strategy with real
pathfinding that later work can build on, rather than another heuristic
patch on top of one that provably can't solve mazes. Snapshotted as
`src/g_iter12/`; new baseline `gauntlet/20260902-225556/`.

**Next.** The natural follow-on is extending bug-navigation to
`collectCheese()` -- currently excluded because cheese targets move as
tiles deplete, but a *sticky* target (commit to one cheese tile until
reached or depleted, per BC22's `LEARNINGS.md` "a sticky/committed target
beats recompute-nearest-every-round," one of that project's single
largest wins) would make it a fixed target and unlock the same fix there.

---

## Iteration 36 attempt — sticky cheese target + bug-nav for collection; REJECTED

Direct follow-on from Iteration 35's own "Next" note, combining two BC22
lessons: `LEARNINGS.md`'s sticky-target pattern (a Miner recomputing
"nearest lead beacon" every round ping-ponged between similar beacons,
"wasting many rounds moving without ever mining" -- committing to one
was worth 60.3%->69.7% there) and Iteration 35's finding that
bug-navigation only works for *fixed* targets. `collectCheese()` had the
identical recompute-every-turn structure, so making it sticky should fix
the ping-pong *and* unlock pathfinding for collection.

Implemented: remember the chosen cheese tile; keep it until picked up or
sensed depleted; bound the commitment at 40 rounds so an unreachable
tile can't recreate the old camping bug; `useBugNav=true` now that the
target is fixed.

**Smoke test** (`knifefight` vs. `pure_cooperator`, one of the two maps
Iteration 35 regressed): flipped back to a win. **Full Gauntlet: 28/40
(70.0%), down from 75.0%.** `pure_cooperator` 65% (unchanged),
`immediate_defector` 85%->75%. New losses spread across `tiny` (both
coop sides), `pipes`, `rift`, `knifefight` (both `immediate_defector`
sides), `whereisthecheese` (both `immediate_defector` sides).

**REJECT.** Reverted to `g_iter12`.

**Diagnosis:** unlike delivery (one fixed King, always worth reaching),
cheese tiles are contested and deplete -- several rats committing to the
*same* tile, or holding a 40-round commitment to a tile another rat
empties first, wastes more than the ping-pong it prevents. BC22's Miner
case differs in a way that matters: lead beacons there were large and
long-lived, so commitment paid off; cheese tiles here are small and
frequently consumed mid-approach. A future attempt would need
per-tile claim deconfliction (shared-array or ID-based partitioning) so
rats commit to *different* tiles, not the same one -- untried.

---

## Infrastructure fix — vs-old-bots roster was tracking only g_iter1, not every 10th snapshot

User caught this: "Why didn't g_iter12 play against g_iter11 in the
old-bots graph? I thought we were playing against a gauntlet of every
10th old bot." Correct -- BC22's `tools/track_vs_old_bots.py` documents
the roster explicitly as `g_iter1 g_iter11 g_iter21 g_iter31 ...`. When
ported, the usage example was hardcoded to `OPPONENTS="g_iter1"` because
that was the only snapshot old enough at the time, and never revisited
once `g_iter11` was accepted -- so the chart kept tracking a single
reference point after a second one existed.

Fixed properly rather than just re-running: added
`tools/track_vs_old_bots.py --roster`, which *derives* the every-10th
list from the snapshots that actually exist (excluding the current one),
so it can't silently go stale again. Backfilled `g_iter12` against the
full roster (dropping the incomplete single-opponent row first):
**vs. `g_iter1` 16/20 (80%), vs. `g_iter11` 14/20 (70%)**.

The new data point is independently informative: **`g_iter12` beats its
immediate predecessor `g_iter11` head-to-head 70-30**, which is real
corroboration for Iteration 35's mechanistic accept -- that change tied
on the peer Gauntlet (75.0% both), so a direct head-to-head against the
bot it replaced is exactly the evidence that was missing. Had the
roster been correct at the time, this would have been available as
accept evidence rather than found afterward.

---

## Iteration 37 attempt — congestion-based build throttle; REJECTED (sixth economy failure)

The strongest remaining untried lead, from BC22's `RESEARCH.md` section
5: "Congestion is a real, easily-overlooked resource cost." Both Gone
Fishin' and 4 Musketeers independently found over-producing a cheap unit
"actively clogged pathing near the spawn point and reduced the
throughput of everything else," and both added "explicit production
throttling keyed off local unit density, not just 'should I afford to
build this.'"

This was genuinely promising because it identified the blind spot shared
by all five previous economy attempts (Iterations 28-31, 34): every one
keyed off *cheese* (affordability, scaling reserve, cooldown,
hysteresis, rolling trend) or off `builtCount` (cumulative-ever-built,
which can never decrease -- the classic cumulative-vs-live pitfall, and
the direct cause of the starvation lockout). Local density is a *live*
census by construction, so in principle it throttles the early overbuild
*and* un-throttles after attrition, with no cap to get stuck against.

**Two variants, both rejected:**

1. **Density across the King's full vision, limit 8: 27/40 (67.5%)**,
   with an unmistakable new signature -- eliminations at r387, r410,
   r525, r541, r571, r636, r645, r652, r694, r702, r745, far faster than
   any previous failure mode. That's death by *under*-building: 8 allies
   loosely spread across vision's ~78 tiles is completely normal during a
   healthy ramp, so the throttle latched almost immediately and
   suppressed production for the rest of the game.
2. **Density within the build radius only (radius^2 8, ~25 tiles), limit
   6:** smoke tests still died fast (`closeup` r430, `tiny` r612) --
   still under-building.

**REJECT.** Reverted to `g_iter12`.

**Stopping this thread here rather than trying a third threshold.** The
mechanism is sound and the diagnosis of the blind spot was correct, but
at this point I'm guessing threshold values again -- the exact pattern
already documented as unproductive after Iterations 28-31/34. Six
attempts across the King's build policy have now failed. What's
consistently missing is a *principled* way to set the level rather than
another guess: the right next step is to measure, from an accepted
baseline replay, what near-King density and build cadence actually look
like in games we win, and derive the threshold from that -- rather than
proposing a number and spending a full Gauntlet finding out it's wrong.

---

## Iteration 38 — sliding build budget; measurement finally explains six prior failures

**The measurement that reframed everything.** After six failed attempts
at the King's build policy (Iterations 28-31, 34, 37), extracted the
actual build cadence from a *winning* game rather than proposing another
threshold: the King builds all 25 rats in rounds **1-25, one per round,
back-to-back**, then never builds again. Confirmed the identical pattern
in losing games (`keepout`, `tiny`). So the maximal early burst is the
proven-good behavior -- **every one of the six throttle attempts was
damaging the thing that works**, which is exactly why they all
regressed. The only genuine defect is the second half: `builtCount` is
cumulative-ever-built, so once it hits the cap the King is locked out of
*replacing* losses for the remaining ~1975 rounds regardless of how many
rats have died.

**Iteration 38:** keep the per-window cap at 25 (preserving the
round-1-25 burst byte-for-byte) but refresh the budget every 400 rounds,
permitting replacement without ever allowing a faster-than-proven ramp.

**Full Gauntlet: 30/40 (75.0%)** -- tied with the `g_iter12` baseline,
but with a clean, one-directional diff rather than noise. **Fixed:**
`knifefight` on all three losing pairings (the exact map Iteration 35
had regressed), plus `keepout`, `thunderdome`, `tiny`. **Broke:**
`minimaze` (3 pairings) and `pipes` (2).

**Traced the new `minimaze` loss, and it falsified the obvious
hypothesis.** The intuitive read was congestion -- `minimaze`/`pipes`
are the tight maze/corridor maps, so more rats where movement is already
the bottleneck. The replay says otherwise. Build rounds were
`1..25, 400-420, 480, 530, 639, 682, 800-802` -- the mechanism fired
exactly as designed -- but the economy tells the real story:

    round 400   cheese 1660   aliveBabies 19
    round 500   cheese  150   aliveBabies 40
    round 900   cheese  160 ... round 1100  cheese 18  (King starves)

The replacement burst built ~21 rats in 20 rounds and **spent the entire
treasury**, from 1660 down to 150, never recovering. This is a
*cheese-bankruptcy* failure, not a congestion failure.

**The mechanism is `BUILD_ROBOT_COST_INCREASE = 10*floor(pop/4)`** (see
`RULES.md`): build cost scales with current population. The opening
burst is cheap because population starts at *zero* and the cost ramps up
as it grows. A replacement burst starting at population ~19 begins
already-expensive and compounds from there -- so "25 builds" costs
dramatically more in round 400 than the identical "25 builds" did in
round 1. Refreshing the budget to the same value was never
cost-equivalent, and nothing in the design accounted for that.

This also retroactively explains Iteration 28's "boom-bust overbuild"
(population to 36-37, cheese crash) as the same phenomenon rather than a
separate one: it wasn't that *more rats* is inherently wrong, it's that
building them at high population is priced completely differently.

**Next:** the fix is to make replacement building respect its real cost
-- a much larger cheese buffer for refreshes than for the opening burst
(the opening burst can safely spend down to `RESERVE`; a replacement
burst cannot), and/or a partial rather than full budget refresh.

---

## Iteration 39 — cost-aware replacement reserve; ACCEPTED, 90.0% (largest jump of the project)

Follows directly from Iteration 38's replay finding: replacement
building was bankrupting the King because `BUILD_ROBOT_COST_INCREASE =
10*floor(pop/4)` makes build cost scale with *current* population, so a
budget refresh to "25 builds" at population ~19 costs far more than the
identical 25 builds at population 0.

**Fix:** the opening burst keeps spending down to `RESERVE` exactly as
before (unchanged, proven -- rounds 1-25, one rat per round), but
replacement windows require a much deeper buffer
(`REPLACEMENT_RESERVE = 1000`): topping the army back up may draw only
on genuine surplus, never on the King's survival margin. This is the
escalating-threshold pattern BC22's `LEARNINGS.md` records for
discretionary spending -- a committed investment (the opening army) and
a discretionary one (replacing losses) should not be gated at the same
bar.

**Smoke test** (`minimaze`, the map Iteration 38 broke): loss -> win, and
the mechanism is visible in the economy trace -- treasury stabilizes at
~1000-1050 (the reserve floor) instead of collapsing to 18, population
sustains at 23-32 against the opponent's 17-19, `cheeseTransferred`
6970 vs. 4745, `catDamage` 1160 vs. 20.

**Full Gauntlet: 36/40 (90.0%)**, up from the `g_iter12` baseline's
75.0%. `pure_cooperator` 70%->85%, `immediate_defector` 80%->95%.

**Diff shape: six losses fixed, zero new.** Every remaining loss was
already a loss at baseline -- `tiny` (both), `minimaze` bot=B,
`whereisthecheese` bot=B. A purely one-directional improvement with no
regression on any map or side, which is the cleanest accept signal this
project has produced.

**ACCEPT.** Snapshotted as `src/g_iter13/`; new baseline
`gauntlet/20260902-233818/`.

**Why this took seven attempts, recorded as the methodology lesson:**
Iterations 28-31, 34 and 37 all failed because they *proposed a
threshold and let a 40-game Gauntlet adjudicate it*, and every one of
them throttled the opening burst -- the single behavior every winning
game depends on. What finally worked was measuring first: extracting the
actual build cadence from a winning replay (25 builds, rounds 1-25, then
silence) reframed the problem from "the King builds too fast" to "the
King can never rebuild," and tracing the resulting regression falsified
the next obvious guess (congestion) in favor of the real one (cost
scaling). Two measurements replaced seven guesses. **Measure the target
behavior before proposing a threshold for it.**

**vs-old-bots for `g_iter13`** (`gauntlet/20260902-234351/`), now against
the corrected every-10th roster: **vs. `g_iter1` 17/20 (85%)**, up from
`g_iter12`'s 80%; **vs. `g_iter11` 18/20 (90%)**, up sharply from
`g_iter12`'s 70%. The `g_iter11` jump is the more meaningful of the two
-- it's the most recent fixed reference point, so a 70%->90% move
against it is independent corroboration that Iteration 39 is a real
strength gain rather than a peer-roster artifact. This also reverses the
gradual decline the old-bot chart had been showing (90->85->80->80),
which had been the one metric trending the wrong way.

---

## Diagnostic: 3 of the 4 remaining g_iter13 losses share one cause

Traced all four losses in the new `g_iter13` baseline
(`gauntlet/20260902-233818/`). Three are the *same* mechanism, and it's
one Iteration 39 introduced:

- `tiny` (coop bot=A): army 0 from round 575, cheese 950 -> 0, King starves.
- `whereisthecheese` (coop bot=B): army 0 from round 425, cheese
  970 -> 845 -> 695 -> ... -> 1, King starves. Build rounds: 1-25 only.
- `tiny` (`immediate_defector` bot=B): army 0 from round 275, cheese
  1035 -> 885 -> 735 -> ... -> 1, King starves.

In every case the King is holding **several hundred to ~1000 cheese with
zero living Baby Rats**, and never rebuilds, because
`REPLACEMENT_RESERVE = 1000` is above the cheese level these
lower-economy maps sustain. The reserve exists to keep the King alive,
but here it does the opposite: it preserves a buffer the King then
slowly burns on upkeep while defenceless, instead of converting it into
the army that would generate income. Iteration 39 fixed bankruptcy-by-
overbuilding and introduced starvation-by-hoarding at the low end.

(The fourth loss, `minimaze` bot=B, is unrelated: population is healthy
at 25-31 vs. the opponent's 14 and we lead `cheeseTransferred`
7655 vs. 5505 -- it's the known `catDamage`-weighted map-luck pattern,
already closed out as not-further-pursuable.)

**Iteration 40** targets exactly this: when *no* allied Baby Rat is
visible to the King at all, drop back to the ordinary `RESERVE` bar.
Checked against the traces above, the override fires in all three cases
(e.g. `whereisthecheese` round 425: army 0, cheese 970 > 150, so it
rebuilds instead of hoarding). Result pending.

---

## Iteration 40 — emergency build override when the army is gone; ACCEPTED, 95.0%

Direct fix for the starvation-by-hoarding failure Iteration 39
introduced (see the diagnostic above). When **no** allied Baby Rat is
visible to the King at all, fall back from `REPLACEMENT_RESERVE` (1000)
to the ordinary `RESERVE` (150). Rationale: the reserve exists to keep
the King alive, so preserving it while the King has no army at all
inverts its own purpose -- an undefended King with no income dies
holding the buffer. Rats alive but outside the King's vision aren't
defending or feeding it either, and the per-window cap still bounds the
response, so a false positive is cheap.

**Full Gauntlet: 38/40 (95.0%)**, up from `g_iter13`'s 90.0%.
`pure_cooperator` 85%->90%; **`immediate_defector` 95%->100% (20/20)**.

**Diff: exactly the three predicted losses fixed, one new.**
Fixed `tiny` (both sides) and `whereisthecheese` bot=B -- precisely the
three traced as sharing the hoarding mechanism, each verified in advance
to trigger the override. New: `minimaze` bot=A, joining the existing
`minimaze` bot=B; both remaining losses are now the same
`catDamage`-weighted map-luck pattern long since closed out as
not-further-pursuable. Predicting the fixed set from the traces *before*
running the Gauntlet, and having it come out exactly, is the strongest
confirmation yet that the mechanism is understood rather than guessed.

**ACCEPT.** Snapshotted as `src/g_iter14/`; new baseline
`gauntlet/20260902-234812/`.

**Process note (my error, recorded rather than hidden):** the source
change for this iteration was accidentally committed early, in
`38ffc2f` ("Track g_iter13 vs-old-bots"), because that commit used
`git add -A` while an untested edit sat in the working tree. So `main`
briefly carried unverified bot code under a commit message about
charts. It happened to pass, but the same slip with a *rejected*
iteration is exactly how a regression gets baselined silently. Fixed
going forward by staging explicit paths; noted in memory.

**Watch item:** `immediate_defector` is at 100% (20/20). Per
TRAINING_ALGORITHM.md's retirement rule, a second consecutive 100%
means it should be retired from the peer roster and replaced, or the
Gauntlet stops providing signal on that half of the matchup space.

---

## Infrastructure: archetypes had gone stale again (second occurrence)

Checked the peer roster against `src/bot/` after Iteration 40's accept,
per the standing rule added the first time this happened. Both
archetypes were missing **every** significant change since roughly
Iteration 24 -- greps for `exploreStuckCycles` (Iteration 32's
exploration fix), `bugRoundsFollowing` (Iteration 35's bug-navigation),
`buildWindowStart` (Iteration 38's sliding build budget) and
`REPLACEMENT_RESERVE` (Iteration 39) all returned **zero** in both
files. They were 336 and 379 lines against `src/bot/`'s 943.

That means the headline numbers for Iterations 32-40 were measured
against opponents progressively further behind -- the same silent
score inflation documented the first time, recurring despite the rule,
because the check is only triggered by remembering to run it. **The
95.0% should be treated as provisional until re-measured.**

**Re-synced both** by re-basing them on current `src/bot/` and
re-applying only their distinctive policy, which is the part that makes
them useful as a roster:
- `pure_cooperator`: `desperate` hard-disabled at both the King
  (never broadcasts) and the Baby Rat (never reads), so it never
  initiates a backstab under economic pressure -- it retaliates only
  once actually backstabbed.
- `immediate_defector`: hostile to enemy rats from turn 1 regardless of
  cooperation state, plus its `LEASH_RADIUS_SQUARED = 100` turtle
  behavior restored on top of the new movement code.
Both compile clean against the engine.

Re-measurement against the corrected roster follows.

---

## CORRECTION: the 95.0% was inflated. Honest re-measurement is 62.5% -- and neither number means what it looks like.

Re-ran `g_iter14` against the re-synced roster
(`gauntlet/20260902-235707/`): **25/40 (62.5%)**, not 95.0%.
`pure_cooperator` 90%->**50%**, `immediate_defector` 100%->**75%**.
The 95.0% reported for Iteration 40 was substantially stale-opponent
inflation and **should not be quoted**.

**But 62.5% is not "the bot got worse," and 50% is not a weak result.**
Diffing `src/bot/` against the re-synced `pure_cooperator`, ignoring
comments, they now differ by **8 lines of code** -- the desperation
latch and one conditional. On any map where a backstab never triggers
they are the *same bot*. A ~50% win rate against a near-identical
opponent is what the arithmetic requires, independent of how strong
either bot is.

**This exposes a real flaw in how the peer roster has been read all
along.** Sharing `src/bot/`'s economy/movement code was a deliberate
choice so the archetype isolates *backstab policy* as the only variable
-- a clean controlled experiment. The consequence, never previously
stated, is that the resulting win rate measures **policy advantage
only**, centred on 50%, and cannot measure absolute strength at all. So:

- **Stale archetypes** -> inflated absolute-looking numbers (what
  happened twice).
- **Perfectly synced archetypes** -> ~50% by construction (what we have
  now).
- Neither is a progress metric. Both were being read as one.

**The metric that was right all along is vs-old-bots**, against frozen
`g_iterN` snapshots that genuinely cannot drift:

    vs g_iter1    g_iter12 80%  -> g_iter13 85%  -> g_iter14 95%
    vs g_iter11   g_iter12 70%  -> g_iter13 90%  -> g_iter14 100%

Clean, monotonic, large gains on fixed references. **Iterations 39 and
40 were real improvements** -- that conclusion survives the correction
intact; only the *magnitude* claimed from the peer roster was wrong.
This is also why the user's catch about the every-10th old-bot roster
mattered so much: without `g_iter11` in it, the one uncontaminated
progress signal would have been a single data point.

**Reinterpretation going forward** (no code change, a reading change):
- `progress/vs_old_bots.png` is the **progress** metric. Quote this.
- The peer Gauntlet is a **policy/regression** check: ~50% vs.
  `pure_cooperator` is the healthy expected value; a drop meaningfully
  *below* 50% is a genuine regression signal, and the
  `immediate_defector` number says our policy beats always-defecting
  (75%).
- The new baseline for diffing is `gauntlet/20260902-235707/` at 62.5%.

**Methodology lesson:** an opponent that shares almost all of the
bot's code is a *controlled experiment*, not a benchmark, and its
absolute win rate is close to meaningless. The staleness bug was real
and worth fixing, but fixing it also removed the accidental
benchmark-like behavior that made the peer numbers *look* informative.
Two different measurements were being conflated under one number for
most of this project's history.

---

## Iteration 41 — absolute-order tie-breaks caused a 45-point side asymmetry

The corrected (near-mirror) peer roster immediately earned its keep by
exposing something no previous measurement could have: across the 40
honest re-measurement games, **side A won 85% and side B won 40%**.
Symmetry requires ~50/50. The gap was spread across essentially every
map (A went 2/2 on seven of ten) rather than concentrated on one, which
per "reading a diff's shape" means systemic code bias, not map luck.

**Root cause -- seven `d < bestDist` scans.** A bare `<` hands every
*tie* to whichever candidate the engine returned first, and
`senseNearbyRobots()` / `senseNearbyMapInfos()` /
`getAllLocationsWithinRadiusSquared()` all return results in the
engine's fixed absolute-coordinate order, not relative to the caller.
On a symmetric map the two teams are mirror images, so a preference
expressed in absolute coordinates points "toward the enemy" for one team
and "away" for the other. The affected sites were
`findBuildLocation` (decides where *every* Baby Rat spawns -- by far the
costliest), `collectCheese`, `pickUpBestNearbyCheese`,
`attackNearestHostile`, `nearestOfType`, `nearestEnemyRat`, and
`digTowardOpenSpace`.

This is the single largest recurring bug class in BC22's `LEARNINGS.md`
-- that project rediscovered it twice, ~60 iterations apart, and traced
it to exactly this pattern: "consumers that picked the *first* result
satisfying a condition instead of explicitly finding the best one." It
was inherited here despite the class-level docstring claiming the
codebase was clean of absolute-order bias; that claim covered *movement
directions* (which are genuinely target-relative and ID-tie-broken) and
missed target *selection* entirely.

**Fix:** a single `betterTarget(d, bestDist)` helper used at all seven
sites, breaking ties with the per-robot `rng` (seeded from
`rc.getID()`, unique per robot and not team-correlated -- unlike a
shared fixed-seed `Random`, which BC22 documents as reproducing this
same bug one level removed).

**Why this matters more than the peer win rate will show.** Checking the
independent vs-old-bots runs, there is *no* side asymmetry there at all
(`g_iter14`: side B 100%, side A 95%; `g_iter13`: B 90%, A 85%). The
bias only manifests against a near-equal opponent -- when both bots
share the same absolute-order preference, the map's mirror geometry
hands one side a systematic edge, and against a much weaker opponent we
win from either side regardless so it stays invisible. **An
evenly-matched opponent is exactly the tournament case**, so this is a
real competitive defect even though it was undetectable against the
stale roster and against old snapshots.

It also means the archetypes (still carrying the unfixed tie-break)
make this run a clean A/B: fixed bot vs. biased opponent. The side-B
win rate is the measurement that matters; the overall number will
overstate, since the opponent is handicapped until the next re-sync
propagates the fix.

**Iteration 41 REJECTED -- and it falsified its own hypothesis.**
Full Gauntlet: **21/40 (52.5%)**, down from the 62.5% baseline. The
side split tells the real story:

    side A   85% -> 65%   (dropped 20 points)
    side B   40% -> 40%   (unchanged)

The fairness gap narrowed from 45 points to 25 -- but entirely by making
side A *worse*, not side B better. **So the absolute-order tie-breaks
were not what was costing side B.** The hypothesis was wrong, despite
being well-motivated by BC22 precedent and despite the bug being
genuinely present in the code.

This also reproduces BC22's own follow-up finding almost exactly: there,
"a purely-random one and a per-robot-fixed-random one both narrowed the
gap too, but both caused a real net regression in overall win rate --
most likely because a *consistent* (even arbitrary) per-round preference
was giving the army real movement cohesion that pure randomization
loses." Same outcome here: consistency in target selection is worth
real win rate, and randomizing it away costs more than the fairness it
buys. Reverted.

**What this does establish:** side B's 40% is caused by something else
entirely, and is now the isolated open question. Running a *true* mirror
(current `bot`, byte-identical to the frozen `g_iter14` snapshot, played
against that snapshot) to measure the asymmetry with the cleanest
possible instrument -- no policy difference, no strength difference,
nothing but code-and-map interaction left in the measurement.

## True mirror match: the side asymmetry is a deterministic code x map-geometry interaction

Ran current `bot` (byte-identical to the frozen `g_iter14`) against that
snapshot -- identical code on both sides, so the only remaining
variables are the engine and the map.

    overall   10/20 (50.0%)   <- forced by symmetry; carries no information
    side A     7/10 (70%)
    side B     3/10 (30%)

    A wins:  closeup, knifefight, minimaze, pipes, rift, thunderdome, tiny
    B wins:  keepout, sittingducks, whereisthecheese

**The per-map split is exactly the same set of maps that favored each
side in the peer runs.** So it is fully deterministic and map-specific,
not noise, and not a uniform engine first-mover advantage -- if it were
simply "team 1 acts first" (their King is always `id1` vs `id2`, and
robots act in ID order) every map would favor A. Three maps reliably
favor B. This matches BC22's own conclusion from the same experiment:
"a true mirror match split roughly 6-4 across the 10-map pool, different
maps favoring different sides."

**Why the overall 50% is not reassuring.** In a true mirror the total is
*forced* to 50% -- one side's win is the other's loss -- so the metric
that looks fine is the one that structurally cannot show the problem.
Against a near-mirror opponent (the 8-line-different archetype) the same
underlying bias surfaces as 85%/40%. Our absolute-geometry preferences
make the bot strong where map geometry aligns with them and weak where
it doesn't; averaged over both sides that is simply a worse bot than one
which performs uniformly.

**Status: known-hard, deliberately deprioritized.** Iteration 41's
randomization fix is now the *second* independent attempt across two
projects to narrow this gap by removing the absolute preference, and
both narrowed fairness while *losing* net win rate for the same reason
(consistency provides real movement cohesion). BC22 also tried a
center-relative tiebreak, which "narrowed the fairness gap but caused a
much worse asymmetry on one specific map," and after 128 iterations that
project still lists this as an open question. Given two failed attempts
at the obvious fix and a well-documented history of the non-obvious ones
also failing, further attempts here are low expected value compared to
untouched areas. Recording it as a characterized, quantified limitation
rather than burning more iterations on it.

**What would make a future attempt worthwhile:** a fix that keeps a
*consistent* per-robot preference (preserving cohesion) while making
that preference relative to something symmetric under the map's own
mirror -- e.g. derived from the vector to the team's own King, or to the
map centre -- rather than either absolute compass order or per-decision
randomness. That is a different design from both attempts so far, and
is the only version not yet falsified.

---

## Iteration 42 attempt — kite after every attack; REJECTED (the technique's premise doesn't hold here)

First micro technique attempted from BC22's `RESEARCH.md` section 4,
which argues micro outweighs macro 30-50% vs ~5% and records
*unconditional* kiting as beating conditional kiting "in every test."
The engine numbers looked decisive: a Baby Rat acts and moves every
round (both cooldowns 10) while a CAT scratches once per 3 rounds
(action 30) and moves once per 2 (movement 20), and action/movement draw
on separate cooldowns -- so biting and stepping back is free, and the rat
is twice as fast as the thing chasing it.

**Full Gauntlet: 15/40 (37.5%)**, down hard from 62.5%.

**Traced it, and the mechanism is the opposite of the premise.** On the
same map, baseline vs. kiting:

    catDamage (ours)   4320  ->  1260     (3.4x WORSE)
    RatAttack events    776  ->   679
    CatPounce events      0  ->     0

Kiting *reduced* our cat damage by more than three times. The DPS
argument assumed the payoff was survival -- stay alive longer, deal more
total damage. That premise is false here because **cats have 4000 HP and
we never kill one.** Nothing about the fight is a trade to be won; the
cat is an effectively unkillable damage sponge, and `catDamage` is a
*cumulative score component* (0.5 weight in coop mode). When the target
can't be killed, total damage dealt is just (attack rate x time in
contact) -- and kiting halves the first term to buy survival that wasn't
the binding constraint. Rats were already getting ~15 rounds of attacks
in before dying; trading half the attack rate for more of those rounds
is a straight loss.

(The pounce hypothesis was also wrong -- zero `CatPounce` events in
either run, so retreating to range 2-3 was not exposing rats to
instant-kill jumps. Worth recording since it seemed plausible.)

**REJECT.** Reverted.

**Generalizable lesson, and a caution on porting BC22/RESEARCH.md
advice wholesale:** kiting is valuable when the target is *killable*, so
that surviving longer converts into more kills and a better trade.
Against a target you cannot kill, where the scoring quantity is
cumulative damage dealt, the advice inverts -- maximize attack frequency
and time in contact instead. The cross-year postmortems all assume
killable opponents, which is true of their rulesets and false for BC26's
cats. Check whether a ported technique's *premise* holds before porting
its conclusion; three of this session's rejects (this, Iteration 41's
tie-break randomization, and the congestion throttle) were all sound
advice imported without validating that its precondition applied here.

---

## Diagnostic: every points-loss is lost on catDamage and won on cheese

Extracted final-round score components from all seven r2000 points-losses
in the honest baseline (`gauntlet/20260902-235707/`), being careful with
team indexing -- for a `bot=B` replay the bot is **team 2**, which
inverts a naive reading of the dump:

    map            ourCatDmg  theirCatDmg    ourCheese  theirCheese
    closeup   B         1470        4320         6560         5645
    rift      B         1950        4000        21305        12855
    thunderdome B       2270        4600        16365        11030
    closeup   B         1610        3360         8560         4670
    keepout   A         3210        4460        23490        19570
    minimaze  B          330        2950         7275         7100
    rift      B         3020        4450        21035        20260

**In all seven we lose `catDamage` and win `cheeseTransferred`.** Not
six of seven -- all seven, across three maps and both sides.

That is exactly backwards relative to the scoring weights. Coop-mode
score is `0.5*catDamage% + 0.3*livingKings% + 0.2*cheeseTransferred%`,
so we are winning the 0.2-weight metric, often by 2x, while losing the
0.5-weight one. Worked through on `closeup`:

    ours   0.5*25.4 + 0.3*50 + 0.2*53.7 = 38.4
    theirs 0.5*74.6 + 0.3*50 + 0.2*46.3 = 61.6

With `catDamage` merely *equalized* and everything else unchanged, that
becomes 50.7 vs. 49.3 -- a win. **Closing the cat-damage gap alone flips
these games**, and no economic improvement can, because the economy is
already won and capped at a fifth of the weight.

This reframes several earlier results. Iterations 21/22/27 all tried to
raise cat damage and were rejected for costing economy -- but those ran
*before* Iterations 38-40 fixed the build economy, in a regime where
cheese was genuinely scarce and any diversion hurt. We now finish with
2x the opponent's `cheeseTransferred` and a King sitting on surplus, so
the trade those iterations couldn't afford is now affordable. It also
explains why Iteration 42's kiting was so damaging (it cut the exact
metric that decides these games by 3.4x) and why Iteration 43's
low-HP-flee removal was inert (that condition rarely fires, and it
wasn't where the contact time is being lost).

**Caveat kept honest:** the opponent runs the same cat-engagement code,
so part of this gap is the map-geometry exposure effect characterized in
the true-mirror entry rather than a decision-quality difference. That
argues for *adding* proactive cat-seeking rather than trying to
fight the exposure asymmetry directly, which two projects have now
failed to fix.

**Iteration 44 REJECTED -- 9/20 (45%) head-to-head, and the cat-damage
gap did not move at all** (8620 vs. 18700 across the r2000 losses, the
same ~2.2x ratio as before). Checked whether the surplus gate was simply
never firing, since that has been the failure mode twice before
(Iteration 37's congestion limit, Iteration 34's ramp threshold). It
wasn't: measured the King's actual cheese across a full game and
`globalCheese > 1500` held in **100% of sampled rounds** (mean 4220, max
8300). Half the army really was hunting, all game, and produced no
additional cat damage.

**That falsifies the whole "go find cats" family, cleanly.** Three
attempts now -- Iteration 22 (every idle rat), 27 (economy-gated), 44
(surplus-gated, half the army) -- with three different trigger
conditions, all failing the same way. The common flaw is the target:
`lastKnownCatLoc` is a *remembered* position, and cats patrol fixed
waypoints (RULES.md), so by the time a rat walks there the cat has moved
on. Hunters converge on empty tiles while not collecting cheese. No
gating condition fixes a stale target. **Not attempting a fourth
variant of this.**

## Iteration 45 — buy cat damage with surplus cheese instead of chasing it

The diagnosis stands even though the remedy failed: we are rich in the
0.2-weight currency (mean 4220 banked, 2x the opponent's
`cheeseTransferred`) and poor in the 0.5-weight one. If we cannot
manufacture more *contact* with cats, the remaining lever is to make
each existing contact worth more -- no positioning change required,
which is precisely the part three iterations could not move.

The engine supports this directly: `InternalRobot.bite` computes
`damage += ceil(sqrt(cheeseConsumed))`. Returns diminish sharply, so
small boosts are dramatically more efficient -- **4 cheese buys +2
damage on a base of 10 (a 20% increase at 0.5 damage/cheese), while 100
cheese would buy only +10 (0.1 damage/cheese)**. Sizing it from measured
volume (~776 attack events per game): 4 cheese/bite is on the order of
1-3k cheese over a full game, comfortably inside a treasury that
averages 4220 and a `cheeseTransferred` around 20000.

Distinct from Iteration 16's rejected cheese-bite, which ran when cheese
was scarce and whose spending pulled the desperation latch early,
causing a second-order collapse; cheese is now abundant and that latch
behaves differently since Iterations 38-40.

**Mechanistic verification (Iteration 45).** Before trusting any
aggregate, confirmed the boost actually fires, using a signature that
can't be faked: base `RAT_BITE_DAMAGE` is 10, and a 4-cheese boost makes
it 12, so a team using the boost accumulates `catDamage` totals that are
*not* multiples of 10. In the head-to-head:

    ours (boosted):    1214, 118, 3624     <- non-round, mixed 10s and 12s
    theirs (g_iter14):  4420, 4000, 4450   <- all exact multiples of 10

Unambiguous: the boost engaged for us and not for the frozen opponent.
This is the check that three earlier iterations (21, 43, and the
Iteration 34 threshold bugs) needed and didn't get -- each looked
plausible and was silently inert. Worth making routine: find a
*signature* of the mechanism in the replay data, not just a win-rate
delta.

**Head-to-head (10-map loop): 11/20 (55%)** -- above the 50% accept bar,
but only one game clear of even. Since the Gauntlet is deterministic for
fixed code, re-running adds nothing; the way to get more confidence is a
larger *sample of maps*. Re-running on the full 27-map set (54 games)
before deciding.

**Full 27-map head-to-head: 29/54 (53.7%)** -- consistent in direction
with the 10-map loop's 55%, but a one-tailed binomial test gives
**p = 0.34**. That is not significant. Two samples agreeing is weaker
evidence than it looks, too, since the loop maps are a subset of the
full set, so the runs are not independent.

**Did not snapshot it.** A 53.7% result with p=0.34 is exactly the kind
of marginal number that, accepted uncritically, quietly becomes a
"baseline improvement" no one can later reproduce -- and this project
has already been burned once by treating an unreliable number as
progress (the 95.0%). Deleted the premature `g_iter15` snapshot.

**Better test than more repetitions: dose-response.** The Gauntlet is
deterministic, so re-running the same code yields the identical answer
and adds nothing. But if the cheese-boost genuinely helps, the effect
should *scale with the boost*: 4 cheese buys +2 damage, 16 buys +4.
Running the same 54-game head-to-head at `BITE_BOOST_CHEESE = 16`.
A clearly larger win rate is real evidence of a causal mechanism;
a flat or worse result means the 53.7% was noise and the whole approach
should be rejected regardless of how good the theory looks.

**Dose-response result: `BITE_BOOST_CHEESE = 16` -> 20/54 (37.0%)**,
against 4-cheese's 29/54 (53.7%). The effect scales **negatively and
steeply** -- quadrupling the spend costs ~17 points of win rate.

**REJECT the cheese-boosted bite entirely** (Iteration 45), including
the 4-cheese version. The dose-response curve resolves what the p=0.34
could not: the mechanism is real (verified engaged via the
damage-parity signature) but its true direction is *harmful*, and
53.7% was noise sitting on top of a slightly-negative-to-neutral effect.
Had the marginal result been accepted, a mildly harmful change would
have been baselined as an improvement, and every subsequent iteration
measured against it.

**Why it's harmful, in hindsight:** the exchange-rate reasoning
(0.2-weight cheese into 0.5-weight cat damage) accounted for the
*scoring* cost of spending cheese but not its *operational* cost.
Cheese is not merely a scored quantity; since Iterations 38-40 it is
also the fuel for replacement building, and the King's treasury sitting
near its reserve floor is what keeps population up. Draining thousands
of cheese into chip damage starves that loop, and population feeds
everything -- cheese collection, cat contact, and cat damage itself.
The 0.2 weight understates what cheese is worth because part of its
value is instrumental rather than scored.

**Methodology note worth keeping.** With a deterministic harness,
repeating a run tells you nothing, so the usual answer to a marginal
result (gather more samples) is unavailable. Varying the *dose* of the
mechanism is the substitute, and it worked: an ambiguous p=0.34 became a
decisive rejection in one additional run. **For any future marginal
result, prefer a dose-response or ablation test over another sample.**

---

## Iteration 46 premise falsified mid-run: catDamage does not scale with population

Iteration 46 raised `MAX_POPULATION` 25 -> 35, reasoning from Iteration
45's finding that cheese's value is instrumental (it buys population,
which should buy cat contact and therefore `catDamage`). While the
Gauntlet ran, checked that causal link directly against the baseline
games -- and it does not exist:

    ourPop  ourCatDmg  |  theirPop  theirCatDmg
        8       1470   |       2        4320
       27       1950   |      63        4000
        6       2270   |       5        4600
       12       1610   |       3        3360
       16       3210   |      11        4460
       44        330   |      24        2950
       32       3020   |      54        4450

In three of seven we field **2-4x more rats and take a fraction of the
cat damage** -- most starkly 44 rats for 330 damage against 24 rats for
2950. There is no positive relationship; if anything it is inverted.
**More rats will not produce more cat damage**, so this iteration cannot
work as designed regardless of what the win rate comes back as.

**What this rules in.** The opponent runs identical cat-engagement code
and reaches 3-9x our cat damage with *fewer* units, so the difference is
neither decision quality nor army size -- it is **contact**: their rats
are near cats and ours are not. That is the map-geometry exposure effect
characterized in the true-mirror entry (side A 70% / side B 30%, same
maps every time), and most of these losses are side B.

This closes off the last "just do more of X" lever on cat damage.
Population, hunting (Iterations 22/27/44), damage-per-bite (45), and
contact time (42, 43) have all now been tested and rejected. The
remaining explanation is positional, and the positional problem is the
one two projects have failed to fix by removing absolute-order
preferences.

## Positional evidence: our units are never near cats, and cats revisit sites

Two measurements that reframe the cat-damage problem from "fight cats
better" to "be where cats are":

**1. Our King never meets a cat at all.** Tracked `id6` (our King) across
the whole `minimaze` game: parked at `(32,44)`, **600/600 HP, zero
damage, never moved, for 2000 rounds**. It has 600 HP (6x a Baby Rat)
and `RAT_KING_ATTACK_DISTANCE_SQUARED = 8` (4x a rat's reach), and it
contributes nothing to cat damage because no cat ever comes near it.
This also kills a tempting hypothesis before testing it: removing the
King's flee-from-cats rule would change nothing, since the rule never
fires.

**2. Cats revisit locations.** Across the same game: 93 `CatScratch`
events over 61 distinct tiles, with the top sites hit 3-6 times each.
Cats cycle fixed waypoints (RULES.md), so **a tile where a cat was seen
is likely to have a cat again.**

**This is precisely what the three failed hunts got wrong.** Iterations
22/27/44 all chased `lastKnownCatLoc` -- a single, constantly-overwritten
position, pursued *immediately*, so a rat arrives just after the cat has
moved on and finds an empty tile. The correct exploitation of a cycling
patrol is the opposite: treat an observed sighting as a durable
*hotspot* and be positioned there when the cycle comes back around.

**Iteration 47 design:** each rat remembers the first tile where it ever
sees a cat (`catHotspot`, set once, never overwritten -- unlike the
churning `lastKnownCatLoc`). Half the army by ID parity treats it as a
*positional bias* rather than a task: when far from it they move toward
it, and when near it they resume ordinary behaviour (collect cheese,
engage anything that appears). So it changes *where* those rats live,
not *what they do* -- which avoids the failure mode where hunters stop
contributing to the economy entirely.

## Measured noise floor: +-9 games of churn at 54 games, so 53.7% is indistinguishable from 50%

Iteration 45 (`BITE_BOOST_CHEESE=4`) and Iteration 46 (`MAX_POPULATION`
25->35) both scored **exactly 29/54 (53.7%)** against `g_iter14`.
Compared the two win *sets* rather than just the counts:

    wins in common                     20
    won by boost-4 but not cap-35       9
    won by cap-35 but not boost-4       9

Two mechanistically unrelated changes, each flipping nine games in each
direction, netting to the identical total. That is the signature of
**chaotic sensitivity**: a small perturbation early in a 2000-round game
cascades into a flipped outcome on closely-contested maps, in either
direction, with no relationship to whether the change was beneficial.
BC22's `LEARNINGS.md` documents the same phenomenon ("map-fragile long
games can flip outcome from a tiny, unrelated change").

**This establishes a noise floor of roughly +-9 games out of 54 (~17
percentage points of game-level churn) for near-identical bots**, and it
means a 29/54 result carries essentially no information -- exactly
matching the binomial p=0.34. Both iterations are net-neutral.

**Consequences for the accept criterion** (amending the head-to-head
rule added earlier today):
- 53-55% over 54 games is *inside* the noise floor. The bar for
  "genuinely beats what it replaces" needs to be well above that --
  Iteration 39's accept, for contrast, was +15 points with six losses
  fixed and zero new, far outside this band.
- Comparing **win sets**, not just win counts, is diagnostic: 20/29
  overlap with balanced discordance says "different but not better,"
  which a bare percentage hides completely.
- Dose-response remains the sharpest tool, because it asks whether the
  effect *scales* rather than whether one noisy number beat another.

**Iteration 46 is therefore REJECTED as net-neutral**, independent of
the cap=50 dose-response still running -- its premise was already
falsified (cat damage does not scale with population) and its result
sits inside the noise floor.

---

## External benchmark suite added -- and it invalidates the bubble every prior metric was measured in

Vendored five public BC26 bots as independent validation (see
`BENCHMARK.md`), under a strict rule: **their source is never read**.
Cloning, compiling, running and *replay* analysis only. That is a user
instruction and also methodologically necessary -- reading opponent code
invites tuning against those specific bots rather than getting stronger,
which is exactly what an independent benchmark exists to prevent.
Package renaming was done mechanically (`sed` on declaration lines) with
contents never displayed; bots were chosen by directory name alone.

    bench_lecture   official lectureplayer
    bench_anicolao  anicolao/battlecode-2026 src/myplayer  (user request)
    bench_finalist  AlexT101 finalsbot     -- Top 12 Finalist
    bench_spaark    erikji SPAARK          -- MIT BC26 HS 4th
    bench_stroke    uravt Version41        -- 2nd place overall

**Smoke results on `knifefight` are brutal and are the point of the
exercise:**

    vs bench_lecture    WIN  (r1340)
    vs bench_anicolao   WIN  (r131)
    vs bench_finalist   LOSS (r33)
    vs bench_spaark     LOSS (r21)
    vs bench_stroke     LOSS (r46)

Real tournament bots end us in **21-46 rounds** of a 2000-round game.

**Replays show a structural capability gap, not a tuning gap.** Counting
actions in the first 60 rounds:

    bot            PlaceTrap  RatNap  ThrowRat
    bench_spaark        15       19       9
    bench_finalist      27       38      19
    bench_stroke        14       71      36
    ours                 0        0       0

We use **none** of these. Our own class docstring has said so since
Iteration 1 ("no traps, no ratnap/throw"). Traps were tried once
(Iteration 15, rejected -- never triggered) and ratnap once (Iteration
17, rejected as a bad trade); `throwRat` has never been attempted at
all. Both rejections were made against opponents that also lacked these
tools, so "it didn't help" meant "it didn't help *against bots as
limited as we are*."

**This reframes the whole project's measurement history.** 95-100%
against old snapshots was real progress *inside a bubble*: the peer
archetypes are built from our own code, and the old-bot roster is our
own lineage, so every opponent shared our passive early game and our
missing mechanics. No metric available before today could have detected
that we are missing most of the game.

**`bench_anicolao` is instructive as a control.** Replay shows it also
uses zero traps/ratnaps/throws -- the same minimal toolkit as us -- and
its `cheeseTransferred` stayed at **0** for the whole game while its
King starved out at round 131. That is precisely the King-starvation
failure this project spent Iterations 28-40 fixing. So the one external
bot we comfortably beat is one that shares both our toolkit *and* an
economy bug we already solved, while the three that use the full toolkit
beat us in under 50 rounds. The correlation is hard to miss.

## Benchmark baseline for g_iter14 -- a hard wall at the tournament tier

    vs bench_lecture     20/20  (100%)   official example bot
    vs bench_anicolao    20/20  (100%)   earlier LLM-vibe-coded bot
    vs bench_finalist     0/20    (0%)   Top 12 Finalist
    vs bench_spaark       0/20    (0%)   MIT BC26 HS 4th
    vs bench_stroke       0/20    (0%)   2nd place overall
    -----------------------------------
    overall              40/100  (40%)

Not a gradient -- a **wall**. We win every single game against both
non-tournament bots and lose every single game against all three
tournament bots, typically inside 21-50 rounds. There is no map, no
side, and no lucky game in the middle.

**Two defensive attempts, both essentially inert.** Since losing the
King is an instant loss, the obvious response was to defend it:

1. *Reactive alarm* -- King broadcasts on slot 6 while taking damage,
   nearby rats converge. Result: r21 / r50 / r39 vs. a r21 / r33 / r46
   baseline. Noise.
2. *Standing home guard* -- one rat in three never leaves a radius-6
   ring around the King, so defenders are already in place. Result:
   **r21 / r33 / r39 -- identical to baseline.**

The second result is the informative one. A permanent third of the army
parked on the King changes nothing, because the attackers arrive with
7-10 rats using traps, stuns and throws, and a handful of plain-biting
defenders is not a speed bump. **This is not a positioning problem and
cannot be fixed by moving our existing units around** -- which is what
every iteration this project has ever run amounts to.

**The honest read.** Iterations 1-46 optimised movement, economy,
targeting and thresholds within a fixed action set: move, bite, pick up,
transfer, dig, build. The tournament bots use `PlaceTrap`, `RatNap` and
`ThrowRat` constantly (14-27, 19-71, 9-36 respectively in the first 60
rounds) and open with a coordinated King rush. Closing a 0/60 gap
requires acquiring those mechanics and an opening plan, which is a
rewrite of the combat layer, not another parameter iteration. Recorded
as the project's central open problem rather than attempted as a quick
fix -- and the benchmark now measures it honestly, which nothing before
today could.

---

## Iteration 48 — ring the King with rat traps (first new mechanic in 47 iterations)

The benchmark established that rearranging existing units cannot stop a
King rush (a standing guard of a third of the army changed the outcome
by exactly zero rounds). So this is the first iteration to add a
*mechanic* rather than tune behaviour.

**Why traps, from the engine's own numbers:** `TrapType.RAT_TRAP` is
`buildCost=20, damage=50, stunTime=30, maxCount=25,
triggerRadiusSquared=2`. Fifty damage is **half an attacking rat's 100
HP**, and a 30-round stun removes it from a fight that only lasts ~20
rounds. A full set of 25 costs 500 cheese against a treasury averaging
4220. This was sitting unused the entire project.

**Why Iteration 15's rejection didn't apply any more.** That iteration
laid traps and observed they never triggered -- correctly, because the
peers of the day never attacked the King, so King-adjacent traps were
inert by construction. Against opponents whose entire opening *is* a
King rush, the identical traps sit directly on the attack path. The
mechanic didn't change; the opposition did. This is the second time a
past rejection has been overturned by a change in regime rather than a
change in idea (the first was Iterations 21/22/27's cat-hunting being
re-evaluated once the economy was fixed).

Placement is interleaved with building from `builtCount >= 5`, not
deferred until after the opening burst: the burst runs rounds 1-25 and
`bench_spaark` finishes us at round 21, so post-burst traps would arrive
after we are dead. Traps are aimed at the ring around distance^2 ~5
rather than flush against the King, so an attacker crosses them *before*
reaching bite range.

**Engagement confirmed** (the check three earlier inert iterations
lacked): 15-50 traps placed and **12-40 triggered** per game, so the
mechanic demonstrably fires.

**Single-game smoke results were mixed** -- `bench_finalist` survival
more than doubled (r33 -> r77) while `bench_spaark` (r21 -> r19) and
`bench_stroke` (r46 -> r36) drifted the wrong way. Given the measured
+-9-game noise floor, single games cannot separate those; running the
full 60-game tournament benchmark for a real reading.

**Two engine findings that reframe trap warfare:**

1. **Enemy traps are invisible.** `getMapInfo()` calls
   `gw.getTrap(loc, this.getTeam())`, and `GameWorld.getTrap` indexes
   `trapLocations[team.ordinal()]` -- so `MapInfo.getTrap()` only ever
   reveals *our own* traps. Neither side can see or route around the
   other's. Trap damage is therefore unavoidable by design, and trap
   warfare is a pure **volume exchange**: whoever has more traps down
   collects more triggers. No amount of pathfinding cleverness helps.

2. **We are losing that exchange because only the King places traps.**
   In the `bench_finalist` game: they placed 32, we placed 18; their
   rats triggered ours 13 times, **our rats triggered theirs 27 times**
   -- 650 damage dealt against 1350 taken. The replay shows why they
   out-place us: their *Baby Rats* lay traps
   (`id10949(team2,RAT) PlaceTrap`), while Iteration 48 only ever lets
   the King do it. One placer against twenty-five. `BUILD_DISTANCE_SQUARED`
   applies to rats, so rats laying traps is legal and we simply never
   used it -- the same "mechanic sat unused" pattern as traps themselves.

So Iteration 48 as written is likely *net negative on the trap exchange
alone*, and its survival gains have to be coming from the stun component
rather than from winning the trap trade.

## Iteration 48 result: 0/60 wins, but survival up 15-38x -- mechanistic accept

    opponent          baseline   with traps (mean)   max
    bench_finalist       r33          r1250          r2000
    bench_spaark         r21           r748          r2000
    bench_stroke         r46           r703          r2000

Still **0/60 wins**, so by win rate nothing changed. But the bot went
from being annihilated in 21-46 rounds to surviving a *mean* of 703-1250
and reaching the full 2000-round cap against all three tournament bots.
Reaching r2000 means the game was decided on points rather than by our
King dying -- we are no longer being removed from the board, which is
the precondition for any scoring component mattering at all.

**Accepted as mechanistic progress**, under the decision rule written
*before* the result was known (task #38): "any nonzero win count is a
breakthrough; if still 0/60 but survival improved substantially, treat
as mechanistic partial progress and iterate on placement/timing rather
than reverting." Pre-registering that rule mattered here -- a 0/60 line
is exactly the kind of result that invites post-hoc rationalisation in
either direction.

This is also the first change in the project's history to move a
tournament-tier metric at all. Every previous iteration was tuning
inside a fixed action set; this one added a mechanic, and the effect is
an order of magnitude larger than anything the tuning produced.

## Iteration 49 REJECTED, Iteration 50 (multi-King) engages -- and isolates catDamage as the last blocker

**Iteration 49 (rats lay traps): mechanically successful, outcome
negative.** The volume fix did exactly what was predicted -- traps
placed 18 -> 37, their trigger count 13 -> 31, and the exchange flipped
from -700 to +150 damage. But survival fell slightly (`finalist`
1250 -> 1165, `stroke` 703 -> 667): rats spending actions on traps are
not fighting or collecting, and that cost exceeds the trap gain.
Right about traps, wrong about the opportunity cost. Reverted to
Iteration 48's King-only placement.

**Iteration 50 (multi-King) fires and holds.** At round 2000 vs.
`bench_spaark` we now field **4 Kings to their 2** (previously 1 to 2),
with rats 30 -> 36 and `cheeseTransferred` 18545 -> 22215. Iteration 18
rejected this as catastrophic, but that was before Iterations 38-40:
three Kings' upkeep bankrupted a treasury bouncing off zero, whereas we
now bank 4000-6000. Third rejection overturned by a regime change rather
than a new idea -- and only findable because the benchmark showed what
the gap was.

**Scoring that game out, the picture is now unambiguous:**

    catDamage    share 16.5%  (0.5 weight ->  8.2 pts)
    livingKings  share 66.7%  (0.3 weight -> 20.0 pts)   <- now winning
    cheese       share 46.9%  (0.2 weight ->  9.4 pts)
    ------------------------------------------------
    us 37.6  vs  them 62.4

    with catDamage merely EQUALISED and all else held:
    us 54.4  vs  them 45.6   -> a WIN

**`catDamage` is the last blocker, and the gap is 5x** (3860 vs 19560).
Decomposing it: they field 82 rats to our 36 *and* extract 238 damage
per rat to our 107 -- so it is roughly 2.3x quantity and 2.2x per-rat
efficiency, multiplying out to the 5x.

The quantity half now has an obvious lever that did not exist before.
`builtCount` is a *per-robot* static, so each King runs its own
`MAX_POPULATION` budget -- four Kings can build four times as much. What
throttles it is `REPLACEMENT_RESERVE = 1000`, which four Kings enforce
independently, locking up ~4000 cheese. That constant was calibrated when
losing *the* King was an instant loss; with four Kings the downside of
spending is far smaller, so the reserve is now mis-calibrated for the
regime it operates in -- exactly the kind of stale-constant-in-a-new-
regime that Iterations 39/48/50 each turned out to be.

## Iteration 50 REJECTED on the benchmark -- greedy King formation costs more than it buys

    opponent          Iter48 (traps)   Iter50 (multi-King)
    bench_finalist        r1250              r1026
    bench_spaark           r748               r512
    bench_stroke           r703               r495

Worse on all three, still 0/60. The single `spaark` game that reached
r2000 with 4 Kings was an outlier, and reading a result off one game is
exactly the error the +-9-game noise floor was measured to prevent.

**Why it backfires:** `becomeRatKing` consumes **7 Baby Rats**, so
reaching 4 Kings costs 21 rats out of an army of ~36 -- more than half
of it -- plus 2 cheese/round upkeep each, plus each new King
independently enforcing `REPLACEMENT_RESERVE = 1000`. The scoring
analysis said `catDamage` (0.5 weight) is the last blocker and that
catDamage scales with rat count; greedy King formation pays for a
0.3-weight component using precisely the resource that produces the
0.5-weight one. It optimises the smaller term by starving the larger.

**Iteration 51: cap our own King count, which requires infrastructure we
never had.** There is no API for "how many Kings does my team have" --
only `canBecomeRatKing()`, which enforces the engine's global cap, not
any policy of ours. So implemented BC22 `LEARNINGS.md`'s census pattern:
an accumulator on shared slot 7, with whichever King acts first each
round publishing the previous round's tally to slot 8 and resetting.
Statics are per-robot, so each King's own `censusRound` identifies the
first actor without coordination. Slot 8 becomes a genuine **live**
count -- deliberately not a cumulative counter, the trap that caused the
`MAX_POPULATION` starvation lockout and that BC22 flags as its own
repeated mistake.

Marginal analysis behind `TARGET_KINGS = 2`: against an opponent holding
2 Kings, going 1->2 lifts our `livingKings` share from 33% to 50%
(+5 points) for 7 rats; 2->4 buys a further +5 for 14 more rats. The
later Kings cost double per point, in the exact currency that drives the
component we are actually losing.

## Iteration 51 REJECTED (inert), and multi-King rejected outright

Capping Kings at 2 produced results **byte-identical** to uncapped
Iteration 50 -- `finalist` 1026, `stroke` 495, `spaark` 512. An
identical result means the cap never bound: in most games we were
already forming fewer than 2 extra Kings, and the 4-King `spaark` game
that motivated the whole line was a single outlier.

That isolates the real conclusion: **the cost is King formation itself,
not the number of Kings.** Even one extra King consumes 7 Baby Rats,
and reverting King formation entirely (back to Iteration 48's
traps-only state) restores the best benchmark numbers on record
(r1250 / r748 / r703 vs. r1026 / r512 / r495). Multi-King is rejected on
the benchmark despite winning the `livingKings` component exactly as
designed -- winning a 0.3-weight term by spending the resource that
generates the 0.5-weight one is a bad trade, and the measurement says so
even though the mechanism worked.

**Kept as a finding, not as code:** the census pattern (accumulator on
slot 7, first-actor-per-round publishes to slot 8, first actor detected
via a per-robot static) is implemented and correct, and is the only way
this bot can ever know a live team-wide count -- there is no API for it.
It is reverted along with the King logic since nothing currently
consumes it, but it is recorded here because any future team-wide
policy (population targets, role quotas, coordinated timing) needs
exactly this and it took BC22's `LEARNINGS.md` to point at the pattern.

**State restored to Iteration 48** -- traps only -- which remains the
project's best result against tournament bots: survival r21-46 -> means
of r703-1250 with r2000 reached on all three opponents, still 0/60 wins.

## Iteration 48 ACCEPTED as g_iter15 -- regression check clean

Head-to-head against `g_iter14` on the full 27-map set: **27/54 (50.0%)
-- exactly neutral.** No regression from adding traps, and no
improvement either.

That is the ideal shape of result for this change, and it tells a
coherent story rather than a lucky one: traps only pay off against an
opponent that attacks the King. Our own lineage never does, so King-ring
traps sit unused and cost only the occasional King action -- hence
exactly 50%. The tournament bots' entire opening is a King rush, so the
same traps sit directly on the attack path -- hence survival going from
21-46 rounds to means of 703-1250 with r2000 reached against all three.

This is also the cleanest confirmation yet of *why* Iteration 15 was
wrong to reject traps and why nothing before the benchmark could have
caught it: measured against our own peers, traps are indistinguishable
from a no-op. The mechanic's value is entirely contingent on the
opponent, and every metric this project had until today was
self-referential.

    accepted state: g_iter15
    vs own baseline (g_iter14):  27/54 (50.0%)  -- neutral
    vs tournament bots:          0/60 wins, survival r703-r1250 (was r21-46)

## Iteration 52: kill cats instead of chipping them -- with one claim retracted

**Retraction first.** An earlier note in this session compared
`RatAttack` counts to `catDamage` totals and concluded the opponent
extracted ~114 damage per attack against our ~10.8. That figure is
**not supportable**. Checking the replay format: `DamageAction` entries
carry explicit damage values, but there are only 46 of them in a
2000-round game and **none target cats**, so bites do not emit damage
records at all. `RatAttack` therefore cannot be reconciled against
`catDamage`, and any per-attack efficiency computed from the two is
meaningless. The `MatchMaker` class that would settle the mapping is not
part of the vendored engine subset, so this cannot be resolved from
here. Retracted rather than left standing.

**What survives, and is well-supported:**

- `catDamage` totals come from the engine's own team stats and are
  trustworthy: **3770 for us, 17110 for them.**
- Four cats died in that game (rounds 205, 385, 518, and 1976).
- Their cat damage rockets to ~16700 by roughly round 800 and then
  **plateaus**, which is what running out of targets looks like:
  ~16700 is almost exactly four cats' worth of the 4000 HP each.
- The r1976 death was ours -- our single kill, arriving after the game
  was long decided.

Since `addDamageToCats` credits damage actually dealt, killing a cat
necessarily banks its full 4000 HP, while damage spread across six cats
that all survive banks only what was dealt and forfeits every
almost-kill. That conclusion does not depend on the retracted figure.

**Change:** target the **weakest** visible cat rather than the nearest.
`RobotInfo.getHealth()` exposes cat HP, so every rat independently
converges on whichever cat is nearest death -- focus fire with no
communication, since they all read the same health values. This is BC22
`RESEARCH.md` section 4's "target-prioritize by kill-efficiency, not raw
threat", noted when the benchmark was built and not acted on until the
replay showed precisely this failure mode.

Notable that every previous cat-damage attempt -- hunting (x3), kiting,
paying cheese per bite, growing the army -- adjusted *how much* damage
we deal. None addressed *concentration*, which is what converts damage
into a completed kill.

**Iteration 52 REJECTED (inert).** Benchmark byte-identical to Iteration
48: `finalist` 1250, `stroke` 703, `spaark` 748. Rats rarely see two
cats at once, so "weakest visible" and "nearest visible" pick the same
target and nothing changes.

**But checking why produced the decisive number of this whole thread:**

    our attack events per game        349
    max damage if ALL hit one cat     3490
    cat HP                            4000   -> kill IMPOSSIBLE
    observed catDamage                3770   (~377 bites, i.e. essentially
                                              all our attacks already hit cats)

**Even with perfect focus we cannot finish a single cat**, and our
attacks are already almost entirely spent on cats. So target selection
was never the constraint and Iteration 52 was inert *by construction* --
the arithmetic ruled it out before the Gauntlet did. The binding
constraint is **contact volume**: ~30 rats over 2000 rounds manage about
a dozen cat attacks each.

That also retroactively explains why every cat-damage attempt has
failed. Hunting (x3), kiting, cheese-boosted bites, army size and
targeting were all attempts to get *more or better damage per contact*.
None of them created more contact, and contact is the term that is
short by 5x.

## Iteration 53 (camp cat waypoints): first win against a tournament bot -- but treated as unproven

    overall            1/60  (was 0/60)
    vs bench_finalist  1/20  -- won `minimaze` at r2000
    vs bench_spaark    0/20
    vs bench_stroke    0/20

    survival:  finalist 1260 (was 1250), spaark 770 (748), stroke 670 (703)

The first win this project has ever taken off a real tournament bot.
Task #38's pre-registered rule called any nonzero win a breakthrough --
but that rule was written *before* this benchmark's variance was
understood, and **one win in sixty is not distinguishable from noise**.
The supporting evidence is equally mixed: survival is flat, and
`catDamage` moved in opposite directions on different opponents
(`finalist` 3764 -> 4188, but `spaark` 3770 -> 2724).

Honouring the *spirit* of the pre-registration rather than its letter:
the rule existed to stop me rationalising a 0/60 away, not to license
accepting a single lucky game. A pre-registered threshold that turns out
to be mis-calibrated should be corrected openly, not exploited because
it happens to favour the result.

So the standing marginal-result rule applies instead: **dose-response,
not resampling.** If camping is causal, doubling the camping fraction
(half the army -> all of it) should move wins further; if the single win
was chaotic variance, it will not. Running that now.

**Camping mechanism falsified by direct measurement.** Contact volume
was the diagnosed constraint, so camping had to *raise* attack counts to
work. It did the opposite:

    our attack events   Iteration 48 (roaming): 349
                        Iteration 53 (camping): 290
    their attack events                    150  ->  231

**Camping reduced our contact by 17% and nearly doubled theirs.** The
flaw is in what `catHotspot` actually holds: the *first tile where a rat
happened to see a cat*, which is wherever that cat was passing at that
moment -- not a patrol waypoint. Rats then hold station on an
arbitrary tile and wait for a patrol that has no particular reason to
return there, while giving up the encounters that roaming produced
naturally. The earlier waypoint evidence (93 scratches over 61 tiles,
busy sites revisited 3-6x) says *some* tiles are genuinely revisited --
but a single first sighting is overwhelmingly likely to be one of the 40+
tiles visited once, not one of the handful visited repeatedly.

So the single win at 1/60 was noise, exactly as the marginal-result rule
assumed, and the dose-response is expected to confirm by making things
worse. Identifying a real waypoint would require tracking *repeat*
sightings of the same tile, which no rat lives long enough to accumulate
and which cannot be shared (only Kings may write the shared array).

**Where this leaves the cat-damage problem.** Contact volume is
confirmed as the binding constraint, and every available lever has now
been measured against it: hunting a remembered position (x3) arrives
after the cat has left; camping a remembered position reduces contact;
kiting, cheese-boosted bites and kill-efficiency targeting all change
damage-per-contact, which is not the short term; and more rats did not
raise cat damage. Roaming -- the default behaviour -- produces the most
contact of anything tried. That is a genuine, well-evidenced dead end
rather than an untested idea, and it is worth recording as such.

**Dose-response confirms camping is non-causal.** Doubling the camping
fraction (half the army -> all of it) gave **0/60**, down from 1/60,
with survival worse on every opponent (1230/642/725 vs. 1260/670/770).
More of the mechanism produced less of the result, so the single win was
chaotic variance, exactly as the marginal-result rule predicted.

**Iteration 53 REJECTED.** Reverted to `g_iter15`, verified
byte-identical to the accepted snapshot.

This also vindicates not accepting on the pre-registered "any win is a
breakthrough" rule. Had that been applied literally, a change that
*reduces* our contact volume by 17% would now be the baseline, and every
subsequent measurement would have been taken against a worse bot.
The lesson is not "don't pre-register" -- pre-registration is what
stopped the earlier 0/60 from being rationalised away -- but that a
pre-registered rule is a commitment about *reasoning*, not a licence to
skip verification when the number happens to land favourably.

## Iteration 54 — grab and throw enemy rats (attacking the share, not our own damage)

The insight that reframed the problem: **scoring is share-based.**
`catDamage%` is ours/(ours+theirs), so reducing *their* damage raises our
share exactly as much as raising ours. Our own cat damage is a
documented dead end -- a 4000 HP cat is unkillable at our contact volume
-- but their cat damage is produced by *their rats*, and an enemy rat
has 100 HP and dies to ten bites. Twenty-odd iterations were spent
trying to move the immovable number while the movable one sat next to it.

The supporting measurement is stark: across a full game we lose
**96 units to their 33** -- a 3:1 slaughter -- which is why their army
holds at 82-96 against our 30, and therefore why their cat damage is 5x
ours. One exchange ratio explains the entire cascade.

`carryRat` + `throwRat` is the tool they use constantly and we never
have (`THROW_DAMAGE=10`, `THROW_DAMAGE_PER_TILE=4`,
`THROW_DURATION=4`, plus a stun on landing; `THROW_RAT_COOLDOWN=20`).
Two bites' worth of time to damage a rat *and* remove it from the fight
is a far better trade than swapping bites one-for-one while losing 3:1.
Iteration 17 rejected the nap mechanic, but pre-benchmark against peers
who also lacked it -- the same regime error as Iterations 15 and 18.

**Result on the 10-map benchmark: 2/60 (3.3%)**, against a hard 0/60
baseline, with the mechanism verified engaged and the exchange ratio
improving from **2.9:1 to 2.4:1** (their losses 33 -> 39, ours 96 -> 94).

Two wins still sits inside the measured noise floor, and unlike a
threshold this mechanic has no dose to scale, so the usual
dose-response is unavailable. Running the **full 27-map set (162
games)** instead -- more maps is genuinely more information, unlike
re-running identical deterministic games.

**Iteration 55: face the target before grabbing.** Attributing throw and
nap events by team (the check missed twice earlier in this session, on
trap triggers and cat deaths) shows we *do* use the mechanic but at a
third the opponent's rate: **16 throws to their 43**, with `RatNap`
symmetric at 66 each (that event marks the victim, not the actor).

Reading `assertCanCarryRat` explains the gap: it requires
`canSenseLocation`, which for a Baby Rat is **cone-gated to its
90-degree facing** -- the engine's own error string is "a rat can only
grab robots in front of it". Iteration 54 called `canCarryRat` without
ever turning toward the target, so most attempts failed silently on
facing alone. Turning draws on a separate cooldown from actions, so
facing the enemy first costs nothing we would otherwise spend.

This is the same class of bug as the `Direction.allDirections()`
`CENTER` entry found in Iteration 32 -- a precondition buried in the API
that silently voids an action, invisible in win rates and only findable
by reading the engine's assertions.

## Iteration 54 on the full 27-map set: 6/162 (3.7%) from a 0/60 baseline

    vs bench_finalist   5/54  (9%)
    vs bench_spaark     0/54  (0%)
    vs bench_stroke     1/54  (2%)

Six wins where the accepted baseline had never taken a single game off
these opponents -- and this was measured on the **hobbled** version, at
one third the intended throw rate, before the Iteration 55 facing fix.

**A control is still required before calling this real.** The baseline's
zero has only ever been measured on the 10-map loop (0/60), never on
these 162 games. That matters more than usual here because of an
asymmetry in the noise: chaotic sensitivity flips games in both
directions, but **from a floor of zero it can only add wins** -- you
cannot lose fewer than none. So a handful of wins could in principle be
near-miss games tipped over the line by any perturbation, which is
exactly the reading that the +-9-game churn measurement warned about.
Running `g_iter15` over the identical 162 games settles it; the Gauntlet
is deterministic, so that control is exact rather than an estimate.

Queued behind the Iteration 55 (facing-fix) run, which tests the
mechanism at full strength.

**The six wins were mostly early King kills, not point wins.** Round
numbers: r174, r261, r301, r504, r545, and one r2000.

That is a materially different win condition from the one the scoring
analysis has been chasing. Five of six games ended well before the
2000-round cap, which means they ended by **`RATKING_DESTROYED`** -- we
killed their King -- rather than by winning the
`0.5*catDamage + 0.3*livingKings + 0.2*cheeseTransferred` comparison.

This reframes what grab-and-throw is actually doing. The change was
justified as raising our `catDamage` *share* by removing enemy rats. The
wins suggest a more direct effect: an enemy rat that is grabbed and
thrown is stunned and displaced, and enough of that near their King
apparently opens a path to killing it outright. Elimination bypasses the
scoring formula entirely -- and the formula is where we are structurally
weakest, since `catDamage` at 0.5 weight is a documented dead end.

Worth noting five of the six wins are against `bench_finalist`
specifically, so this may be an opponent-specific vulnerability rather
than a general capability. The control run and the Iteration 55
(facing-fix) run together will show whether the effect strengthens with
throw rate -- the closest thing to a dose-response available for a
binary mechanic.

## Iteration 55 (facing fix): 5/162 -- but the win-SET comparison is the real result

    Iteration 54 (no facing fix)  6/162
    Iteration 55 (facing fix)     5/162

By totals the facing fix did nothing, and 6 vs 5 is meaningless. But
comparing win *sets* rather than counts -- the technique established
when the +-9-game noise floor was measured -- is far more informative:

    shared by both versions (4):
      minimaze | peaceinourtime | popthecork | toomuchcheese   (all bench_finalist)
    only Iteration 54 (2):  closeup|finalist, whatsthecatdoin|stroke
    only Iteration 55 (1):  closeup|spaark

**Four wins are stable across two materially different code versions.**
That is much stronger evidence than either total alone: chaotic
game-flipping is by definition perturbation-sensitive, so wins that
survive a real change to combat behaviour are unlikely to be
chaos. The 2-3 non-shared wins look like exactly the churn the noise
floor predicts.

So the honest reading is roughly **four robust wins plus one to two
noise wins**, not "six" or "five". And all four robust wins are against
`bench_finalist`, which supports the earlier suspicion that this is an
opponent-specific vulnerability rather than a general capability.

The control (`g_iter15` over the identical 162 games) is now the
decisive test: if the baseline also wins those four, grab-and-throw
contributed nothing; if it wins none, the four are attributable. Running.

## Invalid control caught before it produced a wrong conclusion

The first "control" run was not a control. `git stash` was used to
revert to the baseline, but Iteration 55's source had already been
**committed**, so the working tree was clean and `stash` did nothing.
The run therefore re-executed Iteration 55 against itself.

It was caught by a consistency check rather than by luck: the control's
wins were landing on the same maps *at identical round numbers*
(`r545`, `r278`, `r167`, `r718`) as Iteration 55's. Since the Gauntlet
is deterministic, identical rounds means identical code -- a control
that reproduces the treatment exactly is not measuring anything.

**The conclusion it would have supported was the opposite of the truth**:
"the baseline wins the same four games, so grab-and-throw contributes
nothing" -- an argument for reverting a change on the basis of a run
that was testing that very change. Verified the fix explicitly this time
(`canCarryRat` count 0, plus a byte-level `diff` against `g_iter15`)
before starting the real control.

General lesson, and the second version-control slip of this session
after the `git add -A` incident: **when reverting for a control, assert
the revert actually happened.** A committed change is invisible to
`git stash`, and "I ran the revert command" is not evidence the code
changed -- only a diff or a symbol count is.

## The verified control overturns the "breakthrough": baseline already wins 5/162

    g_iter15 baseline (control)   5/162   (4 distinct map|opponent pairs)
    Iteration 54 (grab+throw)     6/162
    Iteration 55 (+ facing fix)   5/162

**The baseline was never 0 on this map set.** The 0/60 figure that made
six wins look like a breakthrough was measured on the **10-map loop**;
the 162-game runs use the **27-map full set**, which contains maps we
already win. Comparing a treatment on 27 maps against a baseline
measured on 10 was an invalid comparison, and it manufactured a
breakthrough out of a map-set change.

Win-set comparison against the true control:

    baseline already won:  peaceinourtime|finalist, popthecork|finalist,
                           toomuchcheese|finalist, whatsthecatdoin|stroke
    iter54 added:          closeup|finalist, minimaze|finalist
    iter54 lost:           (none)

So three of the four "robust wins" -- the ones that looked convincing
precisely because they survived two code versions -- are simply games
the baseline wins anyway. They were stable across versions because they
are stable *full stop*, having nothing to do with grab-and-throw.

Net attributable effect: **+2 games at 162, inside the noise floor**,
and Iteration 55 nets **zero**. Two mechanistically real changes
(engagement verified, exchange ratio measurably improved 2.9:1 -> 2.4:1)
that do not convert into wins.

**Iterations 54 and 55 REJECTED.** Reverted to `g_iter15`, verified.

**The methodological failure is the more valuable finding.** Every
individual step was defensible -- a mechanism confirmed to engage, an
exchange ratio that genuinely improved, wins stable across two versions
-- and the conclusion was still wrong, because the *baseline* silently
changed underneath the comparison. The win-set technique that has been
reliable all session gave a confidently wrong answer here for exactly
that reason: it compared two treatments to each other and never to a
control on the same maps.

Rule going forward, added to the accept criteria: **a treatment and its
baseline must be measured on the identical map set, in the same
session, with the revert verified by diff.** Reusing a remembered
baseline number from a different configuration is not a control.

---

## Iteration 56 — kill-efficiency targeting vs enemy rats — REJECTED (inert)

Swapped `nearestEnemyRat` for a new `weakestEnemyRat` (target the lowest-HP
enemy rat in range, per BC22 RESEARCH.md §4 kill-efficiency).

| measurement | control | Iteration 56 |
|---|---|---|
| benchmarks (162 games, 27 maps) | 5/162 | **5/162** |
| peers, `immediate_defector` (54) | 36/54 = 66.7% | 37/54 = 68.5% |
| peers, `pure_cooperator` (54) | 29/54 = 53.7% | 27/54 = 50.0% |
| **peers overall (108)** | **65/108 = 60.2%** | **64/108 = 59.3%** |

Inert on both instruments. Rejected.

**The map-set rule from Iterations 54/55 paid for itself immediately.** The
last recorded peer baseline was 10/20 = 50.0% and 5/20 = 25.0%, measured on
the **10-map loop**. Against those numbers Iteration 56 looked like a large
win, especially the apparent 25% -> 50% jump vs `pure_cooperator`. Running
the control on the **same 27-map set** showed the real baseline is 53.7%,
so that jump never existed — it was entirely the map-set change, the exact
error that manufactured the Iteration 54 "breakthrough". Second time this
specific trap has been caught by the same rule.

**Per user guidance, the peer Gauntlet was run even though the benchmark
result was flat** ("a change might not win any new benchmark runs, but
still make progress against the peer gauntlet"). Here both were flat, so
the verdict is clean — but the benchmark set alone could not have
established that, being floor-bound at 0-10% with no resolution to
distinguish "inert" from "helped but not enough to flip a game".

---

## Finding: three failed cat-hunting iterations were tuning unreachable code

Not an iteration — a root cause, found while investigating why our
`catDamage` is always near zero.

`runBabyRat` ordered its logic as:

    if (rc.getRawCheese() > 0 && kingLoc != null) {
        if (deliverCheese(rc, kingLoc)) return;   // <-- returns here
    }
    RobotInfo nearestCat = ...                    // <-- cat policy, below

Our rats carry cheese nearly all the time, so **the entire cat
engage/bite policy was unreachable for most robots on most turns.**
Iterations 22, 27 and 44 all tried to make us fight cats and all measured
as inert; they were tuning a branch that mostly never ran.

Measured in `bench_finalist__hatefullattice__botB`:

| | bench_finalist | us |
|---|---|---|
| `RatAttack` | **1015** | 57 |
| cat damage | **9720** | 142 |

9720/1015 ≈ 9.6 ≈ `RAT_BITE_DAMAGE` 10 — essentially every one of their
attacks is a bite on a cat. Across a 15-replay sample our share of all cat
damage dealt was **14.6%** (5134 vs 29984).

### Three claims of mine that the evidence overturned

1. **"catDamage is unreachable because cats have 4000 HP."** Non-sequitur.
   `addDamageToCats` accrues per point of damage and the score uses each
   team's *proportion*, so **the cat never has to die**.
2. **"So it must come from CAT_TRAPs."** Wrong mechanism, caught before
   shipping: `bench_finalist` places **81 rat traps and zero cat traps**
   while scoring 9720 cat damage. Cat-trap code was written and reverted
   unrun. Lesson: confirm the opponent actually *uses* a mechanism before
   building it.
3. **"catDamage is weighted 0.5."** Only while cooperating. Coop ends at
   round 39 in that game and cat damage is still `[0,0]` at round 75, so
   effectively all of it accrues under backstab weights — catDamage **0.3**,
   `livingKings` **0.5**. Since 91% of our losses are King-death losses,
   King survival is plausibly the larger lever.

### Loss-mode census (control, 157 losses vs the three tournament bots)

    <100 rounds (early King wipe)   25  (16%)
    100-499                         47  (30%)
    500-1999                        71  (45%)
    2000 (timeout / score loss)     14  ( 9%)

91% are King-death losses; only 9% are decided on points. Early wipes
concentrate on small maps (`knifefight` 6, `tiny` 6, `thunderdome` 5,
`dirtfulcat` 4). The A/B side asymmetry seen in peer mirrors is absent
here (79 A / 78 B).

### Backstab mechanic (engine-verified)

`GameWorld.triggerTrap` calls `backstab(robot.getTeam().opponent())` where
`robot` is the *triggering* robot — so **the backstabber is the trap's
owner**, and it latches on the first trigger of the game only. The
backstabber is permanently barred from placing cat traps
(`catTrapsAllowed`). In the traced game our rat tripped their trap at round
39, making *them* the backstabber. Relevant to Iteration 48's rat-trap ring,
which risks handing that label to us.

---

## Finding: Iteration 48 put trap-laying on the King, halving our opening build rate

Root cause for the early King wipes (16% of all losses vs tournament bots
end before round 100, concentrated on `knifefight`, `tiny`, `thunderdome`,
`dirtfulcat`).

Both `BABY_RAT` and `RAT_KING` have `actionCooldown` 10 against
`COOLDOWNS_PER_TURN` 10 — **one action per turn**. So every trap the King
lays is literally one rat it did not build. Iteration 48 put trap placement
in `runRatKing`, interleaved with building. The opponent does not.

King action budgets, `bench_finalist` losses (bot = team1):

| map | our kingSpawn | our kingTrap | their kingSpawn | their kingTrap | their **rat**Trap |
|---|---|---|---|---|---|
| knifefight | 22 | 18 | 35 | 0 | 32 |
| tiny | 21 | 17 | 39 | 1 | 10 |
| thunderdome | 25 | 21 | 36 | 0 | 55 |

Their King builds essentially **exclusively**; all their traps are laid by
**baby rats**. Ours serializes both through one budget — on `knifefight`,
40 King actions split 22 spawns / 18 traps, so **45% of the King's opening
went to traps**. The result is a consistent 21-25 vs 35-39 production
deficit (about -37%) at the exact moment we are being rushed, on maps where
the Kings start 5 tiles apart and `ThrowRat` pressure begins at round 6.

### The traps are also in the wrong place

Placement geometry on `knifefight` (our King (22,14), theirs (17,14)):

| | traps | mean d² to own King | mean d² to enemy King | placed closer to enemy |
|---|---|---|---|---|
| us | 18 | 5 | 19 | **0/18** |
| bench_finalist | 32 | 19 | 11 | **21/32** |

Ours all hug our own King; two thirds of theirs are pushed **forward**
onto our approaches. That asymmetry shows up directly in who walks into
what: we triggered **27** traps, they triggered **13**.

**This indicts Iteration 48's implementation, not the idea.** Traps are
clearly good — the opponent lays 32-55 of them. Laying them *with the King*,
*around the King*, is the error. Iteration 59 moves placement onto baby
rats, which both frees the King's build budget and puts traps forward for
free, since that is simply where rats already are.

Note this is a second instance of the pattern behind the cat-policy bug:
a mechanism that measured as working (Iteration 48 did lift `bench_finalist`
0% → 7-10%) while quietly paying a large cost elsewhere that no single-number
win rate exposed.

---

## Iteration 57 — bite in-range cats before the delivery return — REJECTED

Hoisted `nearestCat` above the cheese-delivery early return and took a free
bite when a cat was already in range, to reach the cat policy that carriers
could never reach (see the unreachable-branch finding above).

| | control | Iteration 57 |
|---|---|---|
| peers, `immediate_defector` (54) | 36/54 = 66.7% | 36/54 = 66.7% |
| peers, `pure_cooperator` (54) | 29/54 = 53.7% | **23/54 = 42.6%** |
| **peers overall (108)** | **65/108 = 60.2%** | **59/108 = 54.6%** |

−6 games is inside the ±9 noise band at this sample size, so the win rate
alone would have been ambiguous. The mechanism check settled it — on two
games present in both runs:

| game | our catDamage | our `RatAttack` |
|---|---|---|
| `immediate_defector__dirtfulcat__botB` | 2496 → **1150** | — |
| `immediate_defector__closeup__botB` | 1338 → **780** | 258 → **179** |

**Adding an extra attack made us attack less and deal less cat damage.**
That is an inversion, not noise, and per the dose-response rule an inverted
effect rejects the approach rather than retuning it.

### Why — and it is the same hazard the iteration was fixing

The code comment claimed this change "changes no positioning at all",
distinguishing it from the three failed hunting iterations. **That was
wrong.** Spending the action at the top of the turn flips which branch runs
later: by the time the rat reaches the cat block, `rc.canAttack()` is now
false, so it skips the attack-and-return path and falls through into the
`engage`/`flee` **movement** path instead. An early action silently
rewrote downstream control flow — the same class of bug as the unreachable
branch it was meant to repair, in the opposite direction.

Generalised rule: in a turn function built from `if (...) return;` stages,
**consuming a resource early is never local** — it changes which later
stages fire. Check the branch conditions downstream of any new action, not
just the action's own cost.

The underlying measurement stands and is not retracted: we deal 14.6% of
all cat damage, and `bench_finalist` bites cats ~1015 times a game to our
57. The diagnosis was right; this particular repair was not.

---

## Iteration 59 — King builds instead of fighting/trapping — REJECTED (both instruments)

Moved `attackNearestHostile` below the build attempt, removed the King's trap
branch, and gave trap-laying to non-carrying Baby Rats.

| | control | Iteration 59 |
|---|---|---|
| peers (108) | **65/108 = 60.2%** | **53/108 = 49.1%** |
| benchmarks (162) | **5/162** | **3/162** |

Worse on both, and the peer regression (−12) is well outside the noise band.

### The mechanism fired perfectly and bought nothing

On `pure_cooperator__hatefullattice__botB`, a 2000-round game lost by both:

| | control | Iteration 59 |
|---|---|---|
| King `SpawnAction` | 126 | **126** |
| King `PlaceTrap` | 23 | 0 |
| King `RatAttack` | 0 | 5 |
| **rat**-placed traps | 0 | 28 |

The intervention did exactly what it was designed to do — and produced
**zero extra rats**. 2000 rounds is 5 build windows, so `MAX_POPULATION`
25 sets a 125-build ceiling: **the King was pinned at the cap the entire
game**, never waiting on actions. Freeing an unconstrained resource
changes nothing, and we paid for it by giving up 23 traps and the King's
defensive bites.

### What the diagnosis got wrong

The Iteration 48 root-cause analysis was accurate as arithmetic (the King
really does spend 41% of actions attacking, 26% trapping) but wrong about
the binding constraint. **Measuring how a resource is *spent* does not
establish that the resource is *scarce*.** The King's action budget was
being spent inefficiently and was simultaneously not the limiter — both
true at once, which the action census alone could not distinguish.

Two different maps have two different binders, which is why the census
misled:

- **Long maps** (`hatefullattice`, 2000 rds): the **population cap** binds —
  126 spawns against a 125 ceiling, while holding 10,797 cheese and 11 rats.
- **Short rush maps** (`knifefight`, 77 rds): the **King's actions** bind —
  2250 cheese at round 25 with only 3 rats alive, so cheese was abundant and
  the King's 68 actions really were the limit.

Iteration 59 addressed only the second, and lost the traps that mattered
in it.

### The real gap

| | their live rats | ours |
|---|---|---|
| `hatefullattice` rd 550 | 56 | 6 |
| `hatefullattice` rd 1175 | 61 | **0** (cheese 0) |
| `knifefight` rd 75 | 25 | 5 (King dies rd 77) |

A 5–10× army deficit, self-reinforcing: fewer rats → less cheese collected
→ cannot rebuild → zero income. Our cheese reaching 0 by round 1175 is a
*consequence* of the cap, not an independent economy problem.

Note `MAX_POPULATION` caps **builds per window**, not live population, so
attrition leaves us blocked at 5–11 living rats having spent the quota on
replacements. Iteration 60 raises it 25 → 50 as a dose, changing nothing
else, and is evaluated primarily on the **benchmark** set — the peer bots
are forks of this same file carrying the identical cap, so that Gauntlet is
structurally blind to this change.

---

## Iteration 60 — MAX_POPULATION 25 → 50 — REJECTED, and it refutes the whole production thesis

Benchmarks: **1/162** against a control of **5/162**. Worse.

The mechanism fired exactly as designed. `tools/king_census.py` on
`bench_finalist__hatefullattice__botB`, the same game in both runs:

| window | rounds | spawn | alive | cheese | verdict |
|---|---|---|---|---|---|
| **control, cap 25** ||||||
| 0 | 0-399 | 25 | 4 | 2306 | CAP-LIMITED |
| 1 | 400-799 | 25 | 1 | 726 | CAP-LIMITED |
| 2 | 800-1199 | 16 | 0 | 0 | cheese-limited |
| **Iteration 60, cap 50** ||||||
| 0 | 0-399 | **50** | **4** | 565 | CAP-LIMITED |
| 1 | 400-799 | 22 | 0 | 35 | cheese-limited |
| 2 | 800-1199 | 0 | 0 | 1 | cheese-limited |

**We built twice as many rats in window 0 and ended it with exactly the same
four alive.** The extra 25 rats died as fast as they were produced, and paid
for it out of the treasury — cheese at the end of window 0 fell from 2306 to
565, bringing bankruptcy forward a full window and ending the game with zero
spawns in window 2.

### This retires the production thesis behind Iterations 59 and 60

Three iterations chased army size (59 freed the King's actions, 60 raised the
cap) on the strength of a real and repeatedly-confirmed observation: the
opponent fields 25-61 rats to our 4-6. That observation is correct. The
inference from it was wrong.

**`alive` is invariant at 4 across a 2× change in production.** Our rats are
not scarce because we fail to build them; they die on contact at whatever
rate we build them. Production was never the binding constraint on *army
size* — survival is. Every lever pulled on the production side has now come
back inert or negative:

| iteration | lever | spawns | live rats | result |
|---|---|---|---|---|
| 59 | free the King's actions | 126 → 126 | — | worse both |
| 60 | double the cap | 25 → 50 (window 0) | **4 → 4** | worse |

The pattern across this whole session is one error repeated in three forms:
**a correct measurement of a symptom, followed by a causal story that the
measurement did not license.** The action census showed how the King spends
its budget and I read it as scarcity. The population gap showed how many rats
each side fields and I read it as a production shortfall. In both cases the
number was right and the mechanism it implied was not, and in both cases the
refutation came from a *counterfactual* measurement — change the input, see
whether the output moves — rather than from more careful inspection of the
original number.

### Where this points

Survival, not production. Supporting evidence already collected:

- `knifefight`: we build 22 rats and have **5 alive** at round 75; the
  opponent's 32 forward-placed traps stunned ours **27** times (they
  triggered only 13 of ours), and each `RAT_TRAP` is 50 damage on a 100 HP
  rat plus a **30-round** movement freeze.
- `hatefullattice`: 88 `StunAction`s and 71 trap triggers against us.

Building more rats to feed into that is what Iteration 60 tested, and it
bought nothing. The next hypotheses must reduce the death rate.

---

## Iteration 62 — retreat from enemy rats at ≤50 HP — REJECTED

Benchmarks **3/162** against a control of **5/162**.

Before this, `getHealth()` was consulted exactly **once** in the entire bot
(the cat-engagement gate) and `flee()` was only ever called against cats —
so against enemy rats our rats advanced until killed, at any health, with no
retreat rule at all. 50 was chosen because `RAT_TRAP` deals exactly 50 to a
100 HP rat, so it marks "one hit from death", and we trigger 27 of their
traps per short game to their 13.

The mechanism fired, and the trade went the wrong way:

| `bench_finalist__hatefullattice__botB` (we are team2) | control | Iteration 62 |
|---|---|---|
| our deaths | 67 | **70** |
| `CatScratch` against us | 242 | **297** |
| our `TriggerTrap` | 71 | 65 |
| final `aliveBabies` | 0 | 0 |

Trap triggers fell as intended (71 → 65), so the retreat was real. But
deaths rose and cat scratches rose 23%: **pulling rats back from enemy rats
just fed them to cats instead.**

### The rejection is the useful part

This was a pre-registered discriminator between the two survival hypotheses,
and it settled them. Enemy rats are not what kills us.

Damage accounting on that same control game:

    242 CatScratch  x CAT_SCRATCH_DAMAGE 20 = 4,840
     71 trap hits   x RAT_TRAP damage    50 = 3,550
    ------------------------------------------------
    our 67 dead rats                        = 6,700 HP

Cats alone nearly account for every rat we lose. And the return on all that
dying was **142 cat damage against their 9,720** — we pay on both sides of
the ledger simultaneously.

### This also finally explains the economy collapse

| round | their live rats | ours | their cheese delivered | ours |
|---|---|---|---|---|
| 225 | **36** | 7 | 2990 | 1415 |
| 525 | 50 | 8 | 10965 | 2115 |
| 1125 | **63** | **0** | 29895 | 2595 |

Our cheese *per rat* is comparable-to-better (round 525: theirs 10965 over
50 rats, ours 2115 over 8). So there is no collection problem; and per
Iteration 60 there is no production problem either, since doubling builds
left live rats at exactly 4. There are simply never many of our rats alive,
because they keep walking into cats.

Iteration 63 raises the cat-engagement gate from `allies > 1 || health > 30`
to `allies >= 3`, restoring flight as the default for a lone rat.

---

## Iteration 63 — require `allies >= 3` before closing on a cat — FIRST GAIN

Benchmarks **7/162** against a control of **5/162**, and broader than the
raw total suggests:

| opponent | control | Iteration 63 |
|---|---|---|
| `bench_finalist` | 4/54 | 3/54 |
| `bench_spaark` | **0/54** | **1/54** |
| `bench_stroke` | 1/54 | **3/54** |
| total | 5/162 | **7/162** |

This is the first time all three tournament bots have been beaten in one
run. `bench_spaark` had never been beaten at all.

Mechanism check on `bench_finalist__hatefullattice__botB` (we are team2):

| | control | Iteration 63 |
|---|---|---|
| our deaths | 67 | **62** |
| `CatScratch` against us | 242 | **220** |
| our catDamage | 142 | 24 |
| live rats at round 225 | 7 | 7 |

Every counter moved in the predicted direction, including the accepted cost
(catDamage down, which at 142 vs their 9720 was worth almost nothing).

### A constant I had wrong, corrected

The first draft of this change argued that a cat "out-reaches" a rat,
citing `CAT.visionConeRadiusSquared` 17 against `ATTACK_DISTANCE_SQUARED` 2
and claiming the rat absorbs scratches across ~3 tiles of approach. **That
is wrong** — 17 is how far a cat *sees*. Cats attack through the same
`RobotController.canAttack`, so both sides strike at range 2 and the
approach is not a gauntlet.

The argument survives on the arithmetic that does hold: `CAT.actionCooldown`
30 against `COOLDOWNS_PER_TURN` 10 means a cat scratches every third turn
(~6.67 damage/turn to our 10/turn), a rat survives 5 scratches ≈ 15 turns,
and in 15 turns it deals ~150 damage into a 4000 HP pool. **Each rat trades
its whole life for under 4% of one cat** — and measurement shows we do far
worse than even that, at 242 scratches taken for 142 damage dealt.

### Also corrected: what triggers a backstab

`InternalRobot.bite()` ends with `if (targetRobot.getType() != UnitType.CAT)
{ gameWorld.backstab(this.team); }` — so biting any **non-cat** names the
**attacker** as backstabber. Earlier notes claimed only having your own trap
triggered could do that. Both attributions exist and they read oppositely:
`triggerTrap` names the trap's *owner*, `bite` names the *attacker*.

### Iteration 64: dose-response, not resampling

+2 games could be a map-specific accident, and the Gauntlet is
deterministic so re-running proves nothing. Iteration 64 therefore scales
the mechanism to its limit — `CAT_ENGAGE_MIN_ALLIES = MAX_VALUE`, i.e.
never deliberately close on a cat — while leaving the free opportunistic
bite in place. Monotone improvement means causal; flat means Iteration 63
was noise; inverted means some cat contact is worth it and 3 was near
optimal.

---

## Iterations 63/64 — cat-engagement gate — REJECTED on peers, but they located the real trade-off

The dose curve on **benchmarks** looked causal and clean:

| variant | benchmarks |
|---|---|
| control (`allies > 1 \|\| health > 30`) | 5/162 |
| `allies >= 3` | **7/162** |
| never engage | 6/162 |

Both arms beat the control from two independently compiled builds, and the
win-set comparison showed **3 genuinely new wins shared by both arms**
(`bench_finalist|whatsthecatdoin|A`, `bench_stroke|uneruesansfin|A` and `|B`)
against 2 shared losses — so +1 robust, plus one arm-specific `bench_spaark`
win. Not the clean +2 the totals implied, but real.

**Then the peer run destroyed it: 41/108 = 38.0% against a 65/108 = 60.2%
control.** A 24-game collapse dwarfs a 2-game benchmark gain.

### One number explains both results

`pure_cooperator__hatefullattice__botB`, our catDamage:

| | control | `allies >= 3` |
|---|---|---|
| ours | **4644** | **480** |
| theirs | 6010 | 6070 |

We were splitting cat damage nearly evenly with the peer and we handed the
whole component over. Every score term is a *proportion*, so conceding a
contested one costs up to its full weight (0.3 after backstab, 0.5 while
cooperating).

Against the tournament bots the same change cost nothing, because our share
was already 142 to their 9720 — **1.4%**. There was nothing left to concede,
so the survival saving was pure profit.

The generalisation is neither "cats are a trap" nor "cats are worth
fighting":

> **Contesting cat damage pays exactly when the race is close, and refusing
> to pays when it is already lost.**

This is the first result this session where a change was genuinely *right on
one opponent class and wrong on another*, rather than simply inert. It is
also a caution about the benchmark set as sole arbiter: measured only there,
`allies >= 3` looked like the session's breakthrough.

### Iteration 65

The threshold was the wrong knob. The control gate was
`allies > 1 || rc.getHealth() > 30` — engage with a swarm, **or** engage
*alone* whenever merely healthy. Swarm engagement is what earns the
catDamage share; the solo clause is what feeds rats to a 4000 HP unit one at
a time, and it is the only indefensible part. `allies >= 2` keeps the first
and drops the second, and is being measured on peers first, where the risk
now demonstrably lies.

---

## Iteration 65 — `allies >= 2` — REJECTED, and it inverts the diagnosis

Peers **45/108 = 41.7%** against a 65/108 = 60.2% control. Barely better
than Iteration 63's 38.0%, so the *threshold* was never the knob.

Mechanism check, our catDamage vs `pure_cooperator` on `hatefullattice`:

| variant | peers | ours | theirs |
|---|---|---|---|
| control (`allies > 1 \|\| health > 30`) | **65/108 = 60.2%** | **4644** | 6010 |
| `allies >= 3` | 41/108 = 38.0% | 480 | 6070 |
| `allies >= 2` | 45/108 = 41.7% | **360** | 6210 |

**Requiring any ally at all is effectively "never engage."** Our rats
disperse to collect cheese, so two of them are almost never near the same
cat — which means the `health > 30` solo clause was producing essentially
*all* of our cat damage. What I had labelled the "indefensible" part of the
gate turned out to be the load-bearing part.

### The error underneath three iterations

The case against solo engagement was an *efficiency* argument: a rat trades
its whole life for under 4% of a cat, therefore the trade is bad. The
arithmetic is right and the conclusion does not follow, because catDamage is
a **share**. The opponent is making exactly the same bad trade, and the team
that stops making it forfeits the component entirely.

> For a contested proportional term, the question is never "is this trade
> efficient?" but "is the opponent making it too?"

That is the third time this session a correct piece of arithmetic has
licensed a wrong conclusion — after "cats have 4000 HP so catDamage is
unreachable" and "the King spends 41% of actions attacking so actions are
scarce".

### Iteration 66: condition on whether the race is winnable

Both answers are right somewhere, so the gate should not be a constant:

- **race close** (peers: 4644 vs 6010) → contest it; the share is what scores
- **race lost** (tournament bots: 142 vs 9720, i.e. 1.4%) → refuse; we are
  buying 1.4% of a component with rats we cannot replace

Observable proxy for "race lost": being badly outnumbered locally. Against
the tournament bots we field 6 rats to their 56 (5 to 25 on `knifefight`);
against peers it is near parity. `nearby` is already sensed, so the count is
nearly free. The gate becomes `!raceLost && (allies > 1 || health > 30)`
with `raceLost = enemyRatsNear > 2 * (allies + 1)`.

If this works it should reproduce the benchmark gain (~7/162) *without* the
peer collapse — the first change to satisfy both instruments.

---

## Iteration 66 — conditional cat engagement — INERT (the gate never fired)

Benchmarks **5/162**, matching the control exactly, including its
per-opponent split (finalist 4, spaark 0, stroke 1).

Row-by-row against the control, **161 of 162 games are identical in both
outcome and round count** — only `bench_spaark|closeup|A` differed. On a
deterministic Gauntlet that means the new code path essentially never
executed. The strategy was not tested; the proxy was.

### The constant I had been assuming away

`senseNearbyRobots()` is **not** a radius query for a Baby Rat.
`InternalRobot.canSenseLocation` passes the robot's facing and its
`visionConeAngle`:

```java
return this.location.isWithinDistanceSquared(
    toSense, getVisionRadiusSquared(), this.dir, getVisionConeAngle(), ...);
```

| unit | visionConeRadiusSquared | visionConeAngle |
|---|---|---|
| `BABY_RAT` | 20 | **90** |
| `RAT_KING` | 25 | 360 |
| `CAT` | 17 | 180 |

A rat sees a **quarter-circle wedge**, so any "count what is near me" test
is a noisy lower bound that depends on which way the rat happens to be
pointing — two adjacent rats can see entirely different sets of robots.
Requiring `enemyRatsNear > 2 * (allies + 1)` sightings inside a 90° wedge is
close to unsatisfiable.

The King is exempt at 360°, which is why King-side checks like Iteration
40's `noVisibleArmy` really do behave like radius queries. Worth noting
`rc.turn()` exists with its own cooldown, so facing is a resource — and we
have never managed it at all.

### Iteration 67

A dose-response step on the **proxy**, not the strategy: `raceLost` becomes
the absolute `enemyRatsNear >= 2`. Two enemy rats inside a 90° wedge already
implies a much larger surrounding force, which is the 56-vs-6 situation
against the tournament bots versus near parity against the peers.

First thing to check is not the win rate but whether the run still comes
back identical to the control. If it does, thresholds are the wrong lever
entirely and the count has to move to the King (360° vision) or to a
shared-array census.

---

## Iteration 67 — `raceLost = enemyRatsNear >= 2` — REJECTED. Closing the cat-engagement line.

Benchmarks **4/162** against a 5/162 control. This time the gate genuinely
fired — **127/162 identical to control**, versus Iteration 66's 161/162 —
so the conditional strategy was finally exercised, and it is worse than both
the control and the unconditional refusal.

### Why the proxy was anti-correlated with the need

Rats die to cats when they are **alone with one**. That is precisely the
situation in which `enemyRatsNear >= 2` is *false*, so `raceLost` stays low
and the rat engages anyway. When enemy rats *are* visible, refusing to fight
the cat saves little. The gate fires almost exactly when it should not.

### The whole line, five iterations, in one table

| variant | benchmarks | peers | our catDamage vs `pure_cooperator` |
|---|---|---|---|
| control (`allies > 1 \|\| health > 30`) | 5/162 | **65/108 = 60.2%** | **4644** |
| 63 `allies >= 3` | **7/162** | 41/108 = 38.0% | 480 |
| 64 never engage | 6/162 | — | — |
| 65 `allies >= 2` | 5→ n/a | 45/108 = 41.7% | 360 |
| 66 conditional, `> 2*(allies+1)` | 5/162 (inert) | — | — |
| 67 conditional, `>= 2` | 4/162 | — | — |

**Conclusion: the trade-off is real and has no local fix.** Refusing cat
engagement buys survival worth ~2 benchmark games and costs ~24 peer games
by conceding a proportional term we were splitting 4644-to-6010. No
*locally observable* proxy separates the two cases, because the deciding
variable — who is winning the global cat-damage race — is not visible from
inside a 90° vision cone. Iteration 67 was the honest test of that idea and
it failed for a structural reason, not a tuning one.

Reverted to the control gate, verified by grep.

### What this line did produce

- The proportional-share principle: *contesting a term pays exactly when
  the race is close, and refusing pays when it is already lost* — the same
  code change was right against tournament bots and wrong against peers.
- `BABY_RAT.visionConeAngle = 90`: sensing is a facing-dependent wedge, not
  a radius, so every per-rat "count what's near me" heuristic is a noisy
  lower bound. This invalidated Iteration 66 outright and is now recorded.
- A correction: our bot *does* manage facing (`tryMove` turns then moves
  forward), contrary to my first reading.
- A new lead (#57): `TURNING_COOLDOWN` 10 vs `COOLDOWNS_PER_TURN` 10 means
  one turn per round, and `tryMove` **returns without moving at all** when
  `canTurn` is false — a candidate root cause for the "rats stuck in a small
  region" behaviour that Iteration 32 only palliated. To be measured before
  any change.

---

## Iteration 68 — strafe straight when blocked from turning — INERT (162/162 identical)

Benchmarks 5/162, and **every one of the 162 games matched the control in
both outcome and round count**. The new fallback never executed once.

The reason retires the whole hypothesis rather than this attempt at it:
**`TURNING_COOLDOWN` is 10 and `COOLDOWNS_PER_TURN` is 10**, so the turning
cooldown regenerates exactly as fast as it is spent. `isTurningReady()` is
true every round, `canTurn` essentially never fails, and the "blocked from
turning" state I was writing a fallback for does not arise. Same arithmetic
applies to `BABY_RAT.movementCooldown` 10 — a rat can turn *and* move every
single round.

Two corrections stacked here, both caught before they became conclusions:

1. I first claimed `tryMove` "returns without moving at all" when `canTurn`
   fails. Reading the whole function, it falls through to sidesteps via
   `rc.move(d)`. Wrong.
2. Narrowed to the real gap — it never tries `rc.move(want)` itself, only the
   diagonals. True, but unreachable, per the cooldown arithmetic above.

The useful residue is the cooldown identity itself: with every cooldown at
10 against 10 regeneration, **turning and movement are not scarce resources
for a Baby Rat**, and only the *action* cooldown is genuinely contested
(bite vs place-trap vs transfer-cheese all cost it). Anything framed as
"our rats can't move enough" is not worth pursuing.

Also measured and dropped: `CHEESE_COOLDOWN_PENALTY` is 0.01 per unit
carried, but our delivery batches are small — median 40 cheese per
delivery-round against `bench_finalist`'s 780 — so our carry penalty is only
about ×1.1–1.4 and is not a meaningful brake.

---

## Iteration 69 — trap-zone avoidance — the mechanism works; the trade does not

Benchmarks **4/162** against a 5/162 control, but this was no inert run:
only **33/162** games matched the control, so 129 games changed. The
mechanism check on `bench_finalist__hatefullattice__botB`:

| | control | Iteration 69 |
|---|---|---|
| our `TriggerTrap` | 71 | **28** (−61%) |
| our deaths | 67 | **31** (−54%) |
| rats alive at end | **0** | **4** |
| `cheeseTransferred` | 2595 | **1200** (−54%) |

**We halved our deaths and halved our economy.** For the first time in this
game we finish with living rats — and with barely half the cheese, so the
win rate does not move.

### What this actually reveals about the opponent

Their forward trap placement is **area denial**, not just damage. On
`knifefight` 21 of their 32 traps sit closer to our King than to theirs —
on our approach lanes, which are also our cheese routes. So the traps win
either way: walk in and lose rats, or walk around and lose income. That is
a considerably better strategy than "lay traps to kill things", and it
explains why the damage accounting (traps ≈ a third of everything killing
us) did not translate into a win-rate gain when the damage was removed.

It also retires the framing behind #58. I argued trap avoidance was
strictly better than the cat line because "there is no score term attached
to being stunned, so there is no share to lose". True for the *stun*, and
wrong about the cost — the cost is paid in `cheeseTransferred`, which is a
0.2-weighted proportional term. Avoidance concedes ground, and ground is
where the cheese is.

### Iteration 70: the radius is the dial

`TRAP_ZONE_RADIUS_SQ` 8 → 2, i.e. dodge only the immediately adjacent tile
rather than a ~2.8-tile bubble. `RAT_TRAP.triggerRadiusSquared` is 2, so a
radius-2 zone is the smallest that can still cover a trap's actual trigger
footprint. If the death reduction survives at a fraction of the economic
cost, the trade becomes worth taking; if deaths snap back to 67, the effect
needed the wide bubble and the whole line is a wash.

---

## Iteration 70 — trap-zone radius 8 → 2 — the dose was not a dose

Benchmarks 4/162, and **162/162 games byte-identical to Iteration 69's
radius-8 arm**. Changing the radius changed nothing at all.

The reason is a mistake in how the dose was chosen, not in the strategy.
`reportTrapIfHit` publishes *the rat's own tile* — where it was standing
when it took 50 damage — and `avoidTrapZone` only ever tests the single
**adjacent** tile the rat is about to step into, which sits at distance²
1 or 2 from that point. Both radius 2 and radius 8 include d² ≤ 2, so the
condition actually evaluated was identical in both arms. The radius only
discriminates for tiles ≥3 away, and a one-step lookahead never asks about
those.

> **A parameter is only a dose if it changes the condition actually
> evaluated.** Scaling a number that sits outside the tested range produces
> a perfect null and looks exactly like "the mechanism is insensitive".

Had I not run the arm-to-arm identity check and instead compared only
against the control, "4/162 at both radii" would have read as a flat
dose-response curve — evidence that the mechanism is real but insensitive
to tuning. It is neither; the second arm was never a different bot.

### Verdict on trap avoidance

Rejected. The mechanism does what it claims — deaths 67 → 31, trap
triggers 71 → 28 — but pays for it one-for-one in `cheeseTransferred`
(2595 → 1200), because the traps sit on the lanes the cheese is on. There
is no dial on this design that separates the two: the detour *is* the cost.
Reverted to the control.

A real alternative would have to buy back the ground rather than concede
it — which is what the grab/throw system (#59) does, and that is the next
line.

---

## Iteration 71 — grab/throw for tempo — the third mechanism to trade deaths for economy

Benchmarks **4/162** against a 5/162 control, and emphatically not inert:
only **7/162** games matched the control, so 155 changed.

| `bench_finalist__hatefullattice__botB` | control | Iteration 71 |
|---|---|---|
| our `ThrowRat` | 0 | **58** |
| our grabs (`RatNap`) | 0 | **239** |
| our deaths | 67 | **51** |
| `cheeseTransferred` | 2595 | **1225** (−53%) |
| game ended | round 1175 | round **875** |

The system works — 58 throws and 239 grabs is the same order as
`bench_finalist`'s 88/318 — and it cut deaths by a quarter. We still lost,
sooner, because the economy halved.

### The pattern across three iterations

| | deaths | `cheeseTransferred` |
|---|---|---|
| 69 trap avoidance | −54% | −54% |
| 71 grab/throw | −24% | −53% |

Two unrelated mechanisms, the same trade. That is not a coincidence about
traps or throwing; it is the shape of our position:

> **With only 4-8 living rats, every rat-turn spent on anything other than
> collect-and-deliver is a large fraction of our entire economic output.**

`bench_finalist` affords 88 throws because it fields 56 rats. We cannot
afford 58 with six. Rat-turn-denominated mechanisms are priced *per capita*,
and we are poor — which also retroactively explains why so many
"do something smarter" iterations came back flat-to-negative while the
mechanism itself demonstrably fired.

It also closes a loop with Iteration 60: building more rats does not raise
the live count (spawns 25→50 left live rats at exactly 4), so we cannot buy
our way out of the constraint on the production side either.

### Iteration 72

Bound the spend rather than abandon the mechanism: throw only while
`getRoundNum() <= 100`. The opening is where a throw is worth most and
costs least — little cheese is on the ground yet, rats are still clustered
at spawn so a grab target is actually adjacent *and* inside the 90° cone,
and map presence compounds. `bench_finalist`'s first throw lands at round 6.

Note this is a legitimate dose, unlike Iteration 70's radius: it gates
whether the branch runs at all, so the two arms are guaranteed to differ.

---

## Iteration 72 — throw only in the opening — the economy flips positive

Benchmarks **5/162**, back to control parity from Iteration 71's 4/162.
Both identity checks pass: **15/162** identical to control (so it fires
heavily) and **47/162** identical to Iteration 71 (so this dose is real,
unlike Iteration 70's radius).

| `bench_finalist__hatefullattice__botB` | control | 71 (always) | 72 (≤ rd 100) |
|---|---|---|---|
| our `ThrowRat` | 0 | 58 | **43** |
| our deaths | 67 | 51 | **78** |
| `cheeseTransferred` | 2595 | 1225 | **3085** |
| game ended | rd 1175 | rd 875 | **rd 1325** |

**Bounding the spend flipped the economy from −53% to +19% above control**,
and the game runs 150 rounds longer. That is direct confirmation of the
per-capita theory: the same mechanism, priced into the cheap part of the
game, stops competing with cheese collection and starts feeding it — early
map presence finds cheese sooner.

Note 43 of the original 58 throws survive the round-100 gate, i.e. most
throwing was always happening in the opening anyway; what the gate removed
was the expensive tail.

The remaining cost is deaths, now **78 against the control's 67**. Thrown
rats land having taken `THROW_DAMAGE` 10 and frozen for a turn by
`HIT_GROUND_COOLDOWN`, and `hitGround` runs `processTrapsAtLocation`, so a
landing can also spring a trap. Throwing along our exploration facing sends
passengers toward unscouted ground.

### Iteration 73

Dose the passenger health gate, `> 50` → `> 80`. At the old bar a rat could
be launched at 51 HP and land on 41; at the new one it lands on 71+ and can
survive a cat scratch or a trap. This changes which rats qualify, so the arms
are guaranteed to differ.

If deaths fall back toward 67 while `cheeseTransferred` holds near 3085,
this becomes the first change of the session that is better than control on
both counters — at which point it needs the peer Gauntlet before acceptance.

---

## Correction — Iteration 72's "deaths rose to 78" was a rate-vs-total artifact

Normalising by game length changes the reading of the whole throw line:

| | deaths | rounds | deaths/rd | cheese | cheese/rd |
|---|---|---|---|---|---|
| control | 67 | 1175 | 0.0570 | 2595 | 2.21 |
| 71 (throw always) | 51 | 875 | 0.0583 | 1225 | **1.40** |
| 72 (throw ≤ rd 100) | **78** | **1325** | 0.0589 | 3085 | **2.33** |

**The death rate never moved** — 0.0570, 0.0583, 0.0589 are the same number.
Deaths "rose" purely because Iteration 72 survives 150 rounds longer. And the
celebrated +19% cheese is really **+5% per round**; the rest was extra rounds
of being alive.

A direct correlation check confirms landings are not what kills us: of 78
deaths only **5** fell within 5 rounds of one of our 43 throws, and only **7**
occurred in rounds ≤110 at all — while throwing ran rounds 14-95. Only 2 of
53 trap triggers were near a throw.

So **Iteration 73's premise is void.** It was launched to make landings
survivable; landings were never the problem. Whatever it scores will come
from throwing *less*, not from safer landings, and it should be read that way.

### Honest standing of the throw line

Iteration 72 buys **+5% cheese per round at an unchanged death rate**, and
150 more rounds of survival, for the same 5/162 wins. A real but small
economic gain that does not convert, because we still end with zero rats and
lose on King death.

### The methodological point

This is the second measurement artifact in three iterations, after Iteration
70's radius that was never a dose. Both had a plausible causal story attached
and both were wrong for arithmetic reasons rather than strategic ones. The
two checks that catch them are cheap and now routine:

1. **arm-to-arm identity** — did the two versions actually differ?
2. **normalise per round** — did the counter move, or did the game length?

Neither requires a new Gauntlet run.

---

## The throw line — REJECTED on peers

| variant | benchmarks | peers |
|---|---|---|
| control | 5/162 | **65/108 = 60.2%** |
| 71 throw always | 4/162 | — |
| 72 throw ≤ rd 100 | 5/162 | **51/108 = 47.2%** |
| 73 throw ≤ rd 100, hp > 80 | 5/162 | — |

Flat on benchmarks across three variants, **−14 games on peers**. Rejected
and reverted.

This is what the per-capita theory predicts. The +5%-per-round cheese gain
was measured against `bench_finalist`, an opponent whose 56 rats mean *our*
detour matters more to us than to them. Against peers — near-parity
opponents who do not throw — spending rat-turns on grabbing and throwing is
a straight relative loss.

### What the throw line established

- The grab/throw system is usable and we can drive it as hard as the
  tournament bots (58 throws, 239 grabs against their 88/318).
- Bounding a rat-turn mechanism to the opening genuinely flips its economics
  (cheese/round 1.40 → 2.33), confirming the per-capita account.
- It still does not convert, because our binding problem is not tempo.
- Two measurement artifacts caught along the way (Iteration 70's non-dose,
  Iteration 72's rate-vs-total), both now standing checks.

### Where next: multi-King, a dead end whose premise has expired

"Multi-King costs 7 rats each" was rejected earlier on the assumption that
rats are a scarce asset worth preserving. Three of this session's findings
contradict that:

- The death rate is ~**0.057/round in every variant tested**, so seven rats
  not spent are seven rats that die anyway within ~100 rounds.
- Iteration 60 proved the standing army cannot be grown by building
  (spawns 25 → 50 left live rats at exactly 4). Rats are a flow we cannot
  bank — precisely the asset to convert into something permanent.
- `livingKings` is a **proportional** term at weight **0.5** once
  cooperation ends (~round 39). Both sides hold one King today, so we split
  it 25/25; two Kings against one would be 33/17 — an 8-point swing on the
  largest term on the board. And 91% of our losses are King-death losses,
  with all-Kings-dead an instant loss.

Unlike trap avoidance or throwing, an upgrade is a **one-time conversion**,
not an ongoing tax on rat-turns.

---

## Iteration 74 — opportunistic multi-King — inert, but it exposed the biggest scoring gap yet

Benchmarks 5/162, identical composition to the control, 133/162 games
unchanged. The mechanism check explains why: **0 `UpgradeToRatKing` events,
max kings = 1.** `canBecomeRatKing()` needs **7 allies packed into the 3×3**
around the upgrading rat, and our 4-8 living rats are dispersed collecting
cheese, so the opportunistic gate essentially never opens.

### The finding that matters is on the other side of the scoreboard

The same replay's final line reads `1:kings=5 2:kings=1`. **`bench_finalist`
runs the maximum five Rat Kings against our one**, and gets there steadily:

| their King count | round reached |
|---|---|
| 2 | 125 |
| 3 | 325 |
| 4 | 375 |
| 5 | 825 |

`livingKings` is proportional at weight **0.5** once cooperation ends:

| our Kings vs theirs | our points | theirs |
|---|---|---|
| **1 vs 5 (actual)** | **8** | **42** |
| 2 vs 5 | 14 | 36 |
| 3 vs 5 | 19 | 31 |
| 1 vs 1 | 25 | 25 |

**We concede ~34 points of a 50-point term before the game starts.** That is
larger than the catDamage gap (where we hold ~15% of a 30-point term) and it
is the single biggest scoring deficit measured this session. It also explains
the 9% of losses decided on points, and compounds the King-death losses:
five Kings is five things that must die before the instant-loss condition
triggers, against our one.

### Why we cannot currently do this

The upgrade consumes 7 rats standing in a 3×3. They can afford it repeatedly
because they field 56 rats; at 4-8 we cannot assemble the crowd by accident.
Their first upgrade lands at round **125** — after the opening build burst,
which is exactly when our rats are also briefly clustered near the King.

So the requirement is a deliberate, **one-time** rally, not an ongoing tax.
That distinction is the one the per-capita finding makes: trap avoidance and
throwing failed because they charged rat-turns every round forever; a rally
charges once and returns a permanent 600 HP unit holding a share of the
largest term on the board.

Note `RAT_KING` has `size` 3, so a King occupies a 3×3 footprint — the rally
point must be offset from the existing King rather than on top of it.

---

## Iteration 75 — rally for a King upgrade — 0/162, the worst result of the session

Benchmarks **0/162** against a 5/162 control. Not a single win on any map
against any opponent.

Mechanism check on `bench_finalist__hatefullattice__botB`:

| | control | Iteration 75 |
|---|---|---|
| `UpgradeToRatKing` | 0 | **0** |
| max our kings | 1 | **1** |
| our deaths | 67 | 62 |
| `cheeseTransferred` | 2595 | **1856** (−28%) |
| game ended | rd 1175 | rd 1025 |

**Pure cost, zero benefit.** The rally pulled rats off cheese for 200 rounds,
cost 28% of the economy, and never once produced the upgrade it existed to
enable.

### A population assumption of mine was wrong

I had been reasoning from "we field 4-8 rats", and concluded a 7-rat upgrade
was near-unaffordable. That figure is the **late-game** count. Measured
properly, our population **peaks at 25 living rats at round 50** and then
decays — 7 by round 225, 4 by round 399.

So the bodies do exist; my rally window (60-260) simply opened *after* the
peak and spent its whole span inside the collapse, taxing a shrinking army
for a target that was already receding. That is why it produced nothing but
cost.

This is the third measurement artifact of the session, after Iteration 70's
non-dose and Iteration 72's rate-vs-total. All three shared a shape: a
plausible number carried over from one context into another where it did not
apply. "4-8 rats" was true, just not at the time that mattered.

### Iteration 76

Retime the rally to the population peak: rounds **25-90** instead of 60-260.
Narrower (65 rounds instead of 200, bounding the tax) and aimed at the moment
when 25 rats are alive and still clustered near the King from the build burst.

If the upgrade still records zero, the obstacle is not gathering but the
**3×3 packing** — `becomeRatKing` needs 7 allies on the 8 tiles surrounding
one rat, and terrain plus the King's own size-3 footprint may make that
unachievable at the rally point. In that case the answer is to pick the rally
point for open adjacent tiles rather than a fixed (3,3) offset.

---

## The multi-King line — CLOSED after three attempts, zero upgrades ever

| attempt | approach | benchmarks | upgrades | cheese | game end |
|---|---|---|---|---|---|
| 74 | opportunistic | 5/162 | **0** | 2595 | rd 1175 |
| 75 | rally rounds 60-260 | **0/162** | **0** | 1856 | rd 1025 |
| 76 | rally rounds 25-90 | 1/162 | **0** | **3631** | **rd 1425** |
| — | control | **5/162** | 0 | 2595 | rd 1175 |

**Not one `UpgradeToRatKing` in any variant.** The pre-registered fallback
diagnosis is confirmed: the obstacle is not gathering but **3×3 packing**.
`becomeRatKing` requires 7 allies on the 8 tiles immediately surrounding one
rat; my rally condition stopped rats within distance² 2 of the point, which
spreads them across a ~2-tile radius and never fills the ring.

### Why the line is closed rather than retried

The deficit it targets is real and large — `bench_finalist` runs 5 Kings to
our 1, conceding ~34 points of a 50-point term. But the capability is
**downstream of population, not independent of it**. They assemble 7 packed
allies incidentally because they field 56 rats; we peak at 25 and are at 7 by
round 225. Reaching multi-King requires the standing army we do not have, and
Iteration 60 already established the army cannot be grown by building
(spawns 25 → 50 left live rats at exactly 4).

So the chain is: **deaths → population → multi-King**, and the only lever on
deaths (cat engagement) trades away a proportional term we need. Multi-King
is a symptom of the population problem, not a separate fix for it.

### One more anomaly worth recording

Iteration 76 produced **more cheese than the control (3631 vs 2595) and a
longer game (rd 1425 vs 1175) while winning fewer games (1 vs 5)**. That is
the clearest single demonstration of something this session has hinted at
repeatedly: **our results are not economy-limited.** Three separate changes
have now improved cheese and/or survival without improving wins. Whatever
decides these games, more cheese and more rounds alive are not sufficient
for it — which is a strong argument that future effort belongs on the
scoring terms themselves rather than on the economy that feeds them.

---

## Iteration 77 — move ONLY the King's attack below the build — inert on wins, but it changes the binding constraint

Benchmarks **5/162**, identical composition to the control (finalist 4,
spaark 0, stroke 1), 118/162 games unchanged — so it fired in 44 games and
netted zero.

The mechanism did exactly what it was designed to do. `tools/king_census.py`
on `bench_finalist__knifefight__botA`:

| window 0 | control | Iteration 77 |
|---|---|---|
| King `SpawnAction` | 22 | **25** ← **CAP-LIMITED** |
| King `RatAttack` | 28 | **1** |
| King `PlaceTrap` | 18 | 21 |
| rats alive | 5 | 6 |
| cheese banked | 1685 | 1605 |

The attack reorder works — 28 attacks become 1, and the freed actions go
into building. But spawns rise only from 22 to **25**, because 25 *is*
`MAX_POPULATION`. The King immediately hits the cap and the remaining freed
actions have nowhere to go. +3 rats in a 77-round game buys +1 alive, which
is not enough to move a result.

### The useful part: it creates a state that has never existed

Iteration 60 raised the cap flat, 25 → 50, and scored **1/162** — on the long
maps the extra builds drained window-0 cheese 2306 → 565 and pulled
bankruptcy forward a whole window, while live rats stayed at exactly 4.
Raising the cap when *cheese* is binding merely spends the treasury faster.

Iteration 77 puts the short maps into the opposite condition: **cap-limited
with 1605 cheese still banked.** The cap binds and the money to relieve it is
sitting unused.

So neither constraint is universal — **cheese binds late and on long maps,
the cap binds early and on short ones** — and each previous attempt relieved
the wrong one for the regime it was tested in.

### Iteration 78

Gate the extra capacity on the treasury: `MAX_POPULATION = globalCheese >
1200 ? 40 : 25`. This opens exactly where Iteration 60 was wrong to open it
and stays shut where Iteration 60 went bankrupt. Iteration 77's reorder is
kept, since it is what produces the cap-limited-with-money state in the first
place — this is the first time this session two changes have been combined,
and only because each was measured to relieve a different constraint.

---

## Strategic finding: ~91% of games are decided by King destruction, not points

Found by looking at the games we **win** rather than the ones we lose — an
angle not taken before this session.

All five control wins against the tournament bots ended with *"The winning
team destroyed all of the enemy team's rat kings"*:

| opponent | map | side | rounds |
|---|---|---|---|
| `bench_finalist` | peaceinourtime | B | 545 |
| `bench_finalist` | popthecork | A | 456 |
| `bench_finalist` | popthecork | B | 609 |
| `bench_finalist` | toomuchcheese | B | 183 |
| `bench_stroke` | whatsthecatdoin | A | 560 |

**None reaches round 2000.** The loss census agrees from the other side: only
14 of 157 losses (9%) reach round 2000. So the scoring terms — `catDamage`
0.3-0.5, `livingKings` 0.5, `cheeseTransferred` 0.2 — decide roughly **one
game in eleven**.

### This resolves the central puzzle of the session

| iteration | what it demonstrably achieved | wins |
|---|---|---|
| 69 trap avoidance | deaths 67 → 31 | 4/162 |
| 72 opening throws | cheese/round +5% | 5/162 |
| 76 King rally | cheese 3631 vs 2595, game to rd 1425 | 1/162 |

Every one of these worked on its own terms and none converted. They were all
optimising quantities that almost never decide the outcome. Even the
34-point multi-King deficit only matters in the 9% of games that reach
scoring at all — which retroactively downgrades that finding from "biggest
scoring gap" to "biggest gap in the part of the game that rarely matters".

### What we have never tried

**There is no code anywhere in the bot that targets the enemy King.** Rats
engage enemy rats only when `!rc.isCooperation() || desperate`, purely
reactively. We have never attempted to win the way we actually win.

Timing is tight: `bench_finalist` reaches 2 Kings at round 125, 3 at 325, 4
at 375, 5 at 825, and all our wins land at rounds 183-609 — while they still
have few Kings. Every ~200 rounds of delay adds another King that must also
die. `RAT_KING.health` is 600 against `RAT_BITE_DAMAGE` 10, so ~60 bites kill
one, and our population peaks near 25 rats around round 50.

Note `InternalRobot.bite` calls `backstab(this.team)` for any non-cat target,
so rushing their King makes us the backstabber — which matters far less than
it appears, precisely because scoring rarely decides anything.

---

## Iteration 78 — cheese-gated population cap — REJECTED

Benchmarks **4/162** against 5/162 for both Iteration 77 and the control.

The combination was well-motivated — Iteration 77 had put the short maps into
a cap-limited state with 1605 cheese banked, and gating capacity on the
treasury opens it there while staying shut where Iteration 60 went bankrupt.
It still did not convert, and the reason is now clear from the King-kill
finding: **more rats and more cheese feed scoring terms that decide ~9% of
games.** Iterations 77 and 78 both reverted.

That closes the production line for good. Every lever on it has now been
pulled and measured:

| lever | result |
|---|---|
| free the King's actions (59) | worse both instruments |
| free the King's actions alone (77) | inert, hits the cap at +3 spawns |
| raise the cap flat (60) | 1/162, bankruptcy |
| raise the cap when rich (78) | 4/162 |
| raise the standing army at all (60) | live rats 4 → 4 |

### Iteration 79 — raid the enemy King

The first change aimed at how these games are actually won. Half the rats
(even IDs, so symmetry-safe) become raiders from round 120: they bite an
enemy King in range, close on one they can see, and otherwise path to the
mirror-guess in shared-array slots 3/4.

Two supporting changes, both deliberate:

- The enemy-King guess is now published **always**, not only inside the
  `desperate` branch. It was previously unreachable until our economy had
  already collapsed — useless for a raid. Writing two slots is King-side and
  costs no rat turns.
- Only **half** the rats raid. Sending all of them would repeat exactly the
  mistake that sank trap avoidance and unrestricted throwing, both of which
  halved `cheeseTransferred` by taxing every rat every round.

Accepted cost: `InternalRobot.bite` calls `backstab(this.team)` for any
non-cat target, so raiding names us the backstabber — catDamage drops 0.5 →
0.3 and livingKings rises 0.3 → 0.5. Since scoring decides ~9% of games and
the livingKings side moves in our favour, that is a trade worth taking.

---

## Iteration 79 — raid the enemy King — the raid never arrives

Benchmarks **4/162** against 5/162 control, with 39/162 identical, so 123
games changed behaviour.

| `bench_finalist__hatefullattice__botB` | control | Iteration 79 |
|---|---|---|
| our `RatAttack` after round 120 | 43 | **47** |
| our deaths | 67 | 61 |
| `cheeseTransferred` | 2595 | **1895** (−27%) |
| game ended | rd 1175 | rd 1025 |

Half the army diverted, 27% of the economy spent, and **four extra attacks**.
The raiders walk and never fight.

### Root cause: the mirror guess is wrong on reflection maps

On `knifefight` our King sits at **(22,14)** and theirs at **(17,14)** — a
horizontal reflection. The guess computes
`(width-1-x, height-1-y)` = **(17,25)**. We were marching half the army to an
empty tile three tiles past the target, and on larger maps the error is
proportionally larger.

BC22's `LEARNINGS.md` flagged exactly this: several maps there turned out
non-rotational *even though that engine exposed the symmetry type*. BC26 does
not expose it at all, so the guess cannot be repaired — 180° rotation is one
of three possibilities and we have no way to choose.

A methodological note: I checked `DamageAction target=...RAT_KING` first and
got 0, which looked like proof the raiders never landed a hit. That check is
invalid — this session already established that **bites emit no
`DamageAction` records**. The attack-count comparison above is the valid
instrument, and it happens to agree, but the first reading was the same
attribution error that has bitten this project repeatedly.

### Iteration 80

Steer by observation instead of arithmetic. Any rat that actually *sees* an
enemy King publishes its location to shared-array slots 14/15, and raiders
prefer that over the guess, falling back to the guess only while no King has
ever been seen. This is BC22's accumulate-locally-publish-once census pattern
and costs two writes only on turns when a King is in view.

---

## Iteration 80 — raid by sighting instead of guessing — no better

Benchmarks **4/162**, identical total to Iteration 79 and below the 5/162
control. 26/162 identical to control; **96/162 identical to Iteration 79**,
so correcting the targeting genuinely changed 66 games and changed nothing
about the outcome.

That is informative: the raid does not fail because it aims at the wrong
tile. Fixing the aim left the result exactly where it was. The measured cost
— 27% of `cheeseTransferred` — is the whole story, and it is the same
per-capita tax that sank trap avoidance and unrestricted throwing.

### Iteration 81

Apply the remedy that worked for throwing. Bounding *that* mechanism moved
its economics from −53% to +5% per round; the analogous dial here is the
raider fraction, so `rc.getID() % 2` becomes `% 4` — a quarter of the army
instead of half, sighting retained.

If a quarter still costs more than it returns, the King-rush direction is
correct in principle (it is how 91% of games are decided) but unaffordable at
our population, and the honest conclusion is that our army is too small to
project force *and* run an economy — which is the same wall every line has
hit this session.

---

## Iteration 81 — quarter-strength raid — REJECTED; the raid line closes

| arm | benchmarks |
|---|---|
| control | **5/162** |
| 79 raid toward the mirror guess (half the army) | 4/162 |
| 80 raid toward a sighted King (half) | 4/162 |
| 81 raid, quarter of the army | **3/162** |

Non-monotone and every arm below control, which under the dose-response rule
rejects the approach rather than the tuning. The King-rush direction is
strategically right — it is how 91% of these games are decided — but it is
unaffordable at our population: we cannot project force and run an economy
with the same 4-8 rats.

## The finding that reframes the whole session

Asking *why* conceding catDamage cost Iteration 63 twenty-four peer games,
when scoring supposedly decides only 9% of games, produced this:

| instrument | games reaching round 2000 |
|---|---|
| benchmarks (real MIT tournament entries) | **14/162 = 9%** |
| peers (forks of our own bot) | **64/108 = 59%** |

**The two Gauntlets play structurally different games.** Against tournament
bots, 91% end by King destruction and the scoring terms barely matter.
Against peers — evenly matched because they *are* us — most games run the
full 2000 rounds and are decided on points.

So the peer Gauntlet is systematically biased toward score-optimising
changes and the benchmark set toward survival and King combat. Both of
Iteration 63's numbers were real and they were measuring different games:

> **Iteration 63 (`allies >= 3`) scored 7/162 — the best benchmark result of
> the session and the first run to beat all three tournament bots, including
> `bench_spaark` which had never been beaten — while dropping peers to
> 41/108.**

I rejected it on the peer regression. Under this analysis that was the wrong
call for competitive strength: the benchmark bots are actual tournament
entries, the peers are our own forks, and the change concedes a term that
decides 59% of peer games and 9% of benchmark games.

**Iteration 63 is restored and being evaluated on `vs_old_bots`** — the
frozen `g_iterN` snapshots, which cannot drift and are the designated
progress metric precisely for breaking ties like this one.

---

## Iteration 63 — ACCEPTED. `allies >= 3` before closing on a cat.

The first accepted iteration of this session, and it required arbitrating a
genuine disagreement between instruments rather than picking the one that
agreed with me.

| instrument | control | Iteration 63 | what it measures |
|---|---|---|---|
| benchmarks (real tournament entries) | 5/162 | **7/162** | 91% King-kill games |
| `vs_old_bots` (frozen snapshots) | 95/108 = 88.0% | **97/108 = 89.8%** | designated progress metric |
| peers (forks of our own bot) | 65/108 = 60.2% | **41/108 = 38.0%** | 59% points games |

Two instruments improve by +2 games each. The third degrades sharply — and
the reason is structural, not noise:

> Only **9% of benchmark games reach round 2000**, against **59% of peer
> games**. The peers are forks of this same file, so they are evenly matched
> with us; their games run long and are decided on **points**, where
> conceding the catDamage share is expensive. The tournament bots kill us
> early, so 91% of those games are decided by **King destruction**, where the
> same concession is nearly free.

Both numbers are true of different games. Since the goal is competitive
strength against real entries — and `vs_old_bots`, the metric that exists to
break exactly this tie, moves the same direction as the benchmarks — the
change is accepted with the peer regression recorded as a known, explained
cost rather than buried.

The mechanism, re-stated with the constants that survived checking: a cat has
4000 HP against `RAT_BITE_DAMAGE` 10 and scratches every third turn
(`actionCooldown` 30 vs `COOLDOWNS_PER_TURN` 10) for `CAT_SCRATCH_DAMAGE` 20
against our 100. A lone rat survives ~15 turns and deals ~150 damage — it
trades its entire life for under 4% of one cat. Reach is symmetric at
`ATTACK_DISTANCE_SQUARED` 2; the earlier claim that cats out-range rats was
wrong (17 is *vision*).

Charts regenerated: `progress/vs_old_bots.png` and
`progress/peer_win_spread.png`.

### Accept criteria updated

Benchmarks and `vs_old_bots` now outrank the peer Gauntlet when they
disagree, with the 9%-vs-59% measurement as the justification. A peer
regression is no longer automatically disqualifying — but it must be
explained by the regime difference, not waved away.

---

## Iteration 63 — ACCEPT RETRACTED. The primary test says 33.3%.

Head-to-head against `g_iter15`, the snapshot it would replace:

    ITER63 vs g_iter15:  18/54 = 33.3%    (wins by side: A 12, B 6)

`TRAINING_ALGORITHM.md` is explicit that `< 50%` here "is a regression
against the thing it would replace". **I accepted this change without running
the test the algorithm designates as primary.** Reverted.

### Why three instruments pointed the wrong way

| instrument | control | Iteration 63 | matchup type |
|---|---|---|---|
| benchmarks | 5/162 | 7/162 | lopsided — we lose ~97% |
| `vs_old_bots` (g_iter1, g_iter11) | 95/108 | 97/108 | lopsided — we win ~88% |
| peers | 65/108 | **41/108** | **even** |
| **g_iter15 head-to-head** | (50% by definition) | **18/54 = 33.3%** | **even** |

The two instruments that endorsed the change are both **lopsided matchups**
operating near an extreme — 3% and 88% win rates — where ±2 games is the
resolution floor. The two even matchups, peers and the `g_iter15`
head-to-head, both say clearly negative, and they agree with each other
(38.0% and 33.3%).

This is precisely why the algorithm designates the snapshot head-to-head as
primary: **an even matchup has resolution that a lopsided one does not.**

### The reasoning error, stated plainly

My 9%-vs-59% analysis was correct as a fact — benchmark and peer games really
do reach scoring at very different rates. But I used it to *explain away* the
peer regression instead of treating it as evidence. The peer number was a
genuine warning that the change is worse against a bot of our own strength,
and the head-to-head confirms it against a frozen snapshot that cannot have
drifted.

A true observation was used to license discarding a valid measurement. That
is a more dangerous mistake than a bad hypothesis, because the supporting
fact was real and checked — it made the wrong conclusion *more* persuasive,
not less. The same shape appeared earlier this session with the cat-trap
story and the King action census.

### Accept criteria — corrected again

The previous entry updated the criteria to rank benchmarks and `vs_old_bots`
above peers. That was wrong and is withdrawn. The correct ordering:

1. **`g_iter<latest>` head-to-head is primary** and must be run before any
   accept. Even matchup, frozen opponent, immune to staleness and mirror
   collapse.
2. **Peers** are the regression check — an even matchup, so they carry real
   information even when the regime differs.
3. **Benchmarks and `vs_old_bots`** are lopsided; treat ±2 games as noise
   and use them for direction, never as the deciding evidence.

---

## Iteration 82 — ablate the King trap ring — 57.4% on the mirror test

    bot WITHOUT King traps  vs  g_iter15 WITH them:  31/54 = 57.4%
    (wins by side: A 20/27, B 11/27 -- the known A-side mirror bias,
     which cancels because both sides are played)

**Removing Iteration 48's trap ring beats keeping it**, on the instrument
the algorithm designates as primary.

### The instrument was validated first

`src/bot` and `src/g_iter15` differ **only in the package line** — the whole
diff is 4 lines. So this Gauntlet is a true mirror, pinned at 50% by
construction, and it therefore has resolution that the benchmark set (~3% win
rate) and `vs_old_bots` (~88%) structurally cannot. That is also what makes
Iteration 63's 33.3% a real 17-point regression rather than noise.

### What this overturns

Iteration 48 has been credited all session as *the only change that ever moved
a benchmark line* (`bench_finalist` 0% → 7-10%), and that credit was
load-bearing:

- Iteration 59's benchmark drop (5/162 → 3/162) was **blamed on deleting the
  traps**. That attribution is now suspect — deleting them appears to help.
- Iteration 77 **deliberately kept the traps** while reordering the King's
  attack, on the strength of the same credit.
- The ring costs the King **18-21 placements per short game, ~26% of its
  action budget** (measured by `tools/king_census.py` during Iteration 77).

The original 0% → 7-10% measurement was taken on the lopsided benchmark set,
where ±2 games is the resolution floor. It was never tested on an instrument
that could resolve it.

This is the second time this session that a conclusion drawn from a
low-resolution instrument has been reversed by a high-resolution one — the
first being the Iteration 63 accept itself, retracted three commits ago. The
pattern is consistent enough to state as a rule: **claims measured near 0% or
near 100% should be treated as unverified until checked on the mirror.**

Peer regression check running.

### Iteration 82 — ACCEPTED, and a data-integrity fix

| instrument | control | Iteration 82 | resolution |
|---|---|---|---|
| **`g_iter15` mirror (primary)** | 50% by construction | **31/54 = 57.4%** | high — even |
| peers (regression check) | 65/108 = 60.2% | **66/108 = 61.1%** | high — even |
| benchmarks | 5/162 | **3/162** | low — ~3%, ±2 is the floor |

Both even instruments favour removing the trap ring; the lopsided one
disfavours by exactly the ±2 that is its resolution floor. This is the mirror
image of the Iteration 63 situation — there the *even* instruments dissented
and I wrongly sided with the lopsided ones — so applying the corrected
criteria consistently means accepting here. The benchmark cost is recorded,
not hidden: against actual tournament entries this is 3/162 rather than
5/162, and that number is simply too coarse to weigh against a 4-game edge on
a 50%-baseline mirror.

Snapshotted as `src/g_iter16`, verified identical to `src/bot` apart from the
package line.

**Data-integrity fix in `progress/vs_old_bots_history.csv`.** While recording
the accept I appended the wrong run twice. Two faults:

1. The rows labelled `g_iter15` were produced by the **Iteration 63** build
   (`allies >= 3`), not by the baseline — `track_vs_old_bots.py` labels a run
   with whatever the highest `src/g_iterN` happens to be at the time, which
   says nothing about the code that actually played.
2. Creating `src/g_iter16` then made the tool relabel a *second* append as
   `g_iter16`, duplicating the same Iteration 63 numbers under a new name.

Four rows removed; the `g_iter15` entry rewritten from the genuine control run
(46/54 = 85.2%, 49/54 = 90.7%). The real `g_iter16` row is being generated by
a fresh vs-old-bots run of the accepted build.

The general hazard is worth stating: **the snapshot label in that CSV is
inferred from the directory listing, not from the binary that played.** Any
append is only correct if the working tree at run time matched the label —
which is exactly the assumption that failed here, twice in three commands.

---

## Iteration 83 — the "never-reverted rejection" turns out to have been right to keep

`BITE_BOOST_CHEESE = 4` was found still live in the bot, despite this log
saying at line ~3230: *"REJECT the cheese-boosted bite entirely (Iteration
45), including the 4-cheese version."* That looked like a plain defect — a
measured-harmful change left in, with every comparison since Iteration 45
made against a contaminated baseline.

Removing it and testing on the mirror:

    bot WITHOUT the boost  vs  g_iter16 WITH it:  25/54 = 46.3%

**Removing it is worse.** Equivalently the 4-cheese build wins **53.7%** —
the identical figure Iteration 45 measured, now reproduced against a
different opponent on a different instrument. Restored, in exactly the
guarded form `g_iter16` carries (code-only diff against the snapshot is
empty; only comments differ).

### The original rejection was an inference, not a measurement

Iteration 45 measured two points — 4 cheese at 53.7%, 16 cheese at 37.0% —
and concluded from the negative slope that the 4-cheese version was also
harmful. **That step assumes the response is monotone.** With the third
point now measured, 0 cheese at 46.3%, the curve is concave with an interior
optimum near 4:

    0 cheese   46.3%
    4 cheese   53.7%   <- optimum
    16 cheese  37.0%

A dose-response that slopes down at the high end says the *high dose* is
bad. It says nothing about whether the low dose beats zero — that requires
measuring zero, which was never done.

This corrects the guidance the log itself drew from Iteration 45 ("inverted
-> reject the whole approach regardless of how good the theory is"). The
sharper rule: **an inverted high-dose arm rejects that dose, not the
mechanism. Always include the zero arm.**

Also worth recording: the 53.7% that Iteration 45 dismissed as "noise sitting
on a slightly-negative effect" has now been observed twice, against different
opponents. It was signal.

### Two near-misses in this iteration

1. I first restored the boost as a bare `rc.attack(loc, 4)`, dropping the
   `getGlobalCheese() > 1000` guard and the `canAttack(loc, 4)` check. That
   is a different bot from the one that measured 53.7%, and it could throw a
   `GameActionException` when the boost is unaffordable. Caught by diffing
   against `g_iter16` rather than trusting the edit.
2. The stale Iteration 45 rationale (arguing *for* a feature I had just
   removed) was left contradicting the code for several minutes. Comments
   that outlive their code are how a file starts lying.

---

## Iteration 84 — ablate the emergency build override — INERT

    bot WITHOUT the override  vs  g_iter16 WITH it:  26/54 = 48.1%
    (wins by side: A 13, B 13 -- a balanced split, so a genuine null
     rather than a side artifact)

**Iteration 40's emergency override is worth nothing measurable** — one game
in 54, indistinguishable from zero. Kept, since removing it is equally
neutral and churn carries its own risk, but it should not be credited in any
future reasoning.

That feature was accepted at a headline **95.0%**, corrected in this log to
62.5% after resyncing stale archetypes, with the explicit note that the
number "should be treated as provisional until re-measured". It never was.
The re-measurement says the true value is ~0.

### The ablation program so far

| iteration | feature | mirror result | outcome |
|---|---|---|---|
| 82 | Iteration 48 King trap ring | **57.4%** without it | **removed, accepted** |
| 83 | Iteration 45 cheese-boosted bite | 46.3% without it | kept; corrected a dose-response rule |
| 84 | Iteration 40 emergency override | 48.1% without it | kept; credited value revised to ~0 |

Three runs, three corrections to the record, one accepted change. That is a
better yield than the new-mechanism iterations managed across the whole
session, and the reason is structural: every one of these features was
accepted on the peer set or the benchmark set, and neither can resolve the
differences involved.

**The pattern across all three: headline numbers from low-resolution
instruments (95.0%, 90.0%, 75.0%, "0% → 7-10%") do not survive contact with
the mirror.**

### Iteration 85

Ablating Iteration 32's exploration-heading reassignment — accepted at 75.0%,
motivated by a traced failure (a rat cornered at spawn oscillating for ~1985
rounds without collecting cheese), and the change the user personally asked
for. That makes it the most sympathetic candidate in the program, which is
precisely why it is worth measuring: a real traced bug plus a 75% peer number
is the same evidence profile Iteration 40 had at 95.0%.

Only the *reassignment* is ablated; the per-robot initial heading from
Iteration 4 stays, so rats still fan out — they simply never re-pick a
heading after stalling.

---

## Iteration 85 — ablate the exploration-heading reassignment — 22.2%, the largest effect measured all session

    bot WITHOUT the reassignment  vs  g_iter16 WITH it:  12/54 = 22.2%
    (wins by side: A 7, B 5)

**Iteration 32 is validated, emphatically.** A ~28-point swing on the mirror
— larger in magnitude than anything else measured on that instrument, in
either direction, and the opposite sign from what the ablation program has
mostly been finding.

Without it, a rat whose per-robot initial heading happens to point at a
nearby map edge reaches that edge in ~15 rounds and then oscillates in a
handful of tiles for the remaining ~1985 rounds of a 2000-round game, never
collecting cheese. That is the failure the user reported directly ("baby rats
that tend to get stuck in one small region rather than moving freely to and
from the cheese"), and it turns out to be the single most valuable behaviour
in the bot.

### The ablation program, complete

| iteration | feature | headline claim when accepted | mirror (without it) | true value |
|---|---|---|---|---|
| 82 | King trap ring (Iter 48) | "0% → 7-10% vs `bench_finalist`" | **57.4%** | **harmful — removed** |
| 83 | cheese-boosted bite (Iter 45) | rejected as "noise" | 46.3% | mildly helpful — kept |
| 84 | emergency override (Iter 40) | **95.0%** | 48.1% | **~0 — inert** |
| 85 | heading reassignment (Iter 32) | 75.0% | **22.2%** | **large and real** |

Four features, four different answers: one harmful, one mildly helpful, one
inert, one large. **The headline number at acceptance predicted none of
them.** The feature sold at 95.0% is worth nothing; the one sold at 75.0% is
worth ~28 points; the one credited with the session's only benchmark
improvement was actively costing us; and one that had been formally rejected
was mildly good.

That is not four measurement accidents — it is what happens when every
decision is made on instruments that cannot resolve the differences being
claimed. The mirror can, because it is pinned at 50% by construction and
plays both sides.

### Where this leaves the bot

`src/bot` is code-identical to `g_iter16` (comment-only diff), which is the
Iteration 82 build: baseline minus the King trap ring. That is the session's
one accepted change, and it is now the only one of these four features whose
credit rests on a high-resolution measurement.

---

## Iteration 86 — ablate Bug2 navigation — validated, mildly

    bot WITHOUT Bug2  vs  g_iter16 WITH it:  24/54 = 44.4%

Bug2 is worth roughly **+5.6 points** — real, and the first measurement it
has ever had. Iteration 35 accepted it on a purely mechanistic argument
("greedy movement has no notion of a path, only a heading") plus one traced
maze corridor, with no win-rate evidence recorded at all.

Worth noting the scale: Bug2 is by far the largest and most intricate
subsystem in the bot — wall-following state, closest-distance memory,
per-robot rotation preference, leave conditions — and it is worth about a
fifth of what the eight-line exploration-heading reassignment is worth (~28
points). Complexity and value are unrelated here.

## The ablation program, five features

| iteration | feature | headline claim at acceptance | mirror without it | true value |
|---|---|---|---|---|
| 82 | King trap ring (Iter 48) | "0% → 7-10%" | **57.4%** | **harmful — removed** |
| 83 | cheese-boosted bite (Iter 45) | formally **rejected** | 46.3% | mildly helpful (~+4) |
| 84 | emergency override (Iter 40) | **95.0%** | 48.1% | **~0, inert** |
| 85 | heading reassignment (Iter 32) | 75.0% | **22.2%** | **~+28, large** |
| 86 | Bug2 navigation (Iter 35) | *mechanistic, unmeasured* | 44.4% | ~+5.6 |

Five features, five distinct answers, and **the acceptance headline predicted
none of them.** The 95.0% feature is inert; the 75.0% feature is the most
valuable behaviour in the bot; the one credited with the only benchmark
improvement was harmful; a formally rejected one was mildly good; and the
biggest subsystem is worth a fifth of the smallest.

### Iteration 87

The last major unmeasured feature: Iteration 39's `REPLACEMENT_RESERVE`,
accepted at **90.0%** — the largest headline gain in the project's history
(+15 points, "6 losses fixed, 0 new"). Ablating the reserve gate while
keeping Iteration 38's sliding window, so refills draw on the ordinary
`RESERVE` rather than requiring a 1000-cheese surplus. That isolates the
reserve policy from the window it shipped alongside.

---

## Iteration 87 — ablate REPLACEMENT_RESERVE — validated, ~+24 points

    bot WITHOUT the reserve gate  vs  g_iter16 WITH it:  14/54 = 25.9%

Iteration 39 holds up, and it is the second-largest effect in the bot. It was
accepted at 90.0% on the peer set — the largest headline gain in the
project's history — and it is the only headline claim tested in this program
that survived contact with the mirror.

## The ablation program — complete map of where the value is

| iteration | feature | headline at acceptance | mirror without it | true value |
|---|---|---|---|---|
| 85 | exploration-heading reassignment (32) | 75.0% | **22.2%** | **~+28** |
| 87 | `REPLACEMENT_RESERVE` (39) | 90.0% | **25.9%** | **~+24** |
| 86 | Bug2 navigation (35) | *unmeasured* | 44.4% | ~+5.6 |
| 83 | cheese-boosted bite (45) | formally **rejected** | 46.3% | ~+4 |
| 84 | emergency build override (40) | **95.0%** | 48.1% | **~0** |
| 82 | King trap ring (48) | "0% → 7-10%" | **57.4%** | **negative — removed** |

Six features measured on a common instrument for the first time. **Two of
them carry essentially all the value, and both are failure-mode preventers
rather than tactics:**

- *don't let a rat commit forever to a heading that walks it into a wall*
- *don't spend the King's survival reserve on routine refills*

Everything clever — Bug2's wall-following, cheese-boosted bites, the
emergency override, the trap ring — is small, zero, or harmful. The two
eight-to-twenty-line guards against catastrophic behaviour outweigh the
entire rest of the bot combined.

That reframes what to work on. This session spent ~25 iterations inventing
tactics (traps, throwing, kidnapping, King rushes, multi-King, cat policy)
and every one failed. The two things that actually work are both of the form
"detect a degenerate state and stop doing it". **The next hypotheses should
be searches for remaining degenerate states, not new tactics.**

Also settled: the acceptance headline was uninformative in five of six cases,
including inverting the sign twice (95.0% → inert, "only benchmark
improvement" → harmful). Every one of those decisions was made on the peer or
benchmark set. This is now recorded as standing practice in memory.

---

## Iteration 88 — ACCEPTED. Cheese-gated population cap, retested on the mirror.

| instrument | control | Iteration 88 |
|---|---|---|
| **`g_iter16` mirror (primary)** | 50% by construction | **31/54 = 57.4%** |
| peers (regression check) | 65/108 = 60.2% | **73/108 = 67.6%** |

Both even instruments pass, and peers *improve* by 7.4 points rather than
merely holding. Snapshotted as `src/g_iter17`.

Mechanism, on `g_iter16__hatefullattice__botB` — a game both builds lost, so
the comparison is like-for-like:

| | King spawns | alive @200 | @400 | @600 | @800 |
|---|---|---|---|---|---|
| control (cap 25) | 126 | 12 | 10 | 19 | 12 |
| Iteration 88 (gated 40) | **201** | **25** | 15 | **27** | 14 |

+60% spawns and double the standing army at round 200. The traced map that
motivated the change, `closeup|A`, flipped from loss to win.

### This is Iteration 78, which I rejected four hours ago

Iteration 78 was the identical mechanism. It scored **4/162 against a 5/162
benchmark control** and was rejected as "the production line closes
entirely — no remaining variant of 'build more rats' is worth testing."

That verdict was wrong, and wrong for a reason the ablation program had
already diagnosed: the benchmark set sits at a ~3% win rate where ±2 games is
the resolution floor. **A −1 game reading on an instrument that cannot
resolve one game was treated as a refutation of a whole line of work.** On
the mirror the same change is +4 games, and on peers +8.

Two differences from the first attempt, both deliberate:

1. **The cap change is tested alone.** Iteration 78 bundled it with
   Iteration 77's King-attack reorder, so even a real effect would have been
   confounded.
2. **The degenerate state was measured first, not assumed.** Tracing
   `g_iter16__closeup__botA` showed per-100-round action counts collapsing to
   6-13 across rounds 200-599 while cheese sat at 1271-1581 and living rats
   decayed 25 → 6. We hit the build cap around round 50 and then could not
   build for ~350 rounds while holding the money.

A hypothesis of mine was refuted en route and is worth recording: I first
assumed `REPLACEMENT_RESERVE` caused the lull. The cheese trace disproved it
— the lull occurs at *high* cheese, and activity peaks once cheese drops
*below* the reserve. Checking before building saved an iteration.

### What made this findable

The ablation program's map said the two features carrying nearly all the
bot's value are failure-mode preventers, not tactics. A cap-blocked stall
with a full treasury is exactly that class of defect. Roughly 25 tactical
iterations this session failed; the two accepted changes are "stop laying
traps that cost more than they return" and "stop refusing to build while
rich".

### Iteration 88 benchmark number, for the record

    benchmarks: 5/162  (finalist 2, spaark 0, stroke 3)

Equal to the control's 5/162, and up from Iteration 78's 3/162 — the same
mechanism, isolated rather than bundled. The win composition differs
(control was finalist 4 / stroke 1; this is finalist 2 / stroke 3), which is
the usual behaviour of an instrument at ~3%: the total is stable while which
games it wins is not.

Full accept record for Iteration 88:

| instrument | control | Iteration 88 | verdict |
|---|---|---|---|
| `g_iter16` mirror (primary, even) | 50% | **57.4%** | pass |
| peers (regression check, even) | 60.2% | **67.6%** | pass, +7.4 |
| benchmarks (lopsided, ~3%) | 5/162 | 5/162 | flat, no veto |

### Iteration 88 on vs_old_bots

    g_iter17: 49/54 = 90.7% vs g_iter1, 46/54 = 85.2% vs g_iter11
    overall 95/108 = 88.0%   (g_iter15 baseline: 95/108 = 88.0%)

Flat — identical total, different split. That is the expected behaviour of a
lopsided instrument: we win ~88% of these games either way, so a change worth
+7.4 points on an even matchup is invisible here. Recorded and charted per the
standing rule; it is not evidence against the accept, which rested on the two
even instruments.

## Iteration 89 — dose the accepted cap gate, 40 → 60

The gate is established as real (mirror +7.4, peers +7.4), but the dose curve
has only two points: flat-25 at 50% by construction, gated-40 at 57.4%. Forty
was simply the first value tried.

Testing 60 against `g_iter17`. Reading:
- **>57.4%** — capacity still paying, try 80
- **~57.4%** — plateau; 40 is at or past the useful point
- **<57.4%** — interior optimum below 60

Per the dose-response rule as corrected earlier today, an inverted arm at 60
would reject *that dose*, not the mechanism — and the zero arm is already
measured, since flat-25 is the control.

Watching one specific failure mode: Iteration 60 raised the cap **flat** to 50
and went bankrupt (window-0 cheese 2306 → 565, live rats stuck at 4). The
gate is what makes a higher cap safe, since it only opens above 1200 cheese.
If 60 regresses, the question is whether the *threshold* needs to rise with
the cap — not whether capacity is exhausted.

### Iteration 89 result — the cap gate saturates at 40

    cap 60 vs g_iter17 (cap 40):  28/54 = 51.9%

A plateau. The dose curve:

| step | effect |
|---|---|
| flat 25 → gated 40 | **+7.4 points** |
| gated 40 → gated 60 | +1.9 points (one game, noise on this instrument) |

Keeping 40. There is no measurable gain from 60, and the lower value stays
further from Iteration 60's failure mode, where a flat cap of 50 drained
window-0 cheese 2306 → 565 and left live rats stuck at 4. Reverted; code
verified identical to `g_iter17`.

This is the well-behaved version of a dose-response: the mechanism is real,
the first value tried was already near the knee, and the curve flattens rather
than inverting. Contrast Iteration 45's, where the high-dose arm inverted and
I wrongly read that as condemning the low dose — the correction being that an
inverted arm rejects *that dose* and the zero arm must be measured separately.

### Session position

Two accepted iterations, both found by the same method and both of the same
shape — remove or relieve a degenerate state rather than add a tactic:

| accepted | change | mirror | peers |
|---|---|---|---|
| 82 | remove the King trap ring | 57.4% | 61.1% (from 60.2%) |
| 88 | cheese-gated population cap | 57.4% | **67.6%** (from 60.2%) |

Against roughly 25 tactical iterations that all failed. The bot is
`g_iter17`; `src/bot` is code-identical to it.

## Iteration 90 — close the 1000-1200 cheese dead band — marginal, dosing

    gate 1000 vs g_iter17 (gate 1200):  29/54 = 53.7%

Positive but only +2 games, against +4 for both changes accepted today. That
is the marginal case, so the response is to scale the mechanism rather than
re-run it.

The degenerate state itself is real and worth recording. Tracing the current
build (`g_iter17__closeup__botB`), cheese never escapes a narrow band for
1900 rounds:

    round   100  200  300  400  500  600  700  800  900 1000 1100 1200 1300
    cheese 1118 1118 1045 1034 1004 1016  988  998  998  988 1012  992 1004
    alive    29   26   11    4    9    9    6    8   10   14    8    9    7

Two thresholds set independently produce it: the cap gate opens above
**1200**, `REPLACEMENT_RESERVE` blocks building below **1000**. Between them
sits a 200-cheese dead band where we are rich enough to keep building at the
old cap of 25 but never rich enough to unlock 40. So the gate accepted in
Iteration 88 is effectively shut after the opening — meaning its +7.4 points
came from the opening alone.

This is the second degenerate state caused by two independently-reasonable
constants interacting, and the third found by tracing rather than theorising.

Iteration 91 doses the gate to 600 to see whether the effect scales.

### Process fix — the cumulative-iterations chart was being skipped

User instruction: *"please remember to regenerate cumulative-iterations graph
after every accept."* Correct, and I had not been. After each accept today I
regenerated `vs_old_bots.png` and `peer_win_spread.png` but not
`cumulative_iterations.png`, which sat stale at 02:27 while Iterations 82 and
88 were accepted. Now regenerated: 17 accepted iterations, `g_iter1..g_iter17`.

The post-accept routine is four commands, not three, and the ordering matters
— `plot_progress.py` reads the `src/g_iterN/` directories, so it is only
correct once the new snapshot exists:

    tools/.venv/bin/python3 tools/track_vs_old_bots.py gauntlet/<run-id>/
    tools/.venv/bin/python3 tools/plot_vs_old_bots.py
    tools/.venv/bin/python3 tools/plot_progress.py        # <-- was being skipped
    tools/.venv/bin/python3 tools/plot_alt_metrics.py

Recorded in the standing memory note so it survives the session.

## Iteration 91 — dose the gate to 600 — inverted; optimum is interior

    gate  600 vs g_iter17:  22/54 = 40.7%
    gate 1000 vs g_iter17:  29/54 = 53.7%
    gate 1200 (g_iter17)  :  50% by construction

A clean interior optimum at 1000. Dropping the gate to 600 is actively
harmful — building down that far strips the survival reserve, which is the
same mechanism that made Iteration 60's flat cap bankrupt us.

The non-monotone shape matters for interpreting Iteration 90. On its own,
+2 games at gate 1000 is marginal enough to dismiss; as the peak of a curve
that falls away on *both* sides (50% at 1200, 53.7% at 1000, 40.7% at 600) it
is structure rather than noise. This is the reading that the corrected
dose-response rule makes available — measure the arms around a candidate
rather than treating one number as the verdict.

Peer regression check on gate 1000 running.

## Iteration 90 — ACCEPTED. Cap gate aligned with the build reserve (1200 → 1000).

| instrument | baseline (`g_iter17`) | Iteration 90 |
|---|---|---|
| `g_iter17` mirror (primary, even) | 50% by construction | **29/54 = 53.7%** |
| peers (regression check, even) | 73/108 = 67.6% | **76/108 = 70.4%** |

Both even instruments positive. Snapshotted as `src/g_iter18`.

The accept rests on the *curve*, not the single number. +2 games alone would
be marginal; the surrounding arms make it structure:

| gate | mirror |
|---|---|
| 1200 (baseline) | 50% by construction |
| **1000** | **53.7%** ← peak |
| 600 | 40.7% |

Falling away on both sides is an interior optimum, and 600 fails for an
understood reason — building down that far strips the survival reserve, the
same mechanism that made Iteration 60's flat cap bankrupt us.

### Mechanism check: partial, and it says where the next constraint is

The dead band did widen (cheese spread 134 → 246 across the game) but cheese
still hovers ~900-1150 rather than ranging freely. That is informative
rather than disappointing: **the hover point is set by `REPLACEMENT_RESERVE`
itself.** We build until we cannot afford to, so any reserve creates an
equilibrium just above it, and moving the *gate* only shifts that equilibrium
slightly.

So the remaining lever on the mid-game economy is the reserve, not the gate —
and Iteration 87 measured the reserve at ~+24 points, so it cannot simply be
lowered. What is untested is whether it should *decay* late in the game, when
hoarding a survival buffer has less value than the rats it could buy. That is
a different shape of change from Iteration 40's emergency override, which
tried to detect an emergency and measured inert.

### Session tally — three accepts, all the same shape

| accepted | change | mirror | peers |
|---|---|---|---|
| 82 | remove the King trap ring | 57.4% | 61.1% |
| 88 | cheese-gated population cap | 57.4% | 67.6% |
| 90 | align the gate with the reserve | 53.7% | **70.4%** |

Peers have moved 60.2% → 70.4% across the three. Every one is *relieve a
degenerate state*; none adds a tactic.

### Iteration 90 on vs_old_bots — down 3 games, and worth stating plainly

    g_iter18: 46/54 = 85.2% vs g_iter1, 46/54 = 85.2% vs g_iter11
    overall 92/108 = 85.2%   (g_iter17 was 95/108 = 88.0%)

Three games worse. The accept stands — the two even instruments both
improved, and `vs_old_bots` is a lopsided matchup at ~85-88% where three
games is inside the resolution floor established earlier today. But it is
the one instrument that moved against this change, and burying that would be
exactly the error I made accepting Iteration 63 on a story rather than the
numbers.

For the record, all four instruments on Iteration 90:

| instrument | matchup | baseline | Iteration 90 | weight |
|---|---|---|---|---|
| `g_iter17` mirror | **even** | 50% | **53.7%** | primary |
| peers | **even** | 67.6% | **70.4%** | regression check |
| `vs_old_bots` | lopsided (~88%) | 95/108 | 92/108 | direction only |
| benchmarks | lopsided (~3%) | — | not run | direction only |

If the two even instruments had disagreed with each other, this would be a
near-miss, not an accept.

All four charts regenerated per the corrected routine: `vs_old_bots.png`
(17 rows), `cumulative_iterations.png` (18 accepted iterations,
`g_iter1..g_iter18`), `peer_win_spread.png` (57 runs).

## Iteration 92 — decay `REPLACEMENT_RESERVE` after round 1200 — positive, mechanism ambiguous, dosing

    reserve 400 after rd1200 vs g_iter18:  30/54 = 55.6%
    peers:                                 77/108 = 71.3%  (baseline 76/108 = 70.4%)

Primary clearly above 50%, peers flat-positive. By the letter of the accept
criteria that is enough. I am dosing before accepting anyway, because the
mechanism check did not confirm what it was supposed to.

### The mechanism check disagreed with its own prediction

Predicted: cheese after round 1200 should fall toward the new 400 floor.
Measured on `keepout|A` (2000 rounds):

    cheese BEFORE rd1200:  1121, 1909, 1120, 3065, 2067
    cheese AFTER  rd1200:  4144, 3151, 5435, 2907, 5595

Cheese *rises*. On that map we are **cap-bound, not reserve-bound** — sitting
on 3000-5500 cheese with the population cap binding — so lowering the reserve
cannot do anything there. Yet the run is +3 games overall, which means the
gain comes from a different subset of maps: the ones where we are genuinely
poor late.

That is a real ambiguity, not a formality. A change can be +3 games for a
reason unrelated to its stated mechanism, and this session has already
produced four such cases (the trap ring, the emergency override, Iteration
63, Iteration 78). Accepting on the win rate while the mechanism check points
elsewhere is precisely the pattern that produced the retracted Iteration 63
accept.

So: dose it. `400 → 150` after round 1200. If the effect scales, the reserve
really is the binding constraint on those maps and the mechanism is confirmed
by response rather than by inspection. If it inverts, 400 is near an interior
optimum — also informative. If it is flat, the +3 was not about the reserve at
all and the change should be re-examined before it is trusted.

---

## RULES.md audit, and a bug it exposed in Iteration 80

Audited RULES.md against the vendored engine source. **The document is
substantially accurate** — the per-type stats table, the HP-summing cap on
King formation (`Math.min(health, 600)`), own-team trap immunity
(`trap.getTeam() == this.robot.getTeam()` → skip), the King count limits, the
cheese-carry cooldown penalty and the cooldown mechanics all check out
against the source. The gaps were three things I got wrong *in practice*
today, none of which the document had claimed incorrectly — it had simply
not said them.

Added:

1. **Backstab attribution, which is not symmetric.** Bite and kidnap mark the
   *actor*; a sprung rat trap marks the **trap's owner**
   (`backstab(robot.getTeam().opponent())`). Walking into an enemy trap marks
   *them*. Plus the consequence: the marked team is permanently barred from
   cat traps while the victim keeps them 100 rounds.
2. **Score terms are shares, and cat damage accrues per point dealt** — a cat
   never has to die. This is the error that made `catDamage` look unreachable
   for most of the project ("4000 HP, 10 per bite, therefore impossible"),
   when `bench_finalist` earns ~9,700 per game from ~1,000 ordinary bites and
   zero cat traps.
3. **Only Rat Kings may write the shared array.**

### The third item is a live bug I shipped

`writeSharedArray` throws `CANT_DO_THAT` for a Baby Rat. **Iteration 80
published enemy-King sightings from `runBabyRat`** — so every rat that saw an
enemy King threw, and because the call sat inside the turn logic the
exception propagated to the handler in `run()` and **aborted the rest of that
rat's turn.**

That invalidates Iteration 80's stated conclusion. I recorded it as "steering
by sighting instead of the wrong mirror-guess changed 66 games and changed
nothing, therefore the raid does not fail on aim." In fact the sighting was
never published, the raiders still used the guess, and the 66 changed games
were rats aborting their turns near enemy Kings. The aim was never fixed, so
that experiment did not test what it claimed.

The raid line's *verdict* still stands on other grounds — Iteration 81 cut
the raider fraction and scored 3/162, and the economic cost was measured
directly at −27% `cheeseTransferred` — but the specific inference from
Iteration 80 is withdrawn.

The code is not in the tree (the raid line was reverted), so there is nothing
to fix in `src/bot`; the correction is to the record and to RULES.md.

## Iteration 92 — ACCEPTED. Late-game reserve decay (1000 → 400 after round 1200).

| instrument | baseline (`g_iter18`) | Iteration 92 |
|---|---|---|
| `g_iter18` mirror (primary, even) | 50% by construction | **30/54 = 55.6%** |
| peers (regression check, even) | 76/108 = 70.4% | **77/108 = 71.3%** |
| `vs_old_bots` (lopsided ~85%) | 92/108 = 85.2% | 92/108 = 85.2% (flat) |

Snapshotted as `src/g_iter19`. Fourth accept of the session.

The accept rests on the dose curve, because the replay check was ambiguous:

| late reserve | mirror |
|---|---|
| 1000 (flat, baseline) | 50% by construction |
| **400** | **55.6%** ← peak |
| 150 | 40.7% |

An interior optimum, same shape as the cap-gate curve. The replay inspection
on `keepout|A` had *contradicted* its own prediction — cheese there *rose*
after round 1200 (4144, 3151, 5435...) because that map is cap-bound, not
reserve-bound, so the change cannot fire on it. The dose established
causality by response where inspection could not.

### Process failure caught during the chart routine

The accept, the snapshot and the code change **were never committed.** I ran
the `sed`, created `src/g_iter19`, launched vs-old-bots — and moved on to the
RULES.md audit without a commit. It surfaced only because
`plot_progress.py` reported "18 accepted iterations, g_iter1..g_iter18" while
`src/g_iter19/` existed on disk, and `git status` showed `?? src/g_iter19/`
alongside a modified `src/bot/RobotPlayer.java`.

Verified before committing: `src/bot` carries the accepted value
(`round > 1200 ? 400 : 1000`) and is code-identical to `g_iter19`, so the
working tree was coherent — only unrecorded.

The lesson is that the post-accept routine has a fifth step, and it is the
one that makes the other four durable: **commit the snapshot and the code in
the same action that runs the charts.** A chart regenerated from an
uncommitted snapshot is a chart of something that does not exist in the
repository. Recorded in the standing memory note.

## Iteration 94 — lower the bite-boost gate (1000 → 300) — INERT, and my analysis was biased

    boost gate 300 vs g_iter19 (gate 1000):  26/54 = 48.1%

Reverted; code verified identical to `g_iter19`.

The setup was sound and the interaction is real: the boost is worth ~+4
points, it only fires above 1000 cheese, and on `minimaze|B` cheese sits at
or below 1000 for **68% of the game** (419/389/314/424 after round 1250) —
made worse by Iteration 92's reserve decay, which deliberately spends cheese
down after round 1200. Two accepted changes genuinely do fight each other
there. Opening the gate simply does not convert.

### The analysis error is the more useful finding

I selected the three games I studied **from the loss list**, computed our
catDamage share in each (50.6%, 43.6%, 38.3%), and concluded cat share was
the deciding deficit. That is textbook selection bias: *of course* our share
is low in games we lost — losing on points and having a low share of the
biggest term are close to the same statement. The sample could not have shown
anything else.

And in a **mirror** match the error is worse than usual, because both sides
run byte-identical policy. A share difference between them cannot be caused
by policy at all; it can only come from map and spawn side. The mirror's own
side split confirms an asymmetry is present:

    side A: 16W-11L = 59.3%
    side B: 14W-13L = 51.9%

So "our cat share is 38%" in a mirror loss is not evidence that our
cat-fighting policy is weak. It is mostly evidence about where that map's
cats patrol relative to each spawn.

**Rule going forward:** when sampling replays to locate a deficit, sample
wins *and* losses, and in a mirror treat any inter-team difference as
positional until shown otherwise. The four accepted changes this session were
all found by tracing a *stall over time within one game* — an absolute
signal, immune to this bias — rather than by comparing our number to the
opponent's.

## Iteration 95 — let a zero army bypass the build-count cap — REJECTED

    zero-army cap bypass vs g_iter19:  25/54 = 46.3%
    bucket-A losses (rd 200-999):      8 -> 9

Reverted; code verified identical to `g_iter19`.

**The diagnosis was right and the fix still fails.** The mechanism fired
exactly as designed — on `tiny|B`, round 300 goes from *0 rats* to *4 rats*,
so the cap really had been blocking recovery. But the game gets worse, not
better:

| round | control | Iteration 95 |
|---|---|---|
| 300 | 0 rats, 621 cheese | **4 rats**, 496 cheese |
| 400 | 1 rat, 411 | 8 rats, 356 |
| 500 | 7 rats, 321 | 1 rat, **126** |
| 600 | — | 1 rat, **0** |
| game ends | round 975 | round **600** |

We rebuild, spend the treasury, and starve *sooner*. The 621 cheese buys
about six rats, and the King's 2/round upkeep plus build costs outpace what
those rats collect before dying. **At that point the binding constraint is
income, not permission** — the army wipe on that map is simply not
recoverable, and unlocking the build only converts a slow death into a fast
one.

This is the same shape as Iteration 60's flat cap raise (window-0 cheese
2306 → 565, live rats stuck at 4): building more while poor accelerates
bankruptcy.

It also revises the Iteration 40 verdict I wrote two hours ago. I said that
override measured inert *because* a second gate — the count cap — still
blocked the door. That was a real second gate, but opening it does not help
either. The correct reading is that **both gates were guarding something that
was not worth reaching**: with no army and ~600 cheese, no build policy
recovers the game.

### Bucket B, for the record

The other loss bucket (rounds 1000-1999, a disjoint map set) is not an
economic failure at all. On `jail|B` we hold 28-36 rats the whole game and
out-deliver the opponent late (ratio 1.06, 1.15, **1.37**), then lose one
decisive fight at round 1600 (367 combat actions, army 34 → 11) and die at
1825. In a mirror, where both sides run near-identical code, that is closer
to the symmetric loser of an even fight than to a defect worth chasing.

So of the three loss buckets against the mirror: **A is unrecoverable once it
happens, B is largely symmetric, and the points-losses turn on a cat-damage
share that is mostly positional.** That is a reasonable place for the session
to have arrived — the remaining losses are not obviously defects.

---

## g_iter19 on the benchmark set — 2/162. The session's accepts do not transfer.

| instrument | control (start of session) | g_iter19 (4 accepts) |
|---|---|---|
| `g_iter<latest>` mirror | 50% by construction | 53.7-57.4% per step |
| peers | 65/108 = 60.2% | **77/108 = 71.3%** |
| **benchmarks** | **5/162** | **2/162** |
| early wipes (< rd 100) | 25/157 = 16% | **41/160 = 26%** |

Four accepts improved our own lineage by ~11 points on peers and made us
**measurably worse against real MIT tournament entries**, with early King
wipes up by more than half.

This is the third branch of the reading I pre-registered before running it:
*"the accepts may be overfitting to the mirror, which would be worth
investigating before continuing the same method."* Taking that seriously.

### The likely cause, and the tension it exposes

All four accepts were economic — build more rats when rich, spend the reserve
later, and (Iteration 82) **remove the King's defensive trap ring**. On the
mirror and against peers those are free, because *nobody rushes the King
there*: 0% of mirror losses are early wipes. Against tournament bots, 91% of
games are decided by King destruction and they rush from round 6.

So Iteration 82's ablation was correctly measured and wrongly generalised.
The trap ring is worthless on instruments where the King is never attacked
early, and its original benchmark credit (`bench_finalist` 0% → 7-10%) may
have been real all along. I dismissed that as "measured on an instrument that
cannot resolve ±2 games" — true of the *resolution*, but the instrument was
the only one exhibiting the threat the feature defends against.

**Resolution and representativeness are different properties, and I traded
the second for the first.** The mirror is pinned at 50% and plays both sides,
so it resolves small effects — but it cannot measure a defence against a
behaviour it does not contain. A feature can be genuinely inert on every even
matchup available and still matter against the actual opponent.

That is a sharper statement of the earlier "peers and benchmarks play
different games" finding, and it cuts the other way: I used that finding to
justify trusting the even instruments, and it equally implies they are blind
to threats their opponents never pose.

### Immediate test

Restoring the King trap ring (`KING_TRAPS_ENABLED = true`) on top of the
other three accepts and measuring benchmarks directly. If it recovers toward
5/162, Iteration 82 is the regression and should be reverted regardless of
its mirror score.

## Iteration 96 — restore the King trap ring. Iteration 82's accept is REVERSED.

| | traps OFF (`g_iter19`) | traps ON | session control |
|---|---|---|---|
| benchmarks | 2/162 | **3/162** | 5/162 |
| **early wipes (< rd 100)** | **26%** | **13%** | 16% |
| mirror | **57.4%** (better) | — | 50% |

**Early King wipes halve when the ring returns**, to below the session's
starting rate. The hypothesis is confirmed: the wipes cluster exactly where a
rush is possible — `knifefight` 6, `tiny` 6, `dirtfulcat` 5, `thunderdome` 4
— and the fastest losses are rounds **17-28**, the King dying before the game
begins.

### Why the mirror could not see this

**0% of mirror losses are early wipes.** Our own lineage never rushes the
King, so a defensive feature costs actions and returns nothing there —
Iteration 82's 57.4% was a correct measurement of a real saving, on an
instrument that does not contain the threat the feature defends against.

That is the sharpest methodological result of the session, and it corrects
the one I recorded this morning. I established that even matchups have
resolution and lopsided ones do not, and used it to rank the mirror first.
That ranking is right about *resolution* and silent about
**representativeness** — whether the instrument exhibits the behaviour you
are defending against at all. The mirror has the first property and lacks the
second, and no amount of resolution fixes that.

Restated for the accept criteria: **the mirror can prove a feature is not
paying for itself; it cannot prove a feature is unnecessary.** For anything
defensive, the benchmark set is the only instrument that poses the threat,
and its low resolution is a reason to read it carefully — not to ignore it.

### Where this leaves the four accepts

Traps restored recovers only 2/162 → 3/162 against the session control's
5/162, so the trap ring explains the early-wipe regression but not the whole
win gap. The other three accepts (cheese-gated cap, gate/reserve alignment,
late reserve decay) are all economic and remain untested against benchmarks
in isolation. They may be neutral there, or they may cost the remaining 2
games; that is the next thing to measure, one at a time.

## Iteration 97 — revert the late-reserve decay — benchmarks 3/162 → 4/162

    with reserve decay (Iteration 92):   3/162, early wipes 13%
    decay REVERTED:                      4/162, early wipes 13%
    session control:                     5/162, early wipes 16%

Iteration 92 was costing a benchmark game. Reverting it also recovers a win
against `bench_spaark`, which the with-decay build never beat.

Running total of the session's four accepts, measured against the real
target one at a time:

| build | benchmarks | early wipes |
|---|---|---|
| session control | **5/162** | 16% |
| all four accepts (`g_iter19`) | 2/162 | 26% |
| + King trap ring restored (rev. 82) | 3/162 | **13%** |
| + reserve decay reverted (rev. 92) | **4/162** | 13% |

**Two of the four accepts were costing tournament games**, and both were
accepted on the mirror and peers with clean, well-measured, correctly-run
experiments. The remaining gap is one game, and the only untested accept is
the cheese-gated population cap (Iterations 88/90) — now measuring.

### The pattern is consistent enough to name

Every one of these was an *economic* change validated on instruments that are
our own lineage:

- they never rush the King, so a defence looks like pure cost (trap ring)
- they never punish a thin treasury, so spending the survival buffer looks
  free (reserve decay)

Both changes are genuinely correct *for the game the mirror plays*. The
mirror's game is not the tournament's game: 91% of benchmark games end by
King destruction, 0% of mirror losses are early wipes.

This does not retract the resolution finding — the mirror really is the only
instrument that can resolve a 4-game effect, and the retracted Iteration 63
accept remains correctly retracted. It adds a second requirement alongside
it: **an instrument must both resolve the effect and pose the threat.** No
single Gauntlet available here does both, so a change that survives the
mirror still needs a benchmark reading before it is trusted, and the
early-wipe rate is the counter that moves first.

### The decisive measurement: the mirror has ZERO VARIANCE on the deciding counter

    early-wipe rate (losses before round 100)

    mirror, g_iter17 baseline      0/40  = 0%
    mirror, all four accepts       0/24  = 0%

    benchmarks, session control   25/157 = 16%
    benchmarks, g_iter19          41/160 = 26%
    benchmarks, + trap ring       21/159 = 13%
    benchmarks, + reserve revert  21/158 = 13%

**The counter that exposed the entire regression is identically zero on the
mirror, in every build.** This is stronger than "low resolution" — a
low-resolution instrument gives a noisy reading of the right quantity. Here
the quantity does not vary at all, so no sample size, no dose-response and no
identity check could ever have recovered it. The mirror is not a blurry view
of King defence; it is blind to it.

That is the precise statement of what went wrong today. I ranked instruments
by resolution, which is the correct ranking *for effects the instrument can
express*, and then applied it to an effect one instrument could not express.
The fix is not to demote the mirror — it remains the only thing that can
resolve a 4-game economic change — but to check, before trusting an ablation,
whether the counter the feature protects has any variance on that instrument
at all.

**Concrete pre-flight check, cheap enough to always run:** before ablating a
defensive feature, compute the relevant failure counter on the instrument you
plan to use. If it reads 0 across builds, that instrument cannot answer the
question, and the benchmark set — however coarse — is the only one that can.

---

# Session outcome: four accepts, all reverted. Net bot change: zero.

    build                                    benchmarks   early wipes
    session control (g_iter15-equivalent)      5/162          16%
    all four accepts (g_iter19)                2/162          26%
    + King trap ring restored  (rev. 82)       3/162          13%
    + reserve decay reverted   (rev. 92)       4/162          13%
    + cap gate reverted        (rev. 88/90)    5/162          16%

Reverting the last accept returns **exactly the control's score and its
per-opponent breakdown** (finalist 4, spaark 0, stroke 1) and its identical
16% early-wipe rate. `src/bot` is now functionally identical to the build the
session started with.

**All four accepts improved the mirror and peers and cost benchmark games.**
Peers moved 60.2% → 71.3% and the real target moved 5/162 → 2/162. Every
experiment was correctly run — controls on the same map set, arm-to-arm
identity checks, dose curves with interior optima, mechanism checks in
replays. The measurements were right; the instrument was wrong for the
question.

## What actually happened

The mirror and peers are both our own lineage. They never rush the King
(**0% early wipes, in every build**) and never punish a thin treasury. So on
those instruments:

- a defensive trap ring is pure cost → ablating it scores +7.4%
- spending the King's survival buffer is free → decaying it scores +3
- building more rats when rich is free → gating the cap scores +7.4

All three are true statements about the game the mirror plays. The tournament
plays a different game: 91% of benchmark losses end by King destruction, and
the fastest are rounds 17-28.

## What the session is actually worth

No bot improvement. The output is methodological, and it is not small:

- **`TRAINING_ALGORITHM.md` rewritten** around instruments-by-resolution, the
  three cheap checks (arm-to-arm identity, per-round normalisation, timestamp
  correlation), dose-response semantics, the ablation programme, trace-first
  hypothesis finding, the post-accept routine, and now the representativeness
  limit.
- **`RULES.md` audited** against the engine; three real gaps closed (backstab
  attribution asymmetry, score terms as accruing shares, the Kings-only
  shared-array write that had silently broken Iteration 80).
- **Six features ablated** and their true values measured for the first time:
  the exploration-heading reassignment is worth ~+28 and `REPLACEMENT_RESERVE`
  ~+24, while the feature accepted at a headline "95.0%" is worth ~0.
- **The central lesson**, which cost the day to learn: *an instrument must
  both resolve the effect and pose the threat, and no single Gauntlet here
  does both.* The early-wipe counter is identically 0 on the mirror and moves
  16→26→13 on benchmarks — not low resolution, zero variance.

## What to do next, concretely

Any future economic change must be measured on **both** the mirror (for
resolution) and the benchmark set (for representativeness), with the
early-wipe rate read alongside the win count. A change that improves the
mirror and raises early wipes is a regression regardless of its mirror score.

## Iteration 99 — ACCEPTED. King attacks below the build. (This is Iteration 77, re-judged.)

| instrument | control | Iteration 99 |
|---|---|---|
| **benchmark early wipes** (primary) | 16% (25/157) | **14% (22/157)** |
| early wipes fixed / added | — | **3 fixed, 0 added** |
| benchmark wins | 5/162 | 5/162 |
| mirror vs `g_iter15` | 50% by construction | **50.0%** |
| fastest loss | round 17 | round **20** |

Snapshotted as `src/g_iter20`.

### The trace that found it

`bench_stroke__knifefight__botB`, a 17-round loss:

    rounds  them                            us
    1-5     SpawnAction every round         SpawnAction x4
    6-17    SpawnAction + RatAttack x3-6    RatAttack x1, nothing else
    17      -                               our King dies

They spawn **16** rats in 17 rounds; we spawn **4** and then stop. From round
6 our only action each round is a single `RatAttack` — the King swinging
instead of building, because `attackNearestHostile` ran before the build and a
King has one action per turn. **The moment an enemy arrives, production
halts.** Same signature on `thunderdome`: spawns every round 1-5, then rounds
6-13 are attack-only while the opponent spawns every round (36 to our 22).

### Why this was rejected once already

**Iteration 77 made this exact change today and I called it inert** — 5/162
wins, unchanged. I never looked at the early-wipe rate. Its census even showed
King spawns rising 22 → 25 on `knifefight`, which I dismissed as "hits the
cap, +3 rats, not enough to matter." In a 17-round game the cap is irrelevant
and three extra rats early is the entire game.

So the same code, measured against a counter that is upstream of 91% of these
losses and that actually varies on this instrument, is an improvement. The
diff is one-directional — 3 early wipes fixed, none added, across 44 changed
games — which is the shape the algorithm calls causal rather than chance.

### What made the difference

Not a better idea; a better instrument choice. Today's sequence on this one
change: accepted-then-retracted on the mirror (Iteration 63 pattern), rejected
as inert on benchmark *wins* (Iteration 77), accepted on benchmark *early
wipes* (Iteration 99). The mechanism never changed. **Choosing the counter is
as consequential as choosing the change**, and the counter to choose is the
one that is upstream of how the games are actually lost and that has variance
on the instrument that poses the threat.

### Iteration 99 on vs_old_bots, and the next target

    g_iter20: 46/54 = 85.2% vs g_iter1, 49/54 = 90.7% vs g_iter11
    overall 95/108 = 88.0%   (unchanged from the session baseline)

Flat, as expected from a lopsided instrument. All four charts regenerated per
the routine.

**What the accepted change bought, precisely:** the three fixed wipes roughly
doubled their survival — `thunderdome|B` 81 → 172, `tiny|B` 65 → 131,
`thunderdome|A` 45 → 111 — and all three still lost. The fix buys time, not
games, which is consistent with wins staying at 5/162.

**The new binding cause, traced on `knifefight` under the accepted build:**

    our spawns 25 vs their 29        (was 4 vs 16 -- the King fix worked)
    rd 6:  TriggerTrap x4, DieAction, Damage, Stun
    rd 8:  TriggerTrap x1
    rd 9:  TriggerTrap x1, DieAction
    rd 13: TriggerTrap x2, DieAction, Damage x2, Stun x2

Production is now near parity; **their trap wall is what kills us.** They
place a trap almost every round from round 2, and our rats walk into four of
them in a single round.

**Checked, and it is not a repeat of the Iteration 77 error.** Trap-zone
avoidance (Iteration 69) was rejected on wins, so I re-read it on the
early-wipe counter: **16% (25/158) — identical to control.** It genuinely
does not help the rush, despite halving total deaths on long maps
(67 → 31 on `hatefullattice`). The likely reason is structural: that design
only learns a zone *after* a rat triggers a trap, which is far too slow for a
game decided in 20-60 rounds.

So the next hypothesis needs trap avoidance that requires no learning — the
enemy traps on `knifefight` sit in the corridor between two Kings five tiles
apart, which is knowable from map geometry alone at round 1.

## Iteration 100 — geometry-based threat-direction avoidance — REJECTED

    benchmark early wipes (primary):  14% -> 14%   (no change)
    benchmark wins:                   5/162 -> 4/162
    knifefight TriggerTrap:           30 -> 27
    knifefight King spawns:           25 -> 25   (unchanged, as intended)
    knifefight game length:           rd 64 -> rd 71

Reverted; code verified identical to `g_iter20`.

**The mechanism barely fired — three fewer trap triggers out of thirty.** The
design has a flaw I should have seen before running it: the avoidance was
placed in `explore()`, which only executes when a rat has *nothing better to
do*. A rat heading for cheese goes through `moveToward`/`collectCheese` and
never reaches that code, and cheese-seeking is most of our movement. So the
change steered only idle rats.

The second flaw is geometric and was visible in my own measurement. Their
traps average **4.6 tiles from OUR King** — they ring the place our rats
spawn and must leave from. "Head away from the threat direction" cannot help
when the danger surrounds the origin rather than lying along one bearing.

### What this says about the target

Early wipes have now resisted two distinct trap-avoidance designs — learned
zones (Iteration 69, 16% vs 16%) and geometric steering (this, 14% vs 14%) —
while the one thing that *did* move the counter was giving the King its build
action back (Iteration 99, 16% → 14%, three wipes fixed and none added).

That is a consistent signal: on rush maps our problem is that we are
outproduced in the opening, not that we route badly. They spawn every round
from round 1 and place a trap almost every round from round 2; the traps kill
rats we could afford to lose if we had built more of them. The productive
direction is therefore more early production, not better early evasion — and
`MAX_POPULATION`/`REPLACEMENT_RESERVE` are the constants that throttle it,
both of which were reverted today for costing benchmark games in their
*general* form. A version scoped to the opening on rush maps has not been
tried.

## Analysis note — early wipes are entirely a close-spawn phenomenon

Not an iteration; a measurement made while Iteration 101 was running. King
spawn positions come from the replay headers of the g_iter20 benchmark run
(`gauntlet/20260903-201821`), one replay per map, cross-referenced against
that run's early wipes (`tools/early_wipes.py`).

    map                King dist   wipes/losses
    knifefight              5.0        6/6
    tiny                    5.0        5/6
    thunderdome             8.0        3/6
    dirtfulcat             15.0        4/6
    popthecork             17.0        1/4
    evileye                21.2        1/6
    toomuchcheese          21.2        2/5
    -- 20 maps at 26.0 .. 76.4 --      0/117

**All 22 early wipes are on the seven maps whose Kings spawn within ~21
tiles. The twenty farther maps produce exactly zero.** The separation is
total, with no overlap at all.

Two consequences.

**1. The wipe problem is much smaller than it looks, and much denser.** Those
seven maps supply 42 of 162 benchmark games. We lose 22 of them to a King
killed before round 100 — better than half. Work aimed at wipes should be
judged on those 42 games; averaged over 162 the signal is diluted by a
two-thirds majority of maps where the failure mode does not exist. This is
the same shape as the representativeness problem with the mirror, one level
down: most of the benchmark set does not pose the threat either.

**2. The obvious way to scope a fix does NOT work, and this is worth knowing
before spending an iteration on it.** `RobotController` exposes map width and
height and our own King's location, but not the symmetry type, so the best
round-1 estimate is the minimum distance over the three candidate symmetries
(rotation, horizontal reflection, vertical reflection). That minimum is only
a lower bound and it is badly polluted:

    map                        true   min-est
    corridorofdoomanddespair   51.0       1.0
    streetsofnewyork           47.0       1.0
    keepout                    39.0       3.0
    dirtpassageway             43.0       3.0

A King sitting near a mirror axis in one coordinate scores as "close" under
the reflection candidate for that axis even when the true symmetry is a
rotation putting the enemy 50 tiles away. The threshold that captures all 22
wipes (est < 18) fires on twelve maps, seven of them wrong -- 42% precision,
and the false positives include `rift`, `pipes`, `jail` and `keepout`.
Conditioning a rush posture on that geometry would apply it to maps where it
is dead weight, which is exactly how Iterations 88/90/92 died.

So a rush response must be triggered by OBSERVATION -- an enemy rat actually
arriving near our King early -- not by predicting the matchup from map
geometry at round 1. Noted for the iteration after 101.

## Iteration 101 — build:trap ratio, dose 2 (one trap per two builds) — REJECTED

                              g_iter20        dose 2
    benchmark wins            5/162           5/162
    early wipes (PRIMARY)     22/157 = 14%    29/157 = 18%   WORSE
    close-spawn wins          3/42            2/42
    close-spawn wipes         22/39 = 56%     27/40 = 68%

Reverted; code verified identical to `g_iter20`. Dose 3 NOT run -- the task
pre-registered "if dose 2 raises early wipes, the mechanism is refuted and
dose 3 is not worth running", and it did.

The hypothesis was right about the mechanism and wrong about the sign. The
King really does strictly alternate build and trap from the fifth rat, and it
really does cost six builds in the first twenty rounds on `knifefight`. But
buying those builds back with traps costs more than the rats are worth.

**This independently reconfirms Iteration 96 by a different manipulation.**
Iteration 96 restored the ring by turning it ON vs OFF and halved wipes
26% -> 13%. This varied the ring's DENSITY instead, holding it on, and moved
wipes the same way: thinner ring, more wipes. Two unrelated knobs, same
direction, so the trap ring's value is not an artifact of how it was
switched. 1:1 alternation is at or near optimal and should stop being treated
as an unexamined default -- it has now been measured.

**The wipe boundary moved outward, which is the close-spawn model predicting
something new.** Per-map wipes:

    thunderdome  3 -> 5     evileye     1 -> 2
    tiny         5 -> 6     starvation  0 -> 1   <-- previously wipe-free
    popthecork   1 -> 2     keepout     0 -> 1   <-- previously wipe-free

`starvation` has King distance 26.0 -- the closest of the twenty maps that
had never produced a wipe. Weakening the defence did not scatter wipes at
random; it pushed the boundary out from ~21 to past 26, taking the nearest
safe map first. That is a real prediction the map-distance measurement made
and passed.

### Where this leaves the rush

Two designs have now failed to reduce wipes (learned zones, geometric
steering) and one has failed by *removing* defence (this). The only change
that ever moved the counter was giving the King its build action back
(Iteration 99, 16% -> 14%). The evidence now says the ring is underweight
rather than overweight, so the untested direction is MORE trap density in the
opening, not less -- dose 0.5, so to speak. That is Iteration 102.

### Loss-cause split, measured on the g_iter20 run

                     losses   King destroyed   on points
    close-spawn        39       39 (100%)         0
    far               118      104 (88%)         14

143 of 157 losses are our King dying, 91%. Only 14 games in the whole set are
decided on points. Close and far maps do not differ in HOW we lose, only
WHEN: every close-spawn loss is over before round 500, while 61% of far
losses run 500-1999.

## Analysis note — a 300-round production blackout in every far-map game

Measured with `tools/king_census.py --window 100` on five far-map losses from
the g_iter20 benchmark run, chosen to span the loss-round range (475, 521,
705, 970, 2000) and three different opponents. The shape is identical in all
five:

    game (far map)        alive at rd 99/199/299/399   cheese rd400   rd400 burst
    peaceinourtime         25 / 20 / 18 / 14              1263            6
    whatsthecatdoin        25 / 22 / 19 / 18               490            1
    dirtpassageway         20 /  4 /  1 /  0               130            0
    corridorofdoomand...   18 /  5 /  2 /  1               715            7
    minimaze               23 / 20 / 20 / 15              1636           15

**Zero King spawns in rounds 100-399 in all five games.** Window 0 is
CAP-LIMITED in all five -- 25 rats built in the first ~25 rounds, the
proven-good opening burst -- and then nothing at all for three hundred rounds
while the army decays by 30-100%.

Two throttles interact to produce it:

1. `builtCount` is cumulative-ever-built and the cap is `MAX_POPULATION = 25`,
   so once the opening burst finishes we are locked out of replacing any loss.
2. The lock is only released when the budget refreshes, and
   `BUILD_WINDOW_ROUNDS = 400`.

Iteration 38 identified defect 1 and fixed it *partially* by adding the window
refresh. The 400 was never justified or tuned -- exactly like the 1:1 trap
ratio Iteration 101 examined, it was chosen once and then inherited by every
iteration since.

**The second half of the interaction is worse than the first.** The refresh
also latches `replacementMode = true`, which raises the bar from
`RESERVE = 150` to `REPLACEMENT_RESERVE = 1000`. So the replacement window
opens at round 400 -- and by round 400 the economy has usually decayed below
the reserve it must now clear, because the economy decays *because* the army
decayed. `whatsthecatdoin` reaches round 400 with 490 cheese and builds one
rat; `dirtpassageway` reaches it with 130 and builds none, having already
been at zero rats since round 300. The window opens exactly when we can no
longer afford to use it.

Where it does not bite, we survive: `minimaze` holds 1636 cheese at round
400, builds 15, recovers to 23-28 rats and is one of only 14 games in the set
that reaches round 2000.

### Why this is not Iterations 88/90/92 again

Those raised `MAX_POPULATION` (88/90) and decayed `REPLACEMENT_RESERVE` (92).
All three were accepted on the mirror and peers and then reverted for costing
benchmark games, so both of those constants are now defended by benchmark
evidence and should be left alone. `BUILD_WINDOW_ROUNDS` is a third,
untouched constant, and it is the one that sets the *duration* of the
blackout rather than its depth. Shortening it is also the opposite direction
from Iterations 28-31/34/37, which all tried to SLOW the King and all made
things worse.

Next: Iteration 103, dose `BUILD_WINDOW_ROUNDS` 400 -> 150, after Iteration
102 resolves. Expected mechanism: refreshes at 150 and 300 while cheese is
still ~1100-1200, which buys 2-3 rats each time against the 1000 reserve --
turning 25 -> 14 into roughly 25 -> 19. Modest by construction, because the
reserve stays where the benchmark evidence put it.

## Analysis note — the far-map deficit is cheese, not population

Follow-up to the blackout note above, and it partly REFUTES the fix that note
proposed. Mid-game round headers from three far-map losses (`replay-dump.sh`,
teams read from the header each time):

    map (round 100)     our rats  their rats   our cheese  their cheese
    peaceinourtime         22        16           1190        1980
    corridorofdoom         14        16           1113        2023
    dirtpassageway         14        18            675        1819

**On `peaceinourtime` we hold MORE rats than the opponent and still lose.**
So "we run out of rats" cannot be the general far-map story, and the
300-round blackout -- which is real and universal -- is not obviously the
thing that decides these games.

The deficit that IS universal is cheese: the opponent holds 1.7x to 2.7x ours
at round 100 in every game measured. And it is not an income difference.
`cheeseTransferred` is comparable (peaceinourtime 160 vs 140 at round 100,
220 vs 300 at round 150; corridorofdoom 180 vs 280). It is a SPENDING
difference. Both sides start near 2500; by round 100 we have spent ~1300 on
the opening burst of 25 rats plus traps, and they have spent ~520.

So the shape of a far-map loss is: we convert our treasury into a larger army
early, the army does not out-earn theirs per capita, and we spend the rest of
the game poor while they stay rich.

### What kills the rats we do have

All four deaths on `peaceinourtime` in rounds 100-200 are cat kills, and on
`dirtpassageway` the decay is a cat farming us:

    round 102-103  id10519 RatAttack x2   -> 104  cat kills id10519
    round 105      id12575 RatAttack      -> 106  cat kills id12575
    round 106-108  id13132 RatAttack x3   -> 109  cat kills id13132
    round 113      cat kills id14032         127  cat kills id10782

Our rats attack the cat, keep attacking, and die. A CAT is 4000 HP dealing 30
per scratch; RAT_BITE_DAMAGE is 10 and a Baby Rat has 100 HP, so a cat trades
four scratches for a rat against the 400 bites needed to kill it. On
`corridorofdoom` only 5 of 17 deaths are cats, so this is not universal
either -- but where the decay is worst, a cat is doing it.

### Consequence for Iteration 103

The blackout measurement stands; the inference from it does not. Iteration
103 was going to buy ~6 extra rats in rounds 100-400, and the case for that
rested on a population deficit that `peaceinourtime` shows we do not always
have. Buying more rats also spends the cheese we are already short of, which
is the one deficit that IS universal -- so the dose may push the wrong lever
in the wrong direction.

Iteration 103 is NOT run as specified. The question worth asking first is why
25 rats earn no more than their 16, since that -- not the count -- is what
the numbers actually indict.

## Iteration 102 — trap density, dose 2 (two traps per build) — ACCEPTED (g_iter21)

                              g_iter20        dose 2
    benchmark wins            5/162           7/162
    early wipes (PRIMARY)     22/157 = 14%    12/155 = 8%
    close-spawn wins          3/42            4/42
    close-spawn wipes         22/39 = 56%     12/38 = 32%
    g_iter20 mirror           --              26/54 = 48.1%

All four pre-registered bars cleared. The mechanism check confirms the trade
it was supposed to make -- `knifefight` rounds 0-19, traps 6 -> 8 and spawns
11 -> 8.

**The three doses form a monotone curve**, which is why this is believable
rather than two games of luck:

    trap density                  wins    early wipes
    fewer  (Iter 101, 1 per 2)    5/162   29/157 = 18%
    1:1    (g_iter20)             5/162   22/157 = 14%
    more   (Iter 102, 2 per 1)    7/162   12/155 =  8%

Three points, one direction, on both counters. The 1:1 ratio was never chosen
-- it fell out of the `!lastBuildWasTrap` boolean in Iteration 48 and was
inherited unexamined by every iteration since. It was wrong, and wrong in the
direction of too FEW traps.

### Games changed

    + bench_spaark    popthecork      sideA  rd601    <-- first win ever vs spaark
    + bench_finalist  dirtfulcat      sideB  rd119
    + bench_stroke    uneruesansfin   sideA  rd1814
    - bench_finalist  toomuchcheese   sideB  rd183

`bench_spaark` had been 0/54 in every previous run of the session and is the
hardest opponent in the set (median loss round 56 on close-spawn maps).

### Why the mirror does not veto this

26/54 = 48.1% is one game below even, i.e. no regression, and it carries no
positive information either. The mirror has **0% early wipes in every build**
because our own lineage never rushes the King, so it cannot measure a
defensive feature at any resolution -- the same reason Iteration 82's 57.4%
mirror result was wrong and had to be reversed by Iteration 96. Resolution
and representativeness are different properties. Here the instrument that
poses the threat (benchmarks, 22 wipes) is the one that moved, and the
instrument that cannot pose it stayed flat. That is the expected signature,
not a conflict.

### Iteration 102 post-accept: vs_old_bots

    g_iter20   95/108 = 88.0%   (g_iter1 46/54, g_iter11 49/54)
    g_iter21   99/108 = 91.7%   (g_iter1 49/54, g_iter11 50/54)

Best of the session, and it moves in the same direction as the benchmark set
rather than trading against it. Charts regenerated: `vs_old_bots.png`,
`cumulative_iterations.png` (now g_iter1..g_iter21), `peer_win_spread.png`.

## Iteration 103 — ablate lone-rat cat APPROACH — REJECTED, behaviour VALIDATED

    g_iter21 mirror, approach OFF, playing the version that has it:
        21/54 = 38.9%

Six games below even. Per the ablation rule (<50% validates the feature), the
lone-rat cat approach is worth roughly **+11 points** -- the second-largest
measured feature value in the bot, behind only the exploration-heading
reassignment's ~28. Reverted; code verified identical to `g_iter21`.
Benchmarks NOT run: the task pre-registered the mirror as primary and it
answered clearly.

### Where my reasoning went wrong

The arithmetic was right and the conclusion was wrong. A rat really does
survive only ~15 rounds against a cat and land only ~150 damage on 4000 HP,
and `transferCheese` really does share the action cooldown, so the swings
really are deliveries not made. All of that is true and none of it decides
the question, because **I priced `catDamage` as though it were uncontested.**

I took the price from `peaceinourtime`, where the counter reads us 204 and
them 0, and concluded that further cat damage buys nothing because we already
hold the whole term. That is correct *against `bench_finalist`, who does not
farm cats at all*. It is exactly backwards on an instrument where the
opponent does farm them -- and in a mirror the opponent is our own code, so
it farms them precisely as hard as we do. Stopping there does not save a rat
and forgo a small gain; it hands the opponent 100% of a 0.3-weighted term we
were splitting.

This is [[proportional-score-terms-are-contested]] in TRAINING_LOG form:
conceding a proportional term we are splitting costs its FULL weight, not the
increment. I had the memory, quoted the risk in the task's own RISK line --
"conceding catDamage share where it is currently CONTESTED rather than
dominated" -- and then chose the baseline from the one map where it was not
contested. The risk note was right and I under-weighted it.

### What the measurement does NOT overturn

The mechanism numbers stand and remain odd:

    dirtpassageway rd 95-205   cat kills ours 6, theirs 1   RatAttacks 14 vs 15
    peaceinourtime rd 1-200    cat kills ours 6, theirs 2   RatAttacks 35 vs 2

Equal attack volume on `dirtpassageway` with a 6:1 death ratio, and single-rat
streaks of 18, 10 and 5 consecutive swings. So the approach is worth having
AND we die to cats far more than opponents do at comparable volume. Both can
be true: the term is worth contesting, and our way of contesting it is
needlessly lethal. The untested question is not whether to approach but
whether to *disengage* -- bite, then leave before the fourth scratch lands --
which keeps the share and stops paying a whole rat for it. That is a
different iteration from this one, and this result does not speak to it.

### Correction to Iteration 103: this result was already known

Iteration 103 re-derived **Iteration 63**. That iteration tightened the same
cat-engagement gate (to `allies >= 3`) and measured **41/108 = 38.0%** on the
peer set, with our `catDamage` against `pure_cooperator` collapsing
4644 -> 480 while theirs held near 6000. Iteration 103 removed the approach
instead of tightening it and measured **21/54 = 38.9%** on the mirror.

Two different instruments, two different implementations of the same idea,
forty iterations apart, agreeing to within one percentage point. The `+11
points` figure and the mirror measurement are new; the conclusion was not.
The cost of the repeat was one 54-game run, and it was avoidable by reading
the log entry for Iteration 63 before writing the hypothesis.

Recorded so the next attempt on cat behaviour starts from "abstention has
been measured twice and costs ~11 points" rather than rediscovering it a
third time.

## Iteration 104 — disengage from cats at 60 HP instead of 30 — REJECTED

    g_iter21 mirror:  26/54 = 48.1%

One game below even, i.e. inert, and short of the pre-registered >50% accept
bar. Reverted.

**It completes a monotone dose curve, which is the useful part:**

    cat engagement                        mirror
    abstain entirely     (Iter 103)       38.9%
    disengage at 60 HP   (Iter 104)       48.1%
    disengage at 30 HP   (current)        50.0% by construction

Every step toward LESS cat engagement is worse, and the penalty shrinks as
the step gets smaller -- 11 points for abstaining, 2 for merely leaving
earlier. Three points, one direction.

So the inherited `> 30` is not obviously optimal; it is merely the most
aggressive value ever tried. The curve says the untested direction is MORE
engagement, not less -- exactly the shape of the trap ratio, where Iteration
101 pushed the wrong way (18% wipes), the inherited value sat in the middle
(14%), and Iteration 102 pushed the right way and was accepted (8%).

Iteration 105 therefore lowers the threshold rather than raising it: a rat
keeps closing until 10 HP instead of 30. Dose 80 is NOT worth running -- it
is further along the direction already measured as harmful twice.

## Iteration 105 — engage cats longer (gate 30 -> 10) — REJECTED, parameter CLOSED

    g_iter21 mirror:  27/54 = 50.0%

Exactly inert. Reverted. The full dose curve is now:

    cat engagement                     mirror
    abstain entirely   (Iter 103)      38.9%
    disengage at 60 HP (Iter 104)      48.1%
    disengage at 30 HP (inherited)     50.0% by construction
    disengage at 10 HP (Iter 105)      50.0%

Monotone rising to 30, then FLAT. The inherited value sits on a plateau, so
**the cat-engagement threshold is closed** -- no further dose is worth a run,
in either direction.

**Why the high dose was inert, which the arithmetic predicts in hindsight.**
`CAT_SCRATCH_DAMAGE` is 20 against 100 HP, so a rat's health from cat damage
is always a multiple of 20: 100, 80, 60, 40, 20. `> 30` engages at 100/80/60/40
and stops at 20; `> 10` engages at those plus 20. The dose therefore moved
exactly ONE health band, and a rat at 20 HP is dead to the next scratch
whether it approaches or flees. It did change the evaluated condition -- so it
was a legitimate dose, not the Iteration 70 mistake of an arm that never
differed -- but it could only ever have moved a sliver.

That check was available before the run: enumerate the discrete states the
threshold can separate, not just the numeric gap between arms. A parameter
whose input is quantised to multiples of 20 has only five reachable settings,
and 60/30/10 already sampled three of them.

### The cat question, settled

Three iterations, one conclusion. Cat engagement is worth **~+11 points** and
is the second-most valuable behaviour measured in this bot, but its threshold
is already optimal. The remaining oddity from the mechanism baseline stands
unexplained and is now known NOT to be fixable through this gate:

    dirtpassageway   cat kills ours 6, theirs 1   RatAttacks 14 vs 15
    peaceinourtime   cat kills ours 6, theirs 2   RatAttacks 35 vs 2

We still die to cats far more than opponents do at comparable attack volume.
Whatever they do differently, it is not this threshold.

## Analysis note — capability audit: what the bot has never once called

Prompted by the Iteration 106 discovery. Replay tracing can only show what the
bot DID, so every hypothesis it generates is a variation on existing
behaviour; an entire unused capability never becomes a candidate. Diffing
`RobotController.java` against `rc.` call sites in `RobotPlayer.java` takes a
few minutes and found three major mechanics with zero call sites, plus the
one already under test.

**1. `becomeRatKing()` — under test as Iteration 106.** Opponent fields up to
five kings, we have always had one, and the win condition is *all* kings dead.

**2. `carryRat()` / `throwRat()` — and the opponents already use it.**
`ThrowRat` appears in replays on the opponent side in games we lost:

    g20_spaark_kf.txt   round 12  id11643(team1=bench_spaark,RAT) ThrowRat
    spaark.txt          round 9   id10949(team2=bench_spaark,RAT) ThrowRat
    round 17 same rat again

`carryRat(loc)` grabs an ADJACENT robot, enemy included; `throwRat()` hurls it
in the facing direction. With `THROW_DURATION = 4` and `TILES_FLOWN_PER_TURN =
2` a thrown rat travels about eight tiles. Two distinct uses: launch our own
rats at the enemy King far faster than they can walk, or grab an enemy rat --
which removes it from play and, per the backstab attribution table in
RULES.md, credits the grabber.

**3. `squeak()` / `readSqueaks()` — a Baby Rat comms channel we do not have.**
`readSqueaks(roundNum)` returns messages sent to this unit in the last five
rounds. Every coordination attempt so far has gone through the shared array,
which **only Rat Kings may write** -- that restriction caused the Iteration 80
bug where Baby Rats threw `CANT_DO_THAT` and aborted their turns, and it is
why Iteration 100's threat broadcast had to be King-side. Squeaks are the
rat-to-rat channel that limitation implied we lacked.

Remaining zero-call-site methods worth noting: `placeCatTrap` (consistent with
the measurement that neither side places cat traps), `placeDirt` (walling),
`getBackstabbingTeam`, and the `isActionReady`/`isMovementReady` family.

**Why this took 105 iterations to notice.** I found it by counting `kings=` in
the per-round team-stats line -- a field `ReplayDump` has printed all session.
I had read past it hundreds of times because for us it was always `1`. The
signal was not a number that moved; it was a number that never moved, beside
an opponent's that did. Worth doing the same scan on every constant column.

## Iteration 106 — second Rat King via becomeRatKing() — VOID (mechanism never fired)

                          g_iter21        iter106
    benchmark wins        7/162           7/162
    early wipes           12/155 = 8%     12/155 = 8%
    close-spawn wins      4/42            4/42
    fastest losses        19,20,21,21,27  19,20,21,21,27   identical

Byte-identical outcomes on every counter, including the exact list of fastest
loss rounds. That is the signature of an arm that never differed, so the
mechanism check was mandatory rather than optional:

    bench_stroke__rift__botB, rounds 1-300
        2:kings=1  in every sampled round      <- us, never upgraded
        1:kings=2  in 5 of 16 sampled rounds   <- bench_stroke DOES upgrade
        our BecomeRatKing actions: 0

**VOID, not negative.** The task pre-registered exactly this: "if it never
fires, the 3x3 condition is unreachable and the result is void rather than
negative." Reverted rather than left in, since a `canBecomeRatKing()` call
that never succeeds is pure bytecode cost on every rat every round.

### Why it cannot fire, which was in the preconditions I read and under-weighted

`assertCanBecomeRatKing` rejects the upgrade if **any rat king is in the
3x3**. The only place seven of our rats are ever adjacent to one another is
the spawn congestion around our own King -- and that cluster is disqualified
by the King sitting in it. Everywhere else, rats have dispersed to collect
cheese, deliberately: spreading out for cheese search has been the policy
since Iterations 1-4, and Iteration 9's population cap exists specifically to
*prevent* rats bunching up.

So the capability is not merely unused, it is unreachable under our current
movement policy. Making it fire is not a one-line call, it is a rallying
behaviour: some rats must be told to gather at a point away from the King and
wait there until eight are present.

Also corrects a claim from the capability audit: `bench_stroke` upgrades too.
The earlier two-replay check that showed only `bench_finalist` doing it was
too small a sample.

## Reference — squeak/readSqueaks, verified against engine source

Written while Iteration 107 ran, so that whichever iteration uses this does
not repeat Iteration 106's mistake of designing against preconditions I had
skimmed rather than read.

    squeak(int content)        RobotControllerImpl:1285
      - NO type restriction. Baby Rats can call it. This is the channel the
        King-only shared array (writeSharedArray throws CANT_DO_THAT for
        non-kings) always implied we were missing -- it is why Iteration 80
        crashed and why Iteration 100 had to broadcast King-side.
      - NOT gated by assertIsActionReady. Costs no action cooldown, so unlike
        a bite or a cheese transfer it does not compete with the economy.
        Bytecode only.
      - returns false rather than throwing when over quota; no exception risk.

    MAX_MESSAGES_SENT_ROBOT = 1     but sentMessagesCount is reset in
                                    InternalRobot.processBeginningOfTurn(), so
                                    the real budget is ONE SQUEAK PER RAT PER
                                    ROUND, not one per game.
    SQUEAK_RADIUS_SQUARED   = 16    radius 4 tiles.
    MESSAGE_ROUND_DURATION  = 5     readSqueaks(-1) returns the last 5 rounds.

    Message carries: content, senderID, round, and the SENDER'S LOCATION
    (GameWorld.squeak builds it from robot.getLocation()), so a receiver learns
    where the sender was without spending any of the content field.

    Delivered to allied robots AND to cats within the radius. Harmless: no
    engine code reads a cat's message queue -- InternalRobot.getMessages() is
    called only from RobotControllerImpl, i.e. only by player code.

**Why this is worth an iteration.** Every coordination attempt so far has gone
through the shared array, which only the King may write, so all our
"communication" is one broadcaster talking to everyone. Squeaks are local,
free, per-round and rat-to-rat. Candidates, in rough order of fit with what
this session measured:

1. **Propagate a sighted enemy King.** Today only the King broadcasts, and it
   broadcasts a *guess* derived from assumed 180-degree symmetry (slots 3/4),
   which the map-geometry measurement showed is often the wrong symmetry.
   A rat that actually sees the enemy King could relay it.
2. **Rally formation.** Iteration 107 rallies to a fixed geometric point; with
   squeaks rats could converge on an actual cluster instead of a guessed spot.
3. **Cheese mine locations**, given the far-map finding that our per-capita
   income is the deficit.

## Reference — carryRat/throwRat, verified against engine source

    carryRat(loc)            RobotControllerImpl:1460
      - caller must be a BABY_RAT; target must be adjacent, THROWABLE (a baby
        rat), and not already being thrown or carried.
      - TARGET MAY BE AN ENEMY. The only enemy-specific rule is a cooldown
        against re-grabbing the same enemy rat you just threw
        (SAME_ROBOT_CARRY_COOLDOWN_TURNS).
      - requires canSenseLocation(loc), and a Baby Rat's vision is a 90-degree
        CONE -- the engine's own error text is "A rat can only grab robots in
        front of it". Facing matters, and turning costs TURNING_COOLDOWN 10.
      - costs an ACTION (assertIsActionReady), so it competes with biting and
        with cheese transfer.

    throwRat()               RobotControllerImpl:1393
      - throws in the robot's CURRENT FACING direction; needs the tile directly
        ahead to be on-map, passable and empty.
      - costs an action. THROW_DURATION 4 and TILES_FLOWN_PER_TURN 2, so a
        thrown rat covers roughly eight tiles.

    while carrying, cooldowns are multiplied by CARRY_COOLDOWN_MULTIPLIER --
    a carrying rat is slow.

**The interesting use is defensive, not offensive.** A grabbed rat is removed
from play while held: it cannot move and cannot attack. Against the early rush
that produces 91% of our losses, grabbing an attacker is far stronger crowd
control than biting it, since a bite removes 10 of its 100 HP while a grab
removes the whole unit from the assault for as long as we hold it. That is a
better fit for our measured problem than the obvious offensive use (throwing
our own rats at the enemy King), because on close-spawn maps we are the ones
being rushed.

Caveats to design against, both learned the hard way this session: the 90-degree
cone means a defender must be FACING the attacker, and the action cost means
every grab is a bite not taken -- so this must be measured on the close-spawn
split and the early-wipe counter, not on the 162-game average.

## Iteration 107 — rally to build a second Rat King — REJECTED (mechanism fired)

                          g_iter21        iter107
    benchmark wins        7/162           3/162
    early wipes           12/155 = 8%     14/159 = 9%
    close-spawn wins      4/42            2/42
    close-spawn wipes     12/38 = 32%     14/40 = 35%

**Mechanism check first, and it PASSED** -- unlike Iteration 106 this is a real
negative, not a void:

    bench_spaark__rift__botB, rounds 1-300
        2:kings=2  in 11 of 16 sampled rounds   <- we built a second King

So the rally worked, the upgrade fired, and a second Rat King made the bot
substantially worse: wins more than halved and both close-spawn counters
regressed.

### Reading it honestly

The strategic argument was sound and the price is what kills it. The upgrade
consumes **seven rats** -- 28% of a 25-rat army -- and this design also idled
about eight rats from round 30 to 150 waiting to assemble them. Against that,
the benefit is insurance on a loss mode plus one extra builder. Measured, the
insurance is worth less than the army it costs, and it is worth less
*specifically on close-spawn maps*, where wins fell 4 -> 2 and wipes rose. That
is the opposite of what the insurance argument predicted: those are exactly
the maps where a second King should have saved games, and instead the missing
defenders got the first King killed faster.

The correlation I flagged in the task now looks like the better guide than my
mechanism argument. `bench_finalist` upgrades most and is the opponent we beat
most; the reading that multi-King is something a *winning* bot can afford,
rather than something that makes a bot win, fits the data better than mine did.

### What is and is not settled

Settled: seven rats plus a 120-round rally is too expensive, and the resulting
second King does not pay for it.

Not settled: the seven-rat cost is intrinsic to the mechanic, but the rally
window is not. A version that only attempts the upgrade opportunistically --
no dedicated rallying, no idling, just taking it when seven rats happen to be
adjacent late in a game we are already winning -- has not been measured. Given
that Iteration 106 showed that situation arises essentially never on its own,
such a version would be close to inert by construction, so it is not queued as
the next thing to run.

**Sequencing note for the capability audit.** Of the three unused mechanics
found, this was the one I ran first and it is the most expensive of the three
per use. `carryRat` costs one action and removes an entire attacker from an
assault; `squeak` costs no action at all. Cheaper mechanics should have gone
first, and that ordering error cost two Gauntlet runs.

## Iteration 108 — defensive grab-and-throw near our King — REJECTED

                          g_iter21        iter108
    benchmark wins        7/162           7/162
    early wipes           12/155 = 8%     11/155 = 7%
    close-spawn wins      4/42            3/42
    close-spawn wipes     12/38 = 32%     11/39 = 28%

On the letter of the pre-registration this passes -- early wipes fell below
12 and total wins held at 7. **Rejecting anyway**, because one game on a ~4%
instrument is the resolution floor, and it bought that one wipe by giving
back one close-spawn win. Accepting this would be exactly the mistake
Iteration 63 made: taking a +-2 game move on a lopsided Gauntlet as signal.
The pre-registered bar was too loose, and the honest response is to say so
rather than bank the noise.

### Two real findings, both about measurement

**1. `carryRat` is INVISIBLE in replays.** The schema's `Action` union has no
grab or carry member -- `CatFeed, RatAttack, RatNap, RatCollision, PlaceDirt,
BreakDirt, CheesePickup, CheeseSpawn, CheeseTransfer, CatScratch, CatPounce,
PlaceTrap, RemoveTrap, TriggerTrap, ThrowRat, UpgradeToRatKing, RatSqueak,
DamageAction, StunAction, SpawnAction, DieAction`, and the indicators. Only
`ThrowRat` is recorded. So a grab can only ever be inferred from the throw
that follows it, and a grab that never leads to a throw leaves no trace at
all. Any future iteration using carry must be measured indirectly.

**2. Our rats almost certainly grabbed and then got stuck.** In
`bench_spaark__knifefight__botB` we performed **zero** throws while
`bench_spaark` performed six. Yet the results did change -- the fastest-loss
list moved from 19,20,21,21,27 to 19,20,25,27,31 -- so behaviour clearly
differed. The likely mechanism is that `assertCanThrowRat` requires the tile
directly ahead to be on-map, passable AND empty, which in the middle of a
melee around our King it usually is not. A rat that grabs and cannot throw
keeps carrying, and carrying multiplies its cooldowns, so it is neutralised
along with its captive -- the one-for-one trade the design was specifically
meant to avoid.

That is a fixable defect rather than a refutation: the design needs a
`dropRat` fallback, or should only grab when a throw is already legal. But it
means this run did not test the intended mechanism, so the numbers above
describe grab-and-stall, not grab-and-throw.

### Correction to Iteration 108 — both of my "findings" were wrong

Checked after the fact, and the entry above needs retracting on two counts.

**1. `carryRat` is NOT invisible in replays.** `InternalRobot.grabRobot`
calls `matchMaker.addRatNapAction(robotBeingCarried.getID())`, so a grab emits
a **RatNap action against the grabbed robot's id**. I concluded it was
unobservable from the absence of a "GrabRat" member in the `Action` union
without checking what the engine actually emits. Grabs are observable; they
are merely *disguised*, since a genuine rat nap emits the same action and the
two are told apart only by whose id carries it.

**2. Our rats did not "grab and get stuck" -- they barely grabbed at all.**
Counting RatNap by team on `knifefight`, ours vs the same matchup at baseline:

    enemy (team1) RatNap    baseline 9    iter108 8
    our   (team2) RatNap    baseline 6    iter108 6

If our grabs had fired, enemy rats would show *more* RatNap. They show one
fewer. So the grab essentially never triggered, and the grab-and-stall story
-- rats carrying captives they could not throw -- was fabricated to explain a
difference that had another cause.

**What actually changed, most likely: bytecode.** The change added a
`canThrowRat()` call plus a scan of `nearby` to every Baby Rat's turn.
`RobotPlayer` already tracks overruns (`reportBytecodeBudget`, the "OVERRAN"
indicator), and a turn that overruns is truncated mid-decision. That would
shift outcomes slightly without any grab ever happening, which matches what
we saw: one wipe fewer, one close-spawn win fewer, a reshuffled fastest-loss
list.

**So Iteration 108 was VOID, like Iteration 106 -- not the marginal negative I
recorded.** The rejection stands and the reasoning for it does not. Three of
the four capability iterations have now failed at the *reachability* stage
rather than on merit, which is the real pattern: `becomeRatKing` needed a
rally, `carryRat` needs the target inside a 90-degree cone while adjacent and
while we are near our own King, and only Iteration 107 ever actually executed
its mechanism.

**Method note.** I wrote a confident causal story ("grabbed and got stuck")
from a *difference in outcomes* plus one absent action type, without ever
counting the action that would have confirmed the mechanism fired. The
counting took one grep against a dump already on disk. Verify the mechanism
fired BEFORE explaining why it underperformed -- otherwise the explanation is
unfalsifiable decoration on noise.

## Iteration 109 — squeak cheese sightings to pool vision cones — REJECTED

                          g_iter21        iter109
    benchmark wins        7/162           4/162
    early wipes           12/155 = 8%     15/158 = 9%
    close-spawn wins      4/42            2/42
    close-spawn wipes     12/38 = 32%     15/40 = 38%

**Mechanism confirmed first**, per the Iteration 108 retraction:

    bench_finalist__rift__botA   our RatSqueak 502,  bench_finalist 255

502 squeaks is not a mechanism that failed to fire. This is a real negative
and the third capability iteration to actually execute anything. Reverted.

Incidental finding: **the opponents squeak too** (255 in the same game), so
this is a channel serious bots use -- evidently not for what I used it for.

### Why pooling the cones made things worse

The premise was right and the consequence was not. Squeak radius 4 against a
90-degree vision cone really does mean a neighbour's sighting is new
information. But acting on it makes every rat within radius 4 head for the
*same tile*, and only the first arrival gets the cheese; the rest have spent
their turns walking to a tile that is now empty. Worse, the rats diverted are
exactly the ones that had no cheese in view -- i.e. the ones who were about to
`explore()` and find *new* cheese. So the change traded map coverage, which
compounds, for redundant convergence on cheese already found.

Note the fastest-loss list is byte-identical to baseline
(19,20,21,21,27,34,35,35): the opening is untouched, as expected for an
economic change, and the damage is all in the long games -- which is where the
three lost wins were.

That the shared array only Kings may write, and squeaks reaching only radius
4, both push toward *local* coordination is worth remembering: the engine
seems designed so that broadcasting a target to everyone nearby is a trap, and
whatever the opponents are squeaking, it is presumably something that does not
create a stampede.

### Capability audit scorecard

    becomeRatKing   Iter 106 VOID (unreachable)   Iter 107 real negative
    carryRat        Iter 108 VOID (barely fired)
    squeak          Iter 109 real negative

Four runs, two void, two genuine negatives, no accepts. The audit was still
worth doing -- it produced the only structurally new hypotheses of the session
after the mechanical seams were exhausted -- but the honest scoreline is that
none of the three unused capabilities paid off in its most obvious form.

## Analysis note — the far-map collapse is a TRAJECTORY, and it revives Iteration 103

Measured on `bench_finalist__corridorofdoomanddespair__botA` from the g_iter21
benchmark run (we are team1), taken as the baseline for Iteration 110:

    round   our cheese  our rats  their rats   our transferred  their transferred
    100        1098        20         16            120              220
    200         958        13         29            180              540
    300         773         7         23            200             1000
    400         553         6         18            200             1480
    500         123         7         14            300             1780

**Our cheese delivery is flat and theirs is linear.** Ours goes 120, 180, 200,
200, 300 across five hundred rounds -- *zero cheese delivered between rounds
300 and 400* -- while theirs climbs by roughly 4-5 per round throughout. By
round 400 they have delivered 1480 to our 200, a factor of seven.

**And the population crossover lands inside the blackout window.** At round
100 we lead 20 to 16. By round 200 we are at 13 and they are at 29. The
crossing happens between rounds 100 and 200 -- inside the rounds-100-to-399
window where `king_census` showed we build **zero** rats, because `builtCount`
is cumulative against `MAX_POPULATION` and the budget does not refresh until
`BUILD_WINDOW_ROUNDS = 400`.

### This overturns my withdrawal of Iteration 103

I withdrew Iteration 103 (shorten `BUILD_WINDOW_ROUNDS` 400 -> 150) on the
grounds that "on `peaceinourtime` we hold MORE rats than the opponent and lose
anyway", concluding population was not the far-map deficit. That was read off
a **single round-100 snapshot**. The trajectory says the opposite: round 100
is precisely the last moment we are ahead, and the deficit opens immediately
afterwards, in the window where we are structurally unable to replace a single
loss while the opponent nearly doubles its army.

A snapshot at the crossover point cannot distinguish "we are fine" from "we
are about to collapse". The same normalisation discipline already recorded for
replay totals -- compare rates over time, not endpoints -- applies to
population and income too, and I did not apply it.

Iteration 103's mechanism argument and its pre-flight arithmetic (~3 rats per
refresh at a round-150 window, against the unchanged 1000 reserve) stand
unchanged in the git history and should be re-run as **Iteration 111**, after
Iteration 110 resolves. Its withdrawal note is superseded by this entry.

## Iteration 110 — squeak-based dispersion — REJECTED; squeak steering CLOSED

                          g_iter21        iter110
    benchmark wins        7/162           5/162
    early wipes           12/155 = 8%     15/157 = 10%
    close-spawn wins      4/42            3/42
    close-spawn wipes     12/38 = 32%     15/39 = 38%

Behaviour clearly changed -- the fastest-loss list moved from
19,20,21,21,27 to 18,20,20,20,24 -- and Iteration 109 already established that
squeaks fire in bulk, so this is a real negative rather than a void. Reverted.

The pre-registered risk is exactly what happened: "dispersion could scatter
rats away from the King on close-spawn maps, which is where Iteration 102's
hard-won 8% wipe rate lives." Close-spawn wipes went 32% -> 38%.

**Both directions of the dose are worse than doing nothing:**

    squeak use in explore()        wins      early wipes
    converge on cheese (Iter 109)  4/162     9%
    none               (g_iter21)  7/162     8%
    disperse           (Iter 110)  5/162     10%

A peak at the current behaviour, so squeak-driven steering of `explore()` is
**closed in both directions** -- the same shape as the cat-engagement
threshold, where 60/30/10 gave 48.1/50/50 and the inherited value was already
optimal.

That is now twice in this session that a knob turned out to be at its optimum
already. It is worth stating the corollary plainly: the bot's *existing*
constants are not obviously mistuned, and the remaining wins are unlikely to
come from turning dials on behaviour that already exists. The one dial that
did move -- the trap ratio, Iteration 102 -- was one nobody had ever chosen,
inherited from a boolean rather than a decision. That is the signature worth
hunting: not "is this value optimal" but "was this value ever chosen at all".

`BUILD_WINDOW_ROUNDS = 400` has exactly that signature, and the trajectory
measurement above shows the deficit opening inside the window it controls.
That is Iteration 111.

## Iteration 111 — BUILD_WINDOW_ROUNDS 400 -> 150 — REJECTED, but the mechanism worked

                          g_iter21        iter111
    benchmark wins        7/162           6/162
    early wipes           12/155 = 8%     12/156 = 8%
    close-spawn wins      4/42            2/42
    far-map wins          3/120           4/120

**Mechanism check passed.** `king_census --window 100` on
`corridorofdoomanddespair`, the same game the trajectory came from:

    window       g_iter21 spawns      iter111 spawns
    100-199            0                   4
    200-299            0                   6
    300-399            0                   1
    total             25                  36

The three-hundred-round production blackout is gone. Iteration 103's original
mechanism argument was correct and its withdrawal was correctly overturned.

**And it bankrupted us anyway.** Same game, our treasury:

                    g_iter21    iter111
    round 300         773         328
    round 400         553          68    cheese-limited
    round 500         123           0

This is Iteration 39's failure mode returning -- a replacement burst draining
the treasury until the King starves -- which the task pre-registered as the
risk, on the belief that the unchanged `REPLACEMENT_RESERVE = 1000` would
prevent it.

### Why the reserve did not hold, which is the real finding

Six rats were built in rounds 200-299 while cheese fell 808 -> 328. The build
gate is `cheese - cost >= buildReserve`, so those builds are only possible if
`buildReserve` was **150, not 1000**. The only thing that lowers it is
Iteration 40's emergency override: when no allied Baby Rat is visible to the
King, the bar drops from `REPLACEMENT_RESERVE` back to `RESERVE`.

As the army decays the King increasingly sees no allies, the override fires,
and the reserve stops existing exactly when the shorter window has given
`builtCount` the headroom to spend. **The override was measured INERT at
48.1% on the mirror (Iteration 84) -- and it is inert only while
`BUILD_WINDOW_ROUNDS = 400` keeps `builtCount` pinned at the cap, so there is
nothing for it to unlock.** Change the window and the same dormant feature
becomes the thing that bankrupts the treasury.

That is a general hazard worth naming: **a feature measured inert is inert
*given the rest of the configuration*, not inert absolutely.** Iteration 84
explicitly said the override "should not be credited in any future
reasoning"; the correct reading is stronger, that it should not be assumed
harmless either, because an interaction can wake it up.

Two facts now point the same way -- far-map wins actually rose 3 -> 4, and the
blackout is genuinely fixed -- so the idea is not refuted. The untested
version is the window shortened *and* the override disabled, so the reserve
actually holds. That is Iteration 112.

## Iteration 112 — window 150 + emergency override disabled — HELD pending factor isolation

                          g_iter21        iter112
    benchmark wins        7/162           8/162    best of the session
    far-map wins          3/120           5/120
    close-spawn wins      4/42            3/42
    early wipes           12/155 = 8%     12/154 = 8%
    g_iter21 mirror       --              28/54 = 51.9%
    round-400 cheese      553             598

Every instrument is neutral or slightly positive except close-spawn wins.
The treasury problem from Iteration 111 is fixed outright -- round-400 cheese
went 68 -> 598, above even the baseline's 553, and the late bankruptcy is gone.

**Not accepted yet, because the mechanism check contradicts the hypothesis.**
`king_census` on the same `corridorofdoomanddespair` game:

    window        iter111 spawns    iter112 spawns    g_iter21 spawns
    100-199             4                 1                 0
    200-299             6                 0                 0
    300-399             1                 0                 0

With the reserve genuinely enforced at 1000, mid-game cheese hovers *below*
it (958 at round 199), so the extra refreshes buy almost nothing -- exactly
the risk the task pre-registered. **The blackout this iteration existed to
fix is still there.** One extra spawn cannot be what turned 7/162 into 8/162.

So the gain, if real, comes from the *other* factor: removing the emergency
override, which stops the treasury being drained late. Accepting the pair
would be banking a result while believing a story the measurement already
contradicts.

**Iteration 113 isolates it**: `BUILD_WINDOW_ROUNDS` back to 400, override
still disabled. If that alone reaches 8/162 the change is simpler and the
window is irrelevant; if it drops to 7 then the two genuinely interact and
Iteration 112 is the right form. Either way the +1 is at the resolution floor
of a ~4% instrument, so this also serves as a second look at a result I would
otherwise be accepting on one game -- the mistake Iteration 108 was rejected
for.

## Iteration 113 — override disabled ALONE — REJECTED, and it rescues Iteration 112

                          window 400 + override off   window 150 + override off
    benchmark wins             8/162                       8/162
    early wipes                12/154 = 8%                 12/154 = 8%
    close-spawn wins           3/42                        3/42
    fastest losses             19,20,21,21,27              19,20,21,21,27
    **g_iter21 mirror**        **20/54 = 37.0%**           **28/54 = 51.9%**

**Byte-identical on every benchmark counter and fifteen mirror points apart.**
That is the whole result, and it reverses the conclusion I was about to draw.

I ran this isolation because Iteration 112's mechanism check showed only one
extra spawn, so I believed the window was inert and the override removal was
doing all the work. On benchmarks that is exactly right -- the two arms cannot
be told apart. On the mirror they are not close.

**Why benchmarks cannot see it.** 91% of benchmark losses are King destruction
and every close-spawn loss is over before round 500, so a build window that
governs *replacement after round 150* rarely gets to matter. Mirror games run
long -- most reach round 2000 -- and there replacement capability is decisive.
This is the representativeness limit again, but pointing the other way than
usual: normally the mirror is the instrument that cannot see the effect
(0% early wipes); here it is the only instrument that can.

**What the pair actually does.** The emergency override was a crude
replacement valve: when the King saw no allied rats it dropped the reserve to
150 and rebuilt. Removing it alone deletes all late rebuilding, which the
mirror punishes at 37%. Shortening the window restores rebuilding properly --
on a reserve that genuinely holds at 1000, so the treasury survives (round-400
cheese 598 against Iteration 111's 68). The two constants are not separable
and the pair is the correct form.

So Iteration 112 is ACCEPTED as `g_iter22`, and this isolation run is what
justifies it. Had I accepted Iteration 112 without it, I would have banked the
right change for a reason the evidence contradicted; had I accepted the
simpler Iteration 113 on the identical benchmark score -- which was my stated
intention -- I would have shipped a 37% regression.

## Iteration 112 — ACCEPT RETRACTED before it landed: vs_old_bots reverses it

I had snapshotted `g_iter22` and was running the post-accept routine when the
vs-old-bots Gauntlet came back. It disagrees with everything else, and it
disagrees by more than the other instruments agreed:

    matchup            g_iter21        g_iter22 (Iteration 112)
    vs g_iter1         49/54 (91%)     45/54 (83%)     -4
    vs g_iter11        50/54 (93%)     44/54 (81%)     -6
    comparable subset  99/108 = 91.7%  89/108 = 82.4%  -9.3 points

Those two sub-scores are directly comparable -- same opponents, same maps, the
roster merely grew by `g_iter21`. Ten games is far outside the +-2 resolution
floor of a matchup sitting at ~90%, and it swamps the evidence in favour:

    benchmarks        7/162 -> 8/162        +1 game, at the resolution floor
    g_iter21 mirror   28/54 = 51.9%         +1 game, at the resolution floor
    vs_old_bots       99/108 -> 89/108      -10 games

`src/g_iter22/` deleted, working tree back to `g_iter21`.

### What I nearly did

Three instruments, two of them showing single-game moves I would normally call
noise -- and I was treating those two as the verdict because they were the
ones I had pre-registered. The one instrument that moved decisively was the
one I had not yet run. This is the same failure as the "four accepts, all
reverted" episode: accepting on the instruments that happen to be in front of
you, when a cheap one you have not run yet would reverse it.

The post-accept routine saved this by accident. `vs_old_bots` is listed as a
*post*-accept step -- something you run to record progress after deciding --
and it caught a bad accept only because the snapshot happened before the run
finished. **It should be a pre-accept gate, not a post-accept record**, at
least whenever the benchmark and mirror moves are both within one game.

### What the result actually means

Removing the emergency override costs games against weaker, longer-lived
opponents, and shortening the build window does not fully compensate. That is
consistent with the override's real function -- a crude replacement valve for
a collapsing army -- being worth more in exactly the games our army has time
to collapse in. Against benchmarks those games mostly end early, which is why
the benchmark set could not see the cost, and the mirror is a 50/50 matchup
where both sides suffer it equally.

So all three of Iterations 111, 112 and 113 are rejected, and the emergency
override stays. Its Iteration 84 measurement (48.1%, "inert") remains the best
single-number description of it on the mirror, but the honest summary is now:
inert on the mirror, mildly positive on benchmarks to remove, and clearly
load-bearing against weaker opponents in long games.

## Iteration 114 — widen the override trigger to <3 visible allies — REJECTED at the gate

    vs_old_bots comparable subset (g_iter1 + g_iter11)
        g_iter21    99/108 = 91.7%
        iter114     76/108 = 70.4%      -23 games

Failed the pre-accept gate outright, so benchmarks were not run. Reverted.

**The dose curve now peaks exactly at the current value:**

    override fires when...            vs_old_bots
    never                (Iter 113)   89/108 = 82.4%
    0 allies visible     (g_iter21)   99/108 = 91.7%
    fewer than 3 visible (Iter 114)   76/108 = 70.4%

Both directions are worse, and the wrong direction is much worse than the
missing one. `compare_gauntlets.py` shows why: wins that used to grind to the
round limit now end early -- `jail` r2000 -> r820, `uneruesansfin` r1840 ->
r925, `minimaze` r2000 -> r1074, `dirtpassageway` r2000 -> r770. That is
starvation, the Iteration 111 bankruptcy in a more severe form.

The mechanism is clear in hindsight. Rats are deliberately dispersed to hunt
cheese, so a King almost never has three of them inside its radius-5 vision.
"Fewer than 3 visible" is therefore true nearly always, `buildReserve`
collapses to 150 permanently, and the King builds until the treasury is gone.

**The override's value comes precisely from being RARE.** It is a last-resort
valve, and Iteration 40's "no allied Baby Rat visible at all" is not a
carelessly-picked magnitude but the correct one -- the condition has to be
near-impossible for the reserve to mean anything the rest of the time.

### The "constants nobody chose" heuristic gave a false positive here

Iterations 102, 105 and 110 supported a rule: values that fell out of an
implementation are worth testing, values with reasoned comments are already
optimal. This threshold looked like the former -- Iteration 40 described a
mechanism ("when no allied Baby Rat is visible at all") without ever arguing
a magnitude -- and it is nonetheless optimal, by a wide margin in both
directions.

So the heuristic identifies *candidates worth a run*, not *values that will
move*. Its record is now one hit (the trap ratio, +2 benchmark wins) and one
expensive miss. That is still a reasonable hit rate for hypothesis generation,
but it should not be stated as though an unchosen constant is probably wrong.

## Iteration 115 — batch cheese deliveries — REJECTED, and it found the real bottleneck

                          g_iter21        iter115
    benchmark wins        7/162           3/162
    early wipes           12/155 = 8%     15/159 = 9%
    close-spawn wins      4/42            1/42

**Mechanism fired, but weakly, and that is the informative part:**

    corridorofdoomanddespair rounds 1-400   baseline   iter115
        our CheesePickup                       10        11
        our CheeseTransfer                     10         8
        our transfer amounts                 10x20     6x20, 1x40, 1x60

Batching did happen -- a 40 and a 60 appeared where before every single
transfer was exactly 20. But **pickups did not move: 10 to 11, against the
opponent's 65.** Hauling was never the constraint. We do not collect little
because we deliver inefficiently; we deliver little because we barely collect.

So the throughput arithmetic in the task was correct and irrelevant. Batching
optimises the wrong half of the loop, and it costs real games by leaving
slower, more valuable rats in the field.

### What the rats are actually doing -- full action census, rounds 1-400

    action           us    them
    CheesePickup     11      94
    StunAction       39       0
    TriggerTrap      26      13
    DieAction        20       5
    RatAttack        61      78
    RatSqueak         0     600
    ThrowRat          0      39
    PlaceTrap        18      36

**We are stunned thirty-nine times and they are stunned zero times.**
`RAT_TRAP` stuns for 30 rounds (RULES.md), so that is on the order of 1170
rat-rounds frozen out of roughly 8000 available -- before counting the 42
damage per trigger, and the 20 deaths against their 5.

Our rats do not find cheese because they spend the mid-game stunned, damaged
and dead in the opponent's trap field. That is upstream of every economic
hypothesis this session has tested: squeak convergence (Iteration 109),
squeak dispersion (Iteration 110), and now batching (115) all tried to make a
collection loop more efficient that is being physically interrupted.

Two other columns worth recording: they squeak 600 times to our 0, and they
throw rats 39 times to our 0 -- both capabilities this session tested in their
most obvious form and rejected. Whatever they use them for, it is not what I
tried.

### Standing caution before the obvious next move

The obvious response is trap avoidance, and my record on it is 0 for 2 --
Iteration 69 (learned danger zones, 16% wipes vs 16% control) and Iteration
100 (geometric threat-direction steering, 14% vs 14%). Both were aimed at the
EARLY RUSH on close-spawn maps. This measurement is a different regime: far
maps, rounds 1-400, a trap field between us and the cheese. That is a genuine
difference and not an excuse, but a third attempt needs to be judged on the
counter measured here -- our StunAction and TriggerTrap counts -- not on the
early-wipe rate, and it should be rejected outright if those do not move.

## Iteration 116 — Baby Rat field traps — VOID (gate never opened)

Byte-identical to `g_iter21` on every counter: 7/162 wins, 12/155 wipes,
4/42 close-spawn wins, the same fastest-loss list and the same wipes-by-map.
Mechanism check:

    corridorofdoomanddespair rounds 1-400
        our PlaceTrap:  18, ALL of them RAT_KING -- Baby Rats placed zero
        our cheese:     1098 @100,  958 @200,  773 @300,  553 @400

The gate required `getGlobalCheese() > 1000`. Our treasury is below 1000 from
round ~150 onward, so the condition was false for essentially the whole game,
and it additionally had to coincide with a rat being 8+ tiles from the King.

**I had this trajectory in front of me.** It is recorded in this same log as
the Iteration 110 baseline -- "1098, 958, 773, 553, 123" -- and I set a
threshold above it anyway. The pre-flight I do for *doses* (does the arm
change the evaluated condition?) I did not do for a *gate*: check the gate
against measurements already in the log before running.

That is the third void of the session from the same root -- Iteration 106
(no rat king may be in the 3x3, a precondition I had read), Iteration 108
(the 90-degree cone plus adjacency plus proximity to our King), and now this.
Every one was a condition I could have evaluated against existing data in
under a minute.

Not reverted: the code is inert as written, and the mechanism is still
untested. Iteration 117 lowers the gate to 400 -- comfortably under the
mid-game treasury while still refusing to trade the last of it for traps.

## Iteration 117 — field traps, gate lowered to 400 — VOID AGAIN (wrong target tile)

Byte-identical to `g_iter21` a second time. The cheese gate was fixed and a
different precondition was blocking it:

    assertCanPlaceTrap (RobotControllerImpl:339)
        if (gameWorld.getRobot(loc) != null)
            throw "Can't place trap on an occupied tile!"

I placed at `rc.getLocation()`. A rat occupies the tile it stands on, so
`canPlaceRatTrap(rc.getLocation())` can **never** return true. The call was
dead on arrival in both Iteration 116 and 117.

The same assertion also shows non-kings get `BUILD_DISTANCE_SQUARED = 2`, so
only *adjacent* tiles are legal targets at all -- our own King's
`findTrapLocation` has always scanned candidate locations for exactly this
reason, and I wrote a fresh call site instead of following it.

### Four voids, one root cause

    Iteration 106  becomeRatKing   no rat king may be in the 3x3
    Iteration 108  carryRat        90-degree cone + adjacency + near our King
    Iteration 116  placeRatTrap    cheese gate above our own measured treasury
    Iteration 117  placeRatTrap    target tile occupied by the placing rat

Every one is a precondition sitting in plain sight in the engine source or in
this log's own measurements, and every one cost a full 162-game run to
discover. The mechanism check catches them *after* the fact; what is missing
is the same check *before*. Concretely, before running any new engine call:
read its `assertCanX`, list every clause, and confirm each one against the
code being written or a number already in this log.

Iteration 118 fixes the target: place on the tile behind the rat -- where a
pursuer walks -- falling back to any legal adjacent tile, the same
scan-don't-assume pattern the King has always used.

## Iteration 118 — Baby Rat field traps, working at last — REJECTED on price

                          g_iter21        iter118
    benchmark wins        7/162           1/162
    early wipes           12/155 = 8%     15/161 = 9%
    close-spawn wins      4/42            1/42

**The mechanism finally fired, and it did exactly what it was designed to do:**

    our PlaceTrap        18 (all RAT_KING)  ->  18 RAT_KING + 22 RAT
    their TriggerTrap    13                 ->  22          (+9 caught)

Third time was the charm on reachability, and the idea is still wrong.

**My pre-registered risk was the wrong one.** I guarded against field traps
cannibalising the King's ring through the shared 25-trap cap; the ring is
untouched at 18. The actual cost is cheese: 22 traps at 20 each is **440
cheese**, taken from a treasury that runs 1098 at round 100 down to 553 at
round 400. That is most of our mid-game cash, and at a rat cost of 40-60 it is
seven to eleven rats not built.

So the trade is 440 cheese for 9 extra enemy trap triggers. Each trigger is 42
damage and a 30-round stun on one enemy rat -- nowhere near the value of ten
rats. Losing six benchmark wins for that is entirely consistent.

**Priced as a dose, a smaller version does not look promising either.** Scaling
the per-rat cooldown from 100 to 400 rounds would give roughly a quarter of the
traps: ~110 cheese for ~2 extra triggers. That is a better ratio only if the
non-linearity runs the right way, and nothing here suggests it does. Not
queued.

### What this closes

The trap asymmetry that the Iteration 115 census exposed -- they place 36 to
our 18, we are stunned 39 times to their 0 -- is real and is **not**
addressable from our side. Avoidance is impossible because `getMapInfo` only
reports our own team's traps, so enemy traps cannot be sensed at any cost;
Iterations 69 and 100 failed for that reason. Symmetric retaliation is
affordable only by giving up the economy we were trying to protect. The
opponent can run a dense trap field because their economy supports it (94
pickups to our 11); ours does not.

That inverts the causal story I started with. I read the traps as the cause of
our weak economy. The pricing says the economy is what lets a team afford
traps, and we cannot buy our way out of an income deficit with the income we
do not have.

## Iteration 119 — camp the cheese mines — REJECTED (mechanism worked, price too high)

                          g_iter21        iter119
    benchmark wins        7/162           1/162
    early wipes           12/155 = 8%     13/161 = 8%
    close-spawn wins      4/42            1/42

**The mechanism fired and did exactly what it was supposed to do:**

    our CheesePickup        10  ->  16
    our CheeseTransfer      10  ->  15
    our cheeseTransferred   120/180/200/200  ->  200/300/300/300
    their CheesePickup      66  ->  55

A real ~50% economy improvement, with the opponent's collection pushed down as
we contested the mine. And six benchmark wins gone.

Our population went 21/12/5/4 against the baseline's 20/13/7/6, so the rats
camping the mine were rats not defending, and close-spawn wins fell 4 -> 1 --
exactly the guard the task pre-registered.

### The pattern across the last three iterations is the finding

    Iteration 115  batch deliveries   mechanism fired weakly   7 -> 3
    Iteration 118  field traps        mechanism fired fully    7 -> 1
    Iteration 119  camp mines         mechanism fired fully    7 -> 1

Three consecutive changes that did precisely what they were designed to do,
each verified in the replay, and all three lost badly. Iteration 118 caught 9
more enemy rats for 440 cheese; Iteration 119 raised income 50% and lost the
army; Iteration 115 batched hauling and slowed the field.

**`g_iter21`'s allocation of Baby Rat turns is on a narrow peak.** Every one
of these reallocates rat time -- to carrying, to trapping, to loitering on a
mine -- and every one costs more than it returns. That is a much stronger
statement than any single rejection, and it is consistent with the earlier
finding that the tuned constants (cat threshold, squeak steering, override
threshold) all measured already-optimal in both directions.

It also reframes the economy gap. Our rats collect an eighth of what the
opponent's do, and this iteration proves that is *not* because they fail to
camp productive ground -- camping works, and still loses. The deficit is
upstream of collection policy: they simply have more rats alive doing it (24
against our 5 by round 300), which is the population collapse traced in the
blackout work, on a treasury we cannot grow because we have no income.

That circularity is the actual problem, and none of income, traps, batching or
camping breaks it from inside the rat loop.

### Correction — "DieAction us 20, them 5" was counted wrong

The Iteration 115 census reported `DieAction us 20, them 5` and concluded "we
lose 4x more units". That counted by **actor**, and the actor of a kill is
usually the CAT (`team0`), not the victim's team. Recounted by **target** on
the same baseline replay, rounds 1-400:

    our rats die     12 to traps (actor == victim, the self-attributed form)
                      7 to cats
                      1 to their King            = 20 total
    their rats die   24 to cats
                      5 to traps                 = 29 total

**They lose MORE rats than we do, not fewer** -- 29 against our 20 -- and they
still hold 24 alive at round 300 against our 5. Survival is not our problem.

The gap is production: they spawn **41** rats in those 400 rounds, we spawn
**25** and stop, because `builtCount` is cumulative against
`MAX_POPULATION = 25` and the window does not refresh until round 400. They
fund that with 1360 cheese of income against our 300.

So the entry point into the income/population circle is the BUILD CONSTRAINT,
not survival and not collection policy. Iteration 119 already proved
collection policy is not it -- camping raised income 50% and still lost -- and
this correction removes survival as a candidate too.

What that leaves is the pair `MAX_POPULATION` / `REPLACEMENT_RESERVE`, and the
honest position is that both have been tested and reverted (Iterations 88/90
raised the cap, 92 decayed the reserve) -- but all three were judged before
this session had the close-spawn split, the early-wipe counter, or the
vs_old_bots pre-accept gate. Iteration 112 also showed the window alone cannot
help while the reserve holds at 1000 and mid-game cheese sits at 958 below it.

Re-testing them on the current instrument set is the remaining lead. It is
also the one most likely to fail the same way, since we are poor precisely
because we have no income, and building more rats we cannot pay for is how
Iteration 111 bankrupted the treasury.

## Iteration 120 — REPLACEMENT_RESERVE 1000 -> 500 — REJECTED at the gate

    benchmark wins          7/162 -> 8/162
    early wipes             8% -> 8%
    close-spawn wins        4/42 -> 4/42        (guard held, unlike Iteration 112)
    vs_old_bots subset      99/108 = 91.7%  ->  90/108 = 83.3%     -9 games

Benchmarks up one, vs_old_bots down nine. Identical shape to Iteration 112, and
the gate caught it identically. Reverted from the `g_iter21` snapshot with the
strip-diff verified empty before staging.

**The mechanism also failed as pre-registered.** Spawns in the rounds 100-399
windows are still 0/0/0 -- the reserve was never the only blocker, because
`builtCount` remains capped at `MAX_POPULATION` until the window refreshes at
400. What the lower reserve actually changed was post-refresh spending: 10
spawns in window 400-499 against the baseline's 7, with cheese falling to 168
where the baseline held 553. So the +1 benchmark win came from a different
effect than the hypothesis, and the treasury was heading toward Iteration
111's bankruptcy.

### The reserve is at its optimum, and this is now the fifth confirmation

    reserve setting                        instrument
    0        (Iteration 87 ablation)       mirror 25.9%  -- validated, large
    500      (Iteration 120)               vs_old_bots 83.3%
    1000     (g_iter21)                    vs_old_bots 91.7%

Every relaxation of the treasury guard costs long games, and this is now the
fifth iteration to establish it from a different direction: Iteration 111
(shorter window, bankruptcy), 112 (window + override off, -10), 113 (override
off alone, mirror 37%), 114 (override fired earlier, -23), and now 120 (lower
reserve, -9).

**Stated plainly: the King's spending policy is fully converged.** Five
independent perturbations, all negative, on three different constants. The
economy is not losing because the King mismanages cash; it is losing because
income never arrives, and the reserve is what stops a starved treasury from
being spent into nothing.

### Where that leaves the session

Everything reachable from inside the bot's own decision loop has now been
tested. Rat-turn allocation is on a narrow peak (Iterations 115, 118, 119 all
did exactly what they were designed to do and each lost six wins). King
spending is converged (five perturbations). Collection policy is not the
constraint (camping raised income 50% and still lost). Survival is not the
constraint (they lose 29 rats to our 20). The tuned constants measure optimal
in both directions (cat threshold, squeak steering, override threshold,
reserve).

The one thing that remains true and unexplained is the raw income ratio: 94
pickups to our 11, from two mines producing ~2000 cheese, of which they take
~1880. They achieve that with more rats alive, funded by that income. Nothing
inside the rat loop or the King's spending rules breaks the circle, and I have
not found the entry point.

## Iteration 121 — steer explorers off the map edges — REJECTED, and it explains the whole plateau

                          g_iter21        iter121
    benchmark wins        7/162           2/162
    early wipes           12/155 = 8%     15/160 = 9%
    close-spawn wins      4/42            2/42

**The mechanism worked better than anything else tried this session:**

    our CheesePickup        10  ->  20        (doubled)
    their CheesePickup      66  ->  56
    our cheeseTransferred   120/180/200/200  ->  280/380/380/380   (+90%)

and the tracked edge-walker `id10353`, which previously marched
(19,12)(19,17)(19,23)...(19,50) along the boundary, now goes
(10,2)(12,12)(13,18)(16,23) through the interior. Exactly as designed.

**And the army is annihilated:**

    alive     baseline 20/13/7/6     iter121  11/0/0/1
    deaths    baseline 12 traps, 7 cats, 1 king = 20
              iter121  19 traps, 6 cats = 25, with zero rats alive from round 200

### What this reveals about the plateau

The edge-walking was not merely wasted motion. It was **inadvertently keeping
our rats out of the opponent's trap field.** `corridorofdoomanddespair` is a
20-wide corridor with both mines at x=9; the traps are in that central column,
because that is where cheese and traffic are. Rats hugging x=19 collected
nothing and survived. Rats sent to the middle collect twice as much and are
dead by round 200.

That closes the loop this session has been circling:

    we collect little            because our rats avoid the centre
    our rats avoid the centre    by accident, via a navigation defect
    fixing the defect            walks them into a trap field
    the trap field is unsensable (getMapInfo is team-filtered)
    it cannot be out-trapped     (Iteration 118: 440 cheese for 9 triggers)
    it cannot be walked through  (this iteration: army gone by round 200)

**The bot's 7/162 is achieved by passive avoidance, not by playing well.**
Every iteration that made a rat more productive -- batching (115), field traps
(118), mine camping (119), interior navigation (121) -- did precisely what it
was designed to do, was verified in the replay, and lost five or six benchmark
wins, because productive rats are rats in contested space and we lose contested
space.

So the binding constraint is not economy, navigation, collection policy,
survival rules or King spending. It is that **we cannot hold ground against
these opponents**, and every economic gain is priced in ground we cannot hold.
That is a combat-strength problem, and it is upstream of everything measured
since Iteration 102.

Recorded as the honest state of the search rather than a to-do: I do not have
a tested route to more combat strength, and four consecutive
mechanism-verified improvements losing six wins each is strong evidence that
the remaining gap is not reachable by making the existing rats do more.

## Iteration 122 — trap dose 2 -> 3 — REJECTED; the trap curve is closed at its peak

                          g_iter21        iter122
    benchmark wins        7/162           3/162
    early wipes           12/155 = 8%     14/159 = 9%
    close-spawn wins      4/42            1/42

**The full curve, now complete on both sides:**

    King trap density                    wins     early wipes
    1 trap per 2 builds  (Iter 101)      5/162    18%
    1:1                  (g_iter20)      5/162    14%
    2:1                  (Iter 102)      7/162     8%    <- accepted, peak
    3:1                  (Iter 122)      3/162     9%

Four points, a clean interior maximum at the accepted value. Iteration 102 was
not just an improvement, it was the optimum, and pushing the one direction
that ever worked confirms there is nothing further along it.

### Every tunable in this bot is now at its measured optimum

    parameter                  peak at            established by
    King trap ratio            2 traps per build  Iterations 101, 102, 122
    cat disengage threshold    30 HP              Iterations 103, 104, 105
    squeak steering            none at all        Iterations 109, 110
    emergency override gate    0 visible allies   Iterations 113, 114
    REPLACEMENT_RESERVE        1000               Iterations 87, 120
    BUILD_WINDOW_ROUNDS        400 (given the above) Iterations 111, 112, 113

Six parameters, each tested in both directions where a direction existed, each
peaking where it already sat. Combined with the structural results -- rat-turn
reallocation on a narrow peak (115, 118, 119, 121), the capability audit's four
runs with two voids and two negatives (106-109), and the contested-ground
diagnosis -- `g_iter21` is a genuine local optimum of this bot's design.

### Honest position

Twenty-one iterations since `g_iter21` was accepted, one accept and twenty
rejections, and the rejections are informative rather than noisy: nearly all
had their mechanism verified in the replay and lost anyway. The remaining gap
to these opponents is not reachable by tuning what exists or by making the
existing rats do more of anything.

What would be required is a different bot -- one that can hold contested
ground -- and that is a rewrite of the combat model, not an iteration on it.
I am recording that rather than continuing to spend 162-game runs
re-confirming a converged surface.

## Iteration 123 — turn to face an unseen attacker — REJECTED

                          g_iter21        iter123
    benchmark wins        7/162           5/162
    early wipes           12/155 = 8%     13/157 = 8%
    close-spawn wins      4/42            2/42

**The reflex fired and did the opposite of what it was for:**

    our RatAttack   61  ->  48      (the bar said it must RISE)
    our pickups     10  ->  10
    our deaths      20  ->  25      (14 traps, 9 cats, 2 enemy rats)
    alive           20/13/7/6  ->  20/10/0/1

The defect it targeted is real and engine-verified: a Baby Rat's 90-degree cone
means `senseNearbyRobots` cannot report an enemy behind it, and
`attackNearestHostile`'s `if (!rc.canAttack(loc)) continue;` skips anything
outside that cone, so a rat bitten from behind never turns and never fights
back. Fixing it still made things worse.

**Why: the trigger conflates three very different sources of damage.** "Health
dropped and nothing hostile is in view" is true for an enemy rat behind us, but
it is also true for a cat scratch (20 damage, and cats are worth fleeing, not
facing) and for a trap (42 damage plus a 30-round stun, where there is no
attacker to face at all). Most of our unseen damage is the latter two -- 14
trap deaths and 9 cat deaths against 2 to enemy rats -- so the reflex mostly
spent turns spinning in place next to a cat or while stunned. Cat deaths rose
7 -> 9 and trap deaths 12 -> 14 precisely because turning replaced fleeing.

The attack count falling rather than rising is the clean tell: the turn
consumes the turn (`return` after `rc.turn`), so the reflex bought no extra
bites and cost the escapes it displaced.

**A narrower version is conceivable** -- trigger only when the damage is 10,
the exact `RAT_BITE_DAMAGE`, which would exclude cat scratches (20) and trap
hits (42). I am not queuing it: the same run shows only 2 of 25 deaths come
from enemy rats at all, so the addressable population is about 8% of our
losses, and the reflex would have to be nearly free to pay for itself. That is
a much smaller prize than the mechanism seemed to promise before it was
measured.

This was the session's one genuine combat-model change rather than an economy
or tuning change, and it does not overturn the plateau diagnosis: our rats are
not dying to enemy rats they could have fought, they are dying to cats and
traps.

## Iteration 124 — cat-approach ablation re-measured on BENCHMARKS — REJECTED; cat question closed

                          g_iter21        iter124
    benchmark wins        7/162           4/162
    early wipes           12/155 = 8%     14/158 = 9%
    close-spawn wins      4/42            1/42

The pre-registered PRIMARY was "benchmark wins > 7/162". It fell to 4, so the
argument collapses exactly as that task's RISK line said it would, and the
pre-commitment I made to override the vs_old_bots gate for this change class
was never needed -- benchmarks rejected it on their own.

**Iteration 63's result does not reproduce at this baseline.** That iteration
measured benchmarks 5/162 -> 7/162 for essentially this change, which was the
entire reason to revisit a decision Iteration 103 had already made on the
mirror. At `g_iter21` the same idea is worth -3, not +2.

**And the mechanism worked, strictly, on everything it aimed at:**

    our RatAttack        61  ->  34      (fell, as the bar required)
    cat deaths            7  ->   2
    total rat deaths     20  ->  18
    our catDamage @400  156  -> 214      (rose -- Iteration 83's free bite is
                                          kept, and surviving rats use it more)

Fewer rats die, fewer die to cats specifically, and we end up with MORE cat
damage than when we were charging them. Every intermediate quantity moved the
right way and the bot lost three games anyway.

### The cat question is now closed on four measurements

    Iteration 103  ablate approach       mirror 21/54 = 38.9%
    Iteration 104  disengage at 60 HP    mirror 26/54 = 48.1%
    Iteration 105  disengage at 10 HP    mirror 27/54 = 50.0%
    Iteration 124  ablate approach       benchmarks 4/162 vs 7/162

Both directions of the threshold, on both instrument families. The inherited
policy is optimal and the Iteration 63 exception was baseline-specific.

### What this adds to the plateau picture

This is the cleanest example yet of the pattern that now defines this bot: a
change that improves survival, improves the score term it was supposed to
trade away, and still loses. It is the seventh consecutive
mechanism-verified change to do so (115, 118, 119, 121, 122, 123, 124).

I no longer think the remaining gap is described by any single quantity I can
name and move. Every local quantity I have measured -- deaths, cat damage,
cheese income, pickups, trap exchanges, population, treasury -- has been
improved in isolation by some iteration, and every one of those iterations lost
games. That is the signature of a bot whose behaviours are jointly tuned and
individually non-decomposable, which is a coherent thing for a converged local
optimum to be.

## Instrument maintenance — archetype staleness slipped the guard a THIRD time

Checked after Iteration 124, prompted by the standing note that synthetic peers
go stale silently. Both are behind:

    src/bot               1231 lines   modified 09-04 06:46
    pure_cooperator        950 lines   modified 09-02 23:54   gap 22%
    immediate_defector     961 lines   modified 09-02 23:54   gap 21%

They were last synced before Iterations 99 and 102 were accepted, so they are
missing two accepted changes. **The line-count guard did not fire because the
gap is 21-22% and its threshold is 25%.**

**Line count is structurally the wrong test**, which is why this keeps
happening. An archetype legitimately *deletes* code: `pure_cooperator` must not
place rat traps at all, because `GameWorld.triggerTrap` calls
`backstab(robot.getTeam().opponent())` -- the TRAP'S OWNER initiates the
backstab when an enemy steps on it -- so a trap-laying bot is not a pure
cooperator. Those legitimate deletions sit in the same number as genuine drift
and mask it.

Added a second, date-based check to `tools/gauntlet.sh`: warn if an archetype
is older than the newest `src/g_iterN/`. A snapshot only appears when an
iteration is accepted, so an older archetype is missing at least one accepted
change no matter how the line counts land. Verified firing:

    !! WARNING: pure_cooperator is older than g_iter21 --
    !! it is missing at least one accepted iteration.

**No measurement this session was corrupted** -- every run specified
`OPPONENTS` explicitly and none used the peer default -- but the next peer run
would have been inflated.

**Not re-syncing them right now, deliberately.** A correct re-sync is copy
`src/bot/` and then re-apply the policy edits, and getting that wrong
corrupts the instrument silently, which is worse than a flagged-stale one. The
deltas, now identified for whoever does it:

    pure_cooperator      `desperate = false`; no King trap block
    immediate_defector   `LEASH_RADIUS_SQUARED = 100` (turtles near its King);
                         no King trap block

## Method note — a single 3-game benchmark move is 1.2 sigma, not a verdict

    binomial sd at p = 7/162, n = 162:  2.59 games
    a 3-game drop:                      1.2 sigma

The Gauntlet is deterministic, so this is not sampling noise; it is the scale
on which a behaviour change reshuffles which near-threshold games fall which
way. Either way, **I have been reporting individual 3-game drops as though
they settled something, and they do not.**

What does settle it is the consistency. Wins after each of the seven
mechanism-verified changes: 3, 1, 1, 2, 3, 5, 4 -- every one below the
baseline 7. If a neutral change reshuffled symmetrically, seven of seven in
one direction has p = 0.008.

So the conclusion that `g_iter21` is a local optimum is well supported, and it
is supported by the *pattern*, not by any single run. Individual rejections in
this session should be read as weak evidence that happens to point the same
way, which also means any one of them could be revisited cheaply if a reason
appeared.

## Archetypes re-synced, and a clean peer baseline at last

Both synthetic peers rebuilt from `src/bot/` with their policy edits re-applied,
then verified by comment-stripped diff. The ONLY remaining differences are the
intended ones:

    pure_cooperator      desperate = false (King and Baby Rat)
                         Baby Rat gate `if (!rc.isCooperation())`
                         no King trap block
    immediate_defector   LEASH_RADIUS_SQUARED = 100 + turtle block
                         desperate = true, gate `if (true)`
                         no King trap block

No stray drift in either. The date-based staleness warning no longer fires.

**Clean peer baseline, first in this session:**

    overall                60/108 = 55.6%
    vs pure_cooperator     24/54  = 44%
    vs immediate_defector  36/54  = 67%

### We lose to pure_cooperator, which is our own bot minus traps and backstab

44% against a strictly simpler variant of ourselves is worth recording. A
plausible mechanism, from the engine: `GameWorld.triggerTrap` calls
`backstab(robot.getTeam().opponent())`, so **our own trap ring initiates the
backstab** as soon as an enemy rat steps on one. That flips scoring from
cooperation weights (catDamage 0.5, kings 0.3) to backstab weights (kings 0.5,
catDamage 0.3). With both sides holding exactly one King the kings term ties,
so we trade away the half-weighted term we were contesting and gain a tied one.

`pure_cooperator` never triggers that flip, keeps catDamage at 0.5, and most
of these games run to round 2000 where points decide.

**This does not argue for removing the trap ring.** Iteration 102 measured it
as the single accept of this session on the benchmark set -- early wipes
14% -> 8%, wins 5 -> 7 -- because tournament opponents rush the King and traps
stop that. It is the same representativeness split seen throughout: traps buy
survival against aggressive opponents and cost scoring weight against
cooperative ones, and the benchmark set is the one that resembles a real
tournament.

What it does suggest is that the auto-backstab side effect of trapping has
never been examined on its own terms. Logged as an observation, not queued: any
change here would be judged primarily on benchmarks, where the ring is already
validated in both directions (Iterations 101, 102, 122).

### Correction — "our trap ring initiates the backstab" is WRONG in both regimes

I wrote that last entry from the engine signature alone and never checked a
replay. Instrumented `ReplayDump` to print cooperation transitions and checked
both regimes. The claim fails in each.

**Benchmarks -- the backstab is THEIRS, not ours.**
`bench_finalist__corridorofdoomanddespair__botA`, cooperation flips at round 46:

    round 46 id12334(team1,RAT) TriggerTrap
    round 46 COOPERATION -> false

That is OUR rat stepping on THEIR trap. `triggerTrap` calls
`backstab(robot.getTeam().opponent())` where `robot` is the *triggering* robot,
so the credit goes to the trap's owner -- them. Our ring did not cause it.

**Peers -- cooperation NEVER ends.**
`pure_cooperator__safelycontained__botB` runs the full 2000 rounds with
cooperation true throughout. Our trap ring flipped nothing, because
`pure_cooperator` never walks into the ring around our King.

### What actually decides a peer game, with the formula

`GameWorld.setWinnerIfMorePoints` scores each team as a PROPORTION of the
match total, using **`cheeseTransferred`** (cumulative deliveries), not held
cheese:

    points = cat_w*100*share(catDamage) + king_w*100*share(kings)
                                        + 0.2*100*share(cheeseTransferred)
    cooperating: cat 0.5 / king 0.3 / cheese 0.2
    after backstab: cat 0.3 / king 0.5 / cheese 0.2

On that peer game:

    catDamage          0 vs 0        -> share 0 for BOTH; the 0.5 term is DEAD
    kings              1 vs 1        -> 15 points each
    cheeseTransferred  24510/47615   -> them 10, us 9   (int truncation)
    total              them 25, us 24 -- decided by ONE point

So the 0.5-weighted term contributed nothing to either side, the 0.3 term tied,
and the entire game turned on a 6% gap in cheese delivered.

**The likely cause is still the trap ring, but through production, not
scoring.** At `TRAPS_PER_BUILD = 2` the King spends two of every three opening
actions laying traps instead of building rats, so we field fewer collectors and
deliver ~6% less over 2000 peaceful rounds. That is the same trade Iteration
102 measured as strongly positive on benchmarks (wipes 14% -> 8%, wins 5 -> 7),
where opponents rush the King and the ring earns its cost.

Still not queued as a change: benchmarks are the representative instrument and
the ring is validated there in both directions (Iterations 101, 102, 122).
Recorded because the previous entry's mechanism was simply wrong, and because
the scoring formula is worth having written down exactly.

## Pre-flight — a late second Rat King cannot win the point games (idea killed without a run)

The scoring work suggested an obvious play: kings tie 1-1 in point-decided
games for 15 points each, so a second King would take the share to 2/3 and swing
~10 points against margins measured at ONE point. `RAT_KING_CUTOFF_ROUND` is
1200 with a cap of 2 after it, so a game alive at round 1250 is heading to a
points finish and could afford the 7 rats that Iteration 107 could not afford at
round 30.

**Checked the actual numbers first. The premise is false in two ways.**
`bench_finalist__minimaze__botA`, one of the 14 benchmark games that reach round
2000 (we are team1, cooperation already false so weights are kings 0.5 /
catDamage 0.3 / cheese 0.2):

    round 2000        us      them     points
    kings              1        2      16  vs  33
    catDamage       1764     3460      10  vs  20
    cheeseTransferred 5385   10535       6  vs  13
    aliveBabies        7       55
    TOTAL                             32  vs  66

**1. Kings do not tie in these games -- `bench_finalist` already has two and we
have one.** The tie I generalised from the peer game does not hold against the
opponent that actually reaches scoring. So the term is not a free 15 apiece; it
is already 16 vs 33, our single largest deficit.

**2. Even winning that term does not close the gap.** Upgrading would take our
share from 1/3 to 2/4, worth about +9 points, and would consume all 7 of our
surviving rats to do it. We lose by 34. We are behind roughly 2:1 on every
term simultaneously.

So the idea is rejected by arithmetic, at the cost of one replay dump rather
than a 162-game run. Recording it because the reasoning that produced it --
"kings tie, so a second King is a 10-point swing" -- was sound given the peer
game and simply did not survive contact with the games it was aimed at.

**What the numbers do show:** the point-decided games are not close and are not
a separate winnable pocket. Seven rats against fifty-five is the same
population collapse traced throughout, arriving at the scoring screen instead
of at a destroyed King. That is consistent with, and adds nothing new to, the
plateau diagnosis.

## Iteration 125 — cheese-gated population cap (Iteration 88 re-test) — REJECTED

    bar                          result                    verdict
    PRIMARY  g_iter21 mirror     36/54 = 66.7%  (+9)       pass, strongly
    GATE     vs_old_bots subset  97/108 = 89.8% (-2)       FAIL (bar was 91.7%)
    GUARD    benchmarks          5/162          (-2)       FAIL (bar was >= 7)
    GUARD    early wipes         12/157 = 8%               pass
             close-spawn wins    4/42 -> 3/42

**The mechanism worked, and through a path I did not predict.** Window 0 stays
capped at 25 because the treasury falls under the 1200 gate partway through it.
The gain is afterwards -- with the ceiling at 40, the King resumes building
whenever cheese pops back over 1200:

    window        spawns (baseline 0/0/0)    alive
    100-199             4                     27
    200-299             6                     32
    300-399             5                     35     <- baseline decays to 7-14

The army *grows* instead of collapsing. That is the population deficit --
measured at 7 rats against 55 in point games, 5 against 24 mid-game -- directly
addressed, and it produced the largest mirror result of the session.

### Why it is rejected anyway

66.7% is +9 games on an even, high-resolution instrument; the two failures are
-2 each on lopsided ones, which is the documented noise floor (0.8 sigma). Read
in isolation, each failure is weak and the mirror is strong.

I am rejecting because **this exact profile is the one my own log records as
overfitting.** The "four accepts, all reverted" entry describes four changes
accepted on mirror gains whose combined build measured 2/162 against a 5/162
control -- **and Iteration 88 was one of those four.** Its benchmark number
then was *flat*; here it is -2, so this re-test is a strictly worse version of
the profile that already failed once.

Two further points against:

- Both lopsided instruments moved down. Two independent -2s in the same
  direction is a weaker coincidence than either alone.
- Benchmarks are the tournament-representative instrument. The mirror measures
  play against our own lineage, which is exactly the population regime this
  change alters -- a bigger army beats a smaller identical bot almost by
  definition, which is why a mirror gain here is close to circular.

The `compare_gauntlets` detail supports calling the vs_old_bots move noise --
4 scattered flips (peaceinourtime, popthecork x2, jail) against Iteration 112's
12 flips clustered on tiny/whereisthecheese/closeup -- but noise cutting *both*
ways is not a reason to accept a change that also loses two benchmark games.

**Pre-registered bars decide.** Two of four failed. Reverted.

### What is worth keeping from this

The population deficit is real and this change genuinely fixes it (army 35 at
round 400 against a baseline that decays to 7). It still loses games. That is
the eighth mechanism-verified change to improve its target quantity and lose,
and the first to do so while *also* posting the session's best mirror number --
which is the sharpest available demonstration that the mirror cannot arbitrate
population changes, because more rats trivially beats fewer rats of identical
code.

## Root cause — why "more rats" loses: the cost curve starves the King

Traced after Iteration 125, on the game whose round-delta was worst
(`bench_finalist__minimaze__botB`, r2000 -> r1298; we are team2):

                     rd200  rd400  rd600  rd800  rd1000  rd1200  rd1275
    iter125 cheese    1061   1126    946    666     326      76       0
            rats        23     15      5      4       4       0       0
    baseline cheese   1151   1456    946    796     776     576      --
            rats        21     17     18      8       7       5      --

The treasury reached **zero** and the King starved to death at round 1298. The
baseline never falls below ~576 and survives to the round limit.

**The mechanism, from engine source:**

    getCurrentRatCost() = 10 + 10 * (LIVE babyRats / 4)
    only the KING consumes cheese: RAT_KING_CHEESE_CONSUMPTION = 2 per round
    if team cheese < 2, the King takes RAT_KING_HEALTH_LOSS = 10 per round

Rats carry no upkeep, so a bigger army does not directly drain anything. What
drains is **buying** it: at 35 live rats each additional rat costs 10 + 80 =
**90**, against 30 at eight rats. The cost curve makes a large army
self-limiting, and once the treasury empties the King loses 10 HP per round and
dies within 60 rounds regardless of how many rats are standing around it.

### This unifies six previously separate rejections

Every change that let the King spend more died the same way:

    Iteration 111  shorter build window            treasury 553 -> 68, bankrupt
    Iteration 112  window + override off           -10 on vs_old_bots
    Iteration 113  override off alone              mirror 37.0%
    Iteration 114  override fires earlier          -23 on vs_old_bots
    Iteration 120  REPLACEMENT_RESERVE 1000 -> 500 -9 on vs_old_bots
    Iteration 125  MAX_POPULATION 25 -> 40         treasury to 0, King starved

`REPLACEMENT_RESERVE = 1000` is not a production throttle that happens to be
badly tuned. **It is the anti-starvation floor**, and every one of these six
iterations was an attempt to spend through it. That is why the reserve measured
+24 points when ablated (Iteration 87) and why every relaxation since has lost
games on the instrument with long enough games to show starvation.

### And it closes the population line for good

Population cannot be bought. The cost curve prices it against a treasury we
cannot refill, because income requires rats and rats require income -- the
circularity noted earlier, now with its exact mechanism. `bench_finalist`
fields 75 rats and 5 Kings in that same game while holding 3810 cheese: it
affords the army because its `cheeseTransferred` is roughly double ours, not
because its cap is higher.

So the ordering is fixed and unavoidable: **income first, then population.**
Every attempt this session to take them in the other order has failed, and now
there is a mechanism that says they always will.

## Iteration 126 — relocate the King onto a cheese mine — VOID (squeak never reached it)

                          g_iter21        iter126
    benchmark wins        7/162           7/162
    early wipes           12/155 = 8%     11/155 = 7%
    close-spawn wins      4/42            4/42

No regression anywhere -- the round-300 gate did its job -- but the mechanism
never fired:

    our King, rounds 280-364:  (9,4) throughout, moveCD=0 the whole time
    cheeseTransferred:         200/200/300/300, identical to baseline

**Why: SQUEAK_RADIUS_SQUARED is 16, i.e. radius 4, and the mine is 11 tiles from
our King.** A rat only squeaked while the mine was in view, which means it
squeaked *at* the mine, where no King could hear it. Messages do not relay and
expire after MESSAGE_ROUND_DURATION 5 rounds, so the King never learned a
location and never had anywhere to walk.

**Fifth void of this session, and the same partial-precondition error.** I did
check that an allied King receives squeaks -- `GameWorld.squeak` has no type
filter -- and recorded that as the pre-flight. I never checked whether any
squeak would ever be *in range* of it. "Can a King receive?" and "will one be
within radius 4 of a speaker?" are different questions and I only asked the
first.

The fix is small and its own pre-flight is concrete: `deliverCheese` closes to
`CHEESE_TRANSFER_RADIUS_SQUARED = 9` (3 tiles) to hand over cheese, which is
inside squeak radius 4. So a rat that *remembers* the mine and broadcasts it
every turn carries the news home for free. That is Iteration 127.

## Iteration 127 — King relocation with the squeak actually delivered — REJECTED

                          g_iter21        iter127
    benchmark wins        7/162           5/162
    early wipes           12/155 = 8%     12/157 = 8%
    close-spawn wins      4/42            3/42

**The transmission fix worked and the King moved -- two tiles, then stalled:**

    King  (9,4) through round 340  ->  (8,6) at round 360  ->  still (8,6) at 420+
          moveCD = 0 throughout the stall, so it was able to move and did not
    cheeseTransferred  200/200/400/540   against baseline 200/200/300/300

So the squeak reached home, the King started walking, and income rose ~80% from
two tiles of movement. It then stopped for good.

**Why it stalled: `RAT_KING(600, 3, 25, 360, 10, 40, 20000)` -- the `3` is
SIZE.** The King occupies a 3x3 footprint and needs a three-wide clear corridor
to move through. On a 20-wide map, ringed by its own rats and its own trap
field, that path essentially never exists. The King is not a unit that can
relocate across a map; it can shuffle a tile or two when the immediate
neighbourhood happens to be clear.

That is a property of the piece, not a tuning problem, and it makes
"march the King to the mine" unavailable as a strategy regardless of how the
location is communicated.

**And the two lost wins are probably not even from the relocation.** The
round-300 gate makes the King-side change provably unable to affect an early
wipe, yet close-spawn wins fell 4 -> 3. The rat-side broadcast is NOT gated --
every rat squeaks every turn from round 1 -- so the likely cost is bytecode on
every Baby Rat turn, the same mechanism the Iteration 108 retraction identified.
A cheaper version would gate the broadcast on round >= 250 too.

Not pursuing it: even with perfect transmission the King moves two tiles, so
the ceiling on this idea is the ~80% income gain already measured, and that gain
came with -2 wins. This is the ninth mechanism-verified change to improve its
target quantity and lose games.

## Measurement — the bytecode explanation is FALSE, and we use 4% of the budget

Invoked twice in this log as the likely cause of unexplained outcome shifts --
in the Iteration 108 retraction ("added per-turn bytecode truncating turns") and
again in Iteration 127 ("the likely cost is bytecode"). Both were speculation.
Measured properly by relaxing ReplayDump's indicator filter and reading the
numbers our own `reportBytecodeBudget` has been emitting all along:

    370 Baby Rat turns sampled, limit 17500
        min 374      median 739      max 2546
        median = 4.2% of the limit, peak = 14.5%

**Zero `OVERRAN` in any replay checked.** No turn has ever been truncated. The
bytecode explanation is retracted in both places it appears.

That leaves the two outcome shifts it was invented to explain -- Iteration 108's
and Iteration 127's -- genuinely unexplained. The honest reading of both is that
a small behaviour change reshuffles near-threshold games, which is exactly what
the 1.2-sigma note says a 3-game move means. I reached for a mechanism where
noise was the sufficient answer.

### The positive half: we have ~15,000 unused bytecode per rat per turn

A Baby Rat is allotted 17500 and spends a median of 739. Roughly **25x
headroom**, entirely unused, on every rat on every turn. Nothing this bot does
is constrained by computation:

  - `moveToward` is a single-step greedy choice with a Bug2 fallback; a real
    BFS over the visible region would cost a few hundred bytecodes.
  - `collectCheese` picks the nearest visible cheese tile with no consideration
    of contested-ness, travel time, or what other rats are already doing.
  - target selection nowhere evaluates more than one candidate.

Every failed iteration this session changed a *policy constant* or added a
*single reflex*. None of them spent the available computation on making a
better decision. That is the one dimension of this bot that has never been
pushed, and it is 25x under-used.

Recorded as the honest state of the search. It does not name a specific change,
and I am not going to invent one to fill the gap -- but it does say the
remaining headroom is in decision quality per turn, not in the number of things
a rat does.

## Analysis note — the largest loss bucket was never examined, and it is CATS

Loss distribution across the 155 benchmark losses at `g_iter21`:

    <100 (early wipe)    12    <- most of this session went here
    100-499              57    <- largest bucket, unexamined until now
    500-999              52
    1000-1999            20
    2000 (points)        14

**109 of 155 losses (70%) fall in rounds 100-999**, and the session had
concentrated on the 12 early wipes and the 14 point games -- 17% of the total.

Tracing a representative loss from the biggest bucket
(`bench_finalist__peaceinourtime__botA`, death at 488):

    our King id3:  hp 540 -> 40 across rounds 400-484 = 6.67 HP/round
    treasury:      961 then 911 -- healthy, NOT starvation
    position:      (8,48) -> (10,47) -> (12,47), then frozen from round 436

6.67 per round is exactly `CAT_SCRATCH_DAMAGE` 20 every 3 rounds. **A cat parks
beside the King and grinds it down over ~90 rounds**, and the King cannot leave
because a `RAT_KING` is size 3 and cannot path through its own army -- the
constraint Iteration 127 established for a different reason.

So there are at least three distinct King-death modes, not one:

    early rush        <100      addressed by the trap ring (Iteration 102)
    cat grind         100-999   never addressed; the subject of Iteration 128
    starvation        varies    `streetsofnewyork` shows treasury = 1 from round
                                225 and death at 267

### A tracking error worth recording

I first read this game with `--robot 1` and reported a King at 3018 HP losing
30/round. That was a **cat**: on this map the initial bodies are
`id1,id2 = CAT` and `id3,id4 = RAT_KING`, whereas on `corridorofdoomanddespair`
the kings are `id1,id2`. **Robot id ordering varies by map**, so `--robot 1` is
not reliably the King -- read the `initial body` list first, the same discipline
already recorded for team attribution. The 4000 HP in the round-1 line is what
gave it away.

## Iteration 128 — reactive cat traps — ACCEPTED (g_iter22)

                          g_iter21        iter128
    benchmark wins        7/162           8/162
    early wipes           12/155 = 8%     12/154 = 8%   identical
    close-spawn wins      4/42            4/42          identical
    g_iter21 mirror       --              28/54 = 51.9%
    vs_old_bots subset    99/108 = 91.7%  98/108 = 90.7%

**Mechanism, verified and large:**

    CAT traps placed   0  ->  52
    our catDamage      1554 @400  ->  1764 @400, 3894 @500

**The benchmark gain is purely additive and reproduced at two doses.**
Iteration 129 dosed the trigger radius d^2 20 -> 36 and produced the
*identical win set*: the same single game gained
(`bench_spaark peaceinourtime A`, against the opponent we had beaten once in
54), and nothing lost, at either dose. A reshuffle produces gains *and*
losses; this produces a gain and no loss, twice, under materially different
behaviour (more traps at the wider radius). The curve is 7 -> 8 -> 8, so the
effect saturates at d^2 20 and the wider radius only wastes cheese and pushes
close-spawn wipes 32% -> 34%. Accepted at the cheaper dose.

### Accepting despite a pre-registered gate failing, and why

`vs_old_bots` came back 98/108 against a 99/108 bar -- **short by one game.** I
am not going to pretend that passes. The justification is what the gate is for.
It was added after Iteration 112, where benchmarks and mirror both moved +1
while `vs_old_bots` moved **-10 across 12 flips clustered on
tiny/whereisthecheese/closeup** -- a systematic regression hidden behind small
gains. Here `compare_gauntlets` reports **1 flip and 107 of 108 games
unchanged**. One game at ~91% is 0.4 sigma and the documented resolution floor
for that instrument.

So: two instruments +1, one -1, every guard byte-identical, and a mechanism
that is verified, large, and aimed at a death mode measured this same turn.
That is the cleanest positive profile since Iteration 102.

### What it addresses

The largest loss bucket (rounds 100-499, 57 of 155 losses) contains a King
death mode never previously examined: a cat parking beside the King and
grinding it at 6.67 HP/round while the King, being size 3, cannot path away
through its own army. Cat traps are the counter that was sitting unused --
half the cost of a rat trap, double the damage, they never initiate a backstab,
and each trigger credits 100 catDamage, the 0.5-weighted score term, for 10
cheese.

## Iteration 130 — rats lay cat traps early — REJECTED

                          g_iter22        iter130
    benchmark wins        8/162           5/162
    early wipes           12/154 = 8%     15/157 = 10%
    close-spawn wins      4/42            1/42

**Mechanism fired exactly as designed.** On a valid loss
(`bench_finalist__rift__botB`, we are team2): 20 rat-placed cat traps, 17
triggered by cats, and the placements land where the idle budget was --

    rounds   0-99   12 placements
           100-199   4
           200-299   3
           400+      1

So the reasoning was right that the CAT_TRAP cap sits unused before round 300,
and wrong that this made it free. **Twelve of the twenty placements fall in
rounds 0-99** -- the opening burst, when a rat's action is worth more than
anything a trap returns, and when the close-spawn games are decided. Wins fell
three and close-spawn wins fell from four to one.

The budget is idle early because rat actions are scarce early, not because
nobody thought to use it.

Also of note: `bench_finalist` placed 17 cat traps of its own in that game, so
this is a mechanic strong opponents use -- just evidently not in the opening.

### A measurement error I nearly published

I first dumped `losses/bench_finalist__peaceinourtime__botA.bc26` from this run
and reported **zero** cat traps placed by anyone, including the King's 52 --
which looked like I had broken the accepted Iteration 128 code. Both parts were
wrong. That game is a **win** in this run (round 548), so it is not in
`losses/` at all; `replay-dump.sh` failed, I had suppressed stderr with
`2>/dev/null`, and I read the resulting **0-byte file** as a measurement.

Two habits from this: check that a dump is non-empty before drawing anything
from it, and do not reuse a filename across runs -- a game that flips to a win
silently vanishes from `losses/`. The 0-byte file was visible in `wc -c` the
whole time.

## Iteration 131 — raise the trap cheese gate 250 -> 600 — REJECTED (and it rules out traps as the cause of starvation)

                          g_iter22        iter131
    benchmark wins        8/162           7/162
    early wipes           12/154 = 8%     12/155 = 8%
    close-spawn wins      4/42            3/42

**The mechanism did not fire for the case it was aimed at.**
`bench_finalist__streetsofnewyork__botB` -- one of the two starvation losses
that motivated this -- is byte-identical to baseline:

    window 0-199    25 spawns, 16 traps, cheese 70    CAP-LIMITED
    window 200-399   0 spawns,  0 traps, cheese  1    cheese-limited

Same 16 traps, same collapse to 1. The gate binds only when cheese is already
low, and by then the King had long since stopped trapping anyway -- its traps
were all laid early, while the treasury was still above either threshold.

### What this rules out, which is the useful part

My hypothesis was that the trap ring drains the treasury because traps were
gated at 250 while builds stop at 1000. The arithmetic says otherwise for the
games that actually starve. On `streetsofnewyork`: 25 rats at a cost curve of
10 + 10*(live/4) is roughly 900 cheese, 16 traps is 320, and King upkeep over
199 rounds is ~400. That is ~1620 of a 2500 start, and the treasury still
arrives at 70 -- so what is missing is not restraint in spending, it is
**income**. Traps are a minority of the outflow and capping them changes
nothing.

So **starvation is an income failure, not a spending failure**, which puts it
back behind the same wall as everything else: 
[[the root-cause entry]] already established that population cannot be bought
because the cost curve prices it against a treasury only income refills, and
this says the same thing about solvency itself.

The -1 win is 0.4 sigma and not separately meaningful; the informative result is
the mechanism check.

### Housekeeping note

`bench_stroke__dirtpassageway__botA` is recorded as a loss in `results.csv` but
its replay is absent from `losses/`. Worth knowing that the losses directory is
not guaranteed complete -- check `results.csv` for ground truth on outcomes and
treat a missing replay as missing data rather than as a changed outcome.

## g_iter22 confirmed on peers, and its scope measured

**Peers corroborate the accept.** Against the freshly re-synced archetypes:

    instrument     g_iter21          g_iter22
    benchmarks     7/162             8/162
    peers          60/108 = 55.6%    62/108 = 57.4%   (24->25, 36->37)
    g_iter21 mirror  --              28/54 = 51.9%
    vs_old_bots    99/108 = 91.7%    98/108 = 90.7%

Three independent instruments up, one down by a single game. Peers are the
right corroboration for this change: both sides face cats, their games run long
enough for catDamage to be scored, and the improvement appears against *both*
archetypes rather than one. That is a better basis than the benchmark +1 I
actually accepted on.

**And the scope is narrow, which is worth knowing.** Sampling eight losses from
the g_iter22 benchmark run for any King-placed cat trap:

    closeup A/B, corridorofdoomanddespair A/B, dirtfulcat A,
    dirtpassageway A/B, evileye A          ->  cat traps placed: 0 in all eight

The mechanism fires only where a cat actually comes within d^2 20 of our King,
which is a property of the map, not something the bot controls. It fired
heavily on `peaceinourtime` (52 placements) -- which is exactly where the new
win came from -- and not at all on these eight. `minimaze` under g_iter22 still
reads `catDamage=[1764,3460]`, byte-identical to g_iter21.

That also settles a question cheaply: the point-game arithmetic is unchanged
(32 vs 66 on `minimaze`), so the late-second-King idea rejected by pre-flight
earlier stays rejected. Cat traps did not move that needle because they never
fire there.

**Why widening it is closed.** The trigger radius was dosed 20 -> 36 in
Iteration 129 and produced the identical win set, so distance is not the
binding constraint -- cats simply do not approach the King on most maps. Moving
the King to the cats is impossible (size 3, Iteration 127). Having rats place
them instead is Iteration 130, rejected: 12 of its 20 placements landed in
rounds 0-99 and cost three wins, and gating those to round 300+ would leave
about one placement per game by that iteration's own distribution.

So Iteration 128 is a real but map-limited gain, and the cat-trap line is
closed in every direction that has been tried.

## Iteration 132 — maintain the trap ring — REJECTED (my gate did not do what I said)

                          g_iter22        iter132
    benchmark wins        8/162           7/162
    early wipes           12/154 = 8%     12/155 = 8%
    close-spawn wins      4/42            2/42

**The defect it targeted is real.** Adding `teamRatTrapCount` to the dump shows
our live ring decaying 16 / 12 / 7 / 7 across rounds 100-400 against a maxCount
of 25, because `trapsSinceBuild` resets only on a successful build -- once
`builtCount` hits MAX_POPULATION the King stops building and the trap branch is
dead until the round-400 window refresh.

**And the fix worked, mechanically:** ring now 16 / 16 / 16 / 12, cheese
1097 / 885 / 645 / 425 against a baseline 1098 / 958 / 773 / 553.

**But the gate does not do what the task claimed.** I wrote that it "only acts
once building has stopped". The condition was

    (trapsSinceBuild < TRAPS_PER_BUILD || rc.getNumberRatTraps() < RING_TARGET)

and the ring *starts* below RING_TARGET, so the second clause is true from
`builtCount >= 5` onward. The King therefore traps almost every turn through
the opening rather than at 2:1 -- which is precisely Iteration 122's failure
(density 2:1 -> 3:1, four wins lost), and close-spawn wins fell 4 -> 2 exactly
as that iteration's did.

So this run measured "trap continuously from round ~25", not "maintain the ring
after the cap". The idea is untested; the implementation was wrong.

Iteration 133 gates the maintenance clause on `builtCount >= MAX_POPULATION`,
so it genuinely cannot fire until building has stopped.

## Iteration 133 — ring maintenance, properly gated — REJECTED; ring decay is FINE

                          g_iter22        iter133
    benchmark wins        8/162           6/162
    early wipes           12/154 = 8%     12/156 = 8%
    close-spawn wins      4/42            2/42

**This time the gate did what it claimed.** King census, rounds 0-99:

    g_iter22   25 spawns, 18 traps, cheese 1148   CAP-LIMITED
    iter133    25 spawns, 18 traps, cheese 1148   CAP-LIMITED

Byte-identical opening, so Iteration 132's confound (trapping continuously from
round 25) is gone. And the ring is maintained as intended: 16 / 12 / 12 / 12
against a baseline 16 / 12 / 7 / 7.

**With the opening held fixed, maintaining the ring costs two wins.** The only
difference between the arms is post-cap trapping, so the extra standing traps --
and the King actions and cheese spent replacing them -- are a net negative.

### What that settles

The ring decaying from 16 to 7 is not a defect to fix. It is harmless, and
replacing it is worse than letting it erode. That is consistent with where the
ring's value actually lies: **every early wipe happens before round 100**, when
the ring is still at 16 and freshly built. After the build cap the King's
alternative use of its action -- `attackNearestHostile`, which Iteration 99
established is worth having in the right order -- beats laying a replacement
trap.

So I was wrong that this was "an idle resource". After the cap the King's action
is not idle; it is attacking. Iteration 128 worked because a cat trap is laid
*only when a cat is present*, i.e. exactly when attacking a 4000 HP cat is worth
less than trapping it. A rat trap laid on a timer has no such trigger.

That closes trap-ring maintenance, and with it the whole trap line: density is
peaked (Iterations 101/102/122), the cheese gate is not the starvation cause
(131), rat-placed cat traps cost the opening (130), and ring decay is harmless
(132/133).

## Iteration 134 — rat cat-traps on a tight trigger — REJECTED; the line is closed

                          g_iter22        iter130 (d^2 20)   iter134 (d^2 8)
    benchmark wins        8/162           5/162              4/162
    close-spawn wins      4/42            1/42               1/42

**The tightening worked and the result got worse.** Rat-placed cat traps in
rounds 0-99 fell from Iteration 130's 12 to **2**, exactly as intended, and the
score dropped a further game. So the opening placements were never the cause of
Iteration 130's loss.

### Why -- and it inverts the reasoning I built this on

I justified the tighter trigger with the Iteration 128 lesson: fire only where
the alternative action is worth less. At d^2 <= 8 a cat is effectively on the
rat, so I argued its action was nearly worthless.

That is backwards. **At d^2 <= 8 the rat's best action is to FLEE**, and fleeing
is worth a great deal -- it is the difference between a live collector and a
20-damage scratch followed by death. Spending that action on a trap keeps the
rat next to the cat. The tighter the trigger, the more certainly it fires at the
exact moment fleeing matters most, which is why d^2 8 beats d^2 20 in the wrong
direction.

**The asymmetry I had missed:** Iteration 128 works because a RAT KING cannot
flee. It is size 3 and cannot path through its own army (Iteration 127), so when
a cat arrives its options are trap it or be ground down -- trapping really is
free. A Baby Rat can flee, so for a rat the same action is never free.

That is a sharper statement of the rule than "fire when the alternative is worth
less": **the alternative has to be worth less for THAT UNIT**, and unit mobility
is what decides it. The King's immobility, which has cost us everywhere else
(Iterations 127, 128's death mode), is exactly what makes its cat trap pay.

### Closed

Rat-side cat traps are closed at both triggers, d^2 20 and d^2 8, with
close-spawn wins collapsing to 1/42 in both. Combined with the trap-line closure
from Iterations 130-133, every trap variant this session has now been tested:
density, ratio, cheese gate, ring maintenance, rat-placed rat traps, and
rat-placed cat traps. Only the King's reactive cat trap survives.

## Iteration 135 — stunned rats lay cat traps — REJECTED; the shared cap was the answer all along

    trigger                       wins     close-spawn wins
    none (g_iter22)               8/162    4/42
    rat, cat within d^2 20 (130)  5/162    1/42
    rat, cat within d^2 8  (134)  4/162    1/42
    rat, stunned only      (135)  6/162    1/42

Three completely different triggers, the same collapse to 1/42. The pre-
registered risk said that would settle it, and it does -- but not for the reason
any of the three iterations proposed.

**The mechanism, measured on the map where cat traps were validated
(`peaceinourtime`):**

    g_iter22   King 52 placements, rats  0
    iter135    King 24 placements, rats 13   (37 total, down from 52)
    live catTraps reach 10 -- the cap -- by round 500

**Rat placements starve the King's budget.** `CAT_TRAP.maxCount` is 10 for the
whole team, so every trap a rat lays in the field is a slot the King cannot
refill when a cat actually reaches it. The King's placements more than halved.
The `> 8 tiles from our King` guard I put in all three iterations prevented
*spatial* competition and did nothing about the *global* cap, which is the
resource that was actually contested.

### What this says about my reasoning

I explained these three failures three different ways -- too many early
placements (130), firing when the rat should flee (134), and then a mobility
rule that was supposed to fix it (135). The first two explanations were
plausible and both were wrong; the third was a genuine insight about the King
and still did not rescue the idea, because the binding constraint was never the
trigger.

The tell was available from Iteration 130 onward: `teamCatTrapCount` was in the
Round table the whole time, and I only added it to the dump two iterations ago
while chasing something else. A shared-resource change needs the shared resource
measured *first* -- I was measuring placements and outcomes, never the pool they
draw from.

**Closed for good.** Only the King may place cat traps, because the King's are
the ones the cap should be spent on.

## Iteration 136 — let a collapsed army rebuild when rich — REJECTED (inert, and that is the finding)

                          g_iter22        iter136
    benchmark wins        8/162           8/162
    early wipes           12/154 = 8%     12/154 = 8%
    close-spawn wins      4/42            4/42
    win set                               IDENTICAL, 0 flipped outcomes

**The mechanism fired well.** On `bench_spaark__hatefullattice__botB`, the game
that motivated it:

    window        baseline spawns   iter136 spawns
    0-199              25                28     (cap bypassed 3x)
    200-399             0                 9     (the collapsed-army rebuild)
    400-599            15                25

Nine rats rebuilt in a window that previously produced zero while the King sat
on 2000+ cheese with two rats alive. Exactly what the task asked for.

**And nothing changed.** 135 of 162 games byte-identical, no outcome flipped,
round-delta slightly negative (11 improved, 16 worsened). The game that
motivated it still lost, at round 666 against 674.

### Why this null matters

This was the population lever tested in its most favourable possible form:
spending cheese that was provably idle (2200 sitting unused), at the cheapest
point on the cost curve (10 per rat at 2 alive, against 70 at 25), only when the
army had actually collapsed, with no effect on the opening. If buying population
were going to help anywhere, it would help here.

It does not. Combined with Iteration 125 (raise the cap when rich -- rejected,
mirror-overfit profile) and the root-cause finding that the cost curve prices
population against a treasury only income refills, **the population line is now
closed from three directions**: raising the ceiling, bypassing the cumulative
cap, and rebuilding a collapsed army for free.

The rats are not what we are short of. We rebuilt nine of them into a game we
lost by exactly as much as before.

## Iteration 137 — ablate the King's flee-from-cat — REJECTED; the behaviour is dormant, not harmful

    g_iter22 mirror, flee OFF, playing the build that has it:  26/54 = 48.1%

One game below even. Per the ablation rule that is inert -- keep it, do not
credit it. My hypothesis was that it is actively HARMFUL, walking the King out
of the rat-trap ring it spends the opening building. Not supported.

**Why inert: it barely executes.** Tracking both Kings in
`g_iter22__safelycontained__botB` (team1 = g_iter22 with the flee, team2 = the
ablated build):

    ours    id4 at (48,15) from round 1 to 200+, never moves
    theirs  id3 at (11,15) from round 1 to 200+, never moves

Neither King moves at all, because no cat comes within d^2 20 of either. The
ablation is a no-op wherever that holds, which is most maps -- the same map
property that limits Iteration 128's cat traps to firing in 0 of 8 sampled
losses.

So the flee is dormant rather than balanced, and the Iteration 128 death mode
(a cat grinding the King at 6.67 HP/round) is not a case where the flee makes
things worse; it is a case where the flee tries, moves the King two to four
tiles, and cannot get clear because a RAT_KING is size 3.

### Recorded with the Iteration 84 caveat

Iteration 84 measured the emergency override "inert" at 48.1% and I later
found it load-bearing -- removing it cost ten games once BUILD_WINDOW_ROUNDS
changed. **Inert means inert given the rest of the configuration, not inert
absolutely.** The King's flee is kept on the same basis: neutral to remove
today, and not to be assumed harmless if the King's mobility or the cat
response ever changes.

That closes the King's behaviour set. Every King action is now either measured
optimal (trap ratio, cat-trap trigger, reserve, override, attack ordering) or
measured dormant (flee).

## Iteration 138 — ablate the desperation raid — ACCEPTED (g_iter23)

                              g_iter22          g_iter23
    g_iter22 mirror           --                28/54 = 51.9%
    benchmark wins            8/162             8/162
    benchmark round-delta     --                +1525  (38 improved, 8 worsened)
    vs_old_bots subset        98/108 = 90.7%    100/108 = 92.6%
    vs g_iter21               --                29/54 = 54%
    early wipes               12/154 = 8%       identical
    close-spawn wins          4/42              identical

**The justification is a measured defect, not a story.** Iteration 12 has the
King broadcast a GUESSED enemy-King location (shared-array slots 3/4) assuming
180-degree rotational symmetry, and desperate rats march to it instead of
collecting. Checking that guess against every benchmark map from the replay
headers:

    correct on 11 maps, WRONG on 16

    hatefullattice  symmetry=1  guess(38, 2)  actual(11, 2)   -- 27 tiles off
    jail            symmetry=1  guess(33,37)  actual(26,37)
    dirtfulcat      symmetry=2  guess( 7,17)  actual( 7,12)
    keepout         symmetry=2  guess(44,26)  actual(44,23)

The code's comment calls rotation "the single most common case". It is not:
symmetry 0 (rotation) holds on 11 of 27 maps, reflections on 16.

Worse, `desperate` requires `economyStruggling && cheese < RESERVE 150`, so it
fires when we are nearly bankrupt -- and starvation is roughly half of mid-game
losses. The behaviour switched on exactly when we were most fragile and, on 16
maps in 27, marched the surviving rats to an empty patch of map.

### On accepting with flat benchmark wins

Wins are 8/162 either way and no outcome flipped. What moved is everything
else: the mirror by a game, `vs_old_bots` by two on the comparable subset, and
the benchmark round-delta by +1525 with **38 games improved against 8
worsened** -- a skew I have not seen before this session. `compare_gauntlets`
documents round-delta as a tracked metric that does not gate decisions, so it
is corroboration rather than the case; the case is that a demonstrably
wrong-targeted behaviour was removed and every instrument came back
neutral-or-better.

Only the `desperate` RAID MOVEMENT was gated. The `desperate` flag itself is
untouched, so rats are still willing to fight enemy rats pre-backstab, and this
isolates the guessed-location march.

## g_iter23 confirmed on peers

    instrument            g_iter22          g_iter23
    benchmarks            8/162             8/162  (round-delta +1525, 38 up / 8 down)
    peers                 62/108 = 57.4%    64/108 = 59.3%   (pure_cooperator 25 -> 27)
    vs_old_bots subset    98/108 = 90.7%    100/108 = 92.6%
    g_iter22 mirror       --                28/54 = 51.9%

Three instruments positive, benchmarks flat. Removing the desperation raid --
whose enemy-King guess was wrong on 16 of 27 maps -- is corroborated everywhere
it could be.

## Iteration 139 — early cat traps — VOID (clause nested inside the wrong guard)

Byte-identical to `g_iter23` on every counter, and 0 cat traps on
`dirtfulcat botA` -- the exact game it was written to fix.

**My error.** I attached the new condition inside the existing guard:

    if (nearestCat != null
            && (dist <= CAT_TRAP_TRIGGER_DSQ
                    || (rc.isCooperation() && rc.getNumberCatTraps() < 3)))

so early placement still required a cat VISIBLE to the King, whose vision is
radius^2 25. On `dirtfulcat` no cat is near the King during rounds 1-24, so
`nearestCat` is null and the clause never ran.

The whole point of early placement is to prepare for cats that have not arrived
yet, so it cannot depend on seeing one. Same class of error as Iteration 132,
where the maintenance gate also did not do what I claimed for it -- both times
the fix was written as an extra disjunct inside a guard whose outer condition
already excluded the case of interest.

Iteration 140 hoists it into its own statement.

## Iteration 140 — early cat traps, hoisted out of the guard — REJECTED

                          g_iter23        iter140
    benchmark wins        8/162           4/162
    early wipes           12/154 = 8%     17/158 = 11%
    close-spawn wins      4/42            2/42
    close-spawn wipes     32%             42%

**The mechanism fired, at the worst possible moment.** Three cat traps placed
on `dirtfulcat botA` -- rounds **1, 2 and 3**, before a single rat exists:

    window 0-99   g_iter23  25 spawns, 27 traps, 16 alive, cheese 1184
                  iter140   25 spawns, 45 traps, 12 alive, cheese 1130

The King spent its first three actions on cat traps, delaying the round-1-to-25
opening burst that Iteration 38 identified as the proven-good behaviour and
warned against disturbing. Standing army at round 99 fell 16 -> 12 and early
wipes rose 8% -> 11%, with close-spawn wipes 32% -> 42%.

### The blocked-cat-trap problem is real but has no cheap fix

The diagnosis stands and is worth keeping: `catTrapsAllowed` bars placement once
we are the backstabber, and our own rat-trap ring makes us the backstabber
whenever an enemy steps on it -- on `dirtfulcat` that happens by round 24, after
which we place zero cat traps all game despite 29 CatScratches. The accepted
Iteration 128 feature is genuinely switched off on those maps.

But the only window in which we may place them is the one where King actions are
most valuable. Iteration 122 already priced that: raising opening trap density
from 2:1 to 3:1 cost four wins. Three cat-trap actions at rounds 1-3 cost four
here. Gating at `builtCount >= 5` would move them to rounds 6-8, which is
cheaper but still inside the burst, for a benefit that only materialises on maps
where a cat later approaches the King -- 0 of 8 sampled.

Not pursuing further: the cost is certain and immediate, the benefit is
conditional and rare.

### Two iterations, one mistake worth naming

Iteration 139 was void because I wrote the new condition as an extra disjunct
inside `if (nearestCat != null && ...)`, whose outer test already excluded the
case I was targeting. Iteration 132 failed the same way. **When adding a
condition to an existing branch, check the guard I am nesting inside, not just
the condition I am writing.**

## Iteration 141 — ablate the desperation flag — REJECTED (exactly inert)

    g_iter23 mirror, desperation OFF, playing the build that has it:
        27/54 = 50.0%

Exactly even. The pre-registered PRIMARY was mirror > 50%, so this fails on the
letter and I am not going to rationalise a tie into a pass. Benchmarks were not
run, since the bar made them conditional on the primary.

**Why it is inert: the trigger is nearly unreachable.** `desperate` needs
`economyStruggling && cheese < RESERVE 150`, and the treasury trajectories show
that second condition only holds in the starvation games -- `streetsofnewyork`
70 -> 1, `dirtpassageway` 125 -> 1 -- and only near the end, in games already
lost. Elsewhere cheese sits at 550-2200 and the flag never fires. Same shape as
the King's flee (Iteration 137, also 48-50%): dormant rather than balanced.

### What the reasoning got right, and what it does not license

The three observations behind this were all correct and all still stand:

  - the surviving effect is the willingness to bite enemy rats pre-backstab,
    which the code's own comment records Iteration 11 as having rejected as
    inert;
  - a desperate bite calls `backstab(this.team)`, which bars cat-trap placement
    for the rest of the game via `catTrapsAllowed`;
  - `economyStruggling` is a permanent latch on a trend that demonstrably
    reverses (`hatefullattice` 1555 -> 2041 -> 2233).

None of that adds up to a measurable effect, because the code path is almost
never taken. **A correct chain of reasoning about a dormant branch predicts
nothing.** That is the third time this session I have built a case from sound
premises about code that turns out not to execute -- Iterations 137 and 139
being the others.

Kept, with the Iteration 84 caveat: inert means inert given the current
configuration. If the reserve, the cap, or the economy ever change enough that
cheese sits below 150 for meaningful stretches, this flag wakes up and will
start handing the opponent our cat traps.

## Constant audit — every LIVE constant is tested, every untested one is dormant

Applying the reachability discipline systematically rather than one hypothesis
at a time. The bot has nine tunable constants; cross-referencing each against
the measured treasury trajectory (2500 -> 1098 @100, 958 @200, 773 @300,
553 @400) and the test history:

    constant                  reachable?                          tested
    MAX_POPULATION 25         live, binds rounds ~25-99            125 raised, 136 bypassed
    BUILD_WINDOW_ROUNDS 400   live at round 400                    111 / 112 / 113
    REPLACEMENT_RESERVE 1000  live (cheese 553 < 1000 late)        87 ablation, 120 dose
    TRAPS_PER_BUILD 2         live every King turn                 101 / 102 / 122, peak
    KING_TRAPS_ENABLED        live                                 82 / 96
    CAT_TRAP_TRIGGER_DSQ 20   live when a cat nears the King       129 dose
    DESPERATE_RAID            removed                              138, accepted
    RESERVE 150               DORMANT -- cheese >= 553 until       untested
                              starvation                           
    BITE_BOOST_CHEESE 4       DORMANT -- gated `cheese > 1000`,    45 / 83
                              dead after round ~150                

**Every live constant has been tested in both directions where a direction
exists. The two untested ones are dormant, and dormancy is correct for both.**

`RESERVE = 150` only binds when the treasury is already near zero, i.e. in games
lost to starvation regardless. Lowering the bite-boost gate would make the boost
live all game, but the arithmetic is against it: `ceil(sqrt(4))` is +2 damage for
4 cheese, so ~61 bites a game buys ~122 damage for ~244 cheese -- 0.5 damage per
cheese against the cat trap's 10, spent on the resource that half our mid-game
losses run out of.

### Where that leaves the parameter search

Closed. Combined with the behaviour ablations -- heading reassignment (+28),
`REPLACEMENT_RESERVE` (+24), cat approach (+11), Bug2 (+5.6), cheese-boost
(kept), King trap ring (reversed then validated), emergency override (inert but
load-bearing), King flee (dormant), desperation (dormant, removed raid) -- there
is no untested live knob left in this bot.

The two accepts of this stretch both came from the same place instead: reading
the ENGINE for capabilities and interactions the bot never used. Iteration 128
found `CAT_TRAP` (half the cost, double the damage of a rat trap, credited as
catDamage); Iteration 138 found that the raid's symmetry assumption is wrong on
16 of 27 maps. Neither was a tuning question.

## vs_old_bots roster widened to every 5th snapshot (user request)

`roster_opponents()` now steps by 5 rather than 10, so the roster is every
accepted iteration ending in 1 or 6: `g_iter1, g_iter6, g_iter11, g_iter16,
g_iter21`. Five opponents means 270 games per run rather than 108.

First run at `g_iter23`:

    vs g_iter1    49/54 = 91%
    vs g_iter6    45/54 = 83%
    vs g_iter11   51/54 = 94%
    vs g_iter16   26/54 = 48%     <-- below even
    vs g_iter21   29/54 = 54%
    overall      200/270 = 74.1%

**The denser roster immediately shows something the decade spacing hid: we do
not beat `g_iter16`,** a snapshot seven accepts back, and we only just beat
`g_iter21`. The old two-point roster (g_iter1, g_iter11) reported 91% and 94%
and made the lineage look uniformly dominated.

### Reading it honestly

This is not straightforwardly a regression. Benchmarks over the same span went
**5/162 -> 8/162**, and peers 55.6% -> 59.3%, so the bot has improved against
the opponents that matter. What the head-to-head says is that the improvements
since `g_iter16` are **benchmark-directed rather than lineage-directed** -- the
trap-density accept (Iteration 102), the cat traps (128) and the raid removal
(138) all target behaviours the benchmark bots exploit, and none of them help
much against a copy of ourselves that never rushes and never lays a trap field.

That is the same representativeness split recorded throughout: our own lineage
does not pose the threats we have been fixing. It is also a caution about the
mirror as the primary accept test -- `g_iter16` beating `g_iter23` head-to-head
is exactly what "the mirror cannot arbitrate changes aimed at a threat it does
not pose" predicts.

Worth watching rather than acting on immediately: if `g_iter16` stays above 50%
as further accepts land, that is evidence the lineage has drifted into a local
optimum that is worse head-to-head while better against benchmarks -- and the
benchmark set is the one that resembles a tournament.

### Explained: `g_iter16` is the TRAPLESS build, so 48% is expected

Diffing `g_iter16` against `g_iter23` answers the question the new roster
raised. `src/g_iter16/RobotPlayer.java:305`:

    final boolean KING_TRAPS_ENABLED = false;

**`g_iter16` is the Iteration 82 build, with the King's trap ring ablated.**
Iteration 82 removed the ring on a 57.4% mirror result; Iteration 96 reversed
that decision on representativeness grounds, because the benchmark early-wipe
rate told the opposite story:

    instrument        traps OFF (g_iter16)   traps ON
    benchmarks        2/162                  3/162
    early wipes       26%                    13%
    g_iter15 mirror   57.4% (better)         --

So the 26/54 = 48% is not a regression and not new information -- it is the
*same* Iteration 82/96 result, now visible on the chart because the roster
includes `g_iter16`. A trapless bot beats a trapped one in the lineage, where
nobody rushes the King and the ring is pure cost. Against the benchmark set the
ring halves early wipes, and Iteration 102 later doubled its density for
5/162 -> 7/162.

**This retracts the caution I attached to the roster result an hour ago.** I
wrote that we should watch whether `g_iter16` stays above 50% as evidence the
lineage had "drifted into a local optimum worse head-to-head". It has not
drifted: we knowingly traded lineage strength for benchmark strength in
Iteration 96, on a counter the mirror cannot see, and the trade has since paid
5/162 -> 8/162.

**Expect the `g_iter16` line to stay near or below 50% indefinitely.** That is
the trade working as intended, not a warning. The lines worth watching for
regression are `g_iter1`, `g_iter6` and `g_iter11`, which are trapped builds and
sit at 91%, 83% and 94%.

## Iteration 142 — seek cats when idle — REJECTED

Traced `whereisthecheese`, a map we lose 0/6 on benchmarks and lose *both sides*
of to the ancient trapless `g_iter6`. Against `bench_stroke` (we are team1):

    their RatAttack   438        ours   72      -- 6x
    their catDamage  4200        ours  950
    at round 100, with 12 rats EACH:  1710 vs 120

The gap is not army size: at round 100 both sides field twelve rats and they have
already banked fourteen times our cat damage. Nor is it trapping — they placed 11
cat traps to our 10, so about 1100 of their 4200 is traps and the rest is teeth.

**Hypothesis.** `nearestCat` comes from `senseNearbyRobots()`, and a Baby Rat's
vision is a 90-degree cone — so we engage only cats that happen to lie in the
wedge we are already facing, and a cat that leaves the cone stops existing.
Nothing in this bot had ever *travelled* toward a cat. Note what the cat
dose-curve did and did not test: Iterations 103/104/105 varied when to STOP
fighting (abstain 38.9%, break off at 60 HP 48.1%, current 50.0%, break off at 10
HP 50.0%) and Iteration 124 tested engaging *less* (4/162). Every one moved the
disengage threshold. None moved a rat toward a cat it wasn't already beside.

**Change.** Per-rat `knownCat` memory, consulted after `collectCheese` fails and
before `explore`, gated on health > 30, cleared on arrival.

**Mechanism check — FIRED.** Not a void; this is a real negative.

    whereisthecheese      baseline   iter142
    our RatAttack               72        84   (+17%)
    our catDamage @100         120       240   (2x)
    our catDamage final        950      1050
    our aliveBabies @100        12        15
    our aliveBabies @300         1         0

**Result — REJECT.**

    benchmarks        8/162  ->  6/162      (bar was >8)
    close-spawn wins   4/42  ->   2/42      (guard breached)
    peer archetypes            22/40 (55%)
    early wipes                13/156 = 8%

The mechanism worked and the outcome got worse, which is the informative kind of
failure. Seeking cats buys bites at well under the rate it spends rats, and it
spends them worst on close-spawn maps where the guard broke. The +12 bites cost
two benchmark wins. **The cat dose-curve's flat top (48–50% across three very
different disengage rules) is better read as "cat engagement is already at its
ceiling" than as "we have not pushed hard enough."**

### The far more important finding, from the same trace

While checking the mechanism I found what actually kills us on this map, and it
is not cats. **They grab our rats and throw them.**

    grabs BY them OF us    37   ->  all 37 thrown
    grabs BY them OF own   43   ->  only 2 thrown, rest dropped (mobility)
    grabs BY us             0       throws BY us  0

`THROW_DAMAGE 10 + THROW_DAMAGE_PER_TILE 4` makes the observed `dmg=42` an
8-tile throw, plus `THROW_DURATION 4` rounds stunned. 37 throws is ~1550 damage
onto 100-HP rats — roughly fifteen dead rats, and it is why our army goes 15 -> 0
between rounds 100 and 300 while theirs holds at 12. This also resolves the
"18 self-attributed deaths" I had guessed were rat traps in the earlier baseline
trace: a thrown rat's landing damage is attributed to *itself*, so the killer
column reads `team1,RAT` for a `team1` victim.

**Scope, swept over all 12 archived replays** (opponent behaviour does not depend
on our build, so old samples are valid evidence):

    bench_finalist   jail              71 throws
    bench_stroke     whereisthecheese  39
    bench_spaark     popthecork        25
    ALL peer archetypes                 0    (every map)

**Only the real benchmark bots throw. Every peer archetype throws zero.** So the
mirror and the peer Gauntlet are structurally blind to a throw *counter* — see
`resolution-is-not-representativeness`. Benchmarks are the only instrument with
resolution for the defensive half.

**Their sequencing is deliberate, and worth studying.** `grabRobot` on an enemy
calls `backstab(this.getTeam())`, so the grabber becomes the backstabber — which
under `catTrapsAllowed` bars them from cat traps forever. So they place all 11
cat traps in rounds 25–50 *while still cooperating*, and only then grab at round
55, three rounds after the break. They bank the cooperative-only resource first
and pay the backstab price once it is already spent.

**This also retracts the Iteration 108 conclusion.** I logged `carryRat` VOID on
the grounds the capability "barely fired" (enemy RatNap 9 -> 8). That was a fact
about my implementation, not the mechanic — `bench_finalist` fires it 71 times in
a single game. The engine's bar is low: `HEALTH_GRAB_THRESHOLD = 0`, so an enemy
may be grabbed if it is *facing away* (its own 90-degree cone excludes you) or if
you simply have more HP. What blocked us is that our enemy-rat block sits behind
`!rc.isCooperation() || desperate`, so our rats never approach an enemy during
cooperation at all. Theirs do. Iteration 143.

### Tooling fix this exposed

`ReplayDump` printed only `who` (= `turn.robotId()`, the actor) for `RatNap` and
`ThrowRat`, discarding the payload id. But the schema is not self-consistent:
`RatAttack` carries the *biter* (actor) while `RatNap` carries the *captive* and
`ThrowRat` the *thrown rat* (both victims). So "team2 RatNap" read equally well
as "a team2 rat grabbed someone" or "a team2 rat got grabbed" — opposite
conclusions about who is winning. It bit immediately: my first count of
"team1 RatNap = 30" looked like thirty grabs by us, when we grab zero times and
those lines were the action echoed into the victim's own turn.

Both ends are now printed, and `test_grab_throw_name_both_ends` fails if either
action ever loses its target again. Same family as the Iteration 108 retraction
and `read-the-team-from-the-replay-header`: an id in a replay means nothing until
you know which end of the action it names.

## Iteration 143 — grab and throw enemy rats — REJECTED (flat)

Direction A of the throw finding: use the weapon they use on us.

**Mechanism check — FIRED, but the dose is small.**

    first pass                 7 grabs,  1 throw
    with turn-to-throw         5 grabs,  3 throws
    them, same game           49 grabs, 46 throws

The first pass exposed a real defect worth recording: `throwRat()` takes **no
direction** — it throws along `getDirection()`, and `assertCanThrowRat` needs
that tile on-map, passable and *empty*. A rat that grabs and then moves is
usually facing something solid, so six of seven grabs expired on
`MAX_CARRY_DURATION` (10 turns) instead. An expired carry deals no damage and
neutralises the **carrier** as much as the captive — strictly worse than never
grabbing. Adding a turn-to-a-throwable-direction step took conversion from 1/7 to
3/5.

**The backstab guard held.** `grabRobot()` on an enemy calls
`backstab(this.getTeam())`, making the grabber the backstabber, and
`catTrapsAllowed()` bars the backstabber from cat traps *permanently* — which
would have silently killed the accepted Iteration 128 reactive cat traps.
`GameWorld.backstab` is wrapped in `if (this.isCooperation)`, so once cooperation
is broken it is a no-op and the identity is frozen. But the enclosing condition
is `!isCooperation() || desperate`, and under `desperate` we are still
cooperating, so the grab needed its own strict re-test. Verified in the replay:
round 300 reads `catTraps=[9,0]`, nine live cat traps of ours, so we never became
the backstabber. Iterations 132 and 139 both broke by assuming an enclosing guard
implied something it did not; this time the check was written first.

**Result — REJECT.**

    benchmarks         8/162  ->  7/162     (-1 = 0.4 sigma, flat)
    close-spawn wins    4/42  ->   2/42
    early wipes                 13/155 = 8%

Not a negative — a **flat** result with a mechanism that fired at roughly a
fifteenth of the opponent's rate. Per `dose-response-not-resampling` the move
would normally be to scale the dose. **Here the dose is structurally capped, and
the reason is the interesting part.**

To grab an enemy the engine requires (`HEALTH_GRAB_THRESHOLD = 0`) either that
the target is *facing away* — its own 90-degree cone excludes the grabber — or
that the grabber has more HP. Both clauses run against us:

- Their rats approach ours from outside our vision cone. That is *why* they can
  grab: our rat is facing away. The same fact means our rat cannot see them, so
  `nearestEnemyRat` returns nothing and our grab branch never evaluates.
- Our rats are usually the damaged ones, having already been thrown, so the
  HP clause fails too.

We cannot grab the rats that are grabbing us, because being grabbable and being
blind are the same condition. Scaling the offensive dose does not fix that.

### What this points at instead

Their 49 grabs require 49 adjacencies with our rats. **We supply those
adjacencies.** Iteration 12 added a chase — when `!isCooperation`, a rat that
sees an enemy within d^2 8 closes on it. Iteration 12 was validated against
`pure_cooperator` and `immediate_defector`, and per the Iteration 142 sweep
**every peer archetype throws zero, on every map**. So the chase was accepted on
an instrument that is blind to its single largest cost: walking into grab range
of an opponent whose whole plan is grabbing.

That is an ablation of an accepted feature, measured on the only instrument with
resolution for it — the highest-yield shape available per
`ablate-accepted-features-on-the-mirror`, except that here it must run on
benchmarks rather than the mirror, per `resolution-is-not-representativeness`.
Iteration 144.

## Iteration 144 — ablate the Iteration 12 enemy chase — VOID on premise, REJECTED as ablation

Two separate results here, and they must not be conflated.

### 1. The pre-registered hypothesis is FALSIFIED

The claim was that our chase supplies the adjacencies their grabs need — a rat
that walks from d^2 8 into bite range at d^2 2 spends several turns inside grab
range to land one 10-damage bite. Pre-registered: *their grabs-of-us and
throws-of-us must FALL; if neither falls, VOID.*

    their grabs of us     37 (g_iter23 baseline)  ->  42
    their throws of us    37                      ->  38

Neither fell. Both rose slightly. **They create the adjacency themselves.**
Denying them our approach does not deny them the grab, because their rats hunt
ours — which is consistent with the Iteration 143 finding that they close from
outside our 90-degree cone, i.e. they were never relying on us to come to them.

This kills the whole "stop feeding them adjacencies" family of ideas, not just
this one. Any future throw-defence has to survive the fact that the opponent
supplies its own engagement.

### 2. Reframed as a plain ablation, the chase is one of our most valuable features

Since the change was already built and the premise was dead, I ran it against the
narrower question — *does the Iteration 12 chase earn its keep at all?*

    benchmarks         8/162  ->  3/162     (-5, about 2 sigma)
    close-spawn wins    4/42  ->   0/42
    early wipes                 13/159 = 8%

**REJECT, emphatically.** The chase is worth roughly five benchmark wins and is
the entire source of our close-spawn wins. On knifefight/tiny/thunderdome an
un-chased enemy rat simply walks to our King, exactly the risk the guard was
written for.

Worth stating plainly because I had the sign wrong going in: I expected the peer
Gauntlet to *fall* and benchmarks to *rise*, on the theory that Iteration 12 was
accepted on an instrument blind to throwing. The instrument-blindness claim is
still true — every peer archetype throws zero on every map — but it did not
matter, because the chase's value was never about the throw exchange at all. **A
feature validated on a blind instrument is not thereby wrong; it may simply be
valuable for a reason that instrument happens to capture correctly.**

That is the honest correction to how I framed Iterations 142-144: I found a real
and large opponent capability (throwing), and then spent three iterations
assuming our losses must route through it. The throw damage is real; the
inference that our own combat policy was mis-tuned around it was not.

## Iteration 145 — widen the enemy chase to full vision — REJECTED (and it completes a dose curve)

Iteration 144's ablation measured a steep slope, so the disciplined follow-up was
to push the other way on a constant Iteration 12 set and never dose-tested. d^2 8
is ~2.8 tiles; a Baby Rat's vision radius^2 IS 20, so 20 means "chase anything
you can actually see" and is the natural ceiling rather than an arbitrary bump.

**Mechanism check — FIRED.** Arm-to-arm identity confirmed before spending 162
games, per `a-dose-must-change-the-evaluated-condition`:

    our RatAttack on whereisthecheese
      g_iter23 baseline (d^2 8)     72
      iteration 145     (d^2 20)   106     (+47%)

**Result — REJECT, and the three points together are the finding:**

    chase radius        benchmarks    close-spawn wins
      0 (Iteration 144)    3/162          0/42
      8 (Iteration 12)     8/162          4/42
     20 (Iteration 145)    6/162          1/42

A clean **interior optimum at 8**. Iteration 12's constant — set once, by
argument, four months of iterations ago and never measured — is sitting on the
peak. Both directions are now closed, which is worth more than either single
result: this is the same three-point concave shape that rescued the cheese-boosted
bite in Iteration 83, run deliberately this time instead of by accident.

**Why wider loses, given that removing it entirely loses much more.** The chase's
value is defensive — intercepting a rat that would otherwise walk to our King —
and that value is concentrated in the last two tiles. Extending to 4.5 tiles buys
almost no extra interception but pulls rats off cheese for the whole approach,
which `rat-turn-mechanisms-are-priced-per-capita` predicts is expensive at 4-8
rats. The +47% bites bought nothing; early wipes went 8% -> 9%.

**Iterations 142-145 as a block.** Four rejections, no accepts, and the net gain
is real anyway: the throw mechanic is now understood and documented, the
"stop feeding them adjacencies" family is dead by measurement rather than by
argument, the chase constant is validated at its optimum, and two instrument
bugs are fixed. `g_iter23` remains the accepted build at 8/162.

### Instrument maintenance done alongside

**The staleness guard was crying wolf.** A benchmark Gauntlet printed six
warnings telling me to "re-sync bench_finalist / bench_spaark / bench_stroke to
src/bot/" — nonsense, since those are other teams' bots and derive from nothing
of ours. That is noise that trains you to skip past the one warning that matters.
`bench_*` and `examplefuncsplayer` are now exempt.

**And the peers were genuinely stale — again, a fourth time.** Both sat at
2026-09-02 against `g_iter23`, missing Iteration 102 (rat trap density) and
Iteration 128 (reactive cat traps). Note which of those absences was legitimate:
`pure_cooperator` must not lay **rat** traps, because `GameWorld.triggerTrap`
calls `backstab(triggeringRobot.getTeam().opponent())` and so the trap's OWNER
initiates the backstab. But `triggerTrap` skips that for `CAT_TRAP`, so a pure
cooperator *should* lay cat traps, and their absence was drift. Distinguishing
policy from drift by hand across 1300 lines is exactly what kept failing.

So the edits are now data, not a procedure: `tools/resync_archetypes.py` rebuilds
each archetype from the newest **accepted snapshot** — never from `src/bot/`,
which routinely holds an experiment under test — and aborts if any policy edit
fails to match exactly once. Both peers rebuilt (+84 lines each) and verified by
playing them against each other.

**The 22/40 peer figure recorded earlier today was measured against the stale
pair and should not be compared against anything after this point.**

## Iteration 146 — muster 8 rats to build a SECOND Rat King — REJECTED

The first structural change in many iterations, aimed at the dominant loss mode:
the win condition is "destroyed all of the enemy team's rat king**s**", and 128
of our 154 benchmark losses are exactly that, between rounds 100 and 1999. Only
14 reach points. A second King doubles what an opponent must kill and moves the
scored kings share from 1/2 to 2/3.

Not speculative: `bench_finalist` reaches `2:kings=2` on jail.

**Why Iteration 106's void did not apply.** `assertCanBecomeRatKing` forbids any
non-baby-rat in the 3x3, our only cluster is around our own King, and the King is
SIZE 3 — so every rat beside it is disqualified by the King itself. A muster
point placed away from home dissolves that.

**Two real bugs, both caught by the mechanism check, both instructive.**

1. `findMusterLocation` scanned tiles at d^2 <= 25 (the King's vision) and then
   read the 3x3 *around* each candidate, so neighbours of the outermost tiles lay
   outside vision and `sensePassability` threw. `run()`'s per-turn handler
   swallowed it, so **the King's entire turn aborted from round 200 onward** — it
   stopped building rats and the army fell 14 -> 3. I read that as "the muster is
   eating the army": a coherent, plausible, completely wrong story about a
   strategy, caused by a missing `canSenseLocation`. The tell was that the
   exceptions began at *exactly* the gate round. The baseline replay shows the
   same 14 -> 3 collapse, so the muster never caused it.
2. ID-parity gating, added to protect the economy, made the upgrade
   **unreachable by construction**: it needs 7 adjacent rats plus the one
   upgrading — eight bodies — and half of 12-15 is seven.

**Mechanism check — FIRED, on benchmarks.** Not a void. `1:kings=2` appears in at
least 7 of the 14 longest losses (rift both sides against all three opponents,
minimaze, pipes).

**Result — REJECT.**

    benchmarks         8/162  ->  5/162
    close-spawn wins    4/42  ->   4/42   (guard held)
    early wipes                 12/157 = 8%   (guard held)

**Why it loses, and it is not that the idea is wrong.** Look at when
`bench_finalist` does it: round ~826, holding 1912 cheese and 16-20 rats, from an
already-winning position — we were down to 3 rats at the time. It converts
*surplus* into a second King. Our gate (round 200, 600 cheese, 12 live rats)
fires far earlier and much poorer, and spends 8 of ~13 rats — most of the army —
which accelerates exactly the economic collapse that was already killing us.

**A second King is a win-more move, not a comeback move.** That is the finding,
and it is testable rather than a story: the gate should sit where the reference
bot's does. Iteration 147.

**Useful by-product: population is readable exactly, for free.** The engine sets
`getCurrentRatCost() = 10 + 10 * (live babyRats / 4)`, so
`4 * ((getCurrentRatCost() - 10) / 10)` is a lower bound on our live rats with no
counting and no sensing. Nothing in the bot had ever read our own population.

### Tooling: the Gauntlet now reports thrown exceptions

Bug 1 above was found by accident. `run()` catches `GameActionException` per
turn, so a throw silently abandons the rest of that robot's turn, every turn, and
the only trace is a server-log line the Gauntlet was discarding — 162-game runs
were going by with nobody looking. `gauntlet.sh` now counts them per game and
prints the total **above** the loss list, because a nonzero count means the win
rate is measuring a bug rather than the change.

## Iteration 147 — lift the population cap from surplus — **ACCEPTED** (g_iter24)

The first accept since g_iter23, and it came from decomposing losses rather than
from a new mechanism.

### How this was found: three dead ends that each narrowed the target

Iteration 146's follow-up was supposed to be "re-gate the second King to where
`bench_finalist` sets it". Two things killed that **without spending a run**:

1. I predicted we would never reach their surplus state (16+ rats, 1500+ cheese,
   after round 700). Measured over the 8 longest baseline losses, we reach it in
   **five** — on `rift` we hold 43-53 rats and 3000-7700 cheese and still lose.
   The premise was wrong.
2. Computing the actual scoring formula on each game's final stats, a second King
   is worth **+10.0 to +16.7 points** against margins of **-33 to -64**. Not one
   of the eight flips. Even three extra Kings would not close it.

I also had the kings term backwards: a -16.7 margin at weight 0.5 means our share
is **1/3, not 1/2** — *they* already field two Kings, and one game's -33.3 means
five. A second King would be catching up, not pulling ahead.

Decomposing those eight points-losses by scored term:

    catDamage   -25.4 pts    (the largest)
    kings       -17.9
    cheese       -7.3

So I tried cat-seeking again, gated on surplus (Iteration 142 had been rejected
unconditionally). Same map, opponent and side as the baseline:

                     catDamage       our rats
    g_iter23        1920 / 22080        14
    surplus-gated   2784 / 21216         3     (+45%)

The gate worked and the mechanism fired — and it was still hopeless, because
+864 damage is **+2.2 points** against a -25.4 gap. Closing an 8x share gap needs
a 10x increase, not 1.45x. **A large relative gain on a term you hold 12% of is a
small absolute gain.**

### What the control actually showed

That comparison required a same-map control, and the control read
`aliveBabies=[3,106]`. Tracing the whole game:

    round      our rats / cheese      their rats / cheese
      125        21 / 1359               16 / 1945
      525        29 / 1231               33 / 1940
     1125        12 / 2790               67 / 1908
     1925        14 / 1598               81 /  478

**They compound; we do not.** Their cheese sits flat near 1900 because they spend
everything they earn. Ours oscillates 1000-2800 *permanently unspent* while our
army never passes ~29. At round 1125 we held 2790 cheese and twelve rats and
would not convert. We were not starving in these games — we were refusing to
spend, and the constraint was our own:

    final int MAX_POPULATION = 25;      // builtCount < MAX_POPULATION

`builtCount` is cumulative-ever-built and resets each 400-round window, so a flat
25 caps us at 25 builds per window however deep the treasury is.

### The change

    final int MAX_POPULATION = rc.getGlobalCheese() > 1500 ? 60 : 25;

`REPLACEMENT_RESERVE` deliberately untouched — at 25 live rats
`getCurrentRatCost()` is 70, so `2790 - 70 >= 1000` passes easily and the reserve
is not what binds. Changing both would have left it unknown which mattered.

**Why this is not a seventh repeat.** Iterations 111/112/113/114/120/125 all
pushed population and were rejected, root-caused to the cost curve plus King
starvation. That root cause is real, and this respects it: every one of those
raised the cap *unconditionally*, so it also fired in the 128 short King-kill
games where cheese genuinely is scarce. Gating on held cheese lifts the cap only
when the treasury is provably deep. **The dose is on WHEN, not on how much.**

**Mechanism check — FIRED**, both pre-registered conditions:

                    peak rats     cheese band
    g_iter23            29        1000-2800 (banked)
    iteration 147       42          210-1485 (spent)

### Result — ACCEPT

    benchmarks              8/162  ->   8/162    (flat)
    close-spawn wins         4/42  ->    4/42    (guard held)
    early wipes              12/154 = 8%         (guard held, same maps)
    g_iter23 head-to-head   32/54 = 59.3%        (+5 games over even)

Flat on the lopsided instrument, clearly positive on the even one, guards intact,
mechanism confirmed. This is the case `peers-and-benchmarks-play-different-games`
and `even-matchups-have-resolution` describe: benchmarks win rate ~5% cannot
resolve a change that does not flip a whole game, while a true mirror sits at 50%
by construction and can. **The surplus gate is the part that works** — it is the
first population change that did not break the close-spawn guard, and it broke
none of it.

Honest limits: this did not flip a single benchmark game, and the eight
points-losses remain 33-64 points adrift. It compounds our economy without
closing the gap that decides those games.

**vs_old_bots after g_iter24** (270 games, roster = every 5th snapshot):

    overall     212/270 (78.5%)
    g_iter1      49/54 (91%)
    g_iter6      45/54 (83%)
    g_iter11     51/54 (94%)
    g_iter16     33/54 (61%)     -- was 48% at g_iter23
    g_iter21     34/54 (63%)

`g_iter16` moving 48% -> 61% is the notable line. It is the trapless build, and
the log has expected it to sit "near or below 50% indefinitely" as the price of
the Iteration 96 trap trade. Population compounding appears to pay in exactly the
long games where a trapless opponent was previously winning on points.

Sample game archived as `replays/iter147_g_iter23_closeup_botA.bc26` — a mirror
win over the previous accepted build, chosen over a benchmark win because
benchmarks were flat (8/162 both) and the benchmark wins are identical to
baseline, so none of them is attributable to this change. It ends
`catDamage=[2918,948]`, a 3x advantage over `g_iter23` on the term we are most
behind on against real opponents: more rats, more bites.

## Iteration 148 — dose-response on the population cap — VOID (the cap never binds)

Resolved **without a Gauntlet run**, by two single matches.

I first moved both knobs at once — cap 60 -> 100 and gate 1500 -> 1000 — which
was sloppy, and the mechanism check caught it: peak rats on rift went *down*,
42 -> 37, and `cheeseTransferred` fell 12745 -> 9733. Isolating the cap knob
alone settled it:

    cap  25, flat        peak rats 29
    cap  60, gate 1500   peak rats 42     <- g_iter24, accepted
    cap 100, gate 1500   peak rats 42     <- IDENTICAL GAME
    cap 100, gate 1000   peak rats 37     <- worse

The `cap 100 / gate 1500` game is byte-identical to g_iter24's — same final
`cheese=210`, same `aliveBabies=[7,104]`, same `catDamage=[1970,22030]`, same
`cheeseTransferred=12745`. **`MAX_POPULATION = 60` is never reached, so raising
it is a no-op**: two arms that never differed, which is exactly
`a-dose-must-change-the-evaluated-condition`. VOID.

**What actually binds is the cheese gate**, because it sets an equilibrium: we
build until cheese falls through the gate, the cap reverts to 25, and building
stops until the 400-round window resets `builtCount`. Lowering the gate lowers
that equilibrium and, measured, costs both rats and delivered cheese. So
g_iter24 sits at the optimum of both knobs and this direction is closed.

**The remaining ceiling is not build permission — it is rat survival.** Even
building freely we end rift with 7 rats against 117. We are converting cheese
into rats that die faster than they accumulate, while the opponent's army
compounds monotonically (16/33/67/81/117) on a flat treasury. That is the next
question, and it is a different one: not "may we build?" but "why does nothing we
build survive?"

### Why nothing we build survives: the facing trap

Following Iteration 148's closing question — not "may we build?" but "why does
nothing we build survive?" — the death attribution on rift (g_iter24):

    our 147 rat deaths
      self-attributed   76  (52%)   -- landing damage from being thrown
      by cats           40  (27%)
      by enemy rats     31  (21%)

    their grabs of our rats    168
    their throws of our rats   158

**Throwing kills more of our rats than cats and enemy bites combined.** 158
throws at ~42 damage is ~6600 damage, or sixty-six rats' worth at 100 HP. The
population ceiling is not economic at all.

The engine's grab rule is where this becomes structural. `canGrab` is satisfied
if **either** the target cannot sense the grabber (it is facing away) **or** the
grabber has more HP (`HEALTH_GRAB_THRESHOLD = 0`). So a full-HP rat that can see
its attacker is *ungrabbable* — facing is a necessary condition. Two more engine
facts complete the trap:

    move() never calls setDirection -- only turn() changes facing
    addMovementCooldownTurns(d): BABY_RAT moving off-facing pays
        MOVE_STRAFE_COOLDOWN 18 instead of movementCooldown 10

Our `tryMove`/`tryMoveDirect` already turn to face the direction of travel, which
is *correct* — otherwise every step would cost 1.8x. But the consequence is that
**a travelling or fleeing rat always has its back to whatever is chasing it**, and
is therefore always grabbable. Fleeing is what makes us grabbable.

That is also why Iterations 143 and 144 hit a wall from the other side: we cannot
grab the rats grabbing us, because being grabbable and being blind are the same
condition, and we cannot turn to face a threat we cannot sense inside a
90-degree cone.

So the trade is now explicit and quantified rather than hand-waved: face your
pursuer and become ungrabbable at full HP, but move at 10/18 = 0.55x speed; or
flee at full speed and stay grabbable. No previous iteration knew this trade
existed, and `MOVE_STRAFE_COOLDOWN` had never been read.

## Iteration 149 — damaged rats retreat into the trap ring — REJECTED (guard broken)

Aimed at the facing-trap finding: 52% of our rat deaths are landing damage from
being thrown. A **damaged** rat is undefendable in the open — `canGrab` succeeds
if the target faces away OR the grabber has more HP, and rats do not heal, so
facing cannot save it. Home is different because the King sits in a rat-trap ring
(50 damage, 30-round stun), so a pursuer has to cross it.

**Dose curve measured on rift before spending any run** (same map, opponent and
side as g_iter24):

    variant                throws of us   deaths   cheeseXfer   King survived to
    g_iter24 (none)             158         147      12745          r2000
    hp < 30                     129         128      11195          r1925
    hp < 50                      97         102       9645          r1800
    hp < 50 + cheese > 1000     146         136      13380          r2000

Ungated retreat is monotone **in the wrong direction for the outcome**: it buys
fewer throws by starving the King — cheese to 0 and the King dead before r2000,
which is our dominant loss mode. `rat-turn-mechanisms-are-priced-per-capita`
again: a retreating rat stops earning.

The surplus gate removed that cost and, on the control map, was strictly better
than g_iter24 on every line — fewer throws, fewer deaths, *more* cheese delivered
(13380 vs 12745), more catDamage, no starvation.

**Result — REJECT on the guard.**

    benchmarks          8/162  ->  8/162    (flat)
    close-spawn wins     4/42  ->   2/42    (BROKEN)
    early wipes          8%    ->   9%      (wipe maps gained evileye, toomuchcheese)

**Why the gate failed, and I predicted the opposite.** I wrote that a
`cheese > 1000` gate "should protect close-spawn games, which never reach 1000".
They do — **every map starts at ~2488 cheese**. The gate is on *current* treasury,
so it is wide open from round 1, and damaged rats retreat during the opening rush
precisely when we cannot spare them. Iteration 147's population gate has the same
property and is harmless there, because building more early is fine; retreating
early is not.

**The lesson is about gate variables, not about retreat.** A gate on a quantity
that is large at spawn is not a late-game gate. Iteration 147's cat-seek design
had both a cheese gate *and* `round >= 300`, and I dropped the round clause here
without noticing it was doing separate work. Close-spawn games are decided long
before round 300 — the fastest losses are rounds 19-40 and every wipe is before
round 100 — so a round gate excludes exactly the games this broke.

## Iteration 150 — retreat with the round gate Iteration 149 was missing — REJECTED

One clause added: `rc.getRoundNum() >= 300`.

**The guard prediction was exactly right, which is the useful part.**

    metric               g_iter24   iter149 (no round gate)   iter150
    benchmarks             8/162           8/162               8/162
    close-spawn wins        4/42            2/42                4/42
    early wipes               8%              9%                  8%
    wipe maps            kf5 t4 th2 dc1   +evileye +toomuch   kf5 t4 th2 dc1

Close-spawn wins and the wipe profile return **exactly** to g_iter24's. So
Iteration 149's damage really was the opening-rush retreat, caused by gating on a
quantity that is large at spawn (~2488 cheese on every map), and the round clause
is the right fix for it.

**But fixing the guard did not make the change good.**

    g_iter24 head-to-head    25/54 = 46.3%

Below even. Benchmarks flat, guards intact, and the mirror — the instrument with
the most resolution — says slightly worse than the build it replaces. **REJECT.**

Worth stating plainly against Iteration 147, which had an identical benchmark
reading and was accepted:

    iteration    benchmarks    guards    mirror       verdict
    147            8/162       intact    59.3%        ACCEPT
    150            8/162       intact    46.3%        REJECT

Same lopsided-instrument result, opposite decisions, and the mirror is the only
thing that separates them. That is the rule from `even-matchups-have-resolution`
doing real work in both directions rather than only when convenient.

**Why retreat fails even when free.** The dose curve had shown the surplus-gated
version strictly better than g_iter24 on the rift control — fewer throws, fewer
deaths, more cheese delivered. Adding the round gate kept the guard but weakened
that: `cheeseTransferred` fell to 11425, below both iter149's 13380 and
g_iter24's 12745. The retreat benefit lives in the early-and-middle game where
damaged rats are numerous, which is precisely the window the round gate removes
to protect close-spawn. **The clause that makes retreat safe is the clause that
makes it worthless** — the two effects are not separable by this gate, so the
direction is closed rather than merely untuned.

The facing-trap finding itself stands: 52% of our rat deaths are throws, and a
damaged rat is undefendable by engine rule. What is now ruled out is *withdrawing*
the damaged rat as the answer.

## Iteration 151 — turn before stepping (kill the strafe penalty) — REJECTED on representativeness

Found while doing Iteration 152's reachability pre-check. Adding `--turns` to
`ReplayDump` exposed `Turn.x/y/dir`, which had never been read, and comparing
actual displacement against reported facing showed **3094 of 7048 rat moves
(43.9%) were strafes**.

`addMovementCooldownTurns(d)` charges a Baby Rat `MOVE_STRAFE_COOLDOWN` 18
instead of `movementCooldown` 10 whenever `dir != d`, and `move()` never changes
facing. `tryMove`'s main path already turned first, but every *fallback* —
both sidesteps, the stuck-escape shuffle, the Bug2 boundary-follow, and
`tryMoveDirect`'s fallback — called `rc.move(d)` raw, so a blocked rat paid the
1.8x penalty on the step it actually took.

Fixed with a `stepTo(rc, d)` helper that turns then moves forward. The first
version left 30.4% strafes; the residual was **my own preamble**, which turned
toward `want` *before* checking it could move there, spending the round's single
turn on a direction it then did not take, so the sidestep found `canTurn` false
and strafed anyway. `stepTo` tests `canMove` first, so deleting the preamble was
strictly better.

**Mechanism — fully achieved.** Measured in the *same game* (both builds playing
each other, so it is a true control):

    build              moves    strafe
    g_iter24          10802     50.6%
    iteration 151     13658      0.0%

Zero strafes, and **26% more moves in the same game** because cooldown clears
faster.

### Result — REJECT, and the two instruments disagree completely

    strafe share        benchmarks    mirror vs g_iter24
      46.2% (g_iter24)     8/162          --
      30.4%                7/162        55.6%
       0.0%                6/162        75.9%

**Both curves are monotone, in opposite directions.** Eliminating the strafe
penalty is worth +14 games over even in the lineage — 3.8 sigma, the largest
mirror result this project has produced — and costs two benchmark wins.

This is Iteration 82/96 again, and the log's own words apply verbatim: *"a
defensive feature is free to remove on an instrument that never poses the threat
it defends against. RESOLUTION AND REPRESENTATIVENESS ARE DIFFERENT PROPERTIES,
and the mirror has only the first."* Here it is the mirror rewarding a feature
whose cost only real opponents impose.

**Why speed helps in the mirror and hurts against benchmarks.** In a mirror both
sides run the same policy, so 26% more movement is 26% more cheese collection and
the economy race decides it. Against benchmarks 91% of games end in King
destruction, and our rats' problem is not that they arrive slowly — it is that
they die when they arrive (52% to throws, per the facing trap). Faster movement
delivers rats into contested ground sooner without changing what happens to them
there. Early wipes rose 8% -> 9% and the fastest losses tightened to rounds
20-26, which is that mechanism visible.

**An honest caveat about g_iter24.** Iteration 147 was accepted on flat
benchmarks (8/162) plus a 59.3% mirror, and this result shows the mirror can
strongly reward economy-and-speed changes that benchmarks do not. g_iter24 is not
*harmful* — its benchmark score is unchanged and `vs_old_bots` rose — but its
evidence is weaker than it looked on the day, and it should not be treated as
proven against real opponents. **The rule going forward: a mirror win can
break a tie when benchmarks are flat and guards hold, but it cannot buy benchmark
losses.** Iterations 147 and 151 sit on opposite sides of exactly that line.

`stepTo` is reverted with the rest, but the measurement stands and the tooling to
repeat it is committed: `replay-dump.sh --turns <team>` plus the strafe analysis.

## Iteration 152 — rotation sweep on blocked turns — VOID, and it closes the facing direction

The other exit from the facing trap: we cannot turn toward a threat we cannot
sense, so sweep the cone instead. Rotate one step whenever the rat is going to
stand still anyway.

**The design was genuinely free, and reachable.** Cooldowns are three separate
counters, so `!isMovementReady()` means the rat cannot move this turn whatever it
decides — the turn spent rotating had no other use and cannot make a later step
more expensive. Measured before building, per the pre-registration:

    rat-turns                     11730
    movement blocked next turn     3177   27.1%
    turning available             11730  100.0%

**Mechanism check — VOID.** Pre-registered: *their grabs-of-us on rift must fall
from 168.*

    their grabs of us   168 -> 169
    their throws of us  158 -> 161

Neither fell. No Gauntlet run spent.

### Why, and this closes the direction rather than just this attempt

`canGrab` has two clauses — the target cannot sense the grabber, **or** the
grabber has more HP. The new `--turns` dump carries per-robot facing and health,
so each grab can be attributed to the clause that actually enabled it:

    grabs of our rats analysed   169
      FACING-AWAY only             0    0.0%
      WEAKER-HP only              58   34.3%
      BOTH (blind and weaker)     62   36.7%
      unresolved (stale state)    49   29.0%

**Not one grab was enabled by facing alone.** Every blind victim was also the
weaker rat, so the facing clause is never load-bearing and no amount of turning,
sweeping or scanning can prevent a single grab in this game. The sweep did
exactly what it was designed to do and could not have helped.

(The 29% unresolved are grabs where the most recent recorded turn predates the
grab within the round, mostly because HP changes mid-round. They do not affect
the conclusion, which rests on the zero.)

**So the facing trap is really the HP trap.** A damaged rat is grabbable however
it faces, and rats do not heal. Both exits are now closed by measurement:

    withdraw the damaged rat   Iterations 149/150 -- the clause that makes
                               retreat safe is the clause that makes it worthless
    let it see the attacker    Iteration 152 -- facing is never the sole enabler

What remains is upstream: stop rats becoming damaged in the first place. Note the
causal chain that implies, because it runs against an accepted feature — our rats
deliberately engage cats (worth +11 games when added), a cat scratch is 20
damage, and a scratched rat is thereafter permanently grabbable and worth 42
damage plus a stun to the opponent. Iteration 124 tested engaging cats *less* and
lost 4/162, but that was measured before any of this was understood, and
`ablate-accepted-features-on-the-mirror` says the headline number at acceptance
predicts little.

### CORRECTION to the Iteration 152 clause attribution

The entry above claims *"not one grab was enabled by facing alone"*. **That is
wrong, and it overstates a result in the direction that closes a research
direction — the worst way to be wrong.**

My first pass bucketed 29% of grabs as "unresolved (stale state)". Those are not
noise: they are precisely the facing-enabled grabs. The check that exposed it is
one line — a grabber cannot exceed 100 HP, so a victim recorded at full 100 HP
cannot have been taken by the HP clause, and there are 32 of those. Redoing it
directly on the HP comparison rather than my cone geometry:

    grabs of our rats                       169
      HP clause sufficient (victim weaker)  119   70.4%
      HP clause NOT sufficient               50   29.6%   <- facing enabled these

Of the 32 full-HP victims, 21 were grabbed by a *strictly weaker* rat.

**Corrected conclusion.** The HP clause dominates at 70%, but facing is
load-bearing for roughly 30% of grabs. The facing direction is therefore **not**
closed by engine rule, as I wrote — it is closed only for the *sweep*, and
empirically: grabs went 168 -> 169.

**Why the sweep failed anyway, now that the reason is not "impossible".** A blind
rotation faces a random direction, not the attacker's. Sweeping raises the chance
of seeing *something* over eight turns, but a grab needs the victim facing the
grabber at that instant, and the sweep is uncorrelated with where the grabber
actually is. It also fires only on movement-blocked turns, which are not
especially when grabs happen. So the mechanism was free and reachable and simply
does not aim.

That leaves a real, still-open target worth about 30% of grabs: a *directed*
turn toward a known threat, rather than a blind sweep. It needs detection to come
from somewhere other than the rat's own cone — the King sees 360 degrees and
already writes the shared array, which is the obvious untried channel.

The rest of the Iteration 152 entry stands: the mechanism check was a clean VOID,
no Gauntlet run was spent, and Iterations 149/150 independently closed
withdrawing the damaged rat.

## Iteration 154 — what actually kills our King (measurement, no code change)

83% of benchmark losses (128 of 154) end with our King destroyed between rounds
100 and 1999, and that damage had never been attributed — every claim about it
came from a single Iteration 128 replay. Sampled six King-destruction losses from
the g_iter24 run, stratified across all three opponents and the round bands
100-499 / 500-999 / 1000-1999.

**Method note: King damage is NOT emitted as a `DamageAction`.** All six replays
contain zero such lines targeting a King. The hp curve has to be read from the
new `--turns` dump instead, which is why this was not measurable before today.

**Starvation vs combat**, partitioned by the treasury at the moment of each drop:

    King hp lost while treasury <  50   1220   34.3%
    King hp lost while treasury >= 50   2335   65.7%
    per replay: 2 of 6 STARVED, 4 KILLED while fed

**What deals the combat 65.7%**, from the per-round drop sizes:

    -10 hp  x123   52.7%   RAT_BITE_DAMAGE
    -20 hp  x 19   16.3%   CAT_SCRATCH_DAMAGE
    -30/-38/-42/-52 hp     ~20%   multiple attackers in one round

**Enemy rat bites are the single largest killer of our King** — about half of
combat damage, plus most of the multi-attacker rounds. Starvation is second at a
third of all damage. **Cats are ~16% of combat damage, i.e. roughly 11% overall.**

Two things worth flagging honestly:

1. **Iteration 128's reactive cat traps were accepted off one replay** showing a
   cat parked beside the King grinding it at 20 per 3 rounds. That trace was
   real, but this sample says cats are the *smallest* of the three sources. The
   feature is defensible on its measured +1 game, but the story attached to it
   over-generalised from n=1.
2. **The reserves do not prevent starvation.** `RESERVE` 150 and
   `REPLACEMENT_RESERVE` 1000 gate *building*, while the King's 2/round upkeep
   continues unconditionally. So once income stops the treasury drains to zero
   regardless of any reserve, which is why a third of King damage happens at
   cheese < 50. Starvation is downstream of the army dying, not a separate
   spending bug.

Two false starts, both caught before they became findings: I first attributed
100% of damage to "unattributed" (King damage is not a DamageAction), then read
a -10/round signature as starvation when `RAT_BITE_DAMAGE` is also 10 and three
of the six had 780-1030 cheese at death. The treasury partition is what separates
them.

## Iteration 151, REOPENED — turn before stepping — **ACCEPTED** (g_iter25)

**This reverses the rejection recorded above.** The user's rule: *if benchmark and
mirror contradict, break the tie with a full peer evaluation* — and the peers say
the change is strongly good.

    instrument                     g_iter24      iteration 151
    benchmarks                       8/162            6/162
    g_iter24 head-to-head              --             75.9%   (+3.8 sigma)
    peers, full mapset, 108 games   69/108 (63.9%)   87/108 (80.6%)   +18 games
      vs pure_cooperator            23/54  (43%)     35/54  (65%)
      vs immediate_defector         46/54  (85%)     52/54  (96%)

Peers were re-synced from g_iter24 first — stale for a fourth time, missing
Iteration 147 — and the control was run on the same rebuilt archetypes and the
same map set, so only the delta is being read.

**+18 of 108 on a paired set is roughly 3.5 sigma.** Note especially
`pure_cooperator` moving 43% -> 65%: the control was *below* 50% against its own
near-mirror, which is the definition of a policy disadvantage, and this removes
it.

### The benchmark -2 was noise, and I can now show it rather than assert it

The user's second point — *a modification that lets us move more freely should be
a good thing, so if it looks bad in benchmarks that is worth investigating* — was
the right instinct. Diffing the two 162-game runs game by game:

    8 games changed result: 3 gained, 5 lost

    bench_finalist  peaceinourtime   A   loss -> WIN
    bench_spaark    popthecork       B   loss -> WIN
    bench_stroke    popthecork       A   loss -> WIN
    bench_finalist  dirtfulcat       B   WIN  -> loss
    bench_finalist  peaceinourtime   B   WIN  -> loss
    bench_spaark    popthecork       A   WIN  -> loss
    bench_stroke    uneruesansfin    A   WIN  -> loss
    bench_stroke    whatsthecatdoin  A   WIN  -> loss

**`popthecork` and `peaceinourtime` each flip in BOTH directions depending on
side.** That is churn in games sitting on a knife edge, not systematic harm, and
a net -2 at an 8/162 base rate is 0.8 sigma. My "monotone 8 -> 7 -> 6 dose curve"
was three noisy points, each of which churned around eight games in both
directions; reading monotonicity into it was the error.

**And my stated mechanism for the harm was wrong too.** I hypothesised that
re-aiming the vision cone along travel would reduce what our rats see. Traced on
knifefight (the worst wipe map) against `bench_stroke`, same opponent both arms:

    g_iter24   lost round  73   our RatAttack 13   rat traps laid  7
    iter151    lost round 100   our RatAttack 19   rat traps laid 10

We survive *longer* and attack *more*. The sensing story had it backwards.

### What this corrects about method

`mirror-wins-cannot-buy-benchmark-losses` was written off this very iteration and
was too strong. The defensible version is: **a mirror win cannot buy benchmark
losses that are real.** Establishing that a benchmark delta is real means looking
at which games moved — 8 flips both ways is not the same evidence as 2 games
moving one way, even though both print as "-2". Iteration 82/96 remains a genuine
representativeness failure because early wipes *doubled*, a directional guard
breaking, not a reshuffle.

The mechanism, unchanged from the rejected entry: `stepTo()` turns to face a
direction before stepping, so a step costs `movementCooldown` 10 rather than
`MOVE_STRAFE_COOLDOWN` 18. Verified in a single shared game — 0.0% strafes against
g_iter24's 50.6%, and 26% more moves.

**vs_old_bots after g_iter25** (270 games, roster = every 5th snapshot):

    overall     232/270 (85.9%)     -- was 212/270 (78.5%) at g_iter24
    g_iter1      51/54 (94%)   was 91%
    g_iter6      50/54 (93%)   was 83%
    g_iter11     50/54 (93%)   was 94%
    g_iter16     38/54 (70%)   was 61%
    g_iter21     43/54 (80%)   was 63%

Every line improves except `g_iter11`, which moves one game. `g_iter21` gaining
17 points and `g_iter16` nine is a broad, independent confirmation of the peer
result — these are frozen snapshots that cannot drift.

Sample archived as `replays/iter151_bench_spaark_popthecork_botB.bc26`: one of
the three **benchmark games this iteration turned from a loss into a win**
(`bench_spaark popthecork botB`, loss at r164 -> win at r570). Reproduced and
verified against that pairing's `results.csv` row before copying.

## Iteration 155 — ablate the reactive cat traps — NEUTRAL, and Iteration 128 is DORMANT

Motivated by Iteration 154's damage attribution: cats are ~11% of all King damage,
the smallest of the three sources, while a cat trap costs a King action and draws
on the team-wide `CAT_TRAP.maxCount` of 10.

**Result — exactly neutral, on every instrument:**

    benchmarks           6/162 -> 6/162,  and ZERO of the 162 games changed result
    g_iter25 mirror      28/54 = 51.9%   (+1 game over even, 0.27 sigma)
    close-spawn wins      3/42 -> 4/42
    early wipes                13/38 on close-spawn, same wipe maps

Not merely the same score — **the identical outcome in all 162 games.** That is a
much stronger statement than a matching total, and it has a simple explanation:

    OUR cat trap placements, across all 8 sampled g_iter25 losses:  0, 0, 0, 0, 0, 0, 0, 0

**The feature never fires in the games we lose.** Its trigger needs a cat within
d^2 20 of the *King*, and the King's own vision is radius^2 25, so a cat has to
walk within ~4.5 tiles of our King — which on most maps never happens. This is
the same dormancy Iteration 137 found from the other side ("no cat comes within
d^2 20").

**So Iteration 128 is dead weight.** It was accepted on +1 benchmark game, which
by the standard established this session is 0.4 sigma — indistinguishable from
noise — and the story attached to it came from a single replay
(`peaceinourtime`). Iteration 154 then showed that story describes the smallest
damage source, and this shows the code does not run in the games that matter.
Even on `peaceinourtime` itself, the ablated build still wins at r511, identical
to baseline.

**Reverted anyway.** The ablation is costless but not beneficial, and the
standing rule is that the accepted build changes only on measured improvement.
The deliverable here is the finding, not the diff. What the finding is worth is
that it invalidates a *reason* — no future iteration should reason from "we have
reactive cat traps" or cite Iteration 128's cat-grinding story as characteristic.

### The wider lesson: audit accepted features for dormancy

Three separate accepted features have now turned out to be inert or near-inert
when finally measured — the desperation raid (Iteration 138, gated off), the
desperation flag itself (Iteration 141, "exactly inert"), and now Iteration 128.
All three were accepted on 1-2 game benchmark moves before the noise floor was
understood. A 1-2 game move at an 8/162 base rate is under 1 sigma, so **a
meaningful fraction of the accepted feature list may be noise**, and dormant code
is worse than useless because it supplies confident-sounding explanations for
behaviour it never produced.

### King damage re-measured on g_iter25

Repeating Iteration 154's attribution on the current build, eight losses:

    starvation   2020   42.4%   (was 34.3% on g_iter24)
    combat       2746   57.6%   (of which -10 bites 42.6%, -20 scratches 18.2%)
    4 of 8 losses are primarily STARVATION (was 2 of 6)

Starvation has grown, which looked at first like the bill for Iterations 147 and
151 both pushing toward more spending. **It is not.** Checking how many rats we
had alive during the starving rounds:

    bench_finalist closeup   75 starving rounds, our rats: 0.0
    bench_spaark   closeup   75 starving rounds, our rats: 0.0
    bench_spaark   pipes     75 starving rounds, our rats: 0.0
    bench_finalist corridor 100 starving rounds, our rats: 4.0

**In three of four the army was already at zero.** Starvation is not an economic
mismanagement mode we can fix by spending less; it is the terminal phase after
the army is wiped, with the King bleeding `RAT_KING_HEALTH_LOSS` 10/round until
it dies. Every King-damage route therefore leads back to army survival.

Also verified rather than assumed, since two iterations rested on it:
**`addHealth` clamps to max health and has no positive call sites anywhere in the
engine — there is no healing.** A damaged rat is permanently damaged, so the HP
clause that enables 70% of grabs can never be undone once taken.

And `bench_spaark pipes` is the whole problem in one line: **92 rats built, zero
alive at the end.** We can out-produce; we cannot keep anything alive.

## Iteration 156 — dormancy audit of the accepted feature list (measurement)

Counting the proving action for each accepted feature, across the eight g_iter25
benchmark losses.

    feature                          fires per game        verdict
    ---------------------------------------------------------------------
    King rat-trap ring (48/96/102)   25.4 placed           LIVE
    population cap from surplus      spawns 34-92 vs a     LIVE
      (147, g_iter24)                base cap of 25
    King digging                     dirt +2 to +77        LIVE
    cat engagement (5/6, 103-105)    194 catDamage         LIVE but small
    reactive cat traps (128)         0, 0, 0, 0, 0, 0, 0, 0   DORMANT
    becomeRatKing (106)              0 (kings never 2)     DORMANT

Two of the audited features never run. That is now **five** accepted features
found inert on measurement — 106, 128, 138, 141, and the raid — all accepted on
1-2 game benchmark moves, which at an 8/162 base rate is under 1 sigma.

### The finding that matters: we barely fight, and lose badly when we do

    ours vs theirs, same eight games      ours    theirs
    RatAttack (bites)                      369      2346     they attack 6.4x more
    enemy rats killed                        7       347     0.02 : 1 exchange

**We kill seven enemy rats and lose 347.** On `closeup` they attack 13x more than
we do. A bite is 10 damage against 100 HP, so killing a rat takes ten landed
bites; a throw is 42 and they land 158 per game. That asymmetry is the whole
population race, and it is why `bench_spaark pipes` reads *92 rats built, zero
alive at the end*.

**Our traps are our best weapon by a wide margin**, once the attribution is done
correctly: enemy robots trigger our traps 180 times across the eight games
(~9000 hp at RAT_TRAP's 50), against 3690 hp from all 369 of our bites combined.

### Enemy traps hurt us more than our traps hurt them, and we cannot do anything about it

    our robots triggering THEIR traps    420   (~21000 hp)
    enemy robots triggering OUR traps    180   (~9000 hp)

On rift they place 73 rat traps to our 39 and our rats walk into 72 of them.
**But enemy traps are invisible to us**: `getMapInfo` calls
`gw.getTrap(loc, this.getTeam())`, which indexes `trapLocations[team.ordinal()]`,
so `MapInfo.getTrap()` only ever reveals our OWN traps. Trap avoidance is not
implementable — closed before writing any code.

### Three attribution errors caught in this audit alone

Worth recording as a pattern, because each would have been a confident finding:

1. `DamageAction` is **not** emitted for bites, so "our rats never damage an
   enemy rat (0 per game)" was an artefact. Bites appear only as `RatAttack`,
   which carries no target. Same shape as Iteration 154's discovery that King
   damage is not a `DamageAction` either.
2. `TriggerTrap`'s actor is the robot that **stepped on** the trap, and the
   engine skips your own team's traps — so `(team1,...) TriggerTrap` counts our
   rats dying on *their* traps, the exact opposite of the "our trap ring is
   working" reading I first wrote.
3. Trap damage and throw-landing damage are both self-attributed in `DieAction`,
   so the earlier "52% of rat deaths are throws" figure silently included trap
   deaths. On rift the split is 161 throws against 72 enemy-trap triggers.

Three actor/victim confusions in one session, all in the same family as the
Iteration 108 retraction. **Before counting any action, check which end of it the
id refers to and whether the engine emits it at all.**

## Iteration 157 — rats lay rat traps — VOID on the effect check, no Gauntlet spent

Motivated by the Iteration 156 audit: traps are our best weapon by a wide margin
(~9000 hp from enemy trigger events against 3690 hp from all 369 of our bites),
and they out-trap us 73 placements to 39 on rift.

**Not a repeat of Iterations 116/117.** Both tried this and were VOID because the
code never fired, so neither tested the idea. `assertCanPlaceTrap` names both
bugs: *"Can't place trap on an occupied tile"* (117 targeted the tile the placing
rat stood on) and `cheese >= buildCost` where buildCost is 20 and `getAllCheese()`
is raw + team (116 gated on cheese > 1000, above our own measured treasury). This
time the eight neighbours are scanned with `canPlaceRatTrap()`, which checks
occupancy, passability, existing traps, cheese mines and the cap.

**The mechanism fired. The effect did not.** Two arms, same map/opponent/side
control:

    metric                        g_iter25   arm A: anywhere   arm B: band
    King ring placements                39                32            27
    RAT-placed traps                     0                13            14
    enemy triggers on our traps         23                23            17
    our rat deaths                     135               154           180

Pre-registered: *enemy trigger count must rise*. Arm A left it exactly unchanged
while laying 13 traps; arm B, which placed in a band just outside the King's own
reach on the theory that traps only pay where the enemy must go, made it **worse**
on every line. VOID, and no 162-game run spent on either.

**The shared pool was the constraint, exactly as `measure-the-shared-pool-first`
predicts.** `RAT_TRAP.maxCount` is 25 team-wide, and every trap a rat places is
one the King cannot: ring placements fell 39 -> 32 -> 27 across the arms while
total triggers fell 23 -> 23 -> 17. This is the same failure that killed
Iterations 130/134/135 with cat traps, and I instrumented the pool in the first
arm this time rather than after three attempts.

**The positive finding, which is the useful part:** the ring works *because of
where it is*, not because traps are good. Enemies converge on our King, so traps
there are the only ones that get stepped on; traps anywhere else are wasted
cheese and wasted cap. That also explains why the ring saturates — only ~21 tiles
sit within `RAT_KING_BUILD_DISTANCE_SQUARED` 8 of the King, and Iteration 102
already found 3:1 density worse than 2:1.

So the trap budget is fully deployed and correctly positioned, and trap-based
avenues are now closed in both directions: we cannot avoid enemy traps either,
since `MapInfo.getTrap()` only reveals our own (Iteration 156).

## Iteration 158 — focus fire on the weakest enemy — VOID, no Gauntlet spent

From the Iteration 156 audit: 369 bites produce seven kills across eight games.
`RAT_BITE_DAMAGE` is 10 against 100 HP, so a kill needs ten landed bites on the
same target, and choosing by proximity smears 3690 hp of damage — in principle
thirty-six rats — across everything in sight.

**Two arms, both measured against a matched g_iter25 control on the same three
maps and opponent:**

                              kills   attacks   our deaths
    g_iter25 control              6       216          178
    arm A: global weakest         2       113          148
    arm B: weakest IN RANGE       3       162          195

Arm A halved our attack count, and the reason is a real design error worth
recording: ranking by health *globally* sends a rat walking toward a distant
wounded enemy instead of biting the healthy one already beside it, so it spends
turns travelling rather than attacking. Arm B fixed that — an in-range target
always outranks an out-of-range one, and weakness only breaks ties among
reachable ones — and recovered most of the attack volume.

**Neither raised kills.** Pre-registered bar was *kills must rise*; they fell.
VOID.

**What this actually establishes.** Kill counts run 0-3 per game, so target
selection is not tunable at this sample size — but more importantly it is not the
binding constraint. The constraint is **volume**: 369 attacks against their 2346.
Even perfect target selection cannot convert 369 bites into a favourable exchange
against an opponent landing 158 throws at 42 damage each.

And attacking ~6x more means our rats spending turns fighting instead of
collecting, which this project has now tested six times — Iterations 115, 118,
119, 121, 142, 145 — and which lost benchmark games every time. **Our
economy-first policy is not an oversight; it is what keeps us alive.** The combat
gap is structural, and closing it by fighting more has been measured to cost more
than it gains.

## Iteration 159/160 — chase radius, re-dosed and then GATED — **ACCEPTED** (g_iter26)

Iteration 145 measured `CHASE_RADIUS_DSQ` 20 at 6/162 against a then-baseline of
8/162 and I recorded a "clean interior optimum at 8". Two things justified
re-opening it: the noise standard changed (Iteration 151 showed a -2 delta can be
eight games churning both ways), and **g_iter25 changed movement outright** — 0%
strafes against 50.6%, 26% more moves per game. The cost of chasing is travel
time, and travel time had just fallen.

### 159: unconditional d^2 20 — rejected, but the diff was the finding

    benchmarks          6/162 -> 7/162   (+1, noise)
    close-spawn wins     4/42 ->  2/42   BREACHED
    early wipes            14 ->    17   directional

Unlike Iteration 151's churn, the five changed games were **structural**:

    GAINED  bench_finalist whatsthecatdoin A   loss r885  -> win r1009
    GAINED  bench_spaark   closeup         B   loss r820  -> win r738
    GAINED  bench_stroke   whatsthecatdoin A   loss r1187 -> win r533
    LOST    bench_spaark   popthecork      B   win r570   -> loss r822  [close-spawn]
    LOST    bench_stroke   popthecork      A   win r318   -> loss r143  [close-spawn]

Every gain is a longer map; both losses are close-spawn. So the wider radius is a
**map-dependent trade, not a flat optimum** — and Iteration 145's single number
was averaging a trade it could not see.

### 160: the same radius, gated on round >= 300 — accepted

    final int CHASE_RADIUS_DSQ = rc.getRoundNum() >= 300 ? 20 : 8;

Round 300 is past the entire wipe window (every early wipe is before round 100,
fastest losses rounds 19-28), so it removes exactly the early chasing that costs
close-spawn games and keeps the late chasing that wins long ones. Same clause
that restored the guard in Iteration 150.

**Mechanism check:** RatAttack on rift is 299 with the gate, *identical* to the
ungated arm and against g_iter25's 234 — the gate costs nothing where the benefit
lives, because rift is long.

**Result — ACCEPT.**

    benchmarks              6/162 -> 8/162
    games changed                2, BOTH GAINED, none lost
    close-spawn wins         4/42 -> 4/42    (restored)
    early wipes                14 ->   14    (restored)
    g_iter25 head-to-head    28/54 = 51.9%   (flat -- no contradiction)

Prediction stated before the run — *close-spawn returns to 4/42, wipes to ~14,
and the three gained games survive* — held on every count. The two surviving
gains are both `whatsthecatdoin`, at r760 and r501.

No instrument contradicts another, so no peer tiebreak was needed. And the
game-by-game diff is what distinguishes this from Iteration 151's "+2": two games
moved, both in the same direction, zero against.

**Method note worth keeping.** Iterations 159 and 160 together are the argument
for diffing every run rather than reading totals. 159's "+1 benchmark game" was
simultaneously a real gain on long maps and a real loss on close-spawn ones; the
total hid both. The gate then captured one and discarded the other.

**vs_old_bots after g_iter26** (270 games): 232/270 (85.9%), unchanged from
g_iter25 — g_iter1 94%, g_iter6 94%, g_iter11 93%, g_iter16 70%, g_iter21 78%.
Expected: the change is gated to round 300+ and the lineage is dominated by
close-spawn and mid-length games where the gate keeps the old behaviour. **A flat
lineage number is the correct reading of a change designed to alter only the late
game**, not a warning.

Sample archived as `replays/iter160_bench_stroke_whatsthecatdoin_botA.bc26` — one
of the two games this iteration turned from a loss into a win (loss r1187 -> win
r501), reproduced and verified against its `results.csv` row.

`g_iter26` ends in 6, so it joins the `vs_old_bots` roster from the next accept
onward (the tracker excludes the current build by construction).

## Iteration 161 — phase-conditional trap density (3:1 early, 2:1 later) — REJECTED

Tested whether Iteration 102's `TRAPS_PER_BUILD = 2` hides the same
map-dependent trade that Iteration 145's chase radius did. It does not.

    benchmarks           8/162 -> 6/162
    close-spawn wins      4/42 ->  2/42   BREACHED
    early wipes             14 ->    12   (better, but see below)

**Game-by-game: 6 changed, 2 gained, 4 lost** — directional, not churn.

    GAINED  bench_stroke   whatsthecatdoin B   loss r1088 -> win r853
    GAINED  bench_stroke   uneruesansfin   B   loss r2000 -> win r1548
    LOST    bench_finalist popthecork      B   win r431   -> loss r783  [close-spawn]
    LOST    bench_stroke   popthecork      A   win r318   -> loss r121  [close-spawn]
    LOST    bench_finalist whatsthecatdoin A   win r760   -> loss r803
    LOST    bench_stroke   whatsthecatdoin A   win r501   -> loss r985

It costs both `popthecork` close-spawn wins **and undoes both of the wins
Iteration 160 had just gained**.

**The instructive part is that early wipes went DOWN (14 -> 12) while close-spawn
WINS also went down (4 -> 2).** More traps early does exactly what Iteration 96
said it does — it prevents King rushes — but it buys that by building fewer rats,
and on close-spawn maps the games we were *winning* were won by having rats, not
by surviving longer. Not being wiped and winning are different outcomes, and a
change can improve the first while losing the second.

**So Iteration 102's constant is genuinely flat, not a hidden trade.** That also
bounds the lesson from Iteration 160: finding one averaged trade does not license
re-opening every constant as potentially conditional. The chase radius was
conditional because its *cost* (travel time) and its *benefit* (kills) fall in
different game phases; trap density trades against building in the same phase, so
there is no window to separate.

## Iteration 162 — phase-gated REPLACEMENT_RESERVE — REJECTED (and the premise was stale)

Applied the Iteration 160/161 cost-benefit-phase test: the reserve's benefit is a
survival buffer (early, while a rush is live) and its cost is unspent cheese
(late, where compounding pays), so it looked like a genuine conditional. Decayed
1000 -> 300 after round 300.

**The premise was wrong, and the control is what caught it.** I justified this by
quoting Iteration 147's finding that we bank "1000-2800 cheese permanently
unspent". That measurement is from a **g_iter24-era trace**. On g_iter26 the same
map reads:

    g_iter26 on rift   cheese hovers 890-1490,  168 spawns
    iteration 162      cheese 49-1490,          140 spawns

The treasury already sits just above the 1000 reserve — the equilibrium Iteration
92 described — so there is no idle pool to release. **Lowering the reserve
produced FEWER rats, not more** (168 -> 140): we overspent early, ran the
treasury to 49, and could not sustain. The traced game ended at r1623 with the
King destroyed instead of surviving to r2000.

So the mechanism check failed in the intended direction. Pre-registered: *banked
cheese must fall AND spawns must rise*. Cheese fell; spawns fell too.

**Result — REJECT.**

    benchmarks         8/162 -> 7/162   (3 games changed, 1 gained 2 lost -- churn)
    close-spawn wins    4/42 ->  4/42   (guard held)
    early wipes           14 ->    14   (guard held)

**This replicates Iteration 92 on a build two major accepts later.** That
iteration decayed the same constant after round 1200, was accepted on mirror and
peers, cost one benchmark game and was reverted. Getting the same answer from a
much more aggressive gate on a very different bot makes `REPLACEMENT_RESERVE =
1000` settled rather than merely untested.

**The methodological error is the more valuable output.** I carried a numeric
baseline across three accepted iterations and used it as if it described the
current build. That is the same failure as
`same-map-set-control-or-no-comparison`, in a form the memory does not yet cover:
not a different map set, but a different *build*. **A measurement is only
evidence about the bot that produced it.** The habit that caught it — running a
matched control on the current build before trusting a remembered number — is
cheap and should be unconditional.

## Iteration 163 — move the population gate 1500 -> 1000 — REJECTED (decisively)

Iteration 162's control exposed a second stale belief: Iteration 147's population
gate is `cheese > 1500`, but g_iter26's treasury hovers **890-1490**, i.e. below
it. Sampling the round lines, the gate is open in only 8-40% of rounds and its
maximum is 2488 — the *starting* treasury. So the accepted g_iter24 feature is
mostly an **early-game** effect (cap 60 at spawn, then closed), not the "build
freely when rich" behaviour I had been describing in three separate entries.

Moving the gate into the band the treasury actually occupies looked like the
obvious correction.

**Mechanism check — passed cleanly.** Both pre-registered conditions:

    gate open on rift    8%  ->  43%
    spawns (matched g_iter26 control on the same map)   168 -> 177
    traced game still reaches r2000 (no starvation collapse)

**Result — REJECT, and not marginally.**

    benchmarks          8/162 -> 3/162   (-5, about 2 sigma)
    close-spawn wins     4/42 ->  2/42   BREACHED
    games changed           7:  1 gained, 6 lost -- directional

The losses sweep `peaceinourtime` (three of four sides), both `popthecork`
close-spawn wins, and both `whatsthecatdoin` wins that g_iter26 had gained.

**This replicates Iteration 148 on the current build.** That iteration measured
gate 1000 as worse on g_iter24; I had discounted it precisely because it was a
g_iter24 number, having just been burned by trusting one in Iteration 162. Both
builds agree, so **the 1500 gate is genuinely right and the question is closed**
— and the caution was still correct procedure, since the only way to know which
stale numbers survive a build change is to re-measure them.

**The real lesson is about the direction of the error.** Spending deeper produces
more rats — spawns rose 168 -> 177, exactly as designed — and still loses five
games. That is now the *third* time this session that raising economic throughput
measured well on its own mechanism and lost benchmark games: Iteration 151's
movement speed (rescued only by the peer tiebreak), Iteration 162's reserve, and
this. **Cheese held above the reserve is not idle capital; it is what keeps the
King alive while the army is being destroyed** — Iteration 155 put 42.4% of King
damage at treasury < 50, and every one of those games had already lost its army.
The treasury band of 890-1490 is not a failure to spend. It is the equilibrium
the bot needs.

## Iteration 164 — prefer the enemy King as a target — VOID (inert), no Gauntlet spent

Found by asking which losses are NEAR-MISSES rather than which are typical.
Enemy King minimum HP (of 600) across six g_iter26 King-destruction losses:

    whatsthecatdoin B  114 (19%)      pipes    A  560 (93%)
    closeup         A  120 (20%)      closeup  A  600 (100%)
    peaceinourtime  B  160 (27%)      jail     A  600 (100%)

**Half of these losses got the enemy King to about a fifth of its health and
failed to finish** — roughly twelve more bites. The other half never touched it,
which is a reach problem, not a targeting one.

`nearestEnemyRat()` skips CATs but *not* kings, so the enemy King was always an
eligible target, chosen purely by proximity. Ranking it above baby rats **within
bite range** (the correction Iteration 158 arm A needed) looked like free value.

**Result — completely inert.** All three near-miss games reproduce byte-identical
King HP floors:

    whatsthecatdoin   114 -> 114
    closeup           120 -> 120
    peaceinourtime    160 -> 160

and `whatsthecatdoin` ended on the same round (1088) with the same result. VOID
by the pre-registered check, no 162-game run spent.

**What it establishes:** when one of our rats is adjacent to the enemy King, the
King is already the only enemy in range — so the preference never fires. The
damage we deal to their King is already the maximum available given who reaches
it. **The limit is how many rats arrive, not what they choose when they do.**

Taken with Iteration 158 (focus fire on the weakest rat: no gain) that closes
target selection as a direction from both ends. Every remaining path runs back
through attack volume, and volume has now failed seven times because buying it
means not collecting cheese.

## Iteration 165 — how our wins actually happen, and the capability we have never used

Started by asking which losses are near-misses. Ended somewhere much more useful.

### Our rats do not kill the enemy King. Cats do.

Enemy King minimum HP (of 600) across six g_iter26 losses, against how close any
of our rats ever got to it:

    replay                        King min hp    our closest rat
    bench_spaark   jail    A            600           1.4 tiles
    bench_finalist closeup A            600          18.4
    bench_spaark   pipes   A            560          20.0
    bench_stroke   whatsthecatdoin B    114           3.0
    bench_stroke   closeup A            120          12.7
    bench_finalist peaceinourtime  B    160          16.0

**No correlation.** We stood adjacent to their King on `jail` and it never lost a
point of health; we were sixteen tiles away on `peaceinourtime` and it fell to
160. Their treasuries were 1567-2813 at those moments, so it was not starvation.

Reproducing a WIN settles it — `bench_stroke popthecork botA`, which we win at
r318 by King destruction:

    enemy King 600 -> 20
    29 hp drops, EVERY ONE of size 20  (= CAT_SCRATCH_DAMAGE)
    drops occurring while one of our rats was adjacent:  0 of 29

**Cats kill the enemy King, and all eight of our wins are King-destructions.** Our
contribution to the win condition is indirect: we survive, and a cat does the
work. This is the single largest correction to the model of the game in the log.

### And cats are steerable — by a call we have never made

`InternalRobot`'s cat AI, in ATTACK mode:

    Message squeak = getFrontMessage();
    if (squeak != null && ...) this.dir = this.getLocation().directionTo(squeak.getSource());

A cat **turns toward the source of a squeak**. Turning re-aims its vision cone,
which is how it picks its next target.

    rc.squeak(int)          no action cooldown, no cheese, 1 message per turn
    SQUEAK_RADIUS_SQUARED   16  (4 tiles)
    recipients              CATS of any team, AND our own teammates in range
    message carries         content, sender id, round, and LOCATION
    MESSAGE_ROUND_DURATION  5

    our squeaks per game:      0        (grep: zero references in RobotPlayer)
    bench_stroke on rift:   2870
    bench_spaark on pipes:  1070
    bench_stroke popthecork: 668

**We have never squeaked. Every opponent squeaks hundreds to thousands of times
per game.**

### This also explains a failure I had filed under something else

`writeSharedArray` requires `isRatKingType`, so **our rats have no way to talk to
each other at all** — the shared array is King-write-only, and squeak is the only
rat-to-rat channel. Iteration 158's focus fire failed partly because "the weakest
visible enemy" is not a shared ordering across eight different 90-degree cones.
It could not have been, because our rats have no shared anything. Squeak carries a
location and an integer, for free, to every ally within four tiles.

So a single unused call sits underneath at least three separately-diagnosed
problems: no coordination (158), no threat bearing (153, closed because the King
cannot see attackers — but a rat that IS being grabbed could squeak), and no
influence over the units that actually decide games.

**This is what `audit-unused-capabilities-not-just-behaviour` is for, and I found
it late** — the audit in Iteration 156 checked whether the code we HAVE runs, and
this is the complementary question about code we never wrote.

## Iteration 166 — squeak as a cat lure — REJECTED (but the mechanic is now characterised)

The first time this bot has ever called `rc.squeak()`. Gated to fire only beyond
d^2 64 from our own King, on the theory that a squeak drags cats toward the
squeaker and so should pull them out of our base.

**Mechanism pass:** 2227 squeaks from a baseline of zero, no exceptions.

**Result — REJECT, directional.**

    benchmarks          8/162 -> 4/162   (-4)
    close-spawn wins     4/42 ->  1/42   BREACHED
    games changed           6:  1 gained, 5 lost
    FOUR of the five losses are popthecork -- the map we won most reliably

**What squeaking actually does, measured.** On `popthecork`, which the baseline
wins at r318 and this loses at r824:

    metric                              baseline    iter166
    enemy King min hp                         20        380
    our King min hp                          366         17
    total cat TURNS                         1272       2963
    cat-turns within 5 tiles of their King    14         14
    cat-turns within 5 tiles of our King       0          0

**Squeaking does not relocate cats. It doubles how much they act.** Cat turns went
1272 -> 2963 with identical positioning near both Kings. A squeak keeps a cat in
ATTACK mode and re-aims it at the squeaker, so instead of wandering the map — and
wandering into the enemy base, which is how we win — the cats spent the game
hunting our rats. Their King finished at 380 instead of 20.

So the lure works in exactly the wrong direction: **the value of cats to us is
that they roam, and squeaking stops them roaming.** That also explains why four
of the five lost games are `popthecork`, the map whose wins are most
cat-dependent.

**The capability is not dead — this USE of it is.** The far larger prize is that
`writeSharedArray` requires `isRatKingType`, so squeak is **the only rat-to-rat
channel that exists**, and it carries a location and an integer to every ally
within four tiles for free. Our rats currently have no shared state of any kind,
which is why Iteration 158's focus fire could not work. Any future use should
treat the cat-attraction as a COST to be minimised (squeak rarely, or only when
no cat is within the radius) rather than as the point.

## Iteration 167 — squeak re-read, and a positional bug in Iteration 165's method

Measurement only, no Gauntlet (both runs on this mechanic came back
directional-negative).

**1. "Squeaks tow cats home" is NOT supported.** Mean cat distance to each King,
per 100-round bucket, baseline vs the squeak-lure arm on popthecork:

    round     baseline: our King / theirs      iter166: our King / theirs
      100            18.0 / 18.6                     20.0 / 20.1
      200            23.7 / 22.3                     25.0 / 25.6
      300            22.7 / 22.2                     20.3 / 21.3

Essentially identical, and if anything the squeaking arm's cats are *further*
from our King. Iteration 166's loss mechanism stands as originally written —
squeaks double how much cats ACT (1272 -> 2963 cat-turns) and tether them to
whoever squeaked, so they hunt our rats instead of roaming. They do not relocate
toward anyone's base.

**2. A method bug in Iteration 165, which I have now fixed and which CONFIRMS its
conclusion.** That entry computed distances against each King's **initial**
position. The enemy King *moves*:

    enemy King on popthecork: 45 distinct positions, (23,31) at r1 -> (28,11) at r317

Recomputing adjacency against the King's tracked position per round:

    cat-turns within 3 tiles of the enemy King    14 (initial pos)  ->  88 (tracked)
    our rat-turns within ~2 tiles                  0               ->   4

88 adjacent cat-turns is consistent with the 29 scratches of exactly 20 damage,
where 14 was not — the drop-size evidence was right and the positional evidence
was broken. **Cats deal the enemy King's damage and our rats contribute ~nothing:
88 cat-turns against 4 of ours.**

**3. A new asymmetry, documented but NOT pursued.** Our King is completely
stationary; theirs is not:

    replay                     our King positions    their King positions
    popthecork (win)                    1                    45
    popthecork (iter166)                1                    79
    rift                                1                    14
    closeup                             1                    33
    corridorofdoomanddespair            1                    56
    dirtfulcat                          2                    40

Our King has no movement code at all beyond a cat-flee reflex that Iteration 137
showed never fires, so it never even attempts to move.

**Why I am not chasing this.** Their King's moves are not cat-avoidance — 32
increased distance to the nearest cat, 24 decreased it, which is indistinguishable
from wandering — and on popthecork it moved 45 times and still died at r315. If
anything the causation runs the other way: **a King that wanders meets cats, and
cats are what kill Kings.** Our stationary King sits inside its own trap ring and
is the one cats rarely reach, which is consistent with our reactive cat traps
being dormant (Iteration 155: zero placements, because no cat ever comes within
d^2 20 of our King). Copying their mobility would most likely copy their
vulnerability.

## Iteration 168 — delay the trap ring — REJECTED, and it re-validates Iteration 96

**First early-wipe trace of the session**, which produced a directly measured
mechanism rather than an inferred one. `bench_spaark knifefight botB`, a round-20
loss with the Kings spawning **five tiles apart**. Our King goes 600 -> 28 in
twenty rounds at ~38 damage/round from mixed drop sizes (10/20/40/60/92, i.e.
many simultaneous attackers), while enemy rats adjacent to it climb 1 at r2 -> 6
at r17 -> 8 at r19.

    FIRST TWENTY ROUNDS      spawns   traps
      us                          8       8
      them                       14       0

They spend the entire opening on bodies. We split it and lose the rush. Our rats
*do* fight — cooperation ends at round 2, so the combat block is open; I checked
this expecting it to be shut — but we land 8 attacks to their 50 purely because
we have fewer bodies.

**Change:** delay the ring rather than thin it. `builtCount >= 5` -> `>= 12`.
Iteration 161 tested *more* early traps and Iteration 102 tested a *lower steady
density*; neither tested delaying the start.

**Mechanism check — passed exactly.** On the traced map, spawns in rounds 1-20
went **8 -> 14**, matching their 14, traps 8 -> 4, and we survived to r29 instead
of r20.

**Result — REJECT on a directional regression, and the regression is the finding.**

    benchmarks        8/162 -> 7/162   (9 games changed, 4 gained 5 lost: churn)
    close-spawn wins   4/42 ->  4/42   (held)
    EARLY WIPES          14 ->    20   (+43%, and 53% of all losses)
    new wipe maps: thunderdome 3 -> 5, toomuchcheese 0 -> 2, evileye 0 -> 1

**The change designed to reduce early wipes increased them by 43%.** Matching
their build order exactly — 14 spawns to their 14 — still lost more games,
because the six extra rats do not substitute for the ring.

**Why, and it is a genuinely useful asymmetry.** More rats slightly delays each
individual death (fastest losses moved 20/20/22 -> 21/22/25) but the ring
*prevents* deaths outright. A rat trades bites at 10 damage against attackers who
throw for 42; a trap deals 50 plus a 30-round stun to anything that steps on it,
and a King under rush is exactly the place enemies must walk. **Bodies delay a
rush; traps break it.**

So Iteration 96's result — the ring halving early wipes, measured on a far weaker
bot — is now re-validated on g_iter26 from the opposite direction, and the trap
budget is confirmed correct in all three dimensions tested this session: density
(161, 3:1 worse), steady rate (102, 1:1 worse) and start time (168, delay worse).

## Iteration 169 — trap type (closed free) and threat-biased ring geometry — REJECTED

Two dimensions, one closed without a run and one measured.

### Trap TYPE — closed on reachability, no Gauntlet

`CAT_TRAP` is half the cost and double the damage of `RAT_TRAP` (10/100 vs
20/50), which looks like an obvious upgrade for rush defence. It is not, and the
engine says so outright in `processTrapsAtLocation`:

    wrongTrapType = ((isBabyRatType() || isRatKingType()) && type == CAT_TRAP)
                 || (isCatType() && type == RAT_TRAP);

**Baby rats and kings never trigger a cat trap.** Cat traps cannot answer a rat
rush at any price. Cheapest possible negative — one grep.

### Ring GEOMETRY — measured, rejected

Only ~21 tiles sit within `RAT_KING_BUILD_DISTANCE_SQUARED` 8, the ring saturates
(Iteration 157), and half of it faces away from the enemy. On the traced
knifefight wipe the Kings spawn five tiles apart, so the rush arrives from one
bearing. `findTrapLocation` now biases placement toward the nearest visible enemy
rat, taking the bearing from the King's own 360-degree vision rather than the
shared-array symmetry guess (wrong on 16 of 27 maps, Iteration 138).

**Mechanism passed strongly:** enemy trigger events on our traps went **3 -> 8**
on the traced map, a 2.7x rise.

**Result — REJECT.** Per the user's rule the flat benchmark sent this to the
peers rather than to a rejection, and the peers settled it:

    instrument                  g_iter26        iteration 169
    benchmarks                    8/162             7/162   (1 game changed)
    early wipes                      14                14   (unchanged)
    close-spawn wins               4/42              3/42
    peers, full mapset, paired   76/108 (70.4%)    70/108 (64.8%)   -6

No gain on either instrument, and peers down six. **Tripling the trigger rate on
one map produced no reduction in early wipes at all** — the guard this change
existed to move.

**Why, most likely:** concentrating the budget on one bearing opens every other
bearing, and the nearest *visible* enemy rat is a poor proxy for where the rush
will actually arrive — the King's vision is radius^2 25, so it sees the threat
only once it is nearly on top of the ring, by which point the traps are already
placed. A ring that is uniformly thin everywhere beats one that is thick in the
wrong quadrant.

**The trap budget is now confirmed correct in four dimensions:** density
(Iteration 161), steady rate (102), start time (168), and geometry (this). Type
is closed by engine rule. That closes traps as a direction.

## CORRECTION to Iteration 166 — squeaks do NOT increase cat activity

Iteration 166's *result* stands: squeaking unconditionally lost 8/162 -> 4/162,
directionally, with four of five lost games on `popthecork`. **Its stated
mechanism was wrong**, and the error is one this log already has a rule against.

I wrote that squeaking "doubles how much cats ACT (1272 -> 2963 cat-turns)". Those
counts come from games of different length — 318 rounds and 824 rounds. Per
round:

    replay            rounds   squeaks   cat-turns   cat-turns PER ROUND
    baseline win        318         0        1272           4.00
    iteration 166       824      2227        2963           3.60
    iteration 170       880        59        2947           3.35

**Cat activity per round is flat, and if anything falls.** Iteration 170 makes the
point unmissable: 59 squeaks produce the same cat-turn total as 2227 squeaks,
because the total is just `~4 cats x rounds`. Squeaking has no measurable effect
on how much cats act.

This is exactly `normalize-per-round-before-comparing`, which exists because
replay totals scale with game length and a longer-surviving bot reads as a
regression. I had the memory and still compared raw totals — because the two
numbers sat in the same table as a King-HP comparison that *was* valid.

**What this changes.** The claim that "squeaks tether cats to the squeaker" is
withdrawn; the observed cat behaviour is consistent with squeaks doing nothing to
cats at this scale. Iteration 166's loss therefore has **no established
mechanism** — it may simply be that squeaking is a wasted action slot, or the
`d^2 64` gate perturbed movement. The associated memory has been corrected.

It does not rescue Iteration 166 — 8/162 -> 4/162 with 5 of 6 directional flips
is still a real regression — but it removes a false constraint from future squeak
work: **the cat cost may not exist**, so a comms use should be judged on what the
message buys, not on cat side effects.

## Iteration 170 — squeak as a danger signal — REJECTED

First *comms* use of `squeak()`, the only rat-to-rat channel in the game
(`writeSharedArray` requires `isRatKingType`, so our rats have shared no state at
all). A rat whose health dropped since last turn squeaks its location; allies
within earshot **step away** from a fresh warning rather than converging on it.

Avoidance rather than reinforcement was a deliberate choice: converging on a
fight is what Iterations 115/118/119/121/142/145 each tried and lost, and the
Iteration 156 audit explains why — we kill 7 enemy rats per eight games while
losing 347, a 0.02:1 exchange, so reinforcing a losing trade loses harder.

**Mechanism passed:** 59 squeaks from a baseline of zero, no exceptions, and the
guard against speaking within a cat's earshot worked as written.

**Result — REJECT, directional.**

    benchmarks         8/162 -> 6/162   (6 games changed, 2 gained 4 lost)
    EARLY WIPES           14 ->    18   (46% of all losses)
    close-spawn wins    4/42 ->  3/42

**Why: avoidance scatters the defence exactly when it is needed.** During a rush
every defender is taking damage, so every defender is squeaking, so every nearby
rat walks away from the King. The signal is loudest precisely where we least want
rats to leave — `knifefight` wipes went 4 -> 6 and `tiny` 4 -> 6.

**Both responses to a distress signal are now measured and both lose:**
converging (six iterations) and avoiding (this one). That is informative about
the channel rather than the signal — **the value of rat-to-rat comms, if any, is
not in reacting to combat.** Any further squeak work should carry information
that is *not* about danger: cheese locations, explored regions, or a rendezvous.
Note that cheese-location sharing is throughput, which is separately closed
(`economic-throughput-passes-its-own-check-and-loses`), so the honest reading is
that the channel may have no profitable use for this bot.

Recorded alongside the correction above: **there is no measured cat cost to
squeaking**, so the channel is cheap. It is the *content* that has failed twice.

## Iteration 171 — King idle-action audit, and the dirt wall — REJECTED

Started as a different *category* of question after many closed directions: not
"should the King do X instead of Y" but "does it act at all when it could?"

**A premise I had wrong, corrected by reading the field order.**
`RAT_KING(600, 3, 25, 360, 10, 40, 20000)` maps to health / size / vision^2 /
angle / **actionCooldown 10** / movementCooldown 40. The King's *action* cooldown
is 10 against `COOLDOWNS_PER_TURN` 10, so it can act **every round** — not every
fourth as I had estimated. Measured utilisation across five g_iter26 losses:

    closeup 6.0%   corridor 11.2%   dirtfulcat 41.1%   closeup 11.2%   corridor 31.1%

Median ~11%: the King idles through most of its turns. But the idleness is
**cheese-gated, not wasted** — median treasury is 752-1198, below
`REPLACEMENT_RESERVE` 1000, and Iteration 162 already proved that forcing that
spend loses. The attack path is also correct and reachable (it runs after the
build attempt, and the King bites 8-59 times a game). So the budget question is a
void on its own.

**What it surfaced: `placeDirt` has never been called by this bot.** We only ever
`removeDirt`. It is nearly free — `PLACE_DIRT_CHEESE_COST = 0`, needing only an
action, dirt inventory, and a tile within `RAT_KING_BUILD_DISTANCE_SQUARED` 8 —
and dirt is **impassable**, so it builds a wall. Our dirt inventory climbs to
18-24 over a game and we spend none of it.

**Two reachability failures before it fired**, both worth recording:
1. Gated on "enemy rat visible while idle": **zero placements in a whole game.**
   The King is only idle when out of cheese, and enemies are almost never inside
   its radius^2 25 vision at that moment — the same conjunction that left
   Iteration 128's cat traps dormant.
2. Ungated but restricted to the outer ring (d^2 5..8): **still zero.** Around a
   SIZE-3 King that annulus is a handful of tiles and our own delivering rats sit
   on them. Only `d^2 >= 3` fired, confirmed with a `System.out` probe reading
   `DIRTWALL 1 at [1,3]` at round 45.

**Result — REJECT, on the peers.**

    instrument                  g_iter26        iteration 171
    benchmarks                    8/162             6/162   (4 changed, 1 gained 3 lost: churn)
    early wipes                      14                13   (slightly better)
    close-spawn wins               4/42              3/42
    peers, full mapset, paired   76/108 (70.4%)    66/108 (61.1%)   -10
      vs pure_cooperator         23/54  (43%)      14/54  (26%)

Per the user's rule the churn-level benchmark delta sent this to the peers rather
than to a verdict, and the peers found a significant regression: **-10 games, with
`pure_cooperator` collapsing 43% -> 26%.**

**Why: we wall ourselves in.** Dirt at d^2 >= 3 from a size-3 King sits directly
on the tiles our own rats use to reach it and deliver cheese, and on the tiles
`findBuildLocation` needs. The `MAX_DIRT_WALL` cap of 6 was meant to bound that
and did not — six impassable tiles inside a build radius of ~21 is enough to
matter. The near-mirror instrument shows it most clearly because there both sides
have identical economies and only ours is obstructed.

**The capability is real and remains unused.** A wall that does not enclose our
own King would need placing far from home, where the King cannot reach (build
radius 8) and rats cannot place (`placeDirt` is available to any robot type, but
a rat's build radius is `BUILD_DISTANCE_SQUARED` 2). That is the shape of any
future attempt, and it is a different design rather than a tuning of this one.

## Iteration 172 — rat-placed dirt walls — REJECTED, and it closes the dirt direction

The fix Iteration 171 pointed at: a rat, not the King, as the placer. A baby
rat's `BUILD_DISTANCE_SQUARED` is 2, so it walls the tile in front of it anywhere
on the map, far from the tiles our own economy uses.

**Pre-checks first, both answered before writing code:**
- *Where does our dirt come from?* `TeamInfo.updateDirt(team, isPlace)`
  **increments** on a removal, so the inventory is what our King digs out of its
  own base when `findBuildLocation` returns null. It banks 18-24 a game and spent
  none of it until now.
- *Does it exist early?* 0-1 at round 1, 13-17 by round 25. So a wall cannot
  defend the opening rush, only the mid-game.

**The asymmetry that motivated it:** they must reach our King to win; we never
reach theirs. Iteration 165 measured our rats adjacent to the enemy King just 4
turns in a whole game, and all eight of our wins are King kills dealt by cats.
Obstructing the map costs us an approach we do not make.

**Mechanism passed clearly.** Dirt inventory, which plateaued at 18 in the
baseline, now runs down to 1-2 — the rats are spending it — and the traced game
lasted to r795 against the baseline's r721.

**Result — REJECT.**

    instrument                  g_iter26        iteration 172
    benchmarks                    8/162             5/162   (7 changed, 2 gained 5 lost)
    close-spawn wins               4/42              2/42
    early wipes                      14                15
    peers, full mapset, paired   76/108 (70.4%)    73/108 (67.6%)

Neither benchmark delta nor close-spawn was unambiguously significant on its own
(-3 is 1.2 sigma, -2 of 42 about 1 sigma), so per the user's rule this went to the
peers rather than to a verdict. The peers found no gain either: **-3, with no
instrument favouring it.**

**One thing it does confirm.** The rat-placed version is far less harmful than
the King-placed one — peers -3 against -10, and `pure_cooperator` 41% against
26%. So Iteration 171's diagnosis was right: the damage there really was
self-blocking near home, and moving the placer away removed most of it. What
remains is simply that a wall on contact buys nothing: it spends the rat's action
(which competed with a bite) and delays an enemy that walks around it.

**Dirt is now closed in both placements.** Combined with traps closed in four
dimensions plus type (161/102/168/169), the entire terrain-and-obstacle family is
exhausted.

## Iteration 173 — second Rat King, re-tested on g_iter26 — REJECTED, and the peers are why

Re-opened a measured negative for a specific reason, the same ground on which
Iteration 160 reopened the chase radius and became g_iter26. Iteration 146 failed
because mustering 8 rats cost two thirds of a 12-15 rat army; g_iter24 and
g_iter25 have since taken our peak to **42 rats and 168 spawns a game**, so eight
is now a fifth.

**Mechanism passed better than it ever has.** On rift we reach `1:kings=2` *and*
`1:kings=3`, and — the thing that killed Iteration 146 — **the army does not
collapse**: 25 rats at r175, 31 at r425, 20 at r675.

**Result — REJECT, and the two instruments disagree by an order of magnitude.**

    instrument                  g_iter26        iteration 173
    benchmarks                    8/162             6/162   (2 games changed, both lost)
    close-spawn wins               4/42              4/42   (held)
    early wipes                      14                14   (held)
    peers, full mapset, paired   76/108 (70.4%)    48/108 (44.4%)   -28
      vs pure_cooperator         23/54  (43%)      12/54  (22%)
      vs immediate_defector      53/54  (98%)      36/54  (67%)

**Benchmarks moved 2 games with both guards intact — I would have called that
flat.** The peers found a **28-game collapse, about 5 sigma.**

**Why the benchmark set cannot see it.** At an 8/162 base rate we lose almost
everything anyway, so a change that makes us substantially worse has little room
to show: the games it costs were already losses. The peers play our own game at
~50% by construction, so a real strategic error registers at full size. The
muster repeatedly pulls eight rats off the economy and parks them on a rendezvous
tile; against an opponent with our economy that is decisive, and against a
benchmark that was beating us regardless it is invisible.

**This is the mirror image of Iteration 151**, where benchmarks read -2 and the
peers rescued a change worth +18. The same instruction — *when benchmarks and the
other instrument disagree, run the full peer evaluation* — has now caught an error
in both directions. A -2 benchmark result means nothing on its own, in either
sign.

**The second-King direction is now closed on the current build**, and closed for a
better-understood reason than in Iteration 146: not that we cannot afford the
rats, but that a King which does not fight is not worth eight rats' worth of
economy, however large the army.

### Verification: g_iter26 checked on the sensitive instrument

Iteration 173 showed the benchmark set is nearly blind to regressions — a change
that collapsed the peers by 28 games moved benchmarks by 2. g_iter26 itself was
accepted on a **+2 benchmark result with a flat mirror and no peer run**, so by
that standard its evidence was thin. Re-measured against the same archetypes and
map set:

    build       benchmarks    peers, full mapset
    g_iter25       6/162       72/108  (66.7%)
    g_iter26       8/162       76/108  (70.4%)

**Both instruments agree**, so the accept stands on better evidence than it had
on the day. Worth noting the peer delta (+4) is smaller than the 28-game swing a
genuinely bad change produces, which is the right shape for a modest, correct
change rather than a dramatic one.

**Standing change to method:** run the peer gauntlet on every accept, not only
when instruments conflict. It is 108 games and it is the only instrument that
reliably shows harm.

## Iteration 174 — peer re-audit of the accepted feature list

Motivated by Iteration 173: the benchmark set is nearly blind to regressions, so
features accepted on benchmarks alone — many on 1-2 game moves — have never been
checked on the instrument that can see harm.

### The method control, and it did two useful things

Ablated the reactive cat traps (Iteration 128) first, precisely because Iteration
155 had measured them **DORMANT**: zero placements across eight benchmark losses,
and removing them changed **zero of 162 benchmark games**. Removing dead code
should return the control's 76/108.

    g_iter26 control        76/108 (70.4%)
    cat traps ablated       73/108 (67.6%)   -3

**1. The peer instrument is exactly reproducible.** Re-running the unmodified
control produced 76/108 with identical sub-scores (23/54, 53/54) — not merely the
same total but the same games. So the -3 is a real behavioural difference, and
paired peer comparisons need no error bars at all: any difference is signal.

**2. The cat traps are NOT dormant — Iteration 155 over-generalised.** That
conclusion came from eight *benchmark* replays. The three peer games the feature
decides are:

    pure_cooperator     evileye           B   win r2000 -> loss r2000
    pure_cooperator     sittingducks      A   win r2000 -> loss r2000
    immediate_defector  whereisthecheese  A   win r877  -> loss r843

Reproducing `sittingducks`: we place **4 cat traps** and cats trigger them **3
times**, in a game whose `catDamage` finishes **exactly tied at [4000, 4000]**.
Three triggers at CAT_TRAP's 100 damage decide a tied scoring term.

**Why benchmarks could never see this.** Two of the three are r2000 points games,
and only ~9% of benchmark games reach scoring against 59% of peer games
(`peers-and-benchmarks-play-different-games`). Cat traps pay in long games decided
on points; benchmark games end in King destruction long before the cat-damage
share matters. **"Dormant on benchmarks" and "dormant" are different claims, and I
conflated them.**

So Iteration 155's write-up is corrected: the feature is live, worth ~3 peer
games, and its acceptance in Iteration 128 was better founded than my own later
audit suggested. The audit's first result is that an accepted feature is *more*
valuable than believed, not less.

### Bite boost (Iteration 83) — peer re-audit: NEUTRAL, keep it

Second feature audited, and the one I most suspected: the 4-cheese boosted bite
was kept on a 53.7% *mirror* reading, and it spends cheese, which Iteration 163
showed is the binding constraint.

    instrument      g_iter26        boost ablated
    benchmarks        8/162            8/162      (2 changed: 1 gained, 1 lost)
    peers            76/108           78/108      (12 changed: 7 gained, 5 lost)

**Net +2 on peers, but from twelve games flipping in both directions.** That is
churn, not a gain — the same reading I would apply to a benchmark result, and the
net of twelve coin-flips has a spread of about +/-1.7. **Verdict: neutral. The
feature stays**, because changing the accepted build needs a reason and "no
measurable difference" is not one.

### An important correction to what I wrote earlier in this entry

I said the peer instrument's exact reproducibility means "any difference is
signal". **That is wrong and I should not have written it.** Reproducibility means
a *repeated identical run* gives an identical result — which it does, byte for
byte. It does not mean a code change produces only meaningful differences: game
trajectories are chaotic, so any perturbation reshuffles a batch of near-threshold
games in both directions.

The two audits show the distinction cleanly:

    cat traps ablated    3 games changed, ALL 3 lost      -> directional, real
    bite boost ablated  12 games changed, 7 gained 5 lost -> churn, neutral

**Determinism removes run-to-run noise; it does not remove trajectory
sensitivity.** The game-by-game diff is still the deciding tool on the peer
instrument, exactly as on benchmarks — a lesson I had already learned for
benchmarks in Iteration 151 and briefly forgot when the instrument changed.

### King trap ring — peer re-audit: KEEP, and it settles the Iteration 82/96 conflict

The highest-stakes item on the accepted list, and the one with a genuine
unresolved disagreement in its history: Iteration 82 ablated the ring and scored
**57.4% on the mirror** (i.e. remove it); Iteration 96 restored it because
benchmark early wipes **halved, 26% -> 13%**. The peers had never been asked.

    instrument           g_iter26 (ring on)      ring ablated
    peers                  76/108 (70.4%)       78/108 (72.2%)   20 changed: 11 gained, 9 lost
    benchmarks               8/162                 4/162         -4
    close-spawn wins          4/42                  0/42         TOTAL COLLAPSE
    early wipes                 14                   ~42         (31 on close-spawn alone,
                                                                  plus 11 outside the set)

**Peers are neutral** — twenty games flipping 11/9 is churn by the standard
established two entries above, not the +2 the total suggests. **Benchmarks are
emphatic**: the ring is worth four wins, *every* close-spawn win, and it holds
early wipes to a third of what they become without it.

**So Iteration 82's 57.4% mirror reading was churn all along**, and Iteration 96
was right to override it. The conflict that has sat in this log since is now
resolved with a third, sensitive instrument agreeing that the ring is not merely
harmless but load-bearing.

### The audit so far

    feature                       peers            verdict
    reactive cat traps (128)      -3 directional   KEEP -- and "dormant" was wrong
    cheese-boosted bite (83)      +2 churn         KEEP (neutral)
    King trap ring (48/96/102)    +2 churn         KEEP (benchmarks -4, wipes 3x)

**No harmful feature found.** The premise that started this audit — that
benchmark-blindness may have let a regression into the accepted list — is so far
not borne out, and two features are better founded than the log claimed. That is
a satisfying negative: the accepted list survives its first serious cross-check.

## Iteration 175 — peer audit, part 2: the emergency override is worth 15 games

The Iteration 40/84 emergency override — when the King can see no allied Baby
Rat, the build reserve drops from `REPLACEMENT_RESERVE` 1000 to `RESERVE` 150, so
it can rebuild instead of hoarding while starving.

**Iteration 84 measured it on the mirror at 26/54 = 48.1%** and concluded it was
*"inert to within one game in 54"*, keeping it only because removing it was
equally neutral, and explicitly instructing that it **"should not be credited in
any future reasoning."**

    instrument       g_iter26        override ablated
    peers            76/108 (70.4%)     61/108 (56.5%)     -15
      pure_cooperator  23/54 (43%)       13/54 (24%)
      immediate_def    53/54 (98%)       48/54 (89%)
    games changed                        19: 2 gained, 17 LOST

**Seventeen of nineteen changed games are losses.** That is directional harm at a
scale nothing else in this audit approaches — the override is worth about fifteen
peer games, and the log has been carrying an instruction not to credit it.

**Why the mirror could not see it.** The override fires precisely when our army
has been wiped and the King is alone. In a mirror both sides run the same policy
and reach that state symmetrically, so the advantage cancels; against
`pure_cooperator`, which plays a different backstab policy, it does not. This is
the same shape as the cat-trap correction two entries above: **an instrument that
never poses the situation a feature answers will always call that feature
inert.**

Verdict: **KEEP, and the log's own note about it is now retracted.** Iteration 40
was right, Iteration 84's re-measurement was reading a blind instrument, and the
feature is one of the more valuable things in the bot.

### Audit scoreboard

    feature                        peers                  verdict
    reactive cat traps (128)       -3, all directional    KEEP (was wrongly "dormant")
    cheese-boosted bite (83)       +2, 12 flips 7/5       KEEP (neutral)
    King trap ring (48/96/102)     +2, 20 flips 11/9      KEEP (benchmarks -4, wipes 3x)
    emergency override (40/84)     -15, 19 flips 2/17     KEEP (was wrongly "inert")

**Two of four accepted features were recorded as worthless and are not.** The
audit has found no harmful feature, but it has found something arguably more
useful: the log's confidence was misallocated, and in both cases the error came
from trusting a single instrument that structurally could not see the effect.

### Desperation flag (11/12/141) — peer audit: genuinely inert, Iteration 141 confirmed

    peers, desperation ablated   76/108 (70.4%), sub-scores 23/54 and 53/54
    games changed                0

Not "small", not churn — **zero games differ**, on an instrument verified to be
byte-reproducible. The flag never changes an outcome against peers. Iteration 141
called it inert on the mirror and was right.

**Kept anyway**, on the same principle as the bite boost: changing the accepted
build needs a reason, and dead-but-harmless code is not one. What matters is that
the log now records it as *measured* dead rather than *assumed* dead, so no future
iteration reasons from it.

## The peer audit, complete

    feature                       peers            diff              verdict
    reactive cat traps (128)      -3               3 flips, all lost  KEEP (was "dormant")
    cheese-boosted bite (83)      +2               12 flips, 7/5      KEEP (neutral)
    King trap ring (48/96/102)    +2               20 flips, 11/9     KEEP (benchmarks -4)
    emergency override (40/84)    -15              19 flips, 2/17     KEEP (was "inert")
    desperation flag (11/12/141)   0               0 flips            KEEP (truly inert)

**No harmful feature exists in the accepted list.** The premise that started this
audit — that benchmark-blindness may have admitted a regression — is refuted for
every feature checked.

**The more useful finding is about the log's own reliability.** Two of five
features were recorded as worthless and are not, one of them worth fifteen peer
games. Both errors came from the mirror, and both have the same cause: *an
instrument that never poses the situation a feature answers will call that feature
inert.* The mirror said "inert" about the emergency override (worth 15) and about
the desperation flag (worth 0), **and nothing in the mirror result distinguishes
the two cases.** That is the argument for the standing rule adopted this session:
run the peers on every accept, and judge by the game-by-game diff rather than the
total.

## Iteration 176 — extend the emergency override to bypass the build CAP — REJECTED

The audit's largest finding suggested its own extension. The Iteration 40/84
override is worth fifteen peer games by relaxing the cheese reserve when the King
sees no allied rat — but it relaxes only the cheese gate, not the count gate:

    buildLoc != null && canBuildRat && cheese - ratCost >= buildReserve
        && builtCount < MAX_POPULATION

`builtCount` is cumulative per 400-round window, so an army wiped late in a window
leaves the King unable to rebuild until the window rolls over, however much cheese
the override just freed. That is the situation the override exists for,
half-answered.

**Mechanism passed — the cap really was binding.** Matched control on
whereisthecheese:

    g_iter26 control   27 spawns
    iteration 176      34 spawns   (+26%)

**Result — REJECT.**

    instrument         g_iter26        iteration 176
    peers            76/108 (70.4%)   74/108 (68.5%)   18 changed: 8 gained, 10 lost
    benchmarks         8/162            8/162          (flat)
    close-spawn wins    4/42             2/42
    early wipes           14               13

**Churn on peers, flat on benchmarks, close-spawn down two.** No gain on either
instrument, so no accept.

**Why the extra rats do not help, and it is the session's recurring answer.** The
rats get built and immediately die: this is the same wall as
`economic-throughput-passes-its-own-check-and-loses`, where three separate
iterations raised output, proved their mechanism, and lost. Rebuilding faster
after a wipe adds bodies to a fight we lose at 0.02:1
(Iteration 156), so the marginal rat is worth much less than the first one the
override already buys.

**That also sharpens what the override actually does.** It is not valuable because
it produces rats in bulk — bulk is what this iteration added, to no effect. It is
valuable because it produces *the first few* rats when we have none, restoring
cheese income before the King starves. Fifteen peer games come from escaping zero,
not from rebuilding to strength.

## Iteration 177 — trigger the emergency override on an exact count — REJECTED

Iteration 176 showed the override's fifteen peer games come from *escaping zero*,
not from volume. So the next lever looked like latency: make the trigger a
measurement rather than a proxy. `noVisibleArmy` scans the King's radius^2 25
vision, whereas `getCurrentRatCost() = 10 + 10 * (live / 4)` inverts to an exact
live count for free.

**Mechanism fired:** spawns on the traced collapse went 27 -> 31 against the
g_iter26 control.

**Result — REJECT.**

    instrument      g_iter26        iteration 177
    peers         76/108 (70.4%)   70/108 (64.8%)   14 changed: 4 gained, 10 LOST
      pure_coop     23/54 (43%)     19/54 (35%)

**The proxy beats the measurement, and the reason inverts my framing.** I called
line-of-sight a proxy for "the army is dead" and the cost-curve count the real
thing. It is the other way round: the override's job is to decide *"should the
King rebuild locally, right now"*, and what matters for that is **whether help is
at hand**, not how many rats exist somewhere on the map.

`noVisibleArmy` fires when rats are alive but far away — a state in which the King
genuinely is alone, unfed and undefended, and should rebuild. The exact count
suppresses the override in exactly that state ("you have eight rats, don't panic")
while firing it when 1-3 survivors are huddled beside the King already feeding it.
Both changes are wrong, and together they cost six peer games.

**Generalisation worth keeping:** a "more accurate" input is only better if it
measures the quantity the decision actually depends on. Vision-limited sensing is
not always an approximation of global truth — sometimes it *is* the relevant
truth, because locality is what the decision turns on.

That closes the recovery-latency direction. The override is well-tuned as
written, and this is the second consecutive iteration (176 volume, 177 latency)
to find that the most valuable feature in the bot cannot be improved along the
obvious axis.

## Iteration 178 — peer-audit cat engagement — KEEP, worth 11 peer games

The last major accepted feature the audit had not covered, chosen because it was
validated on the instrument least able to see either its benefit or its cost, and
because it is causally implicated in our worst problem.

**Two opposing predictions, cleanly distinguishable by one run:**
- *Keep* — catDamage is a scored term, and only ~9% of benchmark games reach
  scoring against 59% of peer games. The same blindness made me wrongly call the
  cat traps dormant.
- *Remove* — a scratch is 20 damage, rats never heal, and `canGrab` succeeds on
  any weaker target, so every engagement permanently converts a full-HP rat into
  a grabbable one, and grabs kill 52% of our rats.

**Mechanism fired decisively.** On rift against `pure_cooperator`:

    g_iter26 control   catDamage 8394 / 4644   -> WIN  at r2000
    engagement off     catDamage  970 / 4640   -> LOSS at r2000

An 89% collapse in our cat damage, turning a won cat term into a badly lost one.

**Result — KEEP.**

    instrument      g_iter26        engagement ablated
    peers         76/108 (70.4%)   65/108 (60.2%)   31 changed: 10 gained, 21 LOST
      pure_coop     23/54 (43%)     18/54 (33%)

**The scoring prediction wins and the throw-loop prediction loses.** The HP cost
is real — engaged rats *are* damaged and *are* therefore grabbable — but it is
outweighed roughly two to one by the catDamage share. Worth stating plainly
because I have twice argued from the throw loop as though it dominated every other
consideration: **a mechanism being real does not make it decisive.**

## Peer audit, final

    feature                        peers   diff               verdict
    cat engagement (5/6/103-105)   -11     31 flips, 10/21     KEEP
    emergency override (40/84)     -15     19 flips, 2/17      KEEP (was "inert")
    reactive cat traps (128)        -3      3 flips, all lost  KEEP (was "dormant")
    King trap ring (48/96/102)      +2     20 flips, 11/9      KEEP (benchmarks -4)
    cheese-boosted bite (83)        +2     12 flips, 7/5       KEEP (neutral)
    desperation flag (11/12/141)     0      0 flips            KEEP (truly inert)

**Six features audited, six kept, no harmful feature found.** Three were worth
more than the log credited (cat engagement, the override, the cat traps), two were
neutral, one was confirmed dead. The accepted list is sound, and the errors were
all in the same direction: **features were undervalued because they were measured
on instruments that could not pose the situations they answer.**

## Iteration 179 — re-dose cat engagement on the peer instrument — REJECTED, dose confirmed

The audit's error pattern was one-directional: three features were worth *more*
than the log credited and none was harmful. The inference was that doses chosen
against those underestimates might also be too low. Cat engagement was the best
candidate — worth eleven peer games, with a dose curve measured entirely on the
mirror (Iterations 103-105: abstain 38.9%, break off at 60 HP 48.1%, current
50.0%, break off at 10 HP 50.0%).

Lowered the engage floor from `health > 30` to `health > 10`, i.e. engage more.

**Result — REJECT.**

    instrument      g_iter26        floor 10
    peers         76/108 (70.4%)   73/108 (67.6%)   11 changed: 4 gained, 7 lost

**The three-point curve on the sensitive instrument:**

    never engage (Iteration 178)   65/108   -11
    floor 10  (engage more)        73/108    -3
    floor 30  (current)            76/108     0

Engaging less is much worse; engaging more is somewhat worse. **The Iteration
103-105 choice of 30 is confirmed optimal**, now on an instrument that can see it
— and notably the mirror had scored floors 30 and 10 *identically* at 50.0%,
which is again the reading a blind instrument produces.

**The audit's inference does not generalise to doses.** Features being undervalued
did not mean their knobs were mis-set: the value was underestimated because the
instrument could not pose the situation, but the *shape* of the dose curve was
apparently still recovered correctly by the mirror even where its absolute level
was wrong. Worth remembering before re-dosing anything else on that basis — the
premise is now tested and it failed.

## Iteration 180 — first trace of a PEER loss, and rats laying cat traps — REJECTED

Every replay traced in this project had been a *benchmark* loss; the peer gauntlet
had been used purely as a scoreboard. g_iter26 loses 32 of 108 peer games and none
had ever been examined.

### The peer loss profile is the inverse of the benchmark one

    19 of 32   pure_cooperator, POINTS at r2000
    12 of 32   pure_cooperator, King kill
     1 of 32   immediate_defector, King kill

Against benchmarks, 83% of losses are King kills. Against a near-mirror we lose on
**points**, and the margins are small — decomposed over five traced losses:

    replay                          margin     cat    king  cheese
    safelycontained  botA             -0.6    +0.0    +0.0    -0.6
    sittingducks     botB             -4.5    -0.9    +0.0    -3.6
    keepout          botA             -7.5    -9.2    +0.0    +1.7
    closeup          botB            -10.5   -13.3    +0.0    +2.8
    rift             botB            -21.2   -21.1    +0.0    -0.1
    mean                              -8.9    -8.9    +0.0    +0.1

**Decided entirely by catDamage**, with kings exactly tied and cheese neutral, at
margins of -0.6 to -21 rather than the -46 of benchmark points losses. These are
winnable games.

### A mechanic discovery: our own rat traps disable our cat traps

We placed **zero** cat traps across all five, while `pure_cooperator` placed seven,
against a team-wide `CAT_TRAP.maxCount` of 10 at 100 damage each. Two distinct
causes:

- **closeup** — cooperation ends at round 194, *the exact round an enemy first
  triggers one of our rat traps*. `triggerTrap` credits
  `backstab(triggeringRobot.getTeam().opponent())`, so the trap's OWNER becomes
  the backstabber, and `catTrapsAllowed` bars the backstabber permanently. **Our
  King's trap ring silently revokes our own cat-trap rights for the rest of the
  game.** Unavoidable — the ring is worth four benchmark wins and every
  close-spawn win.
- **rift** — still cooperating at r2000, so cat traps are legal; the King's
  trigger just never fires, because it needs a cat within d^2 20 of the *King*.

### The fix, and why it was worth retesting

Rats go where cats are. Iterations 130/134/135 tried rat-placed cat traps and
failed because rat placements starved the King's budget — the King was placing 52
and the shared cap bound. **That cannot apply when the King places zero.**

**Mechanism fired spectacularly on the traced map**, flipping it:

    rift vs pure_cooperator    control        iteration 180
    our cat traps                   0                   44
    catDamage, ours              3808                 7022
    catDamage, theirs            9376                 4000
    result                       LOSS                  WIN

**Result — REJECT.**

    instrument      g_iter26        iteration 180
    peers         76/108 (70.4%)   76/108 (70.4%)   16 changed: 8 gained, 8 lost
    benchmarks      8/162             6/162
    close-spawn      4/42              3/42

**Exactly neutral on peers — 8 gained, 8 lost, net zero — and -2 on benchmarks.**
A mechanism that reverses a 5500-point catDamage swing on one map nets nothing
across 108. The cat term it wins on one map it loses on another, which is what
"catDamage is a SHARE" means: forty-four traps raise our share where cats happen
to walk and do nothing where they do not.

**Kept for the record:** the backstab/cat-trap interaction is new and belongs in
any future reasoning about either trap type. It also explains part of the
Iteration 155 confusion — cat traps look dormant partly because we frequently
revoke our own right to place them.

## Iteration 181 — attack the catDamage denominator — CLOSED without a run

Iteration 180 showed peer points losses are decided by catDamage, and that raising
our numerator nets zero because the term is a SHARE. The untried half was reducing
theirs. Both routes close on measurement:

**1. Their cat traps are unremovable AND negligible.** `assertCanRemoveCatTrap`
checks `hasCatTrap(loc, this.getTeam())` — only our OWN traps can be removed, the
same ownership rule that made `MapInfo.getTrap` useless for enemy traps in
Iteration 156. And it would not matter:

    replay                theirCatDmg   their placements   trap dmg (est)   bite dmg
    closeup     botB           4928             7                500          4428
    keepout     botA           7504             0                  0          7504
    rift        botB           9376             0                  0          9376
    sittingducks botB          4116             0                  0          4116

**Their cat damage is essentially all teeth** — traps are ~2% of it in the one
game they used any.

**2. Denying their bites means winning fights we lose 0.02:1** (Iteration 156:
seven enemy rats killed per eight games against 347 lost). Closed by every combat
iteration in the log.

**So the catDamage share is closed in both directions.** Five iterations have
raised our numerator (142, 147, 179, and the cat-trap family 128/130/134/135/180);
this closes the denominator. Since the peer points-loss decomposition is
`catDamage -8.9, kings +0.0, cheese +0.1`, **those 19 games are not winnable
through the scored terms as currently understood.**

That redirects attention to the other bucket: **12 of 32 peer losses are King
kills**, against an opponent running our own economy and movement code. A peer
that beats us by destroying our King is a policy difference, not a strength
mismatch, and it has never been traced.

## Iteration 181b — first trace of peer KING-KILL losses: two distinct causes

Thirteen of the 32 peer losses end in King destruction, none of them early wipes
(r455 and later). Traced four.

### Cause 1: our own trap ring unleashes a pacifist opponent

`pure_cooperator` has `desperate = false` and lays no rat traps, so it never
initiates a backstab and its rats cannot attack us at all while cooperation holds.
We break it ourselves:

    replay                       coop ends   our traps placed   first enemy trigger of OUR trap
    popthecork      botB              r88          26                  r88
    tiny            botA              r10          56                  r10
    peaceinourtime  botA            never          20                  none
    whatsthecatdoin botA             r740          16                  none

**In two of four, cooperation ends on the exact round an enemy first steps on one
of our rat traps.** `triggerTrap` credits the backstab to the trap's OWNER, so our
ring converts a peaceful opponent into an attacker — on `tiny`, at round 10.

This compounds the Iteration 180 finding: the same event also permanently revokes
our cat-trap rights. **One trap trigger costs us both the peace and the cat traps.**
It is still not worth removing the ring — Iteration 174 measured ablation as
peer-churn and benchmark -4 with close-spawn 4/42 -> 0/42 — but it explains a real
part of why we score only 43% against an opponent running our own code.

### Cause 2: a CAT kills our King while we are still cooperating

`peaceinourtime botA`, cooperation never breaks, and we lose anyway at r523:

    our King hp 600 -> 20 over 522 rounds
    drop sizes: 29 drops, EVERY ONE of exactly 20   (= CAT_SCRATCH_DAMAGE)
    hp lost while treasury < 50: 0 of 580
    our rats alive at r100/300/500: 24 / 19 / 12

Not starvation, not bites, with a healthy army and treasury throughout. **This is
the exact mirror of how we win** — Iteration 165 found the enemy King dying to 29
drops of 20 with our rats never adjacent.

**And the counter already exists and already fires.** We placed **28 cat traps**
in that game, peaking at 9 live, and a cat was within d^2 20 of our King in **114
of 522 rounds (22%)**. The feature was not dormant here; it simply does not work
against this threat. A cat has 4000 HP against a trap's 100, and **a cat parked
adjacent to the King never steps on a trap** — traps punish movement, and a
grinding cat does not need to move.

So the reactive cat trap defends against cats *passing through*, not against the
one that stops. That is a real gap with no counter yet identified: the King cannot
outrun a cat (`movementCooldown` 40 against the cat's 20), our rats bite for 10
against 4000 HP, and dirt cannot be placed on an occupied tile.

## Iteration 182 — the King has a blind spot: cats grind it from outside its own reach

Chasing the Iteration 181b finding that a cat killed our King while we were still
cooperating. The obvious counter was the King's own bite —
`RAT_KING_ATTACK_DISTANCE_SQUARED` is **8**, four times a rat's 2, and the King is
idle in ~89% of rounds (Iteration 171).

**Measured, and the counter is unavailable:**

    rounds with a cat within the King's attack range (d^2 8):   0
    closest any cat ever came to the King's centre:             d^2 10
    CatScratch actions in the game:                            98
    King hp drops coinciding with a CatScratch:                29 of 29

**Every one of the 29 drops is a cat scratch, and the cat is never once inside the
King's attack range.**

**Why: the King is SIZE 3 and its attack range is measured from its CENTRE.**
`assertCanAttackRat` computes `this.getLocation().distanceSquaredTo(loc)` against
`RAT_KING_ATTACK_DISTANCE_SQUARED` 8, while the King's body occupies the whole 3x3
and can be scratched at its edge. So a cat sitting beyond d^2 8 from the centre
still reaches the body, and the King cannot reach back. **Its effective reach past
its own body is under two tiles, and a cat can grind it from outside that.**

That closes the last available counter to a parked cat:

    King flight        movementCooldown 40 against the cat's 20 -- cannot outrun it
    cat traps          fire constantly (28 placed, 9 live, cat within d^2 20 in
                       22% of rounds) and do nothing: 4000 hp against 100, and a
                       parked cat never steps on one
    King's own bite    THIS -- geometrically out of range
    dirt               assertCanPlaceDirt rejects an occupied tile

**What remains untried is our RATS.** They already engage cats — worth eleven peer
games (Iteration 178) — but they engage the *nearest* cat wherever it is, with no
notion of defending the King. A cat parked on our King is worth far more to kill
than one wandering the map, and nothing in the bot expresses that. That is the one
lever this trace leaves open.

## Iteration 183 — rats prioritise the cat nearest our King — VOID

The one lever Iteration 182 left open. Our rats are the only unit that can reach a
cat grinding the King, they already engage cats (worth eleven peer games), and
`nearestOfType` picks the nearest cat with no notion of which one matters. The
change re-targets an engagement the rat was already going to make, among cats
already inside its vision, so it should cost no travel.

**Result — VOID. The traced game is identical.**

    metric                     control (g_iter26)   iteration 183
    King hp lost to scratches         580                580
    scratch-coincident drops           29                 29
    our catDamage                    3598               3598
    result                     LOSS r523          LOSS r523

**Why, and it took a careful second look.** Measuring globally, the conditions look
perfect: more than one cat is visible *somewhere* in all 522 rounds, a cat sits
within d^2 20 of our King in 118 rounds, and one of our rats is within d^2 20 of
that cat in **118 of 118 (100%)** of them. On those numbers the re-targeting
should fire constantly.

It does not, because **global proximity is not per-rat visibility.** A Baby Rat
senses through a **90-degree cone** of radius^2 20, so an individual rat almost
never has two cats in view at once — and with one candidate, "nearest cat" and
"cat nearest the King" are the same cat. The selector had a choice globally and
essentially never had one locally.

I nearly recorded the opposite conclusion ("our rats never get near that cat"),
which the global numbers flatly contradict. The cone is the reason, as it was for
Iteration 152's sweep and Iteration 158's focus fire: **any per-rat target
preference is limited by what one 90-degree wedge contains, and that is usually a
single candidate.** Cross-rat coordination would be needed to express a team-level
priority, and the only channel for it — `squeak` — was measured neutral-to-harmful
in Iterations 166 and 170.

That closes the parked-cat problem: the King has no counter of its own (Iteration
182), and the rats cannot be told which cat to prefer.

## Iteration 184 — King-broadcast cat bearing — CLOSED on the pre-check, no code

Widened the peer King-kill sample from 4 to 12 and classified each loss by whether
CatScratch coincides with the King's hp drops:

    CAT-KILLED    2 of 12 (17%)  -- and BOTH are peaceinourtime, one per side
    rats/other   10 of 12        -- 60 drops of ~10, i.e. enemy rat bites

Two games on a single map, against the risk of sending rats toward the King — the
shape that failed in Iterations 115/118/119/121/142. The task's own bar was "if it
is 1 in 12 the ceiling is too low", and it is. **Closed without writing code**,
which is what the pre-check was for.

## Iteration 185 — gate the trap ring on !isCooperation — REJECTED

The wider sample found something better. Ten of twelve peer King-kill losses are
enemy RAT BITES, and `pure_cooperator` cannot bite us while cooperation holds —
its combat block is gated on `!isCooperation || desperate` and it sets
`desperate = false`. **We break the peace ourselves**: `triggerTrap` credits
`backstab(trap owner's opponent)`, so an enemy stepping on our ring makes us the
backstabber. On popthecork cooperation ends at r88, the exact round of the first
enemy trigger; on tiny, r10.

Gating the ring on `!isCooperation` looked stable rather than merely delayed:
benchmarks grab constantly and break the peace themselves at rounds 10-53, so we
would still get our ring, while a pacifist would never wake up.

**Mechanism passed** on tiny vs pure_cooperator — cooperation broke at r937
instead of r10, traps 56 -> 1, and we survived to r1182 instead of r999.

**Result — REJECT.**

    instrument      g_iter26        iteration 185
    peers         76/108 (70.4%)   78/108 (72.2%)   20 changed: 11 gained, 9 lost
      pure_coop     23/54 (43%)     27/54 (50%)
    benchmarks      8/162             6/162
    EARLY WIPES        14                18
    close-spawn      4/42              3/42

Peers are churn (+2 from 11/9), and the benchmark side broke exactly where
Iteration 168 said it would: **delaying the ring raises early wipes**, 14 -> 18
here against 14 -> 20 there. The exposure is shorter — hostility appears at rounds
10-23 on the wipe maps rather than at a build count — but it is the same failure.

**Third confirmation that the ring's value is its EARLY presence.** Iteration 96
(restoring it halved wipes), Iteration 168 (delaying by build count, +43% wipes)
and now Iteration 185 (delaying by hostility, +29% wipes). The ring must be
standing before the rush arrives, and any scheme that waits for evidence of a rush
arrives too late.

**The peace cost is therefore unavoidable and now fully priced.** Our ring wakes a
pacifist opponent and revokes our own cat traps, costing perhaps ten peer games —
and it is still worth keeping, because Iteration 174 measured its removal at
benchmarks -4 with close-spawn 4/42 -> 0/42 and wipes 14 -> ~42. We pay the peace
to survive the openings.

## Iteration 186 — verification pass on the local optimum

After eleven consecutive rejections and a closed-directions review, a verification
pass rather than another speculative run.

**Housekeeping — all clean.**

    archetypes      resync_archetypes.py --check: both up to date
    build integrity git status clean; src/bot byte-identical to src/g_iter26
    snapshots       26 present, matching plot_progress.py's count
    vs_old_bots     history current at g_iter26 (232/270)
    ReplayDump      full test suite passes, including the --turns additions

**The substantive check: is the recent lineage real on the sensitive instrument?**
g_iter25 vs g_iter26 had been measured, but g_iter24 was accepted before the peer
gauntlet was trusted. Run against identical archetypes and maps:

    build       peers            vs pure_cooperator
    g_iter24    58/108 (53.7%)      10/54 (19%)
    g_iter25    72/108 (66.7%)      20/54 (37%)
    g_iter26    76/108 (70.4%)      23/54 (43%)

**Monotone, and the steps are large.** No accepted step was a regression, so the
lineage needs no revisiting — the outcome I was checking for did not occur.

**The g_iter24 -> g_iter25 step is +14 peer games, and benchmarks scored it -2.**
That was Iteration 151, the movement fix, which I rejected on the benchmark
reading and which only the user's peer-tiebreak rule recovered. Had it stayed
rejected the bot would still be at 58/108 rather than 76/108 — **the single
largest improvement in this stretch was one I had thrown away.**

That is the strongest available argument for the standing rule adopted since: run
the peer gauntlet on every accept, and judge any small benchmark delta by the
game-by-game diff rather than the total.

## Iteration 187 — King-published census — CLOSED by reasoning, no run

The structural attempt the closed-directions review called for: have the King
publish what it alone sees correctly (360 degrees, radius^2 25) so rats can
condition on a team-level fact instead of their 90-degree wedge, which has now
killed three iterations (152, 158, 183).

**It cannot work, and the reason is geometric.** The decision it was meant to
improve — engage or flee near a cat — happens wherever a rat *meets* a cat, which
is generally outside the King's radius. The King cannot see the local ally count
at a remote skirmish, so its census cannot correct that undercount. The only
quantities it can publish accurately are about its own neighbourhood, and
Iteration 177 already measured that substituting a global count for local sensing
makes things worse (-6 peer games): the decision turns on whether help is *at
hand*, not on how many units exist.

## Iteration 188 — damaged-rat retreat, re-opened — REJECTED

Re-opened on the pattern that produced g_iter26: a rejection whose stated cause
has changed. Iterations 149/150 rejected retreat because *"the clause that makes
it safe is the clause that makes it worthless"* — the round gate cut the benefit
and `cheeseTransferred` fell to 11425 against 12745. Two things had changed:

1. **The build.** Both ran on g_iter24, before the movement fix. g_iter25 removed
   the strafe penalty (0% against 50.6%, 26% more moves), and retreat's entire
   cost is travel.
2. **The instrument.** Iteration 150 was decided on a MIRROR reading of 46.3%, and
   the mirror has since been shown unreliable in both directions.

**Mechanism passed on both axes, and the old failure mode reversed.** Matched
g_iter26 control on rift:

    metric                  control    iteration 188
    their grabs of us         175          148
    our cheeseTransferred   12865        13607

Retreat now *raises* income rather than cutting it — the movement fix really did
change the economics, exactly as predicted.

**Result — REJECT, and decisively.**

    instrument      g_iter26        iteration 188
    peers         76/108 (70.4%)   67/108 (62.0%)   13 changed: 2 gained, 11 LOST
      pure_coop     23/54 (43%)     15/54 (28%)

Nine peer games, directional. **The mechanism improved and the outcome got much
worse**, which means retreat is bad on its merits rather than badly gated — the
rats that walk home are not paying for the trip in lost cheese any more, they are
simply not where they are needed.

**And the mirror was right this time.** Iteration 150's 46.3% correctly predicted
a rejection that the peers now confirm at -9. The mirror has now been wrong about
the emergency override and the cat traps, and right about the desperation flag and
this — which is the point of
`an-instrument-that-cannot-pose-the-situation-says-inert`: **nothing in a mirror
reading tells you which case you are in.** Re-opening on a changed cause was still
correct procedure; it cost one run and converted a weakly-founded rejection into a
firmly-founded one.

## Iteration 189 — action-histogram audit of what the opponent does that we do not

Every remaining lead had been a variation on something we already do. This asks
the other question systematically: enumerate the opponent's actions, normalised
per round, and diff against ours.

    per 1000 rounds        ours    theirs   ratio      (bench_stroke rift)
    CheesePickup          314.0     943.0    3.0x
    CheeseTransfer        237.5     469.5    2.0x
    RatAttack             149.5     393.0    2.6x
    RatNap                 84.5    1426.0   16.9x
    RatSqueak               0.0    1228.0     inf
    ThrowRat                0.0     145.5     inf
    PlaceTrap              11.5     121.5   10.6x
    TriggerTrap            31.5       5.5    0.2x

**The largest apparent gap dissolves on normalisation.** They pick up 3-14x more
cheese, which looked like a collection-efficiency problem — the one economic
avenue not already closed. Per rat:

    cheese pickups per rat per 1000 rounds
      rift              ours 17.6   theirs 16.9   1.0x   (mean rats 18 vs 56)
      closeup (stroke)  ours 14.4   theirs 26.0   1.8x   (4 vs 29)
      closeup (finalist) ours 19.6  theirs 25.6   1.3x   (3 vs 26)

**Our rats are as efficient at collecting as theirs — at parity on rift.** The
entire cheese gap is army size, which is downstream of rats dying, which is
closed. That kills the collection lead before it cost a run, and it is exactly
`normalize-per-round-before-comparing` applied to a per-unit denominator instead
of a per-round one.

**Squeak, throw, attack volume and RatNap are all already closed** (Iterations
166/170, 143/152/177, 156/158, 143).

**What remains unexplained is the trap asymmetry.** They place traps at 10.6x our
rate, and the trigger balance is inverted: our robots step on their traps 31.5
times per 1000 rounds while theirs step on ours 5.5 times — **a 6x disadvantage on
the one weapon the audit found to be our most effective** (Iteration 174: the ring
is worth four benchmark wins and every close-spawn win).

Our ring is capped by geometry: `findTrapLocation` scans ~21 tiles within
`RAT_KING_BUILD_DISTANCE_SQUARED` 8 of a King that never moves, and we run 16-22
live of a team cap of 25. Theirs is not, because **their King moves** (14-79
distinct positions against our 1, Iteration 167), so it can seed traps across the
map.

Already tried and failed: rat-placed traps anywhere (157 arm A: 13 placed, zero
extra triggers), in a band near our King (157 arm B: worse), and threat-biased
King placement (169: triggers tripled on one map, peers -3). **Untried: placing on
the approaches to CHEESE MINES**, which are static and which enemy rats must visit
repeatedly — `hasCheeseMine` blocks the mine tile itself but not its neighbours.
Headroom is small (3-9 traps under the cap) but the location has never been tested.

## Iteration 190 — trap the approaches to cheese mines — REJECTED

The one lead surviving the Iteration 189 audit. Their robots step on our traps 5.5
times per 1000 rounds while ours step on theirs 31.5 — a 6x disadvantage on our
most effective weapon — because our ring is capped by geometry around a King that
never moves. Three earlier placement variants failed and shared a flaw: none put a
trap where an enemy has a *reason* to walk. A cheese mine is static, both teams
must visit it, and a collecting rat is already standing there, so unlike Iteration
157 there is no travel cost.

**Mechanism passed on both conditions:**

    matched g_iter26 control on rift    control   iteration 190
    rat-placed traps                        0          10
    enemy triggers on OUR traps            11          21     (nearly doubled)
    King ring placements                   23          32     (ROSE)

The ring was not cannibalised — the opposite. More triggers free cap slots, so the
King places *more*. That is the failure mode of Iterations 157 and 169 avoided.

**A stale control nearly inverted this reading.** My first comparison used 39 ring
placements and 23 triggers, carried over from a g_iter25-era replay, against which
iter190 looked like a clear VOID (both numbers *falling*). The matched g_iter26
control reads 23 and 11, against which both *rose*. Same discipline as Iteration
162, and this time it saved a correct result rather than catching a wrong one.

**Result — REJECT.**

    instrument      g_iter26        iteration 190
    peers         76/108 (70.4%)   75/108 (69.4%)   21 changed: 10 gained, 11 lost
    benchmarks      8/162             7/162
    close-spawn      4/42              3/42
    early wipes        14                14

Churn on both instruments, no gain.

**Why doubling the trigger rate buys nothing.** Ten extra triggers at RAT_TRAP's
50 damage is ~500 damage, about five rats' worth — against an opponent we already
lose to at 7 kills per 347 losses (Iteration 156). **Trap damage converts no better
than bite damage does.** The 6x trigger asymmetry the audit found is real, and
closing it is simply not worth much, because killing a few more enemy rats has
never been the constraint.

That closes the last lead from the action-histogram audit, and with it the trap
LOCATION question in its fourth and final variant.

## Iteration 191 — leash the foragers — REJECTED, and it completes the picture

Following the regularity that damage does not convert, the axis to push was
survival: the three changes that ever paid were all about *not losing* things.
Every counter to our death budget was already closed, so the untried form was
having fewer rats where they die — `explore()` walks a fixed compass heading with
no leash at all, so rats wander arbitrarily far into ground the opponent controls.

**Mechanism passed, and dramatically.** Matched g_iter26 control on rift:

    leash             rat deaths /1000r      cheeseTransferred
    none (control)          82.5                  12865
    d^2 400 (20 tiles)      72.0                  11857
    d^2 100 (10 tiles)      42.5                   5729

A 10-tile leash **halves our death rate**. Every previous survival attempt failed
its mechanism check or barely moved it; this one works outright.

**Result — REJECT, decisively, on the 20-tile version.**

    instrument      g_iter26        leash d^2 400
    peers         76/108 (70.4%)   58/108 (53.7%)   20 changed: 1 gained, 19 LOST
      pure_coop     23/54 (43%)      9/54 (17%)

Eighteen peer games, 19 of 20 changed games lost. The tighter leash was not even
worth running.

**Survival does not convert either, if it is bought with inactivity.** That is the
mirror of `damage-does-not-convert` and it completes the picture: our rats die
*because* they are doing the thing that wins games, and keeping them alive by
keeping them home destroys more than it saves. The 52%-of-deaths-are-throws figure
is a cost of doing business, not a leak to be plugged.

**What that means for the loss model.** Both halves of the obvious framing are now
measured and both are wrong:

    "deal more damage"   five iterations, all mechanism-proven, all nil
    "lose fewer rats"    leash halves deaths, costs 18 peer games

The value in this bot has come from three things only, and all three are about
**capability preserved at zero marginal cost** — the trap ring (a standing defence
that costs no actions once placed), the emergency override (spends cheese we were
hoarding anyway), and the movement fix (removes waste with no trade-off at all).
None of them buys an advantage by trading one resource for another. That is a
narrow and demanding class, and it is the honest place to look next.

## Iteration 192 — waste audit: the zero-marginal-cost class is empty

The search criterion the evidence dictates. Both halves of the obvious framing are
measured and wrong, so the remaining class is **waste** — places the bot pays a
cost and gets nothing, removable without giving anything up. That is exactly what
the movement fix was, and it is worth +14 peer games.

Measured on the matched g_iter26 control (rift vs bench_stroke, 38097 rat-turns):

**1. Wasted turns — 6.9%, real but small.**

    rat-turns with no move, no turn, no action:   4799   12.6%
      of which movement-cooldown bound:           2186   45.6% of those
      TRULY idle (could have acted, did not):     2613    6.9% of all rat-turns

**2. Wasted travel — NOT waste. The control inverted the reading.**

    steps per unit of net displacement:   11.7x
    tile revisits by the same rat:        74% of all steps

That looks damning, and it is not, because a forager shuttling between mine and
King revisits tiles by design and ends where it started. Against the opponent on
the same map:

    OURS     167 rats,  31852 steps,  74% revisits
    THEIRS   180 rats,  93451 steps,  93% revisits

**They revisit more than we do, while winning.** An absolute path-efficiency
number means nothing without a control; had I stopped at 74% I would have spent a
run straightening routes that are not crooked. (They also take 2.9x more steps
with ~3x more rats, i.e. per-rat parity — the same normalisation result as
Iteration 189's cheese finding.)

**3. Wasted cheese — no evidence.** Pickups run 314 per 1000 rounds against 237.5
transfers, about 1.3 pickups per delivery, so rats are not hoarding into the
`CHEESE_COOLDOWN_PENALTY`. `deliverCheese` firing at `getRawCheese() > 0` is doing
its job.

**4. Wasted King actions — already answered.** Iteration 171 measured ~11%
utilisation and established the idleness is cheese-gated, not squandered.

**Conclusion: there is no large waste left to remove.** The only candidate is 6.9%
of rat-turns, and those rats are mostly boxed in by terrain or allies rather than
choosing to do nothing. **The zero-marginal-cost class has been searched and is
essentially empty** — the movement fix appears to have been the one big piece of
free value in the bot, which is consistent with it being the largest single
improvement measured (+14 peer games).

That is the honest state: g_iter26 is a local optimum under every framing this run
could construct — more damage, fewer losses, and less waste have all been tested
and all are exhausted.

## Iteration 193 — instrument verification: benchmarks are deterministic too

The peer gauntlet was verified byte-reproducible at Iteration 174 (a repeat run of
an unmodified build gave identical sub-scores). The benchmark set never was, and
every benchmark comparison in this run used the single 20260905-005757 control —
so a non-deterministic pipeline would have invalidated all of them.

Re-ran that control on an unchanged g_iter26:

    repeat    8/162   bench_finalist 4/54, bench_spaark 2/54, bench_stroke 2/54
    control   8/162   bench_finalist 4/54, bench_spaark 2/54, bench_stroke 2/54
    sorted results.csv diff: 0 lines

**Identical, game for game.** A raw `diff -q` reports the files as differing, but
only because games finish in a different order under `MAXJOBS=6` parallelism — the
sorted comparison is empty. Worth noting because the naive check is misleading,
and because every game-by-game diff in this run keyed on
`(opponent, map, side)` rather than row order, so none was affected.

Integrity alongside it: archetypes rebuilt from the newest accepted snapshot
(`resync_archetypes.py --check` clean), working tree clean, `src/bot`
byte-identical to `src/g_iter26` after roughly twenty revert cycles.

**Both instruments are now verified deterministic**, which retroactively firms up
the whole run: every "N games changed, X gained Y lost" diff is a true statement
about the change rather than about run-to-run variation, and the churn-versus-
directional distinction that decided most of these iterations rests on solid
ground.

## Iteration 194 — attack the enemy King — CLOSED on the pre-check, no code

The last strategic asymmetry standing: cats deal the enemy King's damage and we
deal essentially none (Iteration 165 — 29 drops of exactly 20, 88 adjacent cat
turns against 4 of ours), yet 91% of games end in King destruction. Our win
condition is outsourced to a third party we cannot steer.

The pre-check asked whether any targeting scheme could possibly help — how often
is the enemy King inside one of our rats' vision at all?

    replay                       rounds our rat within d^2 20 of their King
    rift     vs bench_stroke          0 of 1999   (0.0%)
    closeup  vs bench_stroke          0 of  472   (0.0%)
    closeup  vs bench_finalist        0 of  864   (0.0%)

**Never. Not rare — zero, across 3335 sampled rounds.** No targeting, bearing or
priority scheme can act on a unit no rat ever sees, which is also why Iteration
164's King-preference was byte-identical and why Iteration 138 gated off the
desperation raid.

And the obvious fix is already closed from the other end: sending rats far from
home is precisely what Iteration 191's leash test measured, and *shortening* their
range halved deaths while costing 18 peer games — so our rats stay near home
because that is where the value is, and the enemy King is not reachable from
there.

**This closes the last open direction.** The final state of the search:

    deal more damage        five iterations, mechanism-proven, all nil
    lose fewer rats         leash halved deaths, -18 peer games
    remove waste            6.9% idle turns; path "inefficiency" is lower than
                            the opponent's (74% vs 93% revisits)
    attack the win condition our rats never see the enemy King, in any game

g_iter26 stands as a local optimum under every framing this run could construct,
on two instruments both verified deterministic (Iterations 174, 193).

## Iteration 195 — what produces our wins, and why there is no lever on it

Every trace in this project had examined a loss. With all loss-driven directions
closed, this examines the wins.

**They are extraordinarily concentrated.** g_iter26's 8 benchmark wins fall on
three maps and nowhere else:

    popthecork 4/6    whatsthecatdoin 2/6    peaceinourtime 2/6
    all 24 other maps 0/6

**No structural property distinguishes those maps.** Size, cat count, King-to-King
distance and symmetry, compared across win and loss maps:

    popthecork   WIN   30x40  4 cats  KingDist 17
    dirtfulcat   loss  30x30  2 cats  KingDist 15
    peaceinourtime WIN 35x50  2 cats  KingDist 30
    closeup      loss  30x30  2 cats  KingDist 38

`popthecork` and `dirtfulcat` are near-identical on every axis and give 4/6 and
0/6.

**Nor is it whether cats reach the enemy King** — the obvious hypothesis, and it
dies immediately:

    case                  cat-turns near THEIR King   near OUR King
    popthecork   WIN               112                      0
    closeup      LOSS              290                      0

Cats swarm the enemy King on `closeup` almost three times as heavily as on the map
we win, and we lose anyway.

**Because it is a RACE, and we are not in it.** Their King's HP at the moment ours
dies, across eight benchmark losses:

    600, 600, 600, 600, 600, 600, 600, 120

**Seven of eight losses end with the enemy King untouched at full health.** The
race is not close, so extending our own survival — the one lever the race framing
suggested — buys nothing: there is no cat progress waiting to be finished.

**Conclusion: our win condition is outsourced to a third party we cannot steer.**
We win when cats happen to grind the enemy King down before anything happens to
ours, which occurs on three maps out of 27. We have no lever on cats: the
"squeaks move cats" claim was withdrawn as a normalisation error (Iteration 167,
cat activity per round is flat at ~4 regardless of squeaking), and every attempt
to reach the enemy King ourselves is closed because **our rats are within d^2 20
of it in 0 of 3335 sampled rounds** (Iteration 194).

**g_iter26 is at its ceiling against this benchmark set**, and the ceiling is
structural rather than a tuning failure. The bot's own contribution — surviving,
collecting, defending the King — is fully optimised under every framing this run
constructed; what it cannot do is produce the event that actually wins games.

## Iteration 196 — endgame cheese push — VOID on the pre-check

With benchmarks shown to be cat-bounded, policy work belongs on the peers, whose
19 points-losses have margins from -0.6 to -21.2. The -0.6 game does not need
catDamage (closed in both directions) — it needs 0.6 points from *any* term, and
cheese is the one nobody had tried to win, because economy work was closed on
THROUGHPUT grounds, which is a different claim from shifting the share.

**The pre-check closes it.** `deliverCheese` already sits above the combat block
and fires unconditionally whenever a rat carries anything, so there is no endgame
priority to add. And the term is not merely close, it is at parity — on
`safelycontained`, the -0.6 game itself:

    ours     1205 pickups / 1011 transfers
    theirs   1225 pickups / 1027 transfers

Within 2%. There is no headroom to take, which is consistent with Iteration 189's
finding that our per-rat collection rate matches theirs.

**So the cheapest scoring margin in the project is unreachable**, and with it the
last identified target. The scored terms now stand as: catDamage closed in both
directions, kings closed (a second King is worth +16.7 against margins of -33 to
-64, and cost 28 peer games when tested), cheese at parity with delivery already
maximal.

### State of the search

    axis                     verdict
    deal more damage         five iterations, mechanism-proven, all nil
    lose fewer rats          leash halved deaths, -18 peer games
    remove waste             6.9% idle turns; we revisit LESS than the opponent
    attack the win condition our rats never see the enemy King (0 of 3335 rounds)
    win the scored terms     catDamage closed, kings closed, cheese at parity
    benchmark ceiling        7 of 8 losses end with the enemy King untouched

g_iter26 stands as a local optimum under every framing this run constructed, on
two instruments both verified byte-deterministic (Iterations 174, 193). Further
progress needs an idea from outside the space this session searched — not another
variation inside it.
