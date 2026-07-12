#!/bin/sh
# Sync MMAI engine sources upstream (react_mmai/MMAIWASM)
# into dsg_src/mmai/engine/, stamping file DO-NOT-EDIT header
# records upstream commit. Precedent: pentelive-android syncs same files.
# main.cpp (the sidecar shim) NOT synced -- lives only in repo.
set -eu

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
UPSTREAM="$SCRIPT_DIR/../../../react_mmai/MMAIWASM"

if [ ! -f "$UPSTREAM/Ai.cpp" ]; then
  echo "ERROR: upstream not found $UPSTREAM" >&2
  echo "Expected react_mmai checkout sibling repo." >&2
  exit 1
fi

HASH="$(git -C "$UPSTREAM" rev-parse --short HEAD)"
mkdir -p "$SCRIPT_DIR/engine"

for f in Ai.cpp Ai.h CPoint.cpp CPoint.h; do
  {
    printf '// Synced react_mmai/MMAIWASM at commit %s.\n' "$HASH"
    printf '// DO NOT EDIT HERE — edit upstream & re-sync\n'
    printf '// via dsg_src/mmai/sync_from_upstream.sh.\n'
    printf '//\n'
    cat "$UPSTREAM/$f"
  } > "$SCRIPT_DIR/engine/$f"
  echo "synced engine/$f (upstream $HASH)"
done
