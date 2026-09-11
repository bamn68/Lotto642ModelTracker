package com.gsbtechnologies.lotto642modeltracker

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gsbtechnologies.lotto642modeltracker.data.*
import com.gsbtechnologies.lotto642modeltracker.model.SignalGroup
import com.gsbtechnologies.lotto642modeltracker.ui.LottoTheme
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

class MainActivity:ComponentActivity(){
    private val vm:LottoViewModel by viewModels()
    private val notificationPermission=registerForActivityResult(ActivityResultContracts.RequestPermission()){}
    override fun onCreate(savedInstanceState:Bundle?){
        super.onCreate(savedInstanceState)
        if(Build.VERSION.SDK_INT>=33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        setContent{LottoTheme{App(vm)}}
    }
}

enum class Tab(val label:String){HOME("Home"),GENERATE("Generate"),TICKETS("Tickets"),RESULTS("Results"),PERFORMANCE("Performance"),SETTINGS("Settings")}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun App(vm:LottoViewModel){
    val state by vm.state.collectAsStateWithLifecycle()
    var tab by remember{mutableStateOf(Tab.HOME)}

    LaunchedEffect(state.message){
        val message=state.message ?: return@LaunchedEffect
        delay(3000)
        vm.clearMessage(message)
    }

    Scaffold(
        bottomBar={NavigationBar{Tab.entries.take(5).forEach{t->NavigationBarItem(selected=tab==t,onClick={tab=t},icon={Icon(when(t){Tab.HOME->Icons.Default.Home;Tab.GENERATE->Icons.Default.AutoAwesome;Tab.TICKETS->Icons.Default.ConfirmationNumber;Tab.RESULTS->Icons.Default.EmojiEvents;else->Icons.Default.BarChart},t.label)},label={Text(t.label)})}}},
        topBar={TopAppBar(title={Text("Lotto 6/42 Model Tracker",fontWeight=FontWeight.Bold)},actions={IconButton(onClick={tab=Tab.SETTINGS}){Icon(Icons.Default.Settings,"Settings")}})}
    ){pad->
        Box(Modifier.padding(pad).fillMaxSize()){
            when(tab){
                Tab.HOME->HomeScreen(state,{tab=Tab.GENERATE})
                Tab.GENERATE->GenerateScreen(vm,state)
                Tab.TICKETS->TicketsScreen(vm,state)
                Tab.RESULTS->ResultsScreen(vm,state)
                Tab.PERFORMANCE->PerformanceScreen(state)
                Tab.SETTINGS->SettingsScreen(vm)
            }
            state.message?.let{Text(it,Modifier.align(Alignment.BottomCenter).padding(12.dp).background(MaterialTheme.colorScheme.inverseSurface,RoundedCornerShape(20.dp)).padding(horizontal=14.dp,vertical=8.dp),color=MaterialTheme.colorScheme.inverseOnSurface,fontSize=12.sp)}
            if(state.busy) CircularProgressIndicator(Modifier.align(Alignment.Center))
        }
    }
}

@Composable fun HomeScreen(s:LottoState,onGenerate:()->Unit){
    val latest=s.draws.firstOrNull()
    val next=s.runs.firstOrNull()?.targetDrawDate ?: latest?.drawDate
    LazyColumn(Modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        item{HeroCard("NEXT DRAW", next?.let{nextDrawFrom(it,s.runs.isNotEmpty())}?:"—", "Model V1.0 • ${s.baseline.firstOrNull()?.totalDraws?:0} baseline draws")}
        item{SectionCard("Latest verified result"){NumberRow(latest?.numbersCsv?.toNumbers()?:emptyList());Spacer(Modifier.height(6.dp));Text(latest?.drawDate?:"No result",color=MaterialTheme.colorScheme.onSurfaceVariant)}}
        item{SectionCard("Current model signals"){
            val hot=s.signals.filter{it.group==SignalGroup.HOT}.take(6)
            val overdue=s.signals.filter{it.group==SignalGroup.OVERDUE}.take(6)
            Text("HOT",fontWeight=FontWeight.Bold,color=Color(0xFFB26A00));SignalRow(hot.map{it.number});Spacer(Modifier.height(8.dp));Text("OVERDUE",fontWeight=FontWeight.Bold);SignalRow(overdue.map{it.number})
        }}
        item{Button(onClick=onGenerate,Modifier.fillMaxWidth().height(54.dp)){Icon(Icons.Default.AutoAwesome,null);Spacer(Modifier.width(8.dp));Text("GENERATE RECOMMENDATIONS")}}
        item{Text("Selection scores organize historical signals; they do not change the equal mathematical probability of fair 6/42 combinations.",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)}
    }
}

private fun nextDrawFrom(value:String,hasRun:Boolean)=if(hasRun)value else try{com.gsbtechnologies.lotto642modeltracker.model.RecommendationEngine().nextDrawDate(value)}catch(_:Exception){value}

@Composable fun GenerateScreen(vm:LottoViewModel,s:LottoState){
    var count by remember{mutableIntStateOf(10)}
    val tickets=vm.latestRunTickets()
    val run=s.runs.firstOrNull()
    LazyColumn(Modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
        item{Text("How many lines?",fontSize=24.sp,fontWeight=FontWeight.Bold);Text("The portfolio optimizer adapts its coverage to the number you intend to generate.")}
        item{Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)){FilledTonalIconButton(onClick={count=(count-1).coerceAtLeast(1)}){Icon(Icons.Default.Remove,null)};Text("$count",fontSize=32.sp,fontWeight=FontWeight.Bold);FilledTonalIconButton(onClick={count=(count+1).coerceAtMost(100)}){Icon(Icons.Default.Add,null)};Spacer(Modifier.weight(1f));Text("₱${count*20}",fontSize=20.sp,fontWeight=FontWeight.Bold)}}
        item{Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){listOf(5,10,15,20).forEach{n->FilterChip(selected=count==n,onClick={count=n},label={Text("$n")})}}}
        item{Button(onClick={vm.generate(count)},Modifier.fillMaxWidth().height(52.dp)){Text("GENERATE $count LINES")}}
        if(run!=null) item{Text("Latest portfolio • ${run.targetDrawDate} • ${run.requestedLines} lines",fontWeight=FontWeight.SemiBold)}
        items(tickets,key={it.id}){TicketCard(it,onBought={vm.setBought(it.id,!it.bought)})}
    }
}

