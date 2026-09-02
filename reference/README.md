# reference/

Spot-checked source files vendored directly from the upstream
[battlecode/battlecode26](https://github.com/battlecode/battlecode26) dev
repo (the *engine* repo — this is not a dependency of our bot, just a
ground-truth reference), pulled when the PDF spec was ambiguous or
inconsistent and worth resolving definitively rather than guessing. Same
spirit as the BC22 project's practice of reading opponent/engine source
directly (`javap` decompilation there, since BC22's engine wasn't public
source; BC26's engine is public, so a straight `git show`/checkout suffices).

- `engine-src/UnitType.java` — canonical per-type stats (health, size,
  vision cone radius²/angle, action/movement cooldown, bytecode limit) for
  `BABY_RAT`/`RAT_KING`/`CAT`. Pulled 2026-09-01 at engine commit matching
  release 1.2.6. This resolved a real discrepancy: the spec PDF's "Cats"
  section states two different cat vision figures (`√17` in one place,
  `√30` in another, for what reads like the same cone) — the engine source
  is unambiguous: `CAT.visionConeRadiusSquared = 17`, angle 180°. Treat
  `√17` as correct; `RULES.md` has been updated accordingly.

Add more files here as future ambiguities come up — don't vendor the whole
engine, just what's actually been checked.
