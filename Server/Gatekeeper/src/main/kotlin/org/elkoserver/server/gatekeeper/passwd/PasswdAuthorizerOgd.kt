package org.elkoserver.server.gatekeeper.passwd

import org.elkoserver.server.gatekeeper.AuthorizerGraph
import org.elkoserver.server.gatekeeper.AuthorizerOgd
import org.elkoserver.server.gatekeeper.AuthorizerProvided
import org.ooverkommelig.ObjectGraphConfiguration
import org.ooverkommelig.ObjectGraphDefinition
import org.ooverkommelig.req

class PasswdAuthorizerOgd(provided: AuthorizerProvided, configuration: ObjectGraphConfiguration = ObjectGraphConfiguration()) : ObjectGraphDefinition(provided, configuration), AuthorizerOgd {
    internal val passwordAuthorizerSgd = add(PasswdAuthorizerSgd(provided))

    override fun graph(): AuthorizerGraph = object : DefinitionObjectGraph(), AuthorizerGraph {
        override fun authorizer() = req(passwordAuthorizerSgd.authorizer)
    }
}
