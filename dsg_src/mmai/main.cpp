// mmai_player: stdin/stdout sidecar around the MMAI C++ engine (CAi).
// Spec: docs/superpowers/specs/2026-07-12-mmai-sidecar-ai-player-design.md §5.
//
// Protocol (one line each way, \n-terminated):
//   MOVE <game> <level> <n> <m1> ... <mn>  ->  OK <move>  |  ERR <message>
//   QUIT                                   ->  (no reply, exit 0)
//   EOF on stdin                           ->  exit 0 (orphan safety)
//   anything else / malformed              ->  ERR <message>, stay alive
//
// <move> reply: 0-360 flat 19x19 index (m = y*19 + x) for every variant
// except Connect6 (13/14), which returns the whole two-stone turn packed
// base 362 exactly as CAi::getMove does: m1*362 + m2, m2 == 361 meaning a
// single-stone turn (opening).
//
// argv[1] = directory holding pente.tbl / pente.scs / opngbk.pen.
// This file is NOT synced from upstream; only engine/ is.
#include "engine/Ai.h"
#include <cstdio>
#include <iostream>
#include <sstream>
#include <string>
#include <vector>

int main(int argc, char **argv) {
    if (argc != 2) {
        std::fprintf(stderr, "usage: mmai_player <dataFilesDir>\n");
        return 2;
    }
    const std::string dataDir = argv[1];
    setvbuf(stdout, nullptr, _IOLBF, 0); // line-flushed replies

    CAi *ai = nullptr;
    int cachedGame = -1, cachedLevel = -1;

    std::string line;
    while (std::getline(std::cin, line)) {
        std::istringstream in(line);
        std::string cmd;
        in >> cmd;
        if (cmd == "QUIT") break;
        if (cmd != "MOVE") {
            std::printf("ERR unknown command: %s\n", cmd.c_str());
            continue;
        }
        int game, level, n;
        if (!(in >> game >> level >> n) || game < 1 || n < 0 || n > 361) {
            std::printf("ERR malformed MOVE header\n");
            continue;
        }
        std::vector<int> moves(n > 0 ? n : 1); // .data() must be non-null even for n==0
        bool bad = false;
        for (int i = 0; i < n; i++) {
            if (!(in >> moves[i]) || moves[i] < 0 || moves[i] > 360) { bad = true; break; }
        }
        if (bad) {
            std::printf("ERR malformed move list\n");
            continue;
        }

        // Even ids are Speed twins with identical board rules: normalize to
        // id-1 before constructing CAi (spec §5.2).
        if (game > 1 && game % 2 == 0) game -= 1;

        // Persistent CAi, rebuilt only on (game, level) change (Android
        // AiWrapper precedent): getMove self-resets and replays the full
        // list, so this is purely a construction-cost optimization.
        if (ai == nullptr || game != cachedGame || level != cachedLevel) {
            delete ai;
            ai = new CAi(game, level, true /*openingBook*/, dataDir.c_str());
            cachedGame = game;
            cachedLevel = level;
        }
        if (!ai->ok()) {
            std::printf("ERR data file load failure under %s\n", dataDir.c_str());
            delete ai; // retry construction on the next MOVE
            ai = nullptr;
            cachedGame = cachedLevel = -1;
            continue;
        }
        int move = ai->getMove(moves.data(), n);
        if (move < 0) {
            std::printf("ERR engine returned %d\n", move);
            continue;
        }
        std::printf("OK %d\n", move);
    }
    delete ai;
    return 0;
}
