package org.elkoserver.server.workshop.bank.client

import org.elkoserver.foundation.json.JSONMethod
import org.elkoserver.foundation.json.OptBoolean
import org.elkoserver.foundation.json.OptInteger
import org.elkoserver.foundation.json.OptString
import org.elkoserver.foundation.server.ServiceActor
import org.elkoserver.foundation.server.ServiceLink
import org.elkoserver.json.JSONLiteral
import org.elkoserver.json.JSONLiteralFactory
import org.elkoserver.server.context.AdminObject
import org.elkoserver.server.context.Contextor
import org.elkoserver.server.workshop.bank.Currency
import org.elkoserver.util.trace.Trace
import java.util.function.Consumer

/**
 * Internal object that acts as a client for the external 'bank' service.
 */
class BankClient @JSONMethod("servicename") constructor(private val myServiceName: String) : AdminObject(), Consumer<ServiceLink?> {
    /** Connection to the workshop running the bank service.  */
    private var myServiceLink: ServiceLink? = null

    /** Tag string indicating the current state of the service connection.  */
    private var myStatus = "startup"

    /** Collection of handlers for pending requests to the service.  */
    private val myResultHandlers: MutableMap<String, BankReplyHandler> = HashMap()

    /** Counter for generating transaction IDs  */
    private var myXidCounter = 0

    private lateinit var tr: Trace

    /**
     * Make this object live inside the context server.  In this case we
     * initiate a connection to the external bank service.
     *
     * @param ref  Reference string identifying this object in the static
     * object table.
     * @param contextor  The contextor for this server.
     */
    override fun activate(ref: String, contextor: Contextor) {
        super.activate(ref, contextor)
        myStatus = "connecting"
        tr = contextor.appTrace()
        contextor.findServiceLink(myServiceName, this)
    }

    /**
     * Callback that is invoked when the service connection is established or
     * fails to be established.
     *
     * @param obj  The connection to the bank service, or null if connection
     * setup failed.
     */
    override fun accept(obj: ServiceLink?) {
        if (obj != null) {
            myServiceLink = obj
            myStatus = "connected"
        } else {
            myStatus = "failed"
        }
    }

    /**
     * Get the current status of the connection to the external service.
     *
     * @return a tag string describing the current connection state.
     */
    fun status() = myStatus

    /**
     * Handle a failure result, internal version: log the error and then
     * call the application-specific handler.
     *
     * @param replyHandler The reply handler to notify of the failure
     * @param op  The message verb that failed
     * @param fail  Failure tag
     * @param desc  Error description
     */
    private fun innerFail(replyHandler: BankReplyHandler, op: String, fail: String, desc: String) {
        tr.errorm("bank $op failure $fail: $desc")
        replyHandler.fail(fail, desc)
    }

    /**
     * Base class for request-specific handlers for replies from the bank.
     */
    abstract class BankReplyHandler {
        /**
         * Handle a failure result, application-specific version: do whatever
         * the application needs or wants to do in a failure case.  The base
         * implementation here does nothing, but application code can override.
         *
         * @param fail  Failure tag
         * @param desc  Error description
         */
        fun fail(fail: String?, desc: String?) {}
    }

    /**
     * Internal class to hold onto a request under construction.
     */
    private inner class BankRequest internal constructor(op: String, key: String?, memo: String?) {
        val msg: JSONLiteral
        private val myXid: String = "x${myXidCounter++}"

        /**
         * Finish the request under construction and send it.
         *
         * @param resultHandler  Application callback that will process the
         * result from the bank service.
         */
        fun send(resultHandler: BankReplyHandler?) {
            if (myServiceLink != null) {
                if (resultHandler != null) {
                    msg.addParameter("rep", ref())
                    myResultHandlers[myXid] = resultHandler
                }
                msg.finish()
                myServiceLink!!.send(msg)
            } else {
                resultHandler!!.fail("noconn", "no connection to bank service")
            }
        }

        init {
            msg = JSONLiteralFactory.targetVerb(myServiceName, op).apply {
                addParameterOpt("key", key)
                addParameter("xid", myXid)
                addParameterOpt("memo", memo)
            }
        }
    }

