#!/usr/bin/env bash
# Run "the Gauntlet" for the current bot (TRAINING_ALGORITHM.md step 2/3).
#
# For every opponent and every map, play two headless games (bot as team A,
# then as team B) on the GCE VM, record win/loss, and copy back the replays
# of games the bot LOST.
#
# Usage:
#   tools/gauntlet.sh
#   BOT=bot OPPONENTS="examplefuncsplayer g_iter1" tools/gauntlet.sh
#   MAPS="tiny jail" MAXJOBS=4 tools/gauntlet.sh
#   MAPSET=full tools/gauntlet.sh
#
# Output (local, gauntlet/<run-id>/):
#   results.csv   opponent,map,bot_side,winner_side,rounds,bot_result,win_type
#   reasons.txt   opponent map side <win-type text>
#   summary.txt   win rate overall and per opponent
#   losses/*.bc26 replays the bot lost (one match each)
set -euo pipefail

VM=battlecode-dev
ZONE=us-west1-b
PROJECT=tvanbelle-vibecode
REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
REMOTE_DIR=battlecode26-vibe

# PLAIN ssh/scp with the gcloud-managed key -- `gcloud compute ssh` re-pushes
# keys to instance metadata every call and stalls the guest agent under load.
SSHO=(-i "$HOME/.ssh/google_compute_engine" -o StrictHostKeyChecking=no
      -o UserKnownHostsFile=/dev/null -o ConnectTimeout=20 -o ServerAliveInterval=20
      -o ServerAliveCountMax=3 -o LogLevel=ERROR)
USER_NAME="${BC_SSH_USER:-$(whoami)}"
vm_ip () { gcloud compute instances describe "$VM" --zone="$ZONE" --project="$PROJECT" \
             --format='value(networkInterfaces[0].accessConfigs[0].natIP)' 2>/dev/null; }
gssh () { ssh "${SSHO[@]}" "$USER_NAME@$IP" "$1"; }
gscp () { scp "${SSHO[@]}" "$@"; }
wait_ssh () { for _ in $(seq 1 40); do gssh true 2>/dev/null && return 0; sleep 8; done; return 1; }

BOT="${BOT:-bot}"
OPPONENTS="${OPPONENTS:-pure_cooperator immediate_defector}"
MAXJOBS="${MAXJOBS:-6}"          # concurrent games on the VM (8 vCPU)
MAPSET="${MAPSET:-loop}"

# Small, varied subset for fast day-to-day Gauntlets; `MAPSET=full` uses every
# map from bc26-maps.txt (regenerate that file with `./gradlew listMaps`).
LOOP_MAPS="tiny closeup keepout knifefight minimaze pipes rift \
sittingducks thunderdome whereisthecheese"
if [ "${MAPS:-}" ]; then :
elif [ "$MAPSET" = "full" ]; then MAPS="$(tr '\n' ' ' < "$REPO/tools/bc26-maps.txt")"
else MAPS="$LOOP_MAPS"; fi

RUN_ID="$(date +%Y%m%d-%H%M%S)"
OUT="$REPO/gauntlet/$RUN_ID"
mkdir -p "$OUT/losses"
NGAMES=$(( $(echo "$OPPONENTS" | wc -w) * $(echo "$MAPS" | wc -w) * 2 ))
echo "gauntlet $RUN_ID   bot=$BOT   opponents=[$OPPONENTS]   maps=$(echo "$MAPS" | wc -w)   games=$NGAMES   parallel=$MAXJOBS"

