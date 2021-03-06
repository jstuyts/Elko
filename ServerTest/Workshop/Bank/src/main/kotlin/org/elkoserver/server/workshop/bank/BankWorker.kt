package org.elkoserver.server.workshop.bank

import org.elkoserver.foundation.json.ClassspecificGorgelUsingObject
import org.elkoserver.foundation.json.ClockUsingObject
import org.elkoserver.foundation.json.JsonMethod
import org.elkoserver.foundation.json.OptBoolean
import org.elkoserver.foundation.json.OptString
import org.elkoserver.json.JsonLiteral
import org.elkoserver.json.JsonLiteralArray
import org.elkoserver.json.JsonLiteralFactory
import org.elkoserver.server.workshop.WorkerObject
import org.elkoserver.server.workshop.WorkshopActor
import org.elkoserver.util.trace.slf4j.Gorgel
import java.text.ParseException
import java.time.Clock

/**
 * Workshop worker object for the bank service.
 *
 * @param serviceName  The name by which this worker object will be
 * addressed.  If omitted, it defaults to "bank".
 * @param myBankRef  Reference string for the persistent bank object that
 * this worker object provides the interface to.
 */
class BankWorker
@JsonMethod("service", "bank") constructor(serviceName: OptString, private val myBankRef: String)
    : WorkerObject(serviceName.value("bank")), ClockUsingObject, ClassspecificGorgelUsingObject {
    /** The bank this worker is the interface to.  */
    private var myBank: Bank? = null

    private lateinit var clock: Clock

    private lateinit var gorgel: Gorgel

    /**
     * Common state for a request to the banking service.
     *
     * @param from  Actor who sent the request
     * @param verb  Operation verb
     * @param key  Key authorizing operation
     * @param xid  Client-side transaction ID, or null
     * @param rep  Reference to reply object, or null
     * @param memo  Arbitrary annotation
     */
    private inner class RequestEnv(
            val from: WorkshopActor,
            val verb: String,
            val key: Key?,
            val xid: String?, val rep: String?, val memo: String?, private val clock: Clock) {

        /**
         * Test if a write status represents a failure.  Send a failure reply
         * if it is.
         *
         * @param failure  Failure string from the account write.  A value of
         * null indicates that the write was successful.
         * @param tag  Tag string indicating the role of the account, for
         * logging and error message purposes.  Typically this will be
         * "dst" or "src".
         *
         * @return true if the account write failed, false if not.
         */
        fun accountWriteFailure(failure: String?, tag: String) =
                if (failure != null) {
                    fail("${tag}unwritable", "$tag account write failed: $failure")
                    true
                } else {
                    false
                }

        /**
         * Test whether a given monetary quantity is valid.  At the moment,
         * this checks to be sure that the amount is not negative.  Sends a
         * failure reply if the amount given is invalid.
         *
         * @param amount  The monetary amount of interest.
         *
         * @return true if the amount was invalid, false if not.
         */
        fun amountValidationFailure(amount: Int) =
                if (amount <= 0) {
                    fail("badamount", "invalid 'amount' parameter")
                    true
                } else {
                    false
                }

        /**
         * Begin constructing a message replying to this request.  It is
         * addressed to the designatedd reply recipient, has the same verb as
         * the request, and has the 'xid' parameter added if appropriate.  It
         * is up to the caller to fill in the operation-specific reply
         * parameters, finish the reply literal, and send it off.
         *
         * @return an open JSON literal as described.
         */
        fun beginReply() =
                JsonLiteralFactory.targetVerb(rep!!, verb).apply {
                    if (xid != null) {
                        addParameter("xid", xid)
                    }
                }

        /**
         * Test whether the request key authorizes operations on a given
         * currency.  Sends a failure reply if it does not.
         *
         * @param currency  The currency the requestor wishes to operate on.
         *
         * @return true if authorization failed, false if not.
         */
        fun currencyAuthorityFailure(currency: String?) =
                if (!key!!.allowsCurrency(currency)) {
                    fail("autherr", "bad authorization key")
                    true
                } else {
                    false
                }

        /**
         * Test whether the request key authorizes operations on a collection
         * of currencies.  Sends a failure reply if it does not.
         *
         * @param currencies  The currencies the requestor wishes to operate
         * on.
         *
         * @return true if authorization failed, false if not.
         */
        fun currencyAuthorityFailure(currencies: Array<String>?): Boolean {
            if (currencies == null) {
                return false
            }
            return currencies.any(this@RequestEnv::currencyAuthorityFailure)
        }

        /**
         * Test whether a given array of currencies is valid or not.  Sends a
         * failure reply if the array or any of the currencies in it are
         * invalid.
         *
         * @param currencies  The currency names of the currencies of interest.
         *
         * @return true if the currencies array was invalid, false if not.
         */
        fun currencyValidationFailure(currencies: Array<String>?): Boolean {
            if (currencies == null || currencies.isEmpty()) {
                fail("badcurr", "invalid currency list")
                return true
            }
            for (currency in currencies) {
                if (myBank!!.getCurrency(currency) == null) {
                    fail("badcurr", "invalid currency $currency")
                    return true
                }
            }
            return false
        }

        /**
         * Reply to this request with a failure message, if the request
         * indicated that a reply was desired.
         *
         * @param fail  Failure code string
         * @param desc  Error message for logging and debugging.
         */
        fun fail(fail: String?, desc: String?) {
            if (rep != null) {
                val reply = beginReply().apply {
                    addParameter("fail", fail)
                    addParameter("desc", desc)
                    finish()
                }
                from.send(reply)
            }
        }

        /**
         * Test whether an account is frozen.  Sends a failure reply if it is.
         *
         * @param account  The account of interest.
         * @param tag  Tag string indicating the role of the account, for
         * logging and error message purposes.  Typically this will be
         * "dst" or "src".
         *
         * @return true if the account is frozen, false if not.
         */
        fun frozenAccountFailure(account: Account?, tag: String): Boolean {
            return if (account!!.isFrozen) {
                fail("${tag}frozen", "$tag account is frozen")
                true
            } else {
                false
            }
        }

        /**
         * Test whether an account object is valid (in this case, not null).
         * Sends a failure reply if the account was not valid.
         *
         * @param account  The account of interest.
         * @param tag  Tag string indicating the role of the account, for
         * logging and error message purposes.  Typically this will be
         * "dst" or "src".
         *
         * @return true if the account was invalid, false if not.
         */
        fun invalidAccountFailure(account: Account?, tag: String): Boolean {
            return if (account == null) {
                fail("bad$tag", "invalid $tag account id")
                true
            } else {
                false
            }
        }

        /**
         * Test whether an encumbrance object is valid (in this case, not
         * null).  Sends a failure reply if the encumbrance was not valid.
         *
         * @param enc  The encumbrance of interest.
         *
         * @return true if the encumbrance was invalid, false if not.
         */
        fun invalidEncumbranceFailure(enc: Encumbrance?): Boolean {
            return if (enc == null) {
                fail("badenc", "invalid encumbrance id $enc")
                true
            } else {
                false
            }
        }

        /**
         * Parse and return an expiration date from its string representation.
         * Sends a failure reply if the expiration date string given was
         * invalid.
         *
         * @param expiresStr  A string allegedly containing an expiration date.
         * @param limitToKey  Control flag: if true, an expiration date that is
         * later than the authorizing key will be considered invalid.
         *
         * @return the expiration date described by the given string, if valid,
         * or null if not.
         */
        fun getValidExpiration(expiresStr: String?, limitToKey: Boolean): ExpirationDate? {
            val expires: ExpirationDate = if (expiresStr == null && limitToKey) {
                key!!.expires
            } else {
                try {
                    ExpirationDate(expiresStr!!, clock)
                } catch (e: ParseException) {
                    fail("badexpiry", "invalid 'expires' parameter: $e")
                    return null
                }
            }
            return if (limitToKey && key!!.expires < expires) {
                fail("badexpiry", "expiration time exceeds authority")
                null
            } else if (expires.isExpired) {
                fail("badexpiry", "expiration time in the past")
                null
            } else {
                expires
            }
        }

        /**
         * Test whether the request key authorizes the operation requested.
         * Sends a failure reply if it does not.
         *
         * @param operation  Operation family of operation desired.
         *
         * @return true if authorization failed, false if not.
         */
        fun operationAuthorityFailure(operation: String?): Boolean {
            return if (!key!!.allowsOperation(operation!!)) {
                fail("autherr", "bad authorization key")
                true
            } else {
                false
            }
        }

    }

    override fun setClock(clock: Clock) {
        this.clock = clock
    }

    override fun setGorgel(gorgel: Gorgel) {
        this.gorgel = gorgel
    }

    /**
     * Activate the bank service.
     */
    public override fun activate() {
        workshop().getObject(myBankRef, { obj: Any? ->
            if (obj is Bank) {
                myBank = obj
                obj.activate(workshop())
            } else {
                gorgel.error("alleged bank object $myBankRef is not a bank")
            }
        })
    }

    /**
     * Generate a new RequestEnv object by extracting and checking the
     * various common parameters from the request message.  Sends a failure
     * reply if the authorization key given was invalid or if any normally-
     * optional-but-in-this-case-required parameters are missing.
     *
     * @param from  Actor who sent the request.
     * @param verb  Operation verb.
     * @param keyRef  Reference string for the authorization key, or null if
     * no key is required.
     * @param optXid  Optional client-side transaction ID.
     * @param optRep  Optional reference for addressing the reply.
     * @param repRequired  Flag that is true if the normal optional reply
     * address is actually required in this case.
     * @param optMemo   Optional annotation for logging the operation
     * @param memoRequired  Flag that is true if the normally optional memo
     * parameter is actually required in this case.
     * @param clock
     * @return a new RequestEnv object constructed by processing the
     * given parameters, or null if these parameters were somehow
     * invalid.
     */
    private fun init(from: WorkshopActor, verb: String, keyRef: String?,
                     optXid: OptString, optRep: OptString, repRequired: Boolean,
                     optMemo: OptString, memoRequired: Boolean, clock: Clock): RequestEnv? {
        from.ensureAuthorizedClient()
        val xid = optXid.valueOrNull()
        val rep = optRep.valueOrNull()
        val memo = optMemo.valueOrNull()
        if (rep == null && repRequired) {
            return null
        }
        val key = myBank?.getKey(keyRef)
        val env = RequestEnv(from, verb, key, xid, rep, memo, clock)
        if (myBank == null) {
            env.fail("unready", "bank object not yet loaded")
            return null
        }
        return if (key == null && keyRef != null) {
            env.fail("autherr", "bad authorization key")
            null
        } else if (memo == null && memoRequired) {
            env.fail("nomemo", "request lacked required 'memo' parameter")
            null
        } else {
            env
        }
    }

    /**
     * Message handler for the 'issuerootkey' request: obtain the bank's root
     * key for the first time.
     *
     * @param from  The sender of the request.
     * @param xid  Optional client-side response tag.
     * @param rep  Optional reference to client object to reply to.
     * @param memo  Transaction annotation, for logging.
     */
    @JsonMethod("xid", "rep", "memo")
    fun issuerootkey(from: WorkshopActor, xid: OptString, rep: OptString,
                     memo: OptString) {
        val env = init(from, "issuerootkey", null, xid, rep, true, memo, false, clock) ?: return
        val rootKey = myBank!!.issueRootKey()
        if (rootKey == null) {
            env.fail("onceonly",
                    "the root key for this bank has already been issued")
        } else {
            val reply = env.beginReply().apply {
                addParameter("rootkey", rootKey.ref)
                finish()
            }
            from.send(reply)
        }
    }

    /**
     * Message handler for the 'xfer' request: transfer money from one account
     * to another.
     *
     * @param from  The sender of the request.
     * @param key  Key from authorizing access.
     * @param xid  Optional client-side response tag.
     * @param rep  Optional reference to client object to reply to.
     * @param memo  Transaction annotation, for logging.
     * @param src  Ref of transfer source account.
     * @param dst  Ref of transfer destination account.
     * @param amount  Quantity of money to transfer.
     */
    @JsonMethod("key", "xid", "rep", "memo", "src", "dst", "amount")
    fun xfer(from: WorkshopActor, key: String, xid: OptString,
             rep: OptString, memo: OptString, src: String,
             dst: String, amount: Int) {
        val env = init(from, "xfer", key, xid, rep, false, memo, false, clock) ?: return
        if (env.operationAuthorityFailure("xfer")) {
            return
        }
        myBank!!.withTwoAccounts(src, dst, object : DualAccountUpdater {
            private var mySrcAccount: Account? = null
            private var myDstAccount: Account? = null
            override fun modify(account1: Account?, account2: Account?): Boolean {
                if (env.invalidAccountFailure(account1, "src")) {
                    return false
                }
                mySrcAccount = account1
                if (env.invalidAccountFailure(account2, "dst")) {
                    return false
                }
                myDstAccount = account2
                if (account1!!.currency != account2!!.currency) {
                    env.fail("curmismatch",
                            "source and destination currencies differ")
                    return false
                }
                if (env.currencyAuthorityFailure(account1.currency)) {
                    return false
                }
                if (env.frozenAccountFailure(account1, "src")) {
                    return false
                }
                if (env.frozenAccountFailure(account2, "dst")) {
                    return false
                }
                if (env.amountValidationFailure(amount)) {
                    return false
                }
                if (account1.availBalance < amount) {
                    env.fail("nsf",
                            "insufficient funds in source account")
                    return false
                }
                if (account1.ref != account2.ref) {
                    account1.withdraw(amount)
                    account2.deposit(amount)
                }
                return true
            }

            override fun complete(failure: String?) {
                if (!env.accountWriteFailure(failure, "xfer")) {
                    val reply = env.beginReply().apply {
                        addParameter("src", src)
                        addParameter("srcbal", mySrcAccount!!.availBalance)
                        addParameter("dst", dst)
                        addParameter("dstbal", myDstAccount!!.availBalance)
                        finish()
                    }
                    from.send(reply)
                }
            }
        })
    }

    /**
     * Message handler for the 'mint' request: create money and deposit it in
     * an account.
     *
     * @param from  The sender of the request.
     * @param key  Key from authorizing access.
     * @param xid  Optional client-side response tag.
     * @param rep  Optional reference to client object to reply to.
     * @param memo  Transaction annotation, for logging.
     * @param dst  Ref of destination account for new money.
     * @param amount  Quantity of money to create.
     */
    @JsonMethod("key", "xid", "rep", "memo", "dst", "amount")
    fun mint(from: WorkshopActor, key: String, xid: OptString,
             rep: OptString, memo: OptString, dst: String,
             amount: Int) {
        val env = init(from, "mint", key, xid, rep, false, memo, false, clock) ?: return
        if (env.operationAuthorityFailure("mint")) {
            return
        }
        if (env.amountValidationFailure(amount)) {
            return
        }
        myBank!!.withAccount(dst, object : AccountUpdater {
            private var myDstAccount: Account? = null
            override fun modify(account: Account?): Boolean {
                myDstAccount = account
                if (env.invalidAccountFailure(account, "dst")) {
                    return false
                }
                if (env.currencyAuthorityFailure(account!!.currency)) {
                    return false
                }
                if (env.frozenAccountFailure(account, "dst")) {
                    return false
                }
                account.deposit(amount)
                return true
            }

            override fun complete(failure: String?) {
                if (!env.accountWriteFailure(failure, "dst")) {
                    val reply = env.beginReply().apply {
                        addParameter("dst", dst)
                        addParameter("dstbal", myDstAccount!!.availBalance)
                        finish()
                    }
                    from.send(reply)
                }
            }
        })
    }

    /**
     * Message handler for the 'unmint' request: remove money from an account
     * and then destroy it.
     *
     * @param from  The sender of the request.
     * @param key  Key from authorizing access.
     * @param xid  Optional client-side response tag.
     * @param rep  Optional reference to client object to reply to.
     * @param memo  Transaction annotation, for logging.
     * @param src  Ref of the account from which the money should be taken.
     * @param amount  Quantity of money to destroy.
     */
    @JsonMethod("key", "xid", "rep", "memo", "src", "amount")
    fun unmint(from: WorkshopActor, key: String, xid: OptString,
               rep: OptString, memo: OptString, src: String,
               amount: Int) {
        val env = init(from, "unmint", key, xid, rep, false, memo, false, clock) ?: return
        if (env.operationAuthorityFailure("mint")) {
            return
        }
        myBank!!.withAccount(src, object : AccountUpdater {
            private var mySrcAccount: Account? = null
            override fun modify(account: Account?): Boolean {
                mySrcAccount = account
                if (env.invalidAccountFailure(account, "src")) {
                    return false
                }
                if (env.currencyAuthorityFailure(account!!.currency)) {
                    return false
                }
                if (env.amountValidationFailure(amount)) {
                    return false
                }
                if (account.availBalance < amount) {
                    env.fail("nsf", "insufficient funds in source account")
                    return false
                }
                account.withdraw(amount)
                return true
            }

            override fun complete(failure: String?) {
                if (!env.accountWriteFailure(failure, "src")) {
                    val reply = env.beginReply().apply {
                        addParameter("srcbal", mySrcAccount!!.availBalance)
                        addParameter("src", src)
                        finish()
                    }
                    from.send(reply)
                }
            }
        })
    }

    /**
     * Message handler for the 'encumber' request: reserve money in an account
     * for a future transaction.
     *
     * @param from  The sender of the request.
     * @param key  Key from authorizing access.
     * @param xid  Optional client-side response tag.
     * @param rep  Optional reference to client object to reply to.
     * @param memo  Transaction annotation, for logging.
     * @param src  Ref of account whose funds are to be encumbered.
     * @param amount  Quantity of money to encumber.
     * @param expiresStr  Date after which the encumbrance will be released.
     */
    @JsonMethod("key", "xid", "rep", "memo", "src", "amount", "expires")
    fun encumber(from: WorkshopActor, key: String, xid: OptString,
                 rep: OptString, memo: OptString, src: String,
                 amount: Int, expiresStr: String) {
        val env = init(from, "encumber", key, xid, rep, true, memo, false, clock) ?: return
        val expires = env.getValidExpiration(expiresStr, false) ?: return
        if (env.operationAuthorityFailure("xfer")) {
            return
        }
        myBank!!.withAccount(src, object : AccountUpdater {
            private var mySrcAccount: Account? = null
            private var myEnc: Encumbrance? = null
            override fun modify(account: Account?): Boolean {
                mySrcAccount = account
                if (env.invalidAccountFailure(account, "src")) {
                    return false
                }
                if (env.currencyAuthorityFailure(account!!.currency)) {
                    return false
                }
                if (env.frozenAccountFailure(account, "src")) {
                    return false
                }
                if (env.amountValidationFailure(amount)) {
                    return false
                }
                if (account.availBalance < amount) {
                    env.fail("nsf", "insufficient funds in source account")
                    return false
                }
                myEnc = Encumbrance(myBank!!.generateRef("enc"), account,
                        amount, expires, env.memo)
                account.encumber(myEnc!!)
                return true
            }

            override fun complete(failure: String?) {
                if (!env.accountWriteFailure(failure, "src")) {
                    val reply = env.beginReply().apply {
                        addParameter("enc", myEnc!!.ref)
                        addParameter("srcbal", mySrcAccount!!.availBalance)
                        finish()
                    }
                    from.send(reply)
                }
            }
        })
    }

    /**
     * Message handler for the 'releaseenc' request: release an encumbrance on
     * an account, making the funds once again available to the account owner.
     *
     * @param from  The sender of the request.
     * @param key  Key from authorizing access.
     * @param xid  Optional client-side response tag.
     * @param rep  Optional reference to client object to reply to.
     * @param memo  Transaction annotation, for logging.
     * @param encRef  Ref of the encumbrance to release.
     */
    @JsonMethod("key", "xid", "rep", "memo", "enc")
    fun releaseenc(from: WorkshopActor, key: String, xid: OptString,
                   rep: OptString, memo: OptString, encRef: String) {
        val env = init(from, "releaseenc", key, xid, rep, false, memo, false, clock) ?: return
        if (env.operationAuthorityFailure("xfer")) {
            return
        }
        myBank!!.withEncumberedAccount(encRef, object : AccountUpdater {
            private var myEnc: Encumbrance? = null
            override fun modify(account: Account?): Boolean {
                if (env.invalidAccountFailure(account, "src")) {
                    return false
                }
                myEnc = account!!.getEncumbrance(encRef)
                if (env.invalidEncumbranceFailure(myEnc)) {
                    return false
                }
                if (!myEnc!!.isExpired) {
                    myEnc!!.release()
                }
                return true
            }

            override fun complete(failure: String?) {
                if (!env.accountWriteFailure(failure, "src")) {
                    val reply = env.beginReply().apply {
                        addParameter("src", myEnc!!.account!!.ref)
                        addParameter("srcbal", myEnc!!.account!!.availBalance)
                        addParameter("active", !myEnc!!.isExpired)
                        finish()
                    }
                    from.send(reply)
                }
            }
        })
    }

    /**
     * Message handler for the 'xferenc' request: redeem an encumbrance by
     * transferring the encumbered funds to some other account
     *
     * @param from  The sender of the request.
     * @param key  Key from authorizing access.
     * @param xid  Optional client-side response tag.
     * @param rep  Optional reference to client object to reply to.
     * @param memo  Transaction annotation, for logging.
     * @param dst  Ref of transfer destination account.
     * @param encRef  Ref of the encumbrance that will be the source of funds.
     */
    @JsonMethod("key", "xid", "rep", "memo", "dst", "enc")
    fun xferenc(from: WorkshopActor, key: String, xid: OptString,
                rep: OptString, memo: OptString, dst: String,
                encRef: String) {
        val env = init(from, "xferenc", key, xid, rep, false, memo, false, clock) ?: return
        if (env.operationAuthorityFailure("xfer")) {
            return
        }
        myBank!!.withEncumbranceAndAccount(encRef, dst, object : DualAccountUpdater {
            private var mySrcAccount: Account? = null
            private var myDstAccount: Account? = null
            override fun modify(account1: Account?, account2: Account?): Boolean {
                if (env.invalidAccountFailure(account1, "src")) {
                    return false
                }
                mySrcAccount = account1
                val myEnc = account1!!.getEncumbrance(encRef)
                if (env.invalidEncumbranceFailure(myEnc)) {
                    return false
                }
                if (env.invalidAccountFailure(account2, "dst")) {
                    return false
                }
                myDstAccount = account2
                if (account1.currency != account2!!.currency) {
                    env.fail("curmismatch",
                            "source and destination currencies differ")
                    return false
                }
                if (env.currencyAuthorityFailure(account2.currency)) {
                    return false
                }
                if (env.frozenAccountFailure(account2, "dst")) {
                    return false
                }
                if (account1.ref == account2.ref) {
                    myEnc!!.release()
                } else {
                    val amount = myEnc!!.redeem()
                    account2.deposit(amount)
                }
                return true
            }

            override fun complete(failure: String?) {
                if (!env.accountWriteFailure(failure, "xfer")) {
                    val reply = env.beginReply().apply {
                        addParameter("src", mySrcAccount!!.ref)
                        addParameter("srcbal", mySrcAccount!!.availBalance)
                        addParameter("dst", dst)
                        addParameter("dstbal", myDstAccount!!.availBalance)
                        finish()
                    }
                    from.send(reply)
                }
            }
        })
    }

    /**
     * Message handler for the 'unmint' request: redeem an encumbrance by
     * destroying the encumbered funds
     *
     * @param from  The sender of the request.
     * @param key  Key from authorizing access.
     * @param xid  Optional client-side response tag.
     * @param rep  Optional reference to client object to reply to.
     * @param memo  Transaction annotation, for logging.
     * @param encRef  Ref of the encumbrance that will be the source of funds.
     */
    @JsonMethod("key", "xid", "rep", "memo", "enc")
    fun unmintenc(from: WorkshopActor, key: String, xid: OptString,
                  rep: OptString, memo: OptString, encRef: String) {
        val env = init(from, "unmintenc", key, xid, rep, false, memo, false, clock) ?: return
        if (env.operationAuthorityFailure("mint")) {
            return
        }
        myBank!!.withEncumberedAccount(encRef, object : AccountUpdater {
            private var myEnc: Encumbrance? = null
            override fun modify(account: Account?): Boolean {
                if (env.invalidAccountFailure(account, "src")) {
                    return false
                }
                myEnc = account!!.getEncumbrance(encRef)
                if (env.invalidEncumbranceFailure(myEnc)) {
                    return false
                }
                if (env.currencyAuthorityFailure(account.currency)) {
                    return false
                }
                myEnc!!.redeem()
                return true
            }

            override fun complete(failure: String?) {
                if (!env.accountWriteFailure(failure, "src")) {
                    val reply = env.beginReply().apply {
                        addParameter("src", myEnc!!.account!!.ref)
                        addParameter("srcbal", myEnc!!.account!!.availBalance)
                        finish()
                    }
                    from.send(reply)
                }
            }
        })
    }

    /**
     * Message handler for the 'queryenc' request: obtain information about an
     * encumbrance.
     *
     * @param from  The sender of the request.
     * @param key  Key from authorizing access.
     * @param xid  Optional client-side response tag.
     * @param rep  Optional reference to client object to reply to.
     * @param memo  Transaction annotation, for logging.
     * @param encRef  Ref of the encumbrance that is of interest.
     */
    @JsonMethod("key", "xid", "rep", "memo", "enc")
    fun queryenc(from: WorkshopActor, key: String, xid: OptString,
                 rep: OptString, memo: OptString, encRef: String) {
        val env = init(from, "queryenc", key, xid, rep, true, memo, false, clock) ?: return
        if (env.operationAuthorityFailure("xfer")) {
            return
        }
        myBank!!.withEncumberedAccount(encRef, object : AccountUpdater {
            override fun modify(account: Account?): Boolean {
                if (env.invalidAccountFailure(account, "src")) {
                    return false
                }
                val enc = account!!.getEncumbrance(encRef)
                if (env.invalidEncumbranceFailure(enc)) {
                    return false
                }
                if (env.currencyAuthorityFailure(account.currency)) {
                    return false
                }
                val reply = env.beginReply().apply {
                    addParameter("enc", enc!!.ref)
                    addParameter("curr", account.currency)
                    addParameter("account", account.ref)
                    addParameter("amount", enc.amount)
                    addParameter("expires", enc.expires.toString())
                    addParameterOpt("memo", enc.memo)
                    finish()
                }
                from.send(reply)
                /* Don't write, hence even success is a form of failure. */
                return false
            }

            override fun complete(failure: String?) {
                /* never called */
            }
        })
    }

    /**
     * Message handler for the 'makeaccounts' request: create new accounts.
     * This will create one new account for each currency specified.
     *
     * @param from  The sender of the request.
     * @param key  Key from authorizing access.
     * @param xid  Optional client-side response tag.
     * @param rep  Optional reference to client object to reply to.
     * @param memo  Transaction annotation, for logging.
     * @param currs  Currencies in which the new accounts will be denominated.
     * @param owner  Ref of the user who is to be the owner of the new account.
     */
    @JsonMethod("key", "xid", "rep", "memo", "currs", "owner")
    fun makeaccounts(from: WorkshopActor, key: String, xid: OptString,
                     rep: OptString, memo: OptString, currs: Array<String>,
                     owner: String) {
        val env = init(from, "makeaccounts", key, xid, rep, true, memo, true, clock) ?: return
        if (env.operationAuthorityFailure("acct")) {
            return
        }
        if (env.currencyValidationFailure(currs)) {
            return
        }
        if (env.currencyAuthorityFailure(currs)) {
            return
        }
        val replyAccounts = JsonLiteralArray()
        currs
                .map { myBank!!.makeAccount(it, owner, env.memo) }
                .forEach { replyAccounts.addElement(it.ref) }
        replyAccounts.finish()
        val reply = env.beginReply().apply {
            addParameter("accounts", replyAccounts)
            finish()
        }
        from.send(reply)
    }

    /**
     * Message handler for the 'deleteaccount' request: destroy an account.
     *
     * @param from  The sender of the request.
     * @param key  Key from authorizing access.
     * @param xid  Optional client-side response tag.
     * @param rep  Optional reference to client object to reply to.
     * @param memo  Transaction annotation, for logging.
     * @param account  Ref of the account that is to be deleted.
     */
    @JsonMethod("key", "xid", "rep", "memo", "account")
    fun deleteaccount(from: WorkshopActor, key: String,
                      xid: OptString, rep: OptString, memo: OptString,
                      account: String) {
        val env = init(from, "deleteaccount", key, xid, rep, false, memo, false, clock) ?: return
        if (env.operationAuthorityFailure("acct")) {
            return
        }
        myBank!!.withAccount(account, object : AccountUpdater {
            override fun modify(account: Account?): Boolean {
                if (env.invalidAccountFailure(account, "src")) {
                    return false
                }
                if (env.currencyAuthorityFailure(account!!.currency)) {
                    return false
                }
                if (0 < account.totalBalance) {
                    env.fail("notempty", "account still contains funds")
                    return false
                }
                account.delete()
                return true
            }

            override fun complete(failure: String?) {
                if (!env.accountWriteFailure(failure, "src")) {
                    val reply = env.beginReply().apply {
                        addParameter("account", account)
                        finish()
                    }
                    from.send(reply)
                }
            }
        })
    }

    /**
     * Message handler for the 'queryaccounts' request: obtain information
     * about one or more accounts.
     *
     * @param from  The sender of the request.
     * @param key  Key from authorizing access.
     * @param xid  Optional client-side response tag.
     * @param rep  Optional reference to client object to reply to.
     * @param memo  Transaction annotation, for logging.
     * @param accounts  Refs of the accounts of interest
     * @param encs  Flag that is true if reply should include encumbrance info.
     */
    @JsonMethod("key", "xid", "rep", "memo", "accounts", "encs")
    fun queryaccounts(from: WorkshopActor, key: String,
                      xid: OptString, rep: OptString, memo: OptString,
                      accounts: Array<String>, encs: OptBoolean
    ) {
        val env = init(from, "queryaccounts", key, xid, rep, true, memo, false, clock) ?: return
        if (env.operationAuthorityFailure("acct")) {
            return
        }
        if (accounts.isEmpty()) {
            env.fail("noaccounts", "account list provided was empty")
            return
        }
        val lookupHandler: AccountUpdater = object : AccountUpdater {
            private var myResultCount = 0
            private var amFailed = false
            private val myAccountDescs = arrayOfNulls<JsonLiteral>(accounts.size)
            override fun modify(account: Account?): Boolean {
                if (amFailed) {
                    return false
                }
                if (env.invalidAccountFailure(account, "src")) {
                    amFailed = true
                    return false
                }
                if (env.currencyAuthorityFailure(account!!.currency)) {
                    amFailed = true
                    return false
                }
                val accountDesc = JsonLiteral().apply {
                    addParameter("account", account.ref)
                    addParameter("curr", account.currency)
                    addParameter("total", account.totalBalance)
                    addParameter("avail", account.availBalance)
                    addParameter("frozen", account.isFrozen)
                    addParameter("memo", account.memo)
                    addParameter("owner", account.owner)
                    if (encs.value(false)) {
                        val encsList = JsonLiteralArray()
                        account.encumbrances.forEach { enc ->
                            val encDesc = JsonLiteral().apply {
                                addParameter("enc", enc.ref)
                                addParameter("amount", enc.amount)
                                addParameter("expires", enc.expires.toString())
                                addParameterOpt("memo", enc.memo)
                                finish()
                            }
                            encsList.addElement(encDesc)
                        }
                        encsList.finish()
                        addParameter("encs", encsList)
                    }
                    finish()
                }
                for (i in accounts.indices) {
                    if (accounts[i] == account.ref) {
                        myAccountDescs[i] = accountDesc
                        break
                    }
                }
                ++myResultCount
                if (myResultCount == accounts.size) {
                    val reply = env.beginReply().apply {
                        addParameter("accounts", myAccountDescs)
                        finish()
                    }
                    from.send(reply)
                }
                /* Don't write, hence even success is a form of failure. */
                return false
            }

            override fun complete(failure: String?) {
                /* never called */
            }
        }
        for (account in accounts) {
            myBank!!.withAccount(account, lookupHandler)
        }
    }

    /**
     * Message handler for the 'freezeaccount' request: block an account from
     * participating in transactions.
     *
     * @param from  The sender of the request.
     * @param key  Key from authorizing access.
     * @param xid  Optional client-side response tag.
     * @param rep  Optional reference to client object to reply to.
     * @param memo  Transaction annotation, for logging.
     * @param account  Ref of the account to be frozen.
     */
    @JsonMethod("key", "xid", "rep", "memo", "account")
    fun freezeaccount(from: WorkshopActor, key: String,
                      xid: OptString, rep: OptString, memo: OptString,
                      account: String) {
        val env = init(from, "freezeaccount", key, xid, rep, false, memo, false, clock) ?: return
        if (env.operationAuthorityFailure("acct")) {
            return
        }
        myBank!!.withAccount(account, object : AccountUpdater {
            override fun modify(account: Account?): Boolean {
                if (env.invalidAccountFailure(account, "src")) {
                    return false
                }
                if (env.currencyAuthorityFailure(account!!.currency)) {
                    return false
                }
                account.isFrozen = true
                return true
            }

            override fun complete(failure: String?) {
                if (!env.accountWriteFailure(failure, "src")) {
                    val reply = env.beginReply().apply {
                        addParameter("account", account)
                        finish()
                    }
                    from.send(reply)
                }
            }
        })
    }

    /**
     * Message handler for the 'unfreezeaccount' request: remove the blockage
     * on a previously frozen account.
     *
     * @param from  The sender of the request.
     * @param key  Key from authorizing access.
     * @param xid  Optional client-side response tag.
     * @param rep  Optional reference to client object to reply to.
     * @param memo  Transaction annotation, for logging.
     * @param account  Ref of the account to be unfrozen.
     */
    @JsonMethod("key", "xid", "rep", "memo", "account")
    fun unfreezeaccount(from: WorkshopActor, key: String,
                        xid: OptString, rep: OptString, memo: OptString,
                        account: String) {
        val env = init(from, "unfreezeaccount", key, xid, rep, false, memo, false, clock) ?: return
        if (env.operationAuthorityFailure("acct")) {
            return
        }
        myBank!!.withAccount(account, object : AccountUpdater {
            override fun modify(account: Account?): Boolean {
                if (env.invalidAccountFailure(account, "src")) {
                    return false
                }
                if (env.currencyAuthorityFailure(account!!.currency)) {
                    return false
                }
                account.isFrozen = false
                return true
            }

            override fun complete(failure: String?) {
                if (!env.accountWriteFailure(failure, "src")) {
                    val reply = env.beginReply().apply {
                        addParameter("account", account)
                        finish()
                    }
                    from.send(reply)
                }
            }
        })
    }

    /**
     * Message handler for the 'makecurrency' request: create a new currency.
     *
     * @param from  The sender of the request.
     * @param key  Key from authorizing access.
     * @param xid  Optional client-side response tag.
     * @param rep  Optional reference to client object to reply to.
     * @param memo  Transaction annotation, for logging.
     * @param curr  Name for the new currency.
     */
    @JsonMethod("key", "xid", "rep", "memo", "curr")
    fun makecurrency(from: WorkshopActor, key: String, xid: OptString,
                     rep: OptString, memo: OptString, curr: String) {
        val env = init(from, "makecurrency", key, xid, rep, false, memo, true, clock) ?: return
        if (env.operationAuthorityFailure("full")) {
            return
        }
        if (myBank!!.getCurrency(curr) != null) {
            env.fail("currexists", "currency already exists")
            return
        }
        myBank!!.makeCurrency(curr, env.memo)
        val reply = env.beginReply().apply {
            addParameter("curr", curr)
            finish()
        }
        from.send(reply)
    }

    /**
     * Message handler for the 'querycurrencies' request: obtain information
     * about existing currencies.
     *
     * @param from  The sender of the request.
     * @param key  Key from authorizing access.
     * @param xid  Optional client-side response tag.
     * @param rep  Optional reference to client object to reply to.
     * @param memo  Transaction annotation, for logging.
     */
    @JsonMethod("key", "xid", "rep", "memo")
    fun querycurrencies(from: WorkshopActor, key: String, xid: OptString,
                        rep: OptString, memo: OptString) {
        val env = init(from, "querycurrencies", key, xid, rep, true, memo, false, clock) ?: return
        if (env.operationAuthorityFailure("full")) {
            return
        }
        val reply = env.beginReply().apply {
            val currList = JsonLiteralArray()
            myBank!!.currencies().forEach { curr ->
                val currDesc = JsonLiteral().apply {
                    addParameter("curr", curr.name)
                    addParameter("memo", curr.memo)
                    finish()
                }
                currList.addElement(currDesc)
            }
            currList.finish()
            addParameter("currencies", currList)
            finish()
        }
        from.send(reply)
    }

    /**
     * Message handler for the 'makekey' request: create a new key.
     *
     * @param from  The sender of the request.
     * @param key  Key from authorizing access.
     * @param xid  Optional client-side response tag.
     * @param rep  Optional reference to client object to reply to.
     * @param memo  Transaction annotation, for logging.
     * @param auth  The desired authority of the new key.
     * @param currs  Optional currencies scoping the new key.
     * @param optExpires  Date after which the new key will become invalid.
     */
    @JsonMethod("key", "xid", "rep", "memo", "auth", "currs", "expires")
    fun makekey(from: WorkshopActor, key: String, xid: OptString,
                rep: OptString, memo: OptString, auth: String,
                currs: Array<String>, optExpires: OptString) {
        val env = init(from, "makekey", key, xid, rep, true, memo, true, clock) ?: return
        val requiredAuth = if (auth == "curr") {
            "full"
        } else if (auth == "acct" || auth == "mint" || auth == "xfer") {
            "curr"
        } else {
            env.fail("badkeyauth", "invalid 'auth' parameter")
            return
        }
        if (env.operationAuthorityFailure(requiredAuth)) {
            return
        }
        if (env.currencyValidationFailure(currs)) {
            return
        }
        if (env.currencyAuthorityFailure(currs)) {
            return
        }
        val expires = env.getValidExpiration(optExpires.valueOrNull(), true) ?: return
        val newKey = myBank!!.makeKey(env.key, auth, currs, expires, env.memo)
        val reply = env.beginReply().apply {
            addParameter("newkey", newKey.ref)
            finish()
        }
        from.send(reply)
    }

    /**
     * Message handler for the 'cancelkey' request: invalidate an existing key.
     *
     * @param from  The sender of the request.
     * @param key  Key from authorizing access.
     * @param xid  Optional client-side response tag.
     * @param rep  Optional reference to client object to reply to.
     * @param memo  Transaction annotation, for logging.
     * @param cancel  Ref of the key to be cancelled.
     */
    @JsonMethod("key", "xid", "rep", "memo", "cancel")
    fun cancelkey(from: WorkshopActor, key: String, xid: OptString,
                  rep: OptString, memo: OptString, cancel: String) {
        val env = init(from, "cancelkey", key, xid, rep, false, memo, false, clock) ?: return
        val toCancel = myBank!!.getKey(cancel)
        if (toCancel == null) {
            env.fail("badkey", "invalid key specified by 'cancel' parameter")
            return
        }
        if (!toCancel.hasAncestor(env.key!!)) {
            env.fail("autherr", "bad authorization key")
            return
        }
        myBank!!.deleteKey(toCancel)
        val reply = env.beginReply().apply {
            addParameter("cancel", cancel)
            finish()
        }
        from.send(reply)
    }

    /**
     * Message handler for the 'dupkey' request: make a separately cancellable
     * copy of an existing key.
     *
     * @param from  The sender of the request.
     * @param key  Key from authorizing access (which will be copied).
     * @param xid  Optional client-side response tag.
     * @param rep  Optional reference to client object to reply to.
     * @param memo  Transaction annotation, for logging.
     * @param optExpires  Date after which the new key will become invalid.
     */
    @JsonMethod("key", "xid", "rep", "memo", "expires")
    fun dupkey(from: WorkshopActor, key: String, xid: OptString,
               rep: OptString, memo: OptString, optExpires: OptString) {
        val env = init(from, "makekey", key, xid, rep, true, memo, true, clock) ?: return
        val expires = env.getValidExpiration(optExpires.valueOrNull(), true) ?: return
        val newKey = myBank!!.makeKey(env.key, env.key!!.auth,
                env.key.currencies, expires, env.memo)
        val reply = env.beginReply().apply {
            addParameter("newkey", newKey.ref)
            finish()
        }
        from.send(reply)
    }

}