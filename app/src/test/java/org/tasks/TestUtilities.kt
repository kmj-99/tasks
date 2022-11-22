package org.tasks

import android.content.Context
import at.bitfire.ical4android.Task.Companion.tasksFromReader
import com.todoroo.astrid.data.Task
import kotlinx.coroutines.runBlocking
import org.tasks.caldav.applyRemote
import org.tasks.caldav.iCalendar.Companion.reminders
import org.tasks.data.Alarm
import org.tasks.data.CaldavTask
import org.tasks.preferences.Preferences
import org.tasks.time.DateTime
import java.io.StringReader
import java.nio.file.Files
import java.nio.file.Paths
import java.util.*

object TestUtilities {
    fun withTZ(id: String, runnable: suspend () -> Unit) = withTZ(TimeZone.getTimeZone(id), runnable)

    fun withTZ(tz: TimeZone, runnable: suspend () -> Unit) {
        val def = TimeZone.getDefault()
        try {
            TimeZone.setDefault(tz)
            runBlocking {
                runnable()
            }
        } finally {
            TimeZone.setDefault(def)
        }
    }

    fun assertEquals(expected: Long, actual: DateTime) =
            org.junit.Assert.assertEquals(expected, actual.millis)

    fun assertEquals(expected: DateTime, actual: Long?) =
            org.junit.Assert.assertEquals(expected.millis, actual)

    fun newPreferences(context: Context): Preferences {
        return Preferences(context, "test_preferences")
    }

    fun vtodo(path: String): Task {
        val task = Task()
        task.applyRemote(fromResource(path), null)
        return task
    }

    val String.alarms: List<Alarm>
        get() = fromResource(this).reminders

    fun setup(path: String): Triple<Task, CaldavTask, at.bitfire.ical4android.Task> {
        val task = Task()
        val vtodo = readFile(path)
        val remote = fromString(vtodo)
        task.applyRemote(remote, null)
        return Triple(task, CaldavTask(), remote)
    }

    private fun fromResource(path: String): at.bitfire.ical4android.Task =
            fromString(readFile(path))

    fun readFile(path: String): String {
        val uri = javaClass.classLoader?.getResource(path)?.toURI()
                ?: throw IllegalArgumentException()
        val paths = Paths.get(uri)
        return String(Files.readAllBytes(paths), Charsets.UTF_8)
    }

    fun fromString(task: String): at.bitfire.ical4android.Task =
            tasksFromReader(StringReader(task))
                    .takeIf { it.size == 1 }
                    ?.first()
                    ?: throw IllegalStateException()
}