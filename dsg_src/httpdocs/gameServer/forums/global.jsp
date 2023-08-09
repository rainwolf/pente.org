<%
   /**
    * $RCSfile: global.jsp,v $
    * $Revision: 1.16 $
    * $Date: 2002/12/20 08:07:29 $
    */
%>

<%@ page import="com.jivesoftware.base.JiveGlobals,
                 com.jivesoftware.webwork.util.ValueStack"
%>

<%! /**
 * Returns a Jive property from the jive_config.xml file. The property will first
 * be loaded as "skin.default." + name -- if that fails, just the name is used.
 *
 * @param name the name of the property to look up.
 * @return a Jive property from the jive_config.xml file.
 */
private static String getProp(String name) {
   String value = JiveGlobals.getJiveProperty("skin.default." + name);
   if (value != null) {
      return value;
   } else {
      return JiveGlobals.getJiveProperty(name);
   }
}

   private Object getAction(HttpServletRequest request) {
      ValueStack vs = ValueStack.getStack(request);
      Object obj = vs.popValue();
      vs.pushValue(obj);
      return obj;
   }
%>

<% // Set the content type
   response.setContentType("text/html; charset=" + JiveGlobals.getCharacterEncoding());
%>