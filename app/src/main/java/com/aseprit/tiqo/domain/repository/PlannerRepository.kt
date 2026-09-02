package com.aseprit.tiqo.domain.repository

import com.aseprit.tiqo.domain.model.PlannerItem
import com.aseprit.tiqo.domain.model.PlannerOccurrence
import com.aseprit.tiqo.domain.model.WeeklyStatistics
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

interface PlannerRepository {
    fun observeOccurrences(date: LocalDate): Flow<List<PlannerOccurrence>>

    fun observeOccurrences(
        startInclusive: LocalDate,
        endInclusive: LocalDate,
    ): Flow<List<PlannerOccurrence>>

    fun observeWeeklyStatistics(weekStart: LocalDate): Flow<WeeklyStatistics>

    suspend fun getItem(itemId: Long): PlannerItem?

    suspend fun save(item: PlannerItem): Long

    suspend fun delete(itemId: Long)

    suspend fun setCompleted(itemId: Long, occurrenceDate: LocalDate, completed: Boolean)

    suspend fun isCompleted(itemId: Long, occurrenceDate: LocalDate): Boolean

    suspend fun rescheduleAllReminders()
}

