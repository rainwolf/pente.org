<%@ page import="java.util.*,
                 org.pente.gameServer.core.*" %>

<%
   DSGPlayerData dsgPlayerData = (DSGPlayerData) request.getAttribute("dsgPlayerData");
   if (dsgPlayerData == null) {
      throw new Exception("Illegal access attempted");
   }

   pageContext.setAttribute("title", "My Kids");
   pageContext.setAttribute("current", "My Profile");
%>
<%@ include file="begin.jsp" %>
<link rel="stylesheet" type="text/css" href="<%= request.getContextPath() %>/gameServer/forums/style.jsp"/>


<% String selectedTab = "My Kids"; %>
<%@ include file="tabs.jsp" %>

<a name="kids"></a>
<h2>My Kids</h2>

<div id="kidsStatus" style="margin:8px 0;font-weight:bold;"></div>
<div id="kidsRoot">Loading...</div>

<script type="text/javascript">
(function () {
   var URL = "/gameServer/kids";
   var LIVE_NOTE = "Live chat copies are best effort: if the server's chat buffer is unavailable, that session isn't copied.";
   var VERIFY_NOTE = "Verify your email address on My Info before adding kids.";
   var MANAGED_NOTE = "Managed by your parent";
   var root = document.getElementById("kidsRoot");
   var statusEl = document.getElementById("kidsStatus");

   function el(tag, text, attrs) {
      var e = document.createElement(tag);
      if (text != null) { e.textContent = text; }
      if (attrs) { for (var k in attrs) { e.setAttribute(k, attrs[k]); } }
      return e;
   }
   function setStatus(text, isError) {
      statusEl.textContent = text || "";
      statusEl.style.color = isError ? "#b00020" : "#1a6b1a";
   }
   function post(params) {
      var body = new URLSearchParams();
      for (var k in params) { body.append(k, params[k]); }
      return fetch(URL, {
         method: "POST",
         credentials: "same-origin",
         headers: {"Content-Type": "application/x-www-form-urlencoded"},
         body: body.toString()
      }).then(handle);
   }
   function handle(resp) {
      return resp.text().then(function (txt) {
         var j;
         try { j = JSON.parse(txt); } catch (e) { j = null; }
         if (!j) { setStatus("Unexpected response from the server.", true); return; }
         if (j.success) {
            setStatus(j.message || "", false);
            render(j);
         } else {
            setStatus(j.error || "Request failed.", true);
         }
      });
   }
   function load() {
      fetch(URL, {credentials: "same-origin"}).then(handle).catch(function () {
         setStatus("Could not reach the server.", true);
      });
   }
   function button(label, onclick) {
      var b = el("button", label, {type: "button"});
      b.addEventListener("click", onclick);
      return b;
   }
   function row(label, input) {
      var d = el("div", null, {style: "margin:4px 0;"});
      var l = el("label", label + " ");
      l.appendChild(input);
      d.appendChild(l);
      return d;
   }
   function approvedList(list, onRemove) {
      var ul = el("ul");
      if (!list || list.length === 0) {
         ul.appendChild(el("li", "No approved players yet."));
         return ul;
      }
      list.forEach(function (p) {
         var li = el("li", p.name + " ");
         if (onRemove) { li.appendChild(button("Remove", function () { onRemove(p); })); }
         ul.appendChild(li);
      });
      return ul;
   }

   function addKidForm() {
      var box = el("div", null, {style: "margin:16px 0;"});
      box.appendChild(el("h3", "Add a kid"));
      var n = el("input", null, {type: "text", maxlength: "20", autocomplete: "off"});
      var p = el("input", null, {type: "password", autocomplete: "new-password"});
      var c = el("input", null, {type: "password", autocomplete: "new-password"});
      box.appendChild(row("Name", n));
      box.appendChild(row("Password", p));
      box.appendChild(row("Confirm password", c));
      box.appendChild(button("Add kid", function () {
         if (p.value !== c.value) { setStatus("Passwords do not match.", true); return; }
         post({action: "create", kidName: n.value, kidPassword: p.value});
      }));
      return box;
   }
   function convertForm() {
      var box = el("div", null, {style: "margin:16px 0;"});
      box.appendChild(el("h3", "Convert an existing account"));
      var n = el("input", null, {type: "text", maxlength: "20", autocomplete: "off"});
      box.appendChild(row("Account name", n));
      box.appendChild(button("Send confirmation email", function () {
         post({action: "convert", kidName: n.value});
      }));
      return box;
   }
   function kidBox(k) {
      var box = el("div", null, {style: "margin:16px 0;padding:8px;border:1px solid #ccc;"});
      box.appendChild(el("h3", k.name));

      var pw = el("input", null, {type: "password", autocomplete: "new-password"});
      var pwRow = row("Set password", pw);
      pwRow.appendChild(button("Save password", function () {
         post({action: "password", kidPid: k.kidPid, kidPassword: pw.value});
      }));
      box.appendChild(pwRow);

      box.appendChild(el("h4", "Approved players"));
      box.appendChild(approvedList(k.whitelist, function (p) {
         post({action: "whitelistRemove", kidPid: k.kidPid, playerPid: p.pid});
      }));
      var ap = el("input", null, {type: "text", maxlength: "20", autocomplete: "off"});
      var apRow = row("Add player", ap);
      apRow.appendChild(button("Add", function () {
         post({action: "whitelistAdd", kidPid: k.kidPid, playerName: ap.value});
      }));
      box.appendChild(apRow);

      var cm = el("input", null, {type: "checkbox"});
      cm.checked = !!k.copyMessages;
      var cl = el("input", null, {type: "checkbox"});
      cl.checked = !!k.copyLiveChat;
      var ct = el("input", null, {type: "checkbox"});
      ct.checked = !!k.copyTbMessages;
      function saveSettings() {
         post({action: "settings", kidPid: k.kidPid,
               copyMessages: cm.checked ? "Y" : "N", copyLiveChat: cl.checked ? "Y" : "N",
               copyTbMessages: ct.checked ? "Y" : "N"});
      }
      cm.addEventListener("change", saveSettings);
      cl.addEventListener("change", saveSettings);
      ct.addEventListener("change", saveSettings);
      var l1 = el("label", " Email me copies of private messages");
      l1.insertBefore(cm, l1.firstChild);
      var l2 = el("label", " Email me copies of live game room chat");
      l2.insertBefore(cl, l2.firstChild);
      box.appendChild(el("div", null, {style: "margin:8px 0;"})).appendChild(l1);
      var d2 = box.appendChild(el("div", null, {style: "margin:8px 0;"}));
      d2.appendChild(l2);
      d2.appendChild(el("div", LIVE_NOTE, {style: "font-size:0.85em;color:#666;"}));
      var l3 = el("label", " Turn-based game messages");
      l3.insertBefore(ct, l3.firstChild);
      var d3 = box.appendChild(el("div", null, {style: "margin:8px 0;"}));
      d3.appendChild(l3);
      d3.appendChild(el("div", "One email per game when it ends, with every message from both players.", {style: "font-size:0.85em;color:#666;"}));

      box.appendChild(el("h4", "Active turn-based games"));
      var gl = el("ul");
      box.appendChild(gl);
      fetch(URL + "?action=activeGames&kidPid=" + encodeURIComponent(k.kidPid), {credentials: "same-origin"})
         .then(function (r) { return r.json(); })
         .then(function (g) {
            if (!g.success) { gl.appendChild(el("li", g.error || "Could not load games.")); return; }
            if (!g.games || g.games.length === 0) { gl.appendChild(el("li", "No active turn-based games.")); return; }
            g.games.forEach(function (x) {
               var li = el("li");
               li.appendChild(el("a", x.gameName + " vs " + x.opponentName,
                  {href: "/gameServer/tb/game?gid=" + encodeURIComponent(x.gid) + "&command=load"}));
               li.appendChild(document.createTextNode(" - " + (x.kidsTurn ? k.name : x.opponentName) + "'s turn" +
                  (x.lastMoveDate > 0 ? ", last move " + new Date(x.lastMoveDate).toLocaleString() : "")));
               gl.appendChild(li);
            });
         })
         .catch(function () { gl.appendChild(el("li", "Could not load games.")); });

      box.appendChild(button("Release", function () {
         if (confirm("Release " + k.name + "? They will become a normal account.")) {
            post({action: "release", kidPid: k.kidPid});
         }
      }));
      return box;
   }

   function render(j) {
      root.textContent = "";
      if (j.isKid) {
         root.appendChild(el("p", "Your account is managed by your parent, " + j.parentName + "."));
         root.appendChild(el("h3", "Players you can chat with"));
         root.appendChild(approvedList(j.whitelist, null));
         root.appendChild(el("p", MANAGED_NOTE, {style: "font-size:0.85em;color:#666;"}));
         return;
      }
      if (!j.emailVerified) { root.appendChild(el("p", VERIFY_NOTE)); }
      root.appendChild(addKidForm());
      root.appendChild(convertForm());
      (j.kids || []).forEach(function (k) { root.appendChild(kidBox(k)); });
   }

   load();
})();
</script>

<%@ include file="end.jsp" %>
