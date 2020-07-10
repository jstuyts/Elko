package org.elkoserver.feature.basicexamples.chat

import org.elkoserver.foundation.json.JsonMethod
import org.elkoserver.foundation.json.MessageHandlerException
import org.elkoserver.foundation.json.OptString
import org.elkoserver.json.EncodeControl
import org.elkoserver.json.JsonLiteral
import org.elkoserver.server.context.Mod
import org.elkoserver.server.context.User
import org.elkoserver.server.context.UserMod
import org.elkoserver.server.context.msgPush
import org.elkoserver.server.context.msgSay

/**
 * Mod to enable users in a context to chat privately with each other.  This
 * mod must be attached to a user, but note that it is not to be attached to
 * the user record in the object database.  It never persists, but is always
 * attached dynamically by a [Chat] mod attached to the context.
 *
 * Note that setting both 'allowPrivate' and 'allowPush' to false is
 * permitted but not useful.
 *
 * @param amAllowPrivate  If true, users can chat privately, i.e., transmit
 *    utterances to other individual users.
 * @param amAllowPush  If true, users can push URLs privately, i.e., to other
 *    individual users.
 *
 * @see Chat
 */
class PrivateChat(private val amAllowPrivate: Boolean, private val amAllowPush: Boolean) : Mod(), UserMod {

    /**
     * Encode this mod for transmission or persistence.  Note that this mod is
     * never persisted or transmitted.
     *
     * @param control  Encode control determining what flavor of encoding
     * should be done.
     *
     * @return null since this mod is never persisted or transmitted.
     */
    override fun encode(control: EncodeControl): JsonLiteral? = null

    /**
     * Message handler for the 'push' message.
     *
     * This message pushes a URL to the user this mod is attached to.  This
     * is done by echoing the 'push' message to the target user, marked as
     * being from the user who sent it.
     *
     * <u>recv</u>: ` { to:*REF*, op:"push", url:*STR*,
     * frame:*optSTR*, features:*optSTR*
     * } `<br></br>
     *
     * <u>send</u>: ` { to:*REF*, op:"push", url:*STR*,
     * frame:*optSTR*, features:*optSTR*,
     * from:*REF* } `
     *
     * @param url  The URL being pushed.
     * @param frame  Optional name of a frame to push it to.
     * @param features  Optional features string to associate with it.
     *
     * @throws MessageHandlerException if 'from' is not in the same context as
     * this mod or if the 'allowPush' constructor parameter was false.
     */
    @JsonMethod("url", "frame", "features")
    fun push(from: User, url: String, frame: OptString, features: OptString) {
        if (amAllowPush) {
            ensureSameContext(from)
            if (!context().isSemiPrivate) {
                val who = `object`() as User
                val response = msgPush(who, from, url, frame.value<String?>(null), features.value<String?>(null))
                who.send(response)
                if (from !== who) {
                    from.send(response)
                }
            }
        } else {
            throw MessageHandlerException("private push not allowed")
        }
    }

    /**
     * Message handler for the 'say' message.
     *
     * This message transmits chat text to the user this mod is attached to.
     * This is done by echoing the 'say' message to the target user, marked as
     * being from the user who sent it.
     *
     * <u>recv</u>: ` { to:*REF*, op:"say", text:*STR* } `<br></br>
     *
     * <u>send</u>: ` { to:*REF*, op:"say", text:*STR*,
     * from:*REF* } `
     *
     * @param text  The chat text being "spoken".
     *
     * @throws MessageHandlerException if 'from' is not in the same context as
     * this mod or if the 'allowPrivate' constructor parameter was false.
     */
    @JsonMethod("text")
    fun say(from: User, text: String) {
        if (amAllowPrivate) {
            ensureSameContext(from)
            if (!context().isSemiPrivate) {
                val who = `object`() as User
                val response = msgSay(who, from, text)
                who.send(response)
                if (from !== who) {
                    from.send(response)
                }
            }
        } else {
            throw MessageHandlerException("private chat not allowed")
        }
    }
}
