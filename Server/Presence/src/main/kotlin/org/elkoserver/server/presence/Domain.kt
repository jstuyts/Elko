package org.elkoserver.server.presence

internal class Domain(internal val name: String, domainRegistry: DomainRegistry) {
    internal val index = domainRegistry.add(this)
    private val mySubscribers: MutableMap<String, PresenceActor> = HashMap()
    fun subscriber(context: String) = mySubscribers[context]

    fun addSubscriber(context: String, client: PresenceActor) {
        mySubscribers[context] = client
    }

    fun removeClient(client: PresenceActor) {
        mySubscribers.values.removeIf { subscriber: PresenceActor -> subscriber === client }
    }

    fun removeSubscriber(context: String) {
        mySubscribers.remove(context)
    }
}
