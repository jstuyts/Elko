package org.elkoserver.server.context

import org.elkoserver.json.Encodable
import org.elkoserver.json.JsonLiteral
import org.elkoserver.json.JsonLiteralFactory
import org.elkoserver.json.Referenceable

/**
 * Create a 'make' message.  This directs a client to create an object.
 *
 * @param target  Object the message is being sent to (the object that is
 * to be the container of the new object).
 * @param obj  The object that is to be created by the client.
 * @param maker  The user who is to be represented as the creator of the
 * object, or null if none is.
 * @param you  If true, object being made is its recipient.
 * @param sess  The client context session ID, or null if there is none.
 */
fun msgMake(target: Referenceable, obj: BasicObject?, maker: User? = null, you: Boolean = false, sess: String? = null): JsonLiteral =
        JsonLiteralFactory.targetVerb(target, "make").apply {
            addParameter("obj", obj as Encodable?)
            addParameterOpt("maker", maker as Referenceable?)
            if (you) {
                addParameter("you", you)
            }
            addParameterOpt("sess", sess)
            finish()
        }

/**
 * Create a 'make' message with a default creator and explicit session
 * identifier.  This method is exactly equivalent to:
 *
 * `msgMake(target, obj, null, false, sess)`
 *
 * and is provided just for convenience.
 *
 * @param target  Object the message is being sent to (the object that is
 * to be the container of the new object).
 * @param obj  The object that is to be created by the client.
 * @param sess  The client context session ID, or null if there is none.
 */
fun msgMake(target: Referenceable, obj: BasicObject?, sess: String?): JsonLiteral = msgMake(target, obj, null, false, sess)
