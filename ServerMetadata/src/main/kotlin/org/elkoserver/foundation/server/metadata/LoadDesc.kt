package org.elkoserver.foundation.server.metadata

import org.elkoserver.foundation.json.JSONMethod
import org.elkoserver.json.Encodable
import org.elkoserver.json.EncodeControl
import org.elkoserver.json.JSONLiteralArray
import org.elkoserver.json.JSONLiteralFactory

/**
 * Description of the load on a server.
 */
class LoadDesc @JSONMethod("label", "load", "provider") constructor(private val myLabel: String, private val myLoad: Double, private val myProviderID: Int) : Encodable {

    /**
     * Get the label for the server being described.
     *
     * @return the server label.
     */
    fun label() = myLabel

    /**
     * Get the reported load factor.
     *
     * @return the load factor.
     */
    fun load() = myLoad

    /**
     * Get the provider ID for the server being described.
     *
     * @return the provider ID.
     */
    fun providerID() = myProviderID

    /**
     * Encode this object for transmission or persistence.
     *
     * @param control  Encode control determining what flavor of encoding
     * should be done.
     *
     * @return a JSON literal representing this object.
     */
    override fun encode(control: EncodeControl) =
            JSONLiteralFactory.type("loaddesc", control).apply {
                addParameter("label", myLabel)
                addParameter("load", myLoad)
                addParameter("provider", myProviderID)
                finish()
            }

    /**
     * Encode this descriptor as a single-element JSONLiteralArray.
     */
    fun encodeAsArray() =
            JSONLiteralArray().apply {
                addElement(this)
                finish()
            }
}