# Archetype staleness guard. The synthetic peers (pure_cooperator,
# immediate_defector) are supposed to share src/bot/'s economy/movement code
# and differ only in backstab policy. Twice now they have silently fallen
# behind -- once for 6+ iterations, once again across Iterations 32-40 --
# each time inflating every win rate measured in between, with no signal
# that it was happening. A memory note didn't prevent the second occurrence
# because it only fires if someone remembers to run it, so the check lives
# here instead, where it runs automatically on every Gauntlet.
#
# Two checks, because the line-count one alone has now failed a THIRD time.
# On 2026-09-04 both archetypes sat 21-22% below src/bot -- just under the 25%
# threshold -- while missing two accepted iterations (99 and 102).
#
# Line count is structurally the wrong test: an archetype legitimately DELETES
# code. `pure_cooperator` must not place rat traps at all, because
# GameWorld.triggerTrap calls backstab(robot.getTeam().opponent()), i.e. the
# TRAP'S OWNER initiates the backstab when an enemy steps on it -- so a bot
# that places traps is not a pure cooperator. Those legitimate deletions mask
# genuine drift in the same number.
#
# So also compare MODIFICATION TIME against the newest frozen snapshot. A
# snapshot only appears when an iteration is accepted, so an archetype older
# than the newest g_iterN is missing at least one accepted change, regardless
# of how the line counts happen to land. Warning, not a hard failure -- a
# stale-peer run is still worth having, it just must not be mistaken for a
# clean baseline.
#
# When re-syncing, copy src/bot/ and then RE-APPLY the policy edits; do not
# simply overwrite. pure_cooperator needs `desperate = false` and no rat traps.
BOT_LINES=$(wc -l < "$REPO/src/bot/RobotPlayer.java" 2>/dev/null || echo 0)
#
# EXTERNAL opponents are exempt. bench_* are other teams' bots and
# examplefuncsplayer ships with the engine; none of them derives from src/bot/,
# so "stale" is meaningless for them and "re-sync it to src/bot/" is actively
# wrong advice. Before this exemption a benchmark Gauntlet printed six warnings
# telling me to re-sync bench_finalist/spaark/stroke, which is noise that trains
# you to skip past the ONE warning that matters -- the peers, which have now
# drifted three separate times.
STALE_OPPS=""   # must exist even when nothing is stale; the script runs under set -u
for _opp in $OPPONENTS; do
  case "$_opp" in
    g_iter*) continue ;;                      # frozen snapshots; drift is the point
    bench_*|examplefuncsplayer*) continue ;;  # external bots; not derived from src/bot
  esac
  _f="$REPO/src/$_opp/RobotPlayer.java"
  [ -f "$_f" ] || continue
  _lines=$(wc -l < "$_f")
  if [ "$BOT_LINES" -gt 0 ] && [ "$(( (BOT_LINES - _lines) * 100 / BOT_LINES ))" -gt 25 ]; then
    echo "  !! WARNING: $_opp is $_lines lines vs bot's $BOT_LINES -- likely stale."
    echo "  !! Re-sync it to src/bot/ before trusting this run's win rate."
  fi
  # Date check: older than the newest accepted snapshot means it is missing at
  # least one accepted iteration, whatever the line counts say.
  _newest_snap=$(ls -1dt "$REPO"/src/g_iter*/RobotPlayer.java 2>/dev/null | head -1)
  if [ -n "$_newest_snap" ] && [ "$_f" -ot "$_newest_snap" ]; then
    echo "  !! WARNING: $_opp is older than $(basename "$(dirname "$_newest_snap")") --"
    echo "  !! it is missing at least one accepted iteration. Re-sync before trusting win rates."
    echo "  !! (See TRAINING_LOG.md's archetype-staleness entries.)"
    STALE_OPPS="$STALE_OPPS $_opp"
  fi
done

state=$(gcloud compute instances describe "$VM" --zone="$ZONE" --project="$PROJECT" --format='value(status)' 2>/dev/null || true)
[ "$state" = RUNNING ] || { echo "  starting VM ..."; gcloud compute instances start "$VM" --zone="$ZONE" --project="$PROJECT" >/dev/null; }
IP="$(vm_ip)"; RVM="$USER_NAME@$IP"
[ -n "$IP" ] || { echo "!! no external IP" >&2; exit 1; }
wait_ssh || { echo "!! cannot reach $RVM" >&2; exit 1; }
gssh "pkill -9 -f gauntlet_run.sh; pkill -9 -f battlecode.server; pkill -9 -f org.gradle; mkdir -p ~/$REMOTE_DIR/src" 2>/dev/null || true
gscp -r "$REPO/src/." "$RVM:$REMOTE_DIR/src/" >/dev/null

# ---- remote runner: bare `java` per game (1 JVM, no gradle daemon), parallel ----
remote=$(mktemp)
cat > "$remote" <<REMOTE
set -uo pipefail
export JAVA_HOME=\$HOME/jdk21 PATH=\$HOME/jdk21/bin:\$PATH
cd ~/$REMOTE_DIR
./gradlew --no-daemon -q build >/dev/null 2>&1 || { echo "BUILD-FAILED" > gauntlet/results.txt; exit 1; }
BC_JAR=\$(find ~/.gradle -name 'battlecode26-java-*.jar' | sort -V | tail -1)
CP="build/classes:\$BC_JAR"
mkdir -p gauntlet; : > gauntlet/results.txt

