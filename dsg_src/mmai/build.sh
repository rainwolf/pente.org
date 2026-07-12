#!/bin/sh
# Local dev/test build of the mmai sidecar binary. The Dockerfile build stage
# uses the same three translation units and flags (spec section 7.2).
set -eu
cd "$(dirname "$0")"
g++ -O2 -std=c++11 engine/Ai.cpp engine/CPoint.cpp main.cpp -o mmai_player
echo "built $(pwd)/mmai_player"
