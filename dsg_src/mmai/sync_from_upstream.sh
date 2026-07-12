#!/bin/sh
# Sync the MMAI engine sources from the canonical upstream (react_mmai/MMAIWASM)
# into dsg_src/mmai/engine/, stamping each file with a DO-NOT-EDIT header that
# records the upstream commit. Precedent: pentelive-android syncs the same files.
# main.cpp (the sidecar shim) is NOT synced -- it lives only in this repo.
set -eu

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
UPSTREAM="$SCRIPT_DIR/../../../react_mmai/MMAIWASM"

if [ ! -f "$UPSTREAM/Ai.cpp" ]; then
    echo "ERROR: upstream not found at $UPSTREAM" >&2
    echo "Expected a react_mmai checkout as a sibling of this repo." >&2
    exit 1
fi

HASH="$(git -C "$UPSTREAM" rev-parse --short HEAD)"
mkdir -p "$SCRIPT_DIR/engine"

for f in Ai.cpp Ai.h CPoint.cpp CPoint.h; do
    {
        printf '// Synced from react_mmai/MMAIWASM at commit %s.\n' "$HASH"
        printf '// DO NOT EDIT HERE — edit upstream & re-sync\n'
        printf '// via dsg_src/mmai/sync_from_upstream.sh.\n'
        printf '//\n'
        cat "$UPSTREAM/$f"
    } > "$SCRIPT_DIR/engine/$f"
    echo "synced engine/$f (upstream $HASH)"
done
