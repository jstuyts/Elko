package org.elkoserver.server.context

import org.elkoserver.foundation.timer.Timeout
import org.elkoserver.foundation.timer.TimeoutNoticer
import org.elkoserver.foundation.timer.Timer
import org.elkoserver.util.trace.slf4j.Gorgel
import org.elkoserver.util.trace.slf4j.Tag

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
        private val myAuthCode: String?,
        myGorgel: Gorgel) : TimeoutNoticer {

    private val communicationsGorgel = myGorgel.withAdditionalStaticTags(Tag("category", "comm"))

    /** The director that issued this reservation.  */
    internal var issuer: DirectorActor? = null

    /** Timeout for expiring an unredeemed reservation.  */
    private var myExpirationTimeout: Timeout? = null

    /** Flag that this reservation has been redeemed (in case timeout trips
     * before it can be cancelled).  */
    private var amRedeemed = false

    constructor(who: String?, where: String, authCode: String, expirationTime: Int,
                theIssuer: DirectorActor?, timer: Timer, gorgel: Gorgel) : this(who, where, authCode, gorgel) {
        issuer = theIssuer
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
     * Handle reservation expiration.
     */
    override fun noticeTimeout() {
        if (!amRedeemed) {
            amRedeemed = true
            issuer!!.removeReservation(this)
            communicationsGorgel.i?.run { info("expiring reservation $myWho|$myWhere|$myAuthCode") }
        }
    }

    /**
     * Redeem this reservation.
     */
    fun redeem() {
        if (!amRedeemed) {
            amRedeemed = true
            issuer!!.removeReservation(this)
            myExpirationTimeout?.cancel()
        }
    }
}
