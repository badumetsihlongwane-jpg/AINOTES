# AINOTES - Android AI Study App Prototype

This repository contains a working Android prototype built with Kotlin, Jetpack Compose, MVVM, and Room.

The current scope is feature-first (no AI integration yet), with architecture designed so Gemma 4 or other AI services can be integrated later without refactoring the core app.

## What Is Implemented

- Home dashboard with live overview of subjects, tasks, notes, files, and upcoming work
- Subject management
- Task and assignment management
- Task progress updates and logging
- Notes management
- File metadata capture and storage, including subject/task attachment
- Reminders with alarm scheduling via AlarmManager + notification receiver
- Progress screen with summary metrics and history
- Local persistence with Room and reactive Flow streams

## Tech Stack

- Android
- Kotlin
- Jetpack Compose
- MVVM
- Room

## Project Structure

- `app/src/main/java/com/ainotes/studyassistant/data/local`: Room database, entities, DAOs
- `app/src/main/java/com/ainotes/studyassistant/data/repository`: Repository implementation
- `app/src/main/java/com/ainotes/studyassistant/domain`: Repository contracts and dashboard model
- `app/src/main/java/com/ainotes/studyassistant/feature`: Feature ViewModels and Compose screens
- `app/src/main/java/com/ainotes/studyassistant/core`: App container, ViewModel factory, navigation, theme
- `app/src/main/java/com/ainotes/studyassistant/notifications`: Reminder scheduler and alarm receiver

## Future AI Integration Path

The code is prepared for future AI actions by centralizing app operations in the repository layer.

Potential AI-capable actions to plug in later:

- create subjects
- create tasks and study plans
- summarize notes
- schedule reminders
- suggest priority and deadline adjustments

When AI is added, integrate it behind service interfaces called by ViewModels, while keeping Room and UI flows unchanged.

## Notes

- AI and Gemma 4 are intentionally not integrated in this prototype.
- This repository is structured to build on CI/CD after push.