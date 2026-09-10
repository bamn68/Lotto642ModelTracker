package com.gsbtechnologies.lotto642modeltracker

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gsbtechnologies.lotto642modeltracker.backup.BackupManager
import com.gsbtechnologies.lotto642modeltracker.data.*
import com.gsbtechnologies.lotto642modeltracker.model.*
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
    private val _state=MutableStateFlow(LottoState(busy=true))
    val state:StateFlow<LottoState> = _state
    private val prefs=app.getSharedPreferences("settings",Context.MODE_PRIVATE)

    init {
        viewModelScope.launch(Dispatchers.IO) {
            SeedData.ensureSeeded(dao)
            refresh("Ready")
        }
    }

    suspend fun refresh(message:String?=null) {
        val draws=dao.allDraws()
        val baseline=dao.allBaseline()
        _state.value=LottoState(draws,baseline,dao.allRuns(),dao.allTickets(),dao.allMatches(),engine.analyze(draws,baseline),message,false)
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
        val runs=dao.runsForDate(date)
        val newMatches=mutableListOf<MatchEntity>()
        for(run in runs) for(ticket in dao.ticketsForRun(run.id)) {
            val matched=ticket.numbersCsv.toNumbers().intersect(nums.toSet()).sorted()
            newMatches += MatchEntity(ticketId=ticket.id,drawId=draw.id,matchCount=matched.size,matchedCsv=matched.toCsv())
            if(ticket.bought && ticket.lockedAt!=null && matched.size>=5) {
                MajorWinNotifier.notifyMatch(getApplication(),matched.size,ticket.lineNumber,matched)
            }
        }
        dao.insertMatches(newMatches)
        if(prefs.getBoolean("auto_backup_after_draw",false)) {
            prefs.getString("backup_tree_uri",null)?.let { runCatching { backup.autoBackupToTree(it) } }
        }
        refresh("Verified result saved and ${newMatches.size} ticket outcomes scored")
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
    fun testMajorWin()=MajorWinNotifier.test(getApplication())
    fun latestRunTickets():List<TicketEntity>{
        val run=_state.value.runs.firstOrNull()?:return emptyList()
        return _state.value.tickets.filter{it.modelRunId==run.id && !it.isRandomControl}.sortedBy{it.lineNumber}
    }
    fun latestTargetDate():String?=_state.value.runs.firstOrNull()?.targetDrawDate ?: _state.value.draws.firstOrNull()?.let{engine.nextDrawDate(it.drawDate)}
}
