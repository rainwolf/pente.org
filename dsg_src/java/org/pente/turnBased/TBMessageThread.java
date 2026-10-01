package org.pente.turnBased;

import org.pente.game.GridStateFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

/**
 * Groups a turn-based game's messages into one display entry per move number.
 * Opening decisions (swaps, swap2 pass, renju take-over/offers) are not moves,
 * so their messages share a move number with a stone or sit on a stone another
 * player placed. Such entries carry their authors' names in the text.
 */
public final class TBMessageThread {

    private TBMessageThread() {
    }

    public static final class Entry {
        public final int moveNum;     // raw move_num; consumers add their own Connect6 offset
        public final int authorSeat;  // 1 or 2; 0 = text already names its author(s)
        public final int seqNbr;      // of the first message in the entry
        public final long date;       // epoch millis of the first message
        public final String text;     // encoded, ready to emit

        Entry(int moveNum, int authorSeat, int seqNbr, long date, String text) {
            this.moveNum = moveNum;
            this.authorSeat = authorSeat;
            this.seqNbr = seqNbr;
            this.date = date;
            this.text = text;
        }
    }

    /** @return 1 + the highest seq_nbr already stored at moveNum (1 if none) */
    public static int nextSeqNbr(List<TBMessage> messages, int moveNum) {
        int max = 0;
        for (TBMessage m : messages) {
            if (m.getMoveNum() == moveNum) {
                max = Math.max(max, m.getSeqNbr());
            }
        }
        return max + 1;
    }

    /**
     * @param encoder    encodes one message's text (filters, comma escaping)
     * @param nameFormat format for an author prefix, e.g. "%s: "
     * @param separator  joins the parts of a merged entry
     */
    public static List<Entry> entries(TBGame game, Function<TBMessage, String> encoder,
                                      String p1Name, String p2Name,
                                      String nameFormat, String separator) {
        List<TBMessage> sorted = new ArrayList<>(game.getMessages());
        sorted.sort(Comparator.comparingInt(TBMessage::getMoveNum)
                .thenComparingInt(TBMessage::getSeqNbr)
                .thenComparingLong(TBMessageThread::time));

        List<Entry> entries = new ArrayList<>();
        int i = 0;
        while (i < sorted.size()) {
            int moveNum = sorted.get(i).getMoveNum();
            int j = i;
            while (j < sorted.size() && sorted.get(j).getMoveNum() == moveNum) {
                j++;
            }
            List<TBMessage> group = sorted.subList(i, j);
            TBMessage first = group.get(0);
            int seat = seatOf(game, first.getPid());
            boolean named = group.size() > 1 ||
                    (hasOpening(game.getGame()) && seat != paritySeat(moveNum));

            String text;
            if (named) {
                StringBuilder sb = new StringBuilder();
                for (TBMessage m : group) {
                    if (sb.length() > 0) {
                        sb.append(separator);
                    }
                    String name = seatOf(game, m.getPid()) == 1 ? p1Name : p2Name;
                    sb.append(String.format(nameFormat, name)).append(encoder.apply(m));
                }
                text = sb.toString();
            } else {
                text = encoder.apply(first);
            }
            entries.add(new Entry(moveNum, named ? 0 : seat, first.getSeqNbr(), time(first), text));
            i = j;
        }
        return entries;
    }

    private static long time(TBMessage m) {
        return m.getDate() == null ? 0L : m.getDate().getTime();
    }

    private static int seatOf(TBGame game, long pid) {
        return game.getPlayer1Pid() == pid ? 1 : 2;
    }

    /** the seat that places stone n outside the opening: odd → 1, even → 2 */
    private static int paritySeat(int moveNum) {
        return moveNum % 2 == 1 ? 1 : 2;
    }

    private static boolean hasOpening(int game) {
        return game == GridStateFactory.TB_DPENTE ||
                game == GridStateFactory.TB_DKERYO ||
                game == GridStateFactory.TB_SWAP2PENTE ||
                game == GridStateFactory.TB_SWAP2KERYO ||
                game == GridStateFactory.TB_RENJU;
    }
}
