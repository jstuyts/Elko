package org.elkoserver.server.context

internal abstract class CheckpointTask private constructor() {
    abstract fun execute(contextor: Contextor)
    private class DeleteTask internal constructor(private val myRef: String) : CheckpointTask() {
        override fun execute(contextor: Contextor) {
            contextor.writeObjectDelete(myRef)
        }
    }

    private class WriteTask internal constructor(private val myRef: String, private val myObj: BasicObject) : CheckpointTask() {
        override fun execute(contextor: Contextor) {
            contextor.writeObjectState(myRef, myObj)
        }
    }

    companion object {
        fun makeDeleteTask(ref: String): CheckpointTask = DeleteTask(ref)

        fun makeWriteTask(ref: String, obj: BasicObject): CheckpointTask = WriteTask(ref, obj)
    }
}
