package org.elkoserver.server.gatekeeper

import java.security.MessageDigest

// FIXME: Different classes (objects even?) may need different digests.
interface MessageDigestUsingObject {
    fun setMessageDigest(messageDigest: MessageDigest)
}
