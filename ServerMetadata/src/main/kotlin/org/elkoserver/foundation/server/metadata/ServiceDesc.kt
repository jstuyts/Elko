package org.elkoserver.foundation.server.metadata

import org.elkoserver.foundation.json.JSONMethod
import org.elkoserver.foundation.json.OptInteger
import org.elkoserver.foundation.json.OptString
import org.elkoserver.json.Encodable
import org.elkoserver.json.EncodeControl
import org.elkoserver.json.JSONLiteralArray
import org.elkoserver.json.JSONLiteralFactory

/**
 * Description of a (possibly) registered service.
 *
 * @param myService  The name of the service.
 * @param myHostport  Where to reach the service.
 * @param myProtocol  Protocol to speak to the sevice with.
 * @param myLabel  Printable name for the service.
 * @param auth  Authorization configuration for connection to the service.
 * @param myFailure  Optional error message.
 * @param myProviderID  Provider ID, or -1 if not set.
 */
class ServiceDesc(private val myService: String, private val myHostport: String?, private val myProtocol: String?,
                  private var myLabel: String?, auth: AuthDesc?,
                  private val myFailure: String?,
                  private var myProviderID: Int) : Encodable {

    /** Authorization configuration to connect to the service.  */
    private val myAuth: AuthDesc = auth ?: AuthDesc.theOpenAuth

    /**
     * Error constructor.
     *
     * @param service  The name of the service.
     * @param failure  Error message.
     */
    constructor(service: String, failure: String?) : this(service, null, null, null, null, failure, -1) {}

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
    @JSONMethod("service", "hostport", "protocol", "label", "?auth", "failure", "provider")
    constructor(service: String, hostport: OptString, protocol: OptString,
                label: OptString, auth: AuthDesc?, failure: OptString,
                providerID: OptInteger) : this(service, hostport.value<String?>(null), protocol.value<String?>(null),
            label.value<String?>(null), auth, failure.value<String?>(null),
            providerID.value(-1)) {
    }

    /**
     * Generate a HostDesc object suitable for establishing a connection
     * to the service described by this service descriptor.
     *
     * @param retryInterval  Connection retry interval for connecting to this
     * host, or -1 to take the default.
     *
     * @return a HostDesc for this service's host.
     */
    fun asHostDesc(retryInterval: Int) = HostDesc(myProtocol, false, myHostport, myAuth, retryInterval)

    /**
     * Set this service's a label string.
     *
     * @param label  The new label string for this service.
     */
    fun attachLabel(label: String?) {
        myLabel = label
    }

    /**
     * Get this service's authorization configuration.
     *
     * @return this services's authorization configuration.
     */
    fun auth() = myAuth

    /**
     * Get this descriptor's error message.
     *
     * @return this descriptors's error message (or null if there is none).
     */
    fun failure() = myFailure

    /**
     * Get this service's host:port string.
     *
     * @return this services's host:port string (or null if there is none).
     */
    fun hostport() = myHostport

    /**
     * Get this service's label string.
     *
     * @return this service's label (or null if there is no label).
     */
    fun label() = myLabel

    /**
     * Get this service's protocol string.
     *
     * @return this service's protocol (or null if there is none).
     */
    fun protocol() = myProtocol

    /**
     * Get this service's provider ID.
     *
     * @return this service's provider ID (or -1 if it has none).
     */
    fun providerID() = myProviderID

    /**
     * Get this services's service name.
     *
     * @return this services's service name (or null if it has none).
     */
    fun service() = myService

    /**
     * Set this service's provider ID.  It is an error to set this value if it
     * has already been set.
     *
     * @param providerID  Nominal provider ID number for this service.
     */
    fun setProviderID(providerID: Int) {
        myProviderID = if (myProviderID == -1) {
            providerID
        } else {
            throw Error("attempt to set provider ID that is already set")
        }
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
        var label = myLabel
        if (label != null) {
            label += " ($service)"
        }
        return ServiceDesc("$myService-$service", myHostport, myProtocol, label, myAuth, myFailure, myProviderID)
    }

    /**
     * Encode this object for transmission or persistence.
     *
     * @param control  Encode control determining what flavor of encoding
     * should be done.
     *
     * @return a JSON literal representing this object.
     */
    override fun encode(control: EncodeControl) =
            JSONLiteralFactory.type("servicedesc", control).apply {
                addParameter("service", myService)
                addParameterOpt("hostport", myHostport)
                addParameterOpt("protocol", myProtocol)
                addParameterOpt("label", myLabel)
                addParameterOpt("auth", myAuth)
                addParameterOpt("failure", myFailure)
                if (myProviderID != -1) {
                    addParameter("provider", myProviderID)
                }
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

    companion object {
        /**
         * Generate a JSONLiteralArray of ServiceDesc objects from a sequence of
         * them.
         */
        @JvmStatic
        fun encodeArray(services: Iterable<ServiceDesc?>?) =
                JSONLiteralArray().apply {
                    if (services != null) {
                        for (service in services) {
                            addElement(service)
                        }
                    }
                    finish()
                }
    }
}
