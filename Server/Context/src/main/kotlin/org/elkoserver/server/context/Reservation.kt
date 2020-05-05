package org.elkoserver.server.context

import org.elkoserver.foundation.timer.Timeout
import org.elkoserver.foundation.timer.TimeoutNoticer
import org.elkoserver.foundation.timer.Timer
import org.elkoserver.util.trace.TraceFactory

/**
 * Object that tracks reservations issued by directors but not yet redeemed by
 * users.
 * @param myWho  Who reservation is for.
 * @param myWhere   Context it's for.
 * @param myAuthCode  Secret authorization code.
 */
class Reservation(
        private val myWho: String?,
        private val myWhere: String,
        private val myAuthCode: String?, private val traceFactory: TraceFactory) : TimeoutNoticer {

    /** The director that issued this reservation.  */
    private var myIssuer: DirectorActor? = null

    /** Timeout for expiring an unredeemed reservation.  */
    private var myExpirationTimeout: Timeout? = null

    /** Flag that this reservation has been redeemed (in case timeout trips
     * before it can be cancelled).  */
    private var amRedeemed = false

    constructor(who: String?, where: String, authCode: String, expirationTime: Int,
                issuer: DirectorActor?, timer: Timer, traceFactory: TraceFactory) : this(who, where, authCode, traceFactory) {
        myIssuer = issuer
        myExpirationTimeout = timer.after(expirationTime.toLong(), this)
    }

    /**
     * Compare two reservation objects for equality.
     */
    override fun equals(other: Any?): Boolean {
        return if (other is Reservation) {
            if (myAuthCode != other.myAuthCode) {
                return false
            }
            if (myWhere != other.myWhere) {
                return false
            }
            if (myWho == null && other.myWho == null) {
                return true
            }
            if (myWho == null || other.myWho == null) {
                false
            } else myWho == other.myWho
        } else {
            false
        }
    }

    /**
     * Compute a reservation hash code.
     */
    override fun hashCode(): Int {
        var result = myAuthCode.hashCode() xor myWhere.hashCode()
        if (myWho != null) {
            result = result xor myWho.hashCode()
        }
        return result
    }

    /**
     * Get this reservations issuer.
     *
     * @return this reservation's issuer.
     */
    fun issuer() = myIssuer

    /**
     * Handle reservation expiration.
     */
    override fun noticeTimeout() {
        if (!amRedeemed) {
            amRedeemed = true
            myIssuer!!.removeReservation(this)
            traceFactory.comm.eventi("expiring reservation " + myWho + "|" + myWhere +
                    "|" + myAuthCode)
        }
    }

    /**
     * Redeem this reservation.
     */
    fun redeem() {
        if (!amRedeemed) {
            amRedeemed = true
            myIssuer!!.removeReservation(this)
            myExpirationTimeout?.cancel()
        }
    }
}
