#!/usr/bin/env bash
# Paired mechanism check: run src/bot AND the newest accepted snapshot on two maps of
# OPPOSITE character, and print them side by side.
#
# Why this exists (TRAINING_LOG.md, Iterations 232/233/243/247):
#
#   232  A single-map mechanism check looked superb -- CheesePickup 61 -> 132, the
#        game flipped LOSS -> WIN -- and the 216-game run came back 130/216 against a
#        161 baseline. The trace map was a SPARSE MAZE, the terrain most flattering to
#        the hypothesis, and it was chosen precisely because it was a hard (swept-loss)
#        map. Checking one map of the opposite character first would have caught it.
#
#   247  I quoted a control figure from the log (corridor "r1032") that dated from an
#        earlier snapshot; the current build gave r491 on its own. I nearly recorded a
#        large regression that was really the accepted baseline -- and the same stale
#        number had already corrupted Iteration 243's write-up.
#
# So the control is ALWAYS re-measured here, never quoted, and two map characters are
# always run. Cost is four matches, a few minutes, against ~20 for a full gauntlet.
#
#   tools/paired-check.sh                    # default map pair and opponents
#   MAZE=rift OPEN=sittingducks tools/paired-check.sh
set -euo pipefail
REPO="$(cd "$(dirname "$0")/.." && pwd)"
cd "$REPO"

# Sparse maze vs open-and-cheese-rich: the axis that flipped Iterations 232 and 243.
MAZE="${MAZE:-corridorofdoomanddespair}"
OPEN="${OPEN:-closeup}"
MAZE_OPP="${MAZE_OPP:-g_iter21}"
OPEN_OPP="${OPEN_OPP:-opportunistic}"

CONTROL="$(ls -1d src/g_iter*/ 2>/dev/null | sed 's#src/##;s#/##' \
           | awk '{n=substr($0,7)+0; if (n>m) {m=n; b=$0}} END {print b}')"
[ -n "$CONTROL" ] || { echo "no g_iterN snapshot found" >&2; exit 1; }
echo "control = $CONTROL (re-measured now, never quoted)"
echo

run() {  # opponent map bot -> "rounds winner spawns pickups catTraps"
  local opp="$1" map="$2" bot="$3"
  TEAM_A="$opp" TEAM_B="$bot" tools/vm-match.sh "$map" >/dev/null 2>&1
  local d; d="$(mktemp)"
  tools/replay-dump.sh "matches/$opp-vs-$bot-on-$map.bc26" > "$d" 2>&1
  printf "%s %s %s %s %s" \
    "$(grep -oE '^round [0-9]+' "$d" | tail -1 | grep -oE '[0-9]+')" \
    "$(grep -m1 -oE 'winner=[12] winType=[A-Z_]+' "$d" | tr ' ' '/')" \
    "$(grep -c '(team2,RAT_KING) SpawnAction' "$d")" \
    "$(grep -cE '\(team2,RAT\) CheesePickup' "$d")" \
    "$(grep -cE '\(team2,RAT\) PlaceTrap CAT' "$d")"
  rm -f "$d"
}

printf "%-26s %-9s %-6s %-28s %-7s %-8s %s\n" map arm rounds outcome spawns pickups catTraps
for pair in "$MAZE_OPP:$MAZE:maze" "$OPEN_OPP:$OPEN:open"; do
  opp="${pair%%:*}"; rest="${pair#*:}"; map="${rest%%:*}"; kind="${rest##*:}"
  for arm in "$CONTROL" bot; do
    read -r rounds outcome spawns pickups cattraps <<<"$(run "$opp" "$map" "$arm")"
    label=$([ "$arm" = bot ] && echo candidate || echo control)
    printf "%-26s %-9s %-6s %-28s %-7s %-8s %s\n" \
      "$map ($kind)" "$label" "$rounds" "$outcome" "$spawns" "$pickups" "$cattraps"
  done
done
echo
echo "Read BOTH rows of BOTH maps. A gain on one character and a loss on the other is"
echo "the signature that sank Iterations 232 and 243 -- run the full gauntlet only if"
echo "the candidate is at least neutral on both, or if the split itself is the finding."
