#!/usr/bin/env python3
"""
Track win % against a fixed roster of old snapshots over time. Ported
from battlecode22-vibe's tools/plot_vs_old_bots.py.

Unlike the peer Gauntlet (which retires dominated opponents, so the
roster keeps changing), this deliberately keeps an old, otherwise-retired
bot around as a fixed yardstick -- so win % against it, checked
periodically, is a direct read of how much stronger the bot has gotten in
absolute terms, not just relative to whichever opponents currently happen
to be in the Gauntlet.

BC22 only started this at every 10th accepted iteration (its own
TRAINING_ALGORITHM.md: "not urgent before there's a roster worth
tracking"), but at this project's current scale (7 iterations) that
threshold hasn't been reached -- started early instead, tracking
`g_iter1` (the very first accepted iteration) as the sole fixed reference
for now. Add further old snapshots to the roster as the project grows
(BC22's convention was every 10th; this project can pick its own cadence
once it has enough iterations for "every 10th" to mean something) --
append them, don't replace `g_iter1`, since the value is in each line's
long-run trend.

Usage:
    # after running a fresh check:
    #   OPPONENTS="g_iter1" MAPSET=loop tools/gauntlet.sh
    tools/.venv/bin/python3 tools/track_vs_old_bots.py <gauntlet_run_dir>   # append a row per opponent
    tools/.venv/bin/python3 tools/plot_vs_old_bots.py                       # regenerate the chart

Data lives in progress/vs_old_bots_history.csv (checked into git --
unlike gauntlet/, this needs to persist across sessions).
"""
import argparse
from collections import defaultdict
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent
HISTORY_CSV = REPO_ROOT / "progress" / "vs_old_bots_history.csv"


def load_history():
    rows = []
    with open(HISTORY_CSV) as f:
        header = f.readline().strip().split(",")
        for line in f:
            line = line.strip()
            if not line:
                continue
            vals = line.split(",")
            rows.append(dict(zip(header, vals)))
    return rows


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("-o", "--output", default=str(REPO_ROOT / "progress" / "vs_old_bots.png"))
    args = ap.parse_args()

    if not HISTORY_CSV.exists():
        print(f"{HISTORY_CSV} doesn't exist yet -- run tools/track_vs_old_bots.py first")
        return

    rows = load_history()
    if not rows:
        print(f"{HISTORY_CSV} has no data rows yet")
        return

    import matplotlib
    matplotlib.use("Agg")
    import matplotlib.pyplot as plt
    import matplotlib.dates as mdates
    from datetime import datetime

    by_opponent = defaultdict(list)
    for r in rows:
        d = datetime.fromisoformat(r["date"])
        by_opponent[r["opponent"]].append((d, float(r["win_pct"]), r["current_snapshot"]))
    for k in by_opponent:
        by_opponent[k].sort(key=lambda t: t[0])

    def opp_sort_key(name):
        return int(name[len("g_iter"):]) if name.startswith("g_iter") else 0

    fig, ax = plt.subplots(figsize=(13, 7))
    colors = plt.cm.viridis_r([i / max(1, len(by_opponent) - 1) for i in range(len(by_opponent))])
    for color, opp in zip(colors, sorted(by_opponent, key=opp_sort_key)):
        pts = by_opponent[opp]
        dates = [p[0] for p in pts]
        pcts = [p[1] for p in pts]
        ax.plot(dates, pcts, marker="o", markersize=5, linewidth=1.6, color=color, label=f"vs {opp}")

    ax.axhline(50, color="gray", linestyle=":", linewidth=1, alpha=0.6)
    ax.set_title("Win % vs. a fixed roster of old snapshots, over time\n(absolute-strength yardstick, not relative to the current Gauntlet)", fontsize=12)
    ax.set_xlabel("Date (Pacific Time)")
    ax.set_ylabel("Win % (out of 20 games)")
    ax.set_ylim(-5, 105)
    ax.grid(True, alpha=0.3)
    ax.xaxis.set_major_formatter(mdates.DateFormatter("%m-%d %H:%M", tz=None))
    fig.autofmt_xdate(rotation=30)
    ax.legend(loc="center left", bbox_to_anchor=(1.01, 0.5), fontsize=8)
    fig.tight_layout()

    out_path = Path(args.output)
    out_path.parent.mkdir(parents=True, exist_ok=True)
    fig.savefig(out_path, dpi=150)
    print(f"wrote {out_path} ({len(rows)} rows, {len(by_opponent)} tracked opponents)")


if __name__ == "__main__":
    main()
