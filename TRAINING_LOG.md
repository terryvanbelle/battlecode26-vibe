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
