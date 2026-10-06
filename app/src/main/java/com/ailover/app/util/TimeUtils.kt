package com.ailover.app.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object TimeUtils {
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val monthDayFormat = SimpleDateFormat("MM-dd", Locale.getDefault())
    private val fullFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    fun formatConversationTime(timestamp: Long): String {
        val now = Calendar.getInstance()
        val then = Calendar.getInstance().apply { timeInMillis = timestamp }

        return when {
            isSameDay(now, then) -> timeFormat.format(Date(timestamp))
            isYesterday(now, then) -> "昨天"
            isSameYear(now, then) -> monthDayFormat.format(Date(timestamp))
            else -> fullFormat.format(Date(timestamp))
        }
    }

    fun formatMessageTime(timestamp: Long): String {
        return timeFormat.format(Date(timestamp))
    }

    private fun isSameDay(c1: Calendar, c2: Calendar): Boolean {
        return c1.get(Calendar.YEAR) == c2.get(Calendar.YEAR) &&
            c1.get(Calendar.DAY_OF_YEAR) == c2.get(Calendar.DAY_OF_YEAR)
    }

    private fun isYesterday(now: Calendar, then: Calendar): Boolean {
        val yesterday = (Calendar.getInstance() as Calendar).apply {
            add(Calendar.DAY_OF_YEAR, -1)
        }
        return isSameDay(yesterday, then)
    }

    private fun isSameYear(c1: Calendar, c2: Calendar): Boolean {
        return c1.get(Calendar.YEAR) == c2.get(Calendar.YEAR)
    }
}
