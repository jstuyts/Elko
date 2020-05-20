@file:Suppress("MemberVisibilityCanBePrivate")

package org.elkoserver.server.gatekeeper.passwd

import org.elkoserver.foundation.server.ShutdownWatcher
import org.elkoserver.server.gatekeeper.AuthorizerProvided
import org.ooverkommelig.ObjectGraphConfiguration
import org.ooverkommelig.Once
import org.ooverkommelig.SubGraphDefinition
import org.ooverkommelig.req
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

    val authHandler by Once { AuthHandler(req(authorizer), req(provided.gatekeeper()), req(provided.traceFactory())) }
            .wire {
                req(provided.gatekeeper()).refTable().addRef(it)
            }
            .eager()

    val random by Once { SecureRandom() }

    val actorIdBase by Once { req(provided.props()).getProperty("conf.gatekeeper.idbase", "user") }

    val anonymousOk by Once { !req(provided.props()).testProperty("conf.gatekeeper.anonymous", "false") }

    val odb by Once { req(provided.server()).openObjectDatabase("conf.gatekeeper") ?: throw IllegalStateException("no database specified") }
}
