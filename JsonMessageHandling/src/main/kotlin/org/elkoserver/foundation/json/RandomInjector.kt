package org.elkoserver.foundation.json

import java.util.Random

class RandomInjector(private val random: Random) : Injector {
    override fun inject(any: Any?) {
        if (any is RandomUsingObject) {
            any.setRandom(random)
        }
    }
}