game () {  # <opp> <map> <side>
  local OPP=\$1 MAP=\$2 SIDE=\$3 TA TB
  if [ "\$SIDE" = A ]; then TA=$BOT; TB=\$OPP; else TA=\$OPP; TB=$BOT; fi
  local REPLAY=gauntlet/\${OPP}__\${MAP}__bot\${SIDE}.bc26 LOG W R RE
  LOG=\$(java -Xmx2g \\
    --add-opens=java.base/jdk.internal.misc=ALL-UNNAMED \\
    --add-opens=java.base/jdk.internal.math=ALL-UNNAMED \\
    --add-opens=java.base/jdk.internal.util=ALL-UNNAMED \\
    --add-opens=java.base/jdk.internal.access=ALL-UNNAMED \\
    --add-opens=java.base/sun.security.action=ALL-UNNAMED \\
    -Dbc.server.wait-for-client=false -Dbc.server.mode=headless -Dbc.server.map-path=maps \\
    -Dbc.server.robot-player-to-system-out=false -Dbc.server.debug=false \\
    -Dbc.engine.debug-methods=false -Dbc.engine.enable-profiler=false -Dbc.engine.show-indicators=true \\
    -Dbc.game.team-a="\$TA" -Dbc.game.team-b="\$TB" \\
    -Dbc.game.team-a.language=java -Dbc.game.team-b.language=java \\
    -Dbc.game.team-a.url=build/classes -Dbc.game.team-b.url=build/classes \\
    -Dbc.game.team-a.package="\$TA" -Dbc.game.team-b.package="\$TB" \\
    -Dbc.game.maps="\$MAP" -Dbc.server.validate-maps=true -Dbc.server.alternate-order=false \\
    -Dbc.server.save-file="\$REPLAY" \\
    -cp "\$CP" battlecode.server.Main -c=- 2>&1 || true)
  W=\$(printf '%s\n' "\$LOG"  | sed -n 's/.*(\([AB]\)) wins.*/\1/p' | tail -1)
  R=\$(printf '%s\n' "\$LOG"  | sed -n 's/.*wins (round \([0-9]*\)).*/\1/p' | tail -1)
  RE=\$(printf '%s\n' "\$LOG" | sed -n 's/.*Reason: //p' | tail -1)
  printf 'RESULT %s %s %s %s %s\n' "\$OPP" "\$MAP" "\$SIDE" "\${W:-?}" "\${R:-?}" >> gauntlet/results.txt
  printf 'REASON %s %s %s %s\n'    "\$OPP" "\$MAP" "\$SIDE" "\${RE:-?}"           >> gauntlet/results.txt
  # Count OUR robots' thrown exceptions. RobotPlayer.run() catches
  # GameActionException per turn, so a throw silently abandons the rest of that
  # robot's turn -- every turn -- and the only trace is this line. A muster bug
  # once aborted the King's entire turn from round 200 on, which read as a
  # failed strategy rather than a missing canSenseLocation guard. 162 games were
  # being run with nobody looking at it.
  EX=\$(printf '%s\n' "\$LOG" | grep -c "^\[\$SIDE:.*Exception" || true)
  printf 'EXC %s %s %s %s\n'       "\$OPP" "\$MAP" "\$SIDE" "\${EX:-0}"           >> gauntlet/results.txt
}

for OPP in $OPPONENTS; do
  for MAP in $MAPS; do
    for SIDE in A B; do
      while [ "\$(jobs -rp | wc -l)" -ge $MAXJOBS ]; do wait -n; done
      game "\$OPP" "\$MAP" "\$SIDE" &
    done
  done
done
wait
echo GAUNTLET-COMPLETE >> gauntlet/results.txt
REMOTE
gscp "$remote" "$RVM:gauntlet_run.sh" >/dev/null
gssh 'setsid bash -c "bash ~/gauntlet_run.sh > ~/gauntlet_run.log 2>&1" </dev/null >/dev/null 2>&1 &' >/dev/null || true

