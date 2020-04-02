package org.elkoserver.server.context;

import org.elkoserver.foundation.timer.Timeout;
import org.elkoserver.foundation.timer.TimeoutNoticer;
import org.elkoserver.foundation.timer.Timer;
import org.elkoserver.util.trace.TraceFactory;

/**
 * Object that tracks reservations issued by directors but not yet redeemed by
 * users.
 */
class Reservation implements TimeoutNoticer {
    /** Who this reservation is for (user ID or name). */
    private String myWho;

    /** What context. */
    private String myWhere;

    /** Reservation authorization code. */
    private String myAuthCode;

    /** The director that issued this reservation. */
    private DirectorActor myIssuer;
    private final TraceFactory traceFactory;

    /** Timeout for expiring an unredeemed reservation. */
    private Timeout myExpirationTimeout;

    /** Flag that this reservation has been redeemed (in case timeout trips
        before it can be cancelled). */
    private boolean amRedeemed;

    /**
     * Construct a data-holding reservation.  These have no issuer and are only
     * used as keys for lookups in the reservation Map.
     *
     * @param who  Who reservation is for.
     * @param where   Context it's for.
     * @param authCode  Secret authorization code.
     */
    Reservation(String who, String where, String authCode, TraceFactory traceFactory) {
        myWho = who;
        myWhere = where;
        myAuthCode = authCode;
        amRedeemed = false;
        myIssuer = null;

        this.traceFactory = traceFactory;
    }

    /**
     * Construct an expiring reservation.
     *
     * @param who  Who reservation is for.
     * @param where  Context it's for.
     * @param authCode  Secret authorization code.
     * @param expirationTime  How long this reservation is good for, in
     *    milliseconds.
     * @param issuer  What director issued this, or null if don't care.
     */
    Reservation(String who, String where, String authCode, int expirationTime,
                DirectorActor issuer, Timer timer, TraceFactory traceFactory)
    {
        this(who, where, authCode, traceFactory);
        myIssuer = issuer;
        myExpirationTimeout = timer.after(expirationTime, this);
    }

    /**
     * Compare two reservation objects for equality.
     */
    public boolean equals(Object obj) {
        if (obj instanceof Reservation) {
            Reservation other = (Reservation) obj;
            if (!myAuthCode.equals(other.myAuthCode)) {
                return false;
            }
            if (!myWhere.equals(other.myWhere)) {
                return false;
            }
            if (myWho == null && other.myWho == null) {
                return true;
            }
            if (myWho == null || other.myWho == null) {
                return false;
            }
            return myWho.equals(other.myWho);
        } else {
            return false;
        }
    }

    /**
     * Compute a reservation hash code.
     */
    public int hashCode() {
        int result = myAuthCode.hashCode() ^ myWhere.hashCode();
        if (myWho != null) {
            result ^= myWho.hashCode();
        }
        return result;
    }

    /**
     * Get this reservations issuer.
     *
     * @return this reservation's issuer.
     */
    DirectorActor issuer() {
        return myIssuer;
    }

    /**
     * Handle reservation expiration.
     */
    public void noticeTimeout() {
        if (!amRedeemed) {
            amRedeemed = true;
            myIssuer.removeReservation(this);
            traceFactory.comm.eventi("expiring reservation " + myWho + "|" + myWhere +
                              "|" + myAuthCode);
        }
    }

    /**
     * Redeem this reservation.
     */
    void redeem() {
        if (!amRedeemed) {
            amRedeemed = true;
            myIssuer.removeReservation(this);
            if (myExpirationTimeout != null) {
                myExpirationTimeout.cancel();
            }
        }
    }
}
