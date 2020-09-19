package org.elkoserver.server.context

import org.elkoserver.foundation.run.Runner
import org.elkoserver.foundation.timer.Timer
import org.elkoserver.idgeneration.IdGenerator
import org.elkoserver.util.trace.slf4j.Gorgel

internal class UserActorFactoryFactory(
        private val myContextor: Contextor,
        private val runner: Runner,
        private val userActorGorgel: Gorgel,
        private val userActorCommGorgel: Gorgel,
        private val userGorgelWithoutRef: Gorgel,
        private val timer: Timer,
        private val idGenerator: IdGenerator,
        private val mustSendDebugReplies: Boolean) {
    fun create(reservationRequired: Boolean, protocol: String) =
            UserActorFactory(myContextor, runner, reservationRequired, protocol, userActorGorgel, userGorgelWithoutRef, timer, userActorCommGorgel, idGenerator, mustSendDebugReplies)
}
