package com.rabbi.scrollcounter.data

/**
 * Streak badges: earned by staying under the daily limit for N days in a row. Once earned, a
 * badge is kept forever (even if the streak breaks later), with the date it was unlocked.
 */
object Badges {
    enum class Tier(val light: Int, val base: Int, val dark: Int) {
        BRONZE(0xFFF2C094.toInt(), 0xFFC27A3E.toInt(), 0xFF6E3A14.toInt()),
        SILVER(0xFFF8FAFC.toInt(), 0xFFB9C3CF.toInt(), 0xFF5F6B7A.toInt()),
        GOLD(0xFFFFF1A8.toInt(), 0xFFF5B82E.toInt(), 0xFF96600A.toInt()),
        EMERALD(0xFFB4F5D8.toInt(), 0xFF10B981.toInt(), 0xFF05583F.toInt()),
        SAPPHIRE(0xFFC4E8FF.toInt(), 0xFF3B82F6.toInt(), 0xFF1B3384.toInt()),
        RUBY(0xFFFFD0D8.toInt(), 0xFFE11D48.toInt(), 0xFF7A1033.toInt()),
        DIAMOND(0xFFE8F6FF.toInt(), 0xFFA78BFA.toInt(), 0xFF4C1D95.toInt()),
    }

    data class Badge(val days: Int, val title: String, val description: String, val tier: Tier)

    val ALL = listOf(
        Badge(1, "First step", "1 day under your limit", Tier.BRONZE),
        Badge(3, "On a roll", "3 days in a row under your limit", Tier.SILVER),
        Badge(7, "One week strong", "7 days in a row under your limit", Tier.GOLD),
        Badge(14, "Two-week hero", "14 days in a row under your limit", Tier.EMERALD),
        Badge(30, "Monthly master", "30 days in a row under your limit", Tier.SAPPHIRE),
        Badge(60, "Unstoppable", "60 days in a row under your limit", Tier.RUBY),
        Badge(100, "Legend", "100 days in a row under your limit", Tier.DIAMOND),
    )

    fun of(days: Int): Badge? = ALL.firstOrNull { it.days == days }

    /** The next badge to aim for after a streak of [streak] days (null once all are earned). */
    fun next(best: Int): Badge? = ALL.firstOrNull { it.days > best }
}
