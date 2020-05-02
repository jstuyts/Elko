package org.elkoserver.json

object JsonWrapping {
    fun <TObject: Any?> wrapWithElkoJsonImplementationIfNeeded(`object`: TObject): TObject =
            if (`object` is com.grack.nanojson.JsonObject) {
                JsonObject(`object`) as TObject
            } else if (`object` is com.grack.nanojson.JsonArray) {
                JsonArray(`object`) as TObject
            } else {
                `object`
            }
}
