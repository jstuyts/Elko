package org.elkoserver.server.director

class LongOrdinalGenerator(private var nextOrdinal: Long = 0L) : OrdinalGenerator {
    override fun generate(): Long {
        val result = nextOrdinal

        nextOrdinal += 1

        return result
    }
}
