package org.elkoserver.feature.basicexamples.chat

import org.elkoserver.foundation.json.JSONMethod
import org.elkoserver.foundation.json.MessageHandlerException
import org.elkoserver.foundation.json.OptBoolean
import org.elkoserver.foundation.json.OptString
import org.elkoserver.json.EncodeControl
import org.elkoserver.json.JSONLiteralFactory
import org.elkoserver.server.context.ContextMod
import org.elkoserver.server.context.Mod
import org.elkoserver.server.context.Msg
import org.elkoserver.server.context.ObjectCompletionWatcher
import org.elkoserver.server.context.User
import org.elkoserver.server.context.UserWatcher

/**
 * Mod to enable users in a context to chat with each other.  This mod must be
 * attached to a context.
 *
 * If 'allowPrivate' or 'allowPrivatePush' is true, this mod will
 * automatically attach a correspondingly configured ephemeral [ ] mod to any user who enters the context.
 *
 * Note that setting all four 'allow' parameters to false is permitted
 * but not useful.
 *
 * @param allowChat  If true, users can chat publicly, i.e., issue
 *    utterances that are broadcast to everyone in the context.
 * @param allowPrivate  If true, users can chat privately, i.e., transmit
 *    utterances to other individual users.
 * @param allowPush  If true, users can push URLs publicly, i.e., to
 *    everyone in the context.
 * @param allowPrivatePush  If true, users can push URLs privately, i.e.,
 *    to other individual users.
 *
 * @see PrivateChat
 */
class Chat @JSONMethod("allowchat", "allowprivate", "allowpush", "allowprivatepush") constructor(
        allowChat: OptBoolean,
        allowPrivate: OptBoolean,
        allowPush: OptBoolean,
        allowPrivatePush: OptBoolean) : Mod(), ObjectCompletionWatcher, ContextMod {
    private val amAllowChat: Boolean = allowChat.value(true)
    private val amAllowPrivate: Boolean = allowPrivate.value(true)
    private val amAllowPush: Boolean = allowPush.value(true)
    private val amAllowPrivatePush: Boolean

    /**
     * Encode this mod for transmission or persistence.
     *
     * @param control  Encode control determining what flavor of encoding
     * should be done.
     *
     * @return a JSON literal representing this mod.
     */
    override fun encode(control: EncodeControl) =
            if (control.toRepository()) {
                JSONLiteralFactory.type("chat", control).apply {
                    if (!amAllowChat) {
                        addParameter("allowchat", amAllowChat)
                    }
                    if (!amAllowPrivate) {
                        addParameter("allowprivate", amAllowPrivate)
                    }
                    if (!amAllowPush) {
                        addParameter("allowpush", amAllowPush)
                    }
                    if (!amAllowPrivatePush) {
                        addParameter("allowprivatepush", amAllowPrivatePush)
                    }
                    finish()
                }
            } else {
                null
            }

    /**
     * If this mod's configuration enables private chat and/or private push,
     * arrange to automatically attach ephemeral [PrivateChat] mods to
     * arriving users.
     *
     * Application code should not call this method.
     */
    override fun objectIsComplete() {
        if (amAllowPrivate || amAllowPrivatePush) {
            context().registerUserWatcher(
                    object : UserWatcher {
                        override fun noteUserArrival(who: User) {
                            val privateChat = PrivateChat(amAllowPrivate, amAllowPrivatePush)
                            privateChat.attachTo(who)
                        }

                        override fun noteUserDeparture(who: User) {}
                    })
        }
    }

    /**
     * Message handler for the 'push' message.
     *
     * This message pushes a URL to everyone in the context.  This is done
     * by echoing the 'push' message to the context, marked as being from the
     * user who sent it.
     *
     * <u>recv</u>: ` { to:*REF*, op:"push", url:*STR*,
     * frame:*optSTR*,
     * features:*optSTR* } `<br></br>
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
     * this mod or if the 'allowPush' configuration was parameter false.
     */
    @JSONMethod("url", "frame", "features")
    fun push(from: User, url: String, frame: OptString, features: OptString) {
        if (amAllowPush) {
            ensureSameContext(from)
            val response = Msg.msgPush(context(), from, url, frame.value<String?>(null),
                    features.value<String?>(null))
            if (context().isSemiPrivate) {
                from.send(response)
            } else {
                context().send(response)
            }
        } else {
            throw MessageHandlerException("push not allowed")
        }
    }

    /**
     * Message handler for the 'say' message.
     *
     *
     * This message broadcasts chat text to everyone in the context.  This
     * is done by echoing the 'say' message to the context, marked as being
     * from the user who sent it.
     *
     *
     *
     * <u>recv</u>: ` { to:*REF*, op:"say", text:*STR* } `<br></br>
     *
     * <u>send</u>: ` { to:*REF*, op:"say", text:*STR*,
     * from:*fromREF* } `
     *
     * @param text  The chat text being "spoken".
     *
     * @throws MessageHandlerException if 'from' is not in the same context as
     * this mod or if the 'allowChat' configuration parameter was false.
     */
    @JSONMethod("text")
    fun say(from: User, text: String) {
        if (amAllowChat) {
            ensureSameContext(from)
            val response = Msg.msgSay(context(), from, text)
            if (context().isSemiPrivate) {
                from.send(response)
            } else {
                context().send(response)
            }
        } else {
            throw MessageHandlerException("chat not allowed")
        }
    }

    init {
        amAllowPrivatePush = allowPrivatePush.value(amAllowPrivate && amAllowPush)
    }
}
