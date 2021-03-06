@file:Suppress("MemberVisibilityCanBePrivate")

package org.elkoserver.server.gatekeeper.passwd

import org.elkoserver.server.gatekeeper.AuthorizerProvided
import org.ooverkommelig.ObjectGraphConfiguration
import org.ooverkommelig.Once
import org.ooverkommelig.SubGraphDefinition
import org.ooverkommelig.req
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.security.SecureRandom

internal class PasswdAuthorizerSgd(
    provided: AuthorizerProvided,
    configuration: ObjectGraphConfiguration = ObjectGraphConfiguration()
) : SubGraphDefinition(configuration) {
    val authorizer by Once {
        PasswdAuthorizer(
            req(random),
            req(provided.gatekeeper()),
            req(objectDatabaseWithClassesAdded),
            req(anonymousOk),
            req(actorIdBase)
        )
    }
        .dispose { it.shutDown() }

    val authHandler by Once {
        AuthHandler(
            req(authorizer),
            req(provided.gatekeeper()),
            req(provided.baseCommGorgel()).getChild(AuthHandler::class),
            req(random),
            req(sha)
        )
    }
        .wire { req(provided.gatekeeper()).refTable.addRef(it) }
        .eager()

    val random by Once(::SecureRandom)

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

    val objectDatabaseWithClassesAdded by Once {
        req(provided.objectDatabase()).apply {
            addClass("place", PlaceDesc::class.java)
            addClass("actor", ActorDesc::class.java)
        }
    }
}
