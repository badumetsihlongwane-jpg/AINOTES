package com.ainotes.studyassistant.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.ainotes.studyassistant.data.local.dao.NoteDao
import com.ainotes.studyassistant.data.local.dao.ProgressLogDao
import com.ainotes.studyassistant.data.local.dao.ReminderDao
import com.ainotes.studyassistant.data.local.dao.SubjectDao
import com.ainotes.studyassistant.data.local.dao.TaskDao
import com.ainotes.studyassistant.data.local.dao.UploadedFileDao
import com.ainotes.studyassistant.data.local.entity.NoteEntity
import com.ainotes.studyassistant.data.local.entity.ProgressLogEntity
import com.ainotes.studyassistant.data.local.entity.ReminderEntity
import com.ainotes.studyassistant.data.local.entity.SubjectEntity
import com.ainotes.studyassistant.data.local.entity.TaskEntity
import com.ainotes.studyassistant.data.local.entity.UploadedFileEntity

@Database(
    entities = [
        SubjectEntity::class,
        TaskEntity::class,
        NoteEntity::class,
        UploadedFileEntity::class,
        ReminderEntity::class,
        ProgressLogEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun subjectDao(): SubjectDao
    abstract fun taskDao(): TaskDao
    abstract fun noteDao(): NoteDao
    abstract fun uploadedFileDao(): UploadedFileDao
    abstract fun reminderDao(): ReminderDao
    abstract fun progressLogDao(): ProgressLogDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "study_assistant.db"
                ).build().also { instance = it }
            }
        }
    }
}
