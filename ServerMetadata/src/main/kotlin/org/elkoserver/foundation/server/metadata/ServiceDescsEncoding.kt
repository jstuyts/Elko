package org.elkoserver.foundation.server.metadata

import org.elkoserver.json.JsonLiteralArray

/**
 * Generate a JSONLiteralArray of ServiceDesc objects from a sequence of
 * them.
 */
fun encodeServiceDescs(services: Iterable<ServiceDesc?>?): JsonLiteralArray =
        JsonLiteralArray().apply {
            if (services != null) {
                for (service in services) {
                    addElement(service)
                }
            }
            finish()
        }
