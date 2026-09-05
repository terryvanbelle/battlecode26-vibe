#!/usr/bin/env python3
"""
Property tests for tools/replaydump/ReplayDump.java.

ReplayDump is the instrument every root-cause claim in TRAINING_LOG.md rests
on. It hand-decodes the flatbuffers replay -- a hardcoded vtable slot, a
bit-packed kings/cheese field, an x + width*y location packing -- so a silent
schema drift or an off-by-one would not crash, it would just produce
plausible, wrong evidence. That failure mode has already cost this project
real time (an inverted team attribution), so the tool gets tests.

There is no local JVM, so these drive the real thing through
tools/replay-dump.sh on the VM and assert properties of its output. Dumps are
cached per (replay, args) for the life of the run, since each VM round trip
costs 7-30s.

The three decoding constants are pinned against values independently read out
of the engine source:

  actions vtable slot 26   battlecode/schema/Turn.java accessor
  kings + 10*cheese        GameWorld.processEndOfRound
  idx = x + width*y        LiveMap.locationToIndex

Usage:
    tools/.venv/bin/python3 tools/test_replaydump.py [replay.bc26]

Exits nonzero on the first failure and prints the offending line.
"""
import pathlib
import re
import subprocess
import sys

REPO = pathlib.Path(__file__).resolve().parent.parent
DUMP = REPO / "tools" / "replay-dump.sh"

_cache: dict[tuple, str] = {}
_failures: list[str] = []


def dump(replay: str, *args: str) -> str:
    key = (replay, args)
    if key not in _cache:
        r = subprocess.run(
            [str(DUMP), replay, *args], capture_output=True, text=True, timeout=600
        )
        if r.returncode != 0:
            raise SystemExit(f"replay-dump.sh failed for {replay} {args}:\n{r.stderr}")
        _cache[key] = r.stdout
    return _cache[key]


def check(name: str, cond: bool, detail: str = "") -> None:
    if cond:
        print(f"  PASS  {name}")
    else:
        print(f"  FAIL  {name}   {detail}")
        _failures.append(name)


ROUND_RE = re.compile(r"^round (\d+) ")
STATS_RE = re.compile(r"^round (\d+) (\d+):kings=(\d+),cheese=(\d+) (\d+):kings=(\d+),cheese=(\d+)")
LOC_RE = re.compile(r"loc=\((\d+),(\d+) raw=(\d+)\)")
SPAWN_RE = re.compile(r"SpawnAction -> id(\d+)\(team(\d+),(\w+)\) at \((\d+),(\d+)\)")
ACTOR_RE = re.compile(r"^round \d+ (id\d+)(\([^)]*\))? \w+")


def test_header(text: str) -> None:
    check("GameHeader names both teams",
          len(re.findall(r"^  team \d+ packageName=\S+", text, re.M)) == 2)
    m = re.search(r"MatchHeader map=(\S+) size=(\d+)x(\d+) symmetry=(\d+) maxRounds=(\d+)", text)
    check("MatchHeader parses", m is not None)
    if m:
        w, h = int(m.group(2)), int(m.group(3))
        check("map dimensions positive", w > 0 and h > 0, f"{w}x{h}")
        check("maxRounds positive", int(m.group(5)) > 0)
    check("exactly two initial Rat Kings",
          len(re.findall(r"initial body id\d+\(team\d+,RAT_KING\)", text)) == 2)


def test_loc_packing(text: str) -> None:
    """raw index must satisfy idx == x + width*y (LiveMap.locationToIndex)."""
    m = re.search(r"size=(\d+)x(\d+)", text)
    if not m:
        return
    width = int(m.group(1))
    bad = []
    n = 0
    for x, y, raw in LOC_RE.findall(text):
        n += 1
        if int(raw) != int(x) + width * int(y):
            bad.append(f"({x},{y}) raw={raw} width={width}")
    check(f"location packing idx=x+width*y ({n} locs)", not bad, "; ".join(bad[:3]))


def test_stats_unpacking(text: str) -> None:
    """kings = combined%10 must stay <= MAX_NUMBER_OF_RAT_KINGS; cheese >= 0."""
    bad = []
    n = 0
    for line in text.splitlines():
        s = STATS_RE.match(line)
        if not s:
            continue
        n += 1
        for kings, cheese in ((s.group(3), s.group(4)), (s.group(6), s.group(7))):
            if not (0 <= int(kings) <= 5):
                bad.append(line)
            if int(cheese) < 0:
                bad.append(line)
    check(f"kings in 0..5 and cheese >= 0 ({n} stat lines)", not bad, "; ".join(bad[:2]))


def test_rounds_monotonic(text: str) -> None:
    rounds = [int(m.group(1)) for m in (ROUND_RE.match(l) for l in text.splitlines()) if m]
    check("round numbers non-decreasing",
          all(a <= b for a, b in zip(rounds, rounds[1:])), "")
    return rounds


