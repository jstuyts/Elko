package org.elkoserver.server.context.test

import org.elkoserver.foundation.json.JSONMethod
import org.elkoserver.json.JSONLiteralFactory
import org.elkoserver.server.context.AdminObject
import org.elkoserver.server.context.InternalActor

internal class TestInternalObject @JSONMethod constructor() : AdminObject() {
    @JSONMethod("arg")
    fun boom(from: InternalActor, arg: String) {
        val response = JSONLiteralFactory.targetVerb(this, "bah").apply {
            addParameter("arg", arg)
            finish()
        }
        from.send(response)
    }

    @JSONMethod("arg")
    fun superboom(from: InternalActor, arg: String) {
        from.ensureAuthorized()
        val response = JSONLiteralFactory.targetVerb(this, "superbah").apply {
            addParameter("arg", arg)
            finish()
        }
        from.send(response)
    }
}