    /**
     * Lookup the handler for a received reply.  If the reply indicated an
     * error result, the handler's fail() method will be invoked directly.  If
     * there was no registered handler, the error will be logged.
     *
     * @param xid  Transaction ID on the message, for matching requests with
     * responses.
     * @param op  The message verb of the reply being handled, for logging.
     * @param optFail  The failure tag, present in the event of error.
     * @param optDesc  The error description, present in the event of error.
     *
     * @return the registered reply handler for the given transaction ID, if
     * the reply parameters indicate a successful result, or null if there
     * was a problem.
     */
    private fun handlerForReply(op: String, xid: String,
                                optFail: OptString, optDesc: OptString): BankReplyHandler? {
        val handler = myResultHandlers[xid]
        if (handler == null) {
            tr.errorm("no reply handler for bank xid $xid")
        }
        val fail = optFail.value<String?>(null)
        return if (fail != null) {
            val desc = optDesc.value("")
            if (handler != null) {
                innerFail(handler, op, fail, desc)
            } else {
                tr.errorm("bank $op failure $fail: $desc")
            }
            null
        } else {
            handler
        }
    }

    /**
     * Result handler class for requests that return an account ref (delete
     * account, freeze account, and unfreeze account).
     */
    abstract class AccountResultHandler : BankReplyHandler() {
        /**
         * Handle an account result.
         *
         * @param account  Ref of the account.
         */
        abstract fun result(account: String?)
    }

    /**
     * Result handler class for requests that return an array of account refs
     * (make accounts).
     */
    abstract class AccountsResultHandler : BankReplyHandler() {
        /**
         * Handle an account list result.
         *
         * @param accounts  Array of refs accounts.
         */
        abstract fun result(accounts: Array<String?>?)
    }

    /**
     * Result handler class for requests that affect an account balance (mint,
     * unmint, and unmintEncmbrance).
     */
    abstract class BalanceResultHandler : BankReplyHandler() {
        /**
         * Handle a balance update result.
         *
         * @param acct  Ref of the account effected.
         * @param bal  New available balance in the account.
         */
        abstract fun result(acct: String?, bal: Int)
    }

    /**
     * Result handler class for the make currency request.
     */
    abstract class CurrencyResultHandler : BankReplyHandler() {
        /**
         * Handle a currency result.
         *
         * @param currency  The currency name.
         */
        abstract fun result(currency: String?)
    }

    /**
     * Result handler class for the encumber request.
     */
    abstract class EncumberResultHandler : BankReplyHandler() {
        /**
         * Handle an encumbrance result.
         *
         * @param enc  The ref of the created encumbrance.
         * @param  srcbal  Available balance in the encumbered account after
         * encumbrance.
         */
        abstract fun result(enc: String?, srcbal: Int)
    }

    /**
     * Result handler class for request that return a key (cancel key,
     * duplicate key, issue root key, and make key).
     */
    abstract class KeyResultHandler : BankReplyHandler() {
        /**
         * Handle a key result.
         *
         * @param key  The ref of the key.
         */
        abstract fun result(key: String?)
    }

    /**
     * A struct describing an encumbrance, returned as part of the results from
     * query accounts.
     */
    class EncumbranceDesc
    /**
     * JSON-driven constructor.
     */ @JSONMethod("enc", "amount", "expires", "memo") internal constructor(
            /** The ref of the encumbrance.  */
            val enc: String,
            /** The amount of the encumbrance.  */
            val amount: Int,
            /** When the encumbrance expires.  */
            val expires: String,
            /** Memo string associated with the encumbrance at creation time.  */
            val memo: String)

    /**
     * A struct describing an account, returned as part of the results from
     * query accounts.
     */
    class AccountDesc @JSONMethod("account", "curr", "avail", "total", "frozen", "memo", "owner", "?encs") constructor(
            /** The ref of the account.  */
            val account: String,
            /** Currency the account is denominated in.  */
            val currency: String,
            /** Available (unencumbered) balance in the account.  */
            val avail: Int,
            /**  Total (including encumbered) balance in the account.  */
            val total: Int,
            /** Flag that is true if the account is frozen, false if not.  */
            val frozen: Boolean,
            /** Memo string associated with account at creation time.  */
            val memo: String,
            /** Ref of user who is the owner of the account.  */
            val owner: String,
            /** Array of encumbrance information.  This will be null if the 'encs'
             * parameter of the query was false.  */
            val encs: Array<EncumbranceDesc>?)

    /**
     * Result handler class for the query account request.
     */
    abstract class QueryAccountsResultHandler : BankReplyHandler() {
        /**
         * Handle an accounts query result.
         *
         * @param accounts  Descriptors for the accounts queried.
         */
        abstract fun result(accounts: Array<AccountDesc>?)
    }

    /**
     * Result handler class for the query currencies request.
     */
    abstract class QueryCurrenciesResultHandler : BankReplyHandler() {
        /**
         * Handle an query currencies result.
         *
         * @param currencies  Array of currency descriptors.
         */
        abstract fun result(currencies: Array<Currency>?)
    }

