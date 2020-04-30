package org.elkoserver.server.workshop.test

import org.elkoserver.foundation.json.JSONMethod
import org.elkoserver.foundation.json.OptString
import org.elkoserver.json.JSONLiteralFactory
import org.elkoserver.server.workshop.WorkerObject
import org.elkoserver.server.workshop.WorkshopActor

internal class EchoWorker @JSONMethod("prefix", "service") constructor(prefix: OptString, serviceName: OptString) : WorkerObject(serviceName.value("echo")) {
    private val myPrefix = prefix.value("you said: ")

    @JSONMethod("rep", "text")
    fun echo(from: WorkshopActor, rep: OptString, text: OptString) {
        from.ensureAuthorizedClient()
        val response = JSONLiteralFactory.targetVerb(rep.value(ref()), "echo").apply {
            addParameter("text", myPrefix + text.value("<nothing>"))
            finish()
        }
        from.send(response)
    }
}
