package com.gsbtechnologies.lotto642modeltracker.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Pcso642ResultParserTest {
    @Test
    fun `extracts ASP NET state regardless of attribute order`() {
        val html = """
            <html><body>
              <input value="abc&amp;123" type="hidden" id="__VIEWSTATE" />
              <input type="hidden" value="GEN" name="__VIEWSTATEGENERATOR" id="__VIEWSTATEGENERATOR" />
              <input id="__EVENTVALIDATION" value="EV123" type="hidden" />
            </body></html>
        """.trimIndent()

        val state = Pcso642ResultParser.extractAspNetState(html)

        assertEquals("abc&123", state.viewState)
        assertEquals("GEN", state.viewStateGenerator)
        assertEquals("EV123", state.eventValidation)
    }

    @Test
    fun `parses only valid Lotto 6 42 rows`() {
        val html = """
            <table id="cphContainer_cpContent_GridView1">
              <tbody>
                <tr><th>Game</th><th>Combination</th><th>Draw Date</th><th>Jackpot</th><th>Winners</th></tr>
                <tr><td>Lotto 6/42</td><td>42-05-17-21-11-33</td><td>9/14/2026</td><td>10,000,000.00</td><td>0</td></tr>
                <tr><td>Mega Lotto 6/45</td><td>01-02-03-04-05-06</td><td>9/14/2026</td><td>20,000,000.00</td><td>0</td></tr>
                <tr><td>Lotto 6/42</td><td>01-01-02-03-04-05</td><td>9/12/2026</td><td>10,000,000.00</td><td>0</td></tr>
              </tbody>
            </table>
        """.trimIndent()

        val results = Pcso642ResultParser.parseResults(html)

        assertEquals(1, results.size)
        assertEquals("2026-09-14", results.single().drawDate)
        assertEquals(listOf(5, 11, 17, 21, 33, 42), results.single().numbers)
    }

    @Test
    fun `accepts long month draw date and compact game label`() {
        val html = """
            <table id="cphContainer_cpContent_GridView1">
              <tr><td>6/42</td><td>4 - 9 - 15 - 22 - 31 - 40</td><td>September 12, 2026</td><td>0</td><td>0</td></tr>
            </table>
        """.trimIndent()

        val result = Pcso642ResultParser.parseResults(html).single()

        assertEquals("2026-09-12", result.drawDate)
        assertEquals(listOf(4, 9, 15, 22, 31, 40), result.numbers)
        assertTrue(result.numbers.all { it in 1..42 })
    }
}
