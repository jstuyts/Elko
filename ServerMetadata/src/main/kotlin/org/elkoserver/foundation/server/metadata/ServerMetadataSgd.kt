@file:Suppress("MemberVisibilityCanBePrivate")

package org.elkoserver.foundation.server.metadata

import org.elkoserver.foundation.net.Communication.COMMUNICATION_CATEGORY_TAG
import org.elkoserver.foundation.properties.ElkoProperties
import org.elkoserver.util.trace.slf4j.Gorgel
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

    val authDescFromPropertiesFactoryGorgel by Once { req(provided.baseGorgel()).getChild(AuthDescFromPropertiesFactory::class, COMMUNICATION_CATEGORY_TAG) }

    val authDescFromPropertiesFactory by Once { AuthDescFromPropertiesFactory(req(provided.props()), req(authDescFromPropertiesFactoryGorgel)) }

    val hostDescFromPropertiesFactory by Once { HostDescFromPropertiesFactory(req(provided.props()), req(authDescFromPropertiesFactory))}
}
