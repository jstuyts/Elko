package com.example.game.mods

import org.elkoserver.foundation.json.JsonMethod
import org.elkoserver.foundation.json.MessageHandlerException
import org.elkoserver.foundation.json.OptBoolean
import org.elkoserver.foundation.json.OptString
import org.elkoserver.json.EncodeControl
import org.elkoserver.json.JsonLiteral
import org.elkoserver.json.JsonLiteralFactory
import org.elkoserver.server.context.model.ContextMod
import org.elkoserver.server.context.model.Mod
import org.elkoserver.server.context.model.ObjectCompletionWatcher
import org.elkoserver.server.context.model.User
import org.elkoserver.server.context.model.UserWatcher

/**
 * A simple context mod to enable users in a context to chat with
 * each other.
 */
class SimpleChat @JsonMethod("allowpush") constructor(allowPush: OptBoolean) : Mod(), ObjectCompletionWatcher, ContextMod {
    /** Whether users are permitted to push URLs to other users.  */
    private val amAllowingPush = allowPush.value(false)
    override fun encode(control: EncodeControl): JsonLiteral =
            JsonLiteralFactory.type("schat", control).apply {
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

    @JsonMethod("url", "frame")
    fun push(from: User, url: String, frame: OptString) {
        if (amAllowingPush) {
            ensureSameContext(from)
            context().send(msgPush(context(), from, url, frame.valueOrNull()))
        } else {
            throw MessageHandlerException("push not allowed here")
        }
    }

    @JsonMethod("speech")
    fun say(from: User, speech: String) {
        ensureSameContext(from)
        context().send(msgSay(context(), from, speech))
    }
}
