package org.elkoserver.json

object JsonWrapping {
    fun <TObject : Any?> wrapWithElkoJsonImplementationIfNeeded(`object`: TObject): TObject =
            when (`object`) {
                is com.grack.nanojson.JsonObject -> JsonObject(`object`) as TObject
                is com.grack.nanojson.JsonArray -> JsonArray(`object`) as TObject
                else -> `object`
            }
}
