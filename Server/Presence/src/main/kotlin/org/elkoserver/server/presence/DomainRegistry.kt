package org.elkoserver.server.presence

internal interface DomainRegistry {
    fun add(domain: Domain): Int

    fun domain(index: Int): Domain

    fun maxIndex(): Int
}
