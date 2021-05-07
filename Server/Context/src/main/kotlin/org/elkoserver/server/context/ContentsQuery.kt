package org.elkoserver.server.context

import com.grack.nanojson.JsonObject

/**
 * Generate and return a MongoDB query to fetch an object's contents.
 *
 * @param ref  The ref of the container whose contents are of interest.
 *
 * @return a JSON object representing the above described query.
 */
internal fun contentsQuery(ref: String?) =
        // { type: "item", in: REF }
        JsonObject().apply {
            put("type", "item")
            put("in", ref)
        }
