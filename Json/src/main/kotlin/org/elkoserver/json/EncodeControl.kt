package org.elkoserver.json

/**
 * Control object for regulating the behavior of an encoding operation.  When
 * an object is being serialized to JSON, the choice of the subset of the
 * object's state that actually gets written can vary depending on the intended
 * destination or use for the data.
 *
 * Currently, there are two cases: encoding for the client and encoding for the
 * repository.  However, additional cases are possible; for example, a
 * representation being sent to a client might vary depending on whether the
 * user of the client is the "owner" of the object in question or not.  This
 * class exists to provide a place to extend the range of options, though the
 * base case only supports the client vs. repository distinction.
 */
sealed class EncodeControl {
    /**
     * Test if this controller says to encode for the client.
     *
     * @return true if this should be a client encoding.
     */
    abstract fun toClient(): Boolean

    /**
     * Test if this controller says to encode for the repository.
     *
     * @return true if this should be a repository encoding.
     */
    abstract fun toRepository(): Boolean

    /** A global, encoding control representing the intention to encode for the
     * client.  */
    object ForClientEncodeControl : EncodeControl() {
        override fun toClient(): Boolean = true

        override fun toRepository(): Boolean = false
    }

    /** A global, encoding control representing the intention to encode for the
     * repository.  */
    object ForRepositoryEncodeControl : EncodeControl() {
        override fun toClient(): Boolean = false

        override fun toRepository(): Boolean = true
    }
}
