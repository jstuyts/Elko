package org.elkoserver.server.workshop.bank

import org.elkoserver.foundation.json.JSONMethod
import org.elkoserver.foundation.json.OptString
import org.elkoserver.json.Encodable
import org.elkoserver.json.EncodeControl
import org.elkoserver.json.JSONLiteral
import java.util.Arrays
import java.util.Arrays.binarySearch

/**
 * Object representing an access key: an authorization to perform some set of
 * actions.
 *
 * @param myParent  The key's parent key.
 * @param myRef  Reference string for the key.
 * @param myAuth  Auth code denoting the key's authority.
 * @param currencies  Optional currencies scoping the key's authority.
 * @param myExpires  The key's expiration date.
 * @param myMemo  Annotation on key.
 */
internal class Key(
        private var myParent: Key?, private val myRef: String?, private val myAuth: String, currencies: Array<String?>?,
        private val myExpires: ExpirationDate, private val myMemo: String) : Encodable {

    /** Currencies upon which this key authorizes action, or null if the key is
     * not scoped by currency.  */
    private val myCurrencies: Array<String?>?

    /** Ref of parent key.  Note that this field is valid only during the
     * extended key decode/construction process, and must be null in a
     * well-formed key object.  */
    private var myParentRef: String? = null

    /**
     * JSON-driven constructor.
     *
     * @param parentRef  The ref of the key's parent key.
     * @param ref  Reference string for the key.
     * @param auth  Auth code denoting the key's authority.
     * @param currencies  Optional currencies scoping the key's authority.
     * @param expires  The key's expiration date.
     * @param memo  Annotation on key.
     */
    @JSONMethod("parent", "ref", "auth", "?currs", "expires", "memo")
    constructor(parentRef: String?, ref: String?, auth: String, currencies: Array<String?>?,
                expires: ExpirationDate, memo: OptString) : this(null, ref, auth, currencies, expires, memo.value(null)) {
        myParentRef = parentRef
    }

    /**
     * Encode this key for transmission or persistence.
     *
     * @param control  Encode control determining what flavor of encoding
     * should be done.
     *
     * @return a JSON literal representing this key.
     */
    override fun encode(control: EncodeControl) =
            if (control.toRepository()) {
                JSONLiteral(control).apply {
                    addParameter("ref", myRef)
                    addParameter("parent", myParent!!.ref())
                    addParameter("currs", myCurrencies)
                    addParameter("auth", myAuth)
                    addParameter("expires", myExpires)
                    addParameterOpt("memo", myMemo)
                    finish()
                }
            } else {
                null
            }

    /**
     * Test if this key authorizes operations on a particular currency.
     *
     * @param currency  The currency of interest.
     *
     * @return true if this key authorizes operations on the given currency,
     * false if not.
     */
    fun allowsCurrency(currency: String?): Boolean {
        if (myParentRef != null) {
            throw Error("attempt to exercise authority of incomplete key")
        }
        return myCurrencies == null || binarySearch(myCurrencies, currency) >= 0
    }

    /**
     * Test if this key authorizes a particular kind of operation.
     *
     * @param authNeed  The kind of authority that is desired
     *
     * @return true if this key grants the kind of authority sought, false
     * if not.
     */
    fun allowsOperation(authNeed: String): Boolean {
        if (myParentRef != null) {
            throw Error("attempt to exercise authority of incomplete key")
        }
        return if (myAuth == "full" || myAuth == "curr") true else myAuth == authNeed
    }

    /**
     * Obtain the kind of authority this key grants.
     *
     * @return this key's auth value.
     */
    fun auth() = myAuth

    /**
     * Obtain the currencies that scope this key's authority.
     *
     * @return this key's scoping currencies, or null if its authority is not
     * currency scoped.
     */
    fun currencies() = myCurrencies

    /**
     * Obtain the date after which this key no longer works.
     *
     * @return this key's expiration date.
     */
    fun expires() = myExpires

    /**
     * Test if a given key is somewhere in this key's creation ancestry.
     *
     * @param key  The potential parent key of interest.
     *
     * @return true iff the given key was this key's creator, or its creator's
     * creator, or its creator's creator's creator, etc.
     */
    fun hasAncestor(key: Key): Boolean {
        if (myParentRef != null) {
            throw Error("attempt to test parent of incomplete key")
        }
        return when {
            myParent === key -> true
            myParent == null -> false
            else -> myParent!!.hasAncestor(key)
        }
    }

    /**
     * Obtain the ref of this key's parent, as part of construction.
     *
     * @return the parent key ref.
     */
    fun parentRef(): String? {
        if (myParent != null) {
            throw Error("attempt to get parent ref of complete key")
        }
        return myParentRef
    }

    /**
     * Test if this key is expired.
     *
     * @return true if this key is expired, false if not.
     */
    val isExpired: Boolean
        get() = myExpires.isExpired

    /**
     * Obtain this key's memo string, an arbitrary annotation associated with
     * the key when it was created.
     *
     * @return this key's memo.
     */
    fun memo() = myMemo

    /**
     * Obtain this key's unique identifier string.
     *
     * @return this key's ref.
     */
    fun ref() = myRef

    /**
     * Assign this key's parent key.  This operation may only be done once
     * per key.
     *
     * @param parent  The key that should be regarded as this key's parent.
     */
    fun setParent(parent: Key?) {
        if (myParentRef == null || myParent != null) {
            throw Error("setParent on a key that already has a parent")
        }
        myParent = parent
        myParentRef = null
    }

    init {
        if (currencies != null) {
            Arrays.sort(currencies)
        }
        myCurrencies = currencies
    }
}
