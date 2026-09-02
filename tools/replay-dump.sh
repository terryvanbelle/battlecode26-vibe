#!/usr/bin/env bash
# Turn a .bc26 replay into a human-readable text transcript (see
# tools/replaydump/ReplayDump.java for what it prints and why it's written
# in Java against the engine's own vendored schema classes rather than a
# Python FlatBuffers reader -- see tools/README.md).
#
# Usage:
#   tools/replay-dump.sh matches/some-replay.bc26
#   tools/replay-dump.sh matches/some-replay.bc26 --from 100 --to 150
set -euo pipefail

VM=battlecode-dev
ZONE=us-west1-b
PROJECT=tvanbelle-vibecode
REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SSHO=(-i "$HOME/.ssh/google_compute_engine" -o StrictHostKeyChecking=no
      -o UserKnownHostsFile=/dev/null -o ConnectTimeout=20 -o LogLevel=ERROR)
USER_NAME="${BC_SSH_USER:-$(whoami)}"

[ "$#" -ge 1 ] || { echo "usage: $0 <replay.bc26> [--from R] [--to R]" >&2; exit 1; }
REPLAY="$1"; shift
[ -f "$REPLAY" ] || { echo "no such file: $REPLAY" >&2; exit 1; }

state=$(gcloud compute instances describe "$VM" --zone="$ZONE" --project="$PROJECT" --format='value(status)' 2>/dev/null || true)
[ "$state" = RUNNING ] || { echo "starting VM ..." >&2; gcloud compute instances start "$VM" --zone="$ZONE" --project="$PROJECT" >/dev/null; }
IP=$(gcloud compute instances describe "$VM" --zone="$ZONE" --project="$PROJECT" --format='value(networkInterfaces[0].accessConfigs[0].natIP)')
RVM="$USER_NAME@$IP"
for _ in $(seq 1 30); do ssh "${SSHO[@]}" "$RVM" true 2>/dev/null && break; sleep 8; done

ssh "${SSHO[@]}" "$RVM" 'mkdir -p ~/replaydump'
scp "${SSHO[@]}" "$REPO/tools/replaydump/ReplayDump.java" "$RVM:replaydump/" >/dev/null
scp "${SSHO[@]}" "$REPLAY" "$RVM:replaydump/in.bc26" >/dev/null

ssh "${SSHO[@]}" "$RVM" "
  export JAVA_HOME=\$HOME/jdk21 PATH=\$HOME/jdk21/bin:\$PATH
  BC_JAR=\$(find ~/.gradle -name 'battlecode26-java-*.jar' | sort -V | tail -1)
  cd ~/replaydump
  javac -d . -classpath \"\$BC_JAR\" ReplayDump.java
  java -classpath \".:\$BC_JAR\" com.google.flatbuffers.ReplayDump in.bc26 $*
"
