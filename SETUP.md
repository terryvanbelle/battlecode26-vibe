# Battlecode 2026 — dev environment

Contest: **Battlecode 2026 ("Uneasy Alliances")**. The season itself ended
January 31, 2026; this project uses the released engine/scaffold as a
standing practice environment, same as the sister BC22 project did.

- Docs / getting started: https://github.com/battlecode/battlecode26-scaffold
  (`SCAFFOLD.md` here), quick start at https://play.battlecode.org/bc26/quick_start
- Formal spec: vendored at `specs/specs.pdf` (pulled from
  https://github.com/battlecode/battlecode26/blob/master/specs/specs.pdf)
- Episode API: https://api.battlecode.org/api/episode/e/bc26/?format=json
- Language: **Java 21** (required — `sourceCompatibility = 21`, enforced by
  `build.gradle`). This is a hard requirement change from BC22's Java 8.
- Engine/client versions are tracked in `engine_version.txt` /
  `client_version.txt`; run `./gradlew update` periodically (the engine was
  still actively receiving balance patches as of this project's start).

## Layout

This repo *is* the `battlecode26-scaffold` (cloned from `upstream`, pushed
to `origin` = this repo, matching the BC22 project's convention). Key paths:

- `src/examplefuncsplayer/RobotPlayer.java` — the example bot. Our bot lives
  in `src/bot/` (see `TRAINING_ALGORITHM.md` Iteration 0).
- `gradle.properties` — default teams / maps / flags for `./gradlew run`.
- `build.gradle` — `run`, `runLocal`, `build`, `listMaps`, `listPlayers`,
  `update`, `verify`, `zipForSubmit` tasks.
- `matches/*.bc26` — replay files.
- `tools/bc26_replay.py` — converts a `.bc26` replay into a human-readable
  text transcript. See `tools/README.md`. Runs locally (Python +
  `flatbuffers`, venv at `tools/.venv`).

## Why the build runs on GCP

Per the standing "use the GCloud account for compute" instruction, and
because this same discipline already worked well for the BC22 project,
builds/matches run on a GCE VM rather than wherever this Claude Code session
happens to be executing:

- VM `battlecode-dev`, `e2-standard-8`, zone `us-west1-b`, project
  `tvanbelle-vibecode` — **shared with the BC22 project**, which has its own
  checkout at `~/battlecode22-scaffold` on the same VM. This project's
  checkout lives alongside it at `~/battlecode26-vibe`.
- JDK: Temurin **21.0.12.1** (linux x64) unpacked at `~/jdk21` on the VM
  (BC22's `~/jdk8` is untouched, still needed for that project).
- Engine jar resolves via Gradle's dependency cache to
  `~/.gradle/caches/modules-2/files-2.1/org.battlecode/battlecode26-java/<ver>/.../battlecode26-java-<ver>.jar`
  — `tools/gauntlet.sh` finds this directly to run bare `java` per game
  (bypassing the Gradle daemon) for fast parallel Gauntlet runs, same
  optimization as the BC22 project used.
- Stop the VM when done with a work session to avoid idle charges:
  ```
  gcloud compute instances stop battlecode-dev --zone=us-west1-b --project=tvanbelle-vibecode
  ```
  (Only stop it if the BC22 project isn't also mid-session on it.)

### Run matches

```
tools/vm-match.sh tiny closeup             # example bot vs itself, 2 maps
TEAM_A=bot TEAM_B=examplefuncsplayer tools/vm-match.sh knifefight
```

The script starts the VM if stopped, syncs `src/`, runs each match headless,
and copies `*.bc26` + logs back.

### Run the Gauntlet

```
tools/gauntlet.sh
BOT=bot OPPONENTS="examplefuncsplayer g_iter1" MAPSET=full tools/gauntlet.sh
```

See `TRAINING_ALGORITHM.md` for what the Gauntlet is and how its results
drive accept/reject decisions.

To view replays with the graphical client you'd need a local JVM (the
client itself is TypeScript/web-based, downloaded to `client/` by
`./gradlew build`, but launching it may still expect a local engine) — not
set up on this thin driver machine. Replays can be watched at
https://play.battlecode.org, or inspected as text via `tools/bc26_replay.py`.

## Verification runs (this project's Iteration 0 environment check)

`examplefuncsplayer` vs itself, headless, engine 1.2.6, map `DefaultSmall`:
ran the full game to round 1310, ending by coin flip (both Rat Kings alive,
equal points — expected, since neither side does anything but wander).
Valid `.bc26` replay produced → environment works end to end (build, run,
replay capture, and the bare-`java` fast-path used by `tools/gauntlet.sh`
all independently verified).
