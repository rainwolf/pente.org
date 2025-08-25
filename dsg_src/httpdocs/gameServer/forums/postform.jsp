<%--
  - $RCSfile: postform.jsp,v $
  - $Revision: 1.25.4.1 $
  - $Date: 2003/03/21 20:33:17 $
  -
  - Copyright (C) 2002-2003 Jive Software. All rights reserved.
  -
  - This software is the proprietary information of Jive Software. Use is subject to license terms.
--%>

<%@ page import="net.tanesha.recaptcha.ReCaptchaImpl" %>
<%@ page import="net.tanesha.recaptcha.ReCaptchaResponse" %>

<%@ include file="global.jsp" %>

<%@ taglib uri="webwork" prefix="ww" %>
<%@ taglib uri="jivetags" prefix="jive" %>

<jsp:include page="header.jsp" flush="true"/>

<script language="JavaScript" type="text/javascript" src="utils.js"></script>


<table cellpadding="0" cellspacing="0" border="0" width="100%">
   <tr valign="top">
      <td width="98%">

         <%-- Breadcrumbs (customizable via the admin tool) --%>

         <jsp:include page="breadcrumbs.jsp" flush="true"/>

         <%-- Page title --%>

         <ww:if test="reply == true">

            <p class="jive-page-title">
                  <%-- Post Message: Reply --%>
               <jive:i18n key="post.title_reply"/>
            </p>

            <%-- Post a reply in forum: {forum name}, to message: {message subject} --%>
            <jive:i18n key="post.reply_in_forum_to_message">
               <jive:arg>
                  <a href="forum.jspa?forumID=<ww:property value="$forumID" />"><ww:property value="forum/name"/></a>
               </jive:arg>
               <jive:arg>
                  <a href="thread.jspa?forumID=<ww:property value="$forumID" />&threadID=<ww:property value="$threadID" />&messageID=<ww:property value="message/ID" />#<ww:property value="message/ID" />"
                  ><ww:property value="message/subject"/></a>
               </jive:arg>
            </jive:i18n>

         </ww:if>
         <ww:else>

            <p class="jive-page-title">
                  <%-- Post Message: New Topic --%>
               <jive:i18n key="post.title_new"/>
            </p>

            <%-- Post new topic in forum: {forum name} --%>
            <jive:i18n key="post.post_in_forum">
               <jive:arg>
                  <a href="forum.jspa?forumID=<ww:property value="$forumID" />&start=0"><ww:property
                     value="forum/name"/></a>
               </jive:arg>
            </jive:i18n>

         </ww:else>

      </td>
      <td width="1%"><img src="images/blank.gif" width="10" height="1" border="0"></td>
      <td width="1%">

         <%@ include file="accountbox.jsp" %>

      </td>
   </tr>
</table>

<p>
   <ww:if test="reply == true">

      <%--
          Type a reply to the topic using the form below. When finished, you can optionally preview your reply by
          clicking on the "Preview" button. Otherwise, click the "Post Message" button to submit your message immediately.
      --%>
      <jive:i18n key="post.reply_description"/>

   </ww:if>
   <ww:else>

      <%--
          Type your message using the form below. When finished, you can optionally preview your post
          by clicking on the "Preview" button. Otherwise, click the "Post Message" button to submit
          your message immediately.
      --%>
      <jive:i18n key="post.new_description"/>
   </ww:else>
</p>


<ww:if test="hasErrors == true || hasErrorMessages == true">
   <table class="jive-error-message" cellpadding="3" cellspacing="2" border="0" width="350">
      <tr valign="top">
         <td width="1%"><img src="images/error-16x16.gif" width="16" height="16" border="0"></td>
         <td width="99%">

            <span class="jive-error-text">

            <ww:if test="errors['subject']">
               <%-- Please enter a subject. --%>
               <jive:i18n key="post.error_subject"/>
            </ww:if>
            <ww:elseIf test="errors['body']">
               <%-- You can not post a blank message. Please type your message and try again. --%>
               <jive:i18n key="post.error_body"/>
            </ww:elseIf>
            <ww:else>
               <ww:iterator value="errorMessages">
                  <ww:property/> <br>
               </ww:iterator>
            </ww:else>

            </span>

         </td>
      </tr>
   </table>

   <br>

</ww:if>

<ww:if test="hasInfoMessages == true">

   <table class="jive-info-message" cellpadding="3" cellspacing="2" border="0">
      <tr valign="top">
         <td width="1%"><img src="images/info-16x16.gif" width="16" height="16" border="0"></td>
         <td width="99%">

            <span class="jive-info-text">

            <ww:iterator value="infoMessages">
               <ww:property/> <br>
            </ww:iterator>

            </span>

         </td>
      </tr>
   </table>

   <br>

</ww:if>

