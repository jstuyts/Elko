package org.elkoserver.server.context

import org.elkoserver.foundation.timer.Timer
import org.elkoserver.util.trace.slf4j.Gorgel

class ReservationFactory(
        private val reservationGorgel: Gorgel,
        private val reservationTimeout: Int,
        private val timer: Timer) {
    fun create(who: String?, context: String, reservation: String, from: DirectorActor) =
            Reservation(who, context, reservation, reservationTimeout, from, timer, reservationGorgel)

    fun create(who: String?, where: String, authCode: String) =
            Reservation(who, where, authCode, reservationGorgel)
}
