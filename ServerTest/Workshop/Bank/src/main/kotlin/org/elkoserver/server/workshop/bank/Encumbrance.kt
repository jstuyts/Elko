package org.elkoserver.server.workshop.bank

import org.elkoserver.foundation.json.JSONMethod
import org.elkoserver.foundation.json.OptString
import org.elkoserver.json.Encodable
import org.elkoserver.json.EncodeControl
import org.elkoserver.json.JSONLiteral

/**
 * Object representing an encumbrance, a tentative reservation of the funds in
 * some account.
 *
 * @param myRef  Reference string for the encumbrance
 * @param myAccount  The account being encumbered
 * @param myAmount  The amount being encumbered
 * @param myExpires  When the encumbrance should vanish if not released or
 *    redeemed.
 * @param myMemo  Annotation on encumbrance.
 */
internal class Encumbrance(private val myRef: String?, private var myAccount: Account?, private val myAmount: Int,
                           private val myExpires: ExpirationDate, private val myMemo: String?) : Comparable<Encumbrance>, Encodable {

    /**
     * JSON-driven constructor.
     *
     * @param ref  Reference string for the encumbrance
     * @param amount  The amount being encumbered
     * @param expires  When the encumbrance should vanish if not released or
     * redeemed.
     * @param memo  Optional annotation on encumbrance.
     */
    @JSONMethod("ref", "amount", "expires", "memo")
    constructor(ref: String, amount: Int, expires: ExpirationDate, memo: OptString) : this(ref, null, amount, expires, memo.value(null))

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
                JSONLiteral(control).apply {
                    addParameter("ref", myRef)
                    addParameter("amount", myAmount)
                    addParameter("expires", myExpires)
                    addParameterOpt("memo", myMemo)
                    finish()
                }
            } else {
                null
            }

    /**
     * Obtain the account this encumbrance encumbers.
     *
     * @return this encumbrance's account.
     */
    fun account(): Account? = myAccount

    /**
     * Obtain the quantity of funds this encumbrance encumbers.
     *
     * @return this encumbrance's amount.
     */
    fun amount(): Int = myAmount

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
        var result = myExpires.compareTo(other.myExpires)
        if (result == 0) {
            result = hashCode() - other.hashCode()
        }
        return result
    }

    /**
     * Obtain the date after which this encumbrance no longer encumbers.
     *
     * @return this encumbrance's expiration date.
     */
    fun expires() = myExpires

    /**
     * Test if this encumbrance is expired.
     *
     * @return true if this encumbrance is expired, false if not.
     */
    val isExpired: Boolean
        get() = myExpires.isExpired

    /**
     * Obtain this encumbrance's memo string, an arbitrary annotation
     * associated with the encumbrance when it was created.
     *
     * @return this encumbrance's memo.
     */
    fun memo() = myMemo

    /**
     * Redeem this encumbrance, subtracting the encumbered amount from its
     * account's total balance.
     *
     * @return the amount of money withdrawn by the redemption.
     */
    fun redeem() = myAccount!!.redeemEncumbrance(this)

    /**
     * Obtain this encumbrance's unique identifier string.
     *
     * @return this encumbrance's ref.
     */
    fun ref() = myRef

    /**
     * Release this encumbrance, adding the encumbered amount back to its
     * account's available balance.
     */
    fun release() {
        myAccount!!.releaseEncumbrance(this)
    }

    /**
     * Assign the account associated with this encumbrance.  This operation
     * may only be done once per encumbrance object.
     *
     * @param account   The account that this encumbrance encumbers.
     */
    fun setAccount(account: Account?) {
        if (myAccount != null) {
            throw Error("attempt to set account on encumbrance that already has one")
        }
        myAccount = account
    }
}
