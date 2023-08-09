<%@ page import="java.util.*,
                 org.pente.gameServer.core.*,
                 org.pente.gameServer.server.*"
         errorPage="../../five00.jsp" %>

<% Resources resources = (Resources) application.getAttribute(
   Resources.class.getName());
%>
<font face="Verdana, Arial, Helvetica, sans-serif" size="2">
   <h2>Frequently Asked Questions - Applet TroubleShooting</h2>

   <ul>
      <li><a href="#requirements"><b>What do I need in order to play?</b></a></li>
      <li><a href="#loading"><b>Why does the applet never finish loading?</b></a></li>
      <li><a href="#incompatibilities"><b>What are the known incompatibilities?</b></a></li>
      <li><a href="#mac"><b>What about playing with Macintosh?</b></a></li>
   </ul>
   <br>
   <a name="requirements"><u>What do I need in order to play?</u></a><br>
   All you need in order to play is a Java-enabled browser
   (e.g., Netscape Navigator 4 or higher, Internet Explorer 4, or higher)
   and a Pente.org player name.<br>
   <br>
   <a name="loading"><u>Why does the applet never finish loading?</u></a><br>
   If the problems you are experiencing with Pente.org include:
   <ul>
      <li>getting a blank screen
      <li>nothing occuring when you try to join a table
      <li>any similar problem
   </ul>
   You need to double-check that Java and JavaScript are fully enabled in your browser.
   Follow the steps below for your browser:<br>
   <br>
   <b>Netscape 4.XX or newer for Windows, Mac, or Linux</b>
   <ol>
      <li>Click "Edit" in the menu bar, and then select "Preferences."
      <li>Click the word "Advanced" and put checks in the boxes next to "Enable Java" and "Enable JavaScript."
   </ol>

   <b>Internet Explorer 4.XX for Windows</b>

   <ol>
      <li>Click "View" in the menu bar, and then select "Internet Options."
      <li>Click the "Security" tab.
      <li>For the Internet Zone, be sure that the Security level is set to "Medium" or below. (If you have chosen to
         customize your Security settings for the Internet zone, be sure that under the Java heading, "Java permissions"
         is not set to "Disable Java." You will want to select one of the three safety levels instead. Also be sure that
         under the Scripting heading, "Scripting of Java Applets" is set to "Enable" or "Prompt.")
      <li>Next, click the "Advanced" tab.
      <li>Scroll down until you see the sub heading "Java VM."
      <li>Check the boxes next to "Java logging enabled" and "Java console enabled." (Note: If you have checked the box
         next to "Java JIT compiler enabled" and are experiencing freezing problems, uncheck that box.)
   </ol>

   <b>Internet Explorer 5.XX or 6.XX for Windows</b>
   <ol>
      <li>Click "Tools" in the menu bar, and then select "Internet Options."
      <li>Click the "Security" tab. For the Internet zone, be sure that the Security level is set to "Medium" or below.
         (If you have chosen to customize your security settings for the Internet zone, be sure that under the Java
         heading, "Java permissions" is not set to "Disable Java." You will want to select one of the three safety
         levels instead. Also be sure that under the Scripting heading, "Scripting of Java Applets" is set to "Enable"
         or "Prompt.")
      <li>Next, click the "Advanced" tab.
      <li>Scroll down until you see the sub heading "Java VM."
      <li>Check the boxes next to "Java logging enabled" and "Java console enabled." (Note: If you checked the box next
         to "Java JIT compiler enabled" and are experiencing freezing problems, uncheck that box.)
   </ol>

   <b>Note to Windows XP users</b><br>
   By default, the Windows XP operating system comes with the Internet Explorer 6 browser without the Java Virtual
   Machine (JVM) installed, which is required to run Java programs such as the Pente.org game room.<br>
   <br>
   If you install Windows XP over an existing installation of Windows that already has Internet Explorer with a JVM
   installed, then you should be able to play justs fine. <br>
   <br>
   At this time Microsoft no longer provides a download of the JVM if you do not already have it installed. You are
   welcome to contact them for information on where to find and download this program. <br>
   <br>
   However, you have the option to download Sun's Java plug-in by visiting <br>
   <br>
   <a href="http://java.sun.com/getjava/download.html">http://java.sun.com/getjava/download.html</a><br>
   <br>
   Sun is the creator of Java, and their plug-in will allow you to play at Pente.org. <br>
   <br>

   <b>Internet Explorer 4.5 or 5 for Macintosh OS 8-9</b>
   <ol>
      <li>Select "Edit" from your menu bar and choose "Preferences..."
      <li>Then, click the "Java" bullet within the Web Browser section.
      <li>Make sure that the "Enable Java" box is checked.
   </ol>

   <b>Internet Explorer 5 for Macintosh OS X </b>
   <ol>
      <li>Select "Preferences" from the "Explorer" menu.
      <li>Select "Java" from the left navigation bar.
      <li>Place a check-mark next to "Enable Java."
   </ol>

   You may also need to restart your browser. <br>
   <br>
   If you continue to have problems, double-check your computer's compatibility below.<br>
   <br>
   <a name="incompatibilities"><u>What are the known incompatibilities?</u></a><br>
   Pente.org works best with Internet Explorer 4 or higher. Maybe players who have trouble
   with IE also find that Firefox works great, its what I use.
   You may experience difficulties with older browsers. If you are using Internet Explorer 3.xx or lower or Netscape
   3.xx or lower, please upgrade your browser to a newer version.<br>
   <br>
   <b>Windows 3.1, Dreamcast, and WebTV</b><br>
   Windows 3.1, Dreamcast, and WebTV (or other Web appliances) do not support the Java necessary to run the Pente.org
   game room.<br>
   <br>
   <b>Using AOL?</b><br>
   If you're using AOL, CompuServe, or Prodigy, Pente.org should work just fine if you upgrade to the most current
   version of your online service and upgrade your external browser to Netscape 4.XX or Microsoft Internet Explorer
   4.XX. After connecting to your online service, minimize the window and then launch your external browser (Netscape or
   Internet Explorer). <br>
   <br>
   <b>Behind a firewall?</b><br>
   If you are behind a firewall or using a proxy server, you may be unable to play at Pente.org.
   Firewalls need to open port(s)
   <% List servers = resources.getServerData();
      for (int i = 0; i < servers.size(); i++) {
         ServerData data = (ServerData) servers.get(i); %>
   <%= data.getPort() %><% if (i != servers.size() - 1) {%>,<%}%>
   <% } %>.
   You will need TCP inbound and outbound access for this port on pente.org.
   You will need to do a nslookup on pente.org to find out the IP address.<br>
   <br>
   <b>Using Macintosh?</b><br>
   For more information on getting Pente.org to work with your Macintosh, please read below.<br>
   <br>
   <a name="mac"><u>What about playing with Macintosh?</u></a><br>
   For the best performance on a Macintosh, we recommend:
   <ul>
      <li>A PowerPC processor (200MHz or faster recommended)
      <li>Internet Explorer 4.5 (or newer) with current MRJ
      <li>64 megabytes RAM
      <li>OS 8.1 or newer
   </ul>
   On OS 8-9, Pente.org work best on a Macintosh using the Macintosh OS Runtime for Java (MRJ). Currently, the only
   browser which supports the MRJ is Microsoft Internet Explorer.<br>
   <br>
   Therefore, if you use a Macintosh you will see the best results with the Microsoft Internet Explorer 4.5 (or newer)
   browser with the most current version of the MRJ. You may be able to use the current Netscape browser, but the
   performance will likely be sluggish. You may experience problems with other/older browsers or with any non-PowerPC
   Mac (e.g., 68K Mac). <br>
   <br>
   You can download the Internet Explorer browser for free at: <br>
   <a href="http://www.microsoft.com/mac/ie/default.asp">http://www.microsoft.com/mac/ie/default.asp</a><br>
   <br>
   If you are having problems with the browser (we recommend 4.5 or newer), make sure you are using the most current
   MRJ: <br>
   <a href="http://www.apple.com/java">http://www.apple.com/java</a><br>
   <br>
   If you are having problems with OS X, please make sure you are using the most current versions of OS X and Internet
   Explorer. If you continue having problems, you may want to boot in classic mode and use IE 4.5 or 5 or Netscape 4
   which work very well with OS 8-9. <br>
   <br>
   Please note that at this time we <b>do not recommend that you use the Netscape 6/7 browser on a Macintosh.</b><br>

</font>