RES=$REMOTE_DIR/gauntlet/results.txt
echo "  polling every 45s ..."
seen=0; deadline=$(( $(date +%s) + 120*60 ))
while true; do
  sleep 45
  snap=$(gssh "cat $RES 2>/dev/null; echo '@@@'; pgrep -f 'gauntlet_run.sh|battlecode.server' >/dev/null && echo ALIVE") || { echo "  (ssh retry)"; continue; }
  body=${snap%@@@*}; ctl=${snap#*@@@}
  printf '%s\n' "$body" > "$OUT/results.txt"
  n=$(printf '%s\n' "$body" | grep -c '^RESULT ' || true)
  if [ "$n" -gt "$seen" ]; then
    printf '%s\n' "$body" | grep '^RESULT ' | tail -n +"$((seen+1))" | while read -r _ OPP MAP SIDE WIN RND; do
      [ "$WIN" = "$SIDE" ] && r="win " || { [ "$WIN" = "?" ] && r="????" || r="LOSS"; }
      printf '  [%2d/%d] %s %-20s %-16s r%s\n' "$n" "$NGAMES" "$r" "$MAP" "$OPP" "$RND"
    done
    seen=$n
  fi
  grep -q '^BUILD-FAILED' "$OUT/results.txt" && { echo "!! remote build failed" >&2; exit 1; }
  grep -q '^GAUNTLET-COMPLETE' "$OUT/results.txt" && { echo "  complete ($n games)"; break; }
  printf '%s\n' "$ctl" | grep -q ALIVE || { echo "!! runner died at $n/$NGAMES games" >&2; break; }
  [ "$(date +%s)" -gt "$deadline" ] && { echo "!! poll deadline" >&2; break; }
done

# ---- collate ----
{ echo "opponent,map,bot_side,winner_side,rounds,bot_result"
  grep '^RESULT ' "$OUT/results.txt" | while read -r _ OPP MAP SIDE WIN RND; do
    [ "$WIN" = "$SIDE" ] && R=win || { [ "$WIN" = "?" ] && R=unknown || R=loss; }
    echo "$OPP,$MAP,$SIDE,$WIN,$RND,$R"
  done; } > "$OUT/results.csv"
grep '^REASON ' "$OUT/results.txt" | sed 's/^REASON //' > "$OUT/reasons.txt" || true

awk -F, 'NR>1 && $6=="loss"{print $1"__"$2"__bot"$3".bc26"}' "$OUT/results.csv" | while read -r k; do
  [ -n "$k" ] && gscp "$RVM:$REMOTE_DIR/gauntlet/$k" "$OUT/losses/" >/dev/null 2>&1 || true
done

{
  total=$(($(wc -l < "$OUT/results.csv") - 1))
  wins=$(grep -c ',win$' "$OUT/results.csv" || true)
  echo "run $RUN_ID   bot=$BOT"
  # Repeat any staleness warning HERE, inside the summary. It was already printed
  # at startup and it fired correctly on five consecutive runs on 2026-09-05 --
  # and was missed every time, because the results were read with
  # `grep -E "^overall:|^  vs "`, which discards it. A warning that only appears
  # 300 lines above the number it invalidates is a warning you will filter out.
  if [ -n "$STALE_OPPS" ]; then
    echo "!! STALE OPPONENTS:$STALE_OPPS -- win rates below are INFLATED."
    echo "!! Run tools/resync_archetypes.py before trusting or logging this run."
  fi
  awk -v w="$wins" -v t="$total" 'BEGIN{printf "overall: %d/%d wins (%.1f%%)\n", w, t, (t>0)?100*w/t:0}'
  # SWEPT-MAP SCORE. A game count is contaminated by spawn advantage: measured
  # 2026-09-06, 27 of 27 maps against pure_cooperator are split (we win one side and
  # lose the other), i.e. 100% of that archetype's result is decided by side rather
  # than by play -- it is a mirror. opportunistic is 78% split, immediate_defector
  # only 11%. Counting MAPS WON FROM BOTH SIDES cancels the side effect: a swept map
  # is one we win regardless of spawn.
  echo
  echo "  swept maps (won from BOTH sides) -- immune to spawn advantage:"
  for OPP in $OPPONENTS; do
    awk -F, -v o="$OPP" 'NR>1 && $1==o {r[$2]=r[$2] $6 ";"} END {
      sw=0; sl=0; sp=0; n=0
      for (m in r) { n++
        if (r[m] ~ /win;.*win;/) sw++
        else if (r[m] ~ /loss;.*loss;/) sl++
        else sp++ }
      printf "    vs %-20s swept-win %2d/%d   swept-loss %2d   split-by-side %2d\n", o, sw, n, sl, sp
    }' "$OUT/results.csv"
  done
  echo
  for OPP in $OPPONENTS; do
    t=$(grep -c "^$OPP," "$OUT/results.csv" || true)
    w=$(grep -c "^$OPP,.*,win$" "$OUT/results.csv" || true)
    awk -v o="$OPP" -v w="$w" -v t="$t" 'BEGIN{printf "  vs %-20s %d/%d (%.0f%%)\n", o, w, t, (t>0)?100*w/t:0}'
  done
  # Surface thrown exceptions ABOVE the loss list, because a nonzero count means
  # some of these games were played by a bot that was silently skipping the rest
  # of a turn -- the win rate is then measuring a bug, not the change.
  grep '^EXC ' "$OUT/results.txt" | awk '{s+=$5; if($5>0) n++} END{
      if (s>0) {
        printf "\n  !! %d thrown exceptions across %d of the games.\n", s, n
        printf "  !! run()'"'"'s per-turn catch means each one abandoned the rest of that\n"
        printf "  !! robot'"'"'s turn. Fix before trusting this win rate.\n"
        printf "  !! worst offenders:\n"
      }
    }'
  grep '^EXC ' "$OUT/results.txt" | awk '$5>0{printf "  !!   %-16s %-20s bot=%s  %s exceptions\n",$2,$3,$4,$5}' \
      | sort -k5 -rn | head -5
  echo
  echo "losses:"
  awk -F, 'NR>1 && $6=="loss"{printf "  %-20s %-16s bot=%s  r%s\n",$2,$1,$3,$5}' "$OUT/results.csv"
} | tee "$OUT/summary.txt"

echo
echo "wrote $OUT/"
rm -f "$remote"
