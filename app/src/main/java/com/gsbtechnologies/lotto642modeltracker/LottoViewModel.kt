package com.gsbtechnologies.lotto642modeltracker

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gsbtechnologies.lotto642modeltracker.backup.BackupManager
import com.gsbtechnologies.lotto642modeltracker.data.*
import com.gsbtechnologies.lotto642modeltracker.model.*
import com.gsbtechnologies.lotto642modeltracker.network.Pcso642ResultProvider
import com.gsbtechnologies.lotto642modeltracker.notifications.MajorWinNotifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class LottoState(
    val draws:List<DrawEntity> = emptyList(),
    val baseline:List<BaselineFrequencyEntity> = emptyList(),
    val runs:List<ModelRunEntity> = emptyList(),
    val tickets:List<TicketEntity> = emptyList(),
    val matches:List<MatchEntity> = emptyList(),
    val signals:List<NumberSignal> = emptyList(),
    val message:String? = null,
    val busy:Boolean=false
)

class LottoViewModel(app:Application):AndroidViewModel(app) {
    private val db=AppDatabase.get(app)
    private val dao=db.lottoDao()
    private val engine=RecommendationEngine()
    private val backup=BackupManager(app,dao)
    private val resultProvider=Pcso642ResultProvider()
    private val _state=MutableStateFlow(LottoState(busy=true))
    val state:StateFlow<LottoState> = _state
    private val prefs=app.getSharedPreferences("settings",Context.MODE_PRIVATE)

    init {
        viewModelScope.launch(Dispatchers.IO) {
            SeedData.ensureSeeded(dao)
            // Show saved/local data immediately. The online check happens after this refresh and
            // never blocks access to the app when PCSO or the network is unavailable.
            refresh()
            syncOfficialResults(startup=true)
        }
    }

    suspend fun refresh(message:String?=null) {
        val draws=dao.allDraws()
        val baseline=dao.allBaseline()
        _state.value=LottoState(draws,baseline,dao.allRuns(),dao.allTickets(),dao.allMatches(),engine.analyze(draws,baseline),message,false)
    }

    fun clearMessage(expected:String?=null) {
        val current=_state.value.message
        if(expected==null || current==expected) _state.value=_state.value.copy(message=null)
    }

    fun refreshLatestPcsoResult()=viewModelScope.launch(Dispatchers.IO) {
        syncOfficialResults(startup=false)
    }

    private suspend fun syncOfficialResults(startup:Boolean) {
        val fetched=runCatching { resultProvider.fetchRecent() }
        if(fetched.isFailure) {
            refresh(if(startup) "PCSO result check unavailable — showing saved results" else "Could not refresh PCSO results: ${fetched.exceptionOrNull()?.message ?: "network error"}")
            return
        }

        val results=fetched.getOrDefault(emptyList())
        if(results.isEmpty()) {
            refresh(if(startup) null else "PCSO check completed; no recent Lotto 6/42 result was returned")
            return
        }

        var inserted=0
        var upgraded=0
        var conflicts=0
        var outcomes=0
        var changed=false

        // Oldest first keeps scoring and backup/audit behavior deterministic when several draws
        // are caught up in one launch.
        for(result in results.sortedBy { it.drawDate }) {
            val csv=result.numbers.toCsv()
            val existing=dao.drawForDate(result.drawDate)

            // Never silently replace a result that the user already verified manually if the
            // official-page parser returns different numbers. Surface the conflict instead.
            if(existing?.verified==true && existing.numbersCsv!=csv) {
                conflicts++
                continue
            }
            if(existing?.verified==true && existing.numbersCsv==csv) continue

            val drawId=dao.insertDraw(
                DrawEntity(
                    id=existing?.id?:0,
                    drawDate=result.drawDate,
                    numbersCsv=csv,
                    source="PCSO official online",
                    verified=true,
                    createdAt=existing?.createdAt?:System.currentTimeMillis()
                )
            )
            val draw=dao.drawForDate(result.drawDate)
                ?: DrawEntity(drawId,result.drawDate,csv,"PCSO official online",true)

            // Only a newly accepted/verified result can trigger the loud Bought & Locked alert.
            // A later app launch sees the verified row and will not alert again for the same draw.
            outcomes += scoreDraw(draw, notifyMajorWins=true)
            if(existing==null) inserted++ else upgraded++
            changed=true
        }

        if(changed && prefs.getBoolean("auto_backup_after_draw",false)) {
            prefs.getString("backup_tree_uri",null)?.let { runCatching { backup.autoBackupToTree(it) } }
        }

        val message=when {
            conflicts>0 -> "PCSO sync updated ${inserted+upgraded} draw(s); $conflicts verified-result conflict(s) were preserved for review"
            inserted+upgraded>0 -> "PCSO sync added/verified ${inserted+upgraded} Lotto 6/42 draw(s) and scored $outcomes ticket outcomes"
            startup -> null
            else -> "Lotto 6/42 results are already up to date"
        }
        refresh(message)
    }

