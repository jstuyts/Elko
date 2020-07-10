package org.elkoserver.server.context

import org.elkoserver.json.JsonLiteralFactory
import org.elkoserver.json.Referenceable

/**
 * Create an 'address' message.
 *
 * @param target  Object the message is being sent to.
 * @param protocol  The protocol to reach this server.
 * @param hostPort  This server's address, as far as the rest of world is
 * concerned.
 */
internal fun msgAddress(target: Referenceable, protocol: String, hostPort: String) =
        JsonLiteralFactory.targetVerb(target, "address").apply {
            addParameter("protocol", protocol)
            addParameter("hostport", hostPort)
            finish()
        }
