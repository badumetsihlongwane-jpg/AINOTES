package com.ainotes.studyassistant.notifications

interface ReminderScheduler {
    fun schedule(reminderId: Long, title: String, message: String, triggerAt: Long)
}
