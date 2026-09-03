# External benchmark suite

Independent validation against **real MIT Battlecode 2026 tournament
bots**, vendored from public GitHub repositories. This exists because
every previously-available metric measured the bot against *itself*:

- the peer archetypes deliberately share `src/bot/`'s code, so once
  correctly synced they sit at ~50% by construction;
- `vs_old_bots` compares against frozen snapshots of this same lineage;

so both answer "did this change help *relative to my own past*", and
neither can detect a weakness the whole lineage shares. The benchmark
answers the question that actually matters: **how does this bot do
against strong code written by someone else?**

## The rule: never read benchmark bot source

**Their source code must not be read.** Permitted: cloning, compiling,
running matches, and analysing `.bc26` **replays**. Forbidden: `cat`,
`head`, `tail`, `grep`, `less`, editor/`Read` access to their `.java`
files, or any command that displays their logic.

This is a user instruction, and it is also methodologically right:
reading opponent code invites tuning against *those specific bots*
rather than getting genuinely stronger, which would destroy the
independence that makes the benchmark worth having. Replays show
*what* they do (observable in any real match); source shows *how they
decide*, which a real opponent would never expose.

Package renaming during vendoring was done mechanically
(`sed` on `package`/`import` lines) without displaying file contents.

## The suite

| package | origin | standing |
|---|---|---|
| `bench_lecture`  | `battlecode/battlecode26-lectureplayer` | official example bot from the lectures |
| `bench_finalist` | `AlexT101/battlecode26` (`finalsbot`)   | Top 12 Finalist, Best Postmortem Award |
| `bench_spaark`   | `erikji/battlecode26` (`SPAARK`)        | MIT Battlecode 2026 HS 4th |
| `bench_stroke`   | `uravt/Battlecode26` (`Version41`)      | "Generalized Stroke's Theorem", 2nd place |

Chosen by directory name only (`finalsbot`, `SPAARK`, highest
`VersionNN`) -- no inspection of contents.

## Running it

    OPPONENTS="bench_lecture bench_finalist bench_spaark bench_stroke" \
        tools/gauntlet.sh

Losing replays land in `gauntlet/<run>/losses/` and are the *only*
sanctioned way to study these opponents.