    fun generate(lines:Int) = viewModelScope.launch(Dispatchers.IO) {
        _state.value=_state.value.copy(busy=true,message=null)
        val draws=dao.allDraws()
        val baseline=dao.allBaseline()
        if(draws.isEmpty()) {
            refresh("No draw history is available")
            return@launch
        }
        val cutoff=draws.first().drawDate
        val target=engine.nextDrawDate(cutoff)
        val n=lines.coerceIn(1,100)
        val runId=dao.insertRun(ModelRunEntity(targetDrawDate=target,modelVersion=MODEL_VERSION,generatedAt=System.currentTimeMillis(),dataCutoff=cutoff,requestedLines=n))
        val model=engine.generatePortfolio(n,draws,baseline,target).mapIndexed { i,l ->
            TicketEntity(modelRunId=runId,lineNumber=i+1,numbersCsv=l.numbers.toCsv(),selectionScore=l.score,strategy=l.strategy)
        }
        val control=engine.randomControl(n,target).mapIndexed { i,l ->
            TicketEntity(modelRunId=runId,lineNumber=i+1,numbersCsv=l.numbers.toCsv(),selectionScore=0.0,strategy="Random control",isRandomControl=true)
        }
        dao.insertTickets(model+control)
        refresh("Generated $n model lines + $n random-control lines for $target")
    }

    fun setBought(ticketId:Long,bought:Boolean)=viewModelScope.launch(Dispatchers.IO){
        dao.setBought(ticketId,bought,System.currentTimeMillis())
        refresh(if(bought)"Ticket locked as bought" else "Ticket marked not bought")
    }

    fun saveVerifiedResult(date:String, nums:List<Int>)=viewModelScope.launch(Dispatchers.IO) {
        require(nums.size==6 && nums.distinct().size==6 && nums.all{it in 1..42})
        val existing=dao.drawForDate(date)
        dao.insertDraw(DrawEntity(id=existing?.id?:0,drawDate=date,numbersCsv=nums.toCsv(),source="Manual verified",verified=true,createdAt=existing?.createdAt?:System.currentTimeMillis()))
        val draw=dao.drawForDate(date) ?: return@launch
        val scored=scoreDraw(draw,notifyMajorWins=true)
        if(prefs.getBoolean("auto_backup_after_draw",false)) {
            prefs.getString("backup_tree_uri",null)?.let { runCatching { backup.autoBackupToTree(it) } }
        }
        refresh("Verified result saved and $scored ticket outcomes scored")
    }

    private suspend fun scoreDraw(draw:DrawEntity,notifyMajorWins:Boolean):Int {
        val nums=draw.numbersCsv.toNumbers()
        val runs=dao.runsForDate(draw.drawDate)
        val newMatches=mutableListOf<MatchEntity>()
        for(run in runs) for(ticket in dao.ticketsForRun(run.id)) {
            val matched=ticket.numbersCsv.toNumbers().intersect(nums.toSet()).sorted()
            newMatches += MatchEntity(ticketId=ticket.id,drawId=draw.id,matchCount=matched.size,matchedCsv=matched.toCsv())
            if(notifyMajorWins && ticket.bought && ticket.lockedAt!=null && matched.size>=5) {
                MajorWinNotifier.notifyMatch(getApplication(),matched.size,ticket.lineNumber,matched)
            }
        }
        if(newMatches.isNotEmpty()) dao.insertMatches(newMatches)
        return newMatches.size
    }

    fun backupTo(uri:Uri)=viewModelScope.launch(Dispatchers.IO){
        runCatching{backup.backupToUri(uri)}.onSuccess{refresh("Backup saved")}.onFailure{refresh("Backup failed: ${it.message}")}
    }
    fun restoreFrom(uri:Uri)=viewModelScope.launch(Dispatchers.IO){
        runCatching{backup.restoreFromUri(uri)}.onSuccess{refresh("Backup restored and checksum verified")}.onFailure{refresh("Restore blocked: ${it.message}")}
    }
    fun setBackupTree(uri:Uri){prefs.edit().putString("backup_tree_uri",uri.toString()).apply();_state.value=_state.value.copy(message="Backup folder selected")}
    fun setAutoBackup(enabled:Boolean){prefs.edit().putBoolean("auto_backup_after_draw",enabled).apply();_state.value=_state.value.copy(message=if(enabled)"Automatic backup after each result enabled" else "Automatic backup disabled")}
    fun autoBackupEnabled()=prefs.getBoolean("auto_backup_after_draw",false)
    fun backupTreeSet()=prefs.contains("backup_tree_uri")
    fun setMajorWin(enabled:Boolean){prefs.edit().putBoolean("major_win_enabled",enabled).apply()}
    fun majorWinEnabled()=prefs.getBoolean("major_win_enabled",true)
    fun setMajorWinSound(uri:Uri?){
        MajorWinNotifier.setSound(getApplication(),uri)
        _state.value=_state.value.copy(message=if(uri==null)"Major Win sound reset to built-in" else "Major Win alert sound changed")
    }
    fun majorWinSoundName()=MajorWinNotifier.selectedSoundName(getApplication())
    fun majorWinExternalSoundUri()=MajorWinNotifier.selectedExternalSoundUri(getApplication())
    fun testMajorWin()=MajorWinNotifier.test(getApplication())
    fun latestRunTickets():List<TicketEntity>{
        val run=_state.value.runs.firstOrNull()?:return emptyList()
        return _state.value.tickets.filter{it.modelRunId==run.id && !it.isRandomControl}.sortedBy{it.lineNumber}
    }
    fun latestTargetDate():String?=_state.value.runs.firstOrNull()?.targetDrawDate ?: _state.value.draws.firstOrNull()?.let{engine.nextDrawDate(it.drawDate)}
}