<ww:if test="hasWarningMessages == true">

   <table class="jive-warning-message" cellpadding="3" cellspacing="2" border="0">
      <tr valign="top">
         <td width="1%"><img src="images/warning-16x16.gif" width="16" height="16" border="0"></td>
         <td width="99%">

            <span class="jive-warning-text">

            <ww:iterator value="warningMessages">
               <ww:property/> <br>
            </ww:iterator>

            </span>

         </td>
      </tr>
   </table>

   <br>

</ww:if>

<form action="post!post.jspa" method="post" name="postform" onsubmit="return checkPost();">
   <input type="hidden" name="forumID" value="<ww:property value="$forumID" />">
   <ww:if test="$threadID">
      <input type="hidden" name="threadID" value="<ww:property value="$threadID" />">
   </ww:if>
   <ww:if test="$messageID">
      <input type="hidden" name="messageID" value="<ww:property value="$messageID" />">
   </ww:if>
   <ww:if test="reply == true">
      <input type="hidden" name="reply" value="<ww:property value="true" />">
   </ww:if>

   <ww:bean name="'com.jivesoftware.webwork.util.Counter'" id="tabIndex"/>

   <span class="jive-post-form">

<table cellpadding="3" cellspacing="2" border="0">
<ww:if test="guest == true">

    <tr>
        <td class="jive-label">
            <%-- Name: --%>
            <jive:i18n key="global.name"/><jive:i18n key="global.colon"/>
        </td>
        <td>
            <input type="text" name="name" size="30" maxlength="75" tabindex="<ww:property value="@tabIndex/next" />"
                   value="<ww:if test="name"><ww:property value="name" /></ww:if>">
        </td>
    </tr>
   <tr>
        <td class="jive-label">
            <%-- Email: --%>
            <jive:i18n key="global.email"/><jive:i18n key="global.colon"/>
        </td>
        <td>
            <input type="text" name="email" size="30" maxlength="75" tabindex="<ww:property value="@tabIndex/next" />"
                   value="<ww:if test="email"><ww:property value="email" /></ww:if>">
        </td>
    </tr>

</ww:if>

<tr>
    <td class="jive-label">
        <%-- Subject: --%>
        <jive:i18n key="global.subject"/><jive:i18n key="global.colon"/>
    </td>
    <td>
        <ww:if test="reply == true">

            <input type="text" name="subject" size="60" maxlength="75" tabindex="<ww:property value="@tabIndex/next" />"
                   value="<ww:if test="replySubject"><ww:property value="replySubject" /></ww:if>">

        </ww:if>
        <ww:else>

            <input type="text" name="subject" size="60" maxlength="75" tabindex="<ww:property value="@tabIndex/next" />"
                   value="<ww:if test="subject"><ww:property value="subject" /></ww:if>">

        </ww:else>
    </td>
</tr>
<tr>
    <td>&nbsp;</td>
    <td>
        <table class="jive-font-buttons" cellpadding="2" cellspacing="0" border="0">
        <tr>
            <td><a href="#" onclick="styleTag('b',document.postform.body);return false;"
                   title="<jive:i18n key="post.bold" />"
            ><img src="images/bold.gif" width="20" height="22" border="0"></a></td>
            <td><a href="#" onclick="styleTag('i',document.postform.body);return false;"
                   title="<jive:i18n key="post.italic" />"
            ><img src="images/italics.gif" width="20" height="22" border="0"></a></td>
            <td><a href="#" onclick="styleTag('u',document.postform.body);return false;"
                   title="<jive:i18n key="post.underline" />"
            ><img src="images/underline.gif" width="20" height="22" border="0"></a></td>
            <td><input type="submit" name="doSpellCheck" accesskey="s" class="fontButton"
                       value="<jive:i18n key="post.spell_check" />"></td>

            <ww:if test="reply == true">
               <td><input type="submit" name="doQuoteOriq" accesskey="q" class="fontButton"
                          value="<jive:i18n key="post.quote" />" tabindex="<ww:property value="@tabIndex/next" />"></td>
            </ww:if>
        </tr>
        </table>
    </td>
</tr>
<tr>
    <td class="jive-label" valign="top">
        <%-- Message: --%>
        <jive:i18n key="global.message"/><jive:i18n key="global.colon"/>
    </td>
    <td>
        <textarea name="body" wrap="virtual" cols="58" rows="12" tabindex="<ww:property value="@tabIndex/next" />"
        ><ww:if test="body"><ww:property value="body"/></ww:if></textarea>
    </td>
</tr>

<tr>
    <td>&nbsp;</td>
    <td>

<!--
    <%@ page import="net.tanesha.recaptcha.ReCaptcha" %>
    <%@ page import="net.tanesha.recaptcha.ReCaptchaFactory" %>
    <script type="text/javascript">
        var RecaptchaOptions = {
                theme : 'clean', tabindex : "<ww:property value="@tabIndex/next" />"
            };
    </script>
        <form action="" method="post">
        <%
          String captchaSecret = System.getenv("CAPTCHA_SECRET");
          String captchaSiteKey = System.getenv("CAPTCHA_SITE_KEY");
          ReCaptcha c = ReCaptchaFactory.newReCaptcha(captchaSiteKey, captchaSecret, false);
          out.print(c.createRecaptchaHtml(null, null));
        %>
        <input type="submit" value="submit" />
       </form>
