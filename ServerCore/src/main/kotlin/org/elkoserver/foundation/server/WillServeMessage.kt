package org.elkoserver.foundation.server

import org.elkoserver.foundation.server.metadata.ServiceDesc
import org.elkoserver.json.JsonLiteralFactory
import org.elkoserver.json.Referenceable

/**
 * Create a 'willserve' message: notify the broker that this server is
 * offering one or more services.
 *
 * @param target  Object the message is being sent to.
 * @param services  List of the services being offered.
 */
internal fun msgWillserve(target: Referenceable,
                          services: List<ServiceDesc?>) =
        JsonLiteralFactory.targetVerb(target, "willserve").apply {
            addParameter("services", ServiceDesc.encodeArray(services))
            finish()
        }
