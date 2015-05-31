<%
/**
 *	$RCSfile: viewpreview.jsp,v $
 *	$Revision: 1.11 $
 *	$Date: 2003/01/09 06:04:00 $
 */
%>

<%@ page import="com.jivesoftware.base.*,
                 com.jivesoftware.forum.*,
                 com.jivesoftware.util.*,
                 java.util.ArrayList" %>


<%@ page import="net.tanesha.recaptcha.ReCaptchaImpl" %>
<%@ page import="net.tanesha.recaptcha.ReCaptchaResponse" %>


<%@ taglib uri="webwork" prefix="ww" %>
<%@ taglib uri="jivetags" prefix="jive" %>

<%@ include file="global.jsp" %>

<%@ include file="header.jsp" %>

<table cellpadding="0" cellspacing="0" border="0" width="100%">
<tr valign="top">
    <td width="98%">

        <%-- Breadcrumbs (customizable via the admin tool) --%>

        <jsp:include page="breadcrumbs.jsp" flush="true" />

        <%-- Page title --%>

        <p>
        <span class="jive-page-title">
        <ww:if test="reply == true">

            <%-- Message Preview: Reply --%>
            <jive:i18n key="preview.post_reply" />

        </ww:if>
        <ww:else>

            <%-- Message Preview: New Topic --%>
            <jive:i18n key="preview.post_new" />

        </ww:else>
        </span>
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
        reCaptcha.setPrivateKey("***REMOVED***");

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
                <%-- preview description --%>
                <p>
                <jive:i18n key="preview.description" />
                </p>
                
                <br>
                
                <form action="post!execute.jspa" method="post">
                
                <input type="hidden" name="forumID" value="<ww:property value="$forumID" />">
                <ww:if test="$threadID">
                    <input type="hidden" name="threadID" value="<ww:property value="$threadID" />">
                </ww:if>
                <ww:if test="$messageID">
                    <input type="hidden" name="messageID" value="<ww:property value="$messageID" />">
                </ww:if>
                <input type="hidden" name="reply" value="<ww:property value="reply" />">
                <input type="hidden" name="from" value="preview">
                
                <span class="jive-preview-box">
                <table class="jive-box" cellpadding="3" cellspacing="2" border="0" width="100%">
                <tr valign="top" class="jive-odd">
                    <td width="1%">
                        <table cellpadding="0" cellspacing="0" border="0" width="200">
                        <tr>
                            <td>
                                <ww:if test="pageUser">
                
                                    <a href="<%= request.getContextPath() %>/gameServer/profile?viewName=<ww:property value="pageUser/username" />"
                                     ><ww:property value="pageUser/username" /></a>
                                    <br><br>
                                    <%-- Posts: --%>
                                    <jive:i18n key="global.posts" /><jive:i18n key="global.colon" />
                                    <ww:property value="numberFormat/format(forumFactory/userMessageCount(pageUser))" />
                                    <br>
                                    <%-- Registered --%>
                                    <jive:i18n key="global.registered" /><jive:i18n key="global.colon" />
                                    <ww:property value="shortDateFormat/format(pageUser/creationDate)" />
                
                
                                    <ww:if test="pageUser/property('dsgLocation')">
                                      <br>
                                      <%-- Location: --%>
                                      <jive:i18n key="global.from" /><jive:i18n key="global.colon" />
                                      <ww:property value="pageUser/property('dsgLocation')" />
                  
                                    </ww:if>
                
                                    <ww:if test="pageUser/property('dsgAge')">
                                      <br>
                                        Age<jive:i18n key="global.colon" />
                                        <ww:property value="pageUser/property('dsgAge')" />
                                    </ww:if>
                                    <ww:if test="pageUser/property('dsgHomePage')">
                                      <br>
                                       <a href='<ww:property value="pageUser/property('dsgHomePage')" />'>Home page</a><br>
                                    </ww:if>
                                    <ww:if test="pageUser/property('dsgAvatar')">
                                      <br>
                                      <img src='<%= request.getContextPath() %>/gameServer/avatar?name=<ww:property value="pageUser/username" />'>
                                    </ww:if>
                 
                
                                </ww:if>
                                <ww:else>
                
                                    <%-- Guest --%>
                                    <i><jive:i18n key="global.guest" /></i>
                
                                </ww:else>
                            </td>
                        </tr>
                        </table>
                    </td>
                
                    <ww:property value="previewedMessage">
                
                        <td width="99%">
                            <table cellpadding="0" cellspacing="0" border="0" width="100%">
                            <tr>
                                <td width="99%">
                                    <span class="jive-subject">
                                    <ww:property value="subject" />
                                    </span>
                                    <br>
                                    <%-- posted: --%>
                                    <jive:i18n key="global.posted" /><jive:i18n key="global.colon" />
                                    <ww:property value="dateFormat/format(creationDate)" />
                                </td>
                                <td width="1%">
                                    <table cellpadding="3" cellspacing="2" border="0">
                                    <tr>
                                        <td>
                                            <img src="images/reply-16x16.gif" width="16" height="16" border="0">
                                        </td>
                                        <td>
                                            <%-- Reply --%>
                                            <jive:i18n key="global.reply" />
                                        </td>
                                    </tr>
                                    </table>
                                </td>
                            </tr>
                            <tr>
                                <td colspan="2" style="border-top: 1px #ccc solid;">
                                    <br>
                                    <ww:property value="body" />
                                    <br><br>
                                </td>
                            </tr>
                
                        <ww:if test="pageUser/property('dsgSig') != ''" >
                        <tr valign="bottom">
                            <td colspan="2" style="border-top: 1px #ccc solid;">
                               <ww:property value="pageUser/property('dsgSig')" />
                            </td>
                        </tr>
                        </ww:if>
                            </table>
                        </td>
                
                    </ww:property>
                
                </tr>
                </table>
                </span>
                
                <br>
            <%-- go back/edit --%>
            <input type="submit" name="doGoBack" value="<jive:i18n key="global.go_back_or_edit" />">
            <%-- Post message --%>
            <input type="submit" name="doPost" value="<jive:i18n key="global.post_message" />">
            </form>
            
            <jsp:include page="footer.jsp" flush="true" />            
            <% }
      %>
</ww:if>

<ww:if test="!$recaptcha_challenge_field">
    I am not sure how you got here, but this is not supposed to happen.
</ww:if>