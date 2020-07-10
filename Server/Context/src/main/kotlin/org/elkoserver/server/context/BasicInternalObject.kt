package org.elkoserver.server.context

/**
 * Utility base class for internal objects, providing a general implementation
 * of the InternalObject interface that should be suitable for most uses.
 */
abstract class BasicInternalObject : InternalObject {
    /** The name by which this object will be addressed in messages.  */
    private lateinit var myRef: String

    /**
     * Make this object live inside the context server.
     *
     * @param ref  Reference string identifying this object in the static
     * object table.
     * @param contextor  The contextor for this server.
     */
    override fun activate(ref: String, contextor: Contextor) {
        myRef = ref
    }

    /**
     * Obtain this object's reference string.
     *
     * @return a string that can be used to refer to this object in JSON
     * messages, either as the message target or as a parameter value.
     */
    override fun ref(): String = myRef
}
