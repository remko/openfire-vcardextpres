# Openfire vCard-based Extended Presence plugin

## About

This [Openfire](http://www.igniterealtime.org/projects/openfire/) plugin annotates
packets with information from the vCard.

It adds the hash of the PHOTO field of a user's vCard to all its 
outgoing presence packets, according to 
[XEP-0153 (vCard-based Avatars)](http://xmpp.org/extensions/xep-0153.html)

For messages and subscription requests to contacts that do not have a
user on their roster, it adds the user's nickname according to 
[XEP-0172 (User Nickname)](http://xmpp.org/extensions/xep-0172.html).

If the message or presence packet already contains extended presence
information, the plugin does not overwrite it. 

*Note: This plugin was written for a very old version of Openfire, and probably
no longer works. It needs to be updated to the latest codebase.*
