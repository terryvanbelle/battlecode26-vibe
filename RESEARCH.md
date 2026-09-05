# RESEARCH.md — cross-year findings from Battlecode post-mortems

Compiled 2026-09-05 from the team post-mortems published at
[battlecode.org/past.html](https://battlecode.org/past.html), covering 2019–2025,
plus external write-ups. Current-year (2026) post-mortems are out of scope and are
not used here. This is a synthesis of what **recurs across years**, not a
summary of any one game. Read it when the iteration loop is stuck for ideas.

**How to use it.** Most entries below are about *process* — how strong teams decide
a change is real — because that is what transfers between years with completely
different rules. The game-specific entries (§4) transfer only where BC26 shares the
mechanic. Each section ends with **→ for us**, tying the finding to this project's
recorded state in `TRAINING_ALGORITHM.md` and `TRAINING_LOG.md`.

---

## 1. The sources

| Year | Team | Placing | Source |
|---|---|---|---|
| 2025 | Just Woke Up | 1st | [PDF](https://battlecode.org/assets/files/postmortem-2025-just-woke-up.pdf) |
| 2025 | confused | 2nd | [PDF](https://battlecode.org/assets/files/postmortem-2025-confused.pdf) |
| 2025 | Om Nom | 3rd | [PDF](https://battlecode.org/assets/files/postmortem-2025-om-nom.pdf) |
| 2025 | SPAARK | HS 1st | [PDF](https://battlecode.org/assets/files/postmortem-2025-spaark.pdf) |
| 2025 | The Kragle | — | [PDF](https://battlecode.org/assets/files/postmortem-2025-the-kragle.pdf) |
| 2024 | cout for clout | HS 1st | [PDF](https://battlecode.org/assets/files/postmortem-2024-cout-for-clout.pdf) |
| 2023 | Gone Fishin' | 2nd | [PDF](https://battlecode.org/assets/files/postmortem-2023-gone-fishin.pdf) |
| 2023 | 4 Musketeers | 3rd | [PDF](https://battlecode.org/assets/files/postmortem-2023-4-musketeers.pdf) |
| 2023 | don't @ me | 7th | [PDF](https://battlecode.org/assets/files/postmortem-2023-dont-at-me.pdf) |
| 2023 | no thoughts head empty | Newbie 2nd | [PDF](https://battlecode.org/assets/files/postmortem-2023-no-thoughts.pdf) |
| 2022 | 5 Musketeers | 7th–8th | [PDF](https://battlecode.org/assets/files/postmortem-2022-5-musketeers.pdf) |
| 2021 | Baby Ducks | 1st | [web](http://web.mit.edu/agrebe/www/battlecode/21/index.html) |
| 2021 | wololo | 7th–8th | [PDF](https://battlecode.org/assets/files/postmortem-2021-wololo.pdf) |
| 2021 | 3 Musketeers | 9th–12th | [PDF](https://battlecode.org/assets/files/postmortem-2021-musketeers.pdf) |
| 2020 | Java Best Waifu | 1st | [PDF](https://battlecode.org/assets/files/postmortem-2020-java-best-waifu.pdf) |
| 2020 | The High Ground | 4th | [PDF](https://battlecode.org/assets/files/postmortem-2020-the-high-ground.pdf) |
| 2020 | confused | HS 2nd | [PDF](https://battlecode.org/assets/files/postmortem-2020-confused.pdf) |
| 2019 | smite | 1st | [PDF](https://battlecode.org/assets/files/postmortem-2019-smite.pdf) |
| 2019 | Oak's Last Disciple | — | [PDF](https://battlecode.org/assets/files/postmortem-2019-oak.pdf) |

A caution learned while building this file: **summarising these PDFs through a
web-fetch tool produced confident fabricated quotes** (invented percentages that
appear nowhere in the document). Everything below was read from the extracted text
of the PDFs themselves. If you extend this file, extract the text and read it.

---

## 2. Evaluation and tooling — the strongest cross-year regularity

This is the finding that recurs in the most years and the one most relevant here.

**Every serious team builds a local parallel match runner, and they copy each
other's.** SPAARK's 2025 acknowledgements trace the lineage explicitly: their
`runmatches.py` came from their own 2024 script, "which was copied from 4
Musketeers' `run_matches.py` script from 2023 (which was copied from Producing
Perfection's `run_matches.py` script from 2022)". A first-year team (no thoughts
head empty, 2023) names the absence of this as their top lesson: *"Tooling for
iterating and testing quickly is very important. Many times we would make a change
and not be able to tell whether it was helpful or harmful without spending a long
time testing. At the final tournament we found out that many top teams had built up
custom systems for quickly running many matches in parallel for A/B testing."*

**A handful of games is not a measurement.** don't @ me (2023): *"Just three matches
might not be enough to highlight the strength and expose the weaknesses of one's
robot."* Their practice was full-set (10-match) scrimmages against live opponents
**paired with** all-map scrimmages against old versions of their own bot.

**Live opponents outrank frozen ones.** SPAARK (2025) states it flatly:
*"Scrimmage analysis is still more important than raw win rate against old bots."*
The old-bot ladder is the regression check; the live ladder is the measurement.

**Not scrimming is how you miss a meta shift.** Oak's Last Disciple (2019) names it
as their single biggest mistake: *"we barely scrimmed after seeding (games took too
long and we were really lazy about it), and we didn't see the meta shift that was
happening right in front of our eyes."*

**Test the counter specifically against the thing it counters.** confused (2020)
records a feature built to beat a specific opponent that was never tested against
that opponent in enough volume before the tournament.

**Overnight volume genuinely pays.** SPAARK: *"running overnight scrims and hoping
for a random improvement is actually very productive"* — their illustrations are of
individual overnight runs that found real gains.

**→ for us.** The ranking above has to be read against a constraint none of these
teams had: **we are running outside an actual tournament, so scrimmages are simply
unavailable to us.** That is a real loss, and worth being precise about why. A live
ladder supplies a diverse opponent set that is *roughly calibrated to your current
ability*, because the whole field moves together toward more sophisticated play over
the season. It is the calibration, as much as the diversity, that makes scrimmage
analysis so valuable in these write-ups.

Our benchmarks partially fill the gap but do not replace it: they are **tournament
finalists, operating well above our current level**, which is exactly why they are
so nearly blind to our regressions — at an ~8/162 base rate we lose most games
anyway, so a change that makes us substantially worse has little room to show. A
lopsided instrument measures direction poorly in both directions.

So the correct conclusion is *not* that old bots should be discounted. SPAARK's
"scrimmage analysis is still more important than raw win rate against old bots" is a
statement about ranking two instruments **both of which they had**. Lacking the
better one, our frozen ladder (`vs_old_bots`) and our derived peers are doing real
and necessary work: they sit near our own level, which is precisely the property
benchmarks lack, and the peer gauntlet is where this project's own policy decisions
have actually been resolved. The honest summary is that we have the calibrated-but-
self-referential instrument and the diverse-but-uncalibrated one, and neither alone
is what a scrimmage ladder would give us — which is a reason to keep triangulating
across all three, and a reason not to read any single one as a verdict.

---

## 3. What to work on, and in what order

**Do the general things first.** The High Ground (2020): *"The first week and a
half-ish of Battlecode should be mostly focused on writing code which won't change
if your strategy changes. For example, navigation, resource-gathering, and
communication. While this advice has been given many times in many post-mortems, it
is worth repeating because it is true."* SPAARK (2025) says the same under "Work on
the right thing at the right time": start with navigation, micro, code organisation,
testing scripts and test maps, because the macro will change repeatedly.

**Basics beaten well outperform sophistication.** Java Best Waifu, the 2020
**winner**: *"the most important factors … Simplicity, Robustness and Structured
Code. In my personal experience, every time I'd try to implement a sophisticated
strategy that requires a lot of coordination it would always flop since everything
that can go wrong does go wrong. Usually the bots that perform the best are those
that perform the basics really well."*

**Robustness over optimality.** don't @ me (2023): *"Make it work, make it right,
make it fast … Don't try to invent Battlecode Stockfish without having the basic
dumb 'run straight at the nearest thing' bot done … optimality is often not so
important as robustness."*

**Prioritise by expected win-rate, and do not fix everything a replay shows you.**
The High Ground: *"it is not a good strategy to go through a replay and note every
single area of improvement, then go fix them all immediately. Rather, use a task
management system … If you are having trouble determining which things are the
highest priority, think about how much the change will increase your win rate."*

**→ for us.** Two of these cut against the grain of how this project has been
spending its iterations. Our loop is replay-trace-driven — exactly the mode The High
Ground warns produces a long undifferentiated fix list — and our own record agrees:
the value in this bot came from three *capability-preserving* changes, while five
mechanism-proven damage increases converted to nothing (`damage-does-not-convert`).
And the coordination warning is a direct hit: our repeated failures to express
team-level priorities (focus fire, rotation sweeps, squeak coordination) are the
"sophisticated strategy requiring a lot of coordination" that the 2020 winner says
always flops.

---

## 4. Mechanics that recur every year

From The Kragle's 2025 "Advice", the most explicit statement of the perennials:

- **Maps.** *"(nearly) always a coordinate-grid of size between 20x20 and 60x60 …
  For fairness, the map is always symmetrical either by rotation or reflection."*
  Storing known map data and **identifying which of the three symmetries** holds, so
  that observed information can be extrapolated to the unseen half, is called
  *"Battlecode 101"* — essentially every top team does it.
- **Bytecode.** Limits historically ~7,500–15,000 per robot. *"Even performing
  simple tasks such as breadth-first-search on a 20x20 tile map will use a robot's
  entire bytecode budget."* Knowing the limits tells you which algorithms are
  admissible at all (*"Can I implement A*? Spoiler: probably not"*).
- **Pathfinding.** Binary passability (walls/no walls) → most top teams use
  **bugnav**; variable passability (rubble-like) → greedy movement. SPAARK dropped
  BFS for bug2 in 2025 purely on bytecode cost.
- **Comms.** A shared structure recording towers/HQs/resources, plus symmetry bits,
  is standard. SPAARK stored explored tiles as *"an array of 60 longs … each long
  can be used as 60 bits, so we effectively have a 2d array but more bytecode
  efficient"*.

**→ for us.** Three of these four are pointed at BC26 gaps.

1. **Symmetry is unexploited here.** `TRAINING_LOG.md` already records that BC26's
   `RobotController` exposes width/height but not the symmetry type — but the
   cross-year practice is to *infer* it from observations (three candidate
   symmetries, eliminate as terrain is seen), not to be told. We concluded the enemy
   King is unreachable partly because we never know where it is; symmetry inference
   is the standard way teams answer that, and it has never been tried here.
2. **Bytecode is emphatically not our constraint** (we use ~4% of 17,500), which by
   these teams' standards is enormous unused headroom — the perennial complaint is
   the opposite problem.
3. **Comms is our known dead end** for a different reason: `writeSharedArray`
   requires `isRatKingType`, so rats have only `squeak()`. Worth noting that the
   cross-year pattern assumes a real shared array; BC26 denies it to rats, which is
   a genuine structural difference, not something we failed to build.

---

## 5. Endgame discipline

**Do not make last-minute changes.** SPAARK (2025): *"In both 2023 and 2024 we threw
by submitting a last minute change that didn't work. If you really want to make a
last minute change, use a testing script and make sure it isn't losing."*

**And do not freeze either.** The High Ground (2020) reports both failure modes from
consecutive years — 2019 *"we were far too cautious … this led to us falling from
the top team to a deserved 4th place finish"*, 2020 *"we tried to make too many
changes, and didn't make any individual one of them super well."* Their conclusion:
*"choose one or two impactful improvements to your bot, and make them very well."*

**Ladder rank is not the objective.** don't @ me: *"Code > Elo … Generalizing
solutions in your code rather than coding for specific cases that appear on the
ladder often helped us find wins in tournaments where opponents might have
hard-coded heuristics that beat us on the ladder."* 3 Musketeers (2021) hit the
matching failure: they note the gap *"between the maps used in scrims and the maps
used in the final tournament, especially considering different maps can drastically
change what strategies are optimal."*

**→ for us.** The map-distribution warning is the one to take seriously. Our whole
evaluation runs on 27 fixed maps, and our accept/reject decisions turn on
1–4-game margins within them. This project has already found one instance of the
exact failure 3 Musketeers describe — the recorded finding that our 8 benchmark wins
fall on **three maps only**, with no structural property separating them from maps
we never win. A change tuned on that set is fitted to the set.

---

## 6. Copying, and rewriting

**Studying other teams is normal and expected.** The Kragle: *"Before the
competition starts, read Postmortems, look at code from other teams."* don't @ me:
*"Understanding another top team's strategy and applying it yourself is never a bad
idea … But remember: you have to actively attempt to improve upon the 'inspired'
algorithm, or else it'll never be as good or better than the team that you took it
from."* SPAARK found both of their carrying strategies this way, with the caveat
that a copied strategy can be invalidated by a balance patch.

**Rewrite rather than retrofit when the strategy changes.** Java Best Waifu (2020
winner): *"if the code undergoes drastic changes (for instance because you change
the bot's main strategy) I would suggest to do a bot from scratch. It is usually
faster than expected and it is way better on the long run."*

**→ for us.** The rewrite advice is the most actionable unexplored idea in this
document. Our log's own conclusion is that *"further progress needs an idea from
outside the space this session searched"*, and we have accumulated 26 snapshots of
incremental edits to one `RobotPlayer.java`. The 2020 winner's claim is that when
you are at that point, a from-scratch bot is faster than it looks. That is a
high-risk, high-variance move of exactly the kind this project is chartered to try.

---

## 7. The short list, when stuck

1. **Triangulate across instruments; none is a verdict alone.** Scrimmages — the
   diverse, ability-calibrated opponent set these teams rely on — are unavailable
   outside a tournament. Benchmarks are diverse but far above our level (and so
   nearly blind to regressions); peers and `vs_old_bots` are near our level but
   descend from us. Each covers the other's blind spot.
2. **Try symmetry inference** to locate the enemy King. It is "Battlecode 101"
   everywhere else and absent here.
3. **Prefer capability-preserving, zero-marginal-cost changes** — this project's own
   three wins and the 2020 winner's "simplicity and robustness" agree.
4. **Distrust anything requiring cross-unit coordination.** Every year says it
   flops; our four failed attempts at it agree.
5. **Beware fitting the 27-map set**; check whether a gain is map-specific.
6. **Consider a from-scratch rewrite** rather than a 27th incremental edit.