    /**
     * Result handler class for the query encumbrance request.
     */
    abstract class QueryEncumbranceResultHandler : BankReplyHandler() {
        /**
         * Handle an encumbrance query result.
         *
         * @param enc  Ref of the encumbrance.
         * @param currency  Currency the encumbrance is denominated in.
         * @param account  Ref of the encumbered account
         * @param amount  Amount that is encumbered
         * @param expires  Expiration time of the encumbrance
         * @param memo  Memo string associated with encumbrance when created.
         */
        abstract fun result(enc: String?, currency: String?,
                            account: String?, amount: Int, expires: String?,
                            memo: String?)
    }

    /**
     * Result handler class for the release request.
     */
    abstract class ReleaseResultHandler : BankReplyHandler() {
        /**
         * Handle an encumbrance release result.
         *
         * @param src  Ref of the account that was un-encumbered.
         * @param srcbal  Available balance in the account after the release
         * was processed.
         * @param active  Flag that is true if the encumbrance was still active
         * when it was released, fales if it had expired.
         */
        abstract fun result(src: String?, srcbal: Int, active: Boolean)
    }

    /**
     * Result handler class for transfer requests (transfer and
     * transferEncumbrance).
     */
    abstract class TransferResultHandler : BankReplyHandler() {
        /**
         * Handle a transfer result.
         *
         * @param src  Source account ref.
         * @param srcbal  Available balance in src account after transfer.
         * @param dst  Destination account ref.
         * @param dstbal  Available balance in dst account after transfer.
         */
        abstract fun result(src: String?, srcbal: Int, dst: String?,
                            dstbal: Int)
    }

    /**
     * Cancel an authorization key.
     *
     * @param key  Authorizing key.
     * @param memo  Transaction annotation.
     * @param cancel  Ref of key to be cancelled.
     * @param resultHandler  Callback to be invoked on the result.
     */
    fun cancelKey(key: String?, memo: String?, cancel: String?,
                  resultHandler: KeyResultHandler?) {
        val req = BankRequest("cancelkey", key, memo)
        req.msg.addParameter("cancel", cancel)
        req.send(resultHandler)
    }

    /**
     * Delete an account.
     *
     * @param key  Authorizing key.
     * @param memo  Transaction annotation.
     * @param account  Ref of account to be deleted.
     * @param resultHandler  Callback to be invoked on the result.
     */
    fun deleteAccount(key: String?, memo: String?, account: String?,
                      resultHandler: AccountResultHandler?) {
        val req = BankRequest("deleteaccount", key, memo)
        req.msg.addParameter("account", account)
        req.send(resultHandler)
    }

    /**
     * Duplicate an authorization key.
     *
     * @param key  Authorizing key.
     * @param memo  Transaction annotation.
     * @param expires  Expiration time for the new key.
     * @param resultHandler  Callback to be invoked on the result.
     */
    fun dupKey(key: String?, memo: String?, expires: String?,
               resultHandler: KeyResultHandler?) {
        val req = BankRequest("dupkey", key, memo)
        req.msg.addParameterOpt("expires", expires)
        req.send(resultHandler)
    }

    /**
     * Encumber an account, i.e., provisionally reserve funds for a fuure
     * transaction.
     *
     * @param key  Authorizing key.
     * @param memo  Transaction annotation.
     * @param src  Ref of account to be encumbered.
     * @param amount  Quantity of funds to encumber.
     * @param expiration  Expiration time for encumbrance.
     * @param resultHandler  Callback to be invoked on the result.
     */
    fun encumber(key: String?, memo: String?, src: String?, amount: Int,
                 expiration: Long, resultHandler: EncumberResultHandler?) {
        val req = BankRequest("encumber", key, memo)
        req.msg.addParameter("src", src)
        req.msg.addParameter("amount", amount)
        req.msg.addParameter("expires", "+$expiration")
        req.send(resultHandler)
    }

    /**
     * Freeze an account, rendering it temporarily unable to participate in
     * transactions.
     *
     * @param key  Authorizing key.
     * @param memo  Transaction annotation.
     * @param account  Ref of account to be frozen.
     * @param resultHandler  Callback to be invoked on the result.
     */
    fun freezeAccount(key: String?, memo: String?, account: String?,
                      resultHandler: AccountResultHandler?) {
        val req = BankRequest("freezeaccount", key, memo)
        req.msg.addParameter("account", account)
        req.send(resultHandler)
    }

