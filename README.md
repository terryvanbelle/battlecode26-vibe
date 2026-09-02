# battlecode26-vibe

A [Battlecode 2026](https://battlecode.org) ("Uneasy Alliances") contest
entry, built on the official
[`battlecode26-scaffold`](https://github.com/battlecode/battlecode26-scaffold)
(Java, engine/client updated via `./gradlew update`).

Sister project to [`battlecode22-vibe`](https://github.com/terryvanbelle/battlecode22-vibe)
(2022's "Mutation" entry); see that project's `LEARNINGS.md` for the process
lessons this one's `TRAINING_ALGORITHM.md` builds on.

## The game, briefly

Two teams of rats (spawned by up to 5 Rat Kings each) collect cheese, fight
off NPC cats, and must decide whether to stay cooperating against the cats
or **backstab** the other team at any point. See `RULES.md` for the full
digest, or `specs/specs.pdf` for the official spec itself.

## Layout

| Path | What |
|------|------|
| `src/examplefuncsplayer/` | the stock example bot |
| `src/bot/` | our current bot (created at Iteration 0) |
| `src/g_iterNN/` | frozen snapshots of accepted iterations, used as Gauntlet opponents |
| `SCAFFOLD.md` | the upstream scaffold README (gradle tasks, etc.) |
| `SETUP.md` | how this checkout is set up -- **builds/matches run on a GCE VM**, not locally |
| `RULES.md` | working digest of the official spec -- rules, units, scoring |
| `TRAINING_ALGORITHM.md` | the iterative dev-loop this project follows |
| `TRAINING_LOG.md` | the running record of every iteration attempted |
| `specs/specs.pdf` | the official Battlecode 2026 spec (vendored) |
| `tools/bc26_replay.py` | replay (`.bc26`) -> human-readable text transcript ([tools/README.md](tools/README.md)) |
| `tools/gauntlet.sh` | run the Gauntlet (all opponents x maps x sides) on the VM |
| `tools/vm-match.sh` | run one-off headless matches on the VM and pull replays + logs back |
| `tools/snapshot.sh` | freeze `src/bot/` into a new Gauntlet opponent package |
| `tools/compare_gauntlets.py` | diff two Gauntlet runs game-by-game (win/loss flips + round deltas) |

Generated artifacts -- `matches/` (`.bc26` replays), `logs/`, `client/`,
`gauntlet/` -- are git-ignored. `replays/` (hand-picked interesting replays,
one per logged iteration) and `progress/` (charts/CSVs) are checked in.

## Quick start

```bash
# inspect a replay
tools/.venv/bin/python tools/bc26_replay.py matches/some-replay.bc26 --step 25

# run the example bot against itself on a couple of maps (spins up the GCE VM)
tools/vm-match.sh tiny closeup

# run the full Gauntlet for the current bot
tools/gauntlet.sh
```

See `SETUP.md` for the VM details and why the build runs there, and
`TRAINING_ALGORITHM.md` for the iteration loop this bot is developed under.
