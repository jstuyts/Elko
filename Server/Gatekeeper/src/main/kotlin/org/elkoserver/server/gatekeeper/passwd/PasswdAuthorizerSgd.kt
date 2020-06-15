@file:Suppress("MemberVisibilityCanBePrivate")

package org.elkoserver.server.gatekeeper.passwd

import org.elkoserver.foundation.server.ShutdownWatcher
import org.elkoserver.server.gatekeeper.AuthorizerProvided
import org.ooverkommelig.ObjectGraphConfiguration
import org.ooverkommelig.Once
import org.ooverkommelig.SubGraphDefinition
import org.ooverkommelig.req
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.security.SecureRandom

internal class PasswdAuthorizerSgd(provided: AuthorizerProvided, configuration: ObjectGraphConfiguration = ObjectGraphConfiguration()) : SubGraphDefinition(provided, configuration) {
    val authorizer by Once { PasswdAuthorizer(req(random), req(provided.gatekeeper()), req(odb), req(anonymousOk), req(actorIdBase)) }
            .wire {
                req(provided.server()).registerShutdownWatcher(object : ShutdownWatcher {
                    override fun noteShutdown() {
                        it.shutdown()
                    }
                })
            }

    val authHandler by Once { AuthHandler(req(authorizer), req(provided.gatekeeper()), req(provided.traceFactory()), req(random), req(sha)) }
            .wire {
                req(provided.gatekeeper()).refTable.addRef(it)
            }
            .eager()

    val random by Once { SecureRandom() }

    val sha by Once {
        try {
            MessageDigest.getInstance("SHA")
        } catch (e: NoSuchAlgorithmException) {
            /* According to Sun's documentation, this exception can't actually
           happen, since the JVM is required to support the SHA algorithm.
           However, the compiler requires the catch.  And it *could* happen
           if either the documentation or the JVM implementation are wrong.
           Like that ever happens. */
            throw IllegalStateException("This JVM lacks SHA support", e)
        }
    }

    val actorIdBase by Once { req(provided.props()).getProperty("conf.gatekeeper.idbase", "user") }

    val anonymousOk by Once { !req(provided.props()).testProperty("conf.gatekeeper.anonymous", "false") }

    val odb by Once { req(provided.server()).openObjectDatabase("conf.gatekeeper") ?: throw IllegalStateException("no database specified") }
}
