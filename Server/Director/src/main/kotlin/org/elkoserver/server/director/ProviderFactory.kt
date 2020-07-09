package org.elkoserver.server.director

import org.elkoserver.ordinalgeneration.OrdinalGenerator
import org.elkoserver.util.trace.slf4j.Gorgel

internal class ProviderFactory(
        private val director: Director,
        private val providerGorgel: Gorgel,
        private val ordinalGenerator: OrdinalGenerator) {
    fun create(actor: DirectorActor) =
            Provider(director, actor, providerGorgel, ordinalGenerator)
}
