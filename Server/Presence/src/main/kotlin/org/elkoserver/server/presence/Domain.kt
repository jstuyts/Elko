package org.elkoserver.server.presence

internal class Domain(internal val name: String) {
    internal val index: Int
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

    companion object {
        @Deprecated("Global variable")
        private var theNextIndex = 0

        @Deprecated("Global variable")
        private val theDomains = ArrayList<Domain>()

        fun domain(index: Int) = theDomains[index]

        fun maxIndex() = theNextIndex
    }

    init {
        index = theNextIndex++
        theDomains.add(index, this)
    }
}