    /**
     * Obtain the bank's root key, if nobody yet has it.
     *
     * @param resultHandler  Callback to be invoked on the result.
     */
    fun issueRootKey(resultHandler: KeyResultHandler?) {
        val req = BankRequest("issuerootkey", null, null)
        req.send(resultHandler)
    }

    /**
     * Create a set of new accounts.
     *
     * @param key  Authorizing key.
     * @param memo  Transaction annotation.
     * @param currencies  Currencies in which the new accounts will be
     * denominated.
     * @param owner  Ref of the account owner.
     * @param resultHandler  Callback to be invoked on the result.
     */
    fun makeAccounts(key: String?, memo: String?, currencies: Array<String?>?,
                     owner: String?, resultHandler: AccountsResultHandler?) {
        val req = BankRequest("makeaccounts", key, memo)
        req.msg.addParameter("currs", currencies)
        req.msg.addParameter("owner", owner)
        req.send(resultHandler)
    }

    /**
     * Create a new currency.
     *
     * @param key  Authorizing key.
     * @param memo  Transaction annotation.
     * @param currency  Name for the new currency.
     * @param resultHandler  Callback to be invoked on the result.
     */
    fun makeCurrency(key: String?, memo: String?, currency: String?,
                     resultHandler: CurrencyResultHandler?) {
        val req = BankRequest("makecurrency", key, memo)
        req.msg.addParameter("currency", currency)
        req.send(resultHandler)
    }

    /**
     * Create a new authorization key.
     *
     * @param key  Authorizing key.
     * @param memo  Transaction annotation.
     * @param type  Type of authorization new key should grant.
     * @param currency  Optioal name of currency scoping new key's authority,
     * or null.
     * @param expires  Expiration time for the new key.
     * @param resultHandler  Callback to be invoked on the result.
     */
    fun makeKey(key: String?, memo: String?, type: String?, currency: String?,
                expires: String?, resultHandler: KeyResultHandler?) {
        val req = BankRequest("makekey", key, memo)
        req.msg.addParameter("type", type)
        req.msg.addParameterOpt("currency", currency)
        req.msg.addParameterOpt("expires", expires)
        req.send(resultHandler)
    }

    /**
     * Create money into an accont.
     *
     * @param key  Authorizing key.
     * @param memo  Transaction annotation.
     * @param dst  Ref of account to receive the funds.
     * @param amount  Quantity of money to create.
     * @param resultHandler  Callback to be invoked on the result.
     */
    fun mint(key: String?, memo: String?, dst: String?, amount: Int,
             resultHandler: BalanceResultHandler?) {
        val req = BankRequest("mint", key, memo)
        req.msg.addParameter("dst", dst)
        req.msg.addParameter("amount", amount)
        req.send(resultHandler)
    }

    /**
     * Get information about an account.
     *
     * @param key  Authorizing key.
     * @param memo  Transaction annotation.
     * @param accounts  Refs of the accounts being queried.
     * @param encs  Flag that is true if the results should also include
     * information about extant encumbrances on the account.
     * @param resultHandler  Callback to be invoked on the result.
     */
    fun queryAccounts(key: String?, memo: String?, accounts: Array<String?>?,
                      encs: Boolean, resultHandler: QueryAccountsResultHandler?) {
        val req = BankRequest("queryaccounts", key, memo)
        if (encs) {
            req.msg.addParameter("encs", true)
        }
        req.msg.addParameter("accounts", accounts)
        req.send(resultHandler)
    }

    /**
     * Get information about extant currencies.
     *
     * @param key  Authorizing key.
     * @param memo  Transaction annotation.
     * @param resultHandler  Callback to be invoked on the result.
     */
    fun queryCurrencies(key: String?, memo: String?,
                        resultHandler: QueryCurrenciesResultHandler?) {
        val req = BankRequest("querycurrencies", key, memo)
        req.send(resultHandler)
    }

    /**
     * Get information about an encumbrance.
     *
     * @param key  Authorizing key.
     * @param memo  Transaction annotation.
     * @param enc  Ref of the enumbrance being queried.
     * @param resultHandler  Callback to be invoked on the result.
     */
    fun queryEncumbrance(key: String?, memo: String?, enc: String?,
                         resultHandler: QueryEncumbranceResultHandler?) {
        val req = BankRequest("unmintenc", key, memo)
        req.msg.addParameter("enc", enc)
        req.send(resultHandler)
    }

