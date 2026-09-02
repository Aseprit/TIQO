package com.aseprit.tiqo.data.repository

import com.aseprit.tiqo.data.database.OccurrenceCompletionEntity
import com.aseprit.tiqo.data.database.PlannerDao
import com.aseprit.tiqo.data.database.PlannerItemEntity
import com.aseprit.tiqo.domain.model.PlannerItem
import com.aseprit.tiqo.domain.model.PlannerItemType
import com.aseprit.tiqo.domain.model.RecurrenceRule
import com.aseprit.tiqo.domain.model.ReminderOffset
import com.aseprit.tiqo.domain.reminder.ReminderScheduler
import java.time.DayOfWeek
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomPlannerRepositoryTest {
    @Test fun occurrenceQueryReturnsOnlyRequestedDayAndMonthRange() = runBlocking {
        val dao = FakePlannerDao()
        val repository = RoomPlannerRepository(dao, RecordingScheduler())
        repository.save(
            PlannerItem(
                title = "Codzienny",
                startDate = LocalDate.of(2026, 8, 31),
                recurrence = RecurrenceRule.Daily(),
            ),
        )

        val day = repository.observeOccurrences(LocalDate.of(2026, 9, 3)).first()
        val monthSlice = repository.observeOccurrences(
            LocalDate.of(2026, 9, 1),
            LocalDate.of(2026, 9, 30),
        ).first()

        assertEquals(listOf(LocalDate.of(2026, 9, 3)), day.map { it.date })
        assertEquals(30, monthSlice.size)
        assertEquals(LocalDate.of(2026, 9, 1), monthSlice.first().date)
        assertEquals(LocalDate.of(2026, 9, 30), monthSlice.last().date)
    }

    @Test fun completion_isStoredPerOccurrence() = runBlocking {
        val dao = FakePlannerDao()
        val repository = RoomPlannerRepository(dao, RecordingScheduler())
        val id = repository.save(
            PlannerItem(
                title = "Siłownia",
                startDate = LocalDate.of(2026, 9, 1),
                recurrence = RecurrenceRule.Weekly(setOf(DayOfWeek.TUESDAY)),
            ),
        )
        val first = LocalDate.of(2026, 9, 1)
        val second = LocalDate.of(2026, 9, 8)

        repository.setCompleted(id, first, true)

        assertTrue(repository.isCompleted(id, first))
        assertFalse(repository.isCompleted(id, second))
    }

    @Test fun weeklyStatistics_ignoreEvents() = runBlocking {
        val dao = FakePlannerDao()
        val repository = RoomPlannerRepository(dao, RecordingScheduler())
        val monday = LocalDate.of(2026, 9, 7)
        val taskId = repository.save(PlannerItem(title = "Task", startDate = monday))
        repository.save(PlannerItem(title = "Event", type = PlannerItemType.Event, startDate = monday))
        repository.setCompleted(taskId, monday, true)

        val stats = repository.observeWeeklyStatistics(monday).first()

        assertEquals(1, stats.scheduled)
        assertEquals(1, stats.completed)
        assertEquals(100, stats.completionRate)
    }

    @Test fun saveEditDelete_keepReminderScheduleInSync() = runBlocking {
        val dao = FakePlannerDao()
        val scheduler = RecordingScheduler()
        val repository = RoomPlannerRepository(dao, scheduler)
        val item = PlannerItem(
            title = "Dentysta",
            startDate = LocalDate.of(2026, 9, 4),
            reminder = ReminderOffset.OneHour,
        )
        val id = repository.save(item)
        repository.save(item.copy(id = id, title = "Dentysta kontrola"))
        repository.delete(id)

        assertEquals(2, scheduler.scheduled.size)
        assertEquals(listOf(id, id, id), scheduler.cancelled)
    }
}

private class RecordingScheduler : ReminderScheduler {
    val scheduled = mutableListOf<PlannerItem>()
    val cancelled = mutableListOf<Long>()
    override suspend fun schedule(item: PlannerItem) { if (item.reminder != null) scheduled += item }
    override suspend fun cancel(itemId: Long) { cancelled += itemId }
}

private class FakePlannerDao : PlannerDao() {
    private val items = MutableStateFlow<List<PlannerItemEntity>>(emptyList())
    private val completions = MutableStateFlow<List<OccurrenceCompletionEntity>>(emptyList())
    private var nextId = 1L

    override fun observeCandidates(startEpochDay: Long, endEpochDay: Long): Flow<List<PlannerItemEntity>> = items
    override fun observeCompletions(startEpochDay: Long, endEpochDay: Long): Flow<List<OccurrenceCompletionEntity>> = completions
    override suspend fun getItem(itemId: Long) = items.value.firstOrNull { it.id == itemId }
    override suspend fun getItemsWithReminders() = items.value.filter { it.reminderMinutesBefore != null }
    override suspend fun insert(item: PlannerItemEntity): Long {
        val id = nextId++
        items.value += item.copy(id = id)
        return id
    }
    override suspend fun update(item: PlannerItemEntity) { items.value = items.value.map { if (it.id == item.id) item else it } }
    override suspend fun delete(itemId: Long) {
        items.value = items.value.filterNot { it.id == itemId }
        completions.value = completions.value.filterNot { it.itemId == itemId }
    }
    override suspend fun getCompletion(itemId: Long, dateEpochDay: Long) =
        completions.value.firstOrNull { it.itemId == itemId && it.occurrenceDateEpochDay == dateEpochDay }
    override suspend fun insertCompletion(completion: OccurrenceCompletionEntity) {
        completions.value = completions.value.filterNot {
            it.itemId == completion.itemId && it.occurrenceDateEpochDay == completion.occurrenceDateEpochDay
        } + completion
    }
    override suspend fun deleteCompletion(itemId: Long, dateEpochDay: Long) {
        completions.value = completions.value.filterNot { it.itemId == itemId && it.occurrenceDateEpochDay == dateEpochDay }
    }
}
