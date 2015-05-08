<%@ page import="java.util.*,
                 java.text.*,
                 org.pente.game.*, 
                 org.pente.gameServer.core.*, 
                 org.pente.gameServer.client.web.*" %>

<style type="text/css">
  .box { width:200px; }
</style>

<script type="text/javascript">
addLoadEvent(function(){setInterval("loadDonor('/gameServer/randomdonorajax.jsp')", 25000);});
</script>
<script type="text/javascript" src="/gameServer/js/donor.js"></script>

<div class="box">
  <div class="boxhead">
    <h4>Pente.org Donors</h4>
    <p style="padding:0;margin:0;color:white;font-size:10px">Thank you for your support!</p>
  </div>
  <div id="db" class="boxcontents" style="text-align:center"> 
     <%@ include file="randomdonor.jsp" %>
   </div>
</div>