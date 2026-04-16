package com.ainotes.studyassistant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.ainotes.studyassistant.core.AppContainer
import com.ainotes.studyassistant.core.theme.StudyAssistantTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as StudyAssistantApp).container

        setContent {
            StudyAssistantTheme {
                StudyAssistantAppRoot(container = container)
            }
        }
    }
}
