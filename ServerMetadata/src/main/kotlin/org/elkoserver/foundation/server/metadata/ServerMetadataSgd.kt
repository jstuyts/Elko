@file:Suppress("MemberVisibilityCanBePrivate")

package org.elkoserver.foundation.server.metadata

import org.elkoserver.foundation.properties.ElkoProperties
import org.elkoserver.util.trace.slf4j.Gorgel
import org.elkoserver.util.trace.slf4j.Tag
import org.ooverkommelig.D
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

    val authDescFromPropertiesFactoryGorgel by Once { req(provided.baseGorgel()).getChild(AuthDescFromPropertiesFactory::class, Tag("category", "comm")) }

    val authDescFromPropertiesFactory by Once { AuthDescFromPropertiesFactory(req(provided.props()), req(authDescFromPropertiesFactoryGorgel)) }

    val hostDescFromPropertiesFactory by Once { HostDescFromPropertiesFactory(req(provided.props()), req(authDescFromPropertiesFactory))}
}
