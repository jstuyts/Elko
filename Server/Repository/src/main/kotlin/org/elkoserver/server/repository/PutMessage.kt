package org.elkoserver.server.repository

import org.elkoserver.json.JsonLiteralFactory
import org.elkoserver.json.Referenceable
import org.elkoserver.objectdatabase.store.ResultDesc

/**
 * Create a 'put' reply message.
 *
 * @param target  Object the message is being sent to.
 * @param tag  Client tag for matching replies.
 * @param results  Status results.
 */
internal fun msgPut(target: Referenceable, tag: String?, results: Array<out ResultDesc>) =
        JsonLiteralFactory.targetVerb(target, "put").apply {
            addParameterOpt("tag", tag)
            addParameter("results", results)
            finish()
        }
