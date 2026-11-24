package org.pente.gameServer.server.ssl.test;

import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/** Test helper: builds throwaway PKCS12 keystores (alias "tomcat") via openssl. */
public class TestKeystores {

    static final String OPENSSL = System.getProperty("openssl",
        new java.io.File("/opt/homebrew/bin/openssl").exists() ? "/opt/homebrew/bin/openssl" : "openssl");

    public static Path create(Path dir, String name, int validDays, char[] pw) throws Exception {
        Path key = dir.resolve(name + ".key");
        Path crt = dir.resolve(name + ".crt");
        Path p12 = dir.resolve(name + ".p12");
        run(OPENSSL, "req", "-x509", "-newkey", "rsa:2048", "-nodes",
            "-keyout", key.toString(), "-out", crt.toString(),
            "-days", String.valueOf(validDays), "-subj", "/CN=" + name);
        run(OPENSSL, "pkcs12", "-export", "-name", "tomcat",
            "-inkey", key.toString(), "-in", crt.toString(),
            "-out", p12.toString(), "-passout", "pass:" + new String(pw));
        return p12;
    }

    private static void run(String... cmd) throws Exception {
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        byte[] out = p.getInputStream().readAllBytes();
        if (!p.waitFor(60, TimeUnit.SECONDS) || p.exitValue() != 0) {
            throw new IllegalStateException(String.join(" ", cmd) + " failed: " + new String(out));
        }
    }
}
