package org.pente.gameServer.server;

import java.io.*;
import java.security.*;
import java.security.spec.*;

import javax.crypto.*;
import javax.crypto.spec.*;

import org.apache.commons.codec.binary.Base64;

import org.apache.log4j.*;

public class PasswordHelper {

    private static Category log4j = 
        Category.getInstance(PasswordHelper.class.getName());

    private final static String CIPHER_TRANS = "DESede/CBC/PKCS5Padding";
    
    private SecretKey key = null;
    private Cipher encryptCipher = null;
    private Cipher decryptCipher = null;
    private Base64 base64 = new Base64();
    
    public PasswordHelper(File keyFile)
        throws InvalidKeyException, InvalidKeySpecException, IOException,
        NoSuchAlgorithmException, NoSuchPaddingException,
        InvalidAlgorithmParameterException {

        log4j.info("Password helper, read key from " + keyFile);

        initCiphers(keyFile);
    }
    
    public String encrypt(String plain) {

        String encrypted = null;

        try {
            byte[] crypt = encryptCipher.doFinal(plain.getBytes());
            encrypted = new String(base64.encode(crypt));
            
        } catch (Throwable t) {
            log4j.error("Error encrypting String: " + plain);
            try {
                log4j.error("Attempting to re-init() cipher.");
                encryptCipher.init(Cipher.ENCRYPT_MODE, key);
                log4j.error("Re-init() successful.");
            } catch (InvalidKeyException e) {
                log4j.error("Re-init() failed, uh-oh.");
            }
        }
        
        return encrypted;
    }
    public String decrypt(String encrypted) {
        
        String plain = null;
        
        try {
            byte[] crypt = base64.decode(encrypted.getBytes());
            byte[] plainBytes = decryptCipher.doFinal(crypt);
            plain = new String(plainBytes);

        } catch (Throwable t) {
            log4j.error("Error decrypting String: " + encrypted, t);
            try {
                log4j.error("Attempting to re-init() cipher.");
                decryptCipher.init(Cipher.DECRYPT_MODE, key);
                log4j.error("Re-init() successful.");
            } catch (InvalidKeyException e) {
                log4j.error("Re-init() failed, uh-oh.");
            }
        }
        return plain;
    }
    
    private void initCiphers(File f) 
        throws InvalidKeyException, InvalidKeySpecException, IOException,
        NoSuchAlgorithmException, NoSuchPaddingException,
        InvalidAlgorithmParameterException {

        DataInputStream in = new DataInputStream(new FileInputStream(f));

        // read in the iv, needed by ciphers
        int len = in.readInt();
        byte[] iv = new byte[len];
        in.read(iv);

        // read in the key
        byte[] rawkey = new byte[(int) f.length() - len - 4];
        in.readFully(rawkey);
        in.close();

        DESedeKeySpec keyspec = new DESedeKeySpec(rawkey);
        SecretKeyFactory keyfactory = SecretKeyFactory.getInstance("DESede");
        SecretKey key = keyfactory.generateSecret(keyspec);

        IvParameterSpec ivp = new IvParameterSpec(iv);
        
        encryptCipher = Cipher.getInstance(CIPHER_TRANS);
        encryptCipher.init(Cipher.ENCRYPT_MODE, key, ivp);
        
        decryptCipher = Cipher.getInstance(CIPHER_TRANS);
        decryptCipher.init(Cipher.DECRYPT_MODE, key, encryptCipher.getParameters());
    }
}
