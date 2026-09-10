package com.gsbtechnologies.lotto642modeltracker.model

import com.gsbtechnologies.lotto642modeltracker.data.*
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sqrt
import kotlin.random.Random

const val MODEL_VERSION = "1.0"

enum class SignalGroup { HOT, NEUTRAL, COLD, OVERDUE }

data class NumberSignal(
    val number: Int,
    val score: Double,
    val group: SignalGroup,
    val gap: Int,
    val f10: Int,
    val f20: Int,
    val f30: Int,
    val baselineCount: Int
)

data class GeneratedLine(val numbers: List<Int>, val score: Double, val strategy: String)

class RecommendationEngine {
    private val p = 1.0 / 7.0

    fun analyze(draws: List<DrawEntity>, baseline: List<BaselineFrequencyEntity>): List<NumberSignal> {
        val ordered = draws.filter { it.verified }.sortedByDescending { it.drawDate }
        val bMap = baseline.associateBy { it.number }
        fun freq(n:Int, window:Int) = ordered.take(window).count { n in it.numbersCsv.toNumbers() }
        fun z(count:Int, n:Int):Double {
            if (n <= 0) return 0.0
            val mean=n*p; val sd=sqrt(n*p*(1-p))
            return if (sd == 0.0) 0.0 else (count-mean)/sd
        }
        return (1..42).map { n ->
            val f10=freq(n,10); val f20=freq(n,20); val f30=freq(n,30)
            val b=bMap[n] ?: BaselineFrequencyEntity(n,0,0)
            val score=.40*z(f30, minOf(30,ordered.size)) + .25*z(f20,minOf(20,ordered.size)) + .20*z(f10,minOf(10,ordered.size)) + .15*z(b.count,b.totalDraws)
            val gap=ordered.indexOfFirst { n in it.numbersCsv.toNumbers() }.let { if(it<0) ordered.size else it }
            val group=when {
                gap >= 14 -> SignalGroup.OVERDUE
                score >= .55 -> SignalGroup.HOT
                score <= -.55 -> SignalGroup.COLD
                else -> SignalGroup.NEUTRAL
            }
            NumberSignal(n,score,group,gap,f10,f20,f30,b.count)
        }.sortedByDescending { it.score }
    }

    fun nextDrawDate(latestCompleted: String): String {
        var d=LocalDate.parse(latestCompleted).plusDays(1)
        while (d.dayOfWeek !in setOf(DayOfWeek.TUESDAY,DayOfWeek.THURSDAY,DayOfWeek.SATURDAY)) d=d.plusDays(1)
        return d.toString()
    }

    fun generatePortfolio(count: Int, draws: List<DrawEntity>, baseline: List<BaselineFrequencyEntity>, targetDate:String): List<GeneratedLine> {
        val requested=count.coerceIn(1,100)
        val signals=analyze(draws,baseline)
        val scoreBy=signals.associate { it.number to it.score }
        val recent=draws.filter{it.verified}.sortedByDescending{it.drawDate}.take(30).map{it.numbersCsv.toNumbers().toSet()}
        val pairCount=mutableMapOf<Pair<Int,Int>,Int>()
        recent.forEach { set ->
            val list=set.sorted(); for(i in list.indices) for(j in i+1 until list.size) {
                val k=list[i] to list[j]; pairCount[k]=(pairCount[k]?:0)+1
            }
        }
        val seed=(targetDate.hashCode().toLong() shl 32) xor MODEL_VERSION.hashCode().toLong()
        val rng=Random(seed)
        val selected=mutableListOf<GeneratedLine>()
        val usage=IntArray(43)
        val trials=if(requested<=20) 1800 else 800

        repeat(requested) { index ->
            var best: GeneratedLine?=null
            repeat(trials) {
                val nums=weightedSix(rng,signals)
                val base=nums.sumOf { scoreBy[it] ?: 0.0 }
                val odd=nums.count{it%2==1}; val sum=nums.sum()
                val balance=when(odd){3->.45;2,4->.25;else->-.20}
                val sumBonus=if(sum in 100..160) .30 else -.10
                var pairBonus=0.0
                for(i in nums.indices) for(j in i+1 until nums.size) pairBonus += (pairCount[nums[i] to nums[j]] ?: 0) * .035
                val maxOverlap=selected.maxOfOrNull { line -> line.numbers.intersect(nums.toSet()).size } ?: 0
                val overlapPenalty=max(0,maxOverlap-2)*.70
                val usagePenalty=nums.sumOf { usage[it] }*.07
                val objective=base+balance+sumBonus+pairBonus-overlapPenalty-usagePenalty
                if(best==null || objective>best!!.score) best=GeneratedLine(nums,objective,strategyFor(index,nums,signals))
            }
            val line=best ?: GeneratedLine((1..42).shuffled(rng).take(6).sorted(),0.0,"Diversified")
            selected += line
            line.numbers.forEach { usage[it]++ }
        }
        return selected
    }

    fun randomControl(count:Int,targetDate:String):List<GeneratedLine> {
        val rng=Random(targetDate.hashCode().toLong() xor 0x642642L)
        val seen=mutableSetOf<String>()
        val out=mutableListOf<GeneratedLine>()
        while(out.size<count.coerceIn(1,100)) {
            val nums=(1..42).shuffled(rng).take(6).sorted(); val key=nums.joinToString(",")
            if(seen.add(key)) out += GeneratedLine(nums,0.0,"Random control")
        }
        return out
    }

    private fun weightedSix(rng:Random, signals:List<NumberSignal>):List<Int> {
        val remaining=signals.toMutableList(); val out=mutableListOf<Int>()
        repeat(6) {
            val weights=remaining.map { exp((it.score.coerceIn(-2.5,2.5))*.35)+.25 }
            val total=weights.sum(); var x=rng.nextDouble()*total; var idx=0
            while(idx<weights.lastIndex && x>weights[idx]) { x-=weights[idx]; idx++ }
            out += remaining.removeAt(idx).number
        }
        return out.sorted()
    }

    private fun strategyFor(index:Int, nums:List<Int>, signals:List<NumberSignal>):String {
        val map=signals.associateBy{it.number}; val hot=nums.count{map[it]?.group==SignalGroup.HOT}; val overdue=nums.count{map[it]?.group==SignalGroup.OVERDUE}
        return when {
            overdue>=1 && hot>=2 -> "Momentum + overdue diversification"
            hot>=4 -> "Momentum-heavy"
            index%4==1 -> "Balanced recent-strength"
            index%4==2 -> "Pair/coverage diversified"
            else -> "Model-optimized diversified"
        }
    }
}
