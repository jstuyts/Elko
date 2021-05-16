package org.elkoserver.server.context.model

import org.elkoserver.foundation.json.DispatchTarget
import org.elkoserver.json.Referenceable

interface RefTableProtocol {
    fun addRef(target: Referenceable)

    fun addClass(targetClass: Class<in Mod>)

    operator fun get(ref: String): DispatchTarget?
}
