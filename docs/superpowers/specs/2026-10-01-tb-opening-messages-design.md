# Turn-based opening messages — design

Date: 2026-10-01. Owner-approved direction: fix the storage and display of messages in the opening. Do not block messages.

## Problem

A turn-based (TB) message is tied to a `move_num` in `tb_message`, but only stone placements are stored as moves. Opening decisions are not stored as moves. These are: D-Pente/DK-Pente swap, swap2 swap/pass/no-swap, renju take-over, 10 fifth-move offers, and offer selection. So messages that come with them are mishandled:

- **Dropped:** renju TAKE_OVER and BRANCH_B (offers). `MoveServlet` stores a message only when a stone was stored.
- **Filed under the wrong move:**
  - The swap2 start message goes to move 4 while only 3 stones exist.
  - The renju SELECT message goes to move 5 (black's stone), not 6 (white's own stone).
- **Shared move numbers:** several opening messages share one move_num. Clients show only one message per move_num: the web shows the first match (`indexOf`), the apps the last (dictionary overwrite).
- **Wrong sender:**
  - Web and iOS label the sender from move-number parity. A decision message is written by the player who did NOT place that stone.
  - Android shows no sender at all.

## Decisions

1. **Write side (server):** every message in the D-Pente, swap2 and renju opening branches of `MoveServlet` is stored once, after the action. move_num = number of stones on the board after the action. seq_nbr = 1 + the highest seq_nbr already at that move_num.
   - The per-branch stores are removed.
   - Plain, Connect6 and Go branches are unchanged. They store before the move so the win email finds the winning move's message. Openings cannot end the game.
2. **Read side (server):** one helper, `org.pente.turnBased.TBMessageThread`, groups messages by move_num into one entry per move.
   - **Sort order:** move_num, then seq_nbr, then date.
   - **Author:** each entry carries an author seat. `1`/`2` = current seat of the author pid. `0` = the text already names its authors.
   - **When an entry is prefixed and gets seat `0`:** when it holds more than one message, OR when the game is an opening variant (TB D-Pente, DK-Pente, Swap2-Pente, Swap2-Keryo, Renju) and the author seat differs from the parity seat. Parity seat: odd move_num → seat 1, even → seat 2.
   - **Prefix format:** each part is prefixed `"<name>: "` (web: `"<b><name></b>: "`). Parts are joined by a per-consumer separator.
   - **Normal games:** output is unchanged except where messages collide.
3. **Consumers switched to the helper:**
   - Mobile JSON feed (`GameResponse.EncodedMessages.from`): separator `"\n"`. Adds a new field `messageAuthors`, comma-separated seats aligned with `messageNums`.
   - `tb/mobileGame.jsp`, `viewLiveGameMobile.jsp`: separator `"<br>"`. A new JS array `messageAuthors` holds the author name per entry, or `""` when the text already names the author.
   - Legacy text feed `mobile/game.jsp` (app builds from before 2026-03): separator `" | "`.
4. **iOS:** reads `messageAuthors`.
   - Seat `0` → show the text with no label.
   - `1`/`2` → "me" if the seat is mine, otherwise the opponent's name.
   - Field missing (old server) → today's parity logic.
5. **Out of scope:**
   - **Android:** no change. It shows text only, so the server name prefix is enough.
   - **Blocking messages.**
   - **Old data:** messages never written, or already overwritten, cannot be recovered.
   - **`undoReply.jsp` / `cancelReply.jsp`:** they render no messages.
   - **`viewLiveGame.jsp`, `tb/game.jsp`, `finalGo.jsp`, `deadGo.jsp`.**
   - **Renju opening moves sent by old clients without `renjuAction`:** these use the plain branch, which already behaves as today.

## Resulting move numbers

| Action | move_num |
|---|---|
| D-Pente start (4 stones) / decline / swap+stone 5 | 4 / 4 / 5 |
| Swap2 start (3 stones) | 3 |
| Swap2 at 3: swap / no-swap+stone 4 / pass+2 stones | 3 / 4 / 5 |
| Swap2 at 5: swap+stone 6 / no-swap | 6 / 5 |
| Renju TAKE_OVER / PLACE / BRANCH_A / BRANCH_B / SELECT | n / n+1 / 5 / 4 / 6 |

No player writes twice at one move_num during an opening. This matters under the legacy key `(gid, move_num, pid)`, which uses `ON DUPLICATE KEY UPDATE`. The full walk-through is in the research notes.

## Known limits

- iOS builds older than the change still put a parity "me"/opponent label in front of a prefixed entry. The name inside the text is correct.
- Messages kept from undone moves now show merged with the replayed move's message instead of hiding it.
- **Normal games change in one place.** When the inviter and invitee both wrote a message, the two messages at move 0 now show merged with names. Before, web showed only the first and the apps only the last. Leftover messages from undone moves merge the same way.
- **Deploy order.** Classes must go live before the JSPs. The new JSPs use `TBMessageThread` and the 4-arg `EncodedMessages.from`, so a JSP that goes live first returns HTTP 500 until the classes are loaded. The other direction is safe: the old 2-arg `from` stays for JSPs that still call it.
- **Resign messages.** `ResignServlet` stores a resign message at the current move number with seq 2, so it now shows merged, with names, alongside the opponent's last-move message.
