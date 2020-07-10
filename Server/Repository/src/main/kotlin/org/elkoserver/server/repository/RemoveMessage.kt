package org.elkoserver.server.repository

import org.elkoserver.json.JsonLiteralFactory
import org.elkoserver.json.Referenceable
import org.elkoserver.objdb.store.ResultDesc

/**
 * Create a 'remove' reply message.
 *
 * @param target  Object the message is being sent to.
 * @param tag  Client tag for matching replies.
 * @param results  Status results.
 */
internal fun msgRemove(target: Referenceable, tag: String?, results: Array<out ResultDesc>) =
        JsonLiteralFactory.targetVerb(target, "remove").apply {
            addParameterOpt("tag", tag)
            addParameter("results", results)
            finish()
        }
