package com.gsbtechnologies.lotto642modeltracker

import com.gsbtechnologies.lotto642modeltracker.data.*
import com.gsbtechnologies.lotto642modeltracker.model.RecommendationEngine
import org.junit.Assert.*
import org.junit.Test

class RecommendationEngineTest {
    @Test fun portfolio_isDistinctAndValid() {
        val draws=SeedData.recentDraws.map{DrawEntity(drawDate=it.first,numbersCsv=it.second.toCsv(),source="test",verified=true)}
        val baseline=SeedData.baselineCounts.map{BaselineFrequencyEntity(it.key,it.value,2678)}
        val lines=RecommendationEngine().generatePortfolio(10,draws,baseline,"2026-09-12")
        assertEquals(10,lines.size)
        assertEquals(10,lines.map{it.numbers}.distinct().size)
        lines.forEach {
            assertEquals(6,it.numbers.distinct().size)
            assertTrue(it.numbers.all{n->n in 1..42})
        }
    }

    @Test fun nextDraw_isTueThuSat(){
        assertEquals("2026-09-12",RecommendationEngine().nextDrawDate("2026-09-10"))
    }
}
