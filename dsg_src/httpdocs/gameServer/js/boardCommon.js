// Shared board-viewer helpers. Single source for board color + replay dispatch.
// Depends on globals defined elsewhere and loaded FIRST:
//   - GAME            : game-id constants, emitted by gameConstants.jspf
//   - color vars      : penteColor, keryoPenteColor, ... swap2KeryoColor  (tb/gameScript.js)
//   - replay*Game     : replayPenteGame, ... replayOPenteGame             (tb/gameScript.js)
//   - whiteCaptures / blackCaptures / game : page-level globals on each viewer page
// Unknown game ids throw on purpose, so a mis-set id fails loudly in the console.

function getBoardColor(game) {
   switch (game) {
      case GAME.PENTE: case GAME.SPEED_PENTE: case GAME.TB_PENTE:
         return penteColor;
      case GAME.KERYO: case GAME.SPEED_KERYO: case GAME.TB_KERYO:
         return keryoPenteColor;
      case GAME.GOMOKU: case GAME.SPEED_GOMOKU: case GAME.TB_GOMOKU:
         return gomokuColor;
      case GAME.DPENTE: case GAME.SPEED_DPENTE: case GAME.TB_DPENTE:
         return dPenteColor;
      case GAME.GPENTE: case GAME.SPEED_GPENTE: case GAME.TB_GPENTE:
         return gPenteColor;
      case GAME.POOF_PENTE: case GAME.SPEED_POOF_PENTE: case GAME.TB_POOF_PENTE:
         return poofPenteColor;
      case GAME.CONNECT6: case GAME.SPEED_CONNECT6: case GAME.TB_CONNECT6:
         return connect6Color;
      case GAME.BOAT_PENTE: case GAME.SPEED_BOAT_PENTE: case GAME.TB_BOAT_PENTE:
         return boatPenteColor;
      case GAME.DKERYO: case GAME.SPEED_DKERYO: case GAME.TB_DKERYO:
         return dkeryoPenteColor;
      case GAME.GO:  case GAME.SPEED_GO:
      case GAME.GO9: case GAME.SPEED_GO9:
      case GAME.GO13: case GAME.SPEED_GO13:
      case GAME.TB_GO: case GAME.TB_GO9: case GAME.TB_GO13:
         return goColor;
      case GAME.OPENTE: case GAME.SPEED_OPENTE: case GAME.TB_OPENTE:
         return oPenteColor;
      case GAME.SWAP2PENTE: case GAME.SPEED_SWAP2PENTE: case GAME.TB_SWAP2PENTE:
         return swap2PenteColor;
      case GAME.SWAP2KERYO: case GAME.SPEED_SWAP2KERYO: case GAME.TB_SWAP2KERYO:
         return swap2KeryoColor;
      default:
         throw new Error("getBoardColor: unknown game id " + game);
   }
}

// Shared replay dispatch: resets capture counts and runs the moves with the
// game-type-specific replay function. Each viewer page wraps this in its own
// replayGame() -- several pages append per-move message-box rendering after the
// call. Reads the page globals `game`, `whiteCaptures`, `blackCaptures`.
function replayMoves(abstractBoard, movesList, until) {
   whiteCaptures = 0;
   blackCaptures = 0;
   switch (game) {
      case GAME.PENTE: case GAME.SPEED_PENTE: case GAME.TB_PENTE:
      case GAME.DPENTE: case GAME.SPEED_DPENTE: case GAME.TB_DPENTE:
      case GAME.BOAT_PENTE: case GAME.SPEED_BOAT_PENTE: case GAME.TB_BOAT_PENTE:
      case GAME.SWAP2PENTE: case GAME.SPEED_SWAP2PENTE: case GAME.TB_SWAP2PENTE:
         replayPenteGame(abstractBoard, movesList, until); break;
      case GAME.KERYO: case GAME.SPEED_KERYO: case GAME.TB_KERYO:
      case GAME.DKERYO: case GAME.SPEED_DKERYO: case GAME.TB_DKERYO:
      case GAME.SWAP2KERYO: case GAME.SPEED_SWAP2KERYO: case GAME.TB_SWAP2KERYO:
         replayKeryoPenteGame(abstractBoard, movesList, until); break;
      case GAME.GOMOKU: case GAME.SPEED_GOMOKU: case GAME.TB_GOMOKU:
         replayGomokuGame(abstractBoard, movesList, until); break;
      case GAME.GPENTE: case GAME.SPEED_GPENTE: case GAME.TB_GPENTE:
         replayGPenteGame(abstractBoard, movesList, until); break;
      case GAME.POOF_PENTE: case GAME.SPEED_POOF_PENTE: case GAME.TB_POOF_PENTE:
         replayPoofPenteGame(abstractBoard, movesList, until); break;
      case GAME.CONNECT6: case GAME.SPEED_CONNECT6: case GAME.TB_CONNECT6:
         replayConnect6Game(abstractBoard, movesList, until); break;
      case GAME.GO:  case GAME.SPEED_GO:
      case GAME.GO9: case GAME.SPEED_GO9:
      case GAME.GO13: case GAME.SPEED_GO13:
      case GAME.TB_GO: case GAME.TB_GO9: case GAME.TB_GO13:
         replayGoGame(abstractBoard, movesList, until); break;
      case GAME.OPENTE: case GAME.SPEED_OPENTE: case GAME.TB_OPENTE:
         replayOPenteGame(abstractBoard, movesList, until); break;
      default:
         throw new Error("replayMoves: unknown game id " + game);
   }
}
