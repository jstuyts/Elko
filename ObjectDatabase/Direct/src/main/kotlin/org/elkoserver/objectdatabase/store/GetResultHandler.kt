package org.elkoserver.objectdatabase.store

/**
 * Interface for an [ObjectStore] object to deliver the results of
 * servicing a 'get' request.
 */
fun interface GetResultHandler {
    /**
     * Receive the results of a 'get' request.
     *
     * @param results  Results of the get.  The elements in the array may not
     * necessarily correspond one-to-one with the elements of the
     * 'what' parameter of the [ObjectStore.getObjects()][ObjectStore.getObjects]
     * call, since the number of objects returned may vary depending on the
     * number of contained objects.
     */
    fun handle(results: Array<ObjectDesc>)
}