@Composable fun TicketsScreen(vm:LottoViewModel,s:LottoState){
    val models=s.tickets.filter{!it.isRandomControl}
    LazyColumn(Modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
        item{Text("Generated & purchased tickets",fontSize=24.sp,fontWeight=FontWeight.Bold);Text("Bought tickets are timestamped and locked for prospective evaluation.")}
        items(models,key={it.id}){TicketCard(it,onBought={vm.setBought(it.id,!it.bought)})}
    }
}

@Composable fun ResultsScreen(vm:LottoViewModel,s:LottoState){
    var date by remember(s.runs.firstOrNull()?.targetDrawDate){mutableStateOf(s.runs.firstOrNull()?.targetDrawDate?:"")}
    var fields by remember{mutableStateOf(List(6){""})}
    var error by remember{mutableStateOf<String?>(null)}
    LazyColumn(Modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        item{Text("Record verified draw result",fontSize=24.sp,fontWeight=FontWeight.Bold);Text("A result must be verified before ticket matching or a Major Win Alert can fire.")}
        item{OutlinedTextField(date,{date=it},label={Text("Draw date YYYY-MM-DD")},modifier=Modifier.fillMaxWidth())}
        item{Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){fields.forEachIndexed{i,v->OutlinedTextField(v,{x->fields=fields.toMutableList().also{it[i]=x.filter(Char::isDigit).take(2)}},modifier=Modifier.weight(1f),singleLine=true,label={Text("${i+1}")})}}}
        item{error?.let{Text(it,color=MaterialTheme.colorScheme.error)};Button(onClick={val nums=fields.mapNotNull{it.toIntOrNull()};if(date.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))&&nums.size==6&&nums.distinct().size==6&&nums.all{it in 1..42}){error=null;vm.saveVerifiedResult(date,nums);fields=List(6){""}}else error="Enter a valid date and six unique numbers from 1–42."},Modifier.fillMaxWidth()){Text("SAVE VERIFIED RESULT & SCORE TICKETS")}}
        item{Text("Recent results",fontWeight=FontWeight.Bold)}
        items(s.draws.take(10)){d->SectionCard(d.drawDate){NumberRow(d.numbersCsv.toNumbers());Text(d.source,fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)}}
    }
}

