# Renju Turn-Based Persistence Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Persist Renju Taraguchi-10 opening state (swap/branch decisions as a base-3 word + the 10 offered 5th moves) through `TBGame`, `MySQLTBGameStorer`, `CacheTBStorer`, and `MoveServlet`, and reconstruct a fully-correct `RenjuState` on load.

**Architecture:** A pure ternary codec (`RenjuOpeningState`) encodes the six Taraguchi decisions into one `smallint`. `RenjuState.reconstruct(...)` replays the move list *and* re-applies the decoded decisions so engine turn/legality is correct mid-opening. `TBGame` gains `renjuSwaps`/`renjuOffers` fields (ride the Redis aggregate); `MySQLTBGameStorer` mirrors them to `tb_game.renju_swaps`/`renju_offers` via `dPenteSwap`-style update methods (swapping pids on a swap); `CacheTBStorer` updates the aggregate + writes through; `MoveServlet` routes Renju opening actions. Schema for both `tb_game` and `pente_game` is added now; the live `pente_game` write path is deferred.

**Tech Stack:** Java (Tomcat backend), JUnit 3 (`junit.framework.TestCase`), Ant build, MySQL, Redis.

**Spec:** `docs/superpowers/specs/2026-06-13-renju-tb-persistence-design.md`

## Build & Test Commands

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home   # required; default jenv java is broken
./justCompile                                            # sync dsg_src/java -> deploy + compile production
ant test-one -Dtest=org.pente.game.test.<TestClass>      # run a JUnit 3 test class
```

Run from `/Users/waliedothman/mariposa/coding/pente.org-project/pente.org`.

**Testing reality:** `RenjuOpeningState` and `RenjuState.reconstruct` are pure and fully unit-tested here. `MySQLTBGameStorer`/`CacheTBStorer`/`MoveServlet` need a live MySQL + servlet container (the repo already excludes `**/turnBased/test/TBStorerTest.java` from the build for this reason), so those tasks are verified by **clean compile** (`./justCompile`) plus the reconstruction tests that exercise the identical decode/replay logic. Manual DB/integration verification is a follow-up, not part of these tasks.

## File Structure

- Create: `dsg_src/java/org/pente/game/RenjuOpeningState.java` — pure ternary codec + offer byte codec.
- Modify: `dsg_src/java/org/pente/game/RenjuState.java` — add `static reconstruct(...)`.
- Modify: `dsg_src/java/org/pente/turnBased/TBGame.java` — `renjuSwaps`/`renjuOffers` fields, accessors, `renjuSwap`/`renjuBranch` mutators.
- Modify: `dsg_src/sql/schema.sql` — `tb_game`/`pente_game` columns + `pente_renju_offer` table.
- Modify: `dsg_src/java/org/pente/turnBased/TBGameStorer.java` — interface methods.
- Modify: `dsg_src/java/org/pente/turnBased/MySQLTBGameStorer.java` — read/write columns + `renjuSwap`/`renjuBranch`/`renjuOffers`.
- Modify: `dsg_src/java/org/pente/turnBased/CacheTBStorer.java` — overrides + Renju reconstruction in move validation.
- Modify: `dsg_src/java/org/pente/turnBased/web/MoveServlet.java` — opening-action routing.
- Create: `dsg_src/java/org/pente/game/test/RenjuOpeningStateTest.java`
- Create: `dsg_src/java/org/pente/game/test/RenjuReconstructTest.java`

---

## Task 1: RenjuOpeningState — ternary + offer codec

**Files:**
- Create: `dsg_src/java/org/pente/game/RenjuOpeningState.java`
- Test: `dsg_src/java/org/pente/game/test/RenjuOpeningStateTest.java`

- [ ] **Step 1: Write the failing test**

Create `dsg_src/java/org/pente/game/test/RenjuOpeningStateTest.java`:

```java
package org.pente.game.test;

import junit.framework.*;
import org.pente.game.*;

public class RenjuOpeningStateTest extends TestCase {

    public static void main(String[] args) {
        junit.textui.TestRunner.main(new String[]{RenjuOpeningStateTest.class.getName()});
    }

    public static Test suite() {
        return new TestSuite(RenjuOpeningStateTest.class);
    }

    public RenjuOpeningStateTest(String name) {
        super(name);
    }

    public void testConstants() {
        assertEquals(0, RenjuOpeningState.PENDING);
        assertEquals(1, RenjuOpeningState.NO);
        assertEquals(2, RenjuOpeningState.YES);
    }

    public void testEncodeDigitWeights() {
        RenjuOpeningState s = new RenjuOpeningState();
        s.swap1 = 2;
        assertEquals(2, s.encode());            // 2 * 3^0
        s = new RenjuOpeningState();
        s.swap5 = 1;
        assertEquals(243, s.encode());          // 1 * 3^5
        s = new RenjuOpeningState();
        s.branch = 2;
        assertEquals(162, s.encode());          // 2 * 3^4 = 162
    }

    public void testRoundTripExhaustive() {
        for (int packed = 0; packed <= 728; packed++) {
            RenjuOpeningState s = RenjuOpeningState.decode(packed);
            assertEquals(packed, s.encode());
        }
    }

