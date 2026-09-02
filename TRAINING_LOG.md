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
`tools/bc26_replay.py` reads a replay back out as a text transcript.

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

**Gauntlet (step 2/3).** Pending — running now (`MAPSET=loop`, `OPPONENTS=examplefuncsplayer`).
