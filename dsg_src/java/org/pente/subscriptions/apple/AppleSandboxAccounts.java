package org.pente.subscriptions.apple;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.LongSupplier;

import org.apache.log4j.Category;

/**
 * The sandbox allow-list (spec 2026-10-15 §2 D5, §4.4 step 5): pente.org usernames, one per line, '#' starts a
 * comment, case-insensitive. Production grants a sandbox transaction only to these players. The file's mtime is
 * checked at most every 5 minutes and the file is re-read when it changed; a missing file is an empty list.
 */
public class AppleSandboxAccounts {

    private static final Category log4j = Category.getInstance(AppleSandboxAccounts.class.getName());

    static final long CHECK_INTERVAL_MS = 5 * 60 * 1000L;

    private final Path file;
    private final LongSupplier clock;
    private Set<String> names = Collections.emptySet();
    private long lastCheckMs;
    private boolean checked;
    private long lastModifiedMs = Long.MIN_VALUE;

    public AppleSandboxAccounts(Path file, LongSupplier clock) {
        this.file = file;
        this.clock = clock;
    }

    /** lowercase usernames from the file's lines: trimmed, blank lines and '#' comments skipped */
    public static Set<String> parse(List<String> lines) {
        Set<String> result = new HashSet<String>();
        for (String line : lines) {
            int hash = line.indexOf('#');
            String name = (hash >= 0 ? line.substring(0, hash) : line).trim();
            if (!name.isEmpty()) {
                result.add(name.toLowerCase(Locale.ROOT));
            }
        }
        return result;
    }

    public synchronized boolean contains(String username) {
        if (username == null) {
            return false;
        }
        refresh();
        return names.contains(username.trim().toLowerCase(Locale.ROOT));
    }

    private void refresh() {
        long now = clock.getAsLong();
        if (checked && now - lastCheckMs < CHECK_INTERVAL_MS) {
            return;
        }
        checked = true;
        lastCheckMs = now;
        if (file == null) {
            names = Collections.emptySet();
            return;
        }
        try {
            long modified = Files.getLastModifiedTime(file).toMillis();
            if (modified != lastModifiedMs) {
                names = parse(Files.readAllLines(file, StandardCharsets.UTF_8));
                lastModifiedMs = modified;
                log4j.info("AppleSandboxAccounts: " + names.size() + " sandbox accounts from " + file);
            }
        } catch (NoSuchFileException e) {
            names = Collections.emptySet();
            lastModifiedMs = Long.MIN_VALUE;
        } catch (IOException e) {
            log4j.warn("AppleSandboxAccounts: cannot read " + file + ", keeping the previous list: " + e);
        }
    }
}
