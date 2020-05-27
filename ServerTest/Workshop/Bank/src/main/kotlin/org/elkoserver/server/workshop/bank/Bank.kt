package org.elkoserver.server.workshop.bank

import org.elkoserver.foundation.json.ClockUsingObject
import org.elkoserver.foundation.json.JSONMethod
import org.elkoserver.foundation.json.OptString
import org.elkoserver.foundation.json.PostInjectionInitializingObject
import org.elkoserver.json.Encodable
import org.elkoserver.json.EncodeControl
import org.elkoserver.json.JSONLiteralFactory
import org.elkoserver.json.JsonArray
import org.elkoserver.json.JsonObject
import org.elkoserver.server.workshop.Workshop
import org.elkoserver.util.trace.Trace
import java.security.SecureRandom
import java.time.Clock
import java.util.function.Consumer
import kotlin.math.abs

/**
 * The Elko bank object.
 *
 * Each of the key abstractions managed by the bank is represented by its own
 * class: Account, Bank, Currency, Encumbrance, ExpirationDate and Key.
 *
 * However, due to way things get read and written, the persistent
 * representation consists of a smaller number of somewhat more complicated
 * objects.  In the persistent form there are two kinds of objects: the account
 * and the bank.
 *
 * ExpirationDate objects have a very simple representation and so are simply
 * represented as attributes of the things they are expiration dates for (keys
 * and encumbrances).
 *
 * Currency and Key objects are stored as part of the persistent form of the
 * Bank object.  The central motivating idea here is that these objects are (a)
 * few in number and (b) change infrequently.  Consequenly, we manage these
 * objects' persistent states as part of the persistent state of the Bank and
 * simply checkpoint the bank whenever a currency is added or a key is created
 * or cancelled.
 *
 * Encumbrances are stored as part of the persistent form of the Account
 * object.  Each account stores the collection of encumbrances on that account.
 * The central motivating idea here is that an encumbrance is innately
 * associated with the account that it encumbers, and further that, although
 * there may be a large number of encumbrances overall, there will typically be
 * a small number (typically zero) of encumbrances on any particular account.
 * Normally we will have little reason to be messing about with an encumbrance
 * except as part of messing about with its account, so reading and writing an
 * account object simply to modify an encumbrance doesn't generally introduce
 * additional overhead because we'd normally have to be reading or writing the
 * account anyway in such a case.
 *
 *  @param myRef  The ref of this bank object.
 * @param rootKeyRef  Optional ref of the key that is the root of this
 *    bank's authorization key hierarchy.  If omitted, a new root key will
 *    be generated and made available for issuance.
 * @param keys  Array of keys for access to this bank's contents.
 * @param currencies  Array of currencies this bank is managing.
 * @param accountCollection  Optional collection name for account storage.
 */
