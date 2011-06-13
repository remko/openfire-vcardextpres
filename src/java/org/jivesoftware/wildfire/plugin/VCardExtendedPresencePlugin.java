/**
 * Copyright (C) 2006 Remko Troncon.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.wildfire.plugin;

import org.dom4j.Element;
import org.dom4j.QName;
import org.dom4j.Namespace;

import org.jivesoftware.util.StringUtils;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.PropertyEventListener;
import org.jivesoftware.util.PropertyEventDispatcher;
import org.jivesoftware.wildfire.Session;
import org.jivesoftware.wildfire.PresenceManager;
import org.jivesoftware.wildfire.PresenceRouter;
import org.jivesoftware.wildfire.XMPPServer;
import org.jivesoftware.wildfire.container.Plugin;
import org.jivesoftware.wildfire.container.PluginManager;
import org.jivesoftware.wildfire.interceptor.InterceptorManager;
import org.jivesoftware.wildfire.interceptor.PacketInterceptor;
import org.jivesoftware.wildfire.interceptor.PacketRejectedException;
import org.jivesoftware.wildfire.roster.Roster;
import org.jivesoftware.wildfire.roster.RosterManager;
import org.jivesoftware.wildfire.roster.RosterItem;
import org.jivesoftware.wildfire.user.UserManager;
import org.jivesoftware.wildfire.user.UserNotFoundException;
import org.jivesoftware.wildfire.vcard.VCardManager;
import org.jivesoftware.wildfire.vcard.VCardListener;

import org.xmpp.packet.Message;
import org.xmpp.packet.Presence;
import org.xmpp.packet.Presence.Type;
import org.xmpp.packet.Packet;
import org.xmpp.packet.JID;

import java.io.File;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * vCard-based Extended Presence plugin. 
 *
 * This plugin annotates packets with information from the vCard.
 * It adds the hash of the PHOTO field of a user's vCard to all its 
 * outgoing presence packets, according to <a href="http://www.jabber.org/jeps/jep-0153.html">JEP-0153 (vCard-based avatars)</a>.
 * 
 * For messages and subscription requests to contacts that do not have a
 * user on their roster, it adds the user's nickname according to <a href="ttp://www.jabber.org/jeps/jep-0172.html">JEP-0172 (User Nickname)</a>.
 *
 * If the message or presence packet already contains extended presence
 * information, the plugin does not overwrite it. 
 *
 * @author Remko Tron&ccedil;on 
 */
public class VCardExtendedPresencePlugin implements Plugin, PacketInterceptor, VCardListener, PropertyEventListener {
    private XMPPServer xmppServer;
    private UserManager userManager;
    private InterceptorManager interceptorManager;
    private VCardManager vcardManager;
    private RosterManager rosterManager;
    private PresenceManager presenceManager;
    private PresenceRouter presenceRouter;
    private boolean nickEnabled;
    private boolean photoHashEnabled;

    private HashMap<String,String> nicks = new HashMap();
    private HashMap<String,String> photoHashes = new HashMap();

    public VCardExtendedPresencePlugin() {
        xmppServer = XMPPServer.getInstance();
        interceptorManager = InterceptorManager.getInstance();
        vcardManager = VCardManager.getInstance();
        rosterManager = xmppServer.getRosterManager();
        userManager = xmppServer.getUserManager();
        presenceManager = xmppServer.getPresenceManager();
        presenceRouter = xmppServer.getPresenceRouter();
    }

    public void initializePlugin(PluginManager pm, File f) {
        nickEnabled = JiveGlobals.getBooleanProperty("plugin.vcardextpres.nickEnabled", false);
        photoHashEnabled = JiveGlobals.getBooleanProperty("plugin.vcardextpres.photoHashEnabled", false);
        interceptorManager.addInterceptor(this);
        vcardManager.addListener(this);
        PropertyEventDispatcher.addListener(this);
    }

    public void destroyPlugin() {
        vcardManager.removeListener(this);
        interceptorManager.removeInterceptor(this);
        PropertyEventDispatcher.removeListener(this);
    }

    public void interceptPacket(Packet packet, Session session, boolean incoming, boolean processed) throws PacketRejectedException {
        if (incoming || processed)
            return;

        // Check if the sender is a local, non-anonymous user
        JID senderJID = packet.getFrom();
        if (senderJID == null || !xmppServer.isLocal(senderJID))
            return;
        String user = senderJID.getNode();
        if (!userManager.isRegisteredUser(user))
            return;

        if (packet instanceof Presence) {
            Presence presence = (Presence) packet;

            // Unavailable packets don't get extended presence information
            if (presence.getType() == Presence.Type.unavailable) 
               return;
            
            // Add photo hash if necessary
            if (isPhotoHashEnabled()) {
                addPhotoHash(presence,user);
            }
            
            // Nickname
            if (presence.getType() == Presence.Type.subscribe && isNickEnabled()) {
                addNick(presence,user);
            }
        }
        else if (isNickEnabled() && packet instanceof Message) {
            Message message = (Message) packet;

            // Body-less messages don't get nicknames
            if (message.getBody() == null)
                return;

            JID recipientJID = message.getTo();
            if (recipientJID == null)
                return;

            if (message.getBody() != null) {
                // Add a nickname if sender is not on recipient's roster
                try {
                    Roster roster = rosterManager.getRoster(user);
					RosterItem rosterItem = roster.getRosterItem(recipientJID);
					if (rosterItem == null) {
                        addNick(message,user);
                    }
                    else {
                        RosterItem.SubType type = rosterItem.getSubStatus();
                        if (type != RosterItem.SUB_BOTH && type != RosterItem.SUB_FROM) {
                            addNick(message,user);
                        }
                    }
                }
                catch (UserNotFoundException e) { }
            }
        }
    }

