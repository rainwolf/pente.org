<object
   classid="clsid:8AD9C840-044E-11D1-B3E9-00805F499D93"
   width="<%= width %>" height="<%= height %>"
   codebase="http://java.sun.com/products/plugin/1.3/jinstall-13-win32.cab#Version=1,3,0,0">

   <param name="code" value="org.pente.turnBased.swing.TBApplet.class">
   <param name="codebase" value="/gameServer/lib/">
   <param name="archive" value="tb__V<%= version %>.jar">
   <param name="type" value="application/x-java-applet;version=1.3">
   <param name="scriptable" value="false">
   <param name="me" value="<%= me %>">
   <param name="gid" value="<%= game.getGid() %>">
   <param name="sid" value="<%= set.getSetId() %>">
   <param name="event" value="Turn-based Game">
   <param name="game" value="<%= game.getGame() %>">
   <param name="player1" value="<%= p1.getName() %>">
   <param name="player1Rating" value="<%= (int) Math.round(p1GameData.getRating()) %>">
   <param name="player1RatingGif" value="<%= p1GameData.getRatingGif() %>">
   <param name="player2" value="<%= p2.getName() %>">
   <param name="player2Rating" value="<%= (int) Math.round(p2GameData.getRating()) %>">
   <param name="player2RatingGif" value="<%= p2GameData.getRatingGif() %>">
   <param name="myTurn" value="<%= myTurn %>">
   <param name="moves" value="<%= moves %>">
   <param name="showMessages" value="<%= showMessages %>">
   <param name="messages" value="<%= messages %>">
   <param name="moveNums" value="<%= moveNums %>">
   <param name="seqNums" value="<%= seqNums %>">
   <param name="dates" value="<%= dates %>">
   <param name="players" value="<%= players %>">
   <param name="timer" value="<%= game.getDaysPerMove() %> days/move">
   <param name="rated" value="<%= game.isRated() ? "true" : "false" %>">
   <param name="private" value="<%= set.isPrivateGame() ? "true" : "false" %>">
   <param name="timeout" value="<%= game.getTimeoutDate().getTime() %>">
   <param name="gameState" value="<%= Character.toString(game.getState()) %>">
   <param name="winner" value="<%= game.getWinner()%>">
   <param name="timezone" value="<%= meData.getTimezone() %>">
   <param name="setStatus" value="<%= setStatus %>">
   <param name="otherGame" value="<%= otherGame %>">
   <param name="attach" value="<%= attach %>">
   <param name="cancelRequested" value="<%= cancelRequested %>">
   <param name="color" value="#ffffff">
   <% if (game.isCompleted()) { %>
   <param name="completedDate" value="<%= game.getCompletionDate().getTime() %>">
   <% } %>
   <% if (game.getGame() == GridStateFactory.TB_DPENTE) { %>
   <param name="dPenteState" value="<%= game.getDPenteState() %>">
   <param name="dPenteSwap" value="<%= game.didDPenteSwap() ? "true" : "false" %>">
   <% } %>
   <comment>
      <embed type="application/x-java-applet;version=1.3"
             code="org.pente.turnBased.swing.TBApplet.class"
             codebase="/gameServer/lib/"
             archive="tb__V<%= version %>.jar"
             width="<%= pageWidth %>"
             height="<%= height %>"

             me="<%= me %>"
             gid="<%= game.getGid() %>"
             sid="<%= set.getSetId() %>"
             event="Turn-based Game"
             game="<%= game.getGame() %>"
             player1="<%= p1.getName() %>"
             player1Rating="<%= (int) Math.round(p1GameData.getRating()) %>"
             player1RatingGif="<%= p1GameData.getRatingGif() %>"
             player2="<%= p2.getName() %>"
             player2Rating="<%= (int) Math.round(p2GameData.getRating()) %>"
             player2RatingGif="<%= p2GameData.getRatingGif() %>"
             myTurn="<%= myTurn %>"
             moves="<%= moves %>"
             showMessages="<%= showMessages %>"
             messages="<%= messages %>"
             moveNums="<%= moveNums %>"
             seqNums="<%= seqNums %>"
             dates="<%= dates %>"
             players="<%= players %>"
             timer="<%= game.getDaysPerMove() %> days/move"
             rated="<%= game.isRated() ? "true" : "false" %>"
             private="<%= set.isPrivateGame() ? "true" : "false" %>"
             timeout="<%= game.getTimeoutDate().getTime() %>"
             gameState="<%= Character.toString(game.getState()) %>"
             winner="<%= game.getWinner()%>"
             timezone="<%= meData.getTimezone() %>"
             setStatus="<%= setStatus %>"
             otherGame="<%= otherGame %>"
             attach="<%= attach %>"
             cancelRequested="<%= cancelRequested %>"
             color="#ffffff"
         <% if (game.isCompleted()) { %>
             completedDate="<%= game.getCompletionDate().getTime() %>"
         <% } %>
         <% if (game.getGame() == GridStateFactory.TB_DPENTE) { %>
             dPenteState="<%= game.getDPenteState() %>"
             dPenteSwap="<%= game.didDPenteSwap() ? "true" : "false" %>"
         <% } %>
             scriptable="false"
             pluginspage="http://java.sun.com/products/plugin/1.3/plugin-install.html">
      <noembed>
   </comment>
   </noembed>
   </embed>

</object>