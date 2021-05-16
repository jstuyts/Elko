package org.elkoserver.server.context

/**
 * Interface implemented by objects that wish to be notified when users arrive
 * in or depart from context.
 *
 * This notification can be arranged by calling the [Context.registerUserWatcher] method on the [ ] in which one has an interest in the comings and goings of users.
 */
interface UserWatcher {
    /**
     * Do whatever you want when somebody arrives.
     *
     * Whenever a user enters a context, the server will call this method on
     * all objects that have registered an interest in that context via the
     * context's [registerUserWatcher()][Context.registerUserWatcher]
     * method.
     *
     * @param who  The user who arrived.
     */
    fun noteUserArrival(who: User)

    /**
     * Do whatever you want when somebody leaves.
     *
     * Whenever a user exits a context, the server will call this method on
     * all objects that have registered an interest in that context via the
     * context's [registerUserWatcher()][Context.registerUserWatcher]
     * method.
     *
     * @param who  The user who departed.
     */
    fun noteUserDeparture(who: User)
}