    /**
     * Release a previous encumbrance on an account.
     *
     * @param key  Authorizing key.
     * @param memo  Transaction annotation.
     * @param enc  Ref of the encumbrance to be released
     * @param resultHandler  Callback to be invoked on the result.
     */
    fun releaseEncumbrance(key: String?, memo: String?, enc: String?,
                           resultHandler: ReleaseResultHandler?) {
        val req = BankRequest("releaseenc", key, memo)
        req.msg.addParameter("enc", enc)
        req.send(resultHandler)
    }

    /**
     * Transfer money from one account to another.
     *
     * @param key  Authorizing key.
     * @param memo  Transaction annotation.
     * @param src  Ref of source account.
     * @param dst  Ref of destination account.
     * @param amount  Quantity of funds to transfer.
     * @param resultHandler  Callback to be invoked on the result.
     */
    fun transfer(key: String?, memo: String?, src: String?, dst: String?,
                 amount: Int, resultHandler: TransferResultHandler?) {
        val req = BankRequest("xfer", key, memo)
        req.msg.addParameter("src", src)
        req.msg.addParameter("dst", dst)
        req.msg.addParameter("amount", amount)
        req.send(resultHandler)
    }

    /**
     * Transfer encumbered funds into another account.
     *
     * @param key  Authorizing key.
     * @param memo  Transaction annotation.
     * @param enc  Encumbrance that is the source of funds.
     * @param dst  Ref of destination account.
     * @param resultHandler  Callback to be invoked on the result.
     */
    fun transferEncumbrance(key: String?, memo: String?, enc: String?,
                            dst: String?, resultHandler: TransferResultHandler?) {
        val req = BankRequest("xferenc", key, memo)
        req.msg.addParameter("enc", enc)
        req.msg.addParameter("dst", dst)
        req.send(resultHandler)
    }

    /**
     * Unfreeze an account, rendering it once again able to participate in
     * transactions.
     *
     * @param key  Authorizing key.
     * @param memo  Transaction annotation.
     * @param account  Ref of account to be frozen.
     * @param resultHandler  Callback to be invoked on the result.
     */
    fun unfreezeAccount(key: String?, memo: String?, account: String?,
                        resultHandler: AccountResultHandler?) {
        val req = BankRequest("unfreezeaccount", key, memo)
        req.msg.addParameter("account", account)
        req.send(resultHandler)
    }

    /**
     * Destroy money from an account.
     *
     * @param key  Authorizing key.
     * @param memo  Transaction annotation
     * @param src  Ref of account to lose the funds.
     * @param amount  Quantity of money to destroy.
     * @param resultHandler  Callback to be invoked on the result.
     */
    fun unmint(key: String?, memo: String?, src: String?, amount: Int,
               resultHandler: BalanceResultHandler?) {
        val req = BankRequest("unmint", key, memo)
        req.msg.addParameter("src", src)
        req.msg.addParameter("amount", amount)
        req.send(resultHandler)
    }

    /**
     * Destroy encumbered funds.
     *
     * @param key  Authorizing key.
     * @param memo  Transaction annotation
     * @param enc  Ref of encumbrance that reserves the funds to be destroyed.
     * @param resultHandler  Callback to be invoked on the result.
     */
    fun unmintEncumbrance(key: String?, memo: String?, enc: String?,
                          resultHandler: BalanceResultHandler?) {
        val req = BankRequest("unmintenc", key, memo)
        req.msg.addParameter("enc", enc)
        req.send(resultHandler)
    }

    /**
     * JSON message handler for the response to a cancel key request.
     */
    @JSONMethod("xid", "fail", "desc", "cancel")
    fun cancelkey(from: ServiceActor, xid: String, fail: OptString, desc: OptString, optCancel: OptString) {
        val handler = handlerForReply("cancelkey", xid, fail, desc) as KeyResultHandler?
        if (handler != null) {
            val cancel = optCancel.value<String?>(null)
            if (cancel == null) {
                innerFail(handler, "cancelkey", "badreply", "required reply parameter cancel missing")
            } else {
                handler.result(cancel)
            }
        }
    }

    /**
     * JSON message handler for the response to a delete account request.
     */
    @JSONMethod("xid", "fail", "desc", "account")
    fun deleteaccount(from: ServiceActor, xid: String, fail: OptString, desc: OptString, optAccount: OptString) {
        val handler = handlerForReply("deleteaccount", xid, fail, desc) as AccountResultHandler?
        if (handler != null) {
            val account = optAccount.value<String?>(null)
            if (account == null) {
                innerFail(handler, "deleteaccount", "badreply",
                        "required reply parameter account missing")
            } else {
                handler.result(account)
            }
        }
    }

