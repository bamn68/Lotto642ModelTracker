package com.gsbtechnologies.lotto642modeltracker.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities=[DrawEntity::class,BaselineFrequencyEntity::class,ModelRunEntity::class,TicketEntity::class,MatchEntity::class],
    version=1,
    exportSchema=true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun lottoDao(): LottoDao
    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null
        fun get(context: Context): AppDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "lotto642.db")
                // Never add fallbackToDestructiveMigration() in production: prospective records must survive updates.
                .build().also { INSTANCE = it }
        }
    }
}
