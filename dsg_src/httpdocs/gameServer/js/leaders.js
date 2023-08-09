function createCookie(name, value, days) {
   if (days) {
      var date = new Date();
      date.setTime(date.getTime() + (days * 24 * 60 * 60 * 1000));
      var expires = "; expires=" + date.toGMTString();
   } else var expires = "";
   document.cookie = name + "=" + value + expires + "; path=/";
}

function load() {
   var s = document.getElementById('game');
   var game = s.options[s.selectedIndex].value;

   document.rank.game.value = game;
   createCookie("g", game, 1);
   loadIt("/gameServer/leadersajax.jsp?g=" + game);
}

function loadIt(url) {
   // check for iframe
   var ifr = document.getElementById('lbf');
   var cd = document.getElementById('lb');
   var h = 0;
   if (cd.offsetHeight) h = cd.offsetHeight;
   else if (cd.pixelHeight) h = cd.pixelHeight;

   // if it doesn't exist, create it and load the desired url
   if (!ifr) {
      var tempIFrame = document.createElement('iframe');
      tempIFrame.setAttribute('id', 'lbf');
      tempIFrame.style.border = '0px';
      tempIFrame.style.width = '0px';
      tempIFrame.style.height = '0px';
      tempIFrame.src = url;
      if (tempIFrame.attachEvent) {
         tempIFrame.attachEvent('onload', loadComplete);
      } else {
         tempIFrame.onload = loadComplete;
      }
      ifr = document.body.appendChild(tempIFrame);
   } else {
      // the iframe already exists, just load the url into the location.
      // using replace() keeps the history from being modified.
      var idoc = (ifr.contentWindow || ifr.contentDocument);
      if (idoc.document) {
         idoc = idoc.document;
      }
      idoc.location.replace(url);
   }

   cd.innerHTML = '';
   cd.style.height = h + "px";
   cd.className += " boxload";
   return ifr;
}

function loadComplete() {
   var ifr = document.getElementById('lbf');
   var idoc = (ifr.contentWindow || ifr.contentDocument);
   if (idoc.document) {
      idoc = idoc.document;
   }
   var cd = document.getElementById('lb');
   cd.innerHTML = idoc.body.innerHTML;
   cd.style.height = idoc.body.style.height;
   idoc.body.innerHTML = "";
   cd.className = cd.className.replace(/boxload/g, " ");
}

function moreStats() {
   document.rank.submit();
}