    /**
     * JSON message handler for the response to a duplicate key request.
     */
    @JSONMethod("xid", "fail", "desc", "newkey")
    fun dupkey(from: ServiceActor, xid: String, fail: OptString, desc: OptString, optNewkey: OptString) {
        val handler = handlerForReply("dupkey", xid, fail, desc) as KeyResultHandler?
        if (handler != null) {
            val newkey = optNewkey.value<String?>(null)
            if (newkey == null) {
                innerFail(handler, "dupkey", "badreply",
                        "required reply parameter newkey missing")
            } else {
                handler.result(newkey)
            }
        }
    }

    /**
     * JSON message handler for the response to an encumber request.
     */
    @JSONMethod("xid", "fail", "desc", "enc", "srcbal")
    fun encumber(from: ServiceActor, xid: String, fail: OptString, desc: OptString, optEnc: OptString, optSrcbal: OptInteger) {
        val handler = handlerForReply("encumber", xid, fail, desc) as EncumberResultHandler?
        if (handler != null) {
            val enc = optEnc.value<String?>(null)
            val srcbal = optSrcbal.value(-1)
            if (enc == null) {
                innerFail(handler, "encumber", "badreply",
                        "required reply parameter enc missing")
            } else if (srcbal < 0) {
                innerFail(handler, "encumber", "badreply",
                        "required reply parameter srcbal missing")
            } else {
                handler.result(enc, srcbal)
            }
        }
    }

    /**
     * JSON message handler for the response to a freeze account request.
     */
    @JSONMethod("xid", "fail", "desc", "account")
    fun freezeaccount(from: ServiceActor, xid: String, fail: OptString, desc: OptString, optAccount: OptString) {
        val handler = handlerForReply("freezeaccount", xid, fail, desc) as AccountResultHandler?
        if (handler != null) {
            val account = optAccount.value<String?>(null)
            if (account == null) {
                innerFail(handler, "freezeaccount", "badreply",
                        "required reply parameter account missing")
            } else {
                handler.result(account)
            }
        }
    }

    /**
     * JSON message handler for the response to an issue root key request.
     */
    @JSONMethod("xid", "fail", "desc", "rootkey")
    fun issuerootkey(from: ServiceActor, xid: String, fail: OptString, desc: OptString, optRootkey: OptString) {
        val handler = handlerForReply("issuerootkey", xid, fail, desc) as KeyResultHandler?
        if (handler != null) {
            val rootkey = optRootkey.value<String?>(null)
            if (rootkey == null) {
                innerFail(handler, "issuerootkey", "badreply",
                        "required reply parameter rootkey missing")
            } else {
                handler.result(rootkey)
            }
        }
    }

    /**
     * JSON message handler for the response to a make account request.
     */
    @JSONMethod("xid", "fail", "desc", "?accounts")
    fun makeaccounts(from: ServiceActor, xid: String, fail: OptString, desc: OptString, accounts: Array<String?>?) {
        val handler = handlerForReply("makeaccounts", xid, fail, desc) as AccountsResultHandler?
        if (handler != null) {
            if (accounts == null) {
                innerFail(handler, "makeaccounts", "badreply",
                        "required reply parameter accounts missing")
            } else {
                handler.result(accounts)
            }
        }
    }

    /**
     * JSON message handler for the response to a make currency request.
     */
    @JSONMethod("xid", "fail", "desc", "currency")
    fun makecurrency(from: ServiceActor, xid: String, fail: OptString, desc: OptString, optCurrency: OptString) {
        val handler = handlerForReply("makecurrency", xid, fail, desc) as CurrencyResultHandler?
        if (handler != null) {
            val currency = optCurrency.value<String?>(null)
            if (currency == null) {
                innerFail(handler, "makecurrency", "badreply",
                        "required reply parameter currency missing")
            } else {
                handler.result(currency)
            }
        }
    }

    /**
     * JSON message handler for the response to a make key request.
     */
    @JSONMethod("xid", "fail", "desc", "newkey")
    fun makekey(from: ServiceActor, xid: String, fail: OptString, desc: OptString, optNewkey: OptString) {
        val handler = handlerForReply("makekey", xid, fail, desc) as KeyResultHandler?
        if (handler != null) {
            val newkey = optNewkey.value<String?>(null)
            if (newkey == null) {
                innerFail(handler, "makekey", "badreply",
                        "required reply parameter newkey missing")
            } else {
                handler.result(newkey)
            }
        }
    }

