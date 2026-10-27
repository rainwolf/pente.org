package org.pente.turnBased.web;

import java.util.ArrayList;
import java.util.List;

import org.pente.game.GridStateFactory;
import org.pente.game.MoveData;
import org.pente.game.TombstoneCodes;

/** Groups Tombstone move entries into one move-list row per turn. Never throws on bad history. */
public final class TombstoneTurnRows {

    public static final class Row {
        public final int number;
        public final int start;
        public final int end;
        public final int own;
        public final int dead;
        public final String label;

        Row(int number, int start, int end, int own, int dead, String label) {
            this.number = number;
            this.start = start;
            this.end = end;
            this.own = own;
            this.dead = dead;
            this.label = label;
        }
    }

    private TombstoneTurnRows() {
    }

    public static boolean isTombstoneGameName(String gameName) {
        if (gameName == null) return false;
        try {
            return GridStateFactory.isTombstone(GridStateFactory.getGameId(gameName));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public static List<Row> rows(MoveData data) {
        return rows(data.getMoves(), data.getNumMoves());
    }

    public static List<Row> rows(List<Integer> codes) {
        int[] a = new int[codes.size()];
        for (int i = 0; i < a.length; i++) a[i] = codes.get(i);
        return rows(a, a.length);
    }

    private static boolean validOwn(int code) {
        return code >= 0 && code <= 360 || code == TombstoneCodes.OWN_PASS;
    }

    public static List<Row> rows(int[] codes, int n) {
        List<Row> out = new ArrayList<Row>();
        for (int i = 0; i < n; i++) {
            int c = codes[i];
            Row last = out.isEmpty() ? null : out.get(out.size() - 1);
            if (i == 0) {
                out.add(own(1, 0, c));
            } else if (validOwn(c)) {
                out.add(own(out.size() + 1, i, c));
            } else if (TombstoneCodes.isDeadStone(c)) {
                if (last != null && last.number != 1 && validOwn(last.own) && last.dead == -1) {
                    out.set(out.size() - 1, new Row(last.number, last.start, i, last.own, c,
                            TombstoneCodes.turnLabel(last.own, c)));
                } else {
                    out.add(new Row(out.size() + 1, i, i, -1, c, TombstoneCodes.entryLabel(c)));
                }
            } else {
                out.add(new Row(out.size() + 1, i, i, -1, -1, "?"));
            }
        }
        return out;
    }

    private static Row own(int number, int i, int c) {
        if (validOwn(c)) {
            return new Row(number, i, i, c, -1, TombstoneCodes.turnLabel(c, -1));
        }
        return new Row(number, i, i, -1, -1, "?");
    }
}
