package org.elkoserver.server.gatekeeper

import org.elkoserver.foundation.json.Injector
import java.security.MessageDigest

class MessageDigestInjector(private val messageDigest: MessageDigest) : Injector {
    override fun inject(any: Any?) {
        if (any is MessageDigestUsingObject) {
            any.setMessageDigest(messageDigest)
        }
    }
}
