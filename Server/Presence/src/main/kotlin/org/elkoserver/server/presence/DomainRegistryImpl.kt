package org.elkoserver.server.presence

internal class DomainRegistryImpl : DomainRegistry {
    private var theNextIndex = 0

    private val theDomains = ArrayList<Domain>()

    override fun add(domain: Domain): Int {
        val result = theNextIndex++
        theDomains.add(result, domain)
        return result
    }

    override fun domain(index: Int) = theDomains[index]

    override fun maxIndex() = theNextIndex
}
