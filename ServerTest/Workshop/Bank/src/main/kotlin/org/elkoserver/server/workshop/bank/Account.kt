package org.elkoserver.server.workshop.bank

import org.elkoserver.foundation.json.JsonMethod
import org.elkoserver.foundation.json.OptBoolean
import org.elkoserver.json.Encodable
import org.elkoserver.json.EncodeControl
import org.elkoserver.json.JsonLiteralFactory
import org.elkoserver.server.workshop.Workshop
import java.util.TreeSet
import java.util.function.Consumer

/**
 * Object representing an account: a store of money in some currency belonging
 * to some user.
 *
 * @param ref  Reference string for account.
 * @param myVersion  The current version number of the account.
 * @param currency  Currency in which account will be denominated.
 * @param owner  Ref of account owner.
 * @param memo  Annotation on account.
 */
internal class Account(internal val ref: String, private var myVersion: Int, internal val currency: String?, internal val owner: String?, internal val memo: String?) : Encodable {

    /** Flag that account is blocked from participating in transactions.  */
    var isFrozen = false

    /** Total amount of money (both available & encumbered) in the account.  */
    internal var totalBalance = 0
        private set

    /** Amount of unencumbered funds in account.  */
    internal var availBalance = 0
        private set

    /** Collection of encumbrances, sorted by expiration date.  */
    internal val encumbrances = TreeSet<Encumbrance>()

    /** Collection of encumbrances, indexed by ref.  */
    private val myEncumbrancesByRef: MutableMap<String, Encumbrance> = HashMap()

    /** Flag indicating that this account has been deleted.  */
    private var isDeleted = false

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
    @Suppress("SpellCheckingInspection")
    @JsonMethod("ref", "version", "curr", "owner", "memo", "bal", "frozen", "encs", "deleted")
    constructor(ref: String, version: Int, currency: String, owner: String,
                memo: String, totalBalance: Int, frozen: Boolean,
                encumbrances: Array<Encumbrance>, deleted: OptBoolean) : this(ref, version, currency, owner, memo) {
        this.totalBalance = totalBalance
        availBalance = totalBalance
        isFrozen = frozen
        for (enc in encumbrances) {
            if (!enc.isExpired) {
                enc.account = this
                this.encumbrances.add(enc)
                myEncumbrancesByRef[enc.ref] = enc
                availBalance -= enc.amount
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
                @Suppress("SpellCheckingInspection")
                JsonLiteralFactory.type("bankacct", control).apply {
                    addParameter("ref", ref)
                    addParameter("version", myVersion)
                    addParameter("curr", currency)
                    addParameter("owner", owner)
                    addParameter("memo", memo)
                    addParameter("bal", totalBalance)
                    addParameter("frozen", isFrozen)
                    addParameter("encs", encumbrances.toTypedArray())
                    if (isDeleted) {
                        addParameter("deleted", true)
                    }
                    finish()
                }
            } else {
                null
            }

    /**
     * Save the state of this account to persistent storage.
     *
     * @param workshop  Workshop object this account is being managed within,
     * for access to the persistent store.
     * @param databaseId  ID of the database to store the account in.
     * @param resultHandler  Handler that will be invoked with status of
     * write, after completion.
     */
    fun checkpoint(workshop: Workshop, databaseId: String, resultHandler: Consumer<Any?>?) {
        if (myVersion == 0) {
            myVersion = 1
            workshop.putObject(ref, this, databaseId, resultHandler)
        } else {
            workshop.updateObject(ref, myVersion++, this, databaseId, resultHandler)
        }
    }

    /**
     * Mark this account as deleted.
     */
    fun delete() {
        if (totalBalance > 0) {
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
        totalBalance += amount
        availBalance += amount
    }

    /**
     * Encumber some of this account's available funds.
     *
     * @param enc  The encumbrance to add to this account.
     */
    fun encumber(enc: Encumbrance) {
        if (availBalance < enc.amount) {
            throw Error("insufficient funds")
        }
        encumbrances.add(enc)
        myEncumbrancesByRef[enc.ref] = enc
        availBalance -= enc.amount
    }

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
     * Redeem an encumbrance on this account, subtracting the encumbered
     * amount from the total balance.
     *
     * @param enc  The encumbrance being redeemed.
     *
     * @return the amount of money withdrawn by the redemption.
     */
    fun redeemEncumbrance(enc: Encumbrance): Int {
        encumbrances.remove(enc)
        myEncumbrancesByRef.remove(enc.ref)
        totalBalance -= enc.amount
        return enc.amount
    }

    /**
     * Release an encumbrance from this account, adding the encumbered amount
     * back to the available balance.
     *
     * @param enc  The encumbrance to release.
     */
    fun releaseEncumbrance(enc: Encumbrance) {
        if (!encumbrances.remove(enc)) {
            throw Error("attempt to remove encumbrance that wasn't there")
        }
        myEncumbrancesByRef.remove(enc.ref)
        availBalance += enc.amount
    }

    /**
     * Release any expired encumbrances on this account.
     */
    fun releaseExpiredEncumbrances() {
        while (!encumbrances.isEmpty()) {
            val first = encumbrances.first()
            if (first.isExpired) {
                first.release()
            } else {
                break
            }
        }
    }

    /**
     * Subtract from this account's balance.
     *
     * @param amount  The amount of funds to withdraw.
     */
    fun withdraw(amount: Int) {
        if (amount < 0) {
            throw Error("withdraw negative amount")
        } else if (availBalance < amount) {
            throw Error("insufficient funds")
        }
        totalBalance -= amount
        availBalance -= amount
    }
}
