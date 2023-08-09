package org.pente.turnBased.swing;

import java.io.*;

public class JOGLLoader {

    public static void setupJOGL() {
        copyFile("/jogl.dll");

        loadOpenGL();
    }

    /**
     * http://192.18.37.44/forums/index.php?topic=10262.msg82045
     */
    private static void loadOpenGL() {
        System.out.println("load jogl libraries");
        com.sun.opengl.impl.NativeLibLoader.disableLoading();

        // Manually load AWT to work around the problem with error boxes coming up claiming it's not found.
        // Apparently AWT has to be loaded -before- jogl's .dll files or all Hell breaks loose.
        java.awt.Toolkit.getDefaultToolkit();
        System.loadLibrary("jawt");

        // Now, load the .dll/.so files for the OpenGL C bindings
        String strOS = System.getProperty("os.name");

        if (strOS.startsWith("Windows")) {
            System.load(System.getProperty("user.dir") + "/jogl.dll");

        }
//      if (strOS.startsWith("Mac OS"))
//      {
//            System.load(strJOGL + "libjogl.jnlib");     
//      }               
//      if (strOS.startsWith("Linux"))
//      {
//              System.load(strJOGL + "libjogl.so");
//      }
//      if (strOS.startsWith("Solaris"))
//      {
//             String strArch = System.getProperty("os.arch");
//             if (strArch.equalsIgnoreCase("sparc"))
//             {
//                 System.load(strJOGL + "libjogl_solsparc.so");
//             }
//             if (strArch.equalsIgnoreCase("x86"))
//             {
//                 System.load(strJOGL + "libjogl_solx86.so");
//             }
//      }
    }

    private static void copyFile(String fileName) {
        try {
            InputStream is = JOGLLoader.class.getResourceAsStream(fileName);

            File tempDir = new File(System.getProperty("user.dir"));
            File file = new File(tempDir, fileName);

            System.out.println("copy " + fileName + " to " +
                    file.getCanonicalPath());

            OutputStream os = new FileOutputStream(file);
            byte[] buf = new byte[4096];
            int cnt = 0;
            do {
                cnt = is.read(buf);
                if (cnt == -1) break;
                os.write(buf, 0, cnt);

            } while (true);
            os.close();
            is.close();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