    /**
     * Checks whether nickname information should be added to packets.
     */
    public boolean isNickEnabled() {
        return nickEnabled;
    }

    /**
     * Enables or disables adding of nickname information to packets.
     */
    public void setNickEnabled(boolean b) {
        JiveGlobals.setProperty("plugin.vcardextpres.nickEnabled", ( b ? "true" : "false"));
    }

    /**
     * Checks whether photo hash information should be added to packets.
     */
    public boolean isPhotoHashEnabled() {
        return photoHashEnabled;
    }

    /**
     * Enables or disables adding of photo hash information to packets.
     */
    public void setPhotoHashEnabled(boolean b) {
        JiveGlobals.setProperty("plugin.vcardextpres.photoHashEnabled", ( b ? "true" : "false"));
    }

    /**
     * Retrieves the nickname from the user's vCard NICK field.
     * This information is cached.
     *
     * @param user the user whose information is fetched
     * @param force do not use cached information 
     * @return the user's nickname
     */
    protected String getNick(String user, boolean force) {
        if (!nicks.containsKey(user) || force) {
            String nick = vcardManager.getVCardProperty(user,"NICKNAME");
            if (nick != null && nick.length() > 0) {
                nicks.put(user,nick);
            }
        }
        return nicks.get(user);
    }

    /**
     * Retrieves the SHA-1 hash of a user's vCard PHOTO field.
     * This information is cached.
     *
     * @param user the user whose information is fetched
     * @param force do not use cached information 
     * @return the hash of the user's vCard PHOTO field
     */
    protected String getPhotoHash(String user, boolean force) {
        if (!photoHashes.containsKey(user) || force) {
            String photoHash = new String();
            String photoBinVal = vcardManager.getVCardProperty(user,"PHOTO:BINVAL");
            if (photoBinVal != null) {
                photoHash = StringUtils.hash(StringUtils.decodeBase64(photoBinVal),"SHA-1");
            }
            photoHashes.put(user,photoHash); 
        }
        return photoHashes.get(user);
    }

    /**
     * Adds nickname information from a given user's vCard NICK field 
     *  to a packet if the packet does not contain nickname information yet.
     *
     * @param packet the packet to modify
     * @param user the user whose information is added to the packet 
     */
    protected void addNick(Packet packet, String user) {
        // Check if there already is a nickname
        if (packet.getElement().element(QName.get("nick","http://jabber.org/protocol/nick")) != null)
            return;

        // Add the nickname
        String nick = getNick(user,false);
        if (nick.length() > 0) {
            packet.getElement().addElement("nick","http://jabber.org/protocol/nick").setText(nick);
        }
    }

    /**
     * Adds the hash of a given user's vCard PHOTO field to a packet if the
     * packet does not contain a photo hash yet.
     *
     * @param packet the packet to modify
     * @param user the user whose information is added to the packet 
     */
    protected void addPhotoHash(Packet packet, String user) {
        // Check if there already is an <x> element and/or a photo hash
        Element element = packet.getElement().element(QName.get("x","vcard-temp:x:update"));
        if (element != null) {
            if (element.element("photo") != null)
                return;
        }
        else {
            element = packet.getElement().addElement("x","vcard-temp:x:update");
        }

        // Add the photo hash
        element.addElement("photo").addText(getPhotoHash(user,false));
    }
    
    public void vCardUpdated(String user) {
        // Update hash tables
        getNick(user,true);
        
        boolean updatePresence = false;
        String oldPhotoHash = (String) photoHashes.get(user);
        if (!oldPhotoHash.equals(getPhotoHash(user,true))) {
            updatePresence = true;
        }
        
        // Re-send all presences if necessary
        if (updatePresence) {
            for (Presence p : presenceManager.getPresences(user)) {
                presenceRouter.route(p);
            }
        }
    }

    public void vCardCreated(String user) {
        vCardUpdated(user);
    }

    public void vCardDeleted(String user) {
        vCardUpdated(user);
    }
    
    public void propertySet(String property, Map params) {
        if (property.equals("plugin.vcardextpres.photoHashEnabled")) {
            photoHashEnabled = params.get("value").equals("true");
        }
        else if (property.equals("plugin.vcardextpres.nickEnabled")) {
            nickEnabled = params.get("value").equals("true");
        }
    }
    
    public void propertyDeleted(String property, Map params) {
        if (property.equals("plugin.vcardextpres.photoHashEnabled")) {
            photoHashEnabled = false;
        }
        else if (property.equals("plugin.vcardextpres.nickEnabled")) {
            nickEnabled = false;
        }
    }
    
    public void xmlPropertySet(String property, Map params) {
    }
    
    public void xmlPropertyDeleted(String property, Map params) {
    }
}
