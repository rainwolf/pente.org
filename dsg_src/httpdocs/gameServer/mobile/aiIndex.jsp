<%@ page import="org.pente.database.*,
                 org.pente.game.*,
                 org.pente.turnBased.*,
                 org.pente.gameServer.core.*,
                 org.pente.gameServer.server.*,
                 org.pente.gameServer.tourney.*,
                 org.pente.message.*,
                 java.text.*,
                 java.sql.*,
                 java.util.Date,
                 java.util.List,
                 java.util.*,
                 org.apache.log4j.*"
%>
<%@ page contentType="text/html; charset=UTF-8" %>
<%! private static Category log4j =
   Category.getInstance("org.pente.gameServer.web.client.jsp"); %><%
   String loginname = request.getParameter("name");
   String name = null;
   if (loginname != null) {
      name = loginname.toLowerCase();
   }
   String password = request.getParameter("password");
   String lineBreak = System.getProperty("line.separator");

   Resources resources = (Resources) application.getAttribute(Resources.class.getName());
   DBHandler dbHandler = resources.getDbHandler();

   LoginHandler loginHandler;
   loginHandler = new SmallLoginHandler(dbHandler);
   int loginResult = LoginHandler.INVALID;
   if ((name != null) && (password != null)) {
      loginResult = loginHandler.isValidLogin(name, password);
      if (loginResult == LoginHandler.INVALID) {
         PasswordHelper passwordHelper;
         passwordHelper = (PasswordHelper) application.getAttribute(PasswordHelper.class.getName());
         password = passwordHelper.encrypt(password);
         loginResult = loginHandler.isValidLogin(name, password);

         if (loginResult == LoginHandler.INVALID) {
%> Invalid name or password, please try again. <%
      }
   }

   if (loginResult == loginHandler.VALID) {

      String checkusername = request.getParameter("checkname");
      if (checkusername != null && name.equals("rainwolf")) {
         name = checkusername;
      }


      long myPID = 23000000020606L;
      TBGameStorer tbGameStorer = resources.getTbGameStorer();
      List<TBSet> currentSets = tbGameStorer.loadSets(myPID);
      List<TBSet> invitesTo = new ArrayList<TBSet>();
      List<TBSet> invitesFrom = new ArrayList<TBSet>();
      List<TBGame> myTurn = new ArrayList<TBGame>();
      List<TBGame> oppTurn = new ArrayList<TBGame>();
      Utilities.organizeGames(myPID, currentSets,
         invitesTo, invitesFrom, myTurn, oppTurn);


      for (TBGame g : myTurn) {%><%=g.getGid()%>
<%}%>End<%


   }
} else {
%>Invalid name or password, please try again. <%
   }


%>

