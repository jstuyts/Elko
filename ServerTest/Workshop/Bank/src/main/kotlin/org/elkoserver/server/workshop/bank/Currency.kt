package org.elkoserver.server.workshop.bank

import org.elkoserver.foundation.json.JSONMethod
import org.elkoserver.json.Encodable
import org.elkoserver.json.EncodeControl
import org.elkoserver.json.JSONLiteral

/**
 * Object representing a defined currency: a type token denominating a
 * monetary value.
 *
 * @param name  The currency name.
 * @param memo  Annotation on currency.
 */
class Currency @JSONMethod("name", "memo") internal constructor(private val myName: String, private val myMemo: String) : Encodable {

    /**
     * Encode this currency descriptor for transmission or persistence.
     *
     * @param control  Encode control determining what flavor of encoding
     * should be done.
     *
     * @return a JSON literal representing this currency.
     */
    override fun encode(control: EncodeControl) =
            JSONLiteral(control).apply {
                addParameter("name", myName)
                addParameter("memo", myMemo)
                finish()
            }

    /**
     * Obtain this currency's memo string, an arbitrary annotation associated
     * with the currency when it was created.
     *
     * @return the currency's memo.
     */
    fun memo(): String = myMemo

    /**
     * Obtain this currency's name.
     *
     * @return the currency name.
     */
    fun name(): String = myName
}
