#!/usr/bin/env python3
"""
Peer win-rate spread over time, built from this project's own Gauntlet run
history (gauntlet/<run-id>/results.csv -- git-ignored/ephemeral, but
retained locally across a session, which is enough history to plot).
Ported from battlecode22-vibe's tools/plot_alt_metrics.py.

For each full peer Gauntlet run, plots the best and worst per-opponent win
rate in that run, over time. Unlike the cumulative-iterations chart, this
shows dispersion -- is progress broad (both ends rising together) or
lopsided (a growing gap between the easiest and hardest peer matchup)?

BC22's version of this script also plotted average LOSING game length
against benchmark opponents over time -- omitted here because this project
has no benchmark opponents yet (nothing has beaten `bot` even once this
session; see TRAINING_LOG.md). Add that chart back once one exists.

MIN_PEERS is much lower than BC22's (14, reflecting that project's
128-iteration, many-vendored-bot roster) -- this project has run at most 3
opponents in a single Gauntlet so far. Set to 2 so early single-opponent
runs (early Iteration 0-4 sessions were examplefuncsplayer-only) don't
count as "full" runs for this chart, but any run exercising the real peer
roster does. Revisit upward as the roster grows.

Usage:
    tools/.venv/bin/python3 tools/plot_alt_metrics.py

Requires matplotlib in tools/.venv. Run from the repo root.
"""
import csv
import re
from collections import defaultdict
from datetime import datetime
from pathlib import Path
from zoneinfo import ZoneInfo

REPO_ROOT = Path(__file__).resolve().parent.parent
PACIFIC = ZoneInfo("America/Los_Angeles")
UTC = ZoneInfo("UTC")

MIN_PEERS = 2

# No benchmark opponents exist yet (see module docstring) -- kept as an
# empty tuple, not deleted, so the retirement-event/benchmark logic below
# stays structurally ready for when one does.
BENCHMARK_BOTS = ()


def run_timestamp(rundir: Path):
    # gauntlet.sh names each run directory via the VM's local `date`
    # (RUN_ID="$(date +%Y%m%d-%H%M%S)"), which on battlecode-dev is UTC.
    dt = datetime.strptime(rundir.name, "%Y%m%d-%H%M%S").replace(tzinfo=UTC)
    return dt.astimezone(PACIFIC)


def load_results(path: Path):
    with open(path) as f:
        return list(csv.DictReader(f))


def is_vs_old_bots_run(per_opp):
    """True if every opponent is a g_iterN snapshot, i.e. this is a
    vs-old-bots run rather than a peer-roster Gauntlet.

    These are tracked separately (progress/vs_old_bots.png) and must be
    excluded here. Including them corrupted this chart two ways once the
    vs-old-bots roster grew past a single opponent and started clearing
    MIN_PEERS: their win rates were plotted as if they were peer-roster
    results, and -- worse -- retirement_events() reads the alternation
    between peer runs and old-bot runs as opponents disappearing, so it
    reported `pure_cooperator`/`immediate_defector` and then
    `g_iter1`/`g_iter11` as "retired" when nothing had been retired at
    all."""
    return bool(per_opp) and all(re.fullmatch(r"g_iter\d+", o) for o in per_opp)


def full_runs():
    """(rundir, timestamp, {opponent -> [wins, total]}) for every
    peer-roster run with >= MIN_PEERS distinct opponents, sorted by time."""
    runs = []
    for rundir in sorted(Path(REPO_ROOT / "gauntlet").glob("*/")):
        p = rundir / "results.csv"
        if not p.exists():
            continue
        per_opp = defaultdict(lambda: [0, 0])
        for row in load_results(p):
            opp = row["opponent"]
            if opp in BENCHMARK_BOTS:
                continue
            per_opp[opp][1] += 1
            if row["bot_result"] == "win":
                per_opp[opp][0] += 1
        if len(per_opp) < MIN_PEERS or is_vs_old_bots_run(per_opp):
            continue
        runs.append((rundir, run_timestamp(rundir), per_opp))
    return runs


def peer_spread_series(runs):
    xs, maxs, mins = [], [], []
    for _, ts, per_opp in runs:
        pcts = [w / t for w, t in per_opp.values() if t > 0]
        if not pcts:
            continue
        xs.append(ts)
        maxs.append(100 * max(pcts))
        mins.append(100 * min(pcts))
    return xs, maxs, mins


def retirement_events(runs):
    """A peer present in one full-Gauntlet run and absent from the next is
    inferred as retired somewhere in between (see TRAINING_ALGORITHM.md's
    "Retiring bots from the Gauntlet"). Returns [(timestamp, [names]), ...]."""
    events = []
    for i in range(1, len(runs)):
        prev_opps = set(runs[i - 1][2].keys())
        cur_opps = set(runs[i][2].keys())
        removed = sorted(prev_opps - cur_opps)
        if removed:
            events.append((runs[i][1], removed))
    return events


def main():
    import matplotlib
    matplotlib.use("Agg")
    import matplotlib.pyplot as plt
    import matplotlib.dates as mdates

    out_dir = REPO_ROOT / "progress"
    out_dir.mkdir(parents=True, exist_ok=True)

    runs = full_runs()
    if not runs:
        print(f"no Gauntlet runs with >= {MIN_PEERS} peer opponents found under gauntlet/ -- nothing to plot")
        return

    xs, maxs, mins = peer_spread_series(runs)
    fig, ax = plt.subplots(figsize=(13, 7))
    ax.fill_between(xs, mins, maxs, color="#94a3b8", alpha=0.15, label="spread")
    ax.plot(xs, maxs, color="#16a34a", marker="o", markersize=3, linewidth=1.6,
             label="best peer matchup (win %)")
    ax.plot(xs, mins, color="#dc2626", marker="o", markersize=3, linewidth=1.6,
             label="worst peer matchup (win %)")

    events = retirement_events(runs)
    for i, (ts, names) in enumerate(events):
        ax.axvline(ts, color="#7c3aed", linestyle=":", linewidth=1.3, alpha=0.8, zorder=1)
        label = "retired: " + ", ".join(names)
        ax.annotate(
            label, (ts, 1.0), xycoords=("data", "axes fraction"),
            textcoords="offset points", xytext=(8, -10 - 13 * (i % 4)),
            rotation=0, va="top", ha="left", fontsize=7.5, color="#7c3aed",
        )
    print(f"marked {len(events)} retirement event(s): "
          + "; ".join(f"{ts:%Y-%m-%d %H:%M} -> {names}" for ts, names in events))

    ax.axhline(50, color="#64748b", linestyle="--", linewidth=1, alpha=0.6, zorder=1)

    ax.set_title(f"Peer win-rate spread over time (full Gauntlet runs, ≥{MIN_PEERS} peer opponents)")
    ax.set_xlabel("Date (Pacific Time)")
    ax.set_ylabel("Win rate vs. a single peer opponent (%)")
    ax.set_ylim(-5, 105)
    ax.grid(True, alpha=0.3)
    ax.xaxis.set_major_formatter(mdates.DateFormatter("%m-%d %H:%M", tz=PACIFIC))
    fig.autofmt_xdate(rotation=30)
    ax.legend(loc="lower left", fontsize=9)
    fig.tight_layout()
    out1 = out_dir / "peer_win_spread.png"
    fig.savefig(out1, dpi=150)
    print(f"wrote {out1} ({len(xs)} qualifying runs)")


if __name__ == "__main__":
    main()
