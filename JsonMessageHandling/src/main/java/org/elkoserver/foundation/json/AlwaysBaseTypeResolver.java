package org.elkoserver.foundation.json;

public class AlwaysBaseTypeResolver implements TypeResolver {
    public static final AlwaysBaseTypeResolver theAlwaysBaseTypeResolver =
        new AlwaysBaseTypeResolver();

    private AlwaysBaseTypeResolver() {
    }

    public Class<?> resolveType(Class<?> baseType, String typeName) {
        return baseType;
    }
}
