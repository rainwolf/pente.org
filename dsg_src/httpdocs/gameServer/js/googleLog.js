var iFr;
var page;
var logUrl;
function AdSenseLog()
{ 
    alert('AdSenseLog');
    var theAd=""+ escape(window.status.substring(6)) ;
    var format=queryString('format',iFr.src);
    var channel=queryString('channel',iFr.src);
    alert('about to create bug');
    var bug = new Image();
    bug.src = logUrl + '?ref=' + document.referrer + '&url=' +theAd +'&page=' + page + '&format=' + format +'&channel='+channel ;
}  
 
function queryString(item, source)
{
   var itemLoc=source.indexOf(item);
   var newSrc=source.substring(itemLoc);
   var endLoc=newSrc.indexOf("&");
   var lstSrc=newSrc.substring(0,endLoc);
   var itm=lstSrc.substring(item.length+1);
   return itm;
} 

//alert('attaching w3c');

//alert('looking for iframes');
//var elements = document.getElementsByTagName("iframe"); 
//for (var i = 0; i < elements.length; i++) 
//{ 
//  if(elements[i].src.indexOf('googlesyndication.com') > -1) 
//  {
//    if (document.addEventListener) {
//      alert('attaching w3c');
//      elements[i].addEventListener("focus",AdSenseLog,true);
//    }
//    else {
//      alert('attaching ie');
//      elements[i].attachEvent("onfocus",AdSenseLog)
//    }
//    iFr=elements[i];
//  } 
//}