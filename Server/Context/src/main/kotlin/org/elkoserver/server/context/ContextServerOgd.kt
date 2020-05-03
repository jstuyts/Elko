package org.elkoserver.server.context

import org.ooverkommelig.ObjectGraphConfiguration
import org.ooverkommelig.ObjectGraphDefinition
import org.ooverkommelig.req

class ContextServerOgd(provided: Provided, configuration: ObjectGraphConfiguration = ObjectGraphConfiguration()) : ObjectGraphDefinition(provided, configuration) {
    interface Provided : ContextServerSgd.Provided

    val contextServerSgd = add(ContextServerSgd(provided, configuration))

    inner class Graph : DefinitionObjectGraph() {
        fun contextor() = req(contextServerSgd.contextor)
    }
}
