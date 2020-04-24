package org.elkoserver.server.context;

public class ContainerValidity {
    /**
     * Test if a proposed container for an item is acceptable.  In order for
     * this test to succeed, the proposed container object must be either a
     * context or a container item AND the user and the proposed container must
     * be in the same context.
     *
     * @param into  The proposed container object.
     * @param who  The user who is attempting to do this.
     *
     * @return true if it is OK for the user 'who' to use the object 'into' as
     *    a container, false if not.
     */
    public static boolean validContainer(BasicObject into, User who) {
        if (into == null) {
            return false;
        } else if (into.context() != who.context()) {
            return false;
        } else if (into instanceof Context) {
            return true;
        } else if (into instanceof Item) {
            return into.isContainer();
        } else {
            return false;
        }
    }
}
