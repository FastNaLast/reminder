package ru.monthly.reminder

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime

class AlarmSchedulerTest {

    private fun task(day: Int, hour: Int = 9, minute: Int = 0) =
        Task(id = 1, title = "test", dayOfMonth = day, hour = hour, minute = minute)

    @Test
    fun `time already passed today means next month`() {
        val now = LocalDateTime.of(2026, 1, 15, 10, 0)
        assertEquals(
            LocalDateTime.of(2026, 2, 15, 9, 0),
            AlarmScheduler.nextTrigger(task(15), now)
        )
    }

    @Test
    fun `time still ahead today means this month`() {
        val now = LocalDateTime.of(2026, 1, 10, 10, 0)
        assertEquals(
            LocalDateTime.of(2026, 1, 15, 9, 0),
            AlarmScheduler.nextTrigger(task(15), now)
        )
    }

    @Test
    fun `day 31 in february clamps to 28`() {
        val now = LocalDateTime.of(2026, 1, 31, 10, 0)
        assertEquals(
            LocalDateTime.of(2026, 2, 28, 9, 0),
            AlarmScheduler.nextTrigger(task(31), now)
        )
    }

    @Test
    fun `day 31 in leap february clamps to 29`() {
        val now = LocalDateTime.of(2028, 1, 31, 10, 0)
        assertEquals(
            LocalDateTime.of(2028, 2, 29, 9, 0),
            AlarmScheduler.nextTrigger(task(31), now)
        )
    }

    @Test
    fun `first day at midnight keeps today`() {
        val now = LocalDateTime.of(2026, 3, 1, 0, 0)
        assertEquals(
            LocalDateTime.of(2026, 3, 1, 9, 0),
            AlarmScheduler.nextTrigger(task(1), now)
        )
    }

    @Test
    fun `exact trigger minute is pushed to next month`() {
        val now = LocalDateTime.of(2026, 5, 20, 9, 0)
        assertEquals(
            LocalDateTime.of(2026, 6, 20, 9, 0),
            AlarmScheduler.nextTrigger(task(20), now)
        )
    }
}
