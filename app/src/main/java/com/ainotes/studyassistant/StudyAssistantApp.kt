package com.ainotes.studyassistant

import android.app.Application
import com.ainotes.studyassistant.core.AppContainer

class StudyAssistantApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
