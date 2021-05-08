package org.elkoserver.objectdatabase.store.filestore

import org.elkoserver.foundation.properties.ElkoProperties
import org.elkoserver.json.EncodeControl
import org.elkoserver.objectdatabase.store.ObjectDesc
import org.elkoserver.objectdatabase.store.ObjectStoreArguments
import org.elkoserver.objectdatabase.store.RequestDesc
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class FileObjectStoreGetTest {
    private lateinit var temporaryDirectory: File

    @BeforeEach
    fun createTemporaryDirectory() {
        temporaryDirectory = File.createTempFile("test", null).apply {
            delete()
            mkdirs()
            deleteOnExit()
        }
    }

    @AfterEach
    fun deleteTemporaryDirectory() {
        temporaryDirectory.deleteRecursively()
    }

    @Test
    fun nonExistingResultsInNotFound() {
        val store = createStore(NotImplementedFileOperations)

        store.getObjects(arrayOf(request("does_not_exist"))) { results ->
            val expected = arrayOf(notFound("does_not_exist")).toComparableStrings()
            assertEquals(expected, results.toComparableStrings())
        }
    }

    @Test
    fun readErrorResultsInErrorMessage() {
        val store = createStore(ReadErrorFileOperations)
        createObjectFile("1")

        store.getObjects(arrayOf(request("1"))) { results ->
            val expected = arrayOf(readError("1")).toComparableStrings()
            assertEquals(expected, results.toComparableStrings())
        }
    }

    @Test
    fun existingResultsInObject() {
        val store = createStore(RealFileOperations)
        createObjectFile("1")

        store.getObjects(arrayOf(request("1"))) { results ->
            val expected = arrayOf(success("1", OBJECT_FILE_CONTENTS)).toComparableStrings()
            assertEquals(expected, results.toComparableStrings())
        }
    }

    @Test
    fun nonExistingAndExistingResultsInNotFoundAndObject() {
        val store = createStore(RealFileOperations)
        createObjectFile("2")

        store.getObjects(arrayOf(
                request("1"),
                request("2"))) { results ->
            val expected = arrayOf(
                    notFound("1"),
                    success("2", OBJECT_FILE_CONTENTS)
            ).toComparableStrings()
            assertEquals(expected, results.toComparableStrings())
        }
    }

    @Test
    fun existingAndNonExistingResultsInObjectAndNotFound() {
        val store = createStore(RealFileOperations)
        createObjectFile("1")

        store.getObjects(arrayOf(
                request("1"),
                request("2"))) { results ->
            val expected = arrayOf(
                    success("1", OBJECT_FILE_CONTENTS),
                    notFound("2")
            ).toComparableStrings()
            assertEquals(expected, results.toComparableStrings())
        }
    }

    @Test
    fun nonExistingAndNonExistingResultsInONotFoundAndNotFound() {
        val store = createStore(RealFileOperations)

        store.getObjects(arrayOf(
                request("1"),
                request("2"))) { results ->
            val expected = arrayOf(
                    notFound("1"),
                    notFound("2")
            ).toComparableStrings()
            assertEquals(expected, results.toComparableStrings())
        }
    }

    @Test
    fun readErrorAndExistingResultsInErrorMessageAndObject() {
        val store = createStore(MultipleFileOperations(ReadErrorFileOperations, RealFileOperations))
        createObjectFile("1")
        createObjectFile("2")

        store.getObjects(arrayOf(
                request("1"),
                request("2"))) { results ->
            val expected = arrayOf(
                    readError("1"),
                    success("2", OBJECT_FILE_CONTENTS)
            ).toComparableStrings()
            assertEquals(expected, results.toComparableStrings())
        }
    }

    @Test
    fun existingAndReadErrorResultsInObjectAndErrorMessage() {
        val store = createStore(MultipleFileOperations(RealFileOperations, ReadErrorFileOperations))
        createObjectFile("1")
        createObjectFile("2")

        store.getObjects(arrayOf(
                request("1"),
                request("2"))) { results ->
            val expected = arrayOf(
                    success("1", OBJECT_FILE_CONTENTS),
                    readError("2")
            ).toComparableStrings()
            assertEquals(expected, results.toComparableStrings())
        }
    }

    @Test
    fun readErrorAndReadErrorResultsInErrorMessageAndErrorMessage() {
        val store = createStore(ReadErrorFileOperations)
        createObjectFile("1")
        createObjectFile("2")

        store.getObjects(arrayOf(
                request("1"),
                request("2"))) { results ->
            val expected = arrayOf(
                    readError("1"),
                    readError("2")
            ).toComparableStrings()
            assertEquals(expected, results.toComparableStrings())
        }
    }

    @Test
    fun existingAndExistingResultsInObjectAndObject() {
        val store = createStore(RealFileOperations)
        createObjectFile("1")
        createObjectFile("2")

        store.getObjects(arrayOf(
                request("1"),
                request("2"))) { results ->
            val expected = arrayOf(
                    success("1", OBJECT_FILE_CONTENTS),
                    success("2", OBJECT_FILE_CONTENTS)
            ).toComparableStrings()
            assertEquals(expected, results.toComparableStrings())
        }
    }

    @Test
    fun withPropertyRefToNonExistingResultsInObjectAndNotFound() {
        val store = createStore(RealFileOperations)
        val oneContents = objectWithPropertyRef("2")
        createObjectFile("1", oneContents)

        store.getObjects(arrayOf(request("1"))) { results ->
            val expected = arrayOf(
                    success("1", oneContents),
                    notFound("2")
            ).toComparableStrings()
            assertEquals(expected, results.toComparableStrings())
        }
    }

    @Test
    fun withPropertyRefToReadErrorResultsInObjectAndErrorMessage() {
        val store = createStore(MultipleFileOperations(RealFileOperations, ReadErrorFileOperations))
        val oneContents = objectWithPropertyRef("2")
        createObjectFile("1", oneContents)
        createObjectFile("2")

        store.getObjects(arrayOf(request("1"))) { results ->
            val expected = arrayOf(
                    success("1", oneContents),
                    readError("2")
            ).toComparableStrings()
            assertEquals(expected, results.toComparableStrings())
        }
    }

    @Test
    fun withPropertyRefResultsInObjectAndObject() {
        val store = createStore(RealFileOperations)
        val oneContents = objectWithPropertyRef("2")
        createObjectFile("1", oneContents)
        createObjectFile("2")

        store.getObjects(arrayOf(request("1"))) { results ->
            val expected = arrayOf(
                    success("1", oneContents),
                    success("2", OBJECT_FILE_CONTENTS)
            ).toComparableStrings()
            assertEquals(expected, results.toComparableStrings())
        }
    }

    @Test
    fun withPropertyRefToPropertyRefResultsInObjectAndObjectAndObject() {
        val store = createStore(RealFileOperations)
        val oneContents = objectWithPropertyRef("2")
        val twoContents = objectWithPropertyRef("3")
        createObjectFile("1", oneContents)
        createObjectFile("2", twoContents)
        createObjectFile("3")

        store.getObjects(arrayOf(request("1"))) { results ->
            val expected = arrayOf(
                    success("1", oneContents),
                    success("2", twoContents),
                    success("3", OBJECT_FILE_CONTENTS)
            ).toComparableStrings()
            assertEquals(expected, results.toComparableStrings())
        }
    }

    @Test
    fun withNullPropertyRefResultsInObject() {
        val store = createStore(RealFileOperations)
        val oneContents = objectWithNullPropertyRef()
        createObjectFile("1", oneContents)

        store.getObjects(arrayOf(request("1"))) { results ->
            val expected = arrayOf(success("1", oneContents)).toComparableStrings()
            assertEquals(expected, results.toComparableStrings())
        }
    }

    @Test
    fun withNonStringPropertyRefResultsInObject() {
        val store = createStore(RealFileOperations)
        val oneContents = objectWithNonStringPropertyRef()
        createObjectFile("1", oneContents)

        store.getObjects(arrayOf(request("1"))) { results ->
            val expected = arrayOf(success("1", oneContents)).toComparableStrings()
            assertEquals(expected, results.toComparableStrings())
        }
    }

    @Test
    fun withArrayElementRefToNonExistingResultsInObjectAndNotFound() {
        val store = createStore(RealFileOperations)
        val oneContents = objectWithArrayElementRef("2")
        createObjectFile("1", oneContents)

        store.getObjects(arrayOf(request("1"))) { results ->
            val expected = arrayOf(
                    success("1", oneContents),
                    notFound("2")
            ).toComparableStrings()
            assertEquals(expected, results.toComparableStrings())
        }
    }

    @Test
    fun withArrayElementRefToReadErrorResultsInObjectAndErrorMessage() {
        val store = createStore(MultipleFileOperations(RealFileOperations, ReadErrorFileOperations))
        val oneContents = objectWithArrayElementRef("2")
        createObjectFile("1", oneContents)
        createObjectFile("2")

        store.getObjects(arrayOf(request("1"))) { results ->
            val expected = arrayOf(
                    success("1", oneContents),
                    readError("2")
            ).toComparableStrings()
            assertEquals(expected, results.toComparableStrings())
        }
    }

    @Test
    fun withArrayElementRefResultsInObjectAndObject() {
        val store = createStore(RealFileOperations)
        val oneContents = objectWithArrayElementRef("2")
        createObjectFile("1", oneContents)
        createObjectFile("2")

        store.getObjects(arrayOf(request("1"))) { results ->
            val expected = arrayOf(
                    success("1", oneContents),
                    success("2", OBJECT_FILE_CONTENTS)
            ).toComparableStrings()
            assertEquals(expected, results.toComparableStrings())
        }
    }

    @Test
    fun withArrayElementRefToArrayElementRefResultsInObjectAndObjectAndObject() {
        val store = createStore(RealFileOperations)
        val oneContents = objectWithArrayElementRef("2")
        val twoContents = objectWithArrayElementRef("3")
        createObjectFile("1", oneContents)
        createObjectFile("2", twoContents)
        createObjectFile("3")

        store.getObjects(arrayOf(request("1"))) { results ->
            val expected = arrayOf(
                    success("1", oneContents),
                    success("2", twoContents),
                    success("3", OBJECT_FILE_CONTENTS)
            ).toComparableStrings()
            assertEquals(expected, results.toComparableStrings())
        }
    }

    @Test
    fun withEmptyArrayElementRefResultsInObject() {
        val store = createStore(RealFileOperations)
        val oneContents = objectWithEmptyArrayElementRef()
        createObjectFile("1", oneContents)

        store.getObjects(arrayOf(request("1"))) { results ->
            val expected = arrayOf(success("1", oneContents)).toComparableStrings()
            assertEquals(expected, results.toComparableStrings())
        }
    }

    @Test
    fun withNullArrayElementRefResultsInObject() {
        val store = createStore(RealFileOperations)
        val oneContents = objectWithNullArrayElementRef()
        createObjectFile("1", oneContents)

        store.getObjects(arrayOf(request("1"))) { results ->
            val expected = arrayOf(success("1", oneContents)).toComparableStrings()
            assertEquals(expected, results.toComparableStrings())
        }
    }

    @Test
    fun withNonStringArrayElementRefResultsInObject() {
        val store = createStore(RealFileOperations)
        val oneContents = objectWithNonStringArrayElementRef()
        createObjectFile("1", oneContents)

        store.getObjects(arrayOf(request("1"))) { results ->
            val expected = arrayOf(success("1", oneContents)).toComparableStrings()
            assertEquals(expected, results.toComparableStrings())
        }
    }

    private fun createStore(fileOperations: FileOperations) =
            FileObjectStore(ObjectStoreArguments(ElkoProperties(mapOf("test.odjdb" to temporaryDirectory.absolutePath)), "test", GORGEL), fileOperations)

    private fun createObjectFile(ref: String) {
        createObjectFile(ref, OBJECT_FILE_CONTENTS)
    }

    private fun createObjectFile(ref: String, contents: String) {
        File(temporaryDirectory, "$ref.json").writeText(contents)
    }

    private fun request(ref: String) = RequestDesc(ref, SOME_COLLECTION_NAME, SOME_BOOLEAN)

    private fun success(ref: String, obj: String) = ObjectDesc(ref, obj, null)

    private fun notFound(ref: String) = ObjectDesc(ref, null, "not found")

    private fun failure(ref: String, failure: String) = ObjectDesc(ref, null, failure)
}

private fun Array<ObjectDesc>.toComparableStrings() =
        map { it.encode(EncodeControl.ForClientEncodeControl).toString() }