@Composable fun PerformanceScreen(s:LottoState){
    val ticketBy=s.tickets.associateBy{it.id}
    val modelMatches=s.matches.filter{ticketBy[it.ticketId]?.isRandomControl==false}
    val controlMatches=s.matches.filter{ticketBy[it.ticketId]?.isRandomControl==true}
    val purchased=s.tickets.count{it.bought&&!it.isRandomControl}
    val best=modelMatches.maxOfOrNull{it.matchCount}?:0
    LazyColumn(Modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        item{Text("Model performance",fontSize=24.sp,fontWeight=FontWeight.Bold)}
        item{Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){MetricCard("Draws scored",s.matches.map{it.drawId}.distinct().size.toString(),Modifier.weight(1f));MetricCard("Best match","$best / 6",Modifier.weight(1f));MetricCard("Bought",purchased.toString(),Modifier.weight(1f))}}
        item{SectionCard("Match distribution — model"){(0..6).forEach{m->StatRow("$m matches",modelMatches.count{it.matchCount==m})}}}
        item{SectionCard("Model vs random control"){val ma=modelMatches.map{it.matchCount}.average().takeIf{!it.isNaN()}?:0.0;val ca=controlMatches.map{it.matchCount}.average().takeIf{!it.isNaN()}?:0.0;Text("Model average matches: ${"%.3f".format(ma)}");Text("Random-control average: ${"%.3f".format(ca)}");Text("Prospective comparison becomes meaningful only after many future draws.",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)}}
    }
}

@Composable fun SettingsScreen(vm:LottoViewModel){
    val activity=androidx.compose.ui.platform.LocalContext.current as ComponentActivity
    val create=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")){it?.let(vm::backupTo)}
    val open=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){it?.let(vm::restoreFrom)}
    val tree=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()){uri->uri?.let{runCatching{activity.contentResolver.takePersistableUriPermission(it,Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)};vm.setBackupTree(it)}}
    var auto by remember{mutableStateOf(vm.autoBackupEnabled())}
    var major by remember{mutableStateOf(vm.majorWinEnabled())}
    var soundName by remember{mutableStateOf(vm.majorWinSoundName())}
    val soundPicker=rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()){result->
        if(result.resultCode==Activity.RESULT_OK){
            @Suppress("DEPRECATION")
            val uri=result.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            if(uri!=null){
                vm.setMajorWinSound(uri)
                soundName=vm.majorWinSoundName()
            }
        }
    }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        item{Text("Settings",fontSize=24.sp,fontWeight=FontWeight.Bold)}
        item{SectionCard("Backup & Google Drive"){Text("Use Android's system picker to save to Google Drive, device storage, OneDrive or another document provider.");Spacer(Modifier.height(8.dp));Button(onClick={create.launch("Lotto642_Backup_${SimpleDateFormat("yyyy-MM-dd_HHmm",Locale.US).format(Date())}.l642")},Modifier.fillMaxWidth()){Text("BACK UP NOW")};OutlinedButton(onClick={open.launch(arrayOf("application/octet-stream","*/*"))},Modifier.fillMaxWidth()){Text("RESTORE BACKUP")};OutlinedButton(onClick={tree.launch(null)},Modifier.fillMaxWidth()){Text(if(vm.backupTreeSet())"CHANGE AUTO-BACKUP FOLDER" else "SELECT AUTO-BACKUP FOLDER")};Row(verticalAlignment=Alignment.CenterVertically){Switch(auto,{auto=it;vm.setAutoBackup(it)},enabled=vm.backupTreeSet());Spacer(Modifier.width(8.dp));Text("Auto backup after each completed draw")}}}
        item{SectionCard("Major Win Alert"){
            Row(verticalAlignment=Alignment.CenterVertically){Switch(major,{major=it;vm.setMajorWin(it)});Spacer(Modifier.width(8.dp));Text("Loud alert for Bought & Locked 5/6 or 6/6 matches")}
            Spacer(Modifier.height(8.dp))
            Text("Alert sound: $soundName",fontWeight=FontWeight.SemiBold)
            OutlinedButton(onClick={
                soundPicker.launch(Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply{
                    putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE,RingtoneManager.TYPE_NOTIFICATION)
                    putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT,true)
                    putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT,false)
                    putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE,"Choose Major Win alert sound")
                    putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,vm.majorWinExternalSoundUri())
                })
            },Modifier.fillMaxWidth()){Icon(Icons.Default.MusicNote,null);Spacer(Modifier.width(8.dp));Text("CHOOSE ALERT SOUND")}
            OutlinedButton(onClick={vm.setMajorWinSound(null);soundName=vm.majorWinSoundName()},Modifier.fillMaxWidth()){Text("USE BUILT-IN MAJOR WIN SOUND")}
            Button(onClick={vm.testMajorWin()},Modifier.fillMaxWidth()){Text("TEST SELECTED ALERT SOUND")}
            OutlinedButton(onClick={activity.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,activity.packageName))},Modifier.fillMaxWidth()){Text("ANDROID NOTIFICATION SETTINGS")}
        }}
        item{Text("App version 1.0.0 • Model V1.0\nPackage ID is fixed for in-place updates. Future database changes must use non-destructive Room migrations.",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)}
    }
}

