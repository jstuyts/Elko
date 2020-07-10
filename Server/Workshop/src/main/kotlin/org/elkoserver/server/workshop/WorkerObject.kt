package org.elkoserver.server.workshop

import org.elkoserver.foundation.json.DispatchTarget
import org.elkoserver.json.Encodable
import org.elkoserver.json.EncodeControl
import org.elkoserver.json.JsonLiteral
import org.elkoserver.json.Referenceable

/**
 * Base class for all worker objects.
 *
 * @param myServiceName  Service name to register with broker, or null for
 *    none.
 */
abstract class WorkerObject protected constructor(private val myServiceName: String?) : DispatchTarget, Encodable, Referenceable {
    /** Reference string for this object.  */
    private var myRef: String? = null

    /** The workshop for this server.  */
    private lateinit var myWorkshop: Workshop

    /**
     * Make this object live inside the workshop server.
     *
     * @param ref  Reference string identifying this object.
     * @param workshop  The workshop for this server.
     */
    fun activate(ref: String?, workshop: Workshop) {
        myRef = ref
        myWorkshop = workshop
        if (ref != null) {
            workshop.refTable.addRef(this)
        }
        myServiceName?.let(workshop::registerService)
        activate()
    }

    /**
     * Overridable hook for subclasses to be notified about activation.
     */
    protected open fun activate() {}

    /**
     * Obtain the workshop for this server.
     *
     * @return the Workshop object for this server.
     */
    protected fun workshop(): Workshop = myWorkshop
    /* ----- Encodable interface ------------------------------------------ */
    /**
     * Produce a [JsonLiteral] representing the encoded state of this
     * object, suitable for transmission over a messaging medium or for writing
     * to persistent storage.  The default implementation makes this object
     * not actually encodable, but subclasses can override.
     *
     * @param control  Encode control determining what flavor of encoding
     * should be done.
     *
     * @return a [JsonLiteral] representing the encoded state of this
     * object.
     */
    override fun encode(control: EncodeControl): JsonLiteral? = null

    /* ----- Referenceable interface --------------------------------------- */
    /**
     * Obtain this object's reference string.
     *
     * @return a string that can be used to refer to this object in JSON
     * messages, either as the message target or as a parameter value.
     */
    override fun ref(): String = myRef ?: throw IllegalStateException()
}