--> 
     <script src="https://www.google.com/recaptcha/api.js" async defer></script>
       <!-- <form action="?" method="POST"> -->
      <div class="g-recaptcha" data-sitekey="***REMOVED***"></div>
      <br/>
       <!-- <input type="submit" value="Submit"> -->
       <!-- </form> -->

    </td>
</tr>
<%--
--%>

<tr>
    <td>&nbsp;</td>
    <td>

         <%-- Preview --%>
        <br> Use the preview button to validate the captcha and go to the post page: <br> <br> 
        <input type="submit" name="doPreview" value="<jive:i18n key="global.preview" />"
               tabindex="<ww:property value="@tabIndex/next" />">

        <ww:if test="canAttach(forum) == true">
           <%-- attach files --%>
           <input type="submit" name="doAttach" value="<jive:i18n key="post.attach_files" />"
                  tabindex="<ww:property value="@tabIndex/next" />">
        </ww:if>
        
        <%-- post message --%>
        <%--
        <input type="submit" name="doPost" value="<jive:i18n key="global.post_message" />" tabindex="<ww:property value="@tabIndex/next" />">
        --%>
        
        &nbsp;
        <%-- cancel --%>
        <input type="submit" name="doCancel" value="<jive:i18n key="global.cancel" />"
               tabindex="<ww:property value="@tabIndex/next" />">
    </td>
</tr>
</table>

</span>

</form>

<script language="JavaScript" type="text/javascript">
   <!--
   <ww:if test="guest == true">
   document.postform.name.focus();
   </ww:if>
   <ww:else>
   <ww:if test="reply == true">
   document.postform.body.focus();
   </ww:if>
   <ww:else>
   document.postform.subject.focus();
   </ww:else>
   </ww:else>
   //-->
</script>

<ww:if test="reply == true">

   <%-- Original Message: --%>
   <jive:i18n key="post.original"/><jive:i18n key="global.colon"/>

   <br><br>

   <ww:property value="message">

    <span class="jive-message-list">
    <table class="jive-box" cellpadding="3" cellspacing="2" border="0" width="100%">
    <tr valign="top" class="jive-odd">
        <td width="1%">
            <table cellpadding="0" cellspacing="0" border="0" width="180">
            <tr>
                <td>
                    <ww:if test="user">

                        <a href="<%= request.getContextPath() %>/gameServer/profile?viewName=<ww:property value="user/username" />"
                           <ww:if test="user/name"><ww:property value="user/name"/></ww:if>
                        ><ww:property value="user/username"/></a>
                       <br><br>
                       <jive:i18n key="global.posts"/><jive:i18n key="global.colon"/>
                       <ww:property value="numberFormat/format(forumFactory/userMessageCount(user))"/>
                       <br>
                       <ww:if test="user/property('jiveLocation')">

                          <%-- From: --%>
                          <jive:i18n key="global.from"/><jive:i18n key="global.colon"/>
                          <ww:property value="user/property('jiveLocation')"/>

                       </ww:if>

                       <%-- Registered: --%>
                       <jive:i18n key="global.registered"/><jive:i18n key="global.colon"/>
                       <ww:property value="shortDateFormat/format(user/creationDate)"/>

                    </ww:if>
                    <ww:else>

                       <ww:bean name="'com.jivesoftware.forum.action.util.Guest'" id="guestBean">
                          <ww:param name="'message'" value="message"/>
                       </ww:bean>

                       <span class="jive-guest">
                        <ww:if test="@guestBean/email">

                            <a href="mailto:<ww:property value="@guestBean/email" />"
                            ><ww:property value="@guestBean/display"/></a>

                        </ww:if>
                        <ww:else>

                           <ww:property value="@guestBean/display"/>

                        </ww:else>
                        </span>

                    </ww:else>
                </td>
            </tr>
            </table>
        </td>
        <td width="99%">
            <table cellpadding="0" cellspacing="0" border="0" width="100%">
            <tr valign="top">
                <td>
                    <span class="jive-subject">
                    <ww:property value="subject"/>
                    </span>
                    <br>
                    <%-- Posted: --%>
                    <jive:i18n key="global.posted"/><jive:i18n key="global.colon"/>
                    <ww:property value="dateFormat/format(creationDate)"/>
                </td>
            </tr>
            <tr>
                <td colspan="2" style="border-top: 1px #ccc solid;">
                    <br>
                    <ww:property value="body"/>
                    <br><br>
                </td>
            </tr>
            </table>
        </td>
    </tr>
    </table>
    </span>

   </ww:property>

</ww:if>

<jsp:include page="footer.jsp" flush="true"/>