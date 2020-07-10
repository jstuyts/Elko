package org.elkoserver.server.director

import org.elkoserver.json.JsonLiteralFactory
import org.elkoserver.json.Referenceable

/**
 * Generate a 'dump' message.
 */
internal fun msgDump(target: Referenceable, numProviders: Int, numContexts: Int, numUsers: Int, providerList: List<AdminHandler.ProviderDump>) =
        JsonLiteralFactory.targetVerb(target, "dump").apply {
            addParameter("numproviders", numProviders)
            addParameter("numcontexts", numContexts)
            addParameter("numusers", numUsers)
            if (providerList.isNotEmpty()) {
                addParameter("providers", encodeEncodableList(providerList))
            }
            finish()
        }
