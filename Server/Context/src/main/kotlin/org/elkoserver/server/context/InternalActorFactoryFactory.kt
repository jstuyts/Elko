package org.elkoserver.server.context

import org.elkoserver.foundation.server.metadata.AuthDesc
import org.elkoserver.util.trace.slf4j.Gorgel

internal class InternalActorFactoryFactory(
        private val myContextor: Contextor,
        private val internalActorGorgel: Gorgel,
        private val internalActorCommGorgel: Gorgel,
        private val mustSendDebugReplies: Boolean) {

    fun create(auth: AuthDesc) =
            InternalActorFactory(myContextor, auth, internalActorGorgel, internalActorCommGorgel, mustSendDebugReplies)
}