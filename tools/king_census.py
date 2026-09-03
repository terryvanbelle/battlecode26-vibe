#!/usr/bin/env python3
"""
Per-window King action census for a .bc26 replay.

Answers the question that Iteration 59 got wrong: is the King's *action
budget* the binding constraint, or is `MAX_POPULATION` (or cheese)?
Iteration 59 reallocated the King's actions on the strength of an action
census showing 41% spent attacking and 26% trapping -- and produced zero
extra rats, because the King was pinned at the population cap the whole
game and was never waiting on actions. Measuring how a resource is *spent*
does not establish that it is *scarce*; this tool exists to check the
second thing rather than the first.

Reports, per BUILD_WINDOW_ROUNDS-sized window:
  - King SpawnAction / PlaceTrap / RatAttack counts
  - whether spawns hit MAX_POPULATION (the cap binding)
  - our cheese and live-rat count at the end of the window

A window at the cap means production is cap-limited: freeing King actions
there will do nothing. A window below the cap with low cheese means it is
cheese-limited instead. Those two need opposite fixes, and they can occur
in different windows of the SAME game -- vs bench_finalist the first two
windows are cap-limited and the third is bankruptcy.

Usage:
    tools/.venv/bin/python3 tools/king_census.py <replay.bc26> [--window 400] [--cap 25]

Requires tools/replay-dump.sh on PATH-relative repo layout. Run from the
repo root.
"""
import argparse
import re
import subprocess
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent


def dump(replay: Path) -> list[str]:
    """Text dump of the replay, via the existing replay-dump.sh."""
    out = subprocess.run(
        [str(REPO_ROOT / "tools" / "replay-dump.sh"), str(replay)],
        capture_output=True, text=True, timeout=300,
    )
    return out.stdout.splitlines()


def bot_team(lines) -> int | None:
    """Which engine team number is `bot` in this replay.

    This must be read per file, never assumed: the dump numbers teams by
    SIDE, so `bot` is team 1 in some replays and team 2 in others. Getting
    this backwards silently inverts every number in the report -- it has
    already caused three wrong conclusions in this project.
    """
    for ln in lines:
        m = re.search(r"team ([12]) packageName=(\S+)", ln)
        if m and m.group(2) == "bot":
            return int(m.group(1))
    return None


def census(lines, team: int, window: int, cap: int):
    rows = {}
    rnd = 0
    state = {}
    for ln in lines:
        m = re.match(r"round (\d+) ", ln)
        if m:
            rnd = int(m.group(1))
        # per-round scoreboard line carries cheese and live rats for both teams
        s = re.search(r"aliveBabies=\[(\d+),(\d+)\]", ln)
        if s:
            alive = int(s.group(team))
            c = re.findall(r"[12]:kings=\d+,cheese=(\d+)", ln)
            cheese = int(c[team - 1]) if len(c) >= team else None
            state[rnd // window] = (alive, cheese)
        for action in ("SpawnAction", "PlaceTrap", "RatAttack"):
            if re.search(rf"\(team{team},RAT_KING\) {action}", ln):
                w = rnd // window
                rows.setdefault(w, {"SpawnAction": 0, "PlaceTrap": 0, "RatAttack": 0})
                rows[w][action] += 1
    print(f"{'window':>6}  {'rounds':>12}  {'spawn':>6} {'trap':>5} {'atk':>5}  "
          f"{'alive':>5} {'cheese':>7}   verdict")
    for w in sorted(set(rows) | set(state)):
        r = rows.get(w, {"SpawnAction": 0, "PlaceTrap": 0, "RatAttack": 0})
        alive, cheese = state.get(w, (None, None))
        sp = r["SpawnAction"]
        if sp >= cap:
            verdict = "CAP-LIMITED"
        elif cheese is not None and cheese < 300:
            verdict = "cheese-limited"
        else:
            verdict = "-"
        print(f"{w:>6}  {w*window:>5}-{w*window+window-1:<6}  {sp:>6} "
              f"{r['PlaceTrap']:>5} {r['RatAttack']:>5}  "
              f"{alive if alive is not None else '?':>5} "
              f"{cheese if cheese is not None else '?':>7}   {verdict}")
    total = sum(r["SpawnAction"] for r in rows.values())
    print(f"\ntotal King spawns: {total}")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("replay")
    ap.add_argument("--window", type=int, default=400, help="BUILD_WINDOW_ROUNDS")
    ap.add_argument("--cap", type=int, default=25, help="MAX_POPULATION")
    args = ap.parse_args()

    lines = dump(Path(args.replay))
    team = bot_team(lines)
    if team is None:
        sys.exit("could not find `bot` in the replay header -- is this our replay?")
    print(f"{Path(args.replay).name}  (bot = team {team}, "
          f"window={args.window}, cap={args.cap})\n")
    census(lines, team, args.window, args.cap)


if __name__ == "__main__":
    main()
