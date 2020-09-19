package org.elkoserver.foundation.server

import org.elkoserver.foundation.properties.ElkoProperties
import org.elkoserver.objdb.ObjDbLocalFactory
import org.elkoserver.objdb.ObjDbRemoteFactory

class ObjectDatabaseFactory(
        private val myProps: ElkoProperties,
        private val objDbRemoteFactory: ObjDbRemoteFactory,
        private val objDbLocalFactory: ObjDbLocalFactory) {

    /**
     * Open an asynchronous object database whose location (directory path or
     * remote repository host) is specified by properties.
     *
     * @param propRoot  Prefix string for all the properties describing the objDb
     * that is to be opened.
     *
     * @return an object for communicating with the opened objDb.
     */
    fun openObjectDatabase(propRoot: String) =
            if (myProps.getProperty("$propRoot.odjdb") != null) {
                objDbLocalFactory.create(propRoot)
            } else {
                if (myProps.getProperty("$propRoot.repository.host") != null ||
                        myProps.getProperty("$propRoot.repository.service") != null) {
                    objDbRemoteFactory.create(propRoot)
                } else {
                    throw IllegalStateException()
                }
            }
}
