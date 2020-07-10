package org.elkoserver.server.context.test

import org.elkoserver.foundation.json.JsonMethod
import org.elkoserver.json.JsonLiteralFactory
import org.elkoserver.server.context.AdminObject
import org.elkoserver.server.context.InternalActor

internal class TestInternalObject @JsonMethod constructor() : AdminObject() {
    @JsonMethod("arg")
    fun boom(from: InternalActor, arg: String) {
        val response = JsonLiteralFactory.targetVerb(this, "bah").apply {
            addParameter("arg", arg)
            finish()
        }
        from.send(response)
    }

    @JsonMethod("arg")
    fun superboom(from: InternalActor, arg: String) {
        from.ensureAuthorized()
        val response = JsonLiteralFactory.targetVerb(this, "superbah").apply {
            addParameter("arg", arg)
            finish()
        }
        from.send(response)
    }
}
