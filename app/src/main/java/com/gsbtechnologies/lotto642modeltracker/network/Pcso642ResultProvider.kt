package com.gsbtechnologies.lotto642modeltracker.network

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.util.Locale

/** A validated Lotto 6/42 result read directly from the official PCSO website. */
data class Pcso642Result(
    val drawDate: String,
    val numbers: List<Int>
)

class Pcso642ResultProvider(
    private val endpoint: String = "https://www.pcso.gov.ph/SearchLottoResult.aspx"
) {
    fun fetchRecent(windowDays: Long = 21): List<Pcso642Result> {
        val today = LocalDate.now(ZoneId.of("Asia/Manila"))
        val from = today.minusDays(windowDays.coerceAtLeast(1))
        val initialHtml = request("GET")
        val state = Pcso642ResultParser.extractAspNetState(initialHtml)
        require(state.viewState.isNotBlank()) { "PCSO page did not provide __VIEWSTATE" }
        require(state.eventValidation.isNotBlank()) { "PCSO page did not provide __EVENTVALIDATION" }

        val form = linkedMapOf(
            "__EVENTTARGET" to "",
            "__EVENTARGUMENT" to "",
            "__VIEWSTATE" to state.viewState,
            "__VIEWSTATEGENERATOR" to state.viewStateGenerator,
            "__EVENTVALIDATION" to state.eventValidation,
            "ctl00$ctl00$cphContainer$cpContent$ddlStartMonth" to from.format(DateTimeFormatter.ofPattern("MMMM", Locale.US)),
            "ctl00$ctl00$cphContainer$cpContent$ddlStartDate" to from.dayOfMonth.toString(),
            "ctl00$ctl00$cphContainer$cpContent$ddlStartYear" to from.year.toString(),
            "ctl00$ctl00$cphContainer$cpContent$ddlEndDay" to today.dayOfMonth.toString(),
            "ctl00$ctl00$cphContainer$cpContent$ddlEndMonth" to today.format(DateTimeFormatter.ofPattern("MMMM", Locale.US)),
            "ctl00$ctl00$cphContainer$cpContent$ddlEndYear" to today.year.toString(),
            // 0 means all games. Filtering the returned table by the exact game name is safer
            // than depending on a numeric game id that PCSO can change.
            "ctl00$ctl00$cphContainer$cpContent$ddlSelectGame" to "0",
            "ctl00$ctl00$cphContainer$cpContent$btnSearch" to "Search Lotto"
        )
        val html = request("POST", encodeForm(form))
        return Pcso642ResultParser.parseResults(html)
            .filter { result ->
                runCatching { LocalDate.parse(result.drawDate) }
                    .getOrNull()
                    ?.let { !it.isBefore(from) && !it.isAfter(today) } == true
            }
            .distinctBy { it.drawDate }
            .sortedByDescending { it.drawDate }
    }

    private fun request(method: String, body: String? = null): String {
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 10_000
            readTimeout = 12_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/127.0 Mobile Safari/537.36")
            setRequestProperty("Accept", "text/html,application/xhtml+xml")
            setRequestProperty("Referer", endpoint)
            if (method == "POST") {
                doOutput = true
                setRequestProperty("Origin", "https://www.pcso.gov.ph")
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            }
        }
        try {
            if (body != null) {
                connection.outputStream.use { it.write(body.toByteArray(StandardCharsets.UTF_8)) }
            }
            val code = connection.responseCode
            if (code !in 200..299) throw IllegalStateException("PCSO returned HTTP $code")
            return connection.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun encodeForm(values: Map<String, String>): String = values.entries.joinToString("&") { (key, value) ->
        "${URLEncoder.encode(key, StandardCharsets.UTF_8.name())}=${URLEncoder.encode(value, StandardCharsets.UTF_8.name())}"
    }
}

internal data class AspNetState(
    val viewState: String,
    val viewStateGenerator: String,
    val eventValidation: String
)

internal object Pcso642ResultParser {
    private val inputTag = Regex("<input\\b[^>]*>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val attribute = Regex("([A-Za-z_:][A-Za-z0-9_:.-]*)\\s*=\\s*([\"'])(.*?)\\2", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val table = Regex("<table\\b[^>]*id\\s*=\\s*([\"'])cphContainer_cpContent_GridView1\\1[^>]*>(.*?)</table>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val row = Regex("<tr\\b[^>]*>(.*?)</tr>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val cell = Regex("<td\\b[^>]*>(.*?)</td>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val tag = Regex("<[^>]+>", setOf(RegexOption.DOT_MATCHES_ALL))
    private val lottoNumber = Regex("(?<!\\d)([1-9]|[1-3]\\d|4[0-2])(?!\\d)")

    private val dateFormats = listOf(
        "M/d/yyyy",
        "MM/dd/yyyy",
        "M-d-yyyy",
        "MM-dd-yyyy",
        "MMMM d, yyyy",
        "MMM d, yyyy"
    ).map { pattern ->
        DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .appendPattern(pattern)
            .toFormatter(Locale.US)
    }

    fun extractAspNetState(html: String): AspNetState {
        val values = inputTag.findAll(html).mapNotNull { match ->
            val attrs = attribute.findAll(match.value).associate { attr ->
                attr.groupValues[1].lowercase(Locale.US) to decodeHtml(attr.groupValues[3])
            }
            val id = (attrs["id"] ?: attrs["name"])?.uppercase(Locale.US) ?: return@mapNotNull null
            val value = attrs["value"] ?: ""
            id to value
        }.toMap()
        return AspNetState(
            viewState = values["__VIEWSTATE"].orEmpty(),
            viewStateGenerator = values["__VIEWSTATEGENERATOR"].orEmpty(),
            eventValidation = values["__EVENTVALIDATION"].orEmpty()
        )
    }

    fun parseResults(html: String): List<Pcso642Result> {
        val tableBody = table.find(html)?.groupValues?.getOrNull(2) ?: html
        return row.findAll(tableBody).mapNotNull { rowMatch ->
            val cells = cell.findAll(rowMatch.groupValues[1]).map { htmlToText(it.groupValues[1]) }.toList()
            if (cells.size < 3) return@mapNotNull null
            val game = cells[0].replace(" ", "").lowercase(Locale.US)
            if (game != "lotto6/42" && game != "6/42") return@mapNotNull null

            val numbers = lottoNumber.findAll(cells[1]).map { it.groupValues[1].toInt() }.toList()
            if (numbers.size != 6 || numbers.distinct().size != 6 || numbers.any { it !in 1..42 }) return@mapNotNull null

            val date = parseDate(cells[2]) ?: return@mapNotNull null
            Pcso642Result(date.toString(), numbers.sorted())
        }.toList()
    }

    private fun parseDate(value: String): LocalDate? = dateFormats.firstNotNullOfOrNull { formatter ->
        runCatching { LocalDate.parse(value.trim(), formatter) }.getOrNull()
    }

    private fun htmlToText(value: String): String = decodeHtml(tag.replace(value, " "))
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun decodeHtml(value: String): String = value
        .replace("&nbsp;", " ", ignoreCase = true)
        .replace("&amp;", "&", ignoreCase = true)
        .replace("&quot;", "\"", ignoreCase = true)
        .replace("&#39;", "'", ignoreCase = true)
        .replace("&#x27;", "'", ignoreCase = true)
        .replace("&lt;", "<", ignoreCase = true)
        .replace("&gt;", ">", ignoreCase = true)
}
