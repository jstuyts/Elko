package org.elkoserver.server.repository

import org.elkoserver.json.JsonLiteralFactory
import org.elkoserver.json.Referenceable
import org.elkoserver.objdb.store.ResultDesc

/**
 * Create an 'update' reply message.
 *
 * @param target  Object the message is being sent to.
 * @param tag  Client tag for matching replies.
 * @param results  Status results.
 */
internal fun msgUpdate(target: Referenceable, tag: String?, results: Array<out ResultDesc>) =
        JsonLiteralFactory.targetVerb(target, "update").apply {
            addParameterOpt("tag", tag)
            addParameter("results", results)
            finish()
        }
