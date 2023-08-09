function loadDonor(url) {
   // check for iframe
   var ifr = document.getElementById('df');
   var cd = document.getElementById('db');
   var h = 0;
   if (cd.offsetHeight) h = cd.offsetHeight;
   else if (cd.pixelHeight) h = cd.pixelHeight;

   // if it doesn't exist, create it and load the desired url
   if (!ifr) {
      var tempIFrame = document.createElement('iframe');
      tempIFrame.setAttribute('id', 'df');
      tempIFrame.style.border = '0px';
      tempIFrame.style.width = '0px';
      tempIFrame.style.height = '0px';
      tempIFrame.src = url;
      if (tempIFrame.attachEvent) {
         tempIFrame.attachEvent('onload', loadCompleteDonor);
      } else {
         tempIFrame.onload = loadCompleteDonor;
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

   return ifr;
}

function loadCompleteDonor() {
   var ifr = document.getElementById('df');
   var idoc = (ifr.contentWindow || ifr.contentDocument);
   if (idoc.document) {
      idoc = idoc.document;
   }
   var cd = document.getElementById('db');
   cd.innerHTML = idoc.body.innerHTML;
   cd.style.height = idoc.body.style.height;
   idoc.body.innerHTML = "";
}