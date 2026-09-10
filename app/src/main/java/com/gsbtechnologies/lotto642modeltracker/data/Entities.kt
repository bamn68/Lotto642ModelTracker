package com.gsbtechnologies.lotto642modeltracker.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "draws", indices = [Index(value=["drawDate"], unique=true)])
data class DrawEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val drawDate: String,
    val numbersCsv: String,
    val source: String,
    val verified: Boolean,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "baseline_frequency")
data class BaselineFrequencyEntity(
    @PrimaryKey val number: Int,
    val count: Int,
    val totalDraws: Int
)

@Entity(tableName = "model_runs", indices=[Index("targetDrawDate")])
data class ModelRunEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val targetDrawDate: String,
    val modelVersion: String,
    val generatedAt: Long,
    val dataCutoff: String,
    val requestedLines: Int,
    val ticketCost: Double = 20.0
)

@Entity(
    tableName = "tickets",
    foreignKeys=[ForeignKey(entity=ModelRunEntity::class,parentColumns=["id"],childColumns=["modelRunId"],onDelete=ForeignKey.CASCADE)],
    indices=[Index("modelRunId")]
)
data class TicketEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val modelRunId: Long,
    val lineNumber: Int,
    val numbersCsv: String,
    val selectionScore: Double,
    val strategy: String,
    val bought: Boolean = false,
    val lockedAt: Long? = null,
    val isRandomControl: Boolean = false
)

@Entity(
    tableName = "matches",
    foreignKeys=[
        ForeignKey(entity=TicketEntity::class,parentColumns=["id"],childColumns=["ticketId"],onDelete=ForeignKey.CASCADE),
        ForeignKey(entity=DrawEntity::class,parentColumns=["id"],childColumns=["drawId"],onDelete=ForeignKey.CASCADE)
    ],
    indices=[Index("ticketId"),Index("drawId"),Index(value=["ticketId","drawId"], unique=true)]
)
data class MatchEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ticketId: Long,
    val drawId: Long,
    val matchCount: Int,
    val matchedCsv: String,
    val prizeAmount: Double = 0.0
)

fun String.toNumbers(): List<Int> = split(',').mapNotNull { it.trim().toIntOrNull() }.sorted()
fun List<Int>.toCsv(): String = sorted().joinToString(",")
