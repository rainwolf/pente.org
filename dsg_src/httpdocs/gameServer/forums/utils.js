<!--
var pc = navigator.userAgent.toLowerCase();
var ie4_win = (pc.indexOf("win")!=-1) && (pc.indexOf("msie") != -1)
    && (parseInt(navigator.appVersion) >= 4);
var checked = false;
function checkPost() {
    if (!checked) {
        checked = true;
        return true;
    }
    return false;
}
function styleTag(tag, ta) {
    var tagOpen = '[' + tag.toLowerCase() + ']';
    var tagClose = '[/' + tag.toLowerCase() + ']';
    if (ie4_win) {
        var selected = document.selection.createRange().text;
        if (selected) {
            var addSpace = false;
            if (selected.charAt(selected.length-1) == ' ') {
                selected = selected.substring(0, selected.length-1);
                addSpace = true;
            }
            document.selection.createRange().text
                    = tagOpen + selected + tagClose + ((addSpace)?" ":"");
        } else {
            ta.value += tagOpen + tagClose;
        }
    } else {
        ta.value += tagOpen + tagClose;
    }
    ta.focus();
    return;
}
function caret(ta) {
    if (ie4_win && ta.createTextRange) {
        ta.caretPos = document.selection.createRange().duplicate();
    }
}
//-->