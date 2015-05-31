// ********************************************************************
// *  Filename:  PopupWindow.js
// *  Purpose :  This file contains a function to open and focus upon a Popup Window
// *  Author :   Jason Robbins
//*********************************************************************
var popupWindowAttributes = "height=600,width=700,menubar=yes,resizable=yes,scrollbars=yes, status=yes";
var popupWindowAttributesNoSize = "menubar=yes,resizable=yes,scrollbars=yes, status=yes";

function openDocs(resource, newWin)
{
    formWindow = window.open(resource, newWin, popupWindowAttributes);
    formWindow.focus();
}

function openDocsSized(resource, newWin, height, width, message)
{
    formWindow = window.open(resource, newWin, "height="+height+",width="+width+","+popupWindowAttributesNoSize);
    formWindow.focus();
    if (message)
    {
        formWindow.document.writeln(message);
    }
}

function openUrl(form, url) {
    var title = (new Date()).getTime();
    openDocsSized('', title, 600, 800);
    form.action = url;
    form.target = title;
    form.submit();
}