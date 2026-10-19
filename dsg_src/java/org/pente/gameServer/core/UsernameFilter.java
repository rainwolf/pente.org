package org.pente.gameServer.core;

import java.io.*;
import java.text.Normalizer;
import java.util.*;

/**
 * Decides whether a requested username is offensive.
 * <p>
 * Names are normalized (lowercased, accents stripped, leet characters mapped
 * to letters, everything that is not a letter removed); ambiguous leet
 * characters ('1' as i or l, 'v' as v or u) are tried in every reading. The
 * blocklist has two tiers:
 * <ul>
 * <li>plain entries are unambiguous stems and match anywhere inside the
 * normalized name;</li>
 * <li>entries prefixed with '=' are ambiguous short stems (e.g. "shit" is
 * inside "yoshitaka") and match only when the whole name, or a part of it
 * delimited by non-alphanumeric characters, is exactly that word, ignoring
 * digits ("shit", "sh1t_123", "big_shit").</li>
 * </ul>
 * List files are plain text: one entry per line, '#' starts a comment,
 * blank lines are ignored. Entries are normalized the same way as names.
 */
public class UsernameFilter {

    /** Classpath location of the shipped list, next to this class. */
    public static final String BLOCKLIST_RESOURCE = "username_blocklist.txt";

    // 2^4 readings of the ambiguous leet characters '1' and 'v'
    private static final int MAX_AMBIGUOUS = 4;

    private final List<String> substrings;
    private final Set<String> exact;

    public UsernameFilter(Collection<String> entries) {
        this.substrings = new ArrayList<String>();
        this.exact = new HashSet<String>();
        if (entries != null) {
            for (String e : entries) {
                if (e == null) continue;
                e = e.trim();
                boolean isExact = e.startsWith("=");
                String n = normalize(isExact ? e.substring(1) : e);
                if (n.length() == 0) continue;
                if (isExact) {
                    exact.add(n);
                } else {
                    substrings.add(n);
                }
            }
        }
    }

    /** Builds a filter from a list stream; the stream is not closed. */
    public static UsernameFilter load(InputStream blocklistStream)
            throws IOException {
        return new UsernameFilter(readLines(blocklistStream));
    }

    /** Loads the list shipped on the classpath; null if it is missing. */
    public static UsernameFilter loadShipped() throws IOException {
        InputStream in = UsernameFilter.class.getResourceAsStream(
                BLOCKLIST_RESOURCE);
        if (in == null) {
            return null;
        }
        try {
            return load(in);
        } finally {
            in.close();
        }
    }

    public int getBlocklistSize() {
        return substrings.size() + exact.size();
    }

    /**
     * Leading affixes retried once when a name does not match as-is, so
     * that "pentepenis" or "theretard" are caught by the exact tier. Only
     * affixes proven on real usernames to catch more with no false positives.
     */
    private static final String[] AFFIXES = {"pente", "the", "mr"};

    // Ordinary words that are an affix plus a listed word ("the" + "rapist").
    // Exempt from the retry only; separated forms such as "the_rapist"
    // are still blocked by the first, unstripped pass.
    private static final Set<String> AFFIX_WORDS =
            new HashSet<String>(Arrays.asList("therapist"));

    // a remainder shorter than this is never retried ("mr" + "ape")
    private static final int MIN_REMAINDER_LETTERS = 3;

    public boolean isOffensive(String name) {
        if (name == null) {
            return false;
        }
        if (matches(name)) {
            return true;
        }
        String lower = name.toLowerCase(Locale.ROOT);
        String first = lower.replaceFirst("[^\\p{L}][\\s\\S]*", "");
        if (AFFIX_WORDS.contains(first)) {
            return false;
        }
        for (String affix : AFFIXES) {
            if (lower.startsWith(affix)) {
                String rest = lower.substring(affix.length());
                if (letters(rest) >= MIN_REMAINDER_LETTERS && matches(rest)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static int letters(String s) {
        int count = 0;
        for (int i = 0; i < s.length(); i++) {
            if (Character.isLetter(s.charAt(i))) {
                count++;
            }
        }
        return count;
    }

    private boolean matches(String name) {
        for (String n : candidates(name)) {
            for (String bad : substrings) {
                if (n.indexOf(bad) >= 0) {
                    return true;
                }
            }
            if (exact.contains(n)) {
                return true;
            }
        }
        for (String part : name.split("[^\\p{L}\\p{N}]+")) {
            for (String n : candidates(part)) {
                if (exact.contains(n)) {
                    return true;
                }
            }
            for (String n : candidates(part.replaceAll("\\p{N}", ""))) {
                if (exact.contains(n)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Default normalization: one reading of every leet character. */
    static String normalize(String s) {
        return candidates(s, false).iterator().next();
    }

    /**
     * All plausible normalizations of a name: '1' may be 'i' or 'l', and
     * 'v' may be 'v' or 'u' (so "s1ut" and "fvck" are caught). If the
     * ambiguous characters would give more than MAX_CANDIDATES readings,
     * only the default reading is used.
     */
    static Set<String> candidates(String s) {
        return candidates(s, true);
    }

    private static Set<String> candidates(String s, boolean expand) {
        String d = Normalizer.normalize(s.toLowerCase(Locale.ROOT),
                Normalizer.Form.NFKD);
        int ambiguous = 0;
        for (int i = 0; i < d.length(); i++) {
            if (d.charAt(i) == '1' || d.charAt(i) == 'v') ambiguous++;
        }
        if (!expand || ambiguous > MAX_AMBIGUOUS) {
            ambiguous = 0;
            expand = false;
        }
        Set<String> out = new LinkedHashSet<String>();
        out.add("");
        for (int i = 0; i < d.length(); i++) {
            char c = d.charAt(i);
            char alt = 0;
            switch (c) {
                case '0': c = 'o'; break;
                case '1': c = 'i'; alt = 'l'; break;
                case '3': c = 'e'; break;
                case '4': c = 'a'; break;
                case '5': c = 's'; break;
                case '7': c = 't'; break;
                case '@': c = 'a'; break;
                case '$': c = 's'; break;
                case 'v': alt = 'u'; break;
                default: break;
            }
            if (!Character.isLetter(c)) {
                continue;
            }
            Set<String> next = new LinkedHashSet<String>();
            for (String prefix : out) {
                next.add(prefix + c);
                if (expand && alt != 0) {
                    next.add(prefix + alt);
                }
            }
            out = next;
        }
        return out;
    }

    /** Reads list lines, dropping '#' comments and blank lines. */
    static List<String> readLines(InputStream in) throws IOException {
        List<String> lines = new ArrayList<String>();
        BufferedReader r = new BufferedReader(
                new InputStreamReader(in, "UTF-8"));
        String line;
        while ((line = r.readLine()) != null) {
            int hash = line.indexOf('#');
            if (hash >= 0) {
                line = line.substring(0, hash);
            }
            line = line.trim();
            if (line.length() > 0) {
                lines.add(line);
            }
        }
        return lines;
    }
}
