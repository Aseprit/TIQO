package com.aseprit.tiqo.ai.tools

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class RelativeDateResolverTest {
    private val context = PlannerToolContext(
        currentDate = LocalDate.of(2026, 9, 2),
        currentTime = LocalTime.of(12, 0),
        zoneId = ZoneId.of("Europe/Warsaw"),
        locale = Locale("pl", "PL"),
    )

    @Test fun tomorrow_isRelativeToDeviceDate() {
        assertEquals(LocalDate.of(2026, 9, 3), PolishRelativeDateResolver.resolve("jutro", context))
    }

    @Test fun nextFriday_isNotCurrentWeekFriday() {
        assertEquals(LocalDate.of(2026, 9, 11), PolishRelativeDateResolver.resolve("następny piątek", context))
    }
}

