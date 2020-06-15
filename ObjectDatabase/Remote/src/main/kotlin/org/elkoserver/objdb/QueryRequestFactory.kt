package org.elkoserver.objdb

import org.elkoserver.idgeneration.IdGenerator
import org.elkoserver.json.EncodeControl.ForClientEncodeControl
import org.elkoserver.json.JSONLiteralArray
import org.elkoserver.json.JSONLiteralFactory
import org.elkoserver.json.JsonObject
import java.util.function.Consumer

class QueryRequestFactory(private val tagGenerator: IdGenerator) {
    internal fun create(template: JsonObject, collectionName: String?, maxResults: Int, handler: Consumer<Any?>?): PendingRequest {
        val tag = tagGenerator.generate().toString()
        return PendingRequest(handler, "query", collectionName, tag, msgQuery(template, tag, collectionName, maxResults))
    }

    /**
     * Fill in this request's message field with a 'query' request.
     *
     * @param template  Template object for the objects desired.
     * @param collectionName  Name of collection to query, or null to take the
     * configured default.
     * @param maxResults  Maximum number of result objects to return, or 0 to
     * indicate no fixed limit.
     */
    private fun msgQuery(template: JsonObject, tag: String, collectionName: String?, maxResults: Int) =
            JSONLiteralFactory.targetVerb("rep", "query").apply {
                addParameter("tag", tag)
                val what = JSONLiteralFactory.type("queryi", ForClientEncodeControl).apply {
                    addParameter("template", template)
                    addParameterOpt("coll", collectionName)
                    if (maxResults > 0) {
                        addParameter("limit", maxResults)
                    }
                    finish()
                }
                addParameter("what", JSONLiteralArray.singleElementArray(what))
                finish()
            }
}