    public void testDecodeExtractsDigits() {
        // swap1=1, swap2=2, swap3=0, swap4=1, branch=2, swap5=1
        int packed = 1 + 2 * 3 + 0 * 9 + 1 * 27 + 2 * 81 + 1 * 243;
        RenjuOpeningState s = RenjuOpeningState.decode(packed);
        assertEquals(1, s.swap1);
        assertEquals(2, s.swap2);
        assertEquals(0, s.swap3);
        assertEquals(1, s.swap4);
        assertEquals(2, s.branch);
        assertEquals(1, s.swap5);
    }

    public void testOfferByteRoundTrip() {
        int[] offers = {0, 7, 112, 224, 113, 99, 5, 200, 1, 150};
        byte[] bytes = RenjuOpeningState.encodeOffers(offers);
        assertEquals(10, bytes.length);
        int[] back = RenjuOpeningState.decodeOffers(bytes);
        assertEquals(offers.length, back.length);
        for (int i = 0; i < offers.length; i++) {
            assertEquals(offers[i], back[i]);   // 0..224 survives the unsigned-byte round trip
        }
    }

    public void testDecodeOffersNullEmpty() {
        assertNull(RenjuOpeningState.decodeOffers(null));
        assertEquals(0, RenjuOpeningState.decodeOffers(new byte[0]).length);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
ant test-one -Dtest=org.pente.game.test.RenjuOpeningStateTest
```
Expected: FAIL — `RenjuOpeningState` does not exist.

- [ ] **Step 3: Write the implementation**

Create `dsg_src/java/org/pente/game/RenjuOpeningState.java`:

```java
package org.pente.game;

/**
 * Codec for Renju Taraguchi-10 opening state.
 *
 * Six base-3 digits packed into one int (0..728, fits smallint unsigned):
 *   d0 swap after move 1, d1 after move 2, d2 after move 3, d3 after move 4,
 *   d4 branch choice, d5 swap after move 5 (Branch A only).
 * Each digit: 0 = pending, 1 = no (swap declined / branch A), 2 = yes (swap / branch B).
 *
 * Also codes the 10 offered 5th moves as a byte array (each 15x15 position 0..224
 * fits one unsigned byte).
 */
public class RenjuOpeningState {

    public static final int PENDING = 0;
    public static final int NO = 1;
    public static final int YES = 2;

    public int swap1;
    public int swap2;
    public int swap3;
    public int swap4;
    public int branch;
    public int swap5;

    public int encode() {
        return swap1
                + swap2 * 3
                + swap3 * 9
                + swap4 * 27
                + branch * 81
                + swap5 * 243;
    }

    public static RenjuOpeningState decode(int packed) {
        RenjuOpeningState s = new RenjuOpeningState();
        s.swap1 = packed % 3; packed /= 3;
        s.swap2 = packed % 3; packed /= 3;
        s.swap3 = packed % 3; packed /= 3;
        s.swap4 = packed % 3; packed /= 3;
        s.branch = packed % 3; packed /= 3;
        s.swap5 = packed % 3;
        return s;
    }

    /** Pack offered positions (0..224 each) into one byte each. */
    public static byte[] encodeOffers(int[] offers) {
        if (offers == null) {
            return null;
        }
        byte[] bytes = new byte[offers.length];
        for (int i = 0; i < offers.length; i++) {
            bytes[i] = (byte) (offers[i] & 0xFF);
        }
        return bytes;
    }

    /** Unpack offered positions (unsigned bytes back to 0..224). */
    public static int[] decodeOffers(byte[] bytes) {
        if (bytes == null) {
            return null;
        }
        int[] offers = new int[bytes.length];
        for (int i = 0; i < bytes.length; i++) {
            offers[i] = bytes[i] & 0xFF;
        }
        return offers;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

```bash
./justCompile && ant test-one -Dtest=org.pente.game.test.RenjuOpeningStateTest
```
Expected: PASS (OK, 6 tests).

- [ ] **Step 5: Commit**

```bash
git add dsg_src/java/org/pente/game/RenjuOpeningState.java \
        dsg_src/java/org/pente/game/test/RenjuOpeningStateTest.java
git commit -m "feat(renju): ternary opening-state + offer codec

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 2: RenjuState.reconstruct — rehydrate engine from persisted state

**Files:**
- Modify: `dsg_src/java/org/pente/game/RenjuState.java`
- Test: `dsg_src/java/org/pente/game/test/RenjuReconstructTest.java`

`RenjuState.getInstance(MoveData)` replays moves only (decisions lost). `reconstruct` replays moves AND re-applies the decoded decisions in protocol order, so a reloaded TB game has correct turn/legality even mid-opening.

- [ ] **Step 1: Write the failing test**

Create `dsg_src/java/org/pente/game/test/RenjuReconstructTest.java`:

```java
package org.pente.game.test;

import junit.framework.*;
import org.pente.game.*;

public class RenjuReconstructTest extends TestCase {

    public static void main(String[] args) {
        junit.textui.TestRunner.main(new String[]{RenjuReconstructTest.class.getName()});
    }

    public static Test suite() {
        return new TestSuite(RenjuReconstructTest.class);
    }

    public RenjuReconstructTest(String name) {
        super(name);
    }

    private int xy(int x, int y) {
        return x + y * 15; // 15x15 Renju board move encoding
    }

    // Build a SimpleGridState move list (MoveData) from raw moves.
    private SimpleGridState moves(int... mv) {
        SimpleGridState s = new SimpleGridState(15, 15);
        for (int m : mv) s.addMove(m);
        return s;
    }

    // Reference: play the same opening live and compare board + pending state.
    public void testReconstructMidOpening_pendingSwapAfterMove1() {
        SimpleGridState md = moves(xy(7, 7)); // only move 1 played
        RenjuOpeningState st = new RenjuOpeningState(); // all pending
        RenjuState s = RenjuState.reconstruct(md, st.encode(), null);

        assertEquals(1, s.getNumMoves());
        assertTrue(s.isAwaitingSwapDecision()); // swap after move 1 still pending
        assertTrue(!s.isOpeningComplete());
    }

    public void testReconstructBranchA_full() {
        // moves: 1..4 opening, move5 (9x9), move6 anywhere
        SimpleGridState md = moves(
                xy(7, 7), xy(8, 8), xy(9, 7), xy(6, 8), xy(11, 7), xy(0, 0));
        RenjuOpeningState st = new RenjuOpeningState();
        st.swap1 = RenjuOpeningState.NO;
        st.swap2 = RenjuOpeningState.NO;
        st.swap3 = RenjuOpeningState.NO;
        st.swap4 = RenjuOpeningState.NO;
        st.branch = RenjuOpeningState.NO;  // Branch A
        st.swap5 = RenjuOpeningState.NO;
        RenjuState s = RenjuState.reconstruct(md, st.encode(), null);

        assertEquals(6, s.getNumMoves());
        assertTrue(s.isOpeningComplete());
    }

    public void testReconstructBranchB_full() {
        int[] offers = {xy(0,0), xy(0,2), xy(0,4), xy(0,6), xy(0,8),
                        xy(0,10), xy(0,12), xy(0,14), xy(2,0), xy(4,0)};
        // moves: 1..4 opening, then the selected 5th (offers[3]), then move6
        SimpleGridState md = moves(
                xy(7, 7), xy(8, 8), xy(9, 7), xy(6, 8), offers[3], xy(14, 14));
        RenjuOpeningState st = new RenjuOpeningState();
        st.swap1 = RenjuOpeningState.NO;
        st.swap2 = RenjuOpeningState.NO;
        st.swap3 = RenjuOpeningState.NO;
        st.swap4 = RenjuOpeningState.NO;
        st.branch = RenjuOpeningState.YES; // Branch B
        RenjuState s = RenjuState.reconstruct(md, st.encode(), offers);

        assertEquals(6, s.getNumMoves());
        assertTrue(s.isOpeningComplete());
    }

    public void testReconstructBranchB_awaitingSelection() {
        int[] offers = {xy(0,0), xy(0,2), xy(0,4), xy(0,6), xy(0,8),
                        xy(0,10), xy(0,12), xy(0,14), xy(2,0), xy(4,0)};
        SimpleGridState md = moves(xy(7, 7), xy(8, 8), xy(9, 7), xy(6, 8)); // 4 moves
        RenjuOpeningState st = new RenjuOpeningState();
        st.swap1 = RenjuOpeningState.NO;
        st.swap2 = RenjuOpeningState.NO;
        st.swap3 = RenjuOpeningState.NO;
        st.swap4 = RenjuOpeningState.NO;
        st.branch = RenjuOpeningState.YES;
        RenjuState s = RenjuState.reconstruct(md, st.encode(), offers);

        assertEquals(4, s.getNumMoves());
        assertTrue(s.isAwaitingFifthSelection());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
ant test-one -Dtest=org.pente.game.test.RenjuReconstructTest
```
Expected: FAIL — `RenjuState.reconstruct` not defined.

- [ ] **Step 3: Add the reconstruct method**

In `dsg_src/java/org/pente/game/RenjuState.java`, add this static method (place it after the `getInstance(MoveData)` method):

```java
    /**
     * Rebuild a RenjuState from a persisted move list + opening state.
     * Replays moves AND re-applies swap/branch/offer/select decisions in
     * protocol order, stopping wherever the persisted state is still pending,
     * so the result reports the correct pending decision and current player.
     *
     * @param moves   the played moves (opening moves, then the selected 5th, then the rest)
     * @param renjuSwapsPacked the base-3 packed opening word (see RenjuOpeningState)
     * @param offers  the 10 offered 5th moves (Branch B), or null
     */
    public static RenjuState reconstruct(MoveData moves, int renjuSwapsPacked, int[] offers) {
        RenjuState s = new RenjuState(15, 15);
        RenjuOpeningState st = RenjuOpeningState.decode(renjuSwapsPacked);
        int n = moves.getNumMoves();
        int idx = 0;

        // moves 1-4 each followed by a swap window
        int[] swaps = {st.swap1, st.swap2, st.swap3, st.swap4};
        for (int k = 0; k < 4; k++) {
            if (idx >= n) return s;
            s.addMove(moves.getMove(idx++));
            if (swaps[k] == RenjuOpeningState.PENDING) return s;
            s.renjuSwapDecisionMade(swaps[k] == RenjuOpeningState.YES);
        }

        // branch choice
        if (st.branch == RenjuOpeningState.PENDING) return s;
        boolean tenOffer = st.branch == RenjuOpeningState.YES;
        s.chooseBranch(tenOffer);

        if (!tenOffer) {
            // Branch A: move 5, its swap window, move 6, then the rest
            if (idx >= n) return s;
            s.addMove(moves.getMove(idx++));            // move 5
            if (st.swap5 == RenjuOpeningState.PENDING) return s;
            s.renjuSwapDecisionMade(st.swap5 == RenjuOpeningState.YES);
            while (idx < n) {
                s.addMove(moves.getMove(idx++));
            }
            return s;
        }

        // Branch B: re-offer the candidates, then the selection commits move 5
        if (offers != null) {
            for (int off : offers) {
                s.offerFifthMove(off);
            }
        }
        if (offers != null && offers.length == 10 && idx < n) {
            s.selectFifthMove(moves.getMove(idx++)); // selectFifthMove addMoves the chosen move
            while (idx < n) {
                s.addMove(moves.getMove(idx++));
            }
        }
        return s;
    }
```

- [ ] **Step 4: Run test to verify it passes**

```bash
./justCompile && ant test-one -Dtest=org.pente.game.test.RenjuReconstructTest
```
Expected: PASS (OK, 4 tests).

- [ ] **Step 5: Run the existing Renju suites to confirm no regression**

```bash
ant test-one -Dtest=org.pente.game.test.RenjuStateTest \
  && ant test-one -Dtest=org.pente.game.test.RenjuFactoryTest
```
Expected: both PASS.

- [ ] **Step 6: Commit**

```bash
git add dsg_src/java/org/pente/game/RenjuState.java \
        dsg_src/java/org/pente/game/test/RenjuReconstructTest.java
git commit -m "feat(renju): reconstruct RenjuState from persisted opening state

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 3: TBGame — renjuSwaps / renjuOffers fields + mutators

**Files:**
- Modify: `dsg_src/java/org/pente/turnBased/TBGame.java`

No new test (the storer/servlet layers consume these; behavior is covered by the codec/reconstruct tests). Verified by compile.

- [ ] **Step 1: Add fields**

In `dsg_src/java/org/pente/turnBased/TBGame.java`, next to the existing `private boolean swap2Pass = false;` field, add:

```java
    private int renjuSwaps = 0;       // RenjuOpeningState packed word (0 = fresh / non-Renju)
    private int[] renjuOffers = null; // Branch B: the 10 offered 5th moves, or null
```

- [ ] **Step 2: Add accessors + mutators**

In `TBGame.java`, after the existing `setSwap2Pass(...)` method, add (import `org.pente.game.RenjuOpeningState` at the top of the file):

```java
    public int getRenjuSwaps() {
        return renjuSwaps;
    }

    public void setRenjuSwaps(int renjuSwaps) {
        this.renjuSwaps = renjuSwaps;
    }

    public int[] getRenjuOffers() {
        return renjuOffers;
    }

    public void setRenjuOffers(int[] renjuOffers) {
        this.renjuOffers = renjuOffers;
    }

    /**
     * Resolve the currently-pending Taraguchi swap window (identified by the
     * number of stones played) and, on a swap, hand the just-played side to the
     * other seat by swapping pids -- mirrors dPenteSwap(boolean).
     */
    public void renjuSwap(boolean swap) {
        org.pente.game.RenjuOpeningState st =
                org.pente.game.RenjuOpeningState.decode(renjuSwaps);
        int v = swap ? org.pente.game.RenjuOpeningState.YES
                     : org.pente.game.RenjuOpeningState.NO;
        int n = getNumMoves();
        if (n == 1) st.swap1 = v;
        else if (n == 2) st.swap2 = v;
        else if (n == 3) st.swap3 = v;
        else if (n == 4) st.swap4 = v;
        else if (n == 5) st.swap5 = v;
        renjuSwaps = st.encode();

        if (swap) {
            long tmp = getPlayer1Pid();
            setPlayer1Pid(getPlayer2Pid());
            setPlayer2Pid(tmp);
        }
        lastMoveDate = new Date();
    }

    /** Record the post-move-4 branch choice (false = Branch A, true = Branch B / 10-offer). */
    public void renjuBranch(boolean tenOffer) {
        org.pente.game.RenjuOpeningState st =
                org.pente.game.RenjuOpeningState.decode(renjuSwaps);
        st.branch = tenOffer ? org.pente.game.RenjuOpeningState.YES
                             : org.pente.game.RenjuOpeningState.NO;
        renjuSwaps = st.encode();
        lastMoveDate = new Date();
    }
```

> Note: `getNumMoves()`, `getPlayer1Pid()`/`setPlayer1Pid()`, `getPlayer2Pid()`/`setPlayer2Pid()`, and the `lastMoveDate` field already exist on `TBGame` (used identically by `dPenteSwap`). `TBGame` is already `Serializable`, so the new fields persist into the Redis `TBSet` aggregate with no further change.

- [ ] **Step 3: Compile**

```bash
./justCompile
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add dsg_src/java/org/pente/turnBased/TBGame.java
git commit -m "feat(renju): TBGame opening-state fields + swap/branch mutators

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 4: Schema — tb_game / pente_game columns + pente_renju_offer

**Files:**
- Modify: `dsg_src/sql/schema.sql`

`schema.sql` is the canonical schema (regenerated from the live DB). Add the columns/table directly to the `CREATE TABLE` statements; the column adds are the migration applied to live/replica DBs.

- [ ] **Step 1: Add tb_game columns**

In `dsg_src/sql/schema.sql`, in the `CREATE TABLE \`tb_game\`` statement, after the `\`swap2pass\` tinyint(1) DEFAULT 0,` line, add:

```sql
  `renju_swaps` smallint(5) unsigned DEFAULT NULL,
  `renju_offers` varbinary(10) DEFAULT NULL,
```

- [ ] **Step 2: Add pente_game column**

In the `CREATE TABLE \`pente_game\`` statement, after the `\`swap2pass\` tinyint(1) DEFAULT 0,` line, add:

```sql
  `renju_swaps` smallint(5) unsigned DEFAULT NULL,
```

- [ ] **Step 3: Add the pente_renju_offer table**

In `dsg_src/sql/schema.sql`, after the `pente_game` table's closing `;`, add:

```sql
CREATE TABLE `pente_renju_offer` (
  `gid` bigint(20) unsigned NOT NULL DEFAULT 0,
  `site_id` smallint(5) unsigned NOT NULL DEFAULT 0,
  `offer_num` tinyint(3) unsigned NOT NULL DEFAULT 0,
  `move` smallint(5) unsigned NOT NULL DEFAULT 0,
  PRIMARY KEY (`gid`,`site_id`,`offer_num`)
) ENGINE=MyISAM DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci;
```

- [ ] **Step 4: Sanity-check the SQL parses (syntax only)**

```bash
grep -n "renju_swaps\|renju_offers\|pente_renju_offer" dsg_src/sql/schema.sql
```
Expected: 4 hits (tb_game x2, pente_game x1, table x1 — the table name line).

- [ ] **Step 5: Commit**

```bash
git add dsg_src/sql/schema.sql
git commit -m "feat(renju): schema for opening state (tb_game/pente_game + pente_renju_offer)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 5: TBGameStorer interface + MySQLTBGameStorer persistence

**Files:**
- Modify: `dsg_src/java/org/pente/turnBased/TBGameStorer.java`
- Modify: `dsg_src/java/org/pente/turnBased/MySQLTBGameStorer.java`

Verified by compile + Task 2 reconstruction tests (which exercise the decode/replay the storer feeds).

- [ ] **Step 1: Add interface methods**

In `dsg_src/java/org/pente/turnBased/TBGameStorer.java`, after the `swap2Pass(TBGame game)` declaration, add:

```java
    public void renjuSwap(TBGame game, boolean swap)
            throws TBStoreException;

    public void renjuBranch(TBGame game, boolean tenOffer)
            throws TBStoreException;

    public void renjuOffers(TBGame game)
            throws TBStoreException;
```

- [ ] **Step 2: Read the new columns in MySQLTBGameStorer**

In `dsg_src/java/org/pente/turnBased/MySQLTBGameStorer.java`, extend `TB_COLUMNS` (currently ends `... hiddenBy, swap2pass`):

```java
    private static final String TB_COLUMNS =
            "gid, state, p1_pid, p2_pid, creation_date, " +
                    "start_date, last_move_date, timeout_date, completion_date, " +
                    "game, event_id, round, section, days_per_move, rated, " +
                    "winner, dpente_state, dpente_swap, hiddenBy, swap2pass, " +
                    "renju_swaps, renju_offers";
```

And at the end of `fillGame(...)` (after `game.setSwap2Pass(result.getInt(r++) == 1);`), add:

```java
        game.setRenjuSwaps(result.getInt(r++));
        game.setRenjuOffers(org.pente.game.RenjuOpeningState.decodeOffers(result.getBytes(r++)));
```

> `result.getInt` returns 0 for a SQL NULL `renju_swaps`, which is the correct "fresh / non-Renju" value. `getBytes` returns null for NULL `renju_offers`, and `decodeOffers(null)` returns null.

- [ ] **Step 3: Add the renju update methods**

In `MySQLTBGameStorer.java`, after the `swap2Pass(TBGame g)` method, add:

```java
    public void renjuSwap(TBGame g, boolean swap) throws TBStoreException {

        log4j.debug("MySQLTBGameStorer.renjuSwap(" + g.getGid() + ", " + swap + ")");

        Connection con = null;
        PreparedStatement stmt = null;

        try {
            con = dbHandler.getConnection();
            stmt = con.prepareStatement(
                    "update tb_game " +
                            "set last_move_date = ?, " +
                            "timeout_date = ?, " +
                            "renju_swaps = ?, " +
                            "p1_pid = ?, " +
                            "p2_pid = ? " +
                            "where gid = ?");
            stmt.setTimestamp(1, new Timestamp(g.getLastMoveDate().getTime()));
            stmt.setTimestamp(2, new Timestamp(g.getTimeoutDate().getTime()));
            stmt.setInt(3, g.getRenjuSwaps());
            stmt.setLong(4, g.getPlayer1Pid());
            stmt.setLong(5, g.getPlayer2Pid());
            stmt.setLong(6, g.getGid());
            stmt.executeUpdate();

        } catch (SQLException se) {
            throw new TBStoreException(se);
        } finally {
            if (stmt != null) {
                try {
                    stmt.close();
                } catch (SQLException se) {
                }
            }
            if (con != null) {
                try {
                    dbHandler.freeConnection(con);
                } catch (SQLException se) {
                }
            }
        }
    }

    public void renjuBranch(TBGame g, boolean tenOffer) throws TBStoreException {

        log4j.debug("MySQLTBGameStorer.renjuBranch(" + g.getGid() + ", " + tenOffer + ")");

        Connection con = null;
        PreparedStatement stmt = null;

        try {
            con = dbHandler.getConnection();
            stmt = con.prepareStatement(
                    "update tb_game " +
                            "set last_move_date = ?, " +
                            "timeout_date = ?, " +
                            "renju_swaps = ? " +
                            "where gid = ?");
            stmt.setTimestamp(1, new Timestamp(g.getLastMoveDate().getTime()));
            stmt.setTimestamp(2, new Timestamp(g.getTimeoutDate().getTime()));
            stmt.setInt(3, g.getRenjuSwaps());
            stmt.setLong(4, g.getGid());
            stmt.executeUpdate();

        } catch (SQLException se) {
            throw new TBStoreException(se);
        } finally {
            if (stmt != null) {
                try {
                    stmt.close();
                } catch (SQLException se) {
                }
            }
            if (con != null) {
                try {
                    dbHandler.freeConnection(con);
                } catch (SQLException se) {
                }
            }
        }
    }

    public void renjuOffers(TBGame g) throws TBStoreException {

        log4j.debug("MySQLTBGameStorer.renjuOffers(" + g.getGid() + ")");

        Connection con = null;
        PreparedStatement stmt = null;

        try {
            con = dbHandler.getConnection();
            stmt = con.prepareStatement(
                    "update tb_game " +
                            "set last_move_date = ?, " +
                            "timeout_date = ?, " +
                            "renju_offers = ? " +
                            "where gid = ?");
            stmt.setTimestamp(1, new Timestamp(g.getLastMoveDate().getTime()));
            stmt.setTimestamp(2, new Timestamp(g.getTimeoutDate().getTime()));
            stmt.setBytes(3, org.pente.game.RenjuOpeningState.encodeOffers(g.getRenjuOffers()));
            stmt.setLong(4, g.getGid());
            stmt.executeUpdate();

        } catch (SQLException se) {
            throw new TBStoreException(se);
        } finally {
            if (stmt != null) {
                try {
                    stmt.close();
                } catch (SQLException se) {
                }
            }
            if (con != null) {
                try {
                    dbHandler.freeConnection(con);
                } catch (SQLException se) {
                }
            }
        }
    }
```

- [ ] **Step 4: Compile**

```bash
./justCompile
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add dsg_src/java/org/pente/turnBased/TBGameStorer.java \
        dsg_src/java/org/pente/turnBased/MySQLTBGameStorer.java
git commit -m "feat(renju): persist opening state in MySQLTBGameStorer

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 6: CacheTBStorer — overrides + Renju move validation

**Files:**
- Modify: `dsg_src/java/org/pente/turnBased/CacheTBStorer.java`

Verified by compile + Task 2 tests.

- [ ] **Step 1: Add the renju cache overrides**

In `dsg_src/java/org/pente/turnBased/CacheTBStorer.java`, after the `swap2Pass(TBGame g)` override, add (mirrors the `dPenteSwap`/`swap2Pass` cache pattern: load, mutate in-memory, recompute timeout, persist the set, write through):

```java
    public void renjuSwap(TBGame g, boolean swap) throws TBStoreException {
        log4j.debug("CacheTBStorer.renjuSwap(" + g.getGid() + ", " + swap + ")");
        TBGame game = loadGame(g.getGid());

        synchronized (cacheTbLock) {
            game.renjuSwap(swap);
        }
        long newTimeout = Utilities.calculateNewTimeout(game, dsgPlayerStorer);
        synchronized (cacheTbLock) {
            game.setTimeoutDate(new Date(newTimeout));
            persistSet(game.getTbSet());
        }
        baseStorer.renjuSwap(game, swap);
    }

    public void renjuBranch(TBGame g, boolean tenOffer) throws TBStoreException {
        log4j.debug("CacheTBStorer.renjuBranch(" + g.getGid() + ", " + tenOffer + ")");
        TBGame game = loadGame(g.getGid());

        synchronized (cacheTbLock) {
            game.renjuBranch(tenOffer);
            persistSet(game.getTbSet());
        }
        baseStorer.renjuBranch(game, tenOffer);
    }

    public void renjuOffers(TBGame g) throws TBStoreException {
        log4j.debug("CacheTBStorer.renjuOffers(" + g.getGid() + ")");
        TBGame game = loadGame(g.getGid());

        synchronized (cacheTbLock) {
            game.setRenjuOffers(g.getRenjuOffers());
            persistSet(game.getTbSet());
        }
        baseStorer.renjuOffers(game);
    }
```

> `cacheTbLock`, `loadGame`, `persistSet`, `Utilities.calculateNewTimeout`, `dsgPlayerStorer`, `baseStorer`, and `game.getTbSet()` are all already used by the existing `dPenteSwap`/`swap2Pass` overrides in this file.

- [ ] **Step 2: Use reconstruct for Renju move validation (incl. the 5th-move selection)**

In `CacheTBStorer.java`, `storeNewMove` validates a move before persisting it with this block:

```java
        GridState state = GridStateFactory.createGridState(
                game.getGame(), game);
        if (game.getGame() == GridStateFactory.TB_PENTE ||
                game.getGame() == GridStateFactory.TB_KERYO ||
                game.getGame() == GridStateFactory.TB_BOAT_PENTE ||
                game.getGame() == GridStateFactory.TB_POOF_PENTE ||
                game.getGame() == GridStateFactory.TB_OPENTE) {
            ((PenteState) state).setTournamentRule(game.isRated());
        }
        if (!state.isValidMove(move, state.getCurrentPlayer())) {
            throw new TBStoreException("Invalid move [" + move + "] for " + GridStateFactory.getGameName(game.getGame()) + " game: " +
                    game.getGid());
        }
```

Replace that entire block with the version below. The Renju branch reconstructs the engine from persisted opening state; the Branch-B 5th move is selected from the offered list (the engine's `isValidMove` deliberately blocks board placement during the offer/select phase, so selection is validated against the offers instead):

```java
        boolean valid;
        if (game.getGame() == GridStateFactory.TB_RENJU) {
            RenjuState rs = RenjuState.reconstruct(
                    game, game.getRenjuSwaps(), game.getRenjuOffers());
            if (rs.isAwaitingFifthSelection()) {
                // the 5th move is chosen from the 10 offered moves, not placed freely
                valid = false;
                int[] offs = game.getRenjuOffers();
                if (offs != null) {
                    for (int o : offs) {
                        if (o == move) { valid = true; break; }
                    }
                }
            } else {
                valid = rs.isValidMove(move, rs.getCurrentPlayer());
            }
        } else {
            GridState state = GridStateFactory.createGridState(
                    game.getGame(), game);
            if (game.getGame() == GridStateFactory.TB_PENTE ||
                    game.getGame() == GridStateFactory.TB_KERYO ||
                    game.getGame() == GridStateFactory.TB_BOAT_PENTE ||
                    game.getGame() == GridStateFactory.TB_POOF_PENTE ||
                    game.getGame() == GridStateFactory.TB_OPENTE) {
                ((PenteState) state).setTournamentRule(game.isRated());
            }
            valid = state.isValidMove(move, state.getCurrentPlayer());
        }
        if (!valid) {
            throw new TBStoreException("Invalid move [" + move + "] for " +
                    GridStateFactory.getGameName(game.getGame()) + " game: " +
                    game.getGid());
        }
```

Add the import if not present: `import org.pente.game.RenjuState;` (`GridStateFactory`/`GridState`/`PenteState` are already imported in this file).

- [ ] **Step 3: Compile**

```bash
./justCompile
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add dsg_src/java/org/pente/turnBased/CacheTBStorer.java
git commit -m "feat(renju): CacheTBStorer overrides + reconstruct-based move validation

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 7: MoveServlet — route Renju opening actions

**Files:**
- Modify: `dsg_src/java/org/pente/turnBased/web/MoveServlet.java`

Defines the server-side wire contract for Renju opening actions via a `renjuAction` request parameter (the React client — separately maintained — must send it). Verified by compile.

- [ ] **Step 1: Add the Renju routing block**

In `dsg_src/java/org/pente/turnBased/web/MoveServlet.java`, immediately before the existing D-Pente block (`// handle dpente separately` / `if ((game.getGame() == GridStateFactory.TB_DPENTE ...`), insert a Renju branch. The `moves[]` array and `message` are already parsed above (see lines 363-381); read the new `renjuAction` param near the move parsing.

Add, just after the `moves` array is validated:

```java
                String renjuAction = request.getParameter("renjuAction");
```

Then insert this block before the dpente `if`:

```java
                if (game.getGame() == GridStateFactory.TB_RENJU && renjuAction != null) {
                    org.pente.game.RenjuOpeningState rst =
                            org.pente.game.RenjuOpeningState.decode(game.getRenjuSwaps());

                    if ("swap".equals(renjuAction)) {
                        // moves[0] == 1 means the deciding player takes over (swap)
                        boolean swap = moves[0] == 1;
                        tbGameStorer.renjuSwap(game, swap);

                    } else if ("branch".equals(renjuAction)) {
                        // moves[0] == 2 selects Branch B (10-offer); 1 selects Branch A
                        boolean tenOffer = moves[0] == 2;
                        tbGameStorer.renjuBranch(game, tenOffer);

                    } else if ("offer".equals(renjuAction)) {
                        if (rst.branch != org.pente.game.RenjuOpeningState.YES
                                || game.getNumMoves() != 4
                                || moves.length != 10) {
                            handleError(request, response, "Expected 10 offered moves.");
                            return;
                        }
                        // validate via the engine's offer rules (distinct, non-symmetric, empty)
                        RenjuState rs = RenjuState.reconstruct(
                                game, game.getRenjuSwaps(), null);
                        try {
                            for (int off : moves) {
                                rs.offerFifthMove(off);
                            }
                        } catch (RuntimeException bad) {
                            handleError(request, response, "Invalid 5th-move offer.");
                            return;
                        }
                        int[] offers = new int[10];
                        System.arraycopy(moves, 0, offers, 0, 10);
                        game.setRenjuOffers(offers);
                        tbGameStorer.renjuOffers(game);

                    } else if ("select".equals(renjuAction)) {
                        int[] offers = game.getRenjuOffers();
                        boolean offered = false;
                        if (offers != null) {
                            for (int o : offers) {
                                if (o == moves[0]) {
                                    offered = true;
                                    break;
                                }
                            }
                        }
                        if (!offered) {
                            handleError(request, response, "Selected move was not offered.");
                            return;
                        }
                        // the chosen move becomes the real 5th move
                        tbGameStorer.storeNewMove(game.getGid(), game.getNumMoves(), moves[0]);
                        if (message != null) {
                            message.setMoveNum(game.getNumMoves() + 1);
                            tbGameStorer.storeNewMessage(game.getGid(), message);
                        }
                    } else {
                        handleError(request, response, "Unknown renju action.");
                        return;
                    }

                } else
```

> The trailing `else` chains into the existing `if ((game.getGame() == GridStateFactory.TB_DPENTE ...` so ordinary Renju moves (no `renjuAction`) fall through to the normal move path, where `CacheTBStorer.storeNewMove` validates them via `RenjuState.reconstruct` (Task 6), including the forbidden-point block once the opening completes. `tbGameStorer`, `handleError`, `message`, and `request`/`response` are all already in scope in `doPost`.

- [ ] **Step 2: Compile**

```bash
./justCompile
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add dsg_src/java/org/pente/turnBased/web/MoveServlet.java
git commit -m "feat(renju): route Taraguchi-10 opening actions in MoveServlet

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 8: Full regression

**Files:** none (verification only)

- [ ] **Step 1: Clean compile + run all Renju unit suites**

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
./justCompile \
  && ant test-one -Dtest=org.pente.game.test.RenjuOpeningStateTest \
  && ant test-one -Dtest=org.pente.game.test.RenjuReconstructTest \
  && ant test-one -Dtest=org.pente.game.test.RenjuStateTest \
  && ant test-one -Dtest=org.pente.game.test.RenjuForbiddenPointFinderTest \
  && ant test-one -Dtest=org.pente.game.test.RenjuFactoryTest
```
Expected: all PASS.

- [ ] **Step 2: Update the spec status**

In `docs/superpowers/specs/2026-06-13-renju-tb-persistence-design.md`, change `Status: approved-pending-review` → `Status: implemented (TB path; live pente_game write deferred)`.

- [ ] **Step 3: Commit**

```bash
git add docs/superpowers/specs/2026-06-13-renju-tb-persistence-design.md
git commit -m "docs(renju): mark TB persistence implemented

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Self-Review

**Spec coverage:**
- Ternary swap/branch encoding → Task 1 (`RenjuOpeningState`). ✓
- Offers byte codec → Task 1. ✓
- `RenjuState.reconstruct` (replay moves + decisions) → Task 2. ✓
- `TBGame` fields + swap/branch mutators (pid-swap on yes) → Task 3. ✓
- Schema: `tb_game.renju_swaps`/`renju_offers`, `pente_game.renju_swaps`, `pente_renju_offer` → Task 4. ✓
- `TBGameStorer` interface + `MySQLTBGameStorer` read/write + `renjuSwap`/`renjuBranch`/`renjuOffers` → Task 5. ✓
- `CacheTBStorer` overrides + reconstruct-based validation → Task 6. ✓
- `MoveServlet` opening-action routing → Task 7. ✓
- Live `pente_game` write path deferred → not implemented (schema only in Task 4), per spec "Out of scope". ✓ (`pente_renju_offer` is created but unwritten until the ServerTable step — intentional.)

**Placeholder scan:** none — every code step has complete code; storer/servlet steps note their DB/container test limitation explicitly rather than faking tests.

**Type consistency:** `RenjuOpeningState` (PENDING/NO/YES, swap1..4/branch/swap5, encode/decode/encodeOffers/decodeOffers); `RenjuState.reconstruct(MoveData, int, int[])`; `TBGame.getRenjuSwaps/setRenjuSwaps/getRenjuOffers/setRenjuOffers/renjuSwap(boolean)/renjuBranch(boolean)`; storer `renjuSwap(TBGame,boolean)/renjuBranch(TBGame,boolean)/renjuOffers(TBGame)` — names consistent across interface, MySQL, Cache, servlet, and tests.

**Selection/offer validation flow (not a gap):** offer submission (Task 7 `"offer"`) reconstructs with `offers=null` then re-offers via `offerFifthMove` to reuse the engine's distinct/symmetry/empty checks — correct because no offers are persisted yet at that point. Selection (Task 7 `"select"`) writes the chosen move via the existing `storeNewMove`; `storeNewMove`'s Renju validation (Task 6) sees `isAwaitingFifthSelection()` and accepts the move iff it is one of the persisted `renju_offers` (the engine's `isValidMove` blocks free placement during offer/select, by design). On a subsequent reload, `reconstruct` (Task 2) re-offers the 10 and calls `selectFifthMove(move5)` to rebuild the post-selection engine state.
