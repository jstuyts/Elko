package org.elkoserver.server.workshop.bank

import org.elkoserver.foundation.json.JsonMethod
import org.elkoserver.foundation.json.OptString
import org.elkoserver.json.Encodable
import org.elkoserver.json.EncodeControl
import org.elkoserver.json.JsonLiteral
import java.util.Arrays
import java.util.Arrays.binarySearch

/**
 * Object representing an access key: an authorization to perform some set of
 * actions.
 *
 * @param myParent  The key's parent key.
 * @param ref  Reference string for the key.
 * @param auth  Auth code denoting the key's authority.
 * @param theCurrencies  Optional currencies scoping the key's authority.
 * @param expires  The key's expiration date.
 * @param memo  Annotation on key.
 */
internal class Key(
        private var myParent: Key?, internal val ref: String?, internal val auth: String, theCurrencies: Array<String>?,
        internal val expires: ExpirationDate, private val memo: String?) : Encodable {

    /** Currencies upon which this key authorizes action, or null if the key is
     * not scoped by currency.  */
    internal val currencies: Array<String>?

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
    @JsonMethod("parent", "ref", "auth", "?currs", "expires", "memo")
    constructor(parentRef: String, ref: String, auth: String, currencies: Array<String>?,
                expires: ExpirationDate, memo: OptString) : this(null, ref, auth, currencies, expires, memo.value<String?>(null)) {
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
                JsonLiteral(control).apply {
                    addParameter("ref", ref)
                    addParameter("parent", myParent!!.ref)
                    addParameter("currs", currencies)
                    addParameter("auth", auth)
                    addParameter("expires", expires)
                    addParameterOpt("memo", memo)
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
        return currencies == null || binarySearch(currencies, currency) >= 0
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
        return if (auth == "full" || auth == "curr") true else auth == authNeed
    }

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
        get() = expires.isExpired

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
        if (theCurrencies != null) {
            Arrays.sort(theCurrencies)
        }
        currencies = theCurrencies
    }
}
