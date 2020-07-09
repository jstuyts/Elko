package org.elkoserver.server.director

internal class AdminFactory(
        private val director: Director) {
    fun create(actor: DirectorActor) =
            Admin(director, actor)
}
