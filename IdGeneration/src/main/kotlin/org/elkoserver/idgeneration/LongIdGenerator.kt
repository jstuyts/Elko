package org.elkoserver.idgeneration

class LongIdGenerator(private var nextId: Long = 0L) : IdGenerator {
    override fun generate(): Long {
        val result = nextId

        nextId += 1

        return result
    }
}