    /**
     * JSON message handler for the response to a mint request.
     */
    @JSONMethod("xid", "fail", "desc", "dst", "dstbal")
    fun mint(from: ServiceActor, xid: String, fail: OptString, desc: OptString, optDst: OptString, optDstbal: OptInteger) {
        val handler = handlerForReply("mint", xid, fail, desc) as BalanceResultHandler?
        if (handler != null) {
            val dst = optDst.value<String?>(null)
            val dstbal = optDstbal.value(-1)
            if (dst == null) {
                innerFail(handler, "mint", "badreply",
                        "required reply parameter dst missing")
            } else if (dstbal < 0) {
                innerFail(handler, "mint", "badreply",
                        "required reply parameter dstbal missing")
            } else {
                handler.result(dst, dstbal)
            }
        }
    }

    /**
     * JSON message handler for the response to a query account request.
     */
    @JSONMethod("xid", "fail", "desc", "?accounts")
    fun queryaccounts(from: ServiceActor, xid: String, fail: OptString, desc: OptString, accounts: Array<AccountDesc>?) {
        val handler = handlerForReply("queryaccounts", xid, fail, desc) as QueryAccountsResultHandler?
        if (handler != null) {
            if (accounts == null) {
                innerFail(handler, "queryaccounts", "badreply",
                        "required reply parameter accounts missing")
            } else {
                handler.result(accounts)
            }
        }
    }

    /**
     * JSON message handler for the response to a query currencies request.
     */
    @JSONMethod("xid", "fail", "desc", "?currencies")
    fun querycurrencies(from: ServiceActor, xid: String, fail: OptString, desc: OptString, currencies: Array<Currency>?) {
        val handler = handlerForReply("querycurrencies", xid, fail, desc) as QueryCurrenciesResultHandler?
        handler?.result(currencies)
    }

    /**
     * JSON message handler for the response to a query encumbrance request.
     */
    @JSONMethod("xid", "fail", "desc", "enc", "curr", "account", "amount", "expires", "memo")
    fun queryenc(from: ServiceActor, xid: String, fail: OptString,
                 desc: OptString, optEnc: OptString, optCurr: OptString,
                 optAccount: OptString, optAmount: OptInteger,
                 optExpires: OptString, optMemo: OptString) {
        val handler = handlerForReply("queryenc", xid, fail, desc) as QueryEncumbranceResultHandler?
        if (handler != null) {
            val enc = optEnc.value<String?>(null)
            val curr = optCurr.value<String?>(null)
            val account = optAccount.value<String?>(null)
            val amount = optAmount.value(-1)
            val expires = optExpires.value<String?>(null)
            val memo = optMemo.value<String?>(null)
            if (enc == null) {
                innerFail(handler, "queryenc", "badreply",
                        "required reply parameter enc missing")
            } else if (curr == null) {
                innerFail(handler, "queryenc", "badreply",
                        "required reply parameter curr missing")
            } else if (account == null) {
                innerFail(handler, "queryenc", "badreply",
                        "required reply parameter account missing")
            } else if (expires == null) {
                innerFail(handler, "queryenc", "badreply",
                        "required reply parameter expires missing")
            } else if (amount < 0) {
                innerFail(handler, "queryenc", "badreply",
                        "required reply parameter amount missing")
            } else {
                handler.result(enc, curr, account, amount, expires, memo)
            }
        }
    }

    /**
     * JSON message handler for the response to a release encumbrance request.
     */
    @JSONMethod("xid", "fail", "desc", "src", "srcbal", "active")
    fun releaseenc(from: ServiceActor, xid: String, fail: OptString,
                   desc: OptString, optSrc: OptString,
                   optSrcbal: OptInteger, optActive: OptBoolean) {
        val handler = handlerForReply("releaseenc", xid, fail, desc) as ReleaseResultHandler?
        if (handler != null) {
            val src = optSrc.value<String?>(null)
            val srcbal = optSrcbal.value(-1)
            val active = optActive.value(true)
            if (src == null) {
                innerFail(handler, "releaseenc", "badreply",
                        "required reply parameter src missing")
            } else if (srcbal < 0) {
                innerFail(handler, "releaseenc", "badreply",
                        "required reply parameter srcbal missing")
            } else {
                handler.result(src, srcbal, active)
            }
        }
    }

    /**
     * JSON message handler for the response to an unfreeze account request.
     */
    @JSONMethod("xid", "fail", "desc", "account")
    fun unfreezeaccount(from: ServiceActor, xid: String, fail: OptString,
                        desc: OptString, optAccount: OptString) {
        val handler = handlerForReply("unfreezeaccount", xid, fail, desc) as AccountResultHandler?
        if (handler != null) {
            val account = optAccount.value<String?>(null)
            if (account == null) {
                innerFail(handler, "unfreezeaccount", "badreply",
                        "required reply parameter account missing")
            } else {
                handler.result(account)
            }
        }
    }

