package org.elkoserver.foundation.server.metadata

import org.elkoserver.foundation.json.JsonMethod
import org.elkoserver.foundation.json.OptInteger
import org.elkoserver.foundation.json.OptString
import org.elkoserver.json.Encodable
import org.elkoserver.json.EncodeControl
import org.elkoserver.json.JsonLiteral
import org.elkoserver.json.JsonLiteralArray
import org.elkoserver.json.JsonLiteralFactory

/**
 * Description of a (possibly) registered service.
 *
 * @param service  The name of the service.
 * @param hostport  Where to reach the service.
 * @param protocol  Protocol to speak to the sevice with.
 * @param theLabel  Printable name for the service.
 * @param theAuth  Authorization configuration for connection to the service.
 * @param failure  Optional error message.
 * @param theProviderID  Provider ID, or -1 if not set.
 */
class ServiceDesc(val service: String, val hostport: String?, val protocol: String?,
                  theLabel: String?, theAuth: AuthDesc?,
                  val failure: String?,
                  theProviderID: Int) : Encodable {

    private var label: String? = theLabel

    var providerID: Int = theProviderID
        set(value) {
            field = if (field == -1) {
                value
            } else {
                throw Error("attempt to set provider ID that is already set")
            }
        }

    /** Authorization configuration to connect to the service.  */
    val auth: AuthDesc = theAuth ?: AuthDesc.theOpenAuth

    /**
     * Error constructor.
     *
     * @param service  The name of the service.
     * @param failure  Error message.
     */
    constructor(service: String, failure: String?) : this(service, null, null, null, null, failure, -1)

    /**
     * JSON-driven constructor.
     *
     * @param service  The name of the service.
     * @param hostport  Where to reach the service.
     * @param protocol  Protocol to speak to the service with.
     * @param label  Optional printable name for the service.
     * @param auth  Optional authorization configuration for connection.
     * @param failure  Optional error message.
     * @param providerID  Optional provider ID.
     */
    @JsonMethod("service", "hostport", "protocol", "label", "?auth", "failure", "provider")
    constructor(service: String, hostport: OptString, protocol: OptString,
                label: OptString, auth: AuthDesc?, failure: OptString,
                providerID: OptInteger) : this(service, hostport.valueOrNull(), protocol.valueOrNull(),
            label.valueOrNull(), auth, failure.valueOrNull(),
            providerID.value(-1))

    /**
     * Generate a HostDesc object suitable for establishing a connection
     * to the service described by this service descriptor.
     *
     * @param retryInterval  Connection retry interval for connecting to this
     * host, or -1 to take the default.
     *
     * @return a HostDesc for this service's host.
     */
    fun asHostDesc(retryInterval: Int): HostDesc = HostDesc(protocol, false, hostport, auth, retryInterval)

    /**
     * Set this service's a label string.
     *
     * @param newLabel  The new label string for this service.
     */
    fun attachLabel(newLabel: String?) {
        label = newLabel
    }

    /**
     * Generate a service descriptor based on this one.
     *
     * @param service   The service name of the sub-service.
     *
     * @return a new service descriptor with the same contact information
     * but with a subsidary service name appended.
     */
    fun subService(service: String): ServiceDesc {
        val label = label?.let { "$it ($service)" }
        return ServiceDesc("${this.service}-$service", hostport, protocol, label, auth, failure, providerID)
    }

    /**
     * Encode this object for transmission or persistence.
     *
     * @param control  Encode control determining what flavor of encoding
     * should be done.
     *
     * @return a JSON literal representing this object.
     */
    override fun encode(control: EncodeControl): JsonLiteral =
            JsonLiteralFactory.type("servicedesc", control).apply {
                addParameter("service", service)
                addParameterOpt("hostport", hostport)
                addParameterOpt("protocol", protocol)
                addParameterOpt("label", label)
                addParameterOpt("auth", auth)
                addParameterOpt("failure", failure)
                if (providerID != -1) {
                    addParameter("provider", providerID)
                }
                finish()
            }

    /**
     * Encode this descriptor as a single-element JSONLiteralArray.
     */
    fun encodeAsArray(): JsonLiteralArray =
            JsonLiteralArray().apply {
                addElement(this)
                finish()
            }
}
