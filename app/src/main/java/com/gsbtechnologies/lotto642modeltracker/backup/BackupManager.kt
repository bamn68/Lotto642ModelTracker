package com.gsbtechnologies.lotto642modeltracker.backup

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.gsbtechnologies.lotto642modeltracker.data.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BackupManager(private val context: Context, private val dao: LottoDao) {
    private fun sha(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray())
            .joinToString("") { "%02x".format(it) }

    suspend fun backupToUri(uri: Uri) {
        val payload = buildPayload().toString()
        val root = JSONObject()
            .put("format", "L642-BACKUP-1")
            .put("sha256", sha(payload))
            .put("payload", payload)
        context.contentResolver.openOutputStream(uri, "w")!!.bufferedWriter().use {
            it.write(root.toString())
        }
    }

    suspend fun backupToFile(file: File) {
        file.parentFile?.mkdirs()
        val payload = buildPayload().toString()
        file.writeText(
            JSONObject()
                .put("format", "L642-BACKUP-1")
                .put("sha256", sha(payload))
                .put("payload", payload)
                .toString()
        )
    }

    suspend fun autoBackupToTree(treeUri: String): String? {
        val tree = DocumentFile.fromTreeUri(context, Uri.parse(treeUri)) ?: return null
        val stamp = SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.US).format(Date())
        val name = "Lotto642_Backup_${stamp}.l642"
        val doc = tree.createFile("application/octet-stream", name) ?: return null
        backupToUri(doc.uri)
        return name
    }

    suspend fun restoreFromUri(uri: Uri) {
        val raw = context.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() }
        restoreRaw(raw)
    }

    suspend fun restoreRaw(raw: String) {
        val root = JSONObject(raw)
        require(root.getString("format") == "L642-BACKUP-1") { "Unsupported backup format" }
        val payload = root.getString("payload")
        require(sha(payload) == root.getString("sha256")) { "Backup checksum failed" }

        backupToFile(File(context.filesDir, "safety_backups/pre_restore_${System.currentTimeMillis()}.l642"))
        val p = JSONObject(payload)

        val baselines = p.getJSONArray("baseline").objects().map {
            BaselineFrequencyEntity(it.getInt("number"), it.getInt("count"), it.getInt("totalDraws"))
        }
        val draws = p.getJSONArray("draws").objects().map {
            DrawEntity(
                it.getLong("id"), it.getString("drawDate"), it.getString("numbersCsv"),
                it.getString("source"), it.getBoolean("verified"), it.getLong("createdAt")
            )
        }
        val runs = p.getJSONArray("runs").objects().map {
            ModelRunEntity(
                it.getLong("id"), it.getString("targetDrawDate"), it.getString("modelVersion"),
                it.getLong("generatedAt"), it.getString("dataCutoff"), it.getInt("requestedLines"),
                it.getDouble("ticketCost")
            )
        }
        val tickets = p.getJSONArray("tickets").objects().map {
            TicketEntity(
                it.getLong("id"), it.getLong("modelRunId"), it.getInt("lineNumber"),
                it.getString("numbersCsv"), it.getDouble("selectionScore"), it.getString("strategy"),
                it.getBoolean("bought"), if (it.isNull("lockedAt")) null else it.getLong("lockedAt"),
                it.getBoolean("isRandomControl")
            )
        }
        val matches = p.getJSONArray("matches").objects().map {
            MatchEntity(
                it.getLong("id"), it.getLong("ticketId"), it.getLong("drawId"),
                it.getInt("matchCount"), it.getString("matchedCsv"), it.getDouble("prizeAmount")
            )
        }

        dao.clearMatches()
        dao.clearTickets()
        dao.clearRuns()
        dao.clearDraws()
        dao.clearBaseline()
        dao.insertBaseline(baselines)
        dao.insertDraws(draws)

        val runMap = mutableMapOf<Long, Long>()
        runs.sortedBy { it.id }.forEach { old ->
            runMap[old.id] = dao.insertRun(old.copy(id = 0))
        }

        val ticketMap = mutableMapOf<Long, Long>()
        tickets.sortedBy { it.id }.forEach { old ->
            val newRun = runMap[old.modelRunId] ?: return@forEach
            ticketMap[old.id] = dao.insertTickets(listOf(old.copy(id = 0, modelRunId = newRun))).first()
        }

        val drawMap = dao.allDraws().associateBy { it.drawDate }
        val oldDraws = draws.associateBy { it.id }
        val rebuilt = matches.mapNotNull { old ->
            val ticketId = ticketMap[old.ticketId] ?: return@mapNotNull null
            val date = oldDraws[old.drawId]?.drawDate ?: return@mapNotNull null
            val drawId = drawMap[date]?.id ?: return@mapNotNull null
            old.copy(id = 0, ticketId = ticketId, drawId = drawId)
        }
        dao.insertMatches(rebuilt)
    }

    private suspend fun buildPayload(): JSONObject {
        fun baselineJson(x: BaselineFrequencyEntity) = JSONObject()
            .put("number", x.number).put("count", x.count).put("totalDraws", x.totalDraws)
        fun drawJson(x: DrawEntity) = JSONObject()
            .put("id", x.id).put("drawDate", x.drawDate).put("numbersCsv", x.numbersCsv)
            .put("source", x.source).put("verified", x.verified).put("createdAt", x.createdAt)
        fun runJson(x: ModelRunEntity) = JSONObject()
            .put("id", x.id).put("targetDrawDate", x.targetDrawDate).put("modelVersion", x.modelVersion)
            .put("generatedAt", x.generatedAt).put("dataCutoff", x.dataCutoff)
            .put("requestedLines", x.requestedLines).put("ticketCost", x.ticketCost)
        fun ticketJson(x: TicketEntity) = JSONObject()
            .put("id", x.id).put("modelRunId", x.modelRunId).put("lineNumber", x.lineNumber)
            .put("numbersCsv", x.numbersCsv).put("selectionScore", x.selectionScore)
            .put("strategy", x.strategy).put("bought", x.bought)
            .put("lockedAt", x.lockedAt ?: JSONObject.NULL).put("isRandomControl", x.isRandomControl)
        fun matchJson(x: MatchEntity) = JSONObject()
            .put("id", x.id).put("ticketId", x.ticketId).put("drawId", x.drawId)
            .put("matchCount", x.matchCount).put("matchedCsv", x.matchedCsv)
            .put("prizeAmount", x.prizeAmount)

        return JSONObject()
            .put("createdAt", System.currentTimeMillis())
            .put("appVersion", "1.0.0")
            .put("baseline", JSONArray(dao.allBaseline().map(::baselineJson)))
            .put("draws", JSONArray(dao.allDraws().map(::drawJson)))
            .put("runs", JSONArray(dao.allRuns().map(::runJson)))
            .put("tickets", JSONArray(dao.allTickets().map(::ticketJson)))
            .put("matches", JSONArray(dao.allMatches().map(::matchJson)))
    }

    private fun JSONArray.objects(): List<JSONObject> =
        (0 until length()).map { index -> getJSONObject(index) }
}
