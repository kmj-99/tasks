package org.tasks.ui.editviewmodel

import com.todoroo.astrid.data.Task
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.UninstallModules
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.tasks.injection.ProductionModule
import org.tasks.makers.TaskMaker.newTask

@UninstallModules(ProductionModule::class)
@HiltAndroidTest
class TaskEditViewModelTest : BaseTaskEditViewModelTest() {
    @Test
    fun noChangesForNewTask() {
        viewModel.setup(newTask())

        assertFalse(viewModel.hasChanges())
    }

    @Test
    fun dontSaveTaskWithoutChanges() = runBlocking {
        viewModel.setup(newTask())

        assertFalse(save())

        assertTrue(taskDao.getAll().isEmpty())
    }

    @Test
    fun dontSaveTaskTwice() = runBlocking {
        viewModel.setup(newTask())

        viewModel.priority = Task.Priority.HIGH

        assertTrue(save())

        assertFalse(viewModel.save())
    }
}