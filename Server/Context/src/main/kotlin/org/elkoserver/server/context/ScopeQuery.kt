package org.elkoserver.server.context

import com.grack.nanojson.JsonArray
import com.grack.nanojson.JsonObject

/**
 * Generate and return a MongoDB query to fetch an object's non-embedded,
 * application-scoped mods.  These mods are stored in the object database as
 * independent objects.  Such a mod is identified by a "refx" property and
 * a "scope" property.  The "refx" property corresponds to the ref of the
 * object the mod should be attached to.  The "scope" property matches if
 * its value is a path prefix match for the query's scope parameter.  For
 * example,
 *
 * scopeQuery("foo", "com-example-thing")
 *
 * would translate to the query pattern:
 *
 * { refx: "foo",
 * $or: [
 * { scope: "com" },
 * { scope: "com-example" },
 * { scope: "com-example-thing" }
 * ]
 * }
 *
 * Note that in the future we may decide (based on what's actually the most
 * efficient in the underlying database) to replace the "$or" in the query
 * with a regex property match or some other way of fetching based on a
 * path prefix, so don't take the above expansion as the literal final
 * word.
 *
 * @param ref  The ref of the object in question.
 * @param scope  The application scope
 *
 * @return a JSON object representing the above described query.
 */
internal fun scopeQuery(ref: String, scope: String): JsonObject {
    val orList = JsonArray()
    val frags = scope.split("-").toTypedArray()
    var scopePart: String? = null
    for (frag in frags) {
        if (scopePart == null) {
            scopePart = frag
        } else {
            scopePart += "-$frag"
        }
        val orTerm = JsonObject()
        orTerm["scope"] = scopePart
        orList.add(orTerm)
    }
    val query = JsonObject()
    query["refx"] = ref
    query["\$or"] = orList
    return query
}