    /**
     * JSON message handler for the response to an unmint request.
     */
    @JSONMethod("xid", "fail", "desc", "src", "srcbal")
    fun unmint(from: ServiceActor, xid: String, fail: OptString,
               desc: OptString, optSrc: OptString, optSrcbal: OptInteger) {
        val handler = handlerForReply("unmint", xid, fail, desc) as BalanceResultHandler?
        if (handler != null) {
            val src = optSrc.value<String?>(null)
            val srcbal = optSrcbal.value(-1)
            if (src == null) {
                innerFail(handler, "unmint", "badreply",
                        "required reply parameter src missing")
            } else if (srcbal < 0) {
                innerFail(handler, "unmint", "badreply",
                        "required reply parameter srcbal missing")
            } else {
                handler.result(src, srcbal)
            }
        }
    }

    /**
     * JSON message handler for the response to an unmint encumbrance request.
     */
    @JSONMethod("xid", "fail", "desc", "src", "srcbal")
    fun unmintenc(from: ServiceActor, xid: String, fail: OptString,
                  desc: OptString, optSrc: OptString,
                  optSrcbal: OptInteger) {
        val handler = handlerForReply("unmintenc", xid, fail, desc) as BalanceResultHandler?
        if (handler != null) {
            val src = optSrc.value<String?>(null)
            val srcbal = optSrcbal.value(-1)
            if (src == null) {
                innerFail(handler, "unmintenc", "badreply",
                        "required reply parameter src missing")
            } else if (srcbal < 0) {
                innerFail(handler, "unmintenc", "badreply",
                        "required reply parameter srcbal missing")
            } else {
                handler.result(src, srcbal)
            }
        }
    }

    /**
     * JSON message handler for the response to a transfer request.
     */
    @JSONMethod("xid", "fail", "desc", "src", "srcbal", "dst", "dstbal")
    fun xfer(from: ServiceActor, xid: String, fail: OptString,
             desc: OptString, optSrc: OptString, optSrcbal: OptInteger,
             optDst: OptString, optDstbal: OptInteger) {
        val handler = handlerForReply("xfer", xid, fail, desc) as TransferResultHandler?
        if (handler != null) {
            val src = optSrc.value<String?>(null)
            val srcbal = optSrcbal.value(-1)
            val dst = optDst.value<String?>(null)
            val dstbal = optDstbal.value(-1)
            if (src == null) {
                innerFail(handler, "xfer", "badreply",
                        "required reply parameter src missing")
            } else if (srcbal < 0) {
                innerFail(handler, "xfer", "badreply",
                        "required reply parameter srcbal missing")
            } else if (dst == null) {
                innerFail(handler, "xfer", "badreply",
                        "required reply parameter dst missing")
            } else if (dstbal < 0) {
                innerFail(handler, "xfer", "badreply",
                        "required reply parameter dstbal missing")
            } else {
                handler.result(src, srcbal, dst, dstbal)
            }
        }
    }

    /**
     * JSON message handler for the response to a transfer encumbrance request.
     */
    @JSONMethod("xid", "fail", "desc", "src", "srcbal", "dst", "dstbal")
    fun xferenc(from: ServiceActor, xid: String, fail: OptString,
                desc: OptString, optSrc: OptString, optSrcbal: OptInteger,
                optDst: OptString, optDstbal: OptInteger) {
        val handler = handlerForReply("xferenc", xid, fail, desc) as TransferResultHandler?
        if (handler != null) {
            val src = optSrc.value<String?>(null)
            val srcbal = optSrcbal.value(-1)
            val dst = optDst.value<String?>(null)
            val dstbal = optDstbal.value(-1)
            if (src == null) {
                innerFail(handler, "xferenc", "badreply",
                        "required reply parameter src missing")
            } else if (srcbal < 0) {
                innerFail(handler, "xferenc", "badreply",
                        "required reply parameter srcbal missing")
            } else if (dst == null) {
                innerFail(handler, "xferenc", "badreply",
                        "required reply parameter dst missing")
            } else if (dstbal < 0) {
                innerFail(handler, "xferenc", "badreply",
                        "required reply parameter dstbal missing")
            } else {
                handler.result(src, srcbal, dst, dstbal)
            }
        }
    }
}
