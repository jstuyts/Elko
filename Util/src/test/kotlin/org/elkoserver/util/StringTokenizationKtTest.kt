package org.elkoserver.util

import java.util.StringTokenizer
import kotlin.test.Test
import kotlin.test.assertEquals

class StringTokenizationKtTest {
    @Test
    fun emptyStringResultsAreTheSame() {
        val text = ""

        val stringTokenizerResult = tokenize(text)
        val splitBasedTokenizeResult = text.tokenize(*DELIMITERS)

        assertEquals(stringTokenizerResult, splitBasedTokenizeResult)
    }

    @Test
    fun spaceResultsAreTheSame() {
        val text = " "

        val stringTokenizerResult = tokenize(text)
        val splitBasedTokenizeResult = text.tokenize(*DELIMITERS)

        assertEquals(stringTokenizerResult, splitBasedTokenizeResult)
    }

    @Test
    fun wordResultsAreTheSame() {
        val text = "someWord"

        val stringTokenizerResult = tokenize(text)
        val splitBasedTokenizeResult = text.tokenize(*DELIMITERS)

        assertEquals(stringTokenizerResult, splitBasedTokenizeResult)
    }

    @Test
    fun wordsResultsAreTheSame() {
        val text = "some words"

        val stringTokenizerResult = tokenize(text)
        val splitBasedTokenizeResult = text.tokenize(*DELIMITERS)

        assertEquals(stringTokenizerResult, splitBasedTokenizeResult)
    }

    @Test
    fun wordsSurroundedByWhitespaceResultsAreTheSame() {
        val text = " some words "

        val stringTokenizerResult = tokenize(text)
        val splitBasedTokenizeResult = text.tokenize(*DELIMITERS)

        assertEquals(stringTokenizerResult, splitBasedTokenizeResult)
    }

    @Test
    fun wordsDelimitedByFirstDelimiterResultsAreTheSame() {
        val text = "some${FIRST_DELIMITER}words"

        val stringTokenizerResult = tokenize(text)
        val splitBasedTokenizeResult = text.tokenize(*DELIMITERS)

        assertEquals(stringTokenizerResult, splitBasedTokenizeResult)
    }

    @Test
    fun wordsSurroundedByWhitespaceAndDelimitedByFirstDelimiterResultsAreTheSame() {
        val text = " some $FIRST_DELIMITER words "

        val stringTokenizerResult = tokenize(text)
        val splitBasedTokenizeResult = text.tokenize(*DELIMITERS)

        assertEquals(stringTokenizerResult, splitBasedTokenizeResult)
    }

    @Test
    fun wordsDelimitedBySecondDelimiterResultsAreTheSame() {
        val text = "some${SECOND_DELIMITER}words"

        val stringTokenizerResult = tokenize(text)
        val splitBasedTokenizeResult = text.tokenize(*DELIMITERS)

        assertEquals(stringTokenizerResult, splitBasedTokenizeResult)
    }

    @Test
    fun wordsDelimitedByFirstAndSecondDelimitersResultsAreTheSame() {
        val text = "some${FIRST_DELIMITER}other${SECOND_DELIMITER}words"

        val stringTokenizerResult = tokenize(text)
        val splitBasedTokenizeResult = text.tokenize(*DELIMITERS)

        assertEquals(stringTokenizerResult, splitBasedTokenizeResult)
    }

    private fun tokenize(text: String): List<String> {
        val result = mutableListOf<String>()

        val stringTokenizer = StringTokenizer(text, DELIMITERS_AS_STRING)
        while (stringTokenizer.hasMoreTokens()) {
            result += stringTokenizer.nextToken()
        }

        return result
    }

    companion object {
        private const val FIRST_DELIMITER = ','
        private const val SECOND_DELIMITER = ';'

        private val DELIMITERS = charArrayOf(FIRST_DELIMITER, SECOND_DELIMITER)
        private val DELIMITERS_AS_STRING = String(DELIMITERS)
    }
}
