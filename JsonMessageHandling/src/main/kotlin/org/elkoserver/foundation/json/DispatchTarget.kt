package org.elkoserver.foundation.json

/**
 * Marker interface for objects that can be the recipients of JSON messages.
 *
 * This interface has no method protocol.  Instead, classes "implementing" this
 * interface must mark individual message handler methods using the [ ] attribute.  These are invoked (by means of the Java reflection
 * API) by the JSON message handler dispatch mechanism.
 *
 *
 * Classes you write that handle JSON messages won't generally need to
 * declare themselves as implementing this interface, since the normal coding
 * pattern for such things is to subclass a standard base class (such as `BasicObject`,`Mod`,
 * `BasicProtocolHandler`, or `NonRoutingActor`  that already implements it.
 */
interface DispatchTarget 