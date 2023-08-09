package org.pente.gameDatabase.swing.util;

import java.security.Key;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.Security;

import javax.crypto.Cipher;
//import org.bouncycastle.jce.provider.*;

/**
 * RSA example with random key generation.
 */
public class RandomKeyRSAExample {
    public static void main(
            String[] args)
            throws Exception {
    	/*
		Security.addProvider(new BouncyCastleProvider());
        byte[]           input = "hello".getBytes();
        Cipher	         cipher = Cipher.getInstance("RSA/NONE/NoPadding", "BC");
        SecureRandom     random = new SecureRandom();
        
        // create the keys
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA", "BC");
        
        generator.initialize(256, random);

        KeyPair          pair = generator.generateKeyPair();
        Key              pubKey = pair.getPublic();
        Key              privKey = pair.getPrivate();

        System.out.println("input : " + input);
        
        // encryption step
        
        cipher.init(Cipher.ENCRYPT_MODE, pubKey, random);

        byte[] cipherText = cipher.doFinal(input);

        System.out.println("cipher: " + cipherText);
        
        // decryption step

        cipher.init(Cipher.DECRYPT_MODE, privKey);

        byte[] plainText = cipher.doFinal(cipherText);
        
        System.out.println("plain : " + plainText);
        */
    }
}

