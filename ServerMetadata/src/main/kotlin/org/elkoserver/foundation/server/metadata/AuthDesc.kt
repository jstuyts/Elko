package org.elkoserver.foundation.server.metadata

import org.elkoserver.foundation.json.JsonMethod
import org.elkoserver.foundation.json.OptString
import org.elkoserver.json.Encodable
import org.elkoserver.json.EncodeControl
import org.elkoserver.json.JsonLiteral
import org.elkoserver.json.JsonLiteralFactory

/**
 * Descriptor containing information required or presented to authorize a
 * connection.
 *
 * @param mode  Authorization mode.
 * @param code  Authorization code, or null if not relevant.
 * @param id  Authorization ID, or null if not relevant.
 */
class AuthDesc(val mode: String, private val code: String?, private val id: String?) : Encodable {

    /**
     * JSON-driven constructor.
     *
     * @param mode  Authorization mode.
     * @param code  Optional authorization code.
     * @param id  Optional authorization ID.
     */
    @JsonMethod("mode", "code", "id")
    constructor(mode: String, code: OptString, id: OptString) : this(mode, code.valueOrNull(), id.valueOrNull())

    /**
     * Check an authorization.  This authorization descriptor is treated as a
     * set of requirements.  The authorization descriptor given in the 'auth'
     * parameter is treated as a presented set of authorization credentials.
     * The credentials are compared to the requirements to see if they satisfy
     * them.
     *
     * @param auth  Authorization credentials being presented.
     *
     * @return true if 'auth' correctly authorizes connection under
     * the authorization configuration described by this object.
     */
    fun verify(auth: AuthDesc?): Boolean {
        return when {
            auth == null -> mode == "open"
            mode == auth.mode ->
                when (mode) {
                    "open" -> true
                    "password" -> code == auth.code
                    else -> false
                }
            else -> false
        }
    }

    /**
     * Encode this object for transmission or persistence.
     *
     * @param control  Encode control determining what flavor of encoding
     * should be done.
     *
     * @return a JSON literal representing this object.
     */
    override fun encode(control: EncodeControl): JsonLiteral? =
            if (control.toClient() && mode == "open") {
                null
            } else {
                JsonLiteralFactory.type("auth", control).apply {
                    addParameter("mode", mode)
                    addParameterOpt("id", id)
                    addParameterOpt("code", code)
                    finish()
                }
            }

    companion object {
        /** Singleton open authorization descriptor. This may be used in all
         * circumstances where open mode authorization is required or
         * presented.  */
        val theOpenAuth: AuthDesc = AuthDesc("open", null, null)
    }
}
