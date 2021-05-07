package org.elkoserver.server.repository

import org.elkoserver.json.JsonLiteralFactory
import org.elkoserver.json.Referenceable
import org.elkoserver.objectdatabase.store.ObjectDesc

/**
 * Create a 'query' reply message.
 *
 * @param target  Object the message is being sent to.
 * @param tag  Client tag for matching replies.
 * @param results  Object results.
 */
internal fun msgQuery(target: Referenceable, tag: String?, results: Array<ObjectDesc>?) =
        JsonLiteralFactory.targetVerb(target, "query").apply {
            addParameterOpt("tag", tag)
            addParameter("results", results)
            finish()
        }
