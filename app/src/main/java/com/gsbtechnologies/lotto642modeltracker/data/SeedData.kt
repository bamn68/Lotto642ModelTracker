package com.gsbtechnologies.lotto642modeltracker.data

object SeedData {
    // Long-run counts through 2026-09-10: Lottolyzer 2,677-draw table through Sep 8,
    // incremented with verified Sep 10 result 07,12,23,27,29,33. Total = 2,678 draws.
    val baselineCounts = mapOf(
        1 to 371, 2 to 377, 3 to 396, 4 to 408, 5 to 389, 6 to 373, 7 to 391,
        8 to 383, 9 to 347, 10 to 364, 11 to 360, 12 to 403, 13 to 368, 14 to 382,
        15 to 410, 16 to 405, 17 to 359, 18 to 384, 19 to 423, 20 to 397, 21 to 388,
        22 to 394, 23 to 381, 24 to 386, 25 to 383, 26 to 368, 27 to 393, 28 to 354,
        29 to 367, 30 to 367, 31 to 390, 32 to 372, 33 to 374, 34 to 380, 35 to 391,
        36 to 380, 37 to 380, 38 to 359, 39 to 405, 40 to 369, 41 to 409, 42 to 388
    )

    // 31 latest completed draws, enough for 10/20/30 momentum windows.
    val recentDraws = listOf(
        "2026-09-10" to listOf(7,12,23,27,29,33),
        "2026-09-08" to listOf(11,14,16,26,27,33),
        "2026-09-05" to listOf(3,24,29,35,36,37),
        "2026-09-03" to listOf(13,14,28,30,32,39),
        "2026-09-01" to listOf(1,8,17,24,33,37),
        "2026-08-29" to listOf(4,6,14,22,39,42),
        "2026-08-27" to listOf(4,13,18,26,35,39),
        "2026-08-25" to listOf(1,7,13,16,17,24),
        "2026-08-22" to listOf(8,16,19,28,34,41),
        "2026-08-20" to listOf(6,10,11,25,27,39),
        "2026-08-18" to listOf(9,16,20,35,39,42),
        "2026-08-15" to listOf(8,12,15,26,28,37),
        "2026-08-13" to listOf(6,12,14,20,21,41),
        "2026-08-11" to listOf(1,6,7,13,15,36),
        "2026-08-08" to listOf(2,26,27,28,30,40),
        "2026-08-06" to listOf(1,2,14,16,39,42),
        "2026-08-04" to listOf(1,15,19,26,34,42),
        "2026-08-01" to listOf(1,2,10,18,28,39),
        "2026-07-30" to listOf(15,17,23,26,38,39),
        "2026-07-28" to listOf(3,7,24,25,39,41),
        "2026-07-25" to listOf(12,15,19,27,35,39),
        "2026-07-23" to listOf(1,3,21,22,32,33),
        "2026-07-21" to listOf(10,13,16,17,38,41),
        "2026-07-18" to listOf(7,12,26,27,32,39),
        "2026-07-16" to listOf(2,6,13,25,30,42),
        "2026-07-14" to listOf(6,18,21,27,35,38),
        "2026-07-11" to listOf(10,21,23,33,40,41),
        "2026-07-09" to listOf(2,27,28,29,38,42),
        "2026-07-07" to listOf(5,6,12,16,20,35),
        "2026-07-04" to listOf(5,6,8,12,23,25),
        "2026-07-02" to listOf(9,19,24,29,31,33)
    )

    suspend fun ensureSeeded(dao: LottoDao) {
        if (dao.allBaseline().isEmpty()) {
            dao.insertBaseline(baselineCounts.map { (n,c) -> BaselineFrequencyEntity(n,c,2678) })
        }
        if (dao.allDraws().isEmpty()) {
            dao.insertDraws(recentDraws.map { (date, nums) ->
                DrawEntity(drawDate=date,numbersCsv=nums.toCsv(),source=if(date=="2026-09-10") "Verified LottoMatik screenshot" else "Historical public draw archive",verified=true)
            })
        }
    }
}
