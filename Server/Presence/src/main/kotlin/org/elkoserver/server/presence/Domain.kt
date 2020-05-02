package org.elkoserver.server.presence

internal class Domain(private val myName: String) {
    private val myIndex: Int
    private val mySubscribers: MutableMap<String, PresenceActor> = HashMap()
    fun index() = myIndex

    fun name() = myName

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
        private var theNextIndex = 0
        private val theDomains = ArrayList<Domain>()

        fun domain(index: Int) = theDomains[index]

        fun maxIndex() = theNextIndex
    }

    init {
        myIndex = theNextIndex++
        theDomains.add(myIndex, this)
    }
}
