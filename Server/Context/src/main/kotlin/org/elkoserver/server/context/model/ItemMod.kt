package org.elkoserver.server.context.model

/**
 * Marker Interface for mods that may be attached to items.
 *
 * The server will insist that any [Mod] subclass implement this
 * before it will allow it to be attached to a [Item] object.
 */
interface ItemMod 