package org.elkoserver.feature.basicexamples.chat

import org.elkoserver.json.JsonLiteral
import org.elkoserver.json.JsonLiteralFactory
import org.elkoserver.json.Referenceable

/**
 * Create a 'push' message.  This directs a client to push the browser to a
 * different URL than the one it is looking at.
 *
 * @param target  Object the message is being sent to (normally this will
 * be a user or context).
 * @param from  Object the message is to be alleged to be from, or
 * null if not relevant.  This normally indicates the user who is doing
 * the pushing.
 * @param url  The URL being pushed.
 * @param frame  Name of a frame to push the URL into, or null if not
 * relevant.
 * @param features  Features string to associate with the URL, or null if
 * not relevant.
 */
fun msgPush(target: Referenceable, from: Referenceable, url: String?, frame: String?, features: String?): JsonLiteral =
        JsonLiteralFactory.targetVerb(target, "push").apply {
            addParameterOpt("from", from)
            addParameter("url", url)
            addParameterOpt("frame", frame)
            addParameterOpt("features", features)
            finish()
        }
