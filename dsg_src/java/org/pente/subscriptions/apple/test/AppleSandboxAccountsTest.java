package org.pente.subscriptions.apple.test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Arrays;
import java.util.Set;
import java.util.function.LongSupplier;

import junit.framework.TestCase;

import org.pente.subscriptions.apple.AppleSandboxAccounts;

/**
 * The sandbox allow-list file: parsing and reload (spec 2026-10-15 §4.4 step 5, §5).
 *
 * Run: ant test-apple-billing
 */
public class AppleSandboxAccountsTest extends TestCase {

    private long now = 1000000000000L;
    private Path file;

    private final LongSupplier clock = new LongSupplier() {
        public long getAsLong() {
            return now;
        }
    };

    public AppleSandboxAccountsTest(String name) {
        super(name);
    }

    protected void setUp() throws Exception {
        file = File.createTempFile("apple-sandbox-accounts", ".txt").toPath();
    }

    protected void tearDown() throws Exception {
        Files.deleteIfExists(file);
    }

    private void write(String content, long mtime) throws Exception {
        Files.write(file, content.getBytes(StandardCharsets.UTF_8));
        Files.setLastModifiedTime(file, FileTime.fromMillis(mtime));
    }

    public void testParse() {
        Set<String> names = AppleSandboxAccounts.parse(Arrays.asList(
                "# test accounts", "  iostest  ", "", "Graviton # app review demo", "#rainwolf"));
        assertEquals(2, names.size());
        assertTrue(names.contains("iostest"));
        assertTrue(names.contains("graviton"));
    }

    public void testCaseInsensitiveLookup() throws Exception {
        write("IosTest\n", 5000L);
        AppleSandboxAccounts accounts = new AppleSandboxAccounts(file, clock);
        assertTrue(accounts.contains("iostest"));
        assertTrue(accounts.contains(" IOSTEST "));
        assertTrue(!accounts.contains("graviton"));
        assertTrue(!accounts.contains(null));
    }

    public void testReloadedOnlyAfterFiveMinutesAndOnlyWhenChanged() throws Exception {
        write("iostest\n", 5000L);
        AppleSandboxAccounts accounts = new AppleSandboxAccounts(file, clock);
        assertTrue(accounts.contains("iostest"));

        write("graviton\n", 6000L);
        now += 4 * 60 * 1000L;
        assertTrue("not re-checked within 5 minutes", accounts.contains("iostest"));
        assertTrue(!accounts.contains("graviton"));

        now += 60 * 1000L;
        assertTrue("re-read after 5 minutes because the mtime changed", accounts.contains("graviton"));
        assertTrue(!accounts.contains("iostest"));
    }

    public void testSameMtimeIsNotReRead() throws Exception {
        write("iostest\n", 5000L);
        AppleSandboxAccounts accounts = new AppleSandboxAccounts(file, clock);
        assertTrue(accounts.contains("iostest"));
        write("graviton\n", 5000L);
        now += 10 * 60 * 1000L;
        assertTrue(accounts.contains("iostest"));
    }

    public void testMissingFileIsAnEmptyList() throws Exception {
        Files.delete(file);
        AppleSandboxAccounts accounts = new AppleSandboxAccounts(file, clock);
        assertTrue(!accounts.contains("iostest"));
        write("iostest\n", 7000L);
        now += 5 * 60 * 1000L;
        assertTrue("a file created later is picked up", accounts.contains("iostest"));
        assertTrue(!new AppleSandboxAccounts(null, clock).contains("iostest"));
    }
}
