<!-- Hide from other browsers
// *********************************************************************
// *  Filename: w_ext_helpwin.js
// *  Purpose:  Provides common functions for the Help Window in external server
// ----------------------------------------------------------------------
//  * Function:    helpwin
//  * Tag:         Opens and focuses upon the Help Window
//  * Outputs:     Opens a browser window and populates it with the
//  *              specified HTML file(s).
//  * Assumptions: - If the control_html argument is empty, then the
//  *                resulting window will not be comprised of frames;
//  *                only the text_html file named will be displayed
//  *                in the this opened window.
//  *              - If the control_html argument is not empty, two
//  *                two frames are created in this opened window with
//  *                the control_html file in the top frame and the
//  *                text_html file in the bottom frame.
//  *              - If an anchor is supplied (the jumpto argument), 
//  *                the browser will focus on the anchor in the 
//  *                text frame.
//  *
//  * Maintenance:
// ----------------------------------------------------------------------

var helpWindowAttributes = "height=400,width=600,menubar=1,resizable=1,scrollbars=1";

function helpwin(control_html, text_html, jumpto)
{
var urlpath = window.location.protocol + "//" + window.location.host + "/ext/"; 

    // Opens the help window if it doesn't currently exist; the handle is returned, otherwise.
    helpWindow = window.open('', 'HelpWin', helpWindowAttributes);

    control = urlpath + control_html;
    text = urlpath + text_html;

    // If control HTML argument IS empty, generate the Help window without frames using
    // the specified text HTML file.
    if (control_html == '') {
        helpWindow.document.location = text + "#" + jumpto;
    }
    else {
        helpWindow.document.writeln('<html>');
        helpWindow.document.writeln('<head>');
        helpWindow.document.writeln('<title>NPDB-HIPDB Help</title>');
        helpWindow.document.writeln('</head>');
        helpWindow.document.writeln('<frameset rows="56,*" border="false" bordersize="0" frameborder="0" framespacing="0">');
        helpWindow.document.writeln('    <frame name="control" src="' + control + '" scrolling="no" marginwidth="0" marginheight="0"  noresize>');
        helpWindow.document.writeln('    <frame name="text" src="' + text + '#' + jumpto + '">');
        helpWindow.document.writeln('</frameset>');
        helpWindow.document.writeln('</html>');
    }

    helpWindow.document.close();
    helpWindow.focus();
}


// ----------------------------------------------------------------------
//  * Function:    closehelpwin
//  * Tag:         Closes the Help Window
//  * Outputs:     None
//  * Assumptions:
//  * Maintenance:
// ----------------------------------------------------------------------
function closehelpwin()
{
    // Get the handle to the Help Window, if it exists, then close it.
    helpWindow = window.open('', 'HelpWin', helpWindowAttributes);
    helpWindow.close();
}


// Stop hiding from other browsers  -->

