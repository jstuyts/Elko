package org.elkoserver.server.workshop.test

import org.elkoserver.foundation.json.JsonMethod
import org.elkoserver.foundation.json.OptString
import org.elkoserver.json.JsonLiteralFactory
import org.elkoserver.server.workshop.WorkerObject
import org.elkoserver.server.workshop.WorkshopActor

internal class EchoWorker @JsonMethod("prefix", "service") constructor(prefix: OptString, serviceName: OptString) : WorkerObject(serviceName.value("echo")) {
    private val myPrefix = prefix.value("you said: ")

    @JsonMethod("rep", "text")
    fun echo(from: WorkshopActor, rep: OptString, text: OptString) {
        from.ensureAuthorizedClient()
        val response = JsonLiteralFactory.targetVerb(rep.value(ref()), "echo").apply {
            addParameter("text", myPrefix + text.value("<nothing>"))
            finish()
        }
        from.send(response)
    }
}
