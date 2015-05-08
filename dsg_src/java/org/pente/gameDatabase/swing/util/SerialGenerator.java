package org.pente.gameDatabase.swing.util;

import java.io.*;
import java.security.*;
import java.security.spec.*;

import javax.crypto.*;
import javax.crypto.spec.*;

import org.apache.commons.codec.binary.Base64;

import org.apache.log4j.*;
//import org.bouncycastle.jce.provider.BouncyCastleProvider;

public class SerialGenerator {

    private static Base64 base64 = new Base64();
    
    public static void main(String args[]) throws Throwable {

        MessageDigest md = MessageDigest.getInstance("SHA1");
        System.out.println("Message digest object info: ");
        System.out.println("   Algorithm = "+md.getAlgorithm());
        System.out.println("   Provider = "+md.getProvider());
        System.out.println("   toString = "+md.toString());

        String input = "";
        md.update(input.getBytes()); 
     	 byte[] output = md.digest();
        System.out.println();
        System.out.println("SHA1(\""+input+"\") =");
        System.out.println("   "+toHex(output));

        input = "abc";
        md.update(input.getBytes()); 
     	 output = md.digest();
        System.out.println();
        System.out.println("SHA1(\""+input+"\") =");
        System.out.println("   "+toHex(output));

        input = "abcdefghijklmnopqrstuvwxyz";
        md.update(input.getBytes()); 
     	 output = md.digest();
        System.out.println();
        System.out.println("SHA1(\""+input+"\") =");
        System.out.println("   "+toHex(output));
        

    	
		//Security.addProvider(new BouncyCastleProvider());
    	System.out.println("input " + args[0]);
    	byte enc[] = SerialGenerator.encrypt(args[0].getBytes());
    	//byte[] encB64 = base64.encode(enc);
    	//System.out.println(new String(encB64));
    	//byte[] decB64 = base64.decode(encB64);
    	String hexEnc = toHex(enc);
    	System.out.println(hexEnc);
    	byte dec[] = SerialGenerator.decrypt(enc);
    	System.out.println(new String(dec));
    }
   private static String	digits = "0123456789abcdef";
    
    /**
     * Return length many bytes of the passed in byte array as a hex string.
     * 
     * @param data the bytes to be converted.
     * @param length the number of bytes in the data block to be converted.
     * @return a hex representation of length bytes of data.
     */
    public static String toHex(byte[] data)
    {
        StringBuffer	buf = new StringBuffer();
        
        for (int i = 0; i != data.length; i++)
        {
            int	v = data[i] & 0xff;
            
            buf.append(digits.charAt(v >> 4));
            buf.append(digits.charAt(v & 0xf));
        }
        
        return buf.toString();
    }
    public static byte[] encrypt(byte[] in) {
        byte[] encrypted = null;
        
    	try {
	    	File keyFile = new File("/dsg_src/conf/private.key");        
	    	byte[] encodedKey = new byte[(int)keyFile.length()];
	
	    	new FileInputStream(keyFile).read(encodedKey);

	    	PKCS8EncodedKeySpec privateKeySpec = new PKCS8EncodedKeySpec(encodedKey);
	
	    	KeyFactory kf = KeyFactory.getInstance("RSA");
	    	PrivateKey pk = kf.generatePrivate(privateKeySpec);
	
	    	Cipher rsa = Cipher.getInstance("RSA");

	    	rsa.init(Cipher.ENCRYPT_MODE, pk);
	    	System.out.println(rsa.getBlockSize());
	    	encrypted = rsa.doFinal(in);
	    	
    	} catch (Throwable t) {
    		t.printStackTrace();
    	}
        return encrypted;
    }
    
    public static byte[] decrypt(byte in[]) {
    	byte[] decrypted = null;
    	try {
	    	File keyFile = new File("/dsg_src/conf/public.key");        
	    	byte[] encodedKey = new byte[(int)keyFile.length()];

	    	new FileInputStream(keyFile).read(encodedKey);
	    	X509EncodedKeySpec publicKeySpec = new X509EncodedKeySpec(encodedKey);

	    	KeyFactory kf = KeyFactory.getInstance("RSA");
	    	PublicKey pk = kf.generatePublic(publicKeySpec);

	    	Cipher rsa = Cipher.getInstance("RSA");
	    	rsa.init(Cipher.DECRYPT_MODE, pk);

	    	decrypted = rsa.doFinal(in);
	    	
    	} catch (Throwable t) {
    		t.printStackTrace();
    	}
    	return decrypted;
    }
}
