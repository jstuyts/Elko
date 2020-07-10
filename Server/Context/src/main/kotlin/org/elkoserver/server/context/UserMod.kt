package org.elkoserver.server.context

/**
 * Marker Interface for mods that may be attached to users.
 *
 * The server will insist that any [Mod] subclass implement this
 * before it will allow it to be attached to a [User] object.
 */
interface UserMod 