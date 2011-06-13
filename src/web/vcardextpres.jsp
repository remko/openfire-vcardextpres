<%@ page import="java.util.*,
                 org.jivesoftware.wildfire.XMPPServer,
                 org.jivesoftware.util.*,
                 org.jivesoftware.wildfire.plugin.VCardExtendedPresencePlugin"
    errorPage="error.jsp"
%>

<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jstl/fmt_rt" prefix="fmt" %>

<%  // Get parameters
    boolean save = request.getParameter("save") != null;
    boolean success = request.getParameter("success") != null;
	boolean enableNick = ParamUtils.getBooleanParameter(request, "enableNick");
	boolean enablePhotoHash = ParamUtils.getBooleanParameter(request, "enablePhotoHash");

	VCardExtendedPresencePlugin plugin = (VCardExtendedPresencePlugin)XMPPServer.getInstance().getPluginManager().getPlugin("vcardextpres");

    // Handle a save
    if (save) {
		plugin.setNickEnabled(enableNick);
		plugin.setPhotoHashEnabled(enablePhotoHash);
        response.sendRedirect("vcardextpres.jsp?success=true");
        return;
    }

	enableNick = plugin.isNickEnabled();
	enablePhotoHash = plugin.isPhotoHashEnabled();
%>

<html>
    <head>
        <title><fmt:message key="vcardextpres.config.title"/></title>
        <meta name="pageID" content="vcardextpres"/>
    </head>
    <body>

<p>
<fmt:message key="vcardextpres.config.instructions"/>
</p>

<%  if (success) { %>

    <div class="jive-success">
    <table cellpadding="0" cellspacing="0" border="0">
    <tbody>
        <tr><td class="jive-icon"><img src="images/success-16x16.gif" width="16" height="16" border="0"></td>
        <td class="jive-icon-label">
			<fmt:message key="vcardextpres.config.success"/>
        </td></tr>
    </tbody>
    </table>
    </div><br>
<% } %>

<form action="vcardextpres.jsp?save" method="post">

<fieldset>
    <legend><fmt:message key="vcardextpres.config.avatars.title"/></legend>
    <div>
    <p>
	<fmt:message key="vcardextpres.config.avatars.description"/>
    </p>
    <table cellpadding="3" cellspacing="0" border="0" width="100%">
    <tbody>
        <tr>
            <td width="1%">
            <input type="radio" name="enablePhotoHash" value="true" id="rb01"
             <%= ((enablePhotoHash) ? "checked" : "") %>>
            </td>
            <td width="99%">
                <label for="rb01"><b><fmt:message key="vcardextpres.enabled"/></b> - 
				<fmt:message key="vcardextpres.config.avatars.enabled"/></label>
            </td>
        </tr>
        <tr>
            <td width="1%">
            <input type="radio" name="enablePhotoHash" value="false" id="rb02"
             <%= ((!enablePhotoHash) ? "checked" : "") %>>
            </td>
            <td width="99%">
                <label for="rb02"><b><fmt:message key="vcardextpres.disabled"/></b> - 
				<fmt:message key="vcardextpres.config.avatars.disabled"/></label>
            </td>
        </tr>
    </tbody>
    </table>
    </div>
</fieldset>

<br><br>

<fieldset>
    <legend><fmt:message key="vcardextpres.config.nick.title"/></legend>
    <div>
    <p>
	<fmt:message key="vcardextpres.config.nick.description"/>
    </p>
    <table cellpadding="3" cellspacing="0" border="0" width="100%">
    <tbody>
        <tr>
            <td width="1%">
            <input type="radio" name="enableNick" value="true" id="rb01"
             <%= ((enableNick) ? "checked" : "") %>>
            </td>
            <td width="99%">
                <label for="rb01"><b><fmt:message key="vcardextpres.enabled"/></b> - <fmt:message key="vcardextpres.config.nick.enabled"/></label>
            </td>
        </tr>
        <tr>
            <td width="1%">
            <input type="radio" name="enableNick" value="false" id="rb02"
             <%= ((!enableNick) ? "checked" : "") %>>
            </td>
            <td width="99%">
                <label for="rb02"><b><fmt:message key="vcardextpres.disabled"/></b> - <fmt:message key="vcardextpres.config.nick.disabled"/></label>
            </td>
        </tr>
    </tbody>
    </table>
    </div>
</fieldset>

<br><br>

<input type="submit" value="Save Properties">
</form>

</body>
</html>
