<%@ page import="com.jivesoftware.forum.action.AttachAction,
                 com.jivesoftware.util.StringUtils"%>
<%@ page import="net.tanesha.recaptcha.ReCaptchaImpl" %>
<%@ page import="net.tanesha.recaptcha.ReCaptchaResponse" %>

<%
/**
 *	$RCSfile: attachform.jsp,v $
 *	$Revision: 1.8 $
 *	$Date: 2002/12/20 23:12:48 $
 */
%>

<%@ include file="global.jsp" %>

<%@ taglib uri="webwork" prefix="ww" %>
<%@ taglib uri="jivetags" prefix="jive" %>

<jsp:include page="header.jsp" flush="true" />

<script language="JavaScript" type="text/javascript" src="utils.js"></script>

<table cellpadding="0" cellspacing="0" border="0" width="100%">
<tr valign="top">
    <td width="98%">

        <%-- Breadcrumbs (customizable via the admin tool) --%>

        <jsp:include page="breadcrumbs.jsp" flush="true" />

        <%-- Page title --%>

        <p class="jive-page-title">
        <%-- Post Message: Attach Files --%>
        <jive:i18n key="attach.title" />
        </p>

    </td>
    <td width="1%"><img src="images/blank.gif" width="10" height="1" border="0"></td>
    <td width="1%">

        <%@ include file="accountbox.jsp" %>

    </td>
</tr>
</table>

<%-- reCaptcha checking --%>
<%! Boolean validCaptcha = false; %> 
<ww:if test="$recaptcha_challenge_field">
      <%
        String remoteAddr = request.getRemoteAddr();
        ReCaptchaImpl reCaptcha = new ReCaptchaImpl();
        reCaptcha.setPrivateKey("6LdZFuYSAAAAAML0nbMAHbkzC7U6uy19FHkQUv1p");

        String challenge = request.getParameter("recaptcha_challenge_field");
        String uresponse = request.getParameter("recaptcha_response_field");
        ReCaptchaResponse reCaptchaResponse = reCaptcha.checkAnswer(remoteAddr, challenge, uresponse);
        if (!reCaptchaResponse.isValid()) { %>
                <table class="jive-error-message" cellpadding="3" cellspacing="2" border="0" width="350">
                <tr valign="top">
                    <td width="1%"><img src="images/error-16x16.gif" width="16" height="16" border="0"></td>
                    <td width="99%">
                        <font color="red">Wrong Captcha, please try again.</font>
                    </td>
                </tr>
                </table>
                <jsp:include page="footer.jsp" flush="true" />            
        <% } else { %>
            
            <p>
            <%-- Use the form below to attach files to this message. --%>
            <jive:i18n key="attach.description" />
            </p>
            
            <ww:if test="hasErrorMessages == true">
            
                <span class="jive-error-text">
                <ww:iterator value="errorMessages">
                    <ww:property />
                </ww:iterator>
                </span>
                <br><br>
            
            </ww:if>
            
            <script language="JavaScript" type="text/javascript">
            <!--
            var clicked = false;
            function isClicked() {
                if (!clicked) { clicked = true; return true; }
                return false;
            }
            //-->
            </script>
            
            <form action="attach!execute.jspa?forumID=<ww:property value="$forumID" /><ww:if test="$threadID">&threadID=<ww:property value="$threadID" /></ww:if><ww:if test="$messageID">&messageID=<ww:property value="$messageID" /></ww:if>&reply=<ww:property value="reply" />"
             method="post" enctype="multipart/form-data" onsubmit="return isClicked();">
            
            <%  // Get the action for this view.
                AttachAction action = (AttachAction)getAction(request);
            %>
            
            <input type="hidden" name="encSubject" value="<%= StringUtils.encodeHex(action.getSubject().getBytes()) %>">
            <input type="hidden" name="encBody" value="<%= StringUtils.encodeHex(action.getBody().getBytes()) %>">
            
            
            <table cellpadding="3" cellspacing="2" border="0">
            <ww:bean name="'com.jivesoftware.webwork.util.Counter'" id="counter">
                <ww:param name="'first'" value="1" />
                <ww:param name="'last'" value="attachmentManager/maxAttachmentsPerMessage" />
                <ww:iterator>
                <tr>
                    <td>
                        <%-- File 1 [2, 3, 4 ...]: --%>
                        <jive:i18n key="attach.file_in_sequence">
                            <jive:arg>
                                <ww:property />
                            </jive:arg>
                        </jive:i18n>
                    </td>
                    <td>
                        <input type="file" name="attachFile<ww:property />" size="40">
                    </td>
                </tr>
                <ww:if test="errors[.]">
                    <tr>
                        <td>&nbsp;</td>
                        <td>
                            <span class="jive-error-text">
                            <ww:property value="errors[.]" />
                            </span>
                        </td>
                    </tr>
                </ww:if>
                </ww:iterator>
            </ww:bean>
            </table>
            
            <br>
            
            <%-- Attach Files &amp; Post Message --%>
            <input type="submit" name="doAttach" value="<jive:i18n key="attach.attach_post" />">
            
            <%-- Cancel --%>
            <input type="submit" name="doCancel" value="<jive:i18n key="global.cancel" />">
            
            </form>
            
            <jsp:include page="footer.jsp" flush="true" />
        <% }
      %>
</ww:if>

<ww:if test="!$recaptcha_challenge_field">
    I am not sure how you got here, but this is not supposed to happen.
</ww:if>