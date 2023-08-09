<%@ page import="com.jivesoftware.base.JiveGlobals" %>
<%
   /*
    * $RCSfile: style.jsp,v $
    * $Revision: 1.41.2.4 $
    * $Date: 2003/03/28 22:35:12 $
    *
    * Copyright (C) 1999-2002 Jive Software. All rights reserved.
    *
    * This software is the proprietary information of Jive Software. Use is subject to license terms.
    */
%>

<%! // This method exists just to shorten the call to retrieve properties

   private String getProp(String name) {
      return JiveGlobals.getJiveProperty("skin.default." + name);
   }
%>

<% // Set the content type of the this page to be CSS
   String contentType = "text/css";
   response.setContentType(contentType);
%>

/* Global Jive Forums 3.x Stylesheet */

/* Styles for existing HTML elements */
<%--
BODY {
    background-color : <%= getProp("bgColor") %>;
    font-size : 100%;
}
BODY, TD, TH {
    font-family : <%= getProp("fontFace") %>;
    font-size : <%= getProp("fontSize") %>;
    color : <%= getProp("textColor") %>;
}
PRE {
    font-size : 100%;
}
A {}
A:link {
    color : <%= getProp("linkColor") %>;
}
A:visited {
    color : <%= getProp("vLinkColor") %>;
}
A:hover {
    color : <%= getProp("aLinkColor") %>;
    text-decoration : none;
}
A:active {
    color : <%= getProp("aLinkColor") %>;
}
--%>
/* Custom styles */

.jive-header TABLE {
border : 1px <%= getProp("headerBorderColor") %> solid;
background-color : <%= getProp("headerBgColor") %>;
}
.jive-breadcrumbs, .jive-breadcrumbs A {
color : <%= getProp("breadcrumbColor") %> !important;
font-weight : bold;
}
.jive-breadcrumbs A:hover {
color : <%= getProp("breadcrumbColorHover") %> !important;
}
.jive-list {
border : 1px <%= getProp("borderColor") %> solid;
}
.jive-list TH {
background-color : <%= getProp("tableHeaderBgColor") %>;
color : <%= getProp("tableHeaderColor") %>;
}

#jive-cat-forum-list .jive-category-name {
background-color : #eee;
}
#jive-cat-forum-list TH, .jive-list .jive-date, .jive-list .jive-author, #jive-reply-tree .jive-author
{
white-space : nowrap;
}
#jive-cat-forum-list TH, .jive-list .jive-counts, .jive-list .jive-date, .jive-list .jive-author, #jive-reply-tree .jive-author
{
padding-left : 6px;
padding-right : 6px;
}
#jive-cat-forum-list .jive-even, #jive-cat-forum-list .jive-odd {
background-color : <%= getProp("evenColor") %>;
}
#jive-reply-tree .jive-bullet {
padding-right : 5px;
}

.jive-category-name {
font-weight : bold;
}
.jive-list .jive-counts {
text-align : center;
}
.jive-description {
font-family : <%= getProp("descrFontFace") %>;
font-weight : normal;
font-size : <%= getProp("descrFontSize") %>;
}

.jive-odd {
background-color : <%= getProp("oddColor") %>;
}
.jive-even {
background-color : <%= getProp("evenColor") %>;
}

.jive-page-title {
font-size : 1.2em;
font-weight : bold;
}
.jive-bullet {
text-align : center;
}
#jive-footer TD {
font-size : 0.7em;
font-weight : bold;
text-align : center;
border-top : 1px <%= getProp("borderColor") %> solid;
padding-top : 5px;
}
#jive-footer TD A {
color : #666;
text-decoration : none;
}
#jive-footer TD A:hover {
text-decoration : underline;
}
.jive-error-text {
color : #f00;
}
.jive-label {
text-align : right;
}
.jive-subject {
font-weight : bold;
}
.jive-box {
border : 1px <%= getProp("borderColor") %> solid;
}
.jive-message-list TH, .jive-message TH {
background-color : <%= getProp("tableHeaderBgColor") %>;
text-align : left;
color : <%= getProp("tableHeaderColor") %>;
}
.jive-message-list .jive-odd, .jive-message {
background-color : <%= getProp("oddColor") %>;
}
.jive-message-list .jive-even {
background-color : <%= getProp("evenColor") %>;
}
.jive-button .jive-button-label {
padding-right : 5px;
}
.jive-sidebar .jive-box TH {
text-align : left;
left-padding : 6px;
background-color : #eee;
border-bottom : 1px <%= getProp("borderColor") %> solid;
}
.jive-sidebar .jive-box {
border : 1px <%= getProp("borderColor") %> solid;
}
.jive-account-box .jive-box {
border : 0px !important;
}
.jive-account-box .jive-box TD {
padding-bottom : 6px;
}
.jive-account-box .jive-box TH {
text-align : left;
border-bottom : 1px <%= getProp("borderColor") %> solid;
}
.jive-account-form .jive-required {
font-weight : bold;
}
.jive-account-form .jive-label {
text-align : left;
}
.jive-last-post {
font-family : verdana;
font-weight : normal;
font-size : 0.8em;
}
.jive-info-text {
color : #060;
}

/* tabs */
.jive-selected-tab {
border-width : 1px 1px 0px 1px;
background-color : #fff;
}
.jive-tab {
border-width : 2px 1px 1px 1px;
}
.jive-tab:hover {
background-color : #eee;
border-top : 2px #999 solid;
}
.jive-tab A:hover, .jive-selected-tab A:hover {
text-decoration : none !important;
}
.jive-tab-spacer, .jive-tab-spring {
border-width : 0px 0px 1px 0px;
}
.jive-tab-bar {
background-color : #fff;
border-width : 0px 1px 1px 1px;
}
.jive-selected-tab, .jive-tab, .jive-tab-spacer, .jive-tab-spring, .jive-tab-bar
{
border-color : #bbb;
border-style : solid;
}
.jive-tab, .jive-selected-tab {
padding : 4px 10px 4px 10px;
font-family : tahoma;
font-size : 1em;
}
.jive-selected-tab A {
color : #000 !important;
text-decoration : none;
font-weight : bold;
}
.jive-tab A {
color : #333 !important;
text-decoration : none;
font-weight : bold;
}
.jive-tab A:hover, .jive-selected-tab A:hover {
text-decoration : underline;
}
.jive-tab {
background-color : #ddd;
}
.jive-tab-bar TD {
font-family : tahoma;
font-weight : bold;
}
.jive-tab-bar A {
color : #000;
text-decoration : none;
}
.jive-tab-bar A:hover {
text-decoration : underline;
}
.jive-tab-section TD {
font-weight : normal;
font-family : verdana;
font-size : 0.7em;
}
.jive-tab-section A {
color : #333 !important;
padding-right : 6px;
font-weight : normal;
}
.jive-tab-spring {
font-size : 0.7em;
}
.jive-tab-logout {
font-size : 0.7em;
}

#jive-reply-tree .jive-odd {
background-color : <%= getProp("oddColor") %>;
}
#jive-reply-tree .jive-even {
background-color : <%= getProp("evenColor") %>;
}
#jive-reply-tree .jive-current, #jive-reply-tree .jive-current A {
background-color : <%= ((getProp("activeColor") == null) ? "#ffc" : getProp("activeColor")) %>;
font-weight : bold;
}
#jive-reply-tree .jive-list {
border : 1px <%= getProp("borderColor") %> solid;
}
#jive-reply-tree TH {
background-color : <%= getProp("tableHeaderBgColor") %>;
color : <%= getProp("tableHeaderColor") %>;
}

.jive-message .jive-box {
border : 1px <%= getProp("borderColor") %> solid;
}
.jive-message .jive-box TD {
background-color : #eee;
}

.jive-message-content .jive-subject-row {
border-bottom : 1px <%= getProp("borderColor") %> solid;
}

.jive-search-form TH {
text-align : left;
border-bottom : 1px <%= getProp("borderColor") %> solid;
}
.jive-search-result .jive-info {
color : #999;
}
.jive-search-result .jive-body {
}
.jive-search-result .jive-hilite {
background-color : #ff0;
font-weight : bold;
}

/* add a little more space next to the by: of the last post */
#jive-topic-list .jive-last-post {
padding-left : 5px;
}

/* Control Panel styles */
.jive-cp-formbox TABLE {
padding-left : 25px;
}
.jive-cp-header {
font-weight : bold;
}
.jive-cp-formbox .jive-label {
text-align : left;
padding-top : 5px;
}

/* Paginator styles */
.jive-paginator .jive-current, .jive-message-list-footer .jive-paginator .jive-current {
background-color : #eee;
text-decoration : none;
font-weight : bold;
color : #000 !important;
}
.jive-message-list .jive-paginator A {
color : #fff;
}
.jive-footer .jive-paginator .jive-current {
background-color : #eee;
text-decoration : none;
font-weight : bold;
color : #000 !important;
}
.jive-message-list .jive-footer .jive-paginator A {
color : #000;
}
.jive-paginator-bottom .jive-paginator .jive-current {
background-color : #eee;
text-decoration : none;
font-weight : bold;
color : #000 !important;
}
.jive-paginator-bottom .jive-paginator A {
color : #000 !important;
}

/* post form */
.jive-post-form .jive-font-buttons INPUT {
background-color : #eee;
font-size : 0.8em;
font-family : verdana;
height : 22px;
border-width : 2px;
border-top-color : #ddd;
border-right-color : #ccc;
border-bottom-color : #ccc;
border-left-color : #ddd;
}

/* profile page */
.jive-profile TH {
text-align : left;
border-bottom : 1px <%= getProp("borderColor") %> solid;
}
.jive-profile .jive-label {
text-align : left;
}

/* watches page */
.jive-watch-list TH {
background-color : <%= getProp("tableHeaderBgColor") %>;
color : <%= getProp("tableHeaderColor") %>;
}
.jive-watch-list .jive-name {
text-align : left;
}
.jive-watch-list .jive-delete, .jive-watch-list .jive-delete-button {
background-color : #eee;
}
.jive-watch-list .jive-even {
background-color : <%= getProp("evenColor") %>;
}
.jive-watch-list .jive-odd {
background-color : <%= getProp("oddColor") %>;
}

/* help page */
.jive-faq-answer {
font-weight : bold;
}

/* attachments */
.jive-attachment-list TD {
font-size : 0.7em !important;
}
.jive-edit-attach-list TH {
background-color : <%= getProp("tableHeaderBgColor") %>;
color : <%= getProp("tableHeaderColor") %>;
padding-left : 10px;
padding-right : 10px;
}

/* spell checking */
.jive-spell-error-current, .jive-spell-error {
color : #f00;
border-bottom : #f00 2px dotted;
}
.jive-spell-error-current {
background-color : #eee;
font-weight : bold;
}
.jive-spell-form .jive-spell-button {
width : 100%;
background-color : #eee;
font-size : 0.8em;
font-family : verdana,arial,helvetica,sans-serif;
padding : 2px 6px 2px 6px;
}
.jive-spell-form .jive-box TH {
background-color : <%= getProp("tableHeaderBgColor") %>;
color : <%= getProp("tableHeaderColor") %>;
}

/* Guest styles */
.jive-guest {
font-style : italic !important;
}