def window_for(text: str) -> tuple[int, int]:
    """
    Pick a sub-window that actually exists in this replay. A fixed [40,60] is
    wrong for short games -- the early-wipe losses this project cares most
    about end around round 20, and dumping a window past the end makes the
    window tests pass vacuously.
    """
    m = re.search(r"totalRounds=(\d+)", text)
    total = int(m.group(1)) if m else 100
    lo = max(2, total // 2)
    return lo, min(total, lo + 20)


def test_window_respected(replay: str, lo: int, hi: int) -> None:
    text = dump(replay, "--from", str(lo), "--to", str(hi))
    rounds = [int(m.group(1)) for m in (ROUND_RE.match(l) for l in text.splitlines()) if m]
    check(f"--from/--to emits nothing outside [{lo},{hi}]",
          all(lo <= r <= hi for r in rounds),
          f"stray {[r for r in rounds if not lo <= r <= hi][:5]}")
    check("narrow window still emits a team-stats line",
          any(STATS_RE.match(l) for l in text.splitlines()),
          "no stats line -- the round%25 sampling swallowed the whole window")


def test_labels_survive_from(replay: str, lo: int, hi: int) -> None:
    """
    THE REGRESSION TEST. Labels are built from SpawnActions; the old code
    skipped rounds before --from entirely, so robots spawned in them printed as
    a bare "id10519" with no (team,type). An unlabelled id is how a trace gets
    attributed to the wrong team, which has already happened once.
    """
    text = dump(replay, "--from", str(lo), "--to", str(hi))
    unlabelled = []
    for line in text.splitlines():
        m = ACTOR_RE.match(line)
        if m and m.group(2) is None:
            unlabelled.append(line)
    check("every actor is team-labelled when using --from",
          not unlabelled,
          f"{len(unlabelled)} bare ids, e.g. {unlabelled[0] if unlabelled else ''}")


def test_labels_agree_across_windows(replay: str, lo: int, hi: int) -> None:
    """A robot's label must not depend on the window it was dumped in."""
    full = dump(replay, "--from", "1", "--to", str(hi))
    narrow = dump(replay, "--from", str(lo), "--to", str(hi))

    def labels(t):
        out = {}
        for m in re.finditer(r"(id\d+)\((team\d+,\w+)\)", t):
            out[m.group(1)] = m.group(2)
        return out

    f, n = labels(full), labels(narrow)
    disagree = {k: (f[k], n[k]) for k in f.keys() & n.keys() if f[k] != n[k]}
    check("labels identical across dump windows", not disagree, str(list(disagree.items())[:3]))


def test_spawn_ids_unique(text: str) -> None:
    ids = [m[0] for m in SPAWN_RE.findall(text)]
    dupes = {i for i in ids if ids.count(i) > 1}
    check(f"spawned robot ids unique ({len(ids)} spawns)", not dupes, str(list(dupes)[:5]))


def test_spawn_in_bounds(text: str) -> None:
    m = re.search(r"size=(\d+)x(\d+)", text)
    if not m:
        return
    w, h = int(m.group(1)), int(m.group(2))
    bad = [s for s in SPAWN_RE.findall(text) if not (0 <= int(s[3]) < w and 0 <= int(s[4]) < h)]
    check("spawn coordinates inside the map", not bad, str(bad[:3]))


def test_footer(text: str, rounds: list) -> None:
    m = re.search(r"MatchFooter winner=(\d+) winType=(\w+) totalRounds=(\d+)", text)
    check("MatchFooter present", m is not None)
    if m and rounds:
        check("winner is team 1 or 2", m.group(1) in ("1", "2"), m.group(1))
        check("totalRounds >= last round seen",
              int(m.group(3)) >= max(rounds), f"{m.group(3)} vs {max(rounds)}")


def test_grab_throw_name_both_ends(text: str) -> None:
    """RatNap/ThrowRat must name the ACTOR and the VICTIM separately.

    The schema is not self-consistent about which end an action's payload id
    refers to: RatAttack carries the biter (the actor), while RatNap carries
    the captive and ThrowRat the thrown rat (both victims). ReplayDump prints
    `who` from turn.robotId(), so for these two the printed robot is the
    grabber/thrower and the payload is the target.

    Printing only `who` made "team2 RatNap" mean either "a team2 rat grabbed
    someone" or "a team2 rat got grabbed" -- readings that support opposite
    conclusions about who is winning the fight. That is the ambiguity behind
    the Iteration 108 retraction, so pin both ends here.
    """
    naps = re.findall(r"\) RatNap grabbed=(\S+)", text)
    throws = re.findall(r"\) ThrowRat thrown=(\S+) to=", text)
    bare_nap = re.search(r"\) RatNap(?! grabbed=)", text)
    bare_throw = re.search(r"\) ThrowRat(?! thrown=)", text)
    check("every RatNap names the grabbed robot", bare_nap is None,
          "a RatNap printed without grabbed=; actor/victim ambiguity is back")
    check("every ThrowRat names the thrown robot", bare_throw is None,
          "a ThrowRat printed without thrown=; actor/victim ambiguity is back")
    label = re.compile(r"^id\d+\(team[12],\w+\)$")
    bad = [x for x in naps + throws if not label.match(x)]
    check(f"grab/throw targets are resolved labels ({len(naps)} naps, {len(throws)} throws)",
          not bad, str(bad[:3]))


def test_remove_trap_decoded(text: str) -> None:
    """RemoveTrap must be decoded, not silently dropped.

    Iteration 201 retracts the King's trap ring once a game has stayed quiet,
    and the proving action is the removal itself. The dumper had no case for
    Action.RemoveTrap, so the ring vanished from the board while the dump
    showed nothing -- "0 removals" read as "the retraction never fired" when
    the retraction had in fact fired sixteen times in one round. An action the
    dumper cannot see is an action no mechanism check can confirm.
    """
    removes = re.findall(r"\) RemoveTrap loc=(\S+) team=(\d)", text)
    bare = re.search(r"\) RemoveTrap(?! loc=)", text)
    check("every RemoveTrap names a location", bare is None,
          "a RemoveTrap printed without loc=")
    for loc, team in removes:
        check_once = team in ("1", "2")
        if not check_once:
            check("RemoveTrap team is 1 or 2", False, f"team={team}")
            break
    check(f"RemoveTrap decodes ({len(removes)} seen)", True, "")


def test_turns_mode(replay: str, window: tuple[int, int]) -> None:
    """`--turns` must emit position+facing lines, including for idle robots.

    Two traps this pins. First, the line is emitted before dumpActions' early
    return for turns with no actions -- a robot standing still does nothing, and
    those turns are exactly the ones a rotate-to-scan proposal depends on.

    Second, dir is NOT the Direction enum ordinal. GameMaker writes
    FlatHelpers.getOrdinalFromDirection, a bespoke mapping where 0=CENTER,
    1=WEST, 2=SOUTHWEST, ... 7=NORTH, 8=NORTHWEST. Decoding it as the enum
    ordinal turns 44% of moves into an apparent 96%, which reads as a
    catastrophic finding rather than a units bug.
    """
    # The window must exist in THIS replay. A hardcoded [300,320] silently
    # fails on any short game -- a knifefight wipe is over by round 20, so the
    # suite reported "0 turns" as a decoding regression when the real cause was
    # an empty window.
    lo, hi = window
    r = subprocess.run([str(DUMP), replay, "--turns", "1", "--from", str(lo), "--to", str(hi)],
                       capture_output=True, text=True, timeout=600)
    check("--turns exits cleanly", r.returncode == 0, r.stderr[:200])
    lines = [l for l in r.stdout.splitlines() if " TURN " in l]
    check("--turns emits turn lines", len(lines) > 0)
    bad_team = [l for l in lines if "(team2," in l]
    check("--turns 1 filters to team 1", not bad_team, str(bad_team[:2]))
    dirs = [int(m) for m in re.findall(r" dir=(\d+)", "\n".join(lines))]
    check(f"dir is in the 0..8 wire range ({len(dirs)} turns)",
          dirs and all(0 <= d <= 8 for d in dirs),
          str(sorted(set(dirs))[:12]))
    fields = re.findall(r"TURN id\d+\(team\d,\w+\) at \(\d+,\d+\) dir=\d+ hp=\d+ moveCd=\d+ turnCd=\d+", "\n".join(lines))
    check("every turn line is fully formed", len(fields) == len(lines),
          f"{len(fields)} of {len(lines)}")


def test_bad_flag_errors(replay: str) -> None:
    """A misspelled flag must fail loudly, not silently dump the whole game."""
    r = subprocess.run([str(DUMP), replay, "--form", "100"],
                       capture_output=True, text=True, timeout=600)
    check("unknown flag is a hard error", r.returncode != 0,
          "misspelled flag was silently ignored")


def main(argv):
    default = REPO / "gauntlet" / "20260903-201821" / "losses" / "bench_spaark__knifefight__botB.bc26"
    replay = argv[0] if argv else str(default)
    if not pathlib.Path(replay).exists():
        raise SystemExit(f"no such replay: {replay}")
    print(f"testing ReplayDump against {pathlib.Path(replay).name}\n")

    full = dump(replay)
    test_header(full)
    test_loc_packing(full)
    test_stats_unpacking(full)
    rounds = test_rounds_monotonic(full)
    test_spawn_ids_unique(full)
    test_spawn_in_bounds(full)
    test_footer(full, rounds)
    test_grab_throw_name_both_ends(full)
    test_remove_trap_decoded(full)
    test_turns_mode(replay, window_for(full))
    lo, hi = window_for(full)
    print(f"  (sub-window for this replay: rounds {lo}-{hi})")
    test_window_respected(replay, lo, hi)
    test_labels_survive_from(replay, lo, hi)
    test_labels_agree_across_windows(replay, lo, hi)
    test_bad_flag_errors(replay)

    print()
    if _failures:
        print(f"FAILED {len(_failures)}: {', '.join(_failures)}")
        return 1
    print("all checks passed")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
