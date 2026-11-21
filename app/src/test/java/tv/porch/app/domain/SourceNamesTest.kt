package tv.porch.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class SourceNamesTest {
    @Test
    fun freeNameIsKept() {
        assertEquals("example.com", SourceNames.unique("example.com", listOf("other.example.com")))
    }

    @Test
    fun takenNameGetsTwoThenThree() {
        assertEquals("example.com 2", SourceNames.unique("example.com", listOf("example.com")))
        assertEquals("example.com 3", SourceNames.unique("example.com", listOf("example.com", "example.com 2")))
    }

    @Test
    fun typedNameIsKeptEvenWhenTaken() {
        assertEquals("Lounge", SourceNames.resolve("Lounge", "Lounge", listOf("Lounge")))
    }

    @Test
    fun untypedNameIsNumberedWhenTaken() {
        assertEquals("example.com 2", SourceNames.resolve("  ", "example.com", listOf("example.com")))
    }

    @Test
    fun caseDoesNotMatter() {
        assertEquals("Example.com 2", SourceNames.unique("Example.com", listOf("example.COM")))
    }
}
