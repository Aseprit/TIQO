package com.aseprit.tiqo.data.repository

import com.aseprit.tiqo.data.database.PlannerDao
import com.aseprit.tiqo.data.database.toDomain
import com.aseprit.tiqo.data.database.toEntity
import com.aseprit.tiqo.domain.model.DailyCompletion
import com.aseprit.tiqo.domain.model.PlannerItem
import com.aseprit.tiqo.domain.model.PlannerItemType
import com.aseprit.tiqo.domain.model.PlannerOccurrence
import com.aseprit.tiqo.domain.model.WeeklyStatistics
import com.aseprit.tiqo.domain.recurrence.RecurrenceEngine
import com.aseprit.tiqo.domain.reminder.ReminderScheduler
import com.aseprit.tiqo.domain.repository.PlannerRepository
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

class RoomPlannerRepository(
    private val dao: PlannerDao,
    private val reminderScheduler: ReminderScheduler,
) : PlannerRepository {
    override fun observeOccurrences(date: LocalDate): Flow<List<PlannerOccurrence>> =
        observeOccurrences(date, date)

    override fun observeOccurrences(
        startInclusive: LocalDate,
        endInclusive: LocalDate,
    ): Flow<List<PlannerOccurrence>> = combine(
        dao.observeCandidates(startInclusive.toEpochDay(), endInclusive.toEpochDay()),
        dao.observeCompletions(startInclusive.toEpochDay(), endInclusive.toEpochDay()),
    ) { entities, completions ->
        val completionByOccurrence = completions.associateBy {
            it.itemId to it.occurrenceDateEpochDay
        }
        entities.flatMap { entity ->
            val item = entity.toDomain()
            RecurrenceEngine.occurrencesBetween(item, startInclusive, endInclusive).map { date ->
                val completion = completionByOccurrence[item.id to date.toEpochDay()]
                PlannerOccurrence(
                    item = item,
                    date = date,
                    isCompleted = completion != null,
                    completedAtMillis = completion?.completedAtMillis,
                )
            }
        }.sortedWith(
            compareBy<PlannerOccurrence> { it.date }
                .thenByDescending { it.item.isAllDay }
                .thenBy { it.item.time },
        )
    }

    override fun observeWeeklyStatistics(weekStart: LocalDate): Flow<WeeklyStatistics> {
        val weekEnd = weekStart.plusDays(6)
        return observeOccurrences(weekStart, weekEnd).map { occurrences ->
            val tasks = occurrences.filter { it.item.type == PlannerItemType.Task }
            WeeklyStatistics(
                completed = tasks.count { it.isCompleted },
                scheduled = tasks.size,
                days = (0L..6L).map { offset ->
                    val date = weekStart.plusDays(offset)
                    val dayTasks = tasks.filter { it.date == date }
                    DailyCompletion(
                        date = date,
                        completed = dayTasks.count { it.isCompleted },
                        scheduled = dayTasks.size,
                    )
                },
            )
        }
    }

    override suspend fun getItem(itemId: Long): PlannerItem? = dao.getItem(itemId)?.toDomain()

    override suspend fun save(item: PlannerItem): Long {
        val now = System.currentTimeMillis()
        val existing = item.id.takeIf { it != 0L }?.let { dao.getItem(it) }
        val normalized = item.copy(
            createdAtMillis = existing?.createdAtMillis ?: item.createdAtMillis,
            updatedAtMillis = now,
        )
        val id = if (existing == null) {
            dao.insert(normalized.toEntity(idOverride = 0))
        } else {
            dao.update(normalized.toEntity())
            normalized.id
        }
        reminderScheduler.cancel(id)
        reminderScheduler.schedule(normalized.copy(id = id))
        return id
    }

    override suspend fun delete(itemId: Long) {
        reminderScheduler.cancel(itemId)
        dao.delete(itemId)
    }

    override suspend fun setCompleted(itemId: Long, occurrenceDate: LocalDate, completed: Boolean) {
        dao.setCompleted(itemId, occurrenceDate.toEpochDay(), completed)
    }

    override suspend fun isCompleted(itemId: Long, occurrenceDate: LocalDate): Boolean =
        dao.getCompletion(itemId, occurrenceDate.toEpochDay()) != null

    override suspend fun rescheduleAllReminders() {
        dao.getItemsWithReminders().forEach { entity ->
            reminderScheduler.cancel(entity.id)
            reminderScheduler.schedule(entity.toDomain())
        }
    }
}

