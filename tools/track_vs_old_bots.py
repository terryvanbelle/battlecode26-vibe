#!/usr/bin/env python3
"""
Append one row per opponent to progress/vs_old_bots_history.csv from a
completed Gauntlet run's results.csv. See tools/plot_vs_old_bots.py for
the full picture (what this data is for, how to regenerate the chart).
Ported from battlecode22-vibe's tools/track_vs_old_bots.py.

The roster is **every 10th accepted snapshot** (g_iter1, g_iter11,
g_iter21, ...), matching battlecode22-vibe's policy -- a fixed set of
historical reference points that grows as the project does, so the chart
shows progress against genuinely old bots rather than only the single
oldest one. Add the next one each time a g_iterN with N ending in 1
(11, 21, 31, ...) is accepted; never replace earlier entries.

Usage (keep this list current -- see roster_opponents() below, which
derives it automatically):
    OPPONENTS="$(tools/.venv/bin/python3 tools/track_vs_old_bots.py --roster)" \\
        tools/gauntlet.sh
    tools/.venv/bin/python3 tools/track_vs_old_bots.py gauntlet/<run-id>/

current_snapshot is auto-detected as the highest-numbered src/g_iterN/
directory at the time this is run.
"""
import csv
import re
import sys
from collections import defaultdict
from datetime import datetime, timezone
from pathlib import Path
from zoneinfo import ZoneInfo

REPO_ROOT = Path(__file__).resolve().parent.parent
PACIFIC = ZoneInfo("America/Los_Angeles")
HISTORY_CSV = REPO_ROOT / "progress" / "vs_old_bots_history.csv"


def current_snapshot():
    names = [p.name for p in (REPO_ROOT / "src").iterdir()
             if p.is_dir() and re.fullmatch(r"g_iter\d+", p.name)]
    return max(names, key=lambda n: int(n[len("g_iter"):]))


def roster_opponents():
    """Every 10th accepted snapshot (g_iter1, g_iter11, g_iter21, ...) that
    actually exists, excluding the current one (a bot doesn't play itself).

    Derived rather than hardcoded specifically so this can't silently go
    stale: the original port hardcoded "g_iter1" as the usage example
    because that was the only snapshot old enough at the time, and then
    wasn't revisited when g_iter11 was accepted -- so the chart kept
    tracking a single reference point long after a second one existed.
    """
    existing = {p.name for p in (REPO_ROOT / "src").iterdir()
                if p.is_dir() and re.fullmatch(r"g_iter\d+", p.name)}
    current = current_snapshot()
    newest = int(current[len("g_iter"):])
    return [n for n in (f"g_iter{i}" for i in range(1, newest + 1, 10))
            if n in existing and n != current]


def run_timestamp(rundir):
    m = re.search(r"(\d{8})-(\d{6})", rundir.name)
    dt = datetime.strptime(m.group(1) + m.group(2), "%Y%m%d%H%M%S").replace(tzinfo=timezone.utc)
    return dt.astimezone(PACIFIC)


def main():
    if len(sys.argv) == 2 and sys.argv[1] == "--roster":
        print(" ".join(roster_opponents()))
        return
    if len(sys.argv) != 2:
        print(__doc__)
        sys.exit(1)
    rundir = Path(sys.argv[1])
    results_csv = rundir / "results.csv"
    if not results_csv.exists():
        results_csv = rundir  # allow passing results.csv directly
        rundir = rundir.parent

    tally = defaultdict(lambda: [0, 0])
    with open(results_csv) as f:
        for row in csv.DictReader(f):
            opp = row["opponent"]
            tally[opp][1] += 1
            if row["bot_result"] == "win":
                tally[opp][0] += 1

    ts = run_timestamp(rundir).isoformat()
    snap = current_snapshot()

    is_new = not HISTORY_CSV.exists()
    HISTORY_CSV.parent.mkdir(parents=True, exist_ok=True)
    with open(HISTORY_CSV, "a") as f:
        if is_new:
            f.write("date,current_snapshot,opponent,wins,total,win_pct\n")
        for opp in sorted(tally, key=lambda n: int(n[len("g_iter"):]) if n.startswith("g_iter") else 0):
            wins, total = tally[opp]
            pct = round(100 * wins / total, 1) if total else 0.0
            f.write(f"{ts},{snap},{opp},{wins},{total},{pct}\n")

    print(f"appended {len(tally)} rows to {HISTORY_CSV} (snapshot={snap}, date={ts})")


if __name__ == "__main__":
    main()
