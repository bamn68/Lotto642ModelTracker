package com.gsbtechnologies.lotto642modeltracker.data

import androidx.room.*

@Dao
interface LottoDao {
    @Query("SELECT * FROM draws ORDER BY drawDate DESC") suspend fun allDraws(): List<DrawEntity>
    @Query("SELECT * FROM baseline_frequency ORDER BY number") suspend fun allBaseline(): List<BaselineFrequencyEntity>
    @Query("SELECT * FROM model_runs ORDER BY generatedAt DESC") suspend fun allRuns(): List<ModelRunEntity>
    @Query("SELECT * FROM tickets ORDER BY id DESC") suspend fun allTickets(): List<TicketEntity>
    @Query("SELECT * FROM matches ORDER BY id DESC") suspend fun allMatches(): List<MatchEntity>

    @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun insertDraw(draw: DrawEntity): Long
    @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun insertDraws(draws: List<DrawEntity>)
    @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun insertBaseline(rows: List<BaselineFrequencyEntity>)
    @Insert suspend fun insertRun(run: ModelRunEntity): Long
    @Insert suspend fun insertTickets(rows: List<TicketEntity>): List<Long>
    @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun insertMatches(rows: List<MatchEntity>)

    @Query("SELECT * FROM model_runs WHERE targetDrawDate=:date") suspend fun runsForDate(date: String): List<ModelRunEntity>
    @Query("SELECT * FROM tickets WHERE modelRunId=:runId") suspend fun ticketsForRun(runId: Long): List<TicketEntity>
    @Query("SELECT * FROM draws WHERE drawDate=:date LIMIT 1") suspend fun drawForDate(date: String): DrawEntity?

    @Query("UPDATE tickets SET bought=:bought, lockedAt=CASE WHEN :bought=1 THEN COALESCE(lockedAt,:now) ELSE NULL END WHERE id=:ticketId AND isRandomControl=0")
    suspend fun setBought(ticketId: Long, bought: Boolean, now: Long)

    @Query("DELETE FROM matches") suspend fun clearMatches()
    @Query("DELETE FROM tickets") suspend fun clearTickets()
    @Query("DELETE FROM model_runs") suspend fun clearRuns()
    @Query("DELETE FROM draws") suspend fun clearDraws()
    @Query("DELETE FROM baseline_frequency") suspend fun clearBaseline()
}
