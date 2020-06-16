package org.elkoserver.foundation.json

import java.util.Random

// FIXME: Different classes (objects even?) may need different randoms.
// FIXME: All instances of all classes will get the same random, but it is not thread-safe. Allow for random per class and/or object.
interface RandomUsingObject {
    fun setRandom(random: Random)
}