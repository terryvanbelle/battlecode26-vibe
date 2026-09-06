#!/usr/bin/env python3
"""Rebuild the synthetic peer archetypes from the newest accepted snapshot.

The peers (pure_cooperator, immediate_defector) exist to answer "is this change
good against a bot that plays our own game with a different backstab policy?"
That only works if they share our economy and movement code and differ ONLY in
policy. Three separate times they have silently fallen behind instead, inflating
every win rate measured in between.

Re-syncing by hand is what kept failing, because it means re-applying a handful
of small edits across a 1300-line file and noticing which absences are policy
and which are drift. So the edits live here as data, and the rebuild is
mechanical.

IMPORTANT: this rebuilds from src/g_iter<N>/, the newest ACCEPTED snapshot --
never from src/bot/, which routinely holds an experiment under test. Building a
measuring instrument out of unvalidated code would make every subsequent
comparison meaningless.

    tools/resync_archetypes.py            # rebuild both, report a diff summary
    tools/resync_archetypes.py --check    # exit 1 if either is out of date
"""
import pathlib
import re
import sys

REPO = pathlib.Path(__file__).resolve().parent.parent
SRC = REPO / "src"


def newest_snapshot() -> pathlib.Path:
    snaps = [(int(p.name[len("g_iter"):]), p) for p in SRC.glob("g_iter*")
             if p.name[len("g_iter"):].isdigit()]
    if not snaps:
        raise SystemExit("no g_iterN snapshots found")
    return max(snaps)[0], max(snaps)[1] / "RobotPlayer.java"


# Each edit is (description, exact_old, new). Every one must match exactly once,
# or the rebuild aborts -- a silently-skipped policy edit is precisely the
# failure this script exists to prevent.
PURE_COOPERATOR = [
    # A trap's OWNER initiates the backstab: GameWorld.triggerTrap calls
    # backstab(triggeringRobot.getTeam().opponent()). So a bot that lays rat
    # traps is not a pure cooperator, and this absence is POLICY, not drift.
    # Cat traps are exempt -- triggerTrap skips the backstab for CAT_TRAP -- so
    # a pure cooperator SHOULD lay those, and their absence WAS drift
    # (Iteration 128 never reached the archetype).
    # Anchor updated for g_iter31 (Iteration 211), which replaced the constant
    # `true` with `!rc.isCooperation()` -- the ring now arms on being attacked.
    # A pure cooperator still lays NO rat traps at all, so the replacement is
    # still the constant false; only the text being matched changed.
    ("no rat traps (owner would initiate the backstab)",
     "final boolean KING_TRAPS_ENABLED = !rc.isCooperation();",
     "final boolean KING_TRAPS_ENABLED = false;  // ARCHETYPE: trap owner backstabs"),
    # Never defects out of desperation.
    ("never desperate (King side)",
     "boolean desperate = economyStruggling && rc.getGlobalCheese() < RESERVE;",
     "boolean desperate = false;  // ARCHETYPE: pure cooperator never defects"),
]

IMMEDIATE_DEFECTOR = [
    # Always treats the game as post-backstab, from round 1.
    ("always desperate (rat side)",
     "boolean desperate = rc.readSharedArray(2) == 1;",
     "boolean desperate = true;  // ARCHETYPE: immediate defector"),
    # Anchor updated for g_iter30 (Iteration 210), which REVOKED the `desperate`
    # licence to bite first, so the gate is now `!rc.isCooperation()` alone. The
    # archetype still forces the combat block permanently on.
    ("combat block always active",
     "if (!rc.isCooperation()) {",
     "if (true) {  // ARCHETYPE: immediate defector always fights"),
    # The raid is gated off in the main bot (Iteration 138) but is this
    # archetype's defining behaviour, so it is forced back on.
    ("raid always enabled",
     "if (DESPERATE_RAID && gx != 0 && gy != 0) {",
     "if (gx != 0 && gy != 0) {  // ARCHETYPE: raid is the whole point"),
    # Keeps its rats near its own King, so it reads as a defensive
    # early-aggressor rather than drifting into a second explorer.
    ("leash rats to the King",
     "        if (collectCheese(rc)) return;",
     "        // ARCHETYPE (immediate_defector): stay within LEASH_RADIUS_SQUARED\n"
     "        // of our own King rather than ranging like the main bot.\n"
     "        if (kingLoc != null\n"
     "                && rc.getLocation().distanceSquaredTo(kingLoc) > LEASH_RADIUS_SQUARED) {\n"
     "            if (moveToward(rc, kingLoc, true)) return;\n"
     "        }\n"
     "\n"
     "        if (collectCheese(rc)) return;"),
    ("leash constant",
     "    static Random rng;",
     "    /** ARCHETYPE DIFFERENCE (immediate_defector): stays within this\n"
     "     *  squared radius of its own Rat King. */\n"
     "    static final int LEASH_RADIUS_SQUARED = 100;\n"
     "\n"
     "    static Random rng;"),
]

# The third archetype TRAINING_ALGORITHM.md specifies and that was never built:
# "cooperates until cats are mostly dead or its own rat-king count is safely ahead,
# then defects. The hardest and most realistic opponent archetype."
#
# It exists because Iteration 222 found pure_cooperator had become a MIRROR of us:
# its two edits are both no-ops in the games it plays, so half the peer gauntlet was
# measuring map-and-side luck between two copies of one bot. "Never initiates a
# backstab" stopped being a policy difference the moment Iteration 210 made that
# true of us too. This archetype restores a real difference.
#
# Trigger is game state, not a clock: a rat that sees a cat below 30% health knows
# the cat pool is nearly spent, i.e. the game is about to end on points -- exactly
# the moment a rational opportunist cashes in its cooperation. Latched per rat.
OPPORTUNISTIC = [
    ("opportunistic defect helper",
     "    static boolean engage(RobotController rc, MapLocation target) throws GameActionException {",
     """    // ARCHETYPE (opportunistic): cooperate while the cat pool is worth farming,
    // then cash the cooperation in. CAT health is 4000; 2400 is 60%, so the trigger
    // fires while there is still game left to exploit rather than at the buzzer.
    //
    // A pure game-state trigger proved unreliable as an INSTRUMENT: at a 1200
    // threshold cooperation never broke at all, because BABY_RAT vision is a
    // 90-degree cone and by the time a cat is that low it dies almost at once. The
    // round fallback guarantees the archetype actually poses its threat, which is
    // what an instrument has to do.
    static boolean oppDefected = false;

    static boolean oppDefect(RobotController rc, RobotInfo[] nearby) {
        if (oppDefected) return true;
        if (rc.getRoundNum() >= 500) {
            oppDefected = true;
            return true;
        }
        for (RobotInfo info : nearby) {
            if (info.getType() == UnitType.CAT && info.getHealth() <= 2400) {
                oppDefected = true;
                return true;
            }
        }
        return false;
    }

    static boolean engage(RobotController rc, MapLocation target) throws GameActionException {"""),
    ("rat combat block opens on the opportunistic trigger",
     "        if (!rc.isCooperation()) {",
     "        if (!rc.isCooperation() || oppDefect(rc, nearby)) {  // ARCHETYPE: opportunist"),
    ("attackNearestHostile honours the latch",
     "                    || (!rc.isCooperation() && info.getTeam() != rc.getTeam());",
     "                    || ((!rc.isCooperation() || oppDefected) && info.getTeam() != rc.getTeam());"),
]


# Iteration 227: an archetype that differs from ROUND 1 by CROSSING THE MAP.
#
# Iteration 226 established that an archetype resolves only to the extent it differs
# early and structurally: pure_cooperator (never differs) is 27/27 side-split and
# yields nothing; opportunistic (flips at r76-463) is 21/27 split; immediate_defector
# (differs from round 1) is only 3/27 split and carries the instrument.
#
# But immediate_defector enables the raid and then LEASHES its rats to its own King,
# so it plays as a defensive early-aggressor. Nothing in the pool commits its opening
# to reaching our King -- which is exactly what the benchmark bots do and what
# produces our early wipes. This is that missing threat, at our own level.
RUSHER = [
    ("King always broadcasts the enemy-King guess",
     "boolean desperate = economyStruggling && rc.getGlobalCheese() < RESERVE;",
     "boolean desperate = true;  // ARCHETYPE: rusher always hunts"),
    ("rats always treat the game as post-backstab",
     "boolean desperate = rc.readSharedArray(2) == 1;",
     "boolean desperate = true;  // ARCHETYPE: rusher"),
    ("combat block always active",
     "if (!rc.isCooperation()) {",
     "if (true) {  // ARCHETYPE: rusher always fights"),
    # The defining difference from immediate_defector: the raid is on AND there is
    # no leash, so rats commit to crossing the map instead of turtling at home.
    ("raid always enabled, and unleashed",
     "if (DESPERATE_RAID && gx != 0 && gy != 0) {",
     "if (gx != 0 && gy != 0) {  // ARCHETYPE: cross the map, do not turtle"),
]


ARCHETYPES = {
    "pure_cooperator": PURE_COOPERATOR,
    "immediate_defector": IMMEDIATE_DEFECTOR,
    "opportunistic": OPPORTUNISTIC,
    "rusher": RUSHER,
}


def build(name: str, edits, source: str) -> str:
    text = source.replace("package g_iter", "package " + name + ";  // from g_iter", 1)
    # Undo the clumsy marker above and write a clean package line.
    text = re.sub(r"^package .*$", f"package {name};", text, count=1, flags=re.M)
    for desc, old, new in edits:
        n = text.count(old)
        if n != 1:
            raise SystemExit(
                f"{name}: edit {desc!r} matched {n} times, expected exactly 1.\n"
                f"  The snapshot changed shape. Fix the anchor here rather than\n"
                f"  letting the archetype quietly skip a policy edit.")
        text = text.replace(old, new, 1)
    return text


def main(argv):
    check_only = "--check" in argv
    num, snap_path = newest_snapshot()
    source = snap_path.read_text()
    print(f"rebuilding archetypes from g_iter{num}")

    stale = []
    for name, edits in ARCHETYPES.items():
        out = SRC / name / "RobotPlayer.java"
        want = build(name, edits, source)
        have = out.read_text() if out.exists() else ""
        if have == want:
            print(f"  {name}: up to date")
            continue
        stale.append(name)
        old_n, new_n = len(have.splitlines()), len(want.splitlines())
        print(f"  {name}: OUT OF DATE ({old_n} lines -> {new_n})")
        if not check_only:
            out.parent.mkdir(parents=True, exist_ok=True)
            out.write_text(want)
            print(f"    rewritten from g_iter{num} with {len(edits)} policy edits")

    if check_only and stale:
        print(f"\nstale: {', '.join(stale)} -- run tools/resync_archetypes.py")
        return 1
    if not stale:
        print("\nall archetypes match the newest snapshot")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
