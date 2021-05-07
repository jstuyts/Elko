package org.elkoserver.foundation.server

import org.elkoserver.foundation.properties.ElkoProperties
import org.elkoserver.objectdatabase.DirectObjectDatabaseFactory
import org.elkoserver.objectdatabase.RepositoryObjectDatabaseFactory

class ObjectDatabaseFactory(
        private val myProps: ElkoProperties,
        private val repositoryObjectDatabaseFactory: RepositoryObjectDatabaseFactory,
        private val directObjectDatabaseFactory: DirectObjectDatabaseFactory) {

    /**
     * Open an asynchronous object database whose location (directory path or
     * remote repository host) is specified by properties.
     *
     * @param propRoot  Prefix string for all the properties describing the
     * object database that is to be opened.
     *
     * @return an object for communicating with the opened object database.
     */
    fun openObjectDatabase(propRoot: String) =
            if (myProps.getProperty("$propRoot.odjdb") != null) {
                directObjectDatabaseFactory.create(propRoot)
            } else {
                if (myProps.getProperty("$propRoot.repository.host") != null ||
                        myProps.getProperty("$propRoot.repository.service") != null) {
                    repositoryObjectDatabaseFactory.create(propRoot)
                } else {
                    throw IllegalStateException()
                }
            }
}