internal class Bank @JSONMethod("ref", "rootkey", "keys", "currencies", "collection")
constructor(private val myRef: String, rootKeyRef: OptString, keys: Array<Key>, currencies: Array<Currency>, accountCollection: OptString) : Encodable, ClockUsingObject, PostInjectionInitializingObject {
    /** Currently defined currencies, by name.  */
    private val myCurrencies: MutableMap<String, Currency> = HashMap()

    /** Access keys, by key ref.  */
    private val myKeys: MutableMap<String?, Key?> = HashMap()

    /** A new root key that has been generated but not issued.  */
    private var myVirginRootKey: Key? = null

    /** The reference string of this bank's root key.  */
    private var myRootKeyRef: String?

    /** The workshop in which this bank is running.  */
    private var myWorkshop: Workshop? = null

    /** Trace object, for logging.  */
    private var myTrace: Trace? = null

    /** MongoDB collection into which account data will be stored.  */
    private val myAccountCollection: String?
    private lateinit var clock: Clock
    override fun setClock(clock: Clock) {
        this.clock = clock
    }

    override fun initialize() {
        myVirginRootKey = null
        var rootKeyGenerated = false
        if (myRootKeyRef == null) {
            myRootKeyRef = generateRef("key")
            rootKeyGenerated = true
        }
        val rootKey = Key(null, myRootKeyRef, "full", null,
                ExpirationDate(Long.MAX_VALUE, clock), "root key")
        myKeys[myRootKeyRef] = rootKey
        if (rootKeyGenerated) {
            myVirginRootKey = rootKey
        }
        for (key in myKeys.values) {
            if (key != rootKey) {
                val parentKey = myKeys[key!!.parentRef()]
                if (parentKey != null) {
                    key.setParent(parentKey)
                } else {
                    throw Error("key ${key.ref} claims non-existent parent key ${key.parentRef()}")
                }
            }
        }
    }

    /**
     * Encode this bank for transmission or persistence.
     *
     * @param control  Encode control determining what flavor of encoding
     * should be done.
     *
     * @return a JSON literal representing this bank.
     */
    override fun encode(control: EncodeControl) =
            if (control.toRepository()) {
                JSONLiteralFactory.type("bank", control).apply {
                    addParameter("ref", myRef)
                    addParameter("rootkey", myRootKeyRef)
                    val rootKey = myKeys.remove(myRootKeyRef)
                    addParameter("keys", myKeys.values.toTypedArray())
                    myKeys[myRootKeyRef] = rootKey
                    addParameter("currencies", myCurrencies.values.toTypedArray())
                    addParameterOpt("collection", myAccountCollection)
                    finish()
                }
            } else {
                null
            }

    /**
     * Make this bank live in a running workshop.
     *
     * @param workshop  The workshop this bank is running inside.
     */
    fun activate(workshop: Workshop) {
        myWorkshop = workshop
        myTrace = workshop.tr
        if (myVirginRootKey != null) {
            checkpoint()
        }
    }

    /**
     * Write this bank's state to persistent storage.
     */
    private fun checkpoint() {
        myWorkshop!!.putObject(myRef, this)
    }

    /**
     * Obtain an object that can be used for enumerating the valid currencies.
     *
     * @return an iterable over the current collection of currencies.
     */
    fun currencies() = myCurrencies.values

    /**
     * Delete a key.  This has the side effect of also deleting any keys for
     * which the given key is an ancestor.
     *
     * @param key  The key to be deleted.
     */
    fun deleteKey(key: Key?) {
        for (otherKey in myKeys.values) {
            if (otherKey!!.hasAncestor(key!!)) {
                myKeys.remove(otherKey.ref)
            }
        }
        myKeys.remove(key!!.ref)
        checkpoint()
    }

    /**
     * Inner logic of single-account atomic update, shared in common by the
     * withAccount() and withEncumberedAccount() methods.
     *
     * Note that at the stage at which this method is invoked it is not yet
     * known if the object that was read from the database is actually an
     * account object, but it should be.  Part of the job of this method is to
     * test for that.
     *
     * @param refRef  The ref by which the account or encumbrance was located.
     * @param allegedAccount  The object that was read from the database.
     * @param updater  Updater to effect the desired account modification.
     */
    private fun doAccountUpdate(refRef: String, allegedAccount: Any, updater: AccountUpdater) {
        if (allegedAccount is Account) {
            allegedAccount.releaseExpiredEncumbrances()
            if (updater.modify(allegedAccount)) {
                allegedAccount.checkpoint(myWorkshop!!, myAccountCollection, Consumer<Any?> { resultObj: Any? ->
                    val failure = resultObj as String?
                    when {
                        failure == null -> updater.complete(null)
                        failure[0] == '@' -> {
                            /* Retryable error */
                            myTrace!!.debugm("${allegedAccount.ref} transaction retry: $failure")
                            withAccount(allegedAccount.ref, updater)
                        }
                        else -> {
                            /* Un-retryable error */
                            myTrace!!.errorm("${allegedAccount.ref} transaction aborted: $failure")
                            updater.complete(failure)
                        }
                    }
                })
            }
        } else {
            myTrace!!.errorm("alleged account object obtained via $refRef is not an account")
            updater.modify(null)
        }
    }

    /**
     * Inner logic of dual-account atomic update, shared in common by the
     * withTwoAccounts() and withEncumbranceAndAccount() methods.
     *
     * Note that at the stage at which this method is invoked it is not yet
     * known if the objects that were read from the database are actually
     * account objects, but they should be.  Part of the job of this method is
     * to test for that.  Further, because the order in which the database
     * retrieves objects in a multi-object query is undefined, even though one
     * object will correspond to the first ref parametr and the other will
     * correspond to the second, at the stage at which this is called, it is
     * not known which is which.  It is also the job of this method to ensure
     * that the two account objects are in the proper order before passing them
     * to the updater.
     *
     * @param refRef1  The ref by which the first account or encumbrance was
     * located.
     * @param allegedAccount1  The first object that was read from the
     * database.
     * @param refRef2  The ref by which the second account or encumbrance was
     * located.
     * @param allegedAccount2  The second object that was read from the
     * database.
     * @param updater  Updater to effect the desired account modifications.
     */
    private fun doDualAccountUpdate(refRef1: String, allegedAccount1: Any,
                                    refRef2: String, allegedAccount2: Any,
                                    updater: DualAccountUpdater) {
        if (allegedAccount1 is Account &&
                allegedAccount2 is Account) {
            var readAccount1 = allegedAccount1
            var readAccount2 = allegedAccount2
            if (refRef2 != readAccount2.ref) {
                val temp = readAccount1
                readAccount1 = readAccount2
                readAccount2 = temp
            }
            val account1 = readAccount1
            val account2 = readAccount2
            account1.releaseExpiredEncumbrances()
            account2.releaseExpiredEncumbrances()
            if (updater.modify(account1, account2)) {
                account1.checkpoint(myWorkshop!!, myAccountCollection, Consumer<Any?> { resultObj: Any? ->
                    val failure = resultObj as String?
                    when {
                        failure == null ->
                            account2.checkpoint(myWorkshop!!, myAccountCollection, Consumer<Any?> { resultObj2: Any? ->
                                val failure2 = resultObj2 as String?
                                when {
                                    failure2 == null -> updater.complete(null)
                                    failure2[0] == '@' -> {
                                        /* Not-really-retryable error */
                                        myTrace!!.errorm("Egregious failure: ${account2.ref} atomic update failure: $failure2 AFTER phase 1 update success!")
                                    }
                                    else -> {
                                        /* Really-unretryable error */
                                        myTrace!!.errorm("Egregious failure: ${account2.ref} write failure: $failure2 AFTER phase 1 update success!")
                                    }
                                }
                            })
                        failure[0] == '@' -> {
                            /* Retryable error */
                            myTrace!!.debugm("${account1.ref} transaction retry: $failure")
                            withTwoAccounts(account1.ref, account2.ref, updater)
                        }
                        else -> {
                            /* Un-retryable error */
                            myTrace!!.errorm("${account1.ref} transaction aborted: $failure")
                            updater.complete(failure)
                        }
                    }
                })
            }
        } else {
            myTrace!!.errorm("at least one alleged account object obtained via $refRef1+$refRef2 is not an account")
            updater.modify(null, null)
        }
    }

    /**
     * Generate a new ref string for a new object.
     *
     * @param prefix  Ref prefix onto which an additional, unguessable token
     * will be appended.
     *
     * @return a new reference string with the given prefix.
     */
    fun generateRef(prefix: String) = prefix + '-' + java.lang.Long.toHexString(abs(theRandom.nextLong()))

    /**
     * Lookup a currency by its name.
     *
     * @param currency  Name of the currency of interest.
     *
     * @return a Currency object describing the named currency, or null if
     * there is no such currency.
     */
    fun getCurrency(currency: String) = myCurrencies[currency]

    /**
     * Lookup a key by its reference string.
     *
     * @param keyRef  The key desired.
     *
     * @return the Key with the given ref, or null if there is none.
     */
    fun getKey(keyRef: String?) =
            if (keyRef == null) {
                null
            } else {
                var key = myKeys[keyRef]
                if (key!!.isExpired) {
                    deleteKey(key)
                    key = null
                }
                key
            }

    /**
     * Obtain the bank's root key if nobody has yet gotten it.  This method
     * can only be usefully called once.
     *
     * @return this bank's root key if it hasn't previously been issued, else
     * null.
     */
    fun issueRootKey(): Key? {
        val result = myVirginRootKey
        myVirginRootKey = null
        return result
    }

    /**
     * Create a new account.
     *
     * @param currency  The currency in which the account will be denominated.
     * @param owner  User ref of the new account's owner.
     * @param memo  Annotation on the account.
     *
     * @return a new, zero-balance, unencumbered account created according to
     * the given parameters.
     */
    fun makeAccount(currency: String, owner: String?, memo: String?) =
            if (getCurrency(currency) != null) {
                val account = Account(generateRef("acct"), 0, currency, owner, memo)
                account.checkpoint(myWorkshop!!, myAccountCollection, null)
                account
            } else {
                throw Error("invalid currency $currency")
            }

    /**
     * Create a new currency.
     *
     * @param currency   Name of the new currency.
     * @param memo  Annotation on the currency.
     */
    fun makeCurrency(currency: String, memo: String?) {
        val newCurrency = Currency(currency, memo!!)
        myCurrencies[currency] = newCurrency
        checkpoint()
    }

    /**
     * Create a new authorization key.
     *
     * @param parentKey  The key that is to be the parent of the new key.
     * @param auth  Authority of the new key.
     * @param currs  Optional currencies scoping the new key's authority.
     * @param expires   Optional expiration date on the key.
     * @param memo  Annotation on the key.
     *
     * @return a new key created according to the given parameters.
     */
    fun makeKey(parentKey: Key?, auth: String?, currs: Array<String>?,
                expires: ExpirationDate?, memo: String?): Key {
        val key = Key(parentKey, generateRef("key"), auth!!, currs, expires!!, memo!!)
        myKeys[key.ref] = key
        checkpoint()
        return key
    }

    /**
     * Produce a MongoDB query object to lookup an account from its ref.
     *
     * This method constructs a query object with the form:
     *
     * { ref: REF }
     *
     * @param ref  The ref of the account.
     *
     * @return a JsonObject suitable for querying MongoDB.
     */
    private fun queryAccount(ref: String): JsonObject {
        val queryTemplate = JsonObject()
        queryTemplate.put("ref", ref)
        return queryTemplate
    }

    /**
     * Produce a MongoDB query object to lookup an account from the ref of an
     * encumbrance on that account.
     *
     * This method constructs a query object with the form:
     *
     * { type: "bankacct", encs: { $elemMatch: { ref: ENCREF }}}
     *
     * @param encRef  The ref of the encumbrance.
     *
     * @return a JsonObject suitable for querying MongoDB.
     */
    private fun queryEnc(encRef: String) =
            JsonObject().apply {
                val encMatch = JsonObject().apply {
                    val encMatchPattern = JsonObject().apply {
                        put("ref", encRef)
                    }
                    put("\$elemMatch", encMatchPattern)
                }
                put("type", "bankacct")
                put("encs", encMatch)
            }

    /**
     * Produce a MongoDB query object to obtain two accounts, one designated by
     * the ref of one of its encumbrances and the other by the its ref
     * directly.
     *
     * @param encRef  The ref of an encumbrance on the first account desired.
     * @param accountRef  The ref of the second account desired.
     *
     * @return a JsonObject suitable for querying MongoDB.
     */
    private fun queryEncAndAccount(encRef: String, accountRef: String) =
            queryOr(queryEnc(encRef), queryAccount(accountRef))

    /**
     * Produce a MongoDB query object to obtain the combined output of two
     * other queries.
     *
     * This method constructs a query object with the form:
     *
     * { $or: [ QUERY1, QUERY2 ] }
     *
     * @param query1  The first query.
     * @param query2  The second query.
     *
     * @return a JsonObject suitable for querying MongoDB.
     */
    private fun queryOr(query1: JsonObject, query2: JsonObject) =
            JsonObject().apply {
                val terms = JsonArray().apply {
                    add(query1)
                    add(query2)
                }
                put("\$or", terms)
            }

    /**
     * Produce a MongoDB query object to obtain two accounts by their refs.
     *
     * @param ref1  The ref of the first account desired.
     * @param ref2  The ref of the second account desired.
     *
     * @return a JsonObject suitable for querying MongoDB.
     */
    private fun queryTwoAccounts(ref1: String, ref2: String) =
            queryOr(queryAccount(ref1), queryAccount(ref2))

    /**
     * Lookup an account by its reference string and perform some operation on
     * it.
     *
     * @param accountRef  The ref of the account to be manipulated.
     * @param updater  Updater object that will effect the desired account
     * manipulation.
     */
    fun withAccount(accountRef: String, updater: AccountUpdater) {
        myWorkshop!!.getObject(accountRef, myAccountCollection,
                Consumer { obj: Any? ->
                    if (obj == null) {
                        myTrace!!.errorm("account object $accountRef not found")
                        updater.modify(null)
                    } else {
                        doAccountUpdate(accountRef, obj, updater)
                    }
                })
    }

    /**
     * Lookup an account by the reference string of an encumbrance on the
     * account, and perform some operation on it.
     *
     * @param encRef  The ref of an encumbrance on the account to be
     * manipulated.
     * @param updater  Updater object that will effect the desired account
     * manipulation.
     */
    fun withEncumberedAccount(encRef: String, updater: AccountUpdater) {
        myWorkshop!!.queryObjects(queryEnc(encRef), myAccountCollection, 1,
                Consumer { queryResult: Any? ->
                    if (queryResult == null) {
                        myTrace!!.errorm("encumbrance object $encRef not found")
                        updater.modify(null)
                    } else if (queryResult is Array<*>) {
                        if (queryResult.size == 1) {
                            doAccountUpdate(encRef, queryResult[0] as Any, updater)
                        } else {
                            myTrace!!.errorm("wrong number of query results for $encRef")
                            updater.modify(null)
                        }
                    } else {
                        myTrace!!.errorm("query results for $encRef are malformed")
                        updater.modify(null)
                    }
                })
    }

    /**
     * Lookup a pair of accounts, one by the reference string of an encumbrance
     * and the other directly, and perform some joint operation on them
     * atomically.
     *
     * @param encRef  The ref of an encumbrance on the first account to be
     * manipulated.
     * @param accountRef  The ref of the second account to be manipulated.
     * @param updater  Updater object that will effect the desired account
     * manipulations.
     */
    fun withEncumbranceAndAccount(encRef: String, accountRef: String, updater: DualAccountUpdater) {
        myWorkshop!!.queryObjects(queryEncAndAccount(encRef, accountRef),
                myAccountCollection, 2, Consumer { queryResult: Any? ->
            if (queryResult == null) {
                myTrace!!.errorm("accounts via $encRef+$accountRef not found")
                updater.modify(null, null)
            } else if (queryResult is Array<*>) {
                if (queryResult.size == 2) {
                    doDualAccountUpdate(encRef, queryResult[0] as Any,
                            accountRef, queryResult[1] as Any, updater)
                } else {
                    myTrace!!.errorm("wrong number of query results for $encRef+$accountRef")
                    updater.modify(null, null)
                }
            } else {
                myTrace!!.errorm("query results for $encRef+$accountRef are malformed")
                updater.modify(null, null)
            }
        })
    }

    /**
     * Lookup a pair of accounts by their reference strings and perform some
     * joint operation on them atomically.
     *
     * @param account1Ref  The ref of the first account to be manipulated.
     * @param account2Ref  The ref of the second account to be manipulated.
     * @param updater  Updater object that will effect the desired account
     * manipulations.
     */
    fun withTwoAccounts(account1Ref: String, account2Ref: String,
                        updater: DualAccountUpdater) {
        myWorkshop!!.queryObjects(queryTwoAccounts(account1Ref, account2Ref),
                myAccountCollection, 2, Consumer { queryResult: Any? ->
            if (queryResult == null) {
                myTrace!!.errorm("accounts $account1Ref+$account2Ref not found")
                updater.modify(null, null)
            } else if (queryResult is Array<*>) {
                if (queryResult.size == 2) {
                    doDualAccountUpdate(account1Ref, queryResult[0] as Any,
                            account2Ref, queryResult[1] as Any, updater)
                } else {
                    myTrace!!.errorm("wrong number of query results for $account1Ref+$account2Ref")
                    updater.modify(null, null)
                }
            } else {
                myTrace!!.errorm("query results for $account1Ref+$account2Ref are malformed")
                updater.modify(null, null)
            }
        })
    }

    companion object {
        /** Random number source for generating new refs.  */
        @Deprecated("Global variable")
        private val theRandom = SecureRandom()
    }

    init {
        for (curr in currencies) {
            myCurrencies[curr.name] = curr
        }
        for (key in keys) {
            myKeys[key.ref] = key
        }
        myAccountCollection = accountCollection.value<String?>(null)
        myRootKeyRef = rootKeyRef.value<String?>(null)
    }
}