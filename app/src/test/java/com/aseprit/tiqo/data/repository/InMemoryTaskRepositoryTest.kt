package com.aseprit.tiqo.data.repository

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InMemoryTaskRepositoryTest {
    @Test
    fun toggleCompleted_changesTaskState() = runBlocking {
        val repository = InMemoryTaskRepository()
        val task = repository.tasks.value.firstOrNull { !it.isCompleted }
        assertNotNull(task)

        repository.toggleCompleted(requireNotNull(task).id)

        assertTrue(repository.tasks.value.first { it.id == task.id }.isCompleted)
    }

    @Test
    fun delete_removesTask() = runBlocking {
        val repository = InMemoryTaskRepository()
        val task = repository.tasks.value.first()

        repository.delete(task.id)

        assertFalse(repository.tasks.value.any { it.id == task.id })
    }
}
