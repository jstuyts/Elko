package org.elkoserver.server.director

import org.elkoserver.json.Encodable
import org.elkoserver.json.JsonLiteralArray

/**
 * Generate a JSONLiteralArray from a linked list of Encodable objects.
 */
internal fun encodeEncodableList(list: List<Encodable>) =
        JsonLiteralArray().apply {
            for (elem in list) {
                addElement(elem)
            }
            finish()
        }
