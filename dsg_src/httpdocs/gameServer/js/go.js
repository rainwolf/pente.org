function goWH(url) {
   var myWidth = 0, myHeight = 0;
   if (typeof (window.innerWidth) == 'number') {
      //Non-IE
      myWidth = window.innerWidth;
      myHeight = window.innerHeight;
   } else if (document.documentElement && (document.documentElement.clientWidth || document.documentElement.clientHeight)) {
      //IE 6+ in 'standards compliant mode'
      myWidth = document.documentElement.clientWidth;
      myHeight = document.documentElement.clientHeight;
   } else if (document.body && (document.body.clientWidth || document.body.clientHeight)) {
      //IE 4 compatible
      myWidth = document.body.clientWidth;
      myHeight = document.body.clientHeight;
   } else if (screen.width) {
      // try using the resolution instead
      myWidth = screen.width - 100;
      myHeight = screen.height - 100;
   }
   if (myWidth > 0 && myHeight > 0) {
      myWidth -= 50;
      myHeight -= 100;
      if (myWidth > myHeight + 150) {
         myWidth = myHeight + 150;
      } else if (myHeight > myWidth - 100) {
         myHeight = myWidth - 100;
      }
      window.open(url + "&w=" + myWidth + "&h=" + myHeight, "_self");
   } else {
      window.open(url, "_self");
   }
}