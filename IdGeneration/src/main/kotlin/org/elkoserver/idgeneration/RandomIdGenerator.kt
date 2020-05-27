package org.elkoserver.idgeneration

import java.util.Random

class RandomIdGenerator(private var random: Random) : IdGenerator {
    override fun generate() = random.nextLong()
}
