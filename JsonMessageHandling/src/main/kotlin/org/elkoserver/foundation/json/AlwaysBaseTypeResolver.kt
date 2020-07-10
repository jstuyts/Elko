package org.elkoserver.foundation.json

object AlwaysBaseTypeResolver : TypeResolver {
    override fun resolveType(baseType: Class<*>, typeName: String): Class<*> = baseType
}
