package org.elkoserver.server.workshop.bank

import org.elkoserver.foundation.json.JSONMethod
import org.elkoserver.foundation.json.OptBoolean
import org.elkoserver.json.Encodable
import org.elkoserver.json.EncodeControl
import org.elkoserver.json.JSONLiteralFactory
import org.elkoserver.server.workshop.Workshop
import java.util.HashMap
import java.util.TreeSet
import java.util.function.Consumer

/**
 * Object representing an account: a store of money in some currency belonging
 * to some user.
 *
 * @param myRef  Reference string for account.
 * @param myVersion  The current version number of the account.
 * @param myCurrency  Currency in which account will be denominated.
 * @param myOwner  Ref of account owner.
 * @param myMemo  Annotation on account.
 */
internal class Account(private val myRef: String?, private var myVersion: Int, private val myCurrency: String?, private val myOwner: String?, private val myMemo: String?) : Encodable {

    /** Flag that account is blocked from participating in transactions.  */
    var isFrozen = false

    /** Total amount of money (both available & encumbered) in the account.  */
    private var myTotalBalance = 0

    /** Amount of unencumbered funds in account.  */
    private var myAvailBalance = 0

    /** Collection of encumbrances, sorted by expiration date.  */
    private val myEncumbrancesByExpiration = TreeSet<Encumbrance>()

    /** Collection of encumbrances, indexed by ref.  */
    private val myEncumbrancesByRef: MutableMap<String, Encumbrance> = HashMap()

    /** Flag indicating that this account has been deleted.  */
    var isDeleted = false
        private set

    /**
     * JSON-driven constructor.
     *
     * @param ref  Reference string for account.
     * @param version  The current version number of the account.
     * @param currency  Currency in which account will be denominated.
     * @param owner  Ref of account owner.
     * @param memo  Annotation on account.
     * @param totalBalance  Total amount of funds in account (including encumbered)
     * @param frozen  Flag indicating whether or not account is frozen.
     * @param encumbrances  Encumbrances on the account.
     * @param deleted  Flag indicating whether or not account is deleted.
     */
    @JSONMethod("ref", "version", "curr", "owner", "memo", "bal", "frozen", "encs", "deleted")
    constructor(ref: String?, version: Int, currency: String?, owner: String?,
                memo: String?, totalBalance: Int, frozen: Boolean,
                encumbrances: Array<Encumbrance>, deleted: OptBoolean) : this(ref, version, currency, owner, memo) {
        myTotalBalance = totalBalance
        myAvailBalance = totalBalance
        isFrozen = frozen
        for (enc in encumbrances) {
            if (!enc.isExpired) {
                enc.setAccount(this)
                myEncumbrancesByExpiration.add(enc)
                myEncumbrancesByRef[enc.ref()!!] = enc
                myAvailBalance -= enc.amount()
            }
        }
        isDeleted = deleted.value(false)
    }

    /**
     * Encode this account for transmission or persistence.
     *
     * @param control  Encode control determining what flavor of encoding
     * should be done.
     *
     * @return a JSON literal representing this account.
     */
    override fun encode(control: EncodeControl) =
            if (control.toRepository()) {
                JSONLiteralFactory.type("bankacct", control).apply {
                    addParameter("ref", myRef)
                    addParameter("version", myVersion)
                    addParameter("curr", myCurrency)
                    addParameter("owner", myOwner)
                    addParameter("memo", myMemo)
                    addParameter("bal", myTotalBalance)
                    addParameter("frozen", isFrozen)
                    addParameter("encs", myEncumbrancesByExpiration.toTypedArray())
                    if (isDeleted) {
                        addParameter("deleted", true)
                    }
                    finish()
                }
            } else {
                null
            }

    /**
     * Obtain the available account balance.  This is the amount of
     * unencumbered funds currently in the account.
     *
     * @return the quantity of available funds in the account.
     */
    fun availBalance() = myAvailBalance

    /**
     * Save the state of this account to persistent storage.
     *
     * @param workshop  Workshop object this account is being managed within,
     * for access to the persistent store.
     * @param collection  MongoDB collection into which to save the account.
     * @param resultHandler  Handler that will be invoked with status of
     * write, after completion.
     */
    fun checkpoint(workshop: Workshop, collection: String?,
                   resultHandler: Consumer<Any?>?) {
        if (myVersion == 0) {
            myVersion = 1
            workshop.putObject(myRef, this, collection, resultHandler)
        } else {
            workshop.updateObject(myRef, myVersion++, this, collection,
                    resultHandler)
        }
    }

    /**
     * Obtain the currency in which this account is denominated.
     *
     * @return the name of this account's currency.
     */
    fun currency(): String? {
        return myCurrency
    }

    /**
     * Mark this account as deleted.
     */
    fun delete() {
        if (myTotalBalance > 0) {
            throw Error("attempt to delete non-empty account")
        }
        isDeleted = true
    }

    /**
     * Add to this account's balance.
     *
     * @param amount  The amount of funds to deposit.
     */
    fun deposit(amount: Int) {
        if (amount < 0) {
            throw Error("deposit negative amount")
        }
        myTotalBalance += amount
        myAvailBalance += amount
    }

    /**
     * Encumber some of this account's available funds.
     *
     * @param enc  The encumbrance to add to this account.
     */
    fun encumber(enc: Encumbrance) {
        if (myAvailBalance < enc.amount()) {
            throw Error("insufficient funds")
        }
        myEncumbrancesByExpiration.add(enc)
        myEncumbrancesByRef[enc.ref()!!] = enc
        myAvailBalance -= enc.amount()
    }

    /**
     * Obtain an object that can be used for enumerating the account's
     * encumbrances.
     *
     * @return an interable over the current collection of encumbrances.
     */
    fun encumbrances() = myEncumbrancesByExpiration

    /**
     * Obtain one of this account's encumbrances.
     *
     * @param encRef  The ref of the encumbrance sought.
     *
     * @return the requested encumbrance, or null if the given ref does not
     * designate one of this account's encumbrances.
     */
    fun getEncumbrance(encRef: String) = myEncumbrancesByRef[encRef]

    /**
     * Obtain this account's memo string, an arbitrary annotation associated
     * with the account when it was created.
     *
     * @return this account's memo.
     */
    fun memo() = myMemo

    /**
     * Obtain the ref of the account's owner.
     *
     * @return this account's owner ref.
     */
    fun owner() = myOwner

    /**
     * Redeem an encumbrance on this account, subtracting the encumbered
     * amount from the total balance.
     *
     * @param enc  The encumbrance being redeemed.
     *
     * @return the amount of money withdrawn by the redemption.
     */
    fun redeemEncumbrance(enc: Encumbrance): Int {
        myEncumbrancesByExpiration.remove(enc)
        myEncumbrancesByRef.remove(enc.ref())
        myTotalBalance -= enc.amount()
        return enc.amount()
    }

    /**
     * Obtain this account's unique identifier string.
     *
     * @return this account's ref.
     */
    fun ref() = myRef

    /**
     * Release an encumbrance from this account, adding the encumbered amount
     * back to the available balance.
     *
     * @param enc  The encumbrance to release.
     */
    fun releaseEncumbrance(enc: Encumbrance) {
        if (!myEncumbrancesByExpiration.remove(enc)) {
            throw Error("attempt to remove encumbrance that wasn't there")
        }
        myEncumbrancesByRef.remove(enc.ref())
        myAvailBalance += enc.amount()
    }

    /**
     * Release any expired encumbrances on this account.
     */
    fun releaseExpiredEncumbrances() {
        while (!myEncumbrancesByExpiration.isEmpty()) {
            val first = myEncumbrancesByExpiration.first()
            if (first.isExpired) {
                first.release()
            } else {
                break
            }
        }
    }

    /**
     * Obtain the total amount of money in this account, both available and
     * encumbered.
     *
     * @return this account's total balance.
     */
    fun totalBalance() = myTotalBalance

    /**
     * Subtract from this account's balance.
     *
     * @param amount  The amount of funds to withdraw.
     */
    fun withdraw(amount: Int) {
        if (amount < 0) {
            throw Error("withdraw negative amount")
        } else if (myAvailBalance < amount) {
            throw Error("insufficient funds")
        }
        myTotalBalance -= amount
        myAvailBalance -= amount
    }
}
