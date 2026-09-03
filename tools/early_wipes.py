#!/usr/bin/env python3
"""
Early-King-wipe rate for one or more gauntlet runs.

An "early wipe" is a LOSS that ends before round 100. It is the counter that
sits upstream of ~91% of benchmark losses (RATKING_DESTROYED), and it is the
only counter with variance on the benchmark set -- the mirror reads 0/40 and
0/24 on it in every build, which is why the mirror could not see the King
trap ring's value (see TRAINING_LOG.md, Iterations 82/96).

This existed only as an ad-hoc awk one-liner and was recomputed by hand at
least four times, with the round-100 threshold carried in my head rather than
in the repo. That is exactly how a threshold silently drifts between two
"comparable" measurements, so it lives here now.

Reports BOTH denominators on purpose:

  count/losses  the historical figure, comparable to TRAINING_LOG.md entries
  count/games   the honest one -- `losses` shrinks as you win more games, so
                the /losses rate can move while the raw wipe count is flat

Quote the raw count alongside either rate. Two runs at "14%" with counts 22
and 22 are the same result; two runs at "14%" with counts 22 and 18 are not.

Usage:
    tools/.venv/bin/python3 tools/early_wipes.py gauntlet/<run-id> [<run-id> ...]
"""
import csv
import pathlib
import sys

THRESHOLD = 100

# The seven maps whose Rat Kings spawn within ~21 tiles of each other,
# measured from replay headers (TRAINING_LOG.md, "early wipes are entirely a
# close-spawn phenomenon"). In the g_iter20 benchmark run these seven supplied
# ALL 22 early wipes; the other twenty maps supplied zero, with no overlap.
#
# They are broken out because they are the only part of the benchmark set that
# poses the rush at all. Averaged over all 162 games a change to rush defence
# is diluted by twenty maps where the failure mode does not occur -- the same
# representativeness trap as judging a defensive feature on the mirror, one
# level down. Judge wipe work on these 42 games.
RUSH_MAPS = {
    "knifefight",       # King distance  5.0
    "tiny",             #                5.0
    "thunderdome",      #                8.0
    "dirtfulcat",       #               15.0
    "popthecork",       #               17.0
    "evileye",          #               21.2
    "toomuchcheese",    #               21.2
}


def rate(run_dir: pathlib.Path):
    with (run_dir / "results.csv").open() as fh:
        rows = list(csv.DictReader(fh))
    losses = [r for r in rows if r["bot_result"] == "loss"]
    wipes = [r for r in losses if int(r["rounds"]) < THRESHOLD]
    return rows, losses, wipes


def main(argv):
    if not argv:
        print(__doc__.strip().split("Usage:")[-1].strip(), file=sys.stderr)
        return 2
    for arg in argv:
        d = pathlib.Path(arg)
        rows, losses, wipes = rate(d)
        print(f"{d.name}  wins {len(rows) - len(losses)}/{len(rows)}")
        print(
            f"    early wipes (< round {THRESHOLD}):  {len(wipes)}/{len(losses)}"
            f" = {100 * len(wipes) / len(losses):.0f}% of losses"
            f"   ({100 * len(wipes) / len(rows):.0f}% of games)"
        )
        fastest = sorted(int(r["rounds"]) for r in losses)[:8]
        print(f"    fastest losses: {fastest}")
        by_map = {}
        for r in wipes:
            by_map[r["map"]] = by_map.get(r["map"], 0) + 1
        top = sorted(by_map.items(), key=lambda kv: -kv[1])[:6]
        print(f"    wipes by map:   {', '.join(f'{m} {n}' for m, n in top)}")

        rush = [r for r in rows if r["map"] in RUSH_MAPS]
        if rush:
            rl = [r for r in rush if r["bot_result"] == "loss"]
            rw = [r for r in rl if int(r["rounds"]) < THRESHOLD]
            stray = len(wipes) - len(rw)
            print(
                f"    close-spawn maps ({len(RUSH_MAPS)}):  "
                f"wins {len(rush) - len(rl)}/{len(rush)}   "
                f"wipes {len(rw)}/{len(rl)} = {100 * len(rw) / len(rl):.0f}% of losses"
            )
            if stray:
                print(
                    f"    NOTE: {stray} wipe(s) outside the close-spawn set --"
                    f" the map/wipe relationship has shifted, re-measure it"
                )
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
