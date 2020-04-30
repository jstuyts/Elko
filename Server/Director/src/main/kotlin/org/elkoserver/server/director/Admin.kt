package org.elkoserver.server.director

import java.util.HashSet

/**
 * The admin facet of a director actor.  This object represents the state
 * functionality required when a connected entity is engaging in the admin
 * protocol.
 *
 * @param myDirector  The director being administered.
 * @param myActor  The actor associated with the administrator.
 */
internal class Admin(private val myDirector: Director, private val myActor: DirectorActor) {

    /** Users currently being watched.  */
    private val myWatchedUsers: MutableSet<String> = HashSet()

    /** Contexts currently being watched.  */
    private val myWatchedContexts: MutableSet<String> = HashSet()

    /**
     * Clean up when the admin actor disconnects.
     */
    fun doDisconnect() {
        for (user in myWatchedUsers) {
            myDirector.unwatchUser(user, myActor)
        }
        for (context in myWatchedContexts) {
            myDirector.unwatchContext(context, myActor)
        }
    }

    /**
     * Stop watching for the openings and closings of a context.
     *
     * @param contextName  The name of the context not to be watched.
     */
    fun unwatchContext(contextName: String) {
        myWatchedContexts.remove(contextName)
        myDirector.unwatchContext(contextName, myActor)
    }

    /**
     * Stop watching for the arrivals and departures of a user.
     *
     * @param userName  The name of the user not to be watched.
     */
    fun unwatchUser(userName: String) {
        myWatchedUsers.remove(userName)
        myDirector.unwatchUser(userName, myActor)
    }

    /**
     * Watch for the openings and closings of a context.
     *
     * @param contextName  The name of the context to be watched.
     */
    fun watchContext(contextName: String) {
        myWatchedContexts.add(contextName)
        myDirector.watchContext(contextName, myActor)
    }

    /**
     * Watch for the arrivals and departures of a user.
     *
     * @param userName  The name of the user to be watched.
     */
    fun watchUser(userName: String) {
        myWatchedUsers.add(userName)
        myDirector.watchUser(userName, myActor)
    }
}
