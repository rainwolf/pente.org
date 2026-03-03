<%@ page import="org.pente.gameDatabase.*,
                 org.pente.gameServer.core.*,
                 org.pente.gameServer.mobile.*,
                 com.google.gson.Gson,
                 java.util.*" %>
<%@ page contentType="application/json; charset=UTF-8" %>
<%
   ServletContext ctx = getServletContext();
   DSGPlayerStorer dsgPlayerStorer = (DSGPlayerStorer) ctx.getAttribute(DSGPlayerStorer.class.getName());

   String nm = (String) request.getAttribute("name");
   DSGPlayerData pdata = null;
   if (nm != null) {
      pdata = dsgPlayerStorer.loadPlayer(nm);
   }

   if (pdata == null || !pdata.databaseAccess()) {
      out.print(new Gson().toJson(DatabaseResponse.noAccess()));
      return;
   }

   if (request.getAttribute("blocked") != null) {
      out.print(new Gson().toJson(DatabaseResponse.blocked()));
      return;
   }

   GameStorerSearchResponseData data = (GameStorerSearchResponseData) request.getAttribute("responseData");
   out.print(new Gson().toJson(DatabaseResponse.build(data, request.getContextPath())));
%>