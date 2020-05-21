package org.elkoserver.objdb.store

import org.elkoserver.foundation.json.JSONMethod
import org.elkoserver.foundation.json.OptInteger
import org.elkoserver.foundation.json.OptString
import org.elkoserver.json.Encodable
import org.elkoserver.json.EncodeControl
import org.elkoserver.json.JSONLiteralFactory.type
import org.elkoserver.json.JsonObject

/**
 * Description of a query for an object.
 *
 * @param template  Query template indicating the objects queried.
 * @param collectionName  Name of collection to query, or null to take the
 *    configured default.
 * @param maxResults  Maximum number of result objects to return, or 0 to
 *    indicate no fixed limit.
 *
 * @see ObjectStore.queryObjects ObjectStore.queryObjects
 */
class QueryDesc(val template: JsonObject, val collectionName: String?, val maxResults: Int) : Encodable {

    /**
     * JSON-driven (and direct) constructor.
     *
     * @param template  Query template indicating the objects queried.
     * @param collectionName  Name of collection to query, or null to take the
     * configured default.
     * @param maxResults  Maximum number of result objects to return, or 0 to
     * indicate no fixed limit (the default if omitted).
     */
    @JSONMethod("template", "coll", "limit")
    constructor(template: JsonObject, collectionName: OptString, maxResults: OptInteger)
            : this(template, collectionName.value<String?>(null), maxResults.value(0))

    /**
     * Encode this object for transmission or persistence.
     *
     * @param control  Encode control determining what flavor of encoding
     * should be done.
     *
     * @return a JSON literal representing this object.
     */
    override fun encode(control: EncodeControl) =
            type("queryi", control).apply {
                addParameter("template", template)
                addParameterOpt("coll", collectionName)
                if (maxResults > 0) {
                    addParameter("limit", maxResults)
                }
                finish()
            }
}
