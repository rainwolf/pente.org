<%@ page import="org.pente.game.*" %>

<%
   pageContext.setAttribute("title", "Android");
%>

<%@ include file="top.jsp" %>

<h2>Pente app for Android phones</h2>

<b>Have an smart phone running Google Android?</b><br/>

Play Pente against a friend or against a computer opponent from your phone with the free Pente app from pente.org.<br/>
<br/>

<div>
   <div style="float:left;">
      <img style="border: 1px solid gray;width:240px;height:360px" src="/res/android_main.png"/>
      <img style="margin-left:5px; border: 1px solid gray;width:240px;height:360px" src="/res/android_board.png"/>
   </div>
   <div style="float:left;margin-left:30px;border:1px solid gray;width:300px;padding:10px">
      <center><h2>Get the app</h2></center>
      <img src="/res/market.gif" style="padding:30px"/>
      <img src="/res/qr.png"/><br/>
      Scan this barcode from your phone to get Pente on the Market, or open the Market app and search for "Pente".<br/>
      <br/>
      Or <a href="/pente1.01.apk">download</a> the app directly if you are viewing this from your phone.
   </div>

   <br style="clear:both"/>
</div>
<br style="clear:both"/>
<br/>

<%@ include file="bottom.jsp" %>
