package org.pente.gameDatabase.swing.util;

import java.io.*;
import java.io.FileWriter;
import java.io.IOException;
import java.security.KeyPairGenerator;
import java.security.KeyPair;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.NoSuchAlgorithmException;
import java.security.spec.RSAKeyGenParameterSpec;
import java.security.InvalidAlgorithmParameterException;

import java.security.*;
import java.security.spec.*;
import javax.crypto.*;
import javax.crypto.spec.*;
/**
 * A simple class to generate a set of RSA (public and private) keys and
 * write those keys to files for exchange with other environments. Four files will
 * be generated containing the public and private keys in byte and text(hex) formats.
 */
public class GenerateRSAKeys {
   public GenerateRSAKeys () {
   }
 
   public static void main(String[] args) throws Throwable {
              KeyPairGenerator keyGen = null;
              try {
                     keyGen = KeyPairGenerator.getInstance("RSA");
         /* The 512 is the key size. For better encryption increase to 2048 */
                     keyGen.initialize(new RSAKeyGenParameterSpec(512, RSAKeyGenParameterSpec.F4));
              }
              catch (NoSuchAlgorithmException noAlgorithm) {
                     System.out.println("No RSA provider available!");
                     return;
              }
              catch (InvalidAlgorithmParameterException invalidAlgorithm) {
                     System.out.println("Invalid algorithm for RSA!");
                     return;
              }
             
              KeyPair keyPair = keyGen.generateKeyPair();
             
              RSAPrivateKey secretKey = (RSAPrivateKey)keyPair.getPrivate();
              RSAPublicKey publicKey = (RSAPublicKey)keyPair.getPublic();
             
              // Write out the ASN.1 and raw key files
              System.out.println(secretKey.getFormat());
              FileOutputStream fOut = new FileOutputStream("private_asn1.key");
              fOut.write(secretKey.getEncoded());
              fOut.close();
             
              System.out.println(publicKey.getFormat());
              fOut = new FileOutputStream("public_asn1.key");
              fOut.write(publicKey.getEncoded());
              fOut.close();
              System.out.println("RSA keys generated successfully.");


   }
}
