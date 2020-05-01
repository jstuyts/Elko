package org.elkoserver.server.context

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

@ExperimentalContracts
fun validContainer(into: BasicObject?, who: User): Boolean {
    contract {
        returns(true) implies (into != null)
    }

    return when {
        into == null -> false
        into.context() !== who.context() -> false
        into is Context -> true
        else -> (into as? Item)?.isContainer ?: false
    }
}