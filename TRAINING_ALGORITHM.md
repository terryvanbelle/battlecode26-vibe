# Battlecode 2026 Automatic Learning Algorithm

This document outlines a technique for writing a world-class Battlecode 2026
("Uneasy Alliances") bot. It's a from-scratch rewrite of the process this
project's predecessor, [`battlecode22-vibe`](https://github.com/terryvanbelle/battlecode22-vibe),
converged on over 128 logged iterations — see that repo's `LEARNINGS.md` for
the full account of what worked and why. The core loop (Gauntlet, baseline
diffing, hypothesis/solution generation, high-risk structural exploration) is
carried over largely unchanged, because it's ruleset-agnostic; the sections
below that are genuinely new (Cooperation & backstab, the cat as a
third-party NPC, chirality) reflect what's actually different about this
year's game. Read `RULES.md` first if you haven't.

## Play symmetry

Same underlying discipline as before, adapted for a mechanic BC26 has that
BC22 didn't: **chirality**. The engine itself now flips each robot's own
sensing/part-iteration order under the map's symmetry (a robot with
chirality 1 senses/iterates in the mirrored order of chirality 0), which
directly targets the single largest bug class BC22's history ever found (see
that project's `LEARNINGS.md`, "Fixed absolute-order tie-breaks silently
encode positional bias" — half a project's worth of tempo asymmetry traced to
`senseNearbyRobots()`'s fixed scan order interacting with map geometry).

**This does not make our own code symmetric for free.** Chirality only
covers the engine's own sensing/part-order primitives. Every tie-break,
default direction, or "first match wins" decision *we* write is exactly as
exploitable to this bug class as it was in BC22:

- Any fixed-order iteration over a `Direction[]` array for tie-breaking.
- Any hardcoded `Direction.NORTH`/`SOUTH`/`EAST`/`WEST` fallback.
- Any reference to `Team.A`/`Team.B` specifically, as opposed to
  `rc.getTeam()`/`.opponent()`.
- Anything keyed off `rc.getID()` in a way that could correlate with spawn
  order or team assignment (using it to seed a genuinely random, per-robot
  tiebreak is fine — the test is whether it correlates with team, not
  whether it's used at all).
- **New to this ruleset:** any absolute-coordinate reasoning in squeak
  target selection, ratnap/throw direction choice, or shared-array slot
  layout that isn't derived from the map's own symmetry axis.

Audit for this from day one — don't wait for a mirror-match to surface it by
accident the way BC22 effectively did for ~60 iterations. **Mirror-match
check**: run the current bot against a byte-identical copy of itself
(`tools/snapshot.sh mirror_check; TEAM_A=bot TEAM_B=mirror_check
tools/vm-match.sh <map>` then swap sides) periodically and on every change
that touches tie-breaking or default-direction logic; a clean 50/50-ish
split (given the point-system's own tiebreak coin flip, expect *some* noise,
not a hard 50/50) is the expected result, and a persistent lopsided split on
a specific map is a real, fixable bug, not something to explain away.

## Cooperation and backstab strategy

This is genuinely new territory — BC22 was strictly adversarial 1v1 (plus a
neutral environment), so nothing in that project's history informs when (or
whether) to defect from cooperation. Treat this as its own first-class
hypothesis space, not a detail to bolt onto a combat/economy bot late.

Some starting framing (to be revised by evidence, not treated as settled):

- **The backstab decision is discrete and irreversible within a game.**
  Once triggered, there's no path back to cooperation-mode scoring for the
  rest of that game. This makes it a genuine commitment device: the bot
  needs a clear, evidence-based trigger condition (e.g. cats are nearly
  dead and our cheese/rat-king share is currently losing; or the enemy team
  has visibly weakened itself fighting cats and we could win a fight now),
  not a vague "opportunistic" heuristic that never actually fires or fires
  by accident (a stray bite, a triggered trap).
- **A backstab can be accidental.** Any bite, trap trigger, or ratnap
  against an enemy rat starts one — including a bite thrown in the
  chaos of a crowded cat fight where friendly-fire-adjacent targeting bugs
  could misfire. Defensive coding here (never target/attack an enemy rat
  without an explicit, deliberate "yes, backstab now" decision gating that
  code path) matters more than it would in a strictly-adversarial game,
  where a stray attack has no equivalent hidden cost.
- **The point formula weights differently by end-state**: cooperation-mode
  endings weight cat damage highest (0.5) and living-rat-king count lowest
  (0.3); backstab-mode endings flip that (0.5 living-rat-kings, 0.3 cat
  damage). This means the *value* of backstabbing depends on your own
  standing in each of the three point components at the moment you'd do it
  — backstabbing while ahead on cat-damage share but only-so-so on rat-king
  count is a very different bet than the reverse.
- **Opponent modeling matters here in a way it didn't in BC22.** Whether to
  backstab is a function of what the *other* team is likely to do, not just
  our own state — closer to a game-theoretic mixed strategy than a
  threshold rule. Early Gauntlet opponents should deliberately span the
  policy space (see "Backstab-policy coverage" below) specifically so we
  can observe how our own bot's decision function performs against each
  pole, rather than over-fitting to a single opponent archetype.

### Backstab-policy coverage

Because there is essentially no library of strong external BC26 bots yet
(the game only just finished its first live season — contrast BC22, where
real tournament finalists' source was available almost immediately), the
Gauntlet's opponent pool should deliberately include a small set of
**synthetic reference archetypes** representing different points on the
cooperation/backstab policy space, built and vendored by us if nothing
external of comparable clarity turns up:

- **Pure cooperator** — never backstabs, focuses entirely on cat damage and
  cheese economy. Tests whether our bot recognizes a safe opponent and
  avoids an unforced backstab.
- **Immediate defector** — backstabs on the first opportunity (or even
  turn 1 via a deliberate trap), then turtles around its rat king(s). Tests
  our bot's resilience to a worst-case-early betrayal and whether it
  recovers economically.
  **Opportunistic** — cooperates until cats are mostly dead or its own
  rat-king count is safely ahead, then defects. The hardest and most
  realistic opponent archetype; also the one worth building last, once
  we've verified the two simpler archetypes work as intended test
  instruments.

Build and vendor these early (ideally alongside or shortly after Iteration
0) — see Step 4's "never idle" guidance; searching for real external BC26
entries is also worth doing periodically (see "Growing the Gauntlet") but
shouldn't block having *some* backstab-policy coverage in the pool.

## The cat as a public, deterministic third party

Unlike BC22, every game includes an NPC whose full decision algorithm is
published in the spec and vendored in this repo's reference
(`RULES.md`, and the actual engine source under
`engine/src/main/battlecode/world/` in the upstream `battlecode26` repo,
worth pulling and reading directly the way BC22 read `sample_camelcase`'s
source rather than guessing). This means:

- **Cat position/behavior is knowable in advance**, not just sensed live.
  A bot that tracks waypoint cycles and mode timers can anticipate a cat's
  next several turns without spending vision/bytecode re-deriving it from
  scratch every round. This is a legitimate, first-class optimization
  target — closer to solving a puzzle than reacting to an adversary.
- **Squeaking is a real double-edged signal**: cats hear squeaks and turn
  toward the source while in Attack mode. A coordination broadcast near an
  attack-mode cat is a real, mechanistically-grounded risk, not
  flavor text — verify with `--squeaks`/`--all-actions` in the replay tool
  whether a given squeak actually drew cat attention before assuming it's
  free.
- **Cat damage output is a shared, contestable resource** in cooperation
  mode (both teams' cat-damage-dealt counts toward the score split) — unlike
  BC22, "fighting the enemy" isn't the only lever; out-damaging the *other
  team's* cat contribution while ostensibly cooperating is a legitimate,
  distinct strategy from actually attacking them.

## The Gauntlet

Define "the Gauntlet" as a set of Battlecode bot implementations used to
test our current implementation. It starts with `examplefuncsplayer` plus
whatever synthetic backstab-policy archetypes we build (see above), and
grows from there.

**Unit of evaluation: a single game**, not a best-of-3 match. The official
spec scores a *match* as best-of-3 games, but nothing in the scaffold's
`build.gradle` (`runJavaLocal`, `runLocal`) currently orchestrates a
3-game match as one invocation — each run plays exactly one game on one map,
same as BC22's per-map games. Given that, and to keep this project's own
tooling (`tools/gauntlet.sh`) simple and directly comparable to the BC22
project's proven approach, **the Gauntlet evaluates single games**, one per
`(opponent, map, side)` triple, exactly like BC22's `2 · B · N` structure.
This is a deliberate simplification, not a confirmed fact about how the
official tournament runner works — if a native multi-game match mode turns
up later (check `run.py`'s `task_run`/`task_verify` or the upstream
`battlecode26` repo's own tournament-runner code, don't just assume), revisit
this. Until then, treat "does the bot perform well across many independent
single games spanning both coop-only and backstab outcomes" as the
practical proxy for match strength.

To "run the Gauntlet": for each bot in the Gauntlet, play on all maps in the
current map pool, once with the current implementation as team A and once as
team B. Running the Gauntlet means `2 · B · N` games.

### Growing the Gauntlet

- **New iterations.** Every implementation that passes Step 3 is added.
- **Backstab-policy archetypes.** See above — fill this coverage gap early,
  it's structural to this game in a way nothing in the Gauntlet naturally
  covers otherwise.
- **External bots.** Periodically search GitHub (and the Battlecode Discord/
  forum, if accessible) for other BC26 entries, especially any with
  published post-mortems from the just-concluded live season. Vendor
  anything that compiles cleanly; record source URL and licence. Expect
  this to be thinner than BC22's pool was initially (BC22 had years of
  accumulated community bots; BC26 has had exactly one season) — don't
  treat an empty search as a process failure, just try again in a few
  iterations as more entries get published publicly.

### Peer opponents vs. benchmark opponents

Same split as BC22, same rationale (a bot we currently lose to is the
*target*, not noise, and shouldn't gate progress or dominate the game
budget while the gap closes):

- **Peer** — current implementation wins ~30-90% of games against it. Every
  frozen prior iteration starts as a peer.
- **Benchmark** — current implementation wins **< 30%**. Strong external
  bots or a well-tuned opportunistic-backstab archetype might start here.

Reclassify after each Gauntlet: benchmark → peer at **≥ 30%**; peer →
benchmark at **< 20% for two consecutive Gauntlets**.

- **Step 3 (accept gate) uses peer games only.** Benchmark games are
  recorded but excluded from `WinPct`.
- **Step 4 (pick a losing game) draws from all games, benchmark included.**
- **Benchmark bots play only every `BenchmarkEvery` Gauntlets** (always on a
  snapshot-candidate Gauntlet).

### Retiring bots from the Gauntlet

> After a Gauntlet completes, any opponent beaten in **≥ 80%** of that
> opponent's `2 · B` games in **two consecutive** Gauntlets is removed.

Applies to reference bots, external bots, and frozen prior iterations alike.
Bots we *lose* to are never retired by this rule — only domination retires.

### The baseline, and comparing Gauntlet runs by shape

Same discipline as BC22: **diff two Gauntlet runs game-by-game** (same
`(opponent, map, side)` key), not aggregate win rate alone. Note this diff
doubles as the arm-to-arm identity check described under "Three cheap
checks" above -- if every key matches, the two builds are the same bot and
no interpretation of the win rate is warranted.

- **The baseline** is the most recently accepted iteration's own full
  Gauntlet run, superseded every time Step 3 accepts.
- **Reading a diff's shape**: a small number of scattered, mixed-direction
  diffs is likely noise (expect this to matter *more* here than in BC22 —
  the coin-flip tiebreak on tied points is a genuine, intentional source of
  randomness this game has that BC22 didn't, on top of the same
  chaos-sensitivity BC22 found in long games). A one-directional and/or
  single-map-concentrated diff across multiple opponents is likely a real,
  causal regression — reproduce and trace it (`tools/bc26_replay.py
  --metrics --indicators --all-actions`) before deciding.
- **New wrinkle vs. BC22**: a flipped outcome here can come from a changed
  *win type*, not just a changed side. Two games that are both "wins" can
  differ meaningfully — a `RATKING_DESTROYED` win is a much stronger result
  than a `COIN_FLIP` win on a tied point score. Track win type (already
  captured by `tools/gauntlet.sh`'s `REASON` line) alongside win/loss when
  judging whether a diff represents real progress.

## Iteration 0

The initial implementation is deliberately minimal: a single Rat King
(unavoidable — every team starts with exactly one) that does nothing but
spawn one Baby Rat and have it wander, with **live bytecode-budget
monitoring wired in from the start** — same lesson BC22 learned the hard
way (a silent bytecode overrun pauses a robot's turn mid-instruction and
resumes next round with no exception; this can quietly break a strategy
long before anyone notices). Concretely: compare `rc.getRoundNum()` before
and after a robot's own per-turn logic to catch confirmed overruns, and
`Clock.getBytecodeNum()` against the robot type's bytecode limit
(`RAT`=17500, `RAT_KING`=20000) to catch near-misses; surface both via
`rc.setIndicatorString()` so it's visible in every replay, and keep checking
it as a standing part of verification (Step 6, and every full Gauntlet)
rather than retrofitting it later.

## The Algorithm

Hyperparameters (unchanged from BC22 by default — revisit if BC26's shorter
games/smaller maps/different game-length distribution turn out to warrant
different values, but there's no evidence for that yet):

- *WinPct*: 60% of peer games required to accept.
- *MaxHypothesisIterations*: 10.
- *MaxSolutionsIterations*: 10.
- *BenchmarkEvery*: 3.
- *ReproSampleSize*: 8 peers for Step 6.5's cheap reproduction sample,
  recomputed fresh (evenly spaced across the current peer roster) each time.
- *NearMissMargin*: 5 points below *WinPct* still counts as a near miss.
- *MaxNearMissRefinements*: 3.
- *MaxConsecutiveRejects*: 3 consecutive Step 6 rejections before the next
  attempt must leave the same functional area (see "Never idle").

### Instruments, ranked by resolution

Added 2026-09-03 after a day in which the acceptance headline was
uninformative in five of six audited cases, and inverted the sign twice.
**Rank a Gauntlet by whether it is an EVEN matchup, not by how much you like
what it measures.**

| instrument | matchup | typical win rate | use |
|---|---|---|---|
| `g_iter<latest>` mirror | **even** | 50% by construction | **primary accept test** |
| peers (`pure_cooperator`, `immediate_defector`) | **even** | ~60-70% | regression check |
| `vs_old_bots` (`g_iter1`, `g_iter11`) | lopsided | ~85-90% | direction only |
| benchmarks (tournament bots) | lopsided | ~3% | direction only |

An instrument pinned near 0% or 100% cannot resolve a few games: at ~3% and
~88% respectively, +/-2 games is the noise floor. **The mirror is pinned at
50% by construction and plays both sides, so a 4-game swing is real signal.**

Consequences, each learned the hard way:

- **Do not accept on a lopsided instrument.** Iteration 63 was accepted on
  benchmarks (+2) and `vs_old_bots` (+2) over a peer drop to 38%, then the
  mirror returned **33.3%** and the accept was retracted. Both endorsing
  instruments were lopsided; both dissenting ones were even.
- **Do not reject on one either.** Iteration 78 scored 4/162 against a 5/162
  benchmark control and was written off as closing an entire line of work.
  Retested on the mirror as Iteration 88 it was **+4 games**, +8 on peers,
  and became an accepted iteration.
- **A dissenting even instrument is evidence, not an obstacle to explain
  away.** The 9%-vs-59% scoring-regime difference between benchmarks and
  peers is real and measured, and using it to dismiss the peer regression is
  what produced the retracted accept. A true observation can still license a
  wrong conclusion.

### Three cheap checks that cost no Gauntlet run

**1. Arm-to-arm identity.** Before interpreting any result, confirm the two
builds actually differ:

```python
# same (opponent, map, side) -> (result, rounds)?
same = sum(1 for k in armA if armA[k] == armB.get(k))
```

`same == n` means the change never executed. This caught Iteration 66
(161/162 identical to control), Iteration 68 (162/162), and Iteration 70,
where a "dose" of a radius from 8 to 2 produced **162/162 identical games**
because the radius fed a check that only ever looked one tile ahead. Read
against the control alone those two arms looked like a flat dose-response
curve; they were the same bot.

**2. Normalise per round.** Every replay counter scales with game length, so
a change that makes you survive longer reads as a regression. Iteration 72
showed deaths "rising" 67 -> 78 while the death *rate* was flat (0.0570 vs
0.0589 per round) and the game ran 150 rounds longer. Divide by rounds
before comparing deaths, `cheeseTransferred`, `catDamage`, trap triggers.

**3. Correlate before attributing.** When blaming a mechanism for a cost,
check timestamps rather than assuming: of 78 deaths only 5 fell within 5
rounds of one of our 43 throws, which refuted the "landings are killing us"
theory an iteration had already been built on.

### Dose-response, and what an inverted arm actually means

The Gauntlet is deterministic -- identical code returns byte-identical
results -- so re-running a marginal result yields **zero** new information.
Vary the size of the mechanism instead.

- **A parameter is only a dose if it changes the condition actually
  evaluated.** See Iteration 70 above.
- **An inverted high-dose arm rejects THAT DOSE, not the mechanism.**
  Iteration 45 measured 4 cheese at 53.7% and 16 at 37.0%, and the negative
  slope was read as condemning the low dose too. That assumes monotonicity.
  Measuring the missing zero arm gave **46.3%**, i.e. a concave curve with an
  interior optimum at 4. **Always measure zero.**
- **A curve that peaks in the middle is stronger evidence than any single
  point.** Iteration 90 was +2 games alone -- marginal -- but the arms around
  it (gate 1200 -> 50%, 1000 -> 53.7%, 600 -> 40.7%) made it structure. Same
  for Iteration 92 (reserve 1000 -> 50%, 400 -> 55.6%, 150 -> 40.7%).
- **A dose can confirm a mechanism that inspection cannot.** Iteration 92's
  replay check contradicted its own prediction on the map examined; the dose
  curve established causality by response instead.

### Ablate accepted features, not just new ideas

Six features carried by the bot were ablated on the mirror on 2026-09-03,
each having been accepted on a lopsided instrument or on no measurement at
all:

| feature | headline at acceptance | mirror without it | true value |
|---|---|---|---|
| exploration-heading reassignment | 75.0% | 22.2% | **~+28** |
| `REPLACEMENT_RESERVE` | 90.0% | 25.9% | **~+24** |
| Bug2 navigation | *unmeasured, mechanistic* | 44.4% | ~+5.6 |
| cheese-boosted bite | formally **rejected** | 46.3% | ~+4 |
| emergency build override | **95.0%** | 48.1% | **~0** |
| King trap ring | "0% -> 7-10%" | 57.4% | **negative** |

Two features carry nearly all the value and both are *failure-mode
preventers*, not tactics. Roughly 25 tactical iterations that day all failed;
every accepted change was of the form "detect a degenerate state and stop
doing it". **When the loop stalls on new ideas, ablate instead** -- it is one
54-game run per feature and it found more real corrections than invention
did.

Procedure: gate the feature behind `final boolean X_ENABLED = false`, run the
mirror, then restore and verify with a comment-stripped diff against the
snapshot so only intended changes survive:

```bash
strip() { sed 's://.*::' "$1" | sed 's/^[[:space:]]*//' | grep -v '^$'; }
diff <(strip src/bot/RobotPlayer.java) <(strip src/g_iter<N>/RobotPlayer.java)
```

### Finding the next hypothesis: trace, do not theorise

The three accepted changes of 2026-09-03 all came from tracing a replay's
per-100-round activity and resource profile and looking for a *stall*, not
from reasoning about what a good bot would do:

- action counts collapsing to 6-13 for 350 rounds while holding 1271-1581
  cheese -> the population cap was blocking builds while we were rich
- cheese pinned in a 200-wide band for 1900 rounds -> two independently-set
  thresholds (cap gate 1200, build reserve 1000) had left a dead band
- the hover point tracking the reserve exactly -> the reserve, not the gate,
  sets the equilibrium

Each trace also killed at least one of my own hypotheses before it cost an
iteration. Trace first.

### Head-to-head against the current best is the primary accept test

Added 2026-09-03, after the peer roster stopped being able to detect
improvement at all. The synthetic archetypes deliberately share
`src/bot/`'s economy/movement code so they isolate *backstab policy* --
which means that once correctly re-synced they differ from `bot` by
about eight lines, are effectively mirror matches, and land at ~50%
**by construction, regardless of how strong the bot is**. Letting them
go stale produces the opposite failure: inflated numbers that look like
progress (this happened twice, most recently masking a 62.5% as 95.0%).
Neither state yields a usable accept signal.

**So the primary accept test is now: run the candidate against the
most recent accepted snapshot, head to head.**

    OPPONENTS="g_iter<latest>" tools/gauntlet.sh     # 20 games, both sides

- **> 50%** means the candidate genuinely beats what it replaces. This
  is immune to both staleness (the snapshot is frozen) and mirror
  collapse (any real improvement shows up as a real edge).
- **~50%** means no measurable change -- treat as a near miss, not an
  accept, unless there's a separate mechanistic argument.
- **< 50%** is a regression against the thing it would replace.

The peer Gauntlet stays useful, but for what it actually measures:
a **policy/regression check**, where ~50% vs. `pure_cooperator` is the
healthy expected value and a drop meaningfully below it is a real alarm.
The vs-old-bots chart remains the **long-run progress** metric. Three
different questions, three different measurements -- conflating them
under one number is what allowed a 15-point inflation to go unnoticed.

1. Create Iteration 0. Set this to be our current implementation.
2. Run the current implementation against the Gauntlet (all peers, all
   maps, both sides; benchmark bots too if due per *BenchmarkEvery*).
3. Evaluate the Step 2 Gauntlet against the current baseline, by shape.
   1. **Accept:** peer *WinPct* met or exceeded, diff shows no unresolved
      real regression — add to the Gauntlet, snapshot, set as new baseline,
      go to Step 4.
   2. **Near miss:** evaluating a Step 6 solution, within *NearMissMargin*
      points of *WinPct*, no real regression, fewer than
      *MaxNearMissRefinements* spent — go back to Step 6.1 (doesn't count
      against *MaxSolutionsIterations*).
   3. **Reject:** otherwise.
      1. Reproduce and trace the flipped game(s).
      2. A specific, well-understood failure mode → targeted refinement,
         back to Step 6.1 (counts against *MaxSolutionsIterations*).
      3. Otherwise, undo back to the last-accepted iteration, go to Step 4.
4. Select a losing game from Step 2. If none exists, go to Step 2.
   - **Prefer fresh territory** — a soft preference, not a hard rule.
   - **Never idle.** No valid state has nothing being attempted or
     verified. Waiting on a Gauntlet run already in flight is normal
     execution, not idling. If the last *MaxConsecutiveRejects* Step 6
     attempts were all rejected, the next attempt must leave that
     functional area — either a losing game elsewhere, or "High-risk
     structural exploration" below, which is an equally valid *first*
     move, not a last resort. A session is never "done" short of the user
     ending it.
   - **Track areas, not just games** — maintain a running sense (visible in
     `TRAINING_LOG.md`) of which functional areas (Baby Rat economy/cheese
     routing, Rat King production/placement, cat-damage coordination,
     backstab-trigger policy, combat targeting, communication/shared-array
     usage) have been recently attempted.

### High-risk structural exploration

Same standing rule as BC22: a first-class, equally-legitimate track
alongside the incremental loop, not a fallback reached only once
incremental ideas run out.

- **Trigger.** Any of: *MaxConsecutiveRejects* fires; Step 4 finds no fresh
  incremental target after genuinely checking several losing games; a bold
  idea has been sitting unactioned in `TRAINING_LOG.md`'s "Next" notes.
- **Process.** Skip the "one losing game, narrow hypothesis" framing:
  1. Name a capability gap or strategic difference vs. a strong opponent —
     not necessarily traced to one specific game. (BC26-specific candidates
     to consider early: multi-Rat-King economies, an opportunistic-backstab
     policy, cat-waypoint prediction, squeak-based coordination.)
  2. Describe the change at whatever scope it needs.
  3. Implement, then verify with the same rigor as Step 6.4-6.5.
  4. A rejected structural attempt is not a failure state — log the
     learning and immediately pick the next thing.
5. Hypothesis generation
   1. Form a hypothesis for why the current implementation lost.
   2. Determine variables/thresholds that would verify it.
   3. Update instrumentation if needed to capture them.
   4. Re-run the losing game from Step 4.
   5. Extract the variables; if unverified, go to Step 5.9.
   6. **Generality check**: re-run at least one other losing game (prefer a
      different opponent/map) and check the same hypothesis there. Record
      either way.
   7. Held on checked games → Step 5.8. Didn't hold → narrow the hypothesis
      to what it actually explains (continue to 5.8) or treat as
      unverified (go to 5.9) if nothing independent supports it.
   8. **Act-on-it check.** Check `TRAINING_LOG.md` for whether a prior
      iteration deliberately established the behavior this hypothesis wants
      to change, and why. If the obvious fix would revert that reasoning
      without new evidence superseding it, log as verified-but-not-
      actionable and go to Step 4 for a different losing game (doesn't
      count against *MaxHypothesisIterations*). Otherwise continue to
      Step 6.
   9. *MaxHypothesisIterations* exhausted with none verified → Step 4, pick
      a different losing game.
   10. Otherwise, back to Step 5.1.
6. Solution generation
   1. Describe a solution based on the verified hypothesis.
   2. Implement it.
   3. Re-run the losing game from Step 4.
   4. **Mechanistic verification** using the replay's own instrumentation:
      1. **Game now won** → Step 6.5.
      2. **Still lost, but the fix demonstrably engaged as designed, with a
         specific evidenced account for why this game couldn't flip
         regardless** (e.g. opponent's cat-damage share is already so
         dominant the fix's marginal contribution can't close the point
         gap) → real, valid basis to proceed, log both parts, go to 6.5.
      3. **Neither** → Step 6.7.
   5. **Cheap reproduction sample**: *ReproSampleSize* peers, all maps, both
      sides, diffed by shape against baseline.
      - Unambiguous real regression already visible at this scale → Step
        6.7, don't spend a full Gauntlet confirming it.
      - Otherwise (clean, ambiguous, or ordinary noise) → Step 2 for the
        full Gauntlet, **even if the small sample looks clean** — BC22
        found real regressions in build/production-priority and
        resource-threshold changes that only showed up at full scale;
        expect the same category of risk for cheese-spend priority,
        backstab-trigger thresholds, and shared-array write scheduling
        here.
   6. (Step 2/3 now run on this solution. Return here only if rejected.)
   7. Undo the Step 6.2 changes.
   8. *MaxSolutionsIterations* exhausted, none passed Step 3 → Step 4, pick
      a different losing game.
   9. Otherwise, back to Step 6.1.

## Post-accept routine

Four commands, in this order. `plot_progress.py` reads the `src/g_iterN/`
directories, so it is only correct once the new snapshot exists -- create
`src/g_iter<N+1>` first.

```bash
tools/.venv/bin/python3 tools/track_vs_old_bots.py gauntlet/<run-id>/
tools/.venv/bin/python3 tools/plot_vs_old_bots.py       # progress metric
tools/.venv/bin/python3 tools/plot_progress.py          # cumulative iterations
tools/.venv/bin/python3 tools/plot_alt_metrics.py       # peer spread + benchmarks
```

`plot_progress.py` was being skipped for most of 2026-09-03 and the chart sat
stale through two accepts. All four run every time.

**Then commit -- this is the step that makes the other four durable.** Later
the same day an accept was made, `src/bot` edited and `src/g_iter19/` created
without any commit; it surfaced only because `plot_progress.py` reported
`g_iter1..g_iter18` while the directory existed on disk. A chart regenerated
from an uncommitted snapshot depicts something not in the repository. Stage
explicit paths, never `git add -A`:

```bash
git add TRAINING_LOG.md src/bot/RobotPlayer.java src/g_iter<N> progress/
```

**Caution on `vs_old_bots_history.csv`:** `track_vs_old_bots.py` labels a run
with whatever the highest `src/g_iterN` directory is *at the time it runs* --
which says nothing about the code that actually played. Four mislabelled rows
had to be removed on 2026-09-03 after a run of one build was appended under
another build's name. Append only when the working tree matches the label.

## Logging

At each step, log summary statistics and observations in `TRAINING_LOG.md`.

### Replay archive

For each iteration (accepted or rejected), check the single most
interesting game into `replays/` in git — named
`replays/iterNN_<opponent>_<map>_bot<side>.bc26`. Do this in the same commit
as the accept/reject decision.

### Round-count and score-margin metrics

Win/loss alone discards information here even more than it did in BC22,
because of the explicit point-margin formula (see "The Gauntlet"/RULES.md).
A change that improves our point share without flipping the outcome column
is real, measurable progress. Once `tools/bc26_replay.py` supports
extracting a game's final score breakdown (cheese %, cat-damage %,
living-rat-king %) and round count, use both as supporting evidence for
Step 6.4.2's mechanistic-verification claims and for judging whether a
near-miss represents real progress — the same role BC22's
`tools/compare_gauntlets.py` round-count metric played. This tooling is
not built yet as of this document's writing; build it once the first
several iterations make its absence a real bottleneck, not preemptively.

### Win % vs. a fixed old-bot roster

Once enough iterations accumulate that Gauntlet retirement starts
mattering, adopt the same fixed-roster tracking BC22 settled on late in its
run (every ~10 iterations, run against a **fixed**, never-retired roster of
every-10th accepted snapshot, track win % over time in a checked-in CSV +
chart). Not urgent before there's a roster worth tracking — revisit this
once the project has passed roughly its 10th accepted iteration.
