package com.example.game.mods

import org.elkoserver.foundation.json.JSONMethod
import org.elkoserver.foundation.json.MessageHandlerException
import org.elkoserver.foundation.json.OptBoolean
import org.elkoserver.foundation.json.OptString
import org.elkoserver.json.EncodeControl
import org.elkoserver.json.JSONLiteralFactory
import org.elkoserver.json.JSONLiteralFactory.targetVerb
import org.elkoserver.json.Referenceable
import org.elkoserver.server.context.ContextMod
import org.elkoserver.server.context.Mod
import org.elkoserver.server.context.ObjectCompletionWatcher
import org.elkoserver.server.context.User
import org.elkoserver.server.context.UserWatcher

/**
 * A simple context mod to enable users in a context to chat with
 * each other.
 */
class SimpleChat @JSONMethod("allowpush") constructor(allowPush: OptBoolean) : Mod(), ObjectCompletionWatcher, ContextMod {
    /** Whether users are permitted to push URLs to other users.  */
    private val amAllowingPush = allowPush.value(false)
    override fun encode(control: EncodeControl) =
            JSONLiteralFactory.type("schat", control).apply {
                if (!control.toClient()) {
                    addParameter("allowpush", amAllowingPush)
                }
                finish()
            }

    override fun objectIsComplete() {
        context().registerUserWatcher(
                object : UserWatcher {
                    override fun noteUserArrival(who: User) {
                        PrivateChat().attachTo(who)
                    }

                    override fun noteUserDeparture(who: User) {}
                }
        )
    }

    @JSONMethod("url", "frame")
    fun push(from: User, url: String, frame: OptString) {
        if (amAllowingPush) {
            ensureSameContext(from)
            context().send(msgPush(context(), from, url, frame.value(null)))
        } else {
            throw MessageHandlerException("push not allowed here")
        }
    }

    @JSONMethod("speech")
    fun say(from: User?, speech: String?) {
        ensureSameContext(from)
        context().send(msgSay(context(), from, speech))
    }

    companion object {
        private fun msgPush(target: Referenceable, from: Referenceable,
                            url: String, frame: String) =
                targetVerb(target, "push").apply {
                    addParameter("from", from)
                    addParameter("url", url)
                    addParameterOpt("frame", frame)
                    finish()
                }

        fun msgSay(target: Referenceable?, from: Referenceable?, speech: String?) =
                targetVerb(target, "say").apply {
                    addParameter("from", from)
                    addParameter("speech", speech)
                    finish()
                }
    }
}
