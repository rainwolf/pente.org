package org.pente.gameDatabase.swing.util;

import java.security.*;
import javax.crypto.Cipher;

//import org.bouncycastle.jce.provider.*;

public class Test {


	  private static byte[] encrypt(byte[] inpBytes, PublicKey key,
	      String xform) throws Exception {
	    Cipher cipher = Cipher.getInstance(xform);
	    cipher.init(Cipher.ENCRYPT_MODE, key);
	    return cipher.doFinal(inpBytes);
	  }
	  private static byte[] decrypt(byte[] inpBytes, PrivateKey key,
	      String xform) throws Exception{
	    Cipher cipher = Cipher.getInstance(xform);
	    cipher.init(Cipher.DECRYPT_MODE, key);
	    return cipher.doFinal(inpBytes);
	  }

	  public static void main(String[] unused) throws Exception {
		  
			//Security.addProvider(new BouncyCastleProvider());
	    String xform = "RSA/NONE/NoPadding";
	    // Generate a key-pair
	    KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA", "BC");
	    kpg.initialize(512); // 512 is the keysize.
	    KeyPair kp = kpg.generateKeyPair();
	    PublicKey pubk = kp.getPublic();
	    PrivateKey prvk = kp.getPrivate();
System.out.println(pubk);
System.out.println(prvk);
	    byte[] dataBytes =
	        "J2EE Security for Servlets, EJBs and Web Services".getBytes();

	    byte[] encBytes = encrypt(dataBytes, pubk, xform);
	    byte[] decBytes = decrypt(encBytes, prvk, xform);

	    boolean expected = java.util.Arrays.equals(dataBytes, decBytes);
	    System.out.println("Test " + (expected ? "SUCCEEDED!" : "FAILED!"));
	  }
}
