package org.elkoserver.server.context

internal abstract class CheckpointTask private constructor() {
    abstract fun execute(contextor: Contextor)
    internal class DeleteTask(private val myRef: String) : CheckpointTask() {
        override fun execute(contextor: Contextor) {
            contextor.writeObjectDelete(myRef)
        }
    }

    internal class WriteTask(private val myRef: String, private val myObj: BasicObject) : CheckpointTask() {
        override fun execute(contextor: Contextor) {
            contextor.writeObjectState(myRef, myObj)
        }
    }
}
