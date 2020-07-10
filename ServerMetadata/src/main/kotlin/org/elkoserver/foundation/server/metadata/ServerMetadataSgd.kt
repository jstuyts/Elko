@file:Suppress("MemberVisibilityCanBePrivate")

package org.elkoserver.foundation.server.metadata

import org.elkoserver.foundation.net.Communication.COMMUNICATION_CATEGORY_TAG
import org.elkoserver.foundation.properties.ElkoProperties
import org.elkoserver.util.trace.slf4j.Gorgel
import org.ooverkommelig.D
import org.ooverkommelig.Definition
import org.ooverkommelig.ObjectGraphConfiguration
import org.ooverkommelig.Once
import org.ooverkommelig.ProvidedBase
import org.ooverkommelig.SubGraphDefinition
import org.ooverkommelig.req

class ServerMetadataSgd(provided: Provided, configuration: ObjectGraphConfiguration = ObjectGraphConfiguration()) : SubGraphDefinition(provided, configuration) {
    interface Provided : ProvidedBase {
        fun props(): D<ElkoProperties>
        fun baseGorgel(): D<Gorgel>
    }

    val authDescFromPropertiesFactoryGorgel: Definition<Gorgel> by Once { req(provided.baseGorgel()).getChild(AuthDescFromPropertiesFactory::class, COMMUNICATION_CATEGORY_TAG) }

    val authDescFromPropertiesFactory: Definition<AuthDescFromPropertiesFactory> by Once { AuthDescFromPropertiesFactory(req(provided.props()), req(authDescFromPropertiesFactoryGorgel)) }

    val hostDescFromPropertiesFactory: Definition<HostDescFromPropertiesFactory> by Once { HostDescFromPropertiesFactory(req(provided.props()), req(authDescFromPropertiesFactory)) }
}
