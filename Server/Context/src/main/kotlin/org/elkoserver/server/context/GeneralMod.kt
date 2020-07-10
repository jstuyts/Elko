package org.elkoserver.server.context

/**
 * Marker Interface for mods that may be attached to any object.
 *
 * This interface is just a notational convenience for coding [Mod]
 * subclasses that can be attached to anything.
 */
interface GeneralMod : ContextMod, ItemMod, UserMod 