package org.pente.tools;

import java.io.*;

public class JavaVersionReader {

	/**
	 * @param args
	 */
	public static void main(String[] args) throws IOException {
	
		BufferedReader in = new BufferedReader(new FileReader(args[0]));
		String line = null;
	
		BufferedWriter out = new BufferedWriter(
			new FileWriter(args[1]));
		
		while ((line = in.readLine()) != null) {
			
			int lbi = line.lastIndexOf("[");
			String nm = line.substring(72, lbi-1);
			
			int bi = line.lastIndexOf("browser=");
			int jvi = line.lastIndexOf("java.version=");
			String browser = line.substring(bi+8, jvi-1);
			
			int jcvi = line.lastIndexOf("java.class.version=");
			String jv = line.substring(jvi+13,jcvi-1);
			
			int osi = line.lastIndexOf("os=");
			String jcv = line.substring(jcvi+19,osi-1);
			
			int osvi = line.lastIndexOf("os.version=");
			String os = line.substring(osi+3, osvi-1);
			out.write(nm + "," + browser + "," + jv + "," + jcv + "," + os + "\n");

		
		}
		
		out.close();
	}

}
