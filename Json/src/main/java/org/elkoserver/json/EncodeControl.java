package org.elkoserver.json;

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
public abstract class EncodeControl {
    /** A global, encoding control representing the intention to encode for the
        client. */
    public static final EncodeControl forClient =
        new ForClientEncodeControl();

    /** A global, encoding control representing the intention to encode for the
        repository. */
    public static final EncodeControl forRepository =
        new ForRepositoryEncodeControl();

    /**
     * Private constructor.
     */
    private EncodeControl() {
    }

    /**
     * Test if this controller says to encode for the client.
     *
     * @return true if this should be a client encoding.
     */
    public abstract boolean toClient();

    /**
     * Test if this controller says to encode for the repository.
     *
     * @return true if this should be a repository encoding.
     */
    public abstract boolean toRepository();

    /** A global, encoding control representing the intention to encode for the
     client. */
    private static class ForClientEncodeControl extends EncodeControl {
        @Override
        public boolean toClient() {
            return true;
        }

        @Override
        public boolean toRepository() {
            return false;
        }
    }

    /** A global, encoding control representing the intention to encode for the
     repository. */
    private static class ForRepositoryEncodeControl extends EncodeControl {
        @Override
        public boolean toClient() {
            return false;
        }

        @Override
        public boolean toRepository() {
            return true;
        }
    }
}
