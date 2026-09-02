# `replay-dump.sh` / `replaydump/ReplayDump.java` — .bc26 replay -> text

Turns a `.bc26` replay (gzipped FlatBuffers, root type `battlecode.schema.GameWrapper`)
into a text transcript: per-25-round team aggregates (living Rat King count,
global cheese, living Baby Rat count, cumulative cat damage) plus a filtered
event log of everything relevant to root-causing a loss -- deaths (with
cause), cat pounces/scratches (with location), damage, and spawns. Cheese/
dirt/trap/indicator/communication events are intentionally omitted for now;
add them if a future iteration needs them (see "Extending it" below).

## Why Java, not Python (unlike BC22's `bc22_replay.py`)

BC22's replay tool was Python because it needed to run on the laptop, which
had no local JVM. This project's tooling already assumes everything (build,
run, Gauntlet) happens on the GCE VM (`battlecode-dev`) -- see `SETUP.md` --
so there's no laptop-side constraint pushing toward Python here.

Building a *Python* FlatBuffers reader would have meant finding or
regenerating a `.fbs` schema and Python bindings for BC26's replay format.
No `.fbs` file could be found vendored anywhere obvious (the old, generic
`battlecode/battlecode-schema` repo is years stale and doesn't match this
game). But the upstream engine repo
(`https://github.com/battlecode/battlecode26`,
`engine/src/main/battlecode/schema/`) has the **already-generated Java
FlatBuffers classes checked into source control**, and those exact classes
are what's bundled in the `battlecode26-java` engine jar our build already
depends on. Writing a small Java program against those classes, compiled
and run directly on the VM (which already has JDK 21), was far less work
than reverse-engineering a schema for Python -- so that's what this is.

## A real wrinkle: structs in unions

`Turn.actions(Table obj, int j)` -- the generated accessor for a turn's list
of actions -- only type-checks for `Table`-derived union members. Several
`Action` union members that matter most for this project's diagnostics
(`DieAction`, `CatPounce`, `CatScratch`, `DamageAction`, `SpawnAction`, ...)
are `Struct`-derived instead, and Java's type system genuinely won't let you
pass a `Struct` where a `Table` is expected -- there's no legal way to call
the generated accessor for these. `ReplayDump.java` works around this by
being declared `package com.google.flatbuffers;` (the same package as the
FlatBuffers runtime's `Table` class), which grants same-package access to
`Table`'s `protected` internals (`__offset`/`__vector`/`__vector_len`/
`__indirect`/`getByteBuffer()`) needed to manually walk the union vector and
`__init()` (public on every generated type) each struct at the right byte
offset -- replicating exactly what `Table.__union()`/`UnionVector.get()` do
internally, just without their `Table`-only type constraint. See the
`dumpActions()` method for the full replication.

## A second wrinkle: the "alive rat kings" field is a lie

`Round.teamAliveRatKings(k)` is documented "The total number of alive rat
kings per team" and *is* a real per-team array in the schema, but the
engine (`GameWorld.java`, `computeGameFooter`-adjacent round-end code)
actually writes `numRatKings + 10*cheese` into it -- a comment right above
the call says `// combine total cheese into the rat kings stat`. Decoded
here as `kings = combined % 10, cheese = combined / 10`. This is a real,
confirmed-from-source quirk of the engine's own replay format, not a bug in
this tool -- worth remembering if a future engine version changes it.

## Usage

```bash
tools/replay-dump.sh matches/some-replay.bc26                  # full transcript
tools/replay-dump.sh matches/some-replay.bc26 --from 90 --to 115   # zoom in
```

The script pushes `replaydump/ReplayDump.java` and the replay file to
`battlecode-dev`, compiles (if needed), and runs it there over ssh -- same
pattern as `vm-match.sh`/`gauntlet.sh`. Output streams back to stdout.

## Extending it

No test suite yet (unlike BC22's `test_bc22_replay.py`) and no `--metrics`
CSV mode -- add both once an iteration's diagnosis actually needs them
(TRAINING_ALGORITHM.md's own stated policy for this tool: build capability
when its absence is a real bottleneck, not preemptively). Other event/action
types the schema supports but this tool currently ignores (cheese pickup/
transfer/spawn, dirt, traps, ratnap/throw, squeak, indicators) are all
straightforward to add to `dumpActions()`'s switch statement following the
same pattern as the cases already there -- Struct-typed ones need the
manual `__init()` treatment above, Table-typed ones (if any turn up) can
use the generated accessor directly.
