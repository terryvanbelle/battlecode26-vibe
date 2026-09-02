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
