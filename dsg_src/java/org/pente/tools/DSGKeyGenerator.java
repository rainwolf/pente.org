package org.pente.tools;

import javax.crypto.*;
import javax.crypto.spec.*;
import java.io.*;

public class DSGKeyGenerator {

    private final static String CIPHER_TRANS = "DESede/CBC/PKCS5Padding";
    private final static String KEY_GEN_TRANS = "DESede";

    public static void main(String[] args) throws Throwable {

        if (args.length != 1) {
            System.err.println("Usage: java DSGKeyGenerator /new/path/to/key");
        }
        File keyFile = new File(args[0]);
        if (keyFile.isFile()) {
            System.err.println("File " + args[0] + " already exists.");
            return;
        }

        KeyGenerator keygen = KeyGenerator.getInstance(KEY_GEN_TRANS);
        SecretKey key = keygen.generateKey();

        Cipher encryptCipher = Cipher.getInstance(CIPHER_TRANS);
        encryptCipher.init(Cipher.ENCRYPT_MODE, key);

        SecretKeyFactory keyfactory = SecretKeyFactory.getInstance(
                KEY_GEN_TRANS);
        DESedeKeySpec keyspec =
                (DESedeKeySpec) keyfactory.getKeySpec(key, DESedeKeySpec.class);
        byte[] rawkey = keyspec.getKey();

        DataOutputStream out = new DataOutputStream(
                new FileOutputStream(keyFile));
        byte[] iv = encryptCipher.getIV();
        out.writeInt(iv.length);
        out.write(iv);
        out.write(rawkey);
        out.close();

        System.out.println("New key written to " + args[0]);
    }
}
