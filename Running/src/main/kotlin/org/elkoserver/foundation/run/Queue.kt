package org.elkoserver.foundation.run

import java.util.Enumeration
import java.util.NoSuchElementException

/**
 * A conventional fifo queue in which dequeued items are removed in the same
 * order they were enqueued.  An untyped queue can hold any object (except
 * null).  A queue can be created with a dynamic type, in which case, at no
 * extra overhead, enqueue will only enqueue objects of that type (or a
 * subtype, but not null).  This check imposes no extra overhead, since Java
 * always makes us pay for a dynamic type check on array store anyway.
 *
 * Queue is a thread-safe data structure, providing its own lock, and a
 * blocking [.dequeue] operation.
 */
class Queue<TElement> : Enumeration<TElement> {
    private val myQLock = Object()
    private var myStuff = arrayOfNulls<Any>(INITIAL_SIZE)
    private var myMaxSize = INITIAL_SIZE
    private var myCurSize = 0
    private var myOut = 0
    private var myIn = 0

    /**
     * Get the least-recently-added element off of the queue.  If the queue
     * is currently empty, block until there is an element that can be
     * dequeued.
     */
    fun dequeue(): TElement {
        synchronized(myQLock) {
            while (true) {
                val result = optDequeue()
                if (result != null) {
                    return result
                }
                try {
                    myQLock.wait()
                } catch (ie: InterruptedException) {
                    /* Ignored on purpose, but we do recheck the queue rather 
                       than just waiting again. */
                }
            }
        }
    }

    /**
     * Add a new element to the queue.
     *
     * @param newElement the object to be added to the end of the queue.
     *
     * @throws NullPointerException thrown if newElement is null
     * @throws ArrayStoreException thrown if newElement does not conform
     * to the elementType specified in the Queue constructor.
     */
    fun enqueue(newElement: TElement?) {
        if (newElement == null) {
            throw NullPointerException("cannot enqueue a null")
        }
        synchronized(myQLock) {

            /* grow array if necessary */
            if (myCurSize == myMaxSize) {
            val newSize = myMaxSize * 3 / 2 + 10
            val elementType = myStuff.javaClass.componentType
            val stuff = java.lang.reflect.Array.newInstance(elementType, newSize) as Array<TElement?>

            /* note: careful code to avoid inadvertantly reordering msgs */
            System.arraycopy(myStuff, myOut, stuff, 0, myMaxSize - myOut)
            if (myOut != 0) {
                System.arraycopy(myStuff, 0, stuff, myMaxSize - myOut,
                        myOut)
            }
            myOut = 0
            myIn = myMaxSize
            myStuff = stuff as Array<Any?>
            myMaxSize = newSize
        }
            /* Will throw ArrayStoreException if newElement's type doesn't 
               conform to elementType */
            myStuff[myIn] = newElement
            ++myIn
            if (myIn == myMaxSize) {
                myIn = 0
            }
            ++myCurSize
            myQLock.notifyAll()
        }
    }

    /**
     * Check to see if the queue has more elements.  This method
     * allows a Queue to be used as an Enumeration.
     *
     * @return is false if the queue is empty, otherwise true
     */
    override fun hasMoreElements() = myCurSize != 0

    /**
     * Get the least-recently-added element off of the queue.  If the queue
     * is currently empty, throw NoSuchElementException.  This method
     * allows a Queue to be used as an Enumeration.
     */
    @Throws(NoSuchElementException::class)
    override fun nextElement() = optDequeue() ?: throw NoSuchElementException("queue is currently empty")

    /**
     * Get the least-recently-added element off of the queue, or null
     * if the queue is currently empty.
     */
    fun optDequeue(): TElement? {
        if (myCurSize == 0) {
            return null
        }
        synchronized(myQLock) {
            val result = myStuff[myOut] as TElement?
            myStuff[myOut] = null
            ++myOut
            if (myOut == myMaxSize) {
                myOut = 0
            }
            --myCurSize
            return result
        }
    }

    companion object {
        private const val INITIAL_SIZE = 400
    }
}
