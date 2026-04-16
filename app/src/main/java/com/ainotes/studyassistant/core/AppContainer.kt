package com.ainotes.studyassistant.core

import android.content.Context
import com.ainotes.studyassistant.data.local.AppDatabase
import com.ainotes.studyassistant.data.repository.DefaultStudyRepository
import com.ainotes.studyassistant.domain.StudyRepository
import com.ainotes.studyassistant.notifications.AndroidReminderScheduler
import com.ainotes.studyassistant.notifications.ReminderScheduler

class AppContainer(context: Context) {
    private val database = AppDatabase.getInstance(context)
    val repository: StudyRepository = DefaultStudyRepository(database)
    val reminderScheduler: ReminderScheduler = AndroidReminderScheduler(context)
}
