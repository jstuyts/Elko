package org.elkoserver.server.workshop.bank

import org.elkoserver.foundation.json.JsonMethod
import org.elkoserver.foundation.json.OptString
import org.elkoserver.json.Encodable
import org.elkoserver.json.EncodeControl
import org.elkoserver.json.JsonLiteral

/**
 * Object representing an encumbrance, a tentative reservation of the funds in
 * some account.
 *
 * @param ref  Reference string for the encumbrance
 * @param myAccount  The account being encumbered
 * @param amount  The amount being encumbered
 * @param expires  When the encumbrance should vanish if not released or
 *    redeemed.
 * @param memo  Annotation on encumbrance.
 */
internal class Encumbrance(internal val ref: String, theAccount: Account?, internal val amount: Int,
                           internal val expires: ExpirationDate, internal val memo: String?) : Comparable<Encumbrance>, Encodable {
    internal var account: Account? = theAccount
        internal set(value) {
            if (field != null) {
                throw Error("attempt to set account on encumbrance that already has one")
            }
            field = value
        }

    /**
     * JSON-driven constructor.
     *
     * @param ref  Reference string for the encumbrance
     * @param amount  The amount being encumbered
     * @param expires  When the encumbrance should vanish if not released or
     * redeemed.
     * @param memo  Optional annotation on encumbrance.
     */
    @JsonMethod("ref", "amount", "expires", "memo")
    constructor(ref: String, amount: Int, expires: ExpirationDate, memo: OptString) : this(ref, null, amount, expires, memo.value<String?>(null))

    /**
     * Encode this encumbrance for transmission or persistence.
     *
     * @param control  Encode control determining what flavor of encoding
     * should be done.
     *
     * @return a JSON literal representing this encumbrance.
     */
    override fun encode(control: EncodeControl) =
            if (control.toRepository()) {
                JsonLiteral(control).apply {
                    addParameter("ref", ref)
                    addParameter("amount", amount)
                    addParameter("expires", expires)
                    addParameterOpt("memo", memo)
                    finish()
                }
            } else {
                null
            }

    /**
     * Compare this encumbrance to another according to the dictates of the
     * standard Java Comparable interface.  Encumbrances are compared by
     * comparing their expiration dates.  Two encumbrances with the same
     * expiration date are compared by the values of their hash codes, just so
     * that sorts will be stable.
     *
     * @param other  The other encumbrance to compare to.
     *
     * @return a value less than, equal to, or greater than zero according to
     * whether this encumbrance's expiration date is before, at, or after
     * other's.
     */
    override fun compareTo(other: Encumbrance): Int {
        var result = expires.compareTo(other.expires)
        if (result == 0) {
            result = hashCode() - other.hashCode()
        }
        return result
    }

    /**
     * Test if this encumbrance is expired.
     *
     * @return true if this encumbrance is expired, false if not.
     */
    val isExpired: Boolean
        get() = expires.isExpired

    /**
     * Redeem this encumbrance, subtracting the encumbered amount from its
     * account's total balance.
     *
     * @return the amount of money withdrawn by the redemption.
     */
    fun redeem() = account!!.redeemEncumbrance(this)

    /**
     * Release this encumbrance, adding the encumbered amount back to its
     * account's available balance.
     */
    fun release() {
        account!!.releaseEncumbrance(this)
    }
}
