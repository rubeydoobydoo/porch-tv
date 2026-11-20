package tv.porch.app.domain

import org.junit.Assert.assertTrue
import org.junit.Test

class FilmSearchTest {
    @Test
    fun aMisspeltTitleIsFoundAmongTwoHundredThousandInUnderASecond() {
        val words = listOf("Silver", "Garden", "Winter", "Copper", "Island", "Summit", "Marble", "Falcon")
        val names = ArrayList<String>(200_001)
        for (i in 0 until 200_000) names.add("${words[i % 8]} ${words[(i / 8) % 8]} ${i}")
        names.add("Harbour Lights")
        val start = System.nanoTime()
        val found = FilmSearch.find(names, "harbor lihgts")
        val ms = (System.nanoTime() - start) / 1_000_000
        assertTrue(found.contains(200_000))
        assertTrue("took $ms ms", ms < 1000)
    }
}