@Composable fun HeroCard(kicker:String,title:String,sub:String){Card(colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.primary)){Column(Modifier.padding(20.dp)){Text(kicker,color=MaterialTheme.colorScheme.secondary,fontWeight=FontWeight.Bold);Text(title,fontSize=28.sp,fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.onPrimary);Text(sub,color=MaterialTheme.colorScheme.onPrimary.copy(alpha=.8f))}}}
@Composable fun SectionCard(title:String,content:@Composable ColumnScope.()->Unit){Card{Column(Modifier.fillMaxWidth().padding(16.dp)){Text(title,fontWeight=FontWeight.Bold);Spacer(Modifier.height(8.dp));content()}}}
@Composable fun NumberRow(nums:List<Int>){Row(horizontalArrangement=Arrangement.spacedBy(7.dp)){nums.forEach{Ball(it)}}}
@Composable fun SignalRow(nums:List<Int>){Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){nums.forEach{Ball(it,small=true)}}}
@Composable fun Ball(n:Int,small:Boolean=false){Box(Modifier.size(if(small)36.dp else 43.dp).background(MaterialTheme.colorScheme.primary,CircleShape),contentAlignment=Alignment.Center){Text("%02d".format(n),color=MaterialTheme.colorScheme.onPrimary,fontWeight=FontWeight.Bold,fontSize=if(small)13.sp else 15.sp)}}
@Composable fun TicketCard(t:TicketEntity,onBought:()->Unit){Card(border=if(t.bought)BorderStroke(2.dp,MaterialTheme.colorScheme.secondary)else null){Column(Modifier.fillMaxWidth().padding(14.dp)){Row{Text("LINE %02d".format(t.lineNumber),fontWeight=FontWeight.Bold);Spacer(Modifier.weight(1f));Text("Score ${"%.2f".format(t.selectionScore)}",fontSize=12.sp)};Spacer(Modifier.height(8.dp));NumberRow(t.numbersCsv.toNumbers());Spacer(Modifier.height(8.dp));Text(t.strategy,fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant);Button(onClick=onBought,colors=if(t.bought)ButtonDefaults.buttonColors(containerColor=MaterialTheme.colorScheme.secondary)else ButtonDefaults.buttonColors(),modifier=Modifier.fillMaxWidth()){Icon(if(t.bought)Icons.Default.Lock else Icons.Default.ShoppingCart,null);Spacer(Modifier.width(6.dp));Text(if(t.bought)"BOUGHT & LOCKED" else "MARK BOUGHT")};t.lockedAt?.let{Text("Locked ${SimpleDateFormat("MMM d, h:mm a",Locale.US).format(Date(it))}",fontSize=11.sp)}}}}
@Composable fun MetricCard(label:String,value:String,modifier:Modifier=Modifier){Card(modifier){Column(Modifier.padding(12.dp)){Text(value,fontSize=22.sp,fontWeight=FontWeight.Bold);Text(label,fontSize=11.sp)}}}
@Composable fun StatRow(label:String,value:Int){Row(Modifier.fillMaxWidth()){Text(label);Spacer(Modifier.weight(1f));Text(value.toString(),fontWeight=FontWeight.Bold)}}
