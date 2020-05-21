package org.elkoserver.foundation.net

/**
 * An HTTP request descriptor, augmented for setting up a WebSocket connection.
 */
class WebSocketRequest : HTTPRequest() {
    /** Goofy byte string at the start of the request body, used as part of the
     * insane connection initiation protocol specified by some versions of the
     * WebSockets spec.  */
    var crazyKey: ByteArray? = null